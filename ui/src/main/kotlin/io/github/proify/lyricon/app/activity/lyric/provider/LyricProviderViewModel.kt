/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.provider

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proify.lyricon.app.util.defaultSharedPreferences
import io.github.proify.lyricon.app.activity.lyric.pkg.sheet.AppCache
import io.github.proify.lyricon.app.util.SignatureValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * 歌词提供者视图模型 (ViewModel)
 * 负责异步扫描、验证系统中的歌词插件，并将其处理为 UI 可直接渲染的分组状态。
 */
class LyricProviderViewModel(application: Application) : AndroidViewModel(application) {

    private val packageManager: PackageManager = application.packageManager

    /**
     * 向 UI 暴露的最终分组数据流。
     * 采用原子化更新，避免增量更新导致的 LazyColumn 闪烁和重排。
     */
    private val _groupedModules = MutableStateFlow<List<ModuleCategory>>(emptyList())
    val groupedModules: StateFlow<List<ModuleCategory>> = _groupedModules.asStateFlow()

    // 内部真实的加载状态
    private val _isInternalLoading = MutableStateFlow(false)

    /**
     * UI 层观察的加载状态 (带有防闪烁延迟逻辑)
     */
    var showLoading by mutableStateOf(false)
        private set

    /**
     * 列表视图模式 (精简/完整)
     */
    var listStyle by mutableIntStateOf(
        application.defaultSharedPreferences.getInt("activity_provider_list_style", ViewMode.SHORT)
    )

    /**
     * 标记是否因 Android 11+ 包可见性限制导致无法查询应用列表
     */
    var noQueryPermission by mutableStateOf(false)

    /**
     * 信任签名源常量定义
     * 集中管理认证插件的 Hash 值，便于溯源与安全审计
     */
    object TrustedSignatures {
        const val DEFAULT = "d75a43f76dbe80d816046f952b8d0f5f7abd71c9bd7b57786d5367c488bd5816"
        const val TRANTOR = "ba86f0c1f52d0f6a24e1b9a63eade0e8b80e7b9e20b8ef068da2e39c7b6e7b49"
        const val SALT_PLAYER = "8488d67a39978f84fd876510e0acb85e3a0504b90fbd56f11beb2123e285fa78"
        const val FLAMINGO = "4ca5ff4e5bf8418b45a8ecb46ddb91b6403ab7cc4b1a4de7e58fba12f54278e6"

        val ALL_CERTIFIED = arrayOf(DEFAULT, TRANTOR, SALT_PLAYER, FLAMINGO)
    }

    private companion object {
        // 超过此时间才通知 UI 显示 Loading 视图，避免极快加载时的瞬间闪屏
        private const val LOADING_DELAY_MS = 0L
    }

    init {
        observeLoadingState()
    }

    /**
     * 监听内部加载状态，处理延迟显示逻辑
     */
    private fun observeLoadingState() {
        viewModelScope.launch {
            _isInternalLoading.collect { isLoading ->
                if (isLoading) {
                    delay(LOADING_DELAY_MS)
                    // 如果延迟后仍在加载，才显示加载圈
                    if (_isInternalLoading.value) showLoading = true
                } else {
                    showLoading = false
                }
            }
        }
    }

