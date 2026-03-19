package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.TextPaint
import androidx.annotation.RequiresApi
import androidx.core.graphics.withSave
import io.github.proify.lyricon.lyric.view.LyricPlayListener
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.WordModel
import kotlin.math.abs
import kotlin.math.max

/**
 * 歌词行渲染控制器
 * 负责单行歌词的状态管理、动画驱动及复杂的着色器渲染逻辑。
 */
class Syllable(private val view: LyricLineView) {
    companion object {
        private const val SUSTAIN_EFFECT_MIN_DURATION_MS = 520L
        private const val SUSTAIN_EFFECT_MAX_LIFT_DP = 1.38f
        private const val SUSTAIN_EFFECT_MAX_GLOW_RADIUS_DP = 3.4f
        private const val SUSTAIN_EFFECT_MAX_GLOW_ALPHA = 160
        private const val SUSTAIN_EFFECT_RELEASE_DURATION_MS = 360L
        private const val SUSTAIN_LINE_BASE_LOWER_DP = 0.85f
        private const val SUSTAIN_LINE_END_RISE_DP = 0.56f
    }

    private data class SustainEffectState(
        val startX: Float,
        val endX: Float,
        val liftOffsetPx: Float,
        val glowRadiusPx: Float,
        val glowAlpha: Int,
        val intensity: Float
    )

    private val backgroundPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val renderDelegate: LineRenderDelegate =
        if (Build.VERSION.SDK_INT >= 29) HardwareRenderer() else SoftwareRenderer()

    private val textRenderer = LineTextRenderer()
    private val progressAnimator = ProgressAnimator()
    private val scrollController = ScrollController()

    var lastPosition = Long.MIN_VALUE
        private set

    var playListener: LyricPlayListener? = null
    private var activeSustainWord: WordModel? = null
    private var activeSustainIntensity = 0f
    private var activeSustainPeakIntensity = 0f
    private var releaseSustainWord: WordModel? = null
    private var releaseStartRealtimeMs = 0L
    private var releaseSeedIntensity = 0f

    private val rainbowColor = RainbowColor(
        background = intArrayOf(0),
        highlight = intArrayOf(0)
    )

    val isRainbowHighlight get() = rainbowColor.highlight.size > 1
    val isRainbowBackground get() = rainbowColor.background.size > 1

    var isGradientEnabled: Boolean = true
        set(value) {
            field = value
            renderDelegate.isGradientEnabled = value
            renderDelegate.invalidate()
        }

    var isScrollOnly: Boolean = false
        set(value) {
            field = value
            renderDelegate.isOnlyScrollMode = value
            renderDelegate.invalidate()
        }

    var isSustainLiftEnabled: Boolean = true
        set(value) {
            field = value
            renderDelegate.invalidate()
        }

    var isSustainGlowEnabled: Boolean = true
        set(value) {
            field = value
            renderDelegate.invalidate()
        }

    val textSize: Float get() = backgroundPaint.textSize
    val isStarted: Boolean get() = progressAnimator.hasStarted
    val isPlaying: Boolean get() = progressAnimator.isAnimating
    val isFinished: Boolean get() = progressAnimator.hasFinished

    init {
        updateLayoutMetrics()
    }

