package com.hyperflowplus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 妙享直通 · 锁屏密码预设置录入页（对齐 App 内二级页：TopAppBar + 白卡片列表）。
 *
 * 两步流程（外观只改壳，逻辑不变）：
 *  - root 识别到本机锁屏类型 → 直接进「输入」步。
 *  - 识别不到 → 先「选择类型」（RadioButtonPreference 单选），选中后进入「输入」步。
 *  - 输入步两遍确认，底部 primary Button 提交。
 */
@Composable
fun AuthSetupScreen(state: HFState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()

    var stage by remember { mutableStateOf(if (state.hasAuth) "saved" else "detecting") } // detecting=识别中；choose=选类型；input=输入；saved=已保存
    // 选择页不预选（去掉默认 pin）：无已存类型时三个圆圈都空，点哪个都一次进入输入页，避免点已选中项无反应
    var type by remember { mutableStateOf(state.authType) }
    var autoHint by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }          // 0=第一遍 1=确认
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(state.hasAuth) }
    var redetect by remember { mutableStateOf(0) }       // 点"更换"递增，重新触发识别
    var diagText by remember { mutableStateOf("") }      // 检测捕获状态的结果展示（调试用）
    val scope = rememberCoroutineScope()

    // root 自动识别本机锁屏类型；识别到直进输入页，识别不到才落到手选；更换后递增 redetect 重新识别
    val appCtx = LocalContext.current.applicationContext
    LaunchedEffect(redetect) {
        if (state.hasAuth && redetect == 0) return@LaunchedEffect
        // 免预填：先把模块捕获的真实锁屏密码同步入库（有捕获值 → 直接"已保存"，跳过手动录入）
        withContext(Dispatchers.IO) { syncCapturedAuth(state) }
        if (state.hasAuth) {
            type = state.authType
            stage = "saved"
            autoHint = "已自动捕获本机锁屏密码，免手动录入"
            return@LaunchedEffect
        }
        val (detected, raw) = withContext(Dispatchers.IO) { detectLockType(appCtx) }
        if (detected != null) {
            type = detected
            stage = "input"
            autoHint = "已自动识别本机锁屏类型"
        } else {
            stage = "choose"
        }
    }

    // 仅「图案绘制进行中」拦截系统返回（画到一半误退会丢路径）；
    // 选类型 / 数字 / 混合输入 / 识别中 / 已保存态一律放行系统返回
    BackHandler(enabled = !saved && stage == "input" && type == "pattern") { }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
    ) {
        TopAppBar(
            title = "锁屏密码预设置",
            color = MiuixTheme.colorScheme.surface,
            titleColor = MiuixTheme.colorScheme.onSurface,
            navigationIcon = { MiuixBackButton(onClick = onBack) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 12.dp)
        ) {
            // 已保存态：显示当前类型 + 更改 + 清除（首次进入已有凭证，或本次保存成功）
            if (saved) {
                GroupTitle("已保存")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("已保存：${typeName(type.ifBlank { state.authType })}", color = MiuixTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        ArrowPreference(
                            title = "检测捕获状态",
                            summary = if (diagText.isBlank()) "检查模块是否读到本机锁屏密码（调试用）" else diagText,
                            onClick = {
                                diagText = "检测中…"
                                scope.launch { diagText = withContext(Dispatchers.IO) { checkCaptureDiag() } }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            // 更换：清空本地输入、重新走识别流程；旧凭证暂不动，新密码两遍一致成功后才覆盖，失败绝不写入
                            onClick = {
                                saved = false; step = 0; first = ""; second = ""; error = ""; visible = false; autoHint = ""
                                redetect++; stage = "detecting"
                            },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("更换") }
                        Spacer(Modifier.height(8.dp))
                        ArrowPreference(
                            title = "清除已保存密码",
                            summary = "删除本地加密留存的锁屏凭证",
                            onClick = {
                                state.clearAuth(); saved = false; stage = "choose"; step = 0; first = ""; second = ""; error = ""
                            }
                        )
                    }
                }
            } else if (stage == "choose") {
                // 第一步：选择密码类型（仅识别失败时到达）。
                // 本页只是"选一个去录入"、无已存限制态，不用单选圆圈占位，
                // 直接左对齐列表行（ArrowPreference），点击即进输入页。
                GroupTitle("选择密码类型")
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        ArrowPreference(
                            title = "数字密码",
                            summary = "4–16 位纯数字",
                            onClick = {
                                type = "pin"; first = ""; second = ""; step = 0; error = ""; stage = "input"
                            }
                        )
                        ArrowPreference(
                            title = "混合密码",
                            summary = "字母 / 数字 / 符号，6–16 位",
                            onClick = {
                                type = "password"; first = ""; second = ""; step = 0; error = ""; stage = "input"
                            }
                        )
                        ArrowPreference(
                            title = "图案",
                            summary = "3×3 连线，至少连接 4 个点",
                            onClick = {
                                type = "pattern"; first = ""; second = ""; step = 0; error = ""; stage = "input"
                            }
                        )
                    }
                }
            } else if (stage == "input") {
                // 第二步：输入（自动识别或手动选定类型后）
                GroupTitle(if (step == 0) "输入${typeName(type)}" else "再次输入以确认")
                if (autoHint.isNotBlank()) {
                    Text(
                        autoHint,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (type == "pattern") {
                            val cur = if (step == 0) first else second
                            PatternPad(
                                selected = cur,
                                onPath = { s ->
                                    error = ""
                                    if (step == 0) first = s else second = s
                                },
                                onClear = { if (step == 0) first = "" else second = "" }
                            )
                        } else {
                            val isPin = type == "pin"
                            TextField(
                                value = if (step == 0) first else second,
                                onValueChange = { v ->
                                    error = ""
                                    if (step == 0) first = v else second = v
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = if (step == 0) typeName(type) else "确认${typeName(type)}",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password
                                ),
                                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Text(
                                        if (visible) "隐藏" else "显示",
                                        modifier = Modifier
                                            .clickable { visible = !visible }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        if (error.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(error, color = MiuixTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val err = checkRule(type, first, second, step)
                                if (err != null) { error = err; return@Button }
                                if (step == 0) {
                                    step = 1; second = ""; visible = false
                                } else {
                                    // 两遍一致且校验通过才一次性覆盖写盘；校验失败/不一致只报错，旧凭证原样保留
                                    if (type == "pattern") state.saveAuth("pattern", null, second)
                                    else state.saveAuth(type, second, null)
                                    saved = true
                                    stage = "saved"
                                }
                            },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(if (step == 0) "下一步" else "保存")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "切换其他类型",
                            textAlign = TextAlign.Center,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    first = ""; second = ""; step = 0; error = ""
                                    stage = "choose"
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            } else {
                // 识别中：先不露"选择密码类型"页，识别完再决定直进输入或让用户选
                GroupTitle("正在识别本机锁屏类型…")
            }
        }
    }
}

private fun typeName(t: String): String = when (t) {
    "pin" -> "数字密码"
    "password" -> "混合密码"
    "pattern" -> "图案"
    else -> "数字密码"
}

/**
 * root 探测本机锁屏密码类型（返回 Pair<类型?, 原始探测输出?>）。
 *
 * 通道1：`cmd lock_settings get-credential-type` —— Android 11+ 系统服务权威回答，
 *   1=图案 / 2=数字PIN / 3=混合密码 / 0 或 -1=无锁。
 *   注意：小米 HyperOS 会拦截该命令，输出 "User has a lock credential, but old credential
 *   was not provided"（非数字）→ 本通道自动跳过，不会误判。
 * 通道2：`sqlite3 /data/system/locksettings.db` 读 `lockscreen.password_type`
 *   （多数 ROM 未内置 sqlite3，报 not found 时跳过）。
 * 通道3（最可靠兜底）：root 把 locksettings.db 拷到应用缓存，用框架自带的
 *   SQLiteDatabase 直读（不依赖任何系统命令/二进制），quality 映射：
 *   0x20000=复杂数字PIN、0x40000/0x80000/0x90000=字母/字母数字/复杂混合密码；
 *   0x10000 是 SOMETHING（图案）与简单 NUMERIC（数字）共用值、无法区分，放弃避免误判。
 * 三通道都拿不到确定类型 → 返回 null，界面落到手动选择；原始输出打日志便于排查。
 */
private fun detectLockType(ctx: android.content.Context): Pair<String?, String?> {
    // 通道1：系统服务权威回答（不依赖 sqlite3；小米拦截时输出非数字自动跳过）
    val t1 = RootExec.su("cmd lock_settings get-credential-type 2>/dev/null")
    val t1s = t1?.trim().orEmpty()
    val t1v = Regex("-?\\d+").find(t1s)?.value?.toIntOrNull()
    if (t1v != null) {
        val d = when (t1v) {
            1 -> "pattern"
            2 -> "pin"
            3 -> "password"
            else -> null // 0/-1 = 无锁屏密码，无法录入（让用户手动选类型无意义，仍落 choose 由 UI 提示）
        }
        if (d != null) return d to t1
    }
    // 通道2：sqlite3 兜底（部分 ROM 保留 sqlite3 二进制）
    val t2 = RootExec.su("sqlite3 /data/system/locksettings.db \"select value from locksettings where name='lockscreen.password_type';\" 2>/dev/null")
    val v = t2?.trim()?.filter { it.isDigit() }?.toIntOrNull()
    if (v != null) {
        val d = qualityToType(v)
        if (d != null) return d to t2
    }
    // 通道3：root 拷贝 + 框架 SQLiteDatabase 直读（不依赖 sqlite3 命令，最可靠）
    val t3 = detectViaDbCopy(ctx)
    if (t3 != null) return t3 to "db-copy"
    // 诊断：识别失败时把各通道原始输出记录下来，方便真机排查
    android.util.Log.d("HyperFlowPlus",
        "auth type detect fail: cmdLockSettings='${t1s}' sqlite='${t2?.trim()}' dbcopy=null")
    return null to (t1s.ifEmpty { t2?.trim().orEmpty() })
}

/** AOSP lockscreen quality 值 → 类型；0x10000 歧义值放弃判断。 */
private fun qualityToType(v: Int): String? = when {
    v and 0x20000 != 0 -> "pin"      // PASSWORD_QUALITY_NUMERIC_COMPLEX
    v and 0x40000 != 0 -> "password" // PASSWORD_QUALITY_ALPHABETIC
    v and 0x80000 != 0 -> "password" // PASSWORD_QUALITY_ALPHANUMERIC
    v and 0x90000 != 0 -> "password" // PASSWORD_QUALITY_COMPLEX
    else -> null                      // 0 / 0x10000(SOMETHING=NUMERIC 共用) 无法区分
}

/**
 * 免预填：模块在 trustservice（授权盘校验成功）或 system_server（日常解锁校验成功）捕获
 * 真实锁屏凭证，落盘 /data/system/hyperflow_captured（公共文件，system uid 可写、普通 App 不可读），
 * 旧版 trustservice sp 作兜底。这里用 root 读回并加密入库。
 * 始终用最新捕获值覆盖（换密码后新密码验证成功即刷新）；无捕获值时不动现有预存。
 */
internal fun syncCapturedAuth(state: HFState) {
    try {
        // 通道A：公共文件（system_server 日常解锁 / trustservice 授权盘双源写入）
        val pub = RootExec.su("cat /data/system/hyperflow_captured 2>/dev/null")?.trim()
        var type = pub?.let { Regex("\"type\":\"([^\"]*)\"").find(it)?.groupValues?.get(1) }
        var secret = pub?.let { Regex("\"secret\":\"([^\"]*)\"").find(it)?.groupValues?.get(1) }.orEmpty()
        var pattern = pub?.let { Regex("\"pattern\":\"([^\"]*)\"").find(it)?.groupValues?.get(1) }.orEmpty()
        // 通道B：旧版 trustservice 私有 sp 兜底
        if (type.isNullOrEmpty()) {
            val xml = RootExec.su(
                "cat /data/data/com.xiaomi.trustservice/shared_prefs/hyperflow_captured.xml 2>/dev/null")
                ?: return
            type = Regex("hf_cap_type\">([^<]*)<").find(xml)?.groupValues?.get(1) ?: return
            if (type.isEmpty()) return
            secret = Regex("hf_cap_secret\">([^<]*)<").find(xml)?.groupValues?.get(1).orEmpty()
            pattern = Regex("hf_cap_pattern\">([^<]*)<").find(xml)?.groupValues?.get(1).orEmpty()
        }
        // 始终覆盖：捕获值=最近一次验证成功的真实密码，换密码后自动跟随
        state.saveAuth(type, secret, pattern)
        android.util.Log.i("HyperFlowPlus",
            "captured auth synced (overwrite): type=$type len=${secret.length + pattern.length}")
    } catch (t: Throwable) {
        android.util.Log.d("HyperFlowPlus", "syncCapturedAuth failed: $t")
    }
}

/** 检测捕获状态（调试用）：公共文件 / trustservice 兜底 / 当前入库 三处逐一探测。 */
private fun checkCaptureDiag(): String {
    val pub = RootExec.su("cat /data/system/hyperflow_captured 2>/dev/null")?.trim()
    val sp = RootExec.su("cat /data/data/com.xiaomi.trustservice/shared_prefs/hyperflow_captured.xml 2>/dev/null")
    val inCfg = HFState.hasAuth
    val cfgType = HFState.authType

    fun typeOf(json: String?): String? = json?.let {
        Regex("\"type\":\"([^\"]*)\" ").find(it)?.groupValues?.get(1)
    }
    val pubType = typeOf(pub)
    val pubLen = pub?.length ?: 0

    return buildString {
        append(if (pub.isNullOrEmpty()) "公共文件：无捕获" else "公共文件：${pubType ?: "?"}(${pubLen}B)")
        append("\n")
        append(if (sp.isNullOrEmpty()) "兜底sp：无" else "兜底sp：有")
        append("\n")
        append(if (inCfg) "App已入库：${typeName(cfgType)}" else "App未入库")
        append("\n")
        append(
            when {
                !pub.isNullOrEmpty() -> "结论：解锁/授权已捕获成功，连接时可直连"
                !sp.isNullOrEmpty() -> "结论：仅旧通道有值，连接时会尝试填入"
                else -> "结论：未捕获到——请解锁手机并输入一次锁屏密码，再回来点此检测"
            }
        )
    }
}

/** 通道3：root 把 locksettings.db 拷到应用缓存后，用框架 SQLiteDatabase 直读密码类型。 */
private fun detectViaDbCopy(ctx: android.content.Context): String? {
    val candidates = listOf("/data/system/locksettings.db", "/data/system_de/0/locksettings.db")
    for (path in candidates) {
        val dst = java.io.File(ctx.cacheDir, "lsdb_${path.hashCode()}.db")
        // cp 后 chmod 644：文件归 root 所有，必须放开读权限应用进程才能打开
        val r = RootExec.su("cp $path '${dst.absolutePath}' && chmod 644 '${dst.absolutePath}' 2>/dev/null")
        if (r != null && dst.exists() && dst.length() > 0) {
            try {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dst.absolutePath, null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                try {
                    db.rawQuery("select value from locksettings where name='lockscreen.password_type'", null)
                        .use { c ->
                            if (c.moveToFirst()) {
                                val v = c.getString(0)?.trim()?.filter { it.isDigit() }?.toIntOrNull()
                                val d = v?.let { qualityToType(it) }
                                if (d != null) return d
                            }
                        }
                } finally {
                    db.close()
                }
            } catch (t: Throwable) {
                // 该路径打不开，试下一个候选路径
            } finally {
                dst.delete()
            }
        }
    }
    return null
}

private fun checkRule(type: String, first: String, second: String, step: Int): String? {
    val cur = if (step == 0) first else second
    if (type == "pattern") {
        if (cur.length !in 4..9) return "图案至少连接 4 个点"
        if (step == 1 && first != second) return "两次图案不一致，请重新绘制"
        return null
    }
    // 长度下限：混合密码（字母/数字/符号）系统要求至少 6 位，数字 PIN 4 位起
    val minLen = if (type == "password") 6 else 4
    if (cur.length !in minLen..16) return "长度需为 $minLen–16 位"
    if (type == "pin" && !cur.all { it.isDigit() }) return "数字密码只能包含 0–9"
    if (step == 1 && first != second) return "两次输入不一致，请重新输入"
    return null
}

/**
 * 3×3 图案手势盘：界面只显示 9 个空白圆点，用户手指拖动连线，
 * 内部按 MiuiLockPatternView 编号 1 2 3 / 4 5 6 / 7 8 9 把连线顺序编码成数字串回调，
 * 用户全程看不到数字。
 */
@Composable
private fun PatternPad(
    selected: String,
    onPath: (String) -> Unit,
    onClear: () -> Unit
) {
    val primary = MiuixTheme.colorScheme.primary
    val dotIdle = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    var path by remember { mutableStateOf(parsePattern(selected)) }
    var finger by remember { mutableStateOf<Offset?>(null) }

    // 外部清空（清除重画 / 换步）时同步内部点阵
    LaunchedEffect(selected) { path = parsePattern(selected) }

    fun rel(r: Int, c: Int) = Offset((c + 0.5f) / 3f, (r + 0.5f) / 3f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val seq = mutableListOf<Int>()
                        fun hit(p: Offset): Int {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val reach = minOf(w, h) / 3f * 0.6f
                            for (r in 0..2) for (c in 0..2) {
                                val cc = rel(r, c)
                                val dx = p.x - cc.x * w
                                val dy = p.y - cc.y * h
                                if (dx * dx + dy * dy <= reach * reach) return r * 3 + c + 1
                            }
                            return -1
                        }
                        hit(down.position).takeIf { it >= 0 && it !in seq }?.let { seq.add(it) }
                        path = seq.toList()
                        finger = down.position
                        while (true) {
                            val ch = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            finger = ch.position
                            hit(ch.position).takeIf { it >= 0 && it !in seq }?.let { seq.add(it) }
                            path = seq.toList()
                        }
                        finger = null
                        onPath(seq.joinToString(""))
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val pts = path.map { n ->
                    val r = (n - 1) / 3
                    val c = (n - 1) % 3
                    val cc = rel(r, c)
                    Offset(cc.x * w, cc.y * h)
                }
                for (i in 1 until pts.size) {
                    drawLine(primary, pts[i - 1], pts[i], strokeWidth = 10f)
                }
                val f = finger
                if (f != null && pts.isNotEmpty()) {
                    drawLine(primary.copy(alpha = 0.5f), pts.last(), f, strokeWidth = 7f)
                }
                for (r in 0..2) for (c in 0..2) {
                    val n = r * 3 + c + 1
                    val cc = rel(r, c)
                    val center = Offset(cc.x * w, cc.y * h)
                    val chosen = n in path
                    drawCircle(
                        color = if (chosen) primary else dotIdle,
                        radius = if (chosen) 28f else 22f,
                        center = center
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                "清除重画",
                modifier = Modifier.clickable { onClear() },
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
}

private fun parsePattern(s: String): List<Int> =
    s.filter { it.isDigit() }.map { it.digitToInt() }
