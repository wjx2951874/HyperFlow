package com.hyperflowplus.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 主题与显示设置（01 手册 §2/§3/§5/§11.7）：
 * - 二级页用 Scaffold + SmallTopAppBar（钉住单行顶栏 + 左返回），不再手搓 "←" 文本行；
 * - 深色模式三选一用 TabRow（独占一行、不进 Card）；
 * - 底栏形态三选一用 OverlayDropdownPreference，写回唯一的 bottom_bar_style 枚举；
 * - 间距走 12（Card）+ 16（行内边距）= 28（SmallTitle）基线。
 */
@Composable
fun ThemeSettingsScreen(state: HFState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // 深色模式：system/light/dark
    val themeValues = listOf("system", "light", "dark")
    val themeIndex = themeValues.indexOf(state.themeMode).coerceAtLeast(0)

    // 底栏形态：standard/floating/liquid
    val barValues = listOf("standard", "floating", "liquid")
    val barIndex = barValues.indexOf(state.bottomBarStyle).coerceAtLeast(0)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            // 01 手册 §11.7：二级页用钉住的小顶栏，左侧统一 MIUI 返回箭头
            SmallTopAppBar(
                title = "主题与显示",
                color = MiuixTheme.colorScheme.surface,
                titleColor = MiuixTheme.colorScheme.onSurface,
                navigationIcon = { MiuixBackButton(onClick = onBack) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .overScrollVertical(),                     // MIUI 拉伸回弹
            // 01 手册 §11.2：用 contentPadding（不是 Modifier.padding），内容可滚到顶栏下
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                // 系统导航条 inset（无底栏时由 Scaffold 提供）+ 悬浮胶囊避让
                bottom = innerPadding.calculateBottomPadding() +
                    if (MainHolder.bottomPad == androidx.compose.ui.unit.Dp.Unspecified)
                        0.dp else MainHolder.bottomPad,
            ),
        ) {
            // ===== 外观 =====
            item(key = "appearance") {
                SmallTitle("外观")
                // 01 手册 §5/§11.6：2–3 段互斥选项用 TabRow；独占一行，不放进 Card，
                // 只有自身 padding 能视觉内缩（内置 fillMaxWidth）
                TabRow(
                    tabs = listOf("跟随系统", "浅色", "深色"),
                    selectedTabIndex = themeIndex,
                    onTabSelected = { state.setString("theme_mode", themeValues[it]) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                )
            }

            // ===== 底栏 =====
            item(key = "navigation") {
                SmallTitle("导航")
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    OverlayDropdownPreference(
                        title = "底栏形态",
                        summary = "默认贴底 / 悬浮胶囊 / 液态玻璃胶囊",
                        items = listOf("默认", "悬浮", "液态玻璃"),
                        selectedIndex = barIndex,
                        onSelectedIndexChange = { state.setBottomBarStyle(barValues[it]) },
                    )
                }
            }

            item(key = "bottomGap") {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
