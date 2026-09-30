package com.hyperflowplus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
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
                    onCheckedChange = { state.set("force_transfer", it) }
                )
                SwitchPreference(
                    title = "分身流转",
                    summary = "微信/QQ 分身通知流转（标题带【分身】）",
                    checked = state.cloneTransfer,
                    onCheckedChange = { state.set("clone_transfer", it) }
                )
                SwitchPreference(
                    title = "短信持久化",
                    summary = "所有带号码的流转短信写入本机收件箱",
                    checked = state.smsPersist,
                    onCheckedChange = { state.set("sms_persist", it) }
                )
                SwitchPreference(
                    title = "自动输密码",
                    summary = "重启后自动输入锁屏密码（P1 开发中）",
                    checked = state.autoUnlock,
                    onCheckedChange = { state.set("auto_unlock", it) }
                )
            }
        }

        GroupTitle("设备信息")

        Card(Modifier.fillMaxWidth()) {
            Column {
                ArrowPreference(
                    title = "机型",
                    summary = android.os.Build.MODEL + " (" + android.os.Build.MANUFACTURER + ")"
                )
                ArrowPreference(
                    title = "系统",
                    summary = android.os.Build.VERSION.RELEASE + " / " + android.os.Build.DISPLAY
                )
                ArrowPreference(
                    title = "Root",
                    summary = state.rootInfo
                )
                ArrowPreference(
                    title = "配置",
                    summary = com.hyperflowplus.Config.GLOBAL_CFG
                )
            }
        }
    }
}
