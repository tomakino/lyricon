/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.R.attr.name
import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.app.bridge.AppBridge.LyricGesturePrefs
import io.github.proify.lyricon.common.StateSharedPreferences
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.PackageStyle
import io.github.proify.lyricon.xposed.ModuleEntry

/**
 * 歌词样式偏好管理器
 *
 * 负责管理基础样式、默认包样式以及各应用独立包样式的偏好数据。
 * 通过 [StateSharedPreferences] 追踪偏好变更，支持热更新样式配置。
 * 缓存偏好实例和包样式对象，避免重复创建。
 */
object LyricPrefs {
    private const val TAG = "LyricPrefs"

    /** 偏好实例缓存，Key 为偏好文件名，Value 为对应的 StateSharedPreferences */
    private val prefsCache = mutableMapOf<String, StateSharedPreferences>()

    /** 包样式缓存，Key 为包名，Value 为带自动更新机制的 PackageStyle 包装 */
    private val packageStyleCache = mutableMapOf<String, PackageStyleCache>()

    /** 当前前台应用包名，用于确定生效的包样式 */
    @Volatile
    var activePackageName: String? = null

    /** 全局偏好变更监听器，触发时刷新歌词视图配置 */
    private val globalSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            LyricViewController.applyConfigurationUpdate(getLyricStyle())
            Log.i(TAG, "global prefs changed")
        }

    /* ---------------- base style ---------------- */

    /** 基础样式偏好（全局生效） */
    val baseStylePrefs: StateSharedPreferences =
        createPrefs(AppBridge.LyricStylePrefs.PREF_NAME_BASE)

    /** 基础样式，访问时自动检测变更并重新加载 */
    val baseStyle: BasicStyle = BasicStyle().apply {
        load(baseStylePrefs)
    }
        get() {
            if (baseStylePrefs.hasChanged()) {
                baseStylePrefs.reload()
                field.load(baseStylePrefs)
            }
            return field
        }

    /* ---------------- default package style ---------------- */

    /** 默认包样式偏好，未匹配到特定应用时使用 */
    private val defaultPackageStylePrefs: StateSharedPreferences by lazy {
        getPackagePrefs(
            AppBridge.LyricStylePrefs.DEFAULT_PACKAGE_NAME
        )
    }

    /** 默认包样式，访问时自动检测变更并重新加载 */
    val defaultPackageStyle: PackageStyle = PackageStyle().apply {
        load(defaultPackageStylePrefs)
    }
        get() {
            if (defaultPackageStylePrefs.hasChanged()) {
                defaultPackageStylePrefs.reload()
                field.load(defaultPackageStylePrefs)
            }
            return field
        }

    /* ---------------- package manager ---------------- */

    /** 包管理器偏好（存储已启用包列表） */
    private val packageStyleManagerPrefs: StateSharedPreferences =
        createPrefs(AppBridge.LyricStylePrefs.PREF_NAME_PACKAGE_MANAGER)
        get() {
            return field.ensureLatest()
        }

    /** 当前生效的包样式：优先使用 activePackageName 对应的样式，否则回退到默认样式 */
    val activePackageStyle: PackageStyle
        get() = run {
            val pkg = activePackageName
            if (pkg != null && isPackageEnabled(pkg)) {
                getPackageStyle(pkg)
            } else {
                defaultPackageStyle
            }
        }

    /**
     * 检查指定包是否启用独立样式
     *
     * @param packageName 目标应用包名
     * @return true 表示该应用有独立样式配置
     */
    private fun isPackageEnabled(packageName: String): Boolean {
        packageStyleManagerPrefs.ensureLatest()
        return runCatching {
            packageStyleManagerPrefs
                .getStringSet(
                    AppBridge.LyricStylePrefs.KEY_ENABLED_PACKAGES,
                    emptySet()
                )
                ?.contains(packageName) ?: false
        }.getOrDefault(false)
    }

    /* ---------------- prefs cache ---------------- */

    /** 根据包名生成对应的偏好文件名 */
    private fun getPackagePrefName(packageName: String): String =
        AppBridge.LyricStylePrefs.getPackageStylePrefName(packageName)

    /**
     * 获取包对应的偏好实例（带缓存）
     *
     * @param packageName 应用包名
     * @return 该包的 StateSharedPreferences 实例
     */
    private fun getPackagePrefs(packageName: String): StateSharedPreferences {
        val prefName = getPackagePrefName(packageName)
        return prefsCache.getOrPut(prefName) {
            createPrefs(prefName)
        }
    }

    /**
     * 创建远程偏好包装实例，注册全局变更监听
     *
     * @param name 偏好文件名
     * @return 封装了远程 SharedPreferences 的 StateSharedPreferences 实例
     */
    private fun createPrefs(name: String): StateSharedPreferences {
        val service = ModuleEntry.instance
        val remotePrefs = service.getRemotePreferences(name)

        Log.i(TAG, "remotePrefs: $name, $remotePrefs")
        return StateSharedPreferencesWrapper(
            remotePrefs,
            globalSharedPreferenceChangeListener
        )
    }

    /* ---------------- package style cache ---------------- */

    /**
     * 包样式缓存项
     *
     * 封装偏好实例和对应的样式对象，访问时自动检测变更并重新加载
     */
    private class PackageStyleCache(
        private val prefs: StateSharedPreferences,
        private val style: PackageStyle
    ) {
        /**
         * 获取最新的包样式
         *
         * @return 已根据偏好状态同步的最新样式
         */
        fun getStyle(): PackageStyle {
            if (prefs.hasChanged()) {
                prefs.reload()
                style.load(prefs)
            }
            return style
        }
    }

    /**
     * 获取指定包的样式（带缓存，自动更新）
     *
     * @param packageName 目标应用包名
     * @return 该包对应的 PackageStyle 实例
     */
    fun getPackageStyle(packageName: String): PackageStyle {
        return packageStyleCache.getOrPut(packageName) {
            val prefs = getPackagePrefs(packageName)
            val style = PackageStyle().apply {
                load(prefs)
            }
            PackageStyleCache(prefs, style)
        }.getStyle()
    }

    /* ---------------- lyric style ---------------- */

    /**
     * 构建完整的歌词样式
     *
     * 组合基础样式与包样式，基础样式提供全局默认值，包样式提供应用级覆盖。
     *
     * @param packageName 目标应用包名，为空时使用当前活跃包样式
     * @return 合并后的 LyricStyle 实例
     */
    fun getLyricStyle(packageName: String? = null): LyricStyle {
        if (packageName.isNullOrBlank()) {
            return LyricStyle(baseStyle, activePackageStyle)
        }
        return LyricStyle(
            baseStyle,
            getPackageStyle(packageName)
        )
    }

    /* ---------------- gesture control ---------------- */

    /** 是否启用状态栏歌词手势控制 */
    val gestureEnabled: Boolean
        get() = baseStylePrefs.getBoolean(
            LyricGesturePrefs.KEY_ENABLED,
            LyricGesturePrefs.DEFAULT_ENABLED
        )

    /** 是否启用震动反馈(手势识别时震动) */
    val gestureHapticEnabled: Boolean
        get() = baseStylePrefs.getBoolean(
            LyricGesturePrefs.KEY_HAPTIC,
            LyricGesturePrefs.DEFAULT_HAPTIC
        )

    /**
     * 是否启用根窗口触摸拦截 ([io.github.proify.lyricon.xposed.systemui.hook.StatusBarTouchHooker])
     *
     * 关闭时拦截器即使已安装也不再接管任何触摸，歌词手势退回系统正常派发路径。
     * 默认开启；偏好缺失（老版本升级）时同样按开启处理，保证行为不回退。
     */
    val rootTouchHookEnabled: Boolean
        get() = baseStylePrefs.getBoolean(
            LyricGesturePrefs.KEY_ROOT_TOUCH_HOOK,
            LyricGesturePrefs.DEFAULT_ROOT_TOUCH_HOOK
        )

    /**
     * 读取指定手势对应的动作 ID,不存在或内容异常时返回 [default]
     *
     * @param key [LyricGesturePrefs.KEY_SWIPE_LEFT] 等手势偏好键
     * @param default 默认动作 ID
     */
    fun gestureAction(key: String, default: Int): Int =
        baseStylePrefs.getInt(key, default)

    /* ---------------- helper classes ---------------- */

    /**
     * [StateSharedPreferences] 的实现类
     *
     * 封装系统 [SharedPreferences] 并添加变更追踪能力，
     * 当远程偏好发生变化时标记 [isChanged]，并转发给外部监听器
     */
    private class StateSharedPreferencesWrapper(
        private val prefs: SharedPreferences,
        private val orderPrefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
    ) : StateSharedPreferences {

        /** 偏好是否已变更的标记 */
        private var isChanged = false

        init {
            prefs.registerOnSharedPreferenceChangeListener { p, key ->
                Log.i(
                    TAG,
                    "prefs changed: $name, key=$key, ${Thread.currentThread()},${Looper.myLooper()}"
                )
                isChanged = true
                orderPrefChangeListener.onSharedPreferenceChanged(p, key)
                Log.i(TAG, "called listener $orderPrefChangeListener")
            }
        }

        override fun hasChanged(): Boolean = isChanged

        override fun reload(): Boolean {
            if (isChanged) {
                isChanged = false
                return true
            }
            return false
        }

        override fun getAll(): Map<String, *> = prefs.all
        override fun getString(key: String?, defValue: String?): String? =
            prefs.getString(key, defValue)

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?
        ): MutableSet<String>? = prefs.getStringSet(key, defValues)

        override fun getInt(key: String?, defValue: Int): Int = prefs.getInt(key, defValue)
        override fun getLong(key: String?, defValue: Long): Long = prefs.getLong(key, defValue)
        override fun getFloat(key: String?, defValue: Float): Float = prefs.getFloat(key, defValue)
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            prefs.getBoolean(key, defValue)

        override fun contains(key: String?): Boolean = prefs.contains(key)
        override fun edit(): SharedPreferences.Editor = prefs.edit()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    /**
     * 确保偏好为最新状态，有变更时自动重新加载
     *
     * @receiver 待检查的 StateSharedPreferences 实例
     * @return 已同步到最新状态的同一实例
     */
    private fun StateSharedPreferences.ensureLatest(): StateSharedPreferences {
        if (hasChanged()) reload()
        return this
    }
}