package com.workbuddy.ketable

/**
 * ═══════════════════════════════════════════════════════════════
 * 今日页 · Liquid Glass 视觉改造 v1
 * 依据:today.html 的逐像素定稿,设计数值一律保持原样,不许"优化"。
 *
 * V4 浅色重构 (2026-11-18,用户定方向:浅色果味,像 iOS 26 默认那样):
 *  V3 深色版在真机上看着像"黑底贴灰板",用户要求整体重构。
 *  本轮只改视觉层,不动数据/逻辑/玻璃引擎:背景换成彩色柔光极光
 *  (玻璃终于有颜色可折了),卡片换成透白,文字换成 iOS 浅色字梯,
 *  Dock/灵动岛/FAB 跟着浅色走。旧设计宪法"背景永远中性"一条,
 *  在今日页被这次重构明确废止,见 Theme.kt 注释。
 * ═══════════════════════════════════════════════════════════════
 *
 * 相对设计稿源码,本文件只做了「接入本工程」的适配:
 *   1. Course 字段换成工程的模型(location / startSection..endSection / color: String)
 *   2. 周胶囊不再套 GlassCard:本页运行在 BackdropHost 录制层内,
 *      LocalBackdrop 为 null,玻璃采样不到东西,用 10% 白蒙层等效(与设计稿的
 *      GlassCard 内层底色一致)
 *   3. 折叠小标题下移让出灵动岛(否则会被常驻胶囊压住)
 *   4. Hero 右上时间胶囊的日期前缀按真实 nextDate 算(今天/明天/周X)
 *   5. 「无固定时间课程」接真实数据;入口胶囊接真实路由
 * ═══════════════════════════════════════════════════════════════
 */

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DOW_CN = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 灵动岛占位高度：胶囊 8dp 上边距 + 38dp 本体 + 6dp 间隙 */
val ISLAND_BAND = 52.dp

/** ── 设计 Tokens（v4 浅色果味定稿值，全页禁止散落硬编码） ───────
 * V4:底不再是纯黑,是极淡的薰衣草灰;卡片是透白(85-90%),让底下的
 * 极光微微透出来;文字是 iOS 浅色字梯(主 #000000 / 次 #3C3C43)。
 * 彩色只在背景极光、课程色条、进度环里出现,绝不大面积铺色。 */
object V3 {
    val Bg = Color(0xFFF5F4FA)
    val Card = Color(0xD9FFFFFF)          // 分组列表:85% 白
    val Hero = Color(0xE8FFFFFF)          // Hero 卡:91% 白
    val Card2 = Color(0xCCFFFFFF)         // 入口胶囊:80% 白
    val CardActive = Color(0xFFF2F2F7)
    val Sep = Color(0x14000000)           // rgba(0,0,0,.08)
    val T1 = Color(0xE0000000)            // 主文字
    val T2 = Color(0x993C3C43)            // 次文字
    val T3 = Color(0x663C3C43)            // 辅文字
    val Accent = Color(0xFFBF5AF2)
    val Accent2 = Color(0xFFE0A6FF)
    // iOS 系统课程色板
    val Blue = Color(0xFF0A84FF); val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A); val Pink = Color(0xFFFF375F)
    val Purple = Color(0xFFBF5AF2); val Teal = Color(0xFF40C8E0)
    const val PAGE_HPAD = 20
    val RHero = 28.dp; val RGroup = 20.dp; val RChip = 16.dp; val RIsland = 22.dp
    /** 数字等宽（对应 CSS font-variant-numeric: tabular-nums） */
    val NumStyle = TextStyle(fontFeatureSettings = "tnum")
}

/** 课程行的右端标签：下课时间（与设计稿一致） */
private fun endLabel(c: Course): String =
    if (c.day == 0) "待定"
    else K.SECTION_TIMES[(c.endSection - 1).coerceIn(0, 9)].second

