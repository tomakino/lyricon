/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
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
import io.github.proify.lyricon.lyric.view.yoyo.AnimConfig
import io.github.proify.lyricon.lyric.view.yoyo.AnimSpeed
import io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets
import io.github.proify.lyricon.lyric.view.yoyo.animateUpdate
import java.util.concurrent.CopyOnWriteArraySet

open class LyricPlayerView(
    context: Context,
    attributes: AttributeSet? = null,
) : LinearLayout(context, attributes), UpdatableColor {

    companion object {
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

    private val activeLines = mutableListOf<TimedLine>()
    private var titleLine: RichLyricLine? = null
    private var titleView: RichLyricLineView? = null

    /** 是否暂停所有行视图的帧推进。 */
    var animationsPaused: Boolean = false
        private set
    private val textRecycleView by lazy { RichLyricLineView(context) }
    private val defaultLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private val tempMatches = mutableListOf<TimedLine>()

    private var layoutTransitionHandler: LayoutTransitionX? = null

    /** 行切换状态；Idle 之外只允许一个切换存活。 */
    private sealed interface SwitchState {
        data object Idle : SwitchState
        data class Running(val generation: Int, val fromTitle: Boolean) : SwitchState
    }

    private var switchState: SwitchState = SwitchState.Idle

    /** 切换世代，用于忽略被取消的旧动画回调。 */
    private var switchGeneration = 0

    private val switching: Boolean get() = switchState != SwitchState.Idle
    private val switchingTitleSlot: Boolean
        get() = (switchState as? SwitchState.Running)?.fromTitle == true

    /** 文本切换动画期间收到的最新文本，动画结束后应用。 */
    private var pendingText: String? = null
    private var hasPendingText = false

    /** 最近一次外部播放位置。 */
    private var lastPosition: Long? = null

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
            // 整歌替换优先于行切换动画：切歌/二次分发立即生效，旧视图不等待动画。
            if (switching) cancelActiveSwitch()
            isTextMode = false
            if (value != null) {
                val prepared = SongPreprocessor(style.placeholder).prepare(value)
                titleLine = prepared.title
                clearLyricViews()
                lineList = prepared.lines
                timingNavigator = TimingNavigator(prepared.lines.toTypedArray())
                field = value

                val position = lastPosition
                if (position != null) {
                    updatePosition(position)
                } else {
                    syncTitle()
                }
            } else {
                reset()
                lineList = null
                timingNavigator = emptyNavigator()
                field = value
            }
        }

    var text: String? = null
        set(value) {
            if (switching) {
                pendingText = value
                hasPendingText = true
                return
            }
            field = value
            if (!isTextMode) {
                reset(); isTextMode = true
            }
            if (value.isNullOrBlank()) {
                removeAllViews(); return
            }

            if (!contains(textRecycleView)) {
                addView(textRecycleView, defaultLp)
                textRecycleView.setAnimationsPaused(animationsPaused)
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
                animateUpdate(preset, onCompleted = { cancelled ->
                    if (!cancelled) {
                        lyricCountChangeListeners.forEach {
                            it.onLyricTextChanged(old?.text ?: "", value)
                        }
                    }
                }) {
                    textRecycleView.line = line
                    textRecycleView.requestStartMarquee()
                }
            } else {
                textRecycleView.line = line
                textRecycleView.requestStartMarquee()
                lyricCountChangeListeners.forEach { it.onLyricTextChanged(old?.text ?: "", value) }
            }
        }

    /** 暂停或恢复所有行视图的帧推进。 */
    fun setAnimationsPaused(paused: Boolean) {
        if (animationsPaused == paused) return
        animationsPaused = paused
        forEach {
            if (it is RichLyricLineView) it.setAnimationsPaused(paused)
        }
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
                // setter 内部只在开关真正变化时重装配次行，避免重复 buildSecondary。
                it.displayTranslation = displayTranslation
                it.displayRoma = displayRoma
            }
        }
        updateViewsVisibility()
    }

    fun seekTo(position: Long) = updatePosition(position, true)

    fun setPosition(position: Long) = updatePosition(position)

    fun reset() {
        switchGeneration++
        switchState = SwitchState.Idle
        pendingText = null
        hasPendingText = false
        lastPosition = null
        titleLine = null
        titleView = null
        removeAllViews()
        activeLines.clear()
        currentInterlude = null
        if (isInInterlude) exitInterludeMode()
        isInInterlude = false
    }

    override fun removeAllViews() {
        layoutTransition = null
        lastVisibilityKey = null
        super.removeAllViews()
    }

    /** 放弃当前行切换动画并清除旧视图，供整歌替换立即生效。 */
    private fun cancelActiveSwitch() {
        switchGeneration++
        switchState = SwitchState.Idle
        pendingText = null
        hasPendingText = false
        removeAllViews()
        titleView = null
        activeLines.clear()
        currentInterlude = null
        if (isInInterlude) exitInterludeMode()
        isInInterlude = false
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
        lastPosition = position
        if (switching) {
            forEach { view ->
                if (view is RichLyricLineView) {
                    if (seekTo) view.seekTo(position) else view.setPosition(position)
                }
            }
            return
        }

        tempMatches.clear()
        timingNavigator.forEachAtOrPrevious(position) { tempMatches.add(it) }
        val matches = tempMatches

        syncViews(matches)
        if (!switching) syncTitle()

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
        if (switching) return

        val engine = slotEngine
        engine.sync(matches, activeLines)

        val titleSwitchPending = activeLines.isEmpty() &&
                engine.toRemove.isEmpty() && engine.toAdd.size == 1 &&
                titleView?.let { it.timedLine == null && it.visibility == View.VISIBLE } == true

        // 已经进入歌词区时，任何标题/无 timedLine 的旧视图都必须隐藏，
        // 避免切歌/更新数据后旧标题视图残留在歌词行上方。
        if (matches.isNotEmpty() && !titleSwitchPending) {
            hideStaleTitleViews()
        }

        if (engine.toRemove.isEmpty() && engine.toAdd.isEmpty()) return

        val speed = AnimSpeed.fromPref(style.animation.speed)
        val animatedPreset = if (style.animation.enabled) {
            YoYoPresets.getById(style.animation.presetId, speed)
        } else {
            null
        }

        // 标题槽位 → 第一行歌词
        if (activeLines.isEmpty() && engine.toRemove.isEmpty() && engine.toAdd.size == 1) {
            val title = titleView
            val newLine = engine.toAdd[0]
            if (title != null && title.timedLine == null && title.visibility == View.VISIBLE) {
                if (animatedPreset != null) {
                    startViewSwitch(title, newLine, animatedPreset)
                } else {
                    applyViewSwitch(title, newLine)
                }
                return
            }
        }

        // 歌词 → 标题槽位（进度回退到标题区）
        if (activeLines.size == 1 && engine.toRemove.size == 1 && engine.toAdd.isEmpty()) {
            val title = titleLine
            if (title != null) {
                val recycle = engine.toRemove.first()
                if (animatedPreset != null) {
                    startTitleSwitch(recycle, title, animatedPreset)
                } else {
                    applyTitleSwitch(recycle, title)
                }
                return
            }
        }

        if (activeLines.size == 1 && engine.toRemove.size == 1 && engine.toAdd.size == 1) {
            val recycle = (0 until childCount)
                .mapNotNull { getChildAtOrNull(it) as? RichLyricLineView }
                .firstOrNull { it.timedLine != null }
            val newLine = engine.toAdd[0]
            if (recycle != null) {
                if (animatedPreset != null) {
                    startViewSwitch(recycle, newLine, animatedPreset)
                } else {
                    applyViewSwitch(recycle, newLine)
                }
            }
        } else {
            val removed = mutableListOf<TimedLine>()
            engine.toRemove.forEach { view ->
                val timed = view.timedLine
                if (timed != null) {
                    removed.add(timed)
                    activeLines.removeAll { it.line == timed.line }
                }
                removeView(view)
            }
            engine.toAdd.forEach { timed ->
                activeLines.add(timed)
                val view = createDoubleLineView(timed)
                autoAddView(view)
                view.requestStartMarquee()
            }
            updateViewsVisibility()
            notifyLyricChanged(engine.toAdd, removed)
        }
    }

    private fun startViewSwitch(
        recycle: RichLyricLineView,
        newLine: TimedLine,
        preset: Pair<AnimConfig, AnimConfig>,
    ) {
        val generation = ++switchGeneration
        switchState = SwitchState.Running(generation, recycle === titleView)
        recycle.resetAnimationState()
        recycle.beginAnimationTransition()
        recycle.setLine(newLine.line, false)
        animateUpdate(preset, onCompleted = { cancelled ->
            if ((switchState as? SwitchState.Running)?.generation == generation) {
                if (cancelled) {
                    switchState = SwitchState.Idle
                } else {
                    commitViewSwitch(recycle, newLine)
                }
            }
        }) {
            recycle.endAnimationTransition()
            recycle.requestStartMarquee()
            updateViewsVisibility()
        }
    }

    private fun startTitleSwitch(
        recycle: RichLyricLineView,
        title: RichLyricLine,
        preset: Pair<AnimConfig, AnimConfig>,
    ) {
        val generation = ++switchGeneration
        switchState = SwitchState.Running(generation, fromTitle = false)
        recycle.resetAnimationState()
        recycle.beginAnimationTransition()
        recycle.setLine(title, true)
        animateUpdate(preset, onCompleted = { cancelled ->
            if ((switchState as? SwitchState.Running)?.generation == generation) {
                if (cancelled) {
                    switchState = SwitchState.Idle
                } else {
                    commitTitleSwitch(recycle)
                }
            }
        }) {
            recycle.endAnimationTransition()
            recycle.requestStartMarquee()
            updateViewsVisibility()
        }
    }

    private fun commitTitleSwitch(recycle: RichLyricLineView) {
        switchState = SwitchState.Idle
        val old = activeLines.firstOrNull()
        activeLines.clear()
        recycle.timedLine = null
        titleView = recycle
        notifyLyricChanged(emptyList(), old?.let { listOf(it) } ?: emptyList())
        updateViewsVisibility()
        syncTitle()

        val position = lastPosition
        val applied = flushPending()
        if (!applied && position != null && !switching && !isTextMode) {
            updatePosition(position)
        }
    }

    private fun applyTitleSwitch(recycle: RichLyricLineView, title: RichLyricLine) {
        switchState = SwitchState.Idle
        val old = activeLines.firstOrNull()
        recycle.resetAnimationState()
        recycle.setLine(title, true)
        recycle.timedLine = null
        activeLines.clear()
        titleView = recycle
        recycle.requestStartMarquee()
        updateViewsVisibility()
        syncTitle()
        notifyLyricChanged(emptyList(), old?.let { listOf(it) } ?: emptyList())
    }

    private fun commitViewSwitch(recycle: RichLyricLineView, newLine: TimedLine) {
        switchState = SwitchState.Idle
        if (recycle === titleView) titleView = null
        val old = activeLines.firstOrNull()
        activeLines.clear()
        activeLines.add(newLine)
        recycle.timedLine = newLine
        notifyLyricChanged(listOf(newLine), old?.let { listOf(it) } ?: emptyList())
        updateViewsVisibility()

        val position = lastPosition
        val applied = flushPending()
        if (!applied && position != null && !switching && !isTextMode) {
            updatePosition(position)
        }
    }

    private fun applyViewSwitch(recycle: RichLyricLineView, newLine: TimedLine) {
        switchState = SwitchState.Idle
        if (recycle === titleView) titleView = null
        val old = activeLines.firstOrNull()
        recycle.resetAnimationState()
        recycle.setLine(newLine.line, false)
        recycle.timedLine = newLine
        activeLines.clear()
        activeLines.add(newLine)
        recycle.requestStartMarquee()
        updateViewsVisibility()
        notifyLyricChanged(listOf(newLine), old?.let { listOf(it) } ?: emptyList())
    }

    private fun notifyLyricChanged(added: List<TimedLine>, removed: List<TimedLine>) {
        val addedLyrics = added.map { it.line }
        val removedLyrics = removed.map { it.line }
        if (addedLyrics.isEmpty() && removedLyrics.isEmpty()) return
        lyricCountChangeListeners.forEach { it.onLyricChanged(addedLyrics, removedLyrics) }
    }

    private fun flushPending(): Boolean {
        if (!hasPendingText) return false
        hasPendingText = false
        val value = pendingText
        pendingText = null
        text = value
        return true
    }

    private data class VisibilityKey(
        val views: List<RichLyricLineView>,
        val finished: List<Boolean>,
        val secondaryStarted: List<Boolean>,
        val alwaysShowSecondary: List<Boolean>,
        val visible: List<Boolean>,
        val style: LyricViewStyle,
        val isTextMode: Boolean,
        val width: Int,
        val height: Int,
    )

    private var lastVisibilityKey: VisibilityKey? = null

    private fun applyVisibility() {
        val children = slotViews()
        if (children.isEmpty()) return

        val key = VisibilityKey(
            views = children,
            finished = children.map { it.main.isFinished },
            secondaryStarted = children.map { it.secondary.isStarted },
            alwaysShowSecondary = children.map { it.alwaysShowSecondary },
            visible = children.map { it.visibility == View.VISIBLE },
            style = style,
            isTextMode = isTextMode,
            width = width,
            height = height,
        )
        if (key == lastVisibilityKey) return
        lastVisibilityKey = key

        ViewVisibilityPolicy.apply(ViewVisibilityPolicy.Input(children, style))
        invalidate()
    }

    /**
     * 当前可见性策略的槽位视图。
     *
     * 顺序固定为：标题槽位（仅标题→第一行切换期间）在前，其后是歌词槽位按行开始时间排序。
     * 不再依赖子视图添加顺序，也不把无 timedLine 的残留视图交给策略。
     */
    private fun slotViews(): List<RichLyricLineView> {
        if (isTextMode) {
            return if (contains(textRecycleView)) listOf(textRecycleView) else emptyList()
        }

        val lyrics = (0 until childCount)
            .mapNotNull { getChildAtOrNull(it) as? RichLyricLineView }
            .filter { it.timedLine != null }
            .sortedBy { it.timedLine?.line?.begin ?: Long.MAX_VALUE }

        val title = titleView
        return if (switchingTitleSlot && title != null) listOf(title) + lyrics else lyrics
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

    private fun createDoubleLineView(timed: TimedLine) = RichLyricLineView(
        context,
        displayTranslation = isDisplayTranslation,
        displayRoma = isDisplayRoma,
        enableRelativeProgress = style.primary.relativeProgress,
        enableRelativeProgressHighlight = style.primary.relativeHighlight,
    ).apply {
        timedLine = timed
        setLine(timed.line, false)
        setAnimationsPaused(this@LyricPlayerView.animationsPaused)
        setStyle(style)
        setMainLyricPlayListener(mainPlayListener)
        setSecondaryLyricPlayListener(secondaryPlayListener)
        setTransitionConfig(_transitionConfig)
    }

    private fun createTitleView() = RichLyricLineView(
        context,
        displayTranslation = isDisplayTranslation,
        displayRoma = isDisplayRoma,
        enableRelativeProgress = style.primary.relativeProgress,
        enableRelativeProgressHighlight = style.primary.relativeHighlight,
    ).apply {
        setLine(titleLine, true)
        setAnimationsPaused(this@LyricPlayerView.animationsPaused)
        setStyle(style)
        setMainLyricPlayListener(mainPlayListener)
        setSecondaryLyricPlayListener(secondaryPlayListener)
        setTransitionConfig(_transitionConfig)
    }

    private fun syncTitle() {
        if (switching) return

        val line = titleLine
        val view = titleView
        if (line == null) {
            view?.visibilityIfChanged = View.GONE
            return
        }

        val firstBegin = lineList?.firstOrNull()?.begin
        val position = lastPosition ?: 0L
        // 已有歌词行时绝不再显示标题；否则切歌/seek 边界会出现标题行与歌词行并存。
        val show = activeLines.isEmpty() && (firstBegin == null || position < firstBegin)
        if (!show) {
            view?.visibilityIfChanged = View.GONE
            return
        }

        val isNew = view == null
        val target = view ?: createTitleView().also {
            titleView = it
            autoAddView(it)
        }
        val wasVisible = target.visibility == View.VISIBLE
        if (target.line?.text != line.text) {
            target.setLine(line, true)
            target.requestStartMarquee()
        }
        target.visibilityIfChanged = View.VISIBLE
        if (isNew || !wasVisible) target.requestStartMarquee()
    }

    private fun hideStaleTitleViews() {
        forEach { child ->
            if (child is RichLyricLineView && child.timedLine == null && child !== titleView) {
                child.visibilityIfChanged = View.GONE
            }
        }
        titleView?.let { view ->
            if (view.timedLine == null) view.visibilityIfChanged = View.GONE
        }
    }

    private fun clearLyricViews() {
        val views = mutableListOf<RichLyricLineView>()
        forEach { if (it is RichLyricLineView && it.timedLine != null) views.add(it) }
        views.forEach { removeView(it) }
        activeLines.clear()
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

