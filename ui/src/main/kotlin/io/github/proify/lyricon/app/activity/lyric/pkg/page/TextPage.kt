/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.page

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior
import io.github.proify.lyricon.app.compose.custom.miuix.preference.CheckboxPreference
import io.github.proify.lyricon.app.compose.preference.DoubleInputPreference
import io.github.proify.lyricon.app.compose.preference.IntInputPreference
import io.github.proify.lyricon.app.compose.preference.LongInputPreference
import io.github.proify.lyricon.app.compose.preference.PreferenceValueDisplay
import io.github.proify.lyricon.app.compose.preference.RectInputPreference
import io.github.proify.lyricon.app.compose.preference.StringInputPreference
import io.github.proify.lyricon.app.compose.preference.TextColorPreference
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.style.TextStyle.Companion.KEY_ENABLED_ENTER_ANIM
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun TextPage(scrollBehavior: ScrollBehavior, preferences: SharedPreferences) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        item(key = "base") {
            SmallTitle(
                text = stringResource(R.string.section_basic),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 0.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {
                DoubleInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_size",
                    title = stringResource(R.string.item_text_size),
                    dialogSummary = stringResource(R.string.dialog_summary_text_size),
                    range = 0.0..100.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_format_size)) },
                )
                RectInputPreference(
                    preferences,
                    "lyric_style_text_margins",
                    stringResource(R.string.item_text_margins),
                    defaultValue = TextStyle.Defaults.MARGINS,
                    dialogSummary = stringResource(R.string.dialog_summary_text_margins),
                    startAction = { IconActions(painterResource(R.drawable.ic_margin)) },
                )
                RectInputPreference(
                    preferences,
                    "lyric_style_text_paddings",
                    stringResource(R.string.item_text_paddings),
                    defaultValue = TextStyle.Defaults.PADDINGS,
                    dialogSummary = stringResource(R.string.dialog_summary_text_paddings),
                    startAction = { IconActions(painterResource(R.drawable.ic_padding)) },
                )

                DoubleInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_size_ratio_in_multi_line_mode",
                    title = stringResource(R.string.item_text_size_scale_multi_line),
                    dialogSummary = stringResource(R.string.dialog_summary_text_size_scale_multi_line),
                    defaultValue = TextStyle.Defaults.TEXT_SIZE_RATIO_IN_MULTI_LINE.toDouble(),
                    range = 0.1..1.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_format_size)) },
                )
                TransitionConfigPreference(preferences)

                DoubleInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_fading_edge_length",
                    title = stringResource(R.string.item_text_fading_edge_length),
                    dialogSummary = stringResource(R.string.dialog_summary_text_fading_edge_length),
                    range = 0.0..100.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                )

                var isGradientProgressStyleEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_gradient_progress_style",
                    defaultValue = TextStyle.Defaults.ENABLE_GRADIENT_PROGRESS_STYLE
                )
                SwitchPreference(
                    checked = isGradientProgressStyleEnabled,
                    title = stringResource(R.string.item_text_word_progress_fading_style),
                    startAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                    onCheckedChange = { isGradientProgressStyleEnabled = it }
                )
                PlaceholderFormatPreference(preferences)

                var isEnterAnimEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = KEY_ENABLED_ENTER_ANIM,
                    defaultValue = false
                )
                SwitchPreference(
                    checked = isEnterAnimEnabled,
                    title = stringResource(R.string.item_text_enable_enter_anim),
                    summary = stringResource(R.string.item_text_enable_enter_anim_summary),
                    startAction = { IconActions(painterResource(R.drawable.masked_transitions_24px)) },
                    onCheckedChange = {
                        isEnterAnimEnabled = it
                    }
                )
            }
        }
        item(key = "color") {
            SmallTitle(
                text = stringResource(R.string.item_text_color),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {

                val customColorEnabled = rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_enable_custom_color",
                    defaultValue = TextStyle.Defaults.ENABLE_CUSTOM_TEXT_COLOR
                )

                var isExtractCoverColorEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_extract_cover_color",
                    defaultValue = TextStyle.Defaults.ENABLE_EXTRACT_COVER_TEXT_COLOR
                )
                SwitchPreference(
                    checked = isExtractCoverColorEnabled,
                    title = stringResource(R.string.item_text_extract_cover_color),
                    startAction = { IconActions(painterResource(R.drawable.colorize_24px)) },
                    onCheckedChange = {
                        isExtractCoverColorEnabled = it
                        if (it) {
                            preferences.editCommit {
                                putBoolean("lyric_style_text_enable_custom_color", false)
                                putBoolean("lyric_style_text_enable_rainbow_color", false)
                            }
                        } else {
                            preferences.editCommit {
                                putBoolean("lyric_style_text_extract_cover_gradient", false)
                            }
                        }
                    }
                )
                var isExtractCoverGradientEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_extract_cover_gradient",
                    defaultValue = TextStyle.Defaults.ENABLE_EXTRACT_COVER_TEXT_GRADIENT
                )
                SwitchPreference(
                    checked = isExtractCoverGradientEnabled,
                    title = stringResource(R.string.item_text_extract_cover_gradient),
                    startAction = { IconActions(painterResource(R.drawable.format_paint_24px)) },
                    enabled = isExtractCoverColorEnabled,
                    onCheckedChange = {
                        isExtractCoverGradientEnabled = it

                        if (it) {
                            preferences.editCommit {
                                putBoolean("lyric_style_text_enable_custom_color", false)
                                putBoolean("lyric_style_text_extract_cover_color", true)
                            }
                        }
                    }
                )

                var isCustomColorEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_enable_custom_color",
                    defaultValue = TextStyle.Defaults.ENABLE_CUSTOM_TEXT_COLOR
                )
                SwitchPreference(
                    checked = isCustomColorEnabled,
                    title = stringResource(R.string.item_text_enable_custom_color),
                    startAction = { IconActions(painterResource(R.drawable.ic_palette)) },
                    onCheckedChange = {
                        isCustomColorEnabled = it
                        if (it) {
                            preferences.editCommit {
                                putBoolean("lyric_style_text_extract_cover_color", false)
                                putBoolean("lyric_style_text_extract_cover_gradient", false)
                            }
                        }
                    }
                )
                TextColorPreference(
                    preferences,
                    "lyric_style_text_rainbow_color_light_mode",
                    title = stringResource(R.string.item_text_color_light_mode),
                    leftAction = { IconActions(painterResource(R.drawable.ic_brightness7)) },
                    enabled = customColorEnabled.value,
                )
                TextColorPreference(
                    preferences,
                    "lyric_style_text_rainbow_color_dark_mode",
                    title = stringResource(R.string.item_text_color_dark_mode),
                    leftAction = { IconActions(painterResource(R.drawable.ic_darkmode)) },
                    enabled = customColorEnabled.value,
                )
            }
        }
        item(key = "font") {
            SmallTitle(
                text = stringResource(R.string.item_text_font),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {
                StringInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_typeface",
                    title = stringResource(R.string.item_text_typeface),
                    dialogSummary = stringResource(R.string.dialog_summary_text_typeface),
                    startAction = { IconActions(painterResource(R.drawable.file_24px)) },
                    maxLines = 1
                )

                IntInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_weight",
                    title = stringResource(R.string.item_text_font_weight),
                    dialogSummary = stringResource(R.string.dialog_summary_text_font_weight),
                    range = 0..1000,
                    startAction = { IconActions(painterResource(R.drawable.ic_fontdownload)) },
                )

                TypefaceCompose(preferences)
            }
        }

        item(key = "item_text_syllable") {
            SmallTitle(
                text = stringResource(R.string.item_text_syllable),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {
                var isRelativeProgressEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_relative_progress",
                    defaultValue = TextStyle.Defaults.RELATIVE_PROGRESS
                )
                SwitchPreference(
                    checked = isRelativeProgressEnabled,
                    onCheckedChange = { isRelativeProgressEnabled = it },
                    title = stringResource(R.string.item_text_relative_progress),
                    summary = stringResource(R.string.item_text_relative_progress_summary),
                    startAction = { IconActions(painterResource(R.drawable.ic_music_note)) },
                )

                var isRelativeProgressHighlightEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_relative_progress_highlight",
                    defaultValue = TextStyle.Defaults.RELATIVE_PROGRESS_HIGHLIGHT
                )
                SwitchPreference(
                    checked = isRelativeProgressHighlightEnabled,
                    onCheckedChange = { isRelativeProgressHighlightEnabled = it },
                    title = stringResource(R.string.item_text_relative_progress_highlight),
                    startAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                )

                var isWordMotionEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = TextStyle.KEY_WORD_MOTION_ENABLED,
                    defaultValue = TextStyle.Defaults.WORD_MOTION_ENABLED
                )
                SwitchPreference(
                    checked = isWordMotionEnabled,
                    onCheckedChange = { isWordMotionEnabled = it },
                    title = stringResource(R.string.item_text_word_motion),
                    summary = stringResource(R.string.item_text_word_motion_summary),
                    startAction = { IconActions(painterResource(R.drawable.percent_24px)) },
                )

                DoubleInputPreference(
                    preferences = preferences,
                    key = TextStyle.KEY_WORD_MOTION_AMPLITUDE,
                    title = stringResource(R.string.item_text_word_motion_amplitude),
                    dialogSummary = stringResource(R.string.dialog_summary_text_word_motion_amplitude),
                    defaultValue = TextStyle.Defaults.WORD_MOTION_AMPLITUDE.toDouble(),
                    range = 0.25..3.0,
                    enabled = isWordMotionEnabled,
                    startAction = { IconActions(painterResource(R.drawable.percent_24px)) },
                )
            }
        }

        item(key = "translation") {
            SmallTitle(
                text = stringResource(R.string.module_tag_translation),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )

            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {

                var isTranslationDisableEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = TextStyle.KEY_TEXT_TRANSLATION_DISABLE,
                    defaultValue = TextStyle.Defaults.TRANSLATION_DISABLE
                )
                SwitchPreference(
                    checked = isTranslationDisableEnabled,
                    title = stringResource(R.string.item_translation_hide),
                    startAction = { IconActions(painterResource(R.drawable.ic_visibility_off)) },
                    onCheckedChange = {
                        isTranslationDisableEnabled = it
                    }
                )

                var isTranslationOnlyEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = TextStyle.KEY_TEXT_TRANSLATION_ONLY,
                    defaultValue = TextStyle.Defaults.TRANSLATION_ONLY
                )
                SwitchPreference(
                    checked = isTranslationOnlyEnabled,
                    title = stringResource(R.string.item_translation_only),
                    startAction = { IconActions(painterResource(R.drawable.translate_24px)) },
                    onCheckedChange = {
                        isTranslationOnlyEnabled = it
                    }
                )
            }
        }

        item(key = "marquee") {
            SmallTitle(
                text = stringResource(R.string.item_text_marquee),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                    .fillMaxWidth(),
            ) {
                IntInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_speed",
                    title = stringResource(R.string.item_text_marquee_speed),
                    dialogSummary = stringResource(R.string.dialog_summary_text_marquee_speed),
                    defaultValue = TextStyle.Defaults.MARQUEE_SPEED.toInt(),
                    range = 0..500,
                    startAction = { IconActions(painterResource(R.drawable.ic_speed)) },
                )
                IntInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_space",
                    title = stringResource(R.string.item_text_marquee_repeat_spacing),
                    dialogSummary = stringResource(R.string.dialog_summary_text_marquee_space),
                    defaultValue = TextStyle.Defaults.MARQUEE_GHOST_SPACING.toInt(),
                    range = 0..1000,
                    startAction = { IconActions(painterResource(R.drawable.ic_space_bar)) },
                )
                LongInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_initial_delay",
                    title = stringResource(R.string.item_text_marquee_initial_delay),
                    dialogSummary = stringResource(R.string.dialog_summary_text_marquee_initial_delay),
                    defaultValue = TextStyle.Defaults.MARQUEE_INITIAL_DELAY.toLong(),
                    range = 0L..3_600_000L,
                    startAction = { IconActions(painterResource(R.drawable.ic_autopause)) },
                    display = PreferenceValueDisplay.Time(),
                )
                LongInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_loop_delay",
                    title = stringResource(R.string.item_text_marquee_delay),
                    dialogSummary = stringResource(R.string.dialog_summary_text_marquee_delay),
                    defaultValue = TextStyle.Defaults.MARQUEE_LOOP_DELAY.toLong(),
                    range = 0L..3_600_000L,
                    startAction = { IconActions(painterResource(R.drawable.ic_autopause)) },
                    display = PreferenceValueDisplay.Time(),
                )

                var isMarqueeRepeatUnlimited by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_marquee_repeat_unlimited",
                    defaultValue = TextStyle.Defaults.MARQUEE_REPEAT_UNLIMITED
                )
                SwitchPreference(
                    checked = isMarqueeRepeatUnlimited,
                    onCheckedChange = { isMarqueeRepeatUnlimited = it },
                    title = stringResource(R.string.item_text_marquee_infinite_scrolling),
                    startAction = { IconActions(painterResource(R.drawable.ic_all_inclusive)) },
                )
                IntInputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_repeat_count",
                    title = stringResource(R.string.item_text_marquee_repeat_count),
                    dialogSummary = stringResource(R.string.dialog_summary_text_marquee_repeat_count),
                    range = 0..3_600_000,
                    startAction = { IconActions(painterResource(R.drawable.ic_pin)) },
                )

                var isMarqueeStopAtEnd by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_marquee_stop_at_end",
                    defaultValue = TextStyle.Defaults.MARQUEE_STOP_AT_END
                )
                SwitchPreference(
                    checked = isMarqueeStopAtEnd,
                    onCheckedChange = { isMarqueeStopAtEnd = it },
                    title = stringResource(R.string.item_text_marquee_stop_at_end),
                    startAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
                )
            }
        }
    }
}


