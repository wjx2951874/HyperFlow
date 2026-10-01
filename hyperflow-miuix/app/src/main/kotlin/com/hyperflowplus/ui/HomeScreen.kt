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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 环境检测三态色：绿=通过 黄=部分 红=关键缺失
private val CGreen = Color(0xFF34C759)
private val CYellow = Color(0xFFFF9F0A)
private val CRed = Color(0xFFFF3B30)

/** 首页：环境检测（红/黄/绿）+ 设备信息 —— 功能开关已移入"流转"页 */
@Composable
fun HomeScreen(state: HFState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var checking by remember { mutableStateOf(true) }
    var rootOk by remember { mutableStateOf(false) }
    var ksuOk by remember { mutableStateOf(false) }
    var lspOk by remember { mutableStateOf(false) }
    var milinkOk by remember { mutableStateOf(false) }

    fun detect() {
        checking = true
        Thread {
            val r = runCatching { RootExec.su("id -u 2>/dev/null | tr -d ' \\n'") }.getOrNull()?.trim() == "0"
            val ksuRaw = runCatching { RootExec.su("ksud -V 2>/dev/null || echo none") }.getOrNull()?.trim()
            val k = r && !ksuRaw.isNullOrBlank() && ksuRaw != "none"
            val l = if (r) runCatching {
                val out = RootExec.su("ls /data/adb/lspd 2>/dev/null | head -1; "
                        + "ls /data/adb/modules/hyperflow/module.prop 2>/dev/null | head -1; "
                        + "ls /data/adb/modules/hyperflowplus/module.prop 2>/dev/null | head -1")
                !out.isNullOrBlank() && (out.contains("lspd") || out.contains("hyperflow") || out.contains("hyperflowplus"))
            }.getOrDefault(false) else false
            val m = runCatching {
                val pm = RootExec.su("pm path com.milink.service 2>/dev/null")
                !pm.isNullOrBlank() && pm.contains("package:")
            }.getOrDefault(false)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                rootOk = r; ksuOk = k; lspOk = l; milinkOk = m
                checking = false
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
        val allOk = rootOk && ksuOk && lspOk && milinkOk
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
            else -> "部分依赖缺失，可正常使用但部分功能受限"
        }
        val passed = listOf(rootOk, ksuOk, lspOk, milinkOk).count { it }

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
                    "$passed/4 项检测通过 · 点击卡片查看状态",
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = level.copy(alpha = 0.95f)
                )
                Spacer(Modifier.height(12.dp))
                // 明细行（紧凑合并进大卡片，不再是独立小框）
                EnvItem("Root 权限", rootOk, "已在 KernelSU 授权" to "未授予 Root 权限")
                EnvItem("KernelSU", ksuOk, "已安装并可用" to "未检测到 KernelSU")
                EnvItem("LSPosed 模块", lspOk, "模块已启用且框架可用" to "模块未启用或框架未激活")
                EnvItem("小米互联服务", milinkOk, "服务正常" to "未安装小米互联服务")
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
private fun EnvItem(title: String, ok: Boolean, texts: Pair<String, String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(if (ok) CGreen else CRed)
        )
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
    }
}
