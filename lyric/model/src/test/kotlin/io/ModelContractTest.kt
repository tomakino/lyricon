/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io

import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.extensions.normalize
import io.github.proify.lyricon.lyric.model.lyricMetadataOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型层的**契约测试**。
 *
 * 固定的不是"某个具体转换结果"，而是三条对外承诺，防止后续改动回退：
 * 1. `normalize()` 系列不修改调用方数据（深拷贝语义）；
 * 2. [io.github.proify.lyricon.lyric.model.LyricMetadata] 与 `Map` 的等值语义对称；
 * 3. [Song.normalize] 逐层收口到词级，并保持与入参零共享。
 */
class ModelContractTest {

    @Test
    fun lyricWordNormalizeDoesNotMutateInput() {
        val words = listOf(
            LyricWord(begin = 0, duration = 100, end = 100, text = "For"),
            // 时间无效的分隔符：会被合并到相邻词，是最容易"顺手改坏入参"的路径
            LyricWord(begin = -1, end = -1, text = " "),
            LyricWord(begin = 100, duration = 0, end = 300, text = "we"),
        )
        val snapshot = words.map { it.copy() }

        words.normalize()

        assertEquals("normalize() 不得就地修改传入的单词对象", snapshot, words)
    }

    @Test
    fun metadataEqualityIsSymmetricWithPlainMap() {
        val metadata = lyricMetadataOf("a" to "b")
        val plain: Map<String, String?> = mapOf("a" to "b")

        assertTrue("metadata == map", metadata == plain)
        assertTrue("map == metadata", plain == metadata)
        assertEquals("等值对象必须同 hash", plain.hashCode(), metadata.hashCode())
        // 哈希容器中的查找不得因等值不对称而失败
        assertTrue(HashSet<Map<String, String?>>().apply { add(plain) }.contains(metadata))
    }

    @Test
    fun songNormalizeReturnsIsolatedCopyAndNormalizesWords() {
        val source = Song(
            id = "1",
            lyrics = listOf(
                RichLyricLine(
                    begin = 0,
                    end = 1000,
                    // 主文本缺失但逐词数据完整：规范化后应被重建而不是丢行
                    text = null,
                    words = listOf(
                        LyricWord(begin = 0, end = 400, text = "For"),
                        LyricWord(begin = -1, end = -1, text = " "),
                        LyricWord(begin = 400, end = 800, text = "we"),
                    )
                ),
                // 生命周期非法（end < begin）：应被剔除
                RichLyricLine(begin = 2000, end = 1500, text = "invalid"),
            )
        )
        val snapshot = source.deepCopy()

        val normalized = source.normalize()

        assertEquals("Song.normalize() 不得修改入参", snapshot, source)

        val lyrics = normalized.lyrics
        assertEquals("非法行应被剔除", 1, lyrics?.size)
        assertEquals("逐词数据应重建出主文本", "For we", lyrics?.first()?.text)
        assertNotSame("结果行不得与入参共享实例", source.lyrics?.first(), lyrics?.first())
    }
}
