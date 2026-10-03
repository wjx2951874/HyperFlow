package com.hyperflowplus.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperflowplus.BuildConfig
import com.hyperflowplus.HFApplication
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 环境检测三态色：绿=通过 黄=部分 红=关键缺失
private val CGreen = Color(0xFF34C759)
private val CYellow = Color(0xFFFF9F0A)
private val CRed = Color(0xFFFF3B30)

/** 首页：环境检测（红/黄/绿）+ 设备信息 —— 功能开关已移入"流转"页 */
@Composable
fun HomeScreen(state: HFState, modifier: Modifier = Modifier, onOpenGuide: (GuideType) -> Unit = {}) {
    val ctx = LocalContext.current
    // v0.5.13：连点「重新检测」3 次 → 隐藏入口弹窗（环境正常为何无法使用 / 一键软重启 / 复制日志）
    var showLspTrouble by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    // 初始值 = 上次检测缓存（避免每次进入红→绿跳变），LaunchedEffect 里后台重查后再更新
    var rootOk by remember { mutableStateOf(state.envRoot) }
    var ksuOk by remember { mutableStateOf(state.envKsu) }
    var lspOk by remember { mutableStateOf(state.envLsp) }
    var moduleOk by remember { mutableStateOf(state.envModule) }
    var scopeOk by remember { mutableStateOf(state.envScope) }
    // 三态：0=异常(红) / 1=配置已启用但运行态未加载(黄，重启后生效) / 3=正常(绿)
    var lspState by remember { mutableIntStateOf(0) }
    var runtimeOk by remember { mutableStateOf(false) }
    var moduleState by remember { mutableIntStateOf(0) }
    var scopeState by remember { mutableIntStateOf(0) }
    var milinkOk by remember { mutableStateOf(state.envMilink) }

    fun detect() {
        checking = true
        Thread {
            // v0.5.15：检测脚本重写 —— 去掉 /data/adb 全盘 find 遍历（最慢）与
            // modules.list / scope 文件 / DB 解析（LSPosed 变体路径格式差异是误报根源），
            // 只保留 4 类判定源：root / KSU / 框架 daemon / 运行时探针。
            // 运行时证据 = XposedEntry 注入时写的探针时间戳：
            //   /data/adb/hyperflowplus/xposed_loaded（system_server=android 作用域，root 可写）
            //   /data/user/0/com.milink.service/files/hf_loaded（milink=小米互联作用域，milink 进程可写）
            // 探针 48h 内命中 = 模块被 LSPosed 真正注入该进程 = 该作用域已生效（配置态不再参与判定，
            // 天然免疫"关闭框架后目录残留 / 模块被禁用后 db 残留"两类误报）。
            val out = runCatching { RootExec.su("""echo ==ID;
id -u 2>/dev/null
echo ==KSU;
ksud -V 2>/dev/null || echo none
echo ==MILINK;
pm path com.milink.service 2>/dev/null
echo ==LSPD;
pidof lspd 2>/dev/null
pidof lspd_64 2>/dev/null
ps -A 2>/dev/null | grep -w lspd | grep -v grep | awk '{print ${'$'}NF}'
echo ==P_ADB;
cat /data/adb/hyperflowplus/xposed_loaded 2>/dev/null
echo ==P_MILINK;
cat /data/user/0/com.milink.service/files/hf_loaded 2>/dev/null
echo ==MAPS;
APP_PID=$(pidof com.hyperflowplus 2>/dev/null | tr ' ' '\n')
for m in $(grep -ilE "hyperflow" /proc/[0-9]*/maps 2>/dev/null); do
  p=${'$'}{m%/*}
  case " ${'$'}APP_PID " in *" ${'$'}{p##*/} "*) continue;; esac
  echo "==LOADED ${'$'}{p##*/}"
done
echo ==END""") }.getOrNull()
            val r = out?.substringAfter("==ID")?.substringBefore("==KSU")?.trim() == "0"
            val ksuRaw = out?.substringAfter("==KSU")?.substringBefore("==MILINK")?.trim()
            val k = r && !ksuRaw.isNullOrBlank() && ksuRaw != "none"
            val m = out?.substringAfter("==MILINK")?.substringBefore("==LSPD")?.contains("package:") == true
            val lspdSeg = out?.substringAfter("==LSPD")?.substringBefore("==P_ADB") ?: ""
            val lspdAlive = lspdSeg.lines().any { it.isNotBlank() }
            // 双探针：android 作用域（system_server）与 milink 作用域（小米互联）各自 48h 内注入证据
            val adbProbe = out?.substringAfter("==P_ADB")?.substringBefore("==P_MILINK")?.trim()?.toLongOrNull()
            val milinkProbe = out?.substringAfter("==P_MILINK")?.substringBefore("==MAPS")?.trim()?.toLongOrNull()
            val fresh = { t: Long? -> t != null && System.currentTimeMillis() - t < 48 * 3600 * 1000L }
            val androidInjected = fresh(adbProbe)
            val milinkInjected = fresh(milinkProbe)
            val mapsHit = out?.substringAfter("==MAPS")?.contains("==LOADED") == true
            // 框架活跃 = daemon 存活，或模块已被注入任意进程（maps 命中必然框架在跑）
            val lspInstalled = lspdAlive || mapsHit
            // 模块已启用 = 框架在跑 且 有运行时证据（任一进程注入）
            val moduleOkV = if (lspInstalled && (androidInjected || milinkInjected || mapsHit)) 3 else 0
            // 推荐作用域 = android 与 milink 两进程都被注入（各自探针 48h 内）——
            // 不再要求"本 App"（模块自身进程无需被 hook，LSPosed 里勾不了属正常）
            val scopeOkV = if (lspInstalled && androidInjected && milinkInjected) 3 else 0
            val lspOkV = if (lspInstalled) 3 else 0
            // 调试：原始检测结果写入 /data/adb/hyperflowplus/detect.log 便于排查
            // 注意：不能用单引号包 $out（shell 不展开 → 日志永远只有字面 $out）；
            // 用 base64 传递内容，杜绝一切 shell 转义/换行/特殊字符问题
            if (!out.isNullOrBlank()) {
                val b64 = android.util.Base64.encodeToString(
                    out.toByteArray(), android.util.Base64.NO_WRAP
                )
                runCatching { RootExec.su("mkdir -p /data/adb/hyperflowplus && echo '$b64' | base64 -d > /data/adb/hyperflowplus/detect.log") }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                rootOk = r; ksuOk = k; lspOk = lspOkV > 0
                moduleOk = moduleOkV > 0; scopeOk = scopeOkV > 0; milinkOk = m
                lspState = lspOkV; moduleState = moduleOkV; scopeState = scopeOkV
                runtimeOk = mapsHit || androidInjected || milinkInjected
                checking = false
                state.saveEnvCache(r, k, lspOkV > 0, moduleOkV > 0, scopeOkV > 0, m)
            }
        }.start()
    }

    /** 一键启用模块 + 勾选推荐作用域（复用 GuideScreen 脚本），写入后需重启设备生效 */
    fun fixLsp() {
        Thread {
            runCatching { RootExec.su(fixLspScript()) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                detect()
                Toast.makeText(ctx, "已写入 LSPosed 配置，重启设备后生效", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    LaunchedEffect(Unit) { detect() }

    // v0.5.12：滚动 → 全局 tick（驱动玻璃 backdrop 重录，见 MainActivity 注释）
    val hScroll = rememberScrollState()
    LaunchedEffect(hScroll) {
        androidx.compose.runtime.snapshotFlow { hScroll.value }.collect { MainHolder.scrollTick++ }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(hScroll)
            .padding(horizontal = 12.dp)
    ) {
        // ===== 环境状态汇总行（轻量条，状态一目了然） =====
        val allOk = rootOk && ksuOk && lspOk && moduleOk && scopeOk && milinkOk
        // v0.5.10：已移除"配置 OK 但未加载"黄条（重启一次即生效，不再二次重启）；
        // LSP 框架没在跑（daemon 死）= lsp/module/scope 全红 → 环境异常并引导一键启用。
        val level: Color = when {
            !rootOk -> CRed
            allOk -> CGreen
            else -> CYellow
        }
        val levelText = when {
            !rootOk -> "环境异常"
            allOk -> "环境已就绪"
            else -> "部分环境未就绪"
        }
        val passed = listOf(rootOk, ksuOk, lspOk, moduleOk, scopeOk, milinkOk).count { it }
        // 框架未运行时的引导提示（用户诉求：不启用就要马上检测出来并要求启用）
        val pendingHint = if (!rootOk || lspState == 0)
            "LSP 框架未在运行，请启用后重启设备生效" else null

        if (showLspTrouble) {
            HyperDialog(
                title = "环境正常为何无法使用？",
                show = showLspTrouble,
                onDismiss = { showLspTrouble = false }
            ) {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        "检测基于 LSPosed 配置与运行时加载证据。若模块实际已生效但页面仍提示未启用/未生效，" +
                                "可先软重启框架（无需整机重启）使注入立即生效；仍无效请复制检测日志反馈。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            showLspTrouble = false
                            fixLsp()
                            Thread {
                                runCatching { RootExec.su("setprop ctl.restart zygote") }
                            }.start()
                            Toast.makeText(ctx, "已写入 LSP 配置并软重启框架，稍后请重新打开 App", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("写入 LSP 配置并软重启（立即生效）") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            Thread {
                                val log = runCatching {
                                    RootExec.su("cat /data/adb/hyperflowplus/detect.log 2>/dev/null")
                                }.getOrNull() ?: "(无检测日志)"
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("HyperFlow检测日志", log))
                                    Toast.makeText(ctx, "检测日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            }.start()
                        },
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("复制检测日志") }
                }
            }
        }
        if (pendingHint != null) {
            Text(
                pendingHint,
                style = MiuixTheme.textStyles.body2,
                color = CYellow,
                modifier = Modifier.padding(start = 6.dp, top = 0.dp, end = 6.dp)
            )
        }

        // ===== 环境检测（三态，v0.5.13：InstallerX MiuixHomePage 同款长方体状态卡） =====
        // ① 无 Root：红色长方体卡（其余项无 root 也查不到）
        // ② 全部就绪：绿色长方体卡（点击弹 6 项详情）
        // ③ 部分未就绪：黄色长方体卡 + "查看更多" → 弹窗红标未成功项 + "去解决" → 步骤弹窗
        val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
        if (!rootOk) {
            HomeStatusCard(
                containerColor = if (isDark) Color(0xFF381A1A) else Color(0xFFFAEEEE),
                iconColor = Color(0xFFD13636),
                bgIcon = Icons.Rounded.ErrorOutline,
                title = "环境异常",
                desc = "未检测到 Root 环境，其余项无法检测",
                extra = "点击前往 KernelSU 管理器授权",
                onClick = { launchKernelSu() }
            )
        } else if (allOk) {
            var showEnvDetail by remember { mutableStateOf(false) }
            HomeStatusCard(
                containerColor = if (isDark) Color(0xFF1A3825) else Color(0xFFDFFAE4),
                iconColor = Color(0xFF36D167),
                bgIcon = Icons.Rounded.CheckCircleOutline,
                title = "环境已就绪",
                desc = "点击查看情况",
                extra = "$passed/6 项通过",
                onClick = { showEnvDetail = true }
            )
            if (showEnvDetail) {
                HyperDialog(
                    title = "环境检测",
                    show = showEnvDetail,
                    onDismiss = { showEnvDetail = false },
                    // v0.5.15：标题右侧小"重新检测"（连点 3 次弹隐藏入口，原首页汇总行按钮移除）
                    titleAction = {
                        var reTap by remember { mutableIntStateOf(0) }
                        var lastTap by remember { mutableLongStateOf(0L) }
                        Text(
                            if (checking) "检测中…" else "重新检测",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // 先计数再动作（不拦点击）：连点 3 次（800ms 窗口）弹隐藏入口；
                                    // 未到 3 次才触发重新检测
                                    val now = System.currentTimeMillis()
                                    reTap = if (now - lastTap < 800) reTap + 1 else 1
                                    lastTap = now
                                    if (reTap >= 3) {
                                        reTap = 0
                                        showLspTrouble = true
                                    } else {
                                        detect()
                                    }
                                }
                        )
                    }
                ) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        EnvDetailRow("Root 权限", rootOk, "已授予（KernelSU）" to "未授予 Root 权限")
                        EnvDetailRow("KSU 内核", ksuOk, "内核已就绪" to "未检测到 KernelSU")
                        EnvDetailRow("LSPosed 框架", lspOk, "框架存在" to "未检测到 LSPosed")
                        EnvDetailRow("模块已启用", moduleOk, "已在 LSPosed 启用" to "未在 LSPosed 启用")
                        EnvDetailRow("推荐作用域", scopeOk, "已勾选（小米互联 + Android 框架）" to "未勾选推荐作用域（小米互联 + Android 框架）")
                        EnvDetailRow("小米互联服务", milinkOk, "服务正常" to "未安装小米互联服务")
                    }
                }
            }
        } else {
            // 部分未就绪：黄色长方体卡（同款结构，黄底 + 黄色圆环叹号——用户要的"长方体、红色系"变体）
            var showMore by remember { mutableStateOf(false) }
            var solveItem by remember { mutableStateOf<Pair<String, GuideType>?>(null) }
            HomeStatusCard(
                containerColor = if (isDark) Color(0xFF3A2E00) else Color(0xFFFFF4DD),
                iconColor = CYellow,
                bgIcon = Icons.Rounded.ErrorOutline,
                title = "部分环境未就绪",
                desc = "点击查看情况",
                extra = "$passed/6 项通过",
                onClick = { showMore = true }
            )
            // 查看更多弹窗：逐项红标未成功项，右侧"去解决"
            if (showMore) {
                HyperDialog(
                    title = "环境检测",
                    show = showMore,
                    onDismiss = { showMore = false },
                    // v0.5.15：标题右侧小"重新检测"（连点 3 次弹隐藏入口）
                    titleAction = {
                        var reTap by remember { mutableIntStateOf(0) }
                        var lastTap by remember { mutableLongStateOf(0L) }
                        Text(
                            if (checking) "检测中…" else "重新检测",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // 先计数再动作（不拦点击）：连点 3 次（800ms 窗口）弹隐藏入口；
                                    // 未到 3 次才触发重新检测
                                    val now = System.currentTimeMillis()
                                    reTap = if (now - lastTap < 800) reTap + 1 else 1
                                    lastTap = now
                                    if (reTap >= 3) {
                                        reTap = 0
                                        showLspTrouble = true
                                    } else {
                                        detect()
                                    }
                                }
                        )
                    }
                ) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        EnvItemRow("Root 权限", rootOk, "已授予（KernelSU）" to "未授予 Root 权限", GuideType.ROOT) { solveItem = "Root 权限" to GuideType.ROOT }
                        EnvItemRow("KSU 内核", ksuOk, "内核已就绪" to "未检测到 KernelSU", GuideType.ROOT) { solveItem = "KSU 内核" to GuideType.ROOT }
                        EnvItemRow("LSPosed 框架", lspOk, "框架存在" to "未检测到 LSPosed", GuideType.LSPOSED) { solveItem = "LSPosed 框架" to GuideType.LSPOSED }
                        EnvItemRow("模块已启用", moduleOk, "已在 LSPosed 启用" to "未在 LSPosed 启用", GuideType.MODULE_SCOPE) { solveItem = "模块已启用" to GuideType.MODULE_SCOPE }
                        EnvItemRow(
                            "推荐作用域", scopeOk,
                            "已勾选（小米互联 + Android 框架）" to "未勾选推荐作用域（小米互联 + Android 框架）",
                            GuideType.MODULE_SCOPE
                        ) { solveItem = "推荐作用域" to GuideType.MODULE_SCOPE }
                        EnvItemRow("小米互联服务", milinkOk, "服务正常" to "未安装小米互联服务", GuideType.MILINK) { solveItem = "小米互联服务" to GuideType.MILINK }
                    }
                }
            }
            // 去解决：步骤弹窗（打开 KernelSU / LSPosed / 一键写入配置）
            solveItem?.let { (title, guide) ->
                val steps: List<String>
                val actionLabel: String?
                val action: () -> Unit
                when (guide) {
                    GuideType.ROOT -> {
                        steps = listOf(
                            "1. 打开 KernelSU 管理器",
                            "2. 在超级用户列表找到 HyperFlow",
                            "3. 授予 Root 权限后返回首页重新检测"
                        )
                        actionLabel = "打开 KernelSU"
                        action = { launchKernelSu() }
                    }
                    GuideType.LSPOSED -> {
                        steps = listOf(
                            "1. 打开 KernelSU 管理器",
                            "2. 进入「模块」页启用 LSPosed 框架",
                            "3. 启用后重启设备生效"
                        )
                        actionLabel = "打开 KernelSU"
                        action = { launchKernelSu() }
                    }
                    GuideType.MODULE_SCOPE -> {
                        steps = listOf(
                            "1. 打开 LSPosed 作用域设置",
                            "2. 勾选：小米互联服务 + Android 系统框架",
                            "3. 保存后重启设备生效（也可用下方按钮一键写入配置）"
                        )
                        actionLabel = "一键写入配置（重启生效）"
                        action = { fixLsp() }
                    }
                    GuideType.MILINK -> {
                        steps = listOf(
                            "小米互联服务（com.milink.service）未安装",
                            "它是澎湃/小米系统自带组件，请确认设备支持互联流转"
                        )
                        actionLabel = null
                        action = {}
                    }
                }
                HyperDialog(
                    title = title,
                    show = solveItem != null,
                    onDismiss = { solveItem = null }
                ) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        steps.forEach { step ->
                            Text(
                                step,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        if (actionLabel != null) {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    solveItem = null
                                    action()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(actionLabel, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        // ===== 设备信息（v0.5.15：改为 KernelSU 管理器 InfoCard 同款 —— 图标 24dp + 标题加粗 + 内容灰字，
        // 行间不用分割线（改 24dp 间距），逐项对齐安装工具首页设备卡） =====
        GroupTitle("设备信息")

        // 解析各字段（机型行格式：机型：Redmi Note 12 Turbo（23049RP8BC））
        val devLine = state.deviceInfo.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val modelName = state.deviceName.ifEmpty {
            devLine.firstOrNull { it.startsWith("机型：") }?.removePrefix("机型：")?.substringBefore("（")?.trim() ?: "加载中…"
        }
        val modelCode = devLine.firstOrNull { it.startsWith("机型：") }?.substringAfter("（", "")?.substringBefore("）")?.trim() ?: ""
        val miuiV = devLine.firstOrNull { it.startsWith("澎湃OS：") }?.removePrefix("澎湃OS：")?.trim() ?: state.miuiOsVersion
        val andV = devLine.firstOrNull { it.startsWith("Android：") }?.removePrefix("Android：")?.trim() ?: state.androidVersion
        val kernV = devLine.firstOrNull { it.startsWith("内核：") }?.removePrefix("内核：")?.trim() ?: state.kernelVersion
        val rootV = if (state.rootInfo.isNotEmpty()) "${state.rootInfo} / KSU ${state.ksuVersion}" else "未授权"

        // 调试机关：连点"系统"行 5 下（800ms 内）→ 捕获 logcat 日志存本地目录
        var sysTaps by remember { mutableStateOf(0) }
        var sysTapLast by remember { mutableStateOf(0L) }
        fun onSysTap() {
            val now = android.os.SystemClock.elapsedRealtime()
            sysTaps = if (now - sysTapLast < 800) sysTaps + 1 else 1
            sysTapLast = now
            if (sysTaps >= 5) {
                sysTaps = 0
                val ctxT = ctx
                Thread {
                    val f = runCatching { HFApplication.captureLogcat(ctxT) }.getOrNull()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            ctxT,
                            if (f != null) "已捕获日志：${f.absolutePath}" else "日志捕获失败",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.start()
            }
        }

        // v0.5.13：按用户要求精简 —— 只留 机型 / 系统（含安卓版本）/ 内核 / 模块版本
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                DevInfoRow(Icons.Filled.Smartphone, "机型",
                    if (modelCode.isNotEmpty()) "$modelName（$modelCode）" else modelName)
                DevInfoRow(Icons.Filled.Tag, "系统",
                    if (miuiV.isNotEmpty()) "$miuiV · Android $andV (API ${android.os.Build.VERSION.SDK_INT})"
                    else "Android $andV (API ${android.os.Build.VERSION.SDK_INT})",
                    onTap = { onSysTap() })
                DevInfoRow(Icons.Filled.DeveloperBoard, "内核", kernV.ifEmpty { "未知" })
                DevInfoRow(Icons.Filled.Fingerprint, "模块", "HyperFlow v${BuildConfig.VERSION_NAME}", bottomPadding = 0.dp)
            }
        }

        // v0.5.12：悬浮胶囊避让 —— 滚动到底最后一行停在胶囊上沿（不遮挡）
        Spacer(Modifier.height(if (MainHolder.bottomPad == androidx.compose.ui.unit.Dp.Unspecified) 0.dp else MainHolder.bottomPad))
    }
}

/** 检测项行：状态图标（谷歌 Material CheckCircle 绿勾=通过 红叉=未通过）+ 名称 + 状态文案 */
@Composable
private fun EnvDetailRow(title: String, ok: Boolean, texts: Pair<String, String>) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBadge(ok)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
            Text(
                if (ok) texts.first else texts.second,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
    }
}

/** 设备信息行（v0.5.15：KernelSU 管理器 InfoCard 同款 —— 图标 24dp + 标题加粗 + 内容灰字，
 *  行间距默认 24dp（最后一行 0），无分割线） */
@Composable
private fun DevInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
    onTap: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp),
            tint = MiuixTheme.colorScheme.onBackground
        )
        Column {
            Text(
                title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground
            )
            Text(
                content,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * 环境状态图标（v0.5.13）：InstallerX / 第三方 KSU 工具共享的 Google Material Rounded
 * 圆环家族 —— 通过 = 绿色圆环对勾（CheckCircleOutline），未通过 = 红色圆环叹号（ErrorOutline）。
 * 全部来自 androidx.compose.material:material-icons-extended（Google 官方开源，Apache-2.0），未自绘。
 */
@Composable
private fun StatusBadge(ok: Boolean, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Icon(
        imageVector = if (ok) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
        contentDescription = null,
        tint = if (ok) CGreen else CRed,
        modifier = Modifier.size(size)
    )
}

/**
 * 首页环境状态大卡（v0.5.13：InstallerX MiuixHomePage 原封不动抄）：
 * 浅色长方体（绿/黄/红三态）+ 右下角 170dp 半透明大圆环图标（offset(50,38) 同款）+
 * 20sp SemiBold 标题 + 14sp Medium 副文案（0.8 alpha）+ 点击反馈（PressFeedbackType.Tilt）。
 * 图标为 Google Material Icons Rounded 圆环家族（CheckCircleOutline / ErrorOutline）。
 */
@Composable
private fun HomeStatusCard(
    containerColor: Color,
    iconColor: Color,
    bgIcon: ImageVector,
    title: String,
    desc: String,
    extra: String,
    onClick: () -> Unit
) {
    val textContentColor = if (containerColor.luminance() < 0.5f) Color(0xFFFAFAFA) else Color(0xFF1A1A1A)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor),
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true
    ) {
        Box(Modifier.fillMaxWidth()) {
            // 背景大图标（右下角，InstallerX offset(50.dp, 38.dp) 同款）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 50.dp, y = 38.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    modifier = Modifier.size(170.dp),
                    imageVector = bgIcon,
                    tint = iconColor.copy(alpha = 0.8f),
                    contentDescription = null
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp)
            ) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textContentColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textContentColor.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(36.dp))
                Text(
                    extra,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textContentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/** 查看更多弹窗内的检测项行：状态图标 + 名称/状态 + 未通过时右侧"去解决"按钮 */
@Composable
private fun EnvItemRow(
    title: String,
    ok: Boolean,
    texts: Pair<String, String>,
    guide: GuideType,
    onSolve: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBadge(ok)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
            Text(
                if (ok) texts.first else texts.second,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
        if (!ok) {
            Button(
                onClick = onSolve,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("去解决", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EnvItem(
    title: String,
    ok: Boolean,
    texts: Pair<String, String>,
    guide: GuideType,
    onOpenGuide: (GuideType) -> Unit,
    openLsposedFirst: Boolean = false
) {
    val ctx = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable {
                if (openLsposedFirst && !ok) {
                    // 优先打开 LSPosed 管理器（KernelSU 内嵌版由 KernelSU 管理器承载）手动调整，失败再进引导页
                    var opened = false
                    runCatching {
                        val pm = ctx.packageManager
                        for (pkg in listOf("com.org.lsposed.manager", "io.github.lsposed.manager", "me.weishu.kernelsu")) {
                            val launch = pm.getLaunchIntentForPackage(pkg)
                            if (launch != null) { ctx.startActivity(launch); opened = true; break }
                        }
                    }
                    if (!opened) onOpenGuide(guide)
                } else {
                    onOpenGuide(guide)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (ok) CGreen.copy(alpha = 0.07f) else CRed.copy(alpha = 0.07f))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miuix 状态大图标：绿底白勾 / 红底白叉（复用 Miuix 资源，InstallerX 同款样式）
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (ok) CGreen else CRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ok) MiuixIcons.Regular.Ok else MiuixIcons.Regular.Close,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                )
                Text(
                    if (ok) texts.first else texts.second,
                    style = MiuixTheme.textStyles.body2,
                    color = if (ok) CGreen.copy(alpha = 0.85f) else CRed.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
        }
    }
}

/** 打开 KernelSU（多版本包名兜底；找不到则 Toast 提示） */
private fun launchKernelSu() {
    val pkgs = listOf(
        "me.weishu.kernelsu", "me.weishu.kernelsu.next",
        "com.rifsxd.ksunext", "com.rifsxd.ksu"
    )
    for (pkg in pkgs) {
        val out = runCatching { RootExec.su("pm path $pkg 2>/dev/null") }.getOrNull()
        if (!out.isNullOrBlank() && out.contains("package:")) {
            runCatching {
                RootExec.su("am start -n $pkg/.ui.activity.MainActivity 2>/dev/null || monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            }
            return
        }
    }
    runCatching { RootExec.su("monkey -p com.rifsxd.ksu -c android.intent.category.LAUNCHER 1") }
}

/** 状态数值卡（v0.5.11：InstallerX 同款——上小标签 + 下大数值） */
@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.padding(vertical = 3.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                label,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                maxLines = 1
            )
        }
    }
}

/** 单行信息行（v0.5.11：InstallerX 同款——左侧标签 + 右侧值；可点击触发调试机关） */
@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.width(64.dp)
            )
            Text(
                value,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                maxLines = 1
            )
        }
    }
}
