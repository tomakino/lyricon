/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ai.analytics

import android.util.Log
import io.github.proify.android.extensions.json
import io.github.proify.lyricon.common.analytics.PlayHistoryData

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 音乐分析工具函数集合。
 *
 * 颗粒化设计：
 * - 底层：直接访问原始数据
 * - 中层：单实体查询
 * - 高层：聚合统计
 */
class MusicAnalysisTools(private val store: PlayHistoryStore) {

    companion object {
        private const val TAG = "MusicAnalysisTools"

        /**
         * 工具定义（集中声明，一处定义，多处使用）
         */
        data class ToolDef(
            val name: String,
            val displayName: String,
            val description: String,
            val handler: suspend MusicAnalysisTools.(Map<String, Any?>) -> String,
            val required: List<String> = emptyList(),
            val params: List<ParamDef> = emptyList(),
        )

        data class ParamDef(
            val name: String,
            val description: String,
            val type: String = "string",
        )

        /**
         * 所有工具定义（唯一声明处）
         */
        val TOOLS = listOf(
            // ===== 底层工具 =====
            ToolDef(
                name = "get_raw_data",
                displayName = "📦 数据摘要",
                description = "获取数据摘要（限制返回量）",
                handler = MusicAnalysisTools::handleGetRawData,
                params = listOf(
                    ParamDef("artist", "指定歌手名获取详情", "string"),
                    ParamDef("limit", "返回歌手数量，默认5", "integer"),
                ),
            ),
            ToolDef(
                name = "get_artist_raw",
                displayName = "📦 歌手原始数据",
                description = "获取歌手原始数据",
                handler = MusicAnalysisTools::handleGetArtistRaw,
                required = listOf("name"),
                params = listOf(ParamDef("name", "歌手名称")),
            ),
            ToolDef(
                name = "get_album_raw",
                displayName = "📦 专辑原始数据",
                description = "获取专辑原始数据",
                handler = MusicAnalysisTools::handleGetAlbumRaw,
                required = listOf("artist_name", "album_name"),
                params = listOf(
                    ParamDef("artist_name", "歌手名称"),
                    ParamDef("album_name", "专辑名称"),
                ),
            ),
            ToolDef(
                name = "get_song_raw",
                displayName = "📦 歌曲原始数据",
                description = "获取歌曲原始数据",
                handler = MusicAnalysisTools::handleGetSongRaw,
                required = listOf("artist_name", "song_name"),
                params = listOf(
                    ParamDef("artist_name", "歌手名称"),
                    ParamDef("song_name", "歌曲名称"),
                ),
            ),
            ToolDef(
                name = "get_records_raw",
                displayName = "📦 播放记录",
                description = "获取播放记录原始数据",
                handler = MusicAnalysisTools::handleGetRecordsRaw,
                required = listOf("artist_name", "song_name"),
                params = listOf(
                    ParamDef("artist_name", "歌手名称"),
                    ParamDef("song_name", "歌曲名称"),
                ),
            ),

            // ===== 中层工具 =====
            ToolDef(
                name = "get_artist",
                displayName = "🎤 歌手详情",
                description = "获取歌手详情",
                handler = MusicAnalysisTools::handleGetArtist,
                required = listOf("name"),
                params = listOf(ParamDef("name", "歌手名称")),
            ),
            ToolDef(
                name = "get_artist_albums",
                displayName = "💿 专辑列表",
                description = "获取歌手专辑列表",
                handler = MusicAnalysisTools::handleGetArtistAlbums,
                required = listOf("artist_name"),
                params = listOf(ParamDef("artist_name", "歌手名称")),
            ),
            ToolDef(
                name = "get_album",
                displayName = "💿 专辑详情",
                description = "获取专辑详情",
                handler = MusicAnalysisTools::handleGetAlbum,
                required = listOf("artist_name", "album_name"),
                params = listOf(
                    ParamDef("artist_name", "歌手名称"),
                    ParamDef("album_name", "专辑名称"),
                ),
            ),
            ToolDef(
                name = "get_album_songs",
                displayName = "🎵 专辑歌曲",
                description = "获取专辑歌曲列表",
                handler = MusicAnalysisTools::handleGetAlbumSongs,
                required = listOf("artist_name", "album_name"),
                params = listOf(
                    ParamDef("artist_name", "歌手名称"),
                    ParamDef("album_name", "专辑名称"),
                ),
            ),
            ToolDef(
                name = "get_song",
                displayName = "🎵 歌曲详情",
                description = "获取歌曲详情",
                handler = MusicAnalysisTools::handleGetSong,
                required = listOf("artist_name", "song_name"),
                params = listOf(
                    ParamDef("artist_name", "歌手名称"),
                    ParamDef("song_name", "歌曲名称"),
                ),
            ),
            ToolDef(
                name = "get_lyrics",
                displayName = "📜 获取歌词",
                description = "获取歌词",
                handler = MusicAnalysisTools::handleGetLyrics,
                required = listOf("artist_name", "song_name"),
                params = listOf(
                    ParamDef("artist_name", "歌手名称"),
                    ParamDef("song_name", "歌曲名称"),
                ),
            ),
            ToolDef(
                name = "search_lyrics",
                displayName = "🔍 歌词搜索",
                description = "搜索歌词",
                handler = MusicAnalysisTools::handleSearchLyrics,
                required = listOf("keyword"),
                params = listOf(
                    ParamDef("keyword", "搜索关键词或正则表达式"),
                    ParamDef("limit", "返回数量，默认10", "integer"),
                    ParamDef("regex", "是否使用正则表达式，默认false", "boolean"),
                ),
            ),
            ToolDef(
                name = "search_artists",
                displayName = "🔍 歌手搜索",
                description = "搜索歌手",
                handler = MusicAnalysisTools::handleSearchArtists,
                required = listOf("keyword"),
                params = listOf(
                    ParamDef("keyword", "搜索关键词或正则表达式"),
                    ParamDef("limit", "返回数量，默认20", "integer"),
                    ParamDef("regex", "是否使用正则表达式，默认false", "boolean"),
                ),
            ),
            ToolDef(
                name = "search_songs",
                displayName = "🔍 歌曲搜索",
                description = "搜索歌曲",
                handler = MusicAnalysisTools::handleSearchSongs,
                required = listOf("keyword"),
                params = listOf(
                    ParamDef("keyword", "搜索关键词或正则表达式"),
                    ParamDef("limit", "返回数量，默认20", "integer"),
                    ParamDef("regex", "是否使用正则表达式，默认false", "boolean"),
                ),
            ),

            // ===== 高层工具 =====
            ToolDef(
                name = "get_overview",
                displayName = "📊 概览",
                description = "获取播放概览",
                handler = MusicAnalysisTools::handleGetOverview,
            ),
            ToolDef(
                name = "get_top_artists",
                displayName = "🏆 热门歌手",
                description = "获取热门歌手",
                handler = MusicAnalysisTools::handleGetTopArtists,
                params = listOf(ParamDef("limit", "返回数量，默认10", "integer")),
            ),
            ToolDef(
                name = "get_top_songs",
                displayName = "🏆 热门歌曲",
                description = "获取热门歌曲",
                handler = MusicAnalysisTools::handleGetTopSongs,
                params = listOf(ParamDef("limit", "返回数量，默认10", "integer")),
            ),
            ToolDef(
                name = "get_time_distribution",
                displayName = "⏰ 时段分布",
                description = "获取时段分布",
                handler = MusicAnalysisTools::handleGetTimeDistribution,
            ),
            ToolDef(
                name = "get_player_distribution",
                displayName = "📱 播放器分布",
                description = "获取播放器分布",
                handler = MusicAnalysisTools::handleGetPlayerDistribution,
            ),
//            ToolDef(
//                name = "get_lyrics_stats",
//                displayName = "📝 歌词统计",
//                description = "获取歌词统计",
//                handler = MusicAnalysisTools::handleGetLyricsStats,
//            ),
        )

        /** 工具名 -> 定义 */
        private val TOOL_MAP = TOOLS.associateBy { it.name }

        fun getDisplayName(toolName: String): String =
            TOOL_MAP[toolName]?.displayName ?: toolName
    }

