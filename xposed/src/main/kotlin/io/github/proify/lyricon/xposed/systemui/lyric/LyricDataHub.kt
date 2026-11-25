/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.util.Log
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.systemui.lyric.processor.LyricDataProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * 歌词数据调度中枢
 * 管理歌词生命周期：获取原始数据 -> 后台加工 -> 第一次分发 -> 后台增强 -> 最终分发。
 */
object LyricDataHub : ActivePlayerListener {
    private const val TAG = "LyricDataHub"

    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 状态版本号，用于在异步恢复后校验数据是否已过时 */
    private val versionCounter = AtomicInteger(0)

    /** 缓存当前的原始歌曲，用于配置变更时重走流程 */
    private var cachedRawSong: Song? = null

    /** 当前执行中的流水线任务 */
    private var activePipelineJob: Job? = null

    /** 当前活动的提供者信息 */
    private var providerInfo: ProviderInfo? = null

    fun addListener(listener: ActivePlayerListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ActivePlayerListener) {
        listeners.remove(listener)
    }

    /**
     * 启动后台加工流水线。
     * 所有歌词加工都在后台协程执行，避免阻塞播放器回调线程。
     * @param rawSong 待加工的原始歌曲
     */
    private fun runProcessingPipeline(rawSong: Song?) {
        val song = rawSong?.deepCopy()
        val currentVersion = versionCounter.incrementAndGet()
        activePipelineJob?.cancel()

        activePipelineJob = scope.launch {
            try {
                if (song == null) {
                    if (isCurrentVersion(currentVersion)) dispatchSong(null)
                    return@launch
                }

                val style = LyricPrefs.getLyricStyle()

                // 1. 后台前置加工：处理繁简、屏蔽词等基础显示数据
                val preProcessed = LyricDataProcessor.executePreProcessing(song)
                if (!isCurrentVersion(currentVersion)) return@launch

                // 第一次分发：基础处理完成后尽快刷新 UI
                dispatchSong(LyricDataProcessor.executeDisplayProcessing(preProcessed, style))

                // 2. 后台后置流水线：处理 AI 翻译等耗时扩展
                val finalSong =
                    LyricDataProcessor.executePostProcessingPipeline(preProcessed, style)

                if (isCurrentVersion(currentVersion)) {
                    dispatchSong(LyricDataProcessor.executeDisplayProcessing(finalSong, style))
                } else {
                    logOutdatedPipeline(currentVersion, finalSong)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Pipeline $currentVersion cancelled. $e")
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline $currentVersion error", e)
            }
        }
    }

    private fun isCurrentVersion(version: Int): Boolean = version == versionCounter.get()

    private fun logOutdatedPipeline(version: Int, song: Song) {
        Log.d(
            TAG,
            "Pipeline requestVersion:$version, nowVersion:${versionCounter.get()} skipped. ${song.name}"
        )
    }

    /**
     * 重走加工流程
     * 当配置（繁简、AI 开关、翻译模式）变更时调用，无需切歌即可应用新设置。
     */
    fun reprocessCurrentSong() {
        runProcessingPipeline(cachedRawSong)
    }

    // --- ActivePlayerListener 触发点 ---

    override fun onSongChanged(song: Song?) {
        this.cachedRawSong = song?.deepCopy()
        runProcessingPipeline(song)
    }

    private var lastDispatchSongId: Int? = null

    private fun dispatchSong(song: Song?) {
        // normalize() 已返回深拷贝且不修改入参，此处无需再 deepCopy()（否则每次分发会复制两份完整歌词对象图）
        val normalize = song?.normalize()
        val hashCode = normalize?.hashCode()

        if (hashCode != null && hashCode == lastDispatchSongId) return
        lastDispatchSongId = hashCode

        listeners.forEach { it.onSongChanged(normalize) }
    }

    // --- 纯状态透传 (不涉及加工) ---

    override fun onReceiveText(text: String?) {
        listeners.forEach { it.onReceiveText(text) }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        listeners.forEach { it.onPlaybackStateChanged(isPlaying) }
    }

    override fun onPositionChanged(position: Long) {
        listeners.forEach { it.onPositionChanged(position) }
    }

    override fun onSeekTo(position: Long) {
        listeners.forEach { it.onSeekTo(position) }
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        listeners.forEach { it.onDisplayTranslationChanged(isDisplayTranslation) }
    }

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        listeners.forEach { it.onDisplayRomaChanged(isDisplayRoma) }
    }

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        // 新播放器会话：允许同一首歌重新分发
        lastDispatchSongId = null
        this.providerInfo = providerInfo
        listeners.forEach { it.onActiveProviderChanged(providerInfo) }
    }
}
