import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

configure<ApplicationExtension> {
    namespace = "io.github.proify.lyricon.lyric_test"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int) {
            //minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.proify.lyricon.lyric_test"
        minSdk = 33
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        viewBinding = true
    }
}

dependencies {
    //noinspection GradleDependency,UseTomlInstead
    implementation("androidx.media3:media3-exoplayer:1.9.2")
    implementation("androidx.media3:media3-exoplayer-dash:1.9.2")
    implementation("androidx.media3:media3-ui:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    //noinspection NewerVersionAvailable
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    implementation(project(":lyric:view"))

    implementation(libs.lyricon.provider)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}