/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.isVisibleIfChanged
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusColor
import io.github.proify.lyricon.subscriber.ProviderLogo
import java.io.File
import kotlin.math.roundToInt

/**
 * 状态栏歌词的 Logo 视图，可显示专辑封面、Provider 图标、App 图标或用户自定义图片。
 *
 * 自身只负责三件事：
 * 1. 依据 [LyricStyle.packageStyle] 的 Logo 配置选择并切换 [LogoStrategy]；
 * 2. 管理策略切换时需要在 View 上复位的属性（旋转、轮廓、Tint、缩放模式）；
 * 3. 绘制间奏进度弧，并按多条件综合决定自身可见性。
 *
 * 内容加载与着色全部交由策略实现，视图本身不关心图片来源。
 */
@SuppressLint("AppCompatCustomView")
class LyricLogoView(context: Context) : ImageView(context) {

    companion object {
        private const val VIEW_TAG: String = "lyricon:logo_view"

        /** 兜底尺寸相对系统时钟字号的倍数。 */
        private const val TEXT_SIZE_MULTIPLIER = 1.2f

        /** 无法继承任何字号时的默认尺寸（dp）。 */
        private const val DEFAULT_SIZE_DP = 14

        /** 间奏进度弧的线宽（dp）。 */
        private const val PROGRESS_STROKE_DP = 2
    }

    /** 系统状态栏时钟视图，用于继承字号推导默认尺寸。 */
    var linkedTextView: TextView? = null

    /** 当前生效的策略；只允许通过内部的策略评估流程替换。 */
    var strategy: LogoStrategy? = null
        private set

    /** 歌词提供方图标数据；变化时清理旧缓存并重新评估策略。 */
    var providerLogo: ProviderLogo? = null
        set(value) {
            if (field === value) return
            field = value
            // 提供者变更后旧图标缓存必然失效，必须显式清理
            (strategy as? ProviderLogoStrategy)?.invalidateCache()
            selectStrategy()
        }

    /** 当前活跃播放器包名，仅 App 图标策略使用。 */
    var activePackage: String? = null
        set(value) {
            if (field == value) return
            field = value
            // 只有 App 图标策略的内容会随包名变化，其他策略无需刷新
            if (strategy is AppLogoStrategy) {
                strategy?.updateContent()
            }
        }

    /** 专辑封面文件，仅封面策略使用。 */
    var coverFile: File? = null

    /** 当前状态栏配色，进度弧与策略着色都依赖它。 */
    var statusColor: StatusColor = StatusColor()

    /** 当前歌词样式。 */
    var lyricStyle: LyricStyle? = null

    /** ColorOS 灵动胶囊是否正在显示。 */
    var isOplusCapsuleShowing: Boolean = false
        set(value) {
            field = value
            refreshVisibility()
        }

    /**
     * 是否被宿主强制隐藏。
     *
     * 与 [isOplusCapsuleShowing] 的区别：这是播放进入动画期间宿主主动要求的隐藏，
     * 优先级最高，不受样式开关影响。
     */
    internal var isForceHidden = false
        set(value) {
            field = value
            refreshVisibility()
        }

    // --- 间奏进度条 ---

