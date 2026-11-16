/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ai.analytics

import io.github.proify.lyricon.common.analytics.ThinkingStep
import io.github.proify.lyricon.common.analytics.ToolCallRecord
import io.github.proify.lyricon.common.analytics.ToolCallStatus

import android.util.Log
import io.github.proify.lyricon.lyric.ai.core.AiChatStreamResult
import io.github.proify.lyricon.lyric.ai.core.AiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 音乐画像分析 AI Agent。
 *
 * 直接使用 OkHttp 实现 Agentic Loop，避免 openai-java SDK 的复杂类型问题。
 *
 * 思考过程以交错时间线形式输出：
 * 推理片段 → 工具调用 → 推理片段 → 工具调用 → ... → 最终结果
 */
object MusicAnalysisAgent {

    private const val TAG = "MusicAnalysisAgent"
    private const val MAX_ROUNDS = 100
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 执行音乐画像分析。
     *
     * @param configs AI 配置
     * @param toolExecutor 工具函数执行器
     * @param customSystemPrompt 自定义系统提示词
     * @param onReasoning 推理文本增量回调（用于最终结果）
     * @param onContent 正文增量回调（用于最终结果）
     * @param onThinkingStep 思考步骤回调（用于构建交错时间线）
     */
    suspend fun analyze(
        configs: AiConfig,
        tools: MusicAnalysisTools,
        customSystemPrompt: String? = null,
        onReasoning: (String) -> Unit = {},
        onContent: (String) -> Unit = {},
        onThinkingStep: (ThinkingStep) -> Unit = {},
    ): AiChatStreamResult? = withContext(Dispatchers.IO) {
        if (!configs.isUsable) {
            Log.e(TAG, "AI config not usable")
            return@withContext null
        }

        val systemPrompt = MusicAnalysisPrompt.buildSystemPrompt(customSystemPrompt)
        val userPrompt = MusicAnalysisPrompt.buildUserPrompt()
        val toolsArray = tools.buildToolsJsonArray()

        val baseUrl = configs.baseUrl?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() }
            ?: "https://api.openai.com/v1"
        val chatUrl = "$baseUrl/chat/completions"

