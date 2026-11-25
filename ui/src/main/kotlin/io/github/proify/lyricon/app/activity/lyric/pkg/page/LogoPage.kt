/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.page

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mocharealm.gaze.capsule.ContinuousRoundedRectangle
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior
import io.github.proify.lyricon.app.compose.custom.miuix.extra.OverlayDialog
import io.github.proify.lyricon.app.compose.preference.DoubleInputPreference
import io.github.proify.lyricon.app.compose.preference.LogoColorPreference
import io.github.proify.lyricon.app.compose.preference.RectInputPreference
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.compose.preference.rememberIntPreference
import io.github.proify.lyricon.app.compose.preference.rememberStringPreference
import io.github.proify.lyricon.app.util.Utils
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LogoStyle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun LogoPage(
    scrollBehavior: ScrollBehavior,
    preferences: SharedPreferences
) {
    val showCustomLogoDialog = remember { mutableStateOf(false) }
    var customLogoStr by rememberStringPreference(
        preferences,
        "lyric_style_logo_custom",
        null
    )

    if (showCustomLogoDialog.value) {
        CustomLogoInputDialog(
            preferences = preferences,
            show = showCustomLogoDialog,
            initialText = customLogoStr ?: "",
            onConfirm = { text ->
                customLogoStr = text.ifBlank { null }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        item(key = "enable") {
            SmallTitle(
                text = stringResource(R.string.section_basic),
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
                var enable by rememberBooleanPreference(
                    preferences,
                    "lyric_style_logo_enable",
                    LogoStyle.Defaults.ENABLE
                )
                SwitchPreference(
                    checked = enable,
                    startAction = {
                        IconActions(painterResource(R.drawable.ic_music_note))
                    },
                    title = stringResource(R.string.item_logo_enable),
                    onCheckedChange = {
                        enable = it
                    }
                )
                DoubleInputPreference(
                    preferences = preferences,
                    key = "lyric_style_logo_width",
                    title = stringResource(R.string.item_logo_size),
                    dialogSummary = stringResource(R.string.dialog_summary_logo_size),
                    syncKeys = listOf("lyric_style_logo_height"),
                    range = 0.0..100.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_format_size)) }
                )
                RectInputPreference(
                    preferences,
                    "lyric_style_logo_margins",
                    stringResource(R.string.item_logo_margins),
                    LogoStyle.Defaults.MARGINS,
                    dialogSummary = stringResource(R.string.dialog_summary_logo_margins),
                    startAction = {
                        IconActions(painterResource(R.drawable.ic_margin))
                    },
                )
                LogoGravity(preferences)
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
                    var hideInColorOsCapsuleMode by rememberBooleanPreference(
                        preferences,
                        "lyric_style_logo_hide_in_coloros_capsule_mode",
                        LogoStyle.Defaults.HIDE_IN_COLOROS_CAPSULE_MODE
                    )
                    SwitchPreference(
                        checked = hideInColorOsCapsuleMode,
                        onCheckedChange = {
                            hideInColorOsCapsuleMode = it
                        },
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

                var logoStyle by rememberIntPreference(
                    preferences,
                    "lyric_style_logo_style",
                    LogoStyle.Defaults.STYLE
                )

                val styleOptions = remember {
                    listOf(
                        R.string.item_logo_style_default to LogoStyle.STYLE_PROVIDER_LOGO,
                        R.string.item_logo_style_app_logo to LogoStyle.STYLE_APP_LOGO,
                        R.string.item_logo_style_cover_square to LogoStyle.STYLE_COVER_SQUIRCLE,
                        R.string.item_logo_style_cover_circle to LogoStyle.STYLE_COVER_CIRCLE,
                    )
                }

                val checkedIndex = remember(logoStyle) {
                    styleOptions.indexOfFirst { it.second == logoStyle }
                }

                styleOptions.forEachIndexed { index, (resId, value) ->
                    CheckboxPreference(
                        title = stringResource(resId),
                        checked = checkedIndex == index,
                        onCheckedChange = {
                            logoStyle = value
                        }
                    )
                }

                CheckboxPreference(
                    title = stringResource(R.string.item_logo_style_custom),
                    checked = logoStyle == LogoStyle.STYLE_LOGO_CUSTOM,
                    onCheckedChange = {
                        logoStyle = LogoStyle.STYLE_LOGO_CUSTOM
                    },
                    endActions = {
                        IconButton(
                            onClick = {
                                showCustomLogoDialog.value = true
                            },
                            minWidth = 28.dp,
                            minHeight = 28.dp
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.keyboard_24px),
                                contentDescription = null
                            )
                        }
                    }
                )
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

                var isCustomColorEnabled by rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_logo_enable_custom_color",
                    defaultValue = LogoStyle.Defaults.ENABLE_CUSTOM_COLOR
                )
                SwitchPreference(
                    checked = isCustomColorEnabled,
                    onCheckedChange = {
                        isCustomColorEnabled = it
                    },
                    title = stringResource(R.string.item_logo_custom_color),
                    startAction = {
                        IconActions(painterResource(R.drawable.ic_palette))
                    }
                )

                LogoColorPreference(
                    preferences,
                    "lyric_style_logo_color_light_mode",
                    defaultColor = Color.Black,
                    title = stringResource(R.string.item_logo_color_light),
                    enabled = isCustomColorEnabled,
                    leftAction = {
                        IconActions(painterResource(R.drawable.ic_brightness7))
                    }
                )

                LogoColorPreference(
                    preferences,
                    "lyric_style_logo_color_dark_mode",
                    defaultColor = Color.White,
                    title = stringResource(R.string.item_logo_color_dark),
                    enabled = isCustomColorEnabled,
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
private fun LogoGravity(preferences: SharedPreferences) {
    var order by rememberIntPreference(
        preferences,
        "lyric_style_logo_gravity",
        LogoStyle.Defaults.GRAVITY
    )

    val optionKeys = remember {
        listOf(
            BasicStyle.INSERTION_ORDER_BEFORE,
            BasicStyle.INSERTION_ORDER_AFTER
        )
    }

    val optionResIds = remember {
        listOf(
            R.string.item_logo_position_before,
            R.string.item_logo_position_after
        )
    }

    val selectedIndex = remember(order) {
        val index = optionKeys.indexOf(order)
        if (index != -1) index else 0
    }

    OverlaySpinnerPreference(
        startAction = { IconActions(painterResource(R.drawable.ic_stack)) },
        title = stringResource(R.string.item_logo_position),
        items = optionResIds.map { DropdownItem(title = stringResource(it)) },
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            order = optionKeys[index]
        }
    )
}

@Composable
private fun CustomLogoInputDialog(
    preferences: SharedPreferences,
    show: MutableState<Boolean>,
    initialText: String,
    onConfirm: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val textState = remember { mutableStateOf(initialText) }
    val scrollState = rememberScrollState()

    var colorful by rememberBooleanPreference(
        preferences,
        "lyric_style_logo_custom_colorful",
        false
    )

    fun dismiss() {
        keyboardController?.hide()
        if (show.value) show.value = false
    }

    OverlayDialog(
        title = stringResource(R.string.custom_logo_dialog_title),
        summary = stringResource(R.string.custom_logo_dialog_summary),
        show = show.value,
        onDismissRequest = { dismiss() }
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth()
        ) {

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
            ) {
                TextField(
                    value = textState.value,
                    onValueChange = { textState.value = it },
                    modifier = Modifier
                        .fillMaxWidth(),
                    maxLines = 12,
                    singleLine = false,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CheckboxPreference(
                insideMargin = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.clip(ContinuousRoundedRectangle(16.dp)),
                checked = colorful,
                onCheckedChange = { colorful = it },
                title = stringResource(R.string.item_logo_custom_colorful),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { dismiss() },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        onConfirm(textState.value)
                        dismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}