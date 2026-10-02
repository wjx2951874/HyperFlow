package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hyperflowplus.BuildConfig
import com.hyperflowplus.Config
import com.hyperflowplus.HFApplication
import com.hyperflowplus.HFState
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

    // v0.5.12：滚动 → 全局 tick（驱动玻璃 backdrop 重录）
    val sScroll = rememberScrollState()
    LaunchedEffect(sScroll) {
        androidx.compose.runtime.snapshotFlow { sScroll.value }.collect { MainHolder.scrollTick++ }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(sScroll)
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
                // 液态玻璃仅悬浮模式可用：悬浮开启时才显示该项（与悬浮开关联动，不并列常驻）
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

        // ===== 消息 =====
        GroupTitle("消息")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "仅显示实时消息",
                    summary = if (state.msgLiveOnly)
                        "本地存档隐藏（不删除），仅显示小米端实时获取的消息"
                    else
                        "本地存档与小米端实时消息一起显示",
                    checked = state.msgLiveOnly,
                    onCheckedChange = { state.set(Config.KEY_MSG_LIVE_ONLY, it) }
                )
            }
        }

        // ===== 调试 =====
        GroupTitle("调试")

        Card(Modifier.fillMaxWidth()) {
            Column {
                // v0.5.11：原"调试模式"开关移除 → 改为机关：首页"系统"行连点 5 次，
                // 或应用连续闪退 3 次，自动捕获日志存到 App 目录（filesDir/hf_logs）
                // v0.5.12：分享改为文件分享（FileProvider 分享 .txt 日志本体）
                ArrowPreference(
                    title = "分享运行日志",
                    summary = "导出日志文件用于反馈问题\n首页连点「系统」5 次或连续闪退 3 次也会自动保存日志",
                    onClick = {
                        val ctxA = ctx
                        Thread {
                            // 优先分享最近一次已保存的日志文件；无则实时捕获一份
                            val dir = java.io.File(ctxA.filesDir, "hf_logs")
                            var file: java.io.File? = dir.listFiles()
                                ?.filter { it.name.endsWith(".txt") }
                                ?.maxByOrNull { it.lastModified() }
                            if (file == null || !file.exists()) {
                                file = runCatching { HFApplication.captureLogcat(ctxA) }.getOrNull()
                            }
                            val f = file
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (f == null) {
                                    Toast.makeText(ctxA, "日志捕获失败", Toast.LENGTH_SHORT).show()
                                    return@post
                                }
                                runCatching {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        ctxA,
                                        "com.hyperflowplus.fileprovider",
                                        f
                                    )
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "HyperFlow v${BuildConfig.VERSION_NAME} 运行日志")
                                        putExtra(Intent.EXTRA_TEXT, "HyperFlow v${BuildConfig.VERSION_NAME} 运行日志\n详见附件文件")
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ctxA.startActivity(Intent.createChooser(send, "分享日志文件"))
                                }.onFailure {
                                    Toast.makeText(ctxA, "无可用分享应用", Toast.LENGTH_SHORT).show()
                                }
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
                // v0.5.12：版本行常驻提示"点击可检查更新"；自动检查更新已有（打开 App 检测一次）
                // 连点 3 次 = 重进引导页
                ArrowPreference(
                    title = "版本",
                    summary = buildString {
                        if (MainHolder.hasUpdate) {
                            append("有新版 v${MainHolder.latestVer} 可更新 · ")
                        }
                        append("点击可检查更新 · v${BuildConfig.VERSION_NAME}")
                    },
                    onClick = {
                        hiddenClicks++
                        if (hiddenClicks >= 3) {
                            hiddenClicks = 0
                            MainHolder.onReopenOnboarding()   // 连点 3 次：重进引导页
                        } else {
                            MainHolder.onCheckUpdate()
                        }
                    }
                )
                ArrowPreference(
                    title = "作者",
                    summary = "酷安 @翰德姆",
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
            }
        }

        // v0.5.12：悬浮胶囊避让 —— 滚动到底最后一行停在胶囊上沿（不遮挡）
        Spacer(
            Modifier.height(
                if (MainHolder.bottomPad == androidx.compose.ui.unit.Dp.Unspecified) 0.dp else MainHolder.bottomPad
            )
        )
    }
}

/** 供设置页回调 MainActivity 的检测更新入口（避免循环依赖） */
object MainHolder {
    var onCheckUpdate: () -> Unit = {}
    var onOpenLicenses: () -> Unit = {}
    var onReopenOnboarding: () -> Unit = {}
    // v0.5.11：自动检测到的更新状态（设置页版本号行显示"有新版"提示）
    var hasUpdate by androidx.compose.runtime.mutableStateOf(false)
    var latestVer by androidx.compose.runtime.mutableStateOf("")
    // v0.5.12：滚动偏移 tick —— 任何页面滚动时 +1，MainActivity 录制 Box 的
    // graphicsLayer 读它建立依赖 → 滚动时重绘 → backdrop 重录（玻璃折射不滞后不黑）
    var scrollTick by androidx.compose.runtime.mutableLongStateOf(0L)
    // v0.5.12：悬浮胶囊避让 —— MainActivity 按 navFloat 设置，页面滚动容器尾部
    // 加同高间距，保证内容最后一行可滚到胶囊上沿（不遮挡、不留白）
    var bottomPad by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.Dp.Unspecified)
}
