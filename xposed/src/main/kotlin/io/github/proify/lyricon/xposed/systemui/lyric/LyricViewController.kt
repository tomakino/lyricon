/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import androidx.core.view.isVisible
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.statusbarlyric.SuperLogo
import io.github.proify.lyricon.xposed.systemui.setting.LyricPrefs
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.systemui.util.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.util.XiaomiIslandHooker
import java.io.File

object LyricViewController : ActivePlayerListener, Handler.Callback,
    OplusCapsuleHooker.CapsuleStateChangeListener,
    NotificationCoverHelper.OnCoverUpdateListener {

    private const val TAG = "LyricViewController"
    private const val DEBUG = true

    private const val MSG_PROVIDER_CHANGED = 1
    private const val MSG_SONG_CHANGED = 2
    private const val MSG_PLAYBACK_STATE = 3
    private const val MSG_POSITION = 4
    private const val MSG_SEEK_TO = 5
    private const val MSG_SEND_TEXT = 6
    private const val MSG_TRANSLATION_TOGGLE = 7
    private const val MSG_SHOW_ROMA = 8
    private const val MSG_SONG_TRANSLATED = 9
    private const val MSG_SONG_TRANSLATION_TIMEOUT = 10
    private const val MSG_VENDOR_SYNC_TICK = 11

    private const val UPDATE_INTERVAL_MS = 1000L / 60L
    private const val TRANSLATION_ONLY_WAIT_TIMEOUT_MS = 2500L
    private const val PLAYBACK_ACTIVE_STALE_MS = 2500L

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var activePackage: String = ""
        private set

    @Volatile
    var providerInfo: ProviderInfo? = null
        private set

    @Volatile
    private var isDisplayTranslation: Boolean = true

    @Volatile
    private var lastSong: Song? = null

    private val uiHandler by lazy { Handler(Looper.getMainLooper(), this) }

    private var lastPostTime = 0L
    private var songVersion: Long = 0L
    @Volatile
    private var lastPositionUpdateAt: Long = 0L

    init {
        OplusCapsuleHooker.registerListener(this)
        NotificationCoverHelper.registerListener(this)
    }

    ////////// 非UI线程方法 /////////

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "onActiveProviderChanged: $providerInfo")

        uiHandler.obtainMessage(MSG_PROVIDER_CHANGED, providerInfo).sendToTarget()
    }

    override fun onSongChanged(song: Song?) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "onSongChanged: $song")

        uiHandler.obtainMessage(MSG_SONG_CHANGED, song).sendToTarget()
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "onPlaybackStateChanged: $isPlaying")

        this.isPlaying = isPlaying
        if (isPlaying) {
            lastPositionUpdateAt = SystemClock.uptimeMillis()
            uiHandler.removeMessages(MSG_VENDOR_SYNC_TICK)
            uiHandler.sendEmptyMessageDelayed(MSG_VENDOR_SYNC_TICK, PLAYBACK_ACTIVE_STALE_MS)
        } else {
            uiHandler.removeMessages(MSG_VENDOR_SYNC_TICK)
        }
        uiHandler.obtainMessage(MSG_PLAYBACK_STATE, if (isPlaying) 1 else 0, 0).sendToTarget()
    }

    override fun onPositionChanged(position: Long) {
        // if (DEBUG) YLog.debug(tag = TAG, msg = "onPositionChanged: $position")

        val now = SystemClock.uptimeMillis()
        lastPositionUpdateAt = now
        uiHandler.removeMessages(MSG_VENDOR_SYNC_TICK)
        uiHandler.sendEmptyMessageDelayed(MSG_VENDOR_SYNC_TICK, PLAYBACK_ACTIVE_STALE_MS)

        // 移除队列中旧的进度消息，确保合并
        uiHandler.removeMessages(MSG_POSITION)

        val msg = uiHandler.obtainMessage(MSG_POSITION)
        // 位运算拆分 Long 到两个 Int，避免 Long 对象装箱分配
        msg.arg1 = (position shr 32).toInt()
        msg.arg2 = (position and 0xFFFFFFFFL).toInt()

        val timePassed = now - lastPostTime
        if (timePassed >= UPDATE_INTERVAL_MS) {
            uiHandler.sendMessage(msg)
            lastPostTime = now
        } else {
            // 频率限制：如果回调过快，则延迟到下一个周期执行
            uiHandler.sendMessageDelayed(msg, UPDATE_INTERVAL_MS - timePassed)
        }
    }

    override fun onSeekTo(position: Long) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "onSeekTo: $position")
        lastPositionUpdateAt = SystemClock.uptimeMillis()
        uiHandler.removeMessages(MSG_VENDOR_SYNC_TICK)
        uiHandler.sendEmptyMessageDelayed(MSG_VENDOR_SYNC_TICK, PLAYBACK_ACTIVE_STALE_MS)

        val msg = uiHandler.obtainMessage(MSG_SEEK_TO)
        msg.arg1 = (position shr 32).toInt()
        msg.arg2 = (position and 0xFFFFFFFFL).toInt()
        msg.sendToTarget()
    }

    override fun onSendText(text: String?) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "onSendText: $text")

        uiHandler.obtainMessage(MSG_SEND_TEXT, text).sendToTarget()
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "onDisplayTranslationChanged: $isDisplayTranslation")

        this.isDisplayTranslation = isDisplayTranslation
        uiHandler.obtainMessage(MSG_TRANSLATION_TOGGLE, if (isDisplayTranslation) 1 else 0, 0)
            .sendToTarget()
    }

    override fun onDisplayRomaChanged(displayRoma: Boolean) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "onDisplayRomaChanged: $displayRoma")

        uiHandler.obtainMessage(MSG_SHOW_ROMA, if (displayRoma) 1 else 0, 0)
            .sendToTarget()
    }

    // --- 集中式 UI 处理逻辑 ---
    override fun handleMessage(msg: Message): Boolean {
        if (msg.what == MSG_VENDOR_SYNC_TICK) {
            syncVendorTemporaryUi()
            return true
        }

        when (msg.what) {
            // provider改变时，重置歌曲版本号和当前歌曲
            MSG_PROVIDER_CHANGED -> {
                songVersion++
                lastSong = null
            }
            // 歌曲改变时，更新当前歌曲并触发自动翻译
            MSG_SONG_CHANGED -> {
                lastSong = msg.obj as? Song
                dispatchAutoTranslation(lastSong)
            }
            // 翻译切换时，根据状态触发自动翻译
            MSG_TRANSLATION_TOGGLE -> {
                if (msg.arg1 == 1 || LyricPrefs.isTranslationOnlyInLyricEnabled()) {
                    dispatchAutoTranslation(lastSong)
                }
            }
        }

        var ok = true
        forControllerEach {
            ok = handleMessageInternal(msg, this)
        }
        syncVendorTemporaryUi()
        return ok
    }

    fun handleMessageInternal(msg: Message, controller: StatusBarViewController): Boolean {
        try {
            val view = controller.lyricView
            when (msg.what) {
                MSG_PROVIDER_CHANGED -> {
                    val provider = msg.obj as? ProviderInfo
                    this.providerInfo = provider
                    activePackage = providerInfo?.playerPackageName.orEmpty()
                    LyricPrefs.activePackageName = activePackage

                    uiHandler.removeMessages(MSG_SONG_TRANSLATION_TIMEOUT)
                    view.setSong(null)
                    view.setPlaying(false)
                    controller.updateLyricStyle(LyricPrefs.getLyricStyle())
                    view.updateVisibility()
                    applyLyricTranslationDisplay(view)

                    view.logoView.apply {
                        val activePackage = this@LyricViewController.activePackage
                        this.activePackage = activePackage
                        val cover = NotificationCoverHelper.getCoverFile(activePackage)
                        coverFile = cover
                        controller.updateCoverThemeColors(cover)
                        post { providerLogo = provider?.logo }
                    }
                }

                MSG_SONG_CHANGED -> {
                    val song = msg.obj as? Song
                    val flags = applyLyricTranslationDisplay(view)
                    uiHandler.removeMessages(MSG_SONG_TRANSLATION_TIMEOUT)
                    if (DEBUG) {
                        val first = song?.lyrics?.firstOrNull()
                        YLog.debug(
                            tag = TAG,
                            msg = "MSG_SONG_CHANGED flags=$flags firstLine(text=${first?.text}, translation=${first?.translation}, secondary=${first?.secondary})"
                        )
                    }

                    if (song == null) {
                        view.setSong(null)
                    } else if (shouldWaitForAutoTranslation(song, flags)) {
                        val timeout = uiHandler.obtainMessage(MSG_SONG_TRANSLATION_TIMEOUT, song)
                        timeout.arg1 = (songVersion shr 32).toInt()
                        timeout.arg2 = (songVersion and 0xFFFFFFFFL).toInt()
                        uiHandler.sendMessageDelayed(timeout, TRANSLATION_ONLY_WAIT_TIMEOUT_MS)
                        view.setSong(null)
                    } else {
                        view.setSong(
                            if (flags.translationOnly) {
                                toTranslationOnlySong(song)
                            } else {
                                song
                            }
                        )
                    }
                }

                MSG_PLAYBACK_STATE -> view.setPlaying(msg.arg1 == 1)
                MSG_POSITION -> {
                    val pos = (msg.arg1.toLong() shl 32) or (msg.arg2.toLong() and 0xFFFFFFFFL)
                    view.setPosition(pos)
                }

                MSG_SEEK_TO -> {
                    val pos = (msg.arg1.toLong() shl 32) or (msg.arg2.toLong() and 0xFFFFFFFFL)
                    view.seekTo(pos)
                }

                MSG_SEND_TEXT -> view.setText(msg.obj as? String)
                MSG_TRANSLATION_TOGGLE -> {
                    val flags = applyLyricTranslationDisplay(view)
                    val song = if (flags.translationOnly) {
                        toTranslationOnlySong(lastSong)
                    } else {
                        lastSong
                    }
                    view.setSong(song)
                }

                MSG_SHOW_ROMA -> view.updateDisplayTranslation(displayRoma = msg.arg1 == 1)
                MSG_SONG_TRANSLATED -> {
                    val version = msg.arg1.toLong() shl 32 or (msg.arg2.toLong() and 0xFFFFFFFFL)
                    if (version == songVersion) {
                        uiHandler.removeMessages(MSG_SONG_TRANSLATION_TIMEOUT)
                        val translated = msg.obj as? Song
                        if (translated != null) {
                            lastSong = translated
                        }
                        val flags = applyLyricTranslationDisplay(view)
                        val song = if (flags.translationOnly) {
                            toTranslationOnlySong(lastSong)
                        } else {
                            lastSong
                        }
                        view.setSong(song)
                    }
                }

                MSG_SONG_TRANSLATION_TIMEOUT -> {
                    val version = msg.arg1.toLong() shl 32 or (msg.arg2.toLong() and 0xFFFFFFFFL)
                    if (version == songVersion) {
                        val fallbackSong = (msg.obj as? Song) ?: lastSong
                        val flags = applyLyricTranslationDisplay(view)
                        val song = if (flags.translationOnly) {
                            toTranslationOnlySong(fallbackSong)
                        } else {
                            fallbackSong
                        }
                        view.setSong(song)
                    }
                }
            }
        } catch (e: Throwable) {
            YLog.error(tag = TAG, msg = "handleMessageInternal error", e = e)
            return false
        }
        return true
    }

    ////////// 非UI线程方法结束 /////////

    override fun onCapsuleVisibilityChanged(isShowing: Boolean) {
        forViewEach {
            setOplusCapsuleVisibility(isShowing)
        }
        syncVendorTemporaryUi()
    }

    override fun onCoverUpdated(packageName: String, coverFile: File) {
        forControllerEach {
            val view = lyricView
            view.logoView.apply {
                if (packageName == activePackage && strategy is SuperLogo.CoverStrategy) {
                    this.coverFile = coverFile
                    strategy?.updateContent()
                }
            }
            if (packageName == activePackage) {
                updateCoverThemeColors(coverFile)
            }
        }
        syncVendorTemporaryUi()
    }

    fun notifyLyricVisibilityChanged() {
        syncVendorTemporaryUi()
    }

    fun refreshLyricTranslationDisplayConfig() {
        val currentSong = lastSong
        var shouldTriggerTranslation = false
        forControllerEach {
            val flags = applyLyricTranslationDisplay(lyricView)
            if (flags.translationOnly && currentSong != null && shouldWaitForAutoTranslation(
                    currentSong,
                    flags
                )
            ) {
                shouldTriggerTranslation = true
            }
            val song = if (flags.translationOnly) {
                toTranslationOnlySong(currentSong)
            } else {
                currentSong
            }
            lyricView.setSong(song)
        }
        if (shouldTriggerTranslation) {
            dispatchAutoTranslation(currentSong)
        }
        syncVendorTemporaryUi()
    }

    private data class TranslationDisplayFlags(
        val showTranslation: Boolean,
        val translationOnly: Boolean
    )

    private fun applyLyricTranslationDisplay(view: StatusBarLyric): TranslationDisplayFlags {
        val hideTranslation = LyricPrefs.isHideTranslationInLyricEnabled()
        val translationOnly = LyricPrefs.isTranslationOnlyInLyricEnabled()
        val showTranslation = !hideTranslation && (isDisplayTranslation || translationOnly)
        val showTranslationOnly = showTranslation && translationOnly
        if (DEBUG) {
            YLog.debug(
                tag = TAG,
                msg = "applyLyricTranslationDisplay pkg=$activePackage isDisplayTranslation=$isDisplayTranslation hideTranslation=$hideTranslation translationOnly=$translationOnly showTranslation=$showTranslation showTranslationOnly=$showTranslationOnly"
            )
        }
        view.updateDisplayTranslation(displayTranslation = showTranslation)
        return TranslationDisplayFlags(
            showTranslation = showTranslation,
            translationOnly = showTranslationOnly
        )
    }

    private fun toTranslationOnlySong(song: Song?): Song? {
        val input = song ?: return null
        val copied = input.deepCopy()
        val lines = copied.lyrics.orEmpty()
        val timestampFallbackText = buildTimestampFallbackText(lines)
        copied.lyrics = lines.map { line ->
            val translationText = line.translation?.takeIf { it.isNotBlank() }
                ?: line.translationWords
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "") { it.text.orEmpty() }
                ?: line.secondary?.takeIf { it.isNotBlank() }
                ?: line.secondaryWords
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "") { it.text.orEmpty() }
                ?: timestampFallbackText[line.begin]
                    ?.takeIf { fallback ->
                        fallback.isNotBlank() && fallback != line.text?.trim().orEmpty()
                    }
            if (translationText.isNullOrBlank()) {
                line
            } else {
                val translationWords = when {
                    !line.translationWords.isNullOrEmpty() -> line.translationWords
                    !line.secondaryWords.isNullOrEmpty() -> line.secondaryWords
                    else -> null
                }
                line.copy(
                    text = translationText,
                    words = translationWords,
                    secondary = null,
                    secondaryWords = null,
                    translation = null,
                    translationWords = null,
                    roma = null
                )
            }
        }
        return copied
    }

    private fun shouldWaitForAutoTranslation(song: Song, flags: TranslationDisplayFlags): Boolean {
        if (!flags.translationOnly) return false

        val settings = LyricPrefs.getActiveTranslationSettings()
        if (!settings.isUsable) return false

        val ignoreRegex = runCatching {
            settings.ignoreRegex.takeIf { it.isNotBlank() }?.toRegex()
        }.getOrNull()
        val timestampHasDualText = buildTimestampHasDualText(song.lyrics.orEmpty())

        return song.lyrics.orEmpty().any { line ->
            val text = line.text?.trim().orEmpty()
            text.isNotBlank()
                    && line.translation.isNullOrBlank()
                    && line.translationWords.isNullOrEmpty()
                    && line.secondary.isNullOrBlank()
                    && line.secondaryWords.isNullOrEmpty()
                    && (timestampHasDualText[line.begin] != true)
                    && (ignoreRegex?.matches(text) != true)
        }
    }

    private fun buildTimestampFallbackText(lines: List<RichLyricLine>): Map<Long, String> {
        return lines
            .groupBy { it.begin }
            .mapValues { (_, group) ->
                val normalizedTexts = group
                    .mapNotNull { it.text?.trim()?.takeIf(String::isNotBlank) }
                    .distinct()
                if (normalizedTexts.size >= 2) normalizedTexts.last() else ""
            }
            .filterValues(String::isNotEmpty)
    }

    private fun buildTimestampHasDualText(lines: List<RichLyricLine>): Map<Long, Boolean> {
        return lines
            .groupBy { it.begin }
            .mapValues { (_, group) ->
                group
                    .mapNotNull { it.text?.trim()?.takeIf(String::isNotBlank) }
                    .distinct()
                    .size >= 2
            }
    }

    private inline fun forControllerEach(crossinline block: StatusBarViewController.() -> Unit) {
        StatusBarViewManager.forEach { controller ->
            try {
                block(controller)
            } catch (e: Throwable) {
                //兜底
                YLog.error(tag = TAG, msg = "forControllerEach error $controller", e = e)
            }
        }
    }

    private inline fun forViewEach(crossinline block: StatusBarLyric.() -> Unit) {
        forControllerEach {
            val view = lyricView
            block(view)
        }
    }

    private fun syncVendorTemporaryUi() {
        val enableXiaomiIslandHide = LyricPrefs.baseStyle.xiaomiIslandTempHideEnabled
        val now = SystemClock.uptimeMillis()
        val playbackActive = isPlaying
                && (lastPositionUpdateAt <= 0L || now - lastPositionUpdateAt <= PLAYBACK_ACTIVE_STALE_MS)
        val shouldHideXiaomiIsland = StatusBarViewManager.controllers.any { controller ->
            val view = controller.lyricView
            enableXiaomiIslandHide
                    && playbackActive
                    && view.isAttachedToWindow
                    && view.isVisible
        }
        XiaomiIslandHooker.setHideByLyric(shouldHideXiaomiIsland)
    }

    private fun dispatchAutoTranslation(song: Song?) {
        if (!isDisplayTranslation && !LyricPrefs.isTranslationOnlyInLyricEnabled()) return

        val settings = LyricPrefs.getActiveTranslationSettings()
        if (!settings.isUsable || song == null) return

        songVersion++
        val version = songVersion

        AutoTranslationManager.translateSongIfNeededAsync(song, settings) { translated ->
            val message = uiHandler.obtainMessage(MSG_SONG_TRANSLATED, translated)
            message.arg1 = (version shr 32).toInt()
            message.arg2 = (version and 0xFFFFFFFFL).toInt()
            message.sendToTarget()
        }
    }
}
