/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.os.Handler
import io.github.proify.android.extensions.crc32
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.statusbarlyric.logo.CoverLogoStrategy
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.hook.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.lyric.StatusBarViewManager.MAIN_LOOPER
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import java.io.File

/**
 * 歌词视图核心控制器 (Lyric View Controller)
 * * 负责接收播放器状态、歌曲信息及系统 UI 变更，并将数据分发至所有已注册的状态栏控制器。
 * 实现了 [ActivePlayerListener]、[OplusCapsuleHooker.CapsuleStateChangeListener] 等核心接口。
 * * @author Tomakino
 * @since 2026
 */
object LyricViewController : ActivePlayerListener,
    OplusCapsuleHooker.CapsuleStateChangeListener,
    NotificationCoverHelper.OnCoverUpdateListener {

    private const val TAG = "LyricViewController"
    private const val DEBUG = true

    /** 当前播放状态 */
    @Volatile
    var isPlaying: Boolean = false
        private set

    /** 当前活跃播放器的包名 */
    @Volatile
    var activePackage: String = ""
        private set

    /** 是否显示翻译内容 */
    @Volatile
    private var isDisplayTranslation: Boolean = true

    /** 是否显示罗马音内容 */
    @Volatile
    private var isDisplayRoma: Boolean = true

    /** 当前歌曲的逻辑播放进度（毫秒） */
    @Volatile
    var currentLogicPosition: Long = 0
        private set

    /** 当前播放歌曲（已加工），供控制窗口读取歌名/歌手/歌词 */
    @Volatile
    var currentSong: Song? = null
        private set

    /** 用于处理 UI 刷新任务的 Handler */
    private val mainHandler by lazy { Handler(MAIN_LOOPER) }

    /** * 高频进度更新任务。
     * 使用单例 Runnable 减少 GC 压力，仅在进度变更时由主线程调度。
     */
    private val frameUpdater = Runnable {
        val controllers = StatusBarViewManager.controllers
        for (i in controllers.indices) {
            controllers[i].lyricView.setPosition(currentLogicPosition)
        }
    }

    init {
        if (DEBUG) YLog.debug(TAG, "Initializing LyricViewController...")
        // 注册数据总线、系统钩子及封面更新监听
        LyricDataHub.addListener(this)
        OplusCapsuleHooker.registerListener(this)
        NotificationCoverHelper.registerListener(this)
    }

    /**
     * 当歌曲发生切换时回调。
     * @param song 新歌曲对象，若停止播放则为 null
     */
    override fun onSongChanged(song: Song?) {
        YLog.info(TAG, "onSongChanged: $song")
        this.currentSong = song

        updateAllControllers {
            lyricView.setSong(song)
            refreshTranslationVisibility(lyricView)
        }

        updateCoverFileFromSong(song)
    }

    private fun updateCoverFileFromSong(song: Song?) {
        val activePackage = this.activePackage
        val name = song?.name
        val artist = song?.artist
        if (activePackage.isBlank() || name.isNullOrBlank() || artist.isNullOrBlank()) {
            return
        }
        val file = NotificationCoverHelper.getCachedCoverFile(activePackage, name, artist)
        if (file != null && file.exists()) {
            YLog.info(TAG, "Cover cache file found: $name - $artist")
            updateCoverFile(file)
        }
    }

    /**
     * 当活跃播放源发生切换时回调（如从网易云切换至 QQ 音乐）。
     * @param providerInfo 播放器信息
     */
    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        YLog.info(TAG, "onActiveProviderChanged: $providerInfo")

        this.activePackage = providerInfo?.playerPackageName.orEmpty()
        LyricPrefs.activePackageName = this.activePackage

        updateAllControllers {
            resetViewForNewPlayer(this, providerInfo)
        }
    }

    /**
     * 播放状态变更回调（播放/暂停）。
     * @param isPlaying 播放状态
     */
    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (this.isPlaying == isPlaying) return
        YLog.info(TAG, "onPlaybackStateChanged: $isPlaying")

        this.isPlaying = isPlaying
        updateAllControllers { lyricView.setPlaying(isPlaying) }
    }

    /**
     * 播放进度正常步进时的回调（通常每秒触发）。
     * @param position 当前逻辑时间戳
     */
    override fun onPositionChanged(position: Long) {
        this.currentLogicPosition = position
        // 进度更新极其频繁，直接 post 到 Handler
        mainHandler.post(frameUpdater)
    }

    /**
     * 用户手动调整进度（Seek）时的回调。
     * @param position 目标时间戳
     */
    override fun onSeekTo(position: Long) {
        this.currentLogicPosition = position
        updateAllControllers { lyricView.seekTo(position) }
    }

    /**
     * 接收到纯文本歌词时的回调（通常用于未匹配到 Lrc 的情况）。
     * @param text 歌词文本内容
     */
    override fun onReceiveText(text: String?) {
        YLog.info(TAG, "onReceiveText: $text")
        updateAllControllers { lyricView.setText(text) }
    }

    /**
     * 翻译显示开关状态变更。
     * @param isDisplayTranslation 是否开启
     */
    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        YLog.info(TAG, "onDisplayTranslationChanged: $isDisplayTranslation")

        this.isDisplayTranslation = isDisplayTranslation
        updateAllControllers { refreshTranslationVisibility(lyricView) }
    }

    /**
     * 罗马音显示开关状态变更。
     * @param isDisplayRoma 是否开启
     */
    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        YLog.info(TAG, "onDisplayRomaChanged: $isDisplayRoma")

        this.isDisplayRoma = isDisplayRoma
        updateAllControllers { lyricView.updateDisplayTranslation(displayRoma = isDisplayRoma) }
    }

    /**
     * 应用全局配置更新（如字体颜色、阴影等样式变更）。
     * @param style 新的歌词样式配置
     */
    fun applyConfigurationUpdate(style: LyricStyle) {
        updateAllControllers { updateLyricStyle(style) }
        LyricDataHub.reprocessCurrentSong()
    }

    /**
     * 针对新播放器重置视图状态。
     * @param controller 具体的控制器实例
     * @param provider 播放源信息
     */
    private fun resetViewForNewPlayer(
        controller: StatusBarViewController,
        provider: ProviderInfo?
    ) {
        val view = controller.lyricView
        view.setSong(null)
        view.setPlaying(false)
        controller.updateLyricStyle(LyricPrefs.getLyricStyle())
        view.updateVisibility()

        view.logoView.apply {
            val pkg = provider?.playerPackageName.orEmpty()
            this.activePackage = pkg
//
//            val cover =
//                if (pkg.isBlank()) {
//                    null
//                } else {
//                    NotificationCoverHelper.getLatestCoverFile(pkg)
//                }
//            this.coverFile = cover
//            controller.updateCoverThemeColors(cover)

            this.providerLogo = provider?.logo
        }
    }

    /**
     * 根据当前用户配置和样式决定翻译行的显示状态。
     * @param view 状态栏歌词视图
     */
    private fun refreshTranslationVisibility(view: StatusBarLyric) {
        val style = LyricPrefs.activePackageStyle
        val shouldShow = isDisplayTranslation &&
                !style.text.isDisableTranslation &&
                !style.text.isTranslationOnly
        view.updateDisplayTranslation(displayTranslation = shouldShow)
    }

    /**
     * 核心分发方法：在主线程遍历所有控制器并执行操作。
     * @param block 需要在每个控制器上执行的逻辑
     */
    private inline fun updateAllControllers(crossinline block: StatusBarViewController.() -> Unit) {
        StatusBarViewManager.forEachOnMainThread { controller ->
            runCatching {
                controller.block()
            }.onFailure { e ->
                YLog.error(TAG, "UI Update distribution error", e)
            }
        }
    }

    /**
     * Oplus (ColorOS) 胶囊状态变更监听。
     * 用于在系统胶囊出现时自动隐藏歌词，避免遮挡。
     * @param isShowing 胶囊是否正在显示
     */
    override fun onColorOsCapsuleVisibilityChanged(isShowing: Boolean) {
        updateAllControllers { lyricView.setOplusCapsuleVisibility(isShowing) }
    }

    /**
     * 专辑封面更新回调。
     * @param packageName 触发更新的播放器包名
     * @param coverFile 封面文件对象
     */
    override fun onCoverUpdated(packageName: String, coverFile: File) {
        if (packageName != activePackage) return
        updateCoverFile(coverFile)
    }

    private var lastCoverSignature = 0L
    private fun updateCoverFile(coverFile: File?) {
        if (coverFile != null) {
            val signature = coverFile.crc32()
            if (signature == lastCoverSignature) {
                YLog.verbose(TAG, "Cover file is the same, skip update")
                return
            }
            lastCoverSignature = signature
        } else {
            lastCoverSignature = 0
        }

        updateAllControllers {
            lyricView.logoView.apply {
                this.coverFile = coverFile
                (strategy as? CoverLogoStrategy)?.updateContent()
            }
            updateCoverThemeColors(coverFile)
        }
    }
}