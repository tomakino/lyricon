/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.R.attr.scheme
import android.app.Activity
import android.content.ClipData
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.ai.analytics.CachedAnalysis
import io.github.proify.lyricon.app.ai.analytics.MusicAnalysisAgent
import io.github.proify.lyricon.app.ai.analytics.MusicAnalysisCache
import io.github.proify.lyricon.app.ai.analytics.MusicAnalysisPrompt
import io.github.proify.lyricon.app.ai.analytics.MusicAnalysisTools
import io.github.proify.lyricon.app.ai.analytics.PlayHistoryStore
import io.github.proify.lyricon.app.compose.md.MarkdownTable
import io.github.proify.lyricon.app.compose.theme.AppTheme
import io.github.proify.lyricon.app.util.AnimationEmoji
import io.github.proify.lyricon.app.util.LyricPrefs
import io.github.proify.lyricon.app.util.toast
import io.github.proify.lyricon.common.analytics.ThinkingProcessState
import io.github.proify.lyricon.common.analytics.ThinkingStep
import io.github.proify.lyricon.common.analytics.ToolCallRecord
import io.github.proify.lyricon.common.analytics.ToolCallStatus
import io.github.proify.lyricon.lyric.ai.core.AiConfig
import io.github.proify.lyricon.lyric.ai.core.AiConfigProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup

/** 智能滚动：判定"在底部"的像素阈值（约 30dp） */
private const val SCROLL_THRESHOLD_PX = 100

// ============================================================
// Activity
// ============================================================

class MusicAnalysisActivity : AbstractLyricActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        setContent {
            AppTheme { MusicAnalysisSheet() }
        }
    }
}

// ============================================================
// State
// ============================================================

@Stable
private class MusicAnalysisState {
    var contentText by mutableStateOf("")
    var contentVisible by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var showMenu by mutableStateOf(false)
    var thinkingExpanded by mutableStateOf(true)
    var thinkingProcess by mutableStateOf(ThinkingProcessState())

    val contentChannel = Channel<String>(Channel.UNLIMITED)
    val thinkingStepChannel = Channel<ThinkingStep>(Channel.UNLIMITED)

    var contentEpoch by mutableIntStateOf(0)
        private set
    private var job: Job? = null

    fun dispose() {
        job?.cancel(); job = null
        contentChannel.close()
        thinkingStepChannel.close()
    }

