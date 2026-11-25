/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.proify.android.extensions.json
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.preference.CheckboxPreference
import io.github.proify.lyricon.app.compose.preference.DoubleInputPreference
import io.github.proify.lyricon.app.compose.preference.StringInputPreference
import io.github.proify.lyricon.app.util.AppPrefs
import io.github.proify.lyricon.app.util.toast
import io.github.proify.lyricon.lyric.ai.core.AiConfig
import io.github.proify.lyricon.lyric.ai.core.AiProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * AI 提供商配置编辑页（独立二级 Activity）。
 *
 * 改动即自动保存（[onSave] 每次变更实时写回配置集合），无保存按钮；
 * 功能按「配置 / 连接 / 参数」分类平铺展示，重命名以「配置名称」输入项平铺，
 * 提供商模板不再作为编辑项（用户手填 OpenAI 兼容参数，provider 默认为 OpenAI）。
 *
 * @author Tomakino
 * @since 2026
 */
@Composable
fun AiConfigEditPage(
    profile: AiProfile,
    onSave: (AiProfile) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val prefs = AppPrefs.lyricBasicStylePrefs
    var draft by remember(profile.id) { mutableStateOf(profile) }
    var showDelete by remember { mutableStateOf(false) }

    // 自动保存：任何字段/名称变更即写回集合。
    fun update(transform: (AiConfig) -> AiConfig) {
        draft = draft.copy(config = transform(draft.config))
        onSave(draft)
    }

    fun rename(name: String) {
        draft = draft.copy(name = name)
        onSave(draft)
    }

    fun redescribe(name: String) {
        draft = draft.copy(description = name)
        onSave(draft)
    }

    BackHandler { onBack() }

    AppToolBarListContainer(
        title = stringResource(R.string.edit_ai_provider_config),
        canBack = true,
        backEvent = onBack,
        actions = {
            IconButton(onClick = {
                showDelete = true
            }) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.item_ai_config_more),
                )
            }
        },
    ) {
        // ---- 配置：配置名称（重命名平铺） ----
        item(key = "name") {
            SmallTitle(
                text = stringResource(R.string.section_ai_config_name),
                insideMargin = SectionTitlePadding
            )
            Card(
                modifier = SectionCardModifier,
            ) {
                StringInputPreference(
                    startAction = { IconActions(painterResource(R.drawable.drive_file_rename_24px)) },
                    preferences = prefs,
                    key = "",
                    title = stringResource(R.string.dialog_summary_config_name),
                    summary = draft.name.ifBlank { stringResource(R.string.not_configured) },
                    dialogSummary = stringResource(R.string.dialog_summary_config_name),
                    maxLines = 1,
                    value = draft.name,
                    onValueChange = { rename(it) },
                )
                StringInputPreference(
                    startAction = { IconActions(painterResource(R.drawable.description_24px)) },
                    preferences = prefs,
                    key = "",
                    title = stringResource(R.string.item_ai_config_description),
                    summary = if (draft.description.isNullOrBlank())
                        stringResource(R.string.not_configured)
                    else draft.description,
                    dialogSummary = stringResource(R.string.dialog_summary_ai_config_description),
                    maxLines = 1,
                    value = draft.description,
                    onValueChange = { redescribe(it) },
                )
            }
        }

        // ---- 连接：Base URL / API Key / Model / 测试连接 ----
        item(key = "connection") {
            SmallTitle(
                text = stringResource(R.string.section_ai_config_connection),
                insideMargin = SectionTitlePadding
            )
            Card(
                modifier = SectionCardModifier,
            ) {
                // Base URL

                val baseUrlBlank = draft.config.baseUrl.isNullOrBlank()

                StringInputPreference(
                    preferences = prefs,
                    key = "",
                    title = stringResource(R.string.item_translation_base_url),
                    summary = if (baseUrlBlank)
                        stringResource(R.string.not_configured)
                    else draft.config.baseUrl,
                    dialogSummary = stringResource(R.string.dialog_summary_translation_base_url),
                    startAction = { IconActions(painterResource(R.drawable.link_24px)) },
                    maxLines = 1,
                    value = draft.config.baseUrl,
                    onValueChange = { v ->
                        update { it.copy(baseUrl = v.takeIf { s -> s.isNotBlank() }) }
                    },
                )

                // API Key
                val apiKeySet = !draft.config.apiKey.isNullOrBlank()
                StringInputPreference(
                    preferences = prefs,
                    key = "",
                    title = stringResource(R.string.item_translation_api_key),
                    summary = if (apiKeySet) {
                        stringResource(R.string.item_translation_api_key_set)
                    } else {
                        stringResource(R.string.item_translation_api_key_not_set)
                    },
                    dialogSummary = stringResource(R.string.dialog_summary_translation_api_key),
                    startAction = { IconActions(painterResource(R.drawable.vpn_key_24px)) },
                    maxLines = 1,
                    value = draft.config.apiKey,
                    onValueChange = { v -> update { it.copy(apiKey = v.takeIf { s -> s.isNotBlank() }) } },
                )

                // Model
                AiConfigEditModelPreference(
                    config = draft.config,
                    onModelChange = { m -> update { it.copy(model = m.takeIf { s -> s.isNotBlank() }) } },
                )

            }
        }

        // ---- 参数：采样与惩罚项 ----
        item(key = "params") {
            SmallTitle(
                text = stringResource(R.string.section_ai_config_params),
                insideMargin = SectionTitlePadding
            )
            Card(
                modifier = SectionCardModifier,
            ) {
                DoubleInputPreference(
                    preferences = prefs,
                    key = "",
                    title = stringResource(R.string.item_translation_temperature),
                    dialogSummary = stringResource(R.string.dialog_summary_translation_temperature),
                    range = 0.0..2.0,
                    startAction = { IconActions(painterResource(R.drawable.device_thermostat_24px)) },
                    value = draft.config.temperature.toDouble(),
                    onValueChange = { v ->
                        update {
                            it.copy(
                                temperature = (v
                                    ?: AiConfig.DEFAULT_TEMPERATURE.toDouble()).toFloat()
                            )
                        }
                    },
                )

                DoubleInputPreference(
                    preferences = prefs,
                    key = "",
                    title = stringResource(R.string.item_translation_top_p),
                    dialogSummary = stringResource(R.string.dialog_summary_translation_top_p),
                    range = 0.0..1.0,
                    startAction = { IconActions(painterResource(R.drawable.discover_tune_24px)) },
                    value = draft.config.topP.toDouble(),
                    onValueChange = { v ->
                        update {
                            it.copy(
                                topP = (v ?: AiConfig.DEFAULT_TOP_P.toDouble()).toFloat()
                            )
                        }
                    },
                )

                DoubleInputPreference(
                    preferences = prefs,
                    key = "",
                    title = stringResource(R.string.item_translation_presence_penalty),
                    dialogSummary = stringResource(R.string.dialog_summary_translation_presence_penalty),
                    range = -2.0..2.0,
                    startAction = { IconActions(painterResource(R.drawable.do_not_disturb_on_24px)) },
                    value = draft.config.presencePenalty.toDouble(),
                    onValueChange = { v ->
                        update {
                            it.copy(
                                presencePenalty = (v
                                    ?: AiConfig.DEFAULT_PRESENCE_PENALTY.toDouble()).toFloat()
                            )
                        }
                    },
                )

                DoubleInputPreference(
                    preferences = prefs,
                    key = "",
                    title = stringResource(R.string.item_translation_frequency_penalty),
                    dialogSummary = stringResource(R.string.dialog_summary_translation_frequency_penalty),
                    range = -2.0..2.0,
                    startAction = { IconActions(painterResource(R.drawable.lightbulb_2_24px)) },
                    value = draft.config.frequencyPenalty.toDouble(),
                    onValueChange = { v ->
                        update {
                            it.copy(
                                frequencyPenalty = (v
                                    ?: AiConfig.DEFAULT_FREQUENCY_PENALTY.toDouble()).toFloat()
                            )
                        }
                    },
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(16.dp))
        }
    }

    // ---- 删除确认 ----
    if (showDelete) {
        ConfirmDeleteDialog(
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                onDelete()
            },
        )
    }
}

