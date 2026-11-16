/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.analytics

import android.util.Log
import io.github.proify.lyricon.common.analytics.PlayHistoryData
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

/**
 * 播放历史写入器（xposed 模块专用）。
 *
 * 职责：
 * - 将播放记录写入 JSON 文件
 * - 维护四层数据结构（歌手 → 专辑 → 歌曲 → 记录）
 *
 * 不负责：
 * - 查询（由 app 模块的 PlayHistoryStore 负责）
 * - 跨进程通信（由 LyriconBridge 负责）
 */
class PlayHistoryWriter(private val file: File) {

    companion object {
        private const val TAG = "PlayHistoryWriter"
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }
    }

    private var data: PlayHistoryData.Root = load()
    private var dirty = false

    // ===== 加载 =====

    private fun load(): PlayHistoryData.Root {
        return try {
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString<PlayHistoryData.Root>(content)
                } else {
                    PlayHistoryData.Root()
                }
            } else {
                PlayHistoryData.Root()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load: ${e.message}", e)
            PlayHistoryData.Root()
        }
    }

    // ===== 保存 =====

    /**
     * 保存到文件。
     */
    fun save() {
        if (!dirty) return
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(PlayHistoryData.Root.serializer(), data))
            dirty = false
            Log.d(TAG, "Saved ${data.artists.size} artists")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save: ${e.message}", e)
        }
    }

    // ===== 写入 =====

    /**
     * 记录播放。
     */
    fun recordPlay(
        songName: String,
        artistName: String,
        albumName: String?,
        songId: String?,
        duration: Long,
        playedDuration: Long,
        timestamp: Long,
        playerPackage: String,
        providerPackage: String,
        processName: String,
        isPlaying: Boolean,
        isLiked: Boolean,
        isCollected: Boolean,
        playMode: String,
        shuffleEnabled: Boolean,
        repeatMode: String,
        networkType: String,
        batteryLevel: Int,
        screenOn: Boolean,
        deviceModel: String,
        osVersion: String,
        lyrics: String?,
        lyricsTranslation: String?,
        metadata: Map<String, String>
    ) {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val playCompletionRate = if (duration > 0) {
            (playedDuration.toFloat() / duration).coerceIn(0f, 1f)
        } else 0f

        val isSkipped = playCompletionRate < 0.3f && playedDuration < 60_000L

        val record = PlayHistoryData.PlayRecord(
            playTimestampMs = timestamp,
            playDurationMs = playedDuration,
            playerPackageName = playerPackage,
//            provider = providerPackage,
//            processName = processName,
            dayOfWeek = dayOfWeek,
            hourOfDay = hourOfDay,
            isWeekend = PlayHistoryData.isWeekend(dayOfWeek),
            timePeriod = PlayHistoryData.calculateTimePeriod(hourOfDay),
            isPlaying = isPlaying,
            playCompletionRate = playCompletionRate,
            isSkipped = isSkipped,
            isLiked = isLiked,
            isCollected = isCollected,
            playMode = playMode,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            networkType = networkType,
            batteryLevel = batteryLevel,
            screenOn = screenOn,
            deviceModel = deviceModel,
            osVersion = osVersion,
            metadata = metadata
        )

        // 获取或创建歌手
        val artist = data.artists.getOrPut(artistName) { PlayHistoryData.ArtistData() }

        // 创建歌曲详情
        val songDetail = PlayHistoryData.SongDetail(
            songId = songId,
            name = songName,
            artist = artistName,
            album = albumName,
            durationMs = duration,
            hasLyrics = lyrics != null,
            lyrics = lyrics,
            lyricsLineCount = lyrics?.lines()?.size ?: 0,
            lyricsWordCount = lyrics?.length ?: 0
        )

        // 获取或创建歌曲，并更新统计
        val song: PlayHistoryData.SongDetail
        if (albumName != null) {
            val album = artist.albums.getOrPut(albumName) { PlayHistoryData.AlbumData() }
            song = album.songs.getOrPut(songName) { songDetail }

            album._info.playCount++
            album._info.totalDuration += playedDuration
            album._info.lastPlayedMs = timestamp
            if (album._info.firstPlayedMs == 0L) album._info.firstPlayedMs = timestamp
            album._info.songCount = album.songs.size
        } else {
            song = artist._singles.songs.getOrPut(songName) { songDetail }

            artist._singles._info.playCount++
            artist._singles._info.totalDuration += playedDuration
            artist._singles._info.lastPlayedMs = timestamp
            if (artist._singles._info.firstPlayedMs == 0L) artist._singles._info.firstPlayedMs = timestamp
            artist._singles._info.songCount = artist._singles.songs.size
        }

        // 更新歌曲统计
        song.playCount++
        song.totalPlayedDuration += playedDuration
        song.lastPlayedMs = timestamp
        if (song.firstPlayedMs == 0L) song.firstPlayedMs = timestamp

        // 添加播放记录
        song.records.add(record)

        // 更新歌手统计
        artist._info.playCount++
        artist._info.totalDurationMs += playedDuration
        artist._info.lastPlayedMs = timestamp
        if (artist._info.firstPlayedMs == 0L) artist._info.firstPlayedMs = timestamp
        artist._info.uniqueSongs = artist.albums.values.sumOf { it.songs.size } + artist._singles.songs.size
        artist._info.uniqueAlbums = artist.albums.size

        dirty = true
        save()  // 每条记录都保存，确保数据安全
        Log.d(TAG, "Recorded: $songName - $artistName (total: ${song.playCount})")
    }

    /**
     * 获取原始数据（用于调试）。
     */
    fun getRawData(): PlayHistoryData.Root = data
}
