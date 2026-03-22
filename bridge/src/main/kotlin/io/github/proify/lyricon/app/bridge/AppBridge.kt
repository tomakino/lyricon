/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.app.bridge

import android.content.Context
import androidx.annotation.Keep
import com.highcapable.yukihookapi.YukiHookAPI
import io.github.proify.lyricon.common.Constants
import java.io.File
import android.os.Build

object AppBridge {

    fun isModuleActive(): Boolean =
        runCatching {
            YukiHookAPI.Status.isXposedModuleActive
        }.getOrDefault(false)

    @Keep
    fun getFrameworkInfo(): FrameworkInfo? = null

    @Keep
    fun getPreferenceDirectory(context: Context): File {
        val baseContext =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
        val dataDir =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                baseContext.dataDir
            } else {
                File(baseContext.applicationInfo.dataDir)
            }
        return File(dataDir, "shared_prefs")
    }

    object LyricStylePrefs {
        const val DEFAULT_PACKAGE_NAME: String = Constants.APP_PACKAGE_NAME
        const val PREF_NAME_BASE_STYLE: String = "baseLyricStyle"
        const val PREF_PACKAGE_STYLE_MANAGER: String = "packageStyleManager"
        const val KEY_ENABLED_PACKAGES: String = "enables"

        fun getPackageStylePreferenceName(packageName: String): String =
            "package_style_${packageName.replace(".", "_")}"
    }
}
