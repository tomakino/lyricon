/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.preference

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.proify.android.extensions.fromJson
import io.github.proify.android.extensions.toJson
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.color.ColorBox
import io.github.proify.lyricon.app.compose.color.MultiColorEditPaletteDialog
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperArrow
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.RainbowTextColor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private val ITEM_SPACING = 16.dp

@Composable
fun TextColorPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    title: String,
    leftAction: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val textColor = rememberTextColor(sharedPreferences, key)
    val isBottomSheetVisible = remember { mutableStateOf(false) }

    TextColorBottomSheet(
        title = title,
        textColor = textColor,
        isVisible = isBottomSheetVisible,
        onReset = { resetTextColor(sharedPreferences, key) },
        onColorChange = { saveTextColor(sharedPreferences, key, textColor) }
    )

    TextColorArrow(
        title = title,
        leftAction = leftAction,
        enabled = enabled,
        onClick = {
            if (enabled) {
                isBottomSheetVisible.value = true
            }
        }
    )
}

@Composable
private fun rememberTextColor(
    sharedPreferences: SharedPreferences,
    key: String
): RainbowTextColor {
    val jsonString = rememberStringPreference(sharedPreferences, key, "{}").value
    return remember(jsonString) {
        jsonString?.fromJson<RainbowTextColor>() ?: RainbowTextColor()
    }
}

private fun resetTextColor(sharedPreferences: SharedPreferences, key: String) {
    sharedPreferences.editCommit { remove(key) }
}

private fun saveTextColor(
    sharedPreferences: SharedPreferences,
    key: String,
    textColor: RainbowTextColor
) {
    sharedPreferences.editCommit { putString(key, textColor.toJson()) }
}

@Composable
private fun TextColorBottomSheet(
    title: String,
    textColor: RainbowTextColor,
    isVisible: MutableState<Boolean>,
    onReset: () -> Unit,
    onColorChange: () -> Unit
) {
    SuperBottomSheet(
        show = isVisible,
        title = title,
        endAction = {
            if (textColor.hasCustomColors()) {
                Row {
                    IconButton(onClick = {
                        isVisible.value = false
                        onReset()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "Reset color"
                        )
                    }
                    Spacer(modifier = Modifier.width(ITEM_SPACING))
                }
            }
        },
        backgroundColor = MiuixTheme.colorScheme.surface,
        insideMargin = DpSize(0.dp, 0.dp),
        onDismissRequest = { isVisible.value = false }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .overScrollVertical()
        ) {
            item("color_settings") {
                ColorSettingsContent(
                    textColor = textColor,
                    onColorChange = onColorChange
                )
            }
        }
    }
}

@Composable
private fun ColorSettingsContent(
    textColor: RainbowTextColor,
    onColorChange: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = ITEM_SPACING)
            .fillMaxWidth()
    ) {
        ColorPickerItem(
            title = stringResource(R.string.item_text_color_normal),
            initialColor = textColor.normal.map { Color(it) }
        ) {
            textColor.normal = it.map { it.toArgb() }.toIntArray()
            onColorChange()
        }
    }

    Spacer(modifier = Modifier.height(ITEM_SPACING))

    Card(
        modifier = Modifier
            .padding(horizontal = ITEM_SPACING)
            .fillMaxWidth()
    ) {
        ColorPickerItem(
            title = stringResource(R.string.item_text_color_background),
            initialColor = textColor.background.map { Color(it) }
        ) {
            textColor.background = it.map { it.toArgb() }.toIntArray()
            onColorChange()
        }
        ColorPickerItem(
            title = stringResource(R.string.item_text_color_highlight),
            initialColor = textColor.highlight.map { Color(it) }
        ) {
            textColor.highlight = it.map { it.toArgb() }.toIntArray()
            onColorChange()
        }
    }

    Spacer(modifier = Modifier.height(ITEM_SPACING))
}

@Composable
private fun TextColorArrow(
    title: String,
    leftAction: @Composable (() -> Unit)?,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    SuperArrow(
        title = title,
        startAction = leftAction,
        endActions = {},
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun ColorPickerItem(
    title: String,
    initialColor: List<Color>,
    leftAction: @Composable (() -> Unit)? = null,
    onColorSelected: (List<Color>) -> Unit
) {
    val isDialogVisible = remember { mutableStateOf(false) }
    val currentColor = remember(initialColor) { mutableStateOf(initialColor) }

    MultiColorEditPaletteDialog(
        title = title,
        show = isDialogVisible,
        initialColor = currentColor.value,
        onDelete = {
            currentColor.value = emptyList()
            onColorSelected(listOf())
        },
        onConfirm = { color ->
            currentColor.value = color
            onColorSelected(color)
        }
    )

    SuperArrow(
        title = title,
        startAction = leftAction,
        endActions = {
            currentColor.value.let {
                ColorBox(colors = it)
                Spacer(modifier = Modifier.width(10.dp))
            }
        },
        onClick = { isDialogVisible.value = true }
    )
}

private fun RainbowTextColor.hasCustomColors(): Boolean =
    normal.isNotEmpty() || background.isNotEmpty() || highlight.isNotEmpty()