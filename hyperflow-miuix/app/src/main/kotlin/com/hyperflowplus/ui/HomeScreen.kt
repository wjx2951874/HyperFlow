package com.hyperflowplus.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var rootOk by remember { mutableStateOf(false) }
    var ksuOk by remember { mutableStateOf(false) }
    var lspOk by remember { mutableStateOf(false) }        // LSPosed 框架存在
    var moduleOk by remember { mutableStateOf(false) }    // 模块已在 LSPosed 启用（modules.list）
    var scopeOk by remember { mutableStateOf(false) }     // 推荐作用域已勾选（scope 文件含本 App + milink）
    var milinkOk by remember { mutableStateOf(false) }

    fun detect() {
        checking = true
        Thread {
            val r = runCatching { RootExec.su("id -u 2>/dev/null | tr -d ' \n'") }.getOrNull()?.trim() == "0"
            val ksuRaw = runCatching { RootExec.su("ksud -V 2>/dev/null || echo none") }.getOrNull()?.trim()
            val k = r && !ksuRaw.isNullOrBlank() && ksuRaw != "none"
            // 一次 su 读全部 LSPosed 配置（兼容 KernelSU 内嵌/模块版不同路径）
            // ==MODULES 段 = 各 modules.list 拼接；==SCOPE 段 = 各 scope 文件拼接
            val out = if (r) runCatching { RootExec.su("""echo ==LSP;
for d in /data/adb/lspd/config /data/adb/modules/lsposed/config /data/adb/modules/zygisk_lsposed/config /data/adb/lspd; do
  [ -e "${'$'}d" ] && echo "==DIR ${'$'}d"
done
echo ==MODULES;
for f in /data/adb/lspd/config/modules.list /data/adb/modules/lsposed/config/modules.list /data/adb/modules/zygisk_lsposed/config/modules.list /data/adb/lspd/modules.list; do
  [ -f "${'$'}f" ] && cat "${'$'}f"
done
echo ==SCOPE;
for f in /data/adb/lspd/config/scope/* /data/adb/modules/lsposed/config/scope/* /data/adb/modules/zygisk_lsposed/config/scope/* /data/adb/lspd/scope/*; do
  [ -f "${'$'}f" ] && cat "${'$'}f"
done
echo ==END""") }.getOrNull() else null
            // 解析：LSPosed 存在 / 模块已启用 / 作用域已勾选
            val lspInstalled = !out.isNullOrBlank() && out.contains("==DIR") && (
                    out.contains("lspd") || out.contains("lsposed"))
            val modsSeg = out?.substringAfter("==MODULES", "")?.substringBefore("==SCOPE") ?: ""
            val scopeSeg = out?.substringAfter("==SCOPE", "") ?: ""
            val modEnabled = lspInstalled && modsSeg.contains("hyperflow")
            val scopeOkV = lspInstalled && scopeSeg.contains("com.hyperflowplus")
            val m = runCatching {
                val pm = RootExec.su("pm path com.milink.service 2>/dev/null")
                !pm.isNullOrBlank() && pm.contains("package:")
            }.getOrDefault(false)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                rootOk = r; ksuOk = k; lspOk = lspInstalled
                moduleOk = modEnabled; scopeOk = scopeOkV; milinkOk = m
                checking = false
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
        // ===== 环境状态大卡片：整块彩色卡（绿/黄/红）+ 大字标题 + 明细合并（参考 HyperModifier 样式） =====
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
        val levelSub = when {
            !rootOk -> "未授予 Root 权限，点击卡片重新授权检测"
            allOk -> "全部检测通过，功能可正常使用"
            !moduleOk -> "模块未在 LSPosed 启用，点击下方按钮一键启用"
            !scopeOk -> "推荐作用域未勾选，点击下方按钮一键勾选"
            else -> "部分依赖缺失，可正常使用但部分功能受限"
        }
        val passed = listOf(rootOk, ksuOk, lspOk, moduleOk, scopeOk, milinkOk).count { it }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!checking) detect()   // 整卡点击即重新检测
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(level.copy(alpha = 0.16f))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(12.dp).clip(CircleShape).background(level)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        levelText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = level
                    )
                    Spacer(Modifier.weight(1f))
                    if (checking) {
                        Text("检测中…", style = MiuixTheme.textStyles.body2, color = level)
                    } else {
                        Text(
                            "重新检测",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            color = level.copy(alpha = 0.9f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    levelSub,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "$passed/6 项检测通过 · 点击卡片重新检测",
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = level.copy(alpha = 0.95f)
                )
                Spacer(Modifier.height(12.dp))
                // 明细行（紧凑合并进大卡片，不再是独立小框）
                EnvItem("Root 权限", rootOk, "已在 KernelSU 授权" to "未授予 Root 权限", GuideType.ROOT, onOpenGuide)
                EnvItem("KernelSU", ksuOk, "已安装并可用" to "未检测到 KernelSU", GuideType.ROOT, onOpenGuide)
                EnvItem("LSPosed 框架", lspOk, "框架存在" to "未检测到 LSPosed", GuideType.LSPOSED, onOpenGuide)
                EnvItem("模块已启用", moduleOk, "已在 LSPosed 启用" to "未在 LSPosed 启用", GuideType.MODULE_SCOPE, onOpenGuide)
                EnvItem("推荐作用域", scopeOk, "已勾选（本 App + milink）" to "未勾选推荐作用域", GuideType.MODULE_SCOPE, onOpenGuide)
                EnvItem("小米互联服务", milinkOk, "服务正常" to "未安装小米互联服务", GuideType.MILINK, onOpenGuide)

            }
        }

        // ===== 设备信息 =====
        GroupTitle("设备信息")

        Card(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    buildString {
                        append(state.deviceInfo.substringBefore("Root：").trimEnd().ifEmpty { "加载中…" })
                        if (state.deviceInfo.isNotEmpty()) {
                            append("\nRoot：").append(state.rootInfo).append(" / KSU ").append(state.ksuVersion)
                        }
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
                ArrowPreference(
                    title = "重新授权 Root",
                    summary = "把 milink 与本模块加入 KernelSU 名单",
                    onClick = {
                        Thread {
                            runCatching {
                                RootExec.exec("ksud", "allowlist", "add", "com.milink.service")
                                RootExec.exec("ksud", "allowlist", "add", "com.hyperflowplus")
                            }
                        }.start()
                        Toast.makeText(ctx, "已执行授权", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

/** 检测项行：状态圆点（绿=通过 红=未通过）+ 名称 + 状态文案 */
@Composable
private fun EnvItem(
    title: String,
    ok: Boolean,
    texts: Pair<String, String>,
    guide: GuideType,
    onOpenGuide: (GuideType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenGuide(guide) }
            .padding(horizontal = 16.dp, vertical = 9.dp),
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
        Text(
            title,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.9f)
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (ok) texts.first else texts.second,
            style = MiuixTheme.textStyles.body2,
            color = if (ok) CGreen.copy(alpha = 0.85f) else CRed.copy(alpha = 0.85f)
        )
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        )
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
