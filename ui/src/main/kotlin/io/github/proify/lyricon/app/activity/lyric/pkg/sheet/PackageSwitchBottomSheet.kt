/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.sheet

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.SwipeableItem
import io.github.proify.lyricon.app.compose.custom.miuix.preference.CheckboxPreference
import io.github.proify.lyricon.app.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowBottomSheet

data class PackageSwitchUiState(
    val configureds: List<PackageItem> = emptyList(),
    val enableds: Set<String> = emptySet(),
    val selectedPackage: String = "",
    val isLoading: Boolean = true,
)

class PackageSwitchViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val packageManager: PackageManager = app.packageManager
    private val defaultAppInfo: ApplicationInfo = app.applicationInfo

    private val _uiState = MutableStateFlow(PackageSwitchUiState())
    val uiState: StateFlow<PackageSwitchUiState> = _uiState.asStateFlow()

    init {
        loadConfiguredPackages()
    }

    private fun loadConfiguredPackages() {
        viewModelScope.launch {
            val packages = loadConfiguredPackagesInternal()
            val selected =
                packages.firstOrNull()?.applicationInfo?.packageName
                    ?: defaultAppInfo.packageName
            val enableds = AppPrefs.getEnabledLyricPackageNames()

            _uiState.value =
                PackageSwitchUiState(
                    configureds = packages,
                    enableds = enableds,
                    selectedPackage = selected,
                    isLoading = false,
                )
        }
    }

    private suspend fun loadConfiguredPackagesInternal(): List<PackageItem> =
        withContext(Dispatchers.IO) {
            val packages = mutableListOf<PackageItem>()
            val configuredNames = AppPrefs.getLyricConfiguredPackageNames()

            configuredNames.forEach { packageName ->
                runCatching {
                    val info = packageManager.getApplicationInfo(packageName, 0)
                    packages.add(PackageItem(info))
                }.onFailure {
                    // 忽略无法加载的包
                }
            }

            // 确保默认应用在列表中
            if (packages.none { it.applicationInfo.packageName == defaultAppInfo.packageName }) {
                packages.add(PackageItem(applicationInfo = defaultAppInfo))
            }

            packages
        }

    fun selectPackage(packageName: String) {
        _uiState.value = _uiState.value.copy(selectedPackage = packageName)
    }

    fun setPackageEnabled(
        packageNames: Array<String>,
        enabled: Boolean,
    ) {
        val enabledPackages = AppPrefs.getEnabledLyricPackageNames().toMutableSet()

        if (enabled) {
            enabledPackages.addAll(packageNames)
        } else {
            enabledPackages.removeAll(packageNames.toSet())
        }

        AppPrefs.setEnabledLyricPackageNames(enabledPackages)
        _uiState.value = _uiState.value.copy(enableds = enabledPackages)
    }

    fun setConfiguredPackages(newPackages: List<PackageItem>) {
        val packageNames = newPackages.map { it.applicationInfo.packageName }.toSet()
        AppPrefs.setConfiguredPackageNames(packageNames)
        _uiState.value = _uiState.value.copy(configureds = newPackages)
    }

    fun removeConfiguredPackage(packageName: String) {
        // 禁用该包
        setPackageEnabled(arrayOf(packageName), false)

        // 从配置列表中移除
        val updatedPackageNames =
            AppPrefs
                .getLyricConfiguredPackageNames()
                .filterNot { it == packageName }
                .toSet()
        AppPrefs.setConfiguredPackageNames(updatedPackageNames)

        // 更新 UI 状态
        _uiState.value =
            _uiState.value.copy(
                configureds =
                    _uiState.value.configureds.filterNot {
                        it.applicationInfo.packageName == packageName
                    },
            )
    }

    fun getDefaultPackageName(): String = defaultAppInfo.packageName

    fun isDefaultPackage(packageName: String): Boolean = packageName == defaultAppInfo.packageName
}

// ============================================================================
// Composable - UI 层
// ============================================================================