/** 课程行副标题：时间 · 地点 · 教师 */
private fun rowMeta(c: Course): String = buildList {
    add(Sch.timeLabel(c))
    add(c.location.ifBlank { "地点待定" })
    if (c.teacher.isNotBlank()) add(c.teacher)
}.distinct().joinToString(" · ")

/** 极光背景（V4：玻璃需要“信号”，信号直接做进背景里）。
 *
 * V3 的 8% 紫 + 6% 青在纯黑上根本看不见，玻璃折了个寂寞。
 * V4 换成 iOS 26 壁纸式的彩色柔光：极淡的薰衣草灰打底，右上薰衣草紫、
 * 左中天空蓝、右下薄荷绿、左下蜜桃粉，四团柔光叠在一起。
 * 颜色看着明显，但都是浅粉彩，不抢内容；Dock / 灵动岛 / FAB
 * 从它上面滑过时，终于有颜色可以折射和模糊了。
 * 如果以后觉得太花，把每团颜色的 alpha 往下调，别直接删掉。 */
@Composable
fun NeutralBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(V3.Bg).drawBehind {
        // 右上：薰衣草紫
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFDCD4FF), Color.Transparent),
                center = Offset(size.width * .88f, size.height * -.06f),
                radius = size.width * 1.05f
            )
        )
        // 左中：天空蓝
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFBFE0FF), Color.Transparent),
                center = Offset(size.width * -.02f, size.height * .38f),
                radius = size.width * 0.95f
            )
        )
        // 右下：薄荷绿
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFC2F0DC), Color.Transparent),
                center = Offset(size.width * .95f, size.height * .92f),
                radius = size.width * 0.9f
            )
        )
        // 左下：蜜桃粉
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFDCC8), Color.Transparent),
                center = Offset(size.width * .02f, size.height * 1.02f),
                radius = size.width * 0.85f
            )
        )
    })
}

/** ── 今日页 v3 主入口 ───────────────────────────────────────── */
@Composable
fun TodayScreenV3(
    /** 下一节课（开始时刻, 课程）；null = 14 天内都没课 */
    next: Pair<LocalDateTime, Course>?,
    /** 今天的课（已按节次排序） */
    todayCourses: List<Course>,
    /** 无固定时间课程（day == 0） */
    undated: List<Course>,
    weekNo: Int,
    today: LocalDate,
    now: LocalDateTime,
    onChip: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    // 滚过 ~90px 触发大标题折叠（Apple 的 spring 阈值手感）
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 90
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 96.dp, start = V3.PAGE_HPAD.dp, end = V3.PAGE_HPAD.dp,
                bottom = 140.dp   // Dock(66)+间距,内容禁止被 Dock 遮挡
            )
        ) {
            item { BigTitleHeader(today, weekNo) }
            item {
                Spacer(Modifier.height(10.dp))
                HeroCard(next, now)
            }
            item { EntryChips(onChip) }
            if (todayCourses.isNotEmpty()) {
                item { SectionHeader("今日课程(共 ${todayCourses.size} 节)") }
                item { CourseGroup(todayCourses.map { it to endLabel(it) }) }
            }
            item { SectionHeader("无固定时间课程") }
            item { CourseGroup(undated.map { it to endLabel(it) }) }
        }

        // 折叠后顶栏：渐隐底 + 小标题（让出灵动岛）
        NavOverlay(collapsed, Modifier.align(Alignment.TopCenter))
    }
}

/** ── 大标题导航（未折叠态，随内容滚走） ─────────────────────── */
@Composable
private fun BigTitleHeader(today: LocalDate, weekNo: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                "今日", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp, color = V3.T1, lineHeight = 38.sp
            )
            Text(
                "${today.year}年${today.monthValue}月${today.dayOfMonth}日 " +
                    DOW_CN[today.dayOfWeek.value - 1],
                fontSize = 13.5.sp, color = V3.T3, letterSpacing = .1.sp,
                modifier = Modifier.padding(top = 7.dp)
            )
        }
        WeekChip(weekNo)
    }
}

