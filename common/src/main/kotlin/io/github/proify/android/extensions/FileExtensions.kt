/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.android.extensions

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

/**
 * 计算文件的 MD5 哈希值
 * @return MD5 哈希字符串，如果文件不存在或不可读则返回空字符串
 */
fun File.md5(): String {
    if (!exists() || !canRead()) return ""
    return try {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(this).use { fis ->
            DigestInputStream(fis, digest).use { dis ->
                val buffer = ByteArray(8192)
                while (dis.read(buffer) != -1) {
                    // DigestInputStream 自动更新 digest
                }
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        android.util.Log.e("FileExt", "md5: Failed to calculate hash", e)
        ""
    }
}

/**
 * 计算文件的 CRC32 校验值
 * @return CRC32 校验值
 */
fun File.crc32(): Long {
    val crc = CRC32()
    inputStream().use { input ->
        CheckedInputStream(input, crc).use { cis ->
            val buffer = ByteArray(8192)
            while (cis.read(buffer) != -1) {
                // CheckedInputStream 自动更新 CRC
            }
        }
    }
    return crc.value
}

/**
 * 计算大文件的 MD5 哈希值（使用 NIO 以获得更好性能）
 * @return MD5 哈希字符串，如果文件不存在或不可读则返回空字符串
 */
fun File.md5Nio(): String {
    if (!exists() || !canRead()) return ""
    return try {
        val digest = MessageDigest.getInstance("MD5")
        Files.newInputStream(toPath()).use { fis ->
            Channels.newChannel(fis).use { channel ->
                // 使用堆内存替代直接内存，对小文件更高效
                val buffer = ByteBuffer.allocate(8192)
                while (channel.read(buffer) != -1) {
                    buffer.flip()
                    digest.update(buffer)
                    buffer.clear()
                }
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        android.util.Log.e("FileExt", "md5Nio: Failed to calculate hash", e)
        ""
    }
}