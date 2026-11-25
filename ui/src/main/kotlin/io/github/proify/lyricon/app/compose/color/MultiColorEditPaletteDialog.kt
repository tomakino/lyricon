/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.color

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.proify.lyricon.app.compose.icon.MinusCircle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private val ITEM_SPACING = 16.dp
private val DEFAULT_NEW_COLOR = Color.Black

@Composable
fun MultiColorEditPaletteDialog(
    title: String,
    show: MutableState<Boolean>,
    initialColor: List<Color> = listOf(DEFAULT_NEW_COLOR),
    onDelete: () -> Unit,
    onConfirm: (List<Color>) -> Unit,
    content: @Composable () -> Unit = {}
) {
    val editColors = remember(initialColor) { initialColor.toMutableStateList() }

    var selectedIndex by remember { mutableIntStateOf(0) }
    if (editColors.isEmpty()) {
        editColors.add(DEFAULT_NEW_COLOR)
    }
    if (selectedIndex >= editColors.size) {
        selectedIndex = editColors.lastIndex
    }
    val selectedColor = editColors[selectedIndex]

    var hexInput by remember(selectedColor) {
        mutableStateOf(selectedColor.toHexString())
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    fun dismiss() {
        show.value = false
        keyboardController?.hide()
    }

    fun addColor() {
        editColors.add(DEFAULT_NEW_COLOR)
        selectedIndex = editColors.lastIndex
        hexInput = DEFAULT_NEW_COLOR.toHexString()
    }

    fun deleteCurrentColor() {
        if (editColors.size <= 1) {
            editColors[0] = DEFAULT_NEW_COLOR
            selectedIndex = 0
            hexInput = DEFAULT_NEW_COLOR.toHexString()
        } else {
            editColors.removeAt(selectedIndex)
            selectedIndex = selectedIndex.coerceAtMost(editColors.lastIndex)
            hexInput = editColors[selectedIndex].toHexString()
        }
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
                    contentDescription = "Delete all"
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
                item("color_selector_row") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(editColors.size, key = { it }) { index ->
                                ColorSwatch(
                                    color = editColors[index],
                                    isSelected = index == selectedIndex,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            selectedIndex = index
                                            hexInput = editColors[index].toHexString()
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = {
                                deleteCurrentColor()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            },
                            backgroundColor = MiuixTheme.colorScheme.secondaryVariant
                        ) {
                            Icon(
                                imageVector = MiuixIcons.MinusCircle,
                                tint = LocalContentColor.current,
                                contentDescription = stringResource(R.string.action_delete_current_color)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))

                        IconButton(
                            onClick = {
                                addColor()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)

                            },
                            backgroundColor = MiuixTheme.colorScheme.secondaryVariant
                        ) {
                            Icon(
                                imageVector = MiuixIcons.AddCircle,
                                tint = LocalContentColor.current,
                                contentDescription = stringResource(R.string.action_add_color)
                            )
                        }
                    }

                    Spacer(Modifier.height(ITEM_SPACING))
                }

                item("color_picker") {
                    ColorPalette(
                        color = selectedColor,
                        onColorChanged = { newColor ->
                            editColors[selectedIndex] = newColor
                            hexInput = newColor.toHexString()
                        }
                    )
                    Spacer(Modifier.height(ITEM_SPACING))
                }

                item("hex_input") {
                    HexInputRow(
                        hexInput = hexInput,
                        onHexInputChange = { newHex ->
                            hexInput = newHex
                            runCatching {
                                val color = newHex.parseHexColor()
                                editColors[selectedIndex] = color
                            }
                        },
                        onCopy = {
                            scope.launch { clipboard.copyText(hexInput) }
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                        },
                        onPaste = {
                            scope.launch {
                                clipboard.pasteText()?.let { text ->
                                    hexInput = text
                                    runCatching {
                                        val color = text.parseHexColor()
                                        editColors[selectedIndex] = color
                                    }
                                }
                            }
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                    )

                    Spacer(Modifier.height(ITEM_SPACING))
                }

                item("custom_content") {
                    content()
                }

                item("confirm_buttons") {
                    DialogButtonRow(
                        onCancel = { dismiss() },
                        onConfirm = {
                            onConfirm(editColors.toList())
                            dismiss()
                        }
                    )

                    Spacer(Modifier.height(ITEM_SPACING))
                }
            }
        })
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color)
            .border(if (isSelected) 2.dp else 0.dp, Color.Black, CircleShape)
    ) {
    }
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
