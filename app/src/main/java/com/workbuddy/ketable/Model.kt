package com.workbuddy.ketable

import androidx.compose.ui.graphics.Color

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ============================================================
// 数据模型：字段与 WebView 版 v4.1.0s 的 JSON 完全一致，
// 保证旧版本 localStorage 里的数据能原样迁移过来。
// ============================================================

/** 课程。day: 1=周一..7=周日，0=无固定时间 */
data class Course(
    val id: String = "",
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val day: Int = 1,
    val startSection: Int = 1,
    val endSection: Int = 2,
    val weekStart: Int = 1,
    val weekEnd: Int = 20,
    val parity: Int = 0,          // 0=每周 1=单周 2=双周
    val color: String = "purple",
    val icon: String = "",
    val remark: String = "",
    val credit: Double? = null,
    val score: Int? = null
)

/** 备忘。dueDate: "yyyy-MM-dd"，空表示无截止日期 */
data class Memo(
    val id: String = "",
    val text: String = "",
    val done: Boolean = false,
    val dueDate: String = "",
    val icon: String = ""
)

/** 设置。字段与 v4.1.0s 的 kt.settings.v1 对齐 */
data class Settings(
    val semesterStart: String = "2026-09-07",
    val remindLeadMin: Int = 15,
    val showWeekend: Boolean = false,
    val liquidGlass: Boolean = true,
    /** 玻璃等级：2=完整（AGSL 折射+色散）1=基础（模糊+放大+高光，不跑 AGSL）0=关 */
    val glassLevel: Int = 2,
    val themeMode: String = "system",   // system / light / dark
    val apiBase: String = "https://api.openai.com",
    val apiKey: String = "",
    val apiModel: String = "gpt-4o",
    val gradCredits: Int = 160,
    val seededVersion: Int = 1
)

// ============================================================
// 常量：节次时间、星期、色板
// ============================================================

object K {
    /** 10 节课的起止时间，与 WebView 版 SECTION_TIMES 一致 */
    val SECTION_TIMES = listOf(
        "08:30" to "09:15",
        "09:15" to "10:00",
        "10:15" to "11:00",
        "11:00" to "11:45",
        "13:00" to "13:45",
        "13:45" to "14:30",
        "14:45" to "15:30",
        "15:30" to "16:15",
        "18:30" to "19:15",
        "19:15" to "20:00"
    )

    /** 周视图按两节一组分行 */
    val PAIRS = listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10)

    val DOW_CN = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

    val PALETTE = listOf("orange", "blue", "green", "pink", "purple", "teal")

    /** 课程块背景透明度：玻璃模式下统一紫色系，颜色名只决定深浅（与 v4.1.0s 一致） */
    fun courseAlpha(color: String): Float = when (color) {
        "orange" -> 0.34f
        "blue" -> 0.38f
        "green" -> 0.30f
        "pink" -> 0.42f
        "purple" -> 0.46f
        "teal" -> 0.32f
        else -> 0.36f
    }

    /** 课程图标 emoji → 无 emoji 时按课程名猜一个，用于块内首字标识 */
    fun iconOf(c: Course): String = c.icon.ifBlank { c.name.take(1) }

    /** 课程色：统一走 Theme.kt 的 iOS 系统色板（每门课固定一色） */
    fun dotColor(color: String): Color = KtCourse.of(color)
}

// ============================================================
// 教学周计算：与 WebView 版 Schedule 逐条对齐
// ============================================================

