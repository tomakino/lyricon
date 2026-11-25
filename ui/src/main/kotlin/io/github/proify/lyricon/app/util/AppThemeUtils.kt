/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.content.Context

object AppThemeUtils {
    const val MODE_SYSTEM: Int = 0
    const val MODE_LIGHT: Int = 1
    const val MODE_DARK: Int = 2

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_MONET_COLOR = "theme_monet_color"
    private var isEnableMonetColor: Boolean? = null

    fun getMode(context: Context): Int =
        context.defaultSharedPreferences.getInt(KEY_THEME_MODE, MODE_SYSTEM)

    fun setMode(context: Context, mode: Int) {
        context.defaultSharedPreferences.editCommit { putInt(KEY_THEME_MODE, mode) }
    }

    fun isEnableMonet(context: Context): Boolean {
        if (isEnableMonetColor == null) {
            isEnableMonetColor = context.defaultSharedPreferences.getBoolean(
                KEY_MONET_COLOR,
                false
            )
        }
        return isEnableMonetColor!!
    }

    fun setEnableMonet(context: Context, enable: Boolean) {
        context.defaultSharedPreferences.editCommit {
            putBoolean(KEY_MONET_COLOR, enable)
            isEnableMonetColor = enable
        }
    }
}