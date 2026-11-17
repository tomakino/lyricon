/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.custom.miuix.preference.CheckboxPreference
import io.github.proify.lyricon.app.util.LyricPrefs
import io.github.proify.lyricon.lyric.ai.core.AiConfig
import io.github.proify.lyricon.lyric.ai.core.AiConfigCollection
import io.github.proify.lyricon.lyric.ai.core.AiConfigStore
import io.github.proify.lyricon.lyric.ai.core.AiProfile
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.preference.ArrowPreference
import java.util.UUID

/**
 * AI 实验室 (AI Laboratory)
 *
 * 集中管理所有 AI 功能：
 * - AI 提供商配置：列表单选激活（CheckboxPreference 高亮当前激活项），行尾「编辑」IconButton
 *   启动二级 [AiConfigEditActivity]；支持模板化新建、重命名、删除、测试连接
 * - AI 音乐解读（状态栏控制窗口内触发）
 * - AI 歌词翻译（整首歌级联翻译流水线）
 *
 * @author Tomakino
 * @since 2026
 */
class AiLaboratoryActivity : AbstractLyricActivity() {

    private val preferences by lazy { LyricPrefs.basicStylePrefs }
    private var collection by mutableStateOf(AiConfigCollection())

    override fun onResume() {
        super.onResume()
        reloadCollection()
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        reloadCollection()
        setContent { Content() }
    }

    /**
     * 重新读取配置集合：
     * - 冷启动（onCreate）在 [setContent] 前调用，保证首次组合即拿到最新配置、避免首帧空白闪现；
     * - 从 [AiConfigEditActivity] 返回（onResume）调用，保证编辑结果即时刷新。
     */
    private fun reloadCollection() {
        collection = AiConfigStore.load(preferences)
    }

    @Composable
    private fun Content() {
        val prefs = preferences
        val defaultName = stringResource(R.string.item_ai_config_default_name)

        fun activate(id: String) {
            if (id == collection.activeId) return
            AiConfigStore.activate(prefs, id)
            collection = AiConfigStore.load(prefs)
        }

        fun startEdit(profile: AiProfile) {
            startActivity(AiConfigEditActivity.createIntent(this, profile))
        }

        // ---- 配置列表（单选激活 + 行尾编辑入口） ----
        AppToolBarListContainer(
            title = stringResource(R.string.activity_ai_laboratory),
            canBack = true
        ) {
            item(key = "ai_config_profiles") {
                SmallTitle(
                    text = stringResource(R.string.section_ai_provider),
                    insideMargin = SectionTitlePadding,
                )
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        collection.profiles.forEach { profile ->
                            AiConfigListItem(
                                profile = profile,
                                active = collection.activeId == profile.id,
                                onActivate = { activate(profile.id) },
                                onEdit = { startEdit(profile) },
                            )
                        }

                        ArrowPreference(
                            title = stringResource(R.string.item_ai_config_new),
                            summary = stringResource(R.string.item_ai_config_new_summary),
                            onClick = {
                                startEdit(
                                    AiProfile(
                                        id = UUID.randomUUID().toString(),
                                        name = defaultName,
                                        config = AiConfig(),
                                    )
                                )
                            },
                        )
                    }
                }
            }

            item(key = "ai_explain") {
                SmallTitle(
                    text = stringResource(R.string.section_ai_explain),
                    insideMargin = SectionTitlePadding,
                )
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                        .fillMaxWidth(),
                ) {
                    AiExplainPreference(preferences)
                }
            }

            item(key = "ai_translation") {
                SmallTitle(
                    text = stringResource(R.string.section_translation),
                    insideMargin = SectionTitlePadding,
                )
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                        .fillMaxWidth(),
                ) {
                    AiTranslationPreference(preferences)
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 「AI 实验室」各小节标题的统一内边距（与 [AiConfigEditPage] 的小节标题一致）。 */
private val SectionTitlePadding = PaddingValues(
    start = 26.dp,
    top = 16.dp,
    end = 26.dp,
    bottom = 10.dp,
)

/** 单条配置行：整行点击 = 设为激活（单选）；行尾「编辑」IconButton = 进入编辑页。 */
@Composable
private fun AiConfigListItem(
    profile: AiProfile,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
) {
    CheckboxPreference(
        title = profile.name,
        summary = profile.description?.takeIf { it.isNotBlank() },
        checked = active,
        onCheckedChange = { checked -> if (checked) onActivate() },
        endActions = {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = MiuixIcons.Edit,
                    contentDescription = stringResource(R.string.item_ai_config_edit),
                )
            }
        },
    )
}