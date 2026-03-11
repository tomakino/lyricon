/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.animation.LayoutTransition
import android.animation.TimeInterpolator
import android.view.animation.LinearInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator

class LayoutTransitionX : LayoutTransition {

    constructor(config: Config = Smooth) : super() {
        applyConfig(config)
    }

    constructor(type: String?) : this(
        when (type) {
            TRANSITION_CONFIG_FAST -> Fast
            TRANSITION_CONFIG_SLOW -> Slow
            TRANSITION_CONFIG_SMOOTH -> Smooth
            TRANSITION_CONFIG_NONE -> None
            else -> Smooth
        }
    )

    private fun applyConfig(config: Config) {
        setDuration(APPEARING, config.appearingDuration)
        setInterpolator(APPEARING, config.appearingInterpolator)

        setDuration(DISAPPEARING, config.disappearingDuration)
        setInterpolator(DISAPPEARING, config.disappearingInterpolator)

        setDuration(CHANGE_APPEARING, config.changeAppearingDuration)
        setInterpolator(CHANGE_APPEARING, config.changeAppearingInterpolator)

        setDuration(CHANGE_DISAPPEARING, config.changeDisappearingDuration)
        setInterpolator(CHANGE_DISAPPEARING, config.changeDisappearingInterpolator)

        setDuration(CHANGING, config.changingDuration)
        setInterpolator(CHANGING, config.changingInterpolator)
    }

    data class Config(
        val appearingDuration: Long = 300,
        val disappearingDuration: Long = 300,
        val changeAppearingDuration: Long = 300,
        val changeDisappearingDuration: Long = 300,
        val changingDuration: Long = 250,

        val appearingInterpolator: TimeInterpolator = INTERPOLATOR_ACCEL_DECEL,
        val disappearingInterpolator: TimeInterpolator = INTERPOLATOR_ACCEL_DECEL,
        val changeAppearingInterpolator: TimeInterpolator = INTERPOLATOR_DECEL,
        val changeDisappearingInterpolator: TimeInterpolator = INTERPOLATOR_DECEL,
        val changingInterpolator: TimeInterpolator = INTERPOLATOR_DECEL
    )

    companion object {
        const val TRANSITION_CONFIG_NONE = "none"
        const val TRANSITION_CONFIG_FAST = "fast"
        const val TRANSITION_CONFIG_SMOOTH = "smooth"
        const val TRANSITION_CONFIG_SLOW = "slow"

        private val INTERPOLATOR_ACCEL_DECEL get() = FastOutSlowInInterpolator()
        private val INTERPOLATOR_DECEL get() = LinearOutSlowInInterpolator()
        private val INTERPOLATOR_LINEAR get() = LinearInterpolator()

        val Fast: Config
            get() = Config(
                appearingDuration = 180,
                disappearingDuration = 180,
                changeAppearingDuration = 150,
                changeDisappearingDuration = 150,
                changingDuration = 140,
            )

        val Smooth: Config
            get() = Config(
                appearingDuration = 300,
                disappearingDuration = 300,
                changeAppearingDuration = 280,
                changeDisappearingDuration = 280,
                changingDuration = 250,
            )

        val Slow: Config
            get() = Config(
                appearingDuration = 400,
                disappearingDuration = 400,
                changeAppearingDuration = 350,
                changeDisappearingDuration = 350,
                changingDuration = 320,
            )

        val None: Config
            get() = Config(
                appearingDuration = 0,
                disappearingDuration = 0,
                changeAppearingDuration = 0,
                changeDisappearingDuration = 0,
                changingDuration = 0,
                appearingInterpolator = INTERPOLATOR_LINEAR,
                disappearingInterpolator = INTERPOLATOR_LINEAR,
                changeAppearingInterpolator = INTERPOLATOR_LINEAR,
                changeDisappearingInterpolator = INTERPOLATOR_LINEAR,
                changingInterpolator = INTERPOLATOR_LINEAR
            )
    }
}