    /** 工具名 -> 执行函数 */
    private val dispatch: Map<String, suspend (Map<String, Any?>) -> String> =
        TOOLS.associate { it.name to { args: Map<String, Any?> -> it.handler.invoke(this, args) } }

    /**
     * 执行工具函数。
     */
    suspend fun execute(name: String, arguments: String): String {
        val handler = dispatch[name] ?: return """{"error": "Unknown tool: $name"}"""
        Log.d(TAG, "Executing: $name")
        return try {
            val args = parseArguments(arguments)
            handler(args)
        } catch (e: Exception) {
            Log.e(TAG, "Tool $name failed: ${e.message}", e)
            """{"error": "${e.message}"}"""
        }
    }

    private fun parseArguments(arguments: String): Map<String, Any?> {
        if (arguments.isBlank()) return emptyMap()
        return runCatching {
            val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(arguments)
            if (jsonObj is kotlinx.serialization.json.JsonObject) {
                jsonObj.entries.associate { (key, value) ->
                    key to when (value) {
                        is kotlinx.serialization.json.JsonPrimitive -> {
                            if (value.isString) value.content
                            else value.content.toIntOrNull() ?: value.content.toLongOrNull()
                            ?: value.content.toDoubleOrNull() ?: value.content
                        }
                        else -> value.toString()
                    }
                }
            } else emptyMap()
        }.getOrElse { emptyMap() }
    }

