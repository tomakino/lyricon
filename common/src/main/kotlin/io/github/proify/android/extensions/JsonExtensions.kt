/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.android.extensions

import kotlinx.serialization.json.Json

val json: Json = Json {
    coerceInputValues = true     // 尝试转换类型
    ignoreUnknownKeys = true     // 忽略未知字段
    isLenient = true             // 宽松的 JSON 语法
    explicitNulls = false        // 不序列化 null
    encodeDefaults = false       // 不序列化默认值
}

/**
 * 根据类型获取默认的 JSON 字符串
 */
inline fun <reified T> defaultJson(): String {
    val clazz = T::class.java
    return if (
        clazz.isArray ||
        Collection::class.java.isAssignableFrom(clazz)
    ) "[]" else "{}"
}

/**
 * 安全解码 JSON，失败时返回默认值
 */
inline fun <reified T> Json.safeDecode(json: String?, default: T? = null): T {
    if (json.isNullOrBlank()) {
        return default ?: decodeFromString(defaultJson<T>())
    }

    return runCatching {
        decodeFromString<T>(json)
    }.getOrElse {
        default ?: decodeFromString(defaultJson<T>())
    }
}

/**
 * 安全编码为 JSON，失败时返回默认空结构
 */
inline fun <reified T> Json.safeEncode(value: T?): String {
    if (value == null) return defaultJson<T>()

    return runCatching {
        encodeToString(value)
    }.getOrElse {
        defaultJson<T>()
    }
}

/**
 * 将任意对象转换为 JSON 字符串
 */
inline fun <reified T> T.toJson(): String {
    return json.safeEncode(this)
}

/**
 * 从 JSON 字符串解析对象
 */
inline fun <reified T> String.fromJson(default: T? = null): T {
    return json.safeDecode(this, default)
}

/**
 * 尝试解析 JSON，返回可空结果
 */
inline fun <reified T> String.fromJsonOrNull(): T? {
    return runCatching {
        json.decodeFromString<T>(this)
    }.getOrNull()
}