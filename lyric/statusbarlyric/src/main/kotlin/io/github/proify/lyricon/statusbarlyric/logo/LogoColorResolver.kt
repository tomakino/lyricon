/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.content.res.ColorStateList

/**
 * Logo 着色解析器。
 *
 * [CustomLogoStrategy] 与 [ProviderLogoStrategy] 的取色链路一致
 * （跟随文字颜色 → 指定颜色 → 状态栏首色），差异只在两处：
 * - "是否彩色图标"的判定来源不同；
 * - 未开启自定义颜色时的回退不同：自定义图标保留原图，Provider 图标回退为状态栏首色。
 *
 * 因此这里提供两个语义化入口共享同一实现，避免两处重复的取色分支各自漂移。
 */
internal object LogoColorResolver {

    /**
     * 解析自定义图标的 Tint。
     *
     * @param view       宿主 Logo 视图，提供样式与状态栏颜色
     * @param isColorful 图标本身是否彩色；彩色图标保持原图，不染色
     * @return 需要应用的 Tint；返回 `null` 表示不染色
     */
    fun resolveCustomLogoColor(view: LyricLogoView, isColorful: Boolean): ColorStateList? =
        resolve(view, isColorful, useStatusColorAsFallback = false)

    /**
     * 解析 Provider 图标的 Tint。
     *
     * Provider 图标通常是单色线条，未配置颜色时也必须跟随状态栏颜色，否则会出现
     * 深底黑图标一类的不可见情况。
     *
     * @param view       宿主 Logo 视图，提供样式与状态栏颜色
     * @param isColorful Provider 声明的彩色图标；彩色图标保持原图，不染色
     * @return 需要应用的 Tint；返回 `null` 表示不染色
     */
    fun resolveProviderLogoColor(view: LyricLogoView, isColorful: Boolean): ColorStateList? =
        resolve(view, isColorful, useStatusColorAsFallback = true)

    // --- 内部实现 ---

    private fun resolve(
        view: LyricLogoView,
        isColorful: Boolean,
        useStatusColorAsFallback: Boolean
    ): ColorStateList? {
        // 彩色图标自行携带配色，任何 Tint 都会破坏原图
        if (isColorful) return null

        val logoStyle = view.lyricStyle?.packageStyle?.logo
        if (logoStyle == null || !logoStyle.enableCustomColor) {
            return if (useStatusColorAsFallback) {
                view.statusColor.firstColor().toColorStateList()
            } else {
                null
            }
        }

        val logoColorConfig = logoStyle.color(view.statusColor.isLightMode)
        val resolvedColor = when {
            logoColorConfig.followTextColor -> resolveFollowTextColor(view)
            logoColorConfig.color != 0 -> logoColorConfig.color
            else -> view.statusColor.firstColor()
        }
        return resolvedColor.toColorStateList()
    }

    /**
     * 按当前文字样式解析"跟随文字颜色"。
     *
     * 文字未开启自定义颜色（或颜色数组为空）时回退到状态栏首色，保证颜色始终有效。
     */
    private fun resolveFollowTextColor(view: LyricLogoView): Int {
        val statusColor = view.statusColor
        val textStyle = view.lyricStyle?.packageStyle?.text

        if (textStyle?.enableCustomTextColor != true) return statusColor.firstColor()

        val textColorConfig = textStyle.color(statusColor.isLightMode)
        if (textColorConfig == null || textColorConfig.normal.isEmpty()) {
            return statusColor.firstColor()
        }
        return textColorConfig.normal.firstOrNull() ?: statusColor.firstColor()
    }

    private fun Int.toColorStateList(): ColorStateList = ColorStateList.valueOf(this)
}
