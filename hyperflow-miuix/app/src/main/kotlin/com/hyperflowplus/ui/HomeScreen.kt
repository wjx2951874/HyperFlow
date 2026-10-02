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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
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
            // v0.5.9：一次 su 会话完成全部检测（原 4 次独立 su 会话 → 每次 1-3s，合计可达 10s+）
            // 段标记解析：==ID / ==KSU / ==MILINK / ==LSP / ==LSPD / ==MODULES / ==SCOPE / ==DB / ==RUNNING / ==MAPS
            val out = runCatching { RootExec.su("""echo ==ID;
id -u 2>/dev/null
echo ==KSU;
ksud -V 2>/dev/null || echo none
echo ==MILINK;
pm path com.milink.service 2>/dev/null
echo ==LSP;
for d in /data/adb/lspd/config /data/adb/lspd /data/adb/modules/lsposed/config /data/adb/modules/lsposed /data/adb/modules/zygisk_lsposed/config /data/adb/modules/zygisk_lspd/config /data/adb/modules/zygisk_lspd /data/adb/modules/lspd/config /data/adb/modules/lspd /data/adb/modules/ksu_lspd /data/adb/modules/ksu_lsposed /data/adb/riru/modules/lsposed/config; do
  [ -e "${'$'}d" ] && echo "==DIR ${'$'}d"
done
echo ==LSPD;
# v0.5.10：框架活跃判定 = daemon 进程存活（目录残留≠框架在跑）。
# LSPosed(zygisk) 守护进程名 lspd；KernelSU 内嵌版同名；riru 版 lspd/riru_lspd。
# 用户"不启动 LSP"（框架禁用/未激活）时目录可能仍在 → 靠进程判定才能真正反映框架状态。
pidof lspd 2>/dev/null
pidof lspd_64 2>/dev/null
ps -A 2>/dev/null | grep -w lspd | grep -v grep | awk '{print ${'$'}NF}'
ps -A 2>/dev/null | grep -iE "riru.*lspd|lspd.*daemon" | grep -v grep | awk '{print ${'$'}NF}'
echo ==MODULES;
# 标准路径 + find 全盘遍历（覆盖所有 LSPosed 变体，如 KernelSU 内嵌版的不同目录）
for f in /data/adb/lspd/config/modules.list /data/adb/lspd/modules.list /data/adb/modules/lsposed/config/modules.list /data/adb/modules/lsposed/modules.list /data/adb/modules/zygisk_lsposed/config/modules.list /data/adb/riru/modules/lsposed/config/modules.list; do
  [ -f "${'$'}f" ] && { echo "==ML ${'$'}f"; cat "${'$'}f"; }
done
find /data/adb -maxdepth 6 -name "modules.list" -type f 2>/dev/null | while read f; do
  case "${'$'}f" in *lspd*|*lsposed*) echo "==MLX ${'$'}f"; cat "${'$'}f";; esac
done
echo ==SCOPE;
for f in /data/adb/lspd/config/scope/* /data/adb/lspd/scope/* /data/adb/modules/lsposed/config/scope/* /data/adb/modules/lsposed/scope/* /data/adb/modules/zygisk_lsposed/config/scope/* /data/adb/riru/modules/lsposed/config/scope/*; do
  [ -f "${'$'}f" ] && echo "==FILE ${'$'}(basename ${'$'}f)"
done
find /data/adb -maxdepth 7 -path "*scope*" -type f 2>/dev/null | while read f; do
  case "${'$'}f" in *lspd*|*lsposed*) echo "==FILEX ${'$'}(basename ${'$'}f)";; esac
done
echo ==DB;
# LSPosed 1.9+（含 KernelSU 内嵌版）：启用状态存在 SQLite 数据库 modules_config.db，无 modules.list/scope 文件
for db in /data/adb/lspd/config/modules_config.db /data/adb/modules/lsposed/config/modules_config.db /data/adb/modules/zygisk_lsposed/config/modules_config.db; do
  [ -f "${'$'}db" ] && { echo "==DBFILE ${'$'}db"; grep -a "com.hyperflowplus" "${'$'}db" 2>/dev/null && echo "==HF_IN_DB"; }
done
find /data/adb -maxdepth 6 -name "modules_config.db" -type f 2>/dev/null | while read db; do
  echo "==DBX ${'$'}db"; grep -a "com.hyperflowplus" "${'$'}db" 2>/dev/null && echo "==HF_IN_DB"
done
echo ==DBQ;
# v0.5.12：模块启用标志精确检测 —— db 里 grep 到包名 ≠ 启用（enabled 列才决定）！
# 用户实测：在 LSPosed 里关掉 HyperFlow 模块后，包名仍留在 modules_config.db，
# 旧逻辑 dbHit=true → 误判"已启用/环境正常"。设备有 sqlite3 时直接查 enabled 列：
# 输出 "com.hyperflowplus|1"（启用）/ "com.hyperflowplus|0"（禁用），供解析段区分。
for db in /data/adb/lspd/config/modules_config.db /data/adb/modules/lsposed/config/modules_config.db /data/adb/modules/zygisk_lsposed/config/modules_config.db; do
  [ -f "${'$'}db" ] && { echo "==DBQF ${'$'}db"; command -v sqlite3 >/dev/null 2>&1 && sqlite3 "${'$'}db" "SELECT module_pkg_name, enabled FROM modules_config WHERE module_pkg_name LIKE '%hyperflow%';" 2>/dev/null; }
done
find /data/adb -maxdepth 6 -name "modules_config.db" -type f 2>/dev/null | while read db; do
  echo "==DBQX ${'$'}db"; command -v sqlite3 >/dev/null 2>&1 && sqlite3 "${'$'}db" "SELECT module_pkg_name, enabled FROM modules_config WHERE module_pkg_name LIKE '%hyperflow%';" 2>/dev/null
done
echo ==RUNNING;
cat /data/adb/hyperflowplus/xposed_loaded 2>/dev/null
cat /data/user/0/com.hyperflowplus/files/xposed_loaded 2>/dev/null
echo ==MAPS;
# 运行态证据：模块 APK 被加载进进程后 maps 里有其路径。
# v0.5.9 修复误报：本模块是 priv-app 安装（/system/priv-app/HyperFlowPlus/HyperFlowPlus.apk），
# 旧版只 grep "modules/hyperflowplus"（模块目录路径）永远匹配不上 → 重启生效后仍黄条"请重启"。
# 大小写不敏感多模式单次 grep -l（一次遍历全部进程 maps，不再逐进程 for+grep）；
# 排除本 App 自身进程（App 运行中 maps 必含自身 APK 路径，不代表模块被 LSPosed 加载），
# 命中其它进程（zygote / milink 等作用域进程）= 模块真正被加载。
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
            // 调试：原始检测结果写入 /data/adb/hyperflowplus/detect.log 便于排查（LSP 配置路径因框架版本而异）
            if (!out.isNullOrBlank()) {
                runCatching { RootExec.su("mkdir -p /data/adb/hyperflowplus && echo '${'$'}out' > /data/adb/hyperflowplus/detect.log") }
            }
            // 解析：LSPosed 存在 / 模块已启用（modules.list 内容=模块包名）/ 作用域已勾选（scope 目录下存在本模块文件）
            // 兼容不同 LSPosed 变体：包名/短名/大小写模糊匹配（用户已启用但检测不到 = 路径或格式差异）
            // v0.5.10：框架"活跃"判定升级 —— 目录存在 且 daemon(lspd) 进程存活。
            // 用户关闭框架（zygisk 停用/未激活）时目录仍残留，旧逻辑误判框架在线 → 环境一直"正常"；
            // 现在框架不在跑 = lspInstalled=false → 环境异常并引导启用（用户诉求：不启用就要检测出来）。
            val lspSeg = out?.substringAfter("==LSP", "")?.substringBefore("==LSPD") ?: ""
            val lspdSeg = out?.substringAfter("==LSPD", "")?.substringBefore("==MODULES") ?: ""
            val lspDirHit = lspSeg.contains("==DIR") && (lspSeg.contains("lspd") || lspSeg.contains("lsposed"))
            // daemon 存活：==LSPD 段有任意输出（pidof 的 pid 或 ps 的进程行）即框架在跑
            val lspdAlive = lspdSeg.lines().any { it.isNotBlank() }
            val lspInstalled = lspDirHit && lspdAlive
            val modsSeg = out?.substringAfter("==MODULES", "")?.substringBefore("==SCOPE") ?: ""
            val scopeSeg = out?.substringAfter("==SCOPE", "") ?: ""
            val dbSeg = out?.substringAfter("==DB", "")?.substringBefore("==RUNNING") ?: ""
            // 运行态双保险：模块被 LSPosed 真正加载时 XposedEntry 会写时间戳。
            // 配置态（db/scope 文件）在"框架整体关闭/去作用域"时会残留 → 曾误判"环境正常"。
            // 以最近 48h 内加载过为准（装好后没重启=不加载=如实显示未启用）。
            val runSeg = out?.substringAfter("==RUNNING", "")?.substringBefore("==END")?.trim() ?: ""
            // 双路径任一最近 48h 内加载过即生效（/data/adb=root 进程写入，/data/user/0/<app>=App 进程写入）
            // MAPS 证据：非 root 作用域进程（milink 等）加载模块时写不进 /data/adb 时间戳，
            // 但 /proc/*/maps 里能看到模块 APK 已被 mmap —— 命中即视为已生效。
            val mapsHit = runSeg.contains("==LOADED")
            val runStamp = runSeg.lines().mapNotNull { it.trim().toLongOrNull() }.maxOrNull()
            val runtimeOkLocal = mapsHit || (runStamp?.let {
                System.currentTimeMillis() - it < 48 * 3600 * 1000L
            } ?: false)
            // v0.5.10：模块/作用域"启用"以框架活跃为前提（lspInstalled 已含 daemon 存活）——
            // 用户关闭 LSP 框架时 db/scope 文件残留 → 旧逻辑误判已启用 → 环境一直"正常"；
            // 现在框架没在跑 = 全部 LSP 相关项为异常（红），页面引导去启用（一键启用按钮）。
            val dbHit = dbSeg.contains("==HF_IN_DB")
            val hasModName = { seg: String -> seg.contains("com.hyperflowplus") || seg.contains("hyperflowplus") || seg.contains("hyperflow", ignoreCase = true) }
            // v0.5.12：db enabled 精确标志（==DBQ 段 sqlite3 输出 "com.hyperflowplus|1"/"|0"）。
            // LSPosed 1.9+ 里模块被禁用时包名仍留在 modules_config.db → 旧 dbHit 误判已启用。
            val dbqSeg = out?.substringAfter("==DBQ", "")?.substringBefore("==RUNNING") ?: ""
            val dbEnabled = dbqSeg.contains("com.hyperflowplus", ignoreCase = true) && dbqSeg.contains("|1")
            val dbDisabled = dbqSeg.contains("com.hyperflowplus", ignoreCase = true) && dbqSeg.contains("|0")
            // 无 sqlite3 时（dbqSeg 为空）无法知道 enabled → 用运行时证据兜底：
            // 最近 48h 内被 LSPosed 真正加载过（maps/时间戳）才算"模块已启用"。
            // 用户场景：在 LSPosed 关闭模块并重启 → 模块不再被加载 → 时间戳过期/maps 无命中
            // → runtimeOkLocal=false → modEnabled=false → 环境如实显示异常。
            val modEnabled = lspInstalled && !dbDisabled && (
                hasModName(modsSeg) || dbEnabled ||
                        (dbqSeg.isBlank() && dbHit && runtimeOkLocal)
                )
            val scopeOkV = lspInstalled && !dbDisabled && (
                scopeSeg.contains("==FILE com.hyperflowplus") || scopeSeg.contains("==FILE hyperflowplus") ||
                        scopeSeg.contains("==FILE hyperflow", ignoreCase = true) || dbEnabled ||
                        (dbqSeg.isBlank() && dbHit && runtimeOkLocal)
                )
            // 生效判定 = 配置态为准（db/scope 命中即绿）。
            // v0.5.10：不再设"配置 OK 但未加载"的黄条 —— 用户诉求"LSP 不动，重启一次刷完就完事"：
            // 框架活跃(daemon)+模块配置命中 = 直接绿；框架不活跃 = 异常并要求启用。
            // 运行时证据（maps/时间戳）仅作内部参考，不再驱动红绿/黄条（曾因 milink 非 zygote 进程
            // 不注入、App 无 root 写时间戳失败等导致"重启后仍黄"的假阴性）。
            val moduleOkV = if (modEnabled) 3 else 0
            val scopeOkV2 = if (scopeOkV) 3 else 0
            val lspOkV = if (lspInstalled) 3 else 0
            val m = out?.substringAfter("==MILINK")?.substringBefore("==LSP")?.contains("package:") == true
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                rootOk = r; ksuOk = k; lspOk = lspOkV > 0
                moduleOk = moduleOkV > 0; scopeOk = scopeOkV2 > 0; milinkOk = m
                lspState = lspOkV; moduleState = moduleOkV; scopeState = scopeOkV2
                runtimeOk = runtimeOkLocal
                checking = false
                state.saveEnvCache(r, k, lspOkV > 0, moduleOkV > 0, scopeOkV2 > 0, m)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 4.dp, end = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 汇总行图标：谷歌 Material 同源（绿对勾=就绪 / 黄叹号=部分 / 红叹号=异常），与详情行 StatusBadge 一致
            Icon(
                imageVector = if (allOk) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = level,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                levelText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = level
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$passed/6",
                style = MiuixTheme.textStyles.body2,
                color = level.copy(alpha = 0.85f)
            )
            Spacer(Modifier.weight(1f))
            if (checking) {
                Text("检测中…", style = MiuixTheme.textStyles.body2, color = level)
            } else {
                Text(
                    "重新检测",
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = level.copy(alpha = 0.9f),
                    modifier = Modifier.clickable { detect() }
                )
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

        // ===== 环境检测（三态，v0.5.12 回退 v0.5.10.1 大卡样式；谷歌 Material 图标） =====
        // ① 无 Root：只显示红色"请授予 root 权限"卡（其余项无 root 也查不到）
        // ② 全部就绪：绿色圆环对勾大卡（点击弹 6 项详情）
        // ③ 部分未就绪：黄色圆环叹号卡 + "查看更多" → 弹窗红标未成功项 + "去解决" → 步骤弹窗
        if (!rootOk) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { launchKernelSu() }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "未授予 Root 权限",
                        tint = CRed,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "请授予 Root 权限",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CRed
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "未检测到 Root 环境，其余项无法检测\n点击前往 KernelSU 管理器授权",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (allOk) {
            var showEnvDetail by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { showEnvDetail = true }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusBadge(ok = true, size = 44.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "环境已就绪",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CGreen
                    )
                }
            }
            if (showEnvDetail) {
                HyperDialog(
                    title = "环境检测",
                    show = showEnvDetail,
                    onDismiss = { showEnvDetail = false }
                ) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        EnvDetailRow("Root 权限", rootOk, "已授予（KernelSU）" to "未授予 Root 权限")
                        EnvDetailRow("KSU 内核", ksuOk, "内核已就绪" to "未检测到 KernelSU")
                        EnvDetailRow("LSPosed 框架", lspOk, "框架存在" to "未检测到 LSPosed")
                        EnvDetailRow("模块已启用", moduleOk, "已在 LSPosed 启用" to "未在 LSPosed 启用")
                        EnvDetailRow("推荐作用域", scopeOk, "已勾选（本 App + milink + android）" to "未勾选推荐作用域（含 android）")
                        EnvDetailRow("小米互联服务", milinkOk, "服务正常" to "未安装小米互联服务")
                    }
                }
            }
        } else {
            // 部分未就绪：黄色圆环叹号卡（与绿色 CheckCircle 同源 Material 圆环样式）
            var showMore by remember { mutableStateOf(false) }
            var solveItem by remember { mutableStateOf<Pair<String, GuideType>?>(null) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { showMore = true }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "部分环境未就绪",
                        tint = CYellow,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "部分环境未就绪",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CYellow
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$passed/6 项通过",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.KeyboardArrowRight,
                            contentDescription = "查看更多",
                            modifier = Modifier.size(16.dp),
                            tint = CYellow.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            // 查看更多弹窗：逐项红标未成功项，右侧"去解决"
            if (showMore) {
                HyperDialog(
                    title = "环境检测",
                    show = showMore,
                    onDismiss = { showMore = false }
                ) {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        EnvItemRow("Root 权限", rootOk, "已授予（KernelSU）" to "未授予 Root 权限", GuideType.ROOT) { solveItem = "Root 权限" to GuideType.ROOT }
                        EnvItemRow("KSU 内核", ksuOk, "内核已就绪" to "未检测到 KernelSU", GuideType.ROOT) { solveItem = "KSU 内核" to GuideType.ROOT }
                        EnvItemRow("LSPosed 框架", lspOk, "框架存在" to "未检测到 LSPosed", GuideType.LSPOSED) { solveItem = "LSPosed 框架" to GuideType.LSPOSED }
                        EnvItemRow("模块已启用", moduleOk, "已在 LSPosed 启用" to "未在 LSPosed 启用", GuideType.MODULE_SCOPE) { solveItem = "模块已启用" to GuideType.MODULE_SCOPE }
                        EnvItemRow(
                            "推荐作用域", scopeOk,
                            "已勾选（本 App + milink + android）" to "未勾选推荐作用域（含 android）",
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
                            "2. 勾选：HyperFlow + 小米互联服务 + Android 系统框架",
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

        // ===== 设备信息（v0.5.12：一个统一 Card 内单行列表，行间黑色分割线分隔——学安装工具样式） =====
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

        // 设备行数据（学安装工具：左标签右值，单行列表）
        val devRows = listOf(
            "机型" to (if (modelCode.isNotEmpty()) "$modelName（$modelCode）" else modelName),
            "系统" to (if (miuiV.isNotEmpty()) "$miuiV · Android $andV (API ${android.os.Build.VERSION.SDK_INT})"
            else "Android $andV (API ${android.os.Build.VERSION.SDK_INT})"),
            "内核" to kernV.ifEmpty { "未知" },
            "模块" to "HyperFlow v${BuildConfig.VERSION_NAME}",
            "Root" to rootV,
            "框架" to (if (lspOk) "LSPosed 已启用" else "未启用"),
            "互联" to (if (milinkOk) "小米互联服务正常" else "未安装小米互联服务")
        )
        Card(Modifier.fillMaxWidth()) {
            Column {
                devRows.forEachIndexed { idx, (label, value) ->
                    // 行间黑色细分割线（安装工具同款分隔；机型/系统行点击触发机关除外，仅系统行机关）
                    if (idx > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(1.dp)
                                .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (label == "系统") Modifier.clickable { onSysTap() }
                                else Modifier
                            )
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

/**
 * 环境状态图标（v0.5.8）：改用谷歌官方 Material 图标
 * —— 通过 = Icons.Filled.CheckCircle（实心圆对勾，与系统/其他 App 常见选中样式一致），
 * 未通过 = Icons.Filled.Warning（实心三角叹号，红色；core 图标库内置，避免引入 extended 使包体膨胀约 30MB）。
 * 此前用的是手写 path 的 RoundedIcons 空心圆环勾，用户反馈渲染样式不是标准谷歌图标。
 */
@Composable
private fun StatusBadge(ok: Boolean, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Icon(
        imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
        contentDescription = null,
        tint = if (ok) androidx.compose.ui.graphics.Color(0xFF2EBD59) else androidx.compose.ui.graphics.Color(0xFFE84C4C),
        modifier = Modifier.size(size)
    )
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
