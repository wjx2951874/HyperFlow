package com.hyperflowplus

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * 全局状态 + 数据加载（su 读配置 / flow 归档 / 设备信息），本地缓存秒开。
 * hooks（Java 侧）仍使用 Config.java 读写同一份全局配置。
 */
object HFState {
    var ctx: Context? = null

    var cfg by mutableStateOf(JSONObject())
    var flow by mutableStateOf("")
    // V0.6.15.1：仅本地数据源 —— 直接读本地历史文件（local 行），
    // 不经 mergeFlow 合并覆盖（否则小米端实时记录还在时同 id 被 live 行覆盖 → 仅本地过滤为空）
    var localFlow by mutableStateOf("")
    var rootInfo by mutableStateOf("su 不可用")
    var loading by mutableStateOf(false)
    var subtitle by mutableStateOf("澎湃OS 互联通知流转增强")
    var currentConversation by mutableStateOf<Pair<String, List<Array<String>>>?>(null)
    var deviceInfo by mutableStateOf("")          // 设备详情（多行文本）
    var deviceName by mutableStateOf("")          // 机型名（品牌+数字，如 Redmi Note 12 Turbo）
    var miuiOsVersion by mutableStateOf("")        // 澎湃 OS 版本号
    var androidVersion by mutableStateOf("")       // Android 版本
    var kernelVersion by mutableStateOf("")        // 内核版本
    var ksuVersion by mutableStateOf("未检测")      // KernelSU 版本
    var showOnboarding by mutableStateOf(false)    // 引导页是否显示
    var sortVersion by mutableStateOf(0)             // 排序版本号：任何排序变更自增，强制列表/详情重算
    var liveAvailable by mutableStateOf(true)        // 小米端 flow provider 最近一次是否有数据（false=当前显示的是本地历史）
    // ===== 环境检测结果缓存（进入首页先显缓存，后台重查后再更新，避免红→绿跳变） =====
    var envRoot by mutableStateOf(false)
    var envKsu by mutableStateOf(false)
    var envLsp by mutableStateOf(false)
    var envModule by mutableStateOf(false)
    var envScope by mutableStateOf(false)
    var envMilink by mutableStateOf(false)
    var envChecked by mutableStateOf(false)

    private var pollingStarted = false

    /** 环境检测结果缓存：进入首页先显示上次结果（避免闪红再变绿），持久化到 SharedPreferences */
    fun loadEnvCache() {
        val p = prefs ?: return
        envRoot = p.getBoolean("env_root", false)
        envKsu = p.getBoolean("env_ksu", false)
        envLsp = p.getBoolean("env_lsp", false)
        envModule = p.getBoolean("env_module", false)
        envScope = p.getBoolean("env_scope", false)
        envMilink = p.getBoolean("env_milink", false)
        envChecked = true
    }
    fun saveEnvCache(root: Boolean, ksu: Boolean, lsp: Boolean, module: Boolean, scope: Boolean, milink: Boolean) {
        envRoot = root; envKsu = ksu; envLsp = lsp; envModule = module; envScope = scope; envMilink = milink
        val p = prefs ?: return
        try {
            p.edit()
                .putBoolean("env_root", root).putBoolean("env_ksu", ksu)
                .putBoolean("env_lsp", lsp).putBoolean("env_module", module)
                .putBoolean("env_scope", scope).putBoolean("env_milink", milink)
                .apply()
        } catch (t: Throwable) {}
    }

