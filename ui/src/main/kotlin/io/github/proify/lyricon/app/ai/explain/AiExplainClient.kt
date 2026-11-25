/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ai.explain

import io.github.proify.lyricon.lyric.ai.core.AiChatClient
import io.github.proify.lyricon.lyric.ai.core.AiChatStreamResult
import io.github.proify.lyricon.lyric.ai.core.AiConfig

/**
 * AI 音乐解读客户端（App 进程业务层）。
 *
 * 只负责解读功能的业务编排：组装提示词、调用共享的 [AiChatClient] 发起 SSE 流式请求，
 * 并回调思考过程与正文的增量文本。网络细节见 [AiChatClient]。
 */
object AiExplainClient {

    /**
     * 发起流式解释请求。
     *
     * @param configs 统一 AI 连接配置
     * @param targetLanguage 输出语言（null 时回退简体中文），来自翻译目标语言设置
     * @param customSystemPrompt 自定义系统提示词（null 时使用默认提示词）
     * @param title 歌曲名
     * @param artist 歌手名
     * @param album 专辑名（可能为空）
     * @param lyrics 需要解释的歌词上下文
     * @param onReasoning 解析到思考过程增量时回调（可能来自 IO 线程）
     * @param onContent 解析到正文增量时回调（可能来自 IO 线程）
     * @return 完整的思考与正文；失败或结果为空时返回 null
     */
    suspend fun stream(
        configs: AiConfig,
        targetLanguage: String?,
        customSystemPrompt: String? = null,
        title: String,
        artist: String,
        album: String,
        lyrics: String,
        onReasoning: (String) -> Unit,
        onContent: (String) -> Unit
    ): AiChatStreamResult? {
        return AiChatClient.stream(
            configs = configs,
            systemPrompt = AiExplainPrompt.explainSystemPrompt(
                customPrompt = customSystemPrompt,
            ),
            userPrompt = AiExplainPrompt.buildExplainUserPrompt(
                targetLanguage,
                title = title,
                artist = artist,
                album = album,
                lyrics = lyrics
            ),
            onReasoning = onReasoning,
            onContent = onContent
        )
    }
}