object Sch {
    private val dfMd = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)

    fun today(): LocalDate = LocalDate.now()

    fun semStart(s: Settings): LocalDate =
        runCatching { LocalDate.parse(s.semesterStart) }.getOrDefault(LocalDate.of(2026, 9, 7))

    /** 教学周：开学日为第 1 周起点，每 7 天一周（与 WebView 版 floor(diff/7)+1 完全一致） */
    fun weekNo(date: LocalDate, s: Settings): Int {
        val diff = ChronoUnit.DAYS.between(semStart(s), date)
        return Math.floorDiv(diff, 7L).toInt() + 1
    }

    fun dow(date: LocalDate): Int = date.dayOfWeek.value

    /** 课程在某教学周是否出现（周次区间 + 单双周） */
    fun activeInWeek(c: Course, wk: Int): Boolean {
        if (c.day == 0) return false
        if (wk < c.weekStart || wk > c.weekEnd) return false
        if (c.parity == 1 && wk % 2 == 0) return false
        if (c.parity == 2 && wk % 2 == 1) return false
        return true
    }

    fun onDate(date: LocalDate, courses: List<Course>, s: Settings): List<Course> {
        val wk = weekNo(date, s)
        return courses.filter { it.day == dow(date) && activeInWeek(it, wk) }
            .sortedBy { it.startSection }
    }

    /** 某周的 7 天（周一开始） */
    fun weekDates(date: LocalDate): List<LocalDate> {
        val mon = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0..6).map { mon.plusDays(it.toLong()) }
    }

    fun courseStartMin(c: Course): Int = t2min(K.SECTION_TIMES[(c.startSection - 1).coerceIn(0, 9)].first)
    fun courseEndMin(c: Course): Int = t2min(K.SECTION_TIMES[(c.endSection - 1).coerceIn(0, 9)].second)

    fun t2min(t: String): Int {
        val p = t.split(":")
        return p[0].toInt() * 60 + p.getOrElse(1) { "0" }.toInt()
    }

    fun secLabel(c: Course): String =
        if (c.day == 0) "待定"
        else if (c.startSection == c.endSection) "${c.startSection} 节" else "${c.startSection}–${c.endSection} 节"

    fun timeLabel(c: Course): String =
        if (c.day == 0) "时间待定"
        else K.SECTION_TIMES[(c.startSection - 1).coerceIn(0, 9)].first + "–" +
            K.SECTION_TIMES[(c.endSection - 1).coerceIn(0, 9)].second

    fun weekLabel(c: Course): String {
        var s = "${c.weekStart}–${c.weekEnd}周"
        if (c.parity == 1) s += "·单周"
        if (c.parity == 2) s += "·双周"
        return s
    }

    fun fmtDate(d: LocalDate): String = d.format(dfMd)

    fun fmtFull(d: LocalDate): String = "${d.year}年${d.monthValue}月${d.dayOfMonth}日"

    /** 今日状态：当前课 / 下一节（今天没有就向后扫 14 天） */
    data class TodayStatus(
        val list: List<Course>,
        val current: Course?,
        val next: Course?,
        val nextDate: LocalDate?,
        val nowMin: Int
    )

    fun todayStatus(now: java.time.LocalDateTime, courses: List<Course>, s: Settings): TodayStatus {
        val list = onDate(now.toLocalDate(), courses, s)
        val nowMin = now.hour * 60 + now.minute
        var current: Course? = null
        var next: Course? = null
        for (c in list) {
            val a = courseStartMin(c)
            val b = courseEndMin(c)
            if (nowMin >= a && nowMin < b) current = c
            else if (nowMin < a && next == null) next = c
        }
        var nextDate: LocalDate? = null
        if (next == null) {
            for (i in 1..14) {
                val d = now.toLocalDate().plusDays(i.toLong())
                val l = onDate(d, courses, s)
                if (l.isNotEmpty()) { next = l[0]; nextDate = d; break }
            }
        }
        return TodayStatus(list, current, next, nextDate, nowMin)
    }

    /** 倒计时文案：<60 分钟 → "N 分钟后"；<24 小时 → "N 小时 M 分钟后"；否则 "N 天 M 小时后" */
    fun countdownText(now: java.time.LocalDateTime, st: TodayStatus): String? {
        val next = st.next ?: return null
        val base = st.nextDate ?: now.toLocalDate()
        val target = java.time.LocalDateTime.of(
            base,
            LocalTime.of(courseStartMin(next) / 60, courseStartMin(next) % 60)
        )
        val mins = java.time.Duration.between(now, target).toMinutes()
        if (mins < 0) return null
        if (mins < 60) return "$mins 分钟后"
        val h = mins / 60
        if (h < 24) {
            val rest = (mins % 60).toInt()
            return if (rest == 0) "$h 小时后" else "$h 小时 $rest 分钟后"
        }
        val d = h / 24
        val rest = (h % 24).toInt()
        return if (rest == 0) "$d 天后" else "$d 天 $rest 小时后"
    }
}
