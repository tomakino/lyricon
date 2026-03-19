/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package io.github.proify.lyricon.lyric.view.line

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.View
import androidx.core.view.isVisible
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.view.LyricLineConfig
import io.github.proify.lyricon.lyric.view.UpdatableColor
import io.github.proify.lyricon.lyric.view.dp
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.createModel
import io.github.proify.lyricon.lyric.view.line.model.emptyLyricModel
import io.github.proify.lyricon.lyric.view.sp
import java.lang.ref.WeakReference

open class LyricLineView(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), UpdatableColor {

    companion object {
        private const val TAG = "LyricLineView"
        private const val DEBUG = false
    }

    init {
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(10.dp)
    }

    val textPaint: TextPaint = TextPaintX().apply {
        textSize = 24f.sp
    }
    private var currentTextColors: IntArray = intArrayOf(Color.BLACK)

    var lyric: LyricModel = emptyLyricModel()
        private set

    var scrollXOffset: Float = 0f

    var isScrollFinished: Boolean = false
    val marquee: Marquee = Marquee(WeakReference(this))
    val syllable: Syllable = Syllable(this)
    private val animationDriver = AnimationDriver()

    val lyricWidth: Float get() = lyric.width

    fun reset() {
        animationDriver.stop()
        marquee.reset()
        syllable.reset()
        scrollXOffset = 0f
        isScrollFinished = false
        lyric = emptyLyricModel()
        refreshModelSizes()
        invalidate()
    }

    val textSize: Float get() = textPaint.textSize

    fun setTextSize(size: Float) {
        val needUpdate = textPaint.textSize != size
                || syllable.textSize != size

        if (!needUpdate) return

        textPaint.textSize = size
        syllable.setTextSize(size)

        refreshModelSizes()
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) syllable.reLayout()
    }

    fun reLayout() {
        if (isSyllableMode()) syllable.reLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        currentTextColors = primary.copyOf()
        applyCurrentTextColor()
        syllable.setColor(background, highlight)
        invalidate()

        if (DEBUG) Log.d(TAG, "updateColor: $primary, $background, $highlight")
    }

    fun setStyle(configs: LyricLineConfig) {
        val textConfig = configs.text
        val marqueeConfig = configs.marquee
        val syllableConfig = configs.syllable

        textPaint.apply {
            textSize = textConfig.textSize
            typeface = textConfig.typeface
        }
        currentTextColors = textConfig.textColor.copyOf()

        syllable.setColor(
            syllableConfig.backgroundColor,
            syllableConfig.highlightColor
        )
        syllable.setTextSize(textConfig.textSize)
        syllable.setTypeface(textConfig.typeface)
        syllable.isGradientEnabled = configs.gradientProgressStyle
        syllable.isSustainLiftEnabled = syllableConfig.enableSustainLift
        syllable.isSustainGlowEnabled = syllableConfig.enableSustainGlow

        marquee.apply {
            ghostSpacing = marqueeConfig.ghostSpacing
            scrollSpeed = marqueeConfig.scrollSpeed
            initialDelayMs = marqueeConfig.initialDelay
            loopDelayMs = marqueeConfig.loopDelay
            repeatCount = marqueeConfig.repeatCount
            stopAtEnd = marqueeConfig.stopAtEnd
        }

        if (configs.fadingEdgeLength == 0) {
            setFadingEdgeLength(0)
            isHorizontalFadingEdgeEnabled = false
        } else {
            setFadingEdgeLength(configs.fadingEdgeLength)
            isHorizontalFadingEdgeEnabled = true
        }

        refreshModelSizes()

        animationDriver.stop()
        animationDriver.startIfNoRunning()
        invalidate()
    }

    fun isSyllableMode(): Boolean = !isMarqueeMode()

    fun seekTo(position: Long) {
        if (isSyllableMode()) {
            syllable.seek(position)
            animationDriver.startIfNoRunning()
        }
    }

    fun setPosition(position: Long) {
        if (isSyllableMode()) {
            if (syllable.isScrollOnly && !isOverflow()) {
                return
            }
            syllable.updateProgress(position)

            if (syllable.isPlaying && !syllable.isFinished) {
                animationDriver.startIfNoRunning()
            }
        }
    }

    fun refreshModelSizes() {
        lyric.updateSizes(textPaint)
        applyCurrentTextColor()
    }

