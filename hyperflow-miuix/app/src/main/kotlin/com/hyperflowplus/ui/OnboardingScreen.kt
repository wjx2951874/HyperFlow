package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Info
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
import top.yukonga.miuix.kmp.basic.CheckBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 引导弹窗（Miuix 风格，三步流程）：
 * 阶段1 = 协议说明：列出所需权限与责任声明，勾选「我已同意」→ 下一步；
 * 阶段2 = 诊断页：root/LSPosed 检测，权限不足停在诊断页（循环检测直到授权）；
 *          附酷安入口；诊断通过 → 开始使用；
 * 阶段3 = 互关页：幽默文案，「去酷安看看」/「下次一定」。
 * 确认一次后（本地标记）不再弹出。
 */
@Composable
fun OnboardingScreen(state: HFState) {
    val ctx = LocalContext.current
    var stage by remember { mutableIntStateOf(1) }
    var agreed by remember { mutableStateOf(false) }
    // 三态检测：null=检测中，true=通过，false=未通过
    var lspState by remember { mutableStateOf<Boolean?>(null) }
    var rootState by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(true) }

    // 状态检测（阶段2 进入时启动）：未授予 root 时循环重测，授权后自动通过
    LaunchedEffect(stage) {
        if (stage != 2) return@LaunchedEffect
        checking = true
        while (true) {
            val root = checkRoot()
            val lsp = if (root) checkLsposed() else null
            Handler(Looper.getMainLooper()).post {
                rootState = root
                lspState = lsp
                checking = false
            }
            if (root) break
            kotlinx.coroutines.delay(1500)
        }
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
                .background(MiuixTheme.colorScheme.surface)
                .border(
                    0.5.dp,
                    MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (stage) {
                1 -> StageAgreement(
                    ctx = ctx,
                    agreed = agreed,
                    onAgree = { agreed = it },
                    onNext = { stage = 2 }
                )
                2 -> StageDiagnosis(
                    ctx = ctx,
                    checking = checking,
                    rootState = rootState,
                    lspState = lspState,
                    onStart = { stage = 3 }
                )
                else -> StageFollow(ctx, state)
            }
        }
    }
}

/** 阶段1：协议说明 + 勾选「我已同意」 */
@Composable
private fun StageAgreement(
    ctx: android.content.Context,
    agreed: Boolean,
    onAgree: (Boolean) -> Unit,
    onNext: () -> Unit
) {
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
    Spacer(Modifier.height(18.dp))

    Text(
        "本模块需要以下权限：\n" +
                "· Root 授权（KSU / Shamiko 中允许本应用获取超级用户权限）\n" +
                "· LSPosed 2.2+ 框架（KSU 内嵌 LSP 亦可）\n" +
                "· 刷入本模块后重启，作用域勾选 miLINK\n\n" +
                "功能：亮屏流转、微信/QQ 分身流转、短信流转归档、来电在线接听。\n\n" +
                "责任声明：本模块仅供个人设备调试，请遵守相关服务条款；流转数据仅在同一小米账号设备间传输。",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
    )
    Spacer(Modifier.height(16.dp))

    // 我已同意（点整行切换）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onAgree(!agreed) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckBox(
            checked = agreed,
            onCheckedChange = onAgree
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "我已阅读并同意以上说明",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
    }
    Spacer(Modifier.height(16.dp))

    Button(
        enabled = agreed,
        onClick = onNext
    ) {
        Text(if (agreed) "下一步" else "请先勾选同意")
    }
}

/** 阶段2：诊断页 —— 权限不足停住，直到授权才可继续；附酷安链接 */
@Composable
private fun StageDiagnosis(
    ctx: android.content.Context,
    checking: Boolean,
    rootState: Boolean?,
    lspState: Boolean?,
    onStart: () -> Unit
) {
    Text(
        "环境诊断",
        style = MiuixTheme.textStyles.title1,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "确认环境就绪后即可开始使用",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    )
    Spacer(Modifier.height(22.dp))

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
    if (rootState == false) {
        Spacer(Modifier.height(8.dp))
        Text(
            "卡住是因为还没给我权限，\n去 KSU/Shamiko 授权后会自动通过",
            style = MiuixTheme.textStyles.body2,
            color = Color(0xFFE53935),
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(18.dp))

    // 酷安入口（诊断页常驻链接）
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                runCatching {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/4112338"))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "作者：酷安@翰德姆",
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary
        )
    }
    Spacer(Modifier.height(14.dp))

    Button(
        enabled = rootState == true,
        onClick = onStart
    ) {
        Text(
            when {
                rootState == false -> "等待 Root 授权…"
                rootState == null -> "检测中…"
                else -> "开始使用"
            }
        )
    }
}

/** 阶段3：互关页（幽默文案） */
@Composable
private fun StageFollow(ctx: android.content.Context, state: HFState) {
    Text(
        "等一下！\n白嫖可不行",
        style = MiuixTheme.textStyles.title1,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "点个关注再走，作者才有动力继续爆肝。\n出了 bug 也能顺着酷安找到我，\n吹水也欢迎。",
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
        Text("去酷安抱一下大腿")
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
            "下次一定",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

/** 三态检测行：true=绿勾，false=橙叹号，null=灰问号（检测中/需Root） */
@Composable
private fun CheckRow(state: Boolean?, text: String) {
    val (icon, tint) = when (state) {
        true -> Icons.Filled.CheckCircle to Color(0xFF4CAF50)
        false -> Icons.Filled.Warning to Color(0xFFFF9800)
        null -> Icons.Filled.Info to Color(0xFF9E9E9E)
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
