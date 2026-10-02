package com.hyperflowplus.ui

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
import androidx.compose.ui.unit.dp
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 流转页（底部导航第 2 页）：四个功能开关集中管理。
 * 开启需 Miuix 弹窗确认（点确定才生效）；关闭无弹窗直接生效。
 * 短信持久化：App 内消息关闭时走勾选框两段式（勾选清除本地→二次确认）。
 */
@Composable
fun FlowScreen(state: HFState, modifier: Modifier = Modifier) {
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
                    summary = "亮屏时强制模拟锁屏放行通知（含来电在线接听）",
                    checked = state.forceTransfer,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "force"
                        else state.set("force_transfer", false)
                    }
                )
                SwitchPreference(
                    title = "分身流转",
                    summary = "微信/QQ 分身通知独立流转（标题带【分身】）",
                    checked = state.cloneTransfer,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "clone"
                        else state.set("clone_transfer", false)
                    }
                )
            }
        }

        GroupTitle("短信持久化")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "App 内消息",
                    summary = if (state.archiveApp) "流转短信显示在消息页，实时保存本地历史" else "关闭后消息页不显示流转短信",
                    checked = state.archiveApp,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "archive"
                        else pendingToggle = "archive_off"   // 关闭时选择是否保留本地记录
                    }
                )
                SwitchPreference(
                    title = "写入系统短信",
                    summary = "流转短信写入系统收件箱（默认关闭：可能回环/被拦截，建议用 App 内消息）",
                    checked = state.smsPersist,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "sms"   // 10 秒倒计时确认
                        else state.set(Config.KEY_SMS_PERSIST, false)
                    }
                )
            }
        }
    }

    // ===== 开启/关闭确认弹窗（HyperDialog 系统覆盖层实现，稳定不闪退；开启确认后才更新状态） =====
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
            
            title = "你确定要开启写入系统短信嘛？",
            content = "开启后，流转短信会写入系统收件箱。可能存在错误显示、重复互联等问题（测试多次复现），遇到异常请及时关闭。",
            countdownSec = 10,
            onConfirm = { state.set(Config.KEY_SMS_PERSIST, true); pendingToggle = null },
            onDismiss = { pendingToggle = null }
        )
        // 关闭 App 内消息：第一层=勾选框（勾选才清空本地数据）+ 蓝色"继续"；
        // 勾选后点继续 → 第二层"确定删除/保留数据"二次确认
        "archive_off" -> ConfirmDialog(
            show = true,
            
            title = "关闭 App 内消息？",
            content = "关闭后消息页不再显示流转短信。本地保存的历史记录默认保留，重新开启后仍可查看。",
            checkboxText = "同时清除本地保存的历史记录（删除后不可恢复）",
            singleConfirmText = "继续",
            onCheckedConfirm = { del ->
                if (del) pendingToggle = "archive_del"
                else {
                    state.set(Config.KEY_ARCHIVE_APP, false)
                    pendingToggle = null
                }
            },
            onDismiss = { pendingToggle = null }
        )
        // 第二层：左白"确定删除"（关闭+清空）/ 右蓝"保留数据"（只关闭，保留本地）
        "archive_del" -> ConfirmDialog(
            show = true,
            
            title = "确定要删除本地数据？",
            content = "删除后本地保存的历史短信记录将无法恢复（不影响小米互联端的数据）。",
            cancelText = "确定删除",
            confirmText = "保留数据",
            onConfirm = { state.set(Config.KEY_ARCHIVE_APP, false); pendingToggle = null },   // 蓝：只关闭
            onCancel = {
                state.set(Config.KEY_ARCHIVE_APP, false)
                state.clearHistory()   // 白：关闭 + 清空本地
                pendingToggle = null
            },
            onDismiss = { pendingToggle = null }
        )
    }
}
