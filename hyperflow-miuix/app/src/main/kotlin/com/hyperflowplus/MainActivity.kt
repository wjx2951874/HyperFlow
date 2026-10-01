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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
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
import com.hyperflowplus.ui.FlowScreen
import com.hyperflowplus.ui.HomeScreen
import com.hyperflowplus.ui.GuideScreen
import com.hyperflowplus.ui.GuideType
import com.hyperflowplus.ui.BarBlurHost
import com.hyperflowplus.ui.HyperDialog
import com.hyperflowplus.ui.LiquidNavBar
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
        var showUpd by remember { mutableStateOf(false) }
        var showLicenses by remember { mutableStateOf(false) }
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
            MainHolder.onReopenOnboarding = { state.showOnboarding = true }
            MainHolder.onCheckUpdate = {
                // 检测更新：弹 MIUI 风格小窗，检测中转圈，结果在窗内展示
                updPhase = "checking"
                updMsg = ""
                showUpd = true
                checkUpdate(
                    onNew = { ver, url, log ->
                        runCatching {
                            updVer = ver; updUrl = url; updLog = log
                            updPhase = "new"; showUpd = true
                        }
                    },
                    onNone = {
                        runCatching {
                            updMsg = "当前使用的是 V${BuildConfig.VERSION_NAME}。"
                            updPhase = "none"; showUpd = true
                        }
                    },
                    onError = {
                        runCatching {
                            updMsg = it
                            updPhase = "error"; showUpd = true
                        }
                    },
                    onBusy = {
                        runCatching {
                            updMsg = "正在检查中，请稍候再试"
                            updPhase = "busy"; showUpd = true
                        }
                    }
                )
            }
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


        // 会话详情（独立覆盖页，返回手势/返回键返回列表）
        val conversation = state.currentConversation
        if (conversation != null) {
            BackHandler { state.currentConversation = null }
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                ConversationScreen(state, conversation.first, conversation.second)
            }
            return@MiuixTheme
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
        // 开源许可覆盖页（设置页进入）
        if (showLicenses) {
            BackHandler { showLicenses = false }
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                LicensesScreen(onBack = { showLicenses = false })
            }
            return@MiuixTheme
        }

        val tabs = listOf(
            Tab("首页", Icons.Filled.Home),
            Tab("流转", Icons.Filled.Send),
            Tab("消息", Icons.Filled.Notifications),
            Tab("设置", Icons.Filled.Settings)
        )
        // 四个页面各显示页面名（软件名移到设置页关于区）
        val title = when (tab) { 0 -> "首页"; 1 -> "流转"; 2 -> "消息"; 3 -> "设置"; else -> "" }

        // BarBlurHost 包住整个 Scaffold（含 bottomBar）→ backdrop 对导航栏可见，液态玻璃才真正生效
        BarBlurHost(enabled = state.glassEffect) {
        Scaffold(
            containerColor = MiuixTheme.colorScheme.surface,
            topBar = {
                CustomTopBar(
                    title = title,
                    modifier = Modifier.background(MiuixTheme.colorScheme.surface)
                ) {
                    if (tab == 2) {
                        Box {
                            IconButton(onClick = { showSort = true }) {
                                top.yukonga.miuix.kmp.basic.Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
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
                    }
                }
            },
            bottomBar = {
                // 悬浮导航栏：不占 Scaffold 底部（内容全屏，胶囊浮在上面，只有悬浮部分遮挡）
                // 普通/液态全宽导航栏：正常占位
                if (!state.navFloat) {
                    LiquidNavBar(
                        selectedTabIndex = tab,
                        onTabSelected = { tab = it },
                        items = tabs.map { it.icon to it.title },
                        floatEnabled = false,
                        glassEnabled = state.glassEffect
                    )
                }
            }
            ) { padding ->
                val contentMod = Modifier
                    .fillMaxSize()
                    .padding(padding)
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                    when (tab) {
                        0 -> HomeScreen(state, contentMod, onOpenGuide = { guideType = it })
                        1 -> FlowScreen(state, contentMod)
                        2 -> MessagesScreen(state, contentMod)
                        3 -> SettingsScreen(state, contentMod)
                    }
                    // 悬浮导航栏：覆盖在内容之上（浮于底部中央，不挤占内容）
                    if (state.navFloat) {
                        LiquidNavBar(
                            selectedTabIndex = tab,
                            onTabSelected = { tab = it },
                            items = tabs.map { it.icon to it.title },
                            floatEnabled = true,
                            glassEnabled = state.glassEffect
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

                    bottomInset = 40.dp,                    title = "检查更新",
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

                    bottomInset = 40.dp,                    // KSU 模块页「更新日志」样式：版本号 + 可滚动更新说明 + 取消/更新
                    title = "更新日志",
                    summary = updVer,
                    show = showUpd,
                    onDismiss = { showUpd = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 230.dp)
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

                    bottomInset = 40.dp,                    title = "正在检查更新",
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

                    bottomInset = 40.dp,                    title = "已是最新版本",
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

                    bottomInset = 40.dp,                    title = "检查更新失败",
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
                bottomInset = 40.dp,
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
                bottomInset = 40.dp,
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
                bottomInset = 40.dp,
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
                bottomInset = 40.dp,
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
                imageVector = Icons.Filled.CheckCircle,
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
            val channel = best?.let { b ->
                results.entries.firstOrNull { it.value == b }?.key
            }?.let { u -> runCatching { java.net.URI(u).host }.getOrNull() } ?: "多通道"
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                // 结果必弹：无论成功/最新/异常都通知用户
                if (vc > BuildConfig.VERSION_CODE && zipUrl.isNotEmpty()) {
                    onNew(ver, zipUrl, (json.optString("changelog", "") + "\n（通道：$channel）").trim())
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
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
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
    RootExec.su("for p in com.kernelsu.manager com.kernelsu com.rifsxd.ksunext; do " +
            "pm path \$p >/dev/null 2>&1 && { am start --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p \$p >/dev/null 2>&1 && break; }; done")
}
