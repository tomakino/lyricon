/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.extra.OverlayDialog
import io.github.proify.lyricon.app.compose.custom.miuix.preference.CheckboxPreference
import io.github.proify.lyricon.app.compose.preference.StringInputPreference
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.compose.preference.rememberStringPreference
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.lyric.style.TextStyle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.Collator
import java.util.Locale

/**
 * AI 歌词翻译（功能级配置）。
 *
 * 连接类配置（API Key / Base URL / Model / 高级参数）统一在 [AiBasicConfigPreference] 管理，
 * 所有 AI 功能共用；这里只保留翻译功能自身的选项。
 *
 * @author Tomakino
 * @since 2026
 */
@Composable
fun AiTranslationPreference(preferences: SharedPreferences) {
    var isAiTranslationEnabled by rememberBooleanPreference(
        sharedPreferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_ENABLED,
        defaultValue = TextStyle.Defaults.AI_TRANSLATION_ENABLED
    )
    SwitchPreference(
        checked = isAiTranslationEnabled,
        onCheckedChange = { isAiTranslationEnabled = it },
        title = stringResource(R.string.item_translation_ai_enable),
        startAction = { IconActions(painterResource(R.drawable.translate_24px)) },
    )

    var isAiTranslationAutoIgnoreChinese by rememberBooleanPreference(
        sharedPreferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_IGNORE_CHINESE,
        defaultValue = TextStyle.Defaults.AI_TRANSLATION_IGNORE_CHINESE
    )
    SwitchPreference(
        checked = isAiTranslationAutoIgnoreChinese,
        onCheckedChange = { isAiTranslationAutoIgnoreChinese = it },
        title = stringResource(R.string.item_translation_auto_ignore_chinese),
        summary = stringResource(R.string.item_translation_auto_ignore_chinese_summary),
        startAction = { IconActions(painterResource(R.drawable.translate_24px)) },
    )

    TranslationTargetLanguagePreference(preferences)

    StringInputPreference(
        preferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_PROMPT,
        title = stringResource(R.string.item_translation_custom_prompt),
        dialogSummary = stringResource(R.string.dialog_summary_translation_custom_prompt),
        defaultValue = TextStyle.Defaults.AI_TRANSLATION_PROMPT,
        startAction = { IconActions(painterResource(R.drawable.title_24px)) },
    )

    ClearTranslationDB()
}

@Composable
private fun ClearTranslationDB() {
    val showDialog = remember { mutableStateOf(false) }
    OverlayDialog(
        title = stringResource(R.string.alert_dialog_title_translation_clear),
        summary = stringResource(R.string.alert_dialog_message_translation_clear),
        show = showDialog.value,
        onDismissRequest = { showDialog.value = false }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = stringResource(id = R.string.cancel),
                onClick = { showDialog.value = false },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(20.dp))
            TextButton(
                colors = ButtonDefaults.textButtonColorsPrimary(),
                text = stringResource(id = R.string.yes),
                onClick = {
                    showDialog.value = false
                    LyriconBridge.with(LyriconApp.get())
                        .to(PackageNames.SYSTEM_UI)
                        .key(AppBridgeConstants.REQUEST_CLEAR_TRANSLATION_DB)
                        .send()
                },
                modifier = Modifier.weight(1f),
            )
        }

    }
    ArrowPreferenceCompat(
        title = stringResource(R.string.item_translation_clear_db),
        startAction = { IconActions(painterResource(R.drawable.ic_settings_backup_restore)) },
        onClick = {
            showDialog.value = true
        }
    )
}

@Composable
private fun ArrowPreferenceCompat(
    title: String,
    startAction: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    top.yukonga.miuix.kmp.preference.ArrowPreference(
        title = title,
        startAction = startAction,
        onClick = onClick,
    )
}

