/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

/**
 * Logo 显示策略接口。
 *
 * [LyricLogoView] 只负责"何时切换策略"与视图属性复位，具体的内容加载、着色与
 * 动画由本接口的实现承担。新增一种 Logo 来源时，实现本接口并在
 * [LyricLogoView] 的策略评估处注册即可，无需修改既有实现。
 */
interface LogoStrategy {

    /**
     * 当前策略是否已产出可显示内容。
     * [LyricLogoView] 用它决定自身是否可见。
     */
    val isEffective: Boolean

    /**
     * 加载或更新显示内容。
     *
     * 在策略初始化、内容源变更（封面文件更新、Provider 切换）时调用；
     * 实现方应自行缓存，避免重复解码。
     */
    fun updateContent()

    /**
     * 状态栏配色变化时调用，用于重算 Tint。
     */
    fun onStatusColorChanged()

    /**
     * View 附加到窗口时调用，用于恢复动画或补充监听。
     */
    fun onAttach()

    /**
     * View 从窗口移除时调用，用于停止动画、释放重型资源（如 Bitmap 缓存）。
     */
    fun onDetach()

    /**
     * 容器可见性变化时调用。
     *
     * @param visible 当前是否对用户可见
     */
    fun onVisibilityChanged(visible: Boolean)
}
