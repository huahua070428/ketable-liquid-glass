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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MemoScreen(onNav: (Route) -> Unit) {
    val colors = LocalKtColors.current
    val memos = DataStore.memos
    var input by remember { mutableStateOf("") }

    val todo = memos.filter { !it.done }
    val done = memos.filter { it.done }

    ScreenScroll {
        PageHead("备忘", "作业、考试、要带的东西，随手记")

        // 快速添加
        GlassCard(
            Modifier.fillMaxWidth(), corner = 18.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (input.isEmpty()) {
                        Text("写点什么…", fontSize = 14.sp, color = colors.ink4)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = colors.ink1),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (input.isBlank()) colors.controlBg else colors.accent)
                        .clickable(enabled = input.isNotBlank()) {
                            DataStore.upsertMemo(Memo(text = input.trim()))
                            input = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("＋", fontSize = 17.sp, color = if (input.isBlank()) colors.ink3 else Color.White)
                }
            }
        }

        Sect("待办（${todo.size}）")
        GlassCard(
            Modifier.fillMaxWidth(), corner = 20.dp,
            tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
            light = colors.shaderLight()
        ) {
            if (todo.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(26.dp), contentAlignment = Alignment.Center) {
                    Text("没有待办，清爽", fontSize = 13.sp, color = colors.ink3)
                }
            } else {
                Column {
                    todo.forEachIndexed { i, m ->
                        if (i > 0) Hairline()
                        MemoItem(
                            m = m,
                            onToggle = { DataStore.upsertMemo(m.copy(done = !m.done)) },
                            onDelete = { DataStore.deleteMemo(m.id) }
                        )
                    }
                }
            }
        }

        if (done.isNotEmpty()) {
            Sect("已完成（${done.size}）")
            GlassCard(
                Modifier.fillMaxWidth(), corner = 20.dp,
                tiltX = GlassEnv.tiltX, tiltY = GlassEnv.tiltY, screen = GlassEnv.screenSize,
                light = colors.shaderLight()
            ) {
                Column {
                    done.forEachIndexed { i, m ->
                        if (i > 0) Hairline()
                        MemoItem(
                            m = m,
                            onToggle = { DataStore.upsertMemo(m.copy(done = !m.done)) },
                            onDelete = { DataStore.deleteMemo(m.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoItem(m: Memo, onToggle: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalKtColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(22.dp).clip(RoundedCornerShape(7.dp))
                .background(if (m.done) colors.accent else colors.controlBg)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (m.done) Text("✓", fontSize = 13.sp, color = Color.White)
        }
        Text(
            m.text, fontSize = 14.sp,
            color = if (m.done) colors.ink4 else colors.ink1,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(26.dp).clickable { onDelete() }, contentAlignment = Alignment.Center) {
            Text("✕", fontSize = 12.sp, color = colors.ink4)
        }
    }
}
