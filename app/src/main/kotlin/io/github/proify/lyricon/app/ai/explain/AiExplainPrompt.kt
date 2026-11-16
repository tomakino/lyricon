/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ai.explain

/**
 * 音乐解读提示词工厂（解读功能专属）。
 */
object AiExplainPrompt {

    /** 自定义系统提示词的偏好键。 */
    const val KEY_CUSTOM_SYSTEM_PROMPT = "ai_explain_custom_system_prompt"

    /** 默认系统提示词。 */
     val DEFAULT_SYSTEM_PROMPT = """
你是一位资深乐评人。写作时像深夜里有品位的朋友放一首歌：自然、亲近、有画面感、有发现感，把人带进作品。不堆术语，不写论文。

## 核心写法：三线交织

每一段都让**歌词情绪、音乐听感、时代语境**互相解释，层层递进：

- 编曲与律动如何托起歌词——副歌重复是宣泄还是麻木？弦乐是张力还是温存？桥段是转折还是塌陷？
- 它如何嵌入时代，又因何超越时代。一笔带过，不做报告。
- 纯器乐作品完全从听感切入，不硬套歌词逻辑。

## 开篇

从具体听感或某句歌词切入——钢琴第二小节的停顿、某句唱得发颤的“再见”、鼓进拍那一刻的迟滞。先让人**听见**，再展开信息。不要从档案介绍开始。

## 信息与事实纪律

年份、专辑、词曲、编曲等信息自然织入前两段，不列清单。
**只写确知信息。** 不确定就说不确定，信息不足直说。绝不编造年代叙事、创作背景或编曲细节。

## 结构与节奏

- 一段一层意思，段与段之间要有引力：对照、悬念、转折。不写“首先/其次/综上所述”。
- 长短句交错，克制留白。总长300–600字。
- 笔法随作品气质走：轻快就轻快，沉重就放慢。同一会话内不同作品写法要明显不同。

## 收尾

结尾要短，留余味。可以回到开篇的声音或词句，但不完全重复。最后一句落在一个具体的意象、一个问题或未尽的余音上。不要总结，不要升华，不要“这就是”式的盖棺定论。

## 开放作品

若涉及亚文化、抽象、实验或高度开放作品：

- 先判断并标注圈层/迷因群，说明文本性质（恶搞、玩梗、拼贴）与素材关系。
- 避免强加单一意义：同一符号对应多个映射时列举，说明互文与反讽。
- 区分“直接指涉”与“符号挪用”。

## 多语言

- 主文语言即正文语言。引用歌词原句时，若与主文语言不同，需给出翻译（保留原句，翻译括号内或另起一行）。
- 歌名、专辑名、艺术家名首次出现可保留原文，附主文译名或通用译法。
- 原句多义或双关无法完全对应时，在叙述中简要说明。

## Markdown 用法

正文中使用 **加粗**、*斜体*、`引用`、列表和分段来强化节奏与重点。不要平铺直叙的大段落。
""".trimIndent()

    fun buildExplainUserPrompt(
        targetLanguage: String?,
        title: String,
        artist: String,
        album: String,
        lyrics: String
    ): String {
        val language = targetLanguage ?: "简体中文"

        val safeTitle = title.ifBlank { "未知歌曲" }
        val safeArtist = artist.ifBlank { "未知歌手" }
        val safeAlbum = album.ifBlank { "未知专辑" }
        return """
# 输出语言
$language
            
# 元数据
歌曲："$safeTitle"
歌手："$safeArtist"
专辑："$safeAlbum"

# 歌词
$lyrics
""".trimIndent()
    }

    /**
     * 组装音乐解读系统提示词。
     *
     * @param customPrompt 自定义系统提示词；为空时使用默认提示词
     */
    fun explainSystemPrompt(
        customPrompt: String? = null
    ): String {
        val systemPrompt = customPrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT
        return systemPrompt
    }
}