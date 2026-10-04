package com.hyperflowplus.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 消息页：短信 App 风格的会话列表。
 * 每条 = 发送人（加粗） + 最新正文预览 + 右侧时间；点击进入会话详情。
 * App 内消息未开启时：大提示 + 「立即开始」按钮（点击弹开启确认窗）。
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val convos = remember(state.flow, state.localFlow, state.archiveSort, state.sortVersion, state.msgMode, state.deletedVersion) {
        // V0.6.15 删除防回写：已删消息（小米端还能实时搜到的）过滤掉 → 本地隐藏不再出现
        val raw = state.flow.lines()
            .filterNot { line -> state.isDeletedFlowLine(line) }
            .joinToString("\n")
        val parsed = parseFlow(raw, state.archiveSort)
        when (state.msgMode) {
            // 仅显示实时：本地存档隐藏（不删除）
            "live" -> parsed.map { (s, rows) -> s to rows.filter { it.getOrNull(3) != "local" } }
                .filter { it.second.isNotEmpty() }
            // V0.6.15.1：仅显示本地 → 直接解析本地历史文件（localFlow），
            // 不再从合并后的 flow 过滤（否则小米端实时记录还在时同 id 被 live 行覆盖 → 显示为空）
            "local" -> {
                val lRaw = state.localFlow.lines()
                    .filterNot { line -> state.isDeletedFlowLine(line) }
                    .joinToString("\n")
                parseFlow(lRaw, state.archiveSort)
                    .filter { it.second.isNotEmpty() }
            }
            // 默认：实时与本地合并显示
            else -> parsed
        }
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
    // 会话删除确认（长按会话）
    var delTarget by remember { mutableStateOf<Pair<String, List<Array<String>>>?>(null) }
    // v0.5.15：会话多选模式（长按进入，学小米短信 ActionMode：勾选 + 底部操作栏）
    var selMode by remember { mutableStateOf(false) }
    var selConvos by remember { mutableStateOf(setOf<String>()) }
    var batchDel by remember { mutableStateOf(false) }
    // 小米端读不到数据：当前显示的是本地保存的历史记录（避免误以为小米互联还有数据）
    val localOnly = convos.isNotEmpty() && !state.liveAvailable
    // v0.5.12：滚动 → 全局 tick（驱动玻璃 backdrop 重录）
    val mList = rememberLazyListState()
    LaunchedEffect(mList) {
        androidx.compose.runtime.snapshotFlow { mList.firstVisibleItemIndex to mList.firstVisibleItemScrollOffset }
            .collect { MainHolder.scrollTick++ }
    }
    // V0.6.16.6：KSU 同款全屏搜索 —— 输入变化时过滤，结果跨组件共享（MainHolder）供 MainActivity 覆盖层渲染
    LaunchedEffect(MainHolder.searchStatus.searchText, convos) {
        val q = MainHolder.searchStatus.searchText.trim()
        val st = MainHolder.searchStatus
        if (q.isEmpty()) {
            // 展开即显示完整会话列表（KSU：默认全量，输入即过滤）
            MainHolder.searchConvs = convos
            MainHolder.searchMsgs = emptyList()
            if (st.resultStatus != SearchStatus.ResultStatus.DEFAULT) {
                MainHolder.searchStatus = st.copy(resultStatus = SearchStatus.ResultStatus.DEFAULT)
            }
        } else {
            val convHits = convos.filter { (s, rows) ->
                s.contains(q, ignoreCase = true) ||
                    rows.any { it.getOrElse(2) { "" }.contains(q, ignoreCase = true) }
            }
            val msgHits = convos.flatMap { (s, rows) ->
                rows.filter { it.getOrElse(2) { "" }.contains(q, ignoreCase = true) }.map { s to it }
            }
            MainHolder.searchConvs = convHits
            MainHolder.searchMsgs = msgHits
            MainHolder.searchStatus = st.copy(resultStatus = SearchStatus.ResultStatus.SHOW)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = mList,
        // v0.5.12：悬浮胶囊避让 —— 列表滚到底最后一行停在胶囊上沿（不遮挡）
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = if (MainHolder.bottomPad == androidx.compose.ui.unit.Dp.Unspecified) 0.dp else MainHolder.bottomPad
        )
    ) {
        // V0.6.16.6：常驻搜索栏（KSU SearchBarFake）——detectTapGestures 点击进入全屏搜索页
        //（KSU 原版做法：不用 clickable，Miuix InputField disabled 态会消费点击事件）
        item(key = "searchFake") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            // 展开即预填全量会话（LaunchedEffect 只在输入变化时触发过滤，
                            // 展开瞬间不触发 → 先放全量，DEFAULT 分支直接渲染完整列表）
                            MainHolder.searchConvs = convos
                            MainHolder.searchStatus = MainHolder.searchStatus.copy(
                                current = SearchStatus.Status.EXPANDING
                            )
                        }
                    }
            ) {
                SearchBarFake("搜索消息")
            }
        }
        // v0.5.15：多选操作栏（长按会话进入，小米短信 ActionMode 同款：已选 N 个会话 + 删除 + 取消）
        if (selMode) {
            item(key = "actionMode") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已选 ${selConvos.size} 个会话",
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = selConvos.isNotEmpty(),
                                onClick = { if (selConvos.isNotEmpty()) batchDel = true }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MiuixTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "删除",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                    TextButton(
                        text = "取消",
                        onClick = {
                            selMode = false
                            selConvos = emptySet()
                        }
                    )
                }
            }
        }
        // V0.6.16.6：搜索功能整体移除（用户决策），列表常驻完整显示
        if (localOnly) {
            item(key = "localOnly") {
                Text(
                    "小米端暂无可读数据，以下为本地保存的历史记录",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.9f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        // V0.6.16.6：搜索功能整体移除 —— 直接渲染完整会话列表（convo 早退已处理空态）
        items(convos, key = { it.first }) { (sender, rows) ->
            ConversationRow(
                state, sender, rows,
                isSel = selMode && selConvos.contains(sender),
                selectionMode = selMode,
                onOpen = {
                    if (selMode) {
                        selConvos = if (selConvos.contains(sender)) selConvos - sender else selConvos + sender
                        if (selConvos.isEmpty()) selMode = false
                    } else {
                        state.currentConversation = sender to rows
                    }
                },
                onLongClick = {
                    // V0.6.15：删除（长按多选）仅"仅显示本地"模式可用
                    if (state.msgMode == "local") {
                        selMode = true
                        selConvos = selConvos + sender
                    }
                },
                onDelete = { delTarget = sender to rows }
            )
        }
    }
    // 会话删除确认（长按会话 → 删除该会话本地存档；小米端实时消息不允许删除）
    delTarget?.let { (sender, rows) ->
        val localRows = rows.filter { it.getOrNull(3) == "local" }
        val liveCount = rows.size - localRows.size
        if (localRows.isEmpty()) {
            // 全是小米端实时消息：数据源在小米端，删除无意义 → 仅提示，不弹删除
            Toast.makeText(
                androidx.compose.ui.platform.LocalContext.current,
                "小米端消息无法删除，仅支持删除本地存档",
                Toast.LENGTH_SHORT
            ).show()
            delTarget = null
        } else {
            ConfirmDialog(
                show = true,
                
                title = "删除",
                content = buildString {
                    append("确定要删除与「$sender」的 ${localRows.size} 条信息吗？")
                    if (liveCount > 0) append("\n小米端实时消息（$liveCount 条）无法删除。")
                },
                confirmText = "删除",
                confirmDanger = true,
                onConfirm = {
                    state.deleteFlowRows(localRows)
                    delTarget = null
                },
                onDismiss = { delTarget = null }
            )
        }
    }
    // v0.5.15：会话多选批量删除确认
    if (batchDel) {
        val selRows = convos.filter { selConvos.contains(it.first) }
        val selLocal = selRows.flatMap { it.second }.filter { it.getOrNull(3) == "local" }
        val selLive = selRows.map { it.second }.flatten().size - selLocal.size
        if (selLocal.isEmpty()) {
            Toast.makeText(
                androidx.compose.ui.platform.LocalContext.current,
                "小米端消息无法删除，仅支持删除本地存档",
                Toast.LENGTH_SHORT
            ).show()
            batchDel = false
            selMode = false
            selConvos = emptySet()
        } else {
            ConfirmDialog(
                show = true,
                
                title = "删除",
                content = buildString {
                    append("确定要删除选中的 ${selLocal.size} 条信息吗？")
                    if (selLive > 0) append("\n小米端实时消息（$selLive 条）无法删除。")
                },
                confirmText = "删除",
                confirmDanger = true,
                onConfirm = {
                    state.deleteFlowRows(selLocal)
                    batchDel = false
                    selMode = false
                    selConvos = emptySet()
                },
                onDismiss = { batchDel = false }
            )
        }
    }
}

/** 会话行（短信 conversation_item 样式：发送人 17sp 加粗 + 时间｜设备 + 正文预览）
 *  v0.5.15：长按进入会话多选（学小米短信）；多选模式下点击切换选中、选中行描边高亮 */
@Composable
private fun ConversationRow(
    state: HFState,
    sender: String,
    rows: List<Array<String>>,
    isSel: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val latest = rows.maxByOrNull { timeToEpoch(it[0]) } ?: return
    val devTag = if (latest[1].isNotEmpty()) "｜来自" + latest[1] else ""
    val localTag = if (latest.getOrNull(3) == "local") "（本地）" else ""
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSel) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 行 1：发送人名称（左，17sp 加粗）+ 最近时间｜来自设备（右，灰色小字，短信 App 样式）
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
                fmtTime(latest[0]) + devTag + localTag,
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
        // V0.6.16.4：去掉小米验证码聚合标记前缀（"[2条]...xxx"），flow 表原文保留不覆盖
        val body = HFState.sanitizeFlowBody(field(line, "content_description"))
        val time = field(line, "content_time")
        // 机型：只取 provider 的 content_device_name 原样显示（英文机型名），
        // 截断到第一个逗号并清除不可见/替换字符，避免解析把后续字段拼进来
        val device = field(line, "content_device_name")
            .substringBefore(",")
            .replace(Regex("[\\uFFFD\\u0000-\\u001F]"), "")
            .trim()
        // 来源标记：hf_source=live（小米端实时）/ local（本地历史），无标记默认小米端
        val src = field(line, "hf_source").ifEmpty { "live" }
        if (title.isEmpty() && body.isEmpty()) continue
        // 按发送人分组：同一服务商/联系人的消息（即使来自不同设备）合并成一个会话；
        // 标题为空时回退到设备名，避免全部挤进"未知"分组
        // 分组 key：去掉【分身】前缀再分组 —— 带前缀与不带前缀的同一分身消息
        // 必须进同一会话，否则跨会话不去重会显示两条
        val key = title.removePrefix("【分身】").ifEmpty { device.ifEmpty { "未知" } }
        groups.getOrPut(key) { mutableListOf() }.add(arrayOf(time, device, body, src, title))
    }
    // 去重：本地归档与小米端（云端）同一条短信重复显示 → 云端优先，本地隐藏；
    // 同会话内以「时间窗+正文」为唯一键（2 分钟窗口：本地归档与小米流转的时间戳
    // 可能相差几十秒/几分钟，原 1 分钟键会导致同一消息不去重而重复显示）。
    // 先收集全部 live 键再过滤 local，顺序无关（原实现 local 先出现、live 后到时两者都会保留）。
    for ((_, rows) in groups) {
        fun keyOf(r: Array<String>): String =
            (timeToEpoch(r[0]) / 120000).toString() + "|" + r[2]
        val liveKeys = HashSet<String>()
        val prefer = HashSet<String>()
        for (r in rows) {
            if (r.getOrNull(3) != "local") {
                liveKeys.add(keyOf(r))
                if (r.getOrNull(4)?.startsWith("【分身】") == true) prefer.add(keyOf(r))
            }
        }
        rows.removeAll { r ->
            if (r.getOrNull(3) == "local") {
                // 本地遇小米端同内容 → 隐藏本地（不加（本地）括号）；小米端缺失时本地保留并带标记
                liveKeys.contains(keyOf(r))
            } else {
                // live 有带【分身】前缀版本时隐藏不带前缀的
                val prefixed = r.getOrNull(4)?.startsWith("【分身】") == true
                prefer.contains(keyOf(r)) && !prefixed
            }
        }
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
        ", time_stamp=", ", notification_ref=", ", content_device_name=", ", hf_source="
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

// ===== V0.6.16.6：KSU 同款搜索结果（分组 + 匹配高亮），由 MainActivity 全屏覆盖层调用 =====

/** 搜索结果正文高亮：命中的关键词段标主色加粗（短信搜索同款视觉） */
internal fun highlightText(
    text: String,
    q: String,
    primaryColor: androidx.compose.ui.graphics.Color
): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    if (q.isBlank()) {
        builder.append(text)
        return builder.toAnnotatedString()
    }
    var start = 0
    while (true) {
        val idx = text.indexOf(q, start, ignoreCase = true)
        if (idx < 0) {
            builder.append(text.substring(start))
            break
        }
        builder.append(text.substring(start, idx))
        // 命中段：主色加粗高亮（AnnotatedString 直接带 spanStyle 构造，无扩展依赖）
        builder.append(
            androidx.compose.ui.text.AnnotatedString(
                text = text.substring(idx, idx + q.length),
                spanStyle = androidx.compose.ui.text.SpanStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold
                )
            )
        )
        start = idx + q.length
    }
    return builder.toAnnotatedString()
}

