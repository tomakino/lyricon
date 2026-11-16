/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.player

import android.util.Log
import io.github.proify.lyricon.central.internal.CentralConstants
import io.github.proify.lyricon.central.internal.player.PlayerSession.LyricType.NONE
import io.github.proify.lyricon.central.internal.player.PlayerSession.LyricType.SONG
import io.github.proify.lyricon.central.internal.player.PlayerSession.LyricType.TEXT
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 活跃播放器的编排中枢。
 *
 * 选择策略由 [ActivePlayerSelector] 决策；本类负责把决策落实为对 [ActivePlayerListener]
 * 的通知（切换时全量报告、增量事件、异常隔离），并在活跃者断开时释放状态。
 */
internal class ActivePlayerHub : PlayerListener {

    private val debug = CentralConstants.isDebug()
    private val lock = ReentrantReadWriteLock()
    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()
    private val selector = ActivePlayerSelector()

    /**
     * 添加监听器；若已存在活跃播放器，立即同步一份完整状态快照。
     *
     * @param listener 活跃播放器状态监听器。
     */
    fun addListener(listener: ActivePlayerListener) {
        if (listeners.add(listener)) {
            syncLatestState(listener)
        }
    }

    /**
     * 移除监听器。
     *
     * @param listener 之前添加的监听器。
     */
    fun removeListener(listener: ActivePlayerListener) = listeners.remove(listener)

    /** 向指定监听器同步当前活跃状态快照（若无活跃播放器则跳过）。 */
    fun syncLatestState(listener: ActivePlayerListener) {
        val snapshot = lock.read {
            selector.activeSession?.snapshot(selector.activeIsPlaying)
        } ?: return

        dispatchSnapshot(snapshot, listener)
    }

    /**
     * 活跃提供端失效（断开/注销）：若其正是当前活跃者则释放并广播清空状态。
     *
     * @param provider 失效提供端信息。
     */
    fun notifyProviderInvalid(provider: ProviderInfo) {
        val shouldNotify = lock.write {
            selector.release(provider)
        }

        if (shouldNotify) {
            broadcast {
                it.onActiveProviderChanged(null)
                it.onPlaybackStateChanged(false)
            }
        }
    }

    override fun onSongChanged(session: PlayerSession, song: Song?) {
        if (debug) Log.d(TAG, "onSongChanged: $song")
        dispatchIfActive(session, allowDuplicateIfSwitching = false) {
            it.onSongChanged(song)
        }
    }

    override fun onPlaybackStateChanged(session: PlayerSession, isPlaying: Boolean) {
        if (debug) Log.d(TAG, "onPlaybackStateChanged: $isPlaying")
        dispatchIfActive(session) {
            it.onPlaybackStateChanged(isPlaying)
        }
    }

    override fun onPositionChanged(session: PlayerSession, position: Long) {
        dispatchIfActive(session) {
            it.onPositionChanged(position)
        }
    }

    override fun onSeekTo(session: PlayerSession, position: Long) {
        dispatchIfActive(session) {
            it.onSeekTo(position)
        }
    }

    override fun onSendText(session: PlayerSession, text: String?) {
        dispatchIfActive(session, allowDuplicateIfSwitching = false) {
            it.onSendText(text)
        }
    }

    override fun onDisplayTranslationChanged(
        session: PlayerSession,
        isDisplayTranslation: Boolean
    ) {
        dispatchIfActive(session, allowDuplicateIfSwitching = false) {
            it.onDisplayTranslationChanged(isDisplayTranslation)
        }
    }

    override fun onDisplayRomaChanged(session: PlayerSession, displayRoma: Boolean) {
        dispatchIfActive(session, allowDuplicateIfSwitching = false) {
            it.onDisplayRomaChanged(displayRoma)
        }
    }

    /** 切换后向监听器全量同步新活跃者的状态。 */
    private fun syncNewProviderState(session: PlayerSession, listener: ActivePlayerListener) {
        val snapshot = lock.read {
            session.snapshot(selector.activeIsPlaying)
        }
        dispatchSnapshot(snapshot, listener)
    }

    /** 把一份完整报告逐项分发给监听器。 */
    private fun dispatchSnapshot(snapshot: ActivePlayerReport, listener: ActivePlayerListener) {
        listener.onActiveProviderChanged(snapshot.providerInfo)
        listener.onPlaybackStateChanged(snapshot.isPlaying)

        when (snapshot.lyricType) {
            SONG -> listener.onSongChanged(snapshot.song)
            TEXT -> listener.onSendText(snapshot.text)
            NONE -> Unit
        }

        listener.onDisplayTranslationChanged(snapshot.isDisplayTranslation)
        listener.onDisplayRomaChanged(snapshot.isDisplayRoma)
        listener.onPositionChanged(snapshot.position)
    }

    /**
     * 依据选择器决策分发一条来源事件。
     *
     * @param session 事件来源会话。
     * @param allowDuplicateIfSwitching 切换后是否额外广播原始事件增量（见选择器说明）。
     */
    private inline fun dispatchIfActive(
        session: PlayerSession,
        allowDuplicateIfSwitching: Boolean = true,
        crossinline notifier: (ActivePlayerListener) -> Unit
    ) {
        val decision = lock.write {
            selector.decide(session, allowDuplicateIfSwitching)
        }

        when (decision) {
            ActivePlayerDecision.Ignore -> Unit
            ActivePlayerDecision.Keep -> broadcast(notifier)
            is ActivePlayerDecision.Switch -> {
                broadcast { syncNewProviderState(decision.session, it) }
                if (decision.broadcastOriginal) {
                    broadcast(notifier)
                }
            }
        }
    }

    private inline fun broadcast(crossinline notifier: (ActivePlayerListener) -> Unit) {
        for (listener in listeners) {
            try {
                notifier(listener)
            } catch (e: Exception) {
                if (debug) Log.e(TAG, "Dispatch failed for listener: ${listener.javaClass.name}", e)
            }
        }
    }

    /** 生成该会话在 [isPlaying] 下的完整报告。 */
    private fun PlayerSession.snapshot(isPlaying: Boolean) = ActivePlayerReport(
        providerInfo = providerInfo,
        isPlaying = isPlaying,
        song = song,
        text = text,
        lyricType = lyricType,
        isDisplayTranslation = isDisplayTranslation,
        isDisplayRoma = isDisplayRoma,
        position = position
    )

    private companion object {
        private const val TAG = "ActivePlayerHub"
    }
}
