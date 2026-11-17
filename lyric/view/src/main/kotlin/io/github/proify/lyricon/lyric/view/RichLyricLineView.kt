/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.graphics.withScale
import androidx.core.view.forEach
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.view.line.LyricLineView

@SuppressLint("ViewConstructor")
class RichLyricLineView(
    context: Context,
    displayTranslation: Boolean = true,
    var enableRelativeProgress: Boolean = false,
    var enableRelativeProgressHighlight: Boolean = false,
    displayRoma: Boolean = true
) : LinearLayout(context), UpdatableColor {

    val main = LyricLineView(context)
    val secondary = LyricLineView(context).apply {
        visibleIfChanged = false
        // 次行初始不可见，但背景歌词必须按自己的词时间触发；GONE 时仍需推进时间轴。
        runWhenHidden = true
    }

    var alwaysShowSecondary = false

    /** 是否暂停 main/secondary 的帧推进。 */
    var animationsPaused: Boolean = false
        private set

    /** 暂停或恢复两行的帧推进。 */
    fun setAnimationsPaused(paused: Boolean) {
        if (animationsPaused == paused) return
        animationsPaused = paused
        main.setAnimationPaused(paused)
        secondary.setAnimationPaused(paused)
    }

    /** 当前行的视图层时间轴单元。 */
    internal var timedLine: TimedLine? = null

    /** 当前视觉行是否为歌名标题行。 */
    var isTitleLine: Boolean = false
        private set

    var renderScale = 1.0f
        private set

    /**
     * 是否显示翻译行。
     *
     * 开关变化时必须同步到 [assembler] 并立即重装配次行，否则当前行会继续使用旧标志，
     * 直到歌词换行才生效。
     */
    var displayTranslation: Boolean = displayTranslation
        set(value) {
            if (field == value) return
            field = value
            applyDisplayFlags()
        }

    /** 是否显示罗马音行；变化时与 [displayTranslation] 一样立即生效。 */
    var displayRoma: Boolean = displayRoma
        set(value) {
            if (field == value) return
            field = value
            applyDisplayFlags()
        }

    /** 上一次已应用到 [secondary] 的次行内容，用于跳过无变化的重复装配。 */
    private var lastSecondaryLine: LyricLine? = null

    private val assembler = LyricLineAssembler(
        displayTranslation, displayRoma,
        enableRelativeProgress, enableRelativeProgressHighlight
    )

    private var animationTransition = false
    private var pendingLine: IRichLyricLine? = null
    private var pendingPosition: Long? = null
    private var lastAppliedPosition: Long? = null
    private var pendingIsTitle = false
    private var appliedTitle = false
    private var requestMarquee = false

    var rawLine: IRichLyricLine? = null

    var line: IRichLyricLine?
        get() = rawLine
        set(value) {
            rawLine = value
            requestMarquee = false
            if (animationTransition) {
                pendingLine = value
            } else {
                refreshLines()
            }
        }

    /** 设置行内容及标题行标记；动画期间与内容一起延迟到切换点应用。 */
    fun setLine(line: IRichLyricLine?, isTitle: Boolean) {
        pendingIsTitle = isTitle
        this.line = line
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        addView(main, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(secondary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        updateLayoutTransitionX()
    }

    fun reset() {
        pendingIsTitle = false
        animationTransition = false
        lastAppliedPosition = null
        line = null
        renderScale = 1.0f
        pendingLine = null
        pendingPosition = null
        alwaysShowSecondary = false
        refreshLines()
    }

    fun beginAnimationTransition() {
        animationTransition = true
    }

    fun endAnimationTransition() {
        animationTransition = false
        lastAppliedPosition = null
        if (pendingLine != null) {
            refreshLines()
            pendingPosition?.let { setPosition(it) }
        }
        pendingLine = null
        pendingPosition = null
    }

    fun setTransitionConfig(config: String?) {
        updateLayoutTransitionX(config)
    }

    fun notifyLineChanged() = refreshSecondary()

    fun seekTo(position: Long) {
        if (animationTransition) {
            pendingPosition = position; return
        }
        lastAppliedPosition = position
        main.seekTo(position)
        secondary.seekTo(position)
    }

    fun setPosition(position: Long) {
        if (!animationTransition && position == lastAppliedPosition) return
        lastAppliedPosition = position
        if (animationTransition) {
            pendingPosition = position; return
        }
        main.updatePosition(position)
        secondary.updatePosition(position)
    }

    fun requestStartMarquee() {
        requestMarquee = true
        main.requestScroll()
        secondary.requestScroll()
    }

    fun resetAnimationState() {
        animationTransition = false
        pendingLine = null
        pendingPosition = null
    }

    fun setStyle(style: LyricViewStyle) {
        assembler.updateFlags(
            displayTranslation, displayRoma,
            style.primary.relativeProgress, style.primary.relativeHighlight
        )
        enableRelativeProgress = style.primary.relativeProgress
        enableRelativeProgressHighlight = style.primary.relativeHighlight

        applyLineStyle(
            main,
            style.primary,
            style.highlight,
            style.marquee,
            style.gradient,
            style.fadingEdge,
            style.wordMotion
        )
        applyLineStyle(
            secondary,
            style.secondary,
            style.highlight,
            style.marquee,
            style.gradient,
            style.fadingEdge,
            style.wordMotion
        )
    }

    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        forEach { if (it is UpdatableColor) it.updateColor(primary, background, highlight) }
    }

    fun setMainLyricPlayListener(listener: LyricPlayListener?) {
        main.playListener = listener
    }

    fun setSecondaryLyricPlayListener(listener: LyricPlayListener?) {
        secondary.playListener = listener
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        if (renderScale != 1.0f && renderScale > 0) {
            val origW = MeasureSpec.getSize(wSpec)
            val mode = MeasureSpec.getMode(wSpec)
            val compW = (origW / renderScale).toInt()
            super.onMeasure(MeasureSpec.makeMeasureSpec(compW, mode), hSpec)
            setMeasuredDimension(origW, measuredHeight)
        } else {
            super.onMeasure(wSpec, hSpec)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (renderScale != 1.0f) {
            canvas.withScale(renderScale, renderScale, 0f, height / 2f) {
                super.dispatchDraw(this)
            }
        } else {
            super.dispatchDraw(canvas)
        }
    }

    fun setRenderScale(scale: Float) {
        if (renderScale != scale) {
            renderScale = scale
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    private var oldLine: IRichLyricLine? = null

    private fun refreshLines() {
        if (oldLine === line && appliedTitle == pendingIsTitle) return
        oldLine = line
        appliedTitle = pendingIsTitle
        isTitleLine = pendingIsTitle
        lastAppliedPosition = null

        val mainResult = assembler.buildMain(line, isTitleLine)
        main.setLyric(mainResult.line)
        main.isScrollOnly = mainResult.isScrollOnly

        val secResult = assembler.buildSecondary(line)
        alwaysShowSecondary = secResult.alwaysShow
        secondary.visibleIfChanged = secResult.alwaysShow
        lastSecondaryLine = secResult.line
        secondary.setLyric(secResult.line)
        secondary.isScrollOnly = secResult.isScrollOnly

        if (requestMarquee) requestStartMarquee()
    }

    /** 把最新的显示开关同步给装配器，并立即重装配次行。 */
    private fun applyDisplayFlags() {
        assembler.updateFlags(
            displayTranslation, displayRoma,
            enableRelativeProgress, enableRelativeProgressHighlight
        )
        refreshSecondary()
    }

    /**
     * 只重装配翻译 / 罗马音次行。
     *
     * 主行内容与翻译开关无关；重建主行会调用 [LyricLineView.setLyric] 并重置逐字进度，
     * 因此开关变化时只刷新次行。
     */
    private fun refreshSecondary() {
        val secResult = assembler.buildSecondary(line)
        alwaysShowSecondary = secResult.alwaysShow
        secondary.visibleIfChanged = secResult.alwaysShow
        secondary.isScrollOnly = secResult.isScrollOnly
        if (lastSecondaryLine != secResult.line) {
            lastSecondaryLine = secResult.line
            lastAppliedPosition = null
            secondary.setLyric(secResult.line)
            if (requestMarquee) requestStartMarquee()
        }
    }

    private fun applyLineStyle(
        view: LyricLineView, text: TextLook, highlight: Highlight,
        marquee: Marquee, gradient: Boolean, fadingEdge: Int, wordMotion: WordMotion
    ) {
        view.wordMotion = wordMotion
        view.configureWith(text, highlight, marquee, gradient, fadingEdge)
    }

    private fun updateLayoutTransitionX(config: String? = LayoutTransitionX.TRANSITION_CONFIG_SMOOTH) {
        layoutTransition = LayoutTransitionX(config).apply { setAnimateParentHierarchy(true) }
    }
}
