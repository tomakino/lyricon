/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.java

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Rfc3339 {
    /** 固定 3 位毫秒 + 偏移,线程安全 */
    val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    /** 毫秒时间戳 -> RFC3339,默认系统时区 */
    fun format(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(zone)
            .toOffsetDateTime()
            .format(FORMATTER)

    /** 秒时间戳 -> RFC3339 */
    fun formatSeconds(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochSecond(epochSeconds)
            .atZone(zone)
            .toOffsetDateTime()
            .format(FORMATTER)

    /** 当前时间 -> RFC3339 */
    fun now(zone: ZoneId = ZoneId.systemDefault()): String =
        format(Instant.now().toEpochMilli(), zone)
}