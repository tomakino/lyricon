/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.text.NumberFormat
import java.util.Locale

object TimeFormatter {

    private fun getFormatter(locale: Locale) =
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)

    /**
     * 自动格式化时间：
     * 50ms -> 50 毫秒
     * 1_500ms -> 1.5 秒
     * 61_000ms -> 1.02 分钟
     * 3_660_000ms -> 1.02 小时
     * 90_000_000ms -> 1 天
     */
    fun formatTime(millis: Long, locale: Locale? = null): String = runCatching {
        require(millis >= 0) { "时间不能为负数" }
        val locale = locale ?: Locale.getDefault()

        val (value, unit) = when {
            millis >= 86_400_000 -> millis / 86_400_000.0 to MeasureUnit.DAY
            millis >= 3_600_000 -> millis / 3_600_000.0 to MeasureUnit.HOUR
            millis >= 60_000 -> millis / 60_000.0 to MeasureUnit.MINUTE
            millis >= 1_000 -> millis / 1_000.0 to MeasureUnit.SECOND
            else -> millis.toDouble() to MeasureUnit.MILLISECOND
        }

        // 格式化数值，小数最多两位，整数不显示小数
        val formattedValue = if (value % 1.0 == 0.0) {
            NumberFormat.getIntegerInstance(locale).format(value.toLong())
        } else {
            NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(value)
        }

        val formatter = getFormatter(locale)
        return formatter.format(Measure(formattedValue.toDouble(), unit))
    }.getOrElse {
        it.printStackTrace()
        it.message ?: "格式化时间出错"
    }
}