@Composable
private fun <T> DropdownPreference(
    preferences: SharedPreferences,
    preferenceKey: String,
    defaultValue: T,
    options: List<String>,
    values: List<T>,
    title: String,
    iconRes: Int = R.drawable.ic_settings
) {
    val currentValue = preferences.getString(preferenceKey, defaultValue.toString())
    var selectedIndex by remember(currentValue) {
        mutableIntStateOf(values.indexOfFirst { it.toString() == currentValue }.takeIf { it >= 0 }
            ?: 0)
    }

    OverlayDropdownPreference(
        startAction = { IconActions(painterResource(iconRes)) },
        title = title,
        items = options,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = {
            selectedIndex = it
            preferences.editCommit {
                putString(preferenceKey, values[it].toString())
            }
        }
    )
}

@Composable
private fun PlaceholderFormatPreference(preferences: SharedPreferences) {
    DropdownPreference(
        preferences = preferences,
        preferenceKey = "lyric_style_text_placeholder_format",
        defaultValue = TextStyle.PlaceholderFormat.NAME_ARTIST,
        options = listOf(
            stringResource(R.string.option_text_placeholder_format_none),
            stringResource(R.string.option_text_placeholder_format_name_artist),
            stringResource(R.string.option_text_placeholder_format_name),
        ),
        values = listOf(
            TextStyle.PlaceholderFormat.NONE,
            TextStyle.PlaceholderFormat.NAME_ARTIST,
            TextStyle.PlaceholderFormat.NAME,
        ),
        title = stringResource(R.string.item_text_placeholder_format),
        iconRes = R.drawable.title_24px
    )
}