@Composable
private fun WeekChip(weekNo: Int) {
    // V4：透白胶囊，落在极光背景上
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xA6FFFFFF))
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(V3.Accent)
        )
        Spacer(Modifier.width(6.dp))
        Text("第 $weekNo 周", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = V3.T2)
    }
}

/** ── 中性 Hero 内容卡（玻璃只用于系统级悬浮控件） ─────────── */
@Composable
private fun HeroCard(next: Pair<LocalDateTime, Course>?, now: LocalDateTime) {
    val hours: Long
    val minutes: Long
    var courseName = "今天没有更多课程"
    var meta = "好好休息"
    var statusText = "自由时间"
    var whenText: String? = null
    if (next != null) {
        val dur = Duration.between(now, next.first)
        hours = maxOf(0, dur.toHours())
        minutes = maxOf(0, dur.toMinutes() % 60)
        courseName = next.second.name
        meta = buildList {
            if (next.second.location.isNotBlank()) add(next.second.location)
            if (next.second.teacher.isNotBlank()) add(next.second.teacher)
            add(Sch.timeLabel(next.second))
        }.distinct().joinToString(" · ")
        statusText = "下一节"
        // 日期前缀按真实日期算：今天 / 明天 / 周X（设计稿里写死「明天」只对演示数据成立）
        val d = next.first.toLocalDate()
        val prefix = when (d) {
            now.toLocalDate() -> "今天"
            now.toLocalDate().plusDays(1) -> "明天"
            else -> DOW_CN[d.dayOfWeek.value - 1]
        }
        whenText = prefix + " " + next.first.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else { hours = 0; minutes = 0 }

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(V3.RHero))
            .clip(RoundedCornerShape(V3.RHero))
            // V4：Hero 是透白内容卡，不伪装成玻璃：91% 白 + 柔和投影，
            // 上沿留一道细高光。真正的液态玻璃只留给 Dock / FAB / 灵动岛。
            .background(V3.Hero)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFFFFFFFF),
                        0.18f to Color.Transparent,
                        1f to Color(0x05000000)
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x80FFFFFF), Color.Transparent),
                        center = Offset(size.width * .92f, 0f),
                        radius = size.width * .72f
                    )
                )
                drawLine(
                    color = Color(0xB3FFFFFF),
                    start = Offset(22.dp.toPx(), 0.6f),
                    end = Offset(size.width - 22.dp.toPx(), 0.6f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = V3.Accent, letterSpacing = .1.sp,
                    modifier = Modifier.weight(1f)
                )
                if (whenText != null) {
                    Text(
                        whenText, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        color = V3.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(V3.Accent.copy(alpha = .12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$hours", fontSize = 48.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp, color = V3.T1,
                        style = V3.NumStyle, lineHeight = 44.sp
                    )
                    Text(
                        " 小时 ", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = V3.T2
                    )
                    Text(
                        "$minutes", fontSize = 48.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp, color = V3.T1,
                        style = V3.NumStyle, lineHeight = 44.sp
                    )
                    Text(
                        " 分", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = V3.T2
                    )
                }
                Spacer(Modifier.weight(1f))
                RingWithLabel(dayFraction(now), Modifier.size(92.dp))
            }
            Text(
                courseName, fontSize = 17.5.sp, fontWeight = FontWeight.SemiBold,
                color = V3.T1, letterSpacing = (-0.2).sp,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                meta, fontSize = 14.5.sp, color = V3.T2,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            TrackBar(0.64f, Modifier.padding(top = 18.dp))
        }
    }
}

