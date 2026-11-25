/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import io.github.proify.lyricon.app.compose.MaterialPalette
import io.github.proify.lyricon.app.util.AppThemeUtils
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.platformDynamicColors

object CurrentThemeConfigs {
    var isDark: Boolean = false
    var primary: Color = Color.Transparent
    var primaryContainer: Color = Color.Transparent
}

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val activity = view.context as? Activity
    val colors = rememberAppColors()
    CurrentThemeConfigs.isDark = colors.isDark

    SideEffect {
        activity?.window?.let { window ->
            WindowInsetsControllerCompat(window, view)
                .isAppearanceLightStatusBars = !colors.isDark
        }
    }

    MiuixTheme(
        colors = colors.colors,
        content = {
            CurrentThemeConfigs.primary = MiuixTheme.colorScheme.primary
            CurrentThemeConfigs.primaryContainer = MiuixTheme.colorScheme.primaryContainer
            content()
        }
    )
}

@Composable
private fun rememberAppColors(): AppColors {
    val context = LocalContext.current
    val dark = resolveDarkMode(context)

    return when {
        AppThemeUtils.isEnableMonet(context) ->
            AppColors(platformDynamicColors(dark), dark)

        dark ->
            AppColors(appDarkColorScheme(), true)

        else ->
            AppColors(appLightColorScheme(), false)
    }
}

@Composable
fun resolveDarkMode(context: android.content.Context): Boolean =
    when (AppThemeUtils.getMode(context)) {
        AppThemeUtils.MODE_LIGHT -> false
        AppThemeUtils.MODE_DARK -> true
        AppThemeUtils.MODE_SYSTEM -> isSystemInDarkTheme()
        else -> isSystemInDarkTheme()
    }

class AppColors(
    val colors: Colors,
    val isDark: Boolean
)

fun appDarkColorScheme(): Colors = darkColorScheme(
    error = MaterialPalette.Red.Primary,
    errorContainer = MaterialPalette.Red.Hue200,
)

fun appLightColorScheme(): Colors {
    val black = Color(0xFF111111)
    return lightColorScheme(
        surface = Color(0xFFF0F1F2),
        onBackground = black,
        onSurface = black,
        onSurfaceContainer = black,
        error = MaterialPalette.Red.Primary,
        errorContainer = MaterialPalette.Red.Hue200,
    )
}