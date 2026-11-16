/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.control

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import io.github.proify.android.extensions.dp

/**
 * 控制窗口进出场动画 + 按压反馈动画。
 *
 * iOS 灵动岛风格：
 * - 进场：弹簧感溢出，从状态栏"流"出来，有液态/果冻质感
 * - 退场：流畅大方退出，让用户专注其它
 * - 按压：轻微 squish 效果，弹性回弹
 *
 * @author Tomakino
 * @since 2026
 */
object ControlAnimations {

    // ---- 进场：弹簧溢出，液态流动感 ----
    private const val ENTER_DURATION_MS = 450L
    private const val ENTER_OFFSET_DP = 32
    private const val ENTER_SCALE = 0.85f

    // 高 tension 的 OvershootInterpolator 模拟 spring 弹性
    private val ENTER_INTERPOLATOR = OvershootInterpolator(1.2f)

    // ---- 退场：流畅大方退出 ----
    private const val EXIT_DURATION_MS = 250L
    private const val EXIT_OFFSET_DP = 36
    private const val EXIT_SCALE = 0.9f

    // Material FastOutSlowIn：快速启动，平滑减速收尾
    private val EXIT_INTERPOLATOR = FastOutSlowInInterpolator()

    // ---- 按压反馈：squish 弹性效果 ----
    internal const val PRESS_DOWN_SCALE = 0.92f
    internal const val PRESS_DOWN_DURATION_MS = 200L
    internal const val PRESS_UP_DURATION_MS = 200L

    // 按下：快速减速到位
    internal val PRESS_DOWN_INTERPOLATOR = FastOutSlowInInterpolator()

    // 弹回：弹性过冲回弹
    internal val PRESS_UP_INTERPOLATOR = OvershootInterpolator(2.5f)

    /** 从状态栏下沿弹出：液态流动 + 弹簧过冲。 */
    fun playEnter(view: View) {
        view.translationY = -ENTER_OFFSET_DP.dp.toFloat()
        view.scaleX = ENTER_SCALE
        view.scaleY = ENTER_SCALE
        view.alpha = 0f

        view.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(ENTER_DURATION_MS)
            .setInterpolator(ENTER_INTERPOLATOR)
            .start()
    }

    /**
     * 关闭：向上收缩吸入 + 淡出，结束后回调 [onEnd]。
     *
     * @param onUpdateAlpha 动画过程中更新 alpha (0.0~1.0)，用于同步更新模糊背景
     */
    fun playExit(view: View, onEnd: () -> Unit, onUpdateAlpha: ((Float) -> Unit)? = null) {
        view.animate()
            .translationY(-EXIT_OFFSET_DP.dp.toFloat())
            .scaleX(EXIT_SCALE)
            .scaleY(EXIT_SCALE)
            .alpha(0f)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(EXIT_INTERPOLATOR)
            .setUpdateListener {
                // 读取 view 当前的 alpha 值
                onUpdateAlpha?.invoke(view.alpha)
            }
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            .start()
    }
}
