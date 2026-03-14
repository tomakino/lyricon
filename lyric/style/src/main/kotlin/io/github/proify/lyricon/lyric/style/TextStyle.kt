/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.content.SharedPreferences
import android.os.Parcelable
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.toJson
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class TextStyle(
    var textSize: Float = Defaults.TEXT_SIZE,
    var margins: RectF = Defaults.MARGINS,
    var paddings: RectF = Defaults.PADDINGS,
    var repeatOutput: Boolean = Defaults.REPEAT_OUTPUT,

    var fadingEdgeLength: Int = Defaults.FADING_EDGE_LENGTH,

    var enableCustomTextColor: Boolean = Defaults.ENABLE_CUSTOM_TEXT_COLOR,
    var enableExtractCoverTextColor: Boolean = Defaults.ENABLE_EXTRACT_COVER_TEXT_COLOR,
    var enableExtractCoverTextGradient: Boolean = Defaults.ENABLE_EXTRACT_COVER_TEXT_GRADIENT,
    var lightModeRainbowColor: RainbowTextColor? = Defaults.LIGHT_MODE_RAINBOW_COLOR,
    var darkModeRainbowColor: RainbowTextColor? = Defaults.DARK_MODE_RAINBOW_COLOR,

    var typeFace: String? = Defaults.TYPE_FACE,
    var typeFaceBold: Boolean = Defaults.TYPE_FACE_BOLD,
    var typeFaceItalic: Boolean = Defaults.TYPE_FACE_ITALIC,
    var fontWeight: Int = Defaults.FONT_WEIGHT,

    var marqueeSpeed: Float = Defaults.MARQUEE_SPEED,
    var marqueeGhostSpacing: Float = Defaults.MARQUEE_GHOST_SPACING,
    var marqueeLoopDelay: Int = Defaults.MARQUEE_LOOP_DELAY,
    var marqueeRepeatCount: Int = Defaults.MARQUEE_REPEAT_COUNT,
    var marqueeStopAtEnd: Boolean = Defaults.MARQUEE_STOP_AT_END,
    var marqueeInitialDelay: Int = Defaults.MARQUEE_INITIAL_DELAY,
    var marqueeRepeatUnlimited: Boolean = Defaults.MARQUEE_REPEAT_UNLIMITED,
    var gradientProgressStyle: Boolean = Defaults.ENABLE_GRADIENT_PROGRESS_STYLE,

    var relativeProgress: Boolean = Defaults.RELATIVE_PROGRESS,
    var relativeProgressHighlight: Boolean = Defaults.RELATIVE_PROGRESS_HIGHLIGHT,
    var scaleInMultiLine: Float = Defaults.TEXT_SIZE_RATIO_IN_MULTI_LINE,

    var transitionConfig: String? = Defaults.TRANSITION_CONFIG,
    var placeholderFormat: String? = Defaults.PLACEHOLDER_FORMAT
) : AbstractStyle(), Parcelable {

    companion object {
        const val TRANSITION_CONFIG_FAST: String = "fast"
        const val TRANSITION_CONFIG_SMOOTH: String = "smooth"
        const val TRANSITION_CONFIG_SLOW: String = "slow"
        const val TRANSITION_CONFIG_NONE = "none"
    }

    object PlaceholderFormat {
        const val NAME: String = "NameOnly"
        const val NAME_ARTIST: String = "NameAndArtist"
        const val NONE: String = "None"
    }

    object Defaults {
        const val PLACEHOLDER_FORMAT: String = PlaceholderFormat.NAME_ARTIST
        const val TRANSITION_CONFIG: String = TRANSITION_CONFIG_SMOOTH

        const val TEXT_SIZE_RATIO_IN_MULTI_LINE: Float = 0.86f
        const val RELATIVE_PROGRESS: Boolean = true
        const val RELATIVE_PROGRESS_HIGHLIGHT: Boolean = false

        const val TEXT_SIZE: Float = 0f
        val MARGINS: RectF = RectF()
        val PADDINGS: RectF = RectF()
        const val REPEAT_OUTPUT: Boolean = false

        const val FADING_EDGE_LENGTH: Int = 14

        const val ENABLE_CUSTOM_TEXT_COLOR: Boolean = false
        const val ENABLE_EXTRACT_COVER_TEXT_COLOR: Boolean = false
        const val ENABLE_EXTRACT_COVER_TEXT_GRADIENT: Boolean = false

        val LIGHT_MODE_RAINBOW_COLOR: RainbowTextColor? = null
        val DARK_MODE_RAINBOW_COLOR: RainbowTextColor? = null

        val TYPE_FACE: String? = null
        const val TYPE_FACE_BOLD: Boolean = false
        const val TYPE_FACE_ITALIC: Boolean = false
        const val FONT_WEIGHT: Int = -1

        const val MARQUEE_SPEED: Float = 35f
        const val MARQUEE_GHOST_SPACING: Float = 50f
        const val MARQUEE_LOOP_DELAY: Int = 0

        const val MARQUEE_REPEAT_COUNT: Int = -1
        const val MARQUEE_STOP_AT_END: Boolean = false
        const val MARQUEE_INITIAL_DELAY: Int = 300
        const val MARQUEE_REPEAT_UNLIMITED: Boolean = true
        const val ENABLE_GRADIENT_PROGRESS_STYLE: Boolean = true
    }

    fun color(lightMode: Boolean): RainbowTextColor? =
        if (lightMode) lightModeRainbowColor else darkModeRainbowColor

    override fun onLoad(preferences: SharedPreferences) {
        textSize = preferences.getFloat("lyric_style_text_size", Defaults.TEXT_SIZE)
        repeatOutput =
            preferences.getBoolean("lyric_style_text_repeat_output", Defaults.REPEAT_OUTPUT)
        margins = json.safeDecode<RectF>(preferences.getString("lyric_style_text_margins", null))
        paddings = json.safeDecode<RectF>(preferences.getString("lyric_style_text_paddings", null))

        enableCustomTextColor = preferences.getBoolean(
            "lyric_style_text_enable_custom_color",
            Defaults.ENABLE_CUSTOM_TEXT_COLOR
        )
        enableExtractCoverTextColor = preferences.getBoolean(
            "lyric_style_text_extract_cover_color",
            Defaults.ENABLE_EXTRACT_COVER_TEXT_COLOR
        )
        enableExtractCoverTextGradient = preferences.getBoolean(
            "lyric_style_text_extract_cover_gradient",
            Defaults.ENABLE_EXTRACT_COVER_TEXT_GRADIENT
        )
        if (enableCustomTextColor) {
            enableExtractCoverTextColor = false
            enableExtractCoverTextGradient = false
        }
        if (!enableExtractCoverTextColor) {
            enableExtractCoverTextGradient = false
        }
        lightModeRainbowColor = json.safeDecode<RainbowTextColor>(
            preferences.getString("lyric_style_text_rainbow_color_light_mode", null),
            Defaults.LIGHT_MODE_RAINBOW_COLOR
        )
        darkModeRainbowColor = json.safeDecode<RainbowTextColor>(
            preferences.getString("lyric_style_text_rainbow_color_dark_mode", null),
            Defaults.DARK_MODE_RAINBOW_COLOR
        )

        fadingEdgeLength =
            preferences.getInt("lyric_style_text_fading_edge_length", Defaults.FADING_EDGE_LENGTH)

        typeFace = preferences.getString("lyric_style_text_typeface", Defaults.TYPE_FACE)
        typeFaceBold =
            preferences.getBoolean("lyric_style_text_typeface_bold", Defaults.TYPE_FACE_BOLD)
        typeFaceItalic =
            preferences.getBoolean("lyric_style_text_typeface_italic", Defaults.TYPE_FACE_ITALIC)
        fontWeight = preferences.getInt("lyric_style_text_weight", Defaults.FONT_WEIGHT)

        marqueeSpeed =
            preferences.getFloat("lyric_style_text_marquee_speed", Defaults.MARQUEE_SPEED)
        marqueeGhostSpacing =
            preferences.getFloat("lyric_style_text_marquee_space", Defaults.MARQUEE_GHOST_SPACING)
        marqueeLoopDelay =
            preferences.getInt("lyric_style_text_marquee_loop_delay", Defaults.MARQUEE_LOOP_DELAY)
        marqueeInitialDelay = preferences.getInt(
            "lyric_style_text_marquee_initial_delay",
            Defaults.MARQUEE_INITIAL_DELAY
        )
        marqueeRepeatCount = preferences.getInt(
            "lyric_style_text_marquee_repeat_count",
            Defaults.MARQUEE_REPEAT_COUNT
        )
        marqueeStopAtEnd = preferences.getBoolean(
            "lyric_style_text_marquee_stop_at_end",
            Defaults.MARQUEE_STOP_AT_END
        )
        marqueeRepeatUnlimited = preferences.getBoolean(
            "lyric_style_text_marquee_repeat_unlimited",
            Defaults.MARQUEE_REPEAT_UNLIMITED
        )
        gradientProgressStyle = preferences.getBoolean(
            "lyric_style_text_gradient_progress_style",
            Defaults.ENABLE_GRADIENT_PROGRESS_STYLE
        )

        relativeProgress = preferences.getBoolean(
            "lyric_style_text_relative_progress",
            Defaults.RELATIVE_PROGRESS
        )
        relativeProgressHighlight = preferences.getBoolean(
            "lyric_style_text_relative_progress_highlight",
            Defaults.RELATIVE_PROGRESS_HIGHLIGHT
        )
        scaleInMultiLine = preferences.getFloat(
            "lyric_style_text_size_ratio_in_multi_line_mode",
            Defaults.TEXT_SIZE_RATIO_IN_MULTI_LINE
        )

        transitionConfig = preferences.getString(
            "lyric_style_text_transition_config",
            Defaults.TRANSITION_CONFIG
        )
        placeholderFormat = preferences.getString(
            "lyric_style_text_placeholder_format",
            Defaults.PLACEHOLDER_FORMAT
        )
    }

    override fun onWrite(editor: SharedPreferences.Editor) {
        editor.putFloat("lyric_style_text_size", textSize)
        editor.putBoolean("lyric_style_text_repeat_output", repeatOutput)

        editor.putString("lyric_style_text_margins", margins.toJson())
        editor.putString("lyric_style_text_paddings", paddings.toJson())

        editor.putBoolean("lyric_style_text_enable_custom_color", enableCustomTextColor)
        editor.putBoolean("lyric_style_text_extract_cover_color", enableExtractCoverTextColor)
        editor.putBoolean(
            "lyric_style_text_extract_cover_gradient",
            enableExtractCoverTextGradient
        )
        editor.putString(
            "lyric_style_text_rainbow_color_light_mode",
            lightModeRainbowColor.toJson()
        )
        editor.putString("lyric_style_text_rainbow_color_dark_mode", darkModeRainbowColor.toJson())

        editor.putInt("lyric_style_text_fading_edge_length", fadingEdgeLength)

        editor.putString("lyric_style_text_typeface", typeFace)
        editor.putBoolean("lyric_style_text_typeface_bold", typeFaceBold)
        editor.putBoolean("lyric_style_text_typeface_italic", typeFaceItalic)
        editor.putInt("lyric_style_text_weight", fontWeight)

        editor.putFloat("lyric_style_text_marquee_speed", marqueeSpeed)
        editor.putFloat("lyric_style_text_marquee_space", marqueeGhostSpacing)
        editor.putInt("lyric_style_text_marquee_loop_delay", marqueeLoopDelay)
        editor.putInt("lyric_style_text_marquee_initial_delay", marqueeInitialDelay)
        editor.putInt("lyric_style_text_marquee_repeat_count", marqueeRepeatCount)
        editor.putBoolean("lyric_style_text_marquee_stop_at_end", marqueeStopAtEnd)
        editor.putBoolean("lyric_style_text_marquee_repeat_unlimited", marqueeRepeatUnlimited)

        editor.putBoolean("lyric_style_text_gradient_progress_style", gradientProgressStyle)

        editor.putBoolean("lyric_style_text_relative_progress", relativeProgress)
        editor.putBoolean(
            "lyric_style_text_relative_progress_highlight",
            relativeProgressHighlight
        )
        editor.putFloat(
            "lyric_style_text_size_ratio_in_multi_line_mode",
            scaleInMultiLine
        )

        editor.putString(
            "lyric_style_text_transition_config",
            transitionConfig
        )
        editor.putString(
            "lyric_style_text_placeholder_format",
            placeholderFormat
        )
    }
}
