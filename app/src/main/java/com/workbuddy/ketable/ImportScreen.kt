package com.workbuddy.ketable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 导入课程表：文字粘贴解析（离线可用）、JSON 恢复、手动添加。
 * 原生相机识图还在接，页面里如实说明，不用假的「识别中」糊弄。
 */
@Composable
fun ImportScreen(onNav: (Route) -> Unit, onBack: () -> Unit) {
    val colors = LocalKtColors.current
    val clip = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(true) }
    val configured = DataStore.settings.apiKey.isNotBlank()

    ScreenScroll(dockVisible = false) {
        PageHead("导入课程表", "三种方式，任选其一", onBack)

        // 1. 拍照识图
        GlassCard(
            Modifier.fillMaxWidth(), corner = 20.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("① 拍照 / 截图识别", fontSize = 14.5.sp, fontWeight = FontWeight(700), color = colors.ink1)
                    Spacer(Modifier.weight(1f))
                    Tag(if (configured) "接口已就绪" else "未配置接口", configured)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "原生相机识图这一版还没接完，现在点进去也识别不了。\n先用下面两种方式，识图会在下一版补。",
                    fontSize = 11.5.sp, color = colors.ink3, lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                GhostBtn(if (configured) "查看 AI 接口设置" else "去设置 AI 接口") { onNav(Route.Settings) }
            }
        }

        // 2. 文字粘贴导入
        Sect("② 粘贴文字导入（离线可用）")
        GlassCard(
            Modifier.fillMaxWidth(), corner = 20.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "每行一门课，顺序是「课程名 周几 第几节 地点」，例如：\n高等数学 周一 1-2节 教一101\n大学英语 周三 3-4节 外语楼302",
                    fontSize = 11.sp, color = colors.ink3, lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.controlBg)
                        .padding(12.dp)
                ) {
                    if (text.isEmpty()) {
                        Text("把课表文字粘贴到这里…", fontSize = 13.sp, color = colors.ink4)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = colors.ink1, lineHeight = 20.sp),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Box(Modifier.weight(1f)) {
                        GhostBtn("贴剪贴板") {
                            val t = clip.getText()?.text
                            if (t.isNullOrBlank()) { msg = "剪贴板是空的"; ok = false }
                            else text = t
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        PrimaryBtn("解析并导入") {
                            val parsed = TimetableText.parse(text)
                            if (parsed.isEmpty()) {
                                msg = "没认出课程。每行需要包含「周一」和「1-2节」。"
                                ok = false
                            } else {
                                DataStore.saveCourses(DataStore.courses + parsed)
                                msg = "已导入 ${parsed.size} 门课，去课表看看"
                                ok = true
                                text = ""
                            }
                        }
                    }
                }
            }
        }

        // 3. JSON 恢复 / 手动
        Sect("③ 其他方式")
        GlassCard(
            Modifier.fillMaxWidth(), corner = 20.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val t = clip.getText()?.text
                        if (t.isNullOrBlank()) { msg = "剪贴板是空的"; ok = false }
                        else if (DataStore.importJson(t)) {
                            msg = "已恢复 ${DataStore.courses.size} 门课"; ok = true
                        } else { msg = "JSON 格式不对，没读到课程"; ok = false }
                    }.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("从剪贴板恢复 JSON", fontSize = 14.5.sp, fontWeight = FontWeight(600), color = colors.ink1)
                        Spacer(Modifier.height(3.dp))
                        Text("覆盖导入，用于换手机迁移", fontSize = 11.sp, color = colors.ink3)
                    }
                    Text("导入 ›", fontSize = 12.sp, color = colors.ink3)
                }
                Hairline(16.dp)
                Row(
                    Modifier.fillMaxWidth().clickable { onNav(Route.Edit("new")) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("手动添加一门课", fontSize = 14.5.sp, fontWeight = FontWeight(600), color = colors.ink1)
                        Spacer(Modifier.height(3.dp))
                        Text("字段最全，支持单双周、跨节次", fontSize = 11.sp, color = colors.ink3)
                    }
                    Text("去添加 ›", fontSize = 12.sp, color = colors.ink3)
                }
            }
        }

        msg?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontSize = 12.sp, color = if (ok) colors.accent else Color(0xFFFF6B6B))
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "提示：导入不会删除已有课程，只有「从剪贴板恢复 JSON」是整体覆盖。",
            fontSize = 10.5.sp, color = colors.ink4, lineHeight = 16.sp
        )
    }
}

@Composable
private fun Tag(text: String, on: Boolean) {
    val colors = LocalKtColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) colors.accentSoft else colors.controlBg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, color = if (on) colors.accent else colors.ink3)
    }
}

/**
 * 极简课表文本解析："高等数学 周一 1-2节 教一101 张老师"
 * 只要行里能同时找到「周X」和「a-b节」，就认下来；其余当课程名/地点。
 */
object TimetableText {

    private val dayRx = Regex("(?:周|星期|礼拜)([一二三四五六日天])")
    private val secRx = Regex("(\\d{1,2})\\s*[-–—~至到]\\s*(\\d{1,2})\\s*节?")
    private val singleSecRx = Regex("第?\\s*(\\d{1,2})\\s*节")

    fun parse(raw: String): List<Course> {
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val l = line.trim()
            if (l.length < 4) return@mapNotNull null

            val dm = dayRx.find(l) ?: return@mapNotNull null
            val day = "一二三四五六日".indexOf(dm.groupValues[1]).let { if (it < 0) 7 else it + 1 }

            val sm = secRx.find(l)
            val s1: Int
            val s2: Int
            if (sm != null) {
                s1 = sm.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                s2 = sm.groupValues[2].toIntOrNull() ?: s1
            } else {
                val single = singleSecRx.find(l) ?: return@mapNotNull null
                s1 = single.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                s2 = (s1 + 1).coerceAtMost(10)
            }
            if (s1 !in 1..10 || s2 !in 1..10) return@mapNotNull null

            val name = l.substring(0, dm.range.first)
                .trim().trim('，', ',', ':', '：', '-', '—')
                .ifBlank { return@mapNotNull null }
            val lastIdx: Int = sm?.range?.last ?: singleRange(l)?.last ?: dm.range.last
            val rest = l.substring((lastIdx + 1).coerceAtMost(l.length))
                .trim().trim('，', ',', ':', '：')

            Course(
                name = normalizeName(name),
                day = day,
                startSection = s1,
                endSection = s2.coerceAtLeast(s1),
                weekStart = 1,
                weekEnd = 16,
                location = rest,
                color = K.PALETTE[((name.hashCode() % K.PALETTE.size) + K.PALETTE.size) % K.PALETTE.size]
            )
        }
    }

    private fun singleRange(l: String): IntRange? = singleSecRx.find(l)?.range

    /** "高等数学(必修)" 这类括号说明留着反而让周视图更挤，去掉括号里的内容 */
    private fun normalizeName(s: String): String =
        s.replace(Regex("[（(].*?[)）]"), "").trim().ifBlank { s }
}
