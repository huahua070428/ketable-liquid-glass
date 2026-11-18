package com.workbuddy.ketable

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃取证 + 逐级降级。
 *
 * 背景：v5.2 / v5.3 在真机上「一开液态玻璃就退」，但沙箱没有 GPU，
 * 那段绘制代码在本地任何测试里都跑不到（Robolectric 是软件渲染）。
 * 不能连 adb 的情况下，只剩两条路：
 *
 * 1. **取证**：在 Application 里注册 UncaughtExceptionHandler，
 *    把 Java 层堆栈 + 设备/GPU/阶段打点写进 filesDir/crash-last.txt；
 *    下次启动直接把内容弹出来（可一键复制）。
 *    如果进程是被原生层打死的（hwui abort / SIGSEGV），Java 处理器根本不会被调用，
 *    那就靠「启动进行标记」反推：标记还在 = 上次没走到首帧且没有 Java 堆栈
 *    → 判定为原生崩溃，并写明当时的阶段，这同样是有用的证据。
 *
 * 2. **降级**：玻璃等级 2（完整 AGSL）→ 1（只模糊，不跑 AGSL）→ 0（关闭）。
 *    每次坏启动掉一级，保证下一次一定能打开，也把问题缩到最小范围。
 */
object CrashGuard {
    private const val TAG = "CrashGuard"
    private const val PREF = "kt_crash"
    private const val KEY_PENDING = "boot_pending"
    private const val KEY_BAD = "bad_boots"
    private const val KEY_SAFE = "safe_mode"
    private const val KEY_STAGE = "stage"
    private const val KEY_STARTED_AT = "boot_started_at"
    private const val KEY_ACK_AT = "crash_ack_at"
    private const val KEY_PROBE = "glass_probe"

    private var appCtx: Context? = null
    private var stage: String = "app_create"
    private var installed = false

    /** 最近打入的过程点（崩溃时一起写进文件，用来还原死在哪一步） */
    private val trail = ArrayDeque<String>()

    /** 本次启动是否因为上次没起来而降了玻璃等级 */
    var enteredSafeMode: Boolean = false
        private set

    /** 降级原因，给启动弹窗用 */
    var degradeReason: String = ""
        private set

    // ------------------------------------------------------------ 安装

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        if (!DataStore.ready) DataStore.init(app)
        if (installed) return
        installed = true

