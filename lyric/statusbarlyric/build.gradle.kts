plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.proify.lyricon.statusbarlyric"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int){
           // minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 27

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    implementation(project(":common"))
    implementation(project(":lyric:view"))
    implementation(project(":lyric:model"))
    implementation(project(":lyric:style"))

    implementation(libs.lyricon.provider)

    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}