package com.hyperflowplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import com.hyperflowplus.ui.HyperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 会话详情（短信 App 样式）：标题 = 发送人（大字）+ 来源（小字），
 * 每条消息 = 时间标签（上方小字） + 左对齐圆角气泡（正文原文）。
 * 排序按 detailSort（desc 新在前 / asc 旧在前）。
 *
 * v0.5.5：不再自带 TopAppBar（改由 MainActivity 的 Scaffold topBar 槽提供
 * [ConversationTopBar]，使底部导航栏在会话详情页保留不消失）。
 * @param modifier 由调用方传入 Scaffold 内容区 padding 后的 Modifier。
 */
@Composable
fun ConversationScreen(
    state: HFState,
    sender: String,
    rows: List<Array<String>>,
    modifier: Modifier = Modifier
) {
    var showSort by remember { mutableStateOf(false) }
    val desc = state.detailSort != "asc"

    /** 本地私有解析（与消息页同规则）：正文列表首列 M/dd HH:mm 转毫秒时间戳，用于排序 */
    fun timeToEpoch(raw: String): Long {
        val t = raw.trim().replace("/", "-")
        val p = java.text.SimpleDateFormat("M-d HH:mm", java.util.Locale.US)
        p.isLenient = false
        return runCatching { p.parse(t)?.time ?: raw.toLongOrNull() ?: 0L }.getOrDefault(0L)
    }
    val sorted = remember(rows, desc, state.sortVersion) {
        // 时间戳排序：兼容 content_time 两种格式（字符串比较会错乱）
        rows.sortedWith { a, b ->
            val ta = timeToEpoch(a.getOrElse(0) { "" })
            val tb = timeToEpoch(b.getOrElse(0) { "" })
            if (desc) (tb - ta).toInt() else (ta - tb).toInt()
        }
    }
    Column(modifier.fillMaxSize().background(MiuixTheme.colorScheme.surfaceContainerLow)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sorted) { row ->
                val time = row.getOrElse(0) { "" }
                val body = row.getOrElse(2) { "" }
                Column(Modifier.fillMaxWidth()) {
                    // 时间标签：时间 + 机型 + 来源（本地历史标记），居中置顶（对齐系统短信时间戳样式）
                    val rowDevice = row.getOrElse(1) { "" }
                    val rowLocal = if (row.getOrElse(3) { "" } == "local") "（本地）" else ""
                    val devTag = if (rowDevice.isNotEmpty()) "｜来自" + rowDevice else ""
                    Text(
                        fmtTime(time) + devTag + rowLocal,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // 消息气泡：左对齐、小圆角 + 发丝描边（对齐 MIUI 短信接收气泡样式）、
                    // 正文原文、宽度自适应（不撑满全屏）；页面底为浅灰容器色衬托白色气泡
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            body.ifEmpty { "(无正文)" },
                            style = MiuixTheme.textStyles.body1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MiuixTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 会话详情顶栏：返回键 + 发送人标题 + 排序切换（最新在前 ↔ 最早在前）。
 * v0.5.5 由 MainActivity 的 Scaffold topBar 槽调用（Miuix TopAppBar 自行处理
 * 状态栏 inset），配合内容区渲染的 [ConversationScreen]，底部导航栏保留不消失。
 */
@Composable
fun ConversationTopBar(
    sender: String,
    desc: Boolean,
    onBack: () -> Unit,
    onSort: () -> Unit
) {
    TopAppBar(
        title = sender,
        subtitle = "",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "返回"
                )
            }
        },
        actions = {
            // 排序直接点击切换（最新在前 ↔ 最早在前），不再弹窗
            IconButton(onClick = onSort) {
                Icon(
                    imageVector = if (desc) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = if (desc) "最新在前" else "最早在前",
                    tint = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
            }
        }
    )
}
