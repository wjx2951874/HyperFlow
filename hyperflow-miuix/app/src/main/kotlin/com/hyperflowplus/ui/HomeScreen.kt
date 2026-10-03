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
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ReportProblem
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
echo ==SCOPE;
cat /data/adb/lspd/config/scope/com.hyperflowplus 2>/dev/null
echo ==SCOPE_EXISTS;
[ -f /data/adb/lspd/config/scope/com.hyperflowplus ] && echo yes || echo no
echo ==MAPS;
APP_PID=$(pidof com.hyperflowplus 2>/dev/null | tr ' ' '\n')
SYSPID=$(pidof system_server 2>/dev/null | tr ' ' '\n')
MLPID=$(ps -A 2>/dev/null | grep -iE 'milink' | awk '{print $1}' | head -1)
for m in $(grep -ilE "hyperflow" /proc/[0-9]*/maps 2>/dev/null); do
  p=${'$'}{m%/*}
  pid=${'$'}{p##*/}
  case " ${'$'}APP_PID " in *" ${'$'}pid "*) continue;; esac
  case " ${'$'}SYSPID " in *" ${'$'}pid "*) echo "==SYS_LOADED"; continue;; esac
  case " ${'$'}MLPID " in *" ${'$'}pid "*) echo "==ML_LOADED"; continue;; esac
  echo "==LOADED ${'$'}pid"