    private fun intArg(args: Map<String, Any?>, key: String, default: Int): Int {
        return (args[key] as? Number)?.toInt() ?: args[key]?.toString()?.toIntOrNull() ?: default
    }

    private fun strArg(args: Map<String, Any?>, key: String): String {
        return args[key]?.toString() ?: ""
    }

    // ===== 底层工具实现 =====

    private suspend fun handleGetRawData(args: Map<String, Any?>): String {
        val artistName = strArg(args, "artist")
        val limit = intArg(args, "limit", 5)
        val data = store.getRawData()

        if (artistName.isNotBlank()) {
            val artist = data.artists[artistName]
            return if (artist != null) {
                json.encodeToString(PlayHistoryData.ArtistData.serializer(), artist)
            } else {
                """{"error": "Artist not found: $artistName"}"""
            }
        }

        return buildJsonObject {
            for ((name, artist) in data.artists.entries.take(limit)) {
                put(name, buildJsonObject {
                    put("playCount", JsonPrimitive(artist._info.playCount))
                    put("uniqueSongs", JsonPrimitive(artist._info.uniqueSongs))
                    put("albums", kotlinx.serialization.json.JsonArray(
                        artist.albums.keys.map { JsonPrimitive(it) }
                    ))
                    put("singlesCount", JsonPrimitive(artist._singles.songs.size))
                })
            }
        }.toString()
    }

    private suspend fun handleGetArtistRaw(args: Map<String, Any?>): String {
        val name = strArg(args, "name")
        val artist = store.getArtist(name)
        return if (artist != null) {
            json.encodeToString(PlayHistoryData.ArtistData.serializer(), artist)
        } else {
            """{"error": "Artist not found: $name"}"""
        }
    }

    private suspend fun handleGetAlbumRaw(args: Map<String, Any?>): String {
        val artistName = strArg(args, "artist_name")
        val albumName = strArg(args, "album_name")
        val artist = store.getArtist(artistName)
        if (artist == null) return """{"error": "Artist not found: $artistName"}"""

        val album = artist.albums[albumName]
        return if (album != null) {
            json.encodeToString(PlayHistoryData.AlbumData.serializer(), album)
        } else {
            """{"error": "Album not found: $albumName"}"""
        }
    }

    private suspend fun handleGetSongRaw(args: Map<String, Any?>): String {
        val artistName = strArg(args, "artist_name")
        val songName = strArg(args, "song_name")
        val song = store.getSong(artistName, songName)
        return if (song != null) {
            json.encodeToString(PlayHistoryData.SongDetail.serializer(), song)
        } else {
            """{"error": "Song not found: $songName by $artistName"}"""
        }
    }

