package com.hyperflowplus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CategoryTitle
import top.yukonga.miuix.kmp.preference.PreferenceGroup
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** 首页：服务开关 + 设备信息（Miuix Preference 列表） */
@Composable
fun HomeScreen(state: HFState.Companion, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        CategoryTitle("服务开关")

        PreferenceGroup {
            SwitchPreference(
                title = "亮屏流转",
                subtitle = "亮屏时强制模拟锁屏放行通知",
                checked = state.forceTransfer,
                onCheckedChange = { state.set("force_transfer", it) }
            )
            SwitchPreference(
                title = "分身流转",
                subtitle = "微信/QQ 分身通知流转（标题带【分身】）",
                checked = state.cloneTransfer,
                onCheckedChange = { state.set("clone_transfer", it) }
            )
            SwitchPreference(
                title = "短信持久化",
                subtitle = "所有带号码的流转短信写入本机收件箱",
                checked = state.smsPersist,
                onCheckedChange = { state.set("sms_persist", it) }
            )
            SwitchPreference(
                title = "自动输密码",
                subtitle = "重启后自动输入锁屏密码（P1 开发中）",
                checked = state.autoUnlock,
                onCheckedChange = { state.set("auto_unlock", it) }
            )
        }

        CategoryTitle("设备信息")

        PreferenceGroup {
            top.yukonga.miuix.kmp.preference.Preference(
                title = "机型",
                subtitle = android.os.Build.MODEL + " (" + android.os.Build.MANUFACTURER + ")"
            )
            top.yukonga.miuix.kmp.preference.Preference(
                title = "系统",
                subtitle = android.os.Build.VERSION.RELEASE + " / " + android.os.Build.DISPLAY
            )
            top.yukonga.miuix.kmp.preference.Preference(
                title = "Root",
                subtitle = state.rootInfo
            )
            top.yukonga.miuix.kmp.preference.Preference(
                title = "配置",
                subtitle = com.hyperflowplus.Config.GLOBAL_CFG
            )
        }
    }
}