    fun startRequest(
        scope: CoroutineScope,
        context: android.content.Context,
        forceRefresh: Boolean = false
    ) {
        job?.cancel()
        contentText = ""
        contentVisible = false
        contentEpoch++
        isLoading = true
        thinkingProcess = ThinkingProcessState()

        job = scope.launch {
            val store = PlayHistoryStore(context)

            // 从 SystemUI 加载数据
            store.loadFromSystemUI()

            val tools = MusicAnalysisTools(store)

            // 缓存
            if (!forceRefresh) {
                val dataVersion = withContext(Dispatchers.IO) { store.getDataVersionHash() }
                val cached =
                    withContext(Dispatchers.IO) { MusicAnalysisCache.get(context, dataVersion) }
                if (cached != null && cached.content.isNotEmpty()) {
                    isLoading = false
                    if (cached.reasoning.isNotEmpty()) {
                        thinkingProcess = ThinkingProcessState(
                            steps = listOf(ThinkingStep.Reasoning(0, cached.reasoning)),
                            isThinking = false
                        )
                    }
                    contentVisible = true
                    contentChannel.trySend(cached.content)
                    return@launch
                }
            }

            val configs = resolveConfig()
            if (configs == null || !configs.isUsable) {
                isLoading = false
                contentVisible = true
                contentChannel.trySend("**请在「AI 实验室」中配置好 API 后再试**")
                return@launch
            }

            val customSystemPrompt = withContext(Dispatchers.IO) {
                LyricPrefs.basicStylePrefs.getString(
                    MusicAnalysisPrompt.KEY_CUSTOM_SYSTEM_PROMPT,
                    null
                )
            }

            val result = MusicAnalysisAgent.analyze(
                configs = configs,
                tools = tools,
                customSystemPrompt = customSystemPrompt,
                onContent = { chunk ->
                    if (chunk.isNotEmpty()) {
                        contentVisible = true
                        contentChannel.trySend(chunk)
                    }
                },
                onThinkingStep = { step ->
                    thinkingStepChannel.trySend(step)
                },
            )

            isLoading = false
            if (result == null) {
                contentVisible = true
                contentChannel.trySend("**AI 分析失败，请检查网络与配置后重试**")
            } else {
                withContext(NonCancellable + Dispatchers.IO) {
                    val dataVersion = store.getDataVersionHash()
                    MusicAnalysisCache.put(
                        context, dataVersion, CachedAnalysis(
                            reasoning = result.reasoning,
                            content = result.content,
                            time = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private fun resolveConfig(): AiConfig? {
        val provider = AiConfigProviderImpl(LyricPrefs.basicStylePrefs)
        return provider.getActiveConfig()?.takeIf { it.isUsable }
    }
}

// ============================================================
// Main Sheet
// ============================================================

@Composable
private fun MusicAnalysisSheet() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val scrollState = rememberScrollState()
    val state = remember { MusicAnalysisState() }

    // 智能滚动：持续监测用户是否在底部
    val isAtBottom = remember { mutableStateOf(true) }
    LaunchedEffect(scrollState) {
        snapshotFlow {
            scrollState.maxValue == 0 ||
                    scrollState.value >= scrollState.maxValue - SCROLL_THRESHOLD_PX
        }.collect { isAtBottom.value = it }
    }

    DisposableEffect(Unit) { onDispose { state.dispose() } }
    LaunchedEffect(Unit) { state.startRequest(scope, context) }

    // 消费思考步骤 Channel
    LaunchedEffect(Unit) {
        for (step in state.thinkingStepChannel) {
            state.thinkingProcess = state.thinkingProcess.let { current ->
                val updatedSteps = current.steps.toMutableList().apply {
                    val existingIdx = indexOfFirst {
                        it is ThinkingStep.ToolCall && step is ThinkingStep.ToolCall &&
                                it.record.id == step.record.id
                    }
                    if (existingIdx >= 0) set(existingIdx, step) else add(step)
                }
                current.copy(steps = updatedSteps, isThinking = state.isLoading)
            }
        }
    }

    val menuEntries = rememberMenuEntries(
        isLoading = state.isLoading,
        hasContent = state.contentText.isNotEmpty(),
        onCopy = {
            state.showMenu = false
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(
                        ClipData.newPlainText(
                            "music_analysis",
                            state.contentText
                        )
                    )
                )
            }
            toast("已复制到剪贴板")
        },
        onRetry = {
            state.showMenu = false
            state.startRequest(scope, context, forceRefresh = true)
        }
    )

    WindowBottomSheet(
        show = true,
        title = "音乐画像分析",
        endAction = {
            Box {
                IconButton(onClick = { state.showMenu = true }) {
                    Icon(imageVector = MiuixIcons.More, contentDescription = "更多操作")
                }
                WindowCascadingListPopup(
                    show = state.showMenu,
                    entries = menuEntries,
                    onDismissRequest = { state.showMenu = false },
                    alignment = PopupPositionProvider.Align.End,
                )
            }
        },
        onDismissRequest = { (context as? Activity)?.finish() },
        backgroundColor = MiuixTheme.colorScheme.surface,
        insideMargin = DpSize(0.dp, 0.dp),
        enableNestedScroll = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(10.dp))

            // 思考过程时间线（交错展示：推理文本 + 工具调用）
            if (state.thinkingProcess.hasSteps || state.isLoading) {
                ThinkingTimeline(
                    processState = state.thinkingProcess,
                    expanded = state.thinkingExpanded,
                    onExpandedChange = { state.thinkingExpanded = it },
                    isLoading = state.isLoading,
                    isContentReady = state.contentVisible,
                )
                Spacer(Modifier.height(16.dp))
            }

            // 加载指示器：仅在加载中且尚无内容时显示
            if (state.isLoading && !state.contentVisible) {
                AnalysisLoadingIndicator()
            }

            // 分析正文
            AnimatedVisibility(
                visible = state.contentVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 150)),
            ) {
                SelectionContainer {
                    AnalysisStreamingMarkdownText(
                        flow = state.contentChannel,
                        epoch = state.contentEpoch,
                        onDelta = {
                            state.contentText += it
                            // 智能滚动：在底部时自动跟随，上翻阅读时不打断
                            if (isAtBottom.value) {
                                scope.launch {
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ============================================================
// Menu
// ============================================================

@Composable
private fun rememberMenuEntries(
    isLoading: Boolean, hasContent: Boolean,
    onCopy: () -> Unit, onRetry: () -> Unit,
): List<DropdownEntry> {
    return remember(isLoading, hasContent) {
        listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(enabled = hasContent, text = "复制分析", onClick = onCopy),
                    DropdownItem(text = "重新分析", enabled = !isLoading, onClick = onRetry),
                )
            )
        )
    }
}

// ============================================================
// Thinking Timeline (核心组件)
// ============================================================

/**
 * 思考过程时间线：以交错形式展示推理文本和工具调用。
 *
 * 布局：
 *   推理文本片段 → 工具调用 → 推理文本片段 → 工具调用 → ... → 等待/完成
 */
@Composable
private fun ThinkingTimeline(
    processState: ThinkingProcessState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isLoading: Boolean,
    isContentReady: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(16.dp)
        ) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(24.dp),
                    painter = painterResource(R.drawable.psychology_24px),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                val title =  "思考过程"
                ShimmerText(
                    text = title,
                    isAnimating = isLoading && !isContentReady,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.keyboard_arrow_right_24px),
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.rotate(if (expanded) 90f else 0f),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // 时间线内容
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                processState.steps.forEachIndexed { index, step ->
                    when (step) {
                        is ThinkingStep.Reasoning -> TimelineReasoningItem(text = step.text)
                        is ThinkingStep.ToolCall -> TimelineToolCallItem(record = step.record)
                    }
                    if (index < processState.steps.size - 1) Spacer(Modifier.height(8.dp))
                }

            }
        }
    }
}

/** 时间线中的推理文本。 */
@Composable
private fun TimelineReasoningItem(text: String) {
    val stream = rememberStreamingMarkdownState()
    LaunchedEffect(text) { stream.append(text) }
    Markdown(
        streamingMarkdownState = stream,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        colors = markdownColors(),
        typography = markdownTypography(),
        padding = markdownPadding(block = 4.dp)
    )
}

/** 时间线中的工具调用。 */
@Composable
private fun TimelineToolCallItem(record: ToolCallRecord) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
        // insideMargin = PaddingValues(10.dp),
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(10.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()

            ) {
                when (record.status) {
                    ToolCallStatus.EXECUTING -> CircularProgressIndicator(
                        modifier = Modifier.size(
                            16.dp
                        )
                    )

                    ToolCallStatus.COMPLETED -> Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF4CAF50)
                    )

