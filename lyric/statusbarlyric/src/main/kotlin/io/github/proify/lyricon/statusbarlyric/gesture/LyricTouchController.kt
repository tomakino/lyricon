/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.gesture

import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import io.github.proify.android.extensions.dp
import kotlin.math.abs

/**
 * 状态栏歌词的触摸识别与手势反馈控制器。
 *
 * 从歌词视图中剥离出来的单一职责组件，只负责三件事：
 * 1. 识别单击 / 长按 / 左右滑动（[GestureType]）并回调 [gestureListener]；
 * 2. 播放按压缩放、松手回弹、滑动"踢出"等即时反馈动画；
 * 3. 按 [isHapticEnabled] 决定是否触发触觉反馈。
 *
 * 所有动画与回调均在主线程执行（触摸事件本身来自主线程）。持有时长为常量，
 * 不做任何布局与可见性决策——那些属于宿主视图。
 *
 * @param target 承载手势的目标视图，用于播放动画与派发点击事件
 */
internal class LyricTouchController(private val target: View) {

    companion object {
        /** 按下时缩放到的比例。 */
        private const val PRESS_DOWN_SCALE = 0.97f

        /** 长按时放大到的比例。 */
        private const val LONG_PRESS_SCALE = 1.02f

        /** 按下 / 长按强化 / 松手动画时长（毫秒）。 */
        private const val PRESS_DOWN_DURATION_MS = 90L
        private const val LONG_PRESS_INTENSIFY_MS = 140L
        private const val RELEASE_DURATION_MS = 170L

        /** 松手回弹阻尼（DecelerateInterpolator 系数，越大越快停下）。 */
        private const val RELEASE_BOUNCE = 1.5f

        /** 横向拖动时内容跟随手指的阻尼系数。 */
        private const val DRAG_FOLLOW_DAMPING = 0.35f

        /** 滑动识别的"踢出"位移（dp）与动画时长（毫秒）。 */
        private const val SWIPE_KICK_DP = 22
        private const val SWIPE_KICK_MS = 70L
        private const val SWIPE_RETURN_MS = 190L

        /** 判定为横向滑动所需的横纵位移比，避免斜向拖动被误判。 */
        private const val SWIPE_ANGLE_RATIO = 1.5f

        /** 滑动阈值下限（px），触屏较小设备上防止误触发。 */
        private const val MIN_SWIPE_THRESHOLD = 24f
    }

    /**
     * 是否启用手势识别。
     *
     * 关闭后宿主会回退到 [View] 的默认触摸行为（此时可通过
     * [View.setOnClickListener] 委托单击，保持旧版行为）。
     */
    var isGestureEnabled: Boolean = true
        set(value) {
            field = value
            // 关闭时同时撤掉所有触摸消费属性，让事件穿透到状态栏下层
            target.isClickable = value
            target.isLongClickable = value
        }

    /** 是否启用触觉反馈，由上层控制器按用户偏好配置，默认开启。 */
    var isHapticEnabled: Boolean = true

    /** 手势回调，在主线程派发，仅 [isGestureEnabled] 为 true 时触发。 */
    var gestureListener: ((GestureType) -> Unit)? = null

    private val touchSlop: Int = ViewConfiguration.get(target.context).scaledTouchSlop

    /** 横向滑动判定阈值：取两倍 touchSlop，但不低于 [MIN_SWIPE_THRESHOLD]。 */
    private val swipeThreshold: Float =
        (touchSlop * 2f).coerceAtLeast(MIN_SWIPE_THRESHOLD)

    private var downX: Float = 0f
    private var downY: Float = 0f

    /** 本次手势是否已触发长按；用于阻止长按再判定为滑动（一次手势只产出一个动作）。 */
    private var isLongPressFired: Boolean = false

