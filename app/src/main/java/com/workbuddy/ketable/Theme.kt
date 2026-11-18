package com.workbuddy.ketable

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ============================================================
 * 设计令牌 v5.4 · 唯一来源
 * ============================================================
 *
 * 设计宪法（永久有效，后续每一轮都受此约束）：
 *  一、内容即英雄：90% 的画面是排版、留白和内容本身。玻璃只允许出现在
 *      系统级 chrome（Dock、悬浮控件）；卡片一律中性表面色，不自带材质。
 *  二、中性环境：背景永远中性色，禁止彩色光斑 / 氛围渐变 / 彩雾。
 *      色彩只属于内容（课程色、圆点、进度、开关），小剂量高纯度。
 *      【V4 例外，2026-11-18 用户明确要求浅色果味重构：今日页背景允许
 *      彩色柔光极光，因为液态玻璃必须有颜色可折才显形；其余页仍中性。】
 *  三、排版驱动层级：层级只靠字号 / 字重 / 留白，禁止描边、光晕、阴影堆层级。
 *      【V4 例外：今日页 Hero 卡允许一道柔和投影做分离，其余不变。】
 *  四、每页认一个师父：今日→iOS 健身，设置→iOS 设置，课表→iOS 日历，Dock→iOS 26 底栏。
 *  五、先结构后材质：先布局排版，玻璃/动效最后加，且只加在宪法允许的位置。
 *
 * 任何页面都不许再写颜色与字号的字面量，一律从这里取。
 */
data class KtColors(
    /** true = 深色模式 */
    val dark: Boolean,
    /** 页面背景（深 #000000 / 浅 #F2F2F7） */
    val base: Color,
    /** 卡片 / 分组容器（深 #1C1C1E / 浅 #FFFFFF） */
    val elevated: Color,
    /** 高一级表面：控件底、卡片上按下的那一档（深 #2C2C2E） */
    val elevated2: Color,
    /** 再高一级：只用于「落在 elevated2 上的按压态」 */
    val elevated3: Color,
    /** 分隔线：深 rgba(84,84,88,.65) / 浅 rgba(60,60,67,.29) */
    val separator: Color,
    /** 文字梯：主（iOS label） */
    val label: Color,
    /** 次（iOS secondaryLabel） */
    val secondary: Color,
    /** 辅（iOS tertiaryLabel） */
    val tertiary: Color,
    /** 更淡：仅用于极弱提示 / 空态文字 */
    val quaternary: Color,
    /** 全局唯一彩色：紫 #BF5AF2 */
    val accent: Color
) {
    // —— 旧页面（课表/设置/AI/备忘）仍在用这套名字，先留别名，下一轮逐页替换 ——
    val bg: Color get() = base
    val surface: Color get() = elevated
    val ink1: Color get() = label
    val ink2: Color get() = secondary
    val ink3: Color get() = tertiary
    val ink4: Color get() = quaternary
    val hairline: Color get() = separator
    val controlBg: Color get() = elevated2
    val cardTint: Color get() = elevated
    val accentSoft: Color get() = accent.copy(alpha = 0.16f)
    /** Dock / 页头玻璃蒙层：暗 rgba(20,20,26,.50) / 亮 rgba(255,255,255,.70) */
    // V3：深色 Dock 蒙层从 0x2A（16.5% 白）降到 0x1F（12% 白）。
    // 蒙层越厚，背景采样越看不见，玻璃越像实色板。
    // V4.9：浅色 Dock/灵动岛看着像白药片，蒙层再调薄——亮从 70% 白降到 50%，
    // Regular 玻璃蒙层从 55% 白降到 45%，让极光透进来。
    val dockTint: Color get() = if (dark) Color(0x1FFFFFFF) else Color(0x80FFFFFF)
    val glassTint: Color get() = if (dark) Color(0x14FFFFFF) else Color(0x73FFFFFF)
    val glassBorder: Color get() = separator
    val mask: Color get() = Color(0x8C000000)
}

val DarkColors = KtColors(
    dark = true,
    base = Color(0xFF000000),
    elevated = Color(0xFF1C1C1E),
    elevated2 = Color(0xFF2C2C2E),
    elevated3 = Color(0xFF3A3A3C),
    separator = Color(0xA6545458),
    label = Color(0xEBFFFFFF),
    secondary = Color(0x99EBEBF5),
    tertiary = Color(0x66EBEBF5),
    quaternary = Color(0x3DEBEBF5),
    accent = Color(0xFFBF5AF2)
)

val LightColors = KtColors(
    dark = false,
    base = Color(0xFFF2F2F7),
    elevated = Color(0xFFFFFFFF),
    elevated2 = Color(0xFFE9E9EE),
    elevated3 = Color(0xFFDCDCE3),
    separator = Color(0x4A3C3C43),
    label = Color(0xEB000000),
    secondary = Color(0x993C3C43),
    tertiary = Color(0x663C3C43),
    quaternary = Color(0x3D3C3C43),
    accent = Color(0xFFBF5AF2)
)

val LocalKtColors = staticCompositionLocalOf { DarkColors }

/** 液态玻璃总开关（设置 → 运行诊断）。关掉后不跑 RenderEffect，chrome 退回纯色。 */
val LocalGlassOn = staticCompositionLocalOf { true }

// ---------------------------------------------------------------- 字

