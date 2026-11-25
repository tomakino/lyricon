/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.content.Context
import android.content.SharedPreferences
import io.github.proify.lyricon.common.util.safe

fun Context.getPrivateSharedPreferences(name: String): SharedPreferences {
    return getSharedPreferences(name, Context.MODE_PRIVATE).safe()
}

val Context.defaultSharedPreferences: SharedPreferences
    get() = AppPrefs.getSharedPreferences(this, defaultSharedPreferencesName)

val Context.defaultSharedPreferencesName: String get() = packageName + "_preferences"