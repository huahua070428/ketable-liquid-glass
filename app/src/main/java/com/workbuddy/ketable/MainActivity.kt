package com.workbuddy.ketable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.time.LocalDate

/** 各页面共享的玻璃环境（旧引擎参数，保留字段避免旧页面编译中断） */
object GlassEnv {
    var tiltX: Float = 0f
    var tiltY: Float = 0f
    var screenSize: IntSize = IntSize.Zero
}

// ---------------------------------------------------------------- 路由

sealed class Route(val tab: String? = null) {
    data object Today : Route("today")
    data object Week : Route("week")
    data object Ai : Route("ai")
    data object Memo : Route("memo")
    data object Settings : Route("settings")

    /** 二级页（不进 Dock，带返回） */
    data object Stats : Route()
    data object DayTimeline : Route()
    data object Import : Route()
    data object Feedback : Route()
    /** courseId = "new" 表示新建；presetDay / presetSection 是点空白格时的落点 */
    data class Edit(
        val courseId: String,
        val presetDay: Int = 0,
        val presetSection: Int = 0
    ) : Route()
}

val TABS: List<Pair<Route, Pair<String, ImageVector>>> = listOf(
    Route.Today to ("今日" to Icons.Outlined.Home),
    Route.Week to ("课表" to Icons.Outlined.CalendarMonth),
    Route.Ai to ("AI" to Icons.Outlined.SmartToy),
    Route.Memo to ("备忘" to Icons.Outlined.EditNote),
    Route.Settings to ("设置" to Icons.Outlined.Settings)
)

const val HEADER_H = 56
const val DOCK_H = 66

// ---------------------------------------------------------------- 入口

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DataStore.init(applicationContext)
        // 启动自诊断：崩溃留证据 + 连续两次打不开就自动关掉液态玻璃（详见 CrashGuard）
        CrashGuard.install(applicationContext)
        CrashGuard.mark("setContent")
        setContent { KtApp() }

        // 旧版（WebView）数据迁移从启动关键路径上挪走：
        // 之前它在 setContent 之前同步创建 WebView，一旦设备上的 WebView 组件有问题，
        // 就是「点开立刻退」而且没有任何界面。现在放到首帧之后、独立 try 里。
        window.decorView.postDelayed({
            CrashGuard.mark("migrate")
            runCatching { LegacyMigrator.migrateIfNeeded(this) }
                .onFailure { android.util.Log.w("MainActivity", "迁移跳过：" + it.message) }
            CrashGuard.mark("running")
        }, 700)
    }

    /** 正常退到后台 = 这次玻璃跑通了，撤掉探针，避免下次误判降级 */
    override fun onStop() {
        runCatching { CrashGuard.clearProbe() }
        super.onStop()
    }
}

@Composable
fun KtApp() {
    val settings = DataStore.settings
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            tick++
        }
    }
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    // 系统栏图标跟着 App 主题走（浅色主题下白图标会看不见）
    // 首帧画完 = 这次启动算成功，清掉「坏启动」计数
    androidx.compose.runtime.LaunchedEffect(Unit) {
        CrashGuard.mark("first_frame")
        androidx.compose.runtime.withFrameNanos { }
        CrashGuard.markBootOk()
    }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val act = view.context as? android.app.Activity
        if (act != null) {
            androidx.core.view.WindowCompat.getInsetsController(act.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    KtTheme(dark) {
        AppScaffold(dark = dark, tick = tick)

        // 一、上次崩溃的证据：启动就摆出来，可一键复制。没有 adb 只能靠这个。
        val crashText = remember { CrashGuard.lastCrash() }
        var showCrash by remember { mutableStateOf(CrashGuard.hasUnreadCrash()) }
        if (showCrash && crashText != null) {
            CrashReportDialog(crashText) {
                CrashGuard.ackCrash()
                showCrash = false
            }
        } else if (CrashGuard.enteredSafeMode) {
            // 二、这次是降级启动：说清楚降了什么，别让用户以为「玻璃坏了」
            var show by remember { mutableStateOf(true) }
            if (show) {
                AlertDialog(
                    onDismissRequest = { show = false },
                    title = { Text("已自动降级") },
                    text = {
                        Text(
                            CrashGuard.degradeReason +
                                    "\n\n玻璃等级可以在 设置 → 运行诊断 里手动调回去，" +
                                    "崩溃记录也在这里，点「复制」发我就行。"
                        )
                    },
                    confirmButton = { TextButton(onClick = { show = false }) { Text("知道了") } }
                )
            }
        }
    }
}

/** 崩溃详情：全文可滚动 + 一键复制（没 adb 时的唯一取证出口） */
@Composable
fun CrashReportDialog(text: String, onClose: () -> Unit) {
    val colors = LocalKtColors.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("上次启动没走完") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text.trim().take(6000),
                    fontSize = 11.sp, lineHeight = 16.sp, color = colors.ink2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clip.setText(androidx.compose.ui.text.AnnotatedString(text))
                copied = true
            }) { Text(if (copied) "已复制" else "复制全部") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("知道了") } }
    )
}

