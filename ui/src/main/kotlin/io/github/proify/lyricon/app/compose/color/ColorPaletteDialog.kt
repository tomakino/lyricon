/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("AssignedValueIsNeverRead")

package io.github.proify.lyricon.app.compose.color

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private val ITEM_SPACING = 16.dp

@Composable
fun ColorPaletteDialog(
    title: String,
    show: MutableState<Boolean>,
    initialColor: Color = Color.Red,
    onDelete: () -> Unit,
    onConfirm: (Color) -> Unit,
    content: @Composable () -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedColor by remember(initialColor) { mutableStateOf(initialColor) }
    var hexInput by remember(initialColor) { mutableStateOf(selectedColor.toHexString()) }
    val clipboard = LocalClipboard.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    fun dismiss() {
        show.value = false
        keyboardController?.hide()
    }

    OverlayBottomSheet (
        show = show.value,
        modifier = Modifier,
        title = title,
        endAction = {
            IconButton(onClick = {
                onDelete()
                dismiss()
            }) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    tint = LocalContentColor.current,
                    contentDescription = "Delete"
                )
            }
        },
        onDismissRequest = { dismiss() },
        content = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .overScrollVertical()
            ) {
                item("color_picker_content") {
                    ColorPalette(
                        color = selectedColor,
                        onColorChanged = {
                            selectedColor = it
                            hexInput = it.toHexString()
                        }
                    )

                    Spacer(Modifier.height(ITEM_SPACING))

                    HexInputRow(
                        hexInput = hexInput,
                        onHexInputChange = {
                            hexInput = it
                            runCatching { selectedColor = it.parseHexColor() }
                        },
                        onCopy = {
                            scope.launch { clipboard.copyText(hexInput) }
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                        },
                        onPaste = {
                            scope.launch {
                                clipboard.pasteText()?.let { text ->
                                    hexInput = text
                                    runCatching { selectedColor = text.parseHexColor() }
                                }
                            }
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                    )

                    content()
                    Spacer(Modifier.height(ITEM_SPACING))

                    DialogButtonRow(
                        onCancel = { dismiss() },
                        onConfirm = {
                            onConfirm(selectedColor)
                            dismiss()
                        }
                    )

                    Spacer(Modifier.height(ITEM_SPACING))
                }
            }
        })
}

@Composable
private fun HexInputRow(
    hexInput: String,
    onHexInputChange: (String) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            modifier = Modifier.weight(1f),
            value = hexInput,
            onValueChange = onHexInputChange,
            label = stringResource(id = R.string.hint_custom_color),
            singleLine = true
        )

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(
            backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
            onClick = onCopy,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                tint = LocalContentColor.current,
                contentDescription = stringResource(R.string.copy)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(
            backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
            onClick = onPaste,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_paste),
                tint = LocalContentColor.current,
                contentDescription = stringResource(R.string.paste)
            )
        }
    }
}

@Composable
private fun DialogButtonRow(
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            text = stringResource(R.string.cancel),
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(ITEM_SPACING))
        TextButton(
            text = stringResource(R.string.action_confirm),
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}

private suspend fun Clipboard.copyText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("color", text)))
}

private suspend fun Clipboard.pasteText(): String? {
    return getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
}

private fun Color.toHexString(): String =
    String.format("#%08X", toArgb())

private fun String.parseHexColor(): Color {
    var hex = removePrefix("#")
    if (hex.length == 6) hex = "FF$hex"
    require(hex.length == 8) { "Invalid color format: $this" }
    return Color(hex.toULong(16).toLong())
}