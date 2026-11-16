/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.graphics.Bitmap
import android.util.Log
import io.github.proify.lyricon.common.util.SVGHelper
import io.github.proify.lyricon.subscriber.ProviderLogo

/**
 * Provider 图标策略：显示歌词提供方的 Logo。
 *
 * Provider 图标通常是单色线条（Bitmap 或 SVG），必须跟随状态栏配色着色；
 * 声明为彩色的图标保留原色。解码结果按"数据指纹 + 目标尺寸 + 类型"缓存。
 */
class ProviderLogoStrategy(
    private val view: LyricLogoView
) : LogoStrategy {

    companion object {
        private const val TAG = "ProviderLogoStrategy"
    }

    override var isEffective: Boolean = false
        private set

    private var cachedBitmap: Bitmap? = null
    private var lastSignature: String? = null

    /**
     * 使解码缓存失效。
     *
     * Provider 数据变更时由 [LyricLogoView] 调用，避免沿用旧图标。
     */
    fun invalidateCache() {
        cachedBitmap = null
        lastSignature = null
    }

    override fun updateContent() {
        // Provider 图标保持完整矩形，不使用裁剪轮廓
        if (view.outlineProvider != null) view.outlineProvider = null

        val bitmap = decodeBitmap()
        view.setImageBitmap(bitmap)
        isEffective = bitmap != null

        // 内容更新后立即着色，避免出现一帧未着色的图标
        onStatusColorChanged()
        view.refreshVisibility()
    }

    override fun onStatusColorChanged() {
        val isColorful = view.providerLogo?.colorful == true
        view.imageTintList = LogoColorResolver.resolveProviderLogoColor(view, isColorful)
    }

    override fun onAttach() {
        // detach 期间可能释放过 Bitmap，这里按需重建
        if (view.drawable == null && isEffective) {
            updateContent()
        }
    }

    override fun onDetach() {
        // 释放 Bitmap 引用以减轻内存压力：原始字节仍保留在 ProviderLogo 中，可随时重建
        invalidateCache()
        view.setImageDrawable(null)
    }

    override fun onVisibilityChanged(visible: Boolean) {
        if (visible && view.drawable == null) {
            updateContent()
        }
    }

    // --- 内部逻辑 ---

    private fun decodeBitmap(): Bitmap? {
        val logo = view.providerLogo ?: return null

        val params = view.layoutParams ?: return null
        val targetWidth = params.width
        val targetHeight = params.height

        // 指纹必须包含 type：相同字节在不同类型下走不同的解码分支
        val signature = "${logo.hashCode()}_${targetWidth}_${targetHeight}_${logo.type}"
        if (signature == lastSignature && cachedBitmap != null) {
            return cachedBitmap
        }

        // 仅记录指纹与尺寸：SVG 全文可能很长，打印会污染日志
        Log.v(TAG, "Decoding provider logo: $signature")

        val bitmap = when (logo.type) {
            ProviderLogo.TYPE_BITMAP -> logo.toBitmap()

            ProviderLogo.TYPE_SVG -> {
                val svg = logo.toSvg()
                if (svg.isNullOrBlank()) {
                    Log.w(TAG, "Provider logo SVG is empty")
                    null
                } else {
                    // SVG 解析失败不应导致崩溃，降级为不显示图标
                    runCatching {
                        SVGHelper.create(svg).createBitmap(targetWidth, targetHeight)
                    }.getOrNull()
                }
            }

            else -> null
        }

        cachedBitmap = bitmap
        lastSignature = signature
        return bitmap
    }
}
