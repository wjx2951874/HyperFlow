package com.hyperflowplus.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    state: HFState,
    sender: String,
    rows: List<Array<String>>,
    modifier: Modifier = Modifier
) {
    var showSort by remember { mutableStateOf(false) }
    val desc = state.detailSort != "asc"
    val context = androidx.compose.ui.platform.LocalContext.current
    // 多选模式：长按消息进入，顶部操作栏复制/删除；点击切换选中，空选自动退出
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
    val selectedRows = remember(sorted, selected) {
        selected.sorted().map { sorted[it] }
    }
    // 页面底 = 主题 surface（浅 #F7F7F7 / 暗 #000000）＝ 真实短信详情页底色
    Column(modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        // 多选操作栏：已选 N 条 + 复制 / 删除 / 取消（系统短信长按多选同款交互）
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "已选 ${selected.size} 条",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    if (selectedRows.isNotEmpty()) {
                        runCatching {
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            cm.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "HyperFlow",
                                    selectedRows.joinToString("\n") { it.getOrElse(2) { "" } }
                                )
                            )
                            Toast.makeText(context, "已复制 ${selectedRows.size} 条", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("复制") }
                TextButton(onClick = { if (selectedRows.isNotEmpty()) showDeleteConfirm = true }) { Text("删除") }
                TextButton(onClick = { selectionMode = false; selected = emptySet() }) { Text("取消") }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(sorted) { index, row ->
                val isSel = selectionMode && selected.contains(index)
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
                    // 消息气泡：左对齐接收气泡，样式按真实小米短信 18.0.0.32 反编译结果校准 ——
                    // 圆角 16dp（bubble_corner_radius）、内边距 18.9/13.8dp（bubble_padding_left/right/top_and_bottom）、
                    // 气泡底 = 主题 surfaceVariant（浅 #FFFFFF / 暗 #242424）＝ 真实短信收气泡色、无描边、
                    // 正文 16sp 行距 1.1（bubble_body_line_spacing_multiplier）、最大宽受 57dp 屏边距约束。
                    // 注：分身流转的都是"收到"的通知，无自发消息，故全部渲染为左侧收气泡
                    // （真实短信里的绿色"发"气泡不适用）。
                    // 长按进入多选（系统短信逻辑），多选模式下点击切换选中、选中项描边高亮。
                    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp.dp - 72.dp)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            body.ifEmpty { "(无正文)" },
                            style = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 17.6.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .widthIn(max = maxBubbleWidth)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant)
                                .then(
                                    if (isSel) Modifier.border(
                                        2.dp,
                                        MiuixTheme.colorScheme.primary,
                                        RoundedCornerShape(16.dp)
                                    ) else Modifier
                                )
                                .padding(horizontal = 19.dp, vertical = 14.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) {
                                            selected = if (isSel) selected - index else selected + index
                                            if (selected.isEmpty()) selectionMode = false
                                        }
                                    },
                                    onLongClick = {
                                        selectionMode = true
                                        selected = selected + index
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
    // 删除确认（重要信息提前确认，系统短信删除逻辑）
    if (showDeleteConfirm) {
        ConfirmDialog(
            show = showDeleteConfirm,
            bottomInset = 40.dp,
            title = "删除所选 ${selectedRows.size} 条消息？",
            content = "删除后本地保存的记录将一并移除（小米端实时流转的消息可能在下次轮询后重新出现）。",
            onConfirm = {
                state.deleteFlowRows(selectedRows)
                showDeleteConfirm = false
                selectionMode = false
                selected = emptySet()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

/**
 * 会话详情顶栏：返回键 + 发送人标题 + 排序选择框（与消息页第一层一致，Popup 弹窗，
 * 选项：最新在前 / 最早在前）。
 * v0.5.5 由 MainActivity 的 Scaffold topBar 槽调用（Miuix TopAppBar 自行处理
 * 状态栏 inset），配合内容区渲染的 [ConversationScreen]，底部导航栏保留不消失。
 */
@Composable
fun ConversationTopBar(
    sender: String,
    desc: Boolean,
    onBack: () -> Unit,
    onSetSort: (String) -> Unit
) {
    var showSort by remember { mutableStateOf(false) }
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
            // 排序：Popup 选择框（KSU 风格），不再直接切换（用户反馈：点击后应有选择弹窗）
            IconButton(onClick = { showSort = true }) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "排序",
                    tint = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
            }
            if (showSort) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopEnd,
                    offset = androidx.compose.ui.unit.IntOffset(0, with(LocalDensity.current) { 46.dp.roundToPx() }),
                    onDismissRequest = { showSort = false }
                ) {
                    Surface(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 10.dp,
                        modifier = Modifier.width(230.dp)
                    ) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            @Composable
                            fun Item(label: String, sel: Boolean, onClick: () -> Unit) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable(onClick = onClick)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        label,
                                        style = MiuixTheme.textStyles.body1,
                                        color = if (sel) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (sel) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "已选",
                                            tint = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Item("最新在前", desc, {
                                if (!desc) onSetSort("desc")
                                showSort = false
                            })
                            Item("最早在前", !desc, {
                                if (desc) onSetSort("asc")
                                showSort = false
                            })
                        }
                    }
                }
            }
        }
    )
}
