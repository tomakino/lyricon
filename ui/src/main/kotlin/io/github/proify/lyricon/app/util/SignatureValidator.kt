/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * Android 应用签名校验工具类
 */
object SignatureValidator {
    /**
     * 从 PackageInfo 中提取签名信息
     * @param packageInfo 包信息对象
     * @return 签名列表,失败返回 null
     */
    @SuppressLint("ObsoleteSdkInt")
    fun getSignaturesFromPackageInfo(packageInfo: PackageInfo): List<Signature>? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.let { signingInfo ->
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners.toList()
                    } else {
                        signingInfo.signingCertificateHistory.toList()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.toList()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 通过包名获取应用的签名信息
     * @param context 上下文
     * @param packageName 包名
     * @return 签名列表,失败返回 null
     */
    @SuppressLint("ObsoleteSdkInt")
    fun getAppSignatures(context: Context, packageName: String): List<Signature>? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            getSignaturesFromPackageInfo(packageInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * 获取签名的 SHA-256 哈希值
     * @param signature 签名对象
     * @return SHA-256 哈希值(大写,无分隔符)
     */
    fun getSignatureSHA256(signature: Signature): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signature.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }

    /**
     * 获取签名的 SHA-1 哈希值
     * @param signature 签名对象
     * @return SHA-1 哈希值(大写,冒号分隔)
     */
    fun getSignatureSHA1(signature: Signature): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(signature.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * 获取签名的 MD5 哈希值
     * @param signature 签名对象
     * @return MD5 哈希值(大写,冒号分隔)
     */
    fun getSignatureMD5(signature: Signature): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(signature.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * 校验 PackageInfo 的签名(单个期望签名)
     * @param packageInfo 要校验的包信息
     * @param expectedSHA256 期望的 SHA-256 签名
     * @return 如果实际签名与期望签名匹配则返回 true
     */
    fun validateSignature(
        packageInfo: PackageInfo,
        expectedSHA256: String
    ): Boolean {
        return validateSignature(packageInfo, *arrayOf(expectedSHA256))
    }

    /**
     * 校验 PackageInfo 的签名(多个期望签名)
     * @param packageInfo 要校验的包信息
     * @param expectedSHA256Array 期望的 SHA-256 签名数组
     * @return 如果实际签名与任意一个期望签名匹配则返回 true
     */
    fun validateSignature(
        packageInfo: PackageInfo,
        vararg expectedSHA256Array: String,
    ): Boolean {
        val signatures = getSignaturesFromPackageInfo(packageInfo) ?: return false

        return signatures.any { signature ->
            val actualSHA256 = getSignatureSHA256(signature)
            expectedSHA256Array.any { expected ->
                actualSHA256 == expected.uppercase().replace(":", "")
            }
        }
    }

    /**
     * 校验应用签名(通过包名,单个期望签名)
     * @param context 上下文
     * @param packageName 包名
     * @param expectedSHA256 期望的 SHA-256 签名
     * @return 如果实际签名与期望签名匹配则返回 true
     */
    fun validateSignature(
        context: Context,
        packageName: String,
        expectedSHA256: String
    ): Boolean {
        return validateSignature(context, packageName, *arrayOf(expectedSHA256))
    }

    /**
     * 校验应用签名(通过包名,多个期望签名)
     * @param context 上下文
     * @param packageName 包名
     * @param expectedSHA256Array 期望的 SHA-256 签名数组
     * @return 如果实际签名与任意一个期望签名匹配则返回 true
     */
    @SuppressLint("ObsoleteSdkInt")
    fun validateSignature(
        context: Context,
        packageName: String,
        vararg expectedSHA256Array: String
    ): Boolean {
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }

        return validateSignature(packageInfo, *expectedSHA256Array)
    }

    /**
     * 校验当前应用的签名(单个期望签名)
     * @param context 上下文
     * @param expectedSHA256 期望的 SHA-256 签名
     * @return 如果实际签名与期望签名匹配则返回 true
     */
    fun validateCurrentAppSignature(context: Context, expectedSHA256: String): Boolean {
        return validateSignature(context, context.packageName, expectedSHA256)
    }

    /**
     * 校验当前应用的签名(多个期望签名)
     * @param context 上下文
     * @param expectedSHA256Array 期望的 SHA-256 签名数组
     * @return 如果实际签名与任意一个期望签名匹配则返回 true
     */
    fun validateCurrentAppSignature(
        context: Context,
        vararg expectedSHA256Array: String
    ): Boolean {
        return validateSignature(context, context.packageName, *expectedSHA256Array)
    }

    /**
     * 比较两个 PackageInfo 的签名是否一致
     * @param packageInfo1 第一个包信息
     * @param packageInfo2 第二个包信息
     * @return 如果两个包的签名完全一致返回 true
     */
    fun compareSignatures(packageInfo1: PackageInfo, packageInfo2: PackageInfo): Boolean {
        val signatures1 = getSignaturesFromPackageInfo(packageInfo1) ?: return false
        val signatures2 = getSignaturesFromPackageInfo(packageInfo2) ?: return false

        if (signatures1.size != signatures2.size) return false

        val hashes1 = signatures1.map { getSignatureSHA256(it) }.toSet()
        val hashes2 = signatures2.map { getSignatureSHA256(it) }.toSet()

        return hashes1 == hashes2
    }

    /**
     * 签名信息数据类
     */
    data class SignatureInfo(
        val sha256: String,
        val sha1: String,
        val md5: String
    )

    /**
     * 获取单个签名的详细信息
     * @param signature 签名对象
     * @return 签名详细信息
     */
    fun getSignatureInfo(signature: Signature): SignatureInfo {
        return SignatureInfo(
            sha256 = getSignatureSHA256(signature),
            sha1 = getSignatureSHA1(signature),
            md5 = getSignatureMD5(signature)
        )
    }

    /**
     * 获取 PackageInfo 的所有签名详细信息
     * @param packageInfo 包信息
     * @return 所有签名的详细信息列表
     */
    fun getAllSignatureInfo(packageInfo: PackageInfo): List<SignatureInfo>? {
        val signatures = getSignaturesFromPackageInfo(packageInfo) ?: return null
        return signatures.map { getSignatureInfo(it) }
    }

    /**
     * 校验 APK 文件的签名
     * @param context 上下文
     * @param apkPath APK 文件路径
     * @param expectedSHA256Array 期望的 SHA-256 签名数组
     * @return 如果实际签名与任意一个期望签名匹配则返回 true
     */
    @SuppressLint("ObsoleteSdkInt")
    fun validateApkSignature(
        context: Context,
        apkPath: String,
        vararg expectedSHA256Array: String
    ): Boolean {
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            apkPath,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
        ) ?: return false

        return validateSignature(packageInfo, *expectedSHA256Array)
    }
}