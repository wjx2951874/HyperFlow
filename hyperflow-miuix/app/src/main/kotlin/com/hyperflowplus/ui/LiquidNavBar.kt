package com.hyperflowplus.ui

// 液态玻璃导航栏 —— 参考 HyperIsland（Apache-2.0，源自 SukiSU-Ultra 的 FloatingBottomBar
// 与 compose-miuix-ui 的 IosLiquidGlassNavigationBar 示例）

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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

// ===== 三层液态玻璃核心（InstallerX/KernelSU 同源：基础层 + 透明捕获层 + 组合折射指示器） =====

@Composable
private fun RowScope.LiquidTabs(
    items: List<Pair<ImageVector, String>>,
    active: Boolean,
    onTabSelected: (Int) -> Unit,
    tabWidth: androidx.compose.ui.unit.Dp,
) {
    val onSurface = MiuixTheme.colorScheme.onSurface
    val color = if (active) MiuixTheme.colorScheme.primary
    else onSurface.copy(alpha = 0.65f)
    items.forEachIndexed { i, (icon, label) ->
        Column(
            modifier = Modifier
                .width(tabWidth.coerceAtLeast(44.dp))
                .clickable { onTabSelected(i) }
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, color = color)
        }
    }
}

/** 共享三层结构：基础层（玻璃胶囊）+ 透明捕获层（选中态内容）+ 组合折射指示器胶囊 */
@Composable
private fun LiquidGlassLayers(
    items: List<Pair<ImageVector, String>>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    floating: Boolean,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pillShape = RoundedCornerShape(28.dp)
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val density = LocalDensity.current
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val indicatorAnim by animateFloatAsState(
        targetValue = selectedTabIndex.toFloat(),
        animationSpec = tween(320),
        label = "liquidIndicator"
    )
    val tabWidthDp = with(density) { tabWidthPx.toDp() }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // ===== 基础层（未选中态玻璃胶囊） =====
        Row(
            modifier = Modifier
                .selectableGroup()
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
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidTabs(items, active = false, onTabSelected = onTabSelected, tabWidth = tabWidthDp)
        }
        // ===== 透明捕获层：选中态内容写入 tabsBackdrop（不可见，仅供指示器折射） =====
        Row(
            modifier = Modifier
                .clearAndSetSemantics {}
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
            LiquidTabs(items, active = true, onTabSelected = onTabSelected, tabWidth = tabWidthDp)
        }
        // ===== 指示器层：combinedBackdrop（背景+选中内容）折射胶囊 =====
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
                        highlight = { IndicatorSpecular.copy(alpha = 1f) },
                        onDrawSurface = {
                            drawRect(
                                if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)
                                else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.1f)
                            )
                        },
                    )
                    .innerShadow(shape = pillShape) {
                        com.hyperflowplus.ui.liquid.InnerShadow(
                            radius = 8.dp,
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f),
                            alpha = 1f,
                        )
                    }
                    .height(56.dp)
                    .width(tabWidthDp.coerceAtLeast(44.dp)),
            ) {}
        }
    }
}

// ===== 普通全宽液态（三层） =====

@Composable
private fun RowLiquidBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>,
    backdrop: Backdrop,
) {
    LiquidGlassLayers(items, selectedTabIndex, onTabSelected, backdrop, floating = false)
}

// ===== 悬浮液态（三层，底部上浮胶囊） =====

@Composable
private fun FloatingLiquidBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>,
    backdrop: Backdrop,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp + navBottom),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassLayers(items, selectedTabIndex, onTabSelected, backdrop, floating = true)
    }
}
