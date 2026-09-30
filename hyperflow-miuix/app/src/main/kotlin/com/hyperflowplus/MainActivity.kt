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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
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
        LaunchedEffect(Unit) { state.loadAll() }
        // 引导判断：首次（配置无标记）显示引导页
        LaunchedEffect(state.cfg) {
            state.showOnboarding = !state.cfg.optBoolean("first_run_done", false)
        }

        var tab by remember { mutableIntStateOf(0) }
        var showSort by remember { mutableStateOf(false) }

        // 会话详情（独立覆盖页，返回手势/返回键返回列表）
        val conversation = state.currentConversation
        if (conversation != null) {
            BackHandler { state.currentConversation = null }
            ConversationScreen(conversation.first, conversation.second)
            return@MiuixTheme
        }

        val tabs = listOf(
            Tab("首页", Icons.Filled.Home),
            Tab("消息", Icons.Filled.Notifications),
            Tab("设置", Icons.Filled.Settings)
        )
        val title = when (tab) { 1 -> "消息"; 2 -> "设置"; else -> "HyperFlow" }
        val subtitle = if (tab == 0) state.subtitle else null

        // AndroidLiquidGlass：LayerBackdrop 捕获壁纸层 + drawPlainBackdrop 模糊玻璃
        val backdrop = rememberLayerBackdrop()
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.glassOn) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { WallpaperLayer() }
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFF3F5F7)))
            }

            Scaffold(
                modifier = if (state.glassOn)
                    Modifier.fillMaxSize()
                        .drawPlainBackdrop(backdrop, shape = { RoundedCornerShape(24.dp) }) { blur(16f) }
                else
                    Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = title,
                        subtitle = subtitle,
                        actions = {
                            if (tab == 1) {
                                IconButton(onClick = { showSort = true }) {
                                    top.yukonga.miuix.kmp.basic.Icon(
                                        imageVector = Icons.Filled.List,
                                        contentDescription = "排序"
                                    )
                                }
                            }
                        }
                    )
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
                when (tab) {
                    0 -> HomeScreen(state, contentMod)
                    1 -> MessagesScreen(state, contentMod)
                    else -> SettingsScreen(state, contentMod)
                }
            }
        }

        // 排序弹窗（消息页右上角）
        if (showSort) {
            OverlayDialog(
                title = "消息排序",
                show = showSort,
                onDismissRequest = { showSort = false }
            ) {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        "消息列表",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    SortRow(
                        current = state.archiveSort,
                        onClick = { state.setSort(Config.KEY_ARCHIVE_SORT); showSort = false }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正文列表",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    SortRow(
                        current = state.detailSort,
                        onClick = { state.setSort(Config.KEY_DETAIL_SORT); showSort = false }
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
private fun SortRow(current: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (current == "asc") "旧时间在前" else "新时间在前",
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            "点击切换",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}

/** 壁纸层（玻璃开启时被 backdrop 捕获） */
@Composable
private fun WallpaperLayer() {
    val ctx = LocalContext.current
    val bmp = remember(ctx) {
        runCatching {
            val d = WallpaperManager.getInstance(ctx).drawable
            if (d is BitmapDrawable) d.bitmap.asImageBitmap() else null
        }.getOrNull()
    }
    Box(Modifier.fillMaxSize()) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFFF3F5F7)))
        }
    }
}
