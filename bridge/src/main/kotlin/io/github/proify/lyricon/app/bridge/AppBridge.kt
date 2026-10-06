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

    object LyricStylePrefs {
        const val LYRIC_STYLE_PREF_NAME_PREIFY: String = "lyricon_style_"

        const val DEFAULT_PACKAGE_NAME: String = Constants.APP_PACKAGE_NAME

        const val PREF_NAME_BASE: String = LYRIC_STYLE_PREF_NAME_PREIFY + "base"
        const val PREF_NAME_PACKAGE_MANAGER: String =
            LYRIC_STYLE_PREF_NAME_PREIFY + "package_manager"

        const val KEY_ENABLED_PACKAGES: String = "enables"
        const val KEY_CONFIGURED_PACKAGES: String = "configured"

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

        /**
         * 是否启用「根窗口触摸拦截」(StatusBarTouchHooker)
         *
         * ## 这个开关是做什么的
         *
         * 部分系统（如 ColorOS）会在状态栏顶层叠一层容器抢走触摸，
         * 导致**流体云显示时或非播放器界面**下：点击歌词、滑动切歌、展开面板失效。
         *
         * 开启后，模块会挂钩系统状态栏根窗口的 `dispatchTouchEvent`，
         * 在事件到达那层容器之前判断是否点在歌词上：
         * - 点在歌词上 → 由模块直接交给歌词处理；
         * - 竖向滑动（想拉通知栏）→ 原样让渡回系统，不影响下拉通知栏；
         * - 点在别处 → 完全不干预。
         *
         * ## 什么时候需要关掉
         *
         * 这是较底层的改动。若在个别机型上出现状态栏触摸异常
         * （如通知栏拉不下来、状态栏点击无响应），可以关掉本开关，
         * 歌词手势会退回系统的正常派发路径，无需卸载模块。
         */
        const val KEY_ROOT_TOUCH_HOOK: String = "lyric_style_base_gesture_root_touch_hook"

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
        const val DEFAULT_ROOT_TOUCH_HOOK: Boolean = true
        const val DEFAULT_SWIPE_LEFT: Int = ACTION_NEXT
        const val DEFAULT_SWIPE_RIGHT: Int = ACTION_PREVIOUS
        const val DEFAULT_TAP: Int = ACTION_OPEN_CONTROL
        const val DEFAULT_LONG_PRESS: Int = ACTION_TOGGLE_PLAY
    }

}