done
echo ==END""") }.getOrNull()
            val r = out?.substringAfter("==ID")?.substringBefore("==KSU")?.trim() == "0"
            val ksuRaw = out?.substringAfter("==KSU")?.substringBefore("==MILINK")?.trim()
            val k = r && !ksuRaw.isNullOrBlank() && ksuRaw != "none"
            val m = out?.substringAfter("==MILINK")?.substringBefore("==LSPD")?.contains("package:") == true
            val lspdSeg = out?.substringAfter("==LSPD")?.substringBefore("==P_ADB") ?: ""
            val lspdAlive = lspdSeg.lines().any { it.isNotBlank() }
            val fresh = { t: Long? -> t != null && System.currentTimeMillis() - t < 48 * 3600 * 1000L }
            // v0.5.15.3：注入证据以实时 maps 为主（模块 dex 出现在哪个进程），文件探针兜底。
            // 探针文件不可靠：/data/adb 只有 root 能写，而 system_server uid=system(1000) 写不进去
            // → 注入明明生效但探针永远缺失 → 检测永远误报"未勾选"。maps 证据无权限问题。
            val mapsSeg = out?.substringAfter("==MAPS") ?: ""
            val sysLoaded = mapsSeg.contains("==SYS_LOADED")
            val mlLoaded = mapsSeg.contains("==ML_LOADED")
            val mapsHit = mapsSeg.contains("==LOADED") || sysLoaded || mlLoaded
            val adbProbe = out?.substringAfter("==P_ADB")?.substringBefore("==P_MILINK")?.trim()?.toLongOrNull()
            val milinkProbe = out?.substringAfter("==P_MILINK")?.substringBefore("==SCOPE")?.trim()?.toLongOrNull()
            // V0.6.15.1："重新检测"实时补刷作用域快照（等价退出重进效果）——
            // service 绑定只在启动时发生，作用域是绑定瞬间快照；勾选后不重启 App 不更新。
            // 这里用 shell 实时读 scope 文件刷进 ModuleFrameworkState（connected 仍由 service 保证）。
            // 支持"全取消勾选"= 空 scope = 未就绪（文件存在时以 shell 内容为准覆盖 service 旧快照）
            val scopeSeg = out?.substringAfter("==SCOPE")?.substringBefore("==SCOPE_EXISTS") ?: ""
            val scopeLines = scopeSeg.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val scopeFileExists = out?.substringAfter("==SCOPE_EXISTS")?.substringBefore("==MAPS")
                ?.trim()?.contains("yes") == true
            com.hyperflowplus.ModuleFrameworkState.refreshScopeFromShell(scopeLines, scopeFileExists)
            // 系统框架（system_server 注入）：maps 实时证据优先，探针 48h 内命中兜底
            val androidInjected = sysLoaded || fresh(adbProbe)
            // 小米互联（milink 进程注入）：maps 实时证据优先，探针 48h 内命中兜底
            val milinkInjected = mlLoaded || fresh(milinkProbe)
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

    // V0.6.16.1：作用域判定回到 libxposed service 快照（用户确认过 v0.6.14 判定准确：
    // 勾选全部作用域 → service.scope 含 milink+android/system → 就绪；模块禁用 →
    // onServiceDied 归零 → 未连接）。v0.6.16 改回 shell maps/探针判定导致"全部勾选仍提示
    // 未勾选全"（milink/system_server 进程 maps 读不到模块 dex）→ 回退。
    // "重新检测"仍由 detect() 用 shell 实时读 scope 文件刷新 service 快照（文件存在时以
    // shell 内容为准、允许空集=全取消勾选），兼顾"取消后不重启也能刷出未就绪"。
    val fw = com.hyperflowplus.ModuleFrameworkState.snapshot.value
    val fwActive = fw.active
    // LSPosed 模块三态：0=未连接（模块被禁用/service 断开）红；
    // 1=已启用但推荐作用域不全 黄；3=启用+作用域全 绿
    val lspModuleState = when {
        !fwActive -> 0                                   // 模块被禁用/service 断开 → 未连接
        fw.scopeReady -> 3                               // service scope 含 milink + android/system → 推荐作用域就绪
        else -> 1                                        // 已启用但作用域不全 → 部分未就绪
    }
    val lspModuleOk = lspModuleState == 3

    // v0.6.8：6 项检测合一为 3 项 —— ①Root 环境（root 或 KSU 任一）②小米互联 ③LSPosed 模块（启用+作用域三态）
    val rootEnvOk = rootOk || ksuOk

    // v0.5.12：滚动 → 全局 tick（驱动玻璃 backdrop 重录，见 MainActivity 注释）
    val hScroll = rememberScrollState()
    LaunchedEffect(hScroll) {
        androidx.compose.runtime.snapshotFlow { hScroll.value }.collect { MainHolder.scrollTick++ }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(hScroll)
            // v0.6.14：顶部留白 12dp，对齐 KSU HomeMiuix（内容区水平 12dp + 首卡顶 12dp）
            .padding(top = 12.dp, bottom = 0.dp, start = 12.dp, end = 12.dp)
    ) {
        // ===== 环境状态汇总行（轻量条，状态一目了然；v0.6.8 起 3 项合一） =====
        val allOk = rootEnvOk && milinkOk && lspModuleOk
        val level: Color = when {
            !rootEnvOk -> CRed
            allOk -> CGreen
            else -> CYellow
        }
        val levelText = when {
            !rootEnvOk -> "环境异常"
            allOk -> "环境已就绪"
            else -> "部分环境未就绪"
        }
        val passed = listOf(rootEnvOk, milinkOk, lspModuleOk).count { it }
        // V0.6.15：引导提示统一移入检测详情"去解决"（黄字不再显示在首页）

        if (showLspTrouble) {
            HyperDialog(
                title = "环境正常为何无法使用？",
                show = showLspTrouble,
                onDismiss = { showLspTrouble = false }
            ) {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        "环境检测已就绪但功能仍无法使用？请按顺序排查：\n" +
                                "1）确认 LSPosed 里 HyperFlow 已启用（首页对应项为绿）；\n" +
                                "2）确认推荐作用域勾选全（系统框架 + 小米互联）；\n" +
                                "3）以上都对仍异常 → 一键写入配置并重启框架，注入立即生效。",
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
        // V0.6.15：去除环境卡上方的黄色小字提示（引导统一放检测详情"去解决"里，更完整）
        // ===== 环境检测（三态，v0.5.13：InstallerX MiuixHomePage 同款长方体状态卡） =====
        // ① 无 Root：红色长方体卡（其余项无 root 也查不到）
        // ② 全部就绪：绿色长方体卡（点击弹 6 项详情）
        // ③ 部分未就绪：黄色长方体卡 + "查看更多" → 弹窗红标未成功项 + "去解决" → 步骤弹窗
        val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
        // V0.6.15：solveItem（去解决引导）提升到红卡/黄卡共用作用域
        var solveItem by remember { mutableStateOf<Pair<String, GuideType>?>(null) }
        if (!rootOk) {
            // V0.6.15.1：无 Root 红卡 → 标题"未获取到 Root 权限"，0/3 项通过（其余项不可测）
            var showRootDetail by remember { mutableStateOf(false) }
            HomeStatusCard(
                containerColor = if (isDark) Color(0xFF381A1A) else Color(0xFFFAEEEE),
                iconColor = Color(0xFFD13636),
                bgIcon = Icons.Rounded.ErrorOutline,
                title = "未获取到 Root 权限",
                desc = "点击查看情况",
                extra = "0/3 项通过",
                onClick = { showRootDetail = true }
            )
            if (showRootDetail) {
                HyperDialog(
                    title = "环境检测",
                    show = showRootDetail,
                    onDismiss = { showRootDetail = false },
                    titleAction = {
                        var reTap by remember { mutableIntStateOf(0) }
                        Text(
                            if (checking) "检测中…" else "重新检测",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    reTap++
                                    detect()
                                    // V0.6.16.1：弹窗内底部提示（Toast 在弹窗上可能不显示）
                                    // V0.6.16.1：系统 Toast（applicationContext 系统窗口，弹窗上层也能显示）
                                    android.widget.Toast.makeText(
                                        ctx.applicationContext,
                                        "强行停止本 App 再次进入，可获得更准确的检测结果",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    if (reTap >= 3) {
                                        reTap = 0
                                        showLspTrouble = true
                                    }
                                }
                        )
                    }
                ) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        EnvBadgeRow(
                            Icons.Rounded.Cancel, CRed,
                            "Root 环境", "未授予 Root 权限（无法检测其余项）"
                        )
                        EnvBadgeRow(
                            Icons.Rounded.ErrorOutline, Color(0xFF9A9A9A),
                            "LSPosed 模块", "无法检测：需要先获取 Root"
                        )
                        EnvBadgeRow(
                            Icons.Rounded.ErrorOutline, Color(0xFF9A9A9A),
                            "小米互联服务", "无法检测：需要先获取 Root"
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                showRootDetail = false
                                solveItem = "Root 环境" to GuideType.ROOT
                            },
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("去解决") }
                    }
                }
            }
        } else if (allOk) {
            var showEnvDetail by remember { mutableStateOf(false) }
            HomeStatusCard(
                containerColor = if (isDark) Color(0xFF1A3825) else Color(0xFFDFFAE4),
                iconColor = Color(0xFF36D167),
                bgIcon = Icons.Rounded.CheckCircleOutline,
                title = "环境已就绪",
                desc = "点击查看情况",
                extra = "$passed/3 项通过",
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
                        Text(
                            if (checking) "检测中…" else "重新检测",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // v0.6.14：每次点击都执行一次真实检测；
                                    // 累计点击第 3 次时，额外弹出"环境正常为何无法使用"引导提示
                                    reTap++
                                    detect()
                                    // V0.6.16.1：弹窗内底部提示（Toast 在弹窗上可能不显示）
                                    // V0.6.16.1：系统 Toast（applicationContext 系统窗口，弹窗上层也能显示）
                                    android.widget.Toast.makeText(
                                        ctx.applicationContext,
                                        "强行停止本 App 再次进入，可获得更准确的检测结果",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    if (reTap >= 3) {
                                        reTap = 0
                                        showLspTrouble = true
                                    }
                                }
                        )
                    }
                ) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        EnvDetailRow("Root 环境", rootEnvOk, "已授予（KernelSU/Root）" to "未授予 Root 权限")
                        EnvDetailRow3("LSPosed 模块", lspModuleState, Triple("已启用 + 推荐作用域就绪", "已启用但推荐作用域未勾选全", "未连接 LSPosed（模块未启用）"))
                        EnvDetailRow("小米互联服务", milinkOk, "服务正常" to "未安装小米互联服务")
                    }
                }
            }
        } else {
            // 部分未就绪：黄色长方体卡（同款结构，黄底 + 黄色圆环叹号——用户要的"长方体、红色系"变体）
            var showMore by remember { mutableStateOf(false) }
            HomeStatusCard(
                containerColor = if (isDark) Color(0xFF3A2E00) else Color(0xFFFFF4DD),
                iconColor = CYellow,
                bgIcon = Icons.Rounded.ErrorOutline,
                title = "部分环境未就绪",
                desc = "点击查看情况",
                extra = "$passed/3 项通过",
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
                        Text(
                            if (checking) "检测中…" else "重新检测",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // v0.6.14：每次点击都执行一次真实检测；
                                    // 累计点击第 3 次时，额外弹出"环境正常为何无法使用"引导提示
                                    reTap++
                                    detect()
                                    // V0.6.16.1：弹窗内底部提示（Toast 在弹窗上可能不显示）
                                    // V0.6.16.1：系统 Toast（applicationContext 系统窗口，弹窗上层也能显示）
                                    android.widget.Toast.makeText(
                                        ctx.applicationContext,
                                        "强行停止本 App 再次进入，可获得更准确的检测结果",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    if (reTap >= 3) {
                                        reTap = 0
                                        showLspTrouble = true
                                    }
                                }
                        )
                    }
                ) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        EnvItemRow("Root 环境", rootEnvOk, "已授予（KernelSU/Root）" to "未授予 Root 权限", GuideType.ROOT) { solveItem = "Root 环境" to GuideType.ROOT }
                        EnvItemRow3("LSPosed 模块", lspModuleState, Triple("已启用 + 推荐作用域就绪", "已启用但推荐作用域未勾选全", "未连接 LSPosed（模块未启用）"), GuideType.LSPOSED) { solveItem = "LSPosed 模块" to GuideType.LSPOSED }
                        EnvItemRow("小米互联服务", milinkOk, "服务正常" to "未安装小米互联服务", GuideType.MILINK) { solveItem = "小米互联服务" to GuideType.MILINK }
                    }
                }
            }
        }

        // V0.6.15.1：去解决步骤弹窗提到三态分支之外 —— 红卡/绿卡/黄卡点「去解决」都能正常弹出
        // 去解决：步骤弹窗（打开 KernelSU / LSPosed / 一键写入配置）
        solveItem?.let { (title, guide) ->
            val steps: List<String>
            val actionLabel: String?
            val action: () -> Unit
            when (guide) {
                GuideType.ROOT -> {
                    // V0.6.15：引导完整化（KSU 授权界面无法直接打开 → 纯步骤引导，不放打开按钮）
                    steps = listOf(
                        "1. 打开 KernelSU 管理器（桌面应用列表里找 KernelSU）",
                        "2. 底部切换到「超级用户」页面",
                        "3. 找到 HyperFlow（com.hyperflowplus），点击它",
                        "4. 打开授权开关，授予超级用户权限",
                        "5. 返回本应用，点右上角「重新检测」",
                        "如果列表里没有 HyperFlow：先随便进一次本应用（触发权限申请），再回 KernelSU 查看"
                    )
                    actionLabel = null
                    action = {}
                }
                GuideType.LSPOSED -> {
                    steps = listOf(
                        "1. 打开 KernelSU 管理器 → 底部「模块」页",
                        "2. 确认已安装 LSPosed（Zygisk 版）且已启用，然后重启设备",
                        "3. 重启后打开 LSPosed 管理器（通知栏入口或桌面图标）",
                        "4. 进入「模块」页，找到 HyperFlow 并启用它",
                        "5. 勾选下方推荐作用域：系统框架 + 小米互联服务",
                        "6. 保存并重启设备，回首页重新检测"
                    )
                    actionLabel = "打开 KernelSU"
                    action = { launchKernelSu() }
                }
                GuideType.MODULE_SCOPE -> {
                    steps = listOf(
                        "1. 打开 LSPosed → 模块 → HyperFlow",
                        "2. 勾选作用域：系统框架 + 小米互联服务（推荐作用域）",
                        "3. 保存并重启设备（或点下方按钮写入配置后软重启）",
                        "4. 重启后回首页重新检测，三项全绿即就绪"
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

/** v0.6.8：三态详情行（0 红 / 1 黄 / 3 绿），LSPosed 模块合一检测项使用 */
@Composable
private fun EnvDetailRow3(title: String, level: Int, texts: Triple<String, String, String>) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBadge3(level)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
            Text(
                when (level) {
                    3 -> texts.first
                    1 -> texts.second
                    else -> texts.third
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
    }
}

/** v0.6.8：三态引导行（0 红 / 1 黄 / 3 绿），level != 3 时显示"去解决" */
@Composable
private fun EnvItemRow3(
    title: String,
    level: Int,
    texts: Triple<String, String, String>,
    guide: GuideType,
    onSolve: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBadge3(level)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
            Text(
                when (level) {
                    3 -> texts.first
                    1 -> texts.second
                    else -> texts.third
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
        if (level != 3) {
            Button(
                onClick = onSolve,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("去解决", fontSize = 13.sp)
            }
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

/** V0.6.15：自定义徽标详情行（红叉 / 灰色叹号等，无 Root 时无法检测项使用） */
@Composable
private fun EnvBadgeRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    text: String
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
            Text(
                text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
    }
}

/** v0.6.8：三态徽标（0 红叉 / 1 黄叹 / 3 绿勾）——LSPosed 模块合一检测项使用 */
@Composable
private fun StatusBadge3(level: Int, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Icon(
        imageVector = when (level) {
            3 -> Icons.Rounded.CheckCircleOutline
            1 -> Icons.Rounded.ReportProblem
            else -> Icons.Rounded.ErrorOutline
        },
        contentDescription = null,
        tint = when (level) {
            3 -> CGreen
            1 -> CYellow
            else -> CRed
        },
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
