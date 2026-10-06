/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.hook

import android.annotation.SuppressLint
import android.graphics.Point
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.lyric.LyricPrefs
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 根窗口触摸拦截器 (StatusBar Touch Hooker)
 *
 * ## 背景
 *
 * ColorOS 16 在状态栏顶层挂载了流体云胶囊根容器（`CapsuleContainerRoot`），
 * 该容器铺满状态栏并绘制在系统状态栏布局之上，自身消费掉落在其区域内的一切触摸，
 * 导致注入在状态栏子视图上的歌词控件
 * （[io.github.proify.lyricon.statusbarlyric.StatusBarLyric]）在**存在流体云通知**时
 * （展开或收缩均如此）收不到触摸，单击与滑动切歌全部失效。
 *
 * ## 策略
 *
 * 挂钩状态栏根窗口的 `dispatchTouchEvent(MotionEvent)`。它是整个状态栏窗口
 * 触摸派发的唯一入口，位于胶囊容器之上，因此可以：
 *
 * 1. 在事件进入下层视图树（胶囊容器）之前完成命中判定；
 * 2. 命中歌词区域时由本拦截器接管本次手势，直接分发给歌词控件，并取消胶囊链路；
 * 3. 未命中、或识别为竖向位移手势时原样放行，保证通知栏下拉与胶囊自身交互不变。
 *
 * ## 事件编排
 *
 * ```
 * ACTION_DOWN  命中歌词区域 → 开启会话，DOWN 只给歌词（按压反馈），系统链路被截断
 * ACTION_MOVE  |ΔY| 达标且方向偏竖向 → 让渡给系统：取消歌词 → 重放 DOWN → 放行本次 MOVE
 *              |ΔX| 达标且方向偏横向 → 保留给歌词：取消系统链路，此后只给歌词
 *              两者都不满足 → 吞掉该事件（不让胶囊提前看到位移），继续喂给歌词
 * ACTION_UP    按本次判定收尾（歌词得到 TAP / SWIPE，系统得到补发的 CANCEL）
 * ```
 *
 * 接管期间本拦截器自行完成全部派发：歌词与胶囊绝不会同时收到同一串事件，
 * 因此不会出现"单击被歌词与胶囊各响应一次"的双触发。
 *
 * ## 实现要点
 *
 * - **类名**：Android 14 起状态栏窗口位于 `statusbar.window` 包，
 *   旧版 AOSP 在 `statusbar.phone`，因此按候选包名逐个尝试。
 * - **命中判定**：只依据歌词视图自身的实时屏幕坐标做几何判定，
 *   **不要求**歌词与被 hook 的根窗口处于同一棵视图树——ColorOS 16 下二者
 *   确实不在同一个 window，但它们共享屏幕坐标空间。
 * - **重入保护**：放行/重放/补发 CANCEL 都通过主动调用 `dispatchTouchEvent` 完成，
 *   该调用会再次进入本 Hook，必须直接透传，否则会无限递归并导致 SystemUI 栈溢出。
 *
 * ## 边界
 *
 * - **多窗口 / 分屏**：命中测试每次 DOWN 重新采样歌词实时屏幕坐标，不缓存。
 * - **横屏 / 旋转 / 挖孔**：区域来自实时 bounds，随布局自动刷新。
 * - **悬浮窗**：属于另一个 Window，不会进入本窗口派发，天然隔离。
 * - **胶囊动态展开 / 收起**：不依赖胶囊状态，只以歌词实际 bounds 判定。
 * - **性能**：每次触摸仅做常数级判定，不遍历视图树、不使用反射；
 *   全部回调都在主线程，无额外线程与同步开销。
 *
 * @author Tomakino
 * @since 2026
 */
object StatusBarTouchHooker {

    private const val TAG = "StatusBarTouchHooker"

    /** 状态栏根窗口类名（用于识别宿主实现，Oplus 版本为其子类） */
    private const val STATUS_BAR_WINDOW_VIEW = "StatusBarWindowView"

