/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.control

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.setPadding
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.sp
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.xposed.BuildConfig
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.lyric.control.LyricControlPanel.Companion.NO_SEEK_TARGET
import io.github.proify.lyricon.xposed.systemui.util.MediaTrackMeta
import kotlin.math.abs

/**
 * 状态栏歌词控制面板的内容视图（卡片本体）。
 *
 * 布局结构（全部代码构建，运行在 SystemUI 进程内）：
 *  [封面] [歌名/歌手 + 进度条 + 时间行]
 *  [占位] [上一首] [播放/暂停] [下一首] [AI]
 *
 * 面板持有全部子视图引用并封装更新方法（歌曲、进度、封面、背景取色），
 * 交互则通过 [ActionListener] 回抛给宿主（[LyricControlPopup]）。
 *
 * @author Tomakino
 * @since 2026
 */
@Suppress("JoinDeclarationAndAssignment")
@SuppressLint("ClickableViewAccessibility")
class LyricControlPanel(context: Context) : FrameLayout(context) {

    /** 面板交互回调。 */
    interface ActionListener {
        fun onPrevious()
        fun onTogglePlay()
        fun onNext()
        fun onSeekTo(position: Long)
        fun onAiExplain(button: View)

        /** 点击卡片进入音乐软件 */
        fun onOpenApp()
    }

    var actionListener: ActionListener? = null

    /** 封面翻转动画进行中，防止动画重叠 */
    private var isFlipping = false

    /** 是否是首次设置封面（首次设置不播放动画） */
    private var isFirstCover = true

    /** 上一次封面的文件最后修改时间，用于判断封面是否变化 */
    private var lastCoverModified: Long = 0L

    // ---- 视图（在 init 中创建并组装） ----
    private val card: LinearLayout
    private val coverView: ImageView

    /**
     * 标题视图：跑马灯专用（[MarqueeTitleView]）。
     * 弹窗是非焦点 PopupWindow（isFocusable = false），窗口与标题视图都拿不到焦点，
     * 原生 TextView 的跑马灯会因焦点缺失/窗口焦点回调而被停掉、反复重置；
     * [MarqueeTitleView] 自带帧循环滚动实现，不依赖系统跑马灯，行为稳定。
     */
    private val titleView: MarqueeTitleView
    private val artistView: TextView
    private val seekBar: SeekBar
    private val currentTimeView: TextView
    private val totalTimeView: TextView
    private val previousButton: ImageView
    private val playButton: ImageView
    private val nextButton: ImageView
    private val aiButton: ImageView

    /** 模块（宿主 App）Resources，用于加载自带图标。 */
    private val moduleRes: Resources = ControlUi.moduleResources(context)

    /** 通过名称缓存的 drawable 资源 ID，避免重复反射查找。 */
    private val drawableIdCache = mutableMapOf<String, Int>()

    /**
     * 根据 drawable 资源名称获取资源 ID，结果会缓存以避免重复查找。
     *
     * @param name drawable 资源名称（不含扩展名）
     * @return 资源 ID，找不到时返回 0
     */
    @SuppressLint("DiscouragedApi")
    private fun drawableId(name: String): Int {
        return drawableIdCache.getOrPut(name) {
            moduleRes.getIdentifier(name, "drawable", BuildConfig.APP_PACKAGE_NAME)
        }
    }

    /** 当前歌曲时长（毫秒），进度换算的数据源。 */
    private var songDurationMs: Long = 0

    /** 用户是否正在拖动进度条（期间抑制外部进度刷新）。 */
    private var isSeeking: Boolean = false

    /** 刚体锁定：最近一次拖动释放后的 seek 目标位置（[NO_SEEK_TARGET] 表示未锁定）。 */
    private var seekTargetPosition: Long = NO_SEEK_TARGET

    /** 最近一次绑定的标题，用于识别"是否真的切歌"。 */
    private var lastBoundTitle: String? = null

