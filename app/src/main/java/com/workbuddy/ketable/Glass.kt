package com.workbuddy.ketable

import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

/**
 * 玻璃三级制。
 *
 * - [GlassTier.Lens]     Dock 选中胶囊、悬浮按钮：模糊 + 折射 + 色散 + 顶部高光。
 * - [GlassTier.Regular]  页头栏、Dock 长条：只模糊 + 蒙层。
 * - 平面（[FlatCard] / [FrostedCard]）正文、课程卡、hero。
 *
 * **一条铁律（v5.3.1 的教训）**：玻璃元素必须是「被录制内容」的**兄弟**，不能是它的子节点。
 * 因为录制时会给 backdrop 那一层 `record`，如果子节点里再 `drawLayer(同一个 layer)`，
 * 等于让 RenderNode 在录制过程中画自己 —— 真机上 hwui 会直接 abort，表现为「一开玻璃就闪退、
 * 且没有 Java 堆栈」。现在 [BackdropHost] 会在录制内容里把 `LocalBackdrop` 置空，
 * 从结构上堵死这种写法。
 */
enum class GlassTier { Lens, Regular }

/**
 * V3 参数总表 —— 以后做别的 App，直接照抄这组数就是同一套玻璃。
 *
 * 对照 iOS 26 的关键数值：
 * - 底栏 Regular 模糊 20dp（iOS 约 20-24dp），不是 V2 的 11dp
 * - 玻璃泡 Lens 折射高度 34dp / 折射量 46dp，边缘弯曲要一眼看得出
 * - 蒙层要薄（Lens 16% 白），高光要细而亮（宽 1.6dp / 模糊 3dp）
 * - 镜内放大 1.12x，iOS 玻璃泡里的内容就是比外面大一圈
 */
object LiquidGlassV3 {
    val lensBlur = 16.dp
    val lensRefractionHeight = 34.dp
    val lensRefractionAmount = 46.dp
    val regularBlur = 20.dp
    const val lensMagnification = 1.12f
    const val lensTintAlphaDark = 0.16f
}

/** 运行环境是否支持真玻璃（RenderEffect 需要 API 31+；Robolectric 软件渲染不支持） */
object GlassCapability {
    val isRobolectric: Boolean =
        Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
                Build.MODEL.contains("robolectric", ignoreCase = true) ||
                Build.HARDWARE.contains("robolectric", ignoreCase = true)

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isRobolectric
}

/** 采样源。注意：它在「被录制的内容」里会被显式置为 null，见 [BackdropHost] */
val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 玻璃等级：2=完整（AGSL）1=基础（无 AGSL）0=关 */
val LocalGlassLevel = compositionLocalOf { 2 }

/**
 * 玻璃运行时状态：某一帧的着色器绘制抛异常（AGSL 编译失败 / 驱动不支持）时，
 * 立刻永久退回平面渲染，绝不让异常把 App 带崩。
 */
object GlassRuntime {
    private const val TAG = "GlassRuntime"
    var broken by mutableStateOf(false)
        private set
    var lastError: String? = null
        private set

    private var markedFirstDraw = false

    fun onDrawFail(t: Throwable) {
        lastError = t.javaClass.simpleName + ": " + (t.message ?: "")
        if (!broken) {
            broken = true
            Log.e(TAG, "液态玻璃渲染失败，已退回平面渲染", t)
        }
        CrashGuard.mark("glass_fail:" + lastError)
    }

    /** 第一帧玻璃画成功：崩溃日志里就能看出「玻璃至少画出来过」 */
    fun onDrawOk() {
        if (markedFirstDraw) return
        markedFirstDraw = true
        CrashGuard.mark("glass_ok")
    }

    fun reset() {
        broken = false; lastError = null; markedFirstDraw = false
    }
}

/**
 * 背景层录制器。
 *
 * [backdrop] 由调用方（AppScaffold）创建并同时提供给「玻璃元素」，
 * 这里只负责把页面内容录进这一层。**录制内容里 `LocalBackdrop` 被置空**，
 * 所以页面内部即使误用 [GlassSurface]，也只会退化成平面卡，不会出现 RenderNode 自绘。
 */
@Composable
fun BackdropHost(backdrop: LayerBackdrop?, content: @Composable BoxScope.() -> Unit) {
    if (backdrop == null) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            CompositionLocalProvider(LocalBackdrop provides null) { content() }
        }
    }
}

