package com.hyperflowplus.ui

// 底部导航栏 —— V0.4.43 重构：
//   - 悬浮模式：直接使用 InstallerX Revived 原版 FloatingBottomBar（液态玻璃/模糊/普通三态），
//     不再用自绘三层简化版（曾导致悬浮空白/点击消失/闪退）。
//   - 普通模式：Miuix NavigationBar。
//   - 液态玻璃 = 悬浮胶囊上的玻璃效果（InstallerX 形态：只有悬浮的那部分遮住内容，
//     可跨导航栏互进），不再有"全宽液态"（曾下半屏全遮挡）。
// liquid/ 下的 Lens/Vibrancy/CombinedBackdrop/InnerShadow 与 InstallerX 原版逐行一致。

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ===== 背景捕获宿主：液态玻璃需要先捕获整页内容作 backdrop =====

val LocalLiquidBackdrop = compositionLocalOf<Backdrop?> { null }

@Composable
fun BarBlurHost(enabled: Boolean, content: @Composable () -> Unit) {
    val surface = MiuixTheme.colorScheme.surface
    // 玻璃根因修复：backdrop 捕获整页内容，供悬浮胶囊折射（InstallerX/KernelSU 同源做法）；
    // blur 走 RenderEffect Android 12+ 即可用，lens 内部自行判断 shader 支持并降级
    val backdrop = if (enabled) {
        rememberLayerBackdrop {
            drawRect(surface)
            drawContent()
        }
    } else {
        null
    }
    CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) { content() }
}

// ===== 普通模式：Miuix NavigationBar =====

@Composable
private fun PlainBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>,
) {
    NavigationBar {
        items.forEachIndexed { i, (icon, label) ->
            NavigationBarItem(
                selected = selectedTabIndex == i,
                onClick = { onTabSelected(i) },
                icon = icon,
                label = label,
            )
        }
    }
}

// ===== 主入口：悬浮胶囊（InstallerX 同款）/ 普通导航栏 =====

@Composable
fun LiquidNavBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>,
    floatEnabled: Boolean,
    glassEnabled: Boolean,
    backdrop: LayerBackdrop? = null,
) {
    if (items.isEmpty()) return
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (floatEnabled) {
        // 悬浮胶囊：液态玻璃 / 模糊 / 普通 三态（与 InstallerX Revived 一致）
        // backdrop 由 MainActivity 在 Scaffold 内容区挂 layerBackdrop 捕获页面内容
        // （InstallerX 同款结构：胶囊在内容层之外绘制，采样不会卷入自身，不递归不闪退）；
        // 未传入时（仅悬浮无玻璃）内部自建空 backdrop 走普通绘制。
        val bd = backdrop ?: rememberLayerBackdrop()
        val mode = when {
            glassEnabled -> FloatingBottomBarMode.LiquidGlass
            else -> FloatingBottomBarMode.None
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp + navBottom)
                // 胶囊上移进内容区底部：backdrop 由 MainActivity 挂在 Scaffold 内容区 Box 上
                // （内容区在 bottomBar 槽上方），胶囊若留在槽内，采样区域落在内容区纹理之外
                // → 采样透明黑 → 玻璃显示黑底（浅色模式尤为明显）。
                // 上移 76dp（胶囊 64dp + 底部 12dp 空隙）后胶囊悬浮在内容区底部之上，
                // 正确折射页面内容；内容区已补 88dp 底部留白，列表可滚动到胶囊上方。
                .graphicsLayer {
                    translationY = -with(LocalDensity.current) { 76.dp.toPx() }
                },
            contentAlignment = Alignment.Center,
        ) {
            FloatingBottomBar(
                items = items,
                selectedIndex = { selectedTabIndex },
                onSelected = onTabSelected,
                backdrop = bd,
                mode = mode,
                iconContent = { item, _ ->
                    Icon(
                        imageVector = item.first,
                        contentDescription = null,
                        tint = LocalFloatingBottomBarContentColor.current,
                        modifier = Modifier.size(24.dp)
                    )
                },
                labelContent = { item, _ ->
                    Text(
                        item.second,
                        fontSize = 11.sp,
                        color = LocalFloatingBottomBarContentColor.current
                    )
                },
            )
        }
    } else {
        PlainBar(selectedTabIndex, onTabSelected, items)
    }
}
