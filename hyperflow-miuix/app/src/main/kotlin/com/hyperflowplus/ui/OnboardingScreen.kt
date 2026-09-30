package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 引导弹窗：首次启动居中卡片浮层（Miuix 风格）。
 * 阶段1 = 状态检测 + 功能说明 + 责任声明，5 秒倒计时后可确认；
 * 阶段2 = 互关酷安（"我去酷安看看" + 小"算了"）。
 * 确认一次后（本地标记）不再弹出。
 */
@Composable
fun OnboardingScreen(state: HFState) {
    val ctx = LocalContext.current
    var stage by remember { mutableIntStateOf(1) }
    var countdown by remember { mutableIntStateOf(5) }
    var confirmed by remember { mutableStateOf(false) }
    // 三态检测：null=检测中/需Root，true=通过，false=未通过
    var lspState by remember { mutableStateOf<Boolean?>(null) }
    var rootState by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(true) }

    // 倒计时（阶段1 确认前）
    LaunchedEffect(stage) {
        if (stage == 1 && !confirmed) {
            for (i in 5 downTo 1) {
                countdown = i
                kotlinx.coroutines.delay(1000)
            }
            confirmed = true
        }
    }

    // 状态检测：后台线程真实检测，主线程回写
    LaunchedEffect(Unit) {
        checking = true
        Thread {
            val root = checkRoot()
            val lsp = if (root) checkLsposed() else null
            Handler(Looper.getMainLooper()).post {
                rootState = root
                lspState = lsp
                checking = false
            }
        }.start()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (stage == 1) {
                // —— 阶段1：检测 + 功能说明 ——
                Text(
                    "欢迎使用 HyperFlow",
                    style = MiuixTheme.textStyles.title1,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "澎湃OS 互联通知流转增强",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(22.dp))

                // 状态检测区
                CheckRow(
                    state = if (checking) null else rootState,
                    text = when {
                        checking -> "正在检测 Root 权限…"
                        rootState == true -> "Root 权限可用（KSU 已授权 / 设备默认 root）"
                        rootState == false -> "Root 权限未授予（请在 KSU 授权本应用）"
                        else -> "Root 权限不可用"
                    }
                )
                Spacer(Modifier.height(10.dp))
                CheckRow(
                    state = if (checking) null else lspState,
                    text = when {
                        checking -> "正在检测 LSPosed 模块…"
                        lspState == true -> "LSPosed 已安装且模块已刷入"
                        lspState == false -> "LSPosed 未安装或模块未刷入（请先刷入本模块）"
                        else -> "需 Root 权限才能检测模块状态"
                    }
                )
                Spacer(Modifier.height(18.dp))

                Text(
                    "使用说明\n" +
                            "① 需已安装 LSPosed 2.2+（KSU 内嵌 LSP 也可）\n" +
                            "② 刷入本模块后重启，LSPosed 作用域勾选 miLINK\n" +
                            "③ 亮屏流转：通知在亮屏时也会放行流转\n" +
                            "④ 分身流转：微信/QQ 分身通知带【分身】流转\n" +
                            "⑤ 短信：流转短信归档在本 App「消息」页查看\n" +
                            "⑥ 来电：锁屏状态下可在线接听\n\n" +
                            "责任声明：本模块仅供个人设备调试，请遵守相关服务条款；流转数据仅在同一小米账号设备间传输。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    enabled = confirmed,
                    onClick = { stage = 2 }
                ) {
                    Text(if (confirmed) "确认并开始使用" else "请稍候（$countdown）")
                }
            } else {
                // —— 阶段2：互关酷安 ——
                Text(
                    "用酷安，\n和作者互关吧",
                    style = MiuixTheme.textStyles.title1,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "反馈问题、吹水都方便。\n一起努力让产品更好。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        runCatching {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/4112338"))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        }
                        state.markFirstRunDone()
                        state.showOnboarding = false
                    }
                ) {
                    Text("我去酷安看看")
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            state.markFirstRunDone()
                            state.showOnboarding = false
                            Toast.makeText(ctx, "后期可从 App 内「关于」页找到作者反馈", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "算了",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/** 三态检测行：true=绿勾，false=橙叹号，null=灰问号（检测中/需Root） */
@Composable
private fun CheckRow(state: Boolean?, text: String) {
    val (icon, tint) = when (state) {
        true -> Icons.Filled.CheckCircle to Color(0xFF4CAF50)
        false -> Icons.Filled.Warning to Color(0xFFFF9800)
        null -> Icons.Filled.HelpOutline to Color(0xFF9E9E9E)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.width(20.dp).height(20.dp),
            tint = tint
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}

/** Root 实时检测：su 取 uid == 0 */
private fun checkRoot(): Boolean {
    return try {
        RootExec.su("id -u 2>/dev/null | tr -d ' \\n'") == "0"
    } catch (t: Throwable) {
        false
    }
}

/** LSPosed 模块启用检测：框架（/data/adb/lspd 或 magisk 模块）存在 + 模块已刷入（兼容新旧模块 id） */
private fun checkLsposed(): Boolean {
    return try {
        val out = RootExec.su("ls /data/adb/lspd 2>/dev/null | head -1; "
                + "ls /data/adb/modules/hyperflow/module.prop 2>/dev/null | head -1; "
                + "ls /data/adb/modules/hyperflowplus/module.prop 2>/dev/null | head -1")
        !out.isNullOrBlank() &&
                (out.contains("lspd") || out.contains("hyperflow") || out.contains("hyperflowplus"))
    } catch (t: Throwable) {
        false
    }
}