@Composable
private fun TransitionConfigPreference(preferences: SharedPreferences) {
    DropdownPreference(
        preferences = preferences,
        preferenceKey = "lyric_style_text_transition_config",
        defaultValue = TextStyle.TRANSITION_CONFIG_SMOOTH,
        options = listOf(
            stringResource(R.string.option_text_transition_config_none),
            stringResource(R.string.option_text_transition_config_fast),
            stringResource(R.string.option_text_transition_config_smooth),
            stringResource(R.string.option_text_transition_config_slow)
        ),
        values = listOf(
            TextStyle.TRANSITION_CONFIG_NONE,
            TextStyle.TRANSITION_CONFIG_FAST,
            TextStyle.TRANSITION_CONFIG_SMOOTH,
            TextStyle.TRANSITION_CONFIG_SLOW
        ),
        title = stringResource(R.string.item_text_transition_config),
        iconRes = R.drawable.ic_speed
    )


}

@Composable
private fun TypefaceCompose(preferences: SharedPreferences) {
    var isTypefaceBoldEnabled by rememberBooleanPreference(
        sharedPreferences = preferences,
        key = "lyric_style_text_typeface_bold",
        defaultValue = false
    )
    var isTypefaceItalicEnabled by rememberBooleanPreference(
        sharedPreferences = preferences,
        key = "lyric_style_text_typeface_italic",
        defaultValue = false
    )
    CheckboxPreference(
        checked = isTypefaceBoldEnabled,
        title = stringResource(R.string.item_text_typeface_bold),
        startActions = { IconActions(painterResource(R.drawable.ic_formatbold)) },
        checkboxLocation = CheckboxLocation.End,
        onCheckedChange = { isTypefaceBoldEnabled = it }
    )
    CheckboxPreference(
        checked = isTypefaceItalicEnabled,
        title = stringResource(R.string.item_text_typeface_italic),
        startActions = { IconActions(painterResource(R.drawable.ic_format_italic)) },
        checkboxLocation = CheckboxLocation.End,
        onCheckedChange = { isTypefaceItalicEnabled = it }
    )
}
