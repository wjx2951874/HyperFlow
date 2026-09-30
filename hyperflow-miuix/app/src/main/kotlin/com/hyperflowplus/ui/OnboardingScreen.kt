package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 引导页：首次启动全屏覆盖。
 * 阶段1 = 功能说明 + 责任声明，5 秒倒计时后可确认；
 * 阶段2 = 互关酷安（"我去酷安看看" + 小"算了"）。
 * 确认一次后（本地标记）不再提示。
 */
@Composable
fun OnboardingScreen(state: HFState) {
    val ctx = LocalContext.current
    var stage by remember { mutableIntStateOf(1) }
    var countdown by remember { mutableIntStateOf(5) }
    var confirmed by remember { mutableStateOf(false) }
    val moduleEnabled = remember { checkLsposed() }

    LaunchedEffect(stage) {
        if (stage == 1 && !confirmed) {
            for (i in 5 downTo 1) {
                countdown = i
                kotlinx.coroutines.delay(1000)
            }
            confirmed = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (stage == 1) {
            Text(
                "HyperFlow",
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "澎湃OS 互联通知流转增强",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(28.dp))

            // 状态检测：模块/作用域/su
            CheckRow(ok = moduleEnabled, text = "LSPosed 模块已启用（作用域勾选 miLINK 与 HyperFlow）")
            Spacer(Modifier.height(10.dp))
            CheckRow(ok = state.rootInfo.contains("可用"), text = "Root 权限可用（KSU 已授权本应用）")
            Spacer(Modifier.height(28.dp))

            Text(
                "功能说明：亮屏/锁屏通知流转增强 · 微信/QQ 分身流转 · 短信持久归档\n\n" +
                        "责任声明：本模块仅供本人设备调试使用，请遵守相关服务条款；" +
                        "流转数据仅在同一小米账号设备间传输。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(32.dp))

            Button(
                enabled = confirmed,
                onClick = { stage = 2 }
            ) {
                Text(if (confirmed) "确认并开始使用" else "请稍候（$countdown）")
            }
        } else {
            // 阶段2：互关酷安
            Text(
                "用酷安互换吧",
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "作者想和你互关酷安，反馈问题、吹水都方便。\n一起努力让产品更好。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(28.dp))
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
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "算了",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "后期遇到问题，关于页随时能找到作者反馈",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun CheckRow(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.width(20.dp).height(20.dp),
            tint = if (ok) Color(0xFF4CAF50) else Color(0xFFFF9800)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}

/** LSPosed 模块启用状态粗检（su + 模块包可见性） */
private fun checkLsposed(): Boolean {
    return try {
        val out = RootExec.su("ls /data/adb/modules/hyperflowplus 2>/dev/null | head -1; "
                + "pm path com.hyperflowplus 2>/dev/null | head -1")
        !out.isNullOrBlank() && out.contains("hyperflowplus")
    } catch (t: Throwable) {
        false
    }
}
