/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.util

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.util.Log
import androidx.core.graphics.scale
import io.github.proify.android.extensions.md5
import io.github.proify.android.extensions.saveTo
import io.github.proify.lyricon.xposed.systemui.Directory
import io.github.proify.lyricon.xposed.systemui.lyric.control.CoverBackdrop
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper.MAX_COVER_SIZE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 管理媒体通知封面的提取、持久化与缓存。
 *
 * 通过 [SystemUIMediaUtils] 监听媒体会话的元数据变化，自动从 [MediaMetadata]
 * 中提取封面 Bitmap，缩放并保存到对应应用的私有目录中。同时维护一个以歌曲信息
 * 为索引的缓存副本，便于后续快速查找。
 *
 * 主要特性：
 * - 按包名隔离处理，支持多个播放器同时运行
 * - 基于 [Bitmap.getGenerationId] 去重，避免重复处理相同封面
 * - 自动缩放大尺寸封面以节省存储和内存
 * - 线程安全：回调在主线程克隆 Bitmap，IO 操作在协程中异步执行
 */
object NotificationCoverHelper {

    private const val TAG = "NotificationCoverHelper"

    /** 当前播放封面的文件名 */
    private const val COVER_FILENAME = "cover.png"

    /** 保存过程中的临时文件名，用于原子写入 */
    private const val TEMP_FILENAME = "cover.png.tmp"

    /**
     * 封面最大尺寸（像素）。
     */
    private const val MAX_COVER_SIZE = 128

    /** 封面更新监听器集合，线程安全 */
    private val updateListeners = CopyOnWriteArraySet<OnCoverUpdateListener>()

    /** 封面颜色监听器集合，线程安全 */
    private val colorListeners = CopyOnWriteArraySet<OnCoverColorListener>()

    /** 标记是否已完成初始化 */
    private val initialized = AtomicBoolean(false)

    /** 协程作用域，用于异步执行文件 IO 操作 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 按包名管理各自的封面写入器，线程安全 */
    private val sessionWriters = ConcurrentHashMap<String, SessionCoverWriter>()

    // -------------------------------------------------------------------------
    // 公开 API
    // -------------------------------------------------------------------------

    /**
     * 注册封面更新监听器。
     * 当任意应用的封面发生变化时，将通过 [OnCoverUpdateListener.onCoverUpdated] 回调通知。
     */
    fun registerListener(listener: OnCoverUpdateListener) = updateListeners.add(listener)

    /**
     * 取消注册封面更新监听器。
     */
    fun unregisterListener(listener: OnCoverUpdateListener) = updateListeners.remove(listener)

    /**
     * 注册封面颜色监听器。
     * 当封面 Bitmap 提取后立即回调，无需等待文件写入完成。
     */
    fun registerColorListener(listener: OnCoverColorListener) = colorListeners.add(listener)

    /**
     * 取消注册封面颜色监听器。
     */
    fun unregisterColorListener(listener: OnCoverColorListener) = colorListeners.remove(listener)

    /**
     * 初始化 Helper，开始监听系统媒体控制器变化。
     * 多次调用安全，只有首次调用会执行初始化。
     */
    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return

