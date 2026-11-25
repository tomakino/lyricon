/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.sheet

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AsyncAppIcon(application: ApplicationInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val packageName = application.packageName

    var icon by remember(packageName) {
        mutableStateOf<Drawable?>(null)
    }

    LaunchedEffect(packageName) {
        val cached = AppCache.getCachedIcon(packageName)
        if (cached != null) {
            icon = cached
            return@LaunchedEffect
        }

        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                packageManager.getApplicationIcon(application)
            }.getOrNull()
        }

        loaded?.let {
            AppCache.cacheIcon(application.packageName, it)
            icon = it
        }
    }

    if (icon != null) {
        Image(
            painter = rememberDrawablePainter(icon),
            contentDescription = packageName,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                color = MiuixTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
        )
    }
}