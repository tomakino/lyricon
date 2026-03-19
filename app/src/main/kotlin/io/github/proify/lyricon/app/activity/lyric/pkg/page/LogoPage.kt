/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("KotlinConstantConditions", "RedundantValueArgument")

package io.github.proify.lyricon.app.activity.lyric.pkg.page

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperCheckbox
import io.github.proify.lyricon.app.compose.preference.InputPreference
import io.github.proify.lyricon.app.compose.preference.InputType
import io.github.proify.lyricon.app.compose.preference.LogoColorPreference
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.compose.preference.RectInputPreference
import io.github.proify.lyricon.app.compose.preference.SwitchPreference
import io.github.proify.lyricon.app.compose.preference.rememberIntPreference
import io.github.proify.lyricon.app.util.Utils
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LogoStyle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun LogoPage(
    scrollBehavior: ScrollBehavior,
    sharedPreferences: SharedPreferences
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        item(key = "enable") {
            SmallTitle(
                text = stringResource(R.string.item_logo_section_basic),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                SwitchPreference(
                    sharedPreferences,
                    "lyric_style_logo_enable",
                    defaultValue = LogoStyle.Defaults.ENABLE,
                    startAction = {
                        IconActions(painterResource(R.drawable.ic_music_note))
                    },
                    title = stringResource(R.string.item_logo_enable)
                )
                InputPreference(
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_logo_width",
                    title = stringResource(R.string.item_logo_size),
                    syncKeys = arrayOf("lyric_style_logo_height"),
                    inputType = InputType.DOUBLE,
                    maxValue = 100.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_format_size)) }
                )
                RectInputPreference(
                    sharedPreferences,
                    "lyric_style_logo_margins",
                    stringResource(R.string.item_logo_margins),
                    LogoStyle.Defaults.MARGINS,
                    leftAction = {
                        IconActions(painterResource(R.drawable.ic_margin))
                    },
                )
                LogoGravity(sharedPreferences)
            }
        }

        if (Utils.isOPlus) {
            item(key = "coloros") {
                SmallTitle(
                    text = stringResource(R.string.item_logo_section_coloros),
                    insideMargin = PaddingValues(
                        start = 26.dp,
                        top = 16.dp,
                        end = 26.dp,
                        bottom = 10.dp
                    )
                )
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    SwitchPreference(
                        sharedPreferences,
                        "lyric_style_logo_hide_in_coloros_capsule_mode",
                        defaultValue = LogoStyle.Defaults.HIDE_IN_COLOROS_CAPSULE_MODE,
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_visibility_off))
                        },
                        title = stringResource(R.string.item_logo_hide_in_coloros_capsule_mode),
                    )
                }
            }
        }

        item(key = "logo_options") {
            SmallTitle(
                text = stringResource(R.string.item_logo_section_style),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )

            Card(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth()
            ) {

                val logoStyle = rememberIntPreference(
                    sharedPreferences,
                    "lyric_style_logo_style",
                    LogoStyle.Defaults.STYLE
                )

                val styleNameRes = listOf(
                    R.string.item_logo_style_default,
                    R.string.item_logo_style_app_logo,
                    R.string.item_logo_style_cover_square,
                    R.string.item_logo_style_cover_circle
                )

                val styleValues = listOf(
                    LogoStyle.STYLE_PROVIDER_LOGO,
                    LogoStyle.STYLE_APP_LOGO,
                    LogoStyle.STYLE_COVER_SQUIRCLE,
                    LogoStyle.STYLE_COVER_CIRCLE
                )

                val checkedIndex = styleValues.indexOf(logoStyle.value)

                styleNameRes.forEachIndexed { index, resId ->
                    SuperCheckbox(
                        title = stringResource(resId),
                        checked = checkedIndex == index,
                        onCheckedChange = {
                            sharedPreferences.editCommit {
                                putInt(
                                    "lyric_style_logo_style",
                                    styleValues[index]
                                )
                            }
                        }
                    )
                }
            }
        }

        item(key = "color") {
            SmallTitle(
                text = stringResource(R.string.item_logo_section_color),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )

            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                val customColorEnabled = rememberBooleanPreference(
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_logo_enable_custom_color",
                    defaultValue = LogoStyle.Defaults.ENABLE_CUSTOM_COLOR
                )
                SwitchPreference(
                    sharedPreferences,
                    "lyric_style_logo_enable_custom_color",
                    title = stringResource(R.string.item_logo_custom_color),
                    startAction = {
                        IconActions(painterResource(R.drawable.ic_palette))
                    }
                )

                LogoColorPreference(
                    sharedPreferences,
                    "lyric_style_logo_color_light_mode",
                    defaultColor = Color.Black,
                    title = stringResource(R.string.item_logo_color_light),
                    enabled = customColorEnabled.value,
                    leftAction = {
                        IconActions(painterResource(R.drawable.ic_brightness7))
                    },
                )

                LogoColorPreference(
                    sharedPreferences,
                    "lyric_style_logo_color_dark_mode",
                    defaultColor = Color.White,
                    title = stringResource(R.string.item_logo_color_dark),
                    enabled = customColorEnabled.value,
                    leftAction = {
                        IconActions(painterResource(R.drawable.ic_darkmode))
                    },
                )
            }
        }
        item(key = "spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LogoGravity(sharedPreferences: SharedPreferences) {
    val insertionOrder = sharedPreferences.getInt(
        "lyric_style_logo_gravity",
        LogoStyle.Defaults.GRAVITY
    )

    val selectedIndex = remember { mutableIntStateOf(0) }

    val optionKeys = listOf(
        BasicStyle.INSERTION_ORDER_BEFORE,
        BasicStyle.INSERTION_ORDER_AFTER
    )

    val options = listOf(
        SpinnerEntry(title = stringResource(R.string.item_logo_position_before)),
        SpinnerEntry(title = stringResource(R.string.item_logo_position_after)),
    )

    optionKeys.forEachIndexed { index, key ->
        if (insertionOrder == key) {
            selectedIndex.intValue = index
        }
    }

    SuperSpinner(
        startAction = {
            IconActions(painterResource(R.drawable.ic_stack))
        },
        title = stringResource(R.string.item_logo_position),
        items = options,
        selectedIndex = selectedIndex.intValue,
        onSelectedIndexChange = {
            selectedIndex.intValue = it
            sharedPreferences.editCommit {
                putInt(
                    "lyric_style_logo_gravity",
                    optionKeys[it]
                )
            }
        }
    )
}
