/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.control

import android.annotation.SuppressLint
import android.media.MediaMetadata
import android.media.session.MediaController
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import io.github.proify.android.extensions.dp
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.ai.explain.AiExplainLauncher
import io.github.proify.lyricon.xposed.systemui.lyric.LyricDataHub
import io.github.proify.lyricon.xposed.systemui.lyric.LyricViewController
import io.github.proify.lyricon.xposed.systemui.lyric.PlaybackControl
import io.github.proify.lyricon.xposed.systemui.util.MediaTrackMeta
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.systemui.util.SystemUIMediaUtils
import java.io.File

/**
 * 状态栏歌词控制窗口 (Status Bar Lyric Control Popup)
 *
 * 当用户点击状态栏歌词时弹出，提供接近系统播放器控制面板的体验：
 * - 专辑封面、歌名、歌手
 * - 可拖动的播放进度条 + 当前/总时长
 * - 上一首 / 播放暂停 / 下一首
 * - AI 解释歌词：把当前歌词发送给 OpenAI 兼容服务并展示解读
 *
 * 设计取向：类似 iOS 控制中心 / 系统媒体控件的质感——
 * 圆角卡片、层次阴影、半透明磨砂背景、温柔的进出场动画。
 *
 * 由于运行在 SystemUI 系统进程内，视图全部以代码方式构建，不依赖 App 资源。
 *
 * @author Tomakino
 * @since 2026
 */
