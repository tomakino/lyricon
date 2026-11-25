/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.app.util

import android.widget.Toast
import io.github.proify.lyricon.app.LyriconApp

object Toasty {

    fun show(
        text: CharSequence,
        longDuration: Boolean = false
    ): Toast {
        val toast = Toast.makeText(
            LyriconApp.instance,
            text,
            if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        )
        toast.show()
        return toast
    }

    fun show(text: Int): Toast = show(LyriconApp.instance.getString(text), true)
    fun showLong(text: CharSequence): Toast = show(text)
}

fun toast(any: Any, longDuration: Boolean = false): Toast =
    Toasty.show(any.toString(), longDuration)