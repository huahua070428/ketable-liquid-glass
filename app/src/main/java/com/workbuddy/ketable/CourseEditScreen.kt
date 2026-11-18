package com.workbuddy.ketable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 课程编辑 / 新建：字段与 v4.1.0s 的 #edit 完全一致（课程名、教师、地点、星期、节次、周次、单双周、颜色、学分、备注）。
 * 点周视图空白格进来会带上 day / section 预设，省一步选择。
 */
@Composable
fun CourseEditScreen(
    courseId: String,
    presetDay: Int,
    presetSection: Int,
    onBack: () -> Unit
) {
    val colors = LocalKtColors.current
    val orig = remember(courseId) {
        if (courseId == "new") null else DataStore.course(courseId)
    }
    val isNew = orig == null

    var name by remember { mutableStateOf(orig?.name ?: "") }
    var teacher by remember { mutableStateOf(orig?.teacher ?: "") }
    var location by remember { mutableStateOf(orig?.location ?: "") }
    // 点空白格进来会带 day / section；没带就从周一第 1–2 节起
    val ps = if (isNew) presetSection.coerceIn(0, 10) else 0
    var day by remember { mutableStateOf(if (isNew) (if (presetDay == 0) 1 else presetDay.coerceIn(1, 7)) else orig!!.day) }
    var start by remember { mutableStateOf(if (isNew) (if (ps == 0) 1 else ps) else orig!!.startSection) }
    var end by remember { mutableStateOf(if (isNew) (if (ps == 0) 2 else (ps + 1).coerceAtMost(10)) else orig!!.endSection) }
    var ws by remember { mutableStateOf(orig?.weekStart ?: 1) }
    var we by remember { mutableStateOf(orig?.weekEnd ?: 16) }
    var parity by remember { mutableStateOf(orig?.parity ?: 0) }
    var color by remember { mutableStateOf(orig?.color ?: "purple") }
    var credit by remember { mutableStateOf(orig?.credit?.let { fmtCredit(it) } ?: "") }
    var remark by remember { mutableStateOf(orig?.remark ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }

    fun fixRange() {
        if (end < start) end = start
        if (we < ws) we = ws
    }

    ScreenScroll(dockVisible = false) {
        PageHead(if (isNew) "新建课程" else "编辑课程", null, onBack)
        Spacer(Modifier.height(4.dp))

        // 实时预览：改哪个字段这里立刻跟着变，避免填完才发现不对
        GlassCard(
            Modifier.fillMaxWidth(), corner = 18.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                Text(
                    name.ifBlank { "未命名课程" },
                    fontSize = 16.sp, fontWeight = FontWeight(800), color = colors.ink1, maxLines = 1
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    previewLine(day, start, end, ws, we, parity).ifBlank { "先填个课程名" },
                    fontSize = 11.5.sp, color = colors.ink3
                )
            }
        }

        Sect("基本信息")
        FormCard {
            FieldRow("课程名", name, "如：高等数学 B2", { name = it; err = null })
            Hairline(16.dp)
            FieldRow("教师", teacher, "如：张三", { teacher = it })
            Hairline(16.dp)
            FieldRow("地点", location, "如：教一 101", { location = it })
        }

        Sect("上课时间")
        FormCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("星期", fontSize = 14.sp, fontWeight = FontWeight(600), color = colors.ink1)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (day == 0) "时间待定，不进周视图" else K.DOW_CN[day],
                        fontSize = 11.sp, color = colors.ink3
                    )
                }
                Spacer(Modifier.height(10.dp))
                val days = listOf(0, 1, 2, 3, 4, 5, 6, 7)
                days.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { d ->
                            Chip(
                                label = if (d == 0) "待定" else K.DOW_CN[d],
                                selected = day == d,
                                modifier = Modifier.weight(1f)
                            ) { day = d }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (day != 0) {
                Hairline(16.dp)
                StepperRow(
                    title = "起始节次",
                    value = start,
                    min = 1, max = 10,
                    hint = K.SECTION_TIMES[(start - 1).coerceIn(0, 9)].first
                ) { v -> start = v; fixRange() }
                Hairline(16.dp)
                StepperRow(
                    title = "结束节次",
                    value = end,
                    min = start, max = 10,
                    hint = K.SECTION_TIMES[(end - 1).coerceIn(0, 9)].second
                ) { v -> end = v }
            }
        }

        if (day != 0) {
            Sect("周次")
            FormCard {
                StepperRow("起始周", ws, 1, we, "第 $ws 周") { v -> ws = v; fixRange() }
                Hairline(16.dp)
                StepperRow("结束周", we, ws, 24, "第 $we 周") { v -> we = v }
                Hairline(16.dp)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("单双周", fontSize = 14.sp, fontWeight = FontWeight(600), color = colors.ink1)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "每周", 1 to "单周", 2 to "双周").forEach { (v, label) ->
                            Chip(label, parity == v, Modifier.weight(1f)) { parity = v }
                        }
                    }
                }
            }
        }

        Sect("更多")
        FormCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("色块深浅", fontSize = 14.sp, fontWeight = FontWeight(600), color = colors.ink1)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    K.PALETTE.forEach { c ->
                        val on = c == color
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = K.courseAlpha(c)))
                                .then(
                                    if (on) Modifier.border(2.dp, colors.ink1, CircleShape)
                                    else Modifier.border(1.dp, colors.ink4.copy(alpha = 0.35f), CircleShape)
                                )
                                .clickable { color = c }
                        )
                    }
                }
            }
            Hairline(16.dp)
            FieldRow("学分", credit, "可不填", { credit = it }, numeric = true)
            Hairline(16.dp)
            FieldRow("备注", remark, "如：需要带绘图工具", { remark = it })
        }

        err?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontSize = 12.sp, color = Color(0xFFFF6B6B))
        }

        Spacer(Modifier.height(18.dp))
        PrimaryBtn(if (isNew) "添加到课表" else "保存修改") {
            val fixedName = name.trim()
            if (fixedName.isEmpty()) {
                err = "课程名不能为空"
            } else {
                val c = Course(
                    id = orig?.id ?: "",
                    name = fixedName,
                    teacher = teacher.trim(),
                    location = location.trim(),
                    day = day,
                    startSection = start,
                    endSection = end,
                    weekStart = ws,
                    weekEnd = we,
                    parity = parity,
                    color = color,
                    icon = orig?.icon ?: "",
                    remark = remark.trim(),
                    credit = credit.trim().toDoubleOrNull(),
                    score = orig?.score
                )
                DataStore.upsertCourse(c)
                onBack()
            }
        }

        if (!isNew) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1AFF6B6B))
                    .clickable { askDelete = true },
                contentAlignment = Alignment.Center
            ) {
                Text("删除这门课", fontSize = 13.sp, fontWeight = FontWeight(600), color = Color(0xFFFF6B6B))
            }
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("删除「${orig?.name ?: ""}」？") },
            text = { Text("删除后无法恢复，也不能从回收站找回。") },
            confirmButton = {
                TextButton(onClick = {
                    orig?.let { DataStore.deleteCourse(it.id) }
                    askDelete = false
                    onBack()
                }) { Text("删除", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("取消") } }
        )
    }
}

