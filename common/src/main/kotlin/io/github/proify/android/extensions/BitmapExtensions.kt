/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.android.extensions

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.annotation.IntRange
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

private const val TAG = "BitmapExt"

/**
 * 将 [Bitmap] 保存到文件。
 */
fun Bitmap.saveTo(
    file: File,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    @IntRange(from = 0, to = 100) quality: Int = 100
): Boolean {
    if (isRecycled) return false

    return try {
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
        file.outputStream().buffered().use { out ->
            this.compress(format, quality, out)
        }
        true
    } catch (e: IOException) {
        Log.e(TAG, "saveTo: Failed to write file", e)
        false
    }
}

/**
 * 将 [Bitmap] 转换为字节数组。
 */
fun Bitmap.toByteArray(
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    @IntRange(from = 0, to = 100) quality: Int = 100
): ByteArray {
    if (isRecycled) return byteArrayOf()
    return ByteArrayOutputStream().use {
        compress(format, quality, it)
        it.toByteArray()
    }
}

/**
 * 从文件安全解码 Bitmap。
 */
@SuppressLint("ObsoleteSdkInt")
fun File.toBitmap(reqWidth: Int = 0, reqHeight: Int = 0): Bitmap? {
    if (!exists() || !canRead()) return null

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(this)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // 设置采样率或目标尺寸以节省内存
                if (reqWidth > 0 && reqHeight > 0) {
                    decoder.setTargetSize(reqWidth, reqHeight)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
                decoder.isMutableRequired = true
            }
        } else {
            decodeLegacy(this, reqWidth, reqHeight)
        }
    } catch (e: Exception) {
        Log.e(TAG, "toBitmap: Failed", e)
        null
    }
}

private fun decodeLegacy(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
    val options = BitmapFactory.Options()
    if (reqWidth > 0 && reqHeight > 0) {
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
    }
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize = inSampleSize shl 1
        }
    }
    return inSampleSize
}

/**
 * Drawable 转 Bitmap。
 */
fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null && !bitmap.isRecycled) {
        return bitmap
    }

    val width = if (intrinsicWidth <= 0) 1 else intrinsicWidth
    val height = if (intrinsicHeight <= 0) 1 else intrinsicHeight

    return createBitmap(width, height, Bitmap.Config.ARGB_8888).applyCanvas {
        setBounds(0, 0, width, height)
        draw(this)
    }
}