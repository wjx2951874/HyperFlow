package com.hyperflowplus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 1.1.0 主题设置页（严格按 00 规范 §8.2 / KernelSU ColorPaletteScreenMiuix.kt 结构）：
 *
 * 分组结构：
 *   外观 → 深色模式 TabRow(跟随系统/浅色/深色) + AMOLED 纯黑
 *   颜色 → 动态取色(Monet) + 主题色16选1(带调色图标) + Monet时显示调色板风格
 *   底栏 → 底栏形态(标准/悬浮/液态玻璃) + 导航角标
 *   系统 → 液态玻璃模糊 + 预测性返回 + 恢复默认
 */
@Composable
fun ThemeSettingsScreen(state: HFState, onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val appInfo = LocalContext.current.applicationInfo

    Scaffold(
        topBar = {
            TopAppBar(
                title = "主题设置",
                largeTitle = "主题设置",
                navigationIcon = {
                    MiuixBackButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp)
        ) {
            // ===== 外观 / 深色模式 =====
            item { Spacer(Modifier.height(4.dp)) }
            item { SmallTitle("外观") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        val modeIdx = when (state.themeMode) {
                            "light" -> 1
                            "dark" -> 2
                            else -> 0
                        }
                        TabRow(
                            tabs = listOf("跟随系统", "浅色", "深色"),
                            selectedTabIndex = modeIdx,
                            onTabSelected = { i ->
                                state.setString(
                                    "theme_mode",
                                    when (i) { 1 -> "light"; 2 -> "dark"; else -> "system" }
                                )
                            },
                            modifier = Modifier
                                .padding(12.dp)
                        )
                        SwitchPreference(
                            title = "纯黑背景",
                            summary = "深色模式下使用纯黑背景（AMOLED 省电）",
                            checked = state.amoled,
                            onCheckedChange = { state.set("amoled", it) }
                        )
                    }
                }
            }

            // ===== 颜色 =====
            item { SmallTitle("颜色") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        SwitchPreference(
                            title = "动态取色",
                            summary = "跟随系统壁纸配色",
                            checked = state.monet,
                            onCheckedChange = { state.set("miuix_monet", it) }
                        )
                        val keyColors = listOf(
                            0xFF3482FF.toInt() to "HyperOS 蓝",
                            0xFF2196F3.toInt() to "天蓝",
                            0xFF00BCD4.toInt() to "青",
                            0xFF009688.toInt() to "青绿",
                            0xFF4CAF50.toInt() to "绿",
                            0xFF8BC34A.toInt() to "浅绿",
                            0xFFCDDC39.toInt() to "黄绿",
                            0xFFFFC107.toInt() to "琥珀",
                            0xFFFF9800.toInt() to "橙",
                            0xFFFF5722.toInt() to "深橙",
                            0xFFF44336.toInt() to "红",
                            0xFFE91E63.toInt() to "粉红",
                            0xFF9C27B0.toInt() to "紫",
                            0xFF673AB7.toInt() to "深紫",
                            0xFF3F51B5.toInt() to "靛蓝",
                            0xFF795548.toInt() to "棕"
                        )
                        val curIdx = keyColors.indexOfFirst { it.first == state.keyColor }.let { if (it < 0) 0 else it }
                        OverlayDropdownPreference(
                            title = "主题色",
                            items = keyColors.map { it.second },
                            selectedIndex = curIdx,
                            onSelectedIndexChange = { i ->
                                state.setInt("key_color", keyColors[i].first)
                            },
                            startAction = {
                                Icon(
                                    imageVector = Icons.Filled.Colorize,
                                    contentDescription = "主题色",
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        )
                    }
                }
            }

            // ===== 底栏 =====
            item { SmallTitle("底栏") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        val barStyles = listOf("标准", "悬浮", "液态玻璃")
                        val barIdx = when (state.bottomBarStyle) {
                            "floating" -> 1
                            "liquid" -> 2
                            else -> 0
                        }
                        OverlayDropdownPreference(
                            title = "底栏形态",
                            items = barStyles,
                            selectedIndex = barIdx,
                            onSelectedIndexChange = { i ->
                                state.setString(
                                    "bottom_bar_style",
                                    when (i) { 1 -> "floating"; 2 -> "liquid"; else -> "standard" }
                                )
                            }
                        )
                        SwitchPreference(
                            title = "显示导航角标",
                            summary = "底栏消息 Tab 有新消息时显示红点",
                            checked = state.enableBadge,
                            onCheckedChange = { state.set("enable_navigation_badge", it) }
                        )
                    }
                }
            }

            // ===== 系统 =====
            item { SmallTitle("系统") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        SwitchPreference(
                            title = "液态玻璃模糊",
                            summary = "玻璃底栏的模糊与折射效果开关",
                            checked = state.enableBlur,
                            onCheckedChange = { state.set("enable_blur", it) }
                        )
                        SwitchPreference(
                            title = "预测性返回动画",
                            summary = "返回时预览上一页缩略图（需系统支持）",
                            checked = state.enablePredictiveBack,
                            onCheckedChange = {
                                state.set("enable_predictive_back", it)
                                PredictiveBack.apply(it, appInfo)
                            }
                        )
                        var showReset by remember { mutableStateOf(false) }
                        ArrowPreference(
                            title = "恢复默认设置",
                            summary = "重置全部主题与显示设置",
                            onClick = { showReset = true }
                        )
                        if (showReset) {
                            ConfirmDialog(
                                show = showReset,
                                title = "恢复默认设置",
                                content = "确定要恢复默认设置吗？",
                                confirmText = "确定",
                                onConfirm = {
                                    showReset = false
                                    state.setString("theme_mode", "system")
                                    state.set("amoled", false)
                                    state.set("miuix_monet", false)
                                    state.setInt("key_color", 0xFF3482FF.toInt())
                                    state.setString("bottom_bar_style", "standard")
                                    state.set("enable_blur", true)
                                    state.set("enable_navigation_badge", true)
                                    state.set("enable_predictive_back", false)
                                    PredictiveBack.apply(false, appInfo)
                                },
                                onDismiss = { showReset = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 预测性返回反射开关（00 §10：不写 Manifest 属性，运行时反射 setEnableOnBackInvokedCallback） */
object PredictiveBack {
    fun apply(enabled: Boolean, appInfo: android.content.pm.ApplicationInfo?) {
        if (appInfo == null) return
        runCatching {
            runCatching {
                val method = android.content.pm.ApplicationInfo::class.java
                    .getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(appInfo, enabled)
            }.onFailure {
                org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(
                    android.content.pm.ApplicationInfo::class.java, appInfo,
                    "setEnableOnBackInvokedCallback", enabled
                )
            }
        }
    }
}
