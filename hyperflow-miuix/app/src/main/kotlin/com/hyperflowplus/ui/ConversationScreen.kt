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
 * 会话详情：独立覆盖页（系统返回手势/返回键返回）。
 * 每条消息 = 时间+来源（上） + 左对齐圆角气泡（短信原文）。
 * 排序按 detailSort 设置（desc 新在前 / asc 旧在前）。
 */
@Composable
fun ConversationScreen(sender: String, rows: List<Array<String>>) {
    val desc = HFState.detailSort != "asc"
    val sorted = remember(rows, desc) {
        rows.sortedWith { a, b -> if (desc) b[0].compareTo(a[0]) else a[0].compareTo(b[0]) }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = sender,
            subtitle = "共 ${sorted.size} 条"
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sorted) { row ->
                val time = row.getOrElse(0) { "" }
                val device = row.getOrElse(1) { "" }
                val body = row.getOrElse(2) { "" }
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        (fmtTime(time).ifEmpty { "" } + if (device.isNotEmpty()) " ｜ 来自 $device" else "")
                            .trim(),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text(
                            body.ifEmpty { "(无正文)" },
                            style = MiuixTheme.textStyles.body1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
