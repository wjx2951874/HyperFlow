// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.hyperflowplus.ui.liquid

// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * A [Backdrop] that draws [first] then [second] in order, allowing a tinted/overlay
 * backdrop to be sampled on top of a base backdrop. Mirrors Kyant's `CombinedBackdrop`
 * pattern used in `LiquidBottomTabs` to layer a recorded "tinted tabs" pass over the
 * underlying app background as a single sampling source for an indicator.
 */
/**
 * 组合 backdrop：按顺序先画 [first] 再画 [second]。
 *
 * 用途：把"着色/覆盖层"采样叠在"基础 backdrop"之上，作为指示器
 * （选中项胶囊）的单一采样源——否则指示器只能采到基础层，看不到
 * 上面覆盖层的内容。
 *
 * 中文注释：本类在当前版本（v0.5.3，InstallerX 同款结构）中由
 * 胶囊内部 rememberLayerBackdrop() 自捕获替代，主要保留给
 * 需要多层 backdrop 合成的场景复用。
 */
@Stable
class CombinedBackdrop(val first: Backdrop, val second: Backdrop) : Backdrop {

    override val isCoordinatesDependent: Boolean = first.isCoordinatesDependent || second.isCoordinatesDependent

    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

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
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop = remember(first, second) { CombinedBackdrop(first, second) }
