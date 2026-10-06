/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.isLandScape
import io.github.proify.android.extensions.setColorAlpha
import io.github.proify.android.extensions.toBitmap
import io.github.proify.lyricon.app.bridge.AppBridge.LyricGesturePrefs
import io.github.proify.lyricon.colorextractor.palette.ColorExtractor
import io.github.proify.lyricon.colorextractor.palette.ColorPaletteResult
import io.github.proify.lyricon.common.util.ResourceMapper
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.hook.ClockViewFinder
import io.github.proify.lyricon.xposed.systemui.hook.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.hook.StatusBarColorMonitor
import io.github.proify.lyricon.xposed.systemui.hook.StatusBarTouchHooker
import io.github.proify.lyricon.xposed.systemui.lyric.LyricViewController.isPlaying
import io.github.proify.lyricon.xposed.systemui.lyric.control.LyricControlPopup
import io.github.proify.lyricon.xposed.systemui.util.OnColorChangeListener
import io.github.proify.lyricon.xposed.systemui.util.ViewVisibilityController
import java.io.File

/**
 * 状态栏歌词视图控制器：负责歌词视图的注入、位置锚定及显隐逻辑
 */
@SuppressLint("DiscouragedApi")
class StatusBarViewController(
    val statusBarView: ViewGroup,
    var currentLyricStyle: LyricStyle
) : ScreenStateMonitor.ScreenStateListener, StatusBarTouchHooker.Target {
    companion object {
        const val TAG = "StatusBarViewController"
    }

    val context: Context = statusBarView.context.applicationContext
    val visibilityController: ViewVisibilityController = ViewVisibilityController(statusBarView)
    val lyricView: StatusBarLyric by lazy { createLyricView(currentLyricStyle) }

    // --- 手势控制状态 (随偏好热更新) ---
    private var gestureEnabledValue: Boolean = LyricGesturePrefs.DEFAULT_ENABLED
    private var swipeLeftAction: Int = LyricGesturePrefs.DEFAULT_SWIPE_LEFT
    private var swipeRightAction: Int = LyricGesturePrefs.DEFAULT_SWIPE_RIGHT
    private var tapAction: Int = LyricGesturePrefs.DEFAULT_TAP
    private var longPressAction: Int = LyricGesturePrefs.DEFAULT_LONG_PRESS

    private var lastAnchor = ""
    private var lastInsertionOrder = -1
    private var internalRemoveLyricViewFlag = false
    private var lastHighlightView: View? = null
    private var colorMonitorView: View? = null
    private var coverColorPaletteResult: ColorPaletteResult? = null
    private var systemStatusBarColor: SystemStatusBarColor? = null

    private val colorChangeListener = object : OnColorChangeListener {

        private var colorFingerprint: String? = null
        override fun onColorChanged(color: Int, darkIntensity: Float) {
            val colorFingerprint = color.toString() + darkIntensity
            if (colorFingerprint == this.colorFingerprint) return
            this.colorFingerprint = colorFingerprint

            updateStatusColor(SystemStatusBarColor(color, darkIntensity))
        }
    }

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        applyVisibilityRulesNow()
//        // 无副作用复核:布局事件也是状态栏颜色可能的变更时机(补救观察点)
//        StatusBarColorMonitor.refresh()
    }

    // --- 生命周期与初始化 ---
    fun onCreate() {
        statusBarView.addOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.addOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.addListener(this)
        lyricView.onPlayingChanged = { _ -> }

        // 手势控制:读取偏好并绑定回调,手势动作可配置
        refreshGestureConfig()
        lyricView.gestureListener = { gesture -> onLyricGesture(gesture) }

        // 根窗口触摸拦截：ColorOS 流体云胶囊覆盖状态栏后会截走全部触摸事件，
        // 注册为拦截目标后由 StatusBarWindowView.dispatchTouchEvent 在顶层直接分发。
        StatusBarTouchHooker.registerTarget(this)

        StatusBarColorMonitor.bindStatusBar(statusBarView)
        colorMonitorView = getClockView()
        StatusBarColorMonitor.bindClockView(colorMonitorView)
        StatusBarColorMonitor.addListener(colorChangeListener)

        statusBarView.doOnAttach { checkLyricViewExists() }
        YLog.info(tag = TAG, "Lyric view created for $statusBarView")
    }

    fun onDestroy() {
        StatusBarTouchHooker.unregisterTarget(this)
        statusBarView.removeOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.removeOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.removeListener(this)
        lyricView.onPlayingChanged = null
        lyricView.gestureListener = null
        lyricView.setOnClickListener(null)
        LyricControlPopup.dismissIfOwnedBy(lyricView)
        StatusBarColorMonitor.removeListener(colorChangeListener)
        colorMonitorView?.let { StatusBarColorMonitor.unbindClockView(it) }
        colorMonitorView = null
        YLog.info(tag = TAG, "Lyric view destroyed for $statusBarView")
    }

    // --- 核心业务逻辑 ---

    /**
     * 更新状态栏颜色，内部决定最终颜色
     */
    internal fun updateStatusColor(systemStatusBarColor: SystemStatusBarColor) {
        this.systemStatusBarColor = systemStatusBarColor

        val textStyle = currentLyricStyle.packageStyle.text
        lyricView.apply {
            currentStatusColor.apply {
                this.darkIntensity = systemStatusBarColor.darkIntensity

                val coverColorPaletteResult = coverColorPaletteResult
                when {
                    coverColorPaletteResult != null
                            && textStyle.enableExtractCoverTextColor
                            && textStyle.enableExtractCoverTextGradient -> {
                        val themeColors = coverColorPaletteResult
                            .let { if (isLightMode) it.lightModeColors else it.darkModeColors }

                        val gradient = themeColors.swatches

                        this.color = gradient
                        this.translucentColor = gradient.map {
                            it.setColorAlpha(0.75f)
                        }.toIntArray()
                    }

                    coverColorPaletteResult != null
                            && textStyle.enableExtractCoverTextColor -> {
                        val themeColors = coverColorPaletteResult
                            .let { if (isLightMode) it.lightModeColors else it.darkModeColors }

                        val primary = themeColors.primary

                        this.color = intArrayOf(primary)
                        this.translucentColor = intArrayOf(primary.setColorAlpha(0.75f))
                    }

                    else -> {
                        this.color = intArrayOf(systemStatusBarColor.color)
                        this.translucentColor =
                            intArrayOf(systemStatusBarColor.color.setColorAlpha(0.5f))
                    }
                }
            }
            setStatusBarColor(currentStatusColor)
        }
    }

    /**
     * 更新歌词样式及位置，若锚点或顺序变化则重新注入视图
     */
    fun updateLyricStyle(lyricStyle: LyricStyle) {
        this.currentLyricStyle = lyricStyle
        val basicStyle = lyricStyle.basicStyle

        val needUpdateLocation = lastAnchor != basicStyle.anchor
                || lastInsertionOrder != basicStyle.insertionOrder
                || !lyricView.isAttachedToWindow

        if (needUpdateLocation) {
            YLog.info(
                TAG,
                "Lyric location changed: ${basicStyle.anchor}, order ${basicStyle.insertionOrder}"
            )
            updateLocation(basicStyle)
        }
        lyricView.updateStyle(lyricStyle)
        refreshGestureConfig()

        systemStatusBarColor?.let { updateStatusColor(it) }
    }

    fun updateCoverThemeColors(coverFile: File?) {
        coverColorPaletteResult = null
        try {
            val bitmap = coverFile?.toBitmap() ?: return
            ColorExtractor.extractAsync(
                bitmap = bitmap,
                cacheKey = {
                    coverFile.name
                }) {
                coverColorPaletteResult = it
                systemStatusBarColor?.let { updateStatusColor(it) }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            YLog.error(TAG, "Failed to extract cover theme colors", e)
        }
    }

    /**
     * 处理视图注入逻辑：根据 BasicStyle 寻找锚点并插入歌词视图
     */
    private fun updateLocation(baseStyle: BasicStyle) {
        val anchor = baseStyle.anchor
        val anchorId = context.resources.getIdentifier(anchor, "id", context.packageName)
        val anchorView = statusBarView.findViewById<View>(anchorId) ?: return run {
            YLog.error(TAG, "Lyric anchor view $anchor not found")
        }

        val anchorParent = anchorView.parent as? ViewGroup ?: return run {
            YLog.error(TAG, "Lyric anchor parent not found")
        }

        // 标记内部移除，避免触发冗余的 detach 逻辑
        internalRemoveLyricViewFlag = true

        (lyricView.parent as? ViewGroup)?.removeView(lyricView)

        val anchorIndex = anchorParent.indexOfChild(anchorView)

        val lp = lyricView.layoutParams ?: run {
            val width = baseStyle.getAutoWidth(
                context.isLandScape(),
                isOplusCapsuleShowing = OplusCapsuleHooker.isShowing
            ).dp

            ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // 执行插入：在前或在后
        val targetIndex =
            if (baseStyle.insertionOrder == BasicStyle.INSERTION_ORDER_AFTER) anchorIndex + 1
            else anchorIndex
        anchorParent.addView(lyricView, targetIndex, lp)

        lyricView.updateVisibility()
        lastAnchor = anchor
        lastInsertionOrder = baseStyle.insertionOrder
        internalRemoveLyricViewFlag = false

        YLog.info(TAG, "Lyric injected: anchor $anchor, index $targetIndex")
    }

    fun checkLyricViewExists() {
        if (lyricView.isAttachedToWindow) return
        lastAnchor = ""
        lastInsertionOrder = -1
        updateLyricStyle(currentLyricStyle)
    }

    // --- 辅助方法 ---

    private fun getClockView(): View? = ClockViewFinder.find(statusBarView)

    private var wasPlayingBeforeVisibilityUpdate: Boolean = false

    fun computeShouldApplyPlayingRules(): Boolean {
        return isPlaying && when {
            lyricView.isDisabledVisible -> !lyricView.isHideOnLockScreen()
            lyricView.isVisible -> true
            else -> false
        }
    }

    private fun applyVisibilityRulesNow() {
        val isPlaying = computeShouldApplyPlayingRules()
        fun apply() {
            visibilityController.applyVisibilityRules(
                rules = currentLyricStyle.basicStyle.visibilityRules,
                isPlaying = isPlaying
            )
        }

        if (!isPlaying) {
            // 仅在之前是播放状态时才更新（避免重复更新非播放状态的隐藏逻辑）
            if (wasPlayingBeforeVisibilityUpdate) {
                apply()
                wasPlayingBeforeVisibilityUpdate = false
            }
        } else {
            apply()
            wasPlayingBeforeVisibilityUpdate = true
        }
    }

    private fun createLyricView(style: LyricStyle) =
        StatusBarLyric(context, style, getClockView() as? TextView)

    // --- 手势控制 ---

    /**
     * 从偏好刷新手势配置,并同步视图的手势开关与点击行为。
     *
     * 手势关闭时保留旧版"单击打开控制面板"的行为(通过点击监听器委托)。
     */
    private fun refreshGestureConfig() {
        gestureEnabledValue = LyricPrefs.gestureEnabled
        swipeLeftAction = LyricPrefs.gestureAction(
            LyricGesturePrefs.KEY_SWIPE_LEFT,
            LyricGesturePrefs.DEFAULT_SWIPE_LEFT
        )
        swipeRightAction = LyricPrefs.gestureAction(
            LyricGesturePrefs.KEY_SWIPE_RIGHT,
            LyricGesturePrefs.DEFAULT_SWIPE_RIGHT
        )
        tapAction = LyricPrefs.gestureAction(
            LyricGesturePrefs.KEY_TAP,
            LyricGesturePrefs.DEFAULT_TAP
        )
        longPressAction = LyricPrefs.gestureAction(
            LyricGesturePrefs.KEY_LONG_PRESS,
            LyricGesturePrefs.DEFAULT_LONG_PRESS
        )

        lyricView.gestureEnabled = gestureEnabledValue
        lyricView.hapticEnabled = LyricPrefs.gestureHapticEnabled
        // 只有手势控制开启时才接收点击，面板由用户配置的 TAP 动作显式打开。
        // 关闭后既不识别手势，也不保留历史单击监听器。
        lyricView.setOnClickListener(null)
        lyricView.isClickable = gestureEnabledValue
    }

    /**
     * 手势回调入口:根据当前配置将手势映射为动作并执行
     */
    private fun onLyricGesture(gesture: StatusBarLyric.GestureType) {
        if (!gestureEnabledValue) return

        val action = when (gesture) {
            StatusBarLyric.GestureType.SWIPE_LEFT -> swipeLeftAction
            StatusBarLyric.GestureType.SWIPE_RIGHT -> swipeRightAction
            StatusBarLyric.GestureType.TAP -> tapAction
            StatusBarLyric.GestureType.LONG_PRESS -> longPressAction
        }

        when (action) {
            LyricGesturePrefs.ACTION_NONE -> Unit
            LyricGesturePrefs.ACTION_TOGGLE_PLAY -> PlaybackControl.togglePlay()
            LyricGesturePrefs.ACTION_PREVIOUS -> PlaybackControl.previous()
            LyricGesturePrefs.ACTION_NEXT -> PlaybackControl.next()
            LyricGesturePrefs.ACTION_OPEN_CONTROL -> LyricControlPopup.show(lyricView)
            else -> YLog.warning(TAG, "Unknown gesture action: $action")
        }
    }

    /**
     * 是否允许根窗口拦截器把触摸交给歌词。
     *
     * 手势总开关关闭时保持 false：此时歌词与普通状态栏视图无异，
     * 不应由拦截器抢走胶囊容器的触摸。
     */
    override val gestureEnabled: Boolean
        get() = gestureEnabledValue

    /**
     * 命中测试：仅在歌词真正可见时参与接管。
     *
     * 可见性以 [View.VISIBLE] 判定而不使用 [View.isShown]，
     * 避免状态栏父链上出现 GONE/INVISIBLE 标记时误判为不可见。
     */
    override fun touchPoint(event: MotionEvent, hitTolerance: Int): Point? {
        if (!gestureEnabled) return null
        if (lyricView.visibility != View.VISIBLE) return null
        if (!lyricView.isAttachedToWindow) return null
        if (lyricView.width <= 0 || lyricView.height <= 0) return null

        val location = IntArray(2)
        lyricView.getLocationOnScreen(location)

        val left = location[0] - hitTolerance
        val top = location[1] - hitTolerance
        val right = location[0] + lyricView.width + hitTolerance
        val bottom = location[1] + lyricView.height + hitTolerance

        if (event.rawX < left || event.rawX > right || event.rawY < top || event.rawY > bottom) {
            return null
        }

        return Point(event.rawX.toInt(), event.rawY.toInt())
    }

    /**
     * 把拦截器修正过坐标的事件派发给歌词控件。
     *
     * 歌词手势只关心相对位移，坐标修正的目的仅在于让按压反馈与
     * [StatusBarLyric] 内部的滑动判定拿到正确的局部量。
     */
    override fun dispatchTouchToLyric(event: MotionEvent): Boolean =
        lyricView.dispatchTouchEvent(event)

    fun highlightView(idName: String?) {
        YLog.info(TAG, "Highlighting view id:$idName")

        lastHighlightView?.background = null
        if (idName.isNullOrBlank()) return

        val id = ResourceMapper.getIdByName(context, idName)
        statusBarView.findViewById<View>(id)?.let { view ->
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor("#FF3582FF".toColorInt())
                cornerRadius = 20.dp.toFloat()
            }
            lastHighlightView = view
        } ?: YLog.error(TAG, "Highlight target $idName not found")
    }

    private val lyricAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            YLog.info(TAG, "LyricView attached")
        }

        override fun onViewDetachedFromWindow(v: View) {
            YLog.info(TAG, "LyricView detached")
            if (!internalRemoveLyricViewFlag) {
                checkLyricViewExists()
            } else {
                YLog.info(TAG, "LyricView detached by internal flag")
            }
        }
    }

    private val statusBarAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) {}
    }

    override fun onScreenOn() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = false
    }

    override fun onScreenOff() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = true
    }

    override fun onScreenUnlocked() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = false
    }

    fun onDisableStateChanged(shouldHide: Boolean) {
        lyricView.isDisabledVisible = shouldHide
    }

    override fun equals(other: Any?): Boolean =
        (this === other) ||
                (other is StatusBarViewController && statusBarView == other.statusBarView)

    override fun hashCode(): Int = 31 * 17 + statusBarView.hashCode()

    data class SystemStatusBarColor(val color: Int, val darkIntensity: Float)
}