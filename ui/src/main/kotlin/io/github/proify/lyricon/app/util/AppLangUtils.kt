/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.activity.lyric.pkg.sheet.AppCache
import java.util.Locale

object AppLangUtils {
    private const val KEY_LANGUAGE = "language"
    const val DEFAULT_LANGUAGE: String = "system"

    @SuppressLint("ConstantLocale")
    val DEFAULT_LOCALE: Locale = Locale.getDefault()

    fun wrapContext(context: Context): Context =
        wrapContext(context, getCustomizeLang(context))

    fun setDefaultLocale(context: Context) {
        val language = getCustomizeLang(context)
        val locale = forLanguageTag(language)
        Locale.setDefault(locale)
    }

    fun getLocale(context: Context = LyriconApp.instance): Locale =
        forLanguageTag(getCustomizeLang(context))

    private fun forLanguageTag(language: String): Locale {
        return if (language == DEFAULT_LANGUAGE) {
            DEFAULT_LOCALE
        } else runCatching {
            Locale.forLanguageTag(language)
        }.getOrDefault(DEFAULT_LOCALE)
    }

    @SuppressLint("AppBundleLocaleChanges")
    private fun wrapContext(context: Context, language: String): Context {
        val locale = forLanguageTag(language)

        val config = context.resources.configuration
        config.setLocale(locale)

        val newContext = context.createConfigurationContext(config)
        return Cold(newContext)
    }

    fun getCustomizeLang(context: Context): String =
        context.defaultSharedPreferences
            .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE)
            ?: DEFAULT_LANGUAGE

    fun saveCustomizeLanguage(context: Context, language: String) {
        context.defaultSharedPreferences
            .editCommit {
                putString(KEY_LANGUAGE, language)
            }
        AppCache.clearLabelCache()
    }

    class Cold(base: Context) : ContextWrapper(base)
}