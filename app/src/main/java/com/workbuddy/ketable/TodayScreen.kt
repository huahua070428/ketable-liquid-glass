package com.workbuddy.ketable

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * ============================================================
 * 今日页 · 师父：iOS 健身（摘要页）
 * ============================================================
 *
 * 结构（自上而下，精确到像素）：
 *  1. 导航区：大标题「今日」34/700，滚动折叠为 22/600 居左小标题（spring 300ms）；
 *     副标题一行 13/次要灰。
 *  2. Hero 卡「下一节课」：elevated 面、圆角 16、内边距 20，纯排版，无图标无玻璃。
 *  3. 入口行：两个等宽胶囊，44 高，elevated2 面，无边框。
 *  4. 今日课程：elevated 容器、圆角 12、行高 52、左侧 3×20 课程色条、分隔线缩进 20。
 *  5. 无固定时间课程：同上，右侧「待定」。
 *  6. 底部安全区：Dock 高度 + 16。
 *
 * 颜色与字号一律来自 Theme.kt 的 tokens，本文件不允许出现颜色/字号字面量。
 */

/** 大标题导航区高度（不含状态栏） */
private val NAV_BAND = 92.dp

/** 折叠后导航区高度（iOS 的 inline 高度） */
private const val NAV_COLLAPSED_H = 56f

@Composable
fun TodayScreen(
    onNav: (Route) -> Unit,
    tick: Int,
    /** 时钟可注入：截图与单测需要可复现的「此刻」 */
    now: LocalDateTime = LocalDateTime.now(),
    /** 初始滚动位置（恢复现场 / 截图验证折叠态） */
    initialScroll: Int = 0
) {
    val settings = DataStore.settings
    val courses = DataStore.courses
    val memos = DataStore.memos

    // tick 只用来让 Compose 知道「该重算时间了」
    val today = now.toLocalDate()
    val wk = Sch.weekNo(today, settings)
    val st = Sch.todayStatus(now, courses, settings)
    val dueToday = memos.filter { !it.done && it.dueDate == today.toString() }
    val untimed = courses.filter { it.day == 0 }

    // 滚动 → 大标题折叠。全程只允许这一条弹簧曲线。
    // 用 Animatable 而不是 animateFloatAsState：初值可以跟着「恢复现场」一起定，
    // 从列表中途返回时不会先闪一帧大标题。
    val scroll = rememberScrollState(initialScroll)
    // derivedStateOf：滚动到 10px 才翻一次布尔，不要把每个像素都变成一次重组
    val collapsed by remember { derivedStateOf { scroll.value > 10 } }
    val collapse = remember { Animatable(if (initialScroll > 10) 1f else 0f) }
    LaunchedEffect(collapsed) {
        collapse.animateTo(if (collapsed) 1f else 0f, KtMotion.spring())
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(
                    start = KtSize.page,
                    end = KtSize.page,
                    top = statusTop + NAV_BAND + 8.dp,
                    // 底部安全区 = Dock 高度 + 16
                    bottom = DOCK_H.dp + 16.dp + navBottom
                )
        ) {
            Hero(st = st, now = now)

            Spacer(Modifier.height(12.dp))
            EntryRow(onNav)

            SectionTitle("今日课程（${st.list.size} 节）")
            Group {
                if (st.list.isEmpty()) {
                    EmptyRow("今天没课，好好休息")
                } else {
                    st.list.forEachIndexed { i, course ->
                        if (i > 0) Hairline(KtSize.sepInset)
                        CourseRow(course, live = st.current?.id == course.id) {
                            onNav(Route.Edit(course.id))
                        }
                    }
                }
            }

            if (dueToday.isNotEmpty()) {
                SectionTitle("今天到期")
                Group {
                    dueToday.forEachIndexed { i, m ->
                        if (i > 0) Hairline(KtSize.sepInset)
                        MemoRow(m) { DataStore.upsertMemo(m.copy(done = !m.done)) }
                    }
                }
            }

            if (untimed.isNotEmpty()) {
                SectionTitle("无固定时间课程")
                Group {
                    untimed.forEachIndexed { i, course ->
                        if (i > 0) Hairline(KtSize.sepInset)
                        CourseRow(course, live = false) { onNav(Route.Edit(course.id)) }
                    }
                }
            }
        }

        TodayNavBar(
            progress = collapse,
            statusTop = statusTop,
            title = "今日",
            subtitle = "${Sch.fmtFull(today)} ${K.DOW_CN[Sch.dow(today)]} · 第 $wk 教学周"
        )
    }
}

// ---------------------------------------------------------------- 导航区

/**
 * 大标题 → 小标题的折叠。
 * 折叠靠「字号 + 字重 + 位移」三件事同时收拢，不用缩放图层（避免字被拉变形）。
 */
