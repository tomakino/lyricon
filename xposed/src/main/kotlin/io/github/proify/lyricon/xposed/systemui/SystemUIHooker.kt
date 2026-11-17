/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui

import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import io.github.proify.android.extensions.deflate
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeEncode
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.central.BridgeCentral
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.common.util.ViewHierarchyParser
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.LyriconFactory
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.xposed.ModuleEntry
import io.github.proify.lyricon.xposed.hook.PackageHooker
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.ai.translate.AiTranslator
import io.github.proify.lyricon.xposed.systemui.hook.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.hook.StatusBarColorMonitor
import io.github.proify.lyricon.xposed.systemui.hook.StatusBarDisableHooker
import io.github.proify.lyricon.xposed.systemui.hook.StatusBarViewResolver
import io.github.proify.lyricon.xposed.systemui.hook.ViewVisibilityTracker
import io.github.proify.lyricon.xposed.systemui.lyric.LyricDataHub
import io.github.proify.lyricon.xposed.systemui.lyric.LyricPrefs
import io.github.proify.lyricon.xposed.systemui.lyric.StatusBarViewController
import io.github.proify.lyricon.xposed.systemui.lyric.StatusBarViewManager
import io.github.proify.lyricon.xposed.systemui.util.CrashDetector
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.systemui.util.SystemUIMediaUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * SystemUI Hook 入口对象
 * 负责状态栏视图注入、第三方逻辑初始化及跨进程通信绑定
 */
object SystemUIHooker : PackageHooker() {
    private const val TAG = "SystemUIHooker"

    private const val TEST_CRASH = false
    private var isSafeMode = false
    private var isAppCreated = false

    var subscriber: LyriconSubscriber? = null
        private set

