/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ai.explain

import android.content.Context
import io.github.proify.android.extensions.json
import io.github.proify.lyricon.app.ai.explain.AiExplainCache.TTL_MS
import kotlinx.serialization.Serializable
import java.util.Collections

/** 缓存条目：解读结果 + 生成时间。 */
@Serializable
data class AiExplainCached(
    val reasoning: String = "",
    val content: String = "",
    val time: Long = 0L
)

/**
 * AI 音乐解读结果缓存：内存 LRU + SharedPreferences 持久化。
 *
 * 缓存 key 由调用方（[io.github.proify.lyricon.app.activity.lyric.AiExplainActivity]）生成，
 * 需包含歌曲元数据、歌词与模型信息，避免换模型后命中陈旧解读。
 * 条目超过 [TTL_MS] 视为过期，过期条目不命中（内存 / 磁盘一致）。
 */
object AiExplainCache {
    private const val PREFS_NAME = "ai_explain_cache"
    private const val MAX_MEMORY = 60
    private const val TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 天

    /** 读写可能来自不同线程（IO），使用同步包装保证并发安全。 */
    private val memory = Collections.synchronizedMap(
        object : LinkedHashMap<String, AiExplainCached>(MAX_MEMORY, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, AiExplainCached>?
            ): Boolean = size > MAX_MEMORY
        }
    )

    private var prefs: android.content.SharedPreferences? = null

    private fun prefs(context: Context): android.content.SharedPreferences {
        var p = prefs
        if (p == null) {
            p = synchronized(this) {
                val stored = prefs
                if (stored != null) {
                    stored
                } else {
                    val fresh = context.applicationContext.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                    prefs = fresh
                    fresh
                }
            }
        }
        return p
    }

    fun get(context: Context, key: String): AiExplainCached? {
        memory[key]?.let {
            if (isFresh(it)) return it
            memory.remove(key)
        }
        val raw = prefs(context).getString(key, null) ?: return null
        val cached = runCatching { json.decodeFromString<AiExplainCached>(raw) }.getOrNull()
        return cached?.takeIf(::isFresh)
    }

    fun put(context: Context, key: String, cached: AiExplainCached) {
        memory[key] = cached
        prefs(context).edit().putString(key, json.encodeToString(cached)).apply()
    }

    private fun isFresh(cached: AiExplainCached): Boolean =
        System.currentTimeMillis() - cached.time < TTL_MS
}