// ---------------------------------------------------------------- 壳

@Composable
fun AppScaffold(
    dark: Boolean,
    tick: Int,
    startRoute: Route = Route.Today,
    now: java.time.LocalDateTime = java.time.LocalDateTime.now()
) {
    val colors = LocalKtColors.current
    var route by remember { mutableStateOf(startRoute) }
    var stack by remember { mutableStateOf(listOf<Route>()) }
    // 二级页进入时把背景锁成纯色，避免列表在玻璃下过于花
    fun go(r: Route) {
        val isTab = TABS.any { it.first == r }
        if (isTab) {
            stack = emptyList(); route = r
        } else {
            stack = stack + route; route = r
        }
    }
    fun back() {
        route = stack.lastOrNull() ?: Route.Today
        stack = stack.dropLast(1)
    }

    BackHandler(enabled = stack.isNotEmpty()) { back() }

    // 玻璃采样源：只有真正能画玻璃时才创建。
    // 关键：它提供给「录制内容」的兄弟节点（页头/Dock/FAB），
    // 而 BackdropHost 内部会把 LocalBackdrop 置空 —— 防止 RenderNode 在录制时画自己。
    val glassOn = LocalGlassOn.current
    val canGlass = GlassCapability.supported && glassOn && !GlassRuntime.broken
    val backdrop = if (canGlass) rememberLayerBackdrop() else null

    // 玻璃探针：开玻璃时立旗，稳定 10 秒才撤。若进程在这期间结束，
    // 下次启动能确定「是玻璃害的」，自动降一级（见 CrashGuard）。
    LaunchedEffect(canGlass, DataStore.settings.glassLevel) {
        if (!canGlass) return@LaunchedEffect
        CrashGuard.probeGlass(DataStore.settings.glassLevel)
        // 30 秒稳态 + 正常退到后台都会撤旗；只有「前台运行中被结束」才会留下旗子
        kotlinx.coroutines.delay(30_000)
        CrashGuard.clearProbe()
    }

    // V4 浅色重构（2026-11-18，用户定方向：浅色果味）：
    // 今日 tab 强制走浅色，配彩色柔光极光背景 + 透白卡片 + 浅色玻璃 Dock。
    // 其他 tab 仍跟随系统/设置里的主题。状态栏图标在今日页用深色。
    val pageColors = if (route == Route.Today) LightColors else colors
    val rootView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val act = rootView.context as? android.app.Activity
        if (act != null) {
            val lightIcons = route == Route.Today || !dark
            androidx.core.view.WindowCompat.getInsetsController(act.window, rootView).apply {
                isAppearanceLightStatusBars = lightIcons
                isAppearanceLightNavigationBars = lightIcons
            }
        }
    }

    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalGlassLevel provides DataStore.settings.glassLevel,
        LocalKtColors provides pageColors
    ) {
    Box(Modifier.fillMaxSize()) {
        BackdropHost(backdrop) {
            // 今日 tab 用 v3 的中性底（纯黑 + 极淡暗角）；其余页仍是纯色底
            if (route == Route.Today) NeutralBackground() else AppBackground(colors)
            // 页面本体（会被录进 backdrop layer，玻璃采样它 → 滚动从玻璃下穿过）
            Box(Modifier.fillMaxSize()) {
                when (val r = route) {
                    Route.Today -> {
                        // 今日页 v3：状态在这里算好，页面只负责画
                        val s = DataStore.settings
                        val st = Sch.todayStatus(now, DataStore.courses, s)
                        val nextAt = st.next?.let { c ->
                            val d = st.nextDate ?: now.toLocalDate()
                            val m = Sch.courseStartMin(c)
                            java.time.LocalDateTime.of(d, java.time.LocalTime.of(m / 60, m % 60)) to c
                        }
                        TodayScreenV3(
                            next = nextAt,
                            todayCourses = st.list,
                            undated = DataStore.courses.filter { it.day == 0 },
                            weekNo = Sch.weekNo(now.toLocalDate(), s),
                            today = now.toLocalDate(),
                            now = now,
                            onChip = { label ->
                                if (label == "学习数据") go(Route.Stats) else go(Route.DayTimeline)
                            }
                        )
                    }
                    Route.Week -> WeekScreen(onNav = ::go)
                    Route.Memo -> MemoScreen(onNav = ::go)
                    Route.Settings -> SettingsScreen(onNav = ::go)
                    Route.Ai -> AiScreen(onNav = ::go)
                    Route.Stats -> StatsScreen(onNav = ::go, onBack = ::back)
                    Route.DayTimeline -> DayTimelineScreen(onNav = ::go, onBack = ::back)
                    Route.Import -> ImportScreen(onNav = ::go, onBack = ::back)
                    Route.Feedback -> ScreenStub("意见反馈", "反馈通道正在接，先在设置里导出 JSON 发给开发者。", ::go, ::back)
                    is Route.Edit -> CourseEditScreen(
                        courseId = r.courseId,
                        presetDay = r.presetDay,
                        presetSection = r.presetSection,
                        onBack = ::back
                    )
                }
            }
        }

        // 顶部玻璃栏（在 backdrop 之外 → 采样下面的滚动内容）
        // 今日页例外：它用 iOS 健身式的大标题（纯排版，无玻璃），由 TodayScreen 自己画
        if (route != Route.Today) {
            AppHeader(route = route, tick = tick, onBack = if (stack.isEmpty()) null else ::back)
        }

        // 灵动岛：顶层系统胶囊，所有 tab 常驻（切课表/设置也在）
        val islandStatus = Sch.todayStatus(now, DataStore.courses, DataStore.settings)
        val islandNext = islandStatus.next?.let { c ->
            val d = islandStatus.nextDate ?: now.toLocalDate()
            val m = Sch.courseStartMin(c)
            java.time.LocalDateTime.of(d, java.time.LocalTime.of(m / 60, m % 60)) to c
        }
        IslandBar(islandNext, now, Modifier.align(Alignment.TopCenter))

        val isTabPage = TABS.any { it.first == route } && stack.isEmpty()
        if (isTabPage) {
            Dock(
                selected = route,
                onSelect = ::go,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
            if (route == Route.Today || route == Route.Week) {
                AddFab(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 24.dp, bottom = 92.dp)
                ) { go(Route.Edit("new")) }
            }
        }

        // 上课前提醒：设置里填了几分钟就提前几分钟弹（只在前台，同一天同门课只弹一次）
        var reminded by remember { mutableStateOf("") }
        var reminder by remember { mutableStateOf<Course?>(null) }
        LaunchedEffect(tick) {
            val s = DataStore.settings
            if (s.remindLeadMin <= 0) return@LaunchedEffect
            val now = java.time.LocalDateTime.now()
            val st = Sch.todayStatus(now, DataStore.courses, s)
            val next = st.next ?: return@LaunchedEffect
            if (st.nextDate != null) return@LaunchedEffect   // 不是今天的课，不提醒
            val mins = Sch.courseStartMin(next) - st.nowMin
            val key = next.id + "@" + LocalDate.now()
            if (mins in 0..s.remindLeadMin && key != reminded) {
                reminded = key
                reminder = next
            }
        }
        reminder?.let { c ->
            AlertDialog(
                onDismissRequest = { reminder = null },
                title = { Text("${c.name} 快上课了") },
                text = {
                    val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                    val left = (Sch.courseStartMin(c) - nowMin).coerceAtLeast(0)
                    val meta = "${Sch.timeLabel(c)} · ${c.location.ifBlank { "地点待定" }}" +
                            (if (c.teacher.isNotBlank()) " · ${c.teacher}" else "")
                    Text("还有 $left 分钟\n$meta")
                },
                confirmButton = { TextButton(onClick = { reminder = null }) { Text("知道了") } },
                dismissButton = {
                    TextButton(onClick = {
                        reminder = null
                        go(Route.Edit(c.id))
                    }) { Text("看课程") }
                }
            )
        }
    }
    }
}

