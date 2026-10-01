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
import androidx.compose.material.icons.filled.CheckCircle
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
import com.hyperflowplus.ui.HyperDialog
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
    val sorted = remember(rows, desc, state.sortVersion) {
        // 时间戳排序：兼容 content_time 两种格式（字符串比较会错乱）
        rows.sortedWith { a, b ->
            val ta = timeToEpoch(a.getOrElse(0) { "" })
            val tb = timeToEpoch(b.getOrElse(0) { "" })
            if (desc) (tb - ta).toInt() else (ta - tb).toInt()
        }
    }
    // 标题副文案：取首条记录的来源设备
    val device = remember(rows) { rows.firstOrNull()?.getOrElse(1) { "" } ?: "" }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = sender,
            subtitle = "",
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
                    // 消息气泡：左对齐、圆角、正文原文、宽度自适应（不撑满全屏）
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
                                        alpha = 0.65f
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
        HyperDialog(
            title = "正文排序",
            show = showSort,
            bottomInset = if (state.navFloat) 56.dp else 24.dp,
            onDismiss = { showSort = false }
        ) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                DetailSortRow(
                    label = "最新在前",
                    selected = state.detailSort != "asc",
                    onClick = {
                        if (state.detailSort == "asc") state.setSort(Config.KEY_DETAIL_SORT)
                        showSort = false
                    }
                )
                DetailSortRow(
                    label = "最早在前",
                    selected = state.detailSort == "asc",
                    onClick = {
                        if (state.detailSort != "asc") state.setSort(Config.KEY_DETAIL_SORT)
                        showSort = false
                    }
                )
            }
        }
    }
}

/** 正文排序行：单选两行（最新在前/最早在前），选中高亮 primary */
@Composable
private fun DetailSortRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.width(20.dp).height(20.dp),
            tint = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
    }
}

/** 解析两种时间格式为时间戳（排序用）：20260930T155344 / 2026-09-30 15:53 */
private fun timeToEpoch(raw: String): Long {
    if (raw.isBlank()) return 0L
    return runCatching {
        if (raw.contains("T")) {
            java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.CHINA).parse(raw).time
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).parse(raw).time
        }
    }.getOrDefault(0L)
}
