/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.ai.explain

import android.content.Context
import android.content.Intent
import io.github.proify.lyricon.lyric.ai.explain.AiExplainContract
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.extensions.deepCopy
import io.github.proify.lyricon.xposed.BuildConfig
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.lyric.LyricViewController
import io.github.proify.lyricon.xposed.systemui.util.MediaTrackMeta
import kotlinx.serialization.json.Json

/**
 * AI 歌词解释启动器（AI Lyric Explain Launcher）
 *
 * 由控制窗口右下角的 AI 按钮触发：
 * 1. 抽取当前歌词全文作为上下文；
 * 2. 启动 App 进程内的透明 [io.github.proify.lyricon.app.activity.lyric.AiExplainActivity]，
 *    把歌曲元数据与歌词上下文通过 Intent extras 传过去；
 * 3. 解读请求、流式解析(含思考过程)、缓存与复制/重试全部在 App 进程完成，
 *    复用统一的 [io.github.proify.lyricon.lyric.ai.core.AiConfig]。
 *
 * 之所以迁出 SystemUI：SystemUI 进程没有 Compose/miuix 运行环境，
 * 用 PopupWindow 自绘面板既居中不可靠，也无法呈现漂亮的底部弹层。
 *
 * @author Tomakino
 * @since 2026
 */
object AiExplainLauncher {

    private const val TAG = "AiExplainLauncher"

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
        encodeDefaults = false
    }

    /**
     * 启动一次"解释歌词"流程。
     */
    fun launch(context: Context, song: Song?) {
        val lyrics = extractContextLyrics(song)

        val meta = MediaTrackMeta.resolve(LyricViewController.activePackage)
        val displaySong = mergeSystemMeta(song, meta)
        val intent = Intent().apply {
            setClassName(BuildConfig.APP_PACKAGE_NAME, AiExplainContract.ACTIVITY_CLASS)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(AiExplainContract.EXTRA_TITLE, displaySong?.name.orEmpty())
            putExtra(AiExplainContract.EXTRA_ARTIST, displaySong?.artist.orEmpty())
            putExtra(AiExplainContract.EXTRA_ALBUM, meta?.album.orEmpty())
            putExtra(AiExplainContract.EXTRA_LYRICS, lyrics)
        }

        runCatching { context.startActivity(intent) }
            .onFailure { YLog.error(TAG, "Failed to launch AiExplainActivity", it) }
    }

    // -------------------------------------------------------------------------
    // 上下文抽取 / 元数据合并
    // -------------------------------------------------------------------------

    /**
     * 抽取完整歌词作为 AI 解读上下文：直接输出 song.lyrics 的格式化 JSON。
     */
    private fun extractContextLyrics(song: Song?): String {
        val lyrics = song?.lyrics?.deepCopy()
            .orEmpty()
            .map {
                it.words = null
                it.secondaryWords = null
                it.translationWords = null
                it
            }

        if (lyrics.isEmpty()) return "无歌词"

        return prettyJson.encodeToString(lyrics)
    }

    /**
     * 用系统媒体会话元数据覆盖 Song 的标题/歌手（仅用于 AI 请求的元数据，
     * 歌词行不参与合并）。系统元数据优先，缺失时保留 Song 原值。
     */
    private fun mergeSystemMeta(
        song: Song?,
        meta: MediaTrackMeta.TrackMeta? = null
    ): Song? {
        val song = song ?: return null
        val track = meta ?: return song
        val title = track.title ?: song.name
        val artist = track.artist ?: song.artist
        if (title == song.name && artist == song.artist) return song
        return song.copy(name = title, artist = artist)
    }
}
