/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.github.proify.lyricon.common.util.SVGHelper

/**
 * 自定义图标策略：显示用户配置的图片。
 *
 * 支持三种输入格式：SVG 文本、data URI、Base64 图片。解码结果按
 * "内容指纹 + 目标尺寸"缓存，避免每次着色都重新解码。
 *
 * 与 [ProviderLogoStrategy] 的区别：未开启自定义颜色时保留原图，不做任何染色。
 */
class CustomLogoStrategy(
    private val view: LyricLogoView
) : LogoStrategy {

    companion object {
        /** 触发降采样的尺寸倍数阈值：原图未超过目标尺寸的该倍数时保持原图。 */
        private const val SAMPLE_THRESHOLD = 2
    }

    override var isEffective: Boolean = false
        private set

    private var cachedBitmap: Bitmap? = null
    private var lastSignature: String? = null

    override fun updateContent() {
        // 自定义图标不裁剪轮廓
        if (view.outlineProvider != null) view.outlineProvider = null

        val bitmap = decodeBitmap()
        view.setImageBitmap(bitmap)
        isEffective = bitmap != null

        // 内容变化后立即着色，避免出现一帧未着色的图标
        onStatusColorChanged()
        view.refreshVisibility()
    }

    override fun onStatusColorChanged() {
        val logoStyle = view.lyricStyle?.packageStyle?.logo
        val isColorful = logoStyle?.colorfulCustomLogo == true

        view.imageTintList = LogoColorResolver.resolveCustomLogoColor(view, isColorful)
    }

    override fun onAttach() {
        if (view.drawable == null && isEffective) {
            updateContent()
        }
    }

    override fun onDetach() {
        // 释放解码缓存，下次 attach 时按需重建
        cachedBitmap = null
        lastSignature = null
        view.setImageDrawable(null)
    }

    override fun onVisibilityChanged(visible: Boolean) {
        if (visible && view.drawable == null) {
            updateContent()
        }
    }

    // --- 内部逻辑：解码 ---

    /**
     * 解码自定义图标。
     *
     * 指纹包含配置内容与目标尺寸：尺寸变化时必须重新生成，否则位图会被拉伸。
     */
    private fun decodeBitmap(): Bitmap? {
        val customLogo = view.lyricStyle?.packageStyle?.logo?.customLogo
        if (customLogo.isNullOrBlank()) return null

        val params = view.layoutParams ?: return null
        val targetWidth = params.width
        val targetHeight = params.height

        val signature = "${customLogo.hashCode()}_${targetWidth}_${targetHeight}"
        if (signature == lastSignature && cachedBitmap != null) {
            return cachedBitmap
        }

        val bitmap = runCatching {
            when {
                looksLikeSvg(customLogo) ->
                    SVGHelper.create(customLogo).createBitmap(targetWidth, targetHeight)

                customLogo.startsWith("data:") ->
                    decodeDataUri(customLogo, targetWidth, targetHeight)

                else -> decodeBase64(customLogo, targetWidth, targetHeight)
            }
        }.getOrNull()

        cachedBitmap = bitmap
        lastSignature = signature
        return bitmap
    }

    /** 判断字符串是否为 SVG 或 XML 文档。 */
    private fun looksLikeSvg(str: String): Boolean {
        val trimmed = str.trimStart()
        return trimmed.startsWith("<svg", ignoreCase = true) ||
                trimmed.startsWith("<?xml", ignoreCase = true)
    }

    /**
     * 解析 data URI，形如 `data:image/svg+xml;base64,...` 或 `data:image/png;base64,...`。
     *
     * 无法识别的 MIME 类型返回 null，由调用方降级为不显示图标。
     */
    private fun decodeDataUri(dataUri: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex == -1) return null

        val header = dataUri.substring(0, commaIndex)
        val data = dataUri.substring(commaIndex + 1)
        val isBase64 = header.contains(";base64")
        val mimeType = header.substringAfter("data:").substringBefore(";")

        return when {
            mimeType.contains("svg") -> {
                val svg = if (isBase64) {
                    String(Base64.decode(data, Base64.DEFAULT))
                } else {
                    data
                }
                SVGHelper.create(svg).createBitmap(targetWidth, targetHeight)
            }

            isBase64 -> decodeBase64(data, targetWidth, targetHeight)

            else -> null
        }
    }

    /**
     * 解码 Base64 图片。
     *
     * 自定义图标可能远大于显示尺寸（例如用户直接粘贴相册照片），因此先探测原始
     * 尺寸，超标时按 2 的幂次降采样，避免全尺寸解码造成内存峰值。
     */
    private fun decodeBase64(base64Str: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bytes = runCatching { Base64.decode(base64Str, Base64.DEFAULT) }.getOrNull()
            ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        }
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    /**
     * 计算 [BitmapFactory.Options.inSampleSize]。
     *
     * 取原图相对目标尺寸的较小倍数作为基准：等比缩放时两边同步收缩，用较小边判断
     * 可以避免长边被过度压缩。倍数未超过 [SAMPLE_THRESHOLD] 时返回 1，保持原图清晰度。
     */
    private fun calculateSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1
        if (targetWidth <= 0 || targetHeight <= 0) return 1

        val oversizeRatio = minOf(sourceWidth / targetWidth, sourceHeight / targetHeight)
        if (oversizeRatio <= SAMPLE_THRESHOLD) return 1

        var sampleSize = 1
        while (sampleSize * 2 <= oversizeRatio) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
