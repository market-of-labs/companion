pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// AGP ≥9 自带内置 Kotlin 支持，不再需要 org.jetbrains.kotlin.android。
// Compose 编译器插件从 Kotlin 2.0 起与 Kotlin 同版本发布，故版本 = Kotlin 版本。
// Kotlin 自身升到 2.4.10 的写法在根 build.gradle.kts 的 buildscript 里（AGP 9.x 内置的
// KGP 是 2.2.10，要往上走必须显式抬 classpath）。
//
// AGP 用 9.3.2 而非 Obtainium 工程的 9.0.1：compose-bom 2026.09.00 解析出的 compose 1.12.x
// 在自身元数据里要求 AGP ≥9.1.0，9.0.1 会直接拒绝。9.3.2 需要 Gradle ≥9.5.0（本机 9.7.1，满足）。
// 选 9.3.2 而不是更新的 9.4.0：9.3.2 本机 Gradle 缓存里已有，Sync 不必再联网下载 AGP。
plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

rootProject.name = "companion"
include(":app")
