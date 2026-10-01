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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        GroupTitle("通知流转")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "亮屏流转",
                    summary = "亮屏时强制模拟锁屏放行通知",
                    checked = state.forceTransfer,
                    onCheckedChange = { state.set("force_transfer", it) }
                )
                SwitchPreference(
                    title = "分身流转",
                    summary = "微信/QQ 分身通知流转（标题带【分身】）",
                    checked = state.cloneTransfer,
                    onCheckedChange = { state.set("clone_transfer", it) }
                )
            }
        }

        // 短信持久化：默认展开子层（两个内联开关，不用弹窗，避免点击闪退）
        GroupTitle("短信持久化")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "App 内消息",
                    summary = if (state.archiveApp) "流转短信显示在消息页" else "关闭后消息页不显示流转短信",
                    checked = state.archiveApp,
                    onCheckedChange = { state.set(Config.KEY_ARCHIVE_APP, it) }
                )
                SwitchPreference(
                    title = "写入系统短信",
                    summary = "写入系统收件箱（默认关闭：可能回环/被拦截，建议用 App 内消息）",
                    checked = state.smsPersist,
                    onCheckedChange = { state.set(Config.KEY_SMS_PERSIST, it) }
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

    }
}