/** 一天（7:00–23:00）已流逝比例，光环与轨道共用 */
private fun dayFraction(now: LocalDateTime): Float {
    val start = LocalTime.of(7, 0).toSecondOfDay()
    val end = LocalTime.of(23, 0).toSecondOfDay()
    val cur = now.toLocalTime().toSecondOfDay()
    return ((cur - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

/** ── 渐变光环（签名元素）：sweep 渐变 + 辉光 + 底轨 ─────────── */
@Composable
private fun RingProgress(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 5.dp.toPx()
        val inset = stroke
        val arc = Size(size.width - inset * 2, size.height - inset * 2)
        val tl = Offset(inset, inset)
        val sweep = 360f * fraction
        drawIntoCanvas { c ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                this.strokeWidth = stroke
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            // 辉光底层
            paint.maskFilter = android.graphics.BlurMaskFilter(
                7f, android.graphics.BlurMaskFilter.Blur.NORMAL
            )
            paint.shader = android.graphics.SweepGradient(
                size.width / 2, size.height / 2,
                intArrayOf(V3.Accent2.toArgb(), V3.Accent.toArgb(), V3.Blue.toArgb(), V3.Accent2.toArgb()),
                floatArrayOf(0f, 0.45f, 1f, 1f)
            )
            c.nativeCanvas.drawArc(
                tl.x, tl.y, tl.x + arc.width, tl.y + arc.height,
                -90f, sweep, false, paint
            )
            // 清晰顶层
            paint.maskFilter = null
            paint.alpha = 255
            c.nativeCanvas.drawArc(
                tl.x, tl.y, tl.x + arc.width, tl.y + arc.height,
                -90f, sweep, false, paint
            )
        }
        // 底轨（未走部分）
        drawArc(
            color = Color.Black.copy(alpha = .10f),
            startAngle = -90f + sweep, sweepAngle = 360f - sweep, useCenter = false,
            topLeft = tl, size = arc, style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

/** 光环 + 中心标签（拆开是为了 Canvas 不裁切辉光） */
@Composable
private fun RingWithLabel(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier) {
        RingProgress(fraction, Modifier.matchParentSize())
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${(fraction * 100).toInt()}%", fontSize = 20.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, color = V3.T1
            )
            Text("今日", fontSize = 9.5.sp, color = V3.T2, letterSpacing = .4.sp)
        }
    }
}

/** ── 双段进度轨（已过灰 / 剩余紫白渐变） ─────────────────────── */
@Composable
private fun TrackBar(fraction: Float, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .weight(fraction.coerceAtLeast(0.02f))
                .fillMaxSize()
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = .10f))
        )
        Box(
            Modifier
                .weight((1f - fraction).coerceAtLeast(0.02f))
                .fillMaxSize()
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFF2E4FF), V3.Accent)))
        )
    }
}

/** ── 入口胶囊（学习数据 / 时间轴） ───────────────────────────── */
@Composable
private fun EntryChips(onChip: (String) -> Unit) {
    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("学习数据" to V3IconKind.Bars, "时间轴" to V3IconKind.Clock).forEach { (label, icon) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(V3.RChip))
                    .background(V3.Card2)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onChip(label) }
            ) {
                V3Icon(icon, Modifier.size(17.dp), V3.T1)
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = V3.T1)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = V3.T3,
        letterSpacing = .3.sp,
        modifier = Modifier.padding(top = 26.dp, start = 4.dp, bottom = 9.dp)
    )
}

/** ── 课程分组列表（iOS 分组样式，无 chevron） ─────────────────── */
@Composable
private fun CourseGroup(rows: List<Pair<Course, String>>) {
    if (rows.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(V3.RGroup))
                .background(V3.Card).padding(vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) { Text("暂无课程", fontSize = 14.sp, color = V3.T3) }
        return
    }
    Column(
        Modifier.clip(RoundedCornerShape(V3.RGroup)).background(V3.Card)
    ) {
        rows.forEachIndexed { i, (c, tail) ->
            if (i > 0) Box(
                Modifier.padding(start = 48.dp).height(1.dp).fillMaxWidth().background(V3.Sep)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Box(
                    Modifier.padding(end = 15.dp).width(3.dp).height(30.dp)
                        .clip(RoundedCornerShape(2.dp)).background(K.dotColor(c.color))
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        c.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = V3.T1,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        rowMeta(c),
                        fontSize = 14.5.sp, color = V3.T2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(tail, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = V3.T3)
            }
        }
    }
}

