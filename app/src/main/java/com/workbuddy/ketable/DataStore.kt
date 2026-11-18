package com.workbuddy.ketable

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 原生数据层：SharedPreferences 里存 JSON 字符串，结构对齐 WebView 版
 * （kt.courses.v1 / kt.memos.v1 / kt.settings.v1 同名字段），
 * 这样旧版本的数据能原样迁移过来，导出 JSON 也互相兼容。
 */
object DataStore {
    private const val PREF = "kt_native"
    private const val KEY_COURSES = "courses"
    private const val KEY_MEMOS = "memos"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_MIGRATED = "migrated_from_webview"

    private var sp: SharedPreferences? = null

    var courses by mutableStateOf<List<Course>>(emptyList()); private set
    var memos by mutableStateOf<List<Memo>>(emptyList()); private set
    var settings by mutableStateOf(Settings()); private set
    var ready by mutableStateOf(false); private set

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp = p
        courses = parseCourses(p.getString(KEY_COURSES, null))
        memos = parseMemos(p.getString(KEY_MEMOS, null))
        settings = parseSettings(p.getString(KEY_SETTINGS, null))
        ready = true
    }

    fun migratedFromWebView(): Boolean = sp?.getBoolean(KEY_MIGRATED, false) ?: false
    fun markMigrated() { sp?.edit()?.putBoolean(KEY_MIGRATED, true)?.apply() }

    /** 迁移时只在原生库为空的情况下写入，绝不覆盖用户新加的数据 */
    fun adoptLegacy(c: List<Course>, m: List<Memo>, s: Settings?) {
        if (courses.isEmpty() && c.isNotEmpty()) {
            courses = c
            sp?.edit()?.putString(KEY_COURSES, coursesToJson(c))?.apply()
        }
        if (memos.isEmpty() && m.isNotEmpty()) {
            memos = m
            sp?.edit()?.putString(KEY_MEMOS, memosToJson(m))?.apply()
        }
        if (s != null) {
            settings = s
            sp?.edit()?.putString(KEY_SETTINGS, settingsToJson(s))?.apply()
        }
    }

    // ---------------- 课程 ----------------

    fun saveCourses(list: List<Course>) {
        courses = list
        sp?.edit()?.putString(KEY_COURSES, coursesToJson(list))?.apply()
    }

    fun upsertCourse(c: Course) {
        val fixed = if (c.id.isBlank()) c.copy(id = UUID.randomUUID().toString()) else c
        val list = courses.toMutableList()
        val i = list.indexOfFirst { it.id == fixed.id }
        if (i >= 0) list[i] = fixed else list.add(fixed)
        saveCourses(list)
    }

    fun deleteCourse(id: String) = saveCourses(courses.filterNot { it.id == id })

    fun course(id: String): Course? = courses.firstOrNull { it.id == id }

    // ---------------- 备忘 ----------------

    fun saveMemos(list: List<Memo>) {
        memos = list
        sp?.edit()?.putString(KEY_MEMOS, memosToJson(list))?.apply()
    }

    fun upsertMemo(m: Memo) {
        val fixed = if (m.id.isBlank()) m.copy(id = UUID.randomUUID().toString()) else m
        val list = memos.toMutableList()
        val i = list.indexOfFirst { it.id == fixed.id }
        if (i >= 0) list[i] = fixed else list.add(fixed)
        saveMemos(list)
    }

    fun deleteMemo(id: String) = saveMemos(memos.filterNot { it.id == id })

    // ---------------- 设置 ----------------

    fun saveSettings(s: Settings) {
        settings = s
        sp?.edit()?.putString(KEY_SETTINGS, settingsToJson(s))?.apply()
    }

    /** 从导出的 JSON 恢复（设置页「导入数据」用） */
    fun importJson(text: String): Boolean = runCatching {
        val o = JSONObject(text)
        val arr = o.optJSONArray("courses") ?: return false
        val list = parseCourses(arr.toString())
        if (list.isEmpty()) return false
        saveCourses(list)
        o.optJSONArray("memos")?.let { m ->
            val ms = (0 until m.length()).map { i ->
                val x = m.getJSONObject(i)
                Memo(
                    id = x.optString("id", UUID.randomUUID().toString()),
                    text = x.optString("text", ""),
                    done = x.optBoolean("done", false),
                    dueDate = x.optString("dueDate", ""),
                    icon = x.optString("icon", "")
                )
            }
            saveMemos(ms)
        }
        true
    }.getOrDefault(false)

    fun resetAll() {
        courses = emptyList(); memos = emptyList(); settings = Settings()
        sp?.edit()?.remove(KEY_COURSES)?.remove(KEY_MEMOS)?.remove(KEY_SETTINGS)?.apply()
    }

    // ---------------- JSON ----------------

    fun coursesToJson(list: List<Course>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("teacher", c.teacher)
                put("location", c.location); put("day", c.day)
                put("startSection", c.startSection); put("endSection", c.endSection)
                put("weekStart", c.weekStart); put("weekEnd", c.weekEnd)
                put("parity", c.parity); put("color", c.color); put("icon", c.icon)
                put("remark", c.remark)
                if (c.credit != null) put("credit", c.credit) else put("credit", JSONObject.NULL)
                if (c.score != null) put("score", c.score) else put("score", JSONObject.NULL)
            })
        }
        return arr.toString()
    }

    fun parseCourses(raw: String?): List<Course> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Course(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", ""),
                    teacher = o.optString("teacher", ""),
                    location = o.optString("location", ""),
                    day = o.optInt("day", 1),
                    startSection = o.optInt("startSection", 1),
                    endSection = o.optInt("endSection", 2),
                    weekStart = o.optInt("weekStart", 1),
                    weekEnd = o.optInt("weekEnd", 20),
                    parity = o.optInt("parity", 0),
                    color = o.optString("color", "purple"),
                    icon = o.optString("icon", ""),
                    remark = o.optString("remark", ""),
                    credit = if (o.isNull("credit")) null else o.optDouble("credit"),
                    score = if (o.isNull("score")) null else o.optInt("score")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun memosToJson(list: List<Memo>): String {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id); put("text", m.text); put("done", m.done)
                put("dueDate", m.dueDate); put("icon", m.icon)
            })
        }
        return arr.toString()
    }

    private fun parseMemos(raw: String?): List<Memo> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Memo(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    text = o.optString("text", ""),
                    done = o.optBoolean("done", false),
                    dueDate = o.optString("dueDate", ""),
                    icon = o.optString("icon", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun settingsToJson(s: Settings): String = JSONObject().apply {
        put("semesterStart", s.semesterStart)
        put("remindLeadMin", s.remindLeadMin)
        put("showWeekend", s.showWeekend)
        put("liquidGlass", s.liquidGlass)
        put("glassLevel", s.glassLevel)
        put("themeMode", s.themeMode)
        put("apiBase", s.apiBase)
        put("apiKey", s.apiKey)
        put("apiModel", s.apiModel)
        put("gradCredits", s.gradCredits)
        put("seededVersion", s.seededVersion)
    }.toString()

    private fun parseSettings(raw: String?): Settings {
        if (raw.isNullOrBlank()) return Settings()
        return runCatching {
            val o = JSONObject(raw)
            Settings(
                semesterStart = o.optString("semesterStart", "2026-09-07"),
                remindLeadMin = o.optInt("remindLeadMin", 15),
                showWeekend = o.optBoolean("showWeekend", false),
                liquidGlass = o.optBoolean("liquidGlass", true),
                glassLevel = o.optInt("glassLevel", 2),
                themeMode = o.optString("themeMode", "system"),
                apiBase = o.optString("apiBase", "https://api.openai.com"),
                apiKey = o.optString("apiKey", ""),
                apiModel = o.optString("apiModel", "gpt-4o"),
                gradCredits = o.optInt("gradCredits", 160),
                seededVersion = o.optInt("seededVersion", 1)
            )
        }.getOrDefault(Settings())
    }
}