                    ToolCallStatus.FAILED -> Icon(
                        painter = painterResource(R.drawable.ic_info), contentDescription = null,
                        modifier = Modifier.size(16.dp), tint = Color(0xFFF44336)
                    )

                    else -> Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = record.displayName,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (record.isFinished && record.durationMs > 0) {
//                    Text(
//                        text = "${record.durationMs}ms",
//                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
//                        fontSize = 12.sp
//                    )
                }
                Icon(
                    painter = painterResource(R.drawable.keyboard_arrow_right_24px),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (expanded) 90f else 0f),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))

                if (record.arguments != "{}") {
                    Text(
                        text = "参数",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = record.arguments,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp, top = 3.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (record.result.isNotEmpty()) {
                    Text(
                        text = "结果",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = record.result.take(300) + if (record.result.length > 300) "..." else "",
                        color = MiuixTheme.colorScheme.onSurface, fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp, top = 3.dp)
                    )
                }

                if (record.error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = record.error ?: "", color = Color(0xFFF44336), fontSize = 12.sp)
                }
            }
        }
    }
}

// ============================================================
// Loading Indicator
// ============================================================

@Composable
private fun AnalysisLoadingIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val composition by rememberLottieComposition(
            LottieCompositionSpec.Asset(
                AnimationEmoji.getAssetsFile(
                    "Light-bulb"
                )
            )
        )
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "正在分析你的音乐画像...",
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            fontSize = 15.sp
        )
    }
}

