/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine

internal data class PreparedLyrics(
    val title: RichLyricLine?,
    val lines: List<TimedLine>,
)

internal class SongPreprocessor(private val placeholder: TitleSlot) {

    fun prepare(song: Song): PreparedLyrics {
        val lyrics = song.lyrics.orEmpty()
        val title = songTitle(song)?.let { text ->
            when {
                lyrics.isEmpty() -> {
                    val duration = if (song.duration > 0) song.duration else Long.MAX_VALUE
                    titleLine(duration, duration, text)
                }

                lyrics.first().begin > 0 -> {
                    val end = lyrics.first().begin
                    val duration = if (end > 1) end - 1 else end
                    titleLine(end, duration, text)
                }

                else -> null
            }
        }

        val lines = mutableListOf<TimedLine>()
        var prev: TimedLine? = null
        lyrics.forEach { lyric ->
            val timed = TimedLine(lyric).also {
                it.previous = prev
                prev?.next = it
            }
            lines.add(timed)
            prev = timed
        }
        return PreparedLyrics(title, lines)
    }

    private fun titleLine(end: Long, duration: Long, text: String) =
        RichLyricLine(end = end, duration = duration, text = text)

    private fun songTitle(song: Song): String? {
        val name = song.name
        val artist = song.artist
        return when (placeholder) {
            TitleSlot.NONE -> null
            TitleSlot.NAME_ARTIST -> when {
                !name.isNullOrBlank() && !artist.isNullOrBlank() -> "$name - $artist"
                !name.isNullOrBlank() -> name
                else -> null
            }

            TitleSlot.NAME -> name?.takeIf { it.isNotBlank() }
        }
    }
}

internal class TimedLine(
    val line: IRichLyricLine,
) : IRichLyricLine by line {
    var previous: TimedLine? = null
    var next: TimedLine? = null
}
