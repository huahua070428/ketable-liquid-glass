package com.workbuddy.ketable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class UIMsg(val role: String, val text: String, val hint: Boolean = false)

/**
 * AI 助手：配了接口就走模型，没配接口也能用本地引擎答课表问题。
 * 这样「AI」这个 tab 在没 API Key 的手机上不是死页面。
 */
@Composable
fun AiScreen(onNav: (Route) -> Unit) {
    val colors = LocalKtColors.current
    val s = DataStore.settings
    var msgs by remember { mutableStateOf(listOf<UIMsg>()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSettingsLink by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val chips = listOf(
        "下一节是什么课？", "我明天几点上课？", "今天要带什么？",
        "这周有多少节课？", "周三在哪上课？", "哪天没课？"
    )

    fun send(raw: String) {
        val t = raw.trim()
        if (t.isEmpty() || busy) return
        msgs = msgs + UIMsg("user", t)
        input = ""

        // 没配接口：先本地答，答不了再引导配置
        if (!AiClient.configured(s)) {
            val local = LocalQA.answer(t)
            if (local != null) {
                msgs = msgs + UIMsg("assistant", local)
            } else {
                msgs = msgs + UIMsg(
                    "assistant",
                    "这个问题要联网的 AI 才能答。\n\n课表类问题（比如「明天几点上课」「周三在哪上课」）现在就能直接问，不需要配置。",
                    hint = true
                )
                showSettingsLink = true
            }
            return
        }

        busy = true
        scope.launch {
            try {
                val reply = AiClient.chat(s, msgs.map { AiClient.Msg(it.role, it.text) })
                msgs = msgs + UIMsg("assistant", reply)
            } catch (e: Exception) {
                msgs = msgs + UIMsg(
                    "assistant",
                    "请求失败：${e.message}\n\n可以在「设置 → AI 接口」检查地址、Key 和模型名。",
                    hint = true
                )
                showSettingsLink = true
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(msgs.size, busy) {
        if (msgs.isNotEmpty()) {
            runCatching { listState.animateScrollToItem((if (busy) msgs.size else msgs.size - 1).coerceAtLeast(0)) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp,
                top = (HEADER_H + 14).dp, bottom = 10.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 快捷问句跟着列表一起往上滚，从页头玻璃下面穿过去
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { c ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.controlBg)
                                .clickable { send(c) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(c, fontSize = 11.5.sp, color = colors.ink2, maxLines = 1)
                        }
                    }
                }
            }
            if (msgs.isEmpty()) {
                item { AiHello(configured = AiClient.configured(s)) }
            }
            items(msgs) { m -> Bubble(m) }
            if (busy) {
                item {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.cardTint)
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Text("正在想…", fontSize = 12.5.sp, color = colors.ink3)
                    }
                }
            }
            if (showSettingsLink) {
                item {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accentSoft)
                            .clickable { onNav(Route.Settings) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("去设置 AI 接口 →", fontSize = 12.sp, fontWeight = FontWeight(700), color = colors.accent)
                    }
                }
            }
        }

        // 输入栏：AI 是 tab 页，底部要让开 Dock
        Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 106.dp)) {
            GlassCard(
                Modifier.fillMaxWidth(), corner = 20.dp,
                tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
                light = colors.shaderLight()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        if (input.isEmpty()) {
                            Text("问点什么…", fontSize = 13.5.sp, color = colors.ink4)
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(fontSize = 13.5.sp, color = colors.ink1),
                            cursorBrush = SolidColor(colors.accent),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (input.isBlank() || busy) colors.controlBg else colors.accent)
                            .clickable(enabled = input.isNotBlank() && !busy) { send(input) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "↑", fontSize = 16.sp, fontWeight = FontWeight(800),
                            color = if (input.isBlank() || busy) colors.ink3 else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiHello(configured: Boolean) {
    val colors = LocalKtColors.current
    GlassCard(
        Modifier.fillMaxWidth(), corner = 20.dp,
        tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
        light = colors.shaderLight()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("我能帮你做什么", fontSize = 15.sp, fontWeight = FontWeight(800), color = colors.ink1)
            Spacer(Modifier.height(8.dp))
            Line("问课表（不需要联网）", "「周三在哪上课」「今天要带什么书」「这周几节课」")
            Line("排计划 / 讲知识点 / 拍题答疑", "需要先在「设置 → AI 接口」填地址和 Key")
            Spacer(Modifier.height(6.dp))
            Text(
                if (configured) "已配置接口，直接问就行。" else "现在没配接口，课表类问题仍然可以问。",
                fontSize = 11.sp, color = colors.ink4
            )
        }
    }
}

@Composable
private fun Line(title: String, sub: String) {
    val colors = LocalKtColors.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, fontSize = 12.5.sp, fontWeight = FontWeight(700), color = colors.ink2)
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 11.sp, color = colors.ink3, lineHeight = 16.sp)
    }
}

@Composable
private fun Bubble(m: UIMsg) {
    val colors = LocalKtColors.current
    val me = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (me) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (me) 16.dp else 5.dp,
                        bottomEnd = if (me) 5.dp else 16.dp
                    )
                )
                .background(if (me) colors.accent else colors.cardTint)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                mdText(m.text),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = if (me) Color.White else colors.ink1
            )
        }
    }
}

/** 极简 markdown：只认 **加粗**，够用且不会把长文本渲染错 */
private fun mdText(src: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    src.split("**").forEachIndexed { i, part ->
        if (i > 0) bold = !bold
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        else append(part)
    }
}