    /** 刚体锁定的最长持续时间（毫秒时间戳），超时兜底恢复外部刷新。 */
    private var seekLockExpireAt: Long = 0L

    /**
     * 当前卡片背景：优先使用实时模糊 Drawable，设备不支持时降级为纯色 GradientDrawable。
     * 用于 [setBackdropColor] 更新叠加色时判断背景类型。
     */
    private var blurBackground: Drawable? = null

    /** 当前使用的封面主色（用于重新打开 popup 时恢复） */
    private var currentCoverColor: Int = Color.TRANSPARENT

    /**
     * 设置模糊背景的透明度 (0~255)。
     *
     * BackgroundBlurDrawable 是系统内部 drawable，不响应 View 的 alpha 变化，
     * 需要在动画过程中手动调用此方法更新透明度。
     */
    fun setBlurAlpha(alpha: Int) {
        blurBackground?.let {
            try {
                it.alpha = alpha.coerceIn(0, 255)
            } catch (e: Exception) {
                // 部分设备可能不支持动态修改 alpha
                YLog.debug(TAG, "setBlurAlpha failed: ${e.message}")
            }
        }
    }


    init {
        YLog.debug(TAG, "Initializing LyricControlPanel")

        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(CARD_PADDING_DP.dp)
            clipToOutline = true
            outlineProvider = G2RoundedCorner.outlineProvider(CARD_RADIUS_DP.dp.toFloat())
            // 先使用纯色背景，延迟到 onAttachedToWindow 中创建模糊效果
            background = ControlUi.roundedDrawable(CARD_COLOR, CARD_RADIUS_DP.dp.toFloat())
            // 点击卡片进入音乐软件
            setOnClickListener {
                actionListener?.onOpenApp()
            }
        }

        coverView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(COVER_SIZE_DP.dp, COVER_SIZE_DP.dp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = G2RoundedCorner.outlineProvider(COVER_RADIUS_DP.dp.toFloat())
            background = ControlUi.roundedDrawable(
                COVER_PLACEHOLDER_COLOR, COVER_RADIUS_DP.dp.toFloat()
            )
        }

        titleView = MarqueeTitleView(context).apply {
            textSize = TITLE_TEXT_SIZE_SP.sp
            textColor = TEXT_COLOR_PRIMARY
            textTypeface = Typeface.create(Typeface.DEFAULT, 500, false)
            // 单行、滚动与渐隐参数均由 MarqueeTitleView 内部管理
        }

