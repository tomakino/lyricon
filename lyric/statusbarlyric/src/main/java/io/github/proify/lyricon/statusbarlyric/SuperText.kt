/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.sp
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.view.DefaultMarqueeConfig
import io.github.proify.lyricon.lyric.view.LyricPlayerView
import io.github.proify.lyricon.lyric.view.RichLyricLineView
import java.io.File
import kotlin.math.min

/**
 * 高级歌词文本渲染控件，支持逐字染色、跑马灯及间奏监听。
 *
 */
class SuperText(context: Context) : LyricPlayerView(context) {

    @Suppress("unused")
    companion object {
        const val VIEW_TAG: String = "lyricon:text_view"
        private const val TAG = "SuperText"
        private const val DEBUG = false
        private const val MAX_FONT_WEIGHT: Int = 1000
        private val RAINBOW_COLORS = intArrayOf(
            Color.parseColor("#FF4D4F"),
            Color.parseColor("#FF9F43"),
            Color.parseColor("#FFD93D"),
            Color.parseColor("#2ED573"),
            Color.parseColor("#1E90FF"),
            Color.parseColor("#5352ED"),
            Color.parseColor("#A55EEA")
        )
    }

    /**
     * 关联的系统 TextView，用于同步默认的字体、字号等样式。
     */
    var linkedTextView: TextView? = null

    /**
     * 歌词事件监听器
     */
    var eventListener: EventListener? = null

    private var currentStatusColor = StatusColor()
    private var currentLyricStyle: LyricStyle? = null

    /**
     * 事件监听接口
     */
    interface EventListener {
        /** 进入间奏模式 */
        fun enteringInterludeMode(duration: Long)

        /** 退出间奏模式 */
        fun exitInterludeMode()
    }

    init {
        tag = VIEW_TAG
        //setBackgroundColor(Color.CYAN)
    }

    // --- 生命周期/重写方法 ---

    override fun enteringInterludeMode(duration: Long) {
        super.enteringInterludeMode(duration)
        eventListener?.enteringInterludeMode(duration)
    }

    override fun exitInterludeMode() {
        super.exitInterludeMode()
        eventListener?.exitInterludeMode()
    }

    // --- 公开 API ---

    /**
     * 应用全量歌词样式。
     *
     * @param style 歌词样式配置
     */
    fun applyStyle(style: LyricStyle) {
        this.currentLyricStyle = style
        val textStyle = style.packageStyle.text

        // 应用过渡配置
        setTransitionConfig(textStyle.transitionConfig)

        // 更新容器布局（Margin/Padding）
        updateContainerLayout(textStyle)

        // 配置核心样式
        val config = getStyle().apply {
            val resolvedTypeface = resolveTypeface(textStyle)
            val fontSize = if (textStyle.textSize > 0) {
                textStyle.textSize.sp
            } else {
                linkedTextView?.textSize ?: 14f.sp
            }

            primary.apply {
                this.textColor = resolvePrimaryColor(textStyle)
                this.textSize = fontSize
                this.typeface = resolvedTypeface
                enableRelativeProgress = textStyle.relativeProgress
                enableRelativeProgressHighlight = textStyle.relativeProgressHighlight
            }

            secondary.apply {
                this.textColor = primary.textColor
                this.textSize = fontSize * 0.76f
                this.typeface = resolvedTypeface
            }

            this.marquee = buildMarqueeConfig(textStyle)
            this.syllable.apply {
                backgroundColor = resolveBgColor(textStyle)
                highlightColor = resolveHighlightColor(textStyle)
                // 上浮效果暂时移除，仅保留发光。
                enableSustainLift = false
                val colorModeEnabled = textStyle.enableCustomTextColor
                        || textStyle.enableExtractCoverTextColor
                        || textStyle.enableExtractCoverTextGradient
                        || textStyle.enableRainbowTextColor
                // 取色模式下发光禁用，避免叠加影响可读性。
                enableSustainGlow = textStyle.sustainGlowEnabled && !colorModeEnabled
            }

            this.gradientProgressStyle = textStyle.gradientProgressStyle
            scaleInMultiLine = textStyle.scaleInMultiLine
            fadingEdgeLength = textStyle.fadingEdgeLength.coerceAtLeast(0).dp
            placeholderFormat = textStyle.placeholderFormat ?: TextStyle.Defaults.PLACEHOLDER_FORMAT
            enableAnim = style.packageStyle.anim.enable
            animId = style.packageStyle.anim.id
        }

        setStyle(config)
    }

