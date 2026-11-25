import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    id("com.mikepenz.aboutlibraries.plugin.android") version "15.3.0" apply false
}

extra["appPackageName"] = "io.github.proify.lyricon"
extra["appVersionCode"] = 51
extra["appVersionName"] = "1.0.40.rc"
extra["compileSdkVersion"] = 37
extra["targetSdkVersion"] = 37
extra["minSdkVersion"] = 29

extra["providerSdkVersion"] = "0.1.80"
extra["subscriberSdkVersion"] = "0.1.80"
extra["lyricModelVersion"] = "0.1.80"

// apk自动导出
/**
 * 1. 注册清理任务
 */
val cleanApks: TaskProvider<Task> = tasks.register("cleanAllApks") {
    group = "build"
    // 在配置阶段解析路径, 避免执行期访问 project (配置缓存兼容)
    val outputDir = project.layout.buildDirectory.dir("all-apks").get().asFile
    doFirst {
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
            println("--- [Clean] 已清理旧的 APK 导出目录 ---")
        }
    }
}

/**
 * 2. 注册总入口任务
 */
val copyApksAll: TaskProvider<Task> = tasks.register("copyApks") {
    group = "build"
    description = "收集所有 APK 并按 BuildType 分开打包 ZIP"
}

// 建立一个 Set 来记录我们已经创建过哪些 buildType 的 Zip 任务
val buildTypeZipTasks = mutableSetOf<String>()

subprojects {
    plugins.withId("com.android.application") {
        val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()

        androidComponents.onVariants { variant ->
            val variantName = variant.name
            val moduleName = project.name
            val buildType = variant.buildType ?: "others"
            val versionName =
                variant.outputs.firstOrNull()?.versionName?.getOrElse(project.version.toString())
                    ?: "1.0"

            // --- A. 创建或获取该 BuildType 专属的 Zip 任务 ---
            val zipTaskName = "zip${buildType.replaceFirstChar { it.uppercase() }}Apks"
            val typeZipTask = rootProject.tasks.maybeCreate(zipTaskName, Zip::class.java).apply {
                group = "build"
                archiveFileName.set("${rootProject.name}-all-$buildType.zip")
                destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))

                // 只压缩对应的子目录：build/all-apks/{buildType}
                from(rootProject.layout.buildDirectory.dir("all-apks/$buildType"))

                // 确保 Zip 在对应的 Copy 任务完成后运行
                // 注意：这里由于 maybeCreate 的特性，我们后面通过 finalizedBy 关联
            }

            // --- B. 创建 Copy 任务 ---
            val copyTask =
                tasks.register<Copy>("copy${variantName.replaceFirstChar { it.uppercase() }}Apk") {
                    dependsOn(cleanApks)
                    from(variant.artifacts.get(SingleArtifact.APK))
                    into(rootProject.layout.buildDirectory.dir("all-apks/$buildType"))
                    include("*.apk")

                    eachFile {
                        relativePath = RelativePath(true, name)
                    }

                    rename { fileName ->
                        val abiSuffix = when {
                            fileName.contains("arm64-v8a") -> "-arm64-v8a"
                            fileName.contains("armeabi-v7a") -> "-armeabi-v7a"
                            fileName.contains("x86_64") -> "-x86_64"
                            else -> ""
                        }
                        "${moduleName}-${versionName}-${buildType}${abiSuffix}.apk"
                    }
                    duplicatesStrategy = DuplicatesStrategy.INCLUDE

                    // 关键点：Copy 完后，自动触发该类型的 Zip 任务
                    finalizedBy(typeZipTask)
                }

            // --- C. 任务挂载 ---
            copyApksAll.configure {
                dependsOn(copyTask)
            }

            // 自动化：执行 assemble 时触发
            tasks.matching { it.name == "assemble${variantName.replaceFirstChar { c -> c.uppercase() }}" }
                .configureEach {
                    finalizedBy(copyTask)
                }
        }
    }
}