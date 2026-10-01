package com.hyperflowplus.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 分组标题（HyperOS 设置页风格） */
@Composable
fun GroupTitle(text: String) {
    Text(
        text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

/** 首页：服务开关 + 设备信息（Miuix Card 分组） */
@Composable
fun HomeScreen(state: HFState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var pendingEnable by remember { mutableStateOf<String?>(null) }   // 待确认开启的功能 key
    var showPersist by remember { mutableStateOf(false) }             // 短信持久化二级弹窗

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        GroupTitle("服务开关")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "亮屏流转",
                    summary = "亮屏时强制模拟锁屏放行通知",
                    checked = state.forceTransfer,
                    onCheckedChange = { checked ->
                        if (checked) {
                            pendingEnable = "force_transfer"   // 开启前先确认（说明内容/实现/风险）
                        } else {
                            state.set("force_transfer", false)
                        }
                    }
                )
                SwitchPreference(
                    title = "分身流转",
                    summary = "微信/QQ 分身通知流转（标题带【分身】）",
                    checked = state.cloneTransfer,
                    onCheckedChange = { checked ->
                        if (checked) {
                            pendingEnable = "clone_transfer"
                        } else {
                            state.set("clone_transfer", false)
                        }
                    }
                )
                ArrowPreference(
                    title = "短信持久化",
                    summary = "点击设置流转短信的保存方式",
                    onClick = { showPersist = true }
                )
            }
        }

        GroupTitle("设备信息")

        Card(Modifier.fillMaxWidth()) {
            Column {
                // 设备信息直接展示（多行文本，不弹窗——弹窗在部分设备上会闪退）
                // Root 行动态拼接 state.rootInfo：轮询授权后即时更新，不用重进 App
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
                    summary = "把 milink 与本模块加入 KSU 名单",
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

    // ===== 开启确认弹窗（HyperOS 风格：先说明再确认开启） =====
    val pending = pendingEnable
    if (pending != null) {
        val info = when (pending) {
            "force_transfer" -> Triple(
                "亮屏流转",
                "① 要转发哪些内容：本机收到的所有短信 / 通知（含验证码、服务商短信、微信 QQ 消息）\n② 如何实现：通过小米互联服务（milink）流转到已绑定设备\n③ 可能出现的问题：部分短信延迟、验证码可能被拦截、需保持互联与蓝牙连接",
                "force_transfer"
            )
            else -> Triple(
                "分身流转",
                "① 要转发哪些内容：微信 / QQ 分身收到的通知\n② 如何实现：绕过双开检测，将分身通知以主身份流转到已绑定设备\n③ 可能出现的问题：同一人发主 / 分身消息可能合并；与主微信通知同时到达时可能互相覆盖",
                "clone_transfer"
            )
        }
        OverlayDialog(
            title = info.first,
            summary = info.second,
            show = true,
            onDismissRequest = { pendingEnable = null }
        ) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    text = "取消",
                    onClick = { pendingEnable = null },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = "确认开启",
                    onClick = {
                        state.set(info.third, true)
                        pendingEnable = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // ===== 短信持久化二级弹窗（App 内消息 / 写入系统短信） =====
    if (showPersist) {
        OverlayDialog(
            title = "短信持久化",
            summary = "选择流转短信的保存方式",
            show = showPersist,
            onDismissRequest = { showPersist = false }
        ) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                SwitchPreference(
                    title = "App 内消息",
                    summary = if (state.archiveApp) "流转短信显示在消息页" else "关闭后消息页不再显示流转短信",
                    checked = state.archiveApp,
                    onCheckedChange = { state.set(Config.KEY_ARCHIVE_APP, it) }
                )
                SwitchPreference(
                    title = "写入系统短信",
                    summary = "将短信写入系统收件箱（默认关闭）",
                    checked = state.smsPersist,
                    onCheckedChange = { state.set(Config.KEY_SMS_PERSIST, it) }
                )
                Text(
                    "注意：写入系统短信可能被系统拦截或造成重复流转（回环），建议保持关闭，优先使用 App 内消息归档。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }
    }

    }
}
