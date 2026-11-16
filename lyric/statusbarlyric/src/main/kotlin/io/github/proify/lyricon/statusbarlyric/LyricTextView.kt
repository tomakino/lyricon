/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.widget.TextView
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.sp
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.view.AnimParams
import io.github.proify.lyricon.lyric.view.Highlight
import io.github.proify.lyricon.lyric.view.LyricPlayerView
import io.github.proify.lyricon.lyric.view.LyricViewStyle
import io.github.proify.lyricon.lyric.view.Marquee
import io.github.proify.lyricon.lyric.view.RichLyricLineView
import io.github.proify.lyricon.lyric.view.TextLook
import io.github.proify.lyricon.lyric.view.TitleSlot
import io.github.proify.lyricon.lyric.view.WordMotion
import java.io.File
import kotlin.math.min

/**
 * 状态栏歌词的文本视图。
 *
 * 在 [LyricPlayerView] 之上补齐两件事：
 * 1. 把 [LyricStyle]（用户配置模型）翻译为 [LyricViewStyle]（渲染参数）；
 * 2. 对外提供"当前是否有可见歌词行"与间奏回调，供 [StatusBarLyric] 决定容器显隐。
 *
 * 颜色遵循"自定义颜色优先，否则跟随状态栏配色"的规则，详见各 `resolve*Color` 方法。
 */
class LyricTextView(context: Context) : LyricPlayerView(context) {

    companion object {
        private const val VIEW_TAG: String = "lyricon:text_view"

        /** [TextStyle.fontWeight] 的取值上限，防止越界配置导致字体异常。 */
        private const val MAX_FONT_WEIGHT: Int = 1000

        /** 次要歌词（翻译/罗马音）相对主歌词的字号比例。 */
        private const val SECONDARY_TEXT_SIZE_RATIO = 0.76f

        /** 未配置字号且没有系统时钟视图可继承时的兜底字号（sp）。 */
        private const val FALLBACK_TEXT_SIZE_SP = 14f
    }

    /** 系统状态栏时钟视图，用于继承字号与字体；为空时使用默认值。 */
    var linkedTextView: TextView? = null

    /** 间奏状态回调，[StatusBarLyric] 用它驱动 Logo 的进度条绘制。 */
    var interludeListener: InterludeListener? = null

    /** 间奏状态监听。 */
    interface InterludeListener {
        /** 进入间奏，[duration] 为间奏总时长（毫秒）。 */
        fun enteringInterludeMode(duration: Long)

        /** 退出间奏。 */
        fun exitInterludeMode()
    }

    private var statusColor = StatusColor()
    private var lyricStyle: LyricStyle? = null

    init {
        tag = VIEW_TAG
    }

    override fun enteringInterludeMode(duration: Long) {
        super.enteringInterludeMode(duration)
        interludeListener?.enteringInterludeMode(duration)
    }

    override fun exitInterludeMode() {
        super.exitInterludeMode()
        interludeListener?.exitInterludeMode()
    }

    /**
     * 应用歌词样式：翻译渲染参数，并同步过渡配置与容器边距。
     *
     * 该方法不负责触发父容器的重新布局，布局由 [StatusBarLyric.updateStyle] 统一收口。
     */
    fun applyStyle(style: LyricStyle) {
        lyricStyle = style
        val textStyle = style.packageStyle.text

        setTransitionConfig(textStyle.transitionConfig)
        applyContainerLayout(textStyle)
        setStyle(buildViewStyle(style, textStyle))
    }

    /** 整体替换状态栏配色并重算全部颜色。 */
    fun updateStatusColor(color: StatusColor) {
        statusColor = color
        refreshVisualColors()
    }

