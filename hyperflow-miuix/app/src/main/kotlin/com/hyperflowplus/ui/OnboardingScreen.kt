package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (MiuixTheme.colorScheme.background.luminance() < 0.5f)
                    Color(0xFF1A1A1A).copy(alpha = 0.85f)   // 黑色模式：浅黑（非纯黑）
                else Color.Black.copy(alpha = 0.45f)
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        val maxCardH = LocalConfiguration.current.screenHeightDp.dp * 0.78f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
                .heightIn(max = maxCardH)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (MiuixTheme.colorScheme.background.luminance() < 0.5f)
                        Color(0xFF262626)
                    else MiuixTheme.colorScheme.surface
                )
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
                        // 点「退出」直接关闭应用（引导不再显示，下次启动直达主页）
                        (ctx as? android.app.Activity)?.finish()
                    }
                )
                2 -> StageCoolapk(
                    ctx = ctx,
                    onDone = {
                        state.markFirstRunDone()
                        state.showOnboarding = false
                    },
                    onSkip = { stage = 3 }
                )
                3 -> StageFeedback(
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

/** 阶段1：协议说明（说明弹窗 + Miuix Checkbox 勾选 + 退出/继续） */
@Composable
private fun StageAgreement(
    ctx: android.content.Context,
    agreed: Boolean,
    onAgree: (Boolean) -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
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

    // 说明内容区：weight 弹性占位——内容长时可滚动，勾选/按钮固定底部不随滚动消失
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        Spacer(Modifier.height(14.dp))
    }

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

/** 阶段2：酷安互关卡片（跳过则进入阶段3 反馈提醒） */
@Composable
private fun StageCoolapk(
    ctx: android.content.Context,
    onDone: () -> Unit,
    onSkip: () -> Unit
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
            onClick = onSkip,
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

/** 阶段4：反馈提醒卡片（跳过酷安后提示后期如何找到作者；"我知道了"需等待 10s 才可点） */
@Composable
private fun StageFeedback(
    ctx: android.content.Context,
    onDone: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(10) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }
    Text(
        "后期反馈去哪里",
        style = MiuixTheme.textStyles.title1,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "后期遇到任何问题，欢迎来酷安找作者反馈——\n" +
                "更新、反馈、催更都在酷安，咱们一起把 HyperFlow 做得更好~",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(26.dp))

    // 按钮左右排布：左白（我知道了，倒计时内点击弹提示）/ 右蓝（我现在关注，随时可点）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                if (countdown > 0) {
                    android.widget.Toast.makeText(
                        ctx,
                        "$countdown 秒后才可以点击，不妨去酷安关注下作者",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onDone()
                }
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors()
        ) {
            Text("我知道了")
        }
        Button(
            onClick = {
                openCoolapk(ctx)
                onDone()
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text("我现在关注")
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

/** 打开酷安主页 */
private fun openCoolapk(ctx: android.content.Context) {
    runCatching {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/4112338"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
