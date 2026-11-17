/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.view.Choreographer

/**
 * 基于 [Choreographer] 的帧调度器：与 [LyricLineView] 解耦。
 *
 * 每帧回调 [onFrame]（返回本帧是否有内容变化）；有变化时调用 [requestInvalidate] 请求重绘；
 * 当 [shouldRun] 不再满足（例如 View 脱离窗口）时自动停止。
 */
internal class FrameAnimator(
    private val onFrame: (deltaNanos: Long) -> Boolean,
    private val requestInvalidate: () -> Unit,
    private val shouldRun: () -> Boolean,
    private val keepRunning: () -> Boolean = { false },
    private val post: (Runnable) -> Unit,
) : Choreographer.FrameCallback {

    private var running = false
    private var lastFrameNanos = 0L

    val isRunning: Boolean get() = running

    fun startIfNeeded() {
        if (!running && shouldRun()) {
            running = true
            lastFrameNanos = 0L
            post { Choreographer.getInstance().postFrameCallback(this) }
        }
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        lastFrameNanos = 0L
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running || !shouldRun()) {
            running = false
            return
        }

        val deltaNanos = if (lastFrameNanos == 0L) 0L else frameTimeNanos - lastFrameNanos
        lastFrameNanos = frameTimeNanos

        val changed = onFrame(deltaNanos)
        if (changed) requestInvalidate()

        // 没有内容变化且渲染器也不在播放时停止调度，避免静态行/暂停态每帧空转。
        if (!changed && !keepRunning()) {
            running = false
            lastFrameNanos = 0L
            return
        }

        if (running) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}
