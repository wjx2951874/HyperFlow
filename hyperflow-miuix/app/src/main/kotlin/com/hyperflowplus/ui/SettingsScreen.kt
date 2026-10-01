package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hyperflowplus.BuildConfig
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页：主题（悬浮导航栏/液态玻璃）→ 调试（调试模式/分享日志）→ 关于（作者/源码/许可/获取更新） */
@Composable
fun SettingsScreen(state: HFState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    fun browse(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(ctx, "无法打开链接：$url", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        // ===== 主题 =====
        GroupTitle("主题")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "悬浮导航栏",
                    summary = if (state.navFloat) "已开启悬浮导航栏" else "关闭后使用系统导航栏",
                    checked = state.navFloat,
                    onCheckedChange = { state.set(Config.KEY_NAV_FLOAT, it) }
                )
                SwitchPreference(
                    title = "液态玻璃",
                    summary = if (state.glassEffect) "已开启系统级液态玻璃" else "关闭后使用系统默认外观",
                    checked = state.glassEffect,
                    onCheckedChange = { state.set(Config.KEY_GLASS, it) }
                )
            }
        }

        // ===== 调试 =====
        GroupTitle("调试")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "调试模式",
                    summary = if (state.debugMode) "已开启：保存详细运行日志" else "关闭后仅保留关键日志",
                    checked = state.debugMode,
                    onCheckedChange = { state.set(Config.KEY_DEBUG_MODE, it) }
                )
                ArrowPreference(
                    title = "分享运行日志",
                    summary = "导出调试日志用于反馈问题",
                    onClick = {
                        val ctxA = ctx
                        Thread {
                            val log = runCatching {
                                RootExec.su("logcat -d -t 300 2>/dev/null | grep -iE 'hyperflow|milink|LSPosed|AndroidRuntime' | tail -200")
                            }.getOrNull() ?: "日志为空"
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "HyperFlow 运行日志")
                                    putExtra(Intent.EXTRA_TEXT, "HyperFlow v" + BuildConfig.VERSION_NAME + "\n\n" + log)
                                }
                                runCatching { ctxA.startActivity(Intent.createChooser(send, "分享日志")) }
                                    .onFailure { Toast.makeText(ctxA, "无可用分享应用", Toast.LENGTH_SHORT).show() }
                            }
                        }.start()
                    }
                )
            }
        }

        // ===== 关于 =====
        GroupTitle("关于")

        Card(Modifier.fillMaxWidth()) {
            Column {
                var hiddenClicks by remember { mutableIntStateOf(0) }
                ArrowPreference(
                    title = "HyperFlow",
                    summary = "v${BuildConfig.VERSION_NAME} · 澎湃OS 互联通知流转增强",
                    onClick = {
                        hiddenClicks++
                        if (hiddenClicks >= 3) {
                            hiddenClicks = 0
                            MainHolder.onReopenOnboarding()   // 连点 3 次：重进引导页
                        }
                    }
                )
                ArrowPreference(
                    title = "作者",
                    summary = "酷安 @翰德姆（关注反馈，会回关哦）",
                    onClick = { browse("https://www.coolapk.com/u/4112338") }
                )
                ArrowPreference(
                    title = "查看源代码",
                    summary = "GitHub：wjx2951874/HyperFlow（GPL-3.0 开源）",
                    onClick = { browse("https://github.com/wjx2951874/HyperFlow") }
                )
                ArrowPreference(
                    title = "开放源代码许可",
                    summary = "Miuix / KernelSU / LSPosed / AndroidX 等开源库许可详情",
                    onClick = { MainHolder.onOpenLicenses() }
                )
                ArrowPreference(
                    title = "获取更新",
                    summary = "检查 GitHub 最新版本（KernelSU 模块页亦提供在线更新）",
                    onClick = { MainHolder.onCheckUpdate() }
                )
            }
        }

        // 开源致谢小字（按用户要求去掉 AI 辅助字样）
        Text(
            "由衷感谢 KernelSU、LSPosed 与 Miuix 开源社区",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/** 供设置页回调 MainActivity 的检测更新入口（避免循环依赖） */
object MainHolder {
    var onCheckUpdate: () -> Unit = {}
    var onOpenLicenses: () -> Unit = {}
    var onReopenOnboarding: () -> Unit = {}
}
