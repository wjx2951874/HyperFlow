package com.hyperflowplus

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import com.hyperflowplus.ui.ConversationScreen
import com.hyperflowplus.ui.HomeScreen
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
    val keyColor = remember(ctx) { wallpaperKeyColor(ctx) }   // 壁纸主色（Monet 种子）
    val palette = when (HFState.paletteStyle) {
        "neutral" -> ThemePaletteStyle.Neutral
        "vibrant" -> ThemePaletteStyle.Vibrant
        "expressive" -> ThemePaletteStyle.Expressive
        "fruit_salad" -> ThemePaletteStyle.FruitSalad
        "monochrome" -> ThemePaletteStyle.Monochrome
        else -> ThemePaletteStyle.TonalSpot
    }
    val controller = remember(keyColor, palette) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.MonetSystem,   // 壁纸动态取色（MaterialKolor）
            keyColor = keyColor,
            paletteStyle = palette,
            colorSpec = ThemeColorSpec.Spec2025
        )
    }
    MiuixTheme(controller = controller) {
        val state = HFState
        val ctx = LocalContext.current
        LaunchedEffect(Unit) {
            state.loadAll()
            state.startFlowPolling()   // 归档实时刷新（短信流转到达即显示）
        }
        // 引导判断：首次（App 私有标记，root 无关）显示引导页
        LaunchedEffect(state.cfg) {
            state.showOnboarding = !state.firstRunDone
        }

        var tab by remember { mutableIntStateOf(0) }
        var showSort by remember { mutableStateOf(false) }
        var showUpd by remember { mutableStateOf(false) }
        var updVer by remember { mutableStateOf("") }
        var updUrl by remember { mutableStateOf("") }
        var updLog by remember { mutableStateOf("") }

        // 会话详情（独立覆盖页，返回手势/返回键返回列表）
        val conversation = state.currentConversation
        if (conversation != null) {
            BackHandler { state.currentConversation = null }
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                ConversationScreen(state, conversation.first, conversation.second)
            }
            return@MiuixTheme
        }

        val tabs = listOf(
            Tab("首页", Icons.Filled.Home),
            Tab("消息", Icons.Filled.Notifications),
            Tab("设置", Icons.Filled.Settings)
        )
        // 首页/消息/设置各显示页面名（软件名移到设置页关于区）
        val title = when (tab) { 0 -> "首页"; 1 -> "消息"; 2 -> "设置"; else -> "" }

        Scaffold(
            containerColor = MiuixTheme.colorScheme.surface,
            topBar = {
                CustomTopBar(
                    title = title,
                    modifier = Modifier.background(MiuixTheme.colorScheme.surface)
                ) {
                    if (tab == 1) {
                        IconButton(onClick = { showSort = true }) {
                            top.yukonga.miuix.kmp.basic.Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "排序"
                            )
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = t.icon,
                            label = t.title
                        )
                    }
                }
            }
            ) { padding ->
                val contentMod = Modifier
                    .fillMaxSize()
                    .padding(padding)
                // 内容区保持主题色不透明（玻璃只作用于顶栏/底栏）
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                    when (tab) {
                        0 -> HomeScreen(state, contentMod)
                        1 -> MessagesScreen(state, contentMod)
                        else -> SettingsScreen(
                            state,
                            onCheckUpdate = {
                                checkUpdate(
                                    onNew = { ver, url, log ->
                                        runCatching {
                                            updVer = ver; updUrl = url; updLog = log; showUpd = true
                                        }
                                    },
                                    onNone = {
                                        runCatching {
                                            Toast.makeText(ctx, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onError = {
                                        runCatching {
                                            Toast.makeText(ctx, "检查更新失败：$it", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                            contentMod
                        )
                    }
                }
            }

        // 排序弹窗（消息页右上角，KSU 风格：分组 + 单选行、选中高亮）
        if (showSort) {
            OverlayDialog(
                title = "排序",
                show = showSort,
                onDismissRequest = { showSort = false }
            ) {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        "消息列表排序",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                    SortOptionRow(
                        label = "发送人 A→Z",
                        selected = state.archiveSort == "name_asc",
                        onClick = {
                            if (state.archiveSort != "name_asc") state.setSortValue(Config.KEY_ARCHIVE_SORT, "name_asc")
                            showSort = false
                        }
                    )
                    SortOptionRow(
                        label = "发送人 Z→A",
                        selected = state.archiveSort == "name_desc",
                        onClick = {
                            if (state.archiveSort != "name_desc") state.setSortValue(Config.KEY_ARCHIVE_SORT, "name_desc")
                            showSort = false
                        }
                    )
                    SortOptionRow(
                        label = "最近接收在前",
                        selected = state.archiveSort == "time_desc" || state.archiveSort == "desc",
                        onClick = {
                            if (state.archiveSort != "time_desc") state.setSortValue(Config.KEY_ARCHIVE_SORT, "time_desc")
                            showSort = false
                        }
                    )
                    SortOptionRow(
                        label = "最早接收在前",
                        selected = state.archiveSort == "time_asc" || state.archiveSort == "asc",
                        onClick = {
                            if (state.archiveSort != "time_asc") state.setSortValue(Config.KEY_ARCHIVE_SORT, "time_asc")
                            showSort = false
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "正文排序",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                    SortOptionRow(
                        label = "最新在前",
                        selected = state.detailSort != "asc",
                        onClick = {
                            if (state.detailSort != "desc") state.setSort(Config.KEY_DETAIL_SORT)
                            showSort = false
                        }
                    )
                    SortOptionRow(
                        label = "最早在前",
                        selected = state.detailSort == "asc",
                        onClick = {
                            if (state.detailSort == "desc") state.setSort(Config.KEY_DETAIL_SORT)
                            showSort = false
                        }
                    )
                }
            }
        }

        // 发现新版本弹窗（root 层渲染，避免子页面弹窗崩溃）
        if (showUpd) {
            OverlayDialog(
                title = "发现新版本 V$updVer",
                summary = updLog.ifEmpty { "点击下载并一键安装（ksud module install）" },
                show = showUpd,
                onDismissRequest = { showUpd = false }
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(
                        text = "取消",
                        onClick = { showUpd = false },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = "下载并安装",
                        onClick = {
                            showUpd = false
                            Toast.makeText(ctx, "开始下载", Toast.LENGTH_SHORT).show()
                            Thread {
                                runCatching {
                                    val f = java.io.File(
                                        ctx.getExternalFilesDir(null) ?: ctx.filesDir,
                                        "HyperFlow.zip"
                                    )
                                    val conn = java.net.URL(updUrl).openConnection()
                                    conn.connectTimeout = 10000
                                    conn.inputStream.use { input ->
                                        f.outputStream().use { out -> input.copyTo(out) }
                                    }
                                    RootExec.su("ksud module install '" + f.absolutePath + "' && reboot")
                                }
                            }.start()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

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

private fun checkUpdate(
    onNew: (String, String, String) -> Unit,
    onNone: () -> Unit,
    onError: (String) -> Unit
) {
    Thread {
        val urls = listOf(Config.UPDATE_JSON, Config.UPDATE_JSON_FALLBACK)
        try {
            var lastErr: Throwable? = null
            var text = ""
            for (u in urls) {
                try {
                    val conn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.instanceFollowRedirects = true
                    text = conn.inputStream.bufferedReader().use { it.readText() }
                    break
                } catch (t: Throwable) { lastErr = t }
            }
            if (text.isEmpty()) throw lastErr ?: RuntimeException("更新通道不可达")
            val json = org.json.JSONObject(text)
            val ver = json.optString("version", "")
            val vc = json.optInt("versionCode", 0)
            val zipUrl = json.optString("zipUrl", "")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                // UI 回调全部兜底，任何异常不闪退
                runCatching {
                    if (vc > BuildConfig.VERSION_CODE && zipUrl.isNotEmpty()) {
                        onNew(ver, zipUrl, json.optString("changelog", ""))
                    } else {
                        onNone()
                    }
                }
            }
        } catch (t: Throwable) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                runCatching { onError(t.message ?: "网络错误") }
            }
        }
    }.start()
}

/** 自定义顶栏：左对齐大标题（往上走、醒目），右侧放操作按钮 */
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/** 从系统壁纸提取主色（平均色）作为 Monet 动态配色种子 */
private fun wallpaperKeyColor(ctx: android.content.Context): Color {
    val d = WallpaperManager.getInstance(ctx).drawable
    val bmp = (d as? BitmapDrawable)?.bitmap
    if (bmp == null) return Color(0xFF0A84FF)
    return runCatching {
        val sm = android.graphics.Bitmap.createScaledBitmap(bmp, 8, 8, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (x in 0 until 8) for (y in 0 until 8) {
            val c = sm.getPixel(x, y)
            r += android.graphics.Color.red(c)
            g += android.graphics.Color.green(c)
            b += android.graphics.Color.blue(c)
            n++
        }
        sm.recycle()
        Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }.getOrElse { Color(0xFF0A84FF) }
}
