/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.yoyo

import android.animation.Animator
import android.view.View
import com.daimajia.androidanimations.library.YoYo

object YoYoAnimation {

    private const val KEY_ANIM_LOCK = 0x7F_114514
    private const val KEY_ANIM_HANDLE = 0x7F_191981

    fun <T : View> switchContent(
        target: T,
        outConfig: AnimConfig,
        inConfig: AnimConfig,
        action: (T) -> Unit,
        onCompleted: (T, Boolean) -> Unit = { _, _ -> },
    ) {
        cancelAnimation(target)
        target.setTag(KEY_ANIM_LOCK, true)

        val outHandle = YoYo.with(outConfig.technique)
            .duration(outConfig.duration)
            .interpolate(outConfig.interpolator)
            .withListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
                override fun onAnimationCancel(p0: Animator) {
                    target.setTag(KEY_ANIM_LOCK, false)
                }

                override fun onAnimationEnd(p0: Animator) {
                    if (target.getTag(KEY_ANIM_LOCK) != true) return

                    // 执行内容更新
                    action(target)

                    val inHandle = YoYo.with(inConfig.technique)
                        .duration(inConfig.duration)
                        .interpolate(inConfig.interpolator)
                        .withListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(p0: Animator) {}
                            override fun onAnimationRepeat(p0: Animator) {}
                            override fun onAnimationCancel(p0: Animator) {
                                target.setTag(KEY_ANIM_LOCK, false)
                                target.setTag(KEY_ANIM_HANDLE, null)
                                onCompleted(target, true)
                            }

                            override fun onAnimationEnd(p0: Animator) {
                                target.setTag(KEY_ANIM_LOCK, false)
                                target.setTag(KEY_ANIM_HANDLE, null)
                                onCompleted(target, false)
                            }
                        })
                        .playOn(target)

                    target.setTag(KEY_ANIM_HANDLE, inHandle)
                }
            })
            .playOn(target)

        target.setTag(KEY_ANIM_HANDLE, outHandle)
    }

    fun cancelAnimation(target: View) {
        val handle = target.getTag(KEY_ANIM_HANDLE) as? YoYo.YoYoString
        handle?.stop(true)
        target.setTag(KEY_ANIM_HANDLE, null)
        target.setTag(KEY_ANIM_LOCK, false)
    }
}

/**
 * 歌词行更新扩展
 * @param preset 使用 [YoYoPresets] 中的预设组合
 */
fun <T : View> T.animateUpdate(
    preset: Pair<AnimConfig, AnimConfig>,
    onCompleted: (cancelled: Boolean) -> Unit = {},
    block: T.() -> Unit
) {
    YoYoAnimation.switchContent(this, preset.first, preset.second, { it.block() }) { _, cancelled ->
        onCompleted(cancelled)
    }
}