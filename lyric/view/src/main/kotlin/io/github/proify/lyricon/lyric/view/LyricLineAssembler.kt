/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.model.lyricMetadataOf

internal class LyricLineAssembler(
    private var displayTranslation: Boolean = true,
    private var displayRoma: Boolean = true,
    private var enableRelativeProgress: Boolean = false,
    private var enableRelativeHighlight: Boolean = false,
) {
    private val wordBuilder = RelativeWordBuilder()

    fun updateFlags(
        displayTranslation: Boolean, displayRoma: Boolean,
        enableRelativeProgress: Boolean, enableRelativeHighlight: Boolean
    ) {
        this.displayTranslation = displayTranslation
        this.displayRoma = displayRoma
        this.enableRelativeProgress = enableRelativeProgress
        this.enableRelativeHighlight = enableRelativeHighlight
    }

    data class MainResult(
        val line: LyricLine,
        val isScrollOnly: Boolean,
        val syntheticSingleWord: Boolean,
    )

    fun buildMain(source: IRichLyricLine?, isTitle: Boolean): MainResult {
        if (source == null) return MainResult(LyricLine(), false, false)

        val shouldGen = enableRelativeProgress && !isTitle
        val words = if (shouldGen) {
            wordBuilder.build(source, source.text, source.words)
        } else source.words

        val generated = words !== source.words
        val line = LyricLine(
            begin = source.begin, end = source.end, duration = source.duration,
            isAlignedRight = source.isAlignedRight, metadata = source.metadata,
            text = source.text, words = words
        )
        val syntheticSingleWord = generated && line.words?.size == 1
        return MainResult(line, generated && !enableRelativeHighlight, syntheticSingleWord)
    }

    data class SecondaryResult(
        val line: LyricLine,
        val alwaysShow: Boolean,
        val isScrollOnly: Boolean,
        val syntheticSingleWord: Boolean,
    )

    fun buildSecondary(source: IRichLyricLine?): SecondaryResult {
        if (source == null) return SecondaryResult(LyricLine(), false, false, false)

        var generated = false
        val line = LyricLine().apply {
            begin = source.begin; end = source.end; duration = source.duration
            isAlignedRight = source.isAlignedRight

            when {
                !source.secondary.isNullOrBlank() || !source.secondaryWords.isNullOrEmpty() -> {
                    text = source.secondary
                    words = wordBuilder.build(source, source.secondary, source.secondaryWords)
                    generated = words !== source.secondaryWords
                }

                displayTranslation && (!source.translation.isNullOrBlank()
                        || !source.translationWords.isNullOrEmpty()) -> {
                    text = source.translation
                    words = wordBuilder.build(source, source.translation, source.translationWords)
                    metadata = lyricMetadataOf("translation" to "true")
                    generated = words !== source.translationWords
                }

                displayRoma -> {
                    text = source.roma
                    words = wordBuilder.build(source, source.roma, null)
                    metadata = lyricMetadataOf("roma" to "true")
                    generated = true
                }
            }
        }

        val hasContent = line.text?.isNotBlank() == true || !line.words.isNullOrEmpty()
        val isPlain = line.words?.isEmpty() == true
        val alwaysShow = hasContent && (
                isPlain || line.metadata?.getBoolean("translation") == true
                        || line.metadata?.getBoolean("roma") == true
                        || line.words?.firstOrNull()?.begin?.let { (it - source.begin) < 500 } == true
                )

        val syntheticSingleWord = generated && line.words?.size == 1
        return SecondaryResult(
            line,
            alwaysShow,
            generated && !enableRelativeHighlight,
            syntheticSingleWord
        )
    }
}
