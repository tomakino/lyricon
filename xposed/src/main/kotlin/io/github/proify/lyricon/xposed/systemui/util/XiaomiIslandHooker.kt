/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.util

import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * 小米超级岛（island_container）运行时控制器：
 * 在系统界面进程中直接追踪目标 View，并在歌词显示时将尺寸压缩到 0，隐藏时恢复原尺寸。
 */
object XiaomiIslandHooker {
    private const val TAG = "XiaomiIslandHooker"
    private const val TARGET_ID_NAME = "island_container"

    private data class ViewState(
        val width: Int,
        val height: Int,
        val visibility: Int
    )

    private val islandViews = mutableListOf<WeakReference<View>>()
    private val originalStates = WeakHashMap<View, ViewState>()

    private var hideByLyric = false
    private var lastHideByLyric: Boolean? = null
    private val unhooks = mutableListOf<XC_MethodHook.Unhook>()
    private var addViewHooked = false

    fun initialize(classLoader: ClassLoader) {
        unhooks.forEach { it.unhook() }
        unhooks.clear()
        addViewHooked = false
        islandViews.clear()
        originalStates.clear()
        hideByLyric = false
        lastHideByLyric = null

        if (!isXiaomiFamilyDevice()) return

        unhooks += XposedHelpers.findAndHookMethod(
            classLoader.loadClass(View::class.java.name),
            "onAttachedToWindow",
            TrackIslandAttachHook()
        )
        unhooks += XposedHelpers.findAndHookMethod(
            classLoader.loadClass(View::class.java.name),
            "setVisibility",
            Int::class.javaPrimitiveType,
            TrackIslandVisibilityHook()
        )
        hookViewGroupAddView(classLoader)
        YLog.info(tag = TAG, msg = "Initialized")
    }

    fun setHideByLyric(shouldHide: Boolean) {
        hideByLyric = shouldHide
        if (lastHideByLyric == shouldHide) {
            // 允许在同状态下重复执行，以修复部分 ROM 下视图重建后的残留隐藏状态
            if (!shouldHide) {
                applyStateToTrackedViews(false)
            }
            return
        }
        lastHideByLyric = shouldHide
        applyStateToTrackedViews(shouldHide)
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        val brand = Build.BRAND.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        return brand.contains("xiaomi")
                || brand.contains("redmi")
                || brand.contains("poco")
                || manufacturer.contains("xiaomi")
                || manufacturer.contains("redmi")
                || manufacturer.contains("poco")
                || product.contains("xiaomi")
                || product.contains("redmi")
                || product.contains("poco")
    }

    private fun applyStateToTrackedViews(shouldHide: Boolean) {
        val iterator = islandViews.iterator()
        while (iterator.hasNext()) {
            val view = iterator.next().get()
            if (view == null) {
                iterator.remove()
                continue
            }
            applyState(view, shouldHide)
        }
    }

    private fun tryTrackIslandView(view: View): Boolean {
        if (view.id == View.NO_ID) return false

        val idName = runCatching {
            view.resources.getResourceEntryName(view.id)
        }.getOrNull() ?: return false
        if (idName != TARGET_ID_NAME) return false

        val iterator = islandViews.iterator()
        var exists = false
        while (iterator.hasNext()) {
            val tracked = iterator.next().get()
            if (tracked == null) {
                iterator.remove()
            } else if (tracked === view) {
                exists = true
            }
        }
        if (!exists) {
            islandViews.add(WeakReference(view))
            YLog.info(tag = TAG, msg = "Tracked island_container: $view")
        }
        return true
    }

    private fun trackViewTree(root: View) {
        if (tryTrackIslandView(root)) {
            applyState(root, hideByLyric)
        }
        if (root !is ViewGroup) return
        val count = root.childCount
        for (index in 0 until count) {
            val child = root.getChildAt(index) ?: continue
            trackViewTree(child)
        }
    }

    private fun applyState(view: View, shouldHide: Boolean) {
        if (shouldHide) {
            val lp = view.layoutParams
            originalStates[view] = originalStates[view] ?: ViewState(
                width = lp?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                height = lp?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                visibility = view.visibility
            )
            if (view.visibility != View.GONE) {
                view.visibility = View.GONE
            }
            if (lp != null && (lp.width != 0 || lp.height != 0)) {
                lp.width = 0
                lp.height = 0
                view.layoutParams = lp
            }
            view.requestLayout()
            return
        }

        val originalState = originalStates[view] ?: return
        val lp = view.layoutParams
        if (lp != null) {
            if (lp.width != originalState.width || lp.height != originalState.height) {
                lp.width = originalState.width
                lp.height = originalState.height
                view.layoutParams = lp
            }
        }
        if (view.visibility != originalState.visibility) {
            view.visibility = originalState.visibility
        }
        view.requestLayout()
    }

    private class TrackIslandAttachHook : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val view = param.thisObject as? View ?: return
            XiaomiIslandHooker.trackViewTree(view)
        }
    }

    private class TrackIslandVisibilityHook : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val view = param.thisObject as? View ?: return
            if (!XiaomiIslandHooker.tryTrackIslandView(view)) return
            XiaomiIslandHooker.applyState(view, XiaomiIslandHooker.hideByLyric)
        }
    }

    private fun hookViewGroupAddView(classLoader: ClassLoader) {
        if (addViewHooked) return
        addViewHooked = true
        val viewGroupClass = classLoader.loadClass(ViewGroup::class.java.name)
        unhooks.addAll(
            XposedBridge.hookAllMethods(
            viewGroupClass,
            "addView",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val child = param.args.getOrNull(0) as? View ?: return
                    trackViewTree(child)
                }
            }
            )
        )
    }
}
