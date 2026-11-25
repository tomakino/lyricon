/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app

import android.content.SharedPreferences
import android.util.Log
import io.github.proify.android.extensions.deflate
import io.github.proify.lyricon.app.util.getPrivateSharedPreferences
import io.github.proify.android.extensions.inflate
import io.github.proify.lyricon.app.util.AppPrefs
import io.github.proify.lyricon.app.util.AppPrefs.getLyricStylePrefNames
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.ai.core.AiConfigStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

object AppBackup {

    private const val TAG = "AppBackup"

    private val BLACKLIST_KEYS = listOf<String>(
        //  KEY_AI_CONFIG_API_KEY
    )

    fun export(outputStream: OutputStream): Boolean {
        val map = collectAllPrefs()

        return runCatching {
            val root = JSONObject()
            map.forEach { (name, entries) ->
                root.put(name, entriesToJsonObject(entries))
            }

            val bytes = root.toString()
                .toByteArray(Charsets.UTF_8)
                .deflate()

            outputStream.use { it.write(bytes) }
            true
        }.onFailure {
            Log.e(TAG, "Export failed", it)
        }.getOrDefault(false)
    }

    fun restore(input: InputStream): Boolean {
        return runCatching {
            val raw = input.use { it.readBytes() }
            val jsonText = raw
                .inflate()
                .toString(Charsets.UTF_8)

            val root = JSONObject(jsonText)

            applyJsonToPrefs(root)
            true
        }.onFailure {
            Log.e(TAG, "Restore failed", it)
        }.getOrDefault(false)
    }

    private fun collectAllPrefs(): Map<String, Map<String, *>> {
        val prefNames = getLyricStylePrefNames()

        val result = mutableMapOf<String, Map<String, *>>()

        val context = LyriconApp.get()
        prefNames.forEach { name ->
            val prefs = when {
                AppPrefs.isLyricStylePrefName(name) -> AppPrefs.getSharedPreferences(name)
                else -> context.getPrivateSharedPreferences(name)
            }
            val entries = prefs.all
            result[name] = entries
        }

        return result
    }

    private fun entriesToJsonObject(entries: Map<String, *>): JSONObject {
        val jo = JSONObject()
        entries.forEach { (k, v) ->
            if (k in BLACKLIST_KEYS) return@forEach

            // 多配置集合中内嵌 API Key：导出时做脱敏（移除 apiKey 字段），
            // 恢复后用户需自行重填，避免密钥随备份外泄。
            if (k == AiConfigStore.KEY_COLLECTION && v is String) {
                jo.put(k, sanitizeAiCollection(v))
            } else {
                when (v) {
                    is Set<*> -> jo.put(k, JSONArray(v))
                    else -> jo.put(k, v)
                }
            }
        }
        return jo
    }

    /** 递归移除 AI 配置集合 JSON 中所有 profile 的 apiKey。 */
    private fun sanitizeAiCollection(raw: String): String {
        return runCatching {
            val root = JSONObject(raw)
            val profiles = root.optJSONArray("profiles")
            if (profiles != null) {
                for (i in 0 until profiles.length()) {
                    val config = profiles.optJSONObject(i)?.optJSONObject("config")
                    config?.remove("apiKey")
                }
            }
            root.toString()
        }.getOrDefault(raw)
    }

    private fun applyJsonToPrefs(root: JSONObject) {
        val names = root.keys()
        val context = LyriconApp.get()

        while (names.hasNext()) {
            val prefsName = names.next().takeIf { it.isNotBlank() } ?: continue
            val prefsJson = root.optJSONObject(prefsName) ?: continue
            val prefs = when {
                AppPrefs.isLyricStylePrefName(prefsName) -> AppPrefs.getSharedPreferences(
                    prefsName
                )

                else -> context.getPrivateSharedPreferences(prefsName)
            }
            writeJsonToPrefs(prefs, prefsJson)
        }
    }

    private fun writeJsonToPrefs(prefs: SharedPreferences, json: JSONObject) {
        prefs.editCommit {
            //clear()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key in BLACKLIST_KEYS) continue

                when (val v = json.opt(key)) {
                    is Boolean -> putBoolean(key, v)
                    is String -> putString(key, v)
                    is Number -> putNumber(key, v)
                    is JSONArray -> putStringSet(key, jsonArrayToSet(v))
                }
            }
        }
    }

    private fun jsonArrayToSet(array: JSONArray): Set<String> {
        val s = HashSet<String>(array.length())
        for (i in 0 until array.length()) {
            s.add(array.optString(i))
        }
        return s
    }

    private fun SharedPreferences.Editor.putNumber(key: String, number: Number) {
        when (number) {
            is Int -> putInt(key, number)
            is Long -> putLong(key, number)
            is Float -> putFloat(key, number)
            is Double -> putFloat(key, number.toFloat())
            else -> {
                val lv = number.toLong()
                if (lv in Int.MIN_VALUE..Int.MAX_VALUE) putInt(key, lv.toInt()) else putLong(
                    key,
                    lv
                )
            }
        }
    }

}