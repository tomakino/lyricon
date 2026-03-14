/*
 * Copyright 2026 nanguaguag (by Codex)
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.util.Log
import io.github.proify.android.extensions.json
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.extensions.deepCopy
import io.github.proify.lyricon.xposed.systemui.Directory
import io.github.proify.lyricon.xposed.systemui.util.LyricPrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object AutoTranslationManager {
    private const val TAG = "AutoTranslationManager"
    // 缓存目录与文件名
    private const val CACHE_DIR_NAME = "_translation"
    private const val CACHE_FILE_NAME = "llm_translation_cache.json"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    @Volatile
    // 最大缓存歌词条目数量（默认 5000）
    private var currentMaxCacheSize = 5000

    // 用于短期同步控制
    private val lock = Any()
    // 单线程队列执行网络请求与比较耗时的翻译工作，避免并发请求冲突
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    // 内存 LRU 缓存（访问顺序 true）
    private val memoryCache = object :
        LinkedHashMap<String, String>(currentMaxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            val remove = size > currentMaxCacheSize
            if (remove) {
                Log.i(TAG, "LRU remove eldest entry; new size=$size, max=$currentMaxCacheSize")
            }
            return remove
        }
    }

    @Volatile
    private var cacheLoaded = false

    /**
     * 获取缓存文件路径（app data 下的子目录）。
     * 确保目录存在（若不存在则尝试创建），并返回 File 对象。
     */
    private fun getCacheFile(): File {
        val dir = Directory.getPackageDataDir(CACHE_DIR_NAME)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            Log.i(TAG, "create cache dir=${dir.absolutePath}, created=$created")
        } else {
            Log.d(TAG, "cache dir exists=${dir.absolutePath}")
        }
        val file = File(dir, CACHE_FILE_NAME)
        Log.d(TAG, "cache file resolved=${file.absolutePath}")
        return file
    }

    /**
     * 延迟加载磁盘缓存到内存（只加载一次）。
     * 读取 JSON 并放入内存 cache。
     */
    private fun ensureCacheLoaded() {
        if (cacheLoaded) {
            Log.d(TAG, "ensureCacheLoaded: already loaded, skip")
            return
        }
        synchronized(lock) {
            if (cacheLoaded) {
                Log.d(TAG, "ensureCacheLoaded: already loaded inside lock, skip")
                return
            }
            val cacheFile = getCacheFile()
            if (cacheFile.exists()) {
                runCatching {
                    Log.i(TAG, "Loading translation cache from disk: ${cacheFile.absolutePath}")
                    val content = cacheFile.readText(Charsets.UTF_8)
                    val loaded = json.decodeFromString<Map<String, String>>(content)
                    memoryCache.putAll(loaded)
                    Log.i(TAG, "Loaded cache entries=${loaded.size}, memoryCacheSize=${memoryCache.size}")
                }.onFailure {
                    Log.w(TAG, "load cache failed (will continue with empty cache)", it)
                }
            } else {
                Log.i(TAG, "cache file not found, will start with empty cache")
            }
            cacheLoaded = true
        }
    }

    /**
     * 将当前内存 cache 快照序列化写回磁盘。
     * 以同步方式在 lock 上执行，避免并发写入冲突。
     */
    private fun persistCache() {
        synchronized(lock) {
            runCatching {
                val cacheFile = getCacheFile()
                val snapshot = LinkedHashMap(memoryCache) // 快照，避免并发修改时问题
                val jsonText = json.encodeToString(snapshot)
                cacheFile.writeText(jsonText, Charsets.UTF_8)
                Log.i(TAG, "persistCache: written entries=${snapshot.size} to ${cacheFile.absolutePath}")
            }.onFailure {
                Log.w(TAG, "persist cache failed", it)
            }
        }
    }

    /**
     * 构造缓存键：使用 model + 目标语言 + 文本（trim 后）生成 SHA-256 十六进制字符串。
     * 不在日志中输出原始文本；只记录 key 长度/摘要信息。
     */
    private fun buildCacheKey(
        provider: String,
        model: String,
        targetLanguage: String,
        text: String
    ): String {
        val raw = "$provider|$model|$targetLanguage|${text.trim()}"
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
    }

    /**
     * 检查内存缓存中是否已存在翻译。
     * 记录命中或未命中，用于统计和调试。
     */
    private fun getCachedTranslation(
        provider: String,
        model: String,
        targetLanguage: String,
        text: String
    ): String? {
        ensureCacheLoaded()
        val key = buildCacheKey(provider, model, targetLanguage, text)
        synchronized(lock) {
            val value = memoryCache[key]
            if (value != null) {
                Log.d(TAG, "cache HIT for key=${key.take(16)}..., textLen=${text.length}")
            } else {
                Log.d(TAG, "cache MISS for key=${key.take(16)}..., textLen=${text.length}")
            }
            return value
        }
    }

    /**
     * 将一批翻译结果写入内存缓存并持久化到磁盘。
     * pairs: List of (sourceText, translatedText)
     */
    private fun putCachedTranslations(
        provider: String,
        model: String,
        targetLanguage: String,
        pairs: List<Pair<String, String>>
    ) {
        if (pairs.isEmpty()) {
            Log.d(TAG, "putCachedTranslations: no pairs to put, return")
            return
        }
        ensureCacheLoaded()
        synchronized(lock) {
            for ((source, translated) in pairs) {
                val key = buildCacheKey(provider, model, targetLanguage, source)
                memoryCache[key] = translated
            }
            Log.i(TAG, "putCachedTranslations: added=${pairs.size}, memoryCacheSize=${memoryCache.size}")
        }
        // 持久化（异步调用此函数的路径会在外层线程中执行）
        persistCache()
    }

    /**
     * 异步翻译 Song（如果需要）。
     * - 如果设置不可用或歌词为空，立即回调原 song。
     * - 否则在单线程 executor 内执行 translateSong 并回调结果。
     */
    fun translateSongIfNeededAsync(
        song: Song?,
        settings: LyricPrefs.TranslationSettings,
        callback: (Song?) -> Unit
    ) {
        if (!settings.isUsable || song?.lyrics.isNullOrEmpty()) {
            Log.d(TAG, "translateSongIfNeededAsync: settings unusable or no lyrics -> return original")
            callback(song)
            return
        }

        executor.execute {
            val translatedSong = runCatching {
                translateSong(song, settings)
            }.getOrElse {
                Log.w(TAG, "translate song failed", it)
                song
            }
            Log.i(TAG, "translateSongIfNeededAsync: callback with translatedSongId=${translatedSong?.hashCode()}")
            callback(translatedSong)
        }
    }

    /**
     * 同步翻译逻辑：
     * - 先从缓存命中已有翻译
     * - 对未命中的 texts 一次性请求 OpenAI 翻译（batch）
     * - 将新翻译写入缓存并返回替换后的 Song 副本
     */
    private fun translateSong(
        song: Song,
        settings: LyricPrefs.TranslationSettings
    ): Song {
        // 更新最大缓存并清理
        if (settings.maxCacheSize > 0) {
            val oldSize = currentMaxCacheSize
            currentMaxCacheSize = settings.maxCacheSize
            if (currentMaxCacheSize < oldSize) {
                trimMemoryCache()
            }
        }

        val sourceLines = song.lyrics ?: return song
        if (sourceLines.isEmpty()) {

            Log.d(TAG, "translateSong: empty source lines -> return original")
            return song
        }

        Log.i(TAG, "translateSong: begin, totalLines=${sourceLines.size}")
        val translationByText = mutableMapOf<String, String>()
        val missedTexts = LinkedHashSet<String>()

        // 编译正则
        val ignoreRegex = runCatching {
            settings.ignoreRegex.takeIf { it.isNotEmpty() }?.toRegex()
        }.getOrNull()
        Log.i(TAG, "translateSong: ignoreRegex=$ignoreRegex")

        // 遍历每行，检查是否需要翻译
        for (line in sourceLines) {
            val text = line.text?.trim().orEmpty()
            if (text.isBlank()) continue
            if (!line.translation.isNullOrBlank()) continue

            val cached = getCachedTranslation(
                provider = settings.provider,
                model = settings.model,
                targetLanguage = settings.targetLanguage,
                text = text
            )
            if (!cached.isNullOrBlank()) {
                translationByText[text] = cached
                Log.i(TAG, "cached: $text -> $cached")
            } else {
                if (ignoreRegex != null && ignoreRegex.matches(text)) {
                    translationByText[text] = text
                    Log.i(TAG, "ignored: $text")
                } else {
                    missedTexts += text
                    Log.i(TAG, "missed: $text")
                }
            }
        }

        Log.i(TAG, "translateSong: cachedTranslations=${translationByText.size}, missed=${missedTexts.size}")
        // 请求未命中的批量翻译
        if (missedTexts.isNotEmpty()) {
            val newTranslations = requestTranslation(settings, missedTexts.toList())
            if (!newTranslations.isNullOrEmpty()) {
                val cachedPairs = mutableListOf<Pair<String, String>>()
                for (i in missedTexts.indices) {
                    val text = missedTexts.elementAt(i)
                    val translated = newTranslations.getOrNull(i)?.trim().orEmpty()
                    if (translated.isNotBlank()) {
                        translationByText[text] = translated
                        cachedPairs += text to translated
                    } else {
                        Log.w(TAG, "translateSong: returned blank translation for text index=$i, textLen=${text.length}")
                    }
                }
                putCachedTranslations(
                    provider = settings.provider,
                    model = settings.model,
                    targetLanguage = settings.targetLanguage,
                    pairs = cachedPairs
                )
            } else {
                Log.w(TAG, "translateSong: requestOpenAiTranslation returned null or empty result")
            }
        }

        if (translationByText.isEmpty()) {
            Log.d(TAG, "translateSong: no translations found/produced -> return original")
            return song
        }

        // 返回 song 的深拷贝并替换 translation 字段
        val copied = song.deepCopy()
        copied.lyrics = copied.lyrics?.map { line ->
            val text = line.text?.trim().orEmpty()
            if (line.translation.isNullOrBlank() && text.isNotBlank()) {
                val translated = translationByText[text]
                if (!translated.isNullOrBlank() && translated != text) line.copy(translation = translated) else line
            } else {
                line
            }
        }
        return copied
    }

    private fun requestTranslation(
        settings: LyricPrefs.TranslationSettings,
        texts: List<String>
    ): List<String>? {
        if (texts.isEmpty()) return emptyList()
        return when (settings.provider) {
            LyricPrefs.TRANSLATION_PROVIDER_GEMINI -> requestGeminiTranslation(settings, texts)
            LyricPrefs.TRANSLATION_PROVIDER_CLAUDE -> requestClaudeTranslation(settings, texts)
            LyricPrefs.TRANSLATION_PROVIDER_OPENAI,
            LyricPrefs.TRANSLATION_PROVIDER_DEEPSEEK,
            LyricPrefs.TRANSLATION_PROVIDER_QWEN -> requestOpenAiCompatibleTranslation(settings, texts)
            else -> null
        }
    }

    // ---------------------------
    // OpenAI request/response data classes
    // ---------------------------
    @Serializable
    private data class OpenAiRequest(
        val model: String,
        val messages: List<OpenAiMessage>,
        val temperature: Double = 0.2
    )

    @Serializable
    private data class OpenAiMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class OpenAiResponse(
        val choices: List<OpenAiChoice> = emptyList()
    )

    @Serializable
    private data class OpenAiChoice(
        val message: OpenAiChoiceMessage? = null
    )

    @Serializable
    private data class OpenAiChoiceMessage(
        val content: String? = null
    )

    private fun requestOpenAiCompatibleTranslation(
        settings: LyricPrefs.TranslationSettings,
        texts: List<String>
    ): List<String>? {
        val request = OpenAiRequest(
            model = settings.model,
            messages = listOf(
                OpenAiMessage("system", buildSystemPrompt(settings)),
                OpenAiMessage("user", buildUserPrompt(settings.targetLanguage, texts))
            )
        )

        Log.i(TAG, "requestOpenAiCompatibleTranslation: request=$request")

        val responseText = executePost(
            url = settings.baseUrl.trim(),
            body = json.encodeToString(request),
            headers = mapOf(
                "Authorization" to "Bearer ${settings.apiKey}",
                "Content-Type" to "application/json"
            ),
            logTag = settings.provider
        ) ?: return null

        val content = runCatching {
            json.decodeFromString<OpenAiResponse>(responseText)
                .choices.firstOrNull()?.message?.content
        }.getOrNull().orEmpty()

        Log.i(TAG, "requestOpenAiCompatibleTranslation: responseText=$responseText")

        return parseResponseTranslations(content, texts.size)
    }

    @Serializable
    private data class ClaudeRequest(
        val model: String,
        val max_tokens: Int,
        val system: String,
        val messages: List<ClaudeMessage>
    )

    @Serializable
    private data class ClaudeMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class ClaudeResponse(
        val content: List<ClaudeContent> = emptyList()
    )

    @Serializable
    private data class ClaudeContent(
        val type: String? = null,
        val text: String? = null
    )

    private fun requestClaudeTranslation(
        settings: LyricPrefs.TranslationSettings,
        texts: List<String>
    ): List<String>? {
        val request = ClaudeRequest(
            model = settings.model,
            max_tokens = 2048,
            system = buildSystemPrompt(settings),
            messages = listOf(
                ClaudeMessage("user", buildUserPrompt(settings.targetLanguage, texts))
            )
        )

        val responseText = executePost(
            url = settings.baseUrl.trim(),
            body = json.encodeToString(request),
            headers = mapOf(
                "x-api-key" to settings.apiKey,
                "anthropic-version" to ANTHROPIC_VERSION,
                "Content-Type" to "application/json"
            ),
            logTag = settings.provider
        ) ?: return null

        val content = runCatching {
            json.decodeFromString<ClaudeResponse>(responseText).content
                .firstOrNull { it.type == null || it.type == "text" }?.text
        }.getOrNull().orEmpty()

        return parseResponseTranslations(content, texts.size)
    }

    @Serializable
    private data class GeminiRequest(
        val contents: List<GeminiContent>,
        val generationConfig: GeminiGenerationConfig? = null
    )

    @Serializable
    private data class GeminiContent(
        val role: String? = null,
        val parts: List<GeminiPart>
    )

    @Serializable
    private data class GeminiPart(
        val text: String
    )

    @Serializable
    private data class GeminiGenerationConfig(
        val temperature: Double = 0.2
    )

    @Serializable
    private data class GeminiResponse(
        val candidates: List<GeminiCandidate> = emptyList()
    )

    @Serializable
    private data class GeminiCandidate(
        val content: GeminiContent? = null
    )

    private fun requestGeminiTranslation(
        settings: LyricPrefs.TranslationSettings,
        texts: List<String>
    ): List<String>? {
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(buildSystemPrompt(settings)))),
                GeminiContent(role = "user", parts = listOf(GeminiPart(buildUserPrompt(settings.targetLanguage, texts))))
            ),
            generationConfig = GeminiGenerationConfig()
        )
        val url = buildGeminiUrl(settings.baseUrl.trim(), settings.model)

        val responseText = executePost(
            url = url,
            body = json.encodeToString(request),
            headers = mapOf(
                "x-goog-api-key" to settings.apiKey,
                "Content-Type" to "application/json"
            ),
            logTag = settings.provider
        ) ?: return null

        val content = runCatching {
            json.decodeFromString<GeminiResponse>(responseText).candidates
                .firstOrNull()?.content?.parts?.firstOrNull()?.text
        }.getOrNull().orEmpty()

        return parseResponseTranslations(content, texts.size)
    }

    private fun buildGeminiUrl(baseUrl: String, model: String): String {
        if (baseUrl.contains(":generateContent")) return baseUrl
        return "${baseUrl.trimEnd('/')}/$model:generateContent"
    }

    private fun buildSystemPrompt(settings: LyricPrefs.TranslationSettings): String {
        return settings.customPrompt.replace("\$targetLanguage", settings.targetLanguage)
    }

    private fun buildUserPrompt(targetLanguage: String, texts: List<String>): String {
        val payload = json.encodeToString(texts)
        return buildString {
            appendLine("Translate each line individually.")
            appendLine("Output must be a JSON array with the same number of elements.")
            appendLine("Each output item corresponds to the same index in the input.")
            append("Input: $payload")
        }
    }

    /**
     * 执行 POST 请求（HttpURLConnection）。
     * - 不会在日志中打印 apiKey 明文
     * - 会记录 responseCode、以及 responseText 的前若干字符用于排查
     */
    private fun executePost(
        url: String,
        body: String,
        headers: Map<String, String>,
        logTag: String
    ): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 40_000
            doOutput = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        Log.d(TAG, "executePost: opening connection to $url")
        return runCatching {
            // 写出请求体
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            Log.d(TAG, "executePost: request body written, length=${body.length}")

            // 读取响应（区分 success / error stream）
            val responseCode = connection.responseCode
            val stream =
                if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

            Log.i(TAG, "executePost: responseCode=$responseCode, responseLen=${responseText?.length ?: 0}")
            if (responseCode !in 200..299) {
                Log.w(TAG, "[$logTag] request failed code=$responseCode, response=${responseText?.take(300)}")
                return@runCatching null
            }

            responseText
        }.getOrElse {
            Log.w(TAG, "[$logTag] request error", it)
            null
        }.also {
            connection.disconnect()
        }
    }

    /**
     * 解析模型返回的内容并尝试解码为 List<String>。
     * - 支持模型返回被 ``` ``` 包裹（如 ```json [... ]```）的情况
     * - 若解析成功且长度与 expectedSize 匹配则返回不可变列表
     */
    private fun parseResponseTranslations(content: String, expectedSize: Int): List<String>? {
        if (content.isBlank()) {
            Log.w(TAG, "parseResponseTranslations: content is blank")
            return null
        }

        // 预处理：去掉代码块标记并 trim
        val normalized = content.trim().let { raw ->
            if (raw.startsWith("```")) {
                raw.removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            } else {
                raw
            }
        }

        val parsed = runCatching {
            json.decodeFromString<List<String>>(normalized)
        }.getOrElse {
            // 如果直接解析失败，尝试在文本中抽取最外层的 [ ... ] 子串再解析
            val start = normalized.indexOf('[')
            val end = normalized.lastIndexOf(']')
            if (start >= 0 && end > start) {
                runCatching {
                    json.decodeFromString<List<String>>(normalized.substring(start, end + 1))
                }.getOrNull()
            } else {
                null
            }
        } ?: return null

        if (parsed.size != expectedSize) return null
        return Collections.unmodifiableList(parsed)
    }

    private fun trimMemoryCache() {
        synchronized(lock) {
            if (memoryCache.size <= currentMaxCacheSize) return
            val iterator = memoryCache.iterator()
            while (memoryCache.size > currentMaxCacheSize && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
            Log.i(TAG, "Trim cache complete: size=${memoryCache.size}, max=$currentMaxCacheSize")
        }
        // 缓存大小变化，持久化
        persistCache()
    }
}