@Composable
private fun TodayNavBar(
    progress: Animatable<Float, *>,
    statusTop: Dp,
    title: String,
    subtitle: String
) {
    val c = LocalKtColors.current
    val p = progress.value
    val bandH = statusTop + (NAV_BAND.value + (NAV_COLLAPSED_H - NAV_BAND.value) * p).dp

    Box(Modifier.fillMaxWidth().height(statusTop + NAV_BAND)) {
        // 折叠后才出现的实底（内容从它下面穿过）
        Box(
            Modifier
                .fillMaxWidth()
                .height(bandH)
                .background(c.base.copy(alpha = (p * 0.98f).coerceIn(0f, 1f)))
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = KtSize.sepInset)
                    .height(0.5.dp)
                    .background(c.separator.copy(alpha = p))
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = KtSize.page)
                .offset(y = statusTop + (10f + 4f * p).dp)
        ) {
            Text(
                title,
                style = KtType.largeTitle.copy(
                    fontSize = lerp(
                        KtType.largeTitle.fontSize.value,
                        KtType.navTitle.fontSize.value,
                        p
                    ).sp,
                    lineHeight = lerp(
                        KtType.largeTitle.lineHeight.value,
                        KtType.navTitle.lineHeight.value,
                        p
                    ).sp,
                    fontWeight = FontWeight(
                        lerp(
                            KtType.largeTitle.fontWeight!!.weight.toFloat(),
                            KtType.navTitle.fontWeight!!.weight.toFloat(),
                            p
                        ).toInt()
                    )
                ),
                color = c.label,
                maxLines = 1
            )
            Spacer(Modifier.height(lerp(6f, 0f, p).dp))
            Text(
                subtitle,
                style = KtType.footnote,
                color = c.secondary,
                maxLines = 1,
                modifier = Modifier.alpha((1f - 1.8f * p).coerceIn(0f, 1f))
            )
        }
    }
}

// ---------------------------------------------------------------- Hero

/**
 * 「下一节课」大卡：表面色 + 排版，仅此而已。
 * 状态词 13 紫 → 倒计时 34/700（单位 17 灰）→ 课程名 17/600 → 教室·教师·时间 15 灰 → 3px 进度条。
 */
@Composable
private fun Hero(st: Sch.TodayStatus, now: LocalDateTime) {
    val c = LocalKtColors.current
    val cur = st.current
    val next = st.next
    val target = cur ?: next
    val tint = target?.let { KtCourse.of(it.color) } ?: c.tertiary

    val statusText: String
    var mins = 0
    var prefix: String? = null
    var done = 0f

    when {
        cur != null -> {
            statusText = "上课中"
            mins = (Sch.courseEndMin(cur) - st.nowMin).coerceAtLeast(0)
            prefix = "剩"
            val a = Sch.courseStartMin(cur)
            val b = Sch.courseEndMin(cur)
            done = ((st.nowMin - a).toFloat() / (b - a).coerceAtLeast(1)).coerceIn(0f, 1f)
        }

        next != null -> {
            val d = st.nextDate ?: now.toLocalDate()
            statusText = "下一节 · " + when {
                d == now.toLocalDate() -> "今天"
                d == now.toLocalDate().plusDays(1) -> "明天"
                else -> K.DOW_CN[Sch.dow(d)]
            }
            val startMin = Sch.courseStartMin(next)
            val targetAt = LocalDateTime.of(d, LocalTime.of(startMin / 60, startMin % 60))
            mins = Duration.between(now, targetAt).toMinutes().toInt().coerceAtLeast(0)
            // 还没开始 → 整条都是「剩余」，全部课程色
            done = 0f
        }

        else -> {
            statusText = "暂无课程"
        }
    }

    val courseName = target?.name ?: "今天没有课"
    val meta = if (target == null) {
        "去课表加一节，或从教务导进来"
    } else {
        buildList {
            add(target.location.ifBlank { "地点待定" })
            if (target.teacher.isNotBlank()) add(target.teacher)
            add(Sch.timeLabel(target))
        }.distinct().joinToString(" · ")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KtSize.heroRadius))
            .background(c.elevated)
            .padding(20.dp)
    ) {
        Text(
            statusText,
            style = KtType.footnote,
            color = if (target == null) c.tertiary else c.accent,
            maxLines = 1
        )

        Spacer(Modifier.height(6.dp))
        if (target != null) {
            Text(
                countdown(mins, prefix, c),
                style = KtType.body.copy(lineHeight = KtType.largeTitle.lineHeight),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(WEEK_HINT, style = KtType.largeTitle, color = c.label, maxLines = 1)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            courseName,
            style = KtType.cardTitle,
            color = c.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            meta,
            style = KtType.subhead,
            color = c.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(16.dp))
        Track(done = done, tint = tint)
    }
}

/** 无课时的整行大字（保持和倒计时同一视觉重量，避免卡片塌下去） */
private val WEEK_HINT = "好好休息"

/**
 * 倒计时：数字 34/700 tabular，单位 17/次要灰。
 * 例：12 小时 18 分 / 3 天 5 小时 / 剩 42 分。
 */