    /**
     * 状态栏根窗口的候选类名。
     *
     * 必须是精确类名：`StatusBarWindowView` 只存在于 SystemUI 自身，
     * 模糊匹配会误伤同名资源或其他模块的类。
     */
    private val CANDIDATE_CLASS_NAMES = listOf(
        // Android 14+ / ColorOS 16：状态栏窗口被移入 window 子包
        "com.android.systemui.statusbar.window.StatusBarWindowView",
        // 经典 AOSP 及多数 ROM
        "com.android.systemui.statusbar.phone.StatusBarWindowView",
    )

    /** 手势停滞超时（ms）：超过则视为漏发 UP/CANCEL，强制取消本次手势 */
    private const val SESSION_TIMEOUT_MS = 10_000L

    /** 命中测试向外扩张的容差（px），覆盖圆角与边缘按压反馈 */
    private const val HIT_TOLERANCE_PX = 8

    /** 重入深度的保护上限，超出说明存在非预期递归 */
    private const val MAX_REDISPATCH_DEPTH = 32

    /** 每个被 hook 窗口的触摸参数缓存，避免每次触摸都走一遍 ViewConfiguration */
    @Volatile
    private var touchSlop: Int = 0

    private var unhookHandle: XposedInterface.HookHandle? = null

    /** 已注册的手势目标（通常为 [io.github.proify.lyricon.xposed.systemui.lyric.StatusBarViewController]） */
    private val targets = CopyOnWriteArraySet<Target>()

    /** 当前正在被本拦截器接管的手势 */
    private var activeGesture: GestureSession? = null

    /**
     * 主动派发（放行回系统）的重入深度。
     *
     * 触摸派发全程在 SystemUI 主线程，用普通 Int 即可；
     * 大于 0 表示当前正处于拦截器自己发起的 `dispatchTouchEvent` 调用中，
     * 必须直接透传，否则会无限递归。
     */
    private var redispatchDepth: Int = 0

    /* ------------------------------------------------------------------ */
    /* 对外契约                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 手势目标：由状态栏歌词控制器实现，向拦截器暴露命中区域与手势入口。
     */
    interface Target {

        /**
         * 本次触摸是否允许歌词接管。
         *
         * 手势开关关闭时返回 false，所有触摸原样走系统链路。
         */
        val gestureEnabled: Boolean

        /**
         * 命中测试。触摸点落在歌词区域内时返回命中点，否则返回 null
         * （含歌词不可见、未附着、被隐藏等场景）。
         *
         * @param event 当前处于派发中的触摸事件（坐标为事件坐标系）
         * @param hitTolerance 允许向外扩张的容差（px）
         */
        fun touchPoint(event: MotionEvent, hitTolerance: Int): Point?

        /**
         * 把事件交给歌词控件处理（实现内部需按视图坐标修正 `x/y`）。
         *
         * @return 歌词控件是否消费了该事件
         */
        fun dispatchTouchToLyric(event: MotionEvent): Boolean
    }

    /** 命中测试结果：事件坐标系下的命中点与到目标视图的坐标偏移 */
    private class HitArea(
        val hitX: Float,
        val hitY: Float,
        /** 事件坐标 → 目标视图坐标的横向偏移 */
        val offsetX: Int,
        /** 事件坐标 → 目标视图坐标的纵向偏移 */
        val offsetY: Int
    )

    /** 一次手势的判定结果 */
    private enum class Sender {
        /** 尚未判定：歌词与系统同时处于候选状态 */
        NONE,

        /** 已判定给歌词：胶囊链路已取消，后续事件只给歌词 */
        LYRIC,

        /** 已判定给系统：歌词已取消，后续事件全部放行 */
        SYSTEM
    }

    /** 一次手势的会话状态 */
    private class GestureSession(
        val target: Target,
        val area: HitArea,
        val downEvent: MotionEvent,
        val downScreenX: Float,
        val downScreenY: Float,
        val downTime: Long
    ) {
        var sender: Sender = Sender.NONE
        var lastEventTime: Long = downTime
    }

