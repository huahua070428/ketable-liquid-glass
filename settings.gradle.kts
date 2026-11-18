pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        // Kyant0/AndroidLiquidGlass（液态玻璃 Backdrop 引擎）
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\..*") } }
    }
}

rootProject.name = "GlassDemo"
include(":app")
