package com.hyperflowplus

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
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
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller) {
        val state = HFState
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

        // 柔光玻璃（纯位图 + Compose RenderEffect 模糊：仅顶栏/底栏区域磨砂，
        // 内容区保持主题色不透明，避免全屏渲染树在部分机型黑屏/白屏）
        val glassTint = MiuixTheme.colorScheme.surface

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.glassOn) {
                WallpaperLayer()
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFF3F5F7)))
            }

            Scaffold(
                containerColor = if (state.glassOn) Color.Transparent else MiuixTheme.colorScheme.surface,
                topBar = {
                    if (state.glassOn) {
                        Box {
                            LiquidGlassBar(state.glassBlur, top = true, Modifier.matchParentSize())
                            CustomTopBar(
                                title = title,
                                modifier = Modifier.background(Color.Transparent)
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
                        }
                    } else {
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
                    }
                },
                bottomBar = {
                    if (state.glassOn) {
                        Box {
                            LiquidGlassBar(state.glassBlur, top = false, Modifier.matchParentSize())
                            NavigationBar(color = Color.Transparent) {
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
                    } else {
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
                        else -> SettingsScreen(state, contentMod)
                    }
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

/** 壁纸位图（懒加载，缓存一次） */
@Composable
private fun rememberWallpaperBitmap(): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching {
            val d = WallpaperManager.getInstance(ctx).drawable
            if (d is BitmapDrawable) d.bitmap.asImageBitmap() else null
        }.getOrNull()
    }
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

/**
 * 液态玻璃栏背景（iOS 26 Liquid Glass 安卓实现，对标 Kyant0/AndroidLiquidGlass）：
 * 壁纸画入 CanvasBackdrop → 高斯模糊打底 → lens 折射（SDF 圆角折射 + 深度 + 色散）→ 边缘暗调。
 * 不用 layerBackdrop（避免渲染树黑屏问题），背景直接画位图，安全不黑屏。
 */
@Composable
private fun LiquidGlassBar(blurRadius: Int, top: Boolean, modifier: Modifier = Modifier) {
    val bmp = rememberWallpaperBitmap()
    val backdrop = rememberCanvasBackdrop {
        if (bmp != null) {
            // 壁纸近似 Crop 填充（液态玻璃折射的是壁纸本身）
            val area = this.size
            val sw = bmp.width.toFloat()
            val sh = bmp.height.toFloat()
            val scale = maxOf(area.width / sw, area.height / sh)
            val srcW = area.width / scale
            val srcH = area.height / scale
            drawImage(
                bmp,
                srcOffset = IntOffset(((sw - srcW) / 2f).toInt(), ((sh - srcH) / 2f).toInt()),
                srcSize = IntSize(srcW.toInt(), srcH.toInt()),
                dstOffset = Offset.Zero,
                dstSize = IntSize(area.width.toInt(), area.height.toInt()),
                filterQuality = FilterQuality.Medium
            )
        } else {
            drawRect(Color(0xFFF3F5F7))
        }
    }
    val shape = if (top) {
        AbsoluteRoundedCornerShape(0.dp, 0.dp, 28.dp, 28.dp)
    } else {
        AbsoluteRoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp)
    }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .drawPlainBackdrop(
                    backdrop,
                    shape = { shape },
                    effects = {
                        blur(blurRadius.coerceAtLeast(8).toFloat())
                        lens(
                            refractionHeight = 60f,
                            refractionAmount = 14f,
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    }
                )
        ) {}
        // 边缘暗调（玻璃的景深过渡）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f))
                    )
                )
        )
    }
}

/** 壁纸层（玻璃开启时全屏背景 = 原壁纸） */
@Composable
private fun WallpaperLayer() {
    val bmp = rememberWallpaperBitmap()
    Box(Modifier.fillMaxSize()) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFFF3F5F7)))
        }
    }
}
