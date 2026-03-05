/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.util

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * colorOS监听流体云显示
 *
 * ColorOS 流体云在多应用同时使用时，会创建多个 CapsuleView 实例，
 * 通过轮流切换它们的可见性来展示不同应用的胶囊。切换时序为：
 *   旧 CapsuleView -> INVISIBLE，新 CapsuleView -> VISIBLE
 * 两者可能在同一毫秒内发生，但调用顺序不固定。
 *
 * 修复策略：
 * 1. 用 WeakReference 列表追踪所有 CapsuleView/CapsuleContainer 实例，
 *    只要任意一个可见就认为流体云在显示。
 * 2. 对「隐藏」事件做 debounce（延迟 80ms），避免切换间隙的瞬态 false。
 *    「显示」事件立即生效并取消待执行的隐藏。
 */
object OplusCapsuleHooker {
    private val listeners = CopyOnWriteArrayList<CapsuleStateChangeListener>()

    var isShowing: Boolean = false
        private set

    private var lastIsShowing: Boolean? = null
    private var unhook: XC_MethodHook.Unhook? = null
    private var currentHook: SetVisibilityMethodHook? = null

    @SuppressLint("PrivateApi")
    fun isSupportCapsule(classLoader: ClassLoader): Boolean = try {
        classLoader.loadClass("com.android.systemui.plugins.statusbar.CapsulePlugin") != null
    } catch (_: Exception) {
        false
    }

    fun initialize(classLoader: ClassLoader) {
        unhook?.unhook()
        currentHook?.cleanup()
        if (!isSupportCapsule(classLoader)) return

        val hook = SetVisibilityMethodHook()
        currentHook = hook
        unhook = XposedHelpers.findAndHookMethod(
            classLoader.loadClass(
                View::class.java.getName()
            ),
            "setVisibility",
            Int::class.javaPrimitiveType, hook
        )
    }

    fun registerListener(listener: CapsuleStateChangeListener): Boolean = listeners.add(listener)
    fun unregisterListener(listener: CapsuleStateChangeListener): Boolean =
        listeners.remove(listener)

    private fun triggerEvent() {
        if (lastIsShowing != null && lastIsShowing == isShowing) return

        lastIsShowing = isShowing
        listeners.forEach { it.onCapsuleVisibilityChanged(isShowing) }
    }

    private class SetVisibilityMethodHook : XC_MethodHook() {
        companion object {
            /** 隐藏事件的 debounce 延迟，用于吸收胶囊切换时的瞬态 INVISIBLE */
            private const val HIDE_DEBOUNCE_MS = 80L
        }

        private val capsuleViews = mutableListOf<WeakReference<View>>()
        private val capsuleContainers = mutableListOf<WeakReference<View>>()

        private val handler = Handler(Looper.getMainLooper())
        private var pendingHideRunnable: Runnable? = null

        /**
         * 将 view 加入追踪列表（如果尚未追踪），同时清理已被 GC 回收的弱引用。
         */
        private fun trackView(list: MutableList<WeakReference<View>>, view: View) {
            val iterator = list.iterator()
            var found = false
            while (iterator.hasNext()) {
                val ref = iterator.next().get()
                if (ref == null) {
                    iterator.remove()
                } else if (ref === view) {
                    found = true
                }
            }
            if (!found) {
                list.add(WeakReference(view))
            }
        }

        /**
         * 检查追踪列表中是否有任意一个 view 处于 VISIBLE 状态。
         * 同时遍历完整个列表以清理已被 GC 回收的 WeakReference。
         */
        private fun anyVisible(list: MutableList<WeakReference<View>>): Boolean {
            val iterator = list.iterator()
            var visibleFound = false
            while (iterator.hasNext()) {
                val ref = iterator.next().get()
                if (ref == null) {
                    iterator.remove()
                } else if (ref.visibility == View.VISIBLE) {
                    visibleFound = true
                }
            }
            return visibleFound
        }

        /**
         * 清理所有状态：取消待执行的隐藏回调并清空追踪列表。
         * 在 re-initialize / unhook 时调用，防止旧回调基于过期状态运行。
         */
        fun cleanup() {
            pendingHideRunnable?.let { handler.removeCallbacks(it) }
            pendingHideRunnable = null
            capsuleViews.clear()
            capsuleContainers.clear()
        }

        @Throws(Throwable::class)
        override fun afterHookedMethod(param: MethodHookParam) {
            val view = param.thisObject as View
            val name = view.javaClass.getSimpleName()

            if ("CapsuleContainer" == name) {
                trackView(capsuleContainers, view)
            } else if ("CapsuleView" == name) {
                trackView(capsuleViews, view)
            } else {
                return
            }

            val newShowing = anyVisible(capsuleContainers) && anyVisible(capsuleViews)

            if (newShowing) {
                // 流体云可见：立即生效，取消待执行的隐藏
                pendingHideRunnable?.let { handler.removeCallbacks(it) }
                pendingHideRunnable = null
                isShowing = true
                triggerEvent()
            } else if (isShowing) {
                // CapsuleContainer 变为不可见说明流体云真正消失（用户展开或关闭），
                // 应该立即响应，不需要 debounce。
                // 只有 CapsuleView 之间切换（Container 仍可见）才需要 debounce。
                val containerStillVisible = anyVisible(capsuleContainers)
                if (!containerStillVisible) {
                    // Container 已隐藏，立即生效
                    pendingHideRunnable?.let { handler.removeCallbacks(it) }
                    pendingHideRunnable = null
                    isShowing = false
                    triggerEvent()
                } else if (pendingHideRunnable == null) {
                    // Container 可见但所有 CapsuleView 不可见，说明正在切换胶囊，延迟执行
                    val runnable = Runnable {
                        pendingHideRunnable = null
                        // 再次确认，防止在延迟期间又有新胶囊变为可见
                        val stillShowing = anyVisible(capsuleContainers) && anyVisible(capsuleViews)
                        isShowing = stillShowing
                        triggerEvent()
                    }
                    pendingHideRunnable = runnable
                    handler.postDelayed(runnable, HIDE_DEBOUNCE_MS)
                }
            }
        }
    }

    interface CapsuleStateChangeListener {
        fun onCapsuleVisibilityChanged(isShowing: Boolean)
    }
}
