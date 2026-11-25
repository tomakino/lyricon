/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val GeminiColor: ImageVector
    get() {
        if (_GeminiColor != null) {
            return _GeminiColor!!
        }
        _GeminiColor = ImageVector.Builder(
            name = "GeminiColor",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF3186FF))) {
                moveTo(20.616f, 10.835f)
                arcToRelative(
                    14.147f,
                    14.147f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -4.45f,
                    -3.001f
                )
                arcToRelative(
                    14.111f,
                    14.111f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -3.678f,
                    -6.452f
                )
                arcToRelative(
                    0.503f,
                    0.503f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.975f,
                    0f
                )
                arcToRelative(
                    14.134f,
                    14.134f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -3.679f,
                    6.452f
                )
                arcToRelative(
                    14.155f,
                    14.155f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -4.45f,
                    3.001f
                )
                curveToRelative(-0.65f, 0.28f, -1.318f, 0.505f, -2.002f, 0.678f)
                arcToRelative(
                    0.502f,
                    0.502f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0f,
                    0.975f
                )
                curveToRelative(0.684f, 0.172f, 1.35f, 0.397f, 2.002f, 0.677f)
                arcToRelative(
                    14.147f,
                    14.147f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    4.45f,
                    3.001f
                )
                arcToRelative(
                    14.112f,
                    14.112f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    3.679f,
                    6.453f
                )
                arcToRelative(
                    0.502f,
                    0.502f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0.975f,
                    0f
                )
                curveToRelative(0.172f, -0.685f, 0.397f, -1.351f, 0.677f, -2.003f)
                arcToRelative(
                    14.145f,
                    14.145f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    3.001f,
                    -4.45f
                )
                arcToRelative(
                    14.113f,
                    14.113f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    6.453f,
                    -3.678f
                )
                arcToRelative(
                    0.503f,
                    0.503f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0f,
                    -0.975f
                )
                arcToRelative(
                    13.245f,
                    13.245f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -2.003f,
                    -0.678f
                )
                close()
            }
            path(
                fill = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF08B962),
                        1f to Color(0x0008B962)
                    ),
                    start = Offset(7f, 15.5f),
                    end = Offset(11f, 12f)
                )
            ) {
                moveTo(20.616f, 10.835f)
                arcToRelative(
                    14.147f,
                    14.147f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -4.45f,
                    -3.001f
                )
                arcToRelative(
                    14.111f,
                    14.111f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -3.678f,
                    -6.452f
                )
                arcToRelative(
                    0.503f,
                    0.503f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.975f,
                    0f
                )
                arcToRelative(
                    14.134f,
                    14.134f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -3.679f,
                    6.452f
                )
                arcToRelative(
                    14.155f,
                    14.155f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -4.45f,
                    3.001f
                )
                curveToRelative(-0.65f, 0.28f, -1.318f, 0.505f, -2.002f, 0.678f)
                arcToRelative(
                    0.502f,
                    0.502f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0f,
                    0.975f
                )
                curveToRelative(0.684f, 0.172f, 1.35f, 0.397f, 2.002f, 0.677f)
                arcToRelative(
                    14.147f,
                    14.147f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    4.45f,
                    3.001f
                )
                arcToRelative(
                    14.112f,
                    14.112f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    3.679f,
                    6.453f
                )
                arcToRelative(
                    0.502f,
                    0.502f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0.975f,
                    0f
                )
                curveToRelative(0.172f, -0.685f, 0.397f, -1.351f, 0.677f, -2.003f)
                arcToRelative(
                    14.145f,
                    14.145f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    3.001f,
                    -4.45f
                )
                arcToRelative(
                    14.113f,
                    14.113f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    6.453f,
                    -3.678f
                )
                arcToRelative(
                    0.503f,
                    0.503f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0f,
                    -0.975f
                )
                arcToRelative(
                    13.245f,
                    13.245f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -2.003f,
                    -0.678f
                )
                close()
            }
            path(
                fill = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFF94543),
                        1f to Color(0x00F94543)
                    ),
                    start = Offset(8f, 5.5f),
                    end = Offset(11.5f, 11f)
                )
            ) {
                moveTo(20.616f, 10.835f)
                arcToRelative(
                    14.147f,
                    14.147f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -4.45f,
                    -3.001f
                )
                arcToRelative(
                    14.111f,
                    14.111f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -3.678f,
                    -6.452f
                )
                arcToRelative(
                    0.503f,
                    0.503f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.975f,
                    0f
                )
                arcToRelative(
                    14.134f,
                    14.134f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -3.679f,
                    6.452f
                )
                arcToRelative(
                    14.155f,
                    14.155f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -4.45f,
                    3.001f
                )
                curveToRelative(-0.65f, 0.28f, -1.318f, 0.505f, -2.002f, 0.678f)
                arcToRelative(
                    0.502f,
                    0.502f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0f,
                    0.975f
                )
                curveToRelative(0.684f, 0.172f, 1.35f, 0.397f, 2.002f, 0.677f)
                arcToRelative(
                    14.147f,
                    14.147f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    4.45f,
                    3.001f
                )
                arcToRelative(
                    14.112f,
                    14.112f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    3.679f,
                    6.453f
                )
                arcToRelative(
                    0.502f,
                    0.502f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0.975f,
                    0f
                )
                curveToRelative(0.172f, -0.685f, 0.397f, -1.351f, 0.677f, -2.003f)
                arcToRelative(
                    14.145f,
                    14.145f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    3.001f,
                    -4.45f
                )
                arcToRelative(
                    14.113f,
                    14.113f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    6.453f,
                    -3.678f
                )
                arcToRelative(
                    0.503f,
                    0.503f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0f,
                    -0.975f
                )
                arcToRelative(
                    13.245f,
                    13.245f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -2.003f,
                    -0.678f
                )
                close()
            }
            path(
                fill = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFABC12),
                        0.46f to Color(0x00FABC12)
                    ),
                    start = Offset(3.5f, 13.5f),
                    end = Offset(17.5f, 12f)
                )
            ) {
                moveTo(20.616f, 10.835f)
                arcToRelative(
                    14.147f,
                    14.147f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -4.45f,
                    -3.001f
                )
                arcToRelative(
                    14.111f,
                    14.111f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -3.678f,
                    -6.452f
                )
                arcToRelative(
                    0.503f,
                    0.503f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -0.975f,
                    0f
                )
                arcToRelative(
                    14.134f,
                    14.134f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -3.679f,
                    6.452f
                )
                arcToRelative(
                    14.155f,
                    14.155f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -4.45f,
                    3.001f
                )
                curveToRelative(-0.65f, 0.28f, -1.318f, 0.505f, -2.002f, 0.678f)
                arcToRelative(
                    0.502f,
                    0.502f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0f,
                    0.975f
                )
                curveToRelative(0.684f, 0.172f, 1.35f, 0.397f, 2.002f, 0.677f)
                arcToRelative(
                    14.147f,
                    14.147f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    4.45f,
                    3.001f
                )
                arcToRelative(
                    14.112f,
                    14.112f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    3.679f,
                    6.453f
                )
                arcToRelative(
                    0.502f,
                    0.502f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0.975f,
                    0f
                )
                curveToRelative(0.172f, -0.685f, 0.397f, -1.351f, 0.677f, -2.003f)
                arcToRelative(
                    14.145f,
                    14.145f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    3.001f,
                    -4.45f
                )
                arcToRelative(
                    14.113f,
                    14.113f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    6.453f,
                    -3.678f
                )
                arcToRelative(
                    0.503f,
                    0.503f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0f,
                    -0.975f
                )
                arcToRelative(
                    13.245f,
                    13.245f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -2.003f,
                    -0.678f
                )
                close()
            }
        }.build()

        return _GeminiColor!!
    }

@Suppress("ObjectPropertyName")
private var _GeminiColor: ImageVector? = null
