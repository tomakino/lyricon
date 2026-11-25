/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.ai.explain.AiExplainPrompt
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.preference.StringInputPreference

/**
 * AI 音乐解读设置（功能级配置）。
 *
 * 提供自定义系统提示词的设置选项，允许用户自定义音乐解读的风格和内容。
 *
 * @author Tomakino
 * @since 2026
 */
@Composable
fun AiExplainPreference(preferences: SharedPreferences) {
    StringInputPreference(
        preferences = preferences,
        key = AiExplainPrompt.KEY_CUSTOM_SYSTEM_PROMPT,
        title = stringResource(R.string.item_ai_explain_custom_prompt),
        dialogSummary = stringResource(R.string.dialog_summary_ai_explain_custom_prompt),
        defaultValue = AiExplainPrompt.DEFAULT_SYSTEM_PROMPT,
        startAction = { IconActions(painterResource(R.drawable.psychology_24px)) },
    )
}
