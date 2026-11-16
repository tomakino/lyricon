/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.content.Context
import android.graphics.Canvas
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnAttach
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.view.Highlight
import io.github.proify.lyricon.lyric.view.LyricPlayListener
import io.github.proify.lyricon.lyric.view.Marquee
import io.github.proify.lyricon.lyric.view.TextLook
import io.github.proify.lyricon.lyric.view.UpdatableColor
import io.github.proify.lyricon.lyric.view.WordMotion
import io.github.proify.lyricon.lyric.view.dp
import io.github.proify.lyricon.lyric.view.line.effect.EmphasizeGlowEffect
import io.github.proify.lyricon.lyric.view.line.effect.LyricEffectEngine
import io.github.proify.lyricon.lyric.view.line.effect.LyricUnitEffect
import io.github.proify.lyricon.lyric.view.line.effect.WaveLiftEffect
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.createModel
import io.github.proify.lyricon.lyric.view.line.model.emptyLyricModel
import io.github.proify.lyricon.lyric.view.sp

open class LyricLineView(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), UpdatableColor {

    init {
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(10.dp)
    }

    val textPaint: TextPaint = TextPaintX().apply { textSize = 24f.sp }

    val model: LyricModel get() = _model
    private var _model: LyricModel = emptyLyricModel()

    val lineWidth: Float get() = _model.width

    val isPlainText: Boolean get() = _model.isPlainText
    val isWordSync: Boolean get() = !isPlainText
    val isOverflow: Boolean get() = lineWidth > measuredWidth
    val isPlaying: Boolean get() = activeRenderer.isPlaying
    val isFinished: Boolean get() = activeRenderer.isFinished
    val isStarted: Boolean get() = activeRenderer.isStarted

