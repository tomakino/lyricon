/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.annotation.CallSuper
import androidx.core.view.contains
import androidx.core.view.forEach
import androidx.core.view.isNotEmpty
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.view.line.LyricLineView
import io.github.proify.lyricon.lyric.view.yoyo.AnimSpeed
import io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets
import io.github.proify.lyricon.lyric.view.yoyo.animateUpdate
import java.util.concurrent.CopyOnWriteArraySet

open class LyricPlayerView(
    context: Context,
    attributes: AttributeSet? = null,
) : LinearLayout(context, attributes), UpdatableColor {

    companion object {
        internal const val KEY_SONG_TITLE_LINE: String = "TitleLine"
        private const val MIN_GAP_DURATION: Long = 8 * 1000
        //private const val TAG = "LyricPlayerView"
    }

    private var isTextMode = false
    private var style = LyricViewStyle()

    private var lineList: List<TimedLine>? = null
    private var timingNavigator: TimingNavigator<TimedLine> = emptyNavigator()
    private var interludeTracker = InterludeTracker(MIN_GAP_DURATION)
    private var currentInterlude: InterludeTracker.Interlude? = null
    private var isInInterlude = false

    private val activeLines = mutableListOf<IRichLyricLine>()
    private val textRecycleView by lazy { RichLyricLineView(context) }
    private val defaultLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private val tempMatches = mutableListOf<TimedLine>()

    private var layoutTransitionHandler: LayoutTransitionX? = null

    val lyricCountChangeListeners = CopyOnWriteArraySet<LyricCountChangeListener>()

    private val mainPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayEnded(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    private val secondaryPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) {
            view.visibleIfChanged = true; updateViewsVisibility()
        }

        override fun onPlayEnded(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    private val globalLayoutListener =
        ViewTreeObserver.OnGlobalLayoutListener { updateViewsVisibility() }

    init {
        orientation = VERTICAL
        updateLayoutTransitionHandler()
        gravity = Gravity.CENTER_VERTICAL
    }

    var isDisplayTranslation = true
        private set

    var isDisplayRoma = true
        private set

    var song: Song? = null
        set(value) {
            isTextMode = false
            if (value != null) {
                val firstActive = activeLines.firstOrNull()
                val exiting = firstActive.isTitleLine() && resolveTitle(value) == firstActive?.text
                if (!exiting) reset()

                val processor = SongPreprocessor(style.placeholder)
                val lines = processor.prepare(value)
                lineList = lines
                timingNavigator = TimingNavigator(lines.toTypedArray())
            } else {
                reset()
                lineList = null
                timingNavigator = emptyNavigator()
            }
            field = value
        }

    var text: String? = null
        set(value) {
            field = value
            if (!isTextMode) {
                reset(); isTextMode = true
            }
            if (value.isNullOrBlank()) {
                removeAllViews(); return
            }

            if (!contains(textRecycleView)) {
                addView(textRecycleView, defaultLp)
                updateTextLineViewStyle(style)
            }
            val old = textRecycleView.line
            val speed = AnimSpeed.fromPref(style.animation.speed)
            val preset = YoYoPresets.getById(style.animation.presetId, speed)

            val line = RichLyricLine(
                text = value.lines().first(),
                translation = value.lines().getOrNull(1),
            )

            if (style.animation.enabled && preset != null) {
                animateUpdate(preset) {
                    textRecycleView.line = line
                    textRecycleView.requestStartMarquee()
                }
            } else {
                textRecycleView.line = line
                textRecycleView.requestStartMarquee()
            }

            lyricCountChangeListeners.forEach { it.onLyricTextChanged(old?.text ?: "", value) }
        }

    fun setStyle(style: LyricViewStyle) {
        this.style = style
        interludeTracker = InterludeTracker(MIN_GAP_DURATION)
        updateTextLineViewStyle(style)
        forEach { if (it is RichLyricLineView) it.setStyle(style) }
        updateViewsVisibility()
    }

    fun getStyle() = style

    fun setTransitionConfig(config: String?) {
        if (_transitionConfig == config) return
        _transitionConfig = config
        updateLayoutTransitionHandler(config)
        forEach { if (it is RichLyricLineView) it.setTransitionConfig(config) }
    }

    private var _transitionConfig: String? = null

    fun updateDisplayTranslation(
        displayTranslation: Boolean = isDisplayTranslation,
        displayRoma: Boolean = isDisplayRoma
    ) {
        isDisplayTranslation = displayTranslation
        isDisplayRoma = displayRoma
        forEach {
            if (it is RichLyricLineView) {
                it.displayTranslation = displayTranslation
                it.displayRoma = displayRoma
                it.notifyLineChanged()
            }
        }
        updateViewsVisibility()
    }

    fun seekTo(position: Long) = updatePosition(position, true)

    fun setPosition(position: Long) = updatePosition(position)

    fun reset() {
        removeAllViews()
        activeLines.clear()
        currentInterlude = null
        if (isInInterlude) exitInterludeMode()
        isInInterlude = false
    }

    override fun removeAllViews() {
        layoutTransition = null
        super.removeAllViews()
    }

    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        style = style.copy(
            primary = style.primary.copy(color = primary),
            secondary = style.secondary.copy(color = primary),
            highlight = Highlight(background, highlight)
        )
        forEach { if (it is UpdatableColor) it.updateColor(primary, background, highlight) }
    }

    fun updateViewsVisibility() {
        applyVisibility()
    }

    // --- internal ---

    private fun updatePosition(position: Long, seekTo: Boolean = false) {
        if (isTextMode) return

        tempMatches.clear()
        timingNavigator.forEachAtOrPrevious(position) { tempMatches.add(it) }
        val matches = tempMatches

        syncViews(matches)

        forEach { view ->
            if (view is RichLyricLineView) {
                if (seekTo) view.seekTo(position) else view.setPosition(position)
            }
        }
        checkInterlude(position, matches)
    }

    private val slotEngine: ViewSlotEngine by lazy {
        ViewSlotEngine(this)
    }

    private fun syncViews(matches: List<TimedLine>) {
        val engine = slotEngine
        val matchLines: List<IRichLyricLine> = matches.map { it.line }
        engine.sync(matchLines, activeLines)

        if (engine.toRemove.isEmpty() && engine.toAdd.isEmpty()) return

        if (activeLines.size == 1 && engine.toRemove.size == 1 && engine.toAdd.size == 1) {
            val recycle = getChildAtOrNull(0) as? RichLyricLineView
            val newLine = engine.toAdd[0]
            if (recycle != null) {
                val speed = AnimSpeed.fromPref(style.animation.speed)
                val preset by lazy { YoYoPresets.getById(style.animation.presetId, speed) }
                if (style.animation.enabled && preset != null) {
                    // 检查是否是标题行过渡
                    val isTitleTransition = recycle.line?.isTitleLine() == true
                    
                    // 重置动画状态
                    recycle.resetAnimationState()
                    activeLines[0] = newLine
                    
                    // 如果是标题行过渡，先更新内容再执行动画
                    if (isTitleTransition) {
                        recycle.line = newLine
                        recycle.requestStartMarquee()
                        updateViewsVisibility()
                    } else {
                        recycle.beginAnimationTransition()
                        recycle.line = newLine
                        animateUpdate(preset!!) {
                            recycle.endAnimationTransition()
                            recycle.requestStartMarquee()
                            updateViewsVisibility()
                        }
                    }
                } else {
                    activeLines[0] = newLine
                    recycle.line = newLine
                    recycle.requestStartMarquee()
                }
            }
        } else {
            engine.toRemove.forEach {
                removeView(it); activeLines.remove(it.line)
            }
            engine.toAdd.forEach { line ->
                activeLines.add(line)
                val view = createDoubleLineView(line)
                autoAddView(view)
                view.requestStartMarquee()
            }
        }

        updateViewsVisibility()

        lyricCountChangeListeners.forEach {
            it.onLyricChanged(engine.toAdd, engine.toRemove.mapNotNull { it.line })
        }
    }

    private fun applyVisibility() {
        val children =
            (0 until childCount).mapNotNull { i -> getChildAtOrNull(i) as? RichLyricLineView }
        if (children.isEmpty()) return

        ViewVisibilityPolicy.apply(ViewVisibilityPolicy.Input(children, style))
        invalidate()
    }

    private fun checkInterlude(position: Long, matches: List<TimedLine>) {
        val resolved = interludeTracker.evaluate(position, matches, currentInterlude)
        if (resolved == currentInterlude) return

        if (currentInterlude != null && resolved == null) {
            currentInterlude = null
            isInInterlude = false
            exitInterludeMode()
        } else if (resolved != null) {
            currentInterlude = resolved
            isInInterlude = true
            enteringInterludeMode(resolved.duration)
        }
    }

    @CallSuper
    protected open fun enteringInterludeMode(duration: Long) {
    }

    @CallSuper
    protected open fun exitInterludeMode() {
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateViewsVisibility()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        reset()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    // --- helpers ---

    private fun createDoubleLineView(line: IRichLyricLine) = RichLyricLineView(
        context,
        displayTranslation = isDisplayTranslation,
        displayRoma = isDisplayRoma,
        enableRelativeProgress = style.primary.relativeProgress,
        enableRelativeProgressHighlight = style.primary.relativeHighlight,
    ).apply {
        this.line = line
        setStyle(style)
        setMainLyricPlayListener(mainPlayListener)
        setSecondaryLyricPlayListener(secondaryPlayListener)
        setTransitionConfig(_transitionConfig)
    }

    private fun autoAddView(view: RichLyricLineView) {
        if (layoutTransition == null && isNotEmpty()) layoutTransition = layoutTransitionHandler
        addView(view, defaultLp)
    }

    private fun updateTextLineViewStyle(style: LyricViewStyle) {
        textRecycleView.setStyle(style)
    }

    private fun updateLayoutTransitionHandler(config: String? = LayoutTransitionX.TRANSITION_CONFIG_SMOOTH) {
        layoutTransitionHandler =
            LayoutTransitionX(config).apply { setAnimateParentHierarchy(true) }
        layoutTransition = null
    }

    private fun resolveTitle(song: Song): String? = when (style.placeholder) {
        TitleSlot.NONE -> null
        TitleSlot.NAME_ARTIST -> {
            val n = song.name
            val a = song.artist
            when {
                !n.isNullOrBlank() && !a.isNullOrBlank() -> "$n - $a"; !n.isNullOrBlank() -> n; else -> null
            }
        }

        TitleSlot.NAME -> song.name?.takeIf { it.isNotBlank() }
    }

    private fun emptyNavigator() = TimingNavigator<TimedLine>(emptyArray())

    interface LyricCountChangeListener {
        fun onLyricTextChanged(old: String, new: String)
        fun onLyricChanged(news: List<IRichLyricLine>, removes: List<IRichLyricLine>)
    }
}

fun IRichLyricLine?.isTitleLine(): Boolean =
    this?.metadata?.getBoolean(LyricPlayerView.KEY_SONG_TITLE_LINE, false) == true
