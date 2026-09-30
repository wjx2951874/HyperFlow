package com.hyperflowplus.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 会话详情（短信 App 样式）：标题 = 发送人（大字）+ 来源（小字），
 * 每条消息 = 时间标签（上方小字） + 左对齐圆角气泡（正文原文）。
 * 排序按 detailSort（desc 新在前 / asc 旧在前）。
 */
@Composable
fun ConversationScreen(state: HFState, sender: String, rows: List<Array<String>>) {
    var showSort by remember { mutableStateOf(false) }
    val desc = state.detailSort != "asc"
    val sorted = remember(rows, desc) {
        rows.sortedWith { a, b -> if (desc) b[0].compareTo(a[0]) else a[0].compareTo(b[0]) }
    }
    // 标题副文案：取首条记录的来源设备
    val device = remember(rows) { rows.firstOrNull()?.getOrElse(1) { "" } ?: "" }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = sender,
            subtitle = if (device.isNotEmpty()) "来自 $device" else "",
            actions = {
                IconButton(onClick = { showSort = true }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "正文排序"
                    )
                }
            }
        )
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
                    // 时间标签
                    Text(
                        fmtTime(time),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            body.ifEmpty { "(无正文)" },
                            style = MiuixTheme.textStyles.body1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    MiuixTheme.colorScheme.surfaceVariant.copy(
                                        alpha = if (HFState.glassOn) 0.45f else 0.65f
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }

    // 正文排序弹窗
    if (showSort) {
        OverlayDialog(
            title = "正文排序",
            show = showSort,
            onDismissRequest = { showSort = false }
        ) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                DetailSortRow(
                    current = state.detailSort,
                    onClick = { state.setSort(Config.KEY_DETAIL_SORT); showSort = false }
                )
            }
        }
    }
}

/** 正文排序行：最新优先（倒序）/ 最早优先（正序），点击即时切换 */
@Composable
private fun DetailSortRow(current: String, onClick: () -> Unit) {
    val asc = current == "asc"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (asc) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.width(20.dp).height(20.dp),
            tint = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (asc) "最早优先" else "最新优先",
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "点击切换",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
