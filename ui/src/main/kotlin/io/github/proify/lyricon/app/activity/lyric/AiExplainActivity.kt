/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.app.Activity
import android.content.ClipData

import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import io.github.proify.android.extensions.md5
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.ai.explain.AiExplainCache
import io.github.proify.lyricon.app.ai.explain.AiExplainCached
import io.github.proify.lyricon.app.ai.explain.AiExplainClient
import io.github.proify.lyricon.app.ai.explain.AiExplainPrompt
import io.github.proify.lyricon.app.compose.theme.AppTheme
import io.github.proify.lyricon.app.util.AnimationEmoji
import io.github.proify.lyricon.app.util.AppPrefs
import io.github.proify.lyricon.app.util.toast
import io.github.proify.lyricon.lyric.ai.core.AiConfig
import io.github.proify.lyricon.lyric.ai.core.AiConfigProviderImpl
import io.github.proify.lyricon.lyric.ai.explain.AiExplainContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
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
import java.util.Locale

/**
 * AI 音乐解读（透明 Activity）
 *
 * 由状态栏歌词控制窗口(SystemUI 进程)启动：
 * 1. 读取歌曲元数据与歌词上下文(Intent extras)；
 * 2. 在本进程内通过 [AiExplainClient] 请求 OpenAI 兼容服务，SSE 流式解析；
 * 3. 用 miuix [WindowBottomSheet] 居中底部展示：
 *    - 歌曲信息
 *    - 思考过程(模型 reasoning_content/reasoning 增量)
 *    - 解读正文(可选中复制)
 *    - 内容最后面的「复制 / 重试」操作
 *
 * @author Tomakino
 * @since 2026
 */
class AiExplainActivity : AbstractLyricActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        setContent {
            AppTheme {
                AiExplainSheet(
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty(),
                    album = intent.getStringExtra(EXTRA_ALBUM).orEmpty(),
                    lyrics = intent.getStringExtra(EXTRA_LYRICS).orEmpty(),
                )
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = AiExplainContract.EXTRA_TITLE
        const val EXTRA_ARTIST = AiExplainContract.EXTRA_ARTIST
        const val EXTRA_ALBUM = AiExplainContract.EXTRA_ALBUM
        const val EXTRA_LYRICS = AiExplainContract.EXTRA_LYRICS
    }
}

/**
 * AI 解读状态持有者：集中管理所有可变状态，与 Composable 生命周期解耦。
 *
 * @param title 歌曲名
 * @param artist 歌手名
 * @param album 专辑名
 * @param lyrics 歌词内容
 */
