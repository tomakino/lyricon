/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.common.util.safe
import java.util.concurrent.CopyOnWriteArraySet

class LyriconApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "LyriconApp created")

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                Log.i(TAG, "XposedService bind")
                xposedService = service
                xposedServiceStateListeners.forEach {
                    it.onServiceStateChanged(service)
                }
            }

            override fun onServiceDied(service: XposedService) {
                Log.i(TAG, "XposedService died")
                xposedService = null
                xposedServiceStateListeners.forEach {
                    it.onServiceStateChanged(null)
                }
            }
        })
    }

    override fun attachBaseContext(base: Context) {
        instance = this
        AppLangUtils.setDefaultLocale(base)
        super.attachBaseContext(AppLangUtils.wrapContext(base))
    }

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        super.getSharedPreferences(name, mode).safe()

    companion object {
        const val TAG: String = "LyriconApp"
        private val xposedServiceStateListeners = CopyOnWriteArraySet<XposedServiceStateListener>()

        lateinit var instance: LyriconApp
            private set

        var xposedService: XposedService? = null
            private set

        fun addXposedServiceStateListener(
            listener: XposedServiceStateListener,
            notifyImmediately: Boolean = true
        ) {
            xposedServiceStateListeners.add(listener)
            if (notifyImmediately && xposedService != null) {
                listener.onServiceStateChanged(xposedService)
            }
        }

        fun removeXposedServiceStateListener(listener: XposedServiceStateListener) {
            xposedServiceStateListeners.remove(listener)
        }

        fun get(): LyriconApp = instance

        val packageInfo: PackageInfo by lazy {
            instance.packageManager.getPackageInfo(
                instance.packageName, 0
            )
        }

        val versionCode: Long by lazy { PackageInfoCompat.getLongVersionCode(packageInfo) }

        var isSafeMode: Boolean = false
            private set

        fun setSafeMode(safeMode: Boolean) {
            this.isSafeMode = safeMode
        }
    }

    interface XposedServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }
}