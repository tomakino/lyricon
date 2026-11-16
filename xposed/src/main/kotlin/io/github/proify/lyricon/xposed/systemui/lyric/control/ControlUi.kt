/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.control

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import io.github.proify.android.extensions.dp
import io.github.proify.lyricon.xposed.BuildConfig
import io.github.proify.lyricon.xposed.logger.YLog

/**
 * 控制窗口的通用 UI 工具：圆角 Drawable、按钮按压反馈、模块资源访问。
 *
 * @author Tomakino
 * @since 2026
 */
internal object ControlUi {

    private const val TAG = "ControlUi"

    /**
     * 检查设备是否支持跨窗口背景模糊。
     *
     * 通过读取系统属性 `ro.surface_flinger.supports_background_blur` 和运行时状态判断。
     * 低电量模式、多媒体隧道等场景下可能被禁用。
     */
    fun isBackgroundBlurSupported(): Boolean {
        return try {
            val clazz = Class.forName("android.view.CrossWindowBlurListeners")
            val field = clazz.getField("CROSS_WINDOW_BLUR_SUPPORTED")
            val supported = field.getBoolean(null)
            YLog.debug(TAG, "isBackgroundBlurSupported: $supported")
            supported
        } catch (e: Exception) {
            YLog.warning(TAG, "Failed to check blur support: ${e.message}")
            false
        }
    }

    /**
     * 为 View 创建背景模糊 Drawable。
     *
     * 由于运行在 SystemUI 进程内，可直接调用系统内部 API [ViewRootImpl.createBackgroundBlurDrawable]。
     * 若设备不支持或创建失败，返回 null，调用方应降级到纯色背景。
     *
     * @param view 目标 View（需已附加到窗口）
     * @param blurRadius 模糊半径（像素），建议 80-100 实现重度模糊
     * @param cornerRadius 圆角半径（像素）
     * @param overlayColor 叠加在模糊上方的颜色（ARGB）
     * @return BackgroundBlurDrawable 实例，失败返回 null
     */
    fun createBackgroundBlur(
        view: View,
        blurRadius: Int,
        cornerRadius: Float,
        overlayColor: Int = Color.TRANSPARENT
    ): Drawable? {
        if (!isBackgroundBlurSupported()) {
            YLog.info(TAG, "Background blur not supported, will fallback to solid color")
            return null
        }

        return try {
            // 通过反射获取 ViewRootImpl（getViewRootImpl() 是 @hide API）
            val getViewRootImpl = View::class.java.getMethod("getViewRootImpl")
            val viewRootImpl = getViewRootImpl.invoke(view)
            if (viewRootImpl == null) {
                YLog.warning(TAG, "ViewRootImpl is null, view not attached to window?")
                return null
            }
            YLog.debug(TAG, "ViewRootImpl obtained: ${viewRootImpl.javaClass.simpleName}")

            // 调用 ViewRootImpl.createBackgroundBlurDrawable()
            val createMethod = viewRootImpl.javaClass.getMethod("createBackgroundBlurDrawable")
            val drawable = createMethod.invoke(viewRootImpl) as? Drawable
            if (drawable == null) {
                YLog.warning(TAG, "createBackgroundBlurDrawable returned null")
                return null
            }
            YLog.debug(TAG, "BackgroundBlurDrawable created: ${drawable.javaClass.simpleName}")

            // 设置模糊参数
            val drawableClass = drawable.javaClass

            val setBlurRadius = drawableClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType)
            setBlurRadius.invoke(drawable, blurRadius)
            YLog.debug(TAG, "Blur radius set to: $blurRadius")

            val setCornerRadius = drawableClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType)
            setCornerRadius.invoke(drawable, cornerRadius)
            YLog.debug(TAG, "Corner radius set to: $cornerRadius")

            val setColor = drawableClass.getMethod("setColor", Int::class.javaPrimitiveType)
            setColor.invoke(drawable, overlayColor)
            YLog.debug(TAG, "Overlay color set to: #${Integer.toHexString(overlayColor)}")

            drawable.alpha = 255
            YLog.info(TAG, "Background blur created successfully: radius=$blurRadius, corner=$cornerRadius")
            drawable
        } catch (e: Exception) {
            YLog.error(TAG, "Failed to create background blur", e)
            null
        }
    }

    /**
     * 更新 BackgroundBlurDrawable 的叠加颜色。
     *
     * @param drawable 背景模糊 Drawable 实例
     * @param color 新的叠加颜色（ARGB）
     */
    fun updateBlurOverlayColor(drawable: Drawable, color: Int) {
        try {
            val setColor = drawable.javaClass.getMethod("setColor", Int::class.javaPrimitiveType)
            setColor.invoke(drawable, color)
            YLog.debug(TAG, "Updated blur overlay color: #${Integer.toHexString(color)}")
        } catch (e: Exception) {
            // 非 BackgroundBlurDrawable 或 API 变化，静默忽略
            YLog.debug(TAG, "Failed to update blur overlay color: ${e.message}")
        }
    }

    /** 构造纯色圆角矩形 Drawable，可选描边。 */
    fun roundedDrawable(
        color: Int,
        radius: Float,
        strokeColor: Int? = null
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
        if (strokeColor != null) {
            setStroke(1.dp, strokeColor)
        }
    }

    /**
     * 轻微按压缩放反馈。不消费触摸事件（返回 false），不影响点击派发。
     *
     * 手指移出 View 边界时自动取消按压效果，避免滑动手势误触发按压视觉反馈。
     * 使用 DecelerateInterpolator：快速按下，柔和弹回。
     */
    @SuppressLint("ClickableViewAccessibility")
    fun pressFeedbackListener(): View.OnTouchListener {
        return View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(ControlAnimations.PRESS_DOWN_SCALE)
                        .scaleY(ControlAnimations.PRESS_DOWN_SCALE)
                        .setDuration(ControlAnimations.PRESS_DOWN_DURATION_MS)
                        .setInterpolator(ControlAnimations.PRESS_DOWN_INTERPOLATOR)
                        .start()
                }

                MotionEvent.ACTION_MOVE -> {
                    // 手指移出 View 边界 → 取消按压效果
                    if (event.x < 0f || event.y < 0f
                        || event.x > v.width || event.y > v.height
                    ) {
                        v.animate().cancel()
                        v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(ControlAnimations.PRESS_UP_DURATION_MS)
                            .setInterpolator(ControlAnimations.PRESS_UP_INTERPOLATOR)
                            .start()
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(ControlAnimations.PRESS_UP_DURATION_MS)
                        .setInterpolator(ControlAnimations.PRESS_UP_INTERPOLATOR)
                        .start()
                }
            }
            false
        }
    }

    /**
     * 获取宿主应用（模块 App）的 Resources，用于加载模块自带的矢量图标。
     */
    fun moduleResources(context: Context): Resources {
        return context.createPackageContext(
            BuildConfig.APP_PACKAGE_NAME,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
        ).resources
    }
}
