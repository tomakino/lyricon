/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.sheet

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.custom.miuix.preference.CheckboxPreference
import io.github.proify.lyricon.app.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private val MUSIC_APP_KEYWORDS =
    listOf("music", "audio", "player", "spotify", "youtube")

data class PackageSelectionUiState(
    val allApps: List<PackageItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
)

class PackageSelectionViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val packageManager: PackageManager = app.packageManager

    private val _uiState = MutableStateFlow(PackageSelectionUiState())
    val uiState: StateFlow<PackageSelectionUiState> = _uiState

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val apps =
                withContext(Dispatchers.IO) {
                    loadInstalledApplicationsOptimized(packageManager)
                }
            _uiState.value =
                _uiState.value.copy(
                    allApps = apps,
                    isLoading = false,
                )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSelectedPackages(selectedPackages: Set<String>) {
        _uiState.value =
            _uiState.value.copy(selectedPackages = selectedPackages)
    }

    fun toggleSelection(
        packageName: String,
        selected: Boolean,
    ) {
        val current = _uiState.value.selectedPackages.toMutableSet()
        if (selected) current.add(packageName) else current.remove(packageName)
        _uiState.value = _uiState.value.copy(selectedPackages = current)
    }
}

@Composable
fun PackageSelectionBottomSheet(
    show: MutableState<Boolean>,
    initialSelectedPackages: Set<String> = emptySet(),
    onSelectionChanged: (Set<String>) -> Unit,
    viewModel: PackageSelectionViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialSelectedPackages) {
        viewModel.setSelectedPackages(initialSelectedPackages)
        onSelectionChanged(viewModel.uiState.value.selectedPackages)
    }

    val categorized by remember(state.allApps) {
        derivedStateOf {
            categorizeApps(
                state.allApps,
            )
        }
    }
    val context = LocalContext.current
    val categories by remember(state.searchQuery, categorized) {
        derivedStateOf {
            buildCategories(
                context = context,
                categorized = categorized,
                query = state.searchQuery,
            )
        }
    }

    WindowBottomSheet(
        show = show.value,
        modifier = Modifier,
        title = stringResource(R.string.add_app_style),
        backgroundColor = MiuixTheme.colorScheme.surface,
        onDismissRequest = { show.value = false },
        outsideMargin = BottomSheetDefaults.outsideMargin,
        insideMargin = DpSize(0.dp, 0.dp),
        enableNestedScroll = false,
        content = {
            Column {
                PackageSearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                )
                if (state.isLoading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator()
                    }
                } else {
                    val context = LocalContext.current
                    AppList(
                        categories = categories,
                        selectedPackages = state.selectedPackages,
                        onSelection = { packageName, selected ->
                            viewModel.toggleSelection(packageName, selected)
                            onSelectionChanged(viewModel.uiState.value.selectedPackages)

                            if (context is Activity) {
                                context.window.decorView.performHapticFeedback(
                                    HapticFeedbackConstants.CONTEXT_CLICK,
                                )
                            }
                        },
                    )
                }
            }
        })
}

@Composable
private fun AppList(
    categories: List<AppCategory>,
    selectedPackages: Set<String>,
    onSelection: (String, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.overScrollVertical(),
    ) {
        categories.forEach { category ->
            item(key = "header-${category.name}") {
                SmallTitle(
                    text = category.name,
                    insideMargin = PaddingValues(28.dp, 0.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            itemsIndexed(
                items = category.items,
                key = { _, item -> item.applicationInfo.packageName },
            ) { _, item ->
                val checked by remember(selectedPackages) {
                    derivedStateOf { item.applicationInfo.packageName in selectedPackages }
                }

                PackageSelectionItem(
                    item = item,
                    isChecked = checked,
                    onCheckedChange = {
                        onSelection(item.applicationInfo.packageName, it)
                    },
                )
                Spacer(modifier = Modifier.height(13.dp))
            }

            item(key = "bottom_spacer-${category.name}") {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PackageSelectionItem(
    item: PackageItem,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 0.dp)
                .fillMaxWidth(),
    ) {
        CheckboxPreference(
            startActions = {
                AsyncAppIcon(
                    application = item.applicationInfo,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            },
            title = item.getLabel(),
            // summary = item.applicationInfo.packageName,
            checked = isChecked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PackageSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    var localQuery by remember { mutableStateOf(query) }
    val isExpanded = remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (localQuery != query) localQuery = query
    }

    SearchBar(
        modifier = Modifier.padding(bottom = 16.dp),
        insideMargin = DpSize(16.dp, 0.dp),
        inputField = {
            InputField(
                label = stringResource(id = R.string.hint_search),
                query = localQuery,
                onQueryChange = {
                    localQuery = it
                    onQueryChange(it)
                },
                onSearch = { isExpanded.value = false },
                expanded = isExpanded.value,
                onExpandedChange = { isExpanded.value = it },
                leadingIcon = {
                    Icon(
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                        imageVector = MiuixIcons.Search,
                        contentDescription = stringResource(R.string.action_search),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
            )
        },
        expanded = isExpanded.value,
        onExpandedChange = { isExpanded.value = it },
    ) {}
}

private data class AppCategory(
    val name: String,
    val items: List<PackageItem>,
)

private fun categorizeApps(apps: List<PackageItem>): Pair<List<PackageItem>, List<PackageItem>> {
    val (music, other) = apps.partition { isMusicApp(it.applicationInfo) }
    return music to other
}

private fun buildCategories(
    context: Context,
    categorized: Pair<List<PackageItem>, List<PackageItem>>,
    query: String,
): List<AppCategory> {
    val (music, other) = categorized
    val base =
        listOf(
            AppCategory(context.getString(R.string.section_music_apps), music),
            AppCategory(context.getString(R.string.section_other_apps), other),
        )
    if (query.isBlank()) return base
    val queryLower = query.trim().lowercase()
    return base.mapNotNull { category ->
        val filteredItems =
            category.items.filter {
                it.applicationInfo.packageName.contains(
                    queryLower,
                    ignoreCase = true,
                ) ||
                        it.getLabel().contains(queryLower, ignoreCase = true)
            }
        if (filteredItems.isNotEmpty()) {
            AppCategory(
                category.name,
                filteredItems,
            )
        } else {
            null
        }
    }
}

private fun isMusicApp(appInfo: ApplicationInfo): Boolean {
    if (appInfo.category == ApplicationInfo.CATEGORY_AUDIO) {
        return true
    }

    val packageNameLower = appInfo.packageName.lowercase()
    return MUSIC_APP_KEYWORDS.any { keyword ->
        packageNameLower.contains(keyword, ignoreCase = true)
    } || allMusicAppPackages.contains(packageNameLower)
}

fun loadInstalledApplicationsOptimized(packageManager: PackageManager): List<PackageItem> {
    val launcherIntent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

    val launcherPackages =
        packageManager
            .queryIntentActivities(launcherIntent, 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }

    return packageManager
        .getInstalledPackages(0)
        .asSequence()
        .filter { it.packageName != AppPrefs.DEFAULT_PACKAGE_NAME }
        .filter { it.packageName in launcherPackages }
        .mapNotNull { pkg ->
            pkg.applicationInfo?.let { appInfo ->
                PackageItem(applicationInfo = appInfo)
            }
        }.toList()
}
