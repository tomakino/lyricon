/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import io.github.proify.android.extensions.crc32
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.toBitmap
import io.github.proify.lyricon.lyric.style.LogoStyle

/**
 * 封面策略：显示专辑封面。
 *
 * 支持两种裁剪方式：圆形（唱片模式，持续旋转并与间奏进度弧同心）与圆角矩形。
 * 封面始终保留原色，不随状态栏配色变化。
 */
class CoverLogoStrategy(
    private val view: LyricLogoView
) : LogoStrategy {

    companion object {
        /** 圆形封面旋转一圈的时长（毫秒）。 */
        private const val ROTATION_DURATION_MS = 12_000L

        /** 圆角封面的圆角半径（dp）。 */
        private const val SQUIRCLE_CORNER_RADIUS_DP = 3.5f
    }

    private var rotationAnimator: ObjectAnimator? = null

    /** 上次加载的封面指纹（CRC32），用于跳过重复解码。 */
    private var lastFileSignature: String? = null

    override var isEffective: Boolean = false
        private set

    /** 当前生效的封面样式，仅随样式变化更新；[LyricLogoView] 据此判断是否绘制进度弧。 */
    var style: Int = LogoStyle.STYLE_COVER_CIRCLE
        private set

    override fun updateContent() {
        // 封面保留原色：进入封面模式时必须清掉上一个策略遗留的 Tint
        if (view.imageTintList != null) view.imageTintList = null

        val coverFile = view.coverFile
        if (coverFile == null || !coverFile.exists()) {
            view.setImageDrawable(null)
            isEffective = false
            lastFileSignature = null
        } else {
            val signature = coverFile.crc32().toString()

            // 文件内容未变且已有图像时复用，避免同封面反复解码
            if (signature != lastFileSignature || view.drawable == null) {
                val bitmap: Bitmap? = coverFile.toBitmap(view.width, view.height)
                view.setImageBitmap(bitmap)
                lastFileSignature = signature
                isEffective = bitmap != null

                // 换图后角度归零，避免沿用上一张封面的旋转相位
                stopRotation(resetRotation = true)
            }
        }

        // 内容未变时也要校准轮廓与动画：样式可能刚刚切换过
        applyOutlineAndRotation()
        view.refreshVisibility()
    }

    override fun onStatusColorChanged() {
        // 封面保持原色，不应用 Tint
    }

    override fun onAttach() {
        // 重新加载内容并按需恢复旋转动画
        updateContent()
    }

    override fun onDetach() {
        stopRotation()
        // 此处不释放图片：下次 attach 时 updateContent 会按指纹判断是否需要重载
    }

    override fun onVisibilityChanged(visible: Boolean) {
        if (visible) syncRotationAnimation() else stopRotation()
    }

    // --- 内部逻辑 ---

    /** 按最新样式设置裁剪轮廓与旋转状态。 */
    private fun applyOutlineAndRotation() {
        val currentStyle = view.lyricStyle?.packageStyle?.logo?.style
            ?: LogoStyle.STYLE_COVER_CIRCLE
        val previousStyle = style
        style = currentStyle

        applyOutlineProvider(currentStyle)

        // 从圆形切走时角度必须归零，否则圆角矩形会带着倾斜角度显示
        if (previousStyle == LogoStyle.STYLE_COVER_CIRCLE &&
            currentStyle != LogoStyle.STYLE_COVER_CIRCLE
        ) {
            view.rotation = 0f
        }

        syncRotationAnimation()
    }

    /** 按样式设置裁剪轮廓：圆形、圆角矩形或不裁剪。 */
    private fun applyOutlineProvider(style: Int) {
        val provider = when (style) {
            LogoStyle.STYLE_COVER_CIRCLE -> object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }

            LogoStyle.STYLE_COVER_SQUIRCLE -> object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width,
                        view.height,
                        SQUIRCLE_CORNER_RADIUS_DP.dp.toFloat()
                    )
                }
            }

            else -> null
        }
        view.outlineProvider = provider
        view.clipToOutline = provider != null
    }

    /** 仅在"已附加 + 可见 + 有封面 + 圆形样式"四个条件同时成立时旋转。 */
    private fun syncRotationAnimation() {
        val shouldRotate = view.isAttachedToWindow &&
                view.isShown &&
                isEffective &&
                style == LogoStyle.STYLE_COVER_CIRCLE

        if (shouldRotate) startRotation() else stopRotation()
    }

    private fun startRotation() {
        if (rotationAnimator?.isRunning == true) return

        rotationAnimator = ObjectAnimator.ofFloat(
            view,
            View.ROTATION,
            view.rotation,
            view.rotation + 360f
        ).apply {
            duration = ROTATION_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
    }

    /**
     * 停止旋转。
     *
     * 默认保留当前角度，使暂停后恢复时视觉连贯；彻底归零由 [LyricLogoView] 在
     * 切换策略时统一处理。
     *
     * @param resetRotation 是否同时把角度归零
     */
    private fun stopRotation(resetRotation: Boolean = false) {
        rotationAnimator?.cancel()
        rotationAnimator = null

        if (resetRotation) view.rotation = 0f
    }
}