/**
 * 背景：永远是中性纯色。
 * 设计宪法第二条 —— 禁止彩色光斑、氛围渐变、彩雾。色彩只属于内容。
 */
@Composable
private fun AppBackground(colors: KtColors) {
    Box(Modifier.fillMaxSize().background(colors.base))
}

// ---------------------------------------------------------------- 顶部玻璃栏

@Composable
private fun BoxScope.AppHeader(route: Route, tick: Int, onBack: (() -> Unit)?) {
    val colors = LocalKtColors.current
    val today = LocalDate.now()
    val wk = Sch.weekNo(today, DataStore.settings)

    val title: String
    var sub: String? = null
    var trailing: (@Composable () -> Unit)? = null

    when (route) {
        Route.Today -> {
            title = "今日"
            sub = "${Sch.fmtFull(today)} ${K.DOW_CN[Sch.dow(today)]} · 第 $wk 教学周"
        }
        Route.Week -> {
            title = "课表"
            val dates = Sch.weekDates(today).map { it.plusWeeks(WeekUi.offset.toLong()) }
            sub = "第 ${wk + WeekUi.offset} 周 · ${Sch.fmtDate(dates[0])} – ${Sch.fmtDate(dates[6])}"
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderRoundBtn("‹") { WeekUi.offset-- }
                    HeaderRoundBtn("›") { WeekUi.offset++ }
                }
            }
        }
        Route.Ai -> { title = "AI 助手"; sub = "课表问答 · 本地优先" }
        Route.Memo -> {
            title = "备忘"
            val left = DataStore.memos.count { !it.done }
            sub = if (left == 0) "暂无待办" else "$left 条待办"
        }
        Route.Settings -> { title = "设置"; sub = null }
        Route.Stats -> { title = "学习数据"; sub = "课时 · 学分 · 分布" }
        Route.DayTimeline -> { title = "时间轴"; sub = "按钟点看今天" }
        Route.Import -> { title = "导入课程表"; sub = "文字解析 / JSON 恢复" }
        Route.Feedback -> { title = "意见反馈"; sub = null }
        is Route.Edit -> {
            title = if (route.courseId == "new") "新建课程" else "编辑课程"
            sub = null
        }
    }

    Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
        GlassSurface(
            tier = GlassTier.Regular,
            shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
            tint = colors.dockTint,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(HEADER_H.dp)
                    .padding(start = if (onBack != null) 8.dp else 20.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("‹", fontSize = 30.sp, color = colors.ink2)
                    }
                    Spacer(Modifier.width(2.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = colors.ink1, maxLines = 1
                    )
                    if (sub != null) {
                        Text(sub, fontSize = 11.sp, color = colors.ink3, maxLines = 1)
                    }
                }
                trailing?.invoke()
            }
        }
        // 底部分隔线：左缩进 20，暗 8% / 亮 8%
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp)
                .fillMaxWidth()
                .height(0.7.dp)
                .background(colors.hairline)
        )
    }
}

