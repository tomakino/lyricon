/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.analytics

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.systemui.lyric.LyricDataHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 播放数据收集器（xposed 模块）。
 *
 * 使用 PlayHistoryWriter 进行数据写入。
 * 四层结构：歌手 -> 专辑 -> 歌曲 -> 播放记录
 *
 * 优化特性：
 * - 线程安全：所有共享状态使用原子变量或同步保护
 * - 异步保存：使用协程异步写入
 * - 精确计时：基于实际播放进度计算时长
 * - 监控指标：记录写入统计信息
 */
@SuppressLint("StaticFieldLeak")
object PlayHistoryCollector : ActivePlayerListener, Closeable {

    private const val TAG = "PlayHistoryCollector"
    private const val FILE_NAME = "play_history.json"

    /** 去重窗口（毫秒）：同一歌曲在此时间内的重复播放不记录 */
    private const val DEDUP_WINDOW_MS = 15_000L  // 15秒

    /** 最小播放时长（毫秒）：播放不足此时长不记录 */
    private const val MIN_PLAY_DURATION_MS = 5_000L  // 5秒

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ========== 播放状态（原子变量保证线程安全）==========
    private val currentSongRef = AtomicReference<Song?>(null)
    private val currentPlayerPackageRef = AtomicReference("")
    private val currentProviderPackageRef = AtomicReference("")
    private val currentProcessNameRef = AtomicReference("")
    private val playStartTimeRef = AtomicLong(0)
    private val playingRef = AtomicReference(false)
    private val lastPositionRef = AtomicLong(0)
    private val playedAccumulatedRef = AtomicLong(0)

    // ========== 去重状态 ==========
    private val lastRecordKeyRef = AtomicReference("")
    private val lastRecordTimeRef = AtomicLong(0)

    // ========== 写入器 ==========
    private var writer: PlayHistoryWriter? = null
    private var context: Context? = null

    @Volatile
    private var initialized: Boolean = false

    // ========== 写入统计 ==========
    private val writeFailCount = AtomicInteger(0)
    private val writeSuccessCount = AtomicInteger(0)

    /**
     * 初始化并注册到 LyricDataHub。
     * @param context 应用上下文，用于获取本地目录
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) {
            Log.w(TAG, "Already initialized, skipping")
            return
        }
        try {
            val baseDir = File(context.filesDir, "lyricon")
            baseDir.mkdirs()
            val historyFile = File(baseDir, FILE_NAME)

            this.context = context.applicationContext
            writer = PlayHistoryWriter(historyFile)
            LyricDataHub.addListener(this)
            initialized = true
            Log.i(TAG, "Initialized (file=${historyFile.absolutePath})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init: ${e.message}", e)
        }
    }

    /**
     * 释放资源并注销监听器。
     */
    @Synchronized
    override fun close() {
        try {
            LyricDataHub.removeListener(this)

            // 保存数据
            scope.launch {
                writer?.save()
                writer = null
            }

            initialized = false
            context = null
            currentSongRef.set(null)
            currentPlayerPackageRef.set("")
            currentProviderPackageRef.set("")
            currentProcessNameRef.set("")
            playStartTimeRef.set(0)
            playingRef.set(false)
            lastPositionRef.set(0)
            playedAccumulatedRef.set(0)
            lastRecordKeyRef.set("")
            lastRecordTimeRef.set(0)

            Log.i(
                TAG,
                "Destroyed (success=${writeSuccessCount.get()}, failed=${writeFailCount.get()})"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy: ${e.message}", e)
        }
    }

    // ========== ActivePlayerListener 实现 ==========

    @Synchronized
    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        currentPlayerPackageRef.set(providerInfo?.playerPackageName.orEmpty())
        currentProviderPackageRef.set(providerInfo?.providerPackageName.orEmpty())
        currentProcessNameRef.set(providerInfo?.processName.orEmpty())
    }

    @Synchronized
    override fun onSongChanged(song: Song?) {
        // 先刷新当前歌曲的记录
        flushCurrentRecord()

        // 切换到新歌曲
        currentSongRef.set(song)
        lastPositionRef.set(0)
        playedAccumulatedRef.set(0)

        if (playingRef.get()) {
            playStartTimeRef.set(System.currentTimeMillis())
        }
    }

    @Synchronized
    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        val wasPlaying = playingRef.getAndSet(isPlaying)

