/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.graphics.Color
import android.graphics.Typeface

open class LyricLineConfig(
    var text: TextConfig,
    var marquee: MarqueeConfig,
    var syllable: SyllableConfig,
    var gradientProgressStyle: Boolean,
    var fadingEdgeLength: Int,
)

data class RichLyricLineConfig(
    var primary: MainTextConfig = MainTextConfig(),
    var secondary: SecondaryTextConfig = SecondaryTextConfig(),
    var marquee: MarqueeConfig = DefaultMarqueeConfig(),
    var syllable: SyllableConfig = DefaultSyllableConfig(),
    var gradientProgressStyle: Boolean = true,
    var scaleInMultiLine: Float = 1f,
    var fadingEdgeLength: Int = 10,
    var placeholderFormat: String = PlaceholderFormat.NAME_ARTIST,
    var enableAnim: Boolean = false,
    var animId: String = "stack_flow"
)

object PlaceholderFormat {
    const val NAME: String = "NameOnly"
    const val NAME_ARTIST: String = "NameAndArtist"
    const val NONE: String = "None"
}

interface TextConfig {
    var textColor: IntArray
    var textSize: Float
    var typeface: Typeface
}

interface MarqueeConfig {
    var ghostSpacing: Float
    var scrollSpeed: Float
    var initialDelay: Int
    var loopDelay: Int
    var repeatCount: Int
    var stopAtEnd: Boolean
}

open class DefaultMarqueeConfig(
    override var scrollSpeed: Float = 40f,
    override var ghostSpacing: Float = 70f.dp,
    override var initialDelay: Int = 300,
    override var loopDelay: Int = 700,
    override var repeatCount: Int = -1,
    override var stopAtEnd: Boolean = false,
) : MarqueeConfig

interface SyllableConfig {
    var backgroundColor: IntArray
    var highlightColor: IntArray
    var enableSustainLift: Boolean
    var enableSustainGlow: Boolean
}

data class MainTextConfig(
    override var textColor: IntArray = intArrayOf(Color.WHITE),
    override var textSize: Float = 16f.sp,
    override var typeface: Typeface = Typeface.DEFAULT,
    var enableRelativeProgress: Boolean = false,
    var enableRelativeProgressHighlight: Boolean = false,
) : TextConfig {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MainTextConfig

        if (textSize != other.textSize) return false
        if (enableRelativeProgress != other.enableRelativeProgress) return false
        if (enableRelativeProgressHighlight != other.enableRelativeProgressHighlight) return false
        if (!textColor.contentEquals(other.textColor)) return false
        if (typeface != other.typeface) return false

        return true
    }

    override fun hashCode(): Int {
        var result = textSize.hashCode()
        result = 31 * result + enableRelativeProgress.hashCode()
        result = 31 * result + enableRelativeProgressHighlight.hashCode()
        result = 31 * result + textColor.contentHashCode()
        result = 31 * result + typeface.hashCode()
        return result
    }
}

data class DefaultSyllableConfig(
    override var highlightColor: IntArray = intArrayOf(Color.WHITE),
    override var backgroundColor: IntArray = intArrayOf(Color.GRAY),
    override var enableSustainLift: Boolean = true,
    override var enableSustainGlow: Boolean = true,
) : SyllableConfig {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultSyllableConfig

        if (!highlightColor.contentEquals(other.highlightColor)) return false
        if (!backgroundColor.contentEquals(other.backgroundColor)) return false
        if (enableSustainLift != other.enableSustainLift) return false
        if (enableSustainGlow != other.enableSustainGlow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = highlightColor.contentHashCode()
        result = 31 * result + backgroundColor.contentHashCode()
        result = 31 * result + enableSustainLift.hashCode()
        result = 31 * result + enableSustainGlow.hashCode()
        return result
    }
}

data class SecondaryTextConfig(
    override var textColor: IntArray = intArrayOf(Color.GRAY),
    override var textSize: Float = 14f.sp,
    override var typeface: Typeface = Typeface.DEFAULT
) : TextConfig {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SecondaryTextConfig

        if (textSize != other.textSize) return false
        if (!textColor.contentEquals(other.textColor)) return false
        if (typeface != other.typeface) return false

        return true
    }

    override fun hashCode(): Int {
        var result = textSize.hashCode()
        result = 31 * result + textColor.contentHashCode()
        result = 31 * result + typeface.hashCode()
        return result
    }
}
