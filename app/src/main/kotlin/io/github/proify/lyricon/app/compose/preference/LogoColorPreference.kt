/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.app.compose.preference

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mocharealm.gaze.capsule.ContinuousRoundedRectangle
import io.github.proify.android.extensions.fromJson
import io.github.proify.android.extensions.toJson
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.color.ColorBox
import io.github.proify.lyricon.app.compose.color.ColorPaletteDialog
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperArrow
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperCheckbox
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.LogoColor

@Composable
fun LogoColorPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    title: String,
    defaultColor: Color,
    leftAction: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val logoColor = rememberLogoColor(sharedPreferences, key)
    var currentColor by remember(logoColor) { mutableIntStateOf(logoColor.color) }

    val isDialogVisible = remember { mutableStateOf(false) }

    ColorPaletteDialog(
        title = title,
        show = isDialogVisible,
        initialColor = defaultColor,
        onDelete = {
            currentColor = 0
            sharedPreferences.editCommit { remove(key) }
        },
        onConfirm = { color ->
            currentColor = color.toArgb()
            logoColor.color = currentColor
            sharedPreferences.editCommit { putString(key, logoColor.toJson()) }
        },
        content = {
            Spacer(modifier = Modifier.height(16.dp))
            val shape = ContinuousRoundedRectangle(16.dp)

            Box(
                modifier = Modifier.clip(shape),
            ) {
                var isChecked by remember(logoColor) { mutableStateOf(logoColor.followTextColor) }
                SuperCheckbox(
                    insideMargin = PaddingValues(horizontal = 16.dp),
                    title = stringResource(R.string.option_logo_color_follow_text),
                    checked = isChecked,
                    onCheckedChange = {
                        isChecked = it
                        logoColor.followTextColor = it
                        sharedPreferences.editCommit { putString(key, logoColor.toJson()) }
                    }
                )
            }
        }
    )

    SuperArrow(
        title = title,
        summary = if (logoColor.followTextColor) stringResource(R.string.option_logo_color_follow_text) else null,
        startAction = leftAction,
        endActions = {
            val color = currentColor.toColorOrNull()

            if (color != null) {
                ColorBox(colors = listOf(color))
                Spacer(modifier = Modifier.width(10.dp))
            }
        },
        enabled = enabled,
        onClick = {
            if (enabled) {
                isDialogVisible.value = true
            }
        },
    )
}

@Composable
private fun rememberLogoColor(
    sharedPreferences: SharedPreferences,
    key: String
): LogoColor {
    val jsonString = rememberStringPreference(sharedPreferences, key, "{}").value
    return remember(jsonString) {
        jsonString?.fromJson<LogoColor>() ?: LogoColor()
    }
}

private fun Int.toColorOrNull(): Color? =
    if (this == 0) null else runCatching { Color(this) }.getOrNull()
//
//@Composable
//private fun ColorPreviewRow(textColor: TextColor) {
////    val normalColor = textColor.normal.toColorOrNull()
////    val backgroundColor = textColor.background.toColorOrNull()
////    val highlightColor = textColor.highlight.toColorOrNull()
////
////    normalColor?.let {
////        ColorBox(colors = listOf(it))
////        Spacer(modifier = Modifier.width(10.dp))
////    }
////
////    if (backgroundColor != null || highlightColor != null) {
////        ColorBox(colors = listOf(backgroundColor, highlightColor))
////        Spacer(modifier = Modifier.width(10.dp))
////    }
//}
