package com.workbuddy.ketable

import java.time.LocalDate

/**
 * 本地问答引擎：不联网、不花 token 也能回答课表类问题（对齐 v4.1.0s 的 localqa.js）。
 * AI 页在没配接口时先走这里，命中就直接答；答不了才引导去设置接口。
 */
object LocalQA {

    private data class Target(val date: LocalDate, val label: String, val weekend: Boolean = false)

    private fun addDays(d: LocalDate, n: Long): LocalDate = d.plusDays(n)

    private fun target(q: String): Target? {
        val now = LocalDate.now()
        if ("后天" in q) return Target(addDays(now, 2), "后天")
        if ("明天" in q || "明日" in q || "次日" in q) return Target(addDays(now, 1), "明天")
        if ("今天" in q || "今日" in q || "现在" in q) return Target(now, "今天")

        val m = Regex("(?:周|星期|礼拜)([一二三四五六日天末])").find(q) ?: return null
        val t = m.groupValues[1]
        if (t == "末") {
            val dow = now.dayOfWeek.value
            // 本周六；今天已经是周日就取今天
            val saturday = if (dow <= 6) addDays(now, (6 - dow).toLong()) else now
            return Target(saturday, "周末", weekend = true)
        }
        val want = if (t == "天") 7 else "一二三四五六日".indexOf(t) + 1
        var delta = want - now.dayOfWeek.value
        if (delta <= 0) delta += 7
        return Target(addDays(now, delta.toLong()), "周$t")
    }

    private fun list(date: LocalDate): List<Course> =
        Sch.onDate(date, DataStore.courses, DataStore.settings)

    private fun fmtMd(d: LocalDate): String =
        "${d.monthValue}月${d.dayOfMonth}日 周${K.DOW_CN[d.dayOfWeek.value].removePrefix("周")}"

    private fun courseLine(c: Course): String =
        "**${c.name}**\n${Sch.timeLabel(c)} · ${c.location.ifBlank { "地点待定" }}" +
                (if (c.teacher.isNotBlank()) " · ${c.teacher}" else "") +
                (if (c.remark.isNotBlank()) " · ${c.remark}" else "")

    private fun has(q: String, vararg keys: String): Boolean = keys.any { it in q }

