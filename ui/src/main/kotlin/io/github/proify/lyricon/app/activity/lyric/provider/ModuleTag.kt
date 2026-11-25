/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.provider

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

data class ModuleTag(
    val iconRes: Int? = null,
    val imageVector: ImageVector? = null,
    val title: String? = null,
    @field:StringRes val titleRes: Int = -1,
    val isRainbow: Boolean = false
)
