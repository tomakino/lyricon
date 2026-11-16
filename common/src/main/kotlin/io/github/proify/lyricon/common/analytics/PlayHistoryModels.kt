/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.common.analytics

import kotlinx.serialization.Serializable

/**
 * 单次播放记录。
 */
@Serializable
data class PlayRecord(
    val songName: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val playedDurationMs: Long = 0,
    val timestampMs: Long = 0,
    val playerPackage: String = "",
    val hasLyrics: Boolean = false,
    val isPlaying: Boolean = true,
    val dayOfWeek: Int = 0,
)

/**
 * 歌手统计数据。
 */
@Serializable
data class ArtistStat(
    val name: String,
    val playCount: Int,
    val totalDurationMs: Long,
)

/**
 * 歌曲统计数据。
 */
@Serializable
data class SongStat(
    val name: String,
    val artist: String,
    val playCount: Int,
    val totalDurationMs: Long,
)

/**
 * 聚合播放统计结果。
 */
@Serializable
data class PlayStats(
    val totalPlays: Int = 0,
    val totalDurationMs: Long = 0,
    val uniqueSongs: Int = 0,
    val uniqueArtists: Int = 0,
    val topArtists: List<ArtistStat> = emptyList(),
    val topSongs: List<SongStat> = emptyList(),
    val playTimeDistribution: Map<String, Int> = emptyMap(),
    val recentRecords: List<PlayRecord> = emptyList(),
    val firstRecordTimeMs: Long = 0,
    val lastRecordTimeMs: Long = 0,
) {
    val avgPlayDurationMinutes: Double
        get() = if (totalPlays > 0) totalDurationMs.toDouble() / totalPlays / 60000 else 0.0

    val peakPlayPeriod: String
        get() = playTimeDistribution.maxByOrNull { it.value }?.key ?: "未知"

    val dataSpanDays: Int
        get() = if (firstRecordTimeMs > 0 && lastRecordTimeMs > 0) {
            ((lastRecordTimeMs - firstRecordTimeMs) / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
        } else 0
}

/**
 * 工具调用状态。
 */
enum class ToolCallStatus {
    PENDING,
    EXECUTING,
    COMPLETED,
    FAILED
}

/**
 * 工具调用记录。
 */
data class ToolCallRecord(
    val id: String = "",
    val name: String = "",
    val arguments: String = "{}",
    val result: String = "",
    val status: ToolCallStatus = ToolCallStatus.PENDING,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val error: String? = null,
) {
    /**
     * 显示名称（引用 MusicAnalysisTools.DISPLAY_NAMES）
     * 注意：需要在 app 模块中设置此回调
     */
    var displayNameProvider: ((String) -> String)? = null

    val displayName: String
        get() = displayNameProvider?.invoke(name) ?: name

    val durationMs: Long
        get() = if (endTime > 0 && startTime > 0) endTime - startTime else 0

    val isFinished: Boolean
        get() = status == ToolCallStatus.COMPLETED || status == ToolCallStatus.FAILED
}

/**
 * 思考步骤。
 */
sealed class ThinkingStep {
    abstract val index: Int

    data class Reasoning(
        override val index: Int,
        val text: String,
    ) : ThinkingStep()

    data class ToolCall(
        override val index: Int,
        val record: ToolCallRecord,
    ) : ThinkingStep() {
        val isExecuting: Boolean get() = record.status == ToolCallStatus.EXECUTING
        val isCompleted: Boolean get() = record.status == ToolCallStatus.COMPLETED
        val isFailed: Boolean get() = record.status == ToolCallStatus.FAILED
    }
}

/**
 * 思考过程状态。
 */
data class ThinkingProcessState(
    val steps: List<ThinkingStep> = emptyList(),
    val isThinking: Boolean = false,
) {
    val hasSteps: Boolean get() = steps.isNotEmpty()
    val latestStep: ThinkingStep? get() = steps.lastOrNull()
    val toolCallCount: Int get() = steps.count { it is ThinkingStep.ToolCall }
    val completedToolCallCount: Int
        get() = steps.count { it is ThinkingStep.ToolCall && it.record.isFinished }
}