        if (isPlaying && !wasPlaying) {
            // 从暂停恢复播放
            playStartTimeRef.set(System.currentTimeMillis())
            lastPositionRef.set(0)
        } else if (!isPlaying && wasPlaying) {
            // 暂停播放，刷新记录
            flushCurrentRecord()
        }
    }

    @Synchronized
    override fun onPositionChanged(position: Long) {
        val lastPosition = lastPositionRef.getAndSet(position)
        val isPlaying = playingRef.get()

        if (isPlaying && lastPosition > 0 && position > lastPosition) {
            // 累加实际播放时长（排除暂停和跳转）
            val delta = position - lastPosition
            // 过滤异常值（超过10秒的跳转视为 seek）
            if (delta < 10_000L) {
                playedAccumulatedRef.addAndGet(delta)
            }
        }
    }

    override fun onSeekTo(position: Long) {
        // 快进/快退时重置位置，但保留累计时长
        lastPositionRef.set(position)
    }

    override fun onReceiveText(text: String?) {}
    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {}
    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {}

    // ========== 核心逻辑 ==========

    /**
     * 刷新当前歌曲的播放记录。
     */
    @Synchronized
    private fun flushCurrentRecord() {
        val song = currentSongRef.get() ?: return
        val now = System.currentTimeMillis()
        val playStartTime = playStartTimeRef.get()

        // 计算播放时长：优先使用累计时长，否则使用时间差
        val playedDuration = if (playedAccumulatedRef.get() > 0) {
            playedAccumulatedRef.get()
        } else if (playStartTime > 0) {
            now - playStartTime
        } else {
            0L
        }

        // 检查最小播放时长
        if (playedDuration < MIN_PLAY_DURATION_MS) {
            Log.d(TAG, "Skip: played ${playedDuration}ms < ${MIN_PLAY_DURATION_MS}ms")
            return
        }

        // 验证歌曲信息
        val songName = song.name?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "Skip: empty song name")
            return
        }
        val artist = song.artist?.takeIf { it.isNotBlank() } ?: "未知歌手"

        // 去重检查
        val recordKey = "$songName|$artist"
        val lastRecordKey = lastRecordKeyRef.get()
        val lastRecordTime = lastRecordTimeRef.get()

        if (recordKey == lastRecordKey && now - lastRecordTime < DEDUP_WINDOW_MS) {
            Log.d(TAG, "Skip duplicate: $songName within ${DEDUP_WINDOW_MS}ms")
            playedAccumulatedRef.set(0)
            return
        }

        // 提取歌词
        val lyricsData = extractLyrics(song)

        // 提取专辑信息
        val album = song.metadata?.getString("album")?.takeIf { it.isNotBlank() }

        // 从元数据提取用户行为
        val metadata = song.metadata
        val isLiked = metadata?.getBoolean("liked") ?: false
        val isCollected = metadata?.getBoolean("collected") ?: false
        val playMode = metadata?.getString("playMode") ?: "sequential"
        val shuffleEnabled = metadata?.getBoolean("shuffle") ?: false
        val repeatMode = metadata?.getString("repeat") ?: "off"

        // 获取系统信息
        val ctx = context
        val networkType = ctx?.let { getNetworkType(it) } ?: "unknown"
        val batteryLevel = ctx?.let { getBatteryLevel(it) } ?: -1
        val screenOn = ctx?.let { isScreenOn(it) } ?: true

        // 构建扩展元数据
        val extendedMetadata = mutableMapOf<String, String>()
        metadata?.getString("source")?.let { extendedMetadata["source"] = it }
        metadata?.getString("quality")?.let { extendedMetadata["quality"] = it }

        // 异步写入
        scope.launch {
            try {
                writer?.recordPlay(
                    songName = songName,
                    artistName = artist,
                    albumName = album,
                    songId = song.id,
                    duration = song.duration.coerceAtLeast(0),
                    playedDuration = playedDuration,
                    timestamp = playStartTime,
                    playerPackage = currentPlayerPackageRef.get(),
                    providerPackage = currentProviderPackageRef.get(),
                    processName = currentProcessNameRef.get(),
                    isPlaying = playingRef.get(),
                    isLiked = isLiked,
                    isCollected = isCollected,
                    playMode = playMode,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    networkType = networkType,
                    batteryLevel = batteryLevel,
                    screenOn = screenOn,
                    deviceModel = android.os.Build.MODEL,
                    osVersion = android.os.Build.VERSION.RELEASE,
                    lyrics = lyricsData.text,
                    lyricsTranslation = lyricsData.translation,
                    metadata = extendedMetadata
                )

                writeSuccessCount.incrementAndGet()
                Log.d(TAG, "Recorded: $songName - $artist (${playedDuration}ms)")
            } catch (e: Exception) {
                writeFailCount.incrementAndGet()
                Log.e(TAG, "Failed to record: ${e.message}", e)
            }
        }

        // 更新去重状态
        lastRecordKeyRef.set(recordKey)
        lastRecordTimeRef.set(now)

        // 重置计时状态
        playStartTimeRef.set(if (playingRef.get()) now else 0)
        playedAccumulatedRef.set(0)
        lastPositionRef.set(0)
    }

    /**
     * 提取歌词
     */
    private fun extractLyrics(song: Song): LyricsData {
        val lyrics = song.lyrics

        if (lyrics.isNullOrEmpty()) {
            return LyricsData.EMPTY
        }

        // 转换为LRC格式
        val lrcBuilder = StringBuilder()
        val translationBuilder = StringBuilder()
        var hasTranslation = false

        for (line in lyrics) {
            val begin = line.begin
            val text = line.text?.takeIf { it.isNotBlank() } ?: continue

            // 格式化时间标签 [mm:ss.xx]
            val minutes = begin / 60000
            val seconds = (begin % 60000) / 1000
            val millis = (begin % 1000) / 10
            val timeTag = "[%02d:%02d.%02d]".format(minutes, seconds, millis)

            lrcBuilder.append("$timeTag$text\n")

            // 提取翻译
            val translation = line.translation?.takeIf { it.isNotBlank() }
            if (translation != null) {
                hasTranslation = true
                translationBuilder.append("$timeTag$translation\n")
            }
        }

        val lrcText = lrcBuilder.toString().trim()
        if (lrcText.isBlank()) {
            return LyricsData.EMPTY
        }

        val translationText = if (hasTranslation) {
            translationBuilder.toString().trim().takeIf { it.isNotBlank() }
        } else {
            null
        }

        return LyricsData(
            text = lrcText,
            translation = translationText,
            lineCount = lyrics.size,
            wordCount = lrcText.length
        )
    }

    /**
     * 获取网络类型
     */
    @SuppressLint("MissingPermission")
    private fun getNetworkType(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "none"
            val caps = cm.getNetworkCapabilities(network) ?: return "none"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get network type: ${e.message}")
            "unknown"
        }
    }

    /**
     * 获取电池电量
     */
    private fun getBatteryLevel(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get battery level: ${e.message}")
            -1
        }
    }

    /**
     * 判断屏幕是否亮着
     */
    private fun isScreenOn(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isInteractive
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check screen state: ${e.message}")
            true
        }
    }

    // ========== 公共方法 ==========

    /**
     * 手动刷新当前记录。
     */
    @Synchronized
    fun flush() {
        flushCurrentRecord()
        Log.d(TAG, "Flushed")
    }

    /**
     * 手动保存数据。
     */
    fun save() {
        scope.launch {
            writer?.save()
        }
    }

    /**
     * 打开历史文件的 PFD（供 App 端读取）。
     */
    fun openFd(): android.os.ParcelFileDescriptor? {
        val ctx = context ?: run {
            Log.e(TAG, "Context not initialized")
            return null
        }

        val file = File(ctx.filesDir, "lyricon/$FILE_NAME")
        if (!file.exists()) {
            Log.w(TAG, "History file not exists")
            return null
        }

        return try {
            android.os.ParcelFileDescriptor.open(
                file,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open PFD: ${e.message}", e)
            null
        }
    }

    /**
     * 获取监控统计信息。
     */
    fun getStats(): CollectorStats {
        return CollectorStats(
            initialized = initialized,
            writeSuccess = writeSuccessCount.get(),
            writeFail = writeFailCount.get(),
            currentSong = currentSongRef.get()?.name,
            isPlaying = playingRef.get(),
        )
    }

    /**
     * 监控统计数据类。
     */
    data class CollectorStats(
        val initialized: Boolean,
        val writeSuccess: Int,
        val writeFail: Int,
        val currentSong: String?,
        val isPlaying: Boolean,
    ) {
        override fun toString(): String =
            "CollectorStats(init=$initialized, success=$writeSuccess, fail=$writeFail, " +
                    "song=$currentSong, playing=$isPlaying)"
    }

    /**
     * 歌词数据
     */
    private data class LyricsData(
        val text: String?,
        val translation: String?,
        val lineCount: Int,
        val wordCount: Int,
    ) {
        companion object {
            val EMPTY = LyricsData(null, null, 0, 0)
        }
    }
}
