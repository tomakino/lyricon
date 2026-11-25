/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.app.compose

import androidx.compose.ui.graphics.Color

/**
 * Full Material Design color palette with semantic roles.
 *
 * colorPrimary      -> Hue500
 * colorPrimaryDark  -> Hue700
 * colorAccent       -> HueA200 (fallback to HueA400 when A200 not present)
 */
object MaterialPalette {

    object Red {
        val Hue50: Color = Color(0xFFFFEBEE)
        val Hue100: Color = Color(0xFFFFCDD2)
        val Hue200: Color = Color(0xFFEF9A9A)
        val Hue300: Color = Color(0xFFE57373)
        val Hue400: Color = Color(0xFFEF5350)
        val Hue500: Color = Color(0xFFF44336)
        val Hue600: Color = Color(0xFFE53935)
        val Hue700: Color = Color(0xFFD32F2F)
        val Hue800: Color = Color(0xFFC62828)
        val Hue900: Color = Color(0xFFB71C1C)
        val HueA100: Color = Color(0xFFFF8A80)
        val HueA200: Color = Color(0xFFFF5252)
        val HueA400: Color = Color(0xFFFF1744)
        val HueA700: Color = Color(0xFFD50000)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Pink {
        val Hue50: Color = Color(0xFFFCE4EC)
        val Hue100: Color = Color(0xFFF8BBD0)
        val Hue200: Color = Color(0xFFF48FB1)
        val Hue300: Color = Color(0xFFF06292)
        val Hue400: Color = Color(0xFFEC407A)
        val Hue500: Color = Color(0xFFE91E63)
        val Hue600: Color = Color(0xFFD81B60)
        val Hue700: Color = Color(0xFFC2185B)
        val Hue800: Color = Color(0xFFAD1457)
        val Hue900: Color = Color(0xFF880E4F)
        val HueA100: Color = Color(0xFFFF80AB)
        val HueA200: Color = Color(0xFFFF4081)
        val HueA400: Color = Color(0xFFF50057)
        val HueA700: Color = Color(0xFFC51162)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Purple {
        val Hue50: Color = Color(0xFFF3E5F5)
        val Hue100: Color = Color(0xFFE1BEE7)
        val Hue200: Color = Color(0xFFCE93D8)
        val Hue300: Color = Color(0xFFBA68C8)
        val Hue400: Color = Color(0xFFAB47BC)
        val Hue500: Color = Color(0xFF9C27B0)
        val Hue600: Color = Color(0xFF8E24AA)
        val Hue700: Color = Color(0xFF7B1FA2)
        val Hue800: Color = Color(0xFF6A1B9A)
        val Hue900: Color = Color(0xFF4A148C)
        val HueA100: Color = Color(0xFFEA80FC)
        val HueA200: Color = Color(0xFFE040FB)
        val HueA400: Color = Color(0xFFD500F9)
        val HueA700: Color = Color(0xFFAA00FF)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object DeepPurple {
        val Hue50: Color = Color(0xFFEDE7F6)
        val Hue100: Color = Color(0xFFD1C4E9)
        val Hue200: Color = Color(0xFFB39DDB)
        val Hue300: Color = Color(0xFF9575CD)
        val Hue400: Color = Color(0xFF7E57C2)
        val Hue500: Color = Color(0xFF673AB7)
        val Hue600: Color = Color(0xFF5E35B1)
        val Hue700: Color = Color(0xFF512DA8)
        val Hue800: Color = Color(0xFF4527A0)
        val Hue900: Color = Color(0xFF311B92)
        val HueA100: Color = Color(0xFFB388FF)
        val HueA200: Color = Color(0xFF7C4DFF)
        val HueA400: Color = Color(0xFF651FFF)
        val HueA700: Color = Color(0xFF6200EA)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Indigo {
        val Hue50: Color = Color(0xFFE8EAF6)
        val Hue100: Color = Color(0xFFC5CAE9)
        val Hue200: Color = Color(0xFF9FA8DA)
        val Hue300: Color = Color(0xFF7986CB)
        val Hue400: Color = Color(0xFF5C6BC0)
        val Hue500: Color = Color(0xFF3F51B5)
        val Hue600: Color = Color(0xFF3949AB)
        val Hue700: Color = Color(0xFF303F9F)
        val Hue800: Color = Color(0xFF283593)
        val Hue900: Color = Color(0xFF1A237E)
        val HueA100: Color = Color(0xFF8C9EFF)
        val HueA200: Color = Color(0xFF536DFE)
        val HueA400: Color = Color(0xFF3D5AFE)
        val HueA700: Color = Color(0xFF304FFE)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Blue {
        val Hue50: Color = Color(0xFFE3F2FD)
        val Hue100: Color = Color(0xFFBBDEFB)
        val Hue200: Color = Color(0xFF90CAF9)
        val Hue300: Color = Color(0xFF64B5F6)
        val Hue400: Color = Color(0xFF42A5F5)
        val Hue500: Color = Color(0xFF2196F3)
        val Hue600: Color = Color(0xFF1E88E5)
        val Hue700: Color = Color(0xFF1976D2)
        val Hue800: Color = Color(0xFF1565C0)
        val Hue900: Color = Color(0xFF0D47A1)
        val HueA100: Color = Color(0xFF82B1FF)
        val HueA200: Color = Color(0xFF448AFF)
        val HueA400: Color = Color(0xFF2979FF)
        val HueA700: Color = Color(0xFF2962FF)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Green {
        val Hue50: Color = Color(0xFFE8F5E9)
        val Hue100: Color = Color(0xFFC8E6C9)
        val Hue200: Color = Color(0xFFA5D6A7)
        val Hue300: Color = Color(0xFF81C784)
        val Hue400: Color = Color(0xFF66BB6A)
        val Hue500: Color = Color(0xFF4CAF50)
        val Hue600: Color = Color(0xFF43A047)
        val Hue700: Color = Color(0xFF388E3C)
        val Hue800: Color = Color(0xFF2E7D32)
        val Hue900: Color = Color(0xFF1B5E20)
        val HueA100: Color = Color(0xFFB9F6CA)
        val HueA200: Color = Color(0xFF69F0AE)
        val HueA400: Color = Color(0xFF00E676)
        val HueA700: Color = Color(0xFF00C853)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Orange {
        val Hue50: Color = Color(0xFFFFF3E0)
        val Hue100: Color = Color(0xFFFFE0B2)
        val Hue200: Color = Color(0xFFFFCC80)
        val Hue300: Color = Color(0xFFFFB74D)
        val Hue400: Color = Color(0xFFFFA726)
        val Hue500: Color = Color(0xFFFF9800)
        val Hue600: Color = Color(0xFFFB8C00)
        val Hue700: Color = Color(0xFFF57C00)
        val Hue800: Color = Color(0xFFEF6C00)
        val Hue900: Color = Color(0xFFE65100)
        val HueA100: Color = Color(0xFFFFD180)
        val HueA200: Color = Color(0xFFFFAB40)
        val HueA400: Color = Color(0xFFFF9100)
        val HueA700: Color = Color(0xFFFF6D00)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Grey {
        val Hue50: Color = Color(0xFFFAFAFA)
        val Hue100: Color = Color(0xFFF5F5F5)
        val Hue200: Color = Color(0xFFEEEEEE)
        val Hue300: Color = Color(0xFFE0E0E0)
        val Hue400: Color = Color(0xFFBDBDBD)
        val Hue500: Color = Color(0xFF9E9E9E)
        val Hue600: Color = Color(0xFF757575)
        val Hue700: Color = Color(0xFF616161)
        val Hue800: Color = Color(0xFF424242)
        val Hue900: Color = Color(0xFF212121)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = Hue400
    }

    object BlueGrey {
        val Hue50: Color = Color(0xFFECEFF1)
        val Hue100: Color = Color(0xFFCFD8DC)
        val Hue200: Color = Color(0xFFB0BEC5)
        val Hue300: Color = Color(0xFF90A4AE)
        val Hue400: Color = Color(0xFF78909C)
        val Hue500: Color = Color(0xFF607D8B)
        val Hue600: Color = Color(0xFF546E7A)
        val Hue700: Color = Color(0xFF455A64)
        val Hue800: Color = Color(0xFF37474F)
        val Hue900: Color = Color(0xFF263238)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = Hue400
    }

    object LightBlue {
        val Hue50: Color = Color(0xFFE1F5FE)
        val Hue100: Color = Color(0xFFB3E5FC)
        val Hue200: Color = Color(0xFF81D4FA)
        val Hue300: Color = Color(0xFF4FC3F7)
        val Hue400: Color = Color(0xFF29B6F6)
        val Hue500: Color = Color(0xFF03A9F4)
        val Hue600: Color = Color(0xFF039BE5)
        val Hue700: Color = Color(0xFF0288D1)
        val Hue800: Color = Color(0xFF0277BD)
        val Hue900: Color = Color(0xFF01579B)
        val HueA100: Color = Color(0xFF80D8FF)
        val HueA200: Color = Color(0xFF40C4FF)
        val HueA400: Color = Color(0xFF00B0FF)
        val HueA700: Color = Color(0xFF0091EA)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Cyan {
        val Hue50: Color = Color(0xFFE0F7FA)
        val Hue100: Color = Color(0xFFB2EBF2)
        val Hue200: Color = Color(0xFF80DEEA)
        val Hue300: Color = Color(0xFF4DD0E1)
        val Hue400: Color = Color(0xFF26C6DA)
        val Hue500: Color = Color(0xFF00BCD4)
        val Hue600: Color = Color(0xFF00ACC1)
        val Hue700: Color = Color(0xFF0097A7)
        val Hue800: Color = Color(0xFF00838F)
        val Hue900: Color = Color(0xFF006064)
        val HueA100: Color = Color(0xFF84FFFF)
        val HueA200: Color = Color(0xFF18FFFF)
        val HueA400: Color = Color(0xFF00E5FF)
        val HueA700: Color = Color(0xFF00B8D4)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Teal {
        val Hue50: Color = Color(0xFFE0F2F1)
        val Hue100: Color = Color(0xFFB2DFDB)
        val Hue200: Color = Color(0xFF80CBC4)
        val Hue300: Color = Color(0xFF4DB6AC)
        val Hue400: Color = Color(0xFF26A69A)
        val Hue500: Color = Color(0xFF009688)
        val Hue600: Color = Color(0xFF00897B)
        val Hue700: Color = Color(0xFF00796B)
        val Hue800: Color = Color(0xFF00695C)
        val Hue900: Color = Color(0xFF004D40)
        val HueA100: Color = Color(0xFFA7FFEB)
        val HueA200: Color = Color(0xFF64FFDA)
        val HueA400: Color = Color(0xFF1DE9B6)
        val HueA700: Color = Color(0xFF00BFA5)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object LightGreen {
        val Hue50: Color = Color(0xFFF1F8E9)
        val Hue100: Color = Color(0xFFDCEDC8)
        val Hue200: Color = Color(0xFFC5E1A5)
        val Hue300: Color = Color(0xFFAED581)
        val Hue400: Color = Color(0xFF9CCC65)
        val Hue500: Color = Color(0xFF8BC34A)
        val Hue600: Color = Color(0xFF7CB342)
        val Hue700: Color = Color(0xFF689F38)
        val Hue800: Color = Color(0xFF558B2F)
        val Hue900: Color = Color(0xFF33691E)
        val HueA100: Color = Color(0xFFCCFF90)
        val HueA200: Color = Color(0xFFB2FF59)
        val HueA400: Color = Color(0xFF76FF03)
        val HueA700: Color = Color(0xFF64DD17)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Lime {
        val Hue50: Color = Color(0xFFF9FBE7)
        val Hue100: Color = Color(0xFFF0F4C3)
        val Hue200: Color = Color(0xFFE6EE9C)
        val Hue300: Color = Color(0xFFDCE775)
        val Hue400: Color = Color(0xFFD4E157)
        val Hue500: Color = Color(0xFFCDDC39)
        val Hue600: Color = Color(0xFFC0CA33)
        val Hue700: Color = Color(0xFFAFB42B)
        val Hue800: Color = Color(0xFF9E9D24)
        val Hue900: Color = Color(0xFF827717)
        val HueA100: Color = Color(0xFFF4FF81)
        val HueA200: Color = Color(0xFFEEFF41)
        val HueA400: Color = Color(0xFFC6FF00)
        val HueA700: Color = Color(0xFFAEEA00)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA400
    }

    object Yellow {
        val Hue50: Color = Color(0xFFFFFDE7)
        val Hue100: Color = Color(0xFFFFF9C4)
        val Hue200: Color = Color(0xFFFFF59D)
        val Hue300: Color = Color(0xFFFFF176)
        val Hue400: Color = Color(0xFFFFEE58)
        val Hue500: Color = Color(0xFFFFEB3B)
        val Hue600: Color = Color(0xFFFDD835)
        val Hue700: Color = Color(0xFFFBC02D)
        val Hue800: Color = Color(0xFFF9A825)
        val Hue900: Color = Color(0xFFF57F17)
        val HueA100: Color = Color(0xFFFFFF8D)
        val HueA200: Color = Color(0xFFFFFF00)
        val HueA400: Color = Color(0xFFFFEA00)
        val HueA700: Color = Color(0xFFFFD600)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA400
    }

    object Amber {
        val Hue50: Color = Color(0xFFFFF8E1)
        val Hue100: Color = Color(0xFFFFECB3)
        val Hue200: Color = Color(0xFFFFE082)
        val Hue300: Color = Color(0xFFFFD54F)
        val Hue400: Color = Color(0xFFFFCA28)
        val Hue500: Color = Color(0xFFFFC107)
        val Hue600: Color = Color(0xFFFFB300)
        val Hue700: Color = Color(0xFFFFA000)
        val Hue800: Color = Color(0xFFFF8F00)
        val Hue900: Color = Color(0xFFFF6F00)
        val HueA100: Color = Color(0xFFFFE57F)
        val HueA200: Color = Color(0xFFFFD740)
        val HueA400: Color = Color(0xFFFFC400)
        val HueA700: Color = Color(0xFFFFAB00)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object DeepOrange {
        val Hue50: Color = Color(0xFFFBE9E7)
        val Hue100: Color = Color(0xFFFFCCBC)
        val Hue200: Color = Color(0xFFFFAB91)
        val Hue300: Color = Color(0xFFFF8A65)
        val Hue400: Color = Color(0xFFFF7043)
        val Hue500: Color = Color(0xFFFF5722)
        val Hue600: Color = Color(0xFFF4511E)
        val Hue700: Color = Color(0xFFE64A19)
        val Hue800: Color = Color(0xFFD84315)
        val Hue900: Color = Color(0xFFBF360C)
        val HueA100: Color = Color(0xFFFF9E80)
        val HueA200: Color = Color(0xFFFF6E40)
        val HueA400: Color = Color(0xFFFF3D00)
        val HueA700: Color = Color(0xFFDD2C00)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = HueA200
    }

    object Brown {
        val Hue50: Color = Color(0xFFEFEBE9)
        val Hue100: Color = Color(0xFFD7CCC8)
        val Hue200: Color = Color(0xFFBCAAA4)
        val Hue300: Color = Color(0xFFA1887F)
        val Hue400: Color = Color(0xFF8D6E63)
        val Hue500: Color = Color(0xFF795548)
        val Hue600: Color = Color(0xFF6D4C41)
        val Hue700: Color = Color(0xFF5D4037)
        val Hue800: Color = Color(0xFF4E342E)
        val Hue900: Color = Color(0xFF3E2723)

        val Primary: Color = Hue500
        val PrimaryDark: Color = Hue700
        val Accent: Color = Hue400
    }
}