/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import io.github.proify.lyricon.lyric.view.LyricPlayListener
import io.github.proify.lyricon.lyric.view.line.WordSyncRenderer.Companion.MAX_SILENT_EXTRAPOLATION_MS
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.WordModel

internal class WordSyncRenderer(private val view: LyricLineView) : LineRenderer {

    val bgPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    val hlPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val progressAnimator = ProgressAnimator()
    private val scrollStepper = ScrollStepper()
    private val textDrawer = TextDrawer(view.effectEngine)

    var isScrollOnly = false

    var isCharMotionEnabled = true

    /** 普通行合成的单 word 只用于进度高亮，不参与逐字特效。 */
    var isCharMotionSuppressed = false

    var isGradientEnabled = true
        set(value) {
            if (field != value) {
                field = value
                textDrawer.clearShaderCache()
            }
        }

    var playListener: LyricPlayListener? = null
        set(value) {
            field = value
            _playListener = value ?: NoOpPlayListener
        }

    private var _playListener: LyricPlayListener = NoOpPlayListener

    var lastPosition = Long.MIN_VALUE
        private set

    /**
     * 逐帧平滑的动画时钟（毫秒）：与高亮推进同步驱动时间特效（浮动/浮现）。
     *
     * 外部 `updatePosition` 可能低频（数 Hz），若直接以 posMs 作特效时间会使位移动画
     * 出现低帧率；本时钟在 progressAnimator 播放动画期间逐帧外推（60fps），
     * 并通过 [seek]/[update] 校准到真实播放进度。
     */
    private var playbackClockMs = 0L

    /** 外推时钟的纳秒余量：避免每帧整数截断导致特效时钟滞后（60fps 下约 4%）。 */
    private var playbackClockRemainderNanos = 0L

    /**
     * 词间隙（progressAnimator 停摆）期间的特效时钟连续外推时长（ms）。
     * 行仍在播放窗口内时继续外推，消除词与词之间特效冻结的卡顿感；
     * 超过 [MAX_SILENT_EXTRAPOLATION_MS] 视为外部暂停，停止推进，
     * 恢复播放时由 [update] 校准时钟。
     */
    private var silentExtrapolationMs = 0L

    override val isPlaying get() = progressAnimator.isAnimating
    override val isFinished get() = progressAnimator.hasFinished
    override val isStarted get() = progressAnimator.hasStarted

    fun setTextSize(size: Float) {
        bgPaint.textSize = size
        hlPaint.textSize = size
        textDrawer.updateMetrics(bgPaint)
    }

    fun setTypeface(tf: Typeface?) {
        bgPaint.typeface = tf
        hlPaint.typeface = tf
        textDrawer.updateMetrics(bgPaint)
    }

    fun setColors(background: IntArray, highlight: IntArray) {
        if (background.isNotEmpty()) bgPaint.color = background[0]
        if (highlight.isNotEmpty()) hlPaint.color = highlight[0]
        textDrawer.setColors(background, highlight)
        textDrawer.clearShaderCache()
    }

    fun updateLayout(model: LyricModel, state: LineState, viewWidth: Int, viewHeight: Int) {
        textDrawer.updateMetrics(bgPaint)
        if (progressAnimator.hasFinished) {
            progressAnimator.jumpTo(model.width)
        }
        updateScrollState(model, state, viewWidth)
    }

    override fun seek(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val target = targetWidth(posMs, model)
        progressAnimator.jumpTo(target)
        playbackClockMs = posMs
        playbackClockRemainderNanos = 0L
        silentExtrapolationMs = 0L
        textDrawer.currentTimeMs = posMs
        updateScrollState(model, state, viewWidth)
        lastPosition = posMs
        notifyProgress(model)
    }

    override fun update(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        if (lastPosition != Long.MIN_VALUE && posMs < lastPosition) {
            seek(model, state, posMs, viewWidth, viewHeight)
            return
        }

        val word = model.wordTimingNavigator.first(posMs)
        val target = targetWidth(posMs, model, word)

        if (word != null && progressAnimator.currentWidth == 0f) {
            word.previous?.let { progressAnimator.jumpTo(it.endPosition) }
        }
        if (target != progressAnimator.targetWidth) {
            progressAnimator.animateTo(target, word?.duration ?: 0)
        }
        lastPosition = posMs
        playbackClockMs = posMs
        playbackClockRemainderNanos = 0L
        silentExtrapolationMs = 0L
        textDrawer.currentTimeMs = posMs
    }