    private var progress: Float = 0f

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 255
    }

    private val progressRect = RectF()
    private var progressAnimator: ValueAnimator? = null

    /** 上一次上报给策略的可见性，用于屏蔽重复回调。 */
    private var lastReportedVisible: Boolean? = null

    init {
        tag = VIEW_TAG
    }

    // --- 生命周期 ---

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 恢复策略活动（如重新开始封面旋转动画）
        strategy?.onAttach()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 暂停策略活动（停止动画、释放临时资源）
        strategy?.onDetach()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        dispatchVisibilityChange(visibility)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        dispatchVisibilityChange(visibility)
    }

    /**
     * 统一处理可见性回调。
     *
     * [onVisibilityChanged] 与 [onWindowVisibilityChanged] 可能对同一次状态变化各触发
     * 一次，这里按上一次上报值去重，避免策略重复启停动画。
     */
    private fun dispatchVisibilityChange(visibility: Int) {
        val visible = visibility == VISIBLE && isShown
        if (visible == lastReportedVisible) return

        lastReportedVisible = visible
        strategy?.onVisibilityChanged(visible)

        if (!visible) {
            // 不可见时进度弧没有意义，直接停止补间
            progressAnimator?.cancel()
        }
    }

    // --- 公开 API ---

    /**
     * 应用新的样式配置：更新布局参数并重新评估策略。
     */
    fun applyStyle(style: LyricStyle) {
        lyricStyle = style
        applyLayoutParams(style, style.packageStyle.logo)
        selectStrategy()
    }

    /** 更新状态栏配色：通知策略重算 Tint，并重绘进度弧。 */
    fun updateStatusColor(color: StatusColor) {
        statusColor = color
        if (strategy?.isEffective == true) {
            strategy?.onStatusColorChanged()
        }
        invalidate()
    }

    /**
     * 重新计算自身可见性。
     *
     * 四个条件同时成立才显示：样式启用、策略已产出内容、当前不处于胶囊隐藏模式、
     * 未被宿主强制隐藏。
     */
    fun refreshVisibility() {
        val logoConfig = lyricStyle?.packageStyle?.logo
        val isEnabled = logoConfig?.enable == true
        val isEffective = strategy?.isEffective == true
        val isHiddenInCapsule =
            logoConfig?.hideInColorOSCapsuleMode == true && isOplusCapsuleShowing

        this.isVisibleIfChanged = !isHiddenInCapsule && isEnabled && isEffective && !isForceHidden
    }

    // --- 进度条 ---

    /** 重置进度条并取消补间动画。 */
    fun clearProgress() {
        progressAnimator?.cancel()
        progressAnimator = null
        progress = 0f
        invalidate()
    }

    /**
     * 同步间奏进度，并为剩余时长启动补间动画。
     *
     * 进度以 0..1 表示间奏完成度，只在圆形封面模式下绘制（进度弧与圆形轮廓同心）。
     *
     * @param current  当前已播放时长（毫秒）
     * @param duration 间奏总时长（毫秒）
     */
    fun syncProgress(current: Long, duration: Long) {
        progressAnimator?.cancel()
        if (duration <= 0) return

        // 局部变量承接：strategy 是可变属性，无法智能转换
        val coverStrategy = strategy as? CoverLogoStrategy
        if (coverStrategy?.style != LogoStyle.STYLE_COVER_CIRCLE) return

        val startProgress = current.toFloat() / duration
        progress = startProgress
        invalidate()

        if (current >= duration) return

        progressAnimator = ValueAnimator.ofFloat(startProgress, 1f).apply {
            this.duration = duration - current
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                progress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 仅在进度处于中间态时绘制：0 与 1 都与静态轮廓重合，绘制反而产生视觉噪点
        if (strategy is CoverLogoStrategy && progress > 0f && progress < 1f) {
            drawProgress(canvas)
        }
    }

    private fun drawProgress(canvas: Canvas) {
        val strokeWidth = PROGRESS_STROKE_DP.dp.toFloat()
        val padding = strokeWidth / 2

        progressPaint.strokeWidth = strokeWidth
        progressPaint.color = statusColor.color.firstOrNull() ?: Color.TRANSPARENT

        progressRect.set(padding, padding, width - padding, height - padding)
        // 从 12 点方向开始顺时针绘制
        canvas.drawArc(progressRect, -90f, 360f * progress, false, progressPaint)
    }

    // --- 策略管理 ---

    /**
     * 依据当前样式评估并切换策略。
     *
     * 仅当策略类型变化时才执行完整切换（旧策略 detach -> 复位 View 属性 -> 新策略
     * attach），类型不变时只刷新内容，避免重复重建带来的闪烁与资源浪费。
     */
    private fun selectStrategy() {
        val logoConfig = lyricStyle?.packageStyle?.logo ?: return

        val newStrategy = when (logoConfig.style) {
            LogoStyle.STYLE_COVER_SQUIRCLE,
            LogoStyle.STYLE_COVER_CIRCLE -> CoverLogoStrategy(this)

            // Provider 图标缺失时不创建策略，让视图保持隐藏
            LogoStyle.STYLE_PROVIDER_LOGO ->
                if (providerLogo == null) null else ProviderLogoStrategy(this)

            LogoStyle.STYLE_APP_LOGO -> AppLogoStrategy(this)
            LogoStyle.STYLE_LOGO_CUSTOM -> CustomLogoStrategy(this)
            else -> null
        }

        if (strategy?.javaClass == newStrategy?.javaClass) {
            // 策略类型未变：只需刷新内容
            strategy?.updateContent()
        } else {
            strategy?.onDetach()
            clearStrategyAttributes()

            strategy = newStrategy

            // 已 attach 时立即补齐新策略的 attach 生命周期
            if (isAttachedToWindow) {
                newStrategy?.onAttach()
            }
            newStrategy?.updateContent()
        }

        refreshVisibility()
    }

    /**
     * 清除上一个策略在 View 上留下的属性，防止样式残留。
     *
     * 涉及四类属性：旋转角度、裁剪轮廓、Tint、缩放模式。
     */
    private fun clearStrategyAttributes() {
        rotation = 0f
        outlineProvider = null
        clipToOutline = false
        imageTintList = null
        scaleType = ScaleType.FIT_CENTER
    }

    // --- 布局 ---

    private fun applyLayoutParams(style: LyricStyle, logoStyle: LogoStyle) {
        val defaultSize = resolveDefaultSize(style)
        val width = if (logoStyle.width <= 0) defaultSize else logoStyle.width.dp
        val height = if (logoStyle.height <= 0) defaultSize else logoStyle.height.dp

        val params = (layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(width, height)
        params.width = width
        params.height = height
        applyMargins(params, logoStyle.margins)

        layoutParams = params
    }

    private fun applyMargins(
        params: LinearLayout.LayoutParams,
        margins: io.github.proify.lyricon.lyric.style.RectF
    ) {
        params.leftMargin = margins.left.dp
        params.topMargin = margins.top.dp
        params.rightMargin = margins.right.dp
        params.bottomMargin = margins.bottom.dp
    }

    /**
     * 解析 Logo 兜底尺寸。
     *
     * 优先级：文本自定义字号 > 系统时钟字号 × [TEXT_SIZE_MULTIPLIER] > [DEFAULT_SIZE_DP]。
     */
    private fun resolveDefaultSize(style: LyricStyle): Int {
        val configuredTextSize = style.packageStyle.text.textSize
        if (configuredTextSize > 0) return configuredTextSize.dp

        val clockTextSize = linkedTextView?.textSize
        if (clockTextSize != null) {
            return (clockTextSize * TEXT_SIZE_MULTIPLIER).roundToInt()
        }

        return DEFAULT_SIZE_DP.dp
    }
}
