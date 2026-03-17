/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.setColorAlpha
import io.github.proify.android.extensions.toBitmap
import io.github.proify.lyricon.common.util.CoverThemeColorExtractor
import io.github.proify.lyricon.common.util.CoverThemeGradientExtractor
import io.github.proify.lyricon.common.util.ResourceMapper
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.VisibilityRule
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.xposed.systemui.util.ClockColorMonitor
import io.github.proify.lyricon.xposed.systemui.util.OnColorChangeListener
import io.github.proify.lyricon.xposed.systemui.util.ViewVisibilityController
import java.io.File
import kotlin.math.max

/**
 * 状态栏歌词视图控制器：负责歌词视图的注入、位置锚定及显隐逻辑
 */
@SuppressLint("DiscouragedApi")
class StatusBarViewController(
    val statusBarView: ViewGroup,
    var currentLyricStyle: LyricStyle
) : ScreenStateMonitor.ScreenStateListener {

    val context: Context = statusBarView.context.applicationContext
    val visibilityController = ViewVisibilityController(statusBarView)
    val lyricView: StatusBarLyric by lazy { createLyricView(currentLyricStyle) }

    private val clockId: Int by lazy { ResourceMapper.getIdByName(context, "clock") }
    private var lastAnchor = ""
    private var lastInsertionOrder = -1
    private var internalRemoveLyricViewFlag = false
    private var lastHighlightView: View? = null

    private var colorMonitorView: View? = null
    private var coverThemeColors: CoverThemeColorExtractor.ThemeColors? = null
    private var coverThemeGradientColors: CoverThemeGradientExtractor.ThemeGradientColors? = null
    private var lastClockColor: Int? = null
    private var isClockAutoHiddenByDynamicWidth = false
    private var originalClockVisibilityBeforeDynamicHide: Int? = null

    // --- 生命周期与初始化 ---
    fun onCreate() {
        statusBarView.addOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.addOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.addListener(this)


        val onColorChangeListener = object : OnColorChangeListener {
            override fun onColorChanged(color: Int, darkIntensity: Float) {
                lyricView.apply {
                    lastClockColor = color
                    currentStatusColor.darkIntensity = darkIntensity
                    if (shouldUseCoverTextColor() && applyCoverStatusColor()) return
                    setStatusBarColor(currentStatusColor.apply {
                        this.color = color
                        this.darkIntensity = darkIntensity
                        translucentColor = color.setColorAlpha(0.75f)
                        gradientColors = null
                        translucentGradientColors = null
                    })
                }
            }
        }

        ClockColorMonitor.hook()

        colorMonitorView = getClockView()?.also {
            ClockColorMonitor.setListener(it, onColorChangeListener)
        }

        statusBarView.doOnAttach { checkLyricViewExists() }
        YLog.info("Lyric view created for $statusBarView")
    }

    fun onDestroy() {
        statusBarView.removeOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.removeOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.removeListener(this)
        colorMonitorView?.let { ClockColorMonitor.setListener(it, null) }
        restoreClockVisibilityFromDynamicWidth()
        LyricViewController.notifyLyricVisibilityChanged()
        YLog.info("Lyric view destroyed for $statusBarView")
    }

    // --- 核心业务逻辑 ---

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
            YLog.info("Lyric location changed: ${basicStyle.anchor}, order ${basicStyle.insertionOrder}")
            updateLocation(basicStyle)
        } else {
            //YLog.info("Lyric location unchanged: $lastAnchor")
        }
        lyricView.updateStyle(lyricStyle)
        if (shouldUseCoverTextColor()) {
            updateCoverThemeColors(lyricView.logoView.coverFile)
        } else {
            coverThemeColors = null
            coverThemeGradientColors = null
            lastClockColor?.let { color ->
                lyricView.setStatusBarColor(lyricView.currentStatusColor.apply {
                    this.color = color
                    translucentColor = color.setColorAlpha(0.75f)
                    gradientColors = null
                    translucentGradientColors = null
                })
            }
        }
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    fun updateCoverThemeColors(coverFile: File?) {
        if (!shouldUseCoverTextColor()) {
            coverThemeColors = null
            coverThemeGradientColors = null
            return
        }

        val bitmap = coverFile?.toBitmap(64, 64) ?: return
        try {
            coverThemeColors = CoverThemeColorExtractor.extract(bitmap)
            coverThemeGradientColors = if (shouldUseCoverTextGradient()) {
                CoverThemeGradientExtractor.extract(bitmap)
            } else {
                null
            }
        } finally {
            bitmap.recycle()
        }
        applyCoverStatusColor()
    }

    private fun shouldUseCoverTextColor(): Boolean {
        val textStyle = currentLyricStyle.packageStyle.text
        return textStyle.enableExtractCoverTextColor && !textStyle.enableCustomTextColor
    }

    private fun shouldUseCoverTextGradient(): Boolean {
        val textStyle = currentLyricStyle.packageStyle.text
        return shouldUseCoverTextColor() && textStyle.enableExtractCoverTextGradient
    }

    private fun applyCoverStatusColor(): Boolean {
        val colors = coverThemeColors ?: return false
        val statusColor = lyricView.currentStatusColor
        val isLightMode = statusColor.lightMode

        val gradient = coverThemeGradientColors
            ?.takeIf { shouldUseCoverTextGradient() }
            ?.let { if (isLightMode) it.lightModeColors else it.darkModeColors }
            ?.takeIf { it.isNotEmpty() }

        val color = if (isLightMode) colors.lightModeColor else colors.darkModeColor

        lyricView.setStatusBarColor(statusColor.apply {
            if (gradient != null && gradient.size >= 2) {
                this.color = gradient.first()
                translucentColor = this.color.setColorAlpha(0.75f)
                gradientColors = gradient
                translucentGradientColors = gradient.map { it.setColorAlpha(0.75f) }.toIntArray()
            } else {
                this.color = color
                translucentColor = color.setColorAlpha(0.75f)
                gradientColors = null
                translucentGradientColors = null
            }
        })
        return true
    }

    /**
     * 处理视图注入逻辑：根据 BasicStyle 寻找锚点并插入歌词视图
     */
    private fun updateLocation(baseStyle: BasicStyle) {
        val anchor = baseStyle.anchor
        val anchorId = context.resources.getIdentifier(anchor, "id", context.packageName)
        val anchorView = statusBarView.findViewById<View>(anchorId) ?: return run {
            YLog.error("Lyric anchor view $anchor not found")
        }

        val anchorParent = anchorView.parent as? ViewGroup ?: return run {
            YLog.error("Lyric anchor parent not found")
        }

        // 标记内部移除，避免触发冗余的 detach 逻辑
        internalRemoveLyricViewFlag = true

        (lyricView.parent as? ViewGroup)?.removeView(lyricView)

        val anchorIndex = anchorParent.indexOfChild(anchorView)
        val lp = lyricView.layoutParams ?: ViewGroup.LayoutParams(
            if (baseStyle.dynamicWidthEnabled) ViewGroup.LayoutParams.WRAP_CONTENT else baseStyle.width.dp,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 执行插入：在前或在后
        val targetIndex =
            if (baseStyle.insertionOrder == BasicStyle.INSERTION_ORDER_AFTER) anchorIndex + 1 else anchorIndex
        anchorParent.addView(lyricView, targetIndex, lp)

        lyricView.updateVisibility()
        lastAnchor = anchor
        lastInsertionOrder = baseStyle.insertionOrder
        internalRemoveLyricViewFlag = false

        YLog.info("Lyric injected: anchor $anchor, index $targetIndex")
    }

    fun checkLyricViewExists() {
        if (lyricView.isAttachedToWindow) return
        lastAnchor = ""
        lastInsertionOrder = -1
        updateLyricStyle(currentLyricStyle)
    }

    // --- 辅助方法 ---

    private fun getClockView(): View? = statusBarView.findViewById(clockId)

    private fun hasManualClockHideRule(rules: List<VisibilityRule>?): Boolean {
        if (rules.isNullOrEmpty()) return false
        return rules.any { it.id == "clock" && it.mode == VisibilityRule.MODE_HIDE_WHEN_PLAYING }
    }

    private fun hideClockForDynamicWidth() {
        val clockView = getClockView() ?: return
        if (isClockAutoHiddenByDynamicWidth) return
        originalClockVisibilityBeforeDynamicHide = clockView.visibility
        clockView.visibility = View.GONE
        isClockAutoHiddenByDynamicWidth = true
    }

    private fun restoreClockVisibilityFromDynamicWidth() {
        val clockView = getClockView()
        if (clockView == null) {
            isClockAutoHiddenByDynamicWidth = false
            originalClockVisibilityBeforeDynamicHide = null
            return
        }
        if (isClockAutoHiddenByDynamicWidth) {
            clockView.visibility = originalClockVisibilityBeforeDynamicHide ?: View.VISIBLE
            originalClockVisibilityBeforeDynamicHide = null
            isClockAutoHiddenByDynamicWidth = false
        }
    }

    private fun updateDynamicWidthClockVisibility() {
        val basicStyle = currentLyricStyle.basicStyle
        if (hasManualClockHideRule(basicStyle.visibilityRules)) {
            restoreClockVisibilityFromDynamicWidth()
            return
        }

        if (!basicStyle.dynamicWidthEnabled
            || !basicStyle.dynamicWidthAutoHideClock
            || basicStyle.anchor != "clock"
            || !LyricViewController.isPlaying
            || lyricView.visibility != View.VISIBLE
        ) {
            restoreClockVisibilityFromDynamicWidth()
            return
        }

        val maxWidthPx = basicStyle.width.dp
        if (maxWidthPx <= 0) {
            restoreClockVisibilityFromDynamicWidth()
            return
        }

        var contentWidth = lyricView.width
        contentWidth = max(contentWidth, lyricView.measuredWidth)
        contentWidth = max(contentWidth, lyricView.textView.width)
        contentWidth = max(contentWidth, lyricView.textView.measuredWidth)

        if (contentWidth > maxWidthPx) {
            hideClockForDynamicWidth()
        } else {
            restoreClockVisibilityFromDynamicWidth()
        }
    }

    private fun computeShouldApplyPlayingRules(): Boolean {
        return LyricViewController.isPlaying && when {
            lyricView.isDisabledVisible -> !lyricView.isHideOnLockScreen()
            lyricView.isVisible -> true
            else -> false
        }
    }

    private fun applyVisibilityRulesNow() {
        visibilityController.applyVisibilityRules(
            rules = currentLyricStyle.basicStyle.visibilityRules,
            isPlaying = computeShouldApplyPlayingRules()
        )
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    private fun createLyricView(style: LyricStyle) =
        StatusBarLyric(context, style, getClockView() as? TextView)

    fun highlightView(idName: String?) {
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
        } ?: YLog.error("Highlight target $idName not found")
    }

    // --- 监听器实现 ---

    private val onGlobalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            applyVisibilityRulesNow()
        }
    }

    private val lyricAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            YLog.info("LyricView attached")
        }

        override fun onViewDetachedFromWindow(v: View) {
            YLog.info("LyricView detached")
            if (!internalRemoveLyricViewFlag) {
                checkLyricViewExists()
            } else {
                YLog.info("LyricView detached by internal flag")
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
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    override fun onScreenOff() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = true
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    override fun onScreenUnlocked() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = false
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    fun onDisableStateChanged(shouldHide: Boolean) {
        lyricView.isDisabledVisible = shouldHide
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    override fun equals(other: Any?): Boolean =
        (this === other) || (other is StatusBarViewController && statusBarView === other.statusBarView)

    override fun hashCode(): Int = 31 * 17 + statusBarView.hashCode()
}