@Composable
fun GlassSurface(
    tier: GlassTier,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    /** Lens 级镜内放大倍率；iOS 的玻璃泡会把背景内容稍微放大 */
    magnification: Float = 1.12f,
    /** 覆盖蒙层色（Dock / 页头用更实的 rgba(20,20,26,.5) / rgba(255,255,255,.55)） */
    tint: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalKtColors.current
    val backdrop = LocalBackdrop.current
    val glassOn = LocalGlassOn.current
    val level = LocalGlassLevel.current

    if (backdrop == null || !glassOn || level <= 0 || GlassRuntime.broken) {
        // 真玻璃不可用（旧机 / 关闭开关 / 录制内容内部 / 软件渲染截图）：退回纯色面。
        val flat = when {
            tier == GlassTier.Lens && colors.dark -> Color(0xFF232030)
            tier == GlassTier.Lens -> Color(0xFFFFFFFF)
            colors.dark -> Color(0xFF17171E)
            else -> Color(0xF2FFFFFF)
        }
        Box(
            modifier
                .clip(shape)
                .background(flat)
                .border(
                    0.8.dp,
                    if (tier == GlassTier.Lens) colors.accent.copy(alpha = 0.35f) else colors.glassBorder,
                    shape
                ),
            content = content
        )
        return
    }

    val tintColor = tint ?: if (tier == GlassTier.Lens) {
        // V3：Lens 蒙层从 0.28 降到 0.16。之前蒙层太厚，直接把折射盖住了，
        // 玻璃泡看起来就是一块实色圆片。
        if (colors.dark) colors.dockTint.copy(alpha = 0.16f) else colors.dockTint.copy(alpha = 0.20f)
    } else {
        colors.glassTint
    }

    val effects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = {
        try {
            when {
                // 完整：AGSL 折射 + 深度 + 色散（最挑 GPU，等级掉到 1 就不用它）
                tier == GlassTier.Lens && level >= 2 -> {
                    // V3 · 贴近 iOS 26：模糊翻倍，折射带加深加宽。
                    // iOS 的玻璃感主要来自边缘那一圈“把背景拽弯”的折射，
                    // 之前 refractionHeight 16 / Amount 22 太浅，边缘几乎看不出弯曲。
                    blur(16.dp.toPx())
                    lens(
                        refractionHeight = 34.dp.toPx(),
                        refractionAmount = 46.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                    colorControls(saturation = 1.18f, brightness = if (colors.dark) 0.03f else 0.02f)
                }
                // 基础：只模糊。Lens 靠「放大 + 高光 + 边缘色散描边」冒充折射
                tier == GlassTier.Lens -> {
                    blur(12.dp.toPx())
                    colorControls(saturation = 1.25f, brightness = if (colors.dark) 0.02f else 0.01f)
                }
                else -> {
                    // Regular：V3 把 V2 收回去的模糊还回来。
                    // V2 从 16dp 收到 11dp 是为了让 Dock 后面的字更清楚，
                    // 但代价是玻璃直接变回了“灰色板子”。
                    // iOS 26 的底栏模糊在 20dp 上下，字是靠蒙层 + 对比度保证可读，
                    // 不是靠把模糊调小。
                    blur(20.dp.toPx())
                    colorControls(saturation = 1.22f, brightness = if (colors.dark) 0.02f else 0.025f)
                }
            }
        } catch (t: Throwable) {
            // 效果构造失败：这一帧只有蒙层，记录但不崩
            runCatching { GlassRuntime.onDrawFail(t) }
        }
    }

    val fallback = if (colors.dark) Color(0xFF16161C) else Color(0xF7F9FAFC)

    Box(
        modifier
            .clip(shape)
            // 最外层兜住 drawBackdrop 抛出的所有异常；一旦失败立刻永久退化
            .drawWithContent {
                try {
                    drawContent()
                    GlassRuntime.onDrawOk()
                } catch (t: Throwable) {
                    GlassRuntime.onDrawFail(t)
                    drawRoundRect(
                        color = fallback,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                }
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = effects,
                highlight = if (tier == GlassTier.Lens) {
                    {
                        // V3：高光从“ diffuse 的雾”改成“细而亮的边”。
                        // iOS 的玻璃边缘是一条很细、很亮的线，不是糊开的一团。
                        // blurRadius 7dp -> 3dp，falloff 1.35 -> 0.85，线会收紧、变亮。
                        Highlight(
                            width = 1.6.dp,
                            blurRadius = 3.dp,
                            alpha = 1f,
                            style = HighlightStyle.Default(intensity = 1f, angle = 90f, falloff = 0.85f)
                        )
                    }
                } else null,
                shadow = null,
                onDrawBackdrop = if (tier == GlassTier.Lens) {
                    { drawBackdrop ->
                        try {
                            scale(magnification, magnification) { drawBackdrop() }
                        } catch (_: Throwable) {
                            drawBackdrop()
                        }
                    }
                } else {
                    { drawBackdrop -> drawBackdrop() }
                },
                onDrawSurface = { drawRect(tintColor) },
                // 前表面高光：Regular 是柔和的上亮下暗反射；低等级 Lens 保留轻微 RGB 色散边。
                onDrawFront = when {
                    // Regular：可读的背景透射 + 顶部镜面反射 + 下沿暗边。
                    tier == GlassTier.Regular -> {
                        {
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color(0x40FFFFFF),
                                    0.07f to Color(0x1AFFFFFF),
                                    0.38f to Color(0x05FFFFFF),
                                    0.82f to Color(0x02000000),
                                    1f to Color(0x18000000)
                                )
                            )
                        }
                    }
                    // 完整 Lens：在 AGSL 折射层上增加轻微表面反射，避免只像一块放大的模糊区域。
                    tier == GlassTier.Lens && level >= 2 -> {
                        {
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color(0x38FFFFFF),
                                    0.16f to Color(0x0EFFFFFF),
                                    0.52f to Color(0x00FFFFFF),
                                    0.88f to Color(0x04000000),
                                    1f to Color(0x16FFFFFF)
                                )
                            )
                        }
                    }
                    tier == GlassTier.Lens && level < 2 -> {
                        {
                            val path = Path()
                            path.addOutline(shape.createOutline(size, layoutDirection, this))
                            val w = 0.7.dp.toPx()
                            translate(left = -w) {
                                drawPath(path, Color(0x1AFF453A), style = Stroke(1.4f))
                            }
                            translate(left = w) {
                                drawPath(path, Color(0x1A0A84FF), style = Stroke(1.4f))
                            }
                        }
                    }
                    else -> null
                }
            )
            .border(
                width = if (tier == GlassTier.Lens) 1.dp else 0.7.dp,
                color = if (tier == GlassTier.Lens) {
                    if (colors.dark) Color(0x8CFFFFFF) else Color(0x80FFFFFF)
                } else {
                    if (colors.dark) Color(0x66FFFFFF) else Color(0x70FFFFFF)
                },
                shape = shape
            ),
        content = content
    )
}

