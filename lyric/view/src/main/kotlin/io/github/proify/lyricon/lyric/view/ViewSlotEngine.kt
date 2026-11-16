/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.view.ViewGroup

internal class ViewSlotEngine(
    private val container: ViewGroup,
) {
    val toRemove = mutableListOf<RichLyricLineView>()
    val toAdd = mutableListOf<TimedLine>()

    fun sync(matches: List<TimedLine>, active: MutableList<TimedLine>) {
        toRemove.clear()
        toAdd.clear()

        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i) as? RichLyricLineView ?: continue
            val timed = view.timedLine ?: continue
            if (matches.none { it.line == timed.line }) toRemove.add(view)
        }
        matches.forEach { match ->
            if (active.none { it.line == match.line }) toAdd.add(match)
        }
    }
}