/** 各小节标题的统一内边距（与 [AiLaboratoryActivity] 的小节标题一致）。 */
private val SectionTitlePadding = PaddingValues(
    start = 26.dp,
    top = 16.dp,
    end = 26.dp,
    bottom = 10.dp,
)

/** 各小节内容 Card 的通用修饰符：水平留白 + 撑满宽度。 */
private val SectionCardModifier = Modifier
    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
    .fillMaxWidth()

/** 模型编辑行（手动输入 + 自动拉取可用模型）。 */
@Composable
private fun AiConfigEditModelPreference(
    config: AiConfig,
    onModelChange: (String) -> Unit,
) {
    val prefs = AppPrefs.lyricBasicStylePrefs
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val apiKeyNotSet = stringResource(R.string.item_translation_model_api_key_not_set)
    val noModels = stringResource(R.string.item_translation_model_empty)
    val unknown = stringResource(R.string.unknown)

    StringInputPreference(
        preferences = prefs,
        key = "",
        title = stringResource(R.string.item_translation_model),
        summary = if (config.model.isNullOrBlank())
            stringResource(R.string.not_configured)
        else config.model,
        dialogSummary = stringResource(R.string.dialog_summary_translation_model),
        startAction = { IconActions(painterResource(R.drawable.psychology_24px)) },
        maxLines = 1,
        value = config.model,
        onValueChange = { onModelChange(it) },
        endActions = {
            IconButton(
                onClick = {
                    if (isLoading) return@IconButton
                    val apiKey = config.apiKey
                    if (apiKey.isNullOrBlank()) {
                        toast(apiKeyNotSet)
                        return@IconButton
                    }
                    val baseUrl = config.baseUrl
                    if (baseUrl.isNullOrBlank()) {
                        toast(apiKeyNotSet)
                        return@IconButton
                    }
                    isLoading = true
                    scope.launch {
                        val result = fetchOpenAiModels(baseUrl, apiKey)
                        isLoading = false
                        result.onSuccess { fetched ->
                            if (fetched.isEmpty()) {
                                toast(noModels)
                            } else {
                                models = (fetched + listOfNotNull(config.model))
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                showDialog = true
                            }
                        }.onFailure {
                            toast(it.message ?: unknown)
                        }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp))
                } else {
                    Icon(imageVector = MiuixIcons.Search, contentDescription = null)
                }
            }
        },
    )

    if (showDialog) {
        @Suppress("KotlinConstantConditions")
        WindowBottomSheet(
            show = showDialog,
            title = stringResource(R.string.dialog_title_available_models),
            onDismissRequest = { showDialog = false },
            backgroundColor = MiuixTheme.colorScheme.surface,
            insideMargin = DpSize(0.dp, 0.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .overScrollVertical()
            ) {
                models.forEach { model ->
                    item(key = model) {
                        Card(
                            modifier = Modifier
                                .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                                .fillMaxWidth()
                        ) {
                            CheckboxPreference(
                                title = model,
                                checked = config.model == model,
                                onCheckedChange = {
                                    onModelChange(model)
                                    showDialog = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}


internal val modelsHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

internal suspend fun fetchOpenAiModels(
    baseUrl: String,
    apiKey: String
): Result<List<String>> = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url(buildOpenAiModelsUrl(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        modelsHttpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                error("HTTP ${response.code} ${body.take(160)}".trim())
            }

            json.decodeFromString<OpenAiModelsResponse>(body)
                .data
                .map { it.id }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
    }
}

internal fun buildOpenAiModelsUrl(baseUrl: String): String {
    val trimmedUrl = baseUrl.trim().removeSuffix("/")

    val normalizedBaseUrl = when {
        trimmedUrl.endsWith("/models") -> return trimmedUrl
        trimmedUrl.endsWith("/chat/completions") -> trimmedUrl.removeSuffix("/chat/completions")
        else -> trimmedUrl
    }

    return "$normalizedBaseUrl/models"
}

@Serializable
internal data class OpenAiModelsResponse(
    val data: List<OpenAiModel> = emptyList()
)

@Serializable
internal data class OpenAiModel(
    val id: String = ""
)

/** 删除确认弹窗。 */
@Composable
private fun ConfirmDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        title = stringResource(R.string.dialog_title_delete_config),
        summary = stringResource(R.string.dialog_summary_delete_config),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                colors = ButtonDefaults.textButtonColorsPrimary(),
                text = stringResource(R.string.action_delete),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
