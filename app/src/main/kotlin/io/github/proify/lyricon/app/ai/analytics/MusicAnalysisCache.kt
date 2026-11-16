/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ai.analytics

import android.content.Context
import android.util.Log
import io.github.proify.android.extensions.json
import kotlinx.serialization.Serializable
import java.util.Collections

/**
 * 音乐画像分析结果缓存。
 *
 * 参照 [io.github.proify.lyricon.app.ai.explain.AiExplainCache] 的模式：
 * - 内存 LRU 缓存 + SharedPreferences 持久化
 * - 缓存 key 由数据版本哈希决定（播放记录变化后自动失效）
 * - TTL = 24 小时
 */
object MusicAnalysisCache {

    private const val TAG = "MusicAnalysisCache"
    private const val PREFS_NAME = "music_analysis_cache"
    private const val TTL_MS = 24 * 60 * 60 * 1000L // 24 小时

    private val memory = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedAnalysis>(4, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CachedAnalysis>?
            ): Boolean = size > 4
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

    /**
     * 获取缓存的分析结果。
     *
     * @param context Android Context
     * @param dataVersion 数据版本哈希（由 PlayHistoryStore.getDataVersionHash() 生成）
     * @return 缓存的分析结果，如果不存在或已过期则返回 null
     */
    fun get(context: Context, dataVersion: String): CachedAnalysis? {
        // 检查内存缓存
        memory[dataVersion]?.let {
            if (isFresh(it)) {
                Log.d(TAG, "Memory cache hit for version: $dataVersion")
                return it
            }
            Log.d(TAG, "Memory cache expired for version: $dataVersion")
            memory.remove(dataVersion)
        }
        // 检查磁盘缓存
        val raw = prefs(context).getString(dataVersion, null) ?: return null
        val cached = runCatching { json.decodeFromString<CachedAnalysis>(raw) }.getOrNull()
        return cached?.takeIf(::isFresh)?.also {
            Log.d(TAG, "Disk cache hit for version: $dataVersion")
            memory[dataVersion] = it
        }
    }

    /**
     * 保存分析结果到缓存。
     */
    fun put(context: Context, dataVersion: String, analysis: CachedAnalysis) {
        memory[dataVersion] = analysis
        prefs(context).edit().putString(dataVersion, json.encodeToString(analysis)).apply()
    }

    /**
     * 清除所有缓存。
     */
    fun clear(context: Context) {
        memory.clear()
        prefs(context).edit().clear().apply()
        Log.i(TAG, "Music analysis cache cleared")
    }

    private fun isFresh(cached: CachedAnalysis): Boolean =
        System.currentTimeMillis() - cached.time < TTL_MS
}

/**
 * 缓存的分析结果。
 *
 * @property reasoning 思考过程
 * @property content 分析正文
 * @property time 生成时间戳
 */
@Serializable
data class CachedAnalysis(
    val reasoning: String = "",
    val content: String = "",
    val time: Long = 0,
)