    /**
     * 当前是否存在可见的歌词行。
     *
     * 容器显隐依赖此结果：所有行都被隐藏（例如翻译与罗马音均关闭且主行未开始）时
     * 应整体收起，避免残留空白区域。
     */
    fun hasVisibleLyricContent(): Boolean {
        if (isEmpty()) return false

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (!child.isVisible) continue

            // 双行视图中只要主行或次行之一可见即视为有内容
            val isContentVisible = if (child is RichLyricLineView) {
                child.main.isVisible || child.secondary.isVisible
            } else {
                true
            }
            if (isContentVisible) return true
        }
        return false
    }

    // --- 内部逻辑：渲染参数 ---

    /** 把用户配置翻译为渲染参数。 */
    private fun buildViewStyle(style: LyricStyle, textStyle: TextStyle): LyricViewStyle {
        val typeface = resolveTypeface(textStyle)
        val fontSize = resolveTextSize(textStyle)

        return LyricViewStyle(
            primary = TextLook(
                color = resolvePrimaryColor(textStyle),
                size = fontSize,
                typeface = typeface,
                relativeProgress = textStyle.relativeProgress,
                relativeHighlight = textStyle.relativeProgressHighlight,
            ),
            secondary = TextLook(
                color = resolvePrimaryColor(textStyle),
                size = fontSize * SECONDARY_TEXT_SIZE_RATIO,
                typeface = typeface,
            ),
            highlight = Highlight(
                background = resolveBgColor(textStyle),
                foreground = resolveHighlightColor(textStyle),
            ),
            marquee = buildMarquee(textStyle),
            wordMotion = buildWordMotion(textStyle),
            gradient = textStyle.gradientProgressStyle,
            fadingEdge = textStyle.fadingEdgeLength.coerceAtLeast(0).dp,
            scaleMultiLine = textStyle.scaleInMultiLine,
            animation = AnimParams(
                enabled = style.packageStyle.anim.enable,
                presetId = style.packageStyle.anim.id,
                speed = style.packageStyle.anim.speed,
            ),
            placeholder = resolveTitleSlot(textStyle)
        )
    }

    /** 解析跑马灯参数。 */
    private fun buildMarquee(textStyle: TextStyle) = Marquee(
        speed = textStyle.marqueeSpeed,
        spacing = textStyle.marqueeGhostSpacing,
        initialDelay = textStyle.marqueeInitialDelay,
        loopDelay = textStyle.marqueeLoopDelay,
        // -1 表示无限循环，与 Marquee 的约定保持一致
        repeatCount = if (textStyle.marqueeRepeatUnlimited) -1 else textStyle.marqueeRepeatCount,
        stopAtEnd = textStyle.marqueeStopAtEnd,
    )

    /** 解析逐字动画参数。 */
    private fun buildWordMotion(textStyle: TextStyle) = WordMotion(
        enabled = textStyle.wordMotionEnabled,
        cjkLiftFactor = textStyle.wordMotionCjkLiftFactor,
        cjkWaveFactor = textStyle.wordMotionCjkWaveFactor,
        latinLiftFactor = textStyle.wordMotionLatinLiftFactor,
        latinWaveFactor = textStyle.wordMotionLatinWaveFactor,
    )

    /** 解析歌曲占位（歌名 / 歌名+歌手 / 不显示）。 */
    private fun resolveTitleSlot(textStyle: TextStyle): TitleSlot =
        when (textStyle.placeholderFormat ?: TextStyle.Defaults.PLACEHOLDER_FORMAT) {
            TextStyle.PlaceholderFormat.NONE -> TitleSlot.NONE
            TextStyle.PlaceholderFormat.NAME -> TitleSlot.NAME
            TextStyle.PlaceholderFormat.NAME_ARTIST -> TitleSlot.NAME_ARTIST
            else -> TitleSlot.NAME_ARTIST
        }

    /**
     * 解析歌词字号。
     *
     * 优先级：自定义字号 > 系统时钟字号 > 默认字号。
     */
    private fun resolveTextSize(textStyle: TextStyle): Float =
        if (textStyle.textSize > 0) {
            textStyle.textSize.sp
        } else {
            linkedTextView?.textSize ?: FALLBACK_TEXT_SIZE_SP.sp
        }

    /** 应用文本容器的外边距与内边距。 */
    private fun applyContainerLayout(textStyle: TextStyle) {
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

    // --- 内部逻辑：颜色 ---

    /** 按最新状态栏配色重算主色、背景色与高亮色。 */
    private fun refreshVisualColors() {
        val textStyle = lyricStyle?.packageStyle?.text ?: return
        updateColor(
            primary = resolvePrimaryColor(textStyle),
            background = resolveBgColor(textStyle),
            highlight = resolveHighlightColor(textStyle)
        )
    }

    /** 主色：自定义普通色优先，否则使用状态栏取色。 */
    private fun resolvePrimaryColor(textStyle: TextStyle): IntArray {
        val customColor = textStyle.color(statusColor.isLightMode)
        return if (textStyle.enableCustomTextColor && customColor?.normal?.isNotEmpty() == true) {
            customColor.normal
        } else {
            statusColor.color
        }
    }

    /** 高亮背景色：自定义背景色优先，否则使用状态栏半透明色。 */
    private fun resolveBgColor(textStyle: TextStyle): IntArray {
        val customColor = textStyle.color(statusColor.isLightMode)
        return if (textStyle.enableCustomTextColor && customColor?.background?.isNotEmpty() == true) {
            customColor.background
        } else {
            statusColor.translucentColor
        }
    }

    /** 高亮前景色：自定义高亮色优先，否则使用状态栏取色。 */
    private fun resolveHighlightColor(textStyle: TextStyle): IntArray {
        val customColor = textStyle.color(statusColor.isLightMode)
        return if (textStyle.enableCustomTextColor && customColor?.highlight?.isNotEmpty() == true) {
            customColor.highlight
        } else {
            statusColor.color
        }
    }

    /**
     * 解析最终字体。
     *
     * 基础字体优先级：用户指定字体文件 > 系统时钟字体 > 系统默认字体。
     * 字重处理分两档：API 28 及以上使用可变字重，以下回退到粗体/斜体标记。
     */
    private fun resolveTypeface(textStyle: TextStyle): Typeface {
        val baseTypeface = textStyle.typeFace
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                val file = File(path)
                // 用户可能已删除字体文件：读取失败时静默回退，不影响歌词显示
                if (file.exists()) {
                    runCatching { Typeface.createFromFile(file) }.getOrNull()
                } else {
                    null
                }
            }
            ?: linkedTextView?.typeface
            ?: Typeface.DEFAULT

        if (textStyle.fontWeight > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(
                baseTypeface,
                min(MAX_FONT_WEIGHT, textStyle.fontWeight),
                textStyle.typeFaceItalic
            )
        }

        val styleFlag = when {
            textStyle.typeFaceBold && textStyle.typeFaceItalic -> Typeface.BOLD_ITALIC
            textStyle.typeFaceBold -> Typeface.BOLD
            textStyle.typeFaceItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(baseTypeface, styleFlag)
    }
}
