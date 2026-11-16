/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.proify.lyricon.xposed

import io.github.proify.lyricon.lyric.ai.core.AiConfig
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.xposed.systemui.ai.translate.AiTranslator
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test

/**
 * AI 歌词翻译引擎测试类
 * 验证点：
 * 1. 旋律对位（音节是否匹配旋律节奏）
 * 2. 语境关联（前后句是否押韵或语义连贯）
 * 3. 知名音乐 Benchmark 模仿能力
 */
class AiTranslatorManagerTest {

    @Test
    fun testLyricTranslation(): Unit = runBlocking {
        val apiKey = System.getenv("AI_TRANSLATION_API_KEY")
        Assume.assumeTrue("AI_TRANSLATION_API_KEY is not set", !apiKey.isNullOrBlank())

        // 配置信息：建议使用支持长上下文的模型
        val configs = AiConfig(
            apiKey = apiKey,
            model = System.getenv("AI_TRANSLATION_MODEL") ?: "deepseek-chat",
            baseUrl = System.getenv("AI_TRANSLATION_BASE_URL") ?: "https://api.deepseek.com/v1"
        )

        // 模拟一首具有代表性节奏感的歌曲（例如：抒情摇滚风格）
        val mockSong = Song(
            name = "In the Shadow",
            artist = "Generic Rock Band"
        )

        // 具有起承转合的测试歌词：包含长短句对比、押韵潜力
        val testLyrics = listOf(
            "I'm waking up in the dead of night.",        // 节奏点强，期待：午夜梦回、惊醒...
            "Trying to find a flicker of light.",         // 押韵点 i (night/light)，期待：寻觅微光、抓住光芒...
            "The world is cold and the walls are high.", // 叙述句，期待：世界冰冷、高墙矗立...
            "I'm just a shadow passing by."               // 押韵点 i (high/by)，期待：过路之影、瞬息消散...
        )

        println("--- Starting Translation Request ---")

        val result = AiTranslator.doOpenAiRequest(
            configs = configs,
            song = mockSong,
            texts = testLyrics
        )

        // 输出结果：观察 AI 是否实现了“填词级”的音节对位与押韵
        result?.forEach { item ->
            val original = testLyrics[item.index]
            println("[ID:${item.index}]")
            println("Original: $original")
            println("Singable: ${item.translation}")
            println("--------------------------")
        }
    }
}