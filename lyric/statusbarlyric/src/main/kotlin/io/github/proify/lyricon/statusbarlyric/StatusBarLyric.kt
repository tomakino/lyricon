/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.contains
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.view.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.LyricPlayerView
import io.github.proify.lyricon.statusbarlyric.gesture.GestureType
import io.github.proify.lyricon.statusbarlyric.gesture.LyricTouchController
import io.github.proify.lyricon.statusbarlyric.logo.LyricLogoView

/**
 * 状态栏歌词视图：状态栏歌词功能的组合根。
 *
 * 只做编排，不实现细节：
 * - 布局：[LyricLogoView] + [LyricTextView] 的组装、容器宽高与 Logo 位置
 * - 状态：播放状态、歌词内容、休眠、禁用隐藏 -> 可见性判定
 * - 协作：手势识别委托给 [LyricTouchController]，超时隐藏委托给 [LyricTimeoutController]
 *
 * 线程约束：所有公开方法都必须在主线程调用（由宿主 `LyricViewController` 保证），
 * 内部计时统一使用主线程 [Handler]。
 *
 * @param initialStyle   初始歌词样式，后续通过 [updateStyle] 热更新
 * @param linkedTextView 系统状态栏时钟视图，用于继承系统字号与字体
 */
@SuppressLint("ViewConstructor")
class StatusBarLyric(
    context: Context,
    initialStyle: LyricStyle,
    linkedTextView: TextView?
) : LinearLayout(context) {

    companion object {
        /** 视图 TAG，宿主可据此识别并排除歌词子树（如状态栏颜色 Hook）。 */
        const val VIEW_TAG: String = "lyricon:lyric_view"

        private const val TAG = "StatusBarLyric"

        /** 首次进入显示时的布局过渡时长（毫秒），比样式过渡更慢以配合进入动画。 */
        private const val VISIBILITY_TRANSITION_MS = 500L
    }

    // --- 子视图 ---

    /** 歌词来源图标（封面 / Provider / App 图标 / 自定义图片）。 */
    val logoView: LyricLogoView = LyricLogoView(context).apply {
        this.linkedTextView = linkedTextView
    }

    /** 歌词文本视图。间奏开始时驱动 Logo 进度条，结束时清理。 */
    val textView: LyricTextView = LyricTextView(context).apply {
        this.linkedTextView = linkedTextView
        this.interludeListener = object : LyricTextView.InterludeListener {

            override fun enteringInterludeMode(duration: Long) {
                logoView.syncProgress(0, duration)
            }

            override fun exitInterludeMode() {
                logoView.clearProgress()
            }
        }
    }

    // --- 协作组件 ---

    private val touchController = LyricTouchController(this)

    /** 主线程调度器，超时计时与延迟任务共用。 */
    private val mainHandler: Handler = Handler(context.mainLooper)

    private val timeoutController = LyricTimeoutController(mainHandler) {
        // 超时到期后需要立刻隐藏歌词
        updateVisibility()
    }

    // --- 对外状态 ---

    /** 当前状态栏配色；外部只能通过 [setStatusColor] / [updateStatusColor] 修改。 */
    var currentStatusColor: StatusColor = StatusColor()
        private set

    /** 播放状态变化回调，供上层同步控制面板等 UI。 */
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    /**
     * 是否因上层禁用策略而隐藏歌词。
     *
     * 语义是"应当隐藏"而非"当前不可见"：由宿主 `onDisableStateChanged(shouldHide)` 驱动。
     */
    var isHiddenByDisabled: Boolean = false
        set(value) {
            field = value
            updateVisibility()

            // 显隐切换后强制重刷配色：歌词在播放结束瞬间被反复显隐时，
            // 子视图可能残留半透明的错误配色（历史问题回归防护）
            forceRefreshStatusColors()
        }

    /**
     * 屏幕休眠模式。
     *
     * 休眠期间不驱动歌词进度，只记录最近一次定位请求，避免息屏后持续刷新；
     * 退出休眠时若有缓存的定位则一次性 seek 回去。
     */
    var isSleepMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            Log.d(TAG, "休眠模式：$value")

            if (value) {
                // 进入休眠：先丢弃过期请求，避免下次亮屏跳到陈旧位置
                pendingSleepPosition = null
                textView.setAnimationsPaused(true)
            } else {
                // 退出休眠：只有休眠期间真的收到过定位请求才 seek。
                // 若直接 seek(0)，会把歌词错误地拉回歌曲开头。
                pendingSleepPosition?.let { seekTo(it) }
                pendingSleepPosition = null
                textView.setAnimationsPaused(false)
            }
        }

    // --- 手势转发（宿主直接读写这些属性，实现委托给控制器） ---

    /**
     * 是否启用手势识别。
     *
     * 关闭时回退到 [View] 默认触摸行为（此时可通过
     * [setOnClickListener] 委托单击，保持旧版行为）。
     */
    var gestureEnabled: Boolean
        get() = touchController.isGestureEnabled
        set(value) {
            touchController.isGestureEnabled = value
        }

    /** 是否启用震动反馈（手势识别成功时触发），由上层控制器按偏好配置。 */
    var hapticEnabled: Boolean
        get() = touchController.isHapticEnabled
        set(value) {
            touchController.isHapticEnabled = value
        }

    /** 手势回调，在主线程派发，仅 [gestureEnabled] 为 true 时触发。 */
    var gestureListener: ((GestureType) -> Unit)?
        get() = touchController.gestureListener
        set(value) {
            touchController.gestureListener = value
        }

    // --- 运行时状态 ---

    private var currentStyle: LyricStyle = initialStyle
    private var isPlaying: Boolean = false
    private var isOplusCapsuleShowing: Boolean = false

    /**
     * 上一次已应用的播放状态。
     *
     * 初值刻意使用 `null` 而不是 `false`：首次收到 `setPlaying(false)` 也必须执行
     * 一次重置，否则视图可能残留上一位播放器的歌词。
     */
    private var lastAppliedPlaying: Boolean? = null

    /** 上一次应用的 Logo gravity；null 表示尚未应用。用于跳过重复重排。 */
    private var lastLogoGravity: Int? = null

    /** 上一次"因禁用而隐藏"的值，用于判断是否播放进入动画。 */
    private var lastHiddenByDisabled: Boolean = false

    /** 休眠期间缓存的定位请求；null 表示休眠期间没有收到过定位。 */
    private var pendingSleepPosition: Long? = null

    /** 当前是否持有歌词内容，用于超时判定。 */
    private var hasLyricContent: Boolean = false

    /** 当前显示的一行歌词文本，用于关键词超时匹配。 */
    private var currentLyric: String? = null

    /**
     * 当前承载的歌词内容类型。
     *
     * 恢复播放时需要按上次的类型重放内容，否则切歌后歌词会缺失。
     */
    private sealed interface LyricContent {
        /** 尚未收到任何内容。 */
        object None : LyricContent

        /** 结构化歌曲歌词；持有 null 表示显式清空。 */
        data class SongContent(val song: Song?) : LyricContent

        /** 纯文本歌词；持有 null 表示显式清空。 */
        data class PlainText(val text: String?) : LyricContent
    }

    private var contentState: LyricContent = LyricContent.None

    // --- 系统服务 ---

    private val keyguardManager: KeyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    // --- 布局过渡 ---

    /** 样式或尺寸突变时的一次性 CHANGING 过渡。 */
    private val styleTransition: LayoutTransitionX by lazy { newOneShotTransition() }

    /** 首次进入显示时的一次性 CHANGING 过渡，时长更长。 */
    private val visibilityTransition: LayoutTransitionX by lazy {
        newOneShotTransition(VISIBILITY_TRANSITION_MS)
    }

    // --- 子视图监听 ---

    /** 文本子视图结构变化时刷新可见性（新增/移除行都意味着可见内容变了）。 */
    private val textHierarchyChangeListener = object : OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) = updateVisibility()
        override fun onChildViewRemoved(parent: View?, child: View?) = updateVisibility()
    }

    /** 歌词行变化时更新关键词匹配依据并重置超时计时。 */
    private val lyricCountChangeListener = object : LyricPlayerView.LyricCountChangeListener {

        override fun onLyricTextChanged(old: String, new: String) {
            currentLyric = new
            refreshLyricTimeoutState()
        }

        override fun onLyricChanged(
            news: List<IRichLyricLine>,
            removes: List<IRichLyricLine>
        ) {
            currentLyric = news.lastOrNull()?.text
            refreshLyricTimeoutState()
        }
    }

    init {
        tag = VIEW_TAG
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        layoutTransition = null

        // 文本占据剩余宽度，Logo 由 applyLogoGravity 决定插入位置
        addView(
            textView,
            LayoutParams(0, LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        )

        applyLogoGravity()
        applyInitialStyle(initialStyle)

        textView.setOnHierarchyChangeListener(textHierarchyChangeListener)
        textView.lyricCountChangeListeners += lyricCountChangeListener
    }

    // --- 公开 API ---

    /**
     * 更新歌词样式，并重新应用布局、Logo 与超时规则。
     *
     * 会触发一次性的布局过渡，避免样式突变时视觉跳跃。
     */
    fun updateStyle(style: LyricStyle) {
        startStyleTransition()
        currentStyle = style
        logoView.applyStyle(style)
        applyLogoGravity()
        textView.applyStyle(style)
        applyContainerLayout(style)

        refreshLyricTimeoutState()
        requestLayout()
    }

    /**
     * 整体替换状态栏配色并同步到子视图。
     *
     * 若只是修改其中某个分量（如明暗强度），请使用 [updateStatusColor]。
     */
    fun setStatusColor(color: StatusColor) {
        currentStatusColor = color
        applyStatusColorToChildren(color)
    }

    /**
     * 原地修改状态栏配色并同步到子视图。
     *
     * 上层控制器应通过此入口写入颜色，而不是直接持有 [StatusColor] 实例修改，
     * 这样每次修改都会自动完成子视图着色。
     *
     * ```kotlin
     * lyricView.updateStatusColor {
     *     darkIntensity = 0.8f
     *     color = intArrayOf(0xFFFFFFFF.toInt())
     * }
     * ```
     */
    fun updateStatusColor(block: StatusColor.() -> Unit) {
        currentStatusColor.apply(block)
        applyStatusColorToChildren(currentStatusColor)
    }

    /** 当前是否因锁屏而需要隐藏歌词。 */
    fun isHideOnLockScreen(): Boolean =
        currentStyle.basicStyle.hideOnLockScreen && keyguardManager.isKeyguardLocked

    /**
     * 重新计算并应用可见性。
     *
     * 所有显隐判据集中在此：播放中、非锁屏隐藏、存在可见歌词行、未超时、未被禁用隐藏。
     * 任一判据变化后都应调用本方法。
     */
    fun updateVisibility() {
        val shouldShow = isPlaying &&
                !isHideOnLockScreen() &&
                textView.hasVisibleLyricContent() &&
                !timeoutController.isTimedOut &&
                !isHiddenByDisabled

        // 进入动画的判据依赖"上一次是否被禁用隐藏"，必须在提前返回之前同步，
        // 否则隐藏状态变化后仍会读到陈旧值（原实现在此会漏更新）
        val wasHiddenByDisabled = lastHiddenByDisabled
        lastHiddenByDisabled = isHiddenByDisabled

        if (shouldShow == isVisible) return

        if (isEnterAnimEnabled && shouldShow && !wasHiddenByDisabled) {
            isVisible = true
            // 延后一帧再启动过渡，确保上面的显示状态已经生效
            postDelayed({
                startVisibilityTransition()
                logoView.isForceHidden = false
            }, 0)
        } else {
            isVisible = shouldShow
            // 取消进入动画时 Logo 需要立刻归位，避免残留隐藏状态
            logoView.isForceHidden = isEnterAnimEnabled && !shouldShow && !isHiddenByDisabled
        }
    }

    /** 设置结构化歌曲歌词；传 null 表示清空。 */
    fun setSong(song: Song?) {
        contentState = LyricContent.SongContent(song)
        textView.song = song
        hasLyricContent = !song?.lyrics.isNullOrEmpty()
        refreshLyricTimeoutState()
    }

    /** 设置纯文本歌词；传 null 或空白表示清空。 */
    fun setText(text: String?) {
        contentState = LyricContent.PlainText(text)
        textView.text = text
        hasLyricContent = !text.isNullOrBlank()
        refreshLyricTimeoutState()
    }

    /** 更新播放状态；恢复播放时会按上次的内容类型重放歌词。 */
    fun setPlaying(playing: Boolean) {
        if (lastAppliedPlaying == playing) return
        Log.d(TAG, "setPlaying: $playing")

        lastAppliedPlaying = playing
        isPlaying = playing
        onPlayingChanged?.invoke(playing)

        if (!playing) {
            textView.reset()
        } else {
            when (val content = contentState) {
                is LyricContent.None -> Unit
                is LyricContent.SongContent -> setSong(content.song)
                is LyricContent.PlainText -> setText(content.text)
            }
        }

        refreshLyricTimeoutState()
        updateVisibility()
    }

    /** 定位到指定进度，会立即刷新歌词行。 */
    fun seekTo(position: Long) {
        if (isSleepMode) {
            pendingSleepPosition = position
            return
        }

        textView.seekTo(position)
        refreshLyricTimeoutState()
    }

    /** 按当前播放进度同步歌词（不重置行内动画）；休眠期间只记录不刷新。 */
    fun setPosition(position: Long) {
        if (isSleepMode) {
            pendingSleepPosition = position
            return
        }

        textView.setPosition(position)
    }

    /** 更新翻译与罗马音的显示开关。 */
    fun updateDisplayTranslation(
        displayTranslation: Boolean = textView.isDisplayTranslation,
        displayRoma: Boolean = textView.isDisplayRoma
    ) {
        textView.updateDisplayTranslation(displayTranslation, displayRoma)
    }

    /**
     * 设置 ColorOS 灵动胶囊的显示状态。
     *
     * 胶囊会占用状态栏空间，因此需要同时收窄容器宽度并隐藏 Logo。
     */
    fun setOplusCapsuleVisibility(visible: Boolean) {
        isOplusCapsuleShowing = visible
        startStyleTransition()
        applyContainerWidth(currentStyle)
        logoView.isOplusCapsuleShowing = visible
    }

    // --- 触摸事件 ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchController.isGestureEnabled) {
            // 手势关闭：交给系统默认触摸行为，由点击监听器处理单击
            return super.onTouchEvent(event)
        }
        return touchController.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 释放计时任务与动画：detach 后不应再回调修改可见性，也不应持有已移除的 View
        timeoutController.release()
        touchController.cancelRunningAnimation()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        // 横竖屏使用不同的宽度配置
        applyContainerWidth(currentStyle)
    }

    // --- 内部逻辑：样式与布局 ---

    /** 构造期间的初始样式应用：不播放过渡，也不主动 requestLayout。 */
    private fun applyInitialStyle(style: LyricStyle) {
        currentStyle = style
        logoView.applyStyle(style)
        textView.applyStyle(style)
        applyContainerLayout(style)
    }

    /**
     * 按样式中的 gravity 重新摆放 Logo。
     *
     * gravity 未变化时直接跳过：remove/addView 会触发过渡与重新布局，成本较高。
     */
    private fun applyLogoGravity() {
        val gravity = currentStyle.packageStyle.logo.gravity
        if (gravity == lastLogoGravity) return

        lastLogoGravity = gravity

        if (contains(logoView)) removeView(logoView)
        val textIndex = indexOfChild(textView).coerceAtLeast(0)

        // GRAVITY_END 时 Logo 插到文本之后，其余情况（含 START）插到文本之前
        val insertIndex = if (gravity == LogoStyle.GRAVITY_END) textIndex + 1 else textIndex
        addView(logoView, insertIndex)
    }

    /** 应用容器宽高、外边距、内边距与文本权重。 */
    private fun applyContainerLayout(style: LyricStyle) {
        val basic = style.basicStyle
        val margins = basic.margins
        val paddings = basic.paddings

        requireLayoutParams().apply {
            width = resolveContainerWidth(basic)
            leftMargin = margins.left.dp
            topMargin = margins.top.dp
            rightMargin = margins.right.dp
            bottomMargin = margins.bottom.dp
        }
        applyTextWeight()

        updatePadding(
            paddings.left.dp,
            paddings.top.dp,
            paddings.right.dp,
            paddings.bottom.dp
        )
    }

    /** 仅更新容器宽度，用于横竖屏与胶囊模式切换。 */
    private fun applyContainerWidth(style: LyricStyle) {
        val width = resolveContainerWidth(style.basicStyle)
        requireLayoutParams().width = width
        requestLayout()
        Log.d(TAG, "applyContainerWidth: $width")
    }

    /** 计算容器宽度：横竖屏与 ColorOS 胶囊模式各自有独立配置。 */
    private fun resolveContainerWidth(basicStyle: BasicStyle): Int =
        basicStyle.getAutoWidth(isLandscape(), isOplusCapsuleShowing).dp

    /** 让文本占据剩余宽度，Logo 保持自身尺寸。 */
    private fun applyTextWeight() {
        val lp = (textView.layoutParams as? LayoutParams)
            ?: LayoutParams(0, LayoutParams.WRAP_CONTENT)
        lp.width = 0
        lp.weight = 1f
        textView.layoutParams = lp
    }

    /** 取得或补齐本视图的 [LayoutParams]。 */
    private fun requireLayoutParams(): LayoutParams {
        val lp = layoutParams as? LayoutParams
            ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        if (layoutParams == null) layoutParams = lp
        return lp
    }

    /** 当前是否处于横屏。 */
    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // --- 内部逻辑：可见性与配色 ---

    /** 是否启用"进入动画"（由文本样式决定）。 */
    private val isEnterAnimEnabled get() = currentStyle.packageStyle.text.enableEnterAnim

    /**
     * 强制子视图重新执行一次着色。
     *
     * 先置为空白配色再恢复原值：仅当两侧视图都收到一次颜色变更通知时，
     * 才会重新计算 Tint 与高亮色，避免显隐切换后残留错误配色。
     */
    private fun forceRefreshStatusColors() {
        val current = currentStatusColor
        applyStatusColorToChildren(StatusColor())
        applyStatusColorToChildren(current)
    }

    private fun applyStatusColorToChildren(color: StatusColor) {
        logoView.updateStatusColor(color)
        textView.updateStatusColor(color)
    }

    // --- 内部逻辑：超时 ---

    /** 按最新样式与内容状态重置超时计时，并同步可见性。 */
    private fun refreshLyricTimeoutState() {
        timeoutController.refresh(currentStyle, hasLyricContent, currentLyric)
        updateVisibility()
    }

    // --- 内部逻辑：布局过渡 ---

    /**
     * 创建一次性布局过渡。
     *
     * 过渡结束即解绑自身并禁用 CHANGING 类型，避免影响后续无关布局。
     *
     * @param durationMs 自定义过渡时长；null 表示沿用 [LayoutTransitionX] 默认配置
     */
    private fun newOneShotTransition(durationMs: Long? = null): LayoutTransitionX =
        LayoutTransitionX().apply {
            if (durationMs != null) setDuration(durationMs)
            addTransitionListener(object : LayoutTransition.TransitionListener {

                override fun startTransition(
                    transition: LayoutTransition?, container: ViewGroup?,
                    view: View?, transitionType: Int
                ) = Unit

                override fun endTransition(
                    transition: LayoutTransition?, container: ViewGroup?,
                    view: View?, transitionType: Int
                ) {
                    disableTransitionType(LayoutTransition.CHANGING)
                    layoutTransition = null
                }
            })
        }

    private fun startStyleTransition() {
        styleTransition.enableTransitionType(LayoutTransition.CHANGING)
        layoutTransition = styleTransition
    }

    private fun startVisibilityTransition() {
        visibilityTransition.enableTransitionType(LayoutTransition.CHANGING)
        layoutTransition = visibilityTransition
    }
}
