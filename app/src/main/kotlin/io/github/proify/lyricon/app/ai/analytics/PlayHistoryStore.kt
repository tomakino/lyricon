/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ai.analytics

import android.content.Context
import android.util.Log
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.common.analytics.PlayHistoryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * 播放历史数据存储（App 模块）。
 *
 * 职责：
 * - 通过 LyriconBridge 从 SystemUI 读取数据
 * - 解析 JSON 为 PlayHistoryData.Root
 * - 提供查询接口（歌手、歌曲、歌词、搜索、统计）
 *
 * 不负责：
 * - 写入数据（由 xposed 的 PlayHistoryWriter 负责）
 * - 文件IO（直接从 PFD 内存解析）
 */
class PlayHistoryStore(private val context: Context) {

    companion object {
        private const val TAG = "PlayHistoryStore"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    // 内存数据
    @Volatile
    private var data: PlayHistoryData.Root? = null
    private val mutex = Mutex()

    /**
     * 从 SystemUI 加载数据。
     */
    suspend fun loadFromSystemUI(): PlayHistoryData.Root = withContext(Dispatchers.IO) {
        mutex.withLock {
            data?.let { return@withLock it }

            try {
                val ctx = LyriconApp.get()
                Log.d(TAG, "Requesting play history from SystemUI...")

                val result = LyriconBridge.with(ctx)
                    .to(PackageNames.SYSTEM_UI)
                    .key(AppBridgeConstants.REQUEST_PLAY_HISTORY)
                    .await(timeout = 5000)

                val pfd = result.getParcelable<android.os.ParcelFileDescriptor>("pfd")
                if (pfd == null) {
                    Log.w(TAG, "PFD not available")
                    val empty = PlayHistoryData.Root()
                    data = empty
                    return@withLock empty
                }

                // 直接从 PFD 解析 JSON，不写本地文件
                val content = InputStreamReader(FileInputStream(pfd.fileDescriptor), Charsets.UTF_8)
                    .use { it.readText() }
                pfd.close()

                val parsed = if (content.isNotBlank()) {
                    json.decodeFromString<PlayHistoryData.Root>(content)
                } else {
                    PlayHistoryData.Root()
                }

                data = parsed
                Log.d(TAG, "Loaded ${parsed.artists.size} artists")
                parsed
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load: ${e.message}", e)
                val empty = PlayHistoryData.Root()
                data = empty
                empty
            }
        }
    }

    /**
     * 强制刷新。
     */
    suspend fun refresh(): PlayHistoryData.Root = withContext(Dispatchers.IO) {
        mutex.withLock { data = null }
        loadFromSystemUI()
    }

    // ===== 查询接口 =====

    /**
     * 获取原始数据。
     */
    suspend fun getRawData(): PlayHistoryData.Root {
        return data ?: loadFromSystemUI()
    }

    /**
     * 获取所有歌手名。
     */
    suspend fun getArtistNames(): Set<String> {
        return getRawData().artists.keys
    }

    /**
     * 获取歌手数据。
     */
    suspend fun getArtist(name: String): PlayHistoryData.ArtistData? {
        return getRawData().artists[name]
    }

    /**
     * 获取歌曲数据。
     */
    suspend fun getSong(artistName: String, songName: String): PlayHistoryData.SongDetail? {
        val artist = getArtist(artistName) ?: return null

        // 先搜专辑
        for (album in artist.albums.values) {
            album.songs[songName]?.let { return it }
        }

        // 再搜单曲
        return artist._singles.songs[songName]
    }

    /**
     * 获取歌词。
     */
    suspend fun getLyrics(artistName: String, songName: String): String? {
        return getSong(artistName, songName)?.lyrics
    }

    /**
     * 搜索歌词。
     */
    suspend fun searchLyrics(keyword: String, limit: Int = 10): List<LyricsSearchResult> {
        val results = mutableListOf<LyricsSearchResult>()
        val data = getRawData()

        for (artist in data.artists.values) {
            for (album in artist.albums.values) {
                for (song in album.songs.values) {
                    val lyrics = song.lyrics
                    if (lyrics != null && lyrics.contains(keyword, ignoreCase = true)) {
                        results.add(LyricsSearchResult(
                            songName = song.name,
                            artistName = song.artist,
                            albumName = song.album,
                            lyricsPreview = extractPreview(lyrics, keyword)
                        ))
                    }
                }
            }

            for (song in artist._singles.songs.values) {
                val lyrics = song.lyrics
                if (lyrics != null && lyrics.contains(keyword, ignoreCase = true)) {
                    results.add(LyricsSearchResult(
                        songName = song.name,
                        artistName = song.artist,
                        albumName = null,
                        lyricsPreview = extractPreview(lyrics, keyword)
                    ))
                }
            }
        }

        return results.take(limit)
    }

    /**
     * 正则搜索歌词。
     */
    suspend fun searchLyricsRegex(regex: Regex, limit: Int = 10): List<LyricsSearchResult> {
        val results = mutableListOf<LyricsSearchResult>()
        val data = getRawData()

        for (artist in data.artists.values) {
            for (album in artist.albums.values) {
                for (song in album.songs.values) {
                    val lyrics = song.lyrics
                    if (lyrics != null && regex.containsMatchIn(lyrics)) {
                        results.add(LyricsSearchResult(
                            songName = song.name,
                            artistName = song.artist,
                            albumName = song.album,
                            lyricsPreview = extractPreviewRegex(lyrics, regex)
                        ))
                    }
                }
            }

            for (song in artist._singles.songs.values) {
                val lyrics = song.lyrics
                if (lyrics != null && regex.containsMatchIn(lyrics)) {
                    results.add(LyricsSearchResult(
                        songName = song.name,
                        artistName = song.artist,
                        albumName = null,
                        lyricsPreview = extractPreviewRegex(lyrics, regex)
                    ))
                }
            }
        }

        return results.take(limit)
    }

    /**
     * 获取数据版本哈希。
     */
    suspend fun getDataVersionHash(): String {
        val data = getRawData()
        val totalPlays = data.artists.values.sumOf { it._info.playCount }
        val totalDuration = data.artists.values.sumOf { it._info.totalDurationMs }
        val lastPlayed = data.artists.values.maxOfOrNull { it._info.lastPlayedMs } ?: 0
        return "$totalPlays-$totalDuration-$lastPlayed"
    }

    /**
     * 清除缓存。
     */
    fun invalidateCache() {
        data = null
    }

    // ===== 辅助方法 =====

    private fun extractPreview(lyrics: String, keyword: String, contextLength: Int = 100): String {
        val index = lyrics.indexOf(keyword, ignoreCase = true)
        if (index == -1) return lyrics.take(200)

        val start = maxOf(0, index - contextLength)
        val end = minOf(lyrics.length, index + keyword.length + contextLength)

        val preview = lyrics.substring(start, end)
        return if (start > 0) "...$preview" else preview
    }

    private fun extractPreviewRegex(lyrics: String, regex: Regex, contextLength: Int = 100): String {
        val match = regex.find(lyrics) ?: return lyrics.take(200)

        val start = maxOf(0, match.range.first - contextLength)
        val end = minOf(lyrics.length, match.range.last + 1 + contextLength)

        val preview = lyrics.substring(start, end)
        return if (start > 0) "...$preview" else preview
    }

    // ===== 数据类 =====

    @kotlinx.serialization.Serializable
    data class LyricsSearchResult(
        val songName: String,
        val artistName: String,
        val albumName: String?,
        val lyricsPreview: String
    )
}