@SuppressLint("StaticFieldLeak")
@Suppress("unused")
object LyricControlPopup : ActivePlayerListener, NotificationCoverHelper.OnCoverUpdateListener,
    NotificationCoverHelper.OnCoverColorListener {

    private const val TAG = "LyricControlPopup"
    private const val UPDATE_INTERVAL_MS = 200L

    private const val SCREEN_MARGIN_DP = 16
    private const val SCREEN_VERTICAL_GAP_DP = 4
    private const val MIN_WIDTH_DP = 280
    private const val MAX_WIDTH_DP = 480
    private const val ELEVATION_DP = 28

    /** 拖动：自由移动区间（相对锚点），超出后施加阻尼。 */
    private const val DRAG_FREE_UP_DP = 120
    private const val DRAG_FREE_DOWN_DP = 220

    /** 超出自由区间后的阻尼系数（0~1，越小越"拽不动"，iOS 橡皮筋手感）。 */
    private const val DRAG_DAMPEN = 0.35f

    /** 松手回弹时长（ms）与过冲量（0~1）。 */
    private const val DRAG_SPRING_MS = 320L
    private const val DRAG_BOUNCE = 0.45f

    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- 当前弹窗状态（主线程访问） ----
    private var popup: PopupWindow? = null
    private var panel: LyricControlPanel? = null
    private var anchorView: View? = null

    /** 退场动画进行中：防止退出动画期间再次点击歌词创建新的弹窗（重复显示）。 */
    private var dismissing = false

    /**
     * 面板交互回调：转发到系统媒体控制器；AI 按钮交给 [AiExplainLauncher]。
     */
    private val actionListener = object : LyricControlPanel.ActionListener {
        override fun onPrevious() {
            PlaybackControl.previous()
        }

        override fun onTogglePlay() {
            PlaybackControl.togglePlay()
        }

        override fun onNext() {
            PlaybackControl.next()
        }

        override fun onSeekTo(position: Long) {
            transportControls()?.seekTo(position)
        }

        override fun onAiExplain(button: View) {
            val anchor = anchorView ?: return
            AiExplainLauncher.launch(button.context, LyricViewController.currentSong?.deepCopy())
            dismiss()
        }

        override fun onOpenApp() {

            val pkg = LyricViewController.activePackage
            if (pkg.isBlank()) return
            val anchor = anchorView ?: return

            try {
                val intent = anchor.context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    anchor.context.startActivity(intent)
                    dismiss()
                } else {
                    YLog.warning(TAG, "No launch intent for package: $pkg")
                }
            } catch (e: Exception) {
                YLog.error(TAG, "Failed to launch app: $pkg", e)
            }
        }
    }

    /**
     * 系统媒体元数据回调：元数据更新（切歌、标题/歌手/时长变化）时立即刷新面板。
     */
    private val mediaCallback = object : SystemUIMediaUtils.MediaControllerCallback {
        override fun onMediaChanged(controller: MediaController, metadata: MediaMetadata) {
            if (controller.packageName != LyricViewController.activePackage) return
            runOnMainThread { refreshPanel() }
        }
    }

    /**
     * 高频进度刷新任务：[LyricViewController] 持续写入逻辑进度，
     * 这里以固定间隔采样并刷新面板；拖动进度条时由面板自行抑制。
     */
    private val positionUpdater = object : Runnable {
        override fun run() {
            val showingPopup = popup ?: return
            if (!showingPopup.isShowing) return
            panel?.updatePosition(LyricViewController.currentLogicPosition)
            mainHandler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    // -------------------------------------------------------------------------
    // ActivePlayerListener：面板显示期间跟随歌曲/播放状态实时刷新
    // -------------------------------------------------------------------------

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        if (popup?.isShowing != true) return
        runOnMainThread { refreshPanel() }
    }

    override fun onSongChanged(song: Song?) {
        if (popup?.isShowing != true) return
        runOnMainThread {
            refreshPanel()
            refreshCover()
        }
    }

    override fun onReceiveText(text: String?) = Unit

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (popup?.isShowing != true) return
        runOnMainThread { panel?.setPlaying(isPlaying) }
    }

    override fun onPositionChanged(position: Long) = Unit

    override fun onSeekTo(position: Long) = Unit

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit

    // -------------------------------------------------------------------------
    // NotificationCoverHelper.OnCoverUpdateListener：封面写盘完成后刷新面板
    // -------------------------------------------------------------------------

    /**
     * 封面文件写入完成后回调（主线程）。此时 cover.png 已是新歌封面，
     * 直接刷新可避免与写盘时序错位导致"切歌后封面不刷新"。
     */
    override fun onCoverUpdated(packageName: String, coverFile: File) {
        if (packageName != LyricViewController.activePackage) return
        runOnMainThread { refreshCover() }
    }

    // -------------------------------------------------------------------------
    // NotificationCoverHelper.OnCoverColorListener：封面颜色提取后立即回调
    // -------------------------------------------------------------------------

    /**
     * 封面颜色提取后立即回调（主线程），无需等待文件写入完成。
     * 用于快速更新卡片背景色，避免切歌时颜色更新延迟。
     */
    override fun onCoverColorChanged(packageName: String, color: Int) {
        if (packageName != LyricViewController.activePackage) return
        val startTime = System.nanoTime()
        runOnMainThread {
            panel?.setBackdropColor(color)
            val elapsed = (System.nanoTime() - startTime) / 1_000_000.0
            YLog.debug(TAG, "onCoverColorChanged: color=#${Integer.toHexString(color)}, setBackdropColor=${elapsed}ms")
        }
    }

    // -------------------------------------------------------------------------
    // 公开 API
    // -------------------------------------------------------------------------

    /**
     * 在 [anchorView] 附近显示控制窗口（若已在显示则切换为关闭）。
     * 由状态栏歌词的手势动作触发（默认单击 = ACTION_OPEN_CONTROL）。
     */
    @SuppressLint("ClickableViewAccessibility")
    @JvmStatic
    fun show(anchorView: View) {
        // 正在显示或退场动画中：视为"已显示"，再次点击 = 关闭（toggle），
        // 避免退场 200ms 内重复创建弹窗
        if (popup?.isShowing == true || dismissing) {
            dismiss()
            return
        }
        this.anchorView = anchorView
        val context = anchorView.context

        // iPad 式卡片宽度：贴近屏幕宽度，留少量边距
        val dm = context.resources.displayMetrics
        val screenWidth = dm.widthPixels
        val margin = SCREEN_MARGIN_DP.dp
        val popupWidth = (screenWidth - margin * 2).coerceIn(MIN_WIDTH_DP.dp, MAX_WIDTH_DP.dp)

        val panel = LyricControlPanel(context).apply {
            actionListener = this@LyricControlPopup.actionListener
            setPlaying(LyricViewController.isPlaying)
        }
        this.panel = panel
        refreshPanel()
        refreshCover()

        val window = PopupWindow(
            panel,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            // 非焦点、非模态：卡片外的触摸/滑动直达 SystemUI 下层，
            // 不拦截通知栏下拉、边缘手势等系统操作；
            // 同时保留 outsideTouchable：外部触摸的观察事件（坐标在窗口外的 DOWN）
            // 照常送达以触发点击关闭，两者互不冲突
            isFocusable = false
            isTouchModal = false
            isOutsideTouchable = true
            elevation = ELEVATION_DP.dp.toFloat()
            // 设置半透明背景，让模糊效果可见（BackgroundBlurDrawable 需要窗口半透明）
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            // 禁用裁剪：允许卡片 overscroll 时 translationY 超出窗口边界
            isClippingEnabled = false
            // 外部触摸：先播放收走动画再关窗；返回 true 拦截默认的瞬间擦除
            setTouchInterceptor { view, event ->
                val outside =
                    event.action == MotionEvent.ACTION_DOWN &&
                            (event.x < 0f || event.x >= view.width ||
                                    event.y < 0f || event.y >= view.height)
                if (outside || event.action == MotionEvent.ACTION_OUTSIDE) {
                    // 注意：此处处于 PopupWindow.apply {} 内，裸调用 dismiss()
                    // 会解析为 PopupWindow.dismiss()（瞬间擦除）；
                    // 必须显式指向本对象的动画关闭实现
                    this@LyricControlPopup.dismiss()
                    true
                } else {
                    false
                }
            }
            setOnDismissListener { cleanup() }
        }
        popup = window

        // 位置：卡片紧贴状态栏下沿，横向居中（灵动岛从顶部滑出的起点）
        val x = (screenWidth - popupWidth) / 2
        val y = (anchorView.bottom + SCREEN_VERTICAL_GAP_DP.dp).coerceAtLeast(0)
        try {
            window.showAtLocation(anchorView, Gravity.TOP or Gravity.START, x, y)
            // 遍历 PopupWindow 的所有祖先视图，逐层禁用裁剪，
            // 确保卡片 overscroll translationY 不会被任何中间层截断
            var ancestor: ViewGroup? = window.contentView.parent as? ViewGroup
            while (ancestor != null) {
                ancestor.clipChildren = false
                ancestor.clipToPadding = false
                ancestor = ancestor.parent as? ViewGroup
            }
            ControlAnimations.playEnter(panel)
            // 监听歌曲/进度/系统媒体元数据/封面写盘完成，刷新面板
            LyricDataHub.addListener(this)
            SystemUIMediaUtils.registerListener(mediaCallback)
            NotificationCoverHelper.registerListener(this)
            NotificationCoverHelper.registerColorListener(this)  // 注册颜色监听，快速更新背景色
            mainHandler.removeCallbacks(positionUpdater)
            mainHandler.post(positionUpdater)
        } catch (e: Exception) {
            YLog.error(TAG, "Failed to show control popup", e)
        }
    }

    /** 关闭控制窗口并注销监听（AI 解读界面由 App 进程管理，无需在此处理）。 */
    @JvmStatic
    fun dismiss() {
        if (dismissing) return
        dismissing = true

        LyricDataHub.removeListener(this)
        SystemUIMediaUtils.unregisterListener(mediaCallback)
        NotificationCoverHelper.unregisterListener(this)

        val window = popup ?: run {
            dismissing = false
            return
        }
        val root = panel
        popup = null
        panel = null
        mainHandler.removeCallbacks(positionUpdater)
        if (window.isShowing) {
            if (root != null) {
                ControlAnimations.playExit(
                    view = root,
                    onEnd = { window.dismiss() },
                    onUpdateAlpha = { alpha ->
                        // 同步更新模糊背景的透明度 (0.0~1.0 → 0~255)
                        root.setBlurAlpha((alpha * 255).toInt())
                    }
                )
            } else {
                window.dismiss()
            }
        } else {
            dismissing = false
        }
    }

    /** 当前是否有控制窗口正在显示。 */
    @JvmStatic
    val isShowing: Boolean get() = popup?.isShowing == true

    /**
     * 仅当控制窗口仍以 [owner] 为锚点时关闭。用于视图销毁时安全回收，避免误关其它控制窗口。
     */
    @JvmStatic
    fun dismissIfOwnedBy(owner: View) {
        if (anchorView === owner) dismiss()
    }

    // -------------------------------------------------------------------------
    // 封面 / 媒体控制 / 线程工具
    // -------------------------------------------------------------------------

    /**
     * 刷新面板：曲目元数据优先取自系统媒体会话（[MediaTrackMeta]），
     * 缺失字段回退到 [LyricViewController.currentSong]，歌词仍以 Song 为准。
     */
    private fun refreshPanel() {
        val panel = panel ?: return
        panel.bindSong(
            song = LyricViewController.currentSong,
            position = LyricViewController.currentLogicPosition,
            systemMeta = MediaTrackMeta.resolve(LyricViewController.activePackage)
        )
    }

    /** 从当前活跃播放器的封面缓存加载图片，并刷新面板封面与背景取色。 */
    private fun refreshCover() {
        val pkg = LyricViewController.activePackage
        if (pkg.isBlank()) return
        val file = NotificationCoverHelper.getLatestCoverFile(pkg) ?: return
        if (!file.exists()) return
        try {
            val bitmap = CoverBackdrop.decode(file) ?: return
            panel?.apply {
                setCover(bitmap, file.lastModified())
                setBackdropColor(CoverBackdrop.backdropColor(bitmap))
            }
        } catch (e: Exception) {
            YLog.error(TAG, "Failed to load cover for $pkg", e)
        }
    }

    private fun transportControls(): MediaController.TransportControls? {
        return try {
            SystemUIMediaUtils.getController(LyricViewController.activePackage)
                ?.transportControls
        } catch (e: Exception) {
            YLog.error(TAG, "Failed to obtain transport controls", e)
            null
        }
    }

    /** 若主线程则直接执行，否则邮寄到主线程。 */
    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }

    /** 弹窗被系统/外部关闭后的资源回收。 */
    private fun cleanup() {
        mainHandler.removeCallbacks(positionUpdater)
        SystemUIMediaUtils.unregisterListener(mediaCallback)
        NotificationCoverHelper.unregisterListener(this)
        NotificationCoverHelper.unregisterColorListener(this)  // 取消注册颜色监听
        dismissing = false
        popup = null
        panel = null
        anchorView = null
    }
}
