import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

// 1. 最先声明 pluginManagement (必须在最顶部)
pluginManagement {
    repositories {
        maven(url = "https://cache-redirector.jetbrains.com/intellij-platform-gradle-plugin")
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.2.20"
        id("org.jetbrains.intellij.platform") version "2.16.0"
    }
}

// 2. 接着声明 settings 自身的 plugins
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}

// 3. 然后才可以定义项目名称
rootProject.name = "Dfeer-Generate-Code"

// 4. 最后是依赖解析管理
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        mavenCentral()

        // IntelliJ Platform Gradle Plugin Repositories Extension
        intellijPlatform {
            defaultRepositories()
        }
    }
}