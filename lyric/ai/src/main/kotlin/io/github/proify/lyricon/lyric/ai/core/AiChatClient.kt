/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.ai.core

import android.util.Log
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.ResponseFormatJsonObject
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 流式请求收尾结果：思考过程（reasoning_content / reasoning）+ 正文内容。 */
data class AiChatStreamResult(
    val reasoning: String,
    val content: String
)

/**
 * 统一的 OpenAI 兼容 Chat Completions 客户端（官方 openai-java SDK，底层 OkHttp）。
 *
 * App 与 SystemUI 进程共用的唯一网络实现：
 * - 支持自定义 baseUrl（DeepSeek 等 OpenAI 兼容服务）与流式（SSE）返回；
 * - 按 `baseUrl|apiKey` 缓存 [OpenAIClient] 实例（有界 LRU），复用底层连接池，
 *   换配置后旧客户端会被 LRU 淘汰释放；
 * - 解析 DeepSeek/Qwen 等兼容服务的思考过程（reasoning_content / reasoning）；
 * - [CancellationException] 一律向上抛出，不吞掉协程取消。
 */
object AiChatClient {
    private const val TAG = "LyriconAiChatClient"
    private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private const val MAX_CLIENTS = 8

    /** 有界客户端缓存：超限淘汰最久未用的客户端（其底层线程池随之释放）。 */
    private val clients = object : LinkedHashMap<String, OpenAIClient>(MAX_CLIENTS, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, OpenAIClient>?
        ): Boolean = size > MAX_CLIENTS
    }

    /**
     * 发起一次非流式 Chat Completions 请求。
     *
     * @param configs 统一 AI 配置
     * @param systemPrompt system 提示词
     * @param userPrompt user 提示词
     * @param jsonObject 是否要求 JSON Object 响应（发送 response_format）
     * @return 完整响应文本；请求失败或响应为空时返回 null
     */
    suspend fun complete(
        configs: AiConfig,
        systemPrompt: String,
        userPrompt: String,
        jsonObject: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = configs.apiKey ?: return@withContext null
        val baseUrl = resolveBaseUrl(configs)
        val client = clientFor(baseUrl, apiKey)

        try {
            val builder = paramsBuilder(configs, systemPrompt, userPrompt)
            if (jsonObject) {
                builder.responseFormat(ResponseFormatJsonObject.builder().build())
            }
            Log.d(TAG, "Connecting to OpenAI compatible API: $baseUrl (stream=false)")

            client.chat().completions().create(builder.build())
                .choices().firstOrNull()?.message()?.content()?.orElse(null)
                ?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI compatible request failed: ${e.message}", e)
            null
        }
    }

    /**
     * 发起一次 SSE 流式 Chat Completions 请求。
     *
     * 思考过程增量回调 [onReasoning]，正文增量回调 [onContent]；
     * 调用方注意回调可能来自 IO 线程。
     *
     * @return 完整思考与正文；请求失败或正文为空时返回 null
     */
    suspend fun stream(
        configs: AiConfig,
        systemPrompt: String,
        userPrompt: String,
        onReasoning: (String) -> Unit = {},
        onContent: (String) -> Unit = {}
    ): AiChatStreamResult? = withContext(Dispatchers.IO) {
        val apiKey = configs.apiKey ?: return@withContext null
        val baseUrl = resolveBaseUrl(configs)
        val client = clientFor(baseUrl, apiKey)

        try {
            val params = paramsBuilder(configs, systemPrompt, userPrompt).build()
            Log.d(TAG, "Connecting to OpenAI compatible API: $baseUrl (stream=true)")

            val reasoning = StringBuilder()
            val content = StringBuilder()
            client.chat().completions().createStreaming(params).use { respStream ->
                respStream.stream().forEach { chunk ->
                    val delta = chunk.choices().firstOrNull()
                        ?.let { runCatching { it.delta() }.getOrNull() } ?: return@forEach

                    delta.content().ifPresent {
                        content.append(it)
                        onContent(it)
                    }
                    reasoningDelta(delta)?.let {
                        reasoning.append(it)
                        onReasoning(it)
                    }
                }
            }

            val finalContent = content.toString().trim()
            if (finalContent.isBlank()) {
                Log.e(TAG, "Empty content in AI stream response.")
                null
            } else {
                AiChatStreamResult(reasoning.toString().trim(), finalContent)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI stream failed: ${e.message}", e)
            null
        }
    }

    private fun resolveBaseUrl(configs: AiConfig): String {
        return configs.baseUrl?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BASE_URL
    }

    private fun paramsBuilder(
        configs: AiConfig,
        systemPrompt: String,
        userPrompt: String
    ): ChatCompletionCreateParams.Builder {
        return ChatCompletionCreateParams.builder()
            .model(ChatModel.of(configs.model.orEmpty()))
            .addSystemMessage(systemPrompt)
            .addUserMessage(userPrompt)
            .temperature(configs.temperature.toDouble())
            .topP(configs.topP.toDouble())
            .presencePenalty(configs.presencePenalty.toDouble())
            .frequencyPenalty(configs.frequencyPenalty.toDouble())
    }

    /** DeepSeek/Qwen 等兼容服务的思考过程字段：reasoning_content / reasoning。 */
    private fun reasoningDelta(delta: ChatCompletionChunk.Choice.Delta): String? {
        val value = delta._additionalProperties()["reasoning_content"]
            ?: delta._additionalProperties()["reasoning"]
            ?: return null
        return value.accept(object : JsonValue.Visitor<String?> {
            override fun visitString(value: String): String? = value
            override fun visitNull(): String? = null
            override fun visitDefault(): String? = null
        })
    }

    private fun clientFor(baseUrl: String, apiKey: String): OpenAIClient {
        val key = "$baseUrl|$apiKey"
        return synchronized(clients) {
            clients[key] ?: OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build()
                .also { clients[key] = it }
        }
    }
}
