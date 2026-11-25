/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.provider

import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.icon.GeminiColor

object ModuleHelper {
    private val TAG_MAPPING: Map<String, ModuleTag> by lazy {
        mapOf(
            $$"$syllable" to ModuleTag( // 假设 $$"$syllable" 是 "\$syllable" 的意图
                imageVector = GeminiColor,
                titleRes = R.string.module_tag_syllable,
                isRainbow = true
            ),
            $$"$translation" to ModuleTag(
                iconRes = R.drawable.translate_24px,
                titleRes = R.string.module_tag_translation
            )
        )
    }

    fun getPredefinedTag(key: String): ModuleTag? = TAG_MAPPING[key]

    fun categorizeModules(
        modules: List<LyricModule>,
        defaultCategoryName: String
    ): List<ModuleCategory> {
        if (modules.isEmpty()) return emptyList()

        val grouped = modules.groupBy { it.category ?: defaultCategoryName }

        // 如果只有一个分类且是默认分类，则不显示标题（name为空）
        if (grouped.size == 1 && grouped.containsKey(defaultCategoryName)) {
            return listOf(ModuleCategory("", grouped[defaultCategoryName]!!))
        }

        return grouped.map { (name, items) -> ModuleCategory(name, items) }
    }
}
