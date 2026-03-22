package io.github.proify.lyricon.common.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object CoverThemeColorExtractor {
    data class ThemeColors(
        val lightModeColor: Int,
        val darkModeColor: Int
    )

    // 采样数量目标：越大越准，越小越快
    private const val TARGET_SAMPLES = 2600.0
    // 透明像素最小 alpha
    private const val MIN_ALPHA = 32
    // 认为“接近灰度图”的平均色度阈值
    private const val GRAYSCALE_CHROMA_THRESHOLD = 8.0

    fun extract(bitmap: Bitmap, minContrast: Double = 4.5): ThemeColors {
        val step = max(
            1,
            sqrt(bitmap.width * bitmap.height / TARGET_SAMPLES).roundToInt()
        )
        // 1) 先估计背景颜色，用于抑制背景污染
        val background = estimateBackground(bitmap, step)
        val samples = collectSamples(bitmap, step, background)
        // 2) 采样像素，计算平均亮度/平均色度，并为聚类准备样本
        if (samples.isEmpty()) {
            return neutralFallback(minContrast)
        }
        // 3) 如果没有有效像素，直接返回中性色渐变
        val avgChroma = samples.sumOf { it.lab.chroma * it.weight } /
                samples.sumOf { it.weight }
        // 4) 灰度图/黑白图：输出中性灰调
        if (avgChroma < GRAYSCALE_CHROMA_THRESHOLD) {
            return neutralFallback(minContrast)
        }
        // 5) LAB 空间加权 K-Means 聚类
        val clusters = weightedKMeans(samples, 3)
        if (clusters.isEmpty()) {
            return neutralFallback(minContrast)
        }
        val dominant = clusters.maxBy { it.weight }
        // 6) 取聚类中心中亮度最高的作为基础颜色
        val baseColor = labToColor(dominant.lab)
        // 7) 转换为 HSL 空间，调整饱和度和亮度范围
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor, hsl)
        // 8) 调整饱和度和亮度范围
        val sat = max(hsl[1], 0.25f)
        val lightHsl = floatArrayOf(
            hsl[0],
            sat,
            hsl[2].coerceIn(0.22f, 0.40f)
        )
        val darkHsl = floatArrayOf(
            hsl[0],
            sat,
            hsl[2].coerceIn(0.65f, 0.85f)
        )
        // 9) 确保与白色/黑色的对比度满足最小要求
        val lightColor =
            ensureContrast(ColorUtils.HSLToColor(lightHsl), Color.WHITE, minContrast)

        val darkColor =
            ensureContrast(ColorUtils.HSLToColor(darkHsl), Color.BLACK, minContrast)

        return ThemeColors(lightColor, darkColor)
    }

    // ----------------------------
    // 数据结构
    // ----------------------------

    private data class Lab(val l: Double, val a: Double, val b: Double) {
        val chroma get() = sqrt(a * a + b * b)
    }

    private data class Sample(val lab: Lab, val weight: Double)

    private data class Cluster(val lab: Lab, val weight: Double)

    // ----------------------------
    // 背景检测
    // ----------------------------

    private fun estimateBackground(bitmap: Bitmap, step: Int): Lab? {

        val border = min(bitmap.width, bitmap.height) / 18
        val buckets = HashMap<Int, MutableList<Lab>>()

        var y = 0
        while (y < bitmap.height) {

            var x = 0
            while (x < bitmap.width) {

                if (x < border || y < border ||
                    x > bitmap.width - border || y > bitmap.height - border
                ) {

                    val c = bitmap.getPixel(x, y)
                    if (Color.alpha(c) >= MIN_ALPHA) {

                        val lab = colorToLab(c)

                        val key =
                            (Color.red(c) shr 3 shl 10) or
                                    (Color.green(c) shr 3 shl 5) or
                                    (Color.blue(c) shr 3)

                        buckets.getOrPut(key) { mutableListOf() }.add(lab)
                    }
                }

                x += step
            }

            y += step
        }

        val dominant = buckets.maxByOrNull { it.value.size } ?: return null

        val labs = dominant.value

        val l = labs.sumOf { it.l } / labs.size
        val a = labs.sumOf { it.a } / labs.size
        val b = labs.sumOf { it.b } / labs.size

        return Lab(l, a, b)
    }

    // ----------------------------
    // 采样
    // ----------------------------

    private fun collectSamples(
        bitmap: Bitmap,
        step: Int,
        background: Lab?
    ): List<Sample> {

        val samples = ArrayList<Sample>()

        var y = 0
        while (y < bitmap.height) {

            var x = 0
            while (x < bitmap.width) {

                val c = bitmap.getPixel(x, y)
                val alpha = Color.alpha(c)

                if (alpha >= MIN_ALPHA) {

                    val lab = colorToLab(c)

                    var weight =
                        (alpha / 255.0) *
                                (0.25 + min(lab.chroma / 50.0, 1.0) * 0.75)

                    if (background != null) {

                        val d = labDistance(lab, background)

                        if (d < 10) weight *= 0.05
                        else if (d < 18) weight *= 0.3
                    }

                    if (weight > 0.002) {
                        samples.add(Sample(lab, weight))
                    }
                }

                x += step
            }

            y += step
        }

        return samples
    }

    // ----------------------------
    // KMeans
    // ----------------------------

    private fun weightedKMeans(samples: List<Sample>, k: Int): List<Cluster> {
        // 加权 K-Means 聚类
        if (samples.isEmpty()) return emptyList()
        var centroids = samples.take(k).map { it.lab }.toMutableList()

        repeat(6) {
            val acc = Array(k) { doubleArrayOf(0.0, 0.0, 0.0, 0.0) }
            for (s in samples) {
                var best = 0
                var bestDist = Double.MAX_VALUE
                for (i in centroids.indices) {
                    val d = labDistanceSq(s.lab, centroids[i])
                    if (d < bestDist) {
                        bestDist = d
                        best = i
                    }
                }
                acc[best][0] += s.lab.l * s.weight
                acc[best][1] += s.lab.a * s.weight
                acc[best][2] += s.lab.b * s.weight
                acc[best][3] += s.weight
            }

            for (i in centroids.indices) {
                val w = acc[i][3]
                if (w > 0) {
                    centroids[i] = Lab(
                        acc[i][0] / w,
                        acc[i][1] / w,
                        acc[i][2] / w
                    )
                }
            }
        }

        val weights = DoubleArray(k)

        for (s in samples) {
            var best = 0
            var bestDist = Double.MAX_VALUE
            for (i in centroids.indices) {
                val d = labDistanceSq(s.lab, centroids[i])
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            weights[best] += s.weight
        }

        return centroids.mapIndexed { i, c ->
            Cluster(c, weights[i])
        }
    }

    // ----------------------------
    // 灰度 fallback
    // ----------------------------

    private fun neutralFallback(minContrast: Double): ThemeColors {
        // 灰度 fallback 颜色，确保与白色/黑色的对比度满足最小要求
        val light = ensureContrast(
            ColorUtils.HSLToColor(floatArrayOf(28f, 0.05f, 0.32f)),
            Color.WHITE,
            minContrast
        )

        val dark = ensureContrast(
            ColorUtils.HSLToColor(floatArrayOf(28f, 0.05f, 0.72f)),
            Color.BLACK,
            minContrast
        )

        return ThemeColors(light, dark)
    }

    // ----------------------------
    // util
    // ----------------------------

    private fun colorToLab(color: Int): Lab {
        // 转换为 LAB 空间
        val arr = DoubleArray(3)
        ColorUtils.colorToLAB(color, arr)

        return Lab(arr[0], arr[1], arr[2])
    }

    private fun labToColor(lab: Lab): Int =
        ColorUtils.LABToColor(lab.l, lab.a, lab.b)

    private fun labDistance(a: Lab, b: Lab) =
        sqrt(labDistanceSq(a, b))

    private fun labDistanceSq(a: Lab, b: Lab): Double {
        // 计算 LAB 空间中两个颜色之间的欧氏距离的平方
        val dl = a.l - b.l
        val da = a.a - b.a
        val db = a.b - b.b

        return dl * dl + da * da + db * db
    }

    private fun ensureContrast(color: Int, background: Int, minContrast: Double): Int {
        var current = color
        var contrast = ColorUtils.calculateContrast(current, background)

        if (contrast >= minContrast) return current

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(current, hsl)
        val bgDark = ColorUtils.calculateLuminance(background) < 0.5
        val delta = if (bgDark) 0.02f else -0.02f

        repeat(60) {
            hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
            current = ColorUtils.HSLToColor(hsl)
            contrast = ColorUtils.calculateContrast(current, background)
            if (contrast >= minContrast) return current
        }

        return current
    }
}
