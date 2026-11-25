/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.core.os.BundleCompat
import io.github.proify.lyricon.app.util.AppPrefs
import io.github.proify.lyricon.lyric.ai.core.AiConfigStore
import io.github.proify.lyricon.lyric.ai.core.AiProfile

/**
 * AI 提供商配置编辑页（独立二级 Activity）。
 *
 * 接收 [EXTRA_PROFILE]（一条 [AiProfile] 草稿）作为参数，编辑页改动先进入本地草稿，
 * 点击「保存」才写回配置集合（新建时自动设为激活）并结束返回；顶部返回/删除同样结束。
 *
 * @author Tomakino
 * @since 2026
 */
class AiConfigEditActivity : AbstractLyricActivity() {

    private val prefs by lazy { AppPrefs.lyricBasicStylePrefs }
    private val profile by lazy {
        BundleCompat.getParcelable(
            intent.extras ?: android.os.Bundle(),
            EXTRA_PROFILE,
            AiProfile::class.java,
        )
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val target = profile
        if (target == null) {
            finish()
            return
        }

        setContent {
            AiConfigEditPage(
                profile = target,
                onSave = { draft ->
                    //val collection = AiConfigStore.load(prefs)
                    //val exists = collection.profiles.any { it.id == draft.id }
                    AiConfigStore.upsert(prefs, draft)
                    //if (!exists) AiConfigStore.activate(prefs, draft.id)
                },
                onDelete = {
                    AiConfigStore.remove(prefs, target.id)
                    finish()
                },
                onBack = { finish() },
            )
        }
    }

    companion object {
        const val EXTRA_PROFILE = "ai_profile"

        fun createIntent(context: Context, profile: AiProfile): Intent =
            Intent(context, AiConfigEditActivity::class.java)
                .putExtra(EXTRA_PROFILE, profile)
    }
}