@Composable
fun PackageSwitchBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onReset: (String) -> Unit,
    onEnable: (String, Boolean) -> Unit,
) {
    val viewModel: PackageSwitchViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

    val showAddSheet = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 当选中的包被移除时,自动选择默认包
    LaunchedEffect(state.configureds, state.selectedPackage) {
        val selectedExists =
            state.configureds.any {
                it.applicationInfo.packageName == state.selectedPackage
            }
        if (!selectedExists && state.configureds.isNotEmpty()) {
            val defaultPkg = viewModel.getDefaultPackageName()
            viewModel.selectPackage(defaultPkg)
            onSelect(defaultPkg)
        }
    }

    if (showAddSheet.value) {
        PackageSelectionBottomSheet(
            show = showAddSheet,
            initialSelectedPackages =
                state.configureds
                    .map { it.applicationInfo.packageName }
                    .toSet(),
            onSelectionChanged = { selectedPackages ->
                scope.launch {
                    handlePackageSelectionChanged(
                        viewModel = viewModel,
                        state = state,
                        selectedPackages = selectedPackages,
                        onSelect = onSelect,
                    )
                }
            },
        )
    }

    if (show) {
        PackageSwitchSheetContent(
            viewModel = viewModel,
            state = state,
            callbacks =
                PackageSwitchCallbacks(
                    onDismiss = onDismiss,
                    onAddClick = { showAddSheet.value = true },
                    onSelect = { pkg ->
                        viewModel.selectPackage(pkg)
                        onSelect(pkg)
                        onDismiss()
                    },
                    onRestore = onReset,
                    onEnable = { pkg, enabled ->
                        viewModel.setPackageEnabled(arrayOf(pkg), enabled)
                        onEnable(pkg, enabled)
                    },
                    onDelete = { pkg ->
                        handlePackageDelete(
                            viewModel = viewModel,
                            state = state,
                            packageToDelete = pkg,
                            onSelect = onSelect,
                            onReset = onReset,
                        )
                    },
                ),
        )
    }
}

// ============================================================================
// Helper Functions - 业务逻辑辅助函数
// ============================================================================

private fun handlePackageSelectionChanged(
    viewModel: PackageSwitchViewModel,
    state: PackageSwitchUiState,
    selectedPackages: Set<String>,
    onSelect: (String) -> Unit,
) {
    val currentList = state.configureds.toMutableList()

    // 找出被移除的包
    val removeList =
        currentList.filter {
            !selectedPackages.contains(it.applicationInfo.packageName)
        }

    // 禁用被移除的包
    if (removeList.isNotEmpty()) {
        viewModel.setPackageEnabled(
            removeList.map { it.applicationInfo.packageName }.toTypedArray(),
            false,
        )
        currentList.removeAll(removeList.toSet())
    }

    // 添加新选中的包
    selectedPackages.forEach { selected ->
        if (currentList.none { it.applicationInfo.packageName == selected }) {
            val context = viewModel.getApplication<Application>()
            runCatching {
                val appInfo = context.packageManager.getApplicationInfo(selected, 0)
                currentList.add(PackageItem(appInfo))
            }
        }
    }

    viewModel.setConfiguredPackages(currentList)

    // 如果当前选中的包被移除,切换到默认包
    if (removeList.any { it.applicationInfo.packageName == state.selectedPackage }) {
        val defaultPkg = viewModel.getDefaultPackageName()
        viewModel.selectPackage(defaultPkg)
        onSelect(defaultPkg)
    }
}

private fun handlePackageDelete(
    viewModel: PackageSwitchViewModel,
    state: PackageSwitchUiState,
    packageToDelete: String,
    onSelect: (String) -> Unit,
    onReset: (String) -> Unit,
) {
    // 不能删除默认包
    if (viewModel.isDefaultPackage(packageToDelete)) {
        return
    }

    viewModel.removeConfiguredPackage(packageToDelete)
    onReset(packageToDelete)

    // 如果删除的是当前选中的包,切换到默认包
    if (state.selectedPackage == packageToDelete) {
        val defaultPkg = viewModel.getDefaultPackageName()
        viewModel.selectPackage(defaultPkg)
        onSelect(defaultPkg)
    }
}

// ============================================================================
// UI Components
// ============================================================================

data class PackageSwitchCallbacks(
    val onDismiss: () -> Unit,
    val onAddClick: () -> Unit,
    val onSelect: (String) -> Unit,
    val onRestore: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onEnable: (String, Boolean) -> Unit,
)

/**
 * PackageListItem 的状态和回调封装
 */
