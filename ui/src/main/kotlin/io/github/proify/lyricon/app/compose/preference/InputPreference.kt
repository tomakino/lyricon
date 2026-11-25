/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.preference

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.android.extensions.formatToString
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.NumberTextField
import io.github.proify.lyricon.app.compose.color
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.app.util.TimeFormatter
import io.github.proify.lyricon.app.util.editCommit
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Preference 值的摘要展示策略。
 *
 * 类型化输入组件会继续用字符串写入 SharedPreferences 以兼容已有配置，
 * 但摘要展示可以通过该策略转换为更适合用户阅读的文本，例如时间长度。
 */
sealed class PreferenceValueDisplay<T> {
    abstract fun format(value: T?): String?

    /** 使用调用方提供的格式化函数展示原始值。 */
    class Raw<T>(private val formatter: (T) -> String) : PreferenceValueDisplay<T>() {
        override fun format(value: T?): String? = value?.let(formatter)
    }

    /**
     * 将 Long 数值按时间长度展示。
     *
     * @param multiplier 展示前应用的倍率，例如配置值是秒时传入 1000 转为毫秒。
     */
    data class Time(val multiplier: Long = 1L) : PreferenceValueDisplay<Long>() {
        override fun format(value: Long?): String? {
            return value?.let {
                TimeFormatter.formatTime(it * multiplier, AppLangUtils.getLocale())
            }
        }
    }
}

/**
 * 字符串输入 Preference。
 *
 * 适用于 URL、模型名称、正则、提示词等不需要数值校验的配置项。
 * 空字符串保存时会移除主 key 和所有 [syncKeys]，从而回落到默认值。
 *
 * @param preferences 目标 SharedPreferences。
 * @param key 主配置 key。
 * @param title Preference 标题。
 * @param defaultValue 未写入配置时使用的默认文本。
 * @param syncKeys 保存或清空时同步写入的其他 key。
 * @param showKeyboard 打开弹窗后是否自动聚焦并显示键盘。
 * @param summary 非空时直接作为摘要展示，不再使用当前配置值。
 * @param label 输入框标签。
 * @param maxLines 文本输入框最多显示的行数。
 */
@Composable
fun StringInputPreference(
    modifier: Modifier = Modifier,
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: String? = null,
    syncKeys: List<String> = emptyList(),
    showKeyboard: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    dialogSummary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
    maxLines: Int = 10,
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
) {
    TypedInputPreference(
        modifier = modifier,
        preferences = preferences,
        key = key,
        title = title,
        defaultText = defaultValue,
        syncKeys = syncKeys,
        showKeyboard = showKeyboard,
        titleColor = titleColor,
        summary = summary,
        dialogSummary = dialogSummary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = endActions,
        insideMargin = insideMargin,
        onClick = onClick,
        holdDownState = holdDownState,
        enabled = enabled,
        label = label,
        parse = { it },
        format = { it },
        isSavable = { true },
        display = { it.orEmpty() },
        controlledText = value,
        onControlledSave = onValueChange,
    ) { dialogTitle, dialogDescription, initialValue, isValid, save, dismiss ->
        StringInputPreferenceDialog(
            title = dialogTitle,
            summary = dialogDescription,
            initialValue = initialValue,
            showKeyboard = showKeyboard,
            label = label,
            maxLines = maxLines,
            isValidInput = isValid,
            onSave = save,
            onDismiss = dismiss,
        )
    }
}

/**
 * Int 输入 Preference。
 *
 * 该组件对调用方暴露 Int 类型 API，但底层仍以字符串写入 SharedPreferences，
 * 用于兼容项目中已有的字符串型数值配置。
 *
 * @param defaultValue 未写入配置时使用的默认值。
 * @param range 可保存值范围；为 null 时只校验是否为合法 Int。
 * @param summary 自定义摘要，返回 null 时使用默认数值展示。
 */
