/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.bridge

import androidx.annotation.Keep
import io.github.proify.lyricon.common.Constants

object AppBridge {

    @Keep
    fun isActive(): Boolean = false

    const val PREF_NAME_APP_SETTINGS: String = "app_settings"

    object LyricStylePrefs {
        const val LYRIC_STYLE_PREF_NAME_PREIFY: String = "lyricon_style_"

        const val DEFAULT_PACKAGE_NAME: String = Constants.APP_PACKAGE_NAME

        const val PREF_NAME_BASE: String = LYRIC_STYLE_PREF_NAME_PREIFY + "base"
        const val PREF_NAME_PACKAGE_MANAGER: String =
            LYRIC_STYLE_PREF_NAME_PREIFY + "package_manager"

        const val KEY_LYRIC_ENABLED_PACKAGES: String = "enables"
        const val KEY_LYRIC_CONFIGURED_PACKAGES: String = "configured"

        fun getPackageStylePrefName(packageName: String): String {
            val prefix = LYRIC_STYLE_PREF_NAME_PREIFY + "app_"
            return prefix + (packageName.replace(".", "_"))
        }

    }

    /**
     * 状态栏歌词手势控制配置 (Status Bar Lyric Gesture Control)
     *
     * 存储于基础样式偏好 (lyricon_style_base) 中,App 端写入,Xposed 端读取。
     * 四种手势(左滑/右滑/单击/长按)均可独立配置动作。
     */
    object LyricGesturePrefs {

        /* ---------- 偏好键 ---------- */

        /** 是否启用手势控制 */
        const val KEY_ENABLED: String = "lyric_style_base_gesture_enable"

        /** 左滑 */
        const val KEY_SWIPE_LEFT: String = "lyric_style_base_gesture_swipe_left"

        /** 右滑 */
        const val KEY_SWIPE_RIGHT: String = "lyric_style_base_gesture_swipe_right"

        /** 单击 */
        const val KEY_TAP: String = "lyric_style_base_gesture_tap"

        /** 长按 */
        const val KEY_LONG_PRESS: String = "lyric_style_base_gesture_long_press"

        /** 是否启用震动反馈 */
        const val KEY_HAPTIC: String = "lyric_style_base_gesture_haptic"

        /* ---------- 动作定义 ---------- */

        /** 无动作 */
        const val ACTION_NONE: Int = 0

        /** 播放 / 暂停 */
        const val ACTION_TOGGLE_PLAY: Int = 1

        /** 上一曲 */
        const val ACTION_PREVIOUS: Int = 2

        /** 下一曲 */
        const val ACTION_NEXT: Int = 3

        /** 打开控制面板 */
        const val ACTION_OPEN_CONTROL: Int = 4

        /* ---------- 默认值 ---------- */

        const val DEFAULT_ENABLED: Boolean = true
        const val DEFAULT_HAPTIC: Boolean = true
        const val DEFAULT_SWIPE_LEFT: Int = ACTION_NEXT
        const val DEFAULT_SWIPE_RIGHT: Int = ACTION_PREVIOUS
        const val DEFAULT_TAP: Int = ACTION_OPEN_CONTROL
        const val DEFAULT_LONG_PRESS: Int = ACTION_TOGGLE_PLAY
    }

}