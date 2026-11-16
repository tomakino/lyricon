/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.model.lyricMetadataOf

internal class SongPreprocessor(private val placeholder: TitleSlot) {

    companion object {
        internal const val KEY_TITLE_LINE = "TitleLine"
    }

    fun prepare(song: Song): List<TimedLine> {
        val filled = fillGap(song)
        val lines = mutableListOf<TimedLine>()
        var prev: TimedLine? = null
        filled.lyrics?.forEach { lyric ->
            val tl = TimedLine(lyric).also {
                it.previous = prev
                prev?.next = it
            }
            lines.add(tl)
            prev = tl
        }
        return lines
    }

    private fun fillGap(song: Song): Song {
        val title = songTitle(song) ?: return song
        val lyrics = song.lyrics?.toMutableList() ?: mutableListOf()
        if (lyrics.isEmpty()) {
            val d = if (song.duration > 0) song.duration else Long.MAX_VALUE
            lyrics.add(titleLine(d, d, title))
        } else {
            val first = lyrics.first()
            if (first.begin > 0) {
                val end = first.begin
                val duration = if (end > 1) end - 1 else end
                lyrics.add(0, titleLine(end, duration, title))
            }
        }
        song.lyrics = lyrics
        return song
    }

    private fun titleLine(end: Long, duration: Long, text: String) =
        RichLyricLine(end = end, duration = duration, text = text).apply {
            metadata = lyricMetadataOf(KEY_TITLE_LINE to "true")
        }

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

internal class TimedLine(val line: IRichLyricLine) : IRichLyricLine by line {
    var previous: TimedLine? = null
    var next: TimedLine? = null
}
