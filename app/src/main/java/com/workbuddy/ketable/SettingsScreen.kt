package com.workbuddy.ketable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun SettingsScreen(onNav: (Route) -> Unit) {
    val colors = LocalKtColors.current
    val s = DataStore.settings
    val clip = LocalClipboardManager.current

    // 输入弹窗状态
    var editField by remember { mutableStateOf<EditField?>(null) }
    var showReset by remember { mutableStateOf(false) }
    var showCrash by remember { mutableStateOf(false) }
    // 崩溃记录是一次文件读，别放在组合里每次重算
    val crashText = remember { CrashGuard.lastCrash() }
    var msg by remember { mutableStateOf<String?>(null) }

    fun save(f: Settings) = DataStore.saveSettings(f)

    ScreenScroll {
        PageHead("设置", "让课表更贴合你的学期")

        // ---------------- 学期 ----------------
        Sect("学期")
        SettingCard {
            SetRow("学期开始日期", "第1周周一，用于计算教学周", s.semesterStart) {
                editField = EditField("学期开始日期", s.semesterStart, "yyyy-MM-dd") { v ->
                    runCatching { LocalDate.parse(v) }.onSuccess { save(s.copy(semesterStart = v)) }
                        .onFailure { msg = "日期格式不对，应为 yyyy-MM-dd" }
                }
            }
            Hairline(20.dp)
            SetSwitchRow("显示周末列", "周视图显示周六周日", s.showWeekend) {
                save(s.copy(showWeekend = it))
            }
            Hairline(20.dp)
            SetRow("毕业学分要求", "学习数据页的学分进度按它算百分比", "${s.gradCredits}") {
                editField = EditField("毕业学分要求", "${s.gradCredits}", "如 160") { v ->
                    v.toIntOrNull()?.let { save(s.copy(gradCredits = it)) }
                }
            }
        }

        // ---------------- 外观 ----------------
        Sect("外观")
        SettingCard {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("外观模式", fontSize = 15.sp, fontWeight = FontWeight.Normal, color = colors.ink1)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (v, label) ->
                        val on = s.themeMode == v
                        Box(
                            Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) colors.accentSoft else colors.controlBg)
                                .clickable { save(s.copy(themeMode = v)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label, fontSize = 12.sp, maxLines = 1,
                                fontWeight = if (on) FontWeight(700) else FontWeight(500),
                                color = if (on) colors.accent else colors.ink2
                            )
                        }
                    }
                }
            }
            Hairline(20.dp)
            SetSwitchRow("液态玻璃效果", "折射与彩虹光晕", s.liquidGlass) {
                GlassRuntime.reset()
                save(s.copy(liquidGlass = it, glassLevel = if (it) 2 else 0))
            }
        }

        // ---------------- 提醒 ----------------
        Sect("提醒")
        SettingCard {
            SetRow("上课前提醒", "App 在前台时弹窗提醒", "${s.remindLeadMin} 分钟") {
                editField = EditField("上课前提醒（分钟）", "${s.remindLeadMin}", "如 15") { v ->
                    v.toIntOrNull()?.let { save(s.copy(remindLeadMin = it)) }
                }
            }
        }

        // ---------------- AI 接口 ----------------
        Sect("AI 接口（识图导入 + AI 助手）")
        SettingCard {
            SetRow("接口地址", "OpenAI 兼容接口", shortUrl(s.apiBase)) {
                editField = EditField("接口地址", s.apiBase, "https://…") { v -> save(s.copy(apiBase = v)) }
            }
            Hairline(20.dp)
            SetRow("API Key", "只保存在你手机本地，不会上传", if (s.apiKey.isBlank()) "未填写" else "已填写") {
                editField = EditField("API Key", s.apiKey, "sk-…") { v -> save(s.copy(apiKey = v)) }
            }
            Hairline(20.dp)
            SetRow("模型", "识图导入需要「视觉模型」", s.apiModel) {
                editField = EditField("模型", s.apiModel, "如 gpt-4o") { v -> save(s.copy(apiModel = v)) }
            }
        }

        // ---------------- 更多 ----------------
        Sect("更多")
        SettingCard {
            SetRow("导入课程表", "拍照/截图，AI 自动识别", "›") { onNav(Route.Import) }
            Hairline(20.dp)
            SetRow("意见反馈", "bug 反馈 / 功能建议", "›") { onNav(Route.Feedback) }
        }

        // ---------------- 数据 ----------------
        Sect("数据（${DataStore.courses.size} 门课 · ${DataStore.memos.size} 条备忘）")
        SettingCard {
            SetRow("导出数据", "复制为 JSON，换手机可恢复", "复制") {
                clip.setText(
                    AnnotatedString(
                        """{"courses":${DataStore.coursesToJson(DataStore.courses)},""" +
                                """"memos":[],"exportedAt":"${LocalDate.now()}"}"""
                    )
                )
                msg = "已复制到剪贴板"
            }
            Hairline(20.dp)
            SetRow("导入数据", "从剪贴板粘贴之前导出的 JSON", "粘贴") {
                val txt = clip.getText()?.text
                if (txt.isNullOrBlank()) { msg = "剪贴板是空的" }
                else if (DataStore.importJson(txt)) { msg = "已恢复 ${DataStore.courses.size} 门课" }
                else { msg = "JSON 格式不对，没读到课程" }
            }
            Hairline(20.dp)
            SetRow("清空全部数据", "删除所有课程与备忘录", "清空", danger = true) { showReset = true }
        }

        // ---------------- 运行诊断 ----------------
        Sect("运行诊断")
        SettingCard {
            SetRow(
                "液态玻璃引擎",
                "点一下切换：完整 → 基础 → 关闭",
                when {
                    !s.liquidGlass || s.glassLevel <= 0 -> "关闭"
                    GlassRuntime.broken -> "已退回平面"
                    s.glassLevel >= 2 -> "完整 AGSL"
                    else -> "基础（无折射）"
                },
                danger = GlassRuntime.broken
            ) {
                val next = when {
                    !s.liquidGlass || s.glassLevel <= 0 -> 2
                    s.glassLevel >= 2 -> 1
                    else -> 0
                }
                CrashGuard.leaveSafeMode()
                GlassRuntime.reset()
                save(s.copy(glassLevel = next, liquidGlass = next > 0))
                msg = "液态玻璃：${CrashGuard.levelLabel(next)}"
            }
            Hairline(20.dp)
            SetRow(
                "上次启动阶段",
                "崩在启动的哪一步，一眼能看出来",
                CrashGuard.stageLabel(CrashGuard.lastStage())
            ) {}
            Hairline(20.dp)
            SetRow(
                "上次崩溃记录",
                "没有 adb，只能靠这份文件取证",
                if (crashText == null) "无" else "查看"
            ) { if (crashText == null) msg = "没有崩溃记录" else showCrash = true }
            Hairline(20.dp)
            SetRow("清空崩溃记录", "删除本地保存的堆栈", "清空") {
                CrashGuard.clearCrash(); msg = "已清空"
            }
        }

        // ---------------- 关于 ----------------
        Sect("关于")
        SettingCard {
            SetRow(
                "课表 App", "v5.4.1test · Compose · Liquid Glass", "v5.4.1test"
            ) {}
        }
    }

    editField?.let { f ->
        InputDialog(f) { editField = null }
    }
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("清空全部数据？") },
            text = { Text("所有课程与备忘都会被删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    DataStore.resetAll(); showReset = false; msg = "已清空"
                }) { Text("清空", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("取消") } }
        )
    }
    if (showCrash) {
        val text = crashText.orEmpty()
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text("上次崩溃记录") },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text.take(4000), fontSize = 11.sp, lineHeight = 16.sp,
                        color = colors.ink2
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clip.setText(AnnotatedString(text)); showCrash = false; msg = "已复制崩溃记录"
                }) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { showCrash = false }) { Text("关闭") } }
        )
    }
    msg?.let {
        AlertDialog(
            onDismissRequest = { msg = null },
            title = { Text(it) },
            confirmButton = { TextButton(onClick = { msg = null }) { Text("好") } }
        )
    }
}