    /**
     * 更新状态栏颜色环境，同步刷新文本及染色颜色。
     */
    fun setStatusBarColor(color: StatusColor) {
        this.currentStatusColor = color
        refreshVisualColors()
    }

    // --- 内部逻辑私有方法 ---

    /**
     * 仅刷新颜色相关的配置，不触发布局变更。
     */
    private fun refreshVisualColors() {
        val textStyle = currentLyricStyle?.packageStyle?.text ?: return

        updateColor(
            primary = resolvePrimaryColor(textStyle),
            background = resolveBgColor(textStyle),
            highlight = resolveHighlightColor(textStyle)
        )
    }

    private fun updateContainerLayout(textStyle: TextStyle) {
        val margins = textStyle.margins
        val paddings = textStyle.paddings

        val params = (layoutParams as? MarginLayoutParams)
            ?: MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        params.setMargins(
            margins.left.dp,
            margins.top.dp,
            margins.right.dp,
            margins.bottom.dp
        )
        layoutParams = params

        updatePadding(
            paddings.left.dp,
            paddings.top.dp,
            paddings.right.dp,
            paddings.bottom.dp
        )
    }

    private fun buildMarqueeConfig(textStyle: TextStyle) = DefaultMarqueeConfig().apply {
        scrollSpeed = textStyle.marqueeSpeed
        ghostSpacing = textStyle.marqueeGhostSpacing
        initialDelay = textStyle.marqueeInitialDelay
        loopDelay = textStyle.marqueeLoopDelay
        repeatCount = if (textStyle.marqueeRepeatUnlimited) -1 else textStyle.marqueeRepeatCount
        stopAtEnd = textStyle.marqueeStopAtEnd
    }

    private fun resolvePrimaryColor(textStyle: TextStyle): IntArray {
        if (textStyle.enableRainbowTextColor) {
            return RAINBOW_COLORS
        }
        val customColor = textStyle.color(currentStatusColor.lightMode)
        return if (textStyle.enableCustomTextColor && customColor?.normal?.isNotEmpty() == true) {
            customColor.normal
        } else {
            currentStatusColor.gradientColors?.takeIf { it.isNotEmpty() } ?: intArrayOf(
                currentStatusColor.color
            )
        }
    }

    private fun resolveBgColor(textStyle: TextStyle): IntArray {
        if (textStyle.enableRainbowTextColor) {
            return RAINBOW_COLORS.map { it.withAlpha(0.45f) }.toIntArray()
        }
        val customColor = textStyle.color(currentStatusColor.lightMode)
        return if (textStyle.enableCustomTextColor && customColor?.background?.isNotEmpty() == true) {
            customColor.background
        } else {
            currentStatusColor.translucentGradientColors?.takeIf { it.isNotEmpty() } ?: intArrayOf(
                currentStatusColor.translucentColor
            )
        }
    }

    private fun resolveHighlightColor(textStyle: TextStyle): IntArray {
        if (textStyle.enableRainbowTextColor) {
            return RAINBOW_COLORS
        }
        val customColor = textStyle.color(currentStatusColor.lightMode)
        return if (textStyle.enableCustomTextColor && customColor?.highlight?.isNotEmpty() == true) {
            customColor.highlight
        } else {
            currentStatusColor.gradientColors?.takeIf { it.isNotEmpty() } ?: intArrayOf(
                currentStatusColor.color
            )
        }
    }

    private fun resolveTypeface(textStyle: TextStyle): Typeface {
        val baseTypeface = textStyle.typeFace?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.exists()) {
                runCatching { Typeface.createFromFile(file) }.getOrNull()
            } else null
        } ?: linkedTextView?.typeface ?: Typeface.DEFAULT

        return if (textStyle.fontWeight > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(
                baseTypeface,
                min(MAX_FONT_WEIGHT, textStyle.fontWeight),
                textStyle.typeFaceItalic
            )
        } else {
            val styleFlag = when {
                textStyle.typeFaceBold && textStyle.typeFaceItalic -> Typeface.BOLD_ITALIC
                textStyle.typeFaceBold -> Typeface.BOLD
                textStyle.typeFaceItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(baseTypeface, styleFlag)
        }
    }

    private fun Int.withAlpha(ratio: Float): Int {
        val alpha = (ratio.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
    }

    fun shouldShow(): Boolean {
        if (isEmpty()) return false

        var visibleCount = 0
        forEach {
            if (it.isVisible) {
                if (it is RichLyricLineView) {
                    if (it.main.isVisible || it.secondary.isVisible) visibleCount++
                } else {
                    visibleCount++
                }
            }
        }
        return visibleCount > 0
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
}
