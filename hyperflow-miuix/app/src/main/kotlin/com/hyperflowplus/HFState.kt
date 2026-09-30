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

    private val cacheFile: java.io.File?
        get() = ctx?.getFileDir("hf_cache.txt")

    // ===== 开关状态（与 Config.java 的 key 一致） =====
    val forceTransfer: Boolean get() = cfg.optBoolean("force_transfer", true)
    val cloneTransfer: Boolean get() = cfg.optBoolean("clone_transfer", true)
    val smsPersist: Boolean get() = cfg.optBoolean("sms_persist", true)
    val autoUnlock: Boolean get() = cfg.optBoolean("auto_unlock", false)
    val glassOn: Boolean get() = cfg.optBoolean("glass_effect", false)
    val archiveSort: String get() = cfg.optString("archive_sort", "desc")
    val detailSort: String get() = cfg.optString("detail_sort", "desc")

    fun set(key: String, value: Boolean) {
        cfg = cfg.let { it.put(key, value); it }
        saveCfgLater()
    }

    fun setSort(key: String) {
        val cur = cfg.optString(key, "desc")
        cfg = cfg.let { it.put(key, if (cur == "desc") "asc" else "desc"); it }
        saveCfgLater()
        refreshArchive()
    }

    fun refreshArchive() {
        if (flow.isNotEmpty()) {
            // 触发 MessagesScreen 重组即可（flow 已是 state）
        }
    }

    /** 一次 su 会话读全部数据 + 写缓存（App 秒开） */
    fun loadAll() {
        loading = true
        Thread {
            // 先渲染本地缓存
            val cache = readCache()
            if (cache != null) {
                val parts = cache.split("@@CFG|@@FLOW".toRegex())
                if (parts.size > 1 && parts[1].trim().isNotEmpty()) {
                    cfg = runCatching { JSONObject(parts[1].trim()) }.getOrDefault(JSONObject())
                }
                if (parts.size > 2) {
                    flow = parts[2]
                }
            }
            // KSU allowlist 兜底
            try {
                RootExec.exec("ksud", "allowlist", "add", "com.milink.service")
                RootExec.exec("ksud", "allowlist", "add", "com.hyperflowplus")
            } catch (t: Throwable) {
            }
            val all = RootExec.su("echo @@CFG; cat " + Config.GLOBAL_CFG + " 2>/dev/null; echo @@FLOW; "
                    + "content query --uri content://com.android.mms.flow.provider/messageflow 2>&1 | head -60; "
                    + "echo @@ROOT; id -u")
            var cfgRaw: String? = null
            var flowRaw: String? = null
            var rootRaw = "su 不可用"
            if (all != null) {
                val parts = all.split("@@CFG|@@FLOW|@@ROOT".toRegex())
                if (parts.size > 1) cfgRaw = parts[1].trim()
                if (parts.size > 2) flowRaw = parts[2].trim()
                if (parts.size > 3 && parts[3].trim() == "0") rootRaw = "su 可用"
            }
            cfg = runCatching { JSONObject(cfgRaw ?: "") }.getOrDefault(JSONObject())
            if (flowRaw != null) flow = flowRaw
            rootInfo = rootRaw
            subtitle = if (rootInfo.contains("可用")) "澎湃OS 互联通知流转增强" else "root 不可用，仅界面展示"
            loading = false
            saveCache(cfgRaw, flowRaw)
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
