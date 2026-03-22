package io.github.proify.lyricon.common.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object CoverThemeGradientExtractor {
    data class ThemeGradientColors(
        val lightModeColors: IntArray,
        val darkModeColors: IntArray
    )

    // 最小对比度
    private const val DEFAULT_MIN_CONTRAST = 4.5
    // 采样数量目标：越大越准，越小越快
    private const val TARGET_SAMPLES = 3600.0
    // 最多保留几个主色
    private const val MAX_BASE_COLORS = 4
    // 最多聚类数
    private const val MAX_CLUSTERS = 5
    // 认为“接近灰度图”的平均色度阈值
    private const val GRAYSCALE_CHROMA_THRESHOLD = 8.0
    // 作为背景的边缘桶占比阈值
    private const val BG_TOP_BUCKET_RATIO_THRESHOLD = 0.35
    // 认为“颜色太接近背景”的 LAB 距离阈值
    private const val BG_SUPPRESS_DISTANCE = 14.0
    // 透明像素最小 alpha
    private const val MIN_ALPHA = 32

    fun extract(bitmap: Bitmap, minContrast: Double = DEFAULT_MIN_CONTRAST): ThemeGradientColors {
        val sampleStep = max(
            1,
            sqrt(bitmap.width.toDouble() * bitmap.height.toDouble() / TARGET_SAMPLES)
                .roundToInt()
        )
        // 1) 先估计背景颜色，用于抑制背景污染
        val background = estimateBackground(bitmap, sampleStep)
        // 2) 采样像素，计算平均亮度/平均色度，并为聚类准备样本
        val samplePack = collectSamples(bitmap, sampleStep, background)
        // 3) 如果没有有效像素，直接返回中性色渐变
        if (samplePack.samples.isEmpty()) {
            return buildNeutralFallback(
                averageL = samplePack.averageL.takeIf { it > 0.0 } ?: 50.0,
                minContrast = minContrast
            )
        }
        // 4) 灰度图/黑白图：输出中性灰调
        if (samplePack.averageChroma < GRAYSCALE_CHROMA_THRESHOLD) {
            return buildNeutralFallback(
                averageL = samplePack.averageL,
                minContrast = minContrast
            )
        }
        // 5) LAB 空间加权 K-Means 聚类
        val clusterCount = estimateClusterCount(samplePack.samples.size, samplePack.averageChroma)
        var clusters = weightedKMeans(samplePack.samples, clusterCount)
        // 6) 合并过近的聚类，避免重复颜色
        clusters = mergeCloseClusters(clusters)
        // 7) 保留有代表性的颜色
        clusters = keepSignificantClusters(clusters)
        // 8) 如果还是没有有效颜色，就走中性兜底
        if (clusters.isEmpty()) {
            return buildNeutralFallback(
                averageL = samplePack.averageL,
                minContrast = minContrast
            )
        }
        // 9) 如果只有一个颜色，且不是灰度，则补一个类比色，方便做渐变
        if (clusters.size == 1) {
            val c = clusters.first()
            if (c.lab.chroma > 12.0) {
                clusters = clusters + createAnalogousCluster(c)
            } else {
                return buildNeutralFallback(
                    averageL = samplePack.averageL,
                    minContrast = minContrast
                )
            }
        }
        // 10) 按色相或亮度排序，构造更自然的渐变顺序
        val ordered = orderClustersForGradient(clusters)
        // 11) 色相跨度太大时，插入中间过渡色
        val finalClusters = insertTransitionClusters(ordered)
        // 12) 分别生成浅色模式 / 深色模式的颜色数组，并保证对比度
        val lightColors = finalClusters.map { cluster ->
            val color = mapClusterToUiColor(cluster, lightBackground = true)
            ensureContrast(color, Color.WHITE, minContrast)
        }.toIntArray()

        val darkColors = finalClusters.map { cluster ->
            val color = mapClusterToUiColor(cluster, lightBackground = false)
            ensureContrast(color, Color.BLACK, minContrast)
        }.toIntArray()

        return ThemeGradientColors(lightColors, darkColors)
    }

    // ----------------------------
    // 数据结构
    // ----------------------------

    private data class Lab(val l: Double, val a: Double, val b: Double) {
        val chroma: Double get() = sqrt(a * a + b * b)
    }

    private data class Sample(
        val lab: Lab,
        val weight: Double
    )

    private data class SamplePack(
        val samples: List<Sample>,
        val averageL: Double,
        val averageChroma: Double
    )

    private data class BackgroundEstimate(
        val lab: Lab,
        val confidence: Double
    )

    private data class Cluster(
        val lab: Lab,
        val weight: Double
    )

    private data class ClusterAcc(
        var weight: Double = 0.0,
        var sumL: Double = 0.0,
        var sumA: Double = 0.0,
        var sumB: Double = 0.0
    ) {
        fun add(sample: Sample) {
            weight += sample.weight
            sumL += sample.lab.l * sample.weight
            sumA += sample.lab.a * sample.weight
            sumB += sample.lab.b * sample.weight
        }

        fun toCluster(): Cluster? {
            if (weight <= 0.0) return null
            return Cluster(
                lab = Lab(sumL / weight, sumA / weight, sumB / weight),
                weight = weight
            )
        }
    }

    private data class BgBucket(
        var count: Int = 0,
        var sumL: Double = 0.0,
        var sumA: Double = 0.0,
        var sumB: Double = 0.0
    ) {
        fun add(lab: Lab) {
            count++
            sumL += lab.l
            sumA += lab.a
            sumB += lab.b
        }

        fun toLab(): Lab = Lab(sumL / count, sumA / count, sumB / count)
    }

    // ----------------------------
    // 主流程辅助
    // ----------------------------

    private fun estimateClusterCount(sampleCount: Int, averageChroma: Double): Int {
        if (averageChroma < 16.0) return 2
        return when {
            sampleCount < 600 -> 2
            sampleCount < 1200 -> 3
            sampleCount < 2400 -> 4
            else -> 5
        }.coerceIn(2, MAX_CLUSTERS)
    }

    /**
     * 估计封面背景色。
     * 思路：
     * 1. 只采样边缘区域；
     * 2. 把相近 RGB 量化成桶；
     * 3. 找出现频最高的边缘颜色桶；
     * 4. 如果这个桶占比足够高，则认为它大概率是背景色。
     */
    private fun estimateBackground(bitmap: Bitmap, step: Int): BackgroundEstimate? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val border = max(2, min(width, height) / 18)
        val buckets = HashMap<Int, BgBucket>()
        var total = 0

        fun addPixel(c: Int) {
            if (Color.alpha(c) < MIN_ALPHA) return
            val lab = colorToLab(c)
            val key = quantizeRgb(c)
            buckets.getOrPut(key) { BgBucket() }.add(lab)
            total++
        }

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                if (x < border || x >= width - border || y < border || y >= height - border) {
                    addPixel(bitmap.getPixel(x, y))
                }
                x += step
            }
            y += step
        }

        if (total < 40) return null

        val top = buckets.values.maxByOrNull { it.count } ?: return null
        val ratio = top.count.toDouble() / total.toDouble()

        // 边缘主色占比不够高，就不当背景，避免误伤内容区域
        if (ratio < BG_TOP_BUCKET_RATIO_THRESHOLD) return null

        return BackgroundEstimate(
            lab = top.toLab(),
            confidence = ratio.coerceIn(0.0, 1.0)
        )
    }

    /**
     * 采样整张图，输出聚类用样本，同时统计平均亮度 / 平均色度。
     * 这里会对“接近背景”的像素做抑制，减少白底、纯底、边框对结果的污染。
     */
    private fun collectSamples(
        bitmap: Bitmap,
        step: Int,
        background: BackgroundEstimate?
    ): SamplePack {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return SamplePack(emptyList(), 50.0, 0.0)
        }

        val samples = ArrayList<Sample>(4096)
        var weightedL = 0.0
        var weightedChroma = 0.0
        var weightSum = 0.0

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getPixel(x, y)
                val alpha = Color.alpha(color)

                if (alpha >= MIN_ALPHA) {
                    val lab = colorToLab(color)
                    val chroma = lab.chroma

                    // 基础权重：透明度越低权重越小，色度越低权重越小
                    var weight = (alpha / 255.0) * (0.25 + min(chroma / 50.0, 1.0) * 0.75)

                    // 如果存在背景估计，则把“接近背景”的像素压低权重
                    if (background != null) {
                        val bgDistance = labDistance(lab, background.lab)
                        val bgPenalty = backgroundPenalty(bgDistance, background.confidence, background.lab.chroma)
                        weight *= bgPenalty
                    }

                    // 太接近中性灰、且权重很低的点，直接丢掉，减少噪声
                    if (weight > 0.002) {
                        samples.add(Sample(lab, weight))
                        weightedL += lab.l * weight
                        weightedChroma += chroma * weight
                        weightSum += weight
                    }
                }

                x += step
            }
            y += step
        }

        if (weightSum <= 0.0) {
            return SamplePack(emptyList(), 50.0, 0.0)
        }

        return SamplePack(
            samples = samples,
            averageL = weightedL / weightSum,
            averageChroma = weightedChroma / weightSum
        )
    }

    /**
     * 背景抑制函数：
     * - 越接近背景，权重越低；
     * - 背景越“确定”，抑制越强；
     * - 如果背景本身就是低色度的纯底，抑制会更明显。
     */
    private fun backgroundPenalty(distance: Double, confidence: Double, backgroundChroma: Double): Double {
        val strongBackground = confidence >= 0.55 || backgroundChroma < 10.0

        val raw = if (strongBackground) {
            when {
                distance <= 8.0 -> 0.03
                distance <= 14.0 -> 0.15
                distance <= 22.0 -> 0.55
                else -> 1.0
            }
        } else {
            when {
                distance <= 8.0 -> 0.20
                distance <= 14.0 -> 0.55
                distance <= 22.0 -> 0.85
                else -> 1.0
            }
        }

        // 再叠加一个轻微的置信度惩罚
        val confidenceBoost = 1.0 - (confidence * 0.35)
        return (raw * confidenceBoost).coerceIn(0.02, 1.0)
    }

    // ----------------------------
    // LAB 聚类
    // ----------------------------

    private fun weightedKMeans(points: List<Sample>, k: Int, iterations: Int = 8): List<Cluster> {
        if (points.isEmpty()) return emptyList()

        val clusterCount = k.coerceIn(1, minOf(MAX_CLUSTERS, points.size))
        var centroids = initCentroids(points, clusterCount)

        repeat(iterations) {
            val accumulators = Array(clusterCount) { ClusterAcc() }

            // 分配样本到最近的 centroid
            for (sample in points) {
                var bestIndex = 0
                var bestDistance = Double.MAX_VALUE

                for (i in centroids.indices) {
                    val d = labDistanceSq(sample.lab, centroids[i])
                    if (d < bestDistance) {
                        bestDistance = d
                        bestIndex = i
                    }
                }

                accumulators[bestIndex].add(sample)
            }

            // 更新 centroid
            var changed = false
            val newCentroids = centroids.toMutableList()

            for (i in accumulators.indices) {
                val cluster = accumulators[i].toCluster()
                if (cluster != null) {
                    if (labDistanceSq(cluster.lab, centroids[i]) > 1e-4) {
                        changed = true
                    }
                    newCentroids[i] = cluster.lab
                } else {
                    // 空簇：重新找一个离现有 centroid 最远的点
                    val reseed = farthestPoint(points, newCentroids)
                    if (reseed != null) {
                        newCentroids[i] = reseed.lab
                        changed = true
                    }
                }
            }

            centroids = newCentroids
            if (!changed) return@repeat
        }

        val finalAcc = Array(centroids.size) { ClusterAcc() }

        for (sample in points) {
            var bestIndex = 0
            var bestDistance = Double.MAX_VALUE
            for (i in centroids.indices) {
                val d = labDistanceSq(sample.lab, centroids[i])
                if (d < bestDistance) {
                    bestDistance = d
                    bestIndex = i
                }
            }
            finalAcc[bestIndex].add(sample)
        }

        return finalAcc.mapNotNull { it.toCluster() }
            .sortedByDescending { it.weight }
    }

    /**
     * K-Means 初始化：
     * 先选权重最大的点，然后不断选择“离现有中心最远”的点作为新中心。
     * 这样比纯随机稳很多。
     */
    private fun initCentroids(points: List<Sample>, k: Int): MutableList<Lab> {
        val first = points.maxByOrNull { it.weight }?.lab ?: return mutableListOf()
        val centroids = mutableListOf(first)

        while (centroids.size < k) {
            var bestPoint: Sample? = null
            var bestScore = -1.0

            for (p in points) {
                val nearest = centroids.minOf { labDistanceSq(p.lab, it) }
                val score = nearest * p.weight
                if (score > bestScore) {
                    bestScore = score
                    bestPoint = p
                }
            }

            if (bestPoint == null) break
            centroids.add(bestPoint.lab)
        }

        return centroids
    }

    private fun farthestPoint(points: List<Sample>, centroids: List<Lab>): Sample? {
        var best: Sample? = null
        var bestScore = -1.0

        for (p in points) {
            val nearest = centroids.minOf { labDistanceSq(p.lab, it) }
            val score = nearest * p.weight
            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }

        return best
    }

    /**
     * 合并很接近的聚类，避免一个主色被切成两份。
     */
    private fun mergeCloseClusters(clusters: List<Cluster>): List<Cluster> {
        if (clusters.isEmpty()) return emptyList()

        val merged = mutableListOf<Cluster>()
        val sorted = clusters.sortedByDescending { it.weight }

        for (cluster in sorted) {
            val hitIndex = merged.indexOfFirst {
                labDistance(it.lab, cluster.lab) < 10.0
            }

            if (hitIndex >= 0) {
                merged[hitIndex] = mergeCluster(merged[hitIndex], cluster)
            } else {
                merged.add(cluster)
            }
        }

        return merged.sortedByDescending { it.weight }
            .take(MAX_BASE_COLORS)
    }

    private fun mergeCluster(a: Cluster, b: Cluster): Cluster {
        val total = a.weight + b.weight
        if (total <= 0.0) return a

        return Cluster(
            lab = Lab(
                l = (a.lab.l * a.weight + b.lab.l * b.weight) / total,
                a = (a.lab.a * a.weight + b.lab.a * b.weight) / total,
                b = (a.lab.b * a.weight + b.lab.b * b.weight) / total
            ),
            weight = total
        )
    }

    /**
     * 只保留有代表性的主色。
     * 这里不会过度保留小权重噪声。
     */
    private fun keepSignificantClusters(clusters: List<Cluster>): List<Cluster> {
        if (clusters.isEmpty()) return emptyList()

        val dominant = clusters.maxOf { it.weight }
        val total = clusters.sumOf { it.weight }

        return clusters.filter {
            val ratioToDominant = it.weight / dominant
            val ratioToTotal = it.weight / total
            ratioToDominant >= 0.18 || ratioToTotal >= 0.08
        }.take(MAX_BASE_COLORS)
    }

    /**
     * 当两个颜色的色相跨度太大时，插入一个过渡色。
     * 这样渐变不会从“红直接跳到蓝”。
     */
    private fun insertTransitionClusters(clusters: List<Cluster>): List<Cluster> {
        if (clusters.size <= 1) return clusters

        val out = mutableListOf<Cluster>()

        for (i in 0 until clusters.size - 1) {
            val c1 = clusters[i]
            val c2 = clusters[i + 1]
            out.add(c1)

            val hueDiff = shortestHueDiff(labHue(c1.lab), labHue(c2.lab))
            val bothColorful = min(c1.lab.chroma, c2.lab.chroma) > 16.0

            if (bothColorful && hueDiff > 120f) {
                val mid = Lab(
                    l = (c1.lab.l + c2.lab.l) / 2.0,
                    a = (c1.lab.a + c2.lab.a) / 2.0,
                    b = (c1.lab.b + c2.lab.b) / 2.0
                )
                out.add(
                    Cluster(
                        lab = mid,
                        weight = (c1.weight + c2.weight) * 0.35
                    )
                )
            }
        }

        out.add(clusters.last())
        return out
    }

    /**
     * 排序策略：
     * - 彩色图：按 hue 排序，做渐变更自然；
     * - 灰度/低彩图：按亮度排序。
     */
    private fun orderClustersForGradient(clusters: List<Cluster>): List<Cluster> {
        if (clusters.size <= 1) return clusters

        val maxChroma = clusters.maxOf { it.lab.chroma }
        return if (maxChroma < 12.0) {
            clusters.sortedBy { it.lab.l }
        } else {
            clusters.sortedWith(
                compareBy<Cluster> { labHue(it.lab) }
                    .thenByDescending { it.lab.chroma }
            )
        }
    }

    /**
     * 把聚类颜色映射成最终 UI 颜色：
     * - 浅色模式：偏暗一点，保证白底可读；
     * - 深色模式：偏亮一点，保证黑底可读；
     * - 灰度图则保持中性，不强行染蓝。
     */
    private fun mapClusterToUiColor(cluster: Cluster, lightBackground: Boolean): Int {
        val color = labToColor(cluster.lab)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        val neutral = cluster.lab.chroma < 10.0

        val hue = if (neutral) 28f else hsl[0]
        val sat = if (neutral) {
            0.04f
        } else {
            // 稍微提高一点饱和度，避免主题色显得太灰
            max(hsl[1], 0.18f).coerceAtMost(0.95f)
        }

        val l = if (lightBackground) {
            // 白底下，颜色不能太亮
            mapToRange(hsl[2], 0.22f, 0.42f)
        } else {
            // 黑底下，颜色不能太暗
            mapToRange(hsl[2], 0.64f, 0.86f)
        }

        return ColorUtils.HSLToColor(floatArrayOf(hue, sat, l))
    }

    // ----------------------------
    // 灰度 / 无有效色块兜底
    // ----------------------------

    /**
     * 灰度图、黑白图、或者完全没提取到色块时的中性色方案。
     * 这里不再 fallback 到蓝色，而是生成一组低饱和灰调。
     */
    private fun buildNeutralFallback(averageL: Double, minContrast: Double): ThemeGradientColors {
        // 轻微暖灰，不会像“默认蓝”那样突兀
        val neutralHue = 28f
        val neutralSat = 0.04f

        // averageL 是 LAB 的 L*，范围大致 0~100
        val baseLight = ((28.0 + averageL * 0.08).coerceIn(20.0, 40.0) / 100.0).toFloat()
        val baseDark = ((68.0 + averageL * 0.06).coerceIn(66.0, 84.0) / 100.0).toFloat()

        val lightStops = floatArrayOf(
            (baseLight - 0.07f).coerceIn(0f, 1f),
            baseLight.toFloat(),
            (baseLight + 0.07f).coerceIn(0f, 1f)
        )

        val darkStops = floatArrayOf(
            (baseDark - 0.06f).coerceIn(0f, 1f),
            baseDark.toFloat(),
            (baseDark + 0.06f).coerceIn(0f, 1f)
        )

        val lightColors = lightStops.map {
            ensureContrast(
                ColorUtils.HSLToColor(floatArrayOf(neutralHue, neutralSat, it)),
                Color.WHITE,
                minContrast
            )
        }.toIntArray()

        val darkColors = darkStops.map {
            ensureContrast(
                ColorUtils.HSLToColor(floatArrayOf(neutralHue, neutralSat, it)),
                Color.BLACK,
                minContrast
            )
        }.toIntArray()

        return ThemeGradientColors(lightColors, darkColors)
    }

    /**
     * 如果聚类结果只有一个，但又不是灰度图，可以补一个类比色，
     * 让渐变至少有变化，不至于变成单色块。
     */
    private fun createAnalogousCluster(cluster: Cluster): Cluster {
        val color = labToColor(cluster.lab)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        val newHue = (hsl[0] + 28f) % 360f
        val newSat = max(hsl[1], 0.25f).coerceAtMost(0.95f)
        val newLabColor = ColorUtils.HSLToColor(floatArrayOf(newHue, newSat, hsl[2]))
        val newLab = colorToLab(newLabColor)

        return Cluster(
            lab = newLab,
            weight = cluster.weight * 0.72
        )
    }

    // ----------------------------
    // 对比度修正
    // ----------------------------

    private fun ensureContrast(color: Int, background: Int, minContrast: Double): Int {
        var current = color
        var contrast = ColorUtils.calculateContrast(current, background)
        if (contrast >= minContrast) return current

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(current, hsl)

        val bgIsDark = ColorUtils.calculateLuminance(background) < 0.5
        val delta = if (bgIsDark) 0.02f else -0.02f

        repeat(60) {
            hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
            current = ColorUtils.HSLToColor(hsl)
            contrast = ColorUtils.calculateContrast(current, background)
            if (contrast >= minContrast) return current
            if (hsl[2] == 0f || hsl[2] == 1f) return current
        }

        return current
    }

    // ----------------------------
    // 颜色转换 / 工具
    // ----------------------------

    private fun colorToLab(color: Int): Lab {
        val out = DoubleArray(3)
        ColorUtils.colorToLAB(color, out)
        return Lab(out[0], out[1], out[2])
    }

    private fun labToColor(lab: Lab): Int {
        return ColorUtils.LABToColor(lab.l, lab.a, lab.b)
    }

    private fun labDistanceSq(a: Lab, b: Lab): Double {
        val dl = a.l - b.l
        val da = a.a - b.a
        val db = a.b - b.b
        return dl * dl + da * da + db * db
    }

    private fun labDistance(a: Lab, b: Lab): Double = sqrt(labDistanceSq(a, b))

    private fun labHue(lab: Lab): Float {
        var hue = (atan2(lab.b, lab.a) * 180.0 / Math.PI).toFloat()
        if (hue < 0f) hue += 360f
        return hue
    }

    private fun shortestHueDiff(h1: Float, h2: Float): Float {
        val diff = abs(h1 - h2)
        return min(diff, 360f - diff)
    }

    private fun mapToRange(value: Float, minValue: Float, maxValue: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return (minValue + (maxValue - minValue) * v).coerceIn(0f, 1f)
    }

    private fun quantizeRgb(color: Int): Int {
        // 5:5:5 量化，足够用于边缘背景桶统计
        val r = Color.red(color) shr 3
        val g = Color.green(color) shr 3
        val b = Color.blue(color) shr 3
        return (r shl 10) or (g shl 5) or b
    }
}
