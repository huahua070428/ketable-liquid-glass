package com.workbuddy.ketable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * 学习数据：课时 / 学分 / 分布。
 * 口径与 v4.1.0s 一致：1 节 = 45 分钟；单双周按实际出现的周数折算。
 */
@Composable
fun StatsScreen(onNav: (Route) -> Unit, onBack: () -> Unit) {
    val colors = LocalKtColors.current
    val courses = DataStore.courses
    val s = DataStore.settings
    val curWk = Sch.weekNo(LocalDate.now(), s)

    val termSections = courses.sumOf { secCount(it) * effWeeks(it) }
    val doneSections = courses.sumOf { c ->
        (1..curWk.coerceIn(0, 24)).count { w -> Sch.activeInWeek(c, w) } * secCount(c)
    }
    val weekSections = courses.filter { Sch.activeInWeek(it, curWk) }.sumOf { secCount(it) }
    val credits = courses.sumOf { it.credit ?: 0.0 }
    val grad = s.gradCredits.coerceAtLeast(1)

    // 按课程排课时（整个学期）
    val rank = courses
        .filter { it.day != 0 }
        .map { it to secCount(it) * effWeeks(it) }
        .sortedByDescending { it.second }
        .take(6)
    val maxRank = rank.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    // 一周分布（按学期折算到每个星期几）
    val byDay = (1..7).map { d ->
        courses.filter { it.day == d }.sumOf { secCount(it) * effWeeks(it) }
    }
    val maxDay = byDay.maxOrNull()?.coerceAtLeast(1) ?: 1
    val busiest = byDay.indices.maxByOrNull { byDay[it] }?.plus(1) ?: 1

    val noCredit = courses.count { it.credit == null }

    ScreenScroll(dockVisible = false) {
        PageHead("学习数据", "第 $curWk 周 · 共 ${courses.count { it.day != 0 }} 门有课表的课程", onBack)

        // 三个关键数字
        GlassCard(
            Modifier.fillMaxWidth(), corner = 22.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp)) {
                    StatTile("本周课时", "$weekSections", "节", Modifier.weight(1f))
                    Divider()
                    StatTile("已上课时", "$doneSections", "节", Modifier.weight(1f))
                    Divider()
                    StatTile("学期总课时", "$termSections", "节", Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.hairline))
                Text(
                    "约 ${fmt1(termSections * 45 / 60.0)} 小时 · 平均每周 ${fmt1(termSections / 20.0)} 节",
                    fontSize = 11.sp, color = colors.ink3,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 14.dp)
                )
            }
        }

        Sect("学分进度")
        GlassCard(
            Modifier.fillMaxWidth(), corner = 20.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmt1(credits), fontSize = 26.sp, fontWeight = FontWeight(800), color = colors.ink1
                    )
                    Text(
                        " / $grad 学分", fontSize = 12.sp, color = colors.ink3,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(credits / grad * 100).toInt()}%", fontSize = 13.sp,
                        fontWeight = FontWeight(700), color = colors.accent
                    )
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar((credits / grad).toFloat().coerceIn(0f, 1f))
                Spacer(Modifier.height(10.dp))
                Text(
                    if (credits <= 0.0) "课程还没填学分，编辑课程时补上就能算进度。"
                    else if (noCredit > 0) "还有 $noCredit 门课没填学分，进度会偏低。可在「设置 → 毕业学分要求」调整毕业线。"
                    else "按「设置 → 毕业学分要求」的 $grad 学分计算。",
                    fontSize = 11.sp, color = colors.ink3, lineHeight = 17.sp
                )
            }
        }

        Sect("课时最多的课")
        GlassCard(
            Modifier.fillMaxWidth(), corner = 20.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (rank.isEmpty()) {
                    Text("还没有排进课表的课程", fontSize = 12.sp, color = colors.ink3)
                } else {
                    rank.forEachIndexed { i, (c, n) ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(width = 3.dp, height = 14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colors.accent.copy(alpha = 0.35f + K.courseAlpha(c.color) * 0.6f))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                c.name, fontSize = 12.5.sp, color = colors.ink1, maxLines = 1,
                                modifier = Modifier.width(96.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(999.dp))
                                    .background(colors.controlBg)
                            ) {
                                Box(
                                    Modifier.fillMaxHeight()
                                        .fillMaxWidth((n.toFloat() / maxRank).coerceIn(0.06f, 1f))
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(colors.accent, Color(0xFFA58BFF))
                                            )
                                        )
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${n}节", fontSize = 11.sp, color = colors.ink3,
                                modifier = Modifier.width(38.dp)
                            )
                        }
                    }
                }
            }
        }

        Sect("一周分布")
        GlassCard(
            Modifier.fillMaxWidth(), corner = 20.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(84.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (d in 1..7) {
                        val v = byDay[d - 1]
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((v.toFloat() / maxDay).coerceIn(if (v == 0) 0.02f else 0.08f, 1f))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (v == 0) SolidColor(colors.controlBg)
                                        else Brush.verticalGradient(
                                            listOf(colors.accent.copy(alpha = 0.85f), colors.accent.copy(alpha = 0.35f))
                                        )
                                    )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in 1..7) {
                        Text(
                            K.DOW_CN[d].removePrefix("周"),
                            fontSize = 10.sp,
                            color = if (byDay[d - 1] == maxDay) colors.accent else colors.ink3,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Sect("洞察")
        GlassCard(
            Modifier.fillMaxWidth(), corner = 20.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Insight("最忙的一天是${K.DOW_CN[busiest]}，整个学期排了 ${byDay[busiest - 1]} 节。")
                Insight(
                    if (curWk in 1..20) "学期已过 ${curWk} / 20 周，课时进度约 ${(doneSections * 100 / termSections.coerceAtLeast(1))}%。"
                    else "当前不在 1–20 教学周内，进度按已过周数估算。"
                )
                Insight(
                    if (courses.count { it.day == 0 } > 0) "有 ${courses.count { it.day == 0 }} 门课时间待定，只在今日页显示。"
                    else "所有课程都已排进固定时间。"
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "统计口径：1 节 = 45 分钟，单双周按实际周数折算；修改课程后这里会立即重算。",
            fontSize = 10.5.sp, color = colors.ink4, lineHeight = 16.sp
        )
    }
}

private fun secCount(c: Course): Int =
    if (c.day == 0) 0 else (c.endSection - c.startSection + 1).coerceAtLeast(1)

/** 该课在整个学期里实际出现的周数（考虑周次区间与单双周） */
private fun effWeeks(c: Course): Int {
    if (c.day == 0) return 0
    return (c.weekStart..c.weekEnd).count { w ->
        !(c.parity == 1 && w % 2 == 0) && !(c.parity == 2 && w % 2 == 1)
    }
}

private fun fmt1(v: Double): String =
    if (v == v.toInt().toDouble()) "${v.toInt()}" else String.format("%.1f", v)

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val colors = LocalKtColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight(800), color = colors.ink1)
            Text(unit, fontSize = 10.sp, color = colors.ink4, modifier = Modifier.padding(bottom = 3.dp, start = 2.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.5.sp, color = colors.ink3)
    }
}

@Composable
private fun Divider() {
    val colors = LocalKtColors.current
    Box(Modifier.width(1.dp).height(28.dp).background(colors.hairline))
}

@Composable
private fun ProgressBar(f: Float) {
    val colors = LocalKtColors.current
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(colors.controlBg)
    ) {
        Box(
            Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(999.dp))
                .background(Brush.horizontalGradient(listOf(colors.accent, Color(0xFFA58BFF))))
        )
    }
}

@Composable
private fun Insight(text: String) {
    val colors = LocalKtColors.current
    Row(Modifier.padding(vertical = 5.dp)) {
        Box(
            Modifier.padding(top = 6.dp).size(4.dp).clip(RoundedCornerShape(999.dp))
                .background(colors.accent.copy(alpha = 0.6f))
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 11.5.sp, color = colors.ink2, lineHeight = 18.sp)
    }
}
