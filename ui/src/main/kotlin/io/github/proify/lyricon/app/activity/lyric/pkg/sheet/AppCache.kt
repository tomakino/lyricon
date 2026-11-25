/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.sheet

import android.graphics.drawable.Drawable
import java.util.WeakHashMap

object AppCache {
    private val iconCache = WeakHashMap<String, Drawable>()
    private val labelCache = WeakHashMap<String, String>()

    fun getCachedIcon(packageName: String): Drawable? = synchronized(iconCache) {
        iconCache[packageName]
    }

    fun cacheIcon(packageName: String, icon: Drawable?): Any? = synchronized(iconCache) {
        if (icon != null) iconCache[packageName] = icon else iconCache.remove(packageName)
    }

    fun getCachedLabel(packageName: String): String? = synchronized(labelCache) {
        labelCache[packageName]
    }

    fun cacheLabel(packageName: String, label: String): Unit = synchronized(labelCache) {
        labelCache[packageName] = label
    }

    fun clearLabelCache() {
        labelCache.clear()
    }
}