    private val mainCoroutineScope by lazy {
        CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onHook() {
        YLog.info(TAG, "onHook")

        if (!isMainProcess()) {
            YLog.info(TAG, "Not main process, do nothing")
            return
        }

        doOnAppCreated {
            if (isAppCreated) {
                YLog.info(TAG, "App already created, do nothing")
                return@doOnAppCreated
            }
            isAppCreated = true
            YLog.info(TAG, "App created ")
            onPreLoad()
        }
    }

    /**
     * 应用创建前的准备工作，包含崩溃检测逻辑
     */
    private fun onPreLoad() {
        YLog.info(TAG, "onPreLoad")

        val context = appContext
        if (context == null) {
            YLog.info(TAG, "App context not available")
            return
        }

        CrashDetector.getInstance(context).apply {
            record()
            // 检测到多次连续崩溃时进入安全模式，停止后续注入
            if (isContinuousCrash()) {
                isSafeMode = true
                YLog.error(TAG, "检测到连续崩溃，已停止hook")
            }
            if (isSafeMode) reset()
        }

        initCrashDataChannel()
        if (!isSafeMode) {
            onAppCreate()
        } else {
            YLog.info(TAG, "Safe mode enabled, app create skipped")
        }
    }

    private fun onAppCreate() {
        YLog.info(TAG, "onAppCreate")
        val context = appContext
        if (context == null) {
            YLog.info(TAG, "App context not available")
            return
        }

        StatusBarViewResolver.subscribe {
            YLog.info(TAG, "New status bar view resolved ")
            addStatusBarView(it)
        }

        initialize()
    }

    /**
     * 在App onCreate完成时进行各类辅助工具和监控器的初始化
     */
    private fun initialize() {
        YLog.info(TAG, "onInit")
        val context = appContext ?: return

        ScreenStateMonitor.initialize(context)
        OplusCapsuleHooker.initialize(module, classLoader)
        NotificationCoverHelper.initialize()
        ViewVisibilityTracker.initialize(module, classLoader)
        initDataChannel()

        initLyriconService()

        StatusBarDisableHooker.inject(module, classLoader)
        StatusBarDisableHooker.addListener(object :
            StatusBarDisableHooker.OnStatusBarDisableListener {
            private var lastDisableStateChanged: Boolean? = null

            override fun onDisableStateChanged(shouldHide: Boolean, animate: Boolean) {
                if (lastDisableStateChanged == shouldHide) return
                lastDisableStateChanged = shouldHide
                StatusBarViewManager.forEach { it.onDisableStateChanged(shouldHide) }
            }
        })

        StatusBarColorMonitor.initialize(module, classLoader)
        AiTranslator.init(context)
        SystemUIMediaUtils.init(context)

        StatusBarViewResolver.init(module, context)
    }

    private fun initLyriconService() {
        val context = appContext ?: return

        val service = ModuleEntry.instance
        val defaultSp = service.getRemotePreferences("default")
        val coreServiceDisable = defaultSp.getBoolean("core_service_disable", false)

        if (!coreServiceDisable) {
            BridgeCentral.initialize(context)
            BridgeCentral.sendBootCompleted()
        } else {
            YLog.info(TAG, "已禁用内置中心服务")
        }

        val subscriber = LyriconFactory.createSubscriber(appContext!!)
        this.subscriber = subscriber

        subscriber.subscribeActivePlayer(LyricDataHub)

        subscriber.addConnectionListener(object : ConnectionListener {
            override fun onConnected(subscriber: LyriconSubscriber) {
                YLog.info(TAG, "lyriconSubscriber onConnected")
            }

            override fun onReconnected(subscriber: LyriconSubscriber) {
                YLog.info(TAG, "lyriconSubscriber onReconnected")
            }

            override fun onDisconnected(subscriber: LyriconSubscriber) {
                YLog.info(TAG, "lyriconSubscriber onDisconnected")
            }

            override fun onConnectTimeout(subscriber: LyriconSubscriber) {
                YLog.info(TAG, "lyriconSubscriber onConnectTimeout")
            }

        })
        mainCoroutineScope.launch {
            delay(2000.milliseconds)
            subscriber.register()
        }
    }

    private fun initDataChannel() {
        val context = appContext ?: return
        LyriconBridge.routing(context) {
            onCommand(AppBridgeConstants.REQUEST_HIGHLIGHT_VIEW) {
                val id = it.getString("id")
                YLog.info(TAG, "App requested view highlight id: ")

                StatusBarViewManager.forEachOnMainThread { it.highlightView(id) }
            }

            onQuery(AppBridgeConstants.REQUEST_VIEW_TREE) {
                YLog.info(TAG, "App requested view tree")

                val controller = StatusBarViewManager.controllers.firstOrNull() ?: return@onQuery

                val data =
                    json.safeEncode(ViewHierarchyParser.buildNodeTree(controller.statusBarView))
                        .toByteArray(Charsets.UTF_8)
                        .deflate()

                YLog.info(TAG, "View tree reply data: ")

                reply(Bundle().apply {
                    putByteArray("result", data)
                })
            }

            onCommand(AppBridgeConstants.REQUEST_CLEAR_TRANSLATION_DB) {
                AiTranslator.clearCache { LyricDataHub.reprocessCurrentSong() }
            }
        }
    }

    /**
     * 将自定义控制器绑定到状态栏视图
     */
    private fun addStatusBarView(view: ViewGroup) {
        view.doOnAttach {
            val target = view
            val controller = StatusBarViewController(target, LyricPrefs.getLyricStyle())
            StatusBarViewManager.add(controller)

            val isFirst = StatusBarViewManager.controllers.size == 1
            if (isFirst) {
                if (TEST_CRASH) target.postDelayed({ error("test crash") }, 3000)
            }
        }
    }

    /**
     * 初始化崩溃相关的通信频道
     */
    private fun initCrashDataChannel() {
        val context = appContext ?: return
        LyriconBridge.routing(context) {
            onQuery(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE) {
                reply(Bundle().apply {
                    putBoolean("result", isSafeMode)
                })
            }
        }
    }
}