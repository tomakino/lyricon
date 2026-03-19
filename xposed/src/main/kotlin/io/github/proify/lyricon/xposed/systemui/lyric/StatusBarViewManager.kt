/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.lyric

import java.util.concurrent.CopyOnWriteArraySet

object StatusBarViewManager {

    private val _controllers = CopyOnWriteArraySet<StatusBarViewController>()
    val controllers: Set<StatusBarViewController> = _controllers

    fun add(controller: StatusBarViewController) {
        if (_controllers.contains(controller)) return
        _controllers.add(controller)
        controller.onCreate()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    fun remove(controller: StatusBarViewController) {
        if (!_controllers.contains(controller)) return
        _controllers.remove(controller)
        controller.onDestroy()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    inline fun forEach(crossinline block: (StatusBarViewController) -> Unit) {
        controllers.forEach(block)
    }
}