@Composable
private fun HeaderRoundBtn(symbol: String, onClick: () -> Unit) {
    val colors = LocalKtColors.current
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.controlBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 17.sp, color = colors.ink2)
    }
}

// ---------------------------------------------------------------- Dock

@Composable
fun Dock(selected: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalKtColors.current
    val idx = TABS.indexOfFirst { it.first == selected }.coerceAtLeast(0)

    // 玻璃泡滑移：spring ≈ 300ms；切换瞬间轻微横向拉伸再回弹（morph）
    val squash = remember { Animatable(0f) }
    LaunchedEffect(idx) {
        squash.snapTo(1f)
        squash.animateTo(0f, spring(dampingRatio = 0.48f, stiffness = 320f))
    }

    // 注意层级：Dock 的玻璃长条是「背景层」，玻璃泡与图标是它的**兄弟**。
    // 如果把 Lens 泡放进 Regular 长条里，等于在带模糊 effect 的图层里再去采样背景层，
    // 是这整套里最重、最容易踩驱动的组合，拆开更稳（视觉上也更对：泡不该被长条糊掉）。
    Box(modifier.height(DOCK_H.dp)) {
        GlassSurface(
            tier = GlassTier.Regular,
            shape = RoundedCornerShape((DOCK_H / 2).dp),
            tint = colors.dockTint,
            modifier = Modifier.fillMaxSize()
        ) {}

        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val tabW: Dp = maxWidth / TABS.size
            // V5：巨型透镜泡 92x76，顶出 Dock 10dp（参考图：泡泡比底栏高出一截）。
            // 泡泡中心仍对准当前 tab 的槽位中心。
            val bubbleW = 92.dp
            val bubbleH = 76.dp
            val target = (tabW * idx + (tabW - bubbleW) / 2).coerceIn(
                0.dp, (maxWidth - bubbleW).coerceAtLeast(0.dp)
            )
            val x by animateDpAsState(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 430f),
                label = "dockBubble"
            )

            // 选中：凸透镜玻璃泡（唯一 Lens 级，每屏 1 个）。
            // 选中的图标和文字画进泡泡里放大显示；Row 里对应的那一份用透明占位，
            // 位置不变，只是看不见——这样透镜里放大的才是图标本身。
            val (selLabel, selIcon) = TABS[idx].second
            Box(
                Modifier
                    .offset(x = x, y = (-10).dp)
                    .graphicsLayer {
                        scaleX = 1f + 0.10f * squash.value
                        scaleY = 1f - 0.05f * squash.value
                    }
            ) {
                DockGlassBubble(
                    modifier = Modifier.size(bubbleW, bubbleH),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            selIcon,
                            contentDescription = selLabel,
                            modifier = Modifier.size(28.dp),
                            tint = colors.accent
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            selLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }
                }
            }

            Row(Modifier.fillMaxSize()) {
                TABS.forEachIndexed { i, entry ->
                    val (label, icon) = entry.second
                    val on = i == idx
                    Column(
                        Modifier
                            .width(tabW)
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (on) 0f else 1f }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(entry.first) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            modifier = Modifier.size(if (on) 21.dp else 20.dp),
                            tint = if (on) colors.accent else colors.ink3
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            fontSize = 10.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) colors.accent else colors.ink3
                        )
                    }
                }
            }
        }
    }
}