@Stable
private class AiExplainState(
    private val title: String,
    private val artist: String,
    private val album: String,
    private val lyrics: String,
) {
    var contentText by mutableStateOf("")
    var reasoningVisible by mutableStateOf(false)
        private set
    var contentVisible by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var showMenu by mutableStateOf(false)
    var thinkingExpanded by mutableStateOf(false)

    // 增量经 Channel 单消费者串行消费（渲染树内消费，按窗口聚合后 append 到
    // streaming state）：不丢块、不重复、不重建整树——流式期间树稳定不闪烁。
    val reasoningChannel = Channel<String>(Channel.UNLIMITED)
    val contentChannel = Channel<String>(Channel.UNLIMITED)

    // 用于标识当前请求批次：重试时递增，使 key(epoch) 内的 LaunchedEffect 重建
    var contentEpoch by mutableIntStateOf(0)
        private set

    private var job: Job? = null

    /**
     * 清理资源：关闭 Channel 并取消正在进行的 Job。
     *
     * 在 Composable 销毁时调用，防止内存泄漏和协程泄漏。
     */
    fun dispose() {
        job?.cancel()
        job = null
        reasoningChannel.close()
        contentChannel.close()
    }

    fun startRequest(
        scope: CoroutineScope,
        context: android.content.Context,
        forceRefresh: Boolean = false
    ) {
        job?.cancel()
        contentText = ""
        reasoningVisible = false
        contentVisible = false
        contentEpoch++
        isLoading = true

        job = scope.launch {
            if (lyrics.isBlank()) {
                isLoading = false
                contentVisible = true
                contentChannel.trySend(context.getString(R.string.ai_explain_no_lyrics))
                return@launch
            }

            val configs = resolveConfig()
            if (configs == null || !configs.isUsable) {
                isLoading = false
                contentVisible = true
                contentChannel.trySend(context.getString(R.string.ai_explain_not_configured))
                return@launch
            }

            // 缓存 key 纳入模型与服务信息：更换模型/服务商后不命中旧解读
            val cacheKey = "${title}|${artist}|${lyrics}".md5()

            // 仅在首次加载读缓存；手动重试 = 强制重新调用 AI（成功后覆盖旧缓存）
            if (!forceRefresh) {
                val cached = withContext(Dispatchers.IO) {
                    AiExplainCache.get(context, cacheKey)
                }
                if (cached != null && cached.content.isNotEmpty()) {
                    isLoading = false
                    // 缓存命中：同样经通道单次投递，渲染路径唯一
                    if (cached.reasoning.isNotEmpty()) {
                        reasoningVisible = true
                        reasoningChannel.trySend(cached.reasoning)
                    }
                    contentVisible = true
                    contentChannel.trySend(cached.content)
                    return@launch
                }
            }

            // 读取自定义系统提示词
            val customSystemPrompt = withContext(Dispatchers.IO) {
                AppPrefs.lyricBasicStylePrefs.getString(
                    AiExplainPrompt.KEY_CUSTOM_SYSTEM_PROMPT,
                    null
                )
            }

            val result = AiExplainClient.stream(
                configs = configs,
                targetLanguage = Locale.getDefault().toLanguageTag(),
                customSystemPrompt = customSystemPrompt,
                title = title,
                artist = artist,
                album = album,
                lyrics = lyrics,
                onReasoning = { chunk ->
                    if (chunk.isNotEmpty()) {
                        reasoningVisible = true
                        reasoningChannel.trySend(chunk)
                    }
                },
                onContent = { chunk ->
                    if (chunk.isNotEmpty()) {
                        contentVisible = true
                        contentChannel.trySend(chunk)
                    }
                },
            )

            isLoading = false
            if (result == null) {
                contentVisible = true
                contentChannel.trySend(context.getString(R.string.ai_explain_failed))
            } else {
                // 使用 NonCancellable 确保缓存写入完成，即使 Activity 销毁
                withContext(NonCancellable + Dispatchers.IO) {
                    AiExplainCache.put(
                        context,
                        cacheKey,
                        AiExplainCached(
                            reasoning = result.reasoning,
                            content = result.content,
                            time = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    /**
     * 读取统一 AI 配置。
     *
     * 通过 [AiConfigProviderImpl] 从 [AiConfigStore] 获取当前激活的配置。
     * 如果配置不可用（未配置 API Key），直接返回 null 避免白转加载圈。
     */
    private fun resolveConfig(): AiConfig? {
        val prefs = AppPrefs.lyricBasicStylePrefs
        val provider = AiConfigProviderImpl(prefs)
        val config = provider.getActiveConfig()

        if (config == null || !config.isUsable) {
            return null
        }
        return config
    }
}

@Composable
private fun AiExplainSheet(title: String, artist: String, album: String, lyrics: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard: Clipboard = LocalClipboard.current
    val scrollState = rememberScrollState()

    val state = remember { AiExplainState(title, artist, album, lyrics) }

    // 在 Composable 销毁时清理资源，防止内存泄漏
    DisposableEffect(Unit) {
        onDispose { state.dispose() }
    }

    LaunchedEffect(Unit) { state.startRequest(scope, context) }

    // 右上角"更多"菜单：复制 / 重试
    val menuEntries = rememberMenuEntries(
        isLoading = state.isLoading,
        hasContent = state.contentText.isNotEmpty(),
        onCopy = {
            state.showMenu = false
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("explain", state.contentText))
                )
            }
            toast(context.getString(R.string.ai_explain_copied))
        },
        onRetry = {
            state.showMenu = false
            state.startRequest(scope, context, forceRefresh = true)
        }
    )

    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.ai_explain_title),
        endAction = {
            Box {
                IconButton(onClick = { state.showMenu = true }) {
                    Icon(
                        imageVector = MiuixIcons.More,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface
                    )
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
                .animateContentSize()
                .verticalScroll(scrollState)
        ) {
            // 顶部间距
            Spacer(Modifier.height(10.dp))

            // ---- 思考过程（可折叠，默认折叠；state 与消费循环常驻，折叠不丢内容） ----
            if (state.reasoningVisible) {
                ReasoningSection(
                    channel = state.reasoningChannel,
                    epoch = state.contentEpoch,
                    expanded = state.thinkingExpanded,
                    onExpandedChange = { state.thinkingExpanded = it },
                    isLoading = state.isLoading,
                )
                Spacer(Modifier.height(16.dp))
            }

            // ---- 等待 AI 响应：加载指示，避免页面空白 ----
            if (state.isLoading && !state.contentVisible) {
                LoadingIndicator()
            }

            // ---- 解读正文（增量 append 渲染：树稳定，段间距 16dp） ----
            if (state.contentVisible) {
                SelectionContainer {
                    AiStreamingMarkdownText(
                        flow = state.contentChannel,
                        epoch = state.contentEpoch,
                        onDelta = { state.contentText += it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }

            // 底部间距
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 构建菜单条目列表：复制解读 / 重试。
 */
@Composable
private fun rememberMenuEntries(
    isLoading: Boolean,
    hasContent: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
): List<DropdownEntry> {
    val copyText = stringResource(R.string.ai_explain_copy)
    val regenerateText = stringResource(R.string.ai_explain_regenerate)
    return remember(isLoading, hasContent, copyText, regenerateText) {
        listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        enabled = hasContent,
                        text = copyText,
                        onClick = onCopy,
                    ),
                    DropdownItem(
                        text = regenerateText,
                        enabled = !isLoading,
                        onClick = onRetry,
                    ),
                )
            )
        )
    }
}

/**
 * 思考过程卡片：可折叠，默认折叠；streaming state 常驻，折叠不丢内容。
 */
@Composable
private fun ReasoningSection(
    channel: Channel<String>,
    epoch: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isLoading: Boolean = false,
) {
    key(epoch) {
        val reasoningStream = rememberStreamingMarkdownState()
        // 消费 Channel 增量
        LaunchedEffect(Unit) {
            consumeStreamingChannel(channel) { text ->
                withContext(Dispatchers.Default) {
                    reasoningStream.append(text)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(
                        modifier = Modifier
                            .padding(start = 0.dp, end = 10.dp)
                            .size(24.dp),
                        painter = painterResource(R.drawable.psychology_24px),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )

                    Text(
                        text = stringResource(R.string.ai_explain_thinking),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(start = 0.dp)
                            .weight(1f)
                    )
                    Icon(
                        painter = painterResource(R.drawable.keyboard_arrow_right_24px),
                        contentDescription = null,
                        modifier = Modifier.rotate(if (expanded) 90f else 0f),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                // 仅控制 Markdown 显隐：streaming state 在 key(epoch) 内常驻，
                // 关闭折叠不会销毁消费循环与已渲染内容
                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Markdown(
                        streamingMarkdownState = reasoningStream,
                        modifier = Modifier.fillMaxWidth(),
                        colors = rememberAiMarkdownColors(),
                        typography = rememberAiMarkdownTypography(),
                        padding = markdownPadding(
                            block = 16.dp,
                            list = 12.dp,
                            listItemTop = 6.dp,
                            listItemBottom = 10.dp,
                            indentList = 12.dp,
                            listIndent = 12.dp
                        )
                    )
                }
            }
        }
    }
}

/**
 * 加载指示器：Lottie 动画，避免页面空白。
 */
@Composable
private fun LoadingIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val composition by rememberLottieComposition(
            LottieCompositionSpec.Asset(AnimationEmoji.getAssetsFile("Light-bulb"))
        )
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(96.dp),
        )
    }
}

/**
 * AI 解读 Markdown 流式渲染：
 * 从 [Channel] 单消费者串行消费增量（120ms 窗口聚合），append 到 streaming state——
 * 渲染树保持稳定、只更新尾部，因此不闪烁；空白增量（空格/换行/标点）原样保留。
 *
 * paragraph 块间距由 [markdownPadding] 显式设置（库默认 block=0 会挤成一团）。
 */
@Composable
private fun AiStreamingMarkdownText(
    flow: Channel<String>,
    epoch: Int,
    onDelta: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    key(epoch) {
        val streamingState = rememberStreamingMarkdownState()
        // 消费 Channel 增量
        LaunchedEffect(Unit) {
            consumeStreamingChannel(flow) { text ->
                withContext(Dispatchers.Default) {
                    streamingState.append(text)
                }
                onDelta(text)
            }
        }
        Markdown(
            streamingMarkdownState = streamingState,
            modifier = modifier,
            colors = rememberAiMarkdownColors(),
            typography = rememberAiMarkdownTypography(),
            padding = markdownPadding(block = 5.dp)
        )
    }
}

/**
 * 从 Channel 串行消费增量：按上游到达节奏消费，只合并同一时刻已经在队列里的 chunk。
 *
 * 不等待固定时间窗，避免流式内容被人为延迟；append 由调用方放到后台线程，
 * 主线程只负责 UI 状态。
 *
 * @param channel 数据源 Channel
 * @param onBatch 收到一批增量时的回调（已合并当前队列），支持 suspend 调用
 */
private suspend fun consumeStreamingChannel(
    channel: Channel<String>,
    onBatch: suspend (String) -> Unit,
) {
    while (true) {
        val first = channel.receiveCatching().getOrNull() ?: break
        val batch = StringBuilder(first)
        // 只取“此刻已经到达”的 chunk，不额外等待。
        while (true) {
            val next = channel.tryReceive().getOrNull() ?: break
            batch.append(next)
        }
        onBatch(batch.toString())
    }
}

@Composable
private fun rememberAiMarkdownColors(): DefaultMarkdownColors {
    val scheme = MiuixTheme.colorScheme
    return remember(scheme) {
        DefaultMarkdownColors(
            text = scheme.onSurface,
            codeBackground = scheme.surfaceContainer,
            inlineCodeBackground = scheme.surfaceContainer,
            dividerColor = scheme.onSurfaceVariantSummary,
            tableBackground = scheme.surfaceContainer,
            highlightText = scheme.onSurface,
            highlightBackground = scheme.surfaceContainer
        )
    }
}

/**
 * 移动端舒适阅读排版：
 * - 正文 15sp / 行高 26sp（≈1.73），长文阅读不累
 * - 标题按 23/20/18/16/15sp 收敛，避免小屏被大标题压满
 * - 代码 13sp；引用与正文一致
 * - 链接使用主题 primary 色（明暗自适应）
 */
@Composable
private fun rememberAiMarkdownTypography(): DefaultMarkdownTypography {
    val scheme = MiuixTheme.colorScheme
    return remember(scheme) {
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
            textLink = TextLinkStyles(
                style = SpanStyle(color = scheme.primary)
            ),
            table = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
            alertTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}