    var isScrollOnly: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            syncRenderer.isScrollOnly = value
            // 纯滚动行不预留动画高度，行高可能变化，需重新测量。
            requestLayout()
        }

    var playListener: LyricPlayListener? = null
        set(value) {
            field = value
            syncRenderer.playListener = value
        }

    var isWordCharMotionEnabled: Boolean
        get() = syncRenderer.isCharMotionEnabled
        set(value) {
            if (syncRenderer.isCharMotionEnabled == value) return
            syncRenderer.isCharMotionEnabled = value
            requestLayout()
            invalidate()
        }

    var wordMotion: WordMotion = WordMotion()
        set(value) {
            if (field == value) return
            field = value
            syncRenderer.isCharMotionEnabled = value.enabled
            waveEffect.cjkLiftFactor = value.cjkLiftFactor
            waveEffect.cjkWaveFactor = value.cjkWaveFactor
            waveEffect.latinLiftFactor = value.latinLiftFactor
            waveEffect.latinWaveFactor = value.latinWaveFactor
            requestLayout()
            invalidate()
        }

    /**
     * 当前特效链（默认为内置 [WaveLiftEffect] + 强调辉光 [EmphasizeGlowEffect]；
     * 可整体替换以实现自定义歌词特效）。
     */
    var effects: List<LyricUnitEffect>
        get() = effectEngine.effectList
        set(value) {
            effectEngine.setEffects(value)
            invalidate()
        }

    /** 追加一个特效（按注册顺序叠加到当前特效链之后）。 */
    fun addEffect(effect: LyricUnitEffect) {
        effectEngine.add(effect)
        invalidate()
    }

    /** 按名称移除特效（例如移除默认的 [WaveLiftEffect] 的 "wave_lift" 或 [EmphasizeGlowEffect] 的 "emphasize_glow"）。 */
    fun removeEffect(name: String) {
        effectEngine.remove(name)
        invalidate()
    }

    private val lineState = LineState()
    private val scrollRenderer = ScrollTextRenderer()

    /** 默认特效：与旧版逐字波峰动画逐值等价。 */
    private val waveEffect = WaveLiftEffect()

    /** 强调辉光特效：时长 >= 1s 的词逐字缩放 + 位移 + 白色辉光。 */
    private val emphasizeGlowEffect = EmphasizeGlowEffect()

    /** 特效引擎：按注册顺序叠加 [effects] 并对每个单元求变换。 */
    val effectEngine: LyricEffectEngine = LyricEffectEngine(listOf(waveEffect, emphasizeGlowEffect))

    private val syncRenderer = WordSyncRenderer(this)

    /** 配色统一入口：正文着色与彩虹渐变缓存。 */
    private val textStyle = LyricTextStyle()

    /** 是否暂停时间驱动的帧推进。 */
    private var animationPaused = false

    /** 帧调度：每帧步进当前渲染器，有变化时请求重绘。 */
    private val animator = FrameAnimator(
        onFrame = { deltaNanos ->
            activeRenderer.step(deltaNanos, _model, lineState, measuredWidth)
        },
        requestInvalidate = { postInvalidateOnAnimation() },
        shouldRun = { isAttachedToWindow && isShown && !animationPaused },
        post = { runnable -> post(runnable) },
    )

    private var activeRenderer: LineRenderer = scrollRenderer

    private var primaryColors = intArrayOf()
    private var backgroundColors = intArrayOf()
    private var highlightColors = intArrayOf()

    private var ghostSpacing: Float = 40f.dp
    private var scrollStarted = false
    private var scrollUnlocked = false

    val textSize: Float get() = textPaint.textSize

    /** 暂停或恢复帧推进；暂停不改变渲染器状态，恢复后从当前位置继续。 */
    fun setAnimationPaused(paused: Boolean) {
        if (animationPaused == paused) return
        animationPaused = paused
        syncAnimationState()
    }

    private fun syncAnimationState() {
        if (isAttachedToWindow && isShown && !animationPaused) {
            animator.startIfNeeded()
        } else {
            animator.stop()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncAnimationState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncAnimationState()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { syncAnimationState() }
    }

    fun setTextSize(size: Float) {
        val needsUpdate = textPaint.textSize != size || syncRenderer.bgPaint.textSize != size
        if (!needsUpdate) return
        textPaint.textSize = size
        syncRenderer.setTextSize(size)
        refreshSizes()
        syncRenderer.updateLayout(_model, lineState, measuredWidth, measuredHeight)
        // 字号影响测量高度（文本高度与动画预留），需重新测量。
        requestLayout()
        invalidate()
    }

    fun setLyric(rawLine: LyricLine?) {
        val line = if (rawLine?.text.isNullOrBlank()) null else rawLine

        reset()
        scrollUnlocked = false
        scrollStarted = false

        _model = line?.normalize()?.createModel() ?: emptyLyricModel()
        activeRenderer = if (_model.isPlainText) scrollRenderer else syncRenderer
        refreshSizes()
        updateColorsIfReady()
        // 普通文字与逐词同步行的动画高度预留不同，行高可能变化，需重新测量。
        requestLayout()
        invalidate()
    }

    fun configureWith(
        text: TextLook, highlight: Highlight, marquee: Marquee,
        gradient: Boolean, fadingEdge: Int
    ) {
        updateColor(text.color, highlight.background, highlight.foreground)
        setTextSize(text.size)
        textPaint.typeface = text.typeface
        syncRenderer.setTypeface(text.typeface)
        syncRenderer.isGradientEnabled = gradient

        scrollRenderer.apply {
            scrollSpeed = marquee.speed
            ghostSpacing = marquee.spacing
            initialDelayMs = marquee.initialDelay
            loopDelayMs = marquee.loopDelay
            repeatCount = marquee.repeatCount
            stopAtEnd = marquee.stopAtEnd
        }
        ghostSpacing = marquee.spacing

        if (fadingEdge <= 0) {
            setFadingEdgeLength(0)
            isHorizontalFadingEdgeEnabled = false
        } else {
            setFadingEdgeLength(fadingEdge)
            isHorizontalFadingEdgeEnabled = true
        }

        refreshSizes()
        animator.stop()
        animator.startIfNeeded()
        invalidate()
    }

    fun requestScroll() {
        doOnAttach {
            scrollUnlocked = true
            if (isPlainText) startScrolling()
        }
    }

    fun seekTo(posMs: Long) {
        if (isPlainText) {
            startScrolling()
        } else {
            activeRenderer.seek(_model, lineState, posMs, measuredWidth, measuredHeight)
            animator.startIfNeeded()
        }
    }

    fun updatePosition(posMs: Long) {
        if (isWordSync) {
            if (syncRenderer.isScrollOnly && !isOverflow) return
            activeRenderer.update(_model, lineState, posMs, measuredWidth, measuredHeight)
            if (syncRenderer.isPlaying && !syncRenderer.isFinished) {
                animator.startIfNeeded()
            }
        } else {
            startScrolling()
        }
    }

    fun refreshSizes() {
        _model.updateSizes(textPaint)
    }

    fun relayout() {
        if (isWordSync) syncRenderer.updateLayout(_model, lineState, measuredWidth, measuredHeight)
    }

    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        primaryColors = primary
        backgroundColors = background
        highlightColors = highlight

        textStyle.configure(primary, background, highlight)
        textStyle.applyTo(textPaint, lineWidth)
        syncRenderer.setColors(background, highlight)
        invalidate()
    }

    fun reset() {
        animator.stop()
        lineState.reset()
        scrollRenderer.reset(lineState)
        syncRenderer.reset(lineState)
        _model = emptyLyricModel()
        activeRenderer = scrollRenderer
        refreshSizes()
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) relayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            refreshSizes()
            updateColorsIfReady()
        }
    }

    override fun onDraw(canvas: Canvas) {
        activeRenderer.draw(canvas, _model, textPaint, lineState, measuredWidth, measuredHeight)
    }

    override fun getLeftFadingEdgeStrength(): Float {
        if (lineWidth <= width || horizontalFadingEdgeLength <= 0) return 0f
        val edgeL = horizontalFadingEdgeLength.toFloat()

        val offsetInUnit = if (isPlainText) {
            scrollRenderer.scrollProgress
        } else {
            -lineState.scrollOffset
        }

        if (offsetInUnit <= 0f) return 0f
        if (isPlainText && offsetInUnit > lineWidth) return 0f
        return (offsetInUnit / edgeL).coerceIn(0f, 1f)
    }

    override fun getRightFadingEdgeStrength(): Float {
        if (lineWidth <= width || horizontalFadingEdgeLength <= 0) return 0f
        val viewW = width.toFloat()
        val edgeL = horizontalFadingEdgeLength.toFloat()

        if (isPlainText) {
            if (lineState.isScrollFinished) {
                val remaining = lineWidth + lineState.scrollOffset - viewW
                return (remaining / edgeL).coerceIn(0f, 1f)
            }
            val offsetInUnit = scrollRenderer.scrollProgress
            val primaryRightEdge = lineWidth - offsetInUnit
            val ghostLeftEdge = primaryRightEdge + ghostSpacing
            return if (primaryRightEdge < viewW && ghostLeftEdge > viewW) 0f else 1.0f
        } else {
            if (isFinished) return 0f
        }

        val remaining = lineWidth + lineState.scrollOffset - viewW
        return (remaining / edgeL).coerceIn(0f, 1f)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        // 仅逐词同步行（非普通文字、非纯滚动）实际渲染逐字特效，才需要预留动画高度；
        // 普通文字行无单词数据、无特效，预留会使其行高变大造成不必要的初始偏移。
//        val charMotionPadding = if (isWordCharMotionEnabled && isWordSync && !isScrollOnly) {
//            val maxLift = maxOf(wordMotion.cjkLiftFactor, wordMotion.latinLiftFactor)
//            val textHeight = textPaint.descent() - textPaint.ascent()
//            // WaveLift 上浮 + 强调上浮 + 强调缩放（1.12 上限）的对称溢出：
//            // 为逐字动画预留高度，空间充足时动画完整展示；高度被外部压缩时
//            // 由 TextDrawer 的 strength 机制按比例收缩动画幅度。
//            ceil(textPaint.textSize * (maxLift + EMPHASIS_LIFT_EM) + textHeight * SCALE_OVERFLOW_FRACTION).toInt()
//        } else {
//            0
//        }
        val charMotionPadding = 0
        val textHeight = (textPaint.descent() - textPaint.ascent()).toInt() + charMotionPadding
        setMeasuredDimension(w, resolveSize(textHeight, hSpec))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    private fun startScrolling() {
        if (!isPlainText || !scrollUnlocked || scrollStarted) return
        scrollStarted = true
        lineState.reset()
        if (!isOverflow) return
        post {
            scrollRenderer.update(_model, lineState, 0, measuredWidth, measuredHeight)
            animator.stop()
            animator.startIfNeeded()
        }
    }

    private fun updateColorsIfReady() {
        if (primaryColors.isNotEmpty() && backgroundColors.isNotEmpty() && highlightColors.isNotEmpty()) {
            updateColor(primaryColors, backgroundColors, highlightColors)
        }
    }

}
