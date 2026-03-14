package io.github.proify.lyricon.common.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object CoverThemeColorExtractor {
    data class ThemeColors(
        val lightModeColor: Int,
        val darkModeColor: Int
    )

    fun extract(bitmap: Bitmap, minContrast: Double = 4.5): ThemeColors {
        val hsl = extractWeightedHsl(bitmap) ?: floatArrayOf(210f, 0.65f, 0.55f)

        val lightModeHsl = hsl.copyOf().apply { this[2] = this[2].coerceIn(0.20f, 0.40f) }
        val darkModeHsl = hsl.copyOf().apply { this[2] = this[2].coerceIn(0.65f, 0.85f) }

        val lightModeColor =
            ensureContrast(ColorUtils.HSLToColor(lightModeHsl), Color.WHITE, minContrast)
        val darkModeColor =
            ensureContrast(ColorUtils.HSLToColor(darkModeHsl), Color.BLACK, minContrast)

        return ThemeColors(lightModeColor, darkModeColor)
    }

    private fun extractWeightedHsl(bitmap: Bitmap): FloatArray? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val targetSamples = 2500.0
        val step = max(
            1,
            sqrt((width.toDouble() * height.toDouble()) / targetSamples).roundToInt()
        )

        val row = IntArray(width)
        val tmpHsl = FloatArray(3)

        var totalWeight = 0.0
        var sumHueX = 0.0
        var sumHueY = 0.0
        var sumS = 0.0
        var sumL = 0.0

        var y = 0
        while (y < height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            var x = 0
            while (x < width) {
                val c = row[x]
                val alpha = Color.alpha(c)
                if (alpha >= 32) {
                    ColorUtils.colorToHSL(c, tmpHsl)
                    val s = tmpHsl[1]
                    if (s >= 0.08f) {
                        val weight = s.toDouble() * (alpha / 255.0)
                        val rad = (tmpHsl[0].toDouble() / 180.0) * Math.PI
                        sumHueX += cos(rad) * weight
                        sumHueY += sin(rad) * weight
                        sumS += s.toDouble() * weight
                        sumL += tmpHsl[2].toDouble() * weight
                        totalWeight += weight
                    }
                }
                x += step
            }
            y += step
        }

        if (totalWeight <= 0.0) return null

        var hue = (atan2(sumHueY, sumHueX) * 180.0 / Math.PI).toFloat()
        if (hue < 0f) hue += 360f

        val saturation = (sumS / totalWeight).toFloat().coerceIn(0f, 1f)
        val lightness = (sumL / totalWeight).toFloat().coerceIn(0f, 1f)

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
