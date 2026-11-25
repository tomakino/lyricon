/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 滑动展开状态
 */
enum class SwipeState {
    CLOSED, // 关闭
    LEFT_OPEN, // 展开左侧操作(向右滑动)
    RIGHT_OPEN, // 展开右侧操作(向左滑动)
}

@Composable
fun SwipeableItem(
    modifier: Modifier = Modifier,
    leftActions: @Composable RowScope.(close: () -> Unit) -> Unit = {},
    rightActions: @Composable RowScope.(close: () -> Unit) -> Unit = {},
    enableOverscroll: Boolean = true, // 启用过度滑动效果
    overscrollDistance: Dp = 200.dp, // 最大过度滑动距离
    content: @Composable (close: () -> Unit) -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatableOffsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    val currentState = remember { mutableStateOf(SwipeState.CLOSED) }

    // 自动测量左右 action 的宽度
    var leftActionsWidth by remember { mutableFloatStateOf(0f) }
    var rightActionsWidth by remember { mutableFloatStateOf(0f) }

    val overscrollDistancePx =
        with(LocalDensity.current) {
            overscrollDistance.toPx()
        }

    // 根据状态计算目标偏移量
    val getTargetOffset: (SwipeState) -> Float = { state ->
        when (state) {
            SwipeState.CLOSED -> 0f
            SwipeState.LEFT_OPEN -> leftActionsWidth
            SwipeState.RIGHT_OPEN -> -rightActionsWidth
        }
    }

    // 根据偏移量获取当前状态
    val getCurrentState: (Float) -> SwipeState = { offset ->
        when {
            offset > leftActionsWidth / 2 -> SwipeState.LEFT_OPEN
            offset < -rightActionsWidth / 2 -> SwipeState.RIGHT_OPEN
            else -> SwipeState.CLOSED
        }
    }

    // 定义关闭函数
    val closeActions: () -> Unit = {
        scope.launch {
            animatableOffsetX.snapTo(offsetX)
            animatableOffsetX.animateTo(0f)
            offsetX = 0f
            currentState.value = SwipeState.CLOSED
        }
    }

    // 处理返回键
//    BackHandler(enabled = currentState != SwipeState.CLOSED) {
//        closeActions()
//    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        // 左侧操作
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .onSizeChanged { size ->
                        leftActionsWidth = if (size.width > 0) size.width.toFloat() else 0f
                    },
            horizontalArrangement = Arrangement.Start,
        ) {
            leftActions(closeActions)
        }

        // 右侧操作
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .onSizeChanged { size ->
                        rightActionsWidth = if (size.width > 0) size.width.toFloat() else 0f
                    },
            horizontalArrangement = Arrangement.End,
        ) {
            rightActions(closeActions)
        }

        // 主内容
        Box(
            modifier =
                Modifier
                    .offset {
                        val currentOffset = if (isDragging) offsetX else animatableOffsetX.value
                        IntOffset(currentOffset.roundToInt(), 0)
                    }
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (offsetX != 0f) {
                                    closeActions()
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                            },
                            onDragEnd = {
                                isDragging = false
                                scope.launch {
                                    val clampedOffset =
                                        offsetX.coerceIn(-rightActionsWidth, leftActionsWidth)
                                    val targetState = getCurrentState(clampedOffset)
                                    val targetOffset = getTargetOffset(targetState)

                                    animatableOffsetX.snapTo(offsetX)
                                    animatableOffsetX.animateTo(targetOffset)
                                    offsetX = targetOffset
                                    currentState.value = targetState
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val currentOffset = offsetX

                                val newOffset =
                                    if (enableOverscroll) {
                                        val resistanceFactor =
                                            when {
                                                currentOffset > leftActionsWidth -> {
                                                    val overscroll =
                                                        currentOffset - leftActionsWidth
                                                    1f / (1f + overscroll / overscrollDistancePx)
                                                }

                                                currentOffset < -rightActionsWidth -> {
                                                    val overscroll =
                                                        -currentOffset - rightActionsWidth
                                                    1f / (1f + overscroll / overscrollDistancePx)
                                                }

                                                else -> {
                                                    val nextOffset = currentOffset + dragAmount
                                                    when {
                                                        nextOffset > leftActionsWidth -> {
                                                            val overscroll =
                                                                nextOffset - leftActionsWidth
                                                            1f / (1f + overscroll / overscrollDistancePx)
                                                        }

                                                        nextOffset < -rightActionsWidth -> {
                                                            val overscroll =
                                                                -nextOffset - rightActionsWidth
                                                            1f / (1f + overscroll / overscrollDistancePx)
                                                        }

                                                        else -> {
                                                            1f
                                                        }
                                                    }
                                                }
                                            }
                                        currentOffset + dragAmount * resistanceFactor
                                    } else {
                                        (currentOffset + dragAmount).coerceIn(
                                            -rightActionsWidth,
                                            leftActionsWidth,
                                        )
                                    }

                                offsetX = newOffset
                            },
                        )
                    },
        ) {
            content(closeActions)
        }
    }
}