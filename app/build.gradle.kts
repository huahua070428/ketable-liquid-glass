plugins {
    id("com.android.application")
    // AGP 9.0 起 Kotlin 已内置（android.builtInKotlin 默认 true），
    // 再显式应用 org.jetbrains.kotlin.android 会直接报错
    // （run #25：The 'org.jetbrains.kotlin.android' plugin is no longer required）。
    // Compose 编译器插件仍需显式应用，官方迁移指南只要求去掉 kotlin-android。
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.workbuddy.ketable"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.workbuddy.ketable"
        minSdk = 33
        targetSdk = 35
        versionCode = 551
        versionName = "5.4.11test"
    }

    // 正式签名：GitHub Actions 用 Secrets 里的 KEYSTORE_BASE64 解出 keystore 再签，
    // 这样每次编译的签名都一样，手机才能正常覆盖安装。
    // 本地没有 KEYSTORE_PATH 时退回 debug 签名。
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            // R8 保留（不裁剪包体 49MB），但 **关掉混淆**：
            // 真机崩了要能读懂堆栈，同时排除「类被改名」这类隐性崩溃。
            // 我们的代码和玻璃库全量 keep，见 proguard-rules.pro。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有正式 keystore 就用它签，没有就用 debug 签名
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // AGP 8.7 内置的 lint 与 Kotlin 2.2 元数据不兼容（NonNullableMutableLiveDataDetector 崩），
        // 这里只关掉 release 的 lint 阻断，编译与 R8 照常
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 液态玻璃：GraphicsLayer 实时采样背景 + AGSL 折射（真扭曲背后的内容）
    implementation("com.github.Kyant0:AndroidLiquidGlass:1.0.0-rc01")
    // V6 试验（2026-11-18）：PrismalAGSL 1.0.7——Dock 水滴 + FAB 改用它，
    // 水滴采样「页面背景 + 底栏本身（含图标）」，图标真的会被透镜折弯。
    // 其他玻璃（灵动岛/页头）仍走 Kyant，验证通过前不拆旧链路。
    // 注意：坐标必须是三段式 com.github.styropyr0:PrismalAGSL:1.0.7。
    // README 写的四段式 com.github.styropyr0:PrismalAGSL:Prismal:1.0.7 是错的——
    // Gradle 会把 "Prismal" 当成版本号去找，run #19 第二次尝试就是这么失败的。
    // 已用 JitPack 上实际发布的 POM 核对过：groupId=com.github.styropyr0，
    // artifactId=PrismalAGSL，version=1.0.7。
    implementation("com.github.styropyr0:PrismalAGSL:1.0.7")

    // 仅本地验收用：JVM 里软件渲染 Compose 截图(沙箱无 KVM,跑不了模拟器)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
