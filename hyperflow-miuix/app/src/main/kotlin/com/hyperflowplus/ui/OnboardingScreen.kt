package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.state.ToggleableState
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 引导弹窗（Miuix 风格，三步流程）：
 * 阶段1 = 协议说明（7kimisu 式说明弹窗：权限/功能/责任 + 更新渠道 + 致谢，Miuix Checkbox 勾选 + 继续）；
 * 阶段2 = 诊断页：root/LSPosed 检测，权限不足可点击直接跳转 KernelSU/LSPosed 授权，通过后开始使用；
 * 阶段3 = 酷安互关卡片（引导关注作者）。
 * 确认一次后（本地标记）不再弹出。
 */
@Composable
fun OnboardingScreen(state: HFState) {
    val ctx = LocalContext.current
    var stage by remember { mutableIntStateOf(1) }
    var agreed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    // 三态检测：null=检测中，true=通过，false=未通过
    var lspState by remember { mutableStateOf<Boolean?>(null) }
    var rootState by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(true) }

    // 状态检测（阶段2 进入时启动）：未授予 root 时循环重测，授权后自动通过
    LaunchedEffect(stage, retryKey) {
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
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
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
                    onNext = { stage = 2 },
                    onExit = {
                        state.markFirstRunDone()
                        state.showOnboarding = false
                    }
                )
                2 -> StageDiagnosis(
                    ctx = ctx,
                    checking = checking,
                    rootState = rootState,
                    lspState = lspState,
                    onStart = { stage = 3 },
                    onRetry = { retryKey++ }
                )
                3 -> StageCoolapk(
                    ctx = ctx,
                    onDone = {
                        state.markFirstRunDone()
                        state.showOnboarding = false
                    }
                )
            }
        }
    }
}

/** 阶段1：协议说明（说明弹窗 + Miuix Checkbox 勾选 + 继续） */
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

    SectionTitle("使用前需要")
    Text(
        "· Root 权限（在 KernelSU 中允许本应用获取超级用户权限）\n" +
                "· LSPosed 2.2+ 框架（KernelSU 内嵌 LSPosed 亦可）\n" +
                "· 在 LSPosed 中启用本模块（模块已自动刷入，本应用会自动勾选推荐作用域）",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
    )
    Spacer(Modifier.height(14.dp))

    SectionTitle("当前功能")
    Text(
        "· 通知流转——亮屏/锁屏强制放行通知流转（含来电在线接听）\n" +
                "· 分身流转——微信/QQ 分身通知独立流转\n" +
                "· 短信流转——App 内消息归档，可选写入系统短信\n" +
                "· 在线更新——App 内一键检测更新",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
    )
    Spacer(Modifier.height(14.dp))

    SectionTitle("责任声明")
    Text(
        "· 本模块仅供个人设备调试，请遵守相关服务条款\n" +
                "· 流转数据仅在同一小米账号的设备间传输\n" +
                "· 本项目由 AI 辅助开发与调试，并经人工验证；并非完全由 AI 生成\n" +
                "· 使用中有任何问题，点击跳转酷安向作者反馈",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
    )
    Spacer(Modifier.height(16.dp))

    // 更新渠道（酷安）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable { openCoolapk(ctx) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.width(18.dp).height(18.dp),
            tint = MiuixTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "更新 / 反馈",
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Text(
                "酷安 @翰德姆（点击前往，问题反馈 / 关注）",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    // 致谢
    Text(
        "由衷感谢 KernelSU、LSPosed 与 Miuix 开源社区",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.45f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(14.dp))

    // Miuix Checkbox + 勾选文案（小米风格勾选框）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onAgree(!agreed) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            state = if (agreed) ToggleableState.On else ToggleableState.Off,
            onClick = { onAgree(!agreed) }
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "我已阅读并同意以上说明",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
    }
    Spacer(Modifier.height(12.dp))

    // 按钮左右排布：左白（退出）/ 右蓝（继续），与参考弹窗一致
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onExit,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors()
        ) {
            Text("退出")
        }
        Button(
            enabled = agreed,
            onClick = onNext,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text(if (agreed) "继续" else "请先勾选同意")
        }
    }
}

