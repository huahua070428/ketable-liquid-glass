package com.workbuddy.ketable

import android.app.Activity
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 从 v4.1.0s（WebView 版）迁移数据。
 *
 * 旧版把课表存在 file:///android_asset/webapp/index.html 这个源的 localStorage 里，
 * 包名相同、签名相同的 APK 覆盖安装后，WebView 的数据目录仍在。
 * 这里用一个不可见的 WebView 打开同源的 migrate.html，把三个 key 读出来，
 * 写入原生 SharedPreferences —— 用户装完新版，课表原样还在。
 *
 * 只迁移一次（kt_native 里记 migrated 标记），且只在原生库为空时写入。
 */
object LegacyMigrator {
    private const val TAG = "LegacyMigrator"

    /** 持有引用：WebView 没挂到视图树上，不保活会被 GC 掉，回调永远不来 */
    private var holder: WebView? = null

    fun migrateIfNeeded(activity: Activity) {
        if (DataStore.migratedFromWebView()) return
        // 原生库已经有数据（用户已在新版里加过课）→ 不再覆盖，直接标记完成
        if (DataStore.courses.isNotEmpty() || DataStore.memos.isNotEmpty()) {
            DataStore.markMigrated(); return
        }
        try {
            val wv = WebView(activity)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.allowFileAccess = true
            wv.settings.allowContentAccess = false
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(READ_JS) { result ->
                        adopt(result)
                        DataStore.markMigrated()
                        holder?.destroy()
                        holder = null
                    }
                }
            }
            holder = wv
            wv.loadUrl("file:///android_asset/migrate.html")
        } catch (t: Throwable) {
            Log.w(TAG, "迁移失败，跳过：" + t.message)
            DataStore.markMigrated()
        }
    }

    private const val READ_JS = "(function(){try{return JSON.stringify({" +
            "c:localStorage.getItem('kt.courses.v1')," +
            "m:localStorage.getItem('kt.memos.v1')," +
            "s:localStorage.getItem('kt.settings.v1')})}catch(e){return ''}})()"

    private fun adopt(raw: String?) {
        if (raw.isNullOrBlank() || raw == "null") return
        runCatching {
            // evaluateJavascript 返回的是 JSON 字符串字面量，先剥一层
            val inner = JSONTokener(raw).nextValue()
            val text = if (inner is String) inner else raw
            if (text.isBlank()) return
            val o = JSONObject(text)
            val c = o.optString("c", "")
            val m = o.optString("m", "")
            val s = o.optString("s", "")
            val courses = DataStore.parseCourses(c)
            val memos = runCatching {
                // 备忘走同一套字段，复用 courses 的解析不合适，这里单独转
                val arr = org.json.JSONArray(m)
                (0 until arr.length()).map { i ->
                    val x = arr.getJSONObject(i)
                    Memo(
                        id = x.optString("id"), text = x.optString("text", ""),
                        done = x.optBoolean("done", false),
                        dueDate = x.optString("dueDate", ""),
                        icon = x.optString("icon", "")
                    )
                }
            }.getOrDefault(emptyList())
            val settings = if (s.isBlank()) null else runCatching {
                val o2 = JSONObject(s)
                Settings(
                    semesterStart = o2.optString("semesterStart", "2026-09-07"),
                    remindLeadMin = o2.optInt("remindLeadMin", 15),
                    showWeekend = o2.optBoolean("showWeekend", false),
                    liquidGlass = o2.optBoolean("liquidGlass", true),
                    themeMode = o2.optString("themeMode", "system"),
                    apiBase = o2.optString("apiBase", "https://api.openai.com"),
                    apiKey = o2.optString("apiKey", ""),
                    apiModel = o2.optString("apiModel", "gpt-4o"),
                    gradCredits = o2.optInt("gradCredits", 160),
                    seededVersion = o2.optInt("seededVersion", 1)
                )
            }.getOrNull()
            Log.i(TAG, "迁移：${courses.size} 门课 / ${memos.size} 条备忘")
            DataStore.adoptLegacy(courses, memos, settings)
        }.onFailure { Log.w(TAG, "解析旧数据失败：" + it.message) }
    }
}
