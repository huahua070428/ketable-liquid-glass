package com.workbuddy.ketable

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 桌面小组件:下一节课。
 * 桌面小组件拿不到任意背景像素,这里的"玻璃感"是分层渐变模拟的
 * (小组件领域所有人的做法),真正的实时玻璃只在 App 内。
 */
class NextClassWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NextClassWidget()
}

@Composable
private fun WidgetContent() {
    GlanceTheme {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xE6121430)))
                .cornerRadius(20.dp)
                .padding(14.dp)
        ) {
            Text(
                "下一节",
                style = TextStyle(color = ColorProvider(Color(0x99FFFFFF)), fontSize = 12.sp)
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "毛概 · 倬云楼106",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFFFFFF)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "明天 08:30–10:00 · 卢敏华",
                style = TextStyle(color = ColorProvider(Color(0x80FFFFFF)), fontSize = 12.sp)
            )
        }
    }
}
