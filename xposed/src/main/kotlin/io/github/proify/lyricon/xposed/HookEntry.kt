/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.xposed.systemui.Directory
import io.github.proify.lyricon.xposed.systemui.SystemUIHooker

@InjectYukiHookWithXposed(modulePackageName = PackageNames.APPLICATION)
open class HookEntry : IYukiHookXposedInit {

    override fun onHook() {
        YukiHookAPI.encase {
            onAppLifecycle {
                onCreate {
                    Directory.initialize(applicationContext)
                }
            }
            loadApp(PackageNames.APPLICATION, AppHooker)
            loadApp(PackageNames.SYSTEM_UI, SystemUIHooker)
            loadApp(PackageNames.SYSTEM_UI_PLUGIN, SystemUIHooker)
        }
    }

    override fun onInit() {
        YukiHookAPI.configs {
            debugLog {
                tag = "Lyricon"
                isEnable = true
                elements(TAG, PRIORITY, PACKAGE_NAME, USER_ID)
            }
        }
    }
}
