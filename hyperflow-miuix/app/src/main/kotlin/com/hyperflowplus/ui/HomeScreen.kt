package com.hyperflowplus.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
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

    // 待确认的开关弹窗：null=无；其余为弹窗标识
    var pendingToggle by remember { mutableStateOf<String?>(null) }

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
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "force"   // 开启需确认
                        else state.set("force_transfer", false)
                    }
                )
                SwitchPreference(
                    title = "分身流转",
                    summary = "微信/QQ 分身通知流转（标题带【分身】）",
                    checked = state.cloneTransfer,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "clone"
                        else state.set("clone_transfer", false)
                    }
                )
            }
        }

        // 短信持久化：默认展开子层（两个内联开关）
        GroupTitle("短信持久化")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "App 内消息",
                    summary = if (state.archiveApp) "流转短信显示在消息页" else "关闭后消息页不显示流转短信",
                    checked = state.archiveApp,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "archive"
                        else pendingToggle = "archive_off"   // 关闭时选择是否保留本地记录
                    }
                )
                SwitchPreference(
                    title = "写入系统短信",
                    summary = "写入系统收件箱（默认关闭：可能回环/被拦截，建议用 App 内消息）",
                    checked = state.smsPersist,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "sms"   // 10 秒倒计时确认
                        else state.set(Config.KEY_SMS_PERSIST, false)
                    }
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

    // ===== 开启/关闭确认弹窗（Miuix WindowDialog；开启确认后才更新状态，关闭无弹窗直接生效） =====
    when (pendingToggle) {
        "force" -> ConfirmDialog(
            show = true,
            title = "你确定要开启亮屏流转嘛？",
            content = "开启后会模拟锁屏状态，让小米互联中已开启应用（来电、短信、微信、QQ 等）的通知在亮屏时也能流转到其他设备。",
            onConfirm = { state.set("force_transfer", true); pendingToggle = null },
            onDismiss = { pendingToggle = null }
        )
        "clone" -> ConfirmDialog(
            show = true,
            title = "你确定要开启分身流转嘛？",
            content = "开启后，在小米互联中已开启通知流转的分身应用（微信、QQ、钉钉等）的通知也会被流转到另一台设备上，并且能够在标题前添加【分身】用于区分。",
            onConfirm = { state.set("clone_transfer", true); pendingToggle = null },
            onDismiss = { pendingToggle = null }
        )
        "archive" -> ConfirmDialog(
            show = true,
            title = "你确定要开启 App 内消息嘛？",
            content = "开启后，其他设备通过小米互联流转到本设备的短信会显示在本 App 的消息页面。开启期间会实时读取短信并保存到本机，历史短信可长久查看。关闭本功能时，可自由选择是否保留已存储在本地的短信记录。",
            onConfirm = { state.set(Config.KEY_ARCHIVE_APP, true); pendingToggle = null },
            onDismiss = { pendingToggle = null }
        )
        "sms" -> ConfirmDialog(
            show = true,
            title = "你确定要写入系统短信嘛？",
            content = "开启后，流转短信会写入系统收件箱。可能存在错误显示、重复互联等问题（测试多次复现），遇到异常请及时关闭。",
            countdownSec = 10,
            onConfirm = { state.set(Config.KEY_SMS_PERSIST, true); pendingToggle = null },
            onDismiss = { pendingToggle = null }
        )
        // 关闭 App 内消息：自由选择是否保留本地记录（两个按钮都执行关闭，只是保留与否不同）
        "archive_off" -> ConfirmDialog(
            show = true,
            title = "关闭 App 内消息？",
            content = "关闭后消息页不再显示流转短信。已存储在本地的短信记录如何处理？",
            cancelText = "保留记录",
            confirmText = "清除记录",
            onConfirm = {
                state.set(Config.KEY_ARCHIVE_APP, false)
                state.clearHistory()
                pendingToggle = null
            },
            onCancel = {
                state.set(Config.KEY_ARCHIVE_APP, false)
                pendingToggle = null
            },
            onDismiss = { pendingToggle = null }
        )
    }
}
