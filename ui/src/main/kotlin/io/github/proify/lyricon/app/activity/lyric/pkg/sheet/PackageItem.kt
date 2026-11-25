/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.sheet

import android.content.pm.ApplicationInfo
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.util.AppPrefs.DEFAULT_PACKAGE_NAME

data class PackageItem(
    val applicationInfo: ApplicationInfo,
) {
    val isDefault: Boolean
        get() = applicationInfo.packageName == DEFAULT_PACKAGE_NAME

    fun getLabel(): String {
        val cached = AppCache.getCachedLabel(applicationInfo.packageName)
        if (cached != null) return cached

        val context = LyriconApp.instance
        val packageManager = context.packageManager
        val label = applicationInfo.loadLabel(packageManager).toString()
        AppCache.cacheLabel(applicationInfo.packageName, label)
        return label
    }
}