//    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
//        super.onSizeChanged(w, h, oldw, oldh)
//        if (w > 0 && h > 0) {
//            refreshModelSizes()
//        }
//    }

    override fun getLeftFadingEdgeStrength(): Float {
        // 基础检查：文本未溢出或未开启渐隐
        if (lyricWidth <= width || horizontalFadingEdgeLength <= 0) return 0f

        val offsetInUnit = if (isMarqueeMode()) {
            marquee.currentUnitOffset
        } else {
            -scrollXOffset
        }

        // 1. 如果还在起始延迟或位移为0，无左渐隐
        if (offsetInUnit <= 0f) return 0f

        // 2. 行业级处理：处理间距区域（Space Area）
        // 如果当前位移超过了歌词文本宽度，说明左边缘现在处于空白间距中，不应有渐变
        if (isMarqueeMode() && offsetInUnit > lyricWidth) {
            return 0f
        }

        // 3. 计算渐入：位移从 0 到 fadingEdgeLength 过程中线性增加强度
        val edgeL = horizontalFadingEdgeLength.toFloat()
        return (offsetInUnit / edgeL).coerceIn(0f, 1f)
    }

    override fun getRightFadingEdgeStrength(): Float {
        // 基础检查
        if (lyricWidth <= width || horizontalFadingEdgeLength <= 0) return 0f

        val viewW = width.toFloat()
        val edgeL = horizontalFadingEdgeLength.toFloat()

        if (isMarqueeMode()) {
            // 如果处于结束停止状态（stopAtEnd），根据剩余内容长度计算
            if (isScrollFinished) {
                val remaining = lyricWidth + scrollXOffset - viewW
                return (remaining / edgeL).coerceIn(0f, 1f)
            }

            // --- 核心逻辑：判断空白区 ---
            val offsetInUnit = marquee.currentUnitOffset

            // 主体文本的右边缘在屏幕上的坐标
            val primaryRightEdge = lyricWidth - offsetInUnit
            // 鬼影文本的左边缘在屏幕上的坐标
            val ghostLeftEdge = primaryRightEdge + marquee.ghostSpacing

            // 如果【主体右边缘】已经滑过屏幕右侧（进入屏幕），
            // 且【鬼影左边缘】还没有到达屏幕右侧，说明此时右边缘是空白
            return if (primaryRightEdge < viewW && ghostLeftEdge > viewW) {
                0f
            } else {
                // 内容还在持续（主体还没走完，或者鬼影已经进场）
                1.0f
            }
        } else if (isSyllableMode()) {
            if (isPlayFinished()) {
                return 0f
            }
        }

        // 非跑马灯模式（逐字模式）：常规计算
        val remaining = lyricWidth + scrollXOffset - viewW
        return (remaining / edgeL).coerceIn(0f, 1f)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val textHeight = (textPaint.descent() - textPaint.ascent()).toInt()
        setMeasuredDimension(w, resolveSize(textHeight, hSpec))
    }

    fun setLyric(line: LyricLine?) {
        reset()
        lyric = line?.normalize()?.createModel() ?: emptyLyricModel()

        refreshModelSizes()
        invalidate()
    }

    fun startMarquee() {
        if (isMarqueeMode()) {
            scrollXOffset = 0f
            post {
                marquee.start()
                animationDriver.stop()
                animationDriver.startIfNoRunning()
            }
        }
    }

    fun isMarqueeMode(): Boolean = lyric.isPlainText
    fun isOverflow(): Boolean = lyricWidth > measuredWidth

    override fun onDraw(canvas: Canvas) {
        if (isMarqueeMode()) marquee.draw(canvas) else syllable.draw(canvas)
    }

    fun isPlayStarted(): Boolean = if (isMarqueeMode()) {
        true
    } else {
        syllable.isStarted
    }

    fun isPlaying(): Boolean = if (isMarqueeMode()) {
        !marquee.isAnimationFinished()
    } else {
        syllable.isPlaying
    }

    fun isPlayFinished(): Boolean = if (isMarqueeMode()) {
        marquee.isAnimationFinished()
    } else {
        (syllable.lastPosition >= lyric.end)
                || syllable.isFinished
    }

    override fun toString(): String {
        return "LyricLineView{lyric=$lyric,isVisible=$isVisible, lyricWidth=$lyricWidth, lyricHeight=$measuredHeight, isGradientEnabled=${syllable.isGradientEnabled}, scrollXOffset=$scrollXOffset, isScrollFinished=$isScrollFinished, isPlayFinished=${isPlayFinished()}, isPlaying=${isPlaying()}, isPlayStarted=${isPlayStarted()}, isOverflow=${isOverflow()}, isMarqueeMode=${isMarqueeMode()}, isSyllableMode=${isSyllableMode()}, isAttachedToWindow=$isAttachedToWindow"
    }

    internal inner class AnimationDriver : Choreographer.FrameCallback {
        private var running = false
        private var lastFrameNanos = 0L

        fun startIfNoRunning() {
            if (!running && isAttachedToWindow) {
                running = true
                lastFrameNanos = 0L
                post {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }

        fun stop() {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
            lastFrameNanos = 0L
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running || !isAttachedToWindow) {
                running = false
                return
            }

            val deltaNanos = if (lastFrameNanos == 0L) 0L else frameTimeNanos - lastFrameNanos
            lastFrameNanos = frameTimeNanos

            val isMarqueeMode = isMarqueeMode()
            val isOverflow = isOverflow()
            val isSyllableMode = isSyllableMode()

            if (isMarqueeMode) {
                if (!isOverflow || marquee.isAnimationFinished()) {
                    marquee.step(deltaNanos)
                    postInvalidateOnAnimation()
                    return
                }
            }

            if (isSyllableMode) {
                if (isPlayFinished() || (syllable.isScrollOnly && !isOverflow)) {
                    syllable.onFrameUpdate(frameTimeNanos)
                    postInvalidateOnAnimation()
                    return
                }
            }

            var hasChanged = false
            if (isMarqueeMode) {
                marquee.step(deltaNanos)
                hasChanged = true
            } else if (isSyllableMode) {
                hasChanged = syllable.onFrameUpdate(frameTimeNanos)
            }

            if (hasChanged) postInvalidateOnAnimation()

            if (running) {
                Choreographer.getInstance().postFrameCallback(this)
                //Log.d("LyricLineView", "AnimationDriver.doFrame: $frameTimeNanos")
            }
        }
    }

    private var cachedShader: Shader? = null
    private var cachedShaderSignature: Int = 0

    private fun applyCurrentTextColor() {
        val colors = currentTextColors
        if (colors.isEmpty()) {
            textPaint.color = Color.BLACK
            textPaint.shader = null
            return
        }
        textPaint.color = colors.firstOrNull() ?: Color.BLACK
        textPaint.shader = if (colors.size > 1 && lyricWidth > 0f) {
            getRainbowShader(lyricWidth, colors)
        } else {
            null
        }
    }

    private fun getRainbowShader(lyricWidth: Float, colors: IntArray): Shader {
        var sign = 17
        sign = sign * 31 + lyricWidth.hashCode()
        sign = sign * 31 + colors.contentHashCode()

        val shaderCache = cachedShader
        if (shaderCache != null && cachedShaderSignature == sign) {
            return shaderCache
        }
        cachedShaderSignature = sign

        val positions = FloatArray(colors.size) { i ->
            i.toFloat() / (colors.size - 1)
        }
        val shader = LinearGradient(
            0f, 0f, lyricWidth, 0f,
            colors, positions,
            Shader.TileMode.CLAMP
        )
        cachedShader = shader
        return shader
    }
}
