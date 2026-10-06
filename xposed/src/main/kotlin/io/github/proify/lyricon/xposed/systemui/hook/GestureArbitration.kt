/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.hook

import kotlin.math.abs
import kotlin.math.max

/**
 * 根窗口触摸手势归属判定 (Gesture Arbitration)
 *
 * 从 [StatusBarTouchHooker] 中抽出的**纯逻辑**部分：给定一次手势的位移与耗时，
 * 判断它应该归属歌词（横向切歌 / 单击）还是让渡回系统（竖向拉出通知栏）。
 *
 * 抽成独立无 Android 依赖的单元，使这些阈值行为可以在 JVM 单元测试中直接覆盖，
 * 避免今后调整阈值时无意破坏另一条分支。
 *
 * @author Tomakino
 * @since 2026
 */
object GestureArbitration {

    /**
     * 竖向让渡的位移阈值（单位：scaledTouchSlop）
     *
     * 取 1.0 表示与系统自身的拖动判定同步；调低会更早让渡下拉，
     * 调高则给歌词更多容错，但慢速下拉更容易被歌词抢先判定为横向滑动。
     */
    const val YIELD_SLOP_FACTOR: Float = 1.0f

    /** 竖向位移至少要达到横向位移的多少倍，才认定"用户想拉通知栏" */
    const val YIELD_RATIO: Float = 1.15f

    /**
     * "竖向优先"判定的时间窗口（ms）
     *
     * 快速下拉一定在窗口内完成；超时后即便位移偏竖向也不再让渡，
     * 避免"横向滑动末尾手指下沉"把已生效的切歌手势改判给下拉。
     */
    const val YIELD_WINDOW_MS: Long = 650L

    /**
     * 歌词自身的横向滑动阈值下限（px）
     *
     * 与 [io.github.proify.lyricon.statusbarlyric.StatusBarLyric] 内部保持一致：
     * `max(24, scaledTouchSlop * 2)`，避免出现"歌词认为没滑够、系统又被取消"的空手势。
     */
    const val LYRIC_SWIPE_MIN_PX: Float = 24f

    /** 歌词内部的横竖位移比要求：`|dx| > |dy| * 1.5` */
    const val LYRIC_SWIPE_RATIO: Float = 1.5f

    /**
     * 是否把本次手势让渡回系统（通知栏下拉）。
     *
     * 条件：处于快速判定窗口内 + 竖向位移达标 + 位移方向偏竖向。
     *
     * @param dx 相对按下点的横向位移
     * @param dy 相对按下点的纵向位移
     * @param elapsedMs 距按下经过的毫秒数
     * @param touchSlop 系统 scaledTouchSlop
     */
    fun shouldYieldToSystem(dx: Float, dy: Float, elapsedMs: Long, touchSlop: Int): Boolean {
        if (elapsedMs > YIELD_WINDOW_MS) return false

        val vertical = abs(dy)
        if (vertical < max(1f, touchSlop * YIELD_SLOP_FACTOR)) return false

        return vertical > abs(dx) * YIELD_RATIO
    }

    /**
     * 是否由歌词保留本次手势（横向切歌）。
     *
     * 阈值与歌词控件内部的手势判定完全一致，保证拦截器的判定不会先于歌词收敛，
     * 也不会把歌词已经认可的手势取消掉。
     *
     * @param dx 相对按下点的横向位移
     * @param dy 相对按下点的纵向位移
     * @param touchSlop 系统 scaledTouchSlop
     */
    fun shouldKeepToLyric(dx: Float, dy: Float, touchSlop: Int): Boolean {
        val horizontal = abs(dx)
        if (horizontal < max(LYRIC_SWIPE_MIN_PX, touchSlop * 2f)) return false

        return horizontal > abs(dy) * LYRIC_SWIPE_RATIO
    }
}
