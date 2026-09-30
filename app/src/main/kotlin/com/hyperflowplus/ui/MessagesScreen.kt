package com.hyperflowplus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch

/**
 * 消息页：按发送人（含来源设备）分组的会话列表。
 * 会话头 = 发送人 + 最新正文预览（省略号） + 时间；点击进入会话详情。
 */
@Composable
fun MessagesScreen(state: HFState.Companion, modifier: Modifier = Modifier) {
    val convos = remember(state.flow, state.archiveSort) {
        parseFlow(state.flow, state.archiveSort)
    }
    if (convos.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp)) {
            Text("暂无流转消息", color = MiuixTheme.colorScheme.onBackground)
            Text(
                "短信/通知流转到本机后在这里归档显示",
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(convos, key = { it.first }) { (sender, rows) ->
            val latest = rows.maxByOrNull { it[0] } ?: return@items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.currentConversation = sender to rows }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material.icons.Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.width(40.dp)
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sender,
                            style = MiuixTheme.textStyles.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            fmtTime(latest[0]),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        latest[2].ifEmpty { "(无正文)" },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 解析 flow provider 输出 → 会话分组（desc=新在前 / asc=旧在前，按设置） */
fun parseFlow(raw: String, sort: String): List<Pair<String, List<Array<String>>>> {
    val desc = sort != "asc"
    val groups = LinkedHashMap<String, MutableList<Array<String>>>()
    for (line in raw.lines()) {
        val idx = line.indexOf("content_title=")
        val bodyIdx = line.indexOf("content_description=")
        val timeIdx = line.indexOf("content_time=")
        val deviceIdx = line.indexOf("content_device_name=")
        if (idx < 0 || bodyIdx < 0) continue
        val title = line.substring(idx + "content_title=".length, if (bodyIdx > idx) bodyIdx else line.length)
            .trim()
        val body = if (bodyIdx >= 0)
            line.substring(bodyIdx + "content_description=".length,
                if (timeIdx > bodyIdx) timeIdx else line.length).trim()
            else ""
        val time = if (timeIdx >= 0)
            line.substring(timeIdx + "content_time=".length,
                if (deviceIdx > timeIdx) deviceIdx else line.length).trim()
            else ""
        val device = if (deviceIdx >= 0) line.substring(deviceIdx + "content_device_name=".length).trim() else ""
        if (title.isEmpty() && body.isEmpty()) continue
        val key = title + (if (device.isNotEmpty()) "｜来自" + device else "")
        groups.getOrPut(key) { mutableListOf() }.add(arrayOf(time, device, body))
    }
    val list = groups.map { it.key to it.value }.toMutableList()
    for ((_, rows) in list) {
        rows.sortWith { a, b -> if (desc) b[0].compareTo(a[0]) else a[0].compareTo(b[0]) }
    }
    list.sortWith { a, b ->
        val ta = a.second.firstOrNull()?.get(0) ?: ""
        val tb = b.second.firstOrNull()?.get(0) ?: ""
        if (desc) tb.compareTo(ta) else ta.compareTo(tb)
    }
    return list
}

/** 时间格式：今天 HH:mm / 昨天 HH:mm / 一周内 周X HH:mm / 一年内 MM-dd HH:mm / 跨年 yyyy-MM-dd HH:mm */
fun fmtTime(raw: String): String {
    if (raw.isBlank()) return ""
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
    val t = runCatching { sdf.parse(raw) }.getOrNull() ?: return raw
    val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(t)
    fun startOfDay(c: java.util.Calendar): Long {
        val x = c.clone() as java.util.Calendar
        x.set(java.util.Calendar.HOUR_OF_DAY, 0)
        x.set(java.util.Calendar.MINUTE, 0)
        x.set(java.util.Calendar.SECOND, 0)
        x.set(java.util.Calendar.MILLISECOND, 0)
        return x.timeInMillis
    }
    val now = java.util.Calendar.getInstance()
    val tc = java.util.Calendar.getInstance().apply { timeInMillis = t.time }
    val days = ((startOfDay(now) - startOfDay(tc)) / 86400000L).toInt()
    return when {
        days == 0 -> hm
        days == 1 -> "昨天 $hm"
        days in 2..6 -> {
            val week = arrayOf("日", "一", "二", "三", "四", "五", "六")
            "周${week[tc.get(java.util.Calendar.DAY_OF_WEEK) - 1]} $hm"
        }
        tc.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) ->
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(t)
        else -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(t)
    }
}
