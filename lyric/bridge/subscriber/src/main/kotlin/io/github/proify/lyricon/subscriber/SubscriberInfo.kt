/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 订阅端注册信息。
 *
 * 中心服务通过该信息区分不同应用或同一应用内不同进程的订阅者。
 *
 * @property packageName 订阅端应用包名。
 * @property processName 订阅端所在进程名。
 */
@Serializable
@Parcelize
data class SubscriberInfo(
    val packageName: String,
    val processName: String
) : Parcelable