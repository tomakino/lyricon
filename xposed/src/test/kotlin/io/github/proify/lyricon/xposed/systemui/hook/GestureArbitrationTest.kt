/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GestureArbitration] 的边界测试。
 *
 * 覆盖三类容易调坏的行为：
 * 1. **通知栏下拉**必须始终能成立（竖向位移达标 + 在时间窗口内）；
 * 2. **横向切歌**必须始终保留给歌词，不能被让渡逻辑抢走；
 * 3. 斜向滑动、慢速拖动、超时首帧等灰区不应出现"两条分支同时成立"或"两边都不接"。
 *
 * 约定：所有阈值都取自 [GestureArbitration]，测试不重复硬编码，
 * 使阈值调整后测试仍描述同一份契约。
 */
class GestureArbitrationTest {

    private val slop = 24

    /* ---------------- 竖向让渡（通知栏下拉） ---------------- */

    @Test
    fun `pure vertical pull beyond slop yields to system`() {
        assertTrue(
            GestureArbitration.shouldYieldToSystem(
                dx = 0f,
                dy = slop * 2f,
                elapsedMs = 120L,
                touchSlop = slop
            )
        )
    }

    @Test
    fun `vertical pull below slop does not yield yet`() {
        assertFalse(
            GestureArbitration.shouldYieldToSystem(
                dx = 0f,
                dy = slop * 0.5f,
                elapsedMs = 50L,
                touchSlop = slop
            )
        )
    }

    @Test
    fun `slow pull after the yield window is not handed back`() {
        assertFalse(
            GestureArbitration.shouldYieldToSystem(
                dx = 0f,
                dy = slop * 10f,
                elapsedMs = GestureArbitration.YIELD_WINDOW_MS + 1,
                touchSlop = slop
            )
        )
    }

    @Test
    fun `displacement direction dominates the decision`() {
        // 明显偏横向：不让渡
        assertFalse(
            GestureArbitration.shouldYieldToSystem(
                dx = 200f,
                dy = 30f,
                elapsedMs = 100L,
                touchSlop = slop
            )
        )
        // 明显偏竖向：让渡
        assertTrue(
            GestureArbitration.shouldYieldToSystem(
                dx = 30f,
                dy = 200f,
                elapsedMs = 100L,
                touchSlop = slop
            )
        )
    }

    @Test
    fun `upward swipe yields as well since direction is unsigned`() {
        assertTrue(
            GestureArbitration.shouldYieldToSystem(
                dx = 0f,
                dy = -slop * 4f,
                elapsedMs = 100L,
                touchSlop = slop
            )
        )
    }

    /* ---------------- 横向保留（歌词切歌） ---------------- */

    @Test
    fun `pure horizontal swipe beyond threshold is kept by lyric`() {
        assertTrue(
            GestureArbitration.shouldKeepToLyric(dx = 120f, dy = 0f, touchSlop = slop)
        )
    }

    @Test
    fun `horizontal swipe below threshold is not claimed by lyric`() {
        assertFalse(
            GestureArbitration.shouldKeepToLyric(dx = 10f, dy = 0f, touchSlop = slop)
        )
    }

    @Test
    fun `lyric threshold keeps the same floor as the lyric widget`() {
        // 小 slop 机型上仍以 24px 为下限，与 StatusBarLyric 内部一致
        val tinySlop = 4
        assertFalse(
            GestureArbitration.shouldKeepToLyric(
                dx = GestureArbitration.LYRIC_SWIPE_MIN_PX - 1f,
                dy = 0f,
                touchSlop = tinySlop
            )
        )
        assertTrue(
            GestureArbitration.shouldKeepToLyric(
                dx = GestureArbitration.LYRIC_SWIPE_MIN_PX + 1f,
                dy = 0f,
                touchSlop = tinySlop
            )
        )
    }

    @Test
    fun `diagonal swipe that is more vertical is not claimed by lyric`() {
        assertFalse(
            GestureArbitration.shouldKeepToLyric(dx = 100f, dy = 100f, touchSlop = slop)
        )
    }

    /* ---------------- 互斥性：不会同时成立 / 同时落空 ---------------- */

    @Test
    fun `a clear horizontal swipe never yields to the system`() {
        val dx = 200f
        val dy = 10f
        assertTrue(GestureArbitration.shouldKeepToLyric(dx, dy, slop))
        assertFalse(GestureArbitration.shouldYieldToSystem(dx, dy, 100L, slop))
    }

    @Test
    fun `a clear vertical pull never stays with the lyric`() {
        val dx = 10f
        val dy = 200f
        assertTrue(GestureArbitration.shouldYieldToSystem(dx, dy, 100L, slop))
        assertFalse(GestureArbitration.shouldKeepToLyric(dx, dy, slop))
    }

    @Test
    fun `sub threshold movement stays pending on both branches`() {
        // 位移极小（手指几乎没动）：两条分支都不成立，拦截器会继续等待，
        // 从而把决定权保留到 ACTION_UP（此时按单击处理）
        val dx = 2f
        val dy = 2f
        assertFalse(GestureArbitration.shouldKeepToLyric(dx, dy, slop))
        assertFalse(GestureArbitration.shouldYieldToSystem(dx, dy, 30L, slop))
    }
}
