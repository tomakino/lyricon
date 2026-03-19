/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.xposed.systemui.util

import de.robv.android.xposed.XSharedPreferences
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.PackageStyle

object LyricPrefs {
    data class TranslationSettings(
        val enabled: Boolean,
        val provider: String,
        val targetLanguage: String,
        val apiKey: String,
        val model: String,
        val baseUrl: String,
        val maxCacheSize: Int,
        val ignoreRegex: String,
        val customPrompt: String
    ) {
        val isUsable: Boolean
            get() = enabled
                    && provider in SUPPORTED_TRANSLATION_PROVIDERS
                    && apiKey.isNotBlank()
    }

    private const val KEY_TRANSLATION_ENABLED = "lyric_translation_enabled"
    private const val KEY_TRANSLATION_PROVIDER = "lyric_translation_api_provider"
    private const val KEY_TRANSLATION_TARGET_LANGUAGE = "lyric_translation_target_language"
    private const val KEY_TRANSLATION_OPENAI_API_KEY = "lyric_translation_openai_api_key"
    private const val KEY_TRANSLATION_OPENAI_MODEL = "lyric_translation_openai_model"
    private const val KEY_TRANSLATION_OPENAI_BASE_URL = "lyric_translation_openai_base_url"
    private const val KEY_TRANSLATION_CACHE_SIZE = "lyric_translation_cache_size"
    private const val KEY_TRANSLATION_IGNORE_REGEX = "lyric_translation_ignore_regex"
    private const val KEY_TRANSLATION_CUSTOM_PROMPT = "lyric_translation_custom_prompt"
    private const val KEY_TEXT_TRANSLATION_ONLY = "lyric_style_text_translation_only"
    private const val KEY_TEXT_HIDE_TRANSLATION = "lyric_style_text_hide_translation"

    const val TRANSLATION_PROVIDER_OPENAI = "openai"
    const val TRANSLATION_PROVIDER_GEMINI = "gemini"
    const val TRANSLATION_PROVIDER_CLAUDE = "claude"
    const val TRANSLATION_PROVIDER_DEEPSEEK = "deepseek"
    const val TRANSLATION_PROVIDER_QWEN = "qwen"

    val SUPPORTED_TRANSLATION_PROVIDERS: Set<String> = setOf(
        TRANSLATION_PROVIDER_OPENAI,
        TRANSLATION_PROVIDER_GEMINI,
        TRANSLATION_PROVIDER_CLAUDE,
        TRANSLATION_PROVIDER_DEEPSEEK,
        TRANSLATION_PROVIDER_QWEN
    )

    private const val DEFAULT_TRANSLATION_TARGET_LANGUAGE = "简体中文"
    private const val DEFAULT_TRANSLATION_OPENAI_MODEL = "gpt-4o-mini"
    private const val DEFAULT_TRANSLATION_GEMINI_MODEL = "gemini-2.0-flash"
    private const val DEFAULT_TRANSLATION_CLAUDE_MODEL = "claude-3-5-haiku-latest"
    private const val DEFAULT_TRANSLATION_DEEPSEEK_MODEL = "deepseek-chat"
    private const val DEFAULT_TRANSLATION_QWEN_MODEL = "qwen-plus"

    private const val DEFAULT_TRANSLATION_OPENAI_BASE_URL = "https://api.openai.com/v1/chat/completions"
    private const val DEFAULT_TRANSLATION_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val DEFAULT_TRANSLATION_CLAUDE_BASE_URL = "https://api.anthropic.com/v1/messages"
    private const val DEFAULT_TRANSLATION_DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1/chat/completions"
    private const val DEFAULT_TRANSLATION_QWEN_BASE_URL =
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

    private const val DEFAULT_TRANSLATION_CACHE_SIZE = 5000
    private const val DEFAULT_TRANSLATION_IGNORE_REGEX = "^[\\p{Han}\\p{P}\\s]+$"

    private val prefsCache = mutableMapOf<String, XSharedPreferences>()
    private val packageStyleCache = mutableMapOf<String, PackageStyleCache>()

    @Volatile
    var activePackageName: String? = null

    /* ---------------- base style ---------------- */

    private val baseStylePrefs: XSharedPreferences =
        createXPrefs(AppBridge.LyricStylePrefs.PREF_NAME_BASE_STYLE)

    val baseStyle: BasicStyle = BasicStyle().apply {
        load(baseStylePrefs)
    }
        get() {
            if (baseStylePrefs.hasFileChanged()) {
                baseStylePrefs.reload()
                field.load(baseStylePrefs)
            }
            return field
        }