    /** 归档实时刷新：每 5 秒重读 flow provider，有变化立即更新消息页（流转到达即显示）。
     *  App 内消息开启时：每次读取合并去重保存到本机（hf_flow_history.txt），
     *  显示值 = 历史 + 实时 合并结果（互联端记录被清空后历史短信仍可查看）。 */
    fun startFlowPolling() {
        if (pollingStarted) return
        pollingStarted = true
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                Thread {
                    val f = runCatching {
                        RootExec.su("content query --uri content://com.android.mms.flow.provider/messageflow 2>&1 | head -60")
                    }.getOrNull()
                    var display: String? = null
                    val live = f != null && f.isNotBlank()
                    if (live) {
                        // V0.6.15.1：归档开关（archiveApp）开着才存本地；关着不存
                        val hist = if (archiveApp) readHistory() else null
                        if (archiveApp) {
                            persistHistory(f)
                            // 同步"仅本地"数据源（local 行，不合并覆盖）
                            val lf = readHistory()
                            handler.post { if (lf != null && lf != localFlow) localFlow = lf }
                        }
                        display = if (hist != null) mergeFlow(hist, tagSource(f, "live")) else tagSource(f, "live")
                    } else {
                        // 小米端实时记录清空/暂无数据 → 本地历史兜底显示（仅本地模式也能看）
                        display = readHistory()
                    }
                    val finalDisplay = display
                    handler.post {
                        if (finalDisplay != null && finalDisplay != flow) flow = finalDisplay
                        liveAvailable = live
                    }
                    // 顺带刷新 root 状态（用户后授权也能即时反映，设备信息不用重进 App）
                    val uid = runCatching { RootExec.su("id -u") }.getOrNull()?.trim()
                    if (uid == "0" && !rootInfo.contains("可用")) {
                        handler.post { rootInfo = "su 可用" }
                    }
                }.start()
                handler.postDelayed(this, 5000)
            }
        })
    }

    /** 引导标记（App 私有存储，与 root 权限无关：关 root 也只会弹一次） */
    private val prefs: android.content.SharedPreferences?
        get() = ctx?.getSharedPreferences("hf_prefs", Context.MODE_PRIVATE)

    private val cacheFile: java.io.File?
        get() = ctx?.getFileDir("hf_cache.txt")

    /** 本地短信历史文件（App 内消息开启期间累积保存；关闭时可选择保留/清除） */
    private val historyFile: java.io.File?
        get() = ctx?.getFileDir("hf_flow_history.txt")

    // ===== V0.6.15 删除防回写 =====
    /** 已删消息指纹文件（删除后小米端还能实时搜到的也不再保存/显示 → 本地隐藏） */
    private val deletedFile: java.io.File?
        get() = ctx?.getFileDir("hf_deleted.txt")

    private val deletedKeys: MutableSet<String> by lazy {
        val s = LinkedHashSet<String>()
        runCatching {
            deletedFile?.takeIf { it.exists() }?.readText()?.lines()
                ?.forEach { if (it.isNotBlank()) s.add(it) }
        }
        s
    }

    /** 删除版本号：删除操作自增，MessagesScreen remember 依赖它触发重组 */
    var deletedVersion by mutableStateOf(0)
        private set

    /** 指纹：title+body+time（body 截断到第一个逗号，与 flow 行 content_description 解析一致） */
    private fun fingerprint(t: String, b: String, tm: String): String =
        t + "\u0001" + b.substringBefore(",") + "\u0001" + tm

    /** flow 行是否已被删除（防回写过滤） */
    fun isDeletedFlowLine(line: String): Boolean {
        if (deletedKeys.isEmpty() || line.isBlank()) return false
        val t = Regex("content_title=([^,]+)").find(line)?.groupValues?.get(1)?.trim().orEmpty()
        val b = Regex("content_description=([^,]+)").find(line)?.groupValues?.get(1)?.trim().orEmpty()
        val tm = Regex("content_time=([^,]+)").find(line)?.groupValues?.get(1)?.trim().orEmpty()
        return deletedKeys.contains(fingerprint(t, b, tm))
    }

    /** 记录已删指纹（供 deleteFlowRows 调用） */
    private fun recordDeleted(keys: List<Array<String>>) {
        runCatching {
            keys.forEach { r ->
                val t = r.getOrNull(4).orEmpty()
                val b = r.getOrNull(2).orEmpty()
                val tm = r.getOrNull(0).orEmpty()
                deletedKeys.add(fingerprint(t, b, tm))
            }
            deletedFile?.writeText(deletedKeys.joinToString("\n"))
            deletedVersion++
        }
    }

    // ===== 开关状态（与 Config.java 的 key 一致） =====
    // 首次安装全部默认关闭，用户进首页自行开启（V0.4.28 起）
    val forceTransfer: Boolean get() = cfg.optBoolean("force_transfer", false)
    val cloneTransfer: Boolean get() = cfg.optBoolean("clone_transfer", false)
    val smsPersist: Boolean get() = cfg.optBoolean("sms_persist", false)
    val archiveApp: Boolean get() = cfg.optBoolean("archive_app", false)
    val autoUnlock: Boolean get() = cfg.optBoolean("auto_unlock", false)
    val navFloat: Boolean get() = cfg.optBoolean("nav_float", false)         // 主题：悬浮导航栏
    val glassEffect: Boolean get() = cfg.optBoolean("glass_effect", false)   // 主题：液态玻璃
    val debugMode: Boolean get() = cfg.optBoolean("debug_mode", false)       // 调试模式
    // 消息第一层显示模式（V0.6.15 三态）：all=实时与本地合并显示（默认）/ live=仅显示实时 / local=仅显示本地
    val msgMode: String get() = cfg.optString("msg_mode", if (cfg.optBoolean("msg_live_only", false)) "live" else "all")

    fun setMsgMode(mode: String) {
        cfg = JSONObject(cfg.toString()).put("msg_mode", mode)
        saveCfgLater()
        refreshArchive()
    }
    // 列表排序：name_asc/name_desc（发送人名）/time_asc/time_desc（最近接收时间）；默认按发送人名 A→Z
    val archiveSort: String get() = cfg.optString("archive_sort", "time_desc")
    val detailSort: String get() = cfg.optString("detail_sort", "desc")

    fun set(key: String, value: Boolean) {
        // 必须新建 JSONObject 实例：mutableStateOf 按引用比较，同实例 put 不触发重组
        cfg = JSONObject(cfg.toString()).put(key, value)
        saveCfgLater()
    }

    fun setInt(key: String, value: Int) {
        cfg = JSONObject(cfg.toString()).put(key, value)
        saveCfgLater()
    }

    fun setString(key: String, value: String) {
        cfg = JSONObject(cfg.toString()).put(key, value)
        saveCfgLater()
    }

    fun setSort(key: String) {
        val cur = cfg.optString(key, "desc")
        cfg = JSONObject(cfg.toString()).put(key, if (cur == "desc") "asc" else "desc")
        sortVersion++
        saveCfgLater()
        refreshArchive()
    }

    /** 直接设置排序值（列表排序菜单：name_asc/name_desc/time_asc/time_desc） */
    fun setSortValue(key: String, value: String) {
        cfg = JSONObject(cfg.toString()).put(key, value)
        sortVersion++
        saveCfgLater()
        refreshArchive()
    }

    // ===== 引导页 =====
    val firstRunDone: Boolean get() =
        prefs?.getBoolean("first_run_done", cfg.optBoolean("first_run_done", false)) ?: cfg.optBoolean("first_run_done", false)

    fun markFirstRunDone() {
        // 优先写入 App 私有存储（root 无关，保证只弹一次）
        runCatching { prefs?.edit()?.putBoolean("first_run_done", true)?.apply() }
        cfg = JSONObject(cfg.toString()).put("first_run_done", true)
        saveCfgLater()
    }

    fun refreshArchive() {
        if (flow.isNotEmpty()) {
            // 触发 MessagesScreen 重组即可（flow 已是 state）
        }
    }

    // ===== 本地短信历史（App 内消息开启期间累积，互联端读不到也能显示） =====
    /** 合并两段 flow 文本：按 content_notification_ui_id 或整行去重，实时行覆盖历史行 */
    private fun mergeFlow(history: String, realtime: String): String {
        val map = LinkedHashMap<String, String>()
        for (line in (history + "\n" + realtime).lines()) {
            if (line.isBlank()) continue
            var key: String? = null
            for (seg in line.split(", ")) {
                if (seg.startsWith("content_notification_ui_id=")) {
                    val v = seg.substringAfter("=").trim()
                    if (v.isNotEmpty()) key = "id:" + v
                    break
                }
            }
            map[key ?: "line:" + line.hashCode()] = line
        }
        return map.values.joinToString("\n")
    }

    /** 给 flow 行打来源标记：hf_source=live（小米端实时）/ hf_source=local（本地历史） */
    private fun tagSource(raw: String, src: String): String =
        raw.lines().joinToString("\n") { line ->
            if (line.isBlank()) line
            else line.replace(Regex(",?\\s*hf_source=\\w+"), "") + ", hf_source=$src"
        }

    /** 读取本地历史（App 内消息关闭后仍保留的文件） */
    fun readHistory(): String? {
        val f = historyFile ?: return null
        return try {
            if (f.exists()) f.readText() else null
        } catch (t: Throwable) {
            null
        }
    }

    /** 保存实时数据到本地历史（去重累积；历史行统一标 hf_source=local） */
    private fun persistHistory(realtime: String) {
        val f = historyFile ?: return
        try {
            val old = if (f.exists()) f.readText() else ""
            val merged = mergeFlow(old, tagSource(realtime, "local"))
            if (merged.isNotBlank()) {
                f.parentFile?.mkdirs()
                f.writeText(merged)
            }
        } catch (t: Throwable) {
        }
    }

    /** 清除本地历史（关闭 App 内消息时选择"清除记录"） */
    fun clearHistory() {
        try {
            historyFile?.delete()
        } catch (t: Throwable) {
        }
        refreshFlow()
    }

    // ===== 消息删除（长按/多选，v0.5.8 新增） =====
    /** 删除指定消息：从本地历史文件持久移除，同时从当前 flow 显示移除。
     *  @param keys 每条为 flow 行数组 [time, device, body, src, title]（与 parseFlow 一致）。
     *  匹配规则：正文必匹配；标题按原始/【分身】前缀两种写法匹配；时间窗 2 分钟内匹配。
     *  注意：live（小米端实时）行删除后，下一次轮询会从 provider 重新拉回，属预期行为；
     *  本地历史（hf_source=local）行为持久删除。 */
    fun deleteFlowRows(keys: List<Array<String>>) {
        Thread {
            recordDeleted(keys)
            fun parseTs(s: String): Long {
                val fmt1 = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US)
                fmt1.isLenient = false
                val fmt2 = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                fmt2.isLenient = false
                val fmt3 = java.text.SimpleDateFormat("M-d HH:mm", java.util.Locale.US)
                fmt3.isLenient = false
                return runCatching { fmt1.parse(s)?.time ?: 0L }.getOrElse {
                    runCatching { fmt2.parse(s)?.time ?: 0L }.getOrElse {
                        runCatching { fmt3.parse(s)?.time ?: 0L }.getOrDefault(0L)
                    }
                }
            }
            fun matchRow(line: String, r: Array<String>): Boolean {
                if (line.isBlank()) return false
                val body = r.getOrNull(2).orEmpty()
                val title = r.getOrNull(4).orEmpty()
                val time = r.getOrNull(0).orEmpty()
                if (body.isNotEmpty() && !line.contains(body)) return false
                if (title.isNotEmpty()) {
                    val plain = "content_title=" + title
                    val prefixed = "content_title=【分身】" + title
                    if (!line.contains(plain) && !line.contains(prefixed)) return false
                }
                if (time.isNotEmpty()) {
                    val m = Regex("content_time=([^,\\s]+)").find(line)?.groupValues?.get(1)
                    if (m != null) {
                        val d = kotlin.math.abs((parseTs(m) - parseTs(time)))
                        if (d > 120000L) return false
                    }
                }
                return true
            }
            // 1) 本地历史文件持久删除
            val f = historyFile
            if (f != null && f.exists()) {
                try {
                    val newText = f.readText().lines()
                        .filterNot { line -> keys.any { matchRow(line, it) } }
                        .joinToString("\n")
                    f.writeText(newText)
                } catch (t: Throwable) {
                }
            }
            // 2) 当前 flow 显示移除（主线程改 state）
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                flow = flow.lines().filterNot { line -> keys.any { matchRow(line, it) } }
                    .joinToString("\n")
            }
        }.start()
    }

    /** 重读一次实时 flow（清除历史后立即恢复实时显示） */
    fun refreshFlow() {
        Thread {
            val f = runCatching {
                RootExec.su("content query --uri content://com.android.mms.flow.provider/messageflow 2>&1 | head -60")
            }.getOrNull()
            val finalF = if (f != null && f.isNotBlank()) tagSource(f, "live") else null
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (finalF != null && finalF != flow) flow = finalF
                liveAvailable = finalF != null
            }
        }.start()
    }

    /** 一次 su 会话读全部数据 + 写缓存（App 秒开）。
     *  注意：Compose 状态只能在主线程赋值，后台线程只算数据，最后 post 回主线程。 */
    fun loadAll() {
        loading = true
        Thread {
            var cfgText: String? = null
            var flowText: String? = null
            var rootRaw = "su 不可用"
            var devMiui = ""; var devAndroid = ""; var devKernel = ""; var devModel = ""; var devOsCode = ""; var devKsu = "未检测"; var devDisplayId = ""
            try {
                // 先读本地缓存（渲染素材）
                val cache = readCache()
                if (cache != null) {
                    val parts = cache.split("@@CFG|@@FLOW".toRegex())
                    if (parts.size > 1 && parts[1].trim().isNotEmpty()) cfgText = parts[1].trim()
                    if (parts.size > 2) flowText = parts[2]
                }
                // KSU allowlist 兜底
                try { RootExec.exec("ksud", "allowlist", "add", "com.milink.service") } catch (t: Throwable) {}
                try { RootExec.exec("ksud", "allowlist", "add", "com.hyperflowplus") } catch (t: Throwable) {}
                val all = RootExec.su("echo @@CFG; cat " + Config.GLOBAL_CFG + " 2>/dev/null; echo @@FLOW; "
                        + "content query --uri content://com.android.mms.flow.provider/messageflow 2>&1 | head -60; "
                        + "echo @@ROOT; id -u; echo @@DEV; "
                        + "getprop ro.mi.os.version.name; getprop ro.build.version.release; uname -r; "
                        + "getprop ro.product.marketname; ksud -V 2>/dev/null || echo 'none'; "
                        + "getprop ro.mi.os.version.code; getprop ro.build.display.id; getprop ro.miui.ui.version.name; getprop ro.build.version.incremental")
                if (all != null) {
                    val parts = all.split("@@CFG|@@FLOW|@@ROOT|@@DEV".toRegex())
                    if (parts.size > 1) cfgText = parts[1].trim()
                    if (parts.size > 2) flowText = parts[2].trim()
                    if (parts.size > 3 && parts[3].trim() == "0") rootRaw = "su 可用"
                    if (parts.size > 4) {
                        val dev = parts[4].trim().split("\n")
                        if (dev.size > 0) devMiui = dev[0].trim()
                        if (dev.size > 1) devAndroid = dev[1].trim()
                        if (dev.size > 2) devKernel = dev[2].trim()
                        if (dev.size > 3) devModel = dev[3].trim()
                        if (dev.size > 4) devKsu = if (dev[4].trim() == "none") "未安装" else dev[4].trim()
                        if (dev.size > 5) devOsCode = dev[5].trim()
                        if (dev.size > 6) devDisplayId = dev[6].trim()
                        if (dev.size > 7 && dev[7].trim().isNotEmpty() && devDisplayId.isEmpty()) devDisplayId = dev[7].trim()
                        if (dev.size > 8 && dev[8].trim().isNotEmpty() && devDisplayId.isEmpty()) devDisplayId = dev[8].trim()
                    }
                }
            } catch (t: Throwable) {
            }
            val finalCfg = cfgText
            val finalFlow = flowText
            val finalRoot = rootRaw
            val fMiui = devMiui; val fAndroid = devAndroid; val fKernel = devKernel; val fKsu = devKsu
            val finalDev = "机型：${devModel.ifEmpty { android.os.Build.MANUFACTURER.uppercase() + " " + android.os.Build.MODEL }}（${android.os.Build.MODEL}）\n" +
                    "澎湃OS：${if (fMiui.isNotEmpty()) fMiui + " " + devOsCode + (if (devDisplayId.isNotEmpty()) "（" + devDisplayId + "）" else "") else "未知"}\n" +
                    "Android：${fAndroid.ifEmpty { android.os.Build.VERSION.RELEASE }}\n" +
                    "内核：${fKernel.ifEmpty { "未知" }}\n" +
                    "Root：${finalRoot} / KSU ${fKsu}"
            // ===== 秒开：先用缓存快照渲染上次状态，再后台重查覆盖 =====
            val cache = readCache()
            if (cache != null) {
                val cparts = cache.split("@@CFG|@@FLOW|@@DEV".toRegex())
                val cCfg = if (cparts.size > 1 && cparts[1].trim().isNotEmpty()) cparts[1].trim() else null
                val cFlow = if (cparts.size > 2) cparts[2].trim() else null
                val cDev = if (cparts.size > 3) cparts[3].trim() else null
                if (cCfg != null || cFlow != null || cDev != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (cCfg != null) cfg = runCatching { JSONObject(cCfg) }.getOrDefault(JSONObject())
                        if (cFlow != null && cFlow.isNotBlank() && flow.isEmpty()) flow = cFlow
                        if (cDev != null && cDev.isNotBlank()) applyDevSnapshot(cDev)
                        loading = false   // 缓存已渲染，秒开完成（后台刷新完成后再次置 false）
                    }
                }
            }
            // ===== 后台重查（真值） =====
            saveCache(finalCfg, finalFlow, finalDev)   // 文件 IO 留在后台线程
            // V0.6.15.1：归档开关开着才并入/初始化本地历史；关着本地数据源为空
            val archOn = finalCfg != null && runCatching { JSONObject(finalCfg).optBoolean("archive_app", false) }.getOrDefault(false)
            val hist = if (archOn) readHistory() else null
            val mergedFlow = if (finalFlow != null) {
                if (hist != null) mergeFlow(hist, tagSource(finalFlow, "live")) else tagSource(finalFlow, "live")
            } else {
                // 小米端实时记录为空 → 本地历史兜底（仅本地模式也能看）
                hist
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (finalCfg != null) cfg = runCatching { JSONObject(finalCfg) }.getOrDefault(JSONObject())
                if (mergedFlow != null) flow = mergedFlow
                else if (finalFlow != null) flow = finalFlow
                // V0.6.15.1：初始化"仅本地"数据源（local 行）
                val lf = if (archOn) readHistory() else null
                if (lf != null && lf != localFlow) localFlow = lf
                rootInfo = finalRoot
                miuiOsVersion = fMiui
                androidVersion = fAndroid
                kernelVersion = fKernel
                ksuVersion = fKsu
                deviceInfo = finalDev
                deviceName = finalDev.substringAfter("机型：").substringBefore("（").trim()
                subtitle = if (finalRoot.contains("可用")) "澎湃OS 互联通知流转增强" else "root 不可用，仅界面展示"
                loading = false
            }
        }.start()
    }

    // ===== 缓存（秒开） =====
    private fun Context.getFileDir(name: String): java.io.File = java.io.File(filesDir, name)

    private fun saveCache(cfgText: String?, flowText: String?, devSnapshot: String?) {
        val f = cacheFile ?: return
        try {
            f.parentFile?.mkdirs()
            f.writeText("@@CFG\n" + (cfgText ?: "") + "\n@@FLOW\n" + (flowText ?: "") + "\n@@DEV\n" + (devSnapshot ?: ""))
        } catch (t: Throwable) {
        }
    }

    private fun readCache(): String? {
        val f = cacheFile ?: return null
        return try {
            if (f.exists()) f.readText() else null
        } catch (t: Throwable) {
            null
        }
    }

    /** 解析缓存中的设备快照并立即渲染（秒开：启动先显示上次状态，后台重查后再覆盖） */
    private fun applyDevSnapshot(snap: String?) {
        if (snap.isNullOrBlank()) return
        for (l in snap.split("\n")) {
            when {
                l.startsWith("机型：") -> deviceName = l.removePrefix("机型：").substringBefore("（").trim()
                l.startsWith("澎湃OS：") -> miuiOsVersion = l.removePrefix("澎湃OS：").trim()
                l.startsWith("Android：") -> androidVersion = l.removePrefix("Android：").trim()
                l.startsWith("内核：") -> kernelVersion = l.removePrefix("内核：").trim()
                l.startsWith("Root：") -> {
                    val rest = l.removePrefix("Root：").trim()
                    rootInfo = rest.substringBefore("/").trim().ifEmpty { "su 不可用" }
                    val ksu = rest.substringAfter("/", "").trim()
                    if (ksu.isNotEmpty()) ksuVersion = ksu
                }
            }
        }
        deviceInfo = snap
    }

    private fun saveCfgLater() {
        val c = ctx ?: return
        Thread {
            try {
                RootExec.writeJson(c, Config.GLOBAL_CFG, cfg.toString())
            } catch (t: Throwable) {
            }
        }.start()
    }
}