        SystemUIMediaUtils.registerListener(object : SystemUIMediaUtils.MediaControllerCallback {
            override fun onMediaChanged(controller: MediaController, metadata: MediaMetadata) {
                val packageName = controller.packageName ?: return
                getOrCreateWriter(packageName).onMetadataChanged(metadata)
            }

            override fun onSessionDestroyed(controller: MediaController) {
                val packageName = controller.packageName ?: return
                sessionWriters.remove(packageName)
            }
        })
    }

    /**
     * 获取指定应用当前播放封面的文件。
     *
     * @param packageName 目标应用包名
     * @return 封面文件，如果目录不存在则返回 null
     */
    fun getLatestCoverFile(packageName: String): File? {
        val dir = Directory.getPackageDataDir(packageName) ?: return null
        return File(dir, COVER_FILENAME)
    }

    /**
     * 根据歌曲信息获取缓存的封面文件。
     * 缓存文件以 title + artist 的 MD5 哈希命名，存放在数据目录的 caches 子目录下。
     *
     * @param packageName 来源应用包名
     * @param title       歌曲标题
     * @param artist      艺术家名称
     * @return 缓存封面文件，如果目录不存在则返回 null
     */
    fun getCachedCoverFile(
        packageName: String,
        title: String,
        artist: String
    ): File? {
        if (packageName.isBlank()) return null
        if (title.isBlank() && artist.isBlank()) return null

        val dir = Directory.getPackageDataDir(packageName) ?: return null
        return dir.resolve("caches/${buildCoverCacheFilename(title, artist)}")
    }

    /**
     * 销毁 Helper，取消所有协程并清空内部状态。
     * 通常在 Xposed 模块卸载或宿主进程终止时调用。
     */
    fun destroy() {
        scope.cancel()
        sessionWriters.clear()
    }

    // -------------------------------------------------------------------------
    // 内部实现
    // -------------------------------------------------------------------------

    /**
     * 获取或创建指定包名的 [SessionCoverWriter]。
     * 使用 [Synchronized] 保证并发安全。
     */
    @Synchronized
    private fun getOrCreateWriter(packageName: String): SessionCoverWriter {
        return sessionWriters.getOrPut(packageName) {
            SessionCoverWriter(packageName)
        }
    }

    /**
     * 通知所有已注册的监听器封面已更新。
     */
    private fun notifyListeners(packageName: String, coverFile: File) {
        updateListeners.forEach { it.onCoverUpdated(packageName, coverFile) }
    }

    /**
     * 通知所有已注册的颜色监听器封面颜色已提取。
     */
    private fun notifyColorListeners(packageName: String, color: Int) {
        colorListeners.forEach { it.onCoverColorChanged(packageName, color) }
    }

    /**
     * 根据歌曲标题和艺术家生成封面缓存文件名。
     * 使用 MD5 避免特殊字符导致的文件系统兼容问题。
     */
    private fun buildCoverCacheFilename(title: String, artist: String?): String {
        return "${title}_${artist}.jpg".md5()
    }

    // -------------------------------------------------------------------------
    // 封面写入器
    // -------------------------------------------------------------------------

    /**
     * 负责处理单个媒体会话的封面提取、去重与持久化。
     *
     * 设计要点：
     * - 在主线程回调中立即克隆 Bitmap，因为 [MediaMetadata] 中的 Bitmap 生命周期
     *   不受控制，可能在回调返回后被系统回收。
     * - 克隆时同步进行缩放，减少后续内存占用和磁盘 I/O。
     * - 使用 [Mutex] 保证同一包名的保存操作串行化，避免竞态条件。
     * - 通过 [Bitmap.getGenerationId] 快速跳过已处理的封面。
     */
    private class SessionCoverWriter(private val packageName: String) {

        /** 上次处理的封面 generation ID，用于去重 */
        @Volatile
        private var lastCoverId: Int = -1

        /** 保护保存操作的互斥锁 */
        private val writeMutex = Mutex()

        /**
         * 处理元数据变化，提取并保存封面。
         *
         * 此方法在 [SystemUIMediaUtils.MediaControllerCallback.onMediaChanged] 中调用，
         * 运行在主线程。
         */
        fun onMetadataChanged(metadata: MediaMetadata) {
            val startTime = System.nanoTime()
            val originalCover = extractBitmap(metadata) ?: return
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)

            // 防止操作已回收的 Bitmap
            if (originalCover.isRecycled) return

            val coverId = originalCover.generationId
            // generationId 由 native 层保证在内容变化时改变，用于快速跳过重复通知
            if (lastCoverId == coverId) return

            // 必须在主线程立即克隆并缩放，因为 originalCover 可能随时被系统回收
            val cloneStart = System.nanoTime()
            val scaledCover = cloneAndScaleCover(originalCover) ?: return
            val cloneTime = (System.nanoTime() - cloneStart) / 1_000_000.0

            // 立即提取颜色并通知监听器（不等文件写入）
            try {
                val colorStart = System.nanoTime()
                val color = CoverBackdrop.backdropColor(scaledCover)
                val colorTime = (System.nanoTime() - colorStart) / 1_000_000.0

                notifyColorListeners(packageName, color)
                val totalTime = (System.nanoTime() - startTime) / 1_000_000.0
                Log.d(TAG, "[$packageName] onMetadataChanged: clone=${cloneTime}ms, color=${colorTime}ms, total=${totalTime}ms, color=#${Integer.toHexString(color)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract cover color for $packageName", e)
            }

            // 将文件写入操作分发到协程中异步执行
            scope.launch {
                saveCoverAndNotify(scaledCover, coverId, title, artist)
            }
        }

        /**
         * 克隆并缩放封面 Bitmap。
         *
         * 在主线程执行，因此需要对耗时进行控制：
         * - 小尺寸封面（≤ [MAX_COVER_SIZE]）：直接 copy，约 1~3ms
         * - 大尺寸封面：通过 [Bitmap.createScaledBitmap] 缩放，约 3~8ms
         * - 极端大图（≥2000px）：可能达到 10~15ms，但系统通知封面通常已被缩放
         *
         * @return 缩放后的 Bitmap 副本，调用者负责最终回收
         */
        private fun cloneAndScaleCover(original: Bitmap): Bitmap? {
            return try {
                val srcWidth = original.width
                val srcHeight = original.height
                if (srcWidth <= MAX_COVER_SIZE && srcHeight <= MAX_COVER_SIZE) {
                    // 尺寸已满足要求，仅做格式统一拷贝
                    original.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    // 需要缩小，createScaledBitmap 内部使用 Skia 高效缩放
                    original.scale(MAX_COVER_SIZE, MAX_COVER_SIZE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clone/scale album art for $packageName", e)
                null
            }
        }

        /**
         * 在协程中保存封面并通知监听器。
         *
         * 使用 [writeMutex] 串行化同一包名的写入操作，避免并发覆盖。
         */
        private suspend fun saveCoverAndNotify(
            cover: Bitmap,
            coverId: Int,
            title: String?,
            artist: String?
        ) {
            try {
                writeMutex.withLock {
                    // 双重检查：如果在等待锁期间已有更新的封面被处理，丢弃当前任务
                    if (lastCoverId == coverId) {
                        cover.safeRecycle()
                        return@withLock
                    }

                    // 切换到 IO 线程执行文件写入
                    val savedSuccessfully = withContext(Dispatchers.IO) {
                        writeCoverToDisk(cover, title, artist)
                    }

                    if (savedSuccessfully) {
                        lastCoverId = coverId
                        getLatestCoverFile(packageName)?.let { file ->
                            notifyListeners(packageName, file)
                        }
                    }

                    cover.safeRecycle()
                }
            } catch (e: CancellationException) {
                // 协程被取消时仍需回收 Bitmap
                cover.safeRecycle()
                throw e
            }
        }

        /**
         * 将封面写入磁盘。
         *
         * 写入策略：
         * 1. 先写入临时文件，成功后原子移动到目标路径，防止写入中断导致文件损坏
         * 2. 如果歌曲信息完整（title + artist），同步生成缓存副本
         *
         * @return true 表示写入成功
         */
        private fun writeCoverToDisk(
            cover: Bitmap,
            title: String?,
            artist: String?
        ): Boolean {
            return try {
                val dataDir = Directory.getPackageDataDir(packageName)
                    ?: run {
                        Log.e(TAG, "Cannot get data directory for $packageName")
                        return false
                    }

                val targetFile = File(dataDir, COVER_FILENAME)
                val tempFile = File(dataDir, TEMP_FILENAME)

                // 先保存到临时文件
                if (!cover.saveTo(tempFile)) return false

                // 原子移动到目标路径
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )

                // 如果有歌曲信息，额外保存一份缓存副本
                if (title != null && artist != null) {
                    val cacheFile = getCachedCoverFile(packageName, title, artist)
                    if (cacheFile != null) {
                        targetFile.copyTo(cacheFile, overwrite = true)
                    }
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "IO error saving cover for $packageName", e)
                false
            }
        }
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    /**
     * 从 [MediaMetadata] 中提取封面 Bitmap。
     *
     * 按优先级依次尝试以下 key：
     * 1. [MediaMetadata.METADATA_KEY_ALBUM_ART] - 专辑封面（首选）
     * 2. [MediaMetadata.METADATA_KEY_ART]        - 艺术家图片
     * 3. [MediaMetadata.METADATA_KEY_DISPLAY_ICON] - 显示图标（兜底）
     */
    private fun extractBitmap(metadata: MediaMetadata): Bitmap? {
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    /**
     * 安全回收 Bitmap，忽略已回收的情况。
     */
    private fun Bitmap.safeRecycle() {
        if (!isRecycled) recycle()
    }

    // -------------------------------------------------------------------------
    // 回调接口
    // -------------------------------------------------------------------------

    /**
     * 封面更新监听器。
     * 当任意注册应用的当前播放封面发生变化时回调。
     */
    fun interface OnCoverUpdateListener {
        /**
         * @param packageName 发生封面更新的应用包名
         * @param coverFile   最新的封面文件
         */
        fun onCoverUpdated(packageName: String, coverFile: File)
    }

    /**
     * 封面颜色监听器：在封面 Bitmap 提取后立即回调，无需等待文件写入完成。
     */
    fun interface OnCoverColorListener {
        /**
         * @param packageName 发生封面更新的应用包名
         * @param color 封面主色 (ARGB)
         */
        fun onCoverColorChanged(packageName: String, color: Int)
    }
}