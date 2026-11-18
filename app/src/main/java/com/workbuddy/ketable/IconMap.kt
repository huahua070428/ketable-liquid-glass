package com.workbuddy.ketable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * emoji → 单色矢量图标。
 *
 * v4.1.0s 的渲染层把课程 emoji 换成 Lucide 线条图标（数据层仍存 emoji），
 * 原生版照做：数据里的 emoji 字段原样保留，显示时映射成 1.5px 描边的单色图标，
 * 这样换主题时图标自动跟随强调色，不会出现一堆彩色 emoji 打乱色调。
 */
object Ico {
    fun of(emoji: String): ImageVector = when (emoji.trim()) {
        "📐", "📏" -> Icons.Outlined.Straighten
        "💻", "🖥" -> Icons.Outlined.Computer
        "🔤", "🔠", "🌐" -> Icons.Outlined.Translate
        "📊", "📈", "📉" -> Icons.Outlined.BarChart
        "🤖" -> Icons.Outlined.SmartToy
        "⚽", "🏀", "🏃" -> Icons.Outlined.SportsSoccer
        "📕", "📗", "📘", "📙" -> Icons.Outlined.Book
        "📚", "📖" -> Icons.Outlined.MenuBook
        "🎨" -> Icons.Outlined.Palette
        "🎵", "🎶" -> Icons.Outlined.MusicNote
        "🧪", "⚗" -> Icons.Outlined.Science
        "🔬" -> Icons.Outlined.Biotech
        "🏫" -> Icons.Outlined.School
        "🌍" -> Icons.Outlined.Public
        "🧠" -> Icons.Outlined.Psychology
        "⚙", "⚙️" -> Icons.Outlined.Settings
        "💡" -> Icons.Outlined.Lightbulb
        "🗓", "📅" -> Icons.Outlined.CalendarMonth
        "💼" -> Icons.Outlined.Work
        else -> Icons.Outlined.Book
    }

    /** 课程没有 icon 时，用课程名首字当标识（比给所有课一个书本图标更有辨识度） */
    fun hasIcon(c: Course): Boolean = c.icon.isNotBlank()
}