    private data class RainbowColor(
        var background: IntArray,
        var highlight: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RainbowColor) return false
            return background.contentEquals(other.background) && highlight.contentEquals(other.highlight)
        }

        override fun hashCode(): Int =
            31 * background.contentHashCode() + highlight.contentHashCode()
    }

    fun setColor(background: IntArray, highlight: IntArray) {
        if (background.isEmpty() || highlight.isEmpty()) return
        if (!rainbowColor.background.contentEquals(background) || !rainbowColor.highlight.contentEquals(
                highlight
            )
        ) {
            backgroundPaint.color = background[0]
            highlightPaint.color = highlight[0]
            rainbowColor.background = background
            rainbowColor.highlight = highlight
            textRenderer.clearShaderCache()
            renderDelegate.invalidate()
        }
    }

    fun setTextSize(size: Float) {
        if (backgroundPaint.textSize != size) {
            backgroundPaint.textSize = size
            highlightPaint.textSize = size
            reLayout()
        }
    }

    fun reLayout() {
        textRenderer.updateMetrics(backgroundPaint)
        if (isFinished) progressAnimator.jumpTo(view.lyricWidth)
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
        scrollController.update(progressAnimator.currentWidth, view)
        renderDelegate.invalidate()
        view.invalidate()
    }

    fun setTypeface(typeface: Typeface?) {
        if (backgroundPaint.typeface != typeface) {
            backgroundPaint.typeface = typeface
            highlightPaint.typeface = typeface
            textRenderer.updateMetrics(backgroundPaint)
            renderDelegate.invalidate()
        }
    }

    fun reset() {
        progressAnimator.reset()
        scrollController.reset(view)
        lastPosition = Long.MIN_VALUE
        resetSustainState()
        renderDelegate.onHighlightUpdate(0f)
        renderDelegate.onSustainEffectUpdate(emptyList())
    }

    fun seek(position: Long) {
        val currentWord = view.lyric.wordTimingNavigator.first(position)
        val targetWidth = calculateCurrentWidth(position)
        progressAnimator.jumpTo(targetWidth)
        scrollController.update(targetWidth, view)
        renderDelegate.onHighlightUpdate(targetWidth)
        resetSustainState()
        renderDelegate.onSustainEffectUpdate(buildSustainEffects(currentWord, position))
        lastPosition = position
        notifyProgressUpdate()
    }

    fun updateProgress(position: Long) {
        if (lastPosition != Long.MIN_VALUE && position < lastPosition) {
            seek(position)
            return
        }
        val model = view.lyric
        val currentWord = model.wordTimingNavigator.first(position)
        val targetWidth = if (currentWord != null) currentWord.endPosition else calculateCurrentWidth(position)

        if (currentWord != null && progressAnimator.currentWidth == 0f) {
            currentWord.previous?.let { progressAnimator.jumpTo(it.endPosition) }
        }

        // targetWidth发生变化或animator未运行（例如seek后），需要重新启动动画
        if (targetWidth != progressAnimator.targetWidth || !progressAnimator.isAnimating) {
            val duration = if (currentWord != null) {
                (currentWord.end - position).coerceAtLeast(0)
            } else 0L

            if (duration > 0) {
                progressAnimator.start(targetWidth, duration)
            } else {
                progressAnimator.jumpTo(targetWidth)
            }
        }
        renderDelegate.onSustainEffectUpdate(buildSustainEffects(currentWord, position))
        lastPosition = position
    }

    fun onFrameUpdate(nanoTime: Long): Boolean {
        val progressUpdated = progressAnimator.step(nanoTime)
        if (progressUpdated) {
            scrollController.update(progressAnimator.currentWidth, view)
            renderDelegate.onHighlightUpdate(progressAnimator.currentWidth)
        }

        if (progressUpdated || releaseSustainWord != null) {
            renderDelegate.onSustainEffectUpdate(
                buildSustainEffects(
                    view.lyric.wordTimingNavigator.first(lastPosition),
                    lastPosition
                )
            )
            if (progressUpdated) {
                notifyProgressUpdate()
            }
            return progressUpdated || releaseSustainWord != null
        }

        return progressUpdated
    }

    fun draw(canvas: Canvas) {
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
        renderDelegate.draw(canvas, view.scrollXOffset)
    }

    private fun updateLayoutMetrics() {
        textRenderer.updateMetrics(backgroundPaint)
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
    }

    private fun resetSustainState() {
        activeSustainWord = null
        activeSustainIntensity = 0f
        activeSustainPeakIntensity = 0f
        releaseSustainWord = null
        releaseStartRealtimeMs = 0L
        releaseSeedIntensity = 0f
    }

    private fun resolveReleaseSeedIntensity(fallback: Float): Float {
        val peakBased = activeSustainPeakIntensity.takeIf { it > 0f } ?: fallback
        return max(0.68f, max(peakBased * 0.9f, fallback)).coerceIn(0f, 1f)
    }

    private fun beginSustainRelease(word: WordModel, seedIntensity: Float) {
        releaseSustainWord = word
        releaseStartRealtimeMs = SystemClock.elapsedRealtime()
        releaseSeedIntensity = seedIntensity.coerceIn(0f, 1f)
    }

    private fun buildSustainEffects(word: WordModel?, position: Long): List<SustainEffectState> {
        if (!isSustainLiftEnabled && !isSustainGlowEnabled) {
            resetSustainState()
            return emptyList()
        }
        if (position == Long.MIN_VALUE) return emptyList()

        val activeEffect = buildActiveSustainEffect(word)
        if (activeEffect != null && word != null) {
            if (activeSustainWord != null && activeSustainWord != word) {
                beginSustainRelease(
                    activeSustainWord!!,
                    resolveReleaseSeedIntensity(activeSustainIntensity.takeIf { it > 0f } ?: 1f)
                )
                activeSustainPeakIntensity = 0f
            }
            activeSustainWord = word
            activeSustainIntensity = activeEffect.intensity
            activeSustainPeakIntensity = max(activeSustainPeakIntensity, activeEffect.intensity)
            if (releaseSustainWord == word) {
                releaseSustainWord = null
            }
        } else {
            activeSustainWord?.let {
                beginSustainRelease(
                    it,
                    resolveReleaseSeedIntensity(activeSustainIntensity.takeIf { s -> s > 0f } ?: 1f)
                )
            }
            activeSustainWord = null
            activeSustainIntensity = 0f
            activeSustainPeakIntensity = 0f
        }

        val releaseEffect = buildReleaseSustainEffect(position)
        if (releaseEffect == null && activeEffect == null) return emptyList()

        return buildList(2) {
            releaseEffect?.let { add(it) }
            activeEffect?.let { add(it) }
        }
    }

    private fun buildActiveSustainEffect(word: WordModel?): SustainEffectState? {
        if (!isSustainLiftEnabled && !isSustainGlowEnabled) return null
        word ?: return null
        if (word.duration < SUSTAIN_EFFECT_MIN_DURATION_MS) return null

        val wordWidth = (word.endPosition - word.startPosition).coerceAtLeast(0f)
        if (wordWidth <= 0f) return null

        val progress = ((progressAnimator.currentWidth - word.startPosition) / wordWidth).coerceIn(0f, 1f)
        if (progress <= 0f || progress >= 1f) return null

        val edgeFade = when {
            progress < 0.12f -> (progress / 0.12f)
            progress > 0.88f -> ((1f - progress) / 0.12f)
            else -> 1f
        }.coerceIn(0f, 1f)
        val intensity = edgeFade
        if (intensity <= 0f) return null

        val density = view.resources.displayMetrics.density
        val lift = if (isSustainLiftEnabled) {
            density * SUSTAIN_EFFECT_MAX_LIFT_DP * (0.82f + intensity * 0.18f)
        } else {
            0f
        }
        val glowRadius = if (isSustainGlowEnabled) {
            density * SUSTAIN_EFFECT_MAX_GLOW_RADIUS_DP * (0.72f + intensity * 0.28f)
        } else {
            0f
        }
        val glowAlpha = if (isSustainGlowEnabled) {
            (SUSTAIN_EFFECT_MAX_GLOW_ALPHA * (0.28f + intensity * 0.58f))
                .toInt()
                .coerceIn(0, 255)
        } else {
            0
        }

        return SustainEffectState(
            startX = word.startPosition,
            endX = word.endPosition,
            liftOffsetPx = lift,
            glowRadiusPx = glowRadius,
            glowAlpha = glowAlpha,
            intensity = intensity
        )
    }

    private fun buildReleaseSustainEffect(position: Long): SustainEffectState? {
        if (!isSustainLiftEnabled && !isSustainGlowEnabled) return null
        val word = releaseSustainWord ?: return null
        if (releaseStartRealtimeMs <= 0L) return null

        val elapsed = (SystemClock.elapsedRealtime() - releaseStartRealtimeMs).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / SUSTAIN_EFFECT_RELEASE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        if (progress >= 1f) {
            releaseSustainWord = null
            releaseStartRealtimeMs = 0L
            releaseSeedIntensity = 0f
            return null
        }

        val tailProgress = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val easedTail = tailProgress * tailProgress * (3f - 2f * tailProgress)
        val falloff = 1f - easedTail
        val intensity = (releaseSeedIntensity * falloff).coerceIn(0f, 1f)
        if (intensity <= 0.02f) return null

        val density = view.resources.displayMetrics.density
        val lift = if (isSustainLiftEnabled) {
            density * SUSTAIN_EFFECT_MAX_LIFT_DP * (0.3f + intensity * 0.75f)
        } else {
            0f
        }
        val glowRadius = if (isSustainGlowEnabled) {
            density * SUSTAIN_EFFECT_MAX_GLOW_RADIUS_DP * (0.42f + intensity * 0.34f)
        } else {
            0f
        }
        val glowAlpha = if (isSustainGlowEnabled) {
            (SUSTAIN_EFFECT_MAX_GLOW_ALPHA * (0.16f + intensity * 0.38f))
                .toInt()
                .coerceIn(0, 255)
        } else {
            0
        }

        return SustainEffectState(
            startX = word.startPosition,
            endX = word.endPosition,
            liftOffsetPx = lift,
            glowRadiusPx = glowRadius,
            glowAlpha = glowAlpha,
            intensity = intensity
        )
    }

    // 根据当前时间精确计算单词内的高亮宽度
    private fun calculateCurrentWidth(
        pos: Long,
        word: WordModel? = view.lyric.wordTimingNavigator.first(pos)
    ): Float = when {
        word != null -> {
            val progress = ((pos - word.begin).toFloat() / word.duration).coerceIn(0f, 1f)
            word.startPosition + (word.endPosition - word.startPosition) * progress
        }
        pos >= view.lyric.end -> view.lyricWidth
        pos <= view.lyric.begin -> 0f
        else -> progressAnimator.currentWidth
    }

    private fun notifyProgressUpdate() {
        val current = progressAnimator.currentWidth
        val total = view.lyricWidth
        if (!progressAnimator.hasStarted && current > 0f) {
            progressAnimator.hasStarted = true
            playListener?.onPlayStarted(view)
        }
        if (!progressAnimator.hasFinished && current >= total) {
            progressAnimator.hasFinished = true
            playListener?.onPlayEnded(view)
        }
        playListener?.onPlayProgress(view, total, current)
    }

    // --- 内部组件 ---

    private class ProgressAnimator {
        var currentWidth = 0f
        var targetWidth = 0f
        var isAnimating = false
        var hasStarted = false
        var hasFinished = false
        private var startWidth = 0f
        private var startTimeNano = 0L
        private var durationNano = 1L

        fun reset() {
            currentWidth = 0f; targetWidth = 0f; isAnimating = false
            hasStarted = false; hasFinished = false
        }

        fun jumpTo(width: Float) {
            currentWidth = width; targetWidth = width; isAnimating = false
        }

        fun start(target: Float, durationMs: Long) {
            startWidth = currentWidth
            targetWidth = target
            durationNano = max(1L, durationMs) * 1_000_000L
            startTimeNano = System.nanoTime()
            isAnimating = true
        }

        fun step(now: Long): Boolean {
            if (!isAnimating) return false
            val elapsed = (now - startTimeNano).coerceAtLeast(0L)
            if (elapsed >= durationNano) {
                currentWidth = targetWidth
                isAnimating = false
                return true
            }
            val progress = elapsed.toFloat() / durationNano
            currentWidth = startWidth + (targetWidth - startWidth) * progress
            return true
        }
    }

    private class ScrollController {
        fun reset(v: LyricLineView) {
            v.scrollXOffset = 0f
            v.isScrollFinished = false
        }

        fun update(currentX: Float, v: LyricLineView) {
            val lyricW = v.lyricWidth
            val viewW = v.measuredWidth.toFloat()
            if (lyricW <= viewW) {
                v.scrollXOffset = 0f
                return
            }
            val minScroll = -(lyricW - viewW)
            if (v.isPlayFinished()) {
                v.scrollXOffset = minScroll
                v.isScrollFinished = true
                return
            }
            val halfWidth = viewW / 2f
            if (currentX > halfWidth) {
                v.scrollXOffset = (halfWidth - currentX).coerceIn(minScroll, 0f)
                v.isScrollFinished = v.scrollXOffset <= minScroll
            } else {
                v.scrollXOffset = 0f
            }
        }
    }

    private interface LineRenderDelegate {
        var isGradientEnabled: Boolean
        var isOnlyScrollMode: Boolean
        fun onLayout(width: Int, height: Int, overflow: Boolean)
        fun onHighlightUpdate(highlightWidth: Float)
        fun onSustainEffectUpdate(effects: List<SustainEffectState>)
        fun invalidate()
        fun draw(canvas: Canvas, scrollX: Float)
    }

    private inner class SoftwareRenderer : LineRenderDelegate {
        override var isGradientEnabled = true
        override var isOnlyScrollMode = false
        private var width = 0
        private var height = 0
        private var overflow = false
        private var highlightWidth = 0f
        private var sustainEffects: List<SustainEffectState> = emptyList()
        override fun onLayout(width: Int, height: Int, overflow: Boolean) {
            this@SoftwareRenderer.width = width; this@SoftwareRenderer.height =
                height; this@SoftwareRenderer.overflow = overflow
        }

        override fun onHighlightUpdate(highlightWidth: Float) {
            this@SoftwareRenderer.highlightWidth = highlightWidth
        }

        override fun onSustainEffectUpdate(effects: List<SustainEffectState>) {
            sustainEffects = effects
        }

        override fun invalidate() {}
        override fun draw(canvas: Canvas, scrollX: Float) {
            textRenderer.draw(
                canvas,
                view.lyric,
                width,
                height,
                scrollX,
                overflow,
                highlightWidth,
                sustainEffects,
                isGradientEnabled,
                isOnlyScrollMode,
                backgroundPaint,
                highlightPaint,
                view.textPaint
            )
        }
    }

    @RequiresApi(29)
    private inner class HardwareRenderer : LineRenderDelegate {
        override var isGradientEnabled = true
        override var isOnlyScrollMode = false
        private val renderNode = RenderNode("LyricLine").apply { clipToBounds = false }
        private var width = 0
        private var height = 0
        private var overflow = false
        private var highlightWidth = 0f
        private var sustainEffects: List<SustainEffectState> = emptyList()
        private var isDirty = true
        override fun invalidate() {
            isDirty = true
        }

        override fun onLayout(width: Int, height: Int, overflow: Boolean) {
            if (this@HardwareRenderer.width != width || this@HardwareRenderer.height != height || this@HardwareRenderer.overflow != overflow) {
                this@HardwareRenderer.width = width; this@HardwareRenderer.height =
                    height; this@HardwareRenderer.overflow = overflow
                renderNode.setPosition(
                    0, 0, this@HardwareRenderer.width,
                    this@HardwareRenderer.height
                )
                isDirty = true
            }
        }

        override fun onHighlightUpdate(highlightWidth: Float) {
            if (abs(this@HardwareRenderer.highlightWidth - highlightWidth) > 0.1f) {
                this@HardwareRenderer.highlightWidth = highlightWidth; isDirty = true
            }
        }

        override fun onSustainEffectUpdate(effects: List<SustainEffectState>) {
            if (sustainEffects != effects) {
                sustainEffects = effects
                isDirty = true
            }
        }

        override fun draw(canvas: Canvas, scrollX: Float) {
            if (isDirty) {
                val rc = renderNode.beginRecording(width, height)
                textRenderer.draw(
                    rc,
                    view.lyric,
                    width,
                    height,
                    scrollX,
                    overflow,
                    highlightWidth,
                    sustainEffects,
                    isGradientEnabled,
                    isOnlyScrollMode,
                    backgroundPaint,
                    highlightPaint,
                    view.textPaint
                )
                renderNode.endRecording()
                isDirty = false
            }
            canvas.drawRenderNode(renderNode)
        }
    }

    /**
     * 文本渲染器
     * 优化点：使用 ComposeShader 解决彩虹色随进度挤压的问题。
     */
    private inner class LineTextRenderer {
        private val minEdgePosition = 0.9f
        private val fontMetrics = Paint.FontMetrics()
        private var baselineOffset = 0f

        private var cachedRainbowShader: LinearGradient? = null
        private var cachedAlphaMaskShader: LinearGradient? = null
        private var lastTotalWidth = -1f
        private var lastHighlightWidth = -1f
        private var lastColorsHash = 0
        private val sustainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

        fun updateMetrics(paint: TextPaint) {
            paint.getFontMetrics(fontMetrics)
            baselineOffset = -(fontMetrics.descent + fontMetrics.ascent) / 2f
        }

        fun clearShaderCache() {
            cachedRainbowShader = null
            cachedAlphaMaskShader = null
            lastTotalWidth = -1f
        }

        fun draw(
            canvas: Canvas, model: LyricModel, viewWidth: Int, viewHeight: Int,
            scrollX: Float, isOverflow: Boolean, highlightWidth: Float,
            sustainEffects: List<SustainEffectState>,
            useGradient: Boolean, scrollOnly: Boolean, bgPaint: TextPaint,
            hlPaint: TextPaint, normPaint: TextPaint
        ) {
            val density = view.resources.displayMetrics.density
            val lineProgress = if (model.width > 0f) {
                (highlightWidth / model.width).coerceIn(0f, 1f)
            } else {
                0f
            }
            val baseLower = if (isSustainLiftEnabled) density * SUSTAIN_LINE_BASE_LOWER_DP else 0f
            val lineEndRise = if (isSustainLiftEnabled) {
                density * SUSTAIN_LINE_END_RISE_DP * lineProgress
            } else {
                0f
            }
            val y = (viewHeight / 2f) + baselineOffset + baseLower - lineEndRise
            canvas.withSave {
                val xOffset =
                    if (isOverflow) scrollX else if (model.isAlignedRight) viewWidth - model.width else 0f
                translate(xOffset, 0f)

                if (scrollOnly) {
                    canvas.drawText(model.wordText, 0f, y, normPaint)
                    return@withSave
                }

                val sustainRanges = sustainEffects
                    .mapNotNull {
                        val start = it.startX.coerceAtLeast(0f)
                        val end = it.endX.coerceAtMost(model.width)
                        if (end > start) start to end else null
                    }
                    .sortedBy { it.first }
                val hasSustain = sustainRanges.isNotEmpty()

                // 1. 绘制背景层 (可能是静止的彩虹)
                if (isRainbowBackground) {
                    bgPaint.shader = getOrCreateRainbowShader(model.width, rainbowColor.background)
                } else {
                    bgPaint.shader = null
                }

                val backgroundLeft = if (useGradient) 0f else highlightWidth
                drawTextWithOptionalExclusion(
                    canvas = canvas,
                    text = model.wordText,
                    y = y,
                    paint = bgPaint,
                    clipLeft = backgroundLeft,
                    clipRight = model.width,
                    exclusions = if (hasSustain && useGradient) sustainRanges else emptyList(),
                    viewHeight = viewHeight
                )

                // 2. 绘制高亮层
                if (highlightWidth > 0f) {
                    if (useGradient) {
                        // 羽化模式：通过 ComposeShader 结合【固定比例彩虹】+【随进度移动的透明遮罩】
                        val baseShader = if (isRainbowHighlight) {
                            getOrCreateRainbowShader(model.width, rainbowColor.highlight)
                        } else {
                            // 单色高亮转为 Shader 方便混合
                            LinearGradient(
                                0f,
                                0f,
                                model.width,
                                0f,
                                hlPaint.color,
                                hlPaint.color,
                                Shader.TileMode.CLAMP
                            )
                        }

                        val maskShader = getOrCreateAlphaMaskShader(model.width, highlightWidth)
                        hlPaint.shader =
                            ComposeShader(baseShader, maskShader, PorterDuff.Mode.DST_IN)
                    } else {
                        // 非羽化模式：直接裁剪，颜色位置天然正确
                        if (isRainbowHighlight) {
                            hlPaint.shader =
                                getOrCreateRainbowShader(model.width, rainbowColor.highlight)
                        } else {
                            hlPaint.shader = null
                        }
                    }

                    drawTextWithOptionalExclusion(
                        canvas = canvas,
                        text = model.wordText,
                        y = y,
                        paint = hlPaint,
                        clipLeft = 0f,
                        clipRight = highlightWidth,
                    exclusions = if (hasSustain) sustainRanges else emptyList(),
                    viewHeight = viewHeight
                )
                }

                sustainEffects.forEach { effect ->
                    drawSustainEffect(
                        canvas = canvas,
                        model = model,
                        y = y,
                        viewHeight = viewHeight,
                        effect = effect,
                        highlightPaint = hlPaint
                    )
                }
            }
        }

        private fun drawTextWithOptionalExclusion(
            canvas: Canvas,
            text: String,
            y: Float,
            paint: TextPaint,
            clipLeft: Float,
            clipRight: Float,
            exclusions: List<Pair<Float, Float>>,
            viewHeight: Int
        ) {
            val safeLeft = clipLeft.coerceAtLeast(0f)
            val safeRight = clipRight.coerceAtLeast(safeLeft)
            if (safeRight <= safeLeft) return

            val clippedExclusions = exclusions
                .mapNotNull { (start, end) ->
                    if (end <= start || end <= safeLeft || start >= safeRight) null
                    else start.coerceIn(safeLeft, safeRight) to end.coerceIn(safeLeft, safeRight)
                }
                .sortedBy { it.first }

            if (clippedExclusions.isEmpty()) {
                canvas.withSave {
                    clipRect(safeLeft, 0f, safeRight, viewHeight.toFloat())
                    drawText(text, 0f, y, paint)
                }
                return
            }

            var cursor = safeLeft
            clippedExclusions.forEach { (start, end) ->
                if (start > cursor) {
                    canvas.withSave {
                        clipRect(cursor, 0f, start, viewHeight.toFloat())
                        drawText(text, 0f, y, paint)
                    }
                }
                cursor = max(cursor, end)
            }
            if (cursor < safeRight) {
                canvas.withSave {
                    clipRect(cursor, 0f, safeRight, viewHeight.toFloat())
                    drawText(text, 0f, y, paint)
                }
            }
        }

        private fun drawSustainEffect(
            canvas: Canvas,
            model: LyricModel,
            y: Float,
            viewHeight: Int,
            effect: SustainEffectState?,
            highlightPaint: TextPaint
        ) {
            effect ?: return
            val clipStart = effect.startX.coerceAtLeast(0f)
            val clipEnd = effect.endX.coerceAtMost(model.width)
            if (clipEnd <= clipStart) return

            val baseColor = (rainbowColor.highlight.firstOrNull() ?: highlightPaint.color) and 0x00FFFFFF
            val glowRgb = blendToWhite(baseColor, 0.22f)
            val density = view.resources.displayMetrics.density
            val outerStroke = (effect.glowRadiusPx * 0.3f).coerceAtLeast(density * 0.38f)
            val innerStroke = (effect.glowRadiusPx * 0.18f).coerceAtLeast(density * 0.28f)
            val outerAlpha = (effect.glowAlpha * 0.28f * effect.intensity).toInt().coerceIn(0, 255)
            val innerAlpha = (effect.glowAlpha * 0.46f).toInt().coerceIn(0, 255)
            val coreColor = (0xFF shl 24) or baseColor
            val drawGlow = isSustainGlowEnabled && effect.glowAlpha > 0

            sustainPaint.set(highlightPaint)
            sustainPaint.shader = null
            canvas.withSave {
                if (isSustainLiftEnabled) {
                    translate(0f, -effect.liftOffsetPx)
                }
                clipRect(clipStart, 0f, clipEnd, viewHeight.toFloat())

                if (drawGlow) {
                    // 外层光晕（降低过曝，优先可读性）。
                    sustainPaint.style = Paint.Style.STROKE
                    sustainPaint.strokeWidth = outerStroke
                    sustainPaint.color = (outerAlpha shl 24) or glowRgb
                    drawText(model.wordText, 0f, y, sustainPaint)

                    // 内层高亮描边。
                    sustainPaint.style = Paint.Style.STROKE
                    sustainPaint.strokeWidth = innerStroke
                    sustainPaint.color = (innerAlpha shl 24) or glowRgb
                    drawText(model.wordText, 0f, y, sustainPaint)
                }

                // 核心字形。
                sustainPaint.style = Paint.Style.FILL
                sustainPaint.strokeWidth = 0f
                sustainPaint.color = coreColor
                drawText(model.wordText, 0f, y, sustainPaint)
            }
        }

        private fun blendToWhite(rgb: Int, ratio: Float): Int {
            val t = ratio.coerceIn(0f, 1f)
            val r = ((Color.red(rgb) * (1f - t)) + (255f * t)).toInt().coerceIn(0, 255)
            val g = ((Color.green(rgb) * (1f - t)) + (255f * t)).toInt().coerceIn(0, 255)
            val b = ((Color.blue(rgb) * (1f - t)) + (255f * t)).toInt().coerceIn(0, 255)
            return (r shl 16) or (g shl 8) or b
        }

        /**
         * 获取或创建彩虹着色器。
         * 关键：宽度固定为 totalWidth，确保颜色分布在整行歌词上是恒定的。
         */
        private fun getOrCreateRainbowShader(totalWidth: Float, colors: IntArray): Shader {
            val colorsHash = colors.contentHashCode()
            if (cachedRainbowShader == null || lastTotalWidth != totalWidth || lastColorsHash != colorsHash) {
                cachedRainbowShader = LinearGradient(
                    0f, 0f, totalWidth, 0f,
                    colors, null, Shader.TileMode.CLAMP
                )
                lastTotalWidth = totalWidth
                lastColorsHash = colorsHash
            }
            return cachedRainbowShader!!
        }

        /**
         * 获取或创建透明度遮罩。
         * 关键：它负责高亮边缘 90% -> 100% 的淡出效果。
         */
        private fun getOrCreateAlphaMaskShader(totalWidth: Float, highlightWidth: Float): Shader {
            val edgePosition = max(highlightWidth / totalWidth, minEdgePosition)

            if (cachedAlphaMaskShader == null || abs(lastHighlightWidth - highlightWidth) > 0.1f) {
                // 使用从不透明到透明的渐变
                cachedAlphaMaskShader = LinearGradient(
                    0f, 0f, highlightWidth, 0f,
                    intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                    floatArrayOf(0f, edgePosition, 1f),
                    Shader.TileMode.CLAMP
                )
                lastHighlightWidth = highlightWidth
            }
            return cachedAlphaMaskShader!!
        }
    }
}