@Composable
fun IntInputPreference(
    modifier: Modifier = Modifier,
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: Int? = null,
    range: IntRange? = null,
    syncKeys: List<String> = emptyList(),
    showKeyboard: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: (@Composable (Int?) -> String?)? = null,
    dialogSummary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
    value: Int? = null,
    onValueChange: ((Int?) -> Unit)? = null,
) {
    NumberInputPreference(
        modifier = modifier,
        preferences = preferences,
        key = key,
        title = title,
        defaultValue = defaultValue,
        rangeText = range?.let { "${it.first}-${it.last}" },
        syncKeys = syncKeys,
        showKeyboard = showKeyboard,
        titleColor = titleColor,
        summary = summary,
        dialogSummary = dialogSummary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = endActions,
        insideMargin = insideMargin,
        onClick = onClick,
        holdDownState = holdDownState,
        enabled = enabled,
        label = label,
        allowDecimal = false,
        allowNegative = range?.first?.let { it < 0 } ?: true,
        parse = { it.toIntOrNull() },
        format = { it.toString() },
        display = PreferenceValueDisplay.Raw(Int::toString),
        isInRange = { range == null || it in range },
        controlledValue = value,
        onControlledValueChange = onValueChange,
    )
}

/**
 * Long 输入 Preference。
 *
 * 适用于超时、延迟等可能需要时间格式化展示的整数配置。
 * 底层存储保持 string-backed，不改变已有配置格式。
 *
 * @param defaultValue 未写入配置时使用的默认值。
 * @param range 可保存值范围；为 null 时只校验是否为合法 Long。
 * @param summary 自定义摘要，返回 null 时使用 [display] 展示。
 * @param display 当前值的摘要展示策略。
 */
@Composable
fun LongInputPreference(
    modifier: Modifier = Modifier,
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: Long? = null,
    range: LongRange? = null,
    syncKeys: List<String> = emptyList(),
    showKeyboard: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: (@Composable (Long?) -> String?)? = null,
    dialogSummary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
    display: PreferenceValueDisplay<Long> = PreferenceValueDisplay.Raw(Long::toString),
) {
    NumberInputPreference(
        modifier = modifier,
        preferences = preferences,
        key = key,
        title = title,
        defaultValue = defaultValue,
        rangeText = range?.let { "${it.first}-${it.last}" },
        syncKeys = syncKeys,
        showKeyboard = showKeyboard,
        titleColor = titleColor,
        summary = summary,
        dialogSummary = dialogSummary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = endActions,
        insideMargin = insideMargin,
        onClick = onClick,
        holdDownState = holdDownState,
        enabled = enabled,
        label = label,
        allowDecimal = false,
        allowNegative = range?.first?.let { it < 0 } ?: true,
        parse = { it.toLongOrNull() },
        format = { it.toString() },
        display = display,
        isInRange = { range == null || it in range },
    )
}

/**
 * Double 输入 Preference。
 *
 * 适用于尺寸、比例、边缘长度等浮点配置。保存前会用 [formatToString]
 * 规范化文本，避免写入多余的小数格式。
 *
 * @param defaultValue 未写入配置时使用的默认值。
 * @param range 可保存值范围；为 null 时只校验是否为合法 Double。
 * @param summary 自定义摘要，返回 null 时使用 [display] 展示。
 * @param display 当前值的摘要展示策略。
 */
@Composable
fun DoubleInputPreference(
    modifier: Modifier = Modifier,
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: Double? = null,
    range: ClosedFloatingPointRange<Double>? = null,
    syncKeys: List<String> = emptyList(),
    showKeyboard: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: (@Composable (Double?) -> String?)? = null,
    dialogSummary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
    value: Double? = null,
    onValueChange: ((Double?) -> Unit)? = null,
    display: PreferenceValueDisplay<Double> = PreferenceValueDisplay.Raw { it.formatToString() },
) {
    NumberInputPreference(
        modifier = modifier,
        preferences = preferences,
        key = key,
        title = title,
        defaultValue = defaultValue,
        rangeText = range?.let { "${it.start.formatToString()}-${it.endInclusive.formatToString()}" },
        syncKeys = syncKeys,
        showKeyboard = showKeyboard,
        titleColor = titleColor,
        summary = summary,
        dialogSummary = dialogSummary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = endActions,
        insideMargin = insideMargin,
        onClick = onClick,
        holdDownState = holdDownState,
        enabled = enabled,
        label = label,
        allowDecimal = true,
        allowNegative = range?.start?.let { it < 0.0 } ?: true,
        parse = { it.toDoubleOrNull() },
        format = { it.formatToString() },
        display = display,
        isInRange = { range == null || it in range },
        controlledValue = value,
        onControlledValueChange = onValueChange,
    )
}

