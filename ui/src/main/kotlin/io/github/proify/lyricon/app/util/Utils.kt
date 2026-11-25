/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.MainActivity
import io.github.proify.lyricon.app.compose.theme.CurrentThemeConfigs
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

object Utils {
    val isOPlus: Boolean by lazy {
        try {
            val pm = LyriconApp.get().packageManager
            pm.getPackageInfo("com.oplus.systemui.plugins", 0) != null
        } catch (_: Exception) {
            false
        }
    }

//    fun forceStop(packageName: String?): ShellUtils.CommandResult =
//        ShellUtils.execCmd(
//            "am force-stop $packageName",
//            isRoot = true,
//            isNeedResultMsg = true,
//        )

    fun killSystemUI(): ShellUtils.CommandResult =
        ShellUtils.execCmd(
            "kill -9 $(pgrep systemui)",
            isRoot = true,
            isNeedResultMsg = true,
        )
}

fun Activity.restartApp() {
    val intent = Intent(
        this,
        MainActivity::class.java
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    startActivity(intent)
    finish()

    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0)
    Process.killProcess(Process.myPid())
}

@Composable
fun Context.LaunchBrowserCompose(
    url: String,
    toolbarColor: Int? = MiuixTheme.colorScheme.surface.toArgb(),
    darkTheme: Boolean = CurrentThemeConfigs.isDark
) {
    launchBrowser(url, toolbarColor, darkTheme)
}

fun Context.launchBrowser(
    url: String,
    toolbarColor: Int? = null,
    darkTheme: Boolean = CurrentThemeConfigs.isDark
) {
    val colorSchemeParamsBuilder = CustomTabColorSchemeParams.Builder()
    if (toolbarColor != null) {
        colorSchemeParamsBuilder.setToolbarColor(toolbarColor)
    }
    val customTabs =
        CustomTabsIntent
            .Builder()
            .setColorScheme(if (darkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .setDefaultColorSchemeParams(colorSchemeParamsBuilder.build())
            .build()
    customTabs.launchUrl(this, url.toUri())
}

fun Context.resolveLanguageName(
    languageCode: String,
    displayLocale: Locale? = null
): String {
    if (languageCode == AppLangUtils.DEFAULT_LANGUAGE) {
        return getString(R.string.option_language_follow_system)
    }
    return runCatching {
        val locale = Locale.forLanguageTag(languageCode)
        locale.getDisplayName(displayLocale ?: locale)
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(locale) else it.toString()
            }
    }.getOrDefault(languageCode)
}

inline fun SharedPreferences.editCommit(action: SharedPreferences.Editor.() -> Unit): Unit =
    edit(commit = true) { action() }