data class PackageItemState(
    val item: PackageItem,
    val isSelected: Boolean,
    val isEnabled: Boolean,
    val displayEnable: Boolean,
)

data class PackageItemCallbacks(
    val onSelect: () -> Unit,
    val onRestore: () -> Unit,
    val onDelete: () -> Unit,
    val onEnable: (Boolean) -> Unit,
)

@Composable
private fun PackageSwitchSheetContent(
    viewModel: PackageSwitchViewModel,
    state: PackageSwitchUiState,
    callbacks: PackageSwitchCallbacks,
) {
    val packageItems = state.configureds
    val showState = remember { mutableStateOf(true) }

    WindowBottomSheet(
        show = showState.value,
        modifier = Modifier,
        title = stringResource(R.string.manage_app_styles),
        startAction = null,
        endAction = {
            Row {
                IconButton(onClick = callbacks.onAddClick) {
                    Icon(
                        modifier = Modifier.size(26.dp),
                        imageVector = MiuixIcons.AddCircle,
                        contentDescription = stringResource(R.string.action_add),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
        },
        backgroundColor = MiuixTheme.colorScheme.surface,
        onDismissRequest = {
            showState.value = false
            callbacks.onDismiss()
        },
        insideMargin = DpSize(0.dp, 0.dp),
        content = {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .overScrollVertical()
                        .animateContentSize(),
            ) {
                items(
                    items = packageItems,
                    key = { it.applicationInfo.packageName },
                ) { item ->
                    val packageName = item.applicationInfo.packageName
                    val isDefault = viewModel.isDefaultPackage(packageName)
                    val isEnabled = isDefault || state.enableds.contains(packageName)

                    PackageListItem(
                        state =
                            PackageItemState(
                                item = item,
                                isSelected = state.selectedPackage == packageName,
                                isEnabled = isEnabled,
                                displayEnable = !isDefault,
                            ),
                        callbacks =
                            PackageItemCallbacks(
                                onSelect = { callbacks.onSelect(packageName) },
                                onRestore = { callbacks.onRestore(packageName) },
                                onDelete = { callbacks.onDelete(packageName) },
                                onEnable = { enabled -> callbacks.onEnable(packageName, enabled) },
                            ),
                        modifier = Modifier.padding(bottom = 13.dp),
                    )
                }
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageListItem(
    state: PackageItemState,
    callbacks: PackageItemCallbacks,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    SwipeableItem(
        modifier = modifier,
        rightActions = { close ->
            TooltipBox(
                positionProvider =
                    TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above,
                    ),
                tooltip = {
                    PlainTooltip {
                        Text(
                            stringResource(R.string.action_reset),
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                },
                state = rememberTooltipState(),
            ) {
                Row {
                    IconButton(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .background(Color(color = 0xFFFF9800), CircleShape),
                        onClick = {
                            callbacks.onRestore()
                            close()
                            hapticFeedback.performHapticFeedback(
                                HapticFeedbackType.ContextClick,
                            )
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_reset_settings),
                            contentDescription = stringResource(R.string.action_reset),
                            tint = MiuixTheme.colorScheme.onError,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }

            if (!state.item.isDefault) {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above,
                        ),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                stringResource(R.string.action_delete),
                                color = Color.White,
                                fontSize = 14.sp,
                            )
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .background(MiuixTheme.colorScheme.error, CircleShape),
                        onClick = {
                            callbacks.onDelete()
                            close()
                            hapticFeedback.performHapticFeedback(
                                HapticFeedbackType.ContextClick,
                            )
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MiuixTheme.colorScheme.onError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
        },
    ) { closeAction ->
        Card(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
        ) {
            CheckboxPreference(
                startActions = {
                    AsyncAppIcon(
                        application = state.item.applicationInfo,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                },
                endActions = {
                    if (state.displayEnable) {
                        Switch(
                            checked = state.isEnabled,
                            onCheckedChange = callbacks.onEnable,
                        )
                    }
                },
                title = state.item.getLabel(),
                summary =
                    if (state.isEnabled) {
                        stringResource(R.string.status_enabled)
                    } else {
                        stringResource(R.string.status_not_enabled)
                    },
                checked = state.isSelected,
                onCheckedChange = {
                    callbacks.onSelect()
                    closeAction()
                }
            )
        }
    }
}