/** 搜索分组标题（KSU 分组头同款：灰色小字） */
@Composable
private fun SearchGroupHeader(title: String) {
    Text(
        title,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
    )
}

/** 搜索结果会话行（KSU GroupItem 同款：左侧命中色条 + 发送人 + 最新正文预览） */
@Composable
private fun SearchConvRow(
    sender: String,
    rows: List<Array<String>>,
    matched: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 命中标注色条：发送人命中 → 主色（KSU matched），仅正文命中 → 浅主色
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .width(6.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (matched) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 16.dp)
        ) {
            Text(
                sender,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val last = rows.lastOrNull()
            if (last != null) {
                Text(
                    last.getOrElse(2) { "" }.ifEmpty { "(无正文)" },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 搜索结果消息行（KSU 搜索 item 同款：发送人 + 圆角正文块 + 时间，命中段高亮） */
@Composable
private fun SearchMsgRow(
    sender: String,
    row: Array<String>,
    q: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Text(
            sender,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                highlightText(row.getOrElse(2) { "" }.ifEmpty { "(无正文)" }, q, MiuixTheme.colorScheme.primary),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                fmtTime(row.getOrElse(0) { "" }),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

/** 全屏搜索结果：会话分组 + 信息分组（KSU 搜索页同款），空结果提示 */
@Composable
fun MessageSearchResults(state: HFState, modifier: Modifier = Modifier) {
    val convHits = MainHolder.searchConvs
    val msgHits = MainHolder.searchMsgs
    val q = MainHolder.searchStatus.searchText.trim()
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (convHits.isEmpty() && msgHits.isEmpty()) {
            item(key = "noMatch") {
                Text(
                    "没找到短信",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            if (convHits.isNotEmpty()) {
                // q 为空 = 默认全量列表（KSU 展开态），不显示"会话"分组标题
                if (q.isNotEmpty()) item(key = "grpHdrConv") { SearchGroupHeader("会话") }
                items(convHits, key = { "sc_" + it.first }) { (sender, rows) ->
                    val matched = sender.contains(q, ignoreCase = true)
                    SearchConvRow(
                        sender = sender,
                        rows = rows,
                        matched = matched,
                        onClick = {
                            // V0.6.16.9：先收起搜索页再进会话 —— 否则会话详情被覆盖层盖住看不见
                            MainHolder.searchStatus = MainHolder.searchStatus.copy(
                                searchText = "",
                                current = SearchStatus.Status.COLLAPSING
                            )
                            state.currentConversation = sender to rows
                        }
                    )
                }
            }
            if (msgHits.isNotEmpty()) {
                item(key = "grpHdrMsg") { SearchGroupHeader("信息") }
                items(msgHits.size, key = { "sm_" + it }) { idx ->
                    val (sender, row) = msgHits[idx]
                    SearchMsgRow(
                        sender = sender,
                        row = row,
                        q = q,
                        onClick = {
                            val rows = convHits.firstOrNull { it.first == sender }?.second ?: listOf(row)
                            // V0.6.16.9：先收起搜索页再进会话（同会话行点击）
                            MainHolder.searchStatus = MainHolder.searchStatus.copy(
                                searchText = "",
                                current = SearchStatus.Status.COLLAPSING
                            )
                            state.currentConversation = sender to rows
                        }
                    )
                }
            }
        }
    }
}