private fun shortUrl(u: String): String = if (u.length > 26) u.take(24) + "…" else u

// ---------------------------------------------------------------- 通用行

@Composable
private fun SettingCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
private fun SetRow(
    title: String,
    sub: String,
    value: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalKtColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title, fontSize = 15.sp, fontWeight = FontWeight.Normal,
            color = if (danger) Color(0xFFFF6B6B) else colors.ink1
        )
        Spacer(Modifier.weight(1f))
        Text(
            value, fontSize = 13.sp, maxLines = 1,
            color = if (danger) Color(0xFFFF6B6B) else colors.ink3
        )
    }
}

@Composable
private fun SetSwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalKtColors.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Normal, color = colors.ink1)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = if (colors.dark) Color(0x2AFFFFFF) else Color(0x1A000000),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

// ---------------------------------------------------------------- 文本输入弹窗

data class EditField(
    val title: String,
    val initial: String,
    val hint: String,
    val onDone: (String) -> Unit
)

@Composable
private fun InputDialog(f: EditField, onDismiss: () -> Unit) {
    val colors = LocalKtColors.current
    var v by remember { mutableStateOf(f.initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(f.title) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.controlBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (v.isEmpty()) Text(f.hint, fontSize = 14.sp, color = colors.ink4)
                androidx.compose.foundation.text.BasicTextField(
                    value = v, onValueChange = { v = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = colors.ink1),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { f.onDone(v.trim()); onDismiss() }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
