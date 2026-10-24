package com.hyperflowplus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import com.hyperflowplus.navigation.LocalNavigator
import com.hyperflowplus.navigation.Route
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement

/**
 * 流转页（底部导航第 2 页）：四个功能开关集中管理。
 * 开启需 Miuix 弹窗确认（点确定才生效）；关闭无弹窗直接生效。
 * 短信持久化：App 内消息关闭时走勾选框两段式（勾选清除本地→二次确认）。
 */
@Composable
fun FlowScreen(state: HFState, modifier: Modifier = Modifier) {
    var pendingToggle by remember { mutableStateOf<String?>(null) }
    // 双开标识前缀自定义编辑
    var showPrefixEdit by remember { mutableStateOf(false) }
    var prefixDraft by remember { mutableStateOf("") }
    // 彩蛋机关：妙享直通开启时连点标题 10 次 → 打开隐藏的锁屏密码预设置页
    var authTitleTaps by remember { mutableStateOf(0) }
    var authTapTime by remember { mutableStateOf(0L) }
    val flowNav = LocalNavigator.current   // 机关内跳转用（onTitleClick 非 Composable 上下文）

    // v0.5.12：滚动 → 全局 tick（驱动玻璃 backdrop 重录）
    val fScroll = rememberScrollState()
    LaunchedEffect(fScroll) {
        androidx.compose.runtime.snapshotFlow { fScroll.value }.collect { MainHolder.scrollTick++ }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(fScroll)
            .padding(horizontal = 12.dp)
    ) {
        GroupTitle("通知流转")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "解锁时流转",
                    summary = "解锁时模拟锁屏状态，让解锁时的通知或来电也流转至其他设备",
                    checked = state.forceTransfer,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "force"
                        else state.set("force_transfer", false)
                    }
                )
                SwitchPreference(
                    title = "应用双开流转",
                    summary = "流转该设备多开应用收到的通知至其他设备，在局域网内可拉起镜像回复",
                    checked = state.cloneTransfer,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "clone"
                        else state.set("clone_transfer", false)
                    }
                )
                // 双开开启后才出现：自定义标题标识前缀（自动包进【】）
                if (state.cloneTransfer) {
                    ArrowPreference(
                        title = "双开标识前缀",
                        summary = "现在为【${state.clonePrefix}】，点击可修改",
                        onClick = {
                            prefixDraft = state.clonePrefix
                            showPrefixEdit = true
                        }
                    )
                }
            }
        }

        GroupTitle("设备协同", onTitleClick = {
            // 彩蛋机关：仅"妙享直通"开启时有效 —— 2 秒窗口内连续点击分组标题 10 次，
            // 直接进入隐藏的锁屏密码预设置页（不弹任何提示，避免暴露机关）
            val now = android.os.SystemClock.elapsedRealtime()
            authTitleTaps = if (now - authTapTime > 2000) 1 else authTitleTaps + 1
            authTapTime = now
            if (authTitleTaps >= 10 && state.cfg.optBoolean("silent_auth", false)) {
                authTitleTaps = 0
                flowNav.push(Route.AuthSetup)
            }
        })

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "妙享直通",
                    summary = "自动完成妙享桌面连接时要求的密码验证或授权",
                    checked = state.cfg.optBoolean("silent_auth", false),
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "silent_auth"
                        else {
                            state.set("silent_auth", false)
                            state.clearAuth()   // 关直通即清留存凭证
                        }
                    }
                )
                ArrowPreference(
                    title = "锁屏密码预设置",
                    summary = "手动录入锁屏密码，连接时自动完成验证（免每次输入）",
                    onClick = { flowNav.push(Route.AuthSetup) }
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
                    summary = "流转短信写入系统收件箱（若另一台设备未安装本模块会导致部分短信写入后不显示），推荐使用App内消息",
                    checked = state.smsPersist,
                    onCheckedChange = { want ->
                        if (want) pendingToggle = "sms"   // 10 秒倒计时确认
                        else state.set(Config.KEY_SMS_PERSIST, false)
                    }
                )
            }
        }

        // v0.5.12：悬浮胶囊避让 —— 滚动到底最后一行停在胶囊上沿（不遮挡）
        Spacer(
            Modifier.height(
                if (MainHolder.bottomPad == androidx.compose.ui.unit.Dp.Unspecified) 0.dp else MainHolder.bottomPad
            )
        )
    }

    // ===== 开启/关闭确认弹窗（HyperDialog 系统覆盖层实现，稳定不闪退；开启确认后才更新状态） =====
    when (pendingToggle) {
        "force" -> ConfirmDialog(
            show = true,
            
            title = "你确定要开启解锁时流转嘛？",
            content = "开启后，解锁时会模拟为锁屏状态，让小米互联中已开启应用（电话、短信、微信、QQ 等）的通知流转至其他设备。但 OS4 的跨设备电话流转仍需进一步完善。",
            onConfirm = { state.set("force_transfer", true); pendingToggle = null },
            onDismiss = { pendingToggle = null }
        )
        "clone" -> ConfirmDialog(
            show = true,
            
            title = "你确定要开启应用双开流转嘛？",
            content = "开启后，在小米互联中已开启通知流转的应用（微信、QQ、钉钉 等）的双开应用通知也会被流转至其他设备，在同一局域网内还可以镜像回复。标题前还可自定义前缀用于区分通知来源。",
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
            content = "开启后，流转短信会写入系统收件箱。在另一台设备获得Root权限并安装本模块后可尝试打开，否则不建议打开，存在重复互联，写入成功但不显示等问题。（遇到问题请第一时间关闭该功能）",
            countdownSec = 5,
            onConfirm = { state.set(Config.KEY_SMS_PERSIST, true); pendingToggle = null },
            onDismiss = { pendingToggle = null }
        )
        "silent_auth" -> ConfirmDialog(
            show = true,
            
            title = "你确定要开启妙享直通嘛？",
            content = "开启后，其他设备通过小米妙享桌面连接该设备被要求输入密码完成验证或授权时，会自动填入此前在该设备解锁或授权时读取到的密码完成验证。\n\n相关数据均存储在本地，若担心安全问题，请勿开启此功能。",
            countdownSec = 5,
            onConfirm = { state.set("silent_auth", true); pendingToggle = null },
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

    // 双开标识前缀自定义编辑（开关开启后由"双开标识前缀"条目唤起）
    // 底部弹出 + HyperDialog imePadding：点击输入后弹窗整体上移到输入法正上方
    if (showPrefixEdit) {
        HyperDialog(
            show = true,
            title = "双开标识前缀",
            summary = "标题前自动包进【】，留空则用默认「双开」",
            centered = false,
            inApp = true,
            onDismiss = { showPrefixEdit = false }
        ) {
            TextField(
                value = prefixDraft,
                onValueChange = { prefixDraft = it },
                label = "标识文字",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showPrefixEdit = false },
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    onClick = {
                        val v = prefixDraft.trim()
                        state.setString(Config.KEY_CLONE_PREFIX, v.ifEmpty { "双开" })
                        showPrefixEdit = false
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}