        // 消息列表
        val messages = mutableListOf<JsonObject>()
        messages.add(buildJsonObject {
            put("role", JsonPrimitive("system"))
            put("content", JsonPrimitive(systemPrompt))
        })
        messages.add(buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", JsonPrimitive(userPrompt))
        })

        val reasoningBuilder = StringBuilder()
        val contentBuilder = StringBuilder()
        var stepIndex = 0

        try {
            for (round in 0 until MAX_ROUNDS) {
                Log.d(TAG, "Agent loop round $round")

                // 构建请求体
                val requestBody = buildJsonObject {
                    put("model", JsonPrimitive(configs.model.orEmpty()))
                    put("messages", JsonArray(messages))
                    put("tools", toolsArray)
                    put("temperature", JsonPrimitive(configs.temperature.toDouble()))
                    put("top_p", JsonPrimitive(configs.topP.toDouble()))
                    put("presence_penalty", JsonPrimitive(configs.presencePenalty.toDouble()))
                    put("frequency_penalty", JsonPrimitive(configs.frequencyPenalty.toDouble()))
                }

                // 发送请求
                val request = Request.Builder()
                    .url(chatUrl)
                    .addHeader("Authorization", "Bearer ${configs.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    Log.e(TAG, "API request failed: HTTP ${response.code}, body: ${errorBody.take(500)}")
                    val partialContent = contentBuilder.toString().trim()
                    return@withContext if (partialContent.isNotBlank()) {
                        AiChatStreamResult(reasoning = reasoningBuilder.toString().trim(), content = partialContent)
                    } else null
                }
                val responseBody = response.body.string()
                if (responseBody.isBlank()) {
                    Log.e(TAG, "Empty response body")
                    val partialContent = contentBuilder.toString().trim()
                    return@withContext if (partialContent.isNotBlank()) {
                        AiChatStreamResult(reasoning = reasoningBuilder.toString().trim(), content = partialContent)
                    } else null
                }

                // 解析响应
                val responseJson = json.parseToJsonElement(responseBody).jsonObject

                // 检查 API 错误
                val apiError = responseJson["error"]?.jsonObject
                if (apiError != null) {
                    val errorMsg = apiError["message"]?.jsonPrimitive?.content ?: "Unknown API error"
                    Log.e(TAG, "API error: $errorMsg")
                    val partialContent = contentBuilder.toString().trim()
                    return@withContext if (partialContent.isNotBlank()) {
                        AiChatStreamResult(reasoning = reasoningBuilder.toString().trim(), content = partialContent)
                    } else null
                }

                val choices = responseJson["choices"]?.jsonArray
                val firstChoice = choices?.firstOrNull()?.jsonObject
                val message = firstChoice?.get("message")?.jsonObject

                if (message == null) {
                    Log.e(TAG, "No message in response")
                    return@withContext null
                }

                // 收集思考过程（推理文本）- 作为时间线步骤
                val reasoning = message["reasoning_content"]?.jsonPrimitive?.content
                    ?: message["reasoning"]?.jsonPrimitive?.content
                if (!reasoning.isNullOrBlank()) {
                    reasoningBuilder.append(reasoning)
                    onReasoning(reasoning)
                    // 发射推理步骤
                    onThinkingStep(ThinkingStep.Reasoning(index = stepIndex++, text = reasoning))
                }

                // 收集正文内容
                val msgContent = message["content"]?.jsonPrimitive?.content
                if (!msgContent.isNullOrBlank()) {
                    contentBuilder.append(msgContent)
                    onContent(msgContent)
                }

                // 检查是否有工具调用
                val toolCalls = message["tool_calls"]?.jsonArray
                Log.d(TAG, "Response content: ${msgContent?.take(100)}, tool_calls: ${toolCalls?.size ?: 0}")
                if (!toolCalls.isNullOrEmpty()) {
                    Log.d(TAG, "Tool calls received: ${toolCalls.size}")

                    // 添加 assistant message（只保留必要字段）
                    val assistantMsg = buildJsonObject {
                        put("role", JsonPrimitive("assistant"))
                        message["content"]?.let { put("content", it) }
                        message["tool_calls"]?.let { put("tool_calls", it) }
                    }
                    messages.add(assistantMsg)

                    // 执行每个工具调用
                    for (tc in toolCalls) {
                        val tcObj = tc.jsonObject
                        val tcId = tcObj["id"]?.jsonPrimitive?.content ?: continue
                        val function = tcObj["function"]?.jsonObject ?: continue
                        val funcName = function["name"]?.jsonPrimitive?.content ?: continue
                        val funcArgs = function["arguments"]?.jsonPrimitive?.content ?: "{}"

                        // 创建工具调用记录（执行中）
                        val executingRecord = ToolCallRecord(
                            id = tcId,
                            name = funcName,
                            arguments = funcArgs,
                            status = ToolCallStatus.EXECUTING,
                            startTime = System.currentTimeMillis()
                        ).apply {
                            displayNameProvider = MusicAnalysisTools.Companion::getDisplayName
                        }
                        // 发射工具调用步骤（执行中）
                        onThinkingStep(ThinkingStep.ToolCall(index = stepIndex++, record = executingRecord))

                        Log.d(TAG, "Executing tool: $funcName with args: ${funcArgs.take(200)}")
                        val result = try {
                            tools.execute(funcName, funcArgs)
                        } catch (e: Exception) {
                            Log.e(TAG, "Tool execution failed: ${e.message}", e)
                            // 发射工具调用步骤（失败）
                            onThinkingStep(ThinkingStep.ToolCall(
                                index = stepIndex - 1,
                                record = executingRecord.copy(
                                    status = ToolCallStatus.FAILED,
                                    endTime = System.currentTimeMillis(),
                                    error = e.message
                                )
                            ))
                            """{"error": "${e.message}"}"""
                        }

                        // 发射工具调用步骤（完成）
                        onThinkingStep(ThinkingStep.ToolCall(
                            index = stepIndex - 1,
                            record = executingRecord.copy(
                                result = result,
                                status = ToolCallStatus.COMPLETED,
                                endTime = System.currentTimeMillis()
                            )
                        ))

                        Log.d(TAG, "Tool $funcName completed, result length: ${result.length}")

                        // 添加工具结果
                        val toolResult = buildJsonObject {
                            put("role", JsonPrimitive("tool"))
                            put("tool_call_id", JsonPrimitive(tcId))
                            put("content", JsonPrimitive(result.take(10000)))  // 限制结果长度
                        }
                        Log.d(TAG, "Adding tool result: ${toolResult.toString().take(200)}")
                        messages.add(toolResult)
                    }
                    Log.d(TAG, "All tool calls executed, continuing loop (round $round)")
                    continue
                }

                Log.d(TAG, "No tool calls, AI response complete")
                // 没有工具调用，AI 已完成
                val finalContent = contentBuilder.toString().trim()
                val finalReasoning = reasoningBuilder.toString().trim()
                return@withContext if (finalContent.isNotBlank()) {
                    AiChatStreamResult(reasoning = finalReasoning, content = finalContent)
                } else {
                    Log.e(TAG, "Empty content in final response")
                    null
                }
            }

            // 达到最大轮次
            Log.w(TAG, "Agent loop reached max rounds ($MAX_ROUNDS)")
            val finalContent = contentBuilder.toString().trim()
            if (finalContent.isNotBlank()) {
                AiChatStreamResult(reasoning = reasoningBuilder.toString().trim(), content = finalContent)
            } else null

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agent loop error: ${e.message}", e)
            val partialContent = contentBuilder.toString().trim()
            if (partialContent.isNotBlank()) {
                AiChatStreamResult(reasoning = reasoningBuilder.toString().trim(), content = partialContent)
            } else null
        }
    }
}