plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    signing
    id("com.vanniktech.maven.publish")
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // 公共模型带 @Serializable，消费方需要 kotlinx-serialization 运行时才能编解码，
    // 因此按 API 依赖（api）暴露，而不是 implementation
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    // 测试直接使用 org.junit.*，显式声明而非依赖 kotlin("test") 的传递引入
    testImplementation(libs.junit)
}

val version: String = rootProject.extra.get("lyricModelVersion") as String

mavenPublishing {
    coordinates(
        "io.github.proify.lyricon.lyric",
        "model",
        version
    )

    pom {
        name.set("model")
        description.set("model")
        inceptionYear.set("2025")
        url.set("https://github.com/proify/lyricon")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("Proify")
                name.set("Proify")
                url.set("https://github.com/proify")
            }
        }
        scm {
            url.set("https://github.com/proify/lyricon")
            connection.set("scm:git:git://github.com/proify/lyricon.git")
            developerConnection.set("scm:git:ssh://git@github.com/proify/lyricon.git")
        }
    }
    publishToMavenCentral()
    signAllPublications()
}

afterEvaluate {
    signing {
        useGpgCmd()
    }
}

// ==== 性能跑分任务: gradlew :lyric:model:benchmark ====
tasks.register<JavaExec>("benchmark") {
    group = "benchmark"
    description = "运行 TimingNavigator 性能跑分, 输出 benchmark/results.json"
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.benchmark.TimingBenchmarkReportKt")
    workingDir = rootProject.projectDir
    jvmArgs("-Xms512m", "-Xmx2g", "-XX:+UseParallelGC", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

tasks.register<JavaExec>("regenReport") {
    group = "benchmark"
    description = "仅重新生成跑分网页(读取已有 results.json, 不重新测量)"
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.benchmark.TimingBenchmarkReportKt")
    args("--regen")
    workingDir = rootProject.projectDir
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}