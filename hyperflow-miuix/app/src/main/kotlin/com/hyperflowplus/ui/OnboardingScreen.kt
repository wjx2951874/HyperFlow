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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // v0.5.15：弹窗大小学 KSU 更新日志弹窗（限高 0.62 屏高），底部间距加大使整体上移，
        // 修正"上半部分偏下"问题（底部对齐不变）
        val maxCardH = LocalConfiguration.current.screenHeightDp.dp * 0.62f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 64.dp)
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

/**
 * 引导页功能卡片数据。
 * **新增功能只需往 OB_FEATURES 加一项**，卡片、图标、排版全部自动渲染，
 * 不必再改任何布局代码（旧版是硬拼的整段字符串，加一项就要手动调顺序与换行）。
 */
private data class ObFeature(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val desc: String
)

private val OB_FEATURES = listOf(
    ObFeature(
        Icons.Filled.Notifications, Color(0xFF3A7BD5),
        "解锁时流转",
        "解锁状态下被系统拦截的通知与来电，强制流转到其他设备"
    ),
    ObFeature(
        Icons.Filled.ContentCopy, Color(0xFF7C5CFF),
        "双开流转",
        "分身应用通知独立流转，标题前缀可自定义"
    ),
    ObFeature(
        Icons.Filled.TouchApp, Color(0xFFE0564D),
        "点击只开分身",
        "点分身通知只拉起分身，不会连带把主应用拽起来"
    ),
    ObFeature(
        Icons.Filled.LockOpen, Color(0xFFD9A441),
        "妙享直通免密",
        "自动填入锁屏凭证完成验证，通过后自动关闭授权界面"
    ),
    ObFeature(
        Icons.Filled.Sms, Color(0xFF26A69A),
        "短信持久化",
        "流转短信存入消息页，可排序、搜索、删除；可选写入系统短信"
    ),
)

/** 使用前需要（环境要求，4 项） */
private val OB_REQUIREMENTS = listOf(
    "已 Root 的设备（KernelSU / APatch / Magisk），并授予本应用超级用户权限",
    "Xposed 框架：LSPosed 2.2+，或 Vector / JingMatrix 分支",
    "小米澎湃 OS（HyperOS），并在系统设置中开启「互联互通 / 小米互联」",
    "在 Xposed 管理器中启用 HyperFlow 并勾选 6 项作用域（App 内可一键勾选）",
)

/** 法律与责任声明（精简为 3 条最关键项） */
private val OB_LEGAL = listOf(
    "仅供个人设备学习调试使用，禁止商业用途与二次分发",
    "本模块会修改系统行为，可能不符合设备厂商服务条款，由此产生的后果由使用者自行承担",
    "流转数据仅在同一小米账号的设备间传输，仅在本机处理与保存，不会上传至任何服务器",
)

/** 单张功能卡片：色块图标 + 标题 + 一句说明 */
@Composable
private fun FeatureCard(f: ObFeature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(f.tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                f.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                f.title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.9f)
            )
            Text(
                f.desc,
                style = MiuixTheme.textStyles.body2,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
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
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
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
        SectionTitle("它能做什么")
        OB_FEATURES.forEach { f ->
            FeatureCard(f)
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("使用前需要")
        OB_REQUIREMENTS.forEach { r ->
            Text(
                "· $r",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("法律与责任声明")
        OB_LEGAL.forEach { l ->
            Text(
                "· $l",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )
        }
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
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
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
    var countdown by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }
    Text(
        "后期反馈去哪里",
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
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
