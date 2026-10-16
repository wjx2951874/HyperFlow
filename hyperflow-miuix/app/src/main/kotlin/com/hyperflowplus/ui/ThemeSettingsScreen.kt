package com.hyperflowplus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 1.1.0 主题设置页（KernelSU 同款结构，01 手册 §3/§5 决策表）：
 * 深色模式三选 TabRow + AMOLED + 壁纸取色(Monet) + 主题色 16 选 1 下拉 + 底栏形态三选一
 * + 模糊/角标/预测性返回开关 + 恢复默认。切换立即生效（全局 state 驱动重组）。
 */
@Composable
fun ThemeSettingsScreen(state: HFState, onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    // 预测性返回需要 applicationInfo（hidden API 反射调用，提前在 Composable 上下文取）
    val appInfo = LocalContext.current.applicationInfo
    Scaffold(
        topBar = {
            TopAppBar(
                title = "主题与显示",
                largeTitle = "主题与显示",
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
            // ===== 深色模式 =====
            item { SmallTitle("深色模式") }
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
                            modifier = Modifier.padding(12.dp)
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
                            title = "跟随壁纸取色",
                            summary = "从当前壁纸提取主题色（Monet 动态取色）",
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
                            summary = keyColors[curIdx].second,
                            onSelectedIndexChange = { i ->
                                state.setInt("key_color", keyColors[i].first)
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
                            summary = barStyles[barIdx],
                            onSelectedIndexChange = { i ->
                                state.setString(
                                    "bottom_bar_style",
                                    when (i) { 1 -> "floating"; 2 -> "liquid"; else -> "standard" }
                                )
                            }
                        )
                        SwitchPreference(
                            title = "液态玻璃模糊",
                            summary = "玻璃底栏的模糊与折射效果开关",
                            checked = state.enableBlur,
                            onCheckedChange = { state.set("enable_blur", it) }
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
            // 优先普通反射；命中 hidden API 限制时回退 HiddenApiBypass（org.lsposed 官方库）
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
