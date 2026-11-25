/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.fontscaling.MathUtils.lerp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.BaseActivity
import io.github.proify.lyricon.app.compose.custom.miuix.basic.MiuixScrollBehavior
import io.github.proify.lyricon.app.compose.custom.miuix.basic.TopAppBar
import io.github.proify.lyricon.app.compose.theme.AppTheme
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun NavigationBackIcon(
    context: Context = LocalContext.current,
    backEvent: () -> Unit = {
        if (context is BaseActivity) context.onBackPressedDispatcher.onBackPressed()
    },
) {
    IconButton(onClick = { backEvent.invoke() }) {
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        Icon(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = if (isRtl) -1f else 1f
                },
            imageVector = MiuixIcons.Back,
            contentDescription = stringResource(R.string.action_back)
        )
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun BlurTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Transparent,
    largeTitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    horizontalPadding: Dp = 20.dp,
    hazeState: HazeState? = null,
    titleDropdown: Boolean = false,
    titleOnClick: () -> Unit = {},
) {
    val blurRadius = if (isSystemInDarkTheme()) 50.dp else 25.dp
    val surfaceColor = MiuixTheme.colorScheme.surface
    // HazeBlurStyle 按身份比较，必须 remember；折叠比例在 style 块内读取，由 haze 节点观察
    val hazeStyle = remember(blurRadius, surfaceColor, scrollBehavior) {
        HazeBlurStyle {
            blurRadius(blurRadius)
            noiseFactor(0f)
            backgroundColor(surfaceColor)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(
                        surfaceColor.copy(
                            alpha = if (scrollBehavior == null ||
                                scrollBehavior.state.collapsedFraction <= 0f
                            ) {
                                1f
                            } else {
                                lerp(1f, 0.76f, scrollBehavior.state.collapsedFraction)
                            }
                        )
                    )
                )
            )
        }
    }

    TopAppBar(
        title = title,
        modifier = if (hazeState != null) {
            modifier.hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = hazeStyle
            )
        } else {
            modifier
        },
        color = color,
        largeTitle = largeTitle,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        horizontalPadding = horizontalPadding,
        titleDropdown = titleDropdown,
        titleOnClick = titleOnClick
    )
}

@Composable
fun getCurrentTitle(): String? {
    val context = LocalContext.current
    return if (context is Activity) context.title?.toString() else null
}

@Composable
fun AppToolBarListContainer(
    context: Context = LocalContext.current,
    backEvent: () -> Unit = {
        (context as? BaseActivity)?.onBackPressedDispatcher?.onBackPressed()
    },
    title: Any? = getCurrentTitle(),
    canBack: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    scaffoldContent: @Composable () -> Unit = {},
    titleDropdown: Boolean = false,
    titleOnClick: () -> Unit = {},
    showEmpty: Boolean = false,
    empty: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    AppTheme {
        val hazeState = remember { HazeState() }
        val scrollBehavior = MiuixScrollBehavior()

        val titleText = remember(title) {
            when (title) {
                is Int -> null
                else -> title?.toString() ?: ""
            }
        }

        Scaffold(
            bottomBar = bottomBar,
            topBar = {
                BlurTopAppBar(
                    hazeState = hazeState,
                    navigationIcon = {
                        if (canBack) {
                            NavigationBackIcon(backEvent = backEvent)
                        }
                    },
                    title = if (title is Int) stringResource(title) else (titleText ?: ""),
                    scrollBehavior = scrollBehavior,
                    actions = actions,
                    titleDropdown = titleDropdown,
                    titleOnClick = titleOnClick
                )
            }
        ) { paddingValues ->
            scaffoldContent()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {

                AnimatedVisibility(
                    visible = !showEmpty,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .hazeSource(hazeState),
                        content = content
                    )
                }

                AnimatedVisibility(
                    visible = showEmpty,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    ) {
                        empty()
                    }
                }
            }
        }
    }
}

@Composable
fun AppToolBarContainer(
    context: Context = LocalContext.current,
    backEvent: () -> Unit = {
        if (context is BaseActivity) context.onBackPressedDispatcher.onBackPressed()
    },
    title: Any? = getCurrentTitle(),
    canBack: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    titleDropdown: Boolean = false,
    titleOnClick: () -> Unit = {},
    scrollBehavior: io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior = MiuixScrollBehavior(),
    hazeState: HazeState = rememberHazeState(),
    content: @Composable (PaddingValues) -> Unit,
) {
    AppTheme {
        Scaffold(
            bottomBar = bottomBar,
            topBar = {
                BlurTopAppBar(
                    hazeState = hazeState,
                    navigationIcon = {
                        if (canBack) NavigationBackIcon(
                            backEvent = backEvent
                        )
                    },
                    title = if (title is Int) stringResource(title) else title.toString(),
                    scrollBehavior = scrollBehavior,
                    actions = actions,
                    titleDropdown = titleDropdown,
                    titleOnClick = titleOnClick
                )
            }
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}


@Composable
fun IconActions(
    painter: Painter,
    contentDescription: String? = null,
    tint: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
) {
    Icon(
        modifier = Modifier
            .padding(
                start = 0.dp, end = 16.dp
            )
            .size(24.dp),
        painter = painter,
        contentDescription = contentDescription,
        tint = tint
    )
}


fun BasicComponentColors.color(enabled: Boolean): Color {
    return if (enabled) color else disabledColor
}