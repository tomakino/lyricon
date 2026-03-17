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
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.statusbarlyric.SuperLogo
import io.github.proify.lyricon.xposed.systemui.util.LyricPrefs
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

    private const val UPDATE_INTERVAL_MS = 1000L / 60L

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
        uiHandler.obtainMessage(MSG_PLAYBACK_STATE, if (isPlaying) 1 else 0, 0).sendToTarget()
    }

    override fun onPositionChanged(position: Long) {
        // if (DEBUG) YLog.debug(tag = TAG, msg = "onPositionChanged: $position")

        val now = SystemClock.uptimeMillis()

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
                if (msg.arg1 == 1) {
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

                    view.setSong(null)
                    view.setPlaying(false)
                    controller.updateLyricStyle(LyricPrefs.getLyricStyle())
                    view.updateVisibility()

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
                    view.setSong(song)
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
                MSG_TRANSLATION_TOGGLE -> view.updateDisplayTranslation(displayTranslation = msg.arg1 == 1)
                MSG_SHOW_ROMA -> view.updateDisplayTranslation(displayRoma = msg.arg1 == 1)
                MSG_SONG_TRANSLATED -> {
                    val version = msg.arg1.toLong() shl 32 or (msg.arg2.toLong() and 0xFFFFFFFFL)
                    if (version == songVersion) {
                        view.setSong(msg.obj as? Song)
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
        val shouldHideXiaomiIsland = StatusBarViewManager.controllers.any { controller ->
            val view = controller.lyricView
            enableXiaomiIslandHide
                    && view.isAttachedToWindow
                    && view.isVisible
        }
        XiaomiIslandHooker.setHideByLyric(shouldHideXiaomiIsland)
    }

    private fun dispatchAutoTranslation(song: Song?) {
        if (!isDisplayTranslation) return

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
