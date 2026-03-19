/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.contains
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.view.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.LyricPlayerView
import io.github.proify.lyricon.lyric.view.visibleIfChanged
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric.LyricType.NONE
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric.LyricType.SONG
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric.LyricType.TEXT

@SuppressLint("ViewConstructor")
class StatusBarLyric(
    context: Context,
    initialStyle: LyricStyle,
    linkedTextView: TextView?
) : LinearLayout(context) {

    companion object {
        const val VIEW_TAG: String = "lyricon:lyric_view"
        private const val TAG = "StatusBarLyric"
    }

    val logoView: SuperLogo = SuperLogo(context).apply {
        this.linkedTextView = linkedTextView
    }

    val textView: SuperText = SuperText(context).apply {
        this.linkedTextView = linkedTextView
        eventListener = object : SuperText.EventListener {
            override fun enteringInterludeMode(duration: Long) {
                logoView.syncProgress(0, duration)
            }

            override fun exitInterludeMode() {
                logoView.clearProgress()
            }
        }
    }

    // --- 对外状态 ---

    var currentStatusColor: StatusColor = StatusColor()
        private set

    var isSleepMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            Log.d(TAG, "休眠模式：$value")
            if (value) {
                pendingSleepData = PendingData()
            } else {
                pendingSleepData?.let { seekTo(it.position) }
                pendingSleepData = null
            }
        }

    // --- 样式 / 播放状态 ---

    private var currentStyle: LyricStyle = initialStyle
    private var isPlaying: Boolean = false
    private var isOplusCapsuleShowing: Boolean = false
    private var userHideLyric: Boolean = false

    var onPlayingChanged: ((Boolean) -> Unit)? = null

    // 上一次 Logo gravity，用于避免重复重排
    private var lastLogoGravity: Int = -114

    // 休眠期间缓存的进度数据
    private var pendingSleepData: PendingData? = null

    // --- 歌词内容与超时状态 ---

    private var hasLyricContent: Boolean = false
    private var lyricTimedOut: Boolean = false
    private var currentLyric: String? = null

    // 主线程调度器
    private val mainHandler: Handler = Handler(context.mainLooper)

    // 当前生效的超时 Runnable
    private var lyricTimeoutTask: Runnable? = null

    // 跟随系统隐藏状态栏内容
    var isDisabledVisible = false
        set(value) {
            field = value
            updateVisibility()
        }

    // --- 系统 / 辅助组件 ---

    private val keyguardManager: KeyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    /**
     * 单次布局变更动画
     * 用于样式或尺寸突变时的过渡
     */
    private val singleLayoutTransition: LayoutTransition = LayoutTransitionX().apply {
        addTransitionListener(object : LayoutTransition.TransitionListener {

            override fun startTransition(
                transition: LayoutTransition?, container: ViewGroup?,
                view: View?, transitionType: Int
            ) = Unit

            override fun endTransition(
                transition: LayoutTransition?, container: ViewGroup?,
                view: View?, transitionType: Int
            ) {
                disableTransitionType(LayoutTransition.CHANGING)
                layoutTransition = null
            }
        })
    }

    // TextView 子视图结构变化监听，用于刷新可见性
    private val textHierarchyChangeListener = object : OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) = updateVisibility()
        override fun onChildViewRemoved(parent: View?, child: View?) = updateVisibility()
    }

    // 歌词变化监听，用于重置超时逻辑
    private val lyricCountChangeListener =
        object : LyricPlayerView.LyricCountChangeListener {

            override fun onLyricTextChanged(old: String, new: String) {
                currentLyric = new
                refreshLyricTimeoutState()
            }

            override fun onLyricChanged(
                news: List<IRichLyricLine>,
                removes: List<IRichLyricLine>
            ) {
                currentLyric = news.lastOrNull()?.text
                refreshLyricTimeoutState()
            }
        }

    init {
        tag = VIEW_TAG
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        layoutTransition = null

        addView(
            textView,
            LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            }
        )

        updateLogoLocation()
        applyInitialStyle(initialStyle)

        textView.setOnHierarchyChangeListener(textHierarchyChangeListener)
        textView.lyricCountChangeListeners += lyricCountChangeListener


    }

    // --- 公开 API ---

    fun updateStyle(style: LyricStyle) {
        triggerSingleTransition()
        currentStyle = style
        logoView.applyStyle(style)
        updateLogoLocation()
        textView.applyStyle(style)
        updateLayoutConfig(style)

        refreshLyricTimeoutState()
        requestLayout()
    }

    fun setStatusBarColor(color: StatusColor) {
        currentStatusColor = color
        logoView.setStatusBarColor(color)
        textView.setStatusBarColor(color)
    }

    private var lastPlaying: Boolean? = null
    private var lastSong: Song? = null
    private var lastText: String? = null
    private var lyricType = NONE

    fun setPlaying(playing: Boolean) {
        if (lastPlaying == playing) return
        Log.d(TAG, "setPlaying: $playing")

        lastPlaying = playing
        isPlaying = playing
        onPlayingChanged?.invoke(playing)

        if (!playing) {
            textView.reset()
        } else {
            when (lyricType) {
                NONE -> Unit
                SONG -> setSong(lastSong)
                TEXT -> setText(lastText)
            }
        }

        refreshLyricTimeoutState()
        updateVisibility()
    }

    fun isHideOnLockScreen() =
        currentStyle.basicStyle.hideOnLockScreen && keyguardManager.isKeyguardLocked

    fun updateVisibility() {
        val shouldShow = isPlaying
                && !isHideOnLockScreen()
                && textView.shouldShow()
                && !lyricTimedOut
                && !userHideLyric
                && !isDisabledVisible

        visibleIfChanged = shouldShow

        Log.d(TAG, "updateVisibility: $shouldShow")
        Log.d(TAG, "textVisibility: ${textView.isVisible}")
    }

    fun setUserHideLyric(hide: Boolean) {
        if (userHideLyric == hide) return
        userHideLyric = hide
        updateVisibility()
    }

    fun setSong(song: Song?) {
        lyricType = SONG
        lastSong = song
        textView.song = song
        hasLyricContent = !song?.lyrics.isNullOrEmpty()
        refreshLyricTimeoutState()
    }

    fun setText(text: String?) {
        lyricType = TEXT
        lastText = text

        textView.text = text
        hasLyricContent = !text.isNullOrBlank()
        refreshLyricTimeoutState()
    }

    fun seekTo(position: Long) {
        if (isSleepMode) {
            pendingSleepData?.position = position
            return
        }
        textView.seekTo(position)
        refreshLyricTimeoutState()
    }

    fun setPosition(position: Long) {
        if (isSleepMode) {
            pendingSleepData?.position = position
            return
        }
        textView.setPosition(position)
    }

    fun updateDisplayTranslation(
        displayTranslation: Boolean = textView.isDisplayTranslation,
        displayRoma: Boolean = textView.isDisplayRoma
    ) {
        textView.updateDisplayTranslation(displayTranslation, displayRoma)
    }

    fun setOplusCapsuleVisibility(visible: Boolean) {
        isOplusCapsuleShowing = visible
        triggerSingleTransition()
        updateWidthInternal(currentStyle)
        logoView.oplusCapsuleShowing = visible
    }

    // --- 内部逻辑 ---

    private fun applyInitialStyle(style: LyricStyle) {
        currentStyle = style
        logoView.applyStyle(style)
        textView.applyStyle(style)
        updateLayoutConfig(style)
    }

    private fun updateLogoLocation() {
        val logoStyle = currentStyle.packageStyle.logo
        val gravity = logoStyle.gravity
        if (gravity == lastLogoGravity) return
        lastLogoGravity = gravity

        if (contains(logoView)) removeView(logoView)
        val textIndex = indexOfChild(textView).coerceAtLeast(0)

        when (gravity) {
            LogoStyle.GRAVITY_START -> addView(logoView, textIndex)
            LogoStyle.GRAVITY_END -> addView(logoView, textIndex + 1)
            else -> addView(logoView, textIndex)
        }
    }

    private fun updateLayoutConfig(style: LyricStyle) {
        val basic = style.basicStyle
        val margins = basic.margins
        val paddings = basic.paddings

        ensureMarginLayoutParams().apply {
            width = calculateContainerWidth(basic)
            leftMargin = margins.left.dp
            topMargin = margins.top.dp
            rightMargin = margins.right.dp
            bottomMargin = margins.bottom.dp
        }
        updateTextViewWidthMode(basic)

        updatePadding(
            paddings.left.dp,
            paddings.top.dp,
            paddings.right.dp,
            paddings.bottom.dp
        )
    }

    private fun updateWidthInternal(style: LyricStyle) {
        val width = calculateContainerWidth(style.basicStyle)
        ensureMarginLayoutParams().width = width
        requestLayout()
        Log.d(TAG, "updateWidthInternal: $width")
    }

    private fun calculateContainerWidth(basicStyle: BasicStyle): Int {
        return if (basicStyle.dynamicWidthEnabled) {
            LayoutParams.WRAP_CONTENT
        } else {
            calculateTargetWidth(basicStyle).dp
        }
    }

    private fun updateTextViewWidthMode(basicStyle: BasicStyle) {
        val lp = (textView.layoutParams as? LayoutParams)
            ?: LayoutParams(0, LayoutParams.WRAP_CONTENT)
        if (basicStyle.dynamicWidthEnabled) {
            lp.width = LayoutParams.WRAP_CONTENT
            lp.weight = 0f
        } else {
            lp.width = 0
            lp.weight = 1f
        }
        textView.layoutParams = lp
    }

    private fun calculateTargetWidth(basicStyle: BasicStyle): Float {
        return if (isOplusCapsuleShowing) {
            basicStyle.widthInColorOSCapsuleMode
        } else {
            basicStyle.width
        }
    }

    private fun ensureMarginLayoutParams(): MarginLayoutParams {
        val lp = layoutParams as? MarginLayoutParams
            ?: MarginLayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            )
        if (layoutParams == null) layoutParams = lp
        return lp
    }

    private fun triggerSingleTransition() {
        singleLayoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        layoutTransition = singleLayoutTransition
    }

    private fun refreshLyricTimeoutState() {
        resetLyricTimeout()

        val basicStyleConfig = currentStyle.basicStyle

        val noLyricTimeoutSec = basicStyleConfig.noLyricHideTimeout
        val noUpdateTimeoutSec = basicStyleConfig.noUpdateHideTimeout
        val keywordTimeoutSec = basicStyleConfig.keywordHideTimeout

        val shouldHideWhenNoLyric = noLyricTimeoutSec > 0
        val shouldHideWhenNoUpdate = noUpdateTimeoutSec > 0
        val shouldHideWhenKeywordMatched = keywordTimeoutSec > 0

        val timeoutSec = when {
            shouldHideWhenNoLyric && !hasLyricContent -> noLyricTimeoutSec

            hasLyricContent -> {
                val keywordMatched =
                    shouldHideWhenKeywordMatched && !currentLyric.isNullOrEmpty() &&
                            basicStyleConfig.keywordsHidePattern.orEmpty()
                                .any { it.containsMatchIn(currentLyric.orEmpty()) }

                when {
                    keywordMatched -> keywordTimeoutSec
                    shouldHideWhenNoUpdate -> noUpdateTimeoutSec
                    else -> -1
                }
            }

            else -> -1
        }

        if (timeoutSec > 0) {
            val timeoutRunnable = Runnable {
                lyricTimedOut = true
                updateVisibility()
            }
            lyricTimeoutTask = timeoutRunnable
            mainHandler.postDelayed(timeoutRunnable, timeoutSec * 1000L)
        }

        updateVisibility()
    }

    private fun resetLyricTimeout() {
        lyricTimedOut = false
        lyricTimeoutTask?.let { mainHandler.removeCallbacks(it) }
        lyricTimeoutTask = null
    }

    private class PendingData(var position: Long = 0)

    private enum class LyricType {
        NONE, SONG, TEXT
    }
}