    private suspend fun handleGetRecordsRaw(args: Map<String, Any?>): String {
        val artistName = strArg(args, "artist_name")
        val songName = strArg(args, "song_name")
        val song = store.getSong(artistName, songName)
        return if (song != null) {
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(PlayHistoryData.PlayRecord.serializer()),
                song.records
            )
        } else {
            """{"error": "Song not found: $songName by $artistName"}"""
        }
    }

    // ===== 中层工具实现 =====

    private suspend fun handleGetArtist(args: Map<String, Any?>): String {
        val name = strArg(args, "name")
        val artist = store.getArtist(name)
        return if (artist != null) {
            buildJsonObject {
                put("name", JsonPrimitive(name))
                put("playCount", JsonPrimitive(artist._info.playCount))
                put("totalDuration", JsonPrimitive(artist._info.totalDurationMs))
                put("uniqueSongs", JsonPrimitive(artist._info.uniqueSongs))
                put("uniqueAlbums", JsonPrimitive(artist._info.uniqueAlbums))
                put("firstPlayed", JsonPrimitive(artist._info.firstPlayedMs))
                put("lastPlayed", JsonPrimitive(artist._info.lastPlayedMs))
                put("albums", kotlinx.serialization.json.JsonArray(artist.albums.keys.map { JsonPrimitive(it) }))
                put("singles", kotlinx.serialization.json.JsonArray(artist._singles.songs.keys.map { JsonPrimitive(it) }))
            }.toString()
        } else {
            """{"error": "Artist not found: $name"}"""
        }
    }

    private suspend fun handleGetArtistAlbums(args: Map<String, Any?>): String {
        val artistName = strArg(args, "artist_name")
        val artist = store.getArtist(artistName)
        return if (artist != null) {
            buildJsonObject {
                for ((name, album) in artist.albums) {
                    put(name, buildJsonObject {
                        put("playCount", JsonPrimitive(album._info.playCount))
                        put("songCount", JsonPrimitive(album._info.songCount))
                    })
                }
            }.toString()
        } else {
            """{"error": "Artist not found: $artistName"}"""
        }
    }

    private suspend fun handleGetAlbum(args: Map<String, Any?>): String {
        val artistName = strArg(args, "artist_name")
        val albumName = strArg(args, "album_name")
        val artist = store.getArtist(artistName)
        if (artist == null) return """{"error": "Artist not found: $artistName"}"""

        val album = artist.albums[albumName]
        return if (album != null) {
            buildJsonObject {
                put("name", JsonPrimitive(albumName))
                put("artist", JsonPrimitive(artistName))
                put("playCount", JsonPrimitive(album._info.playCount))
                put("totalDuration", JsonPrimitive(album._info.totalDuration))
                put("songCount", JsonPrimitive(album._info.songCount))
                put("songs", kotlinx.serialization.json.JsonArray(album.songs.keys.map { JsonPrimitive(it) }))
            }.toString()
        } else {
            """{"error": "Album not found: $albumName"}"""
        }
    }

    private suspend fun handleGetAlbumSongs(args: Map<String, Any?>): String {
        val artistName = strArg(args, "artist_name")
        val albumName = strArg(args, "album_name")
        val artist = store.getArtist(artistName)
        if (artist == null) return """{"error": "Artist not found: $artistName"}"""

        val album = artist.albums[albumName]
        return if (album != null) {
            buildJsonObject {
                for (song in album.songs.values) {
                    put(song.name, buildJsonObject {
                        put("duration", JsonPrimitive(song.durationMs))
                        put("playCount", JsonPrimitive(song.playCount))
                        put("hasLyrics", JsonPrimitive(song.hasLyrics))
                    })
                }
            }.toString()
        } else {
            """{"error": "Album not found: $albumName"}"""
        }
    }

    private suspend fun handleGetSong(args: Map<String, Any?>): String {
        val artistName = strArg(args, "artist_name")
        val songName = strArg(args, "song_name")
        val song = store.getSong(artistName, songName)
        return if (song != null) {
            buildJsonObject {
                put("name", JsonPrimitive(song.name))
                put("artist", JsonPrimitive(song.artist))
                put("album", JsonPrimitive(song.album ?: ""))
                put("duration", JsonPrimitive(song.durationMs))
                put("playCount", JsonPrimitive(song.playCount))
                put("totalPlayedDuration", JsonPrimitive(song.totalPlayedDuration))
                put("firstPlayed", JsonPrimitive(song.firstPlayedMs))
                put("lastPlayed", JsonPrimitive(song.lastPlayedMs))
                put("hasLyrics", JsonPrimitive(song.hasLyrics))
                put("lyricsLineCount", JsonPrimitive(song.lyricsLineCount))
                put("lyricsWordCount", JsonPrimitive(song.lyricsWordCount))
                put("recordCount", JsonPrimitive(song.records.size))
            }.toString()
        } else {
            """{"error": "Song not found: $songName by $artistName"}"""
        }
    }

    private suspend fun handleGetLyrics(args: Map<String, Any?>): String {
        val artistName = strArg(args, "artist_name")
        val songName = strArg(args, "song_name")
        val lyrics = store.getLyrics(artistName, songName)
        return if (lyrics != null) {
            buildJsonObject {
                put("songName", JsonPrimitive(songName))
                put("artistName", JsonPrimitive(artistName))
                put("lyrics", JsonPrimitive(lyrics))
            }.toString()
        } else {
            """{"error": "Lyrics not found"}"""
        }
    }

    private suspend fun handleSearchLyrics(args: Map<String, Any?>): String {
        val keyword = strArg(args, "keyword")
        val limit = intArg(args, "limit", 10)
        val useRegex = args["regex"]?.toString()?.toBoolean() ?: false

        val results = if (useRegex) {
            try {
                val regex = Regex(keyword, RegexOption.IGNORE_CASE)
                store.searchLyricsRegex(regex, limit)
            } catch (e: Exception) {
                return """{"error": "Invalid regex: ${e.message}"}"""
            }
        } else {
            store.searchLyrics(keyword, limit)
        }
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(PlayHistoryStore.LyricsSearchResult.serializer()),
            results
        )
    }

    private suspend fun handleSearchArtists(args: Map<String, Any?>): String {
        val keyword = strArg(args, "keyword")
        val limit = intArg(args, "limit", 20)
        val useRegex = args["regex"]?.toString()?.toBoolean() ?: false

        val artistNames = store.getArtistNames()
        val matcher: (String) -> Boolean
        if (useRegex) {
            try {
                val regex = Regex(keyword, RegexOption.IGNORE_CASE)
                matcher = { text: String -> regex.containsMatchIn(text) }
            } catch (e: Exception) {
                return """{"error": "Invalid regex: ${e.message}"}"""
            }
        } else {
            matcher = { text: String -> text.contains(keyword, ignoreCase = true) }
        }

        val results = artistNames
            .filter { matcher(it) }
            .take(limit)
            .mapNotNull { name ->
                store.getArtist(name)?.let { artist ->
                    buildJsonObject {
                        put("name", JsonPrimitive(name))
                        put("playCount", JsonPrimitive(artist._info.playCount))
                    }
                }
            }
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.json.JsonObject.serializer()),
            results
        )
    }

    private suspend fun handleSearchSongs(args: Map<String, Any?>): String {
        val keyword = strArg(args, "keyword")
        val limit = intArg(args, "limit", 20)
        val useRegex = args["regex"]?.toString()?.toBoolean() ?: false

        val matcher: (String) -> Boolean
        if (useRegex) {
            try {
                val regex = Regex(keyword, RegexOption.IGNORE_CASE)
                matcher = { text: String -> regex.containsMatchIn(text) }
            } catch (e: Exception) {
                return """{"error": "Invalid regex: ${e.message}"}"""
            }
        } else {
            matcher = { text: String -> text.contains(keyword, ignoreCase = true) }
        }

        val results = mutableListOf<kotlinx.serialization.json.JsonObject>()

        for (artistName in store.getArtistNames()) {
            val artist = store.getArtist(artistName) ?: continue

            for (album in artist.albums.values) {
                for (song in album.songs.values) {
                    if (matcher(song.name) || matcher(song.artist)) {
                        results.add(buildJsonObject {
                            put("name", JsonPrimitive(song.name))
                            put("artist", JsonPrimitive(song.artist))
                            put("album", JsonPrimitive(song.album ?: ""))
                            put("playCount", JsonPrimitive(song.playCount))
                        })
                    }
                }
            }

            for (song in artist._singles.songs.values) {
                if (matcher(song.name) || matcher(song.artist)) {
                    results.add(buildJsonObject {
                        put("name", JsonPrimitive(song.name))
                        put("artist", JsonPrimitive(song.artist))
                        put("album", JsonPrimitive(""))
                        put("playCount", JsonPrimitive(song.playCount))
                    })
                }
            }
        }

        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.json.JsonObject.serializer()),
            results.sortedByDescending {
                it["playCount"]?.let {
                    (it as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                } ?: 0
            }.take(limit)
        )
    }

    // ===== 高层工具实现 =====

    private suspend fun handleGetOverview(args: Map<String, Any?>): String {
        val data = store.getRawData()
        var totalPlays = 0
        var totalDuration = 0L
        var uniqueSongs = 0
        var uniqueArtists = 0
        var songsWithLyrics = 0

        for (artist in data.artists.values) {
            uniqueArtists++
            totalPlays += artist._info.playCount
            totalDuration += artist._info.totalDurationMs
            uniqueSongs += artist._info.uniqueSongs

            for (album in artist.albums.values) {
                for (song in album.songs.values) {
                    if (song.hasLyrics) songsWithLyrics++
                }
            }

            for (song in artist._singles.songs.values) {
                if (song.hasLyrics) songsWithLyrics++
            }
        }

        return buildJsonObject {
            put("totalPlays", JsonPrimitive(totalPlays))
            put("totalDuration", JsonPrimitive(totalDuration))
            put("uniqueSongs", JsonPrimitive(uniqueSongs))
            put("uniqueArtists", JsonPrimitive(uniqueArtists))
            put("songsWithLyrics", JsonPrimitive(songsWithLyrics))
            put("lyricsCoverage", JsonPrimitive(
                if (uniqueSongs > 0) songsWithLyrics.toFloat() / uniqueSongs else 0f
            ))
        }.toString()
    }

    private suspend fun handleGetTopArtists(args: Map<String, Any?>): String {
        val limit = intArg(args, "limit", 10)
        val artistNames = store.getArtistNames()
        val artists = artistNames
            .mapNotNull { name -> store.getArtist(name)?.let { name to it._info } }
            .sortedByDescending { it.second.playCount }
            .take(limit)
            .map { (name, info) ->
                buildJsonObject {
                    put("name", JsonPrimitive(name))
                    put("playCount", JsonPrimitive(info.playCount))
                    put("totalDuration", JsonPrimitive(info.totalDurationMs))
                    put("uniqueSongs", JsonPrimitive(info.uniqueSongs))
                }
            }
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.json.JsonObject.serializer()),
            artists
        )
    }

    private suspend fun handleGetTopSongs(args: Map<String, Any?>): String {
        val limit = intArg(args, "limit", 10)
        val allSongs = mutableListOf<PlayHistoryData.SongDetail>()

        for (artistName in store.getArtistNames()) {
            val artist = store.getArtist(artistName) ?: continue
            for (album in artist.albums.values) {
                allSongs.addAll(album.songs.values)
            }
            allSongs.addAll(artist._singles.songs.values)
        }

        val topSongs = allSongs
            .sortedByDescending { it.playCount }
            .take(limit)
            .map { song ->
                buildJsonObject {
                    put("name", JsonPrimitive(song.name))
                    put("artist", JsonPrimitive(song.artist))
                    put("album", JsonPrimitive(song.album ?: ""))
                    put("playCount", JsonPrimitive(song.playCount))
                    put("totalPlayedDuration", JsonPrimitive(song.totalPlayedDuration))
                    put("hasLyrics", JsonPrimitive(song.hasLyrics))
                }
            }
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.json.JsonObject.serializer()),
            topSongs
        )
    }

    private suspend fun handleGetTimeDistribution(args: Map<String, Any?>): String {
        val distribution = mutableMapOf<String, Int>()

        for (artistName in store.getArtistNames()) {
            val artist = store.getArtist(artistName) ?: continue

            for (album in artist.albums.values) {
                for (song in album.songs.values) {
                    for (record in song.records) {
                        val period = record.timePeriod
                        distribution[period] = (distribution[period] ?: 0) + 1
                    }
                }
            }

            for (song in artist._singles.songs.values) {
                for (record in song.records) {
                    val period = record.timePeriod
                    distribution[period] = (distribution[period] ?: 0) + 1
                }
            }
        }

        return buildJsonObject {
            for ((key, value) in distribution) {
                put(key, JsonPrimitive(value))
            }
        }.toString()
    }

    private suspend fun handleGetPlayerDistribution(args: Map<String, Any?>): String {
        val distribution = mutableMapOf<String, Int>()

        for (artistName in store.getArtistNames()) {
            val artist = store.getArtist(artistName) ?: continue

            for (album in artist.albums.values) {
                for (song in album.songs.values) {
                    for (record in song.records) {
                        val player = record.playerPackageName
                        distribution[player] = (distribution[player] ?: 0) + 1
                    }
                }
            }

            for (song in artist._singles.songs.values) {
                for (record in song.records) {
                    val player = record.playerPackageName
                    distribution[player] = (distribution[player] ?: 0) + 1
                }
            }
        }

        return buildJsonObject {
            for ((key, value) in distribution) {
                put(key, JsonPrimitive(value))
            }
        }.toString()
    }

