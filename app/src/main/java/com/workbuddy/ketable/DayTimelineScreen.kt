package com.workbuddy.ketable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

/**
 * 时间轴：把"第几节"还原成真实钟点。
 * 适合看"今天下午哪段是空的""这节到底几点下课"，是周视图的补充而不是重复。
 */
private const val BASE_HOUR = 7
private const val END_HOUR = 22
private val HOUR_H = 54.dp
private val AXIS_W = 42.dp

@Composable
fun DayTimelineScreen(onNav: (Route) -> Unit, onBack: () -> Unit) {
    val colors = LocalKtColors.current
    val s = DataStore.settings
    val today = LocalDate.now()
    val wk = Sch.weekNo(today, s)
    val list = Sch.onDate(today, DataStore.courses, s)
    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    val totalSections = list.sumOf { (it.endSection - it.startSection + 1).coerceAtLeast(1) }

    fun yOf(min: Int): Dp = (HOUR_H.value * (min - BASE_HOUR * 60) / 60f).dp

    ScreenScroll(dockVisible = false, pad = 16.dp) {
        PageHead(
            "时间轴",
            "${Sch.fmtFull(today)} ${K.DOW_CN[Sch.dow(today)]} · 第 $wk 周 · $totalSections 节",
            onBack
        )
        Spacer(Modifier.height(6.dp))

        GlassCard(
            Modifier.fillMaxWidth(), corner = 22.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("今天没课", fontSize = 15.sp, fontWeight = FontWeight(700), color = colors.ink1)
                            Spacer(Modifier.height(6.dp))
                            Text("整天都是自己的", fontSize = 11.5.sp, color = colors.ink3)
                        }
                    }
                } else {
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().height(HOUR_H * (END_HOUR - BASE_HOUR))
                    ) {
                        val blockX = AXIS_W + 6.dp
                        val blockW = maxWidth - blockX

                        // 刻度线 + 钟点
                        Canvas(Modifier.fillMaxWidth().height(HOUR_H * (END_HOUR - BASE_HOUR))) {
                            for (h in BASE_HOUR..END_HOUR) {
                                val y = HOUR_H.toPx() * (h - BASE_HOUR)
                                drawLine(
                                    color = if (colors.dark) Color(0x14FFFFFF) else Color(0x14181C32),
                                    start = Offset(AXIS_W.toPx(), y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }
                        }
                        for (h in BASE_HOUR until END_HOUR) {
                            Text(
                                "%02d:00".format(h),
                                fontSize = 9.sp,
                                color = colors.ink4,
                                modifier = Modifier.offset(y = yOf(h * 60) + 1.dp).width(AXIS_W - 6.dp)
                            )
                        }

                        // 课程块
                        list.forEach { c ->
                            val a = Sch.courseStartMin(c)
                            val b = Sch.courseEndMin(c)
                            val live = nowMin in a until b
                            val top = yOf(a) + 1.dp
                            val h = (yOf(b) - yOf(a) - 2.dp).coerceAtLeast(26.dp)
                            val alpha = K.courseAlpha(c.color) + if (colors.dark) 0.2f else 0.06f + if (live) 0.1f else 0f
                            Box(
                                Modifier
                                    .offset(x = blockX, y = top)
                                    .width(blockW)
                                    .height(h)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                colors.accent.copy(alpha = alpha.coerceAtMost(0.7f)),
                                                colors.accent.copy(alpha = (alpha * 0.5f).coerceAtLeast(0.14f))
                                            )
                                        )
                                    )
                                    .clickable { onNav(Route.Edit(c.id)) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            c.name,
                                            fontSize = 12.5.sp, fontWeight = FontWeight(700),
                                            color = if (colors.dark) Color.White else colors.ink1,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (live) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "上课中", fontSize = 8.5.sp, fontWeight = FontWeight(700),
                                                color = if (colors.dark) Color.White else colors.accent
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${Sch.timeLabel(c)} · ${c.location.ifBlank { "地点待定" }}" +
                                                if (c.teacher.isNotBlank()) " · ${c.teacher}" else "",
                                        fontSize = 9.5.sp,
                                        color = if (colors.dark) Color(0xCCFFFFFF) else colors.ink2,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 当前时间线
                        if (nowMin in BASE_HOUR * 60 until END_HOUR * 60) {
                            Box(
                                Modifier
                                    .offset(x = AXIS_W + 2.dp, y = yOf(nowMin))
                                    .width(maxWidth - AXIS_W - 4.dp)
                                    .height(1.5.dp)
                                    .background(Color(0xFFFF5C8A).copy(alpha = 0.85f))
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "红线是现在。点课程块可直接改时间；下课时间按每节 45 分钟、课间 10–15 分钟推算。",
            fontSize = 10.5.sp, color = colors.ink4, lineHeight = 16.sp
        )
    }
}
