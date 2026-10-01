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
    var rootInfo by mutableStateOf("su 不可用")
    var loading by mutableStateOf(false)
    var subtitle by mutableStateOf("澎湃OS 互联通知流转增强")
    var currentConversation by mutableStateOf<Pair<String, List<Array<String>>>?>(null)
    var deviceInfo by mutableStateOf("")          // 设备详情（多行文本）
    var miuiOsVersion by mutableStateOf("")        // 澎湃 OS 版本号
    var androidVersion by mutableStateOf("")       // Android 版本
    var kernelVersion by mutableStateOf("")        // 内核版本
    var ksuVersion by mutableStateOf("未检测")      // KernelSU 版本
    var showOnboarding by mutableStateOf(false)    // 引导页是否显示
    var sortVersion by mutableStateOf(0)             // 排序版本号：任何排序变更自增，强制列表/详情重算

    private var pollingStarted = false

    /** 归档实时刷新：每 5 秒重读 flow provider，有变化立即更新消息页（流转到达即显示） */
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
                    if (f != null && f.isNotBlank()) {
                        handler.post {
                            if (f != flow) flow = f
                        }
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

    // ===== 开关状态（与 Config.java 的 key 一致） =====
    val forceTransfer: Boolean get() = cfg.optBoolean("force_transfer", true)
    val cloneTransfer: Boolean get() = cfg.optBoolean("clone_transfer", true)
    val smsPersist: Boolean get() = cfg.optBoolean("sms_persist", false)
    val autoUnlock: Boolean get() = cfg.optBoolean("auto_unlock", false)
    val glassOn: Boolean get() = cfg.optBoolean("glass_effect", false)
    /** 玻璃模糊强度：8=柔和 16=标准 24=强烈 */
    val glassBlur: Int get() = cfg.optInt("glass_blur", 16)
    // 列表排序：name_asc/name_desc（发送人名）/time_asc/time_desc（最近接收时间）；默认按发送人名 A→Z
    val archiveSort: String get() = cfg.optString("archive_sort", "name_asc")
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

    /** 一次 su 会话读全部数据 + 写缓存（App 秒开）。
     *  注意：Compose 状态只能在主线程赋值，后台线程只算数据，最后 post 回主线程。 */
    fun loadAll() {
        loading = true
        Thread {
            var cfgText: String? = null
            var flowText: String? = null
            var rootRaw = "su 不可用"
            var devMiui = ""; var devAndroid = ""; var devKernel = ""; var devKsu = "未检测"
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
                        + "ksud -V 2>/dev/null || echo 'none'")
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
                        if (dev.size > 3) devKsu = if (dev[3].trim() == "none") "未安装" else dev[3].trim()
                    }
                }
            } catch (t: Throwable) {
            }
            val finalCfg = cfgText
            val finalFlow = flowText
            val finalRoot = rootRaw
            val fMiui = devMiui; val fAndroid = devAndroid; val fKernel = devKernel; val fKsu = devKsu
            saveCache(finalCfg, finalFlow)   // 文件 IO 留在后台线程
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                cfg = runCatching { JSONObject(finalCfg ?: "") }.getOrDefault(JSONObject())
                if (finalFlow != null) flow = finalFlow
                rootInfo = finalRoot
                miuiOsVersion = fMiui
                androidVersion = fAndroid
                kernelVersion = fKernel
                ksuVersion = fKsu
                deviceInfo = "机型：${android.os.Build.MODEL}\n" +
                        "澎湃OS：${fMiui.ifEmpty { "未知" }}\n" +
                        "Android：${fAndroid.ifEmpty { android.os.Build.VERSION.RELEASE }}\n" +
                        "内核：${fKernel.ifEmpty { "未知" }}\n" +
                        "Root：${finalRoot} / KSU ${fKsu}"
                subtitle = if (finalRoot.contains("可用")) "澎湃OS 互联通知流转增强" else "root 不可用，仅界面展示"
                loading = false
            }
        }.start()
    }

    // ===== 缓存（秒开） =====
    private fun Context.getFileDir(name: String): java.io.File = java.io.File(filesDir, name)

    private fun saveCache(cfgText: String?, flowText: String?) {
        val f = cacheFile ?: return
        try {
            f.parentFile?.mkdirs()
            f.writeText("@@CFG\n" + (cfgText ?: "") + "\n@@FLOW\n" + (flowText ?: ""))
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