        artistView = TextView(context).apply {
            textSize = ARTIST_TEXT_SIZE_SP
            setTextColor(TEXT_COLOR_SECONDARY)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        seekBar = SeekBar(context).apply {
            // 长圆形（胶囊）轨道：非 Material 细线，两端为半圆
            progressDrawable = longRoundedTrack()
            // 去掉拖动圆球（thumb），纯进度条；仍可点击/拖动轨道进行 seek
            thumb = null
            // 取消素材主题自带的按压圆形 ripple
            background = null
            max = SEEK_MAX
            splitTrack = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentTimeView.text = formatTime(progressToPosition(progress))
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    isSeeking = true
                }

                override fun onStopTrackingTouch(sb: SeekBar) {
                    isSeeking = false
                    val target = progressToPosition(sb.progress)
                    // 进入刚体锁定：等待播放器上报位置接近目标（或超时）前，
                    // 抑制外部进度刷新，避免 seek 生效前被旧位置覆盖来回跳动
                    seekTargetPosition = target
                    seekLockExpireAt = SystemClock.uptimeMillis() + SEEK_LOCK_DURATION_MS
                    actionListener?.onSeekTo(target)
                }
            })
            setPadding(0)
        }

        currentTimeView = TextView(context).apply {
            textSize = TIME_TEXT_SIZE_SP
            setTextColor(TEXT_COLOR_SECONDARY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
        }

        totalTimeView = TextView(context).apply {
            textSize = TIME_TEXT_SIZE_SP
            setTextColor(TEXT_COLOR_SECONDARY)
        }

        previousButton = ImageView(context).apply {
            setImageDrawable(
                moduleRes.getDrawable(
                    drawableId("sui_control_skip_previous_fill1_24px"),
                    context.theme
                )
            )
            setOnTouchListener(ControlUi.pressFeedbackListener())
            setOnClickListener { actionListener?.onPrevious() }
        }

        playButton = ImageView(context).apply {
            setImageDrawable(playPauseIcon(playing = false))
            setOnTouchListener(ControlUi.pressFeedbackListener())
            setOnClickListener { actionListener?.onTogglePlay() }
        }

        nextButton = ImageView(context).apply {
            setImageDrawable(
                moduleRes.getDrawable(
                    drawableId("sui_control_skip_next_fill1_24px"),
                    context.theme
                )
            )
            setOnTouchListener(ControlUi.pressFeedbackListener())
            setOnClickListener { actionListener?.onNext() }
        }

        aiButton = ImageView(context).apply {
            setImageDrawable(
                moduleRes.getDrawable(
                    drawableId("sui_control_gemini_ai"),
                    context.theme
                )
            )
            setOnTouchListener(ControlUi.pressFeedbackListener())
            setOnClickListener { v -> actionListener?.onAiExplain(v) }
        }

        buildInfoRow()
        buildActionRow()

        // 允许卡片平移时不被父容器裁剪（overscroll 效果需要）
        clipChildren = false
        clipToPadding = false

        addView(
            card,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        bindSong(song = null, position = 0L)
    }

    // -------------------------------------------------------------------------
    // 生命周期
    // -------------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // View 已附加到窗口，此时可以安全获取 ViewRootImpl
        YLog.debug(TAG, "onAttachedToWindow: View attached, attempting to create blur background")
        initBlurBackground()
    }

    /**
     * 初始化模糊背景（在 View 附加到窗口后调用）。
     *
     * 此时 ViewRootImpl 已可用，可以创建 BackgroundBlurDrawable。
     * 若设备不支持或创建失败，保持当前纯色背景不变。
     */
    private fun initBlurBackground() {
        val existingBlur = blurBackground
        if (existingBlur != null) {
            YLog.debug(TAG, "initBlurBackground: blur already initialized, skip")
            return
        }

        // 使用保存的封面主色（如果有），否则使用默认的深黑色
        val baseColor =
            if (currentCoverColor != Color.TRANSPARENT) currentCoverColor else CARD_COLOR
        val initialColor = applyOverlayAlpha(baseColor, OVERLAY_ALPHA)
        val alphaPercent = (OVERLAY_ALPHA * 100).toInt()
        YLog.debug(
            TAG,
            "initBlurBackground: creating blur, radius=$BLUR_RADIUS_PX, alpha=${OVERLAY_ALPHA}f ($alphaPercent%), color=#${
                Integer.toHexString(initialColor)
            }"
        )

        blurBackground = ControlUi.createBackgroundBlur(
            view = this,
            blurRadius = BLUR_RADIUS_PX,
            cornerRadius = CARD_RADIUS_DP.dp.toFloat(),
            overlayColor = initialColor
        )
        val isBlurMode = blurBackground != null
        if (isBlurMode) {
            card.background = blurBackground
            YLog.info(
                TAG,
                "✅ initBlurBackground: blur ENABLED, radius=${BLUR_RADIUS_PX}px, alpha=${OVERLAY_ALPHA}f ($alphaPercent%)"
            )
        } else {
            YLog.warning(TAG, "❌ initBlurBackground: blur NOT available")
        }
    }

    /**
     * 消费未被子视图处理的触摸事件，确保事件链不断（UP/CANCEL 一定能到达）。
     * 仅在没有子视图消费 DOWN 时被调用（即点击空白区域），不影响子视图正常工作。
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean = true

    // -------------------------------------------------------------------------
    // 触摸效果：按压缩放 + 垂直 Overscroll 越界回弹
    // -------------------------------------------------------------------------

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchMode = TOUCH_MODE_UNDECIDED
    private var touchOverscroll = false

    /** DOWN 时是否在子控件上（按钮、SeekBar），用于跳过卡片按压。 */
    private var touchOnChild = false

    /** 正在进行的弹簧回弹动画，用于快速触摸时取消。 */
    private var springAnimator: ValueAnimator? = null

    /**
     * 标准 ViewGroup 事件分发，在子视图分发前处理卡片触摸效果。
     *
     * 规则：
     * - 点在子控件上：不做 card 按压，子控件自己的 pressFeedback 正常工作
     * - 点在空白区域：card 按压缩放，松手回弹
     * - 垂直拖动（任何位置）：overscroll，松手弹簧回弹
     * - 水平拖动：不拦截，SeekBar 正常工作
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.rawX
                touchDownY = ev.rawY
                touchMode = TOUCH_MODE_UNDECIDED
                touchOverscroll = false
                touchOnChild = isTouchOnChild(ev)
                // 取消可能正在进行的弹簧回弹动画，重置位移
                springAnimator?.cancel()
                springAnimator = null
                card.translationY = 0f
                // 只在空白区域做按压缩放
                if (!touchOnChild) {
                    card.animate().cancel()
                    card.animate().scaleX(TOUCH_PRESS_SCALE).scaleY(TOUCH_PRESS_SCALE)
                        .setDuration(TOUCH_PRESS_DOWN_MS)
                        .setInterpolator(ControlAnimations.PRESS_DOWN_INTERPOLATOR)
                        .start()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - touchDownX
                val dy = ev.rawY - touchDownY
                val threshold = TOUCH_DIR_THRESHOLD_DP.dp.toFloat()

                if (touchMode == TOUCH_MODE_UNDECIDED
                    && (abs(dx) > threshold || abs(dy) > threshold)
                ) {
                    touchMode =
                        if (abs(dy) > abs(dx)) TOUCH_MODE_VERTICAL else TOUCH_MODE_HORIZONTAL
                    if (touchMode == TOUCH_MODE_HORIZONTAL) {
                        // 水平：恢复按压（如有），不拦截
                        if (!touchOnChild) {
                            animateCardPressUp()
                        }
                    }
                }

                if (touchMode == TOUCH_MODE_VERTICAL) {
                    if (!touchOverscroll) {
                        // 首次进入 overscroll：恢复按压（如有）
                        if (!touchOnChild) {
                            animateCardPressUp()
                        }
                        touchOverscroll = true

                        // 向子 View 发送 CANCEL，清除其 pressed 状态，
                        // 防止垂直拖动结束后松手仍误触发子 View 的 click 事件
                        val cancel = MotionEvent.obtain(ev).apply {
                            action = MotionEvent.ACTION_CANCEL
                        }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }
                    // 限制最大移动范围为 TOUCH_MAX_DP，避免超出 PopupWindow 窗口 Surface 被裁剪
                    val maxPx = TOUCH_MAX_DP.dp.toFloat()
                    // 统一线性阻尼：全程 dy * DAMPEN，平滑逼近上限
                    card.translationY = (dy * TOUCH_DAMPEN).coerceIn(-maxPx, maxPx)
                    return true // 拦截垂直拖动
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasOverscroll = touchOverscroll
                if (wasOverscroll && card.translationY != 0f) {
                    springAnimator = ValueAnimator.ofFloat(card.translationY, 0f).apply {
                        duration = TOUCH_SPRING_MS
                        interpolator = OvershootInterpolator(TOUCH_BOUNCE)
                        addUpdateListener { card.translationY = it.animatedValue as Float }
                        start()
                    }
                } else if (!wasOverscroll && !touchOnChild) {
                    // 空白区域按压恢复
                    animateCardPressUp()
                }
                touchOverscroll = false
                touchMode = TOUCH_MODE_UNDECIDED
                touchOnChild = false
                // 垂直拦截结束后不再派发 UP 给子 View（已在 CANCEL 时清理状态）
                if (wasOverscroll) return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** 卡片按压缩放恢复动画（仅缩放，无透明度变化）。 */
    private fun animateCardPressUp() {
        card.animate().cancel()
        card.animate().scaleX(1f).scaleY(1f)
            .setDuration(TOUCH_PRESS_UP_MS)
            .setInterpolator(ControlAnimations.PRESS_UP_INTERPOLATOR)
            .start()
    }

    /**
     * 判断触摸点是否落在 card 的可交互子控件上（按钮、SeekBar）。
     * ev.x/y 是相对 Panel 的坐标，减去 card 的 layout 位置即得到 card 本地坐标。
     */
    private fun isTouchOnChild(ev: MotionEvent): Boolean {
        val cx = (ev.x - card.left).toInt()
        val cy = (ev.y - card.top).toInt()
        return hasInteractiveDescendant(card, cx, cy)
    }

    private fun hasInteractiveDescendant(parent: ViewGroup, x: Int, y: Int): Boolean {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (x < child.left || x >= child.right || y < child.top || y >= child.bottom) continue
            if (child.isClickable || child is SeekBar) return true
            if (child is ViewGroup && hasInteractiveDescendant(
                    child,
                    x - child.left,
                    y - child.top
                )
            ) return true
        }
        return false
    }

    // -------------------------------------------------------------------------
    // 对外更新接口
    // -------------------------------------------------------------------------

    /**
     * 切换歌曲：刷新歌名/歌手/总时长与进度。
     *
     * @param systemMeta 系统媒体会话元数据，优先于 Song 的同名字段；
     *                   某个字段缺失时回退到 Song。
     */
    fun bindSong(
        song: Song?,
        position: Long,
        systemMeta: MediaTrackMeta.TrackMeta? = null
    ) {
        val title = systemMeta?.title ?: song?.name
        val artist = systemMeta?.artist ?: (song?.artist).orEmpty()
        val newDurationMs = systemMeta?.durationMs ?: song?.duration ?: 0L
        val titleText = title ?: TITLE_WHEN_NO_SONG

        // 仅真正切歌（标题或时长变化）时解除 seek 锁定；
        // 同歌元数据刷新不重置，避免打断锁定导致 seek 回跳
        if (titleText != lastBoundTitle || newDurationMs != songDurationMs) {
            seekTargetPosition = NO_SEEK_TARGET
        }
        lastBoundTitle = titleText
        songDurationMs = newDurationMs

        // 标题为自绘 View，其 text setter 内部已做"文本相同则不刷新"
        titleView.text = titleText

        setTextIfChanged(artistView, artist)
        setTextIfChanged(totalTimeView, formatTime(songDurationMs))

        updatePosition(position)
    }

    /** 刷新进度（拖动或刚体锁定期间忽略外部刷新）。 */
    fun updatePosition(position: Long) {
        if (isSeeking) return
        if (isSeekLocked(position)) return

        val targetProgress = positionToProgress(position)
        val currentProgress = seekBar.progress
        val progressDiff = abs(targetProgress - currentProgress)

        // 判断是否需要动画：
        // 1. 切歌重置到 0（当前进度 > 0 且目标进度 = 0）
        // 2. 进度跳跃太大（差值超过阈值）
        val shouldAnimate = (currentProgress > 0 && targetProgress == 0) ||
                progressDiff > SEEK_ANIMATION_THRESHOLD

        seekBar.setProgress(targetProgress, shouldAnimate)
        setTextIfChanged(currentTimeView, formatTime(position))
    }

    private fun setTextIfChanged(view: TextView, text: String) {
        if (view.text.toString() != text) view.text = text
    }

    /**
     * 是否仍处于 seek 刚体锁定：播放器上报位置接近目标（容差内）或锁定超时后解锁。
     * 容差覆盖 1s 粒度的位置上报；超时兜底防止 seek 无效时 UI 长期卡在目标位置。
     */
    private fun isSeekLocked(position: Long): Boolean {
        val target = seekTargetPosition
        if (target == NO_SEEK_TARGET) return false
        val reached = abs(position - target) <= SEEK_TARGET_TOLERANCE_MS
        val timedOut = SystemClock.uptimeMillis() >= seekLockExpireAt
        if (reached || timedOut) {
            seekTargetPosition = NO_SEEK_TARGET
            return false
        }
        return true
    }

    /** 刷新播放/暂停图标，并更新封面动画状态。 */
    fun setPlaying(playing: Boolean) {
        playButton.setImageDrawable(playPauseIcon(playing))

        // 暂停时封面微微缩小，播放时恢复
        if (playing) {
            coverView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            coverView.animate()
                .scaleX(PAUSE_COVER_SCALE)
                .scaleY(PAUSE_COVER_SCALE)
                .setDuration(200)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
    }

    /**
     * 设置封面图片，带 3D 翻转动画效果。
     *
     * - 首次设置封面：直接显示，不播放动画
     * - 封面未变化（文件最后修改时间相同）：直接显示，不播放动画
     * - 封面变化：播放 3D 翻转动画
     *
     * @param bitmap 封面图片
     * @param coverModified 封面文件最后修改时间，用于判断封面是否变化
     */
    fun setCover(bitmap: Bitmap, coverModified: Long = 0L) {
        if (isFlipping) return  // 防止动画重叠

        // 首次设置封面或封面未变化，直接显示，不播放动画
        if (isFirstCover || coverModified == lastCoverModified) {
            coverView.setImageBitmap(bitmap)
            isFirstCover = false
            lastCoverModified = coverModified
            return
        }

        // 封面变化，播放 3D 翻转动画
        isFlipping = true
        lastCoverModified = coverModified
        val duration = 300L

        // 启用硬件加速以获得更好的 3D 渲染性能
        coverView.setLayerType(LAYER_TYPE_HARDWARE, null)

        // 阶段1：绕 Y 轴旋转到 90°（旧封面消失）+ 缩小 + 淡出
        coverView.animate()
            .rotationY(90f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .alpha(0.5f)
            .setDuration(duration / 2)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                // 切换图片
                coverView.setImageBitmap(bitmap)

                // 从 -90° 开始旋转到 0°（新封面出现）
                coverView.rotationY = -90f
                coverView.animate()
                    .rotationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(duration / 2)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        isFlipping = false
                        // 关闭硬件加速以节省内存
                        coverView.setLayerType(LAYER_TYPE_NONE, null)
                    }
                    .start()
            }
            .start()
    }

    /**
     * 用封面主色刷新卡片背景叠加色。
     *
     * 若当前使用实时模糊背景，则将封面主色设置为叠加色（保持通透）；
     * 否则替换为新的纯色 GradientDrawable（降级路径）。
     */
    fun setBackdropColor(color: Int) {
        currentCoverColor = color  // 保存封面主色，用于重新打开时恢复
        val blur = blurBackground
        val alphaPercent = (OVERLAY_ALPHA * 100).toInt()
        YLog.debug(
            TAG,
            "setBackdropColor: blurBackground=${if (blur != null) "exists" else "null"}, color=#${
                Integer.toHexString(color)
            }"
        )
        if (blur != null) {
            // 实时模糊模式：使用封面主色 + 低 alpha 保持通透感
            val colorWithAlpha = applyOverlayAlpha(color, OVERLAY_ALPHA)
            YLog.debug(
                TAG,
                "setBackdropColor: blur overlay=#${Integer.toHexString(colorWithAlpha)} (alpha=${OVERLAY_ALPHA}f/$alphaPercent%, original=#${
                    Integer.toHexString(color)
                })"
            )
            ControlUi.updateBlurOverlayColor(blur, colorWithAlpha)
            // 强制刷新视图，确保 BlurDrawable 立即渲染
            card.invalidate()
            card.postInvalidateOnAnimation()
        } else {
            // 降级模式：使用封面色 + 深色混合，确保深色效果
            val fallbackColor = applyOverlayAlpha(color, OVERLAY_ALPHA)
            YLog.debug(
                TAG,
                "setBackdropColor: fallback solid=#${Integer.toHexString(fallbackColor)}"
            )
            card.background = ControlUi.roundedDrawable(fallbackColor, CARD_RADIUS_DP.dp.toFloat())
        }
    }

    /**
     * 为颜色应用叠加透明度，保持 RGB 不变。
     *
     * @param color 原始颜色（ARGB）
     * @param alpha 不透明度 (0.0~1.0)，如 0.31 表示 31% 不透明
     * @return 应用 alpha 后的颜色
     */
    private fun applyOverlayAlpha(color: Int, alpha: Float): Int {
        val alphaInt = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (alphaInt shl 24) or (color and 0x00FFFFFF)
    }

    /**
     * 长圆（胶囊）轨道 Drawable：底轨 + 已填充进度两层，两端为半圆。
     * 每层以固定高度、垂直居中放置，避免 Material 细线样式。
     */
    private fun longRoundedTrack(): Drawable {
        val h = LONG_ROUNDED_TRACK_HEIGHT_DP.dp
        val radius = h / 2f
        val track = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(SEEK_TRACK_COLOR)
            setSize(0, h)
        }
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(TEXT_COLOR_PRIMARY)
            setSize(0, h)
        }
        val clip = ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)
        val layers = LayerDrawable(arrayOf(track, clip))
        layers.setLayerGravity(0, Gravity.CENTER_VERTICAL)
        layers.setLayerGravity(1, Gravity.CENTER_VERTICAL)
        return layers
    }

    // -------------------------------------------------------------------------
    // 布局组装
    // -------------------------------------------------------------------------

    /**
     * 歌曲信息行：封面 | 歌名/歌手 | 进度条 | 时间，全在同一水平线。
     */
    private fun buildInfoRow() {
        val titleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(TITLE_COLUMN_LEFT_PADDING_DP.dp, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
            // clipChildren = false
            clipToPadding = false
        }
        titleColumn.addView(titleView)
        titleColumn.addView(artistView)

        titleColumn.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = SEEK_BAR_TOP_MARGIN_DP.dp }
        )

        val timeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, TIME_ROW_TOP_PADDING_DP.dp, 0, 0)
        }
        timeRow.addView(currentTimeView)
        timeRow.addView(totalTimeView)
        titleColumn.addView(timeRow)

        val infoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, INFO_ROW_BOTTOM_PADDING_DP.dp)
        }
        infoRow.addView(coverView)
        infoRow.addView(titleColumn)
        card.addView(infoRow)
    }

    /**
     * 操作按钮行：左边占位、上一首、播放/暂停、下一首均分宽度，
     * 右下圆形 AI 图标独立最右，与参考图布局一致。
     */
    private fun buildActionRow() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, ACTION_ROW_TOP_PADDING_DP.dp, 0, 0)
        }
        addActionSlot(row, View(context)) // 左侧占位
        addActionSlot(row, previousButton)
        addActionSlot(row, playButton)
        addActionSlot(row, nextButton)
        addActionSlot(row, aiButton, 26.dp)

        card.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    /** 把动作按钮包进等宽的居中槽位（按钮固定 40dp）。 */
    private fun addActionSlot(
        row: LinearLayout,
        button: View,
        size: Int = ACTION_BUTTON_SIZE_DP.dp
    ) {
        val slot = FrameLayout(context).apply {
            addView(
                button,
                LayoutParams(
                    size, size, Gravity.CENTER
                )
            )
        }
        row.addView(
            slot,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                weight = 1f
            }
        )
    }

    // -------------------------------------------------------------------------
    // 图标 / 进度与时间换算
    // -------------------------------------------------------------------------

    private fun playPauseIcon(playing: Boolean): Drawable {
        val iconId = if (playing) drawableId("sui_control_pause_fill1_24px")
        else drawableId("sui_control_play_arrow_fill1_24px")
        return moduleRes.getDrawable(iconId, context.theme)
    }

    private fun positionToProgress(position: Long): Int {
        if (songDurationMs <= 0) return 0
        return ((position * SEEK_MAX) / songDurationMs).toInt().coerceIn(0, SEEK_MAX)
    }

    private fun progressToPosition(progress: Int): Long = songDurationMs * progress / SEEK_MAX

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%d:%02d".format(minutes, seconds)
    }

    // -------------------------------------------------------------------------
    // 常量
    // -------------------------------------------------------------------------

    private companion object {
        private const val TAG = "LyricControlPanel"

        const val CARD_RADIUS_DP = 28
        const val COVER_RADIUS_DP = 16
        const val COVER_SIZE_DP = 72
        const val CARD_PADDING_DP = 16

        /** 背景模糊半径（像素），600 为最强磨砂效果。 */
        const val BLUR_RADIUS_PX = 600

        /** 模糊叠加色的不透明度 (0.0~1.0)，0.8 表示 80% 不透明，纯封面主色 */
        const val OVERLAY_ALPHA = 0.8f

        const val INFO_ROW_BOTTOM_PADDING_DP = 10
        const val TITLE_COLUMN_LEFT_PADDING_DP = 10
        const val SEEK_BAR_TOP_MARGIN_DP = 5
        const val TIME_ROW_TOP_PADDING_DP = 5
        const val ACTION_ROW_TOP_PADDING_DP = 0
        const val ACTION_BUTTON_SIZE_DP = 36
        const val SEEK_MAX = 1000

        /** 进度动画阈值：进度差超过此值时使用动画过渡（约 5% 进度） */
        const val SEEK_ANIMATION_THRESHOLD = 50

        /** 暂停时封面缩放比例（微微缩小） */
        const val PAUSE_COVER_SCALE = 0.92f

        /** 长圆（胶囊）进度轨道高度（dp）。 */
        const val LONG_ROUNDED_TRACK_HEIGHT_DP = 5

        /** 无 seek 锁定目标。 */
        const val NO_SEEK_TARGET = Long.MIN_VALUE

        /** 刚体锁定最长持续时间（毫秒）。 */
        const val SEEK_LOCK_DURATION_MS = 1200L

        /** 判定"已到达 seek 目标"的容差（毫秒，覆盖 1s 位置上报粒度）。 */
        const val SEEK_TARGET_TOLERANCE_MS = 2000L

        const val TITLE_TEXT_SIZE_SP = 17f
        const val ARTIST_TEXT_SIZE_SP = 14f
        const val TIME_TEXT_SIZE_SP = 11f

        const val TITLE_WHEN_NO_SONG = "未在播放"

        /** 卡片背景色 RGB：深蓝灰色 (28, 32, 46)，不透明度由 OVERLAY_ALPHA 控制 */
        val CARD_COLOR = Color.argb(OVERLAY_ALPHA, 28f, 32f, 46f)
        val COVER_PLACEHOLDER_COLOR = Color.argb(70, 255, 255, 255)
        const val TEXT_COLOR_PRIMARY = Color.WHITE
        val TEXT_COLOR_SECONDARY = Color.argb(170, 255, 255, 255)
        val SEEK_TRACK_COLOR = Color.argb(60, 255, 255, 255)

        // ---- 卡片触摸效果 ----
        const val TOUCH_PRESS_SCALE = 0.97f
        const val TOUCH_PRESS_DOWN_MS = 200L
        const val TOUCH_PRESS_UP_MS = 200L
        const val TOUCH_DIR_THRESHOLD_DP = 8
        const val TOUCH_MAX_DP = 30
        const val TOUCH_DAMPEN = 0.25f
        const val TOUCH_SPRING_MS = 400L
        const val TOUCH_BOUNCE = 0.35f
        const val TOUCH_MODE_UNDECIDED = 0
        const val TOUCH_MODE_HORIZONTAL = 1
        const val TOUCH_MODE_VERTICAL = 2
    }
}
