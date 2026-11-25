/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.extensions.normalize
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricWordNormalizeTest {

    @Test
    fun mergesAdjacentAsciiFragmentsWithoutSpace() {
        val words = listOf(
            LyricWord(begin = 296453, duration = 200, end = 296653, text = "For"),
            LyricWord(begin = -1, end = -1, text = " "),
            LyricWord(begin = 296653, duration = 206, end = 296859, text = "we"),
            LyricWord(begin = -1, end = -1, text = " "),
            LyricWord(begin = 296937, duration = 490, end = 297427, text = "all"),
            LyricWord(begin = -1, end = -1, text = " "),
            LyricWord(begin = 297427, duration = 403, end = 297830, text = "live"),
            LyricWord(begin = -1, end = -1, text = " "),
            LyricWord(begin = 297830, duration = 418, end = 298248, text = "under"),
            LyricWord(begin = 298248, duration = 447, end = 298695, text = "ground"),
        )

        val normalized = words.normalize()

        assertEquals(
            "For we all live underground",
            normalized.joinToString("") { it.text.orEmpty() })
        assertEquals(
            listOf("For ", "we", " ", "all ", "live ", "underground"),
            normalized.map { it.text })
        assertEquals(297830, normalized.last().begin)
        assertEquals(298695, normalized.last().end)
        assertEquals(865, normalized.last().duration)
    }

    @Test
    fun keepsAsciiFragmentsSeparateWhenSpaceExists() {
        val words = listOf(
            LyricWord(begin = 0, duration = 300, end = 300, text = "under"),
            LyricWord(begin = 0, end = 0, text = " "),
            LyricWord(begin = 400, duration = 300, end = 700, text = "ground"),
        )

        val normalized = words.normalize()

        assertEquals(listOf("under", " ", "ground"), normalized.map { it.text })
        assertEquals("under ground", normalized.joinToString("") { it.text.orEmpty() })
    }

    @Test
    fun doesNotMergeNonAsciiTimedFragments() {
        val words = listOf(
            LyricWord(begin = 0, duration = 100, end = 100, text = "你"),
            LyricWord(begin = 100, duration = 100, end = 200, text = "好"),
        )

        val normalized = words.normalize()

        assertEquals(listOf("你", "好"), normalized.map { it.text })
    }
}
