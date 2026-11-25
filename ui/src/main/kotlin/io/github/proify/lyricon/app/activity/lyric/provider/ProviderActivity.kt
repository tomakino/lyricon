/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.provider

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import io.github.proify.lyricon.app.util.defaultSharedPreferences
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.BaseActivity
import io.github.proify.lyricon.app.activity.lyric.pkg.sheet.AsyncAppIcon
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.GoogleRainbowText
import io.github.proify.lyricon.app.compose.MaterialPalette
import io.github.proify.lyricon.app.compose.color
import io.github.proify.lyricon.app.compose.preference.rememberIntPreference
import io.github.proify.lyricon.app.util.AnimationEmoji
import io.github.proify.lyricon.app.util.LaunchBrowserCompose
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 歌词提供者管理界面
 * 采用响应式扁平结构渲染，避免复杂的嵌套重排。
 */
class LyricProviderActivity : BaseActivity() {

    private val viewModel: LyricProviderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LyricProviderContent() }
    }

    @Composable
    private fun LyricProviderContent() {
        val groupedModules by viewModel.groupedModules.collectAsState()
        val otherLabel = stringResource(R.string.other)

        // 生命周期挂载时请求数据，确保只请求一次
        LaunchedEffect(Unit) {
            viewModel.loadProviders(otherLabel)
        }

        AppToolBarListContainer(
            title = getString(R.string.activity_lyric_provider_services),
            canBack = true,
            // 关闭外层容器的空视图管理，由 LazyColumn 内部根据状态精细控制
            showEmpty = false,
            actions = { DisplayOptionsAction() }
        ) {
            // 状态 1: 加载中 (受延迟策略保护，不会瞬间闪现)
            if (viewModel.showLoading && groupedModules.isEmpty()) {
                item(key = "state_loading") {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingStateView()
                    }
                }
                return@AppToolBarListContainer
            }

            // 状态 2: 空视图 (加载完成且无数据)
            if (!viewModel.showLoading && groupedModules.isEmpty()) {
                item(key = "state_empty") {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            modifier = Modifier,
                            noQueryPermission = viewModel.noQueryPermission
                        )
                    }
                }
                return@AppToolBarListContainer
            }

            // 状态 3: 正常数据渲染
            groupedModules.forEachIndexed { index, category ->
                if (category.name.isNotBlank()) {
                    item(key = "header_${category.name}") {
                        SmallTitle(
                            text = category.name,
                            modifier = Modifier.animateItem(),
                            insideMargin = PaddingValues(
                                start = 28.dp,
                                end = 28.dp,
                                top = if (index > 0) 16.dp else 8.dp,
                                bottom = 8.dp
                            )
                        )
                    }
                }

                itemsIndexed(
                    items = category.items,
                    key = { _, it -> it.packageInfo.packageName }
                ) { index, module ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (index > 0) 16.dp else 0.dp)
                            .animateItem(
                                placementSpec = spring(stiffness = Spring.StiffnessLow)
                            )
                    ) {
                        ModuleCard(
                            module = module,
                            showTags = viewModel.listStyle == ViewMode.FULL
                        )
                    }
                }
            }

            item(key = "footer_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    /**
     * 专属加载动画视图组件
     */
    @Composable
    private fun LoadingStateView(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
            )
//            Spacer(modifier = Modifier.height(16.dp))
//            Text(
//                text = stringResource(R.string.loading),
//                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
//                fontSize = 14.sp
//            )
        }
    }

    /**
     * 列表为空时的视图展示
     */
    @Composable
    private fun EmptyStateView(modifier: Modifier, noQueryPermission: Boolean = false) {
        Column(
            modifier = modifier.overScrollVertical(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.Asset(AnimationEmoji.getAssetsFile("Neutral-face"))
            )
            LottieAnimation(
                modifier = Modifier.size(100.dp),
                composition = composition,
                iterations = LottieConstants.IterateForever
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(
                    if (noQueryPermission) R.string.message_query_app_permission_required
                    else R.string.no_provider_available
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }

    /**
     * 顶部工具栏行为与配置项
     */
    @Composable
    private fun DisplayOptionsAction() {
        val context = LocalContext.current
        val sharedPreferences = remember { context.defaultSharedPreferences }

        var listStylePref by rememberIntPreference(
            sharedPreferences,
            "activity_provider_list_style",
            ViewMode.SHORT
        )

        val options = remember {
            listOf(
                R.string.option_provider_list_short to ViewMode.SHORT,
                R.string.option_provider_list_full to ViewMode.FULL
            )
        }

        val showPopup = remember { mutableStateOf(false) }
        val selectedIndex by remember(listStylePref) {
            derivedStateOf { options.indexOfFirst { it.second == listStylePref }.coerceAtLeast(0) }
        }

        var shouldLaunch by remember { mutableStateOf(false) }
        if (shouldLaunch) {
            LaunchBrowserCompose(stringResource(R.string.provider_release_home))
            shouldLaunch = false
        }

        Row(modifier = Modifier.padding(end = 14.dp)) {
            IconButton(onClick = { shouldLaunch = true }) {
                Icon(
                    modifier = Modifier.size(26.dp),
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box {
                IconButton(onClick = { showPopup.value = true }) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.More,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }

                OverlayListPopup(
                    show = showPopup.value,
                    popupModifier = Modifier,
                    popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                    alignment = PopupPositionProvider.Align.TopEnd,
                    enableWindowDim = true,
                    onDismissRequest = { showPopup.value = false },
                    maxHeight = null,
                    minWidth = 200.dp,
                    renderInRootScaffold = true,
                    content = {
                        ListPopupColumn {
                            options.forEachIndexed { index, (labelRes, value) ->
                                DropdownImpl(
                                    text = stringResource(labelRes),
                                    optionSize = options.size,
                                    isSelected = index == selectedIndex,
                                    onSelectedIndexChange = {
                                        showPopup.value = false
                                        listStylePref = value
                                        viewModel.listStyle = value
                                    },
                                    index = index
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    /**
     * 单个提供者卡片渲染
     */
    @Composable
    private fun ModuleCard(module: LyricModule, showTags: Boolean) {
        val titleColor = BasicComponentDefaults.titleColor()
        val summaryColor = BasicComponentDefaults.summaryColor()
        val unknownText = stringResource(R.string.unknown)

        val secondaryInfo = remember(module.packageInfo.versionName, module.author) {
            getString(
                R.string.item_provider_info_secondary,
                module.packageInfo.versionName ?: unknownText,
                getString(R.string.author),
                module.author ?: unknownText
            )
        }

        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.Sink
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    module.packageInfo.applicationInfo?.let { appInfo ->
                        AsyncAppIcon(application = appInfo, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = module.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = titleColor.color(true)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (module.isCertified && viewModel.listStyle == ViewMode.FULL) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    painter = painterResource(R.drawable.verified_24px),
                                    contentDescription = null,
                                    tint = MaterialPalette.Green.Primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = secondaryInfo,
                                fontSize = 14.sp,
                                color = summaryColor.color(true)
                            )
                        }
                    }
                }

                if (!module.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = module.description,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = summaryColor.color(true),
                    )
                }

                // 配合 AnimatedVisibility，使状态切换时高度平滑过渡而非瞬间拉伸
                AnimatedVisibility(visible = module.tags.isNotEmpty() && showTags) {
                    ModuleTagsFlow(module.tags)
                }
            }
        }
    }

    /**
     * 流式布局渲染模块标签
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ModuleTagsFlow(tags: List<ModuleTag>) {
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            tags.forEach { tag ->
                val title =
                    if (tag.titleRes != -1) stringResource(tag.titleRes) else tag.title.orEmpty()
                Card(
                    modifier = Modifier.padding(end = 10.dp, top = 10.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    onClick = {}
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconSize = 18.dp
                        tag.iconRes?.let {
                            Icon(
                                painterResource(it),
                                null,
                                Modifier.size(iconSize),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        tag.imageVector?.let {
                            Image(it, null, Modifier.size(iconSize))
                            Spacer(Modifier.width(4.dp))
                        }

                        if (tag.isRainbow) {
                            GoogleRainbowText(
                                text = title,
                                style = MiuixTheme.textStyles.body2.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        } else {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
        }
    }
}
