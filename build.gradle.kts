plugins {
    id("com.android.application") version "9.1.1" apply false
    // AGP 9.0 起 Kotlin 已内置，不再声明 org.jetbrains.kotlin.android
    // （run #25 失败原因）；Compose 编译器插件仍保留。
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
