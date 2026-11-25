/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 数字文本输入框。
 *
 * 组件只负责把用户输入过滤为“可编辑的数字文本”，不负责范围校验和业务合法性判断。
 * 例如空字符串、负号、小数点等编辑中间态会保留下来，由外层决定是否允许保存。
 *
 * @param value 当前输入文本。
 * @param onValueChange 输入文本变化回调，返回值已经经过数字字符过滤。
 * @param label 输入框标签。
 * @param allowDecimal 是否允许输入小数点。
 * @param allowNegative 是否允许输入负号。
 * @param autoSelectOnFocus 首次获得焦点时是否全选已有内容。
 * @param isError 是否显示错误态边框。
 * @param borderColor 自定义边框颜色；默认根据 [isError] 在主色和错误色之间切换。
 */
@Composable
fun NumberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    allowDecimal: Boolean = false,
    allowNegative: Boolean = true,
    autoSelectOnFocus: Boolean = false,
    isError: Boolean = false,
    borderColor: Color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
) {
    val initialTf = remember(value) {
        TextFieldValue(text = value, selection = TextRange(value.length))
    }

    var textFieldValueState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(initialTf)
    }

    var initialSelectionDone by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var shouldSelectAll by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (textFieldValueState.text != value) {
            if (!isFocused) {
                textFieldValueState =
                    TextFieldValue(text = value, selection = TextRange(value.length))
            } else {
                val sel = textFieldValueState.selection
                val clamped = sel.end.coerceIn(0, value.length)
                textFieldValueState = TextFieldValue(text = value, selection = TextRange(clamped))
            }
        } else if (!initialSelectionDone) {
            textFieldValueState = textFieldValueState.copy(selection = TextRange(value.length))
        }
        initialSelectionDone = true
    }

    LaunchedEffect(shouldSelectAll) {
        if (shouldSelectAll && textFieldValueState.text.isNotEmpty()) {
            textFieldValueState = textFieldValueState.copy(
                selection = TextRange(0, textFieldValueState.text.length)
            )
        }
        shouldSelectAll = false
    }

    Column(modifier = modifier) {
        TextField(
            colors = TextFieldDefaults.textFieldColors(borderColor = borderColor),
            label = label,
            value = textFieldValueState,
            onValueChange = { newValue ->
                val filtered = filterNumericInput(
                    input = newValue.text,
                    allowDecimal = allowDecimal,
                    allowNegative = allowNegative
                )
                val rawSel = newValue.selection.end
                val clampedSel = rawSel.coerceIn(0, filtered.length)

                textFieldValueState = TextFieldValue(
                    text = filtered,
                    selection = TextRange(clampedSel)
                )
                onValueChange(filtered)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !isFocused && autoSelectOnFocus) {
                        shouldSelectAll = true
                    }
                    isFocused = focusState.isFocused
                },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            singleLine = true
        )
    }
}

/**
 * 过滤数字输入文本。
 *
 * 规则：只保留数字、可选小数点和可选负号；负号最多保留在开头，小数点最多保留一个。
 */
internal fun filterNumericInput(
    input: String,
    allowDecimal: Boolean,
    allowNegative: Boolean
): String {
    if (input.isEmpty()) return input

    var result = input.filter { char ->
        char.isDigit() ||
                (char == '.' && allowDecimal) ||
                (char == '-' && allowNegative)
    }

    if (allowNegative) {
        val hasLeadingNegative = result.startsWith('-')
        result = result.replace("-", "")
        if (hasLeadingNegative) {
            result = "-$result"
        }
    }

    if (allowDecimal) {
        val firstDot = result.indexOf('.')
        if (firstDot != -1) {
            result = result.take(firstDot + 1) +
                    result.substring(firstDot + 1).replace(".", "")
        }
    }

    return result
}