//    private suspend fun handleGetLyricsStats(args: Map<String, Any?>): String {
//        val data = store.getRawData()
//        var totalSongs = 0
//        var songsWithLyrics = 0
//        var totalLyricsLength = 0
//
//        for (artist in data.artists.values) {
//            for (album in artist.albums.values) {
//                for (song in album.songs.values) {
//                    totalSongs++
//                    if (song.hasLyrics) {
//                        songsWithLyrics++
//                        totalLyricsLength += song.lyricsWordCount
//                    }
//                }
//            }
//
//            for (song in artist._singles.songs.values) {
//                totalSongs++
//                if (song.hasLyrics) {
//                    songsWithLyrics++
//                    totalLyricsLength += song.lyricsWordCount
//                }
//            }
//        }
//
//        return buildJsonObject {
//            put("totalSongs", JsonPrimitive(totalSongs))
//            put("songsWithLyrics", JsonPrimitive(songsWithLyrics))
//            put("lyricsCoverage", JsonPrimitive(
//                if (totalSongs > 0) songsWithLyrics.toFloat() / totalSongs else 0f
//            ))
//            put("avgLyricsLength", JsonPrimitive(
//                if (songsWithLyrics > 0) totalLyricsLength / songsWithLyrics else 0
//            ))
//        }.toString()
//    }

    /**
     * 构建 tools JSON 数组（供 Agent 使用）。
     */
    fun buildToolsJsonArray(): kotlinx.serialization.json.JsonArray {
        return kotlinx.serialization.json.buildJsonArray {
            for (tool in TOOLS) {
                add(buildJsonObject {
                    put("type", JsonPrimitive("function"))
                    put("function", buildJsonObject {
                        put("name", JsonPrimitive(tool.name))
                        put("description", JsonPrimitive(tool.description))
                        put("parameters", buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", buildJsonObject {
                                for (param in tool.params) {
                                    put(param.name, buildJsonObject {
                                        put("type", JsonPrimitive(param.type))
                                        put("description", JsonPrimitive(param.description))
                                    })
                                }
                            })
                            if (tool.required.isNotEmpty()) {
                                put("required", kotlinx.serialization.json.JsonArray(
                                    tool.required.map { JsonPrimitive(it) }
                                ))
                            }
                        })
                    })
                })
            }
        }
    }
}
