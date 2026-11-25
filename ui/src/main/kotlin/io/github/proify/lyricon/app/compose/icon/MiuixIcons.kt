/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("UnusedReceiverParameter")

package io.github.proify.lyricon.app.compose.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons

val MiuixIcons.MinusCircle: ImageVector
    get() = MiuixIcons.Regular.MinusCircle

/**
 * Miuix 风格的圆圈减号图标 (Regular)
 */
val MiuixIcons.Regular.MinusCircle: ImageVector
    get() {
        if (_minuscircleRegular != null) return _minuscircleRegular!!

        _minuscircleRegular = ImageVector.Builder(
            name = "MinusCircle.Regular",
            defaultWidth = 24.0f.dp,
            defaultHeight = 24.0f.dp,
            viewportWidth = 1225.2f,
            viewportHeight = 1225.2f,
        ).apply {
            group(
                scaleX = 1.0f,
                scaleY = -1.0f,
                translationX = -51.9f,
                translationY = 988.1f
            ) {
                addPath(
                    pathData = listOf(
                        // 外部圆环路径 (与 AddCircle 保持一致)
                        PathNode.MoveTo(1175.0f, 375.0f),
                        PathNode.QuadTo(1175.0f, 514.0f, 1106.5f, 631.0f),
                        PathNode.QuadTo(1038.0f, 748.0f, 921.0f, 817.0f),
                        PathNode.QuadTo(804.0f, 886.0f, 665.0f, 886.0f),
                        PathNode.QuadTo(526.0f, 886.0f, 409.0f, 817.0f),
                        PathNode.QuadTo(292.0f, 748.0f, 223.0f, 631.0f),
                        PathNode.QuadTo(154.0f, 514.0f, 154.0f, 375.0f),
                        PathNode.QuadTo(154.0f, 236.0f, 223.0f, 119.0f),
                        PathNode.QuadTo(292.0f, 2.0f, 409.0f, -66.5f),
                        PathNode.QuadTo(526.0f, -135.0f, 665.0f, -135.0f),
                        PathNode.QuadTo(804.0f, -135.0f, 921.0f, -66.5f),
                        PathNode.QuadTo(1038.0f, 2.0f, 1106.5f, 119.0f),
                        PathNode.QuadTo(1175.0f, 236.0f, 1175.0f, 375.0f),
                        PathNode.Close,
                        // 内部圆环路径 (镂空)
                        PathNode.MoveTo(240.0f, 375.0f),
                        PathNode.QuadTo(240.0f, 491.0f, 297.0f, 588.5f),
                        PathNode.QuadTo(354.0f, 686.0f, 451.5f, 743.0f),
                        PathNode.QuadTo(549.0f, 800.0f, 665.0f, 800.0f),
                        PathNode.QuadTo(780.0f, 800.0f, 877.5f, 743.0f),
                        PathNode.QuadTo(975.0f, 686.0f, 1032.5f, 588.5f),
                        PathNode.QuadTo(1090.0f, 491.0f, 1090.0f, 375.0f),
                        PathNode.QuadTo(1090.0f, 259.0f, 1032.5f, 161.5f),
                        PathNode.QuadTo(975.0f, 64.0f, 877.5f, 7.0f),
                        PathNode.QuadTo(780.0f, -50.0f, 665.0f, -50.0f),
                        PathNode.QuadTo(549.0f, -50.0f, 451.5f, 7.0f),
                        PathNode.QuadTo(354.0f, 64.0f, 297.0f, 161.5f),
                        PathNode.QuadTo(240.0f, 259.0f, 240.0f, 375.0f),
                        PathNode.Close,
                        // 水平减号路径
                        PathNode.MoveTo(905.0f, 418.0f),
                        PathNode.HorizontalTo(425.0f),
                        PathNode.QuadTo(413.0f, 418.0f, 405.5f, 411.0f),
                        PathNode.QuadTo(398.0f, 404.0f, 398.0f, 389.0f),
                        PathNode.VerticalTo(361.0f),
                        PathNode.QuadTo(398.0f, 348.0f, 406.0f, 340.5f),
                        PathNode.QuadTo(414.0f, 333.0f, 425.0f, 333.0f),
                        PathNode.HorizontalTo(905.0f),
                        PathNode.QuadTo(918.0f, 333.0f, 925.0f, 341.0f),
                        PathNode.QuadTo(932.0f, 349.0f, 932.0f, 362.0f),
                        PathNode.VerticalTo(390.0f),
                        PathNode.QuadTo(932.0f, 403.0f, 925.0f, 410.5f),
                        PathNode.QuadTo(918.0f, 418.0f, 905.0f, 418.0f),
                        PathNode.Close,
                    ),
                    fill = SolidColor(Color.Black),
                    fillAlpha = 1f,
                    pathFillType = PathFillType.NonZero,
                )
            }
        }.build()

        return _minuscircleRegular!!
    }

@Suppress("ObjectPropertyName")
private var _minuscircleRegular: ImageVector? = null