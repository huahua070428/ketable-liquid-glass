plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.workbuddy.ketable"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.workbuddy.ketable"
        minSdk = 33
        targetSdk = 35
        versionCode = 541
        versionName = "5.4.1-test"
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
            // Demo 直接用 debug 签名,免去生成正式 keystore
            signingConfig = signingConfigs.getByName("debug")
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

    // 仅本地验收用：JVM 里软件渲染 Compose 截图(沙箱无 KVM,跑不了模拟器)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