    /**
     * 核心加载逻辑：扫描、验证、排序、分组全部在后台线程原子化完成
     * @param otherLabel 当地化字符串中“其他”分类的兜底文案
     */
    fun loadProviders(otherLabel: String) {
        viewModelScope.launch {
            _isInternalLoading.value = true

            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val getSignFlag = PackageManager.GET_SIGNING_CERTIFICATES
                    val packageInfos = packageManager.getInstalledPackages(
                        PackageManager.GET_META_DATA or getSignFlag
                    )

                    // 权限校验判断
                    if (packageInfos.size <= 1) {
                        withContext(Dispatchers.Main) { noQueryPermission = true }
                    }

                    // 1. 过滤与元数据解析
                    val validModules = packageInfos
                        .filter { isValidModule(it) }
                        .mapNotNull { processPackage(it) }

                    if (validModules.isEmpty()) {
                        _groupedModules.emit(emptyList())
                        return@withContext
                    }

                    val collator = Collator.getInstance(Locale.getDefault())

                    // 2. 预排序：认证状态优先，其次按标签本地化首字母排序
                    val sortedModules = validModules.sortedWith { m1, m2 ->
                        if (m1.isCertified != m2.isCertified) {
                            m2.isCertified.compareTo(m1.isCertified)
                        } else {
                            collator.compare(m1.label, m2.label)
                        }
                    }

                    // 3. 分组处理：确保各个分类内部也有序，且“其他”分类始终排在最后
                    val finalGroups = ModuleHelper.categorizeModules(sortedModules, otherLabel)
                        .map { category ->
                            category.copy(items = category.items.sortedWith { a, b ->
                                collator.compare(a.label, b.label)
                            })
                        }.sortedWith { c1, c2 ->
                            when {
                                c1.name == otherLabel -> 1
                                c2.name == otherLabel -> -1
                                else -> collator.compare(c1.name, c2.name)
                            }
                        }

                    // 4. 一次性推送最终状态，UI 仅需无脑渲染
                    _groupedModules.emit(finalGroups)

                } catch (e: Exception) {
                    e.printStackTrace()
                    _groupedModules.emit(emptyList())
                } finally {
                    _isInternalLoading.value = false
                }
            }
        }
    }

    /**
     * 校验应用包是否为合规的插件模块 (非系统应用且包含特定 meta-data)
     */
    private fun isValidModule(packageInfo: PackageInfo): Boolean {
        val appInfo = packageInfo.applicationInfo ?: return false
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return !(isSystem || isUpdatedSystem) && appInfo.metaData?.getBoolean("lyricon_module") == true
    }

    /**
     * 处理单个安装包，提取模块元数据、进行签名认证并缓存基础图标信息
     */
    private fun processPackage(packageInfo: PackageInfo): LyricModule? {
        return try {
            val appInfo = packageInfo.applicationInfo ?: return null
            val metaData = appInfo.metaData ?: return null
            val label = appInfo.loadLabel(packageManager).toString()

            // 图标与名称缓存，降低渲染阶段的跨进程 Binder 通信成本
            AppCache.cacheLabel(packageInfo.packageName, label)
            if (AppCache.getCachedIcon(packageInfo.packageName) == null) {
                appInfo.loadIcon(packageManager)?.let { AppCache.cacheIcon(packageInfo.packageName, it) }
            }

            val isCertified = SignatureValidator.validateSignature(
                packageInfo,
                *TrustedSignatures.ALL_CERTIFIED
            )

            LyricModule(
                packageInfo = packageInfo,
                description = metaData.getString("lyricon_module_description"),
                homeUrl = metaData.getString("lyricon_module_home"),
                category = metaData.getString("lyricon_module_category"),
                author = metaData.getString("lyricon_module_author"),
                isCertified = isCertified,
                tags = extractTags(appInfo, metaData),
                label = label
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 解析插件的标签元数据
     */
    private fun extractTags(appInfo: ApplicationInfo, metaData: Bundle): List<ModuleTag> {
        val tagsResId = metaData.getInt("lyricon_module_tags")
        val rawTags = if (tagsResId != 0) {
            runCatching {
                packageManager.getResourcesForApplication(appInfo).getStringArray(tagsResId).toList()
            }.getOrDefault(emptyList())
        } else {
            metaData.getString("lyricon_module_tags")?.let { listOf(it) } ?: emptyList()
        }
        return rawTags.mapNotNull { tagKey ->
            if (tagKey.isBlank()) return@mapNotNull null
            ModuleHelper.getPredefinedTag(tagKey) ?: ModuleTag(title = tagKey)
        }
    }
}