/**
 * 数值型 Preference 的内部实现。
 *
 * 负责把具体数值类型的解析、格式化、范围校验接入通用 Preference 壳层。
 */
@Composable
private fun <T> NumberInputPreference(
    modifier: Modifier,
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: T?,
    rangeText: String?,
    syncKeys: List<String>,
    showKeyboard: Boolean,
    titleColor: BasicComponentColors,
    summary: (@Composable (T?) -> String?)?,
    dialogSummary: String?,
    summaryColor: BasicComponentColors,
    startAction: @Composable (() -> Unit)?,
    endActions: @Composable RowScope.() -> Unit,
    insideMargin: PaddingValues,
    onClick: (() -> Unit)?,
    holdDownState: Boolean,
    enabled: Boolean,
    label: String?,
    allowDecimal: Boolean,
    allowNegative: Boolean,
    parse: (String) -> T?,
    format: (T) -> String,
    display: PreferenceValueDisplay<T>,
    isInRange: (T) -> Boolean,
    controlledValue: T? = null,
    onControlledValueChange: ((T?) -> Unit)? = null,
) {
    val isSavable: (String) -> Boolean = { text ->
        text.isEmpty() || parse(text)?.let(isInRange) == true
    }

    TypedInputPreference(
        modifier = modifier,
        preferences = preferences,
        key = key,
        title = title,
        defaultText = defaultValue?.let(format),
        syncKeys = syncKeys,
        showKeyboard = showKeyboard,
        titleColor = titleColor,
        summary = null,
        dialogSummary = dialogSummary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = endActions,
        insideMargin = insideMargin,
        onClick = onClick,
        holdDownState = holdDownState,
        enabled = enabled,
        label = label,
        parse = parse,
        format = format,
        isSavable = isSavable,
        display = { value -> summary?.invoke(value) ?: display.format(value).orEmpty() },
        controlledText = if (onControlledValueChange != null) controlledValue?.let(format) else null,
        onControlledSave = onControlledValueChange?.let { cb ->
            { text -> cb(parse(text)) }
        },
    ) { dialogTitle, dialogDescription, initialValue, isValid, save, dismiss ->
        NumberInputPreferenceDialog(
            title = dialogTitle,
            summary = dialogDescription,
            initialValue = initialValue,
            showKeyboard = showKeyboard,
            label = label,
            allowDecimal = allowDecimal,
            allowNegative = allowNegative,
            rangeText = rangeText,
            isValidInput = isValid,
            onSave = save,
            onDismiss = dismiss,
        )
    }
}

/**
 * 类型化输入 Preference 的通用壳层。
 *
 * 负责读取 SharedPreferences、展示 ArrowPreference、打开输入弹窗、保存主 key 与同步 key。
 * 具体输入 UI 和合法性规则由调用方通过参数注入。
 *
 * 传入 [onControlledSave] 时进入受控模式：不读写 SharedPreferences，
 * 而是以 [controlledText] 展示、通过 [onControlledSave] 回调保存。
 */
