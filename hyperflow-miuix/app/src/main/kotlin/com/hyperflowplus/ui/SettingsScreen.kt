package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperflowplus.BuildConfig
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import com.hyperflowplus.ui.fixLspScript
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页：主题（悬浮导航栏/液态玻璃）→ 调试（调试模式/分享日志）→ 关于（作者/源码/许可/获取更新） */
@Composable
fun SettingsScreen(state: HFState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    // V0.6.15：日志列表二级页由 MainActivity 托管（保留底部导航，TopAppBar 隐藏），
    // 入口通过 MainHolder.onOpenLogs() 打开
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

        // ===== 调试 =====
        GroupTitle("调试")

        Card(Modifier.fillMaxWidth()) {
            Column {
                // v0.6.2：软重启框架条目暂时移除（用户：放调试区仍易误触，先去掉）
                // v0.5.15：重装引导（原"版本连点 3 次"机关移除，改调试区独立入口，防误触）
                // V0.6.16.4：重新引导 —— 仅重新显示首次使用引导页，不清空本地数据；
                // 确认按钮 10 秒倒计时后才可点击（ConfirmDialog countdownSec 语义），点确认立即进入
                var showReGuide by remember { mutableStateOf(false) }
                ArrowPreference(
                    title = "重新引导",
                    summary = "重新显示首次使用引导页",
                    onClick = { showReGuide = true }
                )
                if (showReGuide) {
                    ConfirmDialog(
                        show = showReGuide,
                        title = "重新引导",
                        content = "重新引导会重新显示第一次使用引导，为避免误操作，请等待10秒后点击确定继续操作（该操作不会清空本地数据）",
                        countdownSec = 10,
                        confirmText = "确定",
                        onConfirm = {
                            showReGuide = false
                            MainHolder.onReopenOnboarding()
                        },
                        onDismiss = { showReGuide = false }
                    )
                }
                // v0.5.11：原"调试模式"开关移除 → 改为机关：首页"系统"行连点 5 次，
                // 或应用连续闪退 3 次，自动捕获日志存到 App 目录（filesDir/hf_logs）
                // v0.5.13：仅当存在已捕获的日志文件时才显示该条目（没有就不显示，
                // 也不提示如何触发捕获）；机关捕获成功后下次进设置页自动出现。
                // v0.5.15.2：入口改为"日志列表"——查看/删除/分享/下载全部日志
                var hasLog by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    hasLog = java.io.File(ctx.filesDir, "hf_logs")
                        .listFiles()?.any { it.name.endsWith(".txt") } == true
                }
                if (hasLog) {
                    ArrowPreference(
                        title = "日志列表",
                        summary = "查看、删除、分享、下载已捕获的日志",
                        onClick = { MainHolder.onOpenLogs() }
                    )
                }
            }
        }

        // ===== 关于 =====
        GroupTitle("关于")

        Card(Modifier.fillMaxWidth()) {
            Column {
                // V0.6.15：版本行 —— 常驻醒目色状态提示（有新版=橙 / 已是最新=绿 / 检查中=灰），
                // 点击不再弹检查结果窗：有新版时直接进入更新方式，其余静默检查
                val updTxt = when (MainHolder.updState) {
                    "new" -> "发现新版本 v${MainHolder.latestVer}，点击更新"
                    "none" -> "已是最新版本 · v${BuildConfig.VERSION_NAME}"
                    "checking", "busy" -> "正在检查更新…"
                    "error" -> "检查更新失败，点击重试 · v${BuildConfig.VERSION_NAME}"
                    else -> "点击可检查更新 · v${BuildConfig.VERSION_NAME}"
                }
                val updColor = when (MainHolder.updState) {
                    "new" -> Color(0xFFF08A00)          // 醒目橙：有新版
                    "checking", "busy" -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    "error" -> Color(0xFFD13636)
                    else -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)   // idle/none：普通灰
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (MainHolder.updState == "new") MainHolder.onOpenUpdater()
                            else MainHolder.onCheckUpdate()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "版本",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            updTxt,
                            style = MiuixTheme.textStyles.body2,
                            color = updColor
                        )
                    }
                    top.yukonga.miuix.kmp.basic.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                }
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
    var onOpenUpdater: () -> Unit = {}
    var onOpenLicenses: () -> Unit = {}
    var onOpenLogs: () -> Unit = {}
    var onReopenOnboarding: () -> Unit = {}
    // v0.5.11：自动检测到的更新状态（设置页版本号行显示"有新版"提示）
    var hasUpdate by androidx.compose.runtime.mutableStateOf(false)
    var latestVer by androidx.compose.runtime.mutableStateOf("")
    // V0.6.15：版本行状态（idle/checking/new/none/error/busy）——常驻醒目色提示，点击不再弹检查窗
    var updState by androidx.compose.runtime.mutableStateOf("idle")
    // v0.5.12：滚动偏移 tick —— 任何页面滚动时 +1，MainActivity 录制 Box 的
    // graphicsLayer 读它建立依赖 → 滚动时重绘 → backdrop 重录（玻璃折射不滞后不黑）
    var scrollTick by androidx.compose.runtime.mutableLongStateOf(0L)
    // v0.5.12：悬浮胶囊避让 —— MainActivity 按 navFloat 设置，页面滚动容器尾部
    // 加同高间距，保证内容最后一行可滚到胶囊上沿（不遮挡、不留白）
    var bottomPad by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.Dp.Unspecified)
    // V0.6.16.4：消息页搜索态 —— 右上角搜索图标（排序左侧）打开，搜索框退出时关闭
    var msgSearchOpen by androidx.compose.runtime.mutableStateOf(false)
}