/** 阶段2：诊断页 —— 权限不足可点击跳转 KernelSU/LSPosed；通过后进入酷安页 */
@Composable
private fun StageDiagnosis(
    ctx: android.content.Context,
    checking: Boolean,
    rootState: Boolean?,
    lspState: Boolean?,
    onStart: () -> Unit,
    onRetry: () -> Unit
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

    DiagCard(
        state = if (checking) null else rootState,
        title = when {
            checking -> "正在检测 Root 权限…"
            rootState == true -> "Root 权限可用"
            rootState == false -> "Root 权限未授予"
            else -> "Root 权限不可用"
        },
        subtitle = when {
            rootState == true -> "KernelSU 已授权本应用"
            rootState == false -> "点击右侧按钮前往 KernelSU 授权"
            else -> ""
        },
        actionText = if (rootState == false) "去授权" else null,
        onAction = { launchKernelSu() }
    )
    Spacer(Modifier.height(12.dp))

    DiagCard(
        state = if (checking) null else lspState,
        title = when {
            checking -> "正在检测 LSPosed…"
            lspState == true -> "LSPosed 已就绪 · 模块已启用"
            lspState == false -> "未启用模块"
            else -> "需 Root 权限才能检测"
        },
        subtitle = when {
            lspState == true -> "框架与模块状态正常"
            lspState == false -> "点击右侧按钮前往 LSPosed 启用模块"
            else -> ""
        },
        actionText = if (lspState == false) "去启用" else null,
        onAction = { launchLsposed() }
    )
    if (rootState == false) {
        Spacer(Modifier.height(10.dp))
        Text(
            "卡住是因为还没给权限，去 KernelSU 授权后会自动通过",
            style = MiuixTheme.textStyles.body2,
            color = Color(0xFFE53935),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(20.dp))

    // 按钮左右排布：左白（重新检测）/ 右蓝（下一步），与参考弹窗一致
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onRetry,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors()
        ) {
            Text("重新检测")
        }
        Button(
            enabled = rootState == true,
            onClick = onStart,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text(
                when {
                    rootState == false -> "等待 Root 授权…"
                    rootState == null -> "检测中…"
                    else -> "下一步"
                }
            )
        }
    }
}

/** 阶段3：酷安互关卡片 */
@Composable
private fun StageCoolapk(
    ctx: android.content.Context,
    onDone: () -> Unit
) {
    Text(
        "来酷安关注作者",
        style = MiuixTheme.textStyles.title1,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "我是酷安@翰德姆，加个关注呗（会回关的哦，好友位有限，先到先得）\n" +
                "更新、反馈、催更都在酷安，有问题随时来找我，\n" +
                "咱们一起把 HyperFlow 做得更好~",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(26.dp))

    // 按钮左右排布：左白（跳过）/ 右蓝（去酷安），与参考弹窗一致
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onDone,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors()
        ) {
            Text("算了，先跳过")
        }
        Button(
            onClick = {
                openCoolapk(ctx)
                onDone()
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text("去酷安看看")
        }
    }
}

/** 小节标题 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary
    )
    Spacer(Modifier.height(4.dp))
}

/** 诊断卡片：状态图标 + 标题 + 副文案 + 可选操作按钮（权限不足时跳转授权） */
@Composable
private fun DiagCard(
    state: Boolean?,
    title: String,
    subtitle: String,
    actionText: String?,
    onAction: () -> Unit
) {
    val (icon, tint) = when (state) {
        true -> Icons.Filled.CheckCircle to Color(0xFF4CAF50)
        false -> Icons.Filled.Warning to Color(0xFFFF9800)
        null -> Icons.Filled.Info to Color(0xFF9E9E9E)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.width(22.dp).height(22.dp),
            tint = tint
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
        if (actionText != null) {
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(actionText)
            }
        }
    }
}

/** 打开酷安主页 */
private fun openCoolapk(ctx: android.content.Context) {
    runCatching {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/4112338"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}

/** 跳转 KernelSU 管理器（多候选包名，am start -p 拉起主界面） */
private fun launchKernelSu() {
    RootExec.su("for p in com.kernelsu.manager com.kernelsu com.rifsxd.ksunext; do " +
            "pm path \$p >/dev/null 2>&1 && { am start --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p \$p >/dev/null 2>&1 && break; }; done")
}

/** 跳转 LSPosed 管理器 / KernelSU（内嵌 LSP 时在 KernelSU 管理） */
private fun launchLsposed() {
    RootExec.su("for p in org.lsposed.manager com.kernelsu.manager com.kernelsu com.rifsxd.ksunext; do " +
            "pm path \$p >/dev/null 2>&1 && { am start --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p \$p >/dev/null 2>&1 && break; }; done")
}

/** Root 实时检测：su 取 uid == 0 */
private fun checkRoot(): Boolean {
    return try {
        RootExec.su("id -u 2>/dev/null | tr -d ' \\n'") == "0"
    } catch (t: Throwable) {
        false
    }
}

/** LSPosed 模块启用检测：框架存在 + 模块已刷入（兼容新旧模块 id） */
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
