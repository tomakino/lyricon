/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.control

import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * G2 连续曲率（squircle / superellipse）圆角几何。
 *
 * 忠实移植自 Kyant0/Capsule 的 G2Continuity RoundedRectangle profile：
 * 每角 = 竖向贝塞尔 → 缩放圆弧 → 横向贝塞尔，保证整条轮廓二阶(G2)几何连续，
 * 用于 clipToOutline 裁出类似 iOS 灵动岛的柔和圆角。
 *
 * @author Tomakino
 * @since 2026
 */
object G2RoundedCorner {

    // G2 连续曲率圆角（移植自 Kyant0/Capsule 的 G2Continuity RoundedRectangle profile）
    private val PROFILE = G2Profile(
        extendedFraction = 0.5286651,
        arcFraction = 5.0 / 9.0,
        bezierCurvatureScale = 1.0732051,
        arcCurvatureScale = 1.0732051
    )

    /**
     * 返回一个连续曲率圆角 [ViewOutlineProvider]，用于 clipToOutline 裁出柔和圆角。
     *
     * API 30+ 使用 [Outline.setConvexPath] 加载超椭圆路径；
     * 更低版本回退到普通圆形角。
     */
    fun outlineProvider(radius: Float): ViewOutlineProvider {
        return object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width
                val h = view.height
                // 尚未布局时（尺寸为 0）不要生成非法路径，避免怪形剪裁
                if (w <= 0 || h <= 0) {
                    outline.setRoundRect(0, 0, 0, 0, 0f)
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    outline.setPath(roundRectPath(w, h, radius))
                } else {
                    outline.setRoundRect(0, 0, w, h, radius)
                }
            }
        }
    }

    /**
     * 生成 G2 连续曲率圆角矩形路径。
     *
     * @param width 矩形宽
     * @param height 矩形高
     * @param radius 四角统一圆角半径
     */
    private fun roundRectPath(width: Int, height: Int, radius: Float): Path {
        val w = width.toDouble()
        val h = height.toDouble()
        val r = radius.toDouble().coerceAtMost(minOf(width, height) * 0.5)
        if (r <= 0.0) {
            return Path().apply {
                addRect(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    Path.Direction.CW
                )
            }
        }

        val prof = PROFILE

        // 每角的两条贝塞尔：V(竖向) 与 H(横向)。角越大越接近胶囊；这里统一用 RoundedRectangle profile。
        val bez = baseBezier(
            prof.extendedFraction,
            prof.arcFraction,
            prof.bezierCurvatureScale,
            prof.arcCurvatureScale
        )
        // 圆弧缩放（radiusScale），贝塞尔曲率缩放传 1.0（profile 内部已用）
        val arcKScale = prof.arcCurvatureScale

        return Path().apply {
            val path = this
            // 状态点：当前画到的 (x,y)
            var x = 0.0
            var y = r

            // 起点：左上角左边缘（源码：y = topLeft - offsetTLV，offsetTLV < 0）
            val offsetTLV = -r * prof.extendedFraction
            path.moveTo(0.0.toFloat(), (r - offsetTLV).toFloat())

            // ==== 左上角 ====
            // TLV 竖向贝塞尔
            path.cubicTo(
                (bez.p1y * r).toFloat(), (r - bez.p1x * r).toFloat(),
                (bez.p2y * r).toFloat(), (r - bez.p2x * r).toFloat(),
                (bez.p3y * r).toFloat(), (r - bez.p3x * r).toFloat()
            )
            // TLC 圆弧（缩放）
            arcTo(
                path,
                cx = r, cy = r, radius = r, radiusScale = 1.0 / arcKScale,
                startAngle = PI + PI * 0.5 * (1.0 - prof.arcFraction) * 0.5,
                sweepAngle = PI * 0.5 * prof.arcFraction
            )
            // TLH 横向贝塞尔
            path.cubicTo(
                (r - bez.p2x * r).toFloat(), (bez.p2y * r).toFloat(),
                (r - bez.p1x * r).toFloat(), (bez.p1y * r).toFloat(),
                (r - maxOf(bez.p0x * r, offsetTLV)).toFloat(), (bez.p0y * r).toFloat()
            )

            // 顶边
            path.lineTo((w - r).toFloat(), 0f)

            // ==== 右上角 ====
            x = w - r
            y = 0.0
            path.cubicTo(
                (x + bez.p1x * r).toFloat(), (y + bez.p1y * r).toFloat(),
                (x + bez.p2x * r).toFloat(), (y + bez.p2y * r).toFloat(),
                (x + bez.p3x * r).toFloat(), (y + bez.p3y * r).toFloat()
            )
            arcTo(
                path,
                cx = w - r, cy = r, radius = r, radiusScale = 1.0 / arcKScale,
                startAngle = -PI * 0.5 + PI * 0.5 * (1.0 - prof.arcFraction) * 0.5,
                sweepAngle = PI * 0.5 * prof.arcFraction
            )
            x = w
            y = r
            val offsetTRV = -r * prof.extendedFraction
            path.cubicTo(
                (x - bez.p2y * r).toFloat(), (y - bez.p2x * r).toFloat(),
                (x - bez.p1y * r).toFloat(), (y - bez.p1x * r).toFloat(),
                (x - bez.p0y * r).toFloat(), (y - maxOf(bez.p0x * r, offsetTRV)).toFloat()
            )

            // 右边
            path.lineTo(w.toFloat(), (h - r).toFloat())

            // ==== 右下角 ====
            x = w
            y = h - r
            path.cubicTo(
                (x - bez.p1y * r).toFloat(), (y + bez.p1x * r).toFloat(),
                (x - bez.p2y * r).toFloat(), (y + bez.p2x * r).toFloat(),
                (x - bez.p3y * r).toFloat(), (y + bez.p3x * r).toFloat()
            )
            arcTo(
                path,
                cx = w - r, cy = h - r, radius = r, radiusScale = 1.0 / arcKScale,
                startAngle = 0.0 + PI * 0.5 * (1.0 - prof.arcFraction) * 0.5,
                sweepAngle = PI * 0.5 * prof.arcFraction
            )
            x = w - r
            y = h
            val offsetBRH = -r * prof.extendedFraction
            path.cubicTo(
                (x + bez.p2x * r).toFloat(), (y - bez.p2y * r).toFloat(),
                (x + bez.p1x * r).toFloat(), (y - bez.p1y * r).toFloat(),
                (x + maxOf(bez.p0x * r, offsetBRH)).toFloat(), (y - bez.p0y * r).toFloat()
            )

            // 底边
            path.lineTo(r.toFloat(), h.toFloat())

            // ==== 左下角 ====
            x = r
            y = h
            path.cubicTo(
                (x - bez.p1x * r).toFloat(), (y - bez.p1y * r).toFloat(),
                (x - bez.p2x * r).toFloat(), (y - bez.p2y * r).toFloat(),
                (x - bez.p3x * r).toFloat(), (y - bez.p3y * r).toFloat()
            )
            arcTo(
                path,
                cx = r, cy = h - r, radius = r, radiusScale = 1.0 / arcKScale,
                startAngle = PI * 0.5 + PI * 0.5 * (1.0 - prof.arcFraction) * 0.5,
                sweepAngle = PI * 0.5 * prof.arcFraction
            )
            x = 0.0
            y = h - r
            val offsetBLV = -r * prof.extendedFraction
            path.cubicTo(
                (x + bez.p2y * r).toFloat(), (y + bez.p2x * r).toFloat(),
                (x + bez.p1y * r).toFloat(), (y + bez.p1x * r).toFloat(),
                (x + bez.p0y * r).toFloat(), (y + maxOf(bez.p0x * r, offsetBLV)).toFloat()
            )

            path.close()
        }
    }

    /**
     * 用 G2 连续曲率贝塞尔(零起点曲率)生成单位坐标控制距。
     * 忠实移植自 Kyant0/Capsule 的 G2ContinuityProfile.createBaseBezier +
     * generateG2ContinuousBezierWithZeroStartCurvature。
     */
    private fun baseBezier(
        extFraction: Double,
        arcFraction: Double,
        bezKScale: Double,
        arcKScale: Double
    ): G2Bezier {
        val arcRadians = PI * 0.5 * arcFraction
        val bezierRadians = (PI * 0.5 - arcRadians) * 0.5
        val sin = sin(bezierRadians)
        val cos = cos(bezierRadians)

        if (bezKScale == 1.0 && arcKScale == 1.0) {
            // 圆角已知闭式解
            val halfTan = sin / (1.0 + cos)
            return G2Bezier(
                -extFraction, 0.0,
                (1.0 - 1.5 / (1.0 + cos)) * halfTan, 0.0,
                halfTan, 0.0,
                sin, 1.0 - cos
            )
        }

        val radiusScale = 1.0 / arcKScale
        val arcCenter = Vec2(0.0, 1.0) +
                (Vec2(1.0 / sqrt(2.0), -1.0 / sqrt(2.0)) * (1.0 - radiusScale))
        val arcStartPoint = arcCenter + Vec2(sin, -cos) * radiusScale

        val start = Vec2(-extFraction, 0.0)
        val end = arcStartPoint
        val startTan = Vec2(1.0, 0.0)
        val endTan = Vec2(cos, sin)

        val a2 = 1.5 * bezKScale
        val b = startTan.x * endTan.y - startTan.y * endTan.x
        val dx = end.x - start.x
        val dy = end.y - start.y
        val c1 = -dy * startTan.x + dx * startTan.y
        val c2 = dy * endTan.x - dx * endTan.y

        val lambda0 = -c2 / b - a2 * c1 * c1 / b / b / b
        val lambda3 = -c1 / b

        val p0 = start
        val p1 = start + Vec2(
            maxOf(lambda0 * startTan.x, 0.0),
            maxOf(lambda0 * startTan.y, 0.0)
        )
        val p2 = end - Vec2(
            maxOf(lambda3 * endTan.x, 0.0),
            maxOf(lambda3 * endTan.y, 0.0)
        )
        val p3 = end

        return G2Bezier(
            p0.x, p0.y,
            p1.x, p1.y,
            p2.x, p2.y,
            p3.x, p3.y
        )
    }

    /**
     * 画一段缩放圆弧（圆心沿 centerAngle 方向内移），对应 Capsule 的 arcToWithScaledRadius。
     */
    private fun arcTo(
        path: Path,
        cx: Double,
        cy: Double,
        radius: Double,
        radiusScale: Double,
        startAngle: Double,
        sweepAngle: Double
    ) {
        val centerAngle = startAngle + sweepAngle * 0.5
        val shift = radius * (1.0 - radiusScale)
        val newCx = cx + cos(centerAngle) * shift
        val newCy = cy + sin(centerAngle) * shift
        val cr = radius * radiusScale

        path.arcTo(
            RectF(
                (newCx - cr).toFloat(),
                (newCy - cr).toFloat(),
                (newCx + cr).toFloat(),
                (newCy + cr).toFloat()
            ),
            Math.toDegrees(startAngle).toFloat(),
            Math.toDegrees(sweepAngle).toFloat(),
            false
        )
    }

    /** 二维向量（单位坐标系下的几何运算）。 */
    private class Vec2(val x: Double, val y: Double) {
        operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
        operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
        operator fun times(k: Double) = Vec2(x * k, y * k)
    }

    private data class G2Bezier(
        val p0x: Double, val p0y: Double,
        val p1x: Double, val p1y: Double,
        val p2x: Double, val p2y: Double,
        val p3x: Double, val p3y: Double
    )

    private data class G2Profile(
        val extendedFraction: Double,
        val arcFraction: Double,
        val bezierCurvatureScale: Double,
        val arcCurvatureScale: Double
    )
}