private fun countdown(minsRaw: Int, prefix: String?, c: KtColors): AnnotatedString {
    val total = minsRaw.coerceAtLeast(0)
    val d = total / (24 * 60)
    val h = (total % (24 * 60)) / 60
    val m = total % 60

    val num = SpanStyle(
        fontSize = KtType.largeTitle.fontSize,
        fontWeight = KtType.largeTitle.fontWeight,
        color = c.label
    )
    val unit = SpanStyle(
        fontSize = KtType.body.fontSize,
        fontWeight = FontWeight.Medium,
        color = c.secondary
    )

    return buildAnnotatedString {
        if (prefix != null) withStyle(SpanStyle(fontSize = KtType.body.fontSize, color = c.secondary)) {
            append(prefix)
            append(" ")
        }
        var wroteAny = false
        if (d > 0) {
            withStyle(num) { append("$d") }
            withStyle(unit) { append(" 天") }
            wroteAny = true
        }
        if (h > 0 || (!wroteAny && m == 0)) {
            if (wroteAny) append(" ")
            withStyle(num) { append("$h") }
            withStyle(unit) { append(" 小时") }
            wroteAny = true
        }
        if (m > 0) {
            if (wroteAny) append(" ")
            withStyle(num) { append("$m") }
            withStyle(unit) { append(" 分") }
        }
    }
}

/** 3px 进度：已过 = 次要灰，剩余 = 课程色，两端圆头 */
@Composable
private fun Track(done: Float, tint: Color) {
    val c = LocalKtColors.current
    val d = done.coerceIn(0f, 1f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
    ) {
        if (d > 0.001f) {
            Box(
                Modifier
                    .weight(d)
                    .fillMaxHeight()
                    .background(c.secondary.copy(alpha = 0.45f))
            )
        }
        if (d < 0.999f) {
            Box(
                Modifier
                    .weight(1f - d)
                    .fillMaxHeight()
                    .background(tint)
            )
        }
    }
}

// ---------------------------------------------------------------- 入口行

@Composable
private fun EntryRow(onNav: (Route) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EntryPill("学习数据", Icons.Outlined.BarChart, Modifier.weight(1f)) { onNav(Route.Stats) }
        EntryPill("时间轴", Icons.Outlined.Schedule, Modifier.weight(1f)) { onNav(Route.DayTimeline) }
    }
}

/** 等宽胶囊：44 高、elevated2 面、无边框、无涟漪 */
@Composable
private fun EntryPill(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = LocalKtColors.current
    val src = rememberPressSource()
    val pressed = rememberPressed(src)
    Row(
        modifier
            .height(KtSize.pillH)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (pressed) c.elevated3 else c.elevated2)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.label)
        Spacer(Modifier.width(8.dp))
        Text(text, style = KtType.button, color = c.label, maxLines = 1)
    }
}

// ---------------------------------------------------------------- 分组

@Composable
private fun SectionTitle(text: String) {
    val c = LocalKtColors.current
    Text(
        text,
        style = KtType.footnote,
        color = c.tertiary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalKtColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KtSize.cardRadius))
            .background(c.elevated),
        content = content
    )
}

@Composable
private fun EmptyRow(text: String) {
    val c = LocalKtColors.current
    Box(
        Modifier.fillMaxWidth().height(KtSize.rowH).padding(horizontal = KtSize.page),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, style = KtType.body, color = c.secondary)
    }
}

/**
 * 课程行：左侧 3×20 课程色条 → 课程名 17 → 时间·地点·教师 13 → 右侧节次 13。
 * 行高 52，没有图标。
 */
@Composable
private fun CourseRow(c: Course, live: Boolean, onClick: () -> Unit) {
    val colors = LocalKtColors.current
    val src = rememberPressSource()
    val pressed = rememberPressed(src)
    val sub = buildList {
        add(Sch.timeLabel(c))
        add(c.location.ifBlank { "地点待定" })
        if (c.teacher.isNotBlank()) add(c.teacher)
    }.distinct().joinToString(" · ")

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KtSize.rowH)
            .background(if (pressed) colors.elevated2 else Color.Transparent)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = KtSize.page, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(CircleShape)
                .background(KtCourse.of(c.color))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.name,
                style = KtType.body,
                color = if (live) colors.accent else colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                sub,
                style = KtType.footnote,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (live) "上课中" else Sch.secLabel(c),
            style = KtType.footnote,
            color = if (live) colors.accent else colors.tertiary,
            maxLines = 1
        )
    }
}

/** 备忘行：方形勾选 + 一句待办 + 截止日，行高与课程行一致 */
@Composable
fun MemoRow(m: Memo, onToggle: () -> Unit) {
    val c = LocalKtColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KtSize.rowH)
            .padding(horizontal = KtSize.page, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (m.done) c.accent else c.elevated2)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (m.done) Text("✓", style = KtType.footnote, color = c.elevated)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            m.text,
            style = KtType.body,
            color = if (m.done) c.tertiary else c.label,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (m.dueDate.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(m.dueDate.substring(5), style = KtType.footnote, color = c.tertiary)
        }
    }
}

/** 分隔线：内嵌缩进 20，深 rgba(84,84,88,.65) / 浅 rgba(60,60,67,.29) */
@Composable
fun Hairline(inset: Dp = KtSize.sepInset) {
    val c = LocalKtColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(0.5.dp)
            .background(c.separator)
    )
}
