/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.subscriber

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import io.github.proify.lyricon.subscriber.internal.LyriconSubscriberImpl
import io.github.proify.lyricon.subscriber.internal.registration.CentralBootReceiver

/** 创建 [LyriconSubscriber] 的工厂。 */
object LyriconFactory {

    /**
     * 创建订阅端实例。
     *
     * Android 8.1 以下系统不支持当前 Binder/SharedMemory 通道，会返回空实现。
     *
     * @param context 用于注册中心服务广播接收器和读取进程信息的上下文。
     * @return 可用于注册中心服务的订阅端实例。
     */
    fun createSubscriber(
        context: Context
    ): LyriconSubscriber {
        CentralBootReceiver.initialize(context)

        val subscriberInfo = SubscriberInfo(
            context.packageName,
            getCurrentProcessName(context).orEmpty().ifBlank { context.packageName }
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            LyriconSubscriberImpl(context, subscriberInfo)
        } else {
            EmptyLyriconSubscriber(subscriberInfo)
        }
    }

    /** 读取当前进程名；Android 9 以下回退到 ActivityManager 遍历。 */
    private fun getCurrentProcessName(context: Context): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
    }
}
