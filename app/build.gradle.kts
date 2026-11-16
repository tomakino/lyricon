import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionCode: Int = rootProject.extra["appVersionCode"] as Int
val versionName: String = rootProject.extra["appVersionName"] as String
val buildTime: Long = System.currentTimeMillis()

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.app"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }

    defaultConfig {
        minSdk = rootProject.extra.get("minSdkVersion") as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("int", "VERSION_CODE", versionCode.toString())
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        buildConfigField("long", "BUILD_TIME", "${buildTime}L")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // --- 模块依赖 ---
    implementation(project(":bridge"))
    implementation(project(":common"))
    implementation(project(":lyric:style"))
    implementation(project(":lyric:view"))
    implementation(project(":lyric:ai"))
    implementation(project(":lyric:model"))
    implementation(project(":lyric:bridge:subscriber"))

    // --- 第三方 UI 库 ---
    implementation(libs.miuix.android)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.multiplatform.markdown.renderer)
    implementation(libs.okhttp)

    implementation(libs.libxposed.service)

    implementation(libs.aboutlibraries.core)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.chrisbanes.haze)
    implementation(libs.bonsai.core)
    implementation(libs.capsule.android)
    implementation(libs.lottie.compose) {
        exclude(group = "androidx.appcompat", module = "appcompat")
    }

    // --- 核心逻辑与 AndroidX ---
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Jetpack Compose (通过 BOM 管理) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)


    // --- 单元测试 ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // --- 调试工具 ---
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
