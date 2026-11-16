/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.graphics.drawable.Drawable
import java.util.WeakHashMap

/**
 * App 图标策略：显示当前活跃播放器的应用图标。
 *
 * 作为兜底策略使用：图标由系统管理且自带配色，因此不加 Tint、不加裁剪轮廓，
 * 也不做任何动画。
 */
class AppLogoStrategy(
    private val view: LyricLogoView
) : LogoStrategy {

    /** 包名到图标的弱键缓存，避免对同一应用反复查询 PackageManager。 */
    private val iconCache = WeakHashMap<String, Drawable>()

    override var isEffective: Boolean = false
        private set

    override fun updateContent() {
        // App 图标自带配色与形状：清掉其他策略可能遗留的效果
        if (view.imageTintList != null) view.imageTintList = null
        if (view.outlineProvider != null) view.outlineProvider = null

        val icon = view.activePackage?.takeIf { it.isNotBlank() }?.let(::loadAppIcon)
        view.setImageDrawable(icon)
        isEffective = icon != null
        view.refreshVisibility()
    }

    override fun onStatusColorChanged() {
        // App 图标保持原色
    }

    override fun onAttach() {
        if (view.drawable == null) updateContent()
    }

    override fun onDetach() {
        // 图标通常较小且由系统缓存管理，这里不主动清理，避免反复查询 PackageManager
    }

    override fun onVisibilityChanged(visible: Boolean) {
        // App 图标不依赖可见性做动画，无需处理
    }

    // --- 内部逻辑 ---

    /**
     * 加载应用图标。
     *
     * 包名可能已卸载或对当前进程不可见，查询失败时返回 null，由视图自行隐藏。
     */
    private fun loadAppIcon(packageName: String): Drawable? {
        iconCache[packageName]?.let { return it }

        val icon = runCatching {
            view.context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null

        iconCache[packageName] = icon
        return icon
    }
}
