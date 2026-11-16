@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}

include(
    ":lyricon",
    ":app",
    ":bridge",
    ":xposed",
    ":common",
    "yoyo"
)

include(":lyric:bridge:central")
include(":lyric:bridge:provider")
include(":lyric:bridge:subscriber")
include(":lyric:bridge:centralapp")
include(":lyric:bridge:localcentralapp")

include(":lyric:model")
include(":lyric:view")
include(":lyric:style")
include(":lyric:ai")
include(":lyric:viewAppTest")
include(":lyric:statusbarlyric")

include(":opencc-lite")

rootProject.name = "lyricon"