@Composable
private fun TranslationTargetLanguagePreference(preferences: SharedPreferences) {
    val targetLanguageName = TextStyle.Defaults.AI_TRANSLATION_TARGET_LANGUAGE_DISPLAY_NAME
    var showLanguageSheet by remember { mutableStateOf(false) }
    @Suppress("VariableNeverRead") var targetLanguage by rememberStringPreference(
        preferences,
        TextStyle.KEY_AI_TRANSLATION_TARGET_LANGUAGE,
        targetLanguageName
    )
    var targetLanguageCode by rememberStringPreference(
        preferences,
        TextStyle.KEY_AI_TRANSLATION_TARGET_LANGUAGE_CODE,
        ""
    )

    StringInputPreference(
        preferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_TARGET_LANGUAGE,
        defaultValue = targetLanguageName,
        title = stringResource(R.string.item_translation_target_language),
        dialogSummary = stringResource(R.string.dialog_summary_translation_target_language),
        startAction = { IconActions(painterResource(R.drawable.ic_language)) },
        endActions = {
            IconButton(onClick = { showLanguageSheet = true }) {
                Icon(
                    painter = painterResource(R.drawable.list_24px),
                    contentDescription = stringResource(R.string.dialog_title_translation_languages)
                )
            }
        }
    )

    if (showLanguageSheet) {
        val displayLocale = LocalLocale.current.platformLocale
        val languageGroups =
            remember(displayLocale) { buildTranslationLanguageGroups(displayLocale) }
        var query by remember { mutableStateOf("") }
        var selectedLanguageCode by remember { mutableStateOf<String?>(null) }
        val filteredLanguageGroups = remember(languageGroups, query) {
            filterTranslationLanguageGroups(languageGroups, query)
        }
        val selectedLanguageGroup = remember(languageGroups, selectedLanguageCode) {
            languageGroups.firstOrNull { it.languageCode == selectedLanguageCode }
        }

        var isExpanded by remember { mutableStateOf(false) }

        WindowBottomSheet(
            show = showLanguageSheet,
            title = stringResource(R.string.dialog_title_translation_languages),
            onDismissRequest = { showLanguageSheet = false },
            backgroundColor = MiuixTheme.colorScheme.surface,
            insideMargin = DpSize(0.dp, 0.dp),
            enableNestedScroll = false
        ) {
            val dismiss = LocalDismissState.current

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {

                SearchBar(
                    inputField = {
                        InputField(
                            query = query,
                            onQueryChange = { query = it },
                            label = stringResource(R.string.hint_search),
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 0.dp)
                                .fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                                    imageVector = MiuixIcons.Search,
                                    contentDescription = stringResource(R.string.action_search),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            },
                            onSearch = {
                                isExpanded = false
                            },
                            expanded = isExpanded,
                            onExpandedChange = {
                                isExpanded = it
                            },
                        )
                    },
                    onExpandedChange = {
                        isExpanded = it
                    },
                ) {}
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .overScrollVertical()
                ) {


                    if (query.isBlank()) {
                        itemsIndexed(
                            items = languageGroups,
                            key = { _, group -> "language_${group.languageCode}" }
                        ) { _, group ->
                            Card(
                                modifier = Modifier
                                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                                    .fillMaxWidth()
                            ) {
                                top.yukonga.miuix.kmp.preference.ArrowPreference(
                                    title = group.name,
                                    summary = group.options
                                        .drop(1)
                                        .take(3)
                                        .joinToString(" / ") { it.name }
                                        .takeIf { it.isNotBlank() },
                                    onClick = { selectedLanguageCode = group.languageCode }
                                )
                            }
                        }
                    } else {
                        filteredLanguageGroups.forEach { group ->
                            item(key = "language_group_${group.languageCode}") {
                                SmallTitle(
                                    text = group.name,
                                    insideMargin = PaddingValues(
                                        start = 26.dp,
                                        end = 26.dp,
                                        bottom = 10.dp
                                    )
                                )
                            }

                            itemsIndexed(
                                items = group.options,
                                key = { _, option -> option.code }
                            ) { _, option ->
                                TranslationLanguageOptionPreference(
                                    option = option,
                                    checked = targetLanguageCode == option.code,
                                    onClick = {
                                        targetLanguage = option.applyLanguage
                                        targetLanguageCode = option.code
                                        dismiss?.invoke()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedLanguageGroup != null) {
            WindowBottomSheet(
                show = true,
                title = selectedLanguageGroup.name,
                onDismissRequest = { selectedLanguageCode = null },
                backgroundColor = MiuixTheme.colorScheme.surface,
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                val dismiss = LocalDismissState.current
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .overScrollVertical()
                ) {
                    itemsIndexed(
                        items = selectedLanguageGroup.options,
                        key = { _, option -> option.code }
                    ) { _, option ->
                        TranslationLanguageOptionPreference(
                            option = option,
                            checked = targetLanguageCode == option.code,
                            onClick = {
                                targetLanguage = option.applyLanguage
                                targetLanguageCode = option.code
                                selectedLanguageCode = null
                                dismiss?.invoke()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationLanguageOptionPreference(
    option: TranslationLanguageOption,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
            .fillMaxWidth()
    ) {
        CheckboxPreference(
            title = option.name,
            checked = checked,
            onCheckedChange = { onClick() }
        )
    }
}

private fun buildTranslationLanguageGroups(displayLocale: Locale): List<TranslationLanguageGroup> {
    val collator = Collator.getInstance(displayLocale)
    return systemLanguageTags()
        .asSequence()
        .map { Locale.forLanguageTag(it) }
        .filter { locale -> locale.language.isNotBlank() && locale.language != "und" }
        .distinctBy { it.toLanguageTag() }
        .map { locale ->
            val languageName = locale.getDisplayLanguage(displayLocale).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString()
            }
            val name = locale.getDisplayName(displayLocale).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString()
            }
            val nativeName = locale.getDisplayName(locale).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(locale) else it.toString()
            }
            val englishName = locale.getDisplayName(Locale.ENGLISH).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
            }
            TranslationLanguageOption(
                code = locale.toLanguageTag(),
                languageCode = locale.language,
                languageName = languageName,
                name = name,
                nativeName = nativeName.takeIf { it.isNotBlank() && it != name },
                englishName = englishName.takeIf { it.isNotBlank() && it != name }
            )
        }
        .groupBy { it.languageCode }
        .map { (languageCode, options) ->
            TranslationLanguageGroup(
                languageCode = languageCode,
                name = options.first().languageName,
                options = options.sortedWith { left, right ->
                    collator.compare(
                        left.name,
                        right.name
                    )
                }
            )
        }
        .sortedWith { left, right -> collator.compare(left.name, right.name) }
        .toList()
}

private fun systemLanguageTags(): List<String> {
    val systemLocaleTags = Locale.getAvailableLocales()
        .map { it.toLanguageTag() }
        .asSequence()
        .map { it.replace('_', '-') }
        .filter { it.isNotBlank() }
        .toList()

    return systemLocaleTags
}

private fun filterTranslationLanguageGroups(
    groups: List<TranslationLanguageGroup>,
    query: String
): List<TranslationLanguageGroup> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isBlank()) return groups

    return groups.mapNotNull { group ->
        if (group.searchText.contains(normalizedQuery)) {
            group
        } else {
            val options = group.options.filter { it.searchText.contains(normalizedQuery) }
            if (options.isEmpty()) null else group.copy(options = options)
        }
    }
}

private data class TranslationLanguageGroup(
    val languageCode: String,
    val name: String,
    val options: List<TranslationLanguageOption>,
) {
    val searchText: String = listOf(languageCode, name)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
}

private data class TranslationLanguageOption(
    val code: String,
    val languageCode: String,
    val languageName: String,
    val name: String,
    val nativeName: String?,
    val englishName: String?,
) {

    val applyLanguage = name

    val searchText: String =
        listOfNotNull(code, languageCode, languageName, name, nativeName, englishName)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
}
