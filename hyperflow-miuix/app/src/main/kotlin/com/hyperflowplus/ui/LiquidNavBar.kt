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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// ===== 液态效果：鲜艳度 + 模糊 + 折射透镜 =====

internal fun BackdropEffectScope.vibrancy() {
    colorControls(brightness = 0f, contrast = 1f, saturation = 1.5f)
}

internal fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported() || refractionHeight <= 0f || refractionAmount <= 0f) return
    if (padding < refractionAmount) padding = refractionAmount
    val radii = roundedRectCornerRadii() ?: return
    val hasDispersion = chromaticAberration > 0f
    val scale = downscaleFactor.coerceAtLeast(1).toFloat()
    runtimeShaderEffect(
        key = if (hasDispersion) "HyperFlowLiquidLensDispersion" else "HyperFlowLiquidLens",
        shaderString = if (hasDispersion) RefractionWithDispersionShader else RefractionShader,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", size.width / scale, size.height / scale)
        setFloatUniform("offset", -padding / scale, -padding / scale)
        setFloatUniform("cornerRadii", FloatArray(radii.size) { radii[it] / scale })
        setFloatUniform("refractionHeight", refractionHeight / scale)
        setFloatUniform("refractionAmount", -refractionAmount / scale)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (hasDispersion) setFloatUniform("chromaticAberration", chromaticAberration)
    }
}

private fun BackdropEffectScope.roundedRectCornerRadii(): FloatArray? {
    val corners = shape as? CornerBasedShape ?: return null
    val maximum = size.minDimension / 2f
    val leftToRight = layoutDirection == LayoutDirection.Ltr
    val topLeft = if (leftToRight) corners.topStart else corners.topEnd
    val topRight = if (leftToRight) corners.topEnd else corners.topStart
    val bottomRight = if (leftToRight) corners.bottomEnd else corners.bottomStart
    val bottomLeft = if (leftToRight) corners.bottomStart else corners.bottomEnd
    return floatArrayOf(
        topLeft.toPx(size, this).fastCoerceAtMost(maximum),
        topRight.toPx(size, this).fastCoerceAtMost(maximum),
        bottomRight.toPx(size, this).fastCoerceAtMost(maximum),
        bottomLeft.toPx(size, this).fastCoerceAtMost(maximum),
    )
}

private val RoundedRectSdf = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private val RefractionShader = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$RoundedRectSdf

float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) return content.eval(coord);
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) +
        depthEffect * normalize(centeredCoord)
    );
    return content.eval(coord + d * grad);
}
"""

private val RefractionWithDispersionShader = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$RoundedRectSdf

float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) return content.eval(coord);
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) +
        depthEffect * normalize(centeredCoord)
    );
    float2 refractedCoord = coord + d * grad;
    float intensity = chromaticAberration *
        ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersion = d * grad * intensity;
    half4 color = half4(0.0);
    half4 red = content.eval(refractedCoord + dispersion);
    color.r += red.r / 3.5; color.a += red.a / 7.0;
    half4 orange = content.eval(refractedCoord + dispersion * (2.0 / 3.0));
    color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;
    half4 yellow = content.eval(refractedCoord + dispersion * (1.0 / 3.0));
    color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5; color.a += green.a / 7.0;
    half4 cyan = content.eval(refractedCoord - dispersion * (1.0 / 3.0));
    color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0;
    half4 blue = content.eval(refractedCoord - dispersion * (2.0 / 3.0));
    color.b += blue.b / 3.0; color.a += blue.a / 7.0;
    half4 purple = content.eval(refractedCoord - dispersion);
    color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;
    return color;
}
"""

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

// ===== 悬浮模式 + 液态玻璃（核心效果） =====

@Composable
private fun FloatingLiquidBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>,
    backdrop: Backdrop,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pillShape = CircleShape
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val tabContentColor = MiuixTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp + navBottom),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .width(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .selectableGroup()
                .onGloballyPositioned { coordinates ->
                    totalWidthPx = coordinates.size.width.toFloat()
                    tabWidthPx = ((totalWidthPx - with(density) { 8.dp.toPx() }) / items.size)
                        .coerceAtLeast(0f)
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
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { IndicatorSpecular.copy(alpha = 0.75f) },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(64.dp)
                .padding(4.dp),
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
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = tabContentColor,
                        maxLines = 1,
                    )
                }
            }
        }

        // 选中指示器：液态胶囊（折射 + 色散 + 高光）
        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val offset = selectedTabIndex * tabWidthPx
                        translationX = offset + (tabWidthPx / 2f) - (tabWidthPx - 8.dp.toPx()) / 2f
                    }
                    .layerBackdrop(tabsBackdrop)
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
                                color = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)
                                else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.1f),
                            )
                            drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.03f))
                        },
                    )
                    .height(56.dp)
                    .width(with(density) { tabWidthPx.toDp() }),
            )
        }
    }
}
