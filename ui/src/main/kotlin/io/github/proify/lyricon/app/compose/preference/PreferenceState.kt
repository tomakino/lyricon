/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.app.compose.preference

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.proify.lyricon.app.util.editCommit

/**
 * 将 [SharedPreferences] 中的数据映射为 Compose 的 [MutableState]。
 *
 * 该函数实现了双向绑定：
 * 1. 当 [SharedPreferences] 内部数据发生变更（无论来自何处）时，State 会自动同步。
 * 2. 当对返回的 State 进行赋值时，变更会通过 [editCommit] 同步持久化。
 *
 * @param T 数据类型。
 * @param sharedPreferences 配置存储实例，支持动态切换。
 * @param key 存储对应的键名。
 * @param defaultValue 缺省值。
 * @param getter 读取数据的具体实现。
 * @param setter 写入数据的具体实现。
 * @return 响应式的 [MutableState] 实例。
 */
@Composable
fun <T> rememberPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: T,
    getter: SharedPreferences.(String, T) -> T,
    setter: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor
): MutableState<T> {
    val currentGetter by rememberUpdatedState(getter)
    val currentSetter by rememberUpdatedState(setter)

    // 状态初始化，并在 key 或实例变更时重置
    val state = remember(sharedPreferences, key) {
        mutableStateOf(sharedPreferences.getter(key, defaultValue))
    }

    // 注册监听器，确保 listener 被 DisposableEffect 强引用以防止被 WeakHashMap 回收
    DisposableEffect(sharedPreferences, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
            if (changedKey == key) {
                val newValue = sp.currentGetter(key, defaultValue)
                if (state.value != newValue) {
                    state.value = newValue
                }
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(sharedPreferences, key) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    sharedPreferences.editCommit {
                        currentSetter(key, newValue)
                    }
                }

            override fun component1() = value
            override fun component2() = { v: T -> value = v }
        }
    }
}

/**
 * 构建并记住一个 [Boolean] 类型的配置状态。
 *
 * @param sharedPreferences 存储实例。
 * @param key 键名。
 * @param defaultValue 默认值，默认为 false。
 */
@Composable
fun rememberBooleanPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: Boolean = false
): MutableState<Boolean> =
    rememberPreference(
        sharedPreferences,
        key,
        defaultValue,
        SharedPreferences::getBoolean,
        SharedPreferences.Editor::putBoolean
    )

/**
 * 构建并记住一个 [String] 类型的配置状态。
 *
 * @param sharedPreferences 存储实例。
 * @param key 键名。
 * @param defaultValue 默认值，默认为 null。
 */
@Composable
fun rememberStringPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: String? = null
): MutableState<String?> =
    rememberPreference(
        sharedPreferences,
        key,
        defaultValue,
        SharedPreferences::getString,
        SharedPreferences.Editor::putString
    )

/**
 * 构建并记住一个 [Int] 类型的配置状态。
 *
 * @param sharedPreferences 存储实例。
 * @param key 键名。
 * @param defaultValue 默认值，默认为 0。
 */
@Composable
fun rememberIntPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: Int = 0
): MutableState<Int> =
    rememberPreference(
        sharedPreferences,
        key,
        defaultValue,
        SharedPreferences::getInt,
        SharedPreferences.Editor::putInt
    )

/**
 * 构建并记住一个 [Long] 类型的配置状态。
 *
 * @param sharedPreferences 存储实例。
 * @param key 键名。
 * @param defaultValue 默认值，默认为 0L。
 */
@Composable
fun rememberLongPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: Long = 0L
): MutableState<Long> =
    rememberPreference(
        sharedPreferences,
        key,
        defaultValue,
        SharedPreferences::getLong,
        SharedPreferences.Editor::putLong
    )

/**
 * 构建并记住一个 [Float] 类型的配置状态。
 *
 * @param sharedPreferences 存储实例。
 * @param key 键名。
 * @param defaultValue 默认值，默认为 0f。
 */
@Composable
fun rememberFloatPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: Float = 0f
): MutableState<Float> =
    rememberPreference(
        sharedPreferences,
        key,
        defaultValue,
        SharedPreferences::getFloat,
        SharedPreferences.Editor::putFloat
    )

/**
 * 构建并记住一个 [Set<String>] 类型的配置状态。
 *
 * @param sharedPreferences 存储实例。
 * @param key 键名。
 * @param defaultValue 默认值，默认为 emptySet()。
 */
@Composable
fun rememberStringSetPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: Set<String> = emptySet()
): MutableState<Set<String>?> =
    rememberPreference(
        sharedPreferences,
        key,
        defaultValue,
        SharedPreferences::getStringSet,
        SharedPreferences.Editor::putStringSet
    )