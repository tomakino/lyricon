/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.content.Context
import android.content.SharedPreferences
import io.github.proify.android.extensions.fromJson
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.toJson
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.bridge.AppBridge.LyricStylePrefs
import io.github.proify.lyricon.app.bridge.AppBridge.LyricStylePrefs.KEY_LYRIC_CONFIGURED_PACKAGES
import io.github.proify.lyricon.app.bridge.AppBridge.LyricStylePrefs.KEY_LYRIC_ENABLED_PACKAGES
import io.github.proify.lyricon.lyric.style.VisibilityRule

object AppPrefs {

    const val DEFAULT_PACKAGE_NAME: String = LyricStylePrefs.DEFAULT_PACKAGE_NAME

    private val lyricPackageStyleManager: SharedPreferences by lazy {
        getSharedPreferences(LyricStylePrefs.PREF_NAME_PACKAGE_MANAGER)
    }

    val lyricBasicStylePrefs: SharedPreferences
        get() = getSharedPreferences(LyricStylePrefs.PREF_NAME_BASE)

    fun getSharedPreferences(name: String): SharedPreferences {
        return getSharedPreferences(LyriconApp.get(), name)
    }

    fun getSharedPreferences(context: Context, name: String): SharedPreferences {
        val xposedService = LyriconApp.xposedService
        if (xposedService != null) {
            return xposedService.getRemotePreferences(name)
        }
        return context.getPrivateSharedPreferences(name)
    }

    fun isLyricStylePrefName(name: String): Boolean {
        return name.startsWith(LyricStylePrefs.LYRIC_STYLE_PREF_NAME_PREIFY)
    }

    fun getLyricStylePrefNames() = mutableListOf<String>().apply {
        add(LyricStylePrefs.PREF_NAME_BASE)
        add(LyricStylePrefs.PREF_NAME_PACKAGE_MANAGER)

        getLyricConfiguredPackageNames().forEach {
            add(getLyricPackagePrefName(it))
        }
    }

    fun getLyricPackagePrefName(packageName: String): String =
        LyricStylePrefs.getPackageStylePrefName(packageName)

    fun setEnabledLyricPackageNames(names: Set<String>) {
        lyricPackageStyleManager.editCommit {
            putStringSet(KEY_LYRIC_ENABLED_PACKAGES, names)
        }
    }

    fun getEnabledLyricPackageNames(): Set<String> {
        return lyricPackageStyleManager
            .getStringSet(KEY_LYRIC_ENABLED_PACKAGES, null)?.toSet() ?: emptySet()
    }

    fun setConfiguredPackageNames(names: Set<String>) {
        lyricPackageStyleManager.editCommit {
            putString(KEY_LYRIC_CONFIGURED_PACKAGES, names.toJson())
        }
    }

    fun getLyricConfiguredPackageNames(): Set<String> {
        val jsonData = lyricPackageStyleManager.getString(KEY_LYRIC_CONFIGURED_PACKAGES, null)
        return json.safeDecode<List<String>>(jsonData).toSet()
    }

    fun setLyricViewVisibilityRule(rules: List<VisibilityRule>?) {
        lyricBasicStylePrefs.editCommit {
            if (rules.isNullOrEmpty()) {
                remove("lyric_style_base_visibility_rules")
            } else {
                putString("lyric_style_base_visibility_rules", rules.toJson())
            }
        }
    }

    fun getViewVisibilityRule(): List<VisibilityRule> {
        val json = lyricBasicStylePrefs.getString("lyric_style_base_visibility_rules", null)
        return json?.fromJson<List<VisibilityRule>>() ?: emptyList()
    }
}