    override fun step(
        deltaNanos: Long,
        model: LyricModel,
        state: LineState,
        viewWidth: Int
    ): Boolean {
        if (progressAnimator.isAnimating) {
            // 动画期间逐帧外推特效时钟：保证位移动画与高亮同样平滑（不依赖外部更新频率）。
            advancePlaybackClock(deltaNanos)
            if (progressAnimator.step(deltaNanos)) {
                updateScrollState(model, state, viewWidth)
                notifyProgress(model)
            }
            // 动画播放中（含恰好结束的这一帧）始终重绘：特效时钟已推进。
            return true
        }

        // 词间隙：行仍在播放窗口内时继续外推特效时钟，消除动画冻结；
        // 超过上限视为外部暂停（如用户暂停播放），停止推进以定格特效。
        if (isStarted && !isFinished && silentExtrapolationMs < MAX_SILENT_EXTRAPOLATION_MS) {
            silentExtrapolationMs += deltaNanos / 1_000_000L
            advancePlaybackClock(deltaNanos)
            return true
        }
        return false
    }

    override fun draw(
        canvas: Canvas,
        model: LyricModel,
        paint: TextPaint,
        state: LineState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        textDrawer.draw(
            canvas, model, viewWidth, viewHeight,
            state.scrollOffset, model.width > viewWidth,
            progressAnimator.currentWidth,
            isGradientEnabled, isScrollOnly,
            isCharMotionEnabled && !isCharMotionSuppressed,
            bgPaint, hlPaint, paint
        )
    }

    override fun reset(state: LineState) {
        progressAnimator.reset()
        state.reset()
        lastPosition = Long.MIN_VALUE
        playbackClockMs = 0L
        playbackClockRemainderNanos = 0L
        silentExtrapolationMs = 0L
        textDrawer.currentTimeMs = 0L
        textDrawer.clearShaderCache()
    }

    private fun updateScrollState(model: LyricModel, state: LineState, viewWidth: Int) {
        val offset = scrollStepper.compute(
            progressAnimator.currentWidth, model.width,
            viewWidth.toFloat(), progressAnimator.hasFinished, state.isScrollFinished
        )
        state.scrollOffset = offset
        if (progressAnimator.hasFinished) {
            state.isScrollFinished = true
        }
    }

    private fun targetWidth(posMs: Long, model: LyricModel, word: WordModel? = null): Float {
        val w = word ?: model.wordTimingNavigator.first(posMs)
        return when {
            w != null -> w.endPosition
            posMs >= model.end -> model.width
            posMs <= model.begin -> 0f
            else -> progressAnimator.currentWidth
        }
    }

    private fun notifyProgress(model: LyricModel) {
        val current = progressAnimator.currentWidth
        val total = model.width

        if (!progressAnimator.hasStarted && current > 0f) {
            progressAnimator.hasStarted = true
            _playListener.onPlayStarted(view)
        }
        if (!progressAnimator.hasFinished && current >= total) {
            progressAnimator.hasFinished = true
            _playListener.onPlayEnded(view)
        }
        _playListener.onPlayProgress(view, total, current)
    }

    /** 逐帧外推特效时钟（纳秒余量避免整数截断）。 */
    private fun advancePlaybackClock(deltaNanos: Long) {
        playbackClockRemainderNanos += deltaNanos
        playbackClockMs += playbackClockRemainderNanos / 1_000_000L
        playbackClockRemainderNanos %= 1_000_000L
        textDrawer.currentTimeMs = playbackClockMs
    }

    companion object {
        /** 词间隙特效时钟的最大连续外推时长（ms）：超过视为外部暂停。 */
        private const val MAX_SILENT_EXTRAPOLATION_MS = 300L

        private val NoOpPlayListener = object : LyricPlayListener {
            override fun onPlayStarted(view: LyricLineView) {}
            override fun onPlayEnded(view: LyricLineView) {}
            override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
        }
    }
}
