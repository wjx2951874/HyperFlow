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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    var milinkOk by remember { mutableStateOf(state.envMilink) }

    fun detect() {
        checking = true
        Thread {
            val r = runCatching { RootExec.su("id -u 2>/dev/null | tr -d ' \n'") }.getOrNull()?.trim() == "0"
            val ksuRaw = runCatching { RootExec.su("ksud -V 2>/dev/null || echo none") }.getOrNull()?.trim()
            val k = r && !ksuRaw.isNullOrBlank() && ksuRaw != "none"
            // 一次 su 读全部 LSPosed 配置（兼容 KernelSU 内嵌/模块版不同路径）
            // ==MODULES 段 = 各 modules.list 拼接；==SCOPE 段 = 各 scope 文件拼接
            val out = if (r) runCatching { RootExec.su("""echo ==LSP;
for d in /data/adb/lspd/config /data/adb/lspd /data/adb/modules/lsposed/config /data/adb/modules/lsposed /data/adb/modules/zygisk_lsposed/config /data/adb/riru/modules/lsposed/config; do
  [ -e "${'$'}d" ] && echo "==DIR ${'$'}d"
done
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
echo ==END""") }.getOrNull() else null
            // 调试：原始检测结果写入 /data/adb/hyperflowplus/detect.log 便于排查（LSP 配置路径因框架版本而异）
            if (!out.isNullOrBlank()) {
                runCatching { RootExec.su("mkdir -p /data/adb/hyperflowplus && echo '${'$'}out' > /data/adb/hyperflowplus/detect.log") }
            }
            // 解析：LSPosed 存在 / 模块已启用（modules.list 内容=模块包名）/ 作用域已勾选（scope 目录下存在本模块文件）
            // 兼容不同 LSPosed 变体：包名/短名/大小写模糊匹配（用户已启用但检测不到 = 路径或格式差异）
            val lspInstalled = !out.isNullOrBlank() && out.contains("==DIR") && (
                    out.contains("lspd") || out.contains("lsposed"))
            val modsSeg = out?.substringAfter("==MODULES", "")?.substringBefore("==SCOPE") ?: ""
            val scopeSeg = out?.substringAfter("==SCOPE", "") ?: ""
            val dbSeg = out?.substringAfter("==DB", "") ?: ""
            // 新版 LSPosed：modules_config.db（SQLite）中记录本模块 = 已启用（旧版才用 modules.list/scope 文件）
            val dbHit = dbSeg.contains("==HF_IN_DB")
            val hasModName = { seg: String -> seg.contains("com.hyperflowplus") || seg.contains("hyperflowplus") || seg.contains("hyperflow", ignoreCase = true) }
            val modEnabled = (lspInstalled && hasModName(modsSeg)) || dbHit
            val scopeOkV = lspInstalled && (scopeSeg.contains("==FILE com.hyperflowplus") || scopeSeg.contains("==FILE hyperflowplus") || scopeSeg.contains("==FILE hyperflow", ignoreCase = true) || dbHit)
            val m = runCatching {
                val pm = RootExec.su("pm path com.milink.service 2>/dev/null")
                !pm.isNullOrBlank() && pm.contains("package:")
            }.getOrDefault(false)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                rootOk = r; ksuOk = k; lspOk = lspInstalled
                moduleOk = modEnabled; scopeOk = scopeOkV; milinkOk = m
                checking = false
                state.saveEnvCache(r, k, lspInstalled, modEnabled, scopeOkV, m)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        // ===== 环境状态汇总行（轻量条，状态一目了然） =====
        val allOk = rootOk && ksuOk && lspOk && moduleOk && scopeOk && milinkOk
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 4.dp, end = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(level))
            Spacer(Modifier.width(8.dp))
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

        // ===== 环境检测 =====
        // 全部就绪：折叠为小米风格大对号卡片，点击弹出 6 项详情；未就绪：逐项显示（点击进引导修复）
        if (allOk) {
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
                    top.yukonga.miuix.kmp.basic.Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "环境已就绪",
                        tint = CGreen,
                        modifier = Modifier.size(44.dp)
                    )
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
                        EnvDetailRow("推荐作用域", scopeOk, "已勾选（本 App + milink）" to "未勾选推荐作用域")
                        EnvDetailRow("小米互联服务", milinkOk, "服务正常" to "未安装小米互联服务")
                    }
                }
            }
        } else {
            EnvItem("Root 权限", rootOk, "已授予（KernelSU）" to "未授予 Root 权限", GuideType.ROOT, onOpenGuide)
            EnvItem("KSU 内核", ksuOk, "内核已就绪" to "未检测到 KernelSU", GuideType.ROOT, onOpenGuide)
            EnvItem("LSPosed 框架", lspOk, "框架存在" to "未检测到 LSPosed", GuideType.LSPOSED, onOpenGuide)
            EnvItem("模块已启用", moduleOk, "已在 LSPosed 启用" to "未在 LSPosed 启用", GuideType.MODULE_SCOPE, onOpenGuide)
            EnvItem(
                "推荐作用域",
                scopeOk,
                "已勾选（本 App + milink）" to "未勾选推荐作用域",
                GuideType.MODULE_SCOPE,
                onOpenGuide,
                openLsposedFirst = true
            )
            EnvItem("小米互联服务", milinkOk, "服务正常" to "未安装小米互联服务", GuideType.MILINK, onOpenGuide)
        }

        // ===== 设备信息（大框大标题 + 小卡网格） =====
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

        // 大框：机型小标题 + 品牌数字大标题 + 括号型号
        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "机型",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    modelName,
                    fontSize = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
                if (modelCode.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "型号：$modelCode",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                }
            }
        }

        // 小卡网格（两行两列，圆角小块）
        @Composable
        fun InfoCard(label: String, value: String) {
            Card(Modifier.weight(1f).padding(vertical = 3.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(
                        label,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        value.ifEmpty { "未知" },
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        maxLines = 1
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard("澎湃OS", miuiV)
            InfoCard("Android", andV)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard("内核", kernV)
            InfoCard("Root", rootV)
        }
    }
}

/** 检测项行：状态圆点（绿=通过 红=未通过）+ 名称 + 状态文案 */
@Composable
private fun EnvDetailRow(title: String, ok: Boolean, texts: Pair<String, String>) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        top.yukonga.miuix.kmp.basic.Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Close,
            contentDescription = null,
            tint = if (ok) androidx.compose.ui.graphics.Color(0xFF2EBD59) else androidx.compose.ui.graphics.Color(0xFFE84C4C),
            modifier = Modifier.size(20.dp)
        )
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
                Icons.Filled.KeyboardArrowRight,
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