@Composable
private fun <T> TypedInputPreference(
    modifier: Modifier,
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultText: String?,
    syncKeys: List<String>,
    showKeyboard: Boolean,
    titleColor: BasicComponentColors,
    summary: String?,
    dialogSummary: String?,
    summaryColor: BasicComponentColors,
    startAction: @Composable (() -> Unit)?,
    endActions: @Composable RowScope.() -> Unit,
    insideMargin: PaddingValues,
    onClick: (() -> Unit)?,
    holdDownState: Boolean,
    enabled: Boolean,
    label: String?,
    parse: (String) -> T?,
    format: (T) -> String,
    isSavable: (String) -> Boolean,
    display: @Composable (T?) -> String,
    controlledText: String? = null,
    onControlledSave: ((String) -> Unit)? = null,
    dialog: @Composable (
        title: String,
        summary: String?,
        initialValue: String,
        isValid: (String) -> Boolean,
        onSave: (String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    val isControlled = onControlledSave != null
    val prefValueState =
        if (isControlled) null else rememberStringPreference(preferences, key, defaultText)
    val currentText = if (isControlled) controlledText else (prefValueState?.value ?: defaultText)
    val currentValue = currentText?.let(parse)
    val rawSummary = summary ?: display(currentValue)
    val finalSummary = rawSummary.ifBlank { stringResource(id = R.string.default_text) }
    val truncatedSummary = if (finalSummary.lines().size > 3) {
        finalSummary.lines().take(4).joinToString("\n") + "..."
    } else finalSummary

    var showDialog by remember { mutableStateOf(false) }

    ArrowPreference(
        title = title,
        titleColor = titleColor,
        summary = truncatedSummary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = endActions,
        modifier = modifier,
        insideMargin = insideMargin,
        onClick = {
            onClick?.invoke()
            showDialog = true
        },
        holdDownState = holdDownState || showDialog,
        enabled = enabled,
    )

    if (showDialog) {
        dialog(
            title,
            dialogSummary,
            currentText.orEmpty(),
            isSavable,
            { text ->
                showDialog = false
                val storedValue = parse(text)?.let(format) ?: text
                if (isControlled) {
                    onControlledSave.invoke(storedValue)
                } else {
                    preferences.editCommit {
                        if (text.isEmpty()) {
                            remove(key)
                            prefValueState?.value = null
                            syncKeys.forEach { remove(it) }
                        } else {
                            putString(key, storedValue)
                            syncKeys.forEach { putString(it, storedValue) }
                        }
                    }
                }
            },
            { showDialog = false },
        )
    }

    @Suppress("UNUSED_EXPRESSION")
    showKeyboard
    @Suppress("UNUSED_EXPRESSION")
    label
}

/** 字符串输入弹窗。 */
@Composable
private fun StringInputPreferenceDialog(
    title: String,
    summary: String?,
    initialValue: String,
    showKeyboard: Boolean,
    label: String?,
    maxLines: Int,
    isValidInput: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var inputValue by remember { mutableStateOf(initialValue) }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(initialValue.length)
            )
        )
    }

    InputDialogScaffold(
        title = title,
        summary = summary,
        showKeyboard = showKeyboard,
        isValid = isValidInput(inputValue),
        onDismiss = onDismiss,
        onSave = { onSave(inputValue) },
    ) { focusRequester ->
        TextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                inputValue = it.text
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .focusRequester(focusRequester),
            label = label.orEmpty(),
            singleLine = false,
            maxLines = maxLines,
        )
    }
}

/** 数值输入弹窗。 */
@Composable
private fun NumberInputPreferenceDialog(
    title: String,
    summary: String?,
    initialValue: String,
    showKeyboard: Boolean,
    label: String?,
    allowDecimal: Boolean,
    allowNegative: Boolean,
    rangeText: String?,
    isValidInput: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var inputValue by remember { mutableStateOf(initialValue) }
    val isValid = isValidInput(inputValue)

    InputDialogScaffold(
        title = title,
        summary = summary,
        showKeyboard = showKeyboard,
        isValid = isValid,
        onDismiss = onDismiss,
        onSave = { onSave(inputValue) },
    ) { focusRequester ->
        NumberTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            allowDecimal = allowDecimal,
            allowNegative = allowNegative,
            modifier = Modifier.focusRequester(focusRequester),
            autoSelectOnFocus = true,
            isError = !isValid,
            label = label.orEmpty(),
        )

        if (!rangeText.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = rangeText,
                    fontSize = 13.sp,
                    color = if (isValid) {
                        BasicComponentDefaults.summaryColor().color(true)
                    } else {
                        MiuixTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

/** 输入弹窗的通用布局，包含滚动内容区、键盘控制和底部操作按钮。 */
@Composable
private fun InputDialogScaffold(
    title: String,
    summary: String?,
    showKeyboard: Boolean,
    isValid: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable (FocusRequester) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    fun dismiss() {
        onDismiss()
        keyboardController?.hide()
    }

    LaunchedEffect(Unit) {
        if (showKeyboard) {
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    WindowDialog(
        title = title,
        summary = summary,
        show = true,
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
                content(focusRequester)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(id = R.string.cancel),
                    onClick = { dismiss() },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(20.dp))

                TextButton(
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    text = stringResource(id = R.string.action_save),
                    onClick = {
                        onSave()
                        keyboardController?.hide()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isValid
                )
            }
        }
    }
}
