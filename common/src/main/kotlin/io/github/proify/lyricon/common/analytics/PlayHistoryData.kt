/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.common.analytics

import kotlinx.serialization.Serializable

/**
 * 音乐播放历史数据结构。
 *
 * 四层结构：歌手 -> 专辑 -> 歌曲 -> 播放记录
 */
object PlayHistoryData {

    /**
     * 顶层数据结构：歌手名 -> 歌手数据
     */
    @Serializable
    data class Root(
        val artists: MutableMap<String, ArtistData> = mutableMapOf()
    )

    /**
     * 歌手数据
     */
    @Serializable
    data class ArtistData(
        val _info: ArtistInfo = ArtistInfo(),
        val albums: MutableMap<String, AlbumData> = mutableMapOf(),
        val _singles: SinglesData = SinglesData()
    )

    /**
     * 歌手统计信息
     */
    @Serializable
    data class ArtistInfo(
        var playCount: Int = 0,
        var totalDurationMs: Long = 0,
        var firstPlayedMs: Long = 0,
        var lastPlayedMs: Long = 0,
        var uniqueSongs: Int = 0,
        var uniqueAlbums: Int = 0
    )

    /**
     * 专辑数据
     */
    @Serializable
    data class AlbumData(
        val _info: AlbumInfo = AlbumInfo(),
        val songs: MutableMap<String, SongDetail> = mutableMapOf()
    )

    /**
     * 专辑统计信息
     */
    @Serializable
    data class AlbumInfo(
        var playCount: Int = 0,
        var totalDuration: Long = 0,
        var firstPlayedMs: Long = 0,
        var lastPlayedMs: Long = 0,
        var songCount: Int = 0,
        val coverUrl: String? = null,
        val releaseYear: Int? = null
    )

    /**
     * 单曲集合（无专辑归属）
     */
    @Serializable
    data class SinglesData(
        val _info: AlbumInfo = AlbumInfo(),
        val songs: MutableMap<String, SongDetail> = mutableMapOf()
    )

    /**
     * 歌曲详情
     */
    @Serializable
    data class SongDetail(
        // 基础信息
        val songId: String? = null,
        val name: String,
        val artist: String,
        val album: String? = null,
        val durationMs: Long = 0,

        // 音乐元数据
        val trackNumber: Int? = null,
        val discNumber: Int? = null,
        val genre: String? = null,
        val releaseYear: Int? = null,
        val albumArt: String? = null,
        val isrc: String? = null,
        val bitrate: Int? = null,
        val sampleRate: Int? = null,

        // 统计信息
        var playCount: Int = 0,
        var totalPlayedDuration: Long = 0,
        var firstPlayedMs: Long = 0,
        var lastPlayedMs: Long = 0,

        // 歌词
        val hasLyrics: Boolean = false,
        val lyrics: String? = null,  // LRC格式
        val lyricsLineCount: Int = 0,
        val lyricsWordCount: Int = 0,

        // 播放记录
        val records: MutableList<PlayRecord> = mutableListOf()
    )

    /**
     * 播放记录
     */
    @Serializable
    data class PlayRecord(
        // 时间

        val playTimestampMs: Long,                    // 播放时间戳
        val playDurationMs: Long,                   // 播放时长

        // 播放器
        val playerPackageName: String = "",         // 播放器包名
//
//        val provider: String = "",       // 提供端包名
//        @IgnoredOnParcel
//        val processName: String = "",    // 进程名

        // 时间维度
        val dayOfWeek: Int = 0,          // 星期几 (1-7)
        val hourOfDay: Int = 0,          // 小时 (0-23)
        val isWeekend: Boolean = false,  // 是否周末
        val timePeriod: String = "",     // morning/afternoon/evening/night

        // 播放状态
        val isPlaying: Boolean = true,
        val playCompletionRate: Float = 0f,  // 播放完成率
        val isSkipped: Boolean = false,      // 是否跳过

        // 用户行为
        val isLiked: Boolean = false,    // 是否喜欢
        val isCollected: Boolean = false,// 是否收藏

        // 播放模式
        val playMode: String = "sequential",  // sequential/shuffle/repeat
        val shuffleEnabled: Boolean = false,
        val repeatMode: String = "off",       // off/all/one

        // 环境
        val networkType: String = "unknown",  // wifi/mobile/none
        val batteryLevel: Int = -1,           // 电量
        val screenOn: Boolean = true,         // 屏幕是否亮

        // 设备
        val deviceModel: String = "",         // 设备型号
        val osVersion: String = "",           // 系统版本

        // 扩展
        val metadata: Map<String, String> = emptyMap()
    )

    // ===== 辅助方法 =====

    /**
     * 计算时段标签
     */
    fun calculateTimePeriod(hour: Int): String = when (hour) {
        in 6..11 -> "morning"
        in 12..17 -> "afternoon"
        in 18..22 -> "evening"
        else -> "night"
    }

    /**
     * 判断是否周末
     */
    fun isWeekend(dayOfWeek: Int): Boolean = dayOfWeek == 1 || dayOfWeek == 7

    /**
     * 从元数据提取歌词
     */
    fun extractLyrics(metadata: Map<String, Any?>): LyricsExtract {
        val text = metadata["lyrics"]?.toString()
        val translation = metadata["lyricsTranslation"]?.toString()
        val lineCount = text?.lines()?.size ?: 0
        val wordCount = text?.length ?: 0
        return LyricsExtract(
            text = text,
            translation = translation,
            lineCount = lineCount,
            wordCount = wordCount,
            hasLyrics = text != null
        )
    }

    /**
     * 歌词提取结果
     */
    data class LyricsExtract(
        val text: String?,
        val translation: String?,
        val lineCount: Int,
        val wordCount: Int,
        val hasLyrics: Boolean
    )
}
