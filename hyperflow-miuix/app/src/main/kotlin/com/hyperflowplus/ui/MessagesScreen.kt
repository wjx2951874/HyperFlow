package com.hyperflowplus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 消息页：短信 App 风格的会话列表。
 * 每条 = 发送人（加粗） + 最新正文预览 + 右侧时间；点击进入会话详情。
 */
@Composable
fun MessagesScreen(state: HFState, modifier: Modifier = Modifier) {
    val convos = remember(state.flow, state.archiveSort) {
        parseFlow(state.flow, state.archiveSort)
    }
    if (convos.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("暂无流转消息", style = MiuixTheme.textStyles.body1)
            Text(
                "短信/通知流转到本机后在这里归档显示",
                style = MiuixTheme.textStyles.body2,
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
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sender,
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.SemiBold,
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

/**
 * 解析 flow provider 输出。
 * 每行是 ", " 分隔的键值对：content_notification_ui_id=.., content_title=.., content_description=..,
 * content_time=20260930T155344, time_stamp=.., notification_ref=xiaomi15
 * 正文可能含逗号，用"下一个已知字段"做边界。
 */
fun parseFlow(raw: String, sort: String): List<Pair<String, List<Array<String>>>> {
    val desc = sort != "asc"
    val groups = LinkedHashMap<String, MutableList<Array<String>>>()
    for (line in raw.lines()) {
        if (line.isBlank()) continue
        val title = field(line, "content_title")
        val body = field(line, "content_description")
        val time = field(line, "content_time")
        // 机型优先 content_device_name（设备名），回退 notification_ref（服务商/应用名）
        val device = field(line, "content_device_name").ifEmpty { field(line, "notification_ref") }
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

/** 取键值对中某字段值；结束边界 = 下一个已知字段（含 ", " 前缀），避免正文内逗号干扰 */
private fun field(line: String, key: String): String {
    val start = line.indexOf("$key=")
    if (start < 0) return ""
    val vStart = start + key.length + 1
    var end = line.length
    for (next in arrayOf(
        ", content_title=", ", content_description=", ", content_time=",
        ", time_stamp=", ", notification_ref=", ", content_device_name="
    )) {
        val i = line.indexOf(next, vStart)
        if (i >= 0 && i < end) end = i
    }
    return line.substring(vStart, end).trim().removePrefix(",").trim()
}

/**
 * 时间格式：content_time=20260930T155344（紧凑）或 yyyy-MM-dd HH:mm。
 * 层级：今天 HH:mm / 昨天 昨天 HH:mm / 一周内 周X HH:mm / 一年内 MM-dd HH:mm / 跨年 yyyy-MM-dd HH:mm
 */
fun fmtTime(raw: String): String {
    if (raw.isBlank()) return ""
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
    val t: java.util.Date? = if (raw.contains("T")) {
        runCatching {
            java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.CHINA).parse(raw)
        }.getOrNull()
    } else {
        runCatching { sdf.parse(raw) }.getOrNull()
    }
    if (t == null) return raw
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