/** ── 灵动岛：顶层系统胶囊（下一节课 + 进度 + 倒计时） ─────────── */
@Composable
fun IslandBar(next: Pair<LocalDateTime, Course>?, now: LocalDateTime, modifier: Modifier = Modifier) {
    if (next == null) return
    val startAt = next.first
    val endMin = Sch.courseEndMin(next.second)
    val endAt = startAt.with(LocalTime.of(endMin / 60, endMin % 60))
    val total = Duration.between(now, endAt).toMinutes().coerceAtLeast(1)
    val left = Duration.between(now, startAt).toMinutes().coerceIn(0, total)
    val frac = 1f - left.toFloat() / total
    // V3：灵动岛之前是 72% 不透明的实色黑条（0xB8），根本不是玻璃。
    // 它在 BackdropHost 之外，LocalBackdrop 是有的，所以可以直接用真玻璃。
    GlassSurface(
        tier = GlassTier.Regular,
        shape = RoundedCornerShape(V3.RIsland),
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(V3.Accent, V3.Blue))),
            contentAlignment = Alignment.Center
        ) { V3Icon(V3IconKind.Clock, Modifier.size(12.dp), Color.White) }
        Spacer(Modifier.width(9.dp))
        Text(
            next.second.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = V3.T1,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)
        )
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier.width(38.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = .12f))
        ) {
            Box(
                Modifier.fillMaxWidth(frac).fillMaxSize().clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(V3.Accent, V3.Accent2)))
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            "$left 分钟", fontSize = 12.sp, color = V3.T2,
            style = V3.NumStyle
        )
    }
    }
}

/** 折叠顶栏（展开时隐形；折叠时半透明渐隐背景 + 小标题） */
@Composable
private fun NavOverlay(collapsed: Boolean, modifier: Modifier = Modifier) {
    val bgAlpha by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f, label = "navbg"
    )
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (bgAlpha > 0.01f) {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(V3.Bg.copy(alpha = .74f * bgAlpha), Color.Transparent),
                            endY = size.height + 60f
                        )
                    )
                }
            }
            .statusBarsPadding()
            // 让出常驻的灵动岛，否则小标题会被胶囊压住
            .padding(start = 16.dp, end = 16.dp, top = ISLAND_BAND, bottom = 0.dp)
    ) {
        Text(
            "今日", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp, color = V3.T1.copy(alpha = bgAlpha)
        )
    }
}

/** ── SF 风单色线性图标（Canvas 路径，无第三方依赖） ───────────── */
private enum class V3IconKind { Bars, Clock, Plus }

@Composable
private fun V3Icon(kind: V3IconKind, modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.8f * (w / 24f)
            strokeCap = android.graphics.Paint.Cap.ROUND
            color = tint.toArgb()
        }
        drawIntoCanvas { c ->
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                c.nativeCanvas.drawLine(x1, y1, x2, y2, p)
            when (kind) {
                V3IconKind.Bars -> {
                    val pts = listOf(5f to 20f, 12f to 4f, 19f to 14f)
                    pts.forEach { (x, y) ->
                        line(x, y, x, 20f * (h / 24f))
                    }
                }
                V3IconKind.Clock -> {
                    c.nativeCanvas.drawCircle(w / 2, h / 2, w * .35f, p)
                    line(w / 2, h * .30f, w / 2, h / 2)
                    line(w / 2, h / 2, w * .62f, h * .58f)
                }
                V3IconKind.Plus -> {
                    line(w / 2, h * .2f, w / 2, h * .8f)
                    line(w * .2f, h / 2, w * .8f, h / 2)
                }
            }
        }
    }
}
