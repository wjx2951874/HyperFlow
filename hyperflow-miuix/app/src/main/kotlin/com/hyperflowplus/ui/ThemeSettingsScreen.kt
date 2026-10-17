package com.hyperflowplus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主题与显示设置（回退到 1.0.1 简洁样式）
 * - 深色模式：跟随系统 / 浅色 / 深色
 * - 悬浮导航栏
 * - 液态玻璃（仅悬浮开启时显示）
 */
@Composable
fun ThemeSettingsScreen(state: HFState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sScroll = rememberScrollState()
    LaunchedEffect(sScroll) {
        androidx.compose.runtime.snapshotFlow { sScroll.value }.collect { MainHolder.scrollTick++ }
    }

    // 深色模式：system/light/dark
    var themeMode by remember { mutableStateOf(state.themeMode) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部返回栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "←",
                fontSize = 24.sp,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "主题与显示",
                fontSize = 20.sp,
                color = MiuixTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(sScroll)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ===== 外观 =====
            SmallTitle("外观")

            Card(Modifier.fillMaxWidth()) {
                Column {
                    // 深色模式选择
                    ArrowPreference(
                        title = "深色模式",
                        summary = when (themeMode) {
                            "light" -> "浅色"
                            "dark" -> "深色"
                            else -> "跟随系统"
                        },
                        onClick = {
                            // 循环切换：system → light → dark → system
                            themeMode = when (themeMode) {
                                "system" -> "light"
                                "light" -> "dark"
                                else -> "system"
                            }
                            state.setString("theme_mode", themeMode)
                        }
                    )

                    // 悬浮导航栏
                    SwitchPreference(
                        title = "悬浮导航栏",
                        summary = if (state.navFloat) "已开启悬浮导航栏" else "关闭后使用系统导航栏",
                        checked = state.navFloat,
                        onCheckedChange = { state.set(Config.KEY_NAV_FLOAT, it) }
                    )

                    // 液态玻璃（仅悬浮开启时显示）
                    if (state.navFloat) {
                        SwitchPreference(
                            title = "液态玻璃",
                            summary = if (state.glassEffect) "已开启系统级液态玻璃" else "关闭后使用系统默认外观",
                            checked = state.glassEffect,
                            onCheckedChange = { state.set(Config.KEY_GLASS, it) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