        // 第一件事：接管未捕获异常（后面任何一步崩了都能留下东西）
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { dump(t, e) }
            runCatching { prev?.uncaughtException(t, e) }
        }

        val sp = prefs()
        val pending = sp?.getInt(KEY_PENDING, 0) ?: 0
        val startedAt = sp?.getLong(KEY_STARTED_AT, 0L) ?: 0L
        val prevStage = sp?.getString(KEY_STAGE, "unknown") ?: "unknown"

        val probe = sp?.getInt(KEY_PROBE, 0) ?: 0
        if (pending > 0) {
            val bad = (sp?.getInt(KEY_BAD, 0) ?: 0) + 1
            sp?.edit()?.putInt(KEY_BAD, bad)?.apply()

            // 上次没走到首帧。若这次也没留下「新的」Java 堆栈，
            // 说明它不是 Java 异常，而是原生层被打死 / 被系统杀 —— 也要留证。
            val f = crashFile()
            val freshJavaCrash = f != null && f.exists() &&
                    f.lastModified() >= startedAt && startedAt > 0L
            if (!freshJavaCrash) writeNoStackRecord(prevStage, bad)

            degrade(prevStage)
        } else if (probe > 0) {
            // 首帧已经过了，但「玻璃探针」还没撤：说明是画了玻璃之后才死的
            // （典型场景：切 tab、Dock 泡滑动的某一帧）。这一条专门兜住这种情况。
            val bad = (sp?.getInt(KEY_BAD, 0) ?: 0) + 1
            sp?.edit()?.putInt(KEY_BAD, bad)?.apply()
            writeProbeRecord(probe)
            degrade("glass")
        }
        sp?.edit()?.putInt(KEY_PROBE, 0)?.apply()

        sp?.edit()
            ?.putInt(KEY_PENDING, 1)
            ?.putLong(KEY_STARTED_AT, System.currentTimeMillis())
            ?.apply()

        mark("installed")
    }

    /** 按等级往下掉：2 → 1 → 0，掉下来的原因写进 degradeReason */
    private fun degrade(prevStage: String) {
        val s = DataStore.settings
        if (!s.liquidGlass) return
        val level = s.glassLevel.coerceIn(0, 2)
        if (level <= 0) return
        val next = level - 1
        DataStore.saveSettings(s.copy(glassLevel = next, liquidGlass = next > 0))
        prefs()?.edit()?.putBoolean(KEY_SAFE, true)?.apply()
        enteredSafeMode = true
        degradeReason = "上次启动停在「${stageLabel(prevStage)}」，" +
                "已把液态玻璃从${levelLabel(level)}降到${levelLabel(next)}"
        Log.w(TAG, degradeReason)
    }

    // ------------------------------------------------------------ 打点

    /** 过程点：崩溃时能知道死在哪一步 */
    fun mark(s: String) {
        stage = s
        val now = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
        trail.addLast("$now  $s")
        while (trail.size > 14) trail.removeFirst()
        runCatching { prefs()?.edit()?.putString(KEY_STAGE, s)?.apply() }
    }

    /**
     * 玻璃探针：开启玻璃时先立旗，稳定运行 10 秒才撤。
     * 只要下一次启动还看到这面旗，就说明「上次是画了玻璃之后死的」，
     * 不管它是第几帧死的、有没有 Java 堆栈 —— 都能自动降一级。
     */
    fun probeGlass(level: Int) {
        prefs()?.edit()?.putInt(KEY_PROBE, level)?.apply()
        mark("glass_enter")
    }

    fun clearProbe() {
        prefs()?.edit()?.putInt(KEY_PROBE, 0)?.apply()
        mark("glass_stable")
    }

    /** 首帧画完 → 这次启动算成功 */
    fun markBootOk() {
        val sp = prefs() ?: return
        sp.edit().putInt(KEY_PENDING, 0).putInt(KEY_BAD, 0).apply()
        if (DataStore.settings.liquidGlass) sp.edit().putBoolean(KEY_SAFE, false).apply()
        mark("running")
    }

    // ------------------------------------------------------------ 读取

    fun safeMode(): Boolean = prefs()?.getBoolean(KEY_SAFE, false) ?: false
    fun lastStage(): String = prefs()?.getString(KEY_STAGE, "unknown") ?: "unknown"
    fun badBoots(): Int = prefs()?.getInt(KEY_BAD, 0) ?: 0

    fun lastCrash(): String? {
        val f = crashFile() ?: return null
        return if (f.exists() && f.length() > 0) runCatching { f.readText() }.getOrNull() else null
    }

    /** 有没有「用户还没看过」的崩溃记录（看过=点过知道了） */
    fun hasUnreadCrash(): Boolean {
        val f = crashFile() ?: return false
        if (!f.exists() || f.length() == 0L) return false
        val ack = prefs()?.getLong(KEY_ACK_AT, 0L) ?: 0L
        return f.lastModified() > ack
    }

    fun ackCrash() {
        prefs()?.edit()?.putLong(KEY_ACK_AT, System.currentTimeMillis())?.apply()
    }

    fun clearCrash() {
        runCatching { crashFile()?.delete() }
        ackCrash()
    }

    fun leaveSafeMode() {
        prefs()?.edit()?.putBoolean(KEY_SAFE, false)?.putInt(KEY_BAD, 0)?.apply()
        enteredSafeMode = false
    }

    fun levelLabel(level: Int): String = when (level) {
        2 -> "完整（AGSL 折射）"
        1 -> "基础（仅模糊）"
        else -> "关闭"
    }

    fun stageLabel(s: String): String = when {
        s.startsWith("app") -> "启动初始化"
        s == "setContent" -> "创建界面"
        s.startsWith("glass") -> "液态玻璃绘制"
        s == "first_frame" -> "首帧渲染"
        s == "migrate" -> "旧数据迁移"
        s == "running" -> "正常运行"
        else -> s
    }

    // ------------------------------------------------------------ 内部

    private fun prefs() = appCtx?.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun crashFile(): File? = appCtx?.let { File(it.filesDir, "crash-last.txt") }

    private fun deviceInfo(): String {
        val sb = StringBuilder()
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" · Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("主板: ").append(Build.BOARD).append(" / ").append(Build.HARDWARE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                Build.SOC_MODEL?.takeIf { it.isNotBlank() }?.let { sb.append(" / SoC ").append(it) }
            }
        }
        sb.append('\n')
        // GPU 字符串：AGSL/模糊出问题，基本都跟这颗 GPU 有关
        runCatching {
            val am = appCtx?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val ci = am?.deviceConfigurationInfo
            sb.append("GLES: ").append(ci?.glEsVersion ?: "?").append('\n')
        }
        val s = DataStore.settings
        sb.append("液态玻璃: ").append(if (s.liquidGlass) levelLabel(s.glassLevel) else "关闭").append('\n')
        sb.append("坏启动计数: ").append(badBoots()).append('\n')
        return sb.toString()
    }

    /** Java 异常：写完整堆栈 */
    private fun dump(t: Thread, e: Throwable) {
        val f = crashFile() ?: return
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        val sb = StringBuilder()
        sb.append("== Java 异常 ==\n")
        sb.append("时间: ").append(ts).append('\n')
        sb.append("版本: ").append(BuildConfig.VERSION_NAME)
            .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        sb.append("阶段: ").append(stage).append("  · 线程: ").append(t.name).append('\n')
        sb.append(deviceInfo())
        sb.append("—— 过程点 ——\n")
        trail.forEach { sb.append("  ").append(it).append('\n') }
        sb.append("—— 堆栈 ——\n")
        sb.append(Log.getStackTraceString(e))
        write(f, sb.toString())
        Log.e(TAG, "crash dumped at stage=$stage", e)
    }

    /** 玻璃探针没撤：画了玻璃之后进程结束 */
    private fun writeProbeRecord(level: Int) {
        val f = crashFile() ?: return
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        val sb = StringBuilder()
        sb.append("== 开启液态玻璃后进程结束 ==\n")
        sb.append("时间: ").append(ts).append('\n')
        sb.append("版本: ").append(BuildConfig.VERSION_NAME)
            .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        sb.append("玻璃等级: ").append(levelLabel(level)).append('\n')
        sb.append(deviceInfo())
        sb.append("判定: 首帧已经画出来了，是在后续帧（玻璃绘制/滑动/切页）里死的。\n")
        sb.append("      若下面没有 Java 堆栈，基本可以锁定 GPU 合成或 AGSL 着色器。\n")
        write(f, sb.toString())
    }

    /** 没有 Java 堆栈的坏启动：多半是原生层被打死（hwui / GPU 驱动） */
    private fun writeNoStackRecord(prevStage: String, bad: Int) {
        val f = crashFile() ?: return
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        val sb = StringBuilder()
        sb.append("== 启动未完成（无 Java 堆栈）==\n")
        sb.append("时间: ").append(ts).append('\n')
        sb.append("版本: ").append(BuildConfig.VERSION_NAME)
            .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        sb.append("阶段: ").append(prevStage).append("  · 第 ").append(bad).append(" 次坏启动\n")
        sb.append(deviceInfo())
        sb.append("判定: 进程没走到首帧就被结束，且没触发 Java 异常处理。\n")
        sb.append("      可能性排序：1) GPU/AGSL 着色器在原生层 abort；\n")
        sb.append("      2) 合成阶段 OOM 被系统杀掉；3) 系统/厂商安全策略拦截。\n")
        sb.append("      这几种都不会有 Java 堆栈，所以这里只有阶段信息。\n")
        write(f, sb.toString())
    }

    private fun write(f: File, text: String) {
        runCatching {
            if (f.exists()) f.copyTo(File(f.parentFile, "crash-prev.txt"), overwrite = true)
        }
        runCatching { f.writeText(text) }
    }
}
