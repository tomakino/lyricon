/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.graphics.Color
import io.github.proify.android.extensions.setColorAlpha

/**
 * 状态栏配色快照：歌词文本、Logo 与间奏进度弧的取色依据。
 *
 * [color] 与 [translucentColor] 使用数组是为了支持从封面提取的多色渐变。
 *
 * 注意：本类虽然是 `data class`，但字段为 `IntArray`，`copy()` 是浅拷贝、数组仍共享
 * 引用。因此修改配色请通过 [StatusBarLyric.updateStatusColor]，不要自行复制实例。
 *
 * @property color            主色，至少应包含一个颜色；渐变场景按顺序展开
 * @property translucentColor 半透明变体，用作高亮背景
 * @property darkIntensity    状态栏明暗强度：0 表示浅色背景，1 表示深色背景
 */
data class StatusColor(
    var color: IntArray = intArrayOf(Color.BLACK),
    var translucentColor: IntArray = intArrayOf(Color.BLACK.setColorAlpha(0.5f)),
    var darkIntensity: Float = 0f,
) {

    /** 是否为浅色状态栏（即需要深色文字）。 */
    val isLightMode: Boolean get() = darkIntensity < 0.5f

    /** 取主色中的第一个颜色；数组为空时回退到 [fallback]。 */
    fun firstColor(fallback: Int = Color.BLACK): Int = color.firstOrNull() ?: fallback

    // IntArray 默认按引用比较，这里必须手写为内容比较：
    // 上层（Xposed 状态栏颜色监听）依赖 equals 做"颜色是否变化"的去重判断。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StatusColor

        if (darkIntensity != other.darkIntensity) return false
        if (!color.contentEquals(other.color)) return false
        if (!translucentColor.contentEquals(other.translucentColor)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = darkIntensity.hashCode()
        result = 31 * result + color.contentHashCode()
        result = 31 * result + translucentColor.contentHashCode()
        return result
    }
}
