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
    private val TARGET_ID_NAMES = setOf(
        "island_container",
        "island_music_container",
        "music_container",
        "audio_container",
        "media_container"
    )
    private const val TARGET_CLASS_KEYWORD = "miui.systemui.dynamicisland"

    private data class ViewState(
        val width: Int,
        val height: Int,
        val visibility: Int,
        val alpha: Float,
        val clickable: Boolean,
        val enabled: Boolean,
        val focusable: Boolean
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

        if (!isXiaomiHyperOs3OrAbove()) return

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

    private fun isXiaomiHyperOs3OrAbove(): Boolean {
        return isXiaomiFamilyDevice() && detectHyperOsMajor() >= 3
    }

    private fun detectHyperOsMajor(): Int {
        val sources = listOfNotNull(
            getSystemProperty("ro.system.build.version.incremental"),
            getSystemProperty("ro.build.version.incremental"),
            getSystemProperty("ro.vendor.build.version.incremental"),
            getSystemProperty("ro.system.build.fingerprint"),
            getSystemProperty("ro.vendor.build.fingerprint"),
            Build.DISPLAY,
            Build.FINGERPRINT
        )
        val regex = Regex("""(?i)\bOS(\d+)(?:\.\d+)*""")
        return sources
            .mapNotNull { source ->
                regex.find(source)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            .maxOrNull() ?: 0
    }

    private fun getSystemProperty(key: String): String? {
        return runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, key, "") as? String)?.trim().orEmpty()
        }.getOrNull()?.takeIf { it.isNotBlank() }
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
        val idName = if (view.id != View.NO_ID) {
            runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
        } else {
            null
        }
        val normalizedIdName = idName.orEmpty().lowercase()
        val className = view.javaClass.name.lowercase()

        val matchByExactId = normalizedIdName in TARGET_ID_NAMES
        val matchByIslandId = normalizedIdName.contains("island")
                && (normalizedIdName.contains("music")
                || normalizedIdName.contains("audio")
                || normalizedIdName.contains("media")
                || normalizedIdName.contains("icon"))
        val matchByClass = className.contains(TARGET_CLASS_KEYWORD)
                || (className.contains("island")
                && (className.contains("dynamic")
                || className.contains("music")
                || className.contains("audio")
                || className.contains("media")
                || className.contains("icon")
                || className.contains("capsule")
                || className.contains("bubble")))

        if (!matchByExactId && !matchByIslandId && !matchByClass) return false

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
            YLog.info(tag = TAG, msg = "Tracked island view: id=$idName class=${view.javaClass.name} view=$view")
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
        val lp = view.layoutParams ?: return

        if (shouldHide) {
            if (originalStates[view] == null) {
                originalStates[view] = ViewState(
                    width = lp.width,
                    height = lp.height,
                    visibility = view.visibility,
                    alpha = view.alpha,
                    clickable = view.isClickable,
                    enabled = view.isEnabled,
                    focusable = view.isFocusable
                )
            }
            if (lp.width != 0 || lp.height != 0) {
                lp.width = 0
                lp.height = 0
                view.layoutParams = lp
            }
            if (view.alpha != 0f) {
                view.alpha = 0f
            }
            if (view.visibility != View.GONE) {
                view.visibility = View.GONE
            }
            if (view.isClickable) {
                view.isClickable = false
            }
            if (view.isEnabled) {
                view.isEnabled = false
            }
            if (view.isFocusable) {
                view.isFocusable = false
            }
            view.requestLayout()
            return
        }

        val originalState = originalStates[view] ?: return
        if (lp.width != originalState.width || lp.height != originalState.height) {
            lp.width = originalState.width
            lp.height = originalState.height
            view.layoutParams = lp
        }
        if (view.alpha != originalState.alpha) {
            view.alpha = originalState.alpha
        }
        if (view.visibility != originalState.visibility) {
            view.visibility = originalState.visibility
        }
        if (view.isClickable != originalState.clickable) {
            view.isClickable = originalState.clickable
        }
        if (view.isEnabled != originalState.enabled) {
            view.isEnabled = originalState.enabled
        }
        if (view.isFocusable != originalState.focusable) {
            view.isFocusable = originalState.focusable
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
            if (!XiaomiIslandHooker.hideByLyric) return
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
