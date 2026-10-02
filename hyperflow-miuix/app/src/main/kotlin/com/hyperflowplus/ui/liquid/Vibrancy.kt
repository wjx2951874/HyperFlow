// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.hyperflowplus.ui.liquid

// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

/**
 * 玻璃"鲜艳度"增强（源自 AndroidLiquidGlass 的 vibrancy）。
 *
 * 在折射/模糊之外，把被玻璃覆盖区域的内容饱和度提到 1.5 倍，
 * 让透过玻璃看到的内容色彩更鲜活——这是液态玻璃与普通毛玻璃
 * 观感差异的关键一环（普通 blur 会把颜色磨灰，提饱和后更通透）。
 */
/** Lightweight stand-in for Kyant's `vibrancy()`. */
fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
