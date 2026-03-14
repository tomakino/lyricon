package io.github.proify.lyricon.common.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object CoverThemeGradientExtractor {
    data class ThemeGradientColors(
        val lightModeColors: IntArray,
        val darkModeColors: IntArray
    )

    // 最小对比度
    private const val DEFAULT_MIN_CONTRAST = 4.5
    // 颜色种群阈值比例
    private const val POPULATION_THRESHOLD_RATIO = 0.30
    // 最大基础颜色数量
    private const val MAX_BASE_COLORS = 4
    // 色相差异阈值，超过会认为色相之间相差较大，需要插入过渡颜色
    private const val HUE_DIFFERENCE_THRESHOLD = 135f

    fun extract(bitmap: Bitmap, minContrast: Double = DEFAULT_MIN_CONTRAST): ThemeGradientColors {
        val baseHsl = extractBaseHsl(bitmap)
        if (baseHsl.isEmpty()) {
            val fallback = floatArrayOf(210f, 0.65f, 0.55f)
            val light = ensureContrast(adjustForBackground(fallback, true), Color.WHITE, minContrast)
            val dark = ensureContrast(adjustForBackground(fallback, false), Color.BLACK, minContrast)
            return ThemeGradientColors(intArrayOf(light), intArrayOf(dark))
        }

        val sorted = baseHsl.sortedBy { it[0] }.toMutableList()
        if (sorted.size == 1) {
            val hsl = sorted.first()
            val analogous = floatArrayOf((hsl[0] + 30f) % 360f, hsl[1], hsl[2])
            sorted.add(analogous)
        }

        val finalHsl = insertTransitionColors(sorted)
        val lightColors = finalHsl.map { hsl ->
            ensureContrast(adjustForBackground(hsl, true), Color.WHITE, minContrast)
        }.toIntArray()
        val darkColors = finalHsl.map { hsl ->
            ensureContrast(adjustForBackground(hsl, false), Color.BLACK, minContrast)
        }.toIntArray()

        return ThemeGradientColors(lightColors, darkColors)
    }

    private fun adjustForBackground(hsl: FloatArray, lightBackground: Boolean): Int {
        val hue = hsl[0]
        val saturation = hsl[1].coerceIn(0.0f, 1.0f)
        val lightness = hsl[2].coerceIn(0.0f, 1.0f)

        val adjustedL = if (lightBackground) {
            lightness.coerceIn(0.20f, 0.40f)
        } else {
            lightness.coerceIn(0.65f, 0.85f)
        }
        return ColorUtils.HSLToColor(floatArrayOf(hue, saturation, adjustedL))
    }

    private fun insertTransitionColors(sortedHsl: List<FloatArray>): List<FloatArray> {
        val output = mutableListOf<FloatArray>()
        for (i in 0 until sortedHsl.size - 1) {
            val hsl1 = sortedHsl[i]
            val hsl2 = sortedHsl[i + 1]
            output.add(hsl1)

            val hue1 = hsl1[0]
            val hue2 = hsl2[0]
            val hueDiff = abs(hue1 - hue2)
            val shortestDiff = minOf(hueDiff, 360f - hueDiff)

            if (shortestDiff > HUE_DIFFERENCE_THRESHOLD) {
                var midHue = (hue1 + hue2) / 2f
                if (hueDiff > 180f) midHue = (midHue + 180f) % 360f

                val maxSaturation = max(max(hsl1[1], hsl2[1]), 0.6f).coerceIn(0f, 1f)
                val avgLightness = ((hsl1[2] + hsl2[2]) / 2f).coerceIn(0f, 1f)
                output.add(floatArrayOf(midHue, maxSaturation, avgLightness))
            }
        }
        output.add(sortedHsl.last())
        return output
    }

    private data class Bin(
        var weight: Double = 0.0,
        var sumHueX: Double = 0.0,
        var sumHueY: Double = 0.0,
        var sumS: Double = 0.0,
        var sumL: Double = 0.0
    )

    private fun extractBaseHsl(bitmap: Bitmap): List<FloatArray> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return emptyList()

        val targetSamples = 3000.0
        val step = max(
            1,
            sqrt((width.toDouble() * height.toDouble()) / targetSamples).roundToInt()
        )

        val bins = Array(36) { Bin() }
        val row = IntArray(width)
        val hsl = FloatArray(3)

        var y = 0
        while (y < height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            var x = 0
            while (x < width) {
                val c = row[x]
                val alpha = Color.alpha(c)
                if (alpha >= 32) {
                    ColorUtils.colorToHSL(c, hsl)
                    val s = hsl[1]
                    if (s >= 0.10f) {
                        val w = s.toDouble() * (alpha / 255.0)
                        val rad = (hsl[0].toDouble() / 180.0) * Math.PI
                        val idx = (((hsl[0] / 10f).toInt()) % 36).coerceIn(0, 35)
                        val bin = bins[idx]
                        bin.weight += w
                        bin.sumHueX += cos(rad) * w
                        bin.sumHueY += sin(rad) * w
                        bin.sumS += s.toDouble() * w
                        bin.sumL += hsl[2].toDouble() * w
                    }
                }
                x += step
            }
            y += step
        }

        val sorted = bins
            .withIndex()
            .filter { it.value.weight > 0.0 }
            .sortedByDescending { it.value.weight }

        val dominant = sorted.firstOrNull()?.value?.weight ?: return emptyList()
        val selected = sorted
            .filter { it.value.weight >= dominant * POPULATION_THRESHOLD_RATIO }
            .take(MAX_BASE_COLORS)
            .mapNotNull { (_, bin) -> toHsl(bin) }

        return selected
    }

    private fun toHsl(bin: Bin): FloatArray? {
        if (bin.weight <= 0.0) return null
        var hue = (atan2(bin.sumHueY, bin.sumHueX) * 180.0 / Math.PI).toFloat()
        if (hue < 0f) hue += 360f
        val saturation = (bin.sumS / bin.weight).toFloat().coerceIn(0f, 1f)
        val lightness = (bin.sumL / bin.weight).toFloat().coerceIn(0f, 1f)
        return floatArrayOf(hue, saturation, lightness)
    }

    private fun ensureContrast(color: Int, background: Int, minContrast: Double): Int {
        var contrast = ColorUtils.calculateContrast(color, background)
        if (contrast >= minContrast) return color

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        val bgIsDark = ColorUtils.calculateLuminance(background) < 0.5
        val delta = if (bgIsDark) 0.02f else -0.02f

        var i = 0
        while (i < 60) {
            hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
            val adjusted = ColorUtils.HSLToColor(hsl)
            contrast = ColorUtils.calculateContrast(adjusted, background)
            if (contrast >= minContrast) return adjusted
            if (hsl[2] == 0f || hsl[2] == 1f) return adjusted
            i++
        }
        return ColorUtils.HSLToColor(hsl)
    }
}