// ============================================================
// Content Streaming
// ============================================================

@Composable
private fun AnalysisStreamingMarkdownText(
    flow: Channel<String>,
    epoch: Int,
    onDelta: (String) -> Unit,
    modifier: Modifier
) {
    key(epoch) {
        val streamingState = rememberStreamingMarkdownState()
        LaunchedEffect(Unit) {
            consumeStreamingChannel(flow) { text -> streamingState.append(text); onDelta(text) }
        }
        Markdown(
            streamingMarkdownState = streamingState,
            modifier = modifier,
            colors = markdownColors(),
            typography = markdownTypography(),
            padding = markdownPadding(block = 5.dp),
            dimens = markdownDimens(
                tableCellWidth= 130.dp,
                tableMaxWidth = 350.dp

            ),
            components = markdownComponents(
                table = {
                    MarkdownTable(it.content, it.node, style = it.typography.table)
                }
            )
        )
    }
}

private suspend fun consumeStreamingChannel(
    channel: Channel<String>,
    onBatch: suspend (String) -> Unit
) {
    try {
        while (true) {
            val first = withTimeoutOrNull(120L) { channel.receive() } ?: continue
            val batch = StringBuilder(first)
            while (true) {
                val extra = withTimeoutOrNull(50L) { channel.receive() } ?: break; batch.append(
                    extra
                )
            }
            onBatch(batch.toString())
        }
    } catch (_: ClosedReceiveChannelException) {
    }
}

// ============================================================
// Shimmer Effect
// ============================================================

@Composable
private fun ShimmerText(text: String, isAnimating: Boolean, modifier: Modifier = Modifier) {
    val shimmerBrush = rememberShimmerBrush(isAnimating)
    Box(modifier = modifier) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        if (isAnimating) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent { drawRect(brush = shimmerBrush) })
        }
    }
}

@Composable
private fun rememberShimmerBrush(isAnimating: Boolean): Brush {
    if (!isAnimating) return remember {
        Brush.linearGradient(
            listOf(
                Color.Transparent,
                Color.Transparent
            )
        )
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -100f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_translate"
    )
    return remember(translate) {
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent),
            start = Offset(translate - 100f, 0f), end = Offset(translate, 0f)
        )
    }
}

// ============================================================
// Markdown Styles
// ============================================================

@Composable
private fun markdownColors(): DefaultMarkdownColors {
    val s = MiuixTheme.colorScheme
    return remember(s) {
        DefaultMarkdownColors(
            text = s.onSurface,
            codeBackground = s.surfaceContainer,
            inlineCodeBackground = s.surfaceContainer,
            dividerColor = s.onSurfaceVariantSummary,
            tableBackground = s.surfaceContainer,
            highlightText = s.onSurface,
            highlightBackground = s.surfaceContainer
        )
    }
}

@Composable
private fun markdownTypography(): DefaultMarkdownTypography {
    val s = MiuixTheme.colorScheme
    return remember(s) {
        DefaultMarkdownTypography(
            h1 = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
            h2 = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
            h3 = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            h4 = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            h5 = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            h6 = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            text = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
            code = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
            inlineCode = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
            quote = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
            paragraph = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
            ordered = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
            bullet = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
            list = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
            textLink = TextLinkStyles(style = SpanStyle(color = s.primary)),
            table = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
            alertTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
    }
}