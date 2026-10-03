package com.hyperflowplus

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.Scaffold
import android.widget.Toast
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import com.hyperflowplus.ui.ConversationScreen
import com.hyperflowplus.ui.ConversationTopBar
import com.hyperflowplus.ui.FlowScreen
import com.hyperflowplus.ui.HomeScreen
import com.hyperflowplus.ui.GuideScreen
import com.hyperflowplus.ui.GuideType
import com.hyperflowplus.ui.BarBlurHost
import com.hyperflowplus.ui.HyperDialog
import com.hyperflowplus.ui.LiquidNavBar
import com.hyperflowplus.ui.LogListScreen
import com.hyperflowplus.ui.RoundedIcons
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.hyperflowplus.ui.LicensesScreen
import com.hyperflowplus.ui.MainHolder
import com.hyperflowplus.ui.MessagesScreen
import com.hyperflowplus.ui.OnboardingScreen
import com.hyperflowplus.ui.SettingsScreen

/** HyperFlow —— Miuix(Compose) 全量重写入口（AndroidLiquidGlass 玻璃） */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        HFState.ctx = applicationContext
        setContent { HyperFlowApp() }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

@Composable
fun HyperFlowApp() {
    val ctx = LocalContext.current
    // 上次闪退日志：启动自动读取展示（无需终端抓日志）
    var crashLog by remember { mutableStateOf(readCrashLog(ctx)) }
    // 主题：纯 Miuix 默认主题（跟随系统深浅色），不做壁纸取色、不做 App 内玻璃
    // 液态玻璃是系统级效果（HyperLight 等模块实现），App 内保持 HyperOS 原生观感
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller) {
        val state = HFState
        val ctx = LocalContext.current
        var tab by remember { mutableIntStateOf(0) }
        var showSort by remember { mutableStateOf(false) }
        var showMsgMode by remember { mutableStateOf(false) }
        var showUpd by remember { mutableStateOf(false) }
        var showLicenses by remember { mutableStateOf(false) }
        var showLogs by remember { mutableStateOf(false) }
        var guideType by remember { mutableStateOf<GuideType?>(null) }
        var updVer by remember { mutableStateOf("") }
        var updUrl by remember { mutableStateOf("") }
        var updLog by remember { mutableStateOf("") }
        var updPhase by remember { mutableStateOf("idle") }   // idle/checking/new/none/error
        var updMsg by remember { mutableStateOf("") }
        var updMethod by remember { mutableStateOf(false) }    // 更新方式选择弹窗
        var updDownloading by remember { mutableStateOf(false) }
        var updDownloaded by remember { mutableStateOf<String?>(null) }
        var updDlError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            state.loadAll()
            state.startFlowPolling()   // 归档实时刷新（短信流转到达即显示）
            MainHolder.onOpenLicenses = { showLicenses = true }
            MainHolder.onOpenLogs = { showLogs = true }
            MainHolder.onReopenOnboarding = { state.showOnboarding = true }
            MainHolder.onCheckUpdate = {
                // V0.6.15：主动点击版本号 → 弹检测窗（checking→结果在窗内展示），
                // 同时版本行状态同步（有新版橙字常驻 / 已是最新灰字）
                updPhase = "checking"
                updMsg = ""
                showUpd = true
                MainHolder.updState = "checking"
                checkUpdate(
                    onNew = { ver, url, log ->
                        runCatching {
                            updVer = ver; updUrl = url; updLog = log
                            updPhase = "new"; showUpd = true
                            MainHolder.hasUpdate = true
                            MainHolder.latestVer = ver
                            MainHolder.updState = "new"
                        }
                    },
                    onNone = {
                        runCatching {
                            updMsg = "当前使用的是 V${BuildConfig.VERSION_NAME}。"
                            updPhase = "none"; showUpd = true
                            MainHolder.hasUpdate = false
                            MainHolder.updState = "none"
                        }
                    },
                    onError = {
                        runCatching {
                            updMsg = it
                            updPhase = "error"; showUpd = true
                            MainHolder.hasUpdate = false
                            MainHolder.updState = "error"
                        }
                    },
                    onBusy = {
                        runCatching {
                            updMsg = "正在检查中，请稍候再试"
                            updPhase = "busy"; showUpd = true
                            MainHolder.updState = "busy"
                        }
                    }
                )
            }
            // V0.6.15：版本行"发现新版本"时点击 → 直接弹更新日志窗（日志+更新按钮）
            MainHolder.onOpenUpdater = { updPhase = "new"; showUpd = true }
            // v0.5.11：打开 App 自动检测更新（延迟启动，避开引导页/闪退 Toast 同帧渲染）。
            // 有新版 → 弹窗提示一次（prefs 按版本号去重），之后不再主动弹；
            // 仅设置页"版本号"行显示"有新版可更新"提示；点击该行可随时手动再检。
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching {
                    checkUpdate(
                        onNew = { ver, url, log ->
                            // V0.6.15：自动检测=静默，不弹框；只更新版本行状态，
                            // 用户点击版本行 → 直击更新日志窗
                            updVer = ver; updUrl = url; updLog = log
                            MainHolder.hasUpdate = true
                            MainHolder.latestVer = ver
                            MainHolder.updState = "new"
                        },
                        onNone = {
                            MainHolder.hasUpdate = false
                            MainHolder.updState = "none"
                        },
                        onError = {
                            MainHolder.hasUpdate = false
                            MainHolder.updState = "error"
                        },
                        onBusy = {}
                    )
                }
            }, 1500)
        }
        // 引导判断：首次（App 私有标记，root 无关）显示引导页；连点"关于-HyperFlow"3 次可重开
        LaunchedEffect(Unit) {
            state.showOnboarding = !state.firstRunDone
        }

        // 上次闪退提示：仅 Toast 展示摘要（不弹窗，避免启动弹窗渲染导致循环闪退）
        LaunchedEffect(crashLog) {
            if (crashLog != null) {
                Toast.makeText(
                    ctx,
                    "上次 HyperFlow 异常退出（日志已保存）：" + crashLog!!.take(80),
                    Toast.LENGTH_LONG
                ).show()
                crashLog = null
            }
        }


        // 会话详情：v0.5.5 起改为在 Scaffold 内容区渲染（顶栏进 topBar 槽、
        // 底部导航栏保留不消失），返回键/返回手势回列表
        val conversation = state.currentConversation
        if (conversation != null) {
            BackHandler { state.currentConversation = null }
        }
        // 环境引导覆盖页（首页检测项点击进入，Miuix 返回）
        val gt = guideType
        if (gt != null) {
            BackHandler { guideType = null }
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                GuideScreen(gt, onBack = { guideType = null })
            }
            return@MiuixTheme
        }
        // V0.6.15：开源许可 / 日志列表不再全屏覆盖，进 Scaffold content（底部导航保留），
        // TopAppBar 在这两个页面隐藏（各自自带标题栏+统一 MIUI 返回箭头）
        BackHandler(enabled = showLogs || showLicenses) {
            if (showLogs) showLogs = false
            else if (showLicenses) showLicenses = false
        }

        val tabs = listOf(
            Tab("首页", Icons.Filled.Home),
            Tab("流转", Icons.Filled.Send),
            Tab("消息", Icons.Filled.Notifications),
            Tab("设置", Icons.Filled.Settings)
        )
        // 四个页面各显示页面名（软件名移到设置页关于区）
        val title = when (tab) { 0 -> "首页"; 1 -> "流转"; 2 -> "消息"; 3 -> "设置"; else -> "" }

        // 弹窗智能移位：开启悬浮/液态玻璃时弹窗自动再上移（避开悬浮胶囊），否则默认贴底
        com.hyperflowplus.ui.smartInset = if (state.navFloat || state.glassEffect) 88.dp else 40.dp
        // v0.5.12：悬浮胶囊底部避让 —— 页面滚动容器尾部加同高间距，
        // 内容最后一行可滚到胶囊上沿（不遮挡、不留白）
        MainHolder.bottomPad = if (state.navFloat) 96.dp else 0.dp

        // 液态玻璃 backdrop：在 Scaffold 内容区挂 layerBackdrop 捕获页面内容，供悬浮胶囊折射。
        // InstallerX 同款结构：胶囊在 bottomBar 槽（内容区之外、绘制顺序在内容之后）采样，
        // 不会把胶囊自身卷进捕获子树（v0.5.3 之前的 BarBlurHost 包整页会自采递归崩溃，弃用）。
        // 修复"玻璃后背景变黑"（v0.5.13 根治）：此前 backdrop 用无 block 的 rememberLayerBackdrop()
        // —— 只录制页面 UI、不铺底色，页面内容下方空白区域在采样时是透明 → 玻璃折射区变黑；
        // 设置页内容一直铺到底所以侥幸正常，首页/流转/消息内容短则黑。这里显式先铺 surface
        // 底色再录内容（与 BarBlurHost 一致），任何采样区域都有白底，玻璃不再黑。
        // onDraw 是 DrawScope（非 @Composable）回调，色值先在 Composable 作用域取好再捕获。
        val glassSurface = MiuixTheme.colorScheme.surface
        val glassBackdrop = rememberLayerBackdrop {
            drawRect(glassSurface)
            drawContent()
        }

        // 导航栏/悬浮胶囊的液态折射 backdrop 由 MainActivity 挂载（见上），不再用 BarBlurHost
        // 包内容页 —— 内容页盒子会卷入 backdrop 子树导致递归重绘崩溃。
        // v0.6.8：Miuix 大标题 TopAppBar（学 KSU/HyperModifier）——largeTitle 随内容滚动收起，
        // 收起后回到顶栏居中；切 tab/进出会话时重置为展开态。
        val topBarScroll = MiuixScrollBehavior()
        Scaffold(
            containerColor = MiuixTheme.colorScheme.surface,
            modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
            topBar = {
                if (conversation != null) {
                    // 会话详情顶栏（返回键 + 发送人 + 排序），放 Scaffold topBar 槽
                    // 保证底部导航栏在详情页保留不消失
                    ConversationTopBar(
                        sender = conversation.first,
                        desc = state.detailSort != "asc",
                        onBack = { state.currentConversation = null },
                        onSetSort = { state.setSortValue(Config.KEY_DETAIL_SORT, it) }
                    )
                } else if (showLogs || showLicenses) {
                    // V0.6.15：日志列表 / 开源许可自带标题栏+统一返回箭头，隐藏主 TopAppBar
                } else {
                    TopAppBar(
                        title = title,
                        largeTitle = title,
                        color = MiuixTheme.colorScheme.surface,
                        titleColor = MiuixTheme.colorScheme.onSurface,
                        largeTitleColor = MiuixTheme.colorScheme.onSurface,
                        scrollBehavior = topBarScroll,
                        actions = {
                        if (tab == 2) {
                        Box {
                            IconButton(onClick = { showSort = true }) {
                                top.yukonga.miuix.kmp.basic.Icon(
                                    // V0.6.15：图标换 Material 标准 Sort（学 LSP 管理器排序风格）
                                    imageVector = Icons.Filled.Sort,
                                    contentDescription = "排序"
                                )
                            }
                            // KSU 风格排序下拉菜单：点 Sort 图标在右上角展开（Popup 锚定，非系统 Dialog，防闪退）
                            if (showSort) {
                                androidx.compose.ui.window.Popup(
                                    alignment = Alignment.TopEnd,
                                    offset = androidx.compose.ui.unit.IntOffset(0, with(LocalDensity.current) { 46.dp.roundToPx() }),
                                    onDismissRequest = { showSort = false }
                                ) {
                                    val cur = state.archiveSort
                                    val dim = if (cur.startsWith("name")) "name" else "time"
                                    val desc = cur.endsWith("desc")
                                    Surface(
                                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                        shadowElevation = 10.dp,
                                        modifier = Modifier.width(230.dp)
                                    ) {
                                        Column(Modifier.padding(vertical = 6.dp)) {
                                            @Composable
                                            fun Item(label: String, summary: String, sel: Boolean, onClick: () -> Unit) {
                                                Row(
                                                    Modifier.fillMaxWidth()
                                                        .clickable(onClick = onClick)
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        label,
                                                        style = MiuixTheme.textStyles.body1,
                                                        color = if (sel) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        summary,
                                                        style = MiuixTheme.textStyles.body2,
                                                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                                        modifier = Modifier.padding(end = 6.dp)
                                                    )
                                                    if (sel) {
                                                        top.yukonga.miuix.kmp.basic.Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = "已选",
                                                            tint = MiuixTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Item(
                                                "发送人",
                                                if (dim == "name") (if (desc) "Z→A" else "A→Z") else "",
                                                dim == "name",
                                                {
                                                    if (state.archiveSort != "name_" + (if (desc) "desc" else "asc")) {
                                                        state.setSortValue(Config.KEY_ARCHIVE_SORT, "name_" + (if (desc) "desc" else "asc"))
                                                    }
                                                    showSort = false
                                                }
                                            )
                                            Item(
                                                "时间",
                                                if (dim == "time") (if (desc) "新→旧" else "旧→新") else "",
                                                dim == "time",
                                                {
                                                    if (state.archiveSort != "time_" + (if (desc) "desc" else "asc")) {
                                                        state.setSortValue(Config.KEY_ARCHIVE_SORT, "time_" + (if (desc) "desc" else "asc"))
                                                    }
                                                    showSort = false
                                                }
                                            )
                                            Box(
                                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp)
                                                    .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                                            )
                                            // 底部倒序开关（KSU 风格）
                                            Row(
                                                Modifier.fillMaxWidth()
                                                    .clickable { showSort = false }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "倒序",
                                                    style = MiuixTheme.textStyles.body1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                top.yukonga.miuix.kmp.basic.Switch(
                                                    checked = desc,
                                                    onCheckedChange = {
                                                        state.setSortValue(
                                                            Config.KEY_ARCHIVE_SORT,
                                                            dim + "_" + (if (desc) "asc" else "desc")
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // V0.6.15：显示模式设置（排序图标右侧：实时与本地合并/仅实时/仅本地）
                        Box {
                            IconButton(onClick = { showMsgMode = true }) {
                                top.yukonga.miuix.kmp.basic.Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "显示模式"
                                )
                            }
                            if (showMsgMode) {
                                androidx.compose.ui.window.Popup(
                                    alignment = Alignment.TopEnd,
                                    offset = androidx.compose.ui.unit.IntOffset(0, with(LocalDensity.current) { 46.dp.roundToPx() }),
                                    onDismissRequest = { showMsgMode = false }
                                ) {
                                    Surface(
                                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                        shadowElevation = 10.dp,
                                        modifier = Modifier.width(230.dp)
                                    ) {
                                        Column(Modifier.padding(vertical = 6.dp)) {
                                            @Composable
                                            fun ModeItem(label: String, sel: Boolean, onClick: () -> Unit) {
                                                Row(
                                                    Modifier.fillMaxWidth()
                                                        .clickable(onClick = onClick)
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        label,
                                                        style = MiuixTheme.textStyles.body1,
                                                        color = if (sel) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (sel) {
                                                        top.yukonga.miuix.kmp.basic.Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = "已选",
                                                            tint = MiuixTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            ModeItem("实时与本地合并显示", state.msgMode == "all", {
                                                state.setMsgMode("all")
                                                showMsgMode = false
                                            })
                                            ModeItem("仅显示实时", state.msgMode == "live", {
                                                state.setMsgMode("live")
                                                showMsgMode = false
                                            })
                                            ModeItem("仅显示本地", state.msgMode == "local", {
                                                state.setMsgMode("local")
                                                showMsgMode = false
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                )
                }
            },
            bottomBar = {
                // v0.5.9 结构修正：悬浮模式 bottomBar 槽不再放胶囊。
                // 根因：胶囊在槽内（内容区下方），backdrop 采样坐标超出内容纹理 → 采样透明黑 → 浅色模式黑底。
                // 悬浮胶囊改由内容区叠加（浮于内容之上），坐标落在 backdrop 纹理内，玻璃折射真实页面内容。
                if (!state.navFloat) {
                    LiquidNavBar(
                        selectedTabIndex = tab,
                        onTabSelected = { tab = it },
                        items = tabs.map { it.icon to it.title },
                        floatEnabled = false,
                        glassEnabled = false,
                        backdrop = null
                    )
                }
            }
            ) { padding ->
                val contentMod = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // v0.5.10：学 KSU/安装工具 —— 内容区铺满到底（无底部留白），
                    // 悬浮胶囊 overlay 覆盖其上；被胶囊盖住的部分可看不到，周围内容正常显示
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface)
                ) {
                    // 录制容器（v0.5.10.1 结构）：
                    // 液态玻璃开启时，只把【页面内容】录进 glassBackdrop。
                    // 胶囊必须放在本容器【之外】——否则 LayerBackdropNode.draw() 里
                    // recordLayer 重放 drawContent() 时会把胶囊自身的 LiquidGlass 绘制卷进
                    // 录制层，而胶囊又 drawLayer 采样同一 layer → 递归记录 → 闪退。
                    // 本容器是胶囊的祖先/兄弟同根节点，坐标可正常换算（不黑）。
                    // v0.5.12 玻璃黑底根治：滚动页（首页/流转/消息）滚动时，滚动容器只
                    // invalidate 自身（graphicsLayer 平移），父节点不重绘 → recordLayer 不重录
                    // → 胶囊采样的还是滚动前的旧帧 → 折射错位/黑块（设置页不滚动所以正常）。
                    // 解法：本容器 graphicsLayer 读全局滚动 tick（页面滚动时 +1，见各页
                    // LaunchedEffect），滚动即重绘 → draw() 重跑 → backdrop 每帧重录。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface)
                            .graphicsLayer {
                                // 读滚动 tick 建立依赖：滚动变化 → 本 block 重算 → 节点重绘 → backdrop 重录
                                MainHolder.scrollTick
                            }
                            .then(if (state.glassEffect) Modifier.layerBackdrop(glassBackdrop) else Modifier)
                    ) {
                        if (conversation != null) {
                            // 会话详情在内容区渲染（底栏保留）；padding 由 Scaffold 提供
                            ConversationScreen(state, conversation.first, conversation.second, contentMod)
                        } else if (showLogs) {
                            // V0.6.15：日志列表整页（底部导航保留）
                            LogListScreen(onBack = { showLogs = false }, contentMod)
                        } else if (showLicenses) {
                            // V0.6.15：开源许可整页（底部导航保留）
                            LicensesScreen(onBack = { showLicenses = false }, contentMod)
                        } else {
                            when (tab) {
                                0 -> HomeScreen(state, contentMod, onOpenGuide = { guideType = it })
                                1 -> FlowScreen(state, contentMod)
                                2 -> MessagesScreen(state, contentMod)
                                3 -> SettingsScreen(state, contentMod)
                            }
                        }
                    }
                    // 悬浮胶囊 overlay：在录制容器之外（不污染录制层）、内容 Box 之内
                    // （采样坐标与录制容器同根，换算合法 → 玻璃真实折射、不黑、不闪退）
                    if (state.navFloat) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            LiquidNavBar(
                                selectedTabIndex = tab,
                                onTabSelected = { i -> if (conversation != null) state.currentConversation = null; tab = i },
                                items = tabs.map { it.icon to it.title },
                                floatEnabled = true,
                                glassEnabled = state.glassEffect,
                                backdrop = if (state.glassEffect) glassBackdrop else null
                            )
                        }
                    }
                }
            }

        // 排序菜单已内联在 topBar 的 Sort 图标下方（KSU 风格下拉，Popup 锚定，见 topBar 块）
        // 更新检测弹窗（HyperOS 风格）：检测中转圈，结果（有更新/已最新/失败）在窗内展示
        if (showUpd) {
            when (updPhase) {
                "checking" -> HyperDialog(

                                        title = "检查更新",
                    summary = "正在检查更新…",
                    show = showUpd,
                    onDismiss = { showUpd = false }
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(progress = null)
                    }
                }
                "new" -> HyperDialog(

                                        // KSU 模块页「更新日志」样式：版本号 + 可滚动更新说明 + 取消/更新
                    title = "更新日志",
                    summary = updVer,
                    show = showUpd,
                    onDismiss = { showUpd = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            updLog.ifEmpty { "检测到新版本，请点击「更新」前往 KernelSU 安装。" },
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            textAlign = TextAlign.Start
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "取消",
                            onClick = { showUpd = false },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(16.dp))
                        Button(
                            onClick = {
                                showUpd = false
                                updMethod = true   // 先让用户选：下载更新包 / 去 KSU 检测
                            },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("更新")
                        }
                    }
                }
                "busy" -> HyperDialog(

                                        title = "正在检查更新",
                    summary = updMsg,
                    show = showUpd,
                    onDismiss = { showUpd = false }
                ) {
                    TextButton(
                        text = "关闭",
                        onClick = { showUpd = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "none" -> HyperDialog(

                                        title = "已是最新版本",
                    summary = updMsg,
                    show = showUpd,
                    onDismiss = { showUpd = false }
                ) {
                    TextButton(
                        text = "关闭",
                        onClick = { showUpd = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> HyperDialog(

                                        title = "检查更新失败",
                    summary = updMsg,
                    show = showUpd,
                    onDismiss = { showUpd = false }
                ) {
                    TextButton(
                        text = "关闭",
                        onClick = { showUpd = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        // ===== 更新方式选择（点「更新」后弹出） =====
        if (updMethod) {
            HyperDialog(
                title = "选择更新方式",
                summary = "① 直接下载更新包：将调用系统浏览器/下载器下载（进度见通知栏，支持断点续传），完成后到 KernelSU 模块页「从本地安装模块」，KSU 安装模块时会同步更新 App。\n若 KSU 无法获取到更新，就用这个下载方案。\n\n② 打开 KernelSU 管理器，让它在模块页检测在线更新。",
                
                show = updMethod,
                onDismiss = { updMethod = false }
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(
                        text = "去 KSU 检测",
                        onClick = {
                            updMethod = false
                            launchKernelSu()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = {
                            // 调用系统浏览器下载：通知栏可见进度、支持断点；下载完到 KSU 从本地安装
                            updMethod = false
                            if (!openBrowserDownload(ctx, updUrl)) {
                                updDlError = "无法调用浏览器下载，请复制链接到浏览器打开：\n$updUrl"
                            }
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("下载更新包")
                    }
                }
            }
        }

        // ===== 下载中 =====
        if (updDownloading) {
            HyperDialog(
                title = "正在下载更新包",
                summary = "正在从镜像通道下载，请稍候…",
                
                show = updDownloading,
                onDismiss = { updDownloading = false }
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(progress = null)
                }
            }
        }

        // ===== 下载完成 =====
        updDownloaded?.let { path ->
            HyperDialog(
                title = "更新包已下载",
                summary = "已保存到：\n$path\n\n请到 KernelSU 模块页 → 「从本地安装模块」选择该文件。KSU 安装模块时会同步更新 App；若 KSU 检测不到更新，用此方案即可。",
                
                show = true,
                onDismiss = { updDownloaded = null }
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(
                        text = "稍后安装",
                        onClick = { updDownloaded = null },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = {
                            updDownloaded = null
                            launchKernelSu()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("打开 KSU 安装")
                    }
                }
            }
        }

        // ===== 下载失败 =====
        updDlError?.let { err ->
            HyperDialog(
                title = "下载失败",
                summary = "原因：$err\n\n可稍后重试，或直接打开 KernelSU 管理器在模块页检测更新。",
                
                show = true,
                onDismiss = { updDlError = null }
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(
                        text = "重试下载",
                        onClick = {
                            updDlError = null
                            if (!openBrowserDownload(ctx, updUrl)) {
                                updDlError = "无法调用浏览器下载，请复制链接到浏览器打开：\n$updUrl"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = {
                            updDlError = null
                            launchKernelSu()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("去 KSU 检测")
                    }
                }
            }
        }

        // 排序弹窗（消息页右上角，KSU 风格：分组 + 单选行、选中高亮；系统 Dialog 防闪退）
        // 首次引导覆盖层
        if (state.showOnboarding) {
            OnboardingScreen(state)
        }
    }
}

@Composable
private fun SortOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = RoundedIcons.CheckCircleOutline,
                contentDescription = null,
                modifier = Modifier.width(20.dp).height(20.dp),
                tint = MiuixTheme.colorScheme.primary
            )
        }
    }
}

/** 检测更新防并发：多次点击只跑一次，避免线程堆积 */
private val updateChecking = java.util.concurrent.atomic.AtomicBoolean(false)

private fun checkUpdate(
    onNew: (String, String, String) -> Unit,
    onNone: () -> Unit,
    onError: (String) -> Unit,
    onBusy: () -> Unit = {}
) {
    if (!updateChecking.compareAndSet(false, true)) {
        onBusy()
        return
    }
    Thread {
        try {
            // 多通道并行检测：收集所有通道结果，取 versionCode 最大者（防镜像缓存旧版导致误判"已是最新"）
            val urls = Config.updateJsonUrls()
            val results = java.util.concurrent.ConcurrentHashMap<String, String>()  // url -> body
            val threads = urls.map { u ->
                Thread {
                    try {
                        val conn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 6000
                        conn.instanceFollowRedirects = true
                        conn.setRequestProperty("User-Agent", "HyperFlow/" + BuildConfig.VERSION_NAME)
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        if (body.isNotEmpty() && body.contains("versionCode")) {
                            results[u] = body
                        }
                    } catch (_: Throwable) {
                        // 单个通道失败不影响其他通道
                    }
                }
            }
            threads.forEach { it.start() }
            // 等待全部通道返回（最多 8 秒）
            val deadline = System.currentTimeMillis() + 8000
            while (System.currentTimeMillis() < deadline && results.size < urls.size) {
                Thread.sleep(80)
            }
            if (results.isEmpty()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError("所有更新通道不可达（网络超时），请检查网络后重试")
                }
                return@Thread
            }
            // 取 versionCode 最大者（镜像缓存旧版也无害，只认最新）
            var best: String? = null
            var bestVc = -1
            for ((u, body) in results) {
                runCatching {
                    val vc = org.json.JSONObject(body).optInt("versionCode", 0)
                    if (vc > bestVc) { bestVc = vc; best = body }
                }
            }
            val json = org.json.JSONObject(best ?: "")
            val ver = json.optString("version", "")
            val vc = json.optInt("versionCode", 0)
            val zipUrl = json.optString("zipUrl", "")
            // KSU 规范：update.json 的 changelog 字段放"文本或 URL"——KSU 会把该字段当 URL 请求。
            // 我们的 changelog 字段是 changelog-latest.md 的 URL（否则 KSU 里不显示日志，只显示"开始下载:…"）。
            // App 内检测到 http 开头时同样下载该 URL 的文本再展示。
            val rawLog = json.optString("changelog", "")
            val logText = if (rawLog.startsWith("http")) {
                runCatching { java.net.URL(rawLog).readText() }.getOrDefault(rawLog)
            } else rawLog
            val channel = best?.let { b ->
                results.entries.firstOrNull { it.value == b }?.key
            }?.let { u -> runCatching { java.net.URI(u).host }.getOrNull() } ?: "多通道"
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                // 结果必弹：无论成功/最新/异常都通知用户
                if (vc > BuildConfig.VERSION_CODE && zipUrl.isNotEmpty()) {
                    onNew(ver, zipUrl, (logText + "\n（通道：$channel）").trim())
                } else {
                    onNone()
                }
            }
        } catch (t: Throwable) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onError(t.message ?: "网络错误")
            }
        } finally {
            updateChecking.set(false)
        }
    }.start()
}

/** 自定义顶栏：InstallerX 风格大标题（28sp、左对齐、沉稳靠下），右侧放操作按钮 */
@Composable
private fun CustomTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // v0.6.2：标题整体下移（top 18 → 30dp），四个 tab 页标题统一往下靠，
            // 与首页/流转/消息/设置的内容一起下移对齐
            .padding(start = 20.dp, end = 12.dp, top = 30.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/** 读取并清空上次闪退日志（无则 null） */
private fun readCrashLog(ctx: android.content.Context): String? {
    return runCatching {
        val f = java.io.File(ctx.filesDir, "hf_crash.log")
        if (!f.exists() || f.length() == 0L) return null
        val log = f.readText()
        f.delete()
        log.take(1000)
    }.getOrNull()
}

/** 跳转 KernelSU 管理器（多候选包名，am start -p 拉起主界面）——与引导页同款 */
private fun downloadUpdateZip(ctx: Context, url: String, onDone: (Boolean, String) -> Unit) {
    Thread {
        // 多通道并行下载：镜像 + GitHub 直连，谁先完成用谁（挂代理/境外网络时直连更快）
        val candidates = mutableListOf(
            url,
            url.replace("https://ghproxy.net/https://github.com/", "https://github.com/"),
            url.replace("https://ghfast.top/https://github.com/", "https://github.com/"),
            url.replace("https://gh-proxy.com/https://github.com/", "https://github.com/"),
        ).distinct()
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val errHolder = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val latch = java.util.concurrent.CountDownLatch(1)
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        candidates.forEach { u ->
            Thread {
                if (done.get()) return@Thread
                runCatching {
                    val conn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "HyperFlow/" + BuildConfig.VERSION_NAME)
                    val f = java.io.File(dir, "HyperFlow-update.zip")
                    f.outputStream().use { outs ->
                        conn.inputStream.use { ins -> ins.copyTo(outs) }
                    }
                    if (f.length() < 1_000_000L) throw java.io.IOException("下载不完整（${f.length()}B）")
                    if (done.compareAndSet(false, true)) {
                        onDone(true, f.absolutePath)
                        latch.countDown()
                    }
                }.onFailure {
                    errHolder.compareAndSet(null, it.message ?: "下载失败")
                }
            }.start()
        }
        latch.await(120, java.util.concurrent.TimeUnit.SECONDS)
        if (!done.get()) {
            onDone(false, errHolder.get() ?: "所有通道下载失败")
        }
    }.start()
}

    /** 调用系统浏览器下载（通知栏显示进度、支持断点），优于应用内无进度下载；返回是否成功 */
    private fun openBrowserDownload(ctx: Context, url: String): Boolean {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

private fun launchKernelSu() {
    // 官方 KernelSU 管理器包名 me.weishu.kernelsu 曾在列表中缺失 → "去 KSU 检测"点了没反应。
    // 与 GuideScreen 同款：pm path 校验存在 + am start 显式类名，失败再 monkey 兜底启动 LAUNCHER。
    val pkgs = listOf(
        "me.weishu.kernelsu", "me.weishu.kernelsu.next",
        "com.rifsxd.ksunext", "com.rifsxd.ksu",
        "com.kernelsu.manager", "com.kernelsu"
    )
    Thread {
        for (pkg in pkgs) {
            val out = runCatching { RootExec.su("pm path $pkg 2>/dev/null") }.getOrNull()
            if (!out.isNullOrBlank() && out.contains("package:")) {
                runCatching {
                    RootExec.su("am start -n $pkg/.ui.activity.MainActivity 2>/dev/null || monkey -p $pkg -c android.intent.category.LAUNCHER 1")
                }
                break
            }
        }
    }.start()
}
