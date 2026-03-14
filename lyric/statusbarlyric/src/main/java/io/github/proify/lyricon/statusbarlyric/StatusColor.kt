/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

data class StatusColor(
    var color: Int = 0,
    var darkIntensity: Float = 0f,
    var translucentColor: Int = 0,
    var gradientColors: IntArray? = null,
    var translucentGradientColors: IntArray? = null
) {
    val lightMode: Boolean get() = darkIntensity < 0.5f
}