private fun fmtCredit(v: Double): String =
    if (v == v.toInt().toDouble()) "${v.toInt()}" else "$v"

private fun previewLine(day: Int, start: Int, end: Int, ws: Int, we: Int, parity: Int): String {
    if (day == 0) return "时间待定 · $ws–$we 周"
    val sec = if (start == end) "第${start}节" else "$start–$end 节"
    val t = K.SECTION_TIMES[(start - 1).coerceIn(0, 9)].first + "–" +
            K.SECTION_TIMES[(end - 1).coerceIn(0, 9)].second
    val p = when (parity) { 1 -> " · 单周"; 2 -> " · 双周"; else -> "" }
    return "${K.DOW_CN[day]} · $sec · $t · $ws–$we 周$p"
}

// ---------------------------------------------------------------- 表单零件

@Composable
private fun FormCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = LocalKtColors.current
    GlassCard(
        Modifier.fillMaxWidth(), corner = 20.dp,
        tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
        light = colors.shaderLight()
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    numeric: Boolean = false
) {
    val colors = LocalKtColors.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(label, fontSize = 11.5.sp, color = colors.ink3)
        Spacer(Modifier.height(4.dp))
        Box {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = colors.ink4)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(fontSize = 15.sp, color = colors.ink1),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    hint: String,
    onChange: (Int) -> Unit
) {
    val colors = LocalKtColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight(600), color = colors.ink1)
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 11.sp, color = colors.ink3)
        }
        StepBtn("−", enabled = value > min) { onChange((value - 1).coerceAtLeast(min)) }
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            Text("$value", fontSize = 15.sp, fontWeight = FontWeight(700), color = colors.ink1)
        }
        StepBtn("＋", enabled = value < max) { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun StepBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalKtColors.current
    Box(
        Modifier
            .size(width = 34.dp, height = 32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.controlBg)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 16.sp,
            color = if (enabled) colors.ink2 else colors.ink4.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalKtColors.current
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.accentSoft else colors.controlBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 12.5.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight(700) else FontWeight(500),
            color = if (selected) colors.accent else colors.ink2
        )
    }
}
