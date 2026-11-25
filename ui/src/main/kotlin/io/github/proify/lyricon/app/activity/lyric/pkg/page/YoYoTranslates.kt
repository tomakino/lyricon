/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.page

import android.content.Context
import io.github.proify.lyricon.app.R

object YoYoTranslates {

    fun getLabel(context: Context, id: String?): String {
        val resId = getLabelResId(id)
        if (resId == -1) return id ?: ""
        return context.getString(resId)
    }

    private fun getLabelResId(id: String?): Int = when (id) {
        "default" -> R.string.yoyo_default
        "fade_out_fade_in" -> R.string.yoyo_fade_out_fade_in
        "fade_out_up_fade_in_up" -> R.string.yoyo_fade_out_up_fade_in_up
        "fade_out_down_fade_in_down" -> R.string.yoyo_fade_out_down_fade_in_down
        "fade_out_left_fade_in_right" -> R.string.yoyo_fade_out_left_fade_in_right
        "fade_out_left_fade_in_up" -> R.string.yoyo_fade_out_left_fade_in_up
        "fade_out_left_zoom_in" -> R.string.yoyo_fade_out_left_zoom_in
        "fade_out_left_landing" -> R.string.yoyo_fade_out_left_landing
        "fade_out_right_fade_in_left" -> R.string.yoyo_fade_out_right_fade_in_left
        "fade_out_right_fade_in_up" -> R.string.yoyo_fade_out_right_fade_in_up
        "fade_out_right_zoom_in" -> R.string.yoyo_fade_out_right_zoom_in
        "fade_out_right_landing" -> R.string.yoyo_fade_out_right_landing
        "slide_out_left_slide_in_right" -> R.string.yoyo_slide_out_left_slide_in_right
        "slide_out_left_fade_in_up" -> R.string.yoyo_slide_out_left_fade_in_up
        "slide_out_left_zoom_in" -> R.string.yoyo_slide_out_left_zoom_in
        "slide_out_left_landing" -> R.string.yoyo_slide_out_left_landing
        "slide_out_right_slide_in_left" -> R.string.yoyo_slide_out_right_slide_in_left
        "slide_out_right_fade_in_up" -> R.string.yoyo_slide_out_right_fade_in_up
        "slide_out_right_zoom_in" -> R.string.yoyo_slide_out_right_zoom_in
        "slide_out_right_landing" -> R.string.yoyo_slide_out_right_landing
        "flip_out_x_flip_in_x" -> R.string.yoyo_flip_out_x_flip_in_x
        "flip_out_y_flip_in_y" -> R.string.yoyo_flip_out_y_flip_in_y
        "rotate_out_rotate_in" -> R.string.yoyo_rotate_out_rotate_in
        "zoom_out_zoom_in" -> R.string.yoyo_zoom_out_zoom_in
        "fade_out_left_zoom_in_right" -> R.string.yoyo_fade_out_left_zoom_in_right
        "fade_out_right_zoom_in_left" -> R.string.yoyo_fade_out_right_zoom_in_left
        else -> -1
    }
}