    private val gestureDetector = GestureDetector(
        target.context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // 长按已回调过，抬起时不再补发单击
                if (isLongPressFired) return true

                triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                // 派发给 View 自身的点击监听器，保持无障碍语义与旧版行为
                target.performClick()
                gestureListener?.invoke(GestureType.TAP)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                isLongPressFired = true
                intensifyOnLongPress()
                triggerHaptic(HapticFeedbackConstants.LONG_PRESS)
                gestureListener?.invoke(GestureType.LONG_PRESS)
            }
        }
    )

    /**
     * 处理一次触摸事件。
     *
     * 仅在 [isGestureEnabled] 为 true 时由宿主调用；返回 true 表示事件已被消费。
     * 手势关闭时的回退逻辑（调用 `super.onTouchEvent`）由宿主负责，控制器不参与。
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isLongPressFired = false
                animatePressDown()
            }

            MotionEvent.ACTION_MOVE -> {
                // 横向拖动时内容跟随手指（阻尼），增强"可滑动"的感知
                target.translationX = (event.x - downX) * DRAG_FOLLOW_DAMPING
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                handleActionUp(dx, dy)
            }

            MotionEvent.ACTION_CANCEL -> animateRelease()
        }

        return gestureDetector.onTouchEvent(event)
    }

    /**
     * 取消正在运行的反馈动画并复位位移。
     *
     * 由宿主在视图脱离窗口时调用，避免动画继续持有已 detach 的 View。
     */
    fun cancelRunningAnimation() {
        target.animate().cancel()
        target.translationX = 0f
    }

    // --- 内部逻辑 ---

    /**
     * 抬手判定：优先识别横向滑动，否则视为松手回弹。
     *
     * 这里不依赖 Fling 速度，慢速横向拖拽同样生效；长按已触发时不再判定滑动。
     */
    private fun handleActionUp(dx: Float, dy: Float) {
        val isHorizontalSwipe = !isLongPressFired &&
                abs(dx) > swipeThreshold &&
                abs(dx) > abs(dy) * SWIPE_ANGLE_RATIO

        if (!isHorizontalSwipe) {
            animateRelease()
            return
        }

        playSwipeKick(dx)
        triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
        gestureListener?.invoke(
            if (dx < 0) GestureType.SWIPE_LEFT else GestureType.SWIPE_RIGHT
        )
    }

    /** 按下反馈：以自身中心为轴轻微缩小。 */
    private fun animatePressDown() {
        if (target.width > 0 && target.height > 0) {
            target.pivotX = target.width / 2f
            target.pivotY = target.height / 2f
        }

        target.animate()
            .scaleX(PRESS_DOWN_SCALE).scaleY(PRESS_DOWN_SCALE)
            .setDuration(PRESS_DOWN_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }

    /** 长按反馈：轻微放大提示手势已被识别。 */
    private fun intensifyOnLongPress() {
        target.animate()
            .scaleX(LONG_PRESS_SCALE).scaleY(LONG_PRESS_SCALE)
            .setDuration(LONG_PRESS_INTENSIFY_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** 松手 / 取消反馈：缩放与横向位移一并回弹。 */
    private fun animateRelease() {
        target.animate()
            .scaleX(1f).scaleY(1f).translationX(0f)
            .setDuration(RELEASE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(RELEASE_BOUNCE))
            .start()
    }

    /**
     * 滑动反馈：先向滑动方向"踢"一下，再回弹到原位。
     *
     * @param dx 抬手时的横向位移（带符号），决定踢出方向
     */
    private fun playSwipeKick(dx: Float) {
        val direction = if (dx < 0) -1f else 1f
        val kick = SWIPE_KICK_DP.dp * direction

        target.animate()
            .scaleX(1f).scaleY(1f).translationX(kick)
            .setDuration(SWIPE_KICK_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                target.animate().translationX(0f)
                    .setDuration(SWIPE_RETURN_MS)
                    .setInterpolator(DecelerateInterpolator(RELEASE_BOUNCE))
                    .start()
            }
            .start()
    }

    /** 触觉反馈统一入口：仅 [isHapticEnabled] 开启时震动。 */
    private fun triggerHaptic(feedbackConstant: Int) {
        if (isHapticEnabled) {
            target.performHapticFeedback(feedbackConstant)
        }
    }
}
