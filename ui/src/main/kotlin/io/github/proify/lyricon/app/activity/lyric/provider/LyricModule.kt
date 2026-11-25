/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.provider

import android.content.pm.PackageInfo

data class LyricModule(
    val packageInfo: PackageInfo,
    val description: String?,
    val homeUrl: String?,
    val category: String?,
    val author: String?,
    val isCertified: Boolean,
    val tags: List<ModuleTag>,
    val label: String
)