/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.os.Handler
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle

/**
 * 歌词超时隐藏控制器。
 *
 * 负责 [BasicStyle] 中的三条超时规则：
 * - 无歌词隐藏（[BasicStyle.noLyricHideTimeout]）
 * - 无更新隐藏（[BasicStyle.noUpdateHideTimeout]）
 * - 命中关键词隐藏（[BasicStyle.keywordHideTimeout] + [BasicStyle.keywordsHidePattern]）
 *
 * 设计上不持有 View，也不缓存样式：宿主每次内容/样式变化后调用 [refresh] 传入
 * 最新判据，计时到期后通过 [onTimeout] 通知宿主重新计算可见性。这样控制器可以
 * 独立测试，也不会与视图产生循环引用。
 *
 * @param handler   宿主的主线程 Handler，保证超时回调与 View 操作同线程
 * @param onTimeout 超时到期回调，宿主应在此刷新可见性
 */
internal class LyricTimeoutController(
    private val handler: Handler,
    private val onTimeout: () -> Unit
) {

    companion object {
        /** 无需计时的哨兵值：与配置项"0 表示关闭"保持一致。 */
        private const val NO_TIMEOUT = -1

        private const val MILLIS_PER_SECOND = 1000L
    }

    /** 当前是否已因超时隐藏；宿主在可见性判定中读取。 */
    var isTimedOut: Boolean = false
        private set

    /** 当前生效的计时任务，用于取消。 */
    private var timeoutTask: Runnable? = null

    /**
     * 按最新判据重置计时。
     *
     * 先清除旧计时与超时标记，再按规则决定是否重新计时——因此这是"每次更新都
     * 重新开始倒计时"的语义，与"无更新超时"的配置含义一致。
     *
     * @param style           当前样式，读取三条超时配置与关键词正则
     * @param hasLyricContent 当前是否存在歌词内容
     * @param currentLyric    当前正在显示的一行歌词，用于关键词匹配
     */
    fun refresh(style: LyricStyle, hasLyricContent: Boolean, currentLyric: String?) {
        reset()

        val timeoutSeconds = resolveTimeoutSeconds(style.basicStyle, hasLyricContent, currentLyric)
        if (timeoutSeconds <= 0) return

        val task = Runnable {
            isTimedOut = true
            onTimeout()
        }
        timeoutTask = task
        handler.postDelayed(task, timeoutSeconds * MILLIS_PER_SECOND)
    }

    /** 清除超时标记与待执行的计时任务（不触发任何回调）。 */
    fun reset() {
        isTimedOut = false
        timeoutTask?.let(handler::removeCallbacks)
        timeoutTask = null
    }

    /**
     * 释放资源：取消计时任务。
     *
     * 由宿主在 View 脱离窗口时调用，避免 detached 后回调仍修改可见性。
     */
    fun release() = reset()

    // --- 内部逻辑 ---

    /**
     * 计算本次应使用的超时秒数。
     *
     * 规则优先级：无歌词 > 命中关键词 > 无更新。返回 [NO_TIMEOUT] 表示不计时。
     */
    private fun resolveTimeoutSeconds(
        basicStyle: BasicStyle,
        hasLyricContent: Boolean,
        currentLyric: String?
    ): Int {
        val noLyricTimeoutSeconds = basicStyle.noLyricHideTimeout
        val noUpdateTimeoutSeconds = basicStyle.noUpdateHideTimeout
        val keywordTimeoutSeconds = basicStyle.keywordHideTimeout

        val shouldHideWhenNoLyric = noLyricTimeoutSeconds > 0
        val shouldHideWhenNoUpdate = noUpdateTimeoutSeconds > 0
        val shouldHideWhenKeywordMatched = keywordTimeoutSeconds > 0

        // 无内容：只有"无歌词隐藏"生效
        if (shouldHideWhenNoLyric && !hasLyricContent) return noLyricTimeoutSeconds
        if (!hasLyricContent) return NO_TIMEOUT

        // 有内容：关键词命中优先于"无更新"
        if (isKeywordMatched(shouldHideWhenKeywordMatched, basicStyle, currentLyric)) {
            return keywordTimeoutSeconds
        }
        return if (shouldHideWhenNoUpdate) noUpdateTimeoutSeconds else NO_TIMEOUT
    }

    /**
     * 判断当前歌词行是否命中关键词隐藏规则。
     *
     * 空行不参与匹配：空字符串可能被宽松正则命中，导致误隐藏。
     */
    private fun isKeywordMatched(
        enabled: Boolean,
        basicStyle: BasicStyle,
        currentLyric: String?
    ): Boolean {
        if (!enabled) return false

        val lyric = currentLyric?.takeIf { it.isNotEmpty() } ?: return false
        return basicStyle.keywordsHidePattern.orEmpty().any { it.containsMatchIn(lyric) }
    }
}