    /** 命中返回答案，未命中返回 null */
    fun answer(q: String): String? {
        val text = q.trim()
        if (text.isEmpty()) return null
        val t = target(text)
        val day = t?.date ?: LocalDate.now()
        val list = when {
            t == null -> emptyList()
            t.weekend -> list(day) + list(addDays(day, 1))
            else -> list(day)
        }
        val label = t?.label ?: "今天"

        // —— 下一节课 ——
        if (has(text, "下一节", "下节课", "接下来", "快上课", "马上上")) {
            val st = Sch.todayStatus(java.time.LocalDateTime.now(), DataStore.courses, DataStore.settings)
            val cur = st.current
            if (cur != null) {
                val nx = st.next
                return "现在正在上 **${cur.name}**（${Sch.timeLabel(cur)} · ${cur.location.ifBlank { "地点待定" }}）" +
                        if (nx != null) "\n\n下一节是 **${nx.name}**，${nx.location.ifBlank { "地点待定" }}，${Sch.timeLabel(nx)}。"
                        else "\n\n今天后面没有课啦。"
            }
            val nx = st.next ?: return "未来两周都没有排课。"
            return "下一节是 **${nx.name}**\n" +
                    (st.nextDate?.let { fmtMd(it) + " · " } ?: "") +
                    "${Sch.timeLabel(nx)} · ${nx.location.ifBlank { "地点待定" }}"
        }

        // —— 老师 ——
        if (has(text, "老师", "教师", "谁教")) {
            val named = DataStore.courses.firstOrNull { it.name.isNotBlank() && text.contains(it.name) }
            if (named != null) {
                return "**${named.name}** 的老师是 **${named.teacher.ifBlank { "未填写" }}**；" +
                        "上课时间：${K.DOW_CN[named.day.coerceIn(0, 7)].ifBlank { "时间待定" }} ${Sch.timeLabel(named)}，" +
                        "地点 ${named.location.ifBlank { "待定" }}。"
            }
            val pool = if (t != null) list else DataStore.courses
            val uniq = pool.map { it.teacher }.filter { it.isNotBlank() }.distinct()
            return if (uniq.isEmpty()) "课表里还没填老师信息，可以在课程编辑里补上。"
            else "${label}涉及的老师：${uniq.joinToString("、")}\n\n想问某门课的老师，直接说课程全名，例如「高等数学老师是谁」。"
        }

        // —— 地点 ——
        if (has(text, "在哪", "哪里上课", "在哪个", "教室", "地点", "哪个楼", "几号楼")) {
            if (list.isEmpty()) return "${label}没有课，不用去教室"
            return "**$label（${fmtMd(day)}）的上课地点**\n\n" +
                    list.joinToString("\n") { "· ${Sch.timeLabel(it)} **${it.name}** → ${it.location.ifBlank { "地点待定" }}" }
        }

        // —— 带什么 ——
        if (has(text, "带什么", "带哪些", "拿什么", "要带", "书本", "课本", "材料")) {
            if (list.isEmpty()) return "${label}没课，什么都不用带"
            return "**$label（${fmtMd(day)}）要带的东西**\n\n" +
                    list.joinToString("\n") { "· ${Sch.timeLabel(it)} **${it.name}**" + if (it.remark.isNotBlank()) "（${it.remark}）" else "" } +
                    "\n\n按课程准备课本和笔记本；老师另有要求的以老师为准。"
        }

        // —— 数量统计 ——
        if (has(text, "几节", "多少节", "几门", "多少门", "课时")) {
            if (has(text, "门", "课程数")) return "你的课表里共有 **${DataStore.courses.size} 门**课程。"
            if (t != null) {
                val n = list.sumOf { (it.endSection - it.startSection + 1).coerceAtLeast(1) }
                return "$label（${fmtMd(day)}）有 **${list.size} 门课、共 $n 节**（约 ${"%.1f".format(n * 45 / 60.0)} 小时）。"
            }
            val dates = Sch.weekDates(LocalDate.now())
            val n = dates.sumOf { d -> list(d).sumOf { (it.endSection - it.startSection + 1).coerceAtLeast(1) } }
            val cnt = dates.sumOf { d -> list(d).size }
            val wk = Sch.weekNo(LocalDate.now(), DataStore.settings)
            return "本周（第 $wk 周）共 **$cnt 门课、$n 节**（约 ${"%.1f".format(n * 45 / 60.0)} 小时）。\n\n想看更细的统计可以打开「今日 → 学习数据」。"
        }

        // —— 最早 / 最晚 ——
        if (has(text, "最早", "第一节课", "早上", "最晚", "最后一节")) {
            if (list.isEmpty()) return "${label}没有课。"
            val sorted = list.sortedBy { it.startSection }
            val c = if (has(text, "最晚", "最后")) sorted.last() else sorted.first()
            val which = if (has(text, "最晚", "最后")) "最晚" else "最早"
            return "${label}${which}的一节是 **${c.name}**，${Sch.timeLabel(c)}（${c.location.ifBlank { "地点待定" }}），${Sch.secLabel(c)}。"
        }

        // —— 空闲 ——
        if (has(text, "空闲", "没课", "没有课", "休息", "空堂") && t == null) {
            val now = LocalDate.now()
            val mon = Sch.weekDates(now).first()
            val free = (0..6).map { addDays(mon, it.toLong()) }
                .filter { list(it).isEmpty() }
                .map { K.DOW_CN[it.dayOfWeek.value] }
            return if (free.isEmpty()) "接下来一周每天都排了课，加油"
            else "接下来一周里没课的日子：**${free.joinToString("、")}**\n\n想找共同空闲时间的话，可以看「课表」页整体安排。"
        }

        // —— 通用：某天的课表 ——
        if (t != null || has(text, "课表", "安排", "有什么课", "哪些课", "上什么", "几点上课", "上课时间", "下课")) {
            if (list.isEmpty()) {
                var nextDay: Pair<LocalDate, List<Course>>? = null
                for (i in 1..7) {
                    val d = addDays(day, i.toLong())
                    val l = list(d)
                    if (l.isNotEmpty()) { nextDay = d to l; break }
                }
                return "$label（${fmtMd(day)}）没有课" + (
                        nextDay?.let { (d, l) ->
                            "\n\n最近一次有课是 **${fmtMd(d)}**：\n" +
                                    l.joinToString("\n") { "· " + courseLine(it).replace("\n", " ") }
                        } ?: ""
                        )
            }
            return "**$label（${fmtMd(day)}）的课程安排**\n\n" +
                    list.mapIndexed { i, c -> "${i + 1}. ${courseLine(c)}" }.joinToString("\n\n")
        }

        return null
    }
}
