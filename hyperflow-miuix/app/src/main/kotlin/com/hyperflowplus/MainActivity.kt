package com.hyperflowplus

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import com.hyperflowplus.ui.ConversationScreen
import com.hyperflowplus.ui.HomeScreen
import com.hyperflowplus.ui.MessagesScreen
import com.hyperflowplus.ui.SettingsScreen

/** HyperFlow V0.4.0 —— Miuix(Compose) 全量重写入口 */
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

        var tab by remember { mutableIntStateOf(0) }
        // 会话详情（独立于 tab 的覆盖页，系统返回手势/返回键返回）
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

        // 柔光玻璃：壁纸 + 半透明白层 + 内容区模糊（miuix-blur，API33+）
        val backdrop = rememberLayerBackdrop()
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            if (state.glassOn) {
                WallpaperLayer()
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFF3F5F7)))
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = "HyperFlow",
                        subtitle = state.subtitle
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
                    .then(
                        if (state.glassOn && isRuntimeShaderSupported())
                            Modifier.textureBlur(backdrop, 16f)
                        else Modifier
                    )
                when (tab) {
                    0 -> HomeScreen(state, contentMod)
                    1 -> MessagesScreen(state, contentMod)
                    else -> SettingsScreen(state, contentMod)
                }
            }
        }
    }
}

/** 壁纸层（玻璃开启时展示） */
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
        // 半透明白层：MIUI 玻璃质感
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.72f)))
    }
}
