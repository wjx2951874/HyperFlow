package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperflowplus.BuildConfig
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.hyperflowplus.ui.fixLspScript

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

    // 1.1.0 #69：隐私说明弹窗（口径：小米原生通道，App不联网不上传）
    var showPrivacy by remember { mutableStateOf(false) }

    // v0.5.12：滚动 → 全局 tick（驱动玻璃 backdrop 重录）
    val sScroll = rememberScrollState()
    LaunchedEffect(sScroll) {
        androidx.compose.runtime.snapshotFlow { sScroll.value }.collect { MainHolder.scrollTick++ }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 01 手册 §11.2：列表不加水平 padding，12dp 由每张 Card 自己带；
            // MIUI 弹性回弹（§12.6）挂在滚动容器上
            .overScrollVertical()
            .verticalScroll(sScroll)
    ) {
        // ===== 主题 =====
        GroupTitle("主题")

        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            Column {
                // 外观：跟随系统 / 浅色 / 深色（页内 Overlay 气泡，Scaffold 默认宿主托管，KSU 下不闪退；
                // WindowSpinnerPreference 另开独立 window 在 KSU 环境弹不出/闪退，已弃用）
                val themeOptions = listOf("跟随系统", "浅色", "深色")
                val themeValues = listOf("system", "light", "dark")
                val themeIndex = themeValues.indexOf(state.themeMode).coerceAtLeast(0)
                // 01 手册 §5：枚举选择用 OverlayDropdownPreference（0.9.x 新名，
                // 旧 OverlaySpinnerPreference 已弃用），行尾显示当前值 + 点击弹层
                OverlayDropdownPreference(
                    title = "界面外观",
                    summary = "可单独设置App的界面外观",
                    items = themeOptions,
                    selectedIndex = themeIndex,
                    onSelectedIndexChange = { i ->
                        state.setString("theme_mode", themeValues[i])
                    }
                )

                // 底栏形态：默认 / 悬浮 / 液态玻璃
                val barOptions = listOf("默认", "悬浮", "液态玻璃")
                val barValues = listOf("standard", "floating", "liquid")
                val barIndex = barValues.indexOf(state.bottomBarStyle).coerceAtLeast(0)
                OverlayDropdownPreference(
                    title = "底栏形态",
                    summary = "悬浮导航栏为胶囊样式，液态玻璃带模糊背景",
                    items = barOptions,
                    selectedIndex = barIndex,
                    onSelectedIndexChange = { i ->
                        state.setBottomBarStyle(barValues[i])
                    }
                )
            }
        }

        // ===== 调试 =====
        GroupTitle("调试")

        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
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
                // 常驻组合、show 驱动（01 手册 §11.8）：关闭时完整播放滑出动画
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
                        summary = "查看与管理已捕获的日志",
                        onClick = { MainHolder.onOpenLogs() }
                    )
                }
            }
        }

        // ===== 关于 =====
        GroupTitle("关于")

        // Beta 通道机关（正式版收口后隐藏得更深）：连点版本行 10 次解锁 Beta 通道显示。
        // 正式版默认只走稳定通道；该入口保留给紧急热修与灰度验证用，日常使用不会误触。
        // 状态持久化到 SharedPreferences，关 App 重开不丢；激活后再点正常检查更新，再连点 12 次关闭。
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        val sp = context.getSharedPreferences("hyperflow", android.content.Context.MODE_PRIVATE)
        var betaTapCount by remember { mutableStateOf(0) }
        var betaUnlocked by remember { mutableStateOf(sp.getBoolean("beta_channel", false)) }
        var checkUpdateJob by remember { mutableStateOf<Job?>(null) }

        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            Column {
                // V0.6.15：版本行 —— 常驻醒目色状态提示（有新版=橙 / 已是最新=绿 / 检查中=灰），
                // 点击不再弹检查结果窗：有新版时直接进入更新方式，其余静默检查。
                // 1.1.0：有新版优先显示（beta 通道标识让位，避免检出新版仍显示旧版号）
                val betaTag = if (betaUnlocked) "检测Beta更新 · " else ""
                val updTxt = when (MainHolder.updState) {
                    "new" -> "发现新版本 v${MainHolder.latestVer}，点击更新"
                    "checking", "busy" -> "正在检查更新…"
                    "error" -> "检查更新失败，点击重试 · v${BuildConfig.VERSION_NAME}"
                    "none" -> "${betaTag}已是最新版本 · v${BuildConfig.VERSION_NAME}"
                    else -> "${betaTag}点击可检查更新 · v${BuildConfig.VERSION_NAME}"
                }
                val updColor = when (MainHolder.updState) {
                    "new" -> Color(0xFFF08A00)          // 醒目橙：有新版
                    "checking", "busy" -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    "error" -> Color(0xFFD13636)
                    else -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)   // idle/none：普通灰
                }
                // 01 手册 §5/§11.4：可点击动作行走 ArrowPreference（行高 56dp、17sp Medium 标题、
                // 右侧自带灰色箭头、按下 10% 遮罩），不再手搓 Row + Material 箭头；
                // 状态文案用 summary（14sp / 60% 黑），主次层级由组件保证
                ArrowPreference(
                    title = "版本",
                    summary = updTxt,
                    summaryColor = BasicComponentColors(color = updColor, disabledColor = updColor),
                    onClick = {
                        if (MainHolder.updState == "new") {
                            MainHolder.onOpenUpdater()
                        } else {
                            // v1.1.0-beta3：连点计数——已激活时再点正常检查更新，连点 10 次关闭
                            betaTapCount++
                            checkUpdateJob?.cancel()
                            if (betaUnlocked && betaTapCount >= 12) {
                                betaUnlocked = false
                                sp.edit().putBoolean("beta_channel", false).apply()
                                betaTapCount = 0
                                Toast.makeText(context, "已切回稳定版更新通道", Toast.LENGTH_SHORT).show()
                            } else if (!betaUnlocked && betaTapCount >= 10) {
                                betaUnlocked = true
                                sp.edit().putBoolean("beta_channel", true).apply()
                                betaTapCount = 0
                                Toast.makeText(context, "已切到 Beta 更新通道（测试版，可能不稳定）", Toast.LENGTH_LONG).show()
                            } else {
                                checkUpdateJob = scope.launch {
                                    delay(600)
                                    MainHolder.onCheckUpdate()
                                }
                            }
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
                    summary = "在 GitHub 上查看源代码",
                    onClick = { browse("https://github.com/wjx2951874/HyperFlow") }
                )
                ArrowPreference(
                    title = "开放源代码许可",
                    summary = "查看本应用所使用的第三方开源库及其许可证信息",
                    onClick = { MainHolder.onOpenLicenses() }
                )
                ArrowPreference(
                    title = "隐私说明",
                    onClick = { showPrivacy = true }
                )
            }
        }
        // 隐私说明弹窗：详细文字 + 中间大按钮"知道了"
        if (showPrivacy) {
            HyperDialog(
                show = showPrivacy,
                title = "隐私说明",
                onDismiss = { showPrivacy = false }
            ) {
                Text(
                    "本应用的所有流转数据均通过小米互联系统原生通道在本机处理，" +
                    "App 本身不联网、不上传任何数据。短信/通知仅在您开启相应功能时" +
                    "于本机读取与保存，可随时关闭或删除。",
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                // 大按钮居中
                Button(
                    onClick = { showPrivacy = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("知道了")
                }
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
    var onOpenTheme: () -> Unit = {}
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
    // V0.6.16.6：KSU 同款全屏搜索 —— 状态机（折叠/展开/收起）+ 搜索结果（跨组件共享）
    // 1.1.0 M8：底栏角标 —— 新消息到达且不在消息页时点亮（开启"显示导航角标"时生效）
    var newMsgBadge by androidx.compose.runtime.mutableStateOf(false)
    // beta46：双开标识前缀编辑弹窗 —— 提升到 MainActivity 根层渲染（Scaffold 之后，
    // 绘制顺序在悬浮胶囊之后 → 弹窗盖住胶囊；inApp 保留键盘跟随）。FlowScreen 只设状态。
    var prefixEditOpen by androidx.compose.runtime.mutableStateOf(false)
    var prefixDraft by androidx.compose.runtime.mutableStateOf("")
}
