package com.workbuddy.ketable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * OpenAI 兼容接口调用（与 v4.1.0s 的 js/ai.js 同一个协议）。
 * 课表事实放在 system 里，避免模型瞎编课表；历史只带最近 12 条，控制 token。
 */
object AiClient {

    data class Msg(val role: String, val content: String)

    fun configured(s: Settings): Boolean = s.apiKey.isNotBlank()

    /** 把课表塞进 system，模型回答就有依据 */
    fun systemPrompt(): String {
        val now = LocalDateTime.now()
        val s = DataStore.settings
        val wk = Sch.weekNo(now.toLocalDate(), s)
        val week = Sch.weekDates(now.toLocalDate()).joinToString("\n") { d ->
            val l = Sch.onDate(d, DataStore.courses, s)
            val dow = K.DOW_CN[d.dayOfWeek.value].removePrefix("周")
            "${d.monthValue}/${d.dayOfMonth} 周$dow：" + if (l.isEmpty()) "无课"
            else l.joinToString("、") { "${it.name}(${Sch.timeLabel(it)}${if (it.location.isNotBlank()) "·" + it.location else ""})" }
        }
        val bank = DataStore.courses.map { c ->
            "${c.name}｜${K.DOW_CN[c.day.coerceIn(0, 7)].ifBlank { "时间待定" }}｜${Sch.timeLabel(c)}｜" +
                    "${c.location.ifBlank { "地点待定" }}｜${c.teacher.ifBlank { "老师待定" }}｜${Sch.weekLabel(c)}"
        }.joinToString("\n").ifBlank { "（课表为空）" }

        return "你是课表 App 内置的学习助手，服务对象是一名在校大学生。" +
                "回答要短、准、可执行，优先用要点。涉及课表的问题必须依据下面给出的课表事实回答，不要编造；" +
                "课表里没有的信息要直说不知道。不要输出与学习无关的内容。\n\n" +
                "【学生课表事实】\n今天：${Sch.fmtFull(now.toLocalDate())} " +
                "${K.DOW_CN[now.dayOfWeek.value]}，第 $wk 教学周\n" +
                "本周安排：\n$week\n\n全部课程：\n$bank"
    }

    suspend fun chat(s: Settings, history: List<Msg>): String = withContext(Dispatchers.IO) {
        val base = s.apiBase.trim().trimEnd('/').ifBlank { "https://api.openai.com" }
        val url = when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
        val payload = JSONObject().apply {
            put("model", s.apiModel.ifBlank { "gpt-4o" })
            put("temperature", 0.6)
            put("max_tokens", 900)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt()))
                history.takeLast(12).forEach {
                    put(JSONObject().put("role", it.role).put("content", it.content))
                }
            })
        }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${s.apiKey}")
        }
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw RuntimeException("接口返回 $code：${body.take(160)}")
            }
            val o = JSONObject(body)
            val content = o.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) throw RuntimeException("接口没有返回内容")
            content
        } finally {
            conn.disconnect()
        }
    }

    /** 给"今天/周"这类相对日期用的辅助：把 LocalDate 变成课表里的说法 */
    fun dayLabel(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日 ${K.DOW_CN[d.dayOfWeek.value]}"
}
