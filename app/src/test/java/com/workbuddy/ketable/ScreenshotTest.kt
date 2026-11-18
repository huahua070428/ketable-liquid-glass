package com.workbuddy.ketable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RuntimeShader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File

/**
 * 本地验收截图：JVM 里软件渲染 Compose（Robolectric + view.draw）。
 *
 * RuntimeShader 在软件画布上不可用（AGSL 需要硬件加速），这里让构造直接失败，
 * 走 GlassUi 既有的 try-catch 降级分支，于是能渲染真实布局。
 * 注意：这些图能验收布局/间距/字号/对齐，玻璃质感必须真机截图。
 */
@Implements(RuntimeShader::class)
class ShadowNoAgsL {
    @Implementation
    fun __constructor__(sksl: String) {
        throw RuntimeException("AGSL 在 JVM 中禁用（仅用于布局截图）")
    }

    @Implementation
    fun __constructor__(sksl: String, sksl2: String) {
        throw RuntimeException("AGSL 在 JVM 中禁用（仅用于布局截图）")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w390dp-h844dp-xhdpi", shadows = [ShadowNoAgsL::class])
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun seed() {
        DataStore.init(ApplicationProvider.getApplicationContext())
        DataStore.resetAll()
        DataStore.saveCourses(DEMO)
        DataStore.saveSettings(
            Settings(
                semesterStart = "2026-09-07",
                showWeekend = false,
                themeMode = "dark",
                gradCredits = 160
            )
        )
    }

    private fun shoot(
        name: String,
        dark: Boolean,
        tall: Boolean = false,
        /** 矮视口（内容必然可滚动，用来验证折叠态） */
        short: Boolean = false,
        content: @Composable () -> Unit
    ) {
        rule.setContent {
            KtTheme(dark) {
                // 中性背景，不铺任何渐变（设计宪法第二条）
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (dark) DarkColors.base else LightColors.base)
                ) { content() }
            }
        }
        rule.waitForIdle()
        capture(name, tall = tall, short = short)
    }

    /** 只把当前树画进 PNG（setContent 已经做过的用例复用它） */
    private fun capture(name: String, tall: Boolean = false, short: Boolean = false) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val w = if (view.width > 0) view.width else 780
        val h = when {
            tall -> 3500
            short -> 1200
            view.height > 0 -> view.height
            else -> 1688
        }
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("/workspace/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun weekend(on: Boolean) {
        DataStore.saveSettings(DataStore.settings.copy(showWeekend = on))
    }

    // ---------------- v5.4 今日页（师父：iOS 健身） ----------------
    // 固定演示时钟：2026-11-17 20:12（周二晚）→ 下一节 明天 08:30，正好 12 小时 18 分
    private val DEMO_NOW = java.time.LocalDateTime.of(2026, 11, 17, 20, 12)

    @Test
    fun r54TodayDark() =
        shoot("r54-today-dark", true) { AppScaffold(dark = true, tick = 0, now = DEMO_NOW) }

    @Test
    fun r54TodayLight() =
        shoot("r54-today-light", false) { AppScaffold(dark = false, tick = 0, now = DEMO_NOW) }

    @Test
    fun r54TodayDarkTall() = shoot("r54-today-dark-tall", true, tall = true) {
        AppScaffold(dark = true, tick = 0, now = DEMO_NOW)
    }

    @Test
    fun r54TodayLightTall() = shoot("r54-today-light-tall", false, tall = true) {
        AppScaffold(dark = false, tick = 0, now = DEMO_NOW)
    }

    /** 折叠态：初始滚动 120px，验证 22sp 小标题 + 实底导航条 + 内容从其下穿过 */
    @Test
    fun r54TodayCollapsedDark() = shoot("r54-today-collapsed-dark", true, short = true) {
        // 用固定高度容器把视口压到 560dp，内容才真的能滚起来（截图专用）
        Box(Modifier.fillMaxWidth().height(560.dp)) {
            TodayScreen({}, 0, now = DEMO_NOW, initialScroll = 120)
        }
    }

    /** 折叠态：直接画滚动后的导航（截图里验证 22sp 小标题的位置） */
    @Test
    fun r54TodayInClass() = shoot("r54-today-inclass-dark", true) {
        AppScaffold(dark = true, tick = 0, now = java.time.LocalDateTime.of(2026, 11, 17, 8, 42))
    }

    // ---------------- v5.4.1 今日页 v3（果味版，已合入 AppScaffold） ----------------

    @Test
    fun r541TodayDark() = shoot("r541-today-dark", true) {
        AppScaffold(dark = true, tick = 0, now = DEMO_NOW)
    }

    @Test
    fun r541TodayTall() = shoot("r541-today-tall", true, tall = true) {
        AppScaffold(dark = true, tick = 0, now = DEMO_NOW)
    }

    /** 滚到底：验证最后一条 / 无固定时间分组不被 Dock 压住，顶栏小标题不被灵动岛压住 */
    @Test
    fun r541TodayScrolled() {
        rule.setContent {
            KtTheme(true) { AppScaffold(dark = true, tick = 0, now = DEMO_NOW) }
        }
        rule.waitForIdle()
        repeat(4) {
            rule.onRoot().performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        capture("r541-today-scrolled")
    }

    /** 上课中：灵动岛剩余分钟 + 光环 */
    @Test
    fun r541TodayInClass() = shoot("r541-today-inclass", true) {
        AppScaffold(dark = true, tick = 0, now = java.time.LocalDateTime.of(2026, 11, 17, 8, 42))
    }

    /** 课表 tab：灵动岛常驻，检查是否压标题 */
    @Test
    fun r541Schedule() = shoot("r541-schedule", true) {
        AppScaffold(dark = true, tick = 0, startRoute = Route.Week, now = DEMO_NOW)
    }

    @Test
    fun r541Settings() = shoot("r541-settings", true) {
        AppScaffold(dark = true, tick = 0, startRoute = Route.Settings, now = DEMO_NOW)
    }

    @Test
    fun r541TodayLight() = shoot("r541-today-light", false) {
        AppScaffold(dark = false, tick = 0, now = DEMO_NOW)
    }

    @Test
    fun todayDark() = shoot("r52-today-dark", true) { TodayScreen({}, 0) }

    @Test
    fun todayLight() = shoot("r52-today-light", false) { TodayScreen({}, 0) }

    @Test
    fun weekDark5() = shoot("r52-week5-dark", true) { WeekScreen({}) }

    @Test
    fun weekDark7() {
        weekend(true)
        shoot("r52-week7-dark", true) { WeekScreen({}) }
    }

    @Test
    fun weekLight7() {
        weekend(true)
        shoot("r52-week7-light", false) { WeekScreen({}) }
    }

    @Test
    fun shellWeek7() {
        weekend(true)
        shoot("r52-shell-week7", true) { AppScaffold(dark = true, tick = 0, startRoute = Route.Week) }
    }

    @Test
    fun shellMemo() = shoot("r52-shell-memo", true) { AppScaffold(dark = true, tick = 0, startRoute = Route.Memo) }

    @Test
    fun shellSettings() = shoot("r52-shell-settings", true) { AppScaffold(dark = true, tick = 0, startRoute = Route.Settings) }

    @Test
    fun shellImport() = shoot("r52-shell-import", false) { AppScaffold(dark = false, tick = 0, startRoute = Route.Import) }

    @Test
    fun settingsTall() = shoot("r53-settings-tall", true, tall = true) { SettingsScreen({}) }

    @Test
    fun todayTall() = shoot("r53-today-tall", true, tall = true) { TodayScreen({}, 0) }

    @Test
    fun shellLight() = shoot("r52-shell-light", false) { AppScaffold(dark = false, tick = 0) }

    @Test
    fun settingsDark() = shoot("r52-settings-dark", true) { SettingsScreen({}) }

    @Test
    fun settingsLight() = shoot("r52-settings-light", false) { SettingsScreen({}) }

    @Test
    fun appShellDark() = shoot("r52-shell-dark", true) { AppScaffold(dark = true, tick = 0) }

    @Test
    fun editDark() = shoot("r52-edit-dark", true) { CourseEditScreen("c1", 0, 0) {} }

    @Test
    fun editNewDark() = shoot("r52-edit-new-dark", true) { CourseEditScreen("new", 3, 5) {} }

    @Test
    fun statsDark() = shoot("r52-stats-dark", true) { StatsScreen({}, {}) }

    @Test
    fun timelineDark() = shoot("r52-timeline-dark", true) { DayTimelineScreen({}, {}) }

    @Test
    fun aiDark() = shoot("r52-ai-dark", true) { AiScreen({}) }

    @Test
    fun importDark() = shoot("r52-import-dark", true) { ImportScreen({}, {}) }

    @Test
    fun shellNoGlass() {
        DataStore.saveSettings(DataStore.settings.copy(liquidGlass = false))
        shoot("r52-shell-noglass", true) { AppScaffold(dark = true, tick = 0) }
    }

    @Test
    fun memoDark() = shoot("r52-memo-dark", true) { MemoScreen({}) }

    companion object {
        private val DEMO = listOf(
            Course(
                id = "c1", name = "高等数学 B2", teacher = "令狐青云", location = "教一 101",
                day = 2, startSection = 1, endSection = 2, weekStart = 1, weekEnd = 18,
                color = "purple", credit = 4.0
            ),
            Course(
                id = "c7", name = "马克思主义基本原理", teacher = "卢敏华", location = "倬云楼 106",
                day = 2, startSection = 3, endSection = 4, weekStart = 1, weekEnd = 20,
                color = "orange", credit = 3.0
            ),
            Course(
                id = "c3", name = "大学英语 IV", teacher = "王芳", location = "外语楼 302",
                day = 2, startSection = 9, endSection = 10, weekStart = 1, weekEnd = 16,
                color = "blue", credit = 3.0
            ),
            Course(
                id = "c4", name = "数据结构", teacher = "张国栋", location = "教二 204",
                day = 3, startSection = 1, endSection = 4, weekStart = 3, weekEnd = 14,
                parity = 1, color = "green", credit = 4.0
            ),
            Course(
                id = "c5", name = "人工智能导论", teacher = "陈慧", location = "教三 108",
                day = 3, startSection = 7, endSection = 8, weekStart = 1, weekEnd = 20,
                color = "pink", credit = 2.0
            ),
            Course(
                id = "c6", name = "体育 III", teacher = "赵敏", location = "田径场",
                day = 4, startSection = 7, endSection = 8, weekStart = 1, weekEnd = 18,
                color = "teal", credit = 1.0
            ),
            Course(
                id = "c2", name = "Python 程序设计", teacher = "李强", location = "实验楼 405",
                day = 5, startSection = 5, endSection = 6, weekStart = 1, weekEnd = 16,
                color = "blue", credit = 3.0
            ),
            Course(
                id = "c8", name = "形势与政策", teacher = "", location = "",
                day = 0, startSection = 1, endSection = 2, weekStart = 1, weekEnd = 20,
                color = "green"
            )
        )
    }
}
