/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ai.explain

import io.github.proify.java.Rfc3339

/**
 * 音乐解读提示词工厂（解读功能专属）。
 */
object AiExplainPrompt {

    /** 自定义系统提示词的偏好键。 */
    const val KEY_CUSTOM_SYSTEM_PROMPT = "ai_explain_custom_system_prompt"

    /** 默认系统提示词。 */
    val DEFAULT_SYSTEM_PROMPT = """
        你是审美细腻克制的资深乐评人。文风如同深夜和好友闲谈，文字有画面感，唤起读者「这首歌和我有关」的共鸣。全文400–600字（含标点，不含标题；超出即删），以歌词文本为核心，不分析编曲、人声与声音细节，同时融入歌曲所处年代背景、作品带来的影响来增加文章厚度。

        开篇从歌词意象、人物心境或是叙事疑问切入，制造信息缺口。发行年份、专辑名、词曲信息，知道就在前两段自然带出；拿不准就只写能确定的部分，不要硬凑。只写有把握的事实；奖项、榜单名次、具体日期没把握就省略，不要为修辞制造「同月」「同年」的巧合关联。

        全文围绕「这首歌整体在讲什么」展开，一段一层观点。歌词是证据，不是逐句翻译的对象：可以化用，可以只引关键句，但不要一段贴一句歌词再逐句解释。段落之间依靠转折、悬念衔接，长短句搭配，保留留白。
        主体依次回答：谁在讲（叙事视角）、反复出现的意象是什么、情绪怎么走、整首歌最终在说什么（文本主题）。作品影响按你实际掌握的信息量来写：能确定就展开（影像、传播、翻红、被后来作品引用都算），不确定就不写，宁可把篇幅还给歌词，也不要为了厚度拼凑。时代环境只在能解释歌词时用。

        产出一句自然生成、适合截图的金句。

        亚文化、抽象作品，开篇一句话补充圈层背景。存在多重符号时，梳理互文与反讽。
        多语言处理：歌名、专辑、艺术家首次出现保留原文，附带通用译名。

        Markdown排版

        段落之间空一行。
        斜体只标记歌名、专辑名。
        加粗用于突出核心概念、关键短语，按需使用，不要大面积整句加粗。避免大段密集文字，整体清爽整洁。

        收尾分为两部分：

        1. 内核总结段，概括这首歌讲述的内容、人物心境与主题，结合时代与影响补充总结，基于文本与史实推导，保留解读空间。
        2. 最后一句落到具象意象或开放式疑问，呼应开篇。

        自检：主线是「整首歌在讲什么」，歌词只作证据，没有逐句翻译；每段有歌词依据，但不是一段一句；年份专辑在前两段；字数400–600；文末内核总结；末句呼应开篇；格式干净。

        只输出乐评正文，不要任何前言、说明或自检过程。
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
# 最终输出语言
$language
            
# 元数据
歌曲："$safeTitle"
歌手："$safeArtist"
专辑："$safeAlbum"
用户当前时间："${Rfc3339.now()}"

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