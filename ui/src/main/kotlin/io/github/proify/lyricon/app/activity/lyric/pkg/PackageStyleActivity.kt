/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.lyric.AbstractLyricActivity
import io.github.proify.lyricon.app.activity.lyric.pkg.page.AnimPage
import io.github.proify.lyricon.app.activity.lyric.pkg.page.LogoPage
import io.github.proify.lyricon.app.activity.lyric.pkg.page.TextPage
import io.github.proify.lyricon.app.activity.lyric.pkg.sheet.AppCache
import io.github.proify.lyricon.app.activity.lyric.pkg.sheet.PackageSwitchBottomSheet
import io.github.proify.lyricon.app.compose.AppToolBarContainer
import io.github.proify.lyricon.app.compose.custom.miuix.basic.MiuixScrollBehavior
import io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior
import io.github.proify.lyricon.app.compose.custom.miuix.basic.TabRow
import io.github.proify.lyricon.app.compose.custom.miuix.basic.TabRowDefaults
import io.github.proify.lyricon.app.util.AppPrefs
import io.github.proify.lyricon.app.util.editCommit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val DEFAULT_PACKAGE_NAME: String = AppPrefs.DEFAULT_PACKAGE_NAME
private const val TAB_COUNT = 3

class PackageStyleViewModel(
    @field:SuppressLint("StaticFieldLeak") private val context: Context,
    private val onLyricStyleUpdate: () -> Unit,
) : ViewModel() {
    private val _showBottomSheet = MutableStateFlow(false)
    val showBottomSheet: StateFlow<Boolean> = _showBottomSheet.asStateFlow()

    private val _currentPackageName = MutableStateFlow(DEFAULT_PACKAGE_NAME)
    val currentPackageName: StateFlow<String> = _currentPackageName.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()

    private val _currentSharedPreferences = MutableStateFlow<SharedPreferences?>(null)
    val currentSharedPreferences: StateFlow<SharedPreferences?> =
        _currentSharedPreferences.asStateFlow()

    private var prefsChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    init {
        updateSharedPreferences(DEFAULT_PACKAGE_NAME)
    }

    fun showBottomSheet() {
        _showBottomSheet.value = true
    }

    fun hideBottomSheet() {
        _showBottomSheet.value = false
    }

    fun selectPackage(packageName: String) {
        _currentPackageName.value = packageName
        updateSharedPreferences(packageName)
    }

    fun resetPackage(packageName: String) {
        AppPrefs
            .getSharedPreferences(AppPrefs.getLyricPackagePrefName(packageName))
            .editCommit { clear() }
        _refreshTrigger.value++

        onLyricStyleUpdate()
    }

    fun onPackageEnabled() {
        onLyricStyleUpdate()
    }

    fun getPackageLabel(packageName: String): String =
        if (packageName == DEFAULT_PACKAGE_NAME) {
            context.getString(R.string.app_style_default)
        } else {
            runCatching {
                AppCache.getCachedLabel(packageName)?.let { return@runCatching it }
                val app = context.packageManager.getApplicationInfo(packageName, 0)
                val label = context.packageManager.getApplicationLabel(app).toString()
                AppCache.cacheLabel(packageName, label)
                label
            }.getOrElse { context.getString(R.string.app_style_default) }
        }

    private fun updateSharedPreferences(packageName: String) {
        _currentSharedPreferences.value?.let { sp ->
            prefsChangeListener?.let { listener ->
                sp.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        val newSp = AppPrefs.getSharedPreferences(AppPrefs.getLyricPackagePrefName(packageName))
        val newListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                onLyricStyleUpdate()
            }

        newSp.registerOnSharedPreferenceChangeListener(newListener)

        _currentSharedPreferences.value = newSp
        prefsChangeListener = newListener
    }

    override fun onCleared() {
        super.onCleared()
        _currentSharedPreferences.value?.let { sp ->
            prefsChangeListener?.let { listener ->
                sp.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }
}

class PackageStyleViewModelFactory(
    private val context: Context,
    private val onLyricStyleUpdate: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PackageStyleViewModel::class.java)) {
            return PackageStyleViewModel(context, onLyricStyleUpdate) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class PackageStyleActivity : AbstractLyricActivity() {
    private val viewModel: PackageStyleViewModel by viewModels {
        PackageStyleViewModelFactory(
            context = this,
            onLyricStyleUpdate = {
                //远程自动监听preferences更新，这里不需要实现
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PackageStyleScreen(viewModel = viewModel)
        }
    }
}

@Composable
internal fun PackageStyleScreen(viewModel: PackageStyleViewModel) {
    val showBottomSheet by viewModel.showBottomSheet.collectAsState()
    val currentPackageName by viewModel.currentPackageName.collectAsState()
    val refreshTrigger by viewModel.refreshTrigger.collectAsState()
    val currentSp by viewModel.currentSharedPreferences.collectAsState()
    val view = LocalView.current
    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState: HazeState = rememberHazeState()

    val title by remember(currentPackageName) {
        derivedStateOf {
            viewModel.getPackageLabel(
                currentPackageName,
            )
        }
    }

    AppToolBarContainer(
        title = title,
        canBack = true,
        actions = {},
        titleDropdown = true,
        titleOnClick = { viewModel.showBottomSheet() },
        scrollBehavior = scrollBehavior,
        hazeState = hazeState,
    ) { paddingValues ->

        PackageSwitchBottomSheet(
            show = showBottomSheet,
            onDismiss = { viewModel.hideBottomSheet() },
            onSelect = { packageName ->
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                viewModel.selectPackage(packageName)
            },
            onReset = { packageName ->
                viewModel.resetPackage(packageName)
            },
            onEnable = { _, _ ->
                viewModel.onPackageEnabled()
            }
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = paddingValues.calculateTopPadding()),
        ) {
            StyleTabRow(pagerState, scrollBehavior)
            Spacer(modifier = Modifier.height(16.dp))
            StyleContentPager(
                pagerState = pagerState,
                scrollBehavior = scrollBehavior,
                sharedPreferences = currentSp,
                refreshTrigger = refreshTrigger
            )
        }
    }
}

@Composable
private fun StyleTabRow(
    pagerState: PagerState,
    scrollBehavior: ScrollBehavior,
) {
    val tabs =
        listOf(
            stringResource(R.string.tab_style_text),
            stringResource(R.string.tab_style_icon),
            stringResource(R.string.tab_style_anim),
        )

    val selectedTabIndex = remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    TabRow(
        height = 43.dp,
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        tabs = tabs,
        selectedTabIndex = selectedTabIndex.intValue,
        onTabSelected = { index ->
            selectedTabIndex.intValue = index
            coroutineScope.launch {
                pagerState.animateScrollToPage(index)
            }
        },
        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { selectedTabIndex.intValue = it }
    }
}

@Composable
private fun StyleContentPager(
    pagerState: PagerState,
    scrollBehavior: ScrollBehavior,
    sharedPreferences: SharedPreferences?,
    refreshTrigger: Int
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { page -> "$page-$refreshTrigger" },
    ) { page ->
        if (sharedPreferences == null) {
            Box(modifier = Modifier.fillMaxSize())
            return@HorizontalPager
        }

        when (page) {
            0 -> TextPage(scrollBehavior, sharedPreferences)
            1 -> LogoPage(scrollBehavior, sharedPreferences)
            2 -> AnimPage(scrollBehavior, sharedPreferences)
        }
    }
}
