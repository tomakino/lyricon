/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * [TimingNavigator] 与朴素实现的**对拍测试**。
 *
 * 该类内部维护多个可变缓存（锚点、重叠窗口起点、顺序模式），是模块中状态最复杂的部分，
 * 原先仅有两条断言覆盖。这里用随机重叠数据把 [TimingNavigator] 的每个查询入口
 * 与 O(N) 朴素实现逐一对比，覆盖顺序播放、随机跳转、回退与 [TimingNavigator.resetCache]
 * 等缓存切换路径。
 */
class TimingNavigatorDifferentialTest {

    private data class Scenario(val name: String, val lines: List<LyricLine>)

    // ------------------------------------------------------------------
    // 朴素参考实现：语义与 TimingNavigator 的公开契约一一对应
    // ------------------------------------------------------------------

    /** forEachAt / first 的候选行：按 begin 升序、覆盖 position 的所有行 */
    private fun naiveActive(lines: List<LyricLine>, position: Long): List<LyricLine> =
        lines.filter { position >= it.begin && position <= it.end }

    /** findPreviousEntry：最后一条 begin <= position 的行（不校验 end） */
    private fun naivePreviousEntry(lines: List<LyricLine>, position: Long): LyricLine? =
        lines.lastOrNull { it.begin <= position }

    /**
     * first：锚点（最后一条 begin <= position 的行）仍覆盖 position 时返回锚点，
     * 否则返回最早开始的活动行（与实现中的 resolveOverlapping 首项一致）。
     */
    private fun naiveFirst(lines: List<LyricLine>, position: Long): LyricLine? {
        val anchor = naivePreviousEntry(lines, position) ?: return null
        if (position <= anchor.end) return anchor
        return naiveActive(lines, position).firstOrNull()
    }

    /** forEachAtOrPrevious：有活动行则返回全部活动行，否则回落到最近的历史行 */
    private fun naiveAtOrPrevious(lines: List<LyricLine>, position: Long): List<LyricLine> {
        val active = naiveActive(lines, position)
        if (active.isNotEmpty()) return active
        return listOfNotNull(naivePreviousEntry(lines, position))
    }

    // ------------------------------------------------------------------
    // 数据构造
    // ------------------------------------------------------------------

    private fun randomLines(seed: Long, count: Int): List<LyricLine> {
        val random = Random(seed)
        val lines = ArrayList<LyricLine>(count)
        var begin = 0L
        repeat(count) { index ->
            // 步长恒小于行时长，保证随机数据中必然出现重叠与重复 begin
            begin += random.nextInt(0, 700)
            val end = begin + random.nextInt(100, 3000)
            lines.add(LyricLine(begin = begin, end = end, text = "L$index"))
        }
        return lines
    }

    private fun scenarios(): List<Scenario> = listOf(
        Scenario("空数据源", emptyList()),
        Scenario("单行", listOf(LyricLine(begin = 1000, end = 2000, text = "a"))),
        Scenario(
            "首尾相接", listOf(
                LyricLine(begin = 0, end = 500, text = "a"),
                LyricLine(begin = 500, end = 1000, text = "b"),
            )
        ),
        Scenario(
            "重复 begin", listOf(
                LyricLine(begin = 1000, end = 3000, text = "a"),
                LyricLine(begin = 1000, end = 1500, text = "b"),
                LyricLine(begin = 1000, end = 1200, text = "c"),
            )
        ),
        Scenario(
            "嵌套重叠", listOf(
                LyricLine(begin = 1000, end = 9000, text = "long"),
                LyricLine(begin = 2000, end = 3000, text = "short-a"),
                LyricLine(begin = 2500, end = 3500, text = "short-b"),
                LyricLine(begin = 8000, end = 8500, text = "late"),
            )
        ),
        Scenario(
            "长空隙", listOf(
                LyricLine(begin = 0, end = 1000, text = "a"),
                LyricLine(begin = 5000, end = 6000, text = "b"),
            )
        ),
        Scenario("随机重叠(200 行)", randomLines(seed = 1L, count = 200)),
        Scenario("随机重叠(500 行)", randomLines(seed = 42L, count = 500)),
    )

    /** 查询位置集合：早于/等于/晚于所有行、顺序播放推进、随机跳转与回退 */
    private fun queryPositions(lastEnd: Long): LongArray {
        val positions = ArrayList<Long>()
        positions.add(-1000L)
        positions.add(-1L)
        positions.add(0L)

        var position = 0L
        while (position <= lastEnd + 1000L) {
            positions.add(position)
            position += 89L
        }

        val random = Random(20260814L)
        repeat(800) {
            positions.add(random.nextLong(-500L, lastEnd + 2000L))
        }
        return positions.toLongArray()
    }

    // ------------------------------------------------------------------
    // 测试
    // ------------------------------------------------------------------

    @Test
    fun matchesNaiveImplementationOnAllQueries() {
        scenarios().forEach { scenario ->
            val lines = scenario.lines
            val navigator = TimingNavigator(lines.toTypedArray())
            val positions = queryPositions(lines.maxOfOrNull { it.end } ?: 0L)

            positions.forEach { position ->
                val tag = "${scenario.name} @position=$position"

                assertEquals("$tag first()", naiveFirst(lines, position), navigator.first(position))

                val actualActive = ArrayList<LyricLine>()
                val activeCount = navigator.forEachAt(position) { actualActive.add(it) }
                val expectedActive = naiveActive(lines, position)
                assertEquals("$tag forEachAt() 数量", expectedActive.size, activeCount)
                assertEquals("$tag forEachAt() 内容", expectedActive, actualActive)

                assertEquals(
                    "$tag findPreviousEntry()",
                    naivePreviousEntry(lines, position),
                    navigator.findPreviousEntry(position)
                )

                // forEachAtOrPrevious 复用 forEachAt 刚写入的锚点缓存，必须紧随其后校验
                val actualOrPrevious = ArrayList<LyricLine>()
                val orPreviousCount = navigator.forEachAtOrPrevious(position) { actualOrPrevious.add(it) }
                val expectedOrPrevious = naiveAtOrPrevious(lines, position)
                assertEquals("$tag forEachAtOrPrevious() 数量", expectedOrPrevious.size, orPreviousCount)
                assertEquals("$tag forEachAtOrPrevious() 内容", expectedOrPrevious, actualOrPrevious)
            }
        }
    }

    @Test
    fun staysConsistentAcrossSeeksAndCacheReset() {
        val lines = randomLines(seed = 7L, count = 300)
        val navigator = TimingNavigator(lines.toTypedArray())
        val random = Random(20260101L)
        val maxPosition = lines.maxOf { it.end }

        repeat(3000) { step ->
            val position = random.nextLong(-1000L, maxPosition + 1000L)

            assertEquals(
                "step=$step @first($position)",
                naiveFirst(lines, position),
                navigator.first(position)
            )

            if (step % 500 == 0) {
                // 缓存重置不得改变查询结果，只应影响内部路径
                navigator.resetCache()
            }
        }
    }
}