/** 悬浮按钮：Lens 级玻璃（每屏第 2 个 Lens，封顶） */
@Composable
private fun AddFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalKtColors.current
    GlassSurface(
        tier = GlassTier.Lens,
        shape = CircleShape,
        magnification = 1.22f,
        modifier = modifier.size(56.dp)
    ) {
        Box(
            Modifier.fillMaxSize().clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // V4：加号用当前主题的主文字色——浅色玻璃上白加号看不见
            Text("＋", fontSize = 25.sp, fontWeight = FontWeight.Medium, color = colors.label)
        }
    }
}

// ---------------------------------------------------------------- 通用零件

/** v5.2 起标题统一放到顶部玻璃栏，页面内不再画标题（保留空实现避免旧页调用断裂） */
@Composable
fun PageHead(title: String, sub: String? = null, onBack: (() -> Unit)? = null) {
    // no-op
}

/** 区块小标题（.sect） */
@Composable
fun Sect(text: String) {
    val colors = LocalKtColors.current
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.ink3,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

/**
 * 页面滚动容器：
 * - 顶部让出玻璃栏（内容往上滚时会从玻璃下面穿过）
 * - 底部让出 Dock
 */
@Composable
fun ScreenScroll(
    dockVisible: Boolean = true,
    pad: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = pad,
                end = pad,
                top = statusTop + (HEADER_H + 12).dp,
                bottom = if (dockVisible) 150.dp else 40.dp
            )
    ) {
        content()
    }
}

@Composable
fun ScreenStub(title: String, note: String, onNav: (Route) -> Unit, onBack: (() -> Unit)?) {
    val colors = LocalKtColors.current
    ScreenScroll(dockVisible = false) {
        FlatCard(Modifier.fillMaxWidth(), corner = 20.dp) {
            Column(Modifier.padding(20.dp)) {
                Text(note, fontSize = 14.sp, color = colors.ink2, lineHeight = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text("这一页在迁移队列里，下一轮补上。", fontSize = 12.sp, color = colors.ink3)
            }
        }
    }
}

@Composable
fun ScreenStubNote(note: String) {
    val colors = LocalKtColors.current
    Text(note, fontSize = 13.sp, color = colors.ink2, lineHeight = 21.sp)
}