/** 平面级：正文 / 列表的默认容器，不跑任何 effect */
@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    color: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalKtColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(color ?: colors.surface),
        content = content
    )
}

/**
 * 磨砂面（hero 用）：看起来像玻璃，但**不采样背景**。
 *
 * hero 在滚动流里，如果让它采样 backdrop layer，就会变成「录制中的节点去画正在录制的 layer」。
 * 但它后面本来就是纯色背景，采样也没有任何视觉收益 —— 所以用渐变色面 + 顶部 1px 内高光
 * 还原磨砂质感，成本为零、风险为零。
 */
@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalKtColors.current
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(
                if (colors.dark) {
                    Brush.verticalGradient(0f to Color(0xFF1B1B23), 1f to Color(0xFF121218))
                } else {
                    Brush.verticalGradient(0f to Color(0xFFFFFFFF), 1f to Color(0xFFFAFAFC))
                }
            )
            .drawWithContent {
                drawContent()
                // 顶部内高光：一条 1px 的白线，两端各让 18px，模拟玻璃上沿
                val inset = 18.dp.toPx()
                drawLine(
                    color = if (colors.dark) Color(0x26FFFFFF) else Color(0xB3FFFFFF),
                    start = Offset(inset, 0.5f),
                    end = Offset(size.width - inset, 0.5f),
                    strokeWidth = 1f
                )
            }
            .border(0.8.dp, colors.hairline, shape),
        content = content
    )
}

/**
 * v5.1 的 GlassCard 兼容入口：v5.2 起默认降级为平面卡。
 * 需要玻璃的地方显式调用 [GlassSurface]（而且必须在 BackdropHost 之外）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    tiltX: Float = 0f,
    tiltY: Float = 0f,
    screen: androidx.compose.ui.unit.IntSize = androidx.compose.ui.unit.IntSize.Zero,
    light: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    FlatCard(modifier = modifier, corner = corner, content = content)
}

/** 细描边（平面卡上的 1px 分隔，暗 8% / 亮 8%） */
@Composable
fun Modifier.hairlineBorder(corner: Dp = 18.dp): Modifier {
    val colors = LocalKtColors.current
    return this
        .clip(RoundedCornerShape(corner))
        .border(0.7.dp, colors.hairline, RoundedCornerShape(corner))
}

/** 玻璃内部的一层柔和高光（Lens 用），只在真玻璃上叠 */
@Composable
fun Modifier.innerTopHighlight(corner: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this.border(0.7.dp, Color(0x1FFFFFFF), shape)
}

/**
 * Dock 选中态「玻璃泡」：Lens 级。
 * 完整等级 = 折射 + 色散 + 顶部高光；基础等级 = 放大 + 模糊 + 色散描边。
 * 参数集中在这里，调手感只改这一处。
 */
@Composable
fun DockGlassBubble(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.CircleShape
) {
    GlassSurface(
        tier = GlassTier.Lens,
        shape = shape,
        magnification = 1.07f,
        modifier = modifier
    ) {}
}