    /* ---------------- default package style ---------------- */

    private val defaultPackageStylePrefs: XSharedPreferences by lazy {
        getPackagePrefs(
            AppBridge.LyricStylePrefs.DEFAULT_PACKAGE_NAME
        )
    }

    val defaultPackageStyle: PackageStyle = PackageStyle().apply {
        load(defaultPackageStylePrefs)
    }
        get() {
            if (defaultPackageStylePrefs.hasFileChanged()) {
                defaultPackageStylePrefs.reload()
                field.load(defaultPackageStylePrefs)
            }
            return field
        }

    /* ---------------- package manager ---------------- */

    private val packageStyleManagerPrefs: XSharedPreferences =
        createXPrefs(AppBridge.LyricStylePrefs.PREF_PACKAGE_STYLE_MANAGER)
        get() {
            if (field.hasFileChanged()) {
                field.reload()
            }
            return field
        }

    fun getActivePackageStyle(): PackageStyle {
        val pkg = activePackageName
        return if (pkg != null && isPackageEnabled(pkg)) {
            getPackageStyle(pkg)
        } else {
            defaultPackageStyle
        }
    }

    private fun isPackageEnabled(packageName: String): Boolean {
        packageStyleManagerPrefs.ensureLatest()
        return runCatching {
            packageStyleManagerPrefs
                .getStringSet(
                    AppBridge.LyricStylePrefs.KEY_ENABLED_PACKAGES,
                    emptySet()
                )
                ?.contains(packageName) ?: false
        }.getOrDefault(false)
    }

    /* ---------------- prefs cache ---------------- */


    private fun getPackagePrefName(packageName: String): String =
        AppBridge.LyricStylePrefs.getPackageStylePreferenceName(packageName)

    private fun getPackagePrefs(packageName: String): XSharedPreferences {
        val prefName = getPackagePrefName(packageName)
        return prefsCache.getOrPut(prefName) {
            createXPrefs(prefName)
        }
    }

    private fun createXPrefs(name: String): XSharedPreferences {
        return XSharedPreferences(PackageNames.APPLICATION, name)
    }

    private fun XSharedPreferences.ensureLatest(): XSharedPreferences {
        runCatching { reload() }
        return this
    }

    /* ---------------- package style cache ---------------- */

    private class PackageStyleCache(
        private val prefs: XSharedPreferences,
        private val style: PackageStyle
    ) {
        fun getStyle(): PackageStyle {
            if (prefs.hasFileChanged()) {
                prefs.reload()
                style.load(prefs)
            }
            return style
        }
    }

    fun getPackageStyle(packageName: String): PackageStyle {
        return packageStyleCache.getOrPut(packageName) {
            val prefs = getPackagePrefs(packageName)
            val style = PackageStyle().apply {
                load(prefs)
            }
            PackageStyleCache(prefs, style)
        }.getStyle()
    }

    /* ---------------- lyric style ---------------- */

    fun getLyricStyle(packageName: String? = null): LyricStyle {
        if (packageName == null) {
            return LyricStyle(baseStyle, getActivePackageStyle())
        }
        return LyricStyle(
            baseStyle,
            getPackageStyle(packageName)
        )
    }

    private fun getActivePackagePrefsForConfig(): XSharedPreferences {
        val pkg = activePackageName
        return if (pkg != null && isPackageEnabled(pkg)) {
            getPackagePrefs(pkg)
        } else {
            defaultPackageStylePrefs
        }
    }

    private fun readConfigStringWithFallback(
        activePrefs: XSharedPreferences,
        key: String,
        fallback: String
    ): String {
        return activePrefs.getString(key, null)
            ?: defaultPackageStylePrefs.getString(key, fallback)
            ?: fallback
    }

    private fun readConfigBooleanWithFallback(
        activePrefs: XSharedPreferences,
        key: String,
        fallback: Boolean
    ): Boolean {
        return when {
            activePrefs.contains(key) -> activePrefs.getBoolean(key, fallback)
            defaultPackageStylePrefs.contains(key) -> defaultPackageStylePrefs.getBoolean(key, fallback)
            else -> fallback
        }
    }

    fun isHideTranslationInLyricEnabled(): Boolean {
        val activePrefs = getActivePackagePrefsForConfig().ensureLatest()
        defaultPackageStylePrefs.ensureLatest()
        return readConfigBooleanWithFallback(
            activePrefs = activePrefs,
            key = KEY_TEXT_HIDE_TRANSLATION,
            fallback = false
        )
    }

