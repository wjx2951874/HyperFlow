package com.hyperflowplus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 消息页：短信 App 风格的会话列表。
 * 每条 = 发送人（加粗） + 最新正文预览 + 右侧时间；点击进入会话详情。
 * App 内消息未开启时：大提示 + 「立即开始」按钮（点击弹开启确认窗）。
 */
@Composable
fun MessagesScreen(state: HFState, modifier: Modifier = Modifier) {
    // App 内消息归档关闭：大提示 + 立即开始按钮
    if (!state.archiveApp) {
        var showStartDialog by remember { mutableStateOf(false) }
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "App 内消息未开启",
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 120.dp)
            )
            Text(
                "流转的短信会在这里按发送人显示",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = { showStartDialog = true },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier
                    .padding(top = 24.dp)
                    .width(160.dp)
            ) {
                Text("立即开始")
            }
        }
        ConfirmDialog(
            show = showStartDialog,
            title = "你确定要开启 App 内消息嘛？",
            content = "开启后，其他设备通过小米互联流转到本设备的短信会显示在本 App 的消息页面。开启期间会实时读取短信并保存到本机，历史短信可长久查看。关闭本功能时，可自由选择是否保留已存储在本地的短信记录。",
            onConfirm = { state.set(Config.KEY_ARCHIVE_APP, true); showStartDialog = false },
            onDismiss = { showStartDialog = false }
        )
        return
    }
    val convos = remember(state.flow, state.archiveSort, state.sortVersion) {
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
            val latest = rows.maxByOrNull { timeToEpoch(it[0]) } ?: return@items
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.currentConversation = sender to rows }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 行 1：发送人名称（左，加粗）+ 最近时间｜来自设备（右，灰色小字，短信 App 样式）
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sender,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        fmtTime(latest[0]) +
                            if (latest[1].isNotEmpty()) "｜来自" + latest[1] else "",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                // 行 2：最新正文预览（灰色小字，最多两行）
                Text(
                    latest[2].ifEmpty { "(无正文)" },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

/**
 * 解析 flow provider 输出。
 * 每行是 ", " 分隔的键值对：content_notification_ui_id=.., content_title=.., content_description=..,
 * content_time=20260930T155344, time_stamp=.., notification_ref=xiaomi15
 * 正文可能含逗号，用"下一个已知字段"做边界。
 * 列表排序：name_asc/name_desc（按发送人名称）、time_asc/time_desc（按最近接收时间，兼容 asc/desc）
 */
fun parseFlow(raw: String, sort: String): List<Pair<String, List<Array<String>>>> {
    val groups = LinkedHashMap<String, MutableList<Array<String>>>()
    for (line in raw.lines()) {
        if (line.isBlank()) continue
        val title = field(line, "content_title")
        val body = field(line, "content_description")
        val time = field(line, "content_time")
        // 机型：只取 provider 的 content_device_name 原样显示（英文机型名），
        // 截断到第一个逗号并清除不可见/替换字符，避免解析把后续字段拼进来
        val device = field(line, "content_device_name")
            .substringBefore(",")
            .replace(Regex("[\\uFFFD\\u0000-\\u001F]"), "")
            .trim()
        if (title.isEmpty() && body.isEmpty()) continue
        // 按发送人分组：同一服务商/联系人的消息（即使来自不同设备）合并成一个会话；
        // 标题为空时回退到设备名，避免全部挤进"未知"分组
        val key = title.ifEmpty { device.ifEmpty { "未知" } }
        groups.getOrPut(key) { mutableListOf() }.add(arrayOf(time, device, body))
    }
    val list = groups.map { it.key to it.value }.toMutableList()
    val timeDesc = sort == "time_desc" || sort == "desc"
    val nameDesc = sort == "name_desc"
    val byName = sort.startsWith("name")
    for ((_, rows) in list) {
        // 时间排序：统一解析成时间戳再比较（content_time 有 yyyyMMddTHHmmss 和 yyyy-MM-dd HH:mm 两种格式，字符串比较会错乱）
        rows.sortWith { a, b ->
            if (timeDesc) (timeToEpoch(b[0]) - timeToEpoch(a[0])).toInt()
            else (timeToEpoch(a[0]) - timeToEpoch(b[0])).toInt()
        }
    }
    list.sortWith { a, b ->
        if (byName) {
            if (nameDesc) b.first.compareTo(a.first) else a.first.compareTo(b.first)
        } else {
            val ta = a.second.firstOrNull()?.get(0) ?: ""
            val tb = b.second.firstOrNull()?.get(0) ?: ""
            if (timeDesc) (timeToEpoch(tb) - timeToEpoch(ta)).toInt()
            else (timeToEpoch(ta) - timeToEpoch(tb)).toInt()
        }
    }
    return list
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
