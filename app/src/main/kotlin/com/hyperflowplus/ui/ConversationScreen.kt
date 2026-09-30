package com.hyperflowplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 会话详情（短信 App 样式）：标题 = 发送人（大字）+ 来源（小字），
 * 每条消息 = 时间标签（上方小字） + 左对齐圆角气泡（正文原文）。
 * 排序按 detailSort（desc 新在前 / asc 旧在前）。
 */
@Composable
fun ConversationScreen(sender: String, rows: List<Array<String>>) {
    val desc = HFState.detailSort != "asc"
    val sorted = remember(rows, desc) {
        rows.sortedWith { a, b -> if (desc) b[0].compareTo(a[0]) else a[0].compareTo(b[0]) }
    }
    // 标题副文案：取首条记录的来源设备
    val device = remember(rows) { rows.firstOrNull()?.getOrElse(1) { "" } ?: "" }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = sender,
            subtitle = if (device.isNotEmpty()) "来自 $device" else ""
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
                                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
