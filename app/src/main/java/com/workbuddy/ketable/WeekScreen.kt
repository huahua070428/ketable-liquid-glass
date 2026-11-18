package com.workbuddy.ketable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** 周偏移量放在这里，顶部玻璃栏（壳层）和网格共用 */
object WeekUi {
    var offset by mutableIntStateOf(0)
}

// 7 列（开周末）与 5 列共用一套尺寸：列宽变窄时自动切到紧凑排版
private val ROW_H = 84.dp
private val GAP = 3.dp
private val TIME_W = 24.dp
private val CARD_PAD = 8.dp

@Composable
fun WeekScreen(onNav: (Route) -> Unit) {
    val colors = LocalKtColors.current
    val settings = DataStore.settings
    val courses = DataStore.courses
    val offset = WeekUi.offset

    val today = LocalDate.now()
    val wk = Sch.weekNo(today, settings) + offset
    val dates = Sch.weekDates(today).map { it.plusWeeks(offset.toLong()) }
    val dayCount = if (settings.showWeekend) 7 else 5

    ScreenScroll(pad = 14.dp) {
        if (courses.isEmpty()) {
            EmptyScheduleGuide(onNav)
            return@ScreenScroll
        }

        FlatCard(Modifier.fillMaxWidth(), corner = 22.dp) {
            WeekGrid(wk = wk, dates = dates, dayCount = dayCount, courses = courses, onNav = onNav)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "点课程可编辑，点空白格快速添加；短期课按周次自动显隐，单双周自动区分。" +
                    if (!settings.showWeekend) "周六日没课，设置里可开启周末列。" else "",
            fontSize = 11.sp, color = colors.ink4, lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

/** 周视图网格：表头 + 5 行节次，课程块按 startSection/endSection 跨行 */
@Composable
private fun WeekGrid(
    wk: Int,
    dates: List<LocalDate>,
    dayCount: Int,
    courses: List<Course>,
    onNav: (Route) -> Unit
) {
    val colors = LocalKtColors.current
    val today = LocalDate.now()

    Column(Modifier.padding(CARD_PAD)) {
        // 表头
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
            Spacer(Modifier.width(TIME_W))
            for (d in 1..dayCount) {
                val date = dates[d - 1]
                val isToday = date == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        K.DOW_CN[d].removePrefix("周"), fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        color = if (isToday) colors.accent else colors.ink2
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        Sch.fmtDate(date), fontSize = 9.sp,
                        color = if (isToday) colors.accent else colors.ink4
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(Modifier.fillMaxWidth().height(ROW_H * 5 + GAP * 4)) {
            val colW: Dp = (maxWidth - TIME_W - GAP * dayCount) / dayCount
            val compact = colW < 50.dp
            fun xOf(d: Int): Dp = TIME_W + GAP + (colW + GAP) * (d - 1)
            fun yOf(row: Int): Dp = (ROW_H + GAP) * row

            // 左侧节次列
            K.PAIRS.forEachIndexed { ri, p ->
                Column(
                    Modifier.offset(y = yOf(ri)).width(TIME_W).height(ROW_H),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${p.first}–${p.second}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = colors.ink3, maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(K.SECTION_TIMES[p.first - 1].first, fontSize = 8.5.sp, color = colors.ink4, maxLines = 1)
                    Text(K.SECTION_TIMES[p.second - 1].second, fontSize = 8.5.sp, color = colors.ink4, maxLines = 1)
                }
            }

            // 空格子
            for (ri in 0 until 5) {
                for (d in 1..dayCount) {
                    val occupied = courses.any {
                        it.day == d && Sch.activeInWeek(it, wk) &&
                                ri >= sectionRowOf(it.startSection) && ri <= sectionRowOf(it.endSection)
                    }
                    if (!occupied) {
                        Box(
                            Modifier
                                .offset(x = xOf(d), y = yOf(ri))
                                .size(colW, ROW_H)
                                .clip(RoundedCornerShape(11.dp))
                                .clickable {
                                    onNav(Route.Edit("new", d, K.PAIRS[ri].first))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRoundRect(
                                    color = if (colors.dark) Color(0x1AFFFFFF) else Color(0x1F0A0A0F),
                                    cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
                                    style = Stroke(
                                        width = 0.8.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f), 0f)
                                    )
                                )
                            }
                            Text("＋", fontSize = if (compact) 10.sp else 11.sp, color = colors.ink4)
                        }
                    }
                }
            }

            // 课程块
            for (c in courses) {
                if (!Sch.activeInWeek(c, wk)) continue
                if (c.day < 1 || c.day > dayCount) continue
                val r1 = sectionRowOf(c.startSection)
                val r2 = sectionRowOf(c.endSection)
                val span = (r2 - r1 + 1).coerceAtLeast(1)
                val h = ROW_H * span + GAP * (span - 1)
                CourseBlock(
                    c = c,
                    span = span,
                    compact = compact,
                    modifier = Modifier.offset(x = xOf(c.day), y = yOf(r1)).size(colW, h),
                    live = isLive(c, wk)
                ) { onNav(Route.Edit(c.id)) }
            }
        }
    }
}

private fun sectionRowOf(sec: Int): Int = ((sec - 1) / 2).coerceIn(0, 4)

/** 当前正在上的课（只有本周才判定） */
private fun isLive(c: Course, wk: Int): Boolean {
    val today = LocalDate.now()
    val s = DataStore.settings
    if (Sch.weekNo(today, s) != wk) return false
    if (c.day != Sch.dow(today)) return false
    val now = java.time.LocalTime.now()
    val nowMin = now.hour * 60 + now.minute
    return nowMin >= Sch.courseStartMin(c) && nowMin < Sch.courseEndMin(c)
}

/** 课程块：平面底 + 一个课程色圆点 + 文字（不要图标、不要彩色渐变） */
@Composable
private fun CourseBlock(
    c: Course,
    span: Int,
    compact: Boolean,
    modifier: Modifier,
    live: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalKtColors.current
    val padH = if (compact) 3.dp else 7.dp
    val nameSize: TextUnit = if (compact) 9.5.sp else 12.sp
    val nameLine: TextUnit = if (compact) 12.sp else 15.sp
    val locSize: TextUnit = if (compact) 8.5.sp else 10.sp
    val metaSize: TextUnit = if (compact) 8.sp else 9.sp

    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (colors.dark) Color(0xFF1B1B22) else Color(0xFFEFEFF3))
            .clickable { onClick() }
            .padding(horizontal = padH, vertical = 6.dp)
    ) {
        Column {
            // 课程色只留一个小圆点
            Box(
                Modifier
                    .padding(bottom = 4.dp)
                    .size(if (compact) 5.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (live) colors.accent else K.dotColor(c.color))
            )
            Text(
                c.name, fontSize = nameSize, fontWeight = FontWeight.Bold,
                color = if (live) colors.accent else colors.ink1,
                lineHeight = nameLine,
                maxLines = if (span >= 2) 4 else if (compact) 3 else 2, overflow = TextOverflow.Ellipsis
            )
            if (c.location.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    c.location, fontSize = locSize, color = colors.ink3,
                    lineHeight = (locSize.value + 2.5f).sp, maxLines = if (span >= 2 || compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (span >= 2) {
                Spacer(Modifier.height(3.dp))
                Text(
                    K.SECTION_TIMES[(c.startSection - 1).coerceIn(0, 9)].first,
                    fontSize = metaSize, color = colors.ink4, lineHeight = (metaSize.value + 2f).sp, maxLines = 1
                )
                Text(
                    K.SECTION_TIMES[(c.endSection - 1).coerceIn(0, 9)].second,
                    fontSize = metaSize, color = colors.ink4, lineHeight = (metaSize.value + 2f).sp, maxLines = 1
                )
                Text(
                    Sch.weekLabel(c).replace("·单周", " 单").replace("·双周", " 双"),
                    fontSize = metaSize, color = colors.ink4, lineHeight = (metaSize.value + 2f).sp, maxLines = 1
                )
            } else if (c.weekEnd - c.weekStart < 19 || c.parity != 0) {
                Spacer(Modifier.height(1.dp))
                Text(
                    Sch.weekLabel(c).replace("·单周", "单").replace("·双周", "双"),
                    fontSize = metaSize, color = colors.ink4, lineHeight = (metaSize.value + 2f).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 空课表引导 */
@Composable
private fun EmptyScheduleGuide(onNav: (Route) -> Unit) {
    val colors = LocalKtColors.current
    FlatCard(Modifier.fillMaxWidth(), corner = 24.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("课表是空的", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.ink1)
            Spacer(Modifier.height(8.dp))
            Text(
                "拍照/截图能被 AI 识别导入，\n也可以直接手动添加你的第一门课",
                fontSize = 12.sp, color = colors.ink3, lineHeight = 19.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            PrimaryBtn("识图导入课程表") { onNav(Route.Import) }
            Spacer(Modifier.height(8.dp))
            GhostBtn("手动添加课程") { onNav(Route.Edit("new")) }
            Spacer(Modifier.height(14.dp))
            Text(
                "从旧版覆盖安装的课表会自动迁移；没迁过来可在「设置 → 导入数据」粘贴 JSON 恢复。",
                fontSize = 10.5.sp, color = colors.ink4, lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PrimaryBtn(text: String, onClick: () -> Unit) {
    val colors = LocalKtColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.accent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun GhostBtn(text: String, onClick: () -> Unit) {
    val colors = LocalKtColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.controlBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink2)
    }
}