    /* ------------------------------------------------------------------ */
    /* 安装与目标注册                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * 安装根窗口触摸 Hook。
     *
     * 可在 SystemUI 初始化阶段直接调用：`StatusBarWindowView` 必然早于任何歌词
     * 视图存在，因此此刻还没有手势目标也能安全挂钩；目标集合为空时
     * [intercept] 只做一次空判断便原样放行。
     *
     * @param module Xposed 模块实例
     * @param classLoader SystemUI 的 ClassLoader
     */
    @SuppressLint("PrivateApi")
    fun initialize(module: XposedModule, classLoader: ClassLoader) {
        if (unhookHandle != null) {
            YLog.info(TAG, "Already hooked, skip")
            return
        }

        var lastError: Throwable? = null
        for (className in CANDIDATE_CLASS_NAMES) {
            try {
                val windowViewClass = Class.forName(className, false, classLoader)
                val method = findDispatchTouchEvent(windowViewClass)
                    ?: throw NoSuchMethodException("$className 未定义 dispatchTouchEvent")
                method.isAccessible = true

                @Suppress("ObjectLiteralToLambda")
                unhookHandle = module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val view = chain.thisObject as? ViewGroup ?: return chain.proceed()
                        return handleDispatchTouchEvent(chain, view)
                    }
                })
                YLog.info(TAG, "Hooked ${windowViewClass.name}.dispatchTouchEvent")
                return
            } catch (t: Throwable) {
                lastError = t
                YLog.info(TAG, "Candidate $className unavailable: ${t.message}")
            }
        }

        YLog.error(TAG, "Failed to hook StatusBarWindowView.dispatchTouchEvent", lastError)
    }

    /**
     * 沿继承链查找 `dispatchTouchEvent`。
     *
     * ROM 可能只在某个中间父类里覆写它，因此不能只查声明类。
     */
    private fun findDispatchTouchEvent(clazz: Class<*>): java.lang.reflect.Method? {
        var current: Class<*>? = clazz
        while (current != null && current != View::class.java) {
            current.declaredMethods.firstOrNull {
                it.name == "dispatchTouchEvent" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == MotionEvent::class.java
            }?.let { return it }
            current = current.superclass
        }
        return null
    }

    /** 卸载 Hook（调试 / 重装用） */
    fun unhook() {
        unhookHandle?.unhook()
        unhookHandle = null
    }

    /** 注册手势目标 */
    fun registerTarget(target: Target) {
        if (targets.add(target)) {
            YLog.info(TAG, "Target registered: ${target.javaClass.name}, size=${targets.size}")
        }
    }

    /** 注销手势目标，并结束它正在进行的会话 */
    fun unregisterTarget(target: Target) {
        targets.remove(target)
        if (activeGesture?.target === target) {
            endSession(deliverCancel = true)
        }
        YLog.info(TAG, "Target unregistered: ${target.javaClass.name}, size=${targets.size}")
    }

    /* ------------------------------------------------------------------ */
    /* Hook 入口                                                           */
    /* ------------------------------------------------------------------ */

    @SuppressLint("DiscouragedApi")
    private fun handleDispatchTouchEvent(
        chain: XposedInterface.Chain,
        view: ViewGroup
    ): Any? {
        // 放行 / 重放 / 补发 CANCEL 都是主动调用 dispatchTouchEvent 完成的，
        // 该调用会再次进入本 Hook。若不拦截就形成无限递归，
        // 表现为 SystemUI 栈溢出崩溃（SIGSEGV: stack pointer is not in a rw map）。
        if (redispatchDepth > 0) {
            if (redispatchDepth++ > MAX_REDISPATCH_DEPTH) {
                throw IllegalStateException("dispatchTouchEvent re-entered too many times")
            }
            try {
                return view.dispatchTouchEvent(chain.args[0] as MotionEvent)
            } finally {
                redispatchDepth--
            }
        }

        try {
            val className = view.javaClass.simpleName
            if (className != STATUS_BAR_WINDOW_VIEW) {
                // Oplus 的宿主实现会继承该窗口类，其余未知变体一律不参与接管
                if (!view.javaClass.name.contains(STATUS_BAR_WINDOW_VIEW)) {
                    return chain.proceed()
                }
            }

            val event = chain.args.getOrNull(0) as? MotionEvent ?: return chain.proceed()
            return if (intercept(view, event)) true else chain.proceed()
        } catch (t: Throwable) {
            // 任何异常都不能让状态栏失去触摸响应
            YLog.error(TAG, "Intercept failed, fallback to system", t)
            return chain.proceed()
        }
    }

    /* ------------------------------------------------------------------ */
    /* 事件编排                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 事件编排主流程。
     *
     * 接管期间本方法自行完成全部派发（歌词与系统都只经由这里收到事件），
     * 从而保证同一次触摸绝不会被歌词与胶囊同时消费两次。
     *
     * @return true 表示事件已被本拦截器处理，不再走系统派发；false 表示继续 `chain.proceed()`
     */
    private fun intercept(view: ViewGroup, event: MotionEvent): Boolean {
        ensureTouchConfig(view)

        val session = activeGesture
        if (session != null && !isSessionAlive(session)) {
            // 上一次手势未正常收尾（漏发 UP/CANCEL），先清理再重新开始
            endSession(deliverCancel = true)
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(view, event)
            MotionEvent.ACTION_MOVE -> onMove(view, event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onRelease(view, event)
            else -> onOtherAction(view, event)
        }
    }

    /**
     * DOWN：唯一的接管决策点。
     *
     * 命中歌词区域时由本拦截器接管：先把 DOWN 交给歌词（按压反馈），
     * 同时把落点登记为会话起点，随后 MOVE 阶段依据位移方向收敛归属。
     */
    private fun onDown(view: ViewGroup, event: MotionEvent): Boolean {
        // 总开关关闭时完全不接管：歌词手势退回系统正常派发路径
        if (!LyricPrefs.rootTouchHookEnabled) return false

        // 正常情况下不会有未收尾的会话（上次 UP/CANCEL 已清空），
        // 若上一串手势被框架掐断则会话仍在，这里统一收口，保证每次 DOWN 都是全新状态。
        endSession(deliverCancel = true)

        val session = openSession(event) ?: return false

        // 歌词控件未消费（例如已 GONE / 被系统取消）时本窗口不做任何干预
        return toLyric(session, event)
    }

    /**
     * MOVE：手势让渡判定。
     *
     * 判定前吞掉事件（不让胶囊看到位移），判定后只走被选中一方。
     */
    private fun onMove(view: ViewGroup, event: MotionEvent): Boolean {
        val session = activeGesture ?: return false
        val now = SystemClock.uptimeMillis()
        if (now - session.lastEventTime > SESSION_TIMEOUT_MS) {
            YLog.warning(TAG, "Gesture session timeout, discard")
            endSession(deliverCancel = true)
            return false
        }
        session.lastEventTime = now

        return when (session.sender) {
            Sender.LYRIC -> toLyric(session, event)

            Sender.SYSTEM -> {
                dispatchToSystem(view, event)
                true
            }

            Sender.NONE -> {
                val dx = event.rawX - session.downScreenX
                val dy = event.rawY - session.downScreenY

                when {
                    shouldYieldToSystem(session, dx, dy) -> {
                        yieldToSystem(view, event, session, dx, dy)
                        true
                    }

                    shouldKeepToLyric(session, dx, dy) -> {
                        keepToLyric(view, event, session, dx, dy)
                        true
                    }

                    else -> {
                        // 尚未收敛：继续把位移喂给歌词做按压反馈，但不放给胶囊
                        toLyric(session, event)
                    }
                }
            }
        }
    }

    /**
     * UP / CANCEL：按本次判定结果收尾。
     *
     * 判定给系统时歌词已在 [yieldToSystem] 收到 CANCEL，这里只负责放行收尾事件；
     * 其余情况把最终事件交给歌词（触发 TAP / SWIPE），系统收到一个 CANCEL。
     */
    private fun onRelease(view: ViewGroup, event: MotionEvent): Boolean {
        val session = activeGesture ?: return false
        try {
            return if (session.sender == Sender.SYSTEM) {
                dispatchToSystem(view, event)
                true
            } else {
                dispatchCancelToSystem(view, session.area)
                toLyric(session, event)
            }
        } finally {
            endSession(deliverCancel = false)
        }
    }

    /**
     * 多指 / 其他动作：歌词手势仅支持单指，且系统侧从未收到过 DOWN，
     * 因此无法把序列转交出去。此时收敛为"本次手势作废"：
     * 双方各收到 CANCEL 清理状态，随后交还框架常规派发。
     */
    private fun onOtherAction(view: ViewGroup, event: MotionEvent): Boolean {
        val session = activeGesture ?: return false
        YLog.info(TAG, "Multi-touch (action=${event.actionMasked}), abort gesture")

        if (session.sender != Sender.SYSTEM) {
            toLyric(session, event, MotionEvent.ACTION_CANCEL)
        }
        dispatchCancelToSystem(view, session.area)
        endSession(deliverCancel = false)
        return false
    }

    /* ------------------------------------------------------------------ */
    /* 会话管理                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 开启会话：只有触摸起点落在歌词区域时才接管。
     */
    private fun openSession(event: MotionEvent): GestureSession? {
        val hit = findHit(event) ?: return null
        val (target, area) = hit

        val session = GestureSession(
            target = target,
            area = area,
            downEvent = MotionEvent.obtain(event),
            downScreenX = event.rawX,
            downScreenY = event.rawY,
            downTime = event.downTime
        )
        activeGesture = session

        YLog.info(
            TAG,
            "Gesture claimed: raw=(${event.rawX.fmt()}, ${event.rawY.fmt()}) " +
                    "slop=$touchSlop target=${target.javaClass.simpleName}"
        )
        return session
    }

    /**
     * 会话是否仍然有效。
     *
     * 跨 window 布局下无法用视图树校验归属，因此改为校验目标视图仍然附着：
     * 歌词被移除或界面重建时立即结束会话，避免手势状态悬挂。
     */
    private fun isSessionAlive(session: GestureSession): Boolean {
        val node = session.target as? View ?: return true
        return node.isAttachedToWindow
    }

    /**
     * 结束当前会话。
     *
     * @param deliverCancel true 表示还需向歌词补发 CANCEL（异常收尾 / 让渡给系统）
     */
    private fun endSession(deliverCancel: Boolean) {
        val session = activeGesture ?: return
        activeGesture = null

        if (deliverCancel && session.sender != Sender.SYSTEM) {
            toLyric(session, session.downEvent, MotionEvent.ACTION_CANCEL)
        }
        session.downEvent.recycle()
    }

    /**
     * 竖向让渡：歌词收到 CANCEL 清理按压动画，胶囊链路恢复接管。
     *
     * 系统侧此前只在 DOWN 阶段收到过落点，因此这里按"先重放 DOWN、再送当前 MOVE"
     * 的顺序补齐序列，确保系统的手势识别器拿到完整的 DOWN → MOVE。
     */
    private fun yieldToSystem(
        view: ViewGroup,
        event: MotionEvent,
        session: GestureSession,
        dx: Float,
        dy: Float
    ) {
        session.sender = Sender.SYSTEM
        toLyric(session, event, MotionEvent.ACTION_CANCEL)
        YLog.info(
            TAG,
            "Yield to system: dx=${dx.fmt()} dy=${dy.fmt()} " +
                    "elapsed=${event.eventTime - session.downTime}ms"
        )
        replayDownToSystem(view, session)
        dispatchToSystem(view, event)
    }

    /**
     * 横向保留：胶囊链路收到 CANCEL，此后事件只由歌词处理。
     *
     * 整段手势期间系统侧不再收到事件，因此既不会误触胶囊，
     * 也不会在 TAP 与胶囊点击之间产生竞态。
     */
    private fun keepToLyric(
        view: ViewGroup,
        event: MotionEvent,
        session: GestureSession,
        dx: Float,
        dy: Float
    ) {
        session.sender = Sender.LYRIC
        YLog.info(TAG, "Keep to lyric: dx=${dx.fmt()} dy=${dy.fmt()}")
        dispatchCancelToSystem(view, session.area)
        toLyric(session, event)
    }

    /**
     * 是否把本次手势让渡回系统（通知栏下拉）。判定逻辑见 [GestureArbitration]。
     */
    private fun shouldYieldToSystem(session: GestureSession, dx: Float, dy: Float): Boolean =
        GestureArbitration.shouldYieldToSystem(
            dx = dx,
            dy = dy,
            elapsedMs = SystemClock.uptimeMillis() - session.downTime,
            touchSlop = touchSlop
        )

    /**
     * 是否由歌词保留本次手势（横向切歌）。判定逻辑见 [GestureArbitration]。
     */
    private fun shouldKeepToLyric(session: GestureSession, dx: Float, dy: Float): Boolean =
        GestureArbitration.shouldKeepToLyric(dx = dx, dy = dy, touchSlop = touchSlop)

    /* ------------------------------------------------------------------ */
    /* 派发与命中                                                          */
    /* ------------------------------------------------------------------ */

    /** 向歌词派发事件，坐标修正为视图局部坐标系 */
    private fun toLyric(
        session: GestureSession,
        event: MotionEvent,
        action: Int = event.actionMasked
    ): Boolean = session.target.dispatchTouchToLyric(copyEvent(event, session.area, action))

    /**
     * 把事件放行回系统链路。
     *
     * 这里的调用**必然再次进入本 Hook**，且**无法用反射规避**：
     * LSPosed 对调用点同样插桩，实测改成 `Method.invoke(原始方法)` 后
     * 依然回到 `intercept`，并因失去短路而形成无限递归，
     * 最终主线程栈耗尽、SystemUI ANR 并重启（ANR 堆栈可见
     * `dispatchToSystem → Method.invoke → intercept → dispatchCancelToSystem` 循环）。
     *
     * 因此重入标记是**必需**的，不是冗余保护：
     * 置位后由 [handleDispatchTouchEvent] 短路，直接调用窗口真实实现。
     */
    private fun dispatchToSystem(view: ViewGroup, event: MotionEvent) {
        redispatchDepth = 1
        try {
            view.dispatchTouchEvent(event)
        } finally {
            redispatchDepth = 0
        }
    }

    /** 在让渡发生点重放一次 DOWN，补全系统侧的手势序列 */
    private fun replayDownToSystem(view: ViewGroup, session: GestureSession) {
        val down = MotionEvent.obtain(
            session.downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_DOWN,
            session.area.hitX,
            session.area.hitY,
            0
        )
        try {
            dispatchToSystem(view, down)
        } finally {
            down.recycle()
        }
    }

    /** 向系统链路补发 CANCEL，清理胶囊容器的按压 / 跟踪状态 */
    private fun dispatchCancelToSystem(view: ViewGroup, area: HitArea) {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, area.hitX, area.hitY, 0)
        try {
            dispatchToSystem(view, cancel)
        } finally {
            cancel.recycle()
        }
    }

    /**
     * 命中测试：逐个询问候选目标，返回第一个命中者及其命中区域。
     *
     * 之所以**不要求**歌词视图与被 hook 的根窗口处于同一棵视图树：
     * ColorOS 16 的状态栏歌词内容会挂在另一个 window（不同 rootView）上，
     * 但两者的屏幕坐标空间一致——是否接管完全由几何命中决定。
     * 命中测试自身会校验歌词的可见性、附着状态与尺寸。
     */
    private fun findHit(event: MotionEvent): Pair<Target, HitArea>? {
        for (target in targets) {
            val area = hitArea(target, event) ?: continue
            return target to area
        }
        return null
    }

    /**
     * 命中测试。
     *
     * 事件在根窗口内的坐标与目标视图的屏幕坐标处于同一坐标空间
     * （`StatusBarWindowView` 位于窗口原点，`rawX/rawY` 即屏幕坐标），
     * 因此直接用 `rawX - event.x` 得到事件坐标到目标视图坐标的偏移。
     * 目标自身负责取实时 bounds，本方法不做任何坐标缓存。
     */
    private fun hitArea(target: Target, event: MotionEvent): HitArea? {
        if (target.touchPoint(event, HIT_TOLERANCE_PX) == null) return null

        return HitArea(
            hitX = event.rawX,
            hitY = event.rawY,
            offsetX = (event.rawX - event.x).roundToInt(),
            offsetY = (event.rawY - event.y).roundToInt()
        )
    }

    /** 为歌词控件构造一份坐标已修正（根窗口坐标系 → 视图局部坐标系）的事件副本 */
    private fun copyEvent(
        event: MotionEvent,
        area: HitArea,
        action: Int = event.actionMasked
    ): MotionEvent {
        val copy = MotionEvent.obtain(event)
        if (action != copy.actionMasked) {
            copy.action = action
        }
        copy.offsetLocation(area.offsetX.toFloat(), area.offsetY.toFloat())
        return copy
    }

    private fun ensureTouchConfig(view: View) {
        if (touchSlop > 0) return
        touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    }

    private fun Float.fmt(): String = String.format(Locale.US, "%.1f", this)
}