/** 思源黑体（Noto Sans CJK SC 子集，GB2312 全覆盖，缺字回落到系统同源字体）。 */
val KtSans = FontFamily(
    Font(R.font.kt_sans_regular, FontWeight.Normal),
    Font(R.font.kt_sans_bold, FontWeight.Bold)
)

/**
 * 字号阶梯（照 iOS 大标题 / 正文 / 脚注的比例）。只给字号与字重，
 * 颜色在调用处从 tokens 取 —— 同一字号在深/浅色下是同一个字。
 */
object KtType {
    /** 大标题 34/700，滚动折叠为 22/600 */
    val largeTitle = TextStyle(
        fontFamily = KtSans, fontSize = 34.sp, lineHeight = 41.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp
    )
    /** 折叠后的小标题 / 页面标题 22/600 */
    val navTitle = TextStyle(
        fontFamily = KtSans, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold
    )
    val title = TextStyle(
        fontFamily = KtSans, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold
    )
    /** 卡片标题 17/600 */
    val cardTitle = TextStyle(
        fontFamily = KtSans, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold
    )
    /** 正文 17/400 */
    val body = TextStyle(fontFamily = KtSans, fontSize = 17.sp, lineHeight = 22.sp)
    /** 次要正文 15/400 */
    val subhead = TextStyle(fontFamily = KtSans, fontSize = 15.sp, lineHeight = 20.sp)
    /** 按钮 / 胶囊文字 16/600 */
    val button = TextStyle(
        fontFamily = KtSans, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold
    )
    /** 脚注 / 时间戳 / 分区标题 13/400 */
    val footnote = TextStyle(fontFamily = KtSans, fontSize = 13.sp, lineHeight = 18.sp)
    /** 极小标注 11/400 */
    val caption = TextStyle(fontFamily = KtSans, fontSize = 11.sp, lineHeight = 14.sp)
}

// ---------------------------------------------------------------- 几何

object KtSize {
    /** 页面左右边距 16 */
    val page: Dp = 16.dp
    /** 分组卡片圆角 12，Hero 16 */
    val cardRadius: Dp = 12.dp
    val heroRadius: Dp = 16.dp
    /** 卡片内边距 16 */
    val cardPad: Dp = 16.dp
    /** 列表行高 52（紧凑 44） */
    val rowH: Dp = 52.dp
    val rowCompact: Dp = 44.dp
    /** 入口胶囊高度 44 */
    val pillH: Dp = 44.dp
    /** 行间分隔线左缩进 20 */
    val sepInset: Dp = 20.dp
}

// ---------------------------------------------------------------- 动效

/**
 * 全局只有一条曲线：spring(dampingRatio = .85, stiffness = 300)。
 * 禁止弹跳装饰动效。
 */
object KtMotion {
    const val DAMPING = 0.85f
    const val STIFFNESS = 300f

    fun <T> spring(): SpringSpec<T> = spring(dampingRatio = DAMPING, stiffness = STIFFNESS)
}

// ---------------------------------------------------------------- 课程色板

/**
 * 课程色板（iOS 系统色）。每门课固定映射一色，只用于小面积内容标识：
 * 列表左侧 3px 色条、进度条、周视图色块。绝不用作卡片底或大面积填充。
 */
object KtCourse {
    val blue = Color(0xFF0A84FF)
    val green = Color(0xFF30D158)
    val orange = Color(0xFFFF9F0A)
    val pink = Color(0xFFFF375F)
    val purple = Color(0xFFBF5AF2)
    val teal = Color(0xFF40C8E0)
    val neutral = Color(0xFF8E8E93)

    fun of(name: String): Color = when (name) {
        "blue" -> blue
        "green" -> green
        "orange" -> orange
        "pink" -> pink
        "purple" -> purple
        "teal" -> teal
        else -> neutral
    }
}

// ---------------------------------------------------------------- 主题入口

@Composable
fun KtTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    // tnum：所有数字等宽，倒计时 / 时间戳跳动时不抖
    val base = TextStyle(
        fontFamily = KtSans, color = colors.label, fontFeatureSettings = "tnum"
    )
    CompositionLocalProvider(
        LocalKtColors provides colors,
        LocalGlassOn provides DataStore.settings.liquidGlass,
        LocalTextStyle provides base
    ) {
        MaterialTheme(
            typography = Typography(
                bodyLarge = base,
                bodyMedium = base,
                bodySmall = base,
                titleLarge = base,
                titleMedium = base,
                labelLarge = base,
                labelMedium = base,
                labelSmall = base
            )
        ) {
            content()
        }
    }
}

/** 旧着色器用的明暗系数：0=深空 1=晨雾（旧引擎仍需要） */
fun KtColors.shaderLight(): Float = if (dark) 0f else 1f

// ---------------------------------------------------------------- 交互态

/** 列表项 / 按钮的按压态：背景升一档，没有涟漪。 */
@Composable
fun rememberPressed(interaction: MutableInteractionSource): Boolean {
    val pressed by interaction.collectIsPressedAsState()
    return pressed
}

@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** 落在 elevated 上的行 → 按下取 elevated2；落在 base 上的控件 → 按下取 elevated2 */
@Composable
fun pressBackground(pressed: Boolean, resting: Color = Color.Transparent): Color {
    val c = LocalKtColors.current
    return if (pressed) c.elevated2 else resting
}
