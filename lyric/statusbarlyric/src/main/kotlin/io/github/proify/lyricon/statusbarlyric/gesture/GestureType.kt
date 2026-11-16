/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.gesture

/**
 * 状态栏歌词支持的手势类型。
 *
 * 由 [LyricTouchController] 在识别成功后于主线程回调，上层（如 Xposed 控制器）再将其
 * 映射为具体动作（切歌、播放/暂停、打开控制面板等）。
 */
enum class GestureType {
    /** 手指向左滑动。 */
    SWIPE_LEFT,

    /** 手指向右滑动。 */
    SWIPE_RIGHT,

    /** 单击。 */
    TAP,

    /** 长按。 */
    LONG_PRESS
}
