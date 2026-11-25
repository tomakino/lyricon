/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.preference

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.proify.android.extensions.formatToString
import io.github.proify.android.extensions.fromJsonOrNull
import io.github.proify.android.extensions.toJson
import io.github.proify.lyricon.app.compose.RectFInputDialog
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.RectF
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * RectF 输入 Preference。
 *
 * 以 JSON 字符串形式读写 [RectF]，用于配置外边距、内边距等四方向矩形值。
 * 弹窗内四个字段共享同一套数值校验；确认后会一次性写回完整 RectF。
 *
 * @param preferences 目标 SharedPreferences。
 * @param key 保存 RectF JSON 的配置 key。
 * @param title Preference 标题。
 * @param defaultValue 未写入配置或解析失败时使用的默认 RectF。
 * @param summary 自定义摘要；为 null 时展示 left、top、right、bottom 四个值。
 * @param startAction Preference 左侧动作区域。
 * @param endActions Preference 右侧动作区域。
 */
@Composable
fun RectInputPreference(
    preferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: RectF = RectF(),
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: (@Composable (RectF) -> String)? = null,
    dialogSummary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    enabled: Boolean = true
) {

    val showDialog = remember { mutableStateOf(false) }
    val prefValueState = rememberStringPreference(preferences, key, null)

    val value = prefValueState.value
    val rectF = value?.fromJsonOrNull<RectF>() ?: defaultValue

    val currentSummary = summary?.invoke(rectF)
        ?: "${rectF.left.formatToString()}, ${rectF.top.formatToString()}, ${rectF.right.formatToString()}, ${rectF.bottom.formatToString()}"

    if (showDialog.value) {
        RectFInputDialog(
            initialLeft = rectF.left,
            initialTop = rectF.top,
            initialRight = rectF.right,
            initialBottom = rectF.bottom,
            show = showDialog,
            title = title,
            summary = dialogSummary,
            onConfirm = { left, top, right, bottom ->
                val rectF = RectF(left, top, right, bottom)
                preferences.editCommit {
                    putString(key, rectF.toJson())
                }
            }
        )
    }

    ArrowPreference(
        title = title,
        titleColor = titleColor,
        summary = currentSummary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = endActions,
        insideMargin = insideMargin,
        onClick = {
            showDialog.value = true
        },
        holdDownState = showDialog.value,
        enabled = enabled
    )
}