    fun isTranslationOnlyInLyricEnabled(): Boolean {
        if (isHideTranslationInLyricEnabled()) return false
        val activePrefs = getActivePackagePrefsForConfig().ensureLatest()
        defaultPackageStylePrefs.ensureLatest()
        return readConfigBooleanWithFallback(
            activePrefs = activePrefs,
            key = KEY_TEXT_TRANSLATION_ONLY,
            fallback = false
        )
    }

    fun getActiveTranslationSettings(): TranslationSettings {
        val activePrefs = getActivePackagePrefsForConfig().ensureLatest()
        defaultPackageStylePrefs.ensureLatest()
        val provider = readConfigStringWithFallback(
            activePrefs = activePrefs,
            key = KEY_TRANSLATION_PROVIDER,
            fallback = TRANSLATION_PROVIDER_OPENAI
        )

        val model = readConfigStringWithFallback(
            activePrefs = activePrefs,
            key = KEY_TRANSLATION_OPENAI_MODEL,
            fallback = getDefaultModel(provider)
        )
        val baseUrl = readConfigStringWithFallback(
            activePrefs = activePrefs,
            key = KEY_TRANSLATION_OPENAI_BASE_URL,
            fallback = getDefaultBaseUrl(provider)
        )

        val maxCacheSize = runCatching {
            activePrefs.getString(KEY_TRANSLATION_CACHE_SIZE, null)?.toIntOrNull()
        }.getOrNull() ?: DEFAULT_TRANSLATION_CACHE_SIZE

        val ignoreRegex = readConfigStringWithFallback(
            activePrefs = activePrefs,
            key = KEY_TRANSLATION_IGNORE_REGEX,
            fallback = DEFAULT_TRANSLATION_IGNORE_REGEX
        )

        val customPrompt = readConfigStringWithFallback(
            activePrefs = activePrefs,
            key = KEY_TRANSLATION_CUSTOM_PROMPT,
            fallback = io.github.proify.lyricon.common.Constants.DEFAULT_TRANSLATION_CUSTOM_PROMPT
        )

        return TranslationSettings(
            enabled = activePrefs.getBoolean(
                KEY_TRANSLATION_ENABLED,
                defaultPackageStylePrefs.getBoolean(KEY_TRANSLATION_ENABLED, false)
            ),
            provider = provider,
            targetLanguage = readConfigStringWithFallback(
                activePrefs = activePrefs,
                key = KEY_TRANSLATION_TARGET_LANGUAGE,
                fallback = DEFAULT_TRANSLATION_TARGET_LANGUAGE
            ),
            apiKey = readConfigStringWithFallback(
                activePrefs = activePrefs,
                key = KEY_TRANSLATION_OPENAI_API_KEY,
                fallback = ""
            ),
            model = model,
            baseUrl = baseUrl,
            maxCacheSize = maxCacheSize,
            ignoreRegex = ignoreRegex,
            customPrompt = customPrompt
        )
    }

    fun getDefaultModel(provider: String): String {
        return when (provider) {
            TRANSLATION_PROVIDER_GEMINI -> DEFAULT_TRANSLATION_GEMINI_MODEL
            TRANSLATION_PROVIDER_CLAUDE -> DEFAULT_TRANSLATION_CLAUDE_MODEL
            TRANSLATION_PROVIDER_DEEPSEEK -> DEFAULT_TRANSLATION_DEEPSEEK_MODEL
            TRANSLATION_PROVIDER_QWEN -> DEFAULT_TRANSLATION_QWEN_MODEL
            else -> DEFAULT_TRANSLATION_OPENAI_MODEL
        }
    }

    fun getDefaultBaseUrl(provider: String): String {
        return when (provider) {
            TRANSLATION_PROVIDER_GEMINI -> DEFAULT_TRANSLATION_GEMINI_BASE_URL
            TRANSLATION_PROVIDER_CLAUDE -> DEFAULT_TRANSLATION_CLAUDE_BASE_URL
            TRANSLATION_PROVIDER_DEEPSEEK -> DEFAULT_TRANSLATION_DEEPSEEK_BASE_URL
            TRANSLATION_PROVIDER_QWEN -> DEFAULT_TRANSLATION_QWEN_BASE_URL
            else -> DEFAULT_TRANSLATION_OPENAI_BASE_URL
        }
    }
}
