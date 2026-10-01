package com.hyperflowplus.ui

// 液态玻璃导航栏 —— 参考 HyperIsland（Apache-2.0，源自 SukiSU-Ultra 的 FloatingBottomBar
// 与 compose-miuix-ui 的 IosLiquidGlassNavigationBar 示例）

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import com.hyperflowplus.ui.liquid.innerShadow
import com.hyperflowplus.ui.liquid.lens
import com.hyperflowplus.ui.liquid.rememberCombinedBackdrop
import com.hyperflowplus.ui.liquid.vibrancy
import top.yukonga.miuix.kmp.theme.MiuixTheme
// ===== 背景捕获宿主：液态玻璃需要先捕获整页内容作 backdrop =====

val LocalLiquidBackdrop = compositionLocalOf<Backdrop?> { null }

@Composable
fun BarBlurHost(enabled: Boolean, content: @Composable () -> Unit) {
    val surface = MiuixTheme.colorScheme.surface
    // 玻璃根因修复：backdrop 不再依赖 isRuntimeShaderSupported（blur 走 RenderEffect Android12+ 即可用），
    // lens（折射）内部已自行判断 shader 支持，不支持时自动降级为纯模糊玻璃
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

// ===== 高光材质（双光源描边） =====


private val IndicatorSpecular = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = androidx.compose.ui.graphics.Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = androidx.compose.ui.graphics.Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

// ===== 组合 backdrop（外层背景 + 选中指示器内容） =====

@Stable
private class CombinedBackdrop(
    private val first: Backdrop,
    private val second: Backdrop,
) : Backdrop {
    override val isCoordinatesDependent: Boolean
        get() = first.isCoordinatesDependent || second.isCoordinatesDependent
    override val offsetResidualX: Float
        get() = first.offsetResidualX
    override val offsetResidualY: Float
        get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
private fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }

// ===== 主入口：四模式导航栏 =====

@Composable
fun LiquidNavBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>,
    floatEnabled: Boolean,
    glassEnabled: Boolean,
) {
    if (items.isEmpty()) return
    val backdrop = LocalLiquidBackdrop.current
    val liquid = glassEnabled && backdrop != null

    when {
        floatEnabled && liquid -> FloatingLiquidBar(selectedTabIndex, onTabSelected, items, backdrop)
        floatEnabled -> FloatingPlainBar(selectedTabIndex, onTabSelected, items)
        liquid -> RowLiquidBar(selectedTabIndex, onTabSelected, items, backdrop)
        else -> PlainBar(selectedTabIndex, onTabSelected, items)
    }
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

// ===== 悬浮模式（无液态）：Miuix FloatingNavigationBar =====

@Composable
private fun FloatingPlainBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp + navBottom),
        contentAlignment = Alignment.Center,
    ) {
        top.yukonga.miuix.kmp.basic.FloatingNavigationBar {
            items.forEachIndexed { i, (icon, label) ->
                top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem(
                    selected = selectedTabIndex == i,
                    onClick = { onTabSelected(i) },
                    icon = icon,
                    label = label,
                )
            }
        }
    }
}

// ===== 普通模式 + 液态玻璃 =====

@Composable
private fun RowLiquidBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>,
    backdrop: Backdrop,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pillShape = RoundedCornerShape(28.dp)
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val contentColor = MiuixTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .dropShadow(
                shape = pillShape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = androidx.compose.ui.graphics.Color.Black,
                    alpha = if (isDark) 0.2f else 0.1f,
                ),
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { pillShape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx(), 4.dp.toPx())
                    lens(24.dp.toPx(), 24.dp.toPx())
                },
                highlight = { IndicatorSpecular.copy(alpha = 0.75f) },
                onDrawSurface = { drawRect(containerColor) },
            )
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, (icon, label) ->
            Column(
                modifier = Modifier
                    .selectable(
                        selected = selectedTabIndex == i,
                        interactionSource = null,
                        indication = null,
                        onClick = { onTabSelected(i) },
                    )
                    .fillMaxHeight()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
                Text(
                    label,
                    fontSize = 11.sp,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

// ===== 悬浮模式 + 液态玻璃（InstallerX/KernelSU 同源三层结构：compose-miuix-ui IosLiquidGlassNavigationBar） =====

@Composable
private fun FloatingLiquidBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>,
    backdrop: Backdrop,
) {
    // 三层结构：基础层（可交互 tab + 玻璃胶囊）/ 透明层（捕获选中内容到 tabsBackdrop）/ 指示器层（combinedBackdrop 折射胶囊跟随选中项）
    // 光效 = Miuix drawBackdrop + vibrancy 鲜艳度 + blur 模糊 + lens 折射 + innerShadow 内阴影（与 LSPosed Manager / InstallerX 同源）
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pillShape = RoundedCornerShape(28.dp)
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val density = LocalDensity.current
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val indicatorAnim by animateFloatAsState(
        targetValue = selectedTabIndex.toFloat(),
        animationSpec = tween(320),
        label = "flIndicator"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp + navBottom),
        contentAlignment = Alignment.Center,
    ) {
        // ===== 基础层：玻璃胶囊 + 可交互 tab =====
        Row(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    tabWidthPx = (totalWidthPx - with(density) { 8.dp.toPx() }) / items.size.coerceAtLeast(1)
                }
                .dropShadow(
                    shape = pillShape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = androidx.compose.ui.graphics.Color.Black,
                        alpha = if (isDark) 0.2f else 0.1f,
                    ),
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx(), 4.dp.toPx())
                        lens(refractionHeight = 24.dp.toPx(), refractionAmount = 24.dp.toPx())
                    },
                    highlight = { IndicatorSpecular.copy(alpha = 0.75f) },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .innerShadow(shape = pillShape) {
                    com.hyperflowplus.ui.liquid.InnerShadow(radius = 8.dp)
                }
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, (icon, label) ->
                val selected = selectedTabIndex == i
                Column(
                    modifier = Modifier
                        .width(with(density) { tabWidthPx.toDp() }.coerceAtLeast(56.dp))
                        .clickable { onTabSelected(i) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MiuixTheme.colorScheme.onSurface
                        else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = if (selected) MiuixTheme.colorScheme.onSurface
                        else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
        // ===== 透明层：捕获选中 tab 内容（供指示器层 combinedBackdrop 折射） =====
        Row(
            modifier = Modifier
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx(), 4.dp.toPx())
                        lens(refractionHeight = 24.dp.toPx(), refractionAmount = 24.dp.toPx())
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, (icon, label) ->
                Column(
                    modifier = Modifier
                        .width(with(density) { tabWidthPx.toDp() }.coerceAtLeast(56.dp))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface)
                }
            }
        }
        // ===== 指示器层：跟随选中 tab 的折射胶囊（lens 深度/色差 + 内阴影） =====
        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer { translationX = indicatorAnim * tabWidthPx }
                    .drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { pillShape },
                        effects = {
                            lens(
                                refractionHeight = 10.dp.toPx(),
                                refractionAmount = 14.dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = 0.5f,
                            )
                        },
                        highlight = { IndicatorSpecular.copy(alpha = 0.9f) },
                        onDrawSurface = { drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.03f)) },
                    )
                    .innerShadow(shape = pillShape) {
                        com.hyperflowplus.ui.liquid.InnerShadow(radius = 8.dp)
                    }
                    .width(with(density) { tabWidthPx.toDp() })
                    .height(56.dp)
            ) {}
        }
    }
}
