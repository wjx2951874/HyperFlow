package com.hyperflowplus

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * LSPosed 框架连接实时快照（参考 HyperModifier 的 ModuleFrameworkState）。
 *
 * 来源 = libxposed service 绑定（io.github.libxposed:service:102.0.0）：
 * App 通过 XposedServiceHelper 绑定 LSPosed daemon 的 Binder，模块被启用时立即回调
 * onServiceBind，直接拿到框架版本与【该模块实时配置的作用域列表】（service.scope，
 * 框架从 modules_config.db 读取，非文件探测）。断开时 onServiceDied 归零。
 *
 * 事件驱动 → UI 秒级更新，且不依赖 root / ps / maps / 文件探针，准确率由框架保证。
 */
object ModuleFrameworkState {
    /** libxposed service 连接要求的 LSPosed API 版本（用户设备 LSPosed 2.2.0 = API 102） */
    const val MIN_SUPPORTED_API = 102

    /** 推荐作用域：系统框架（android=经典 / system=新版 Vector 双标识）与小米互联 */
    val REQUIRED_SCOPES = setOf("com.milink.service")

    data class Snapshot(
        val connected: Boolean = false,
        val apiVersion: Int = 0,
        val frameworkName: String = "",
        val frameworkVersion: String = "",
        val frameworkVersionCode: Long = 0L,
        val scope: Set<String> = emptySet(),
    ) {
        /** 模块在该框架下已启用且连接正常 */
        val active: Boolean get() = connected && apiVersion >= MIN_SUPPORTED_API

        /** 小米互联作用域已勾选（LSPosed 实时配置） */
        val milinkScoped: Boolean
            get() = active && scope.any { it == "com.milink.service" }

        /** 系统框架作用域已勾选（android 经典 / system Vector 任一） */
        val systemScoped: Boolean
            get() = active && scope.any { it == "android" || it == "system" }

        /** 推荐作用域全部就绪 = 互联 + 系统框架都勾上（这正是检测最关心的判定） */
        val scopeReady: Boolean
            get() = milinkScoped && systemScoped
    }

    private val mutableSnapshot = mutableStateOf(Snapshot())
    val snapshot: State<Snapshot> = mutableSnapshot

    fun onServiceBound(
        apiVersion: Int,
        frameworkName: String,
        frameworkVersion: String,
        frameworkVersionCode: Long,
        scope: List<String>,
    ) {
        mutableSnapshot.value = Snapshot(
            connected = true,
            apiVersion = apiVersion,
            frameworkName = frameworkName,
            frameworkVersion = frameworkVersion,
            frameworkVersionCode = frameworkVersionCode,
            scope = scope.toSet(),
        )
    }

    fun onServiceDied() {
        mutableSnapshot.value = Snapshot()
    }

    /**
     * V0.6.15："重新检测"实时补刷作用域（等价退出重进效果）。
     * service 绑定只发生在 App 启动时，作用域列表是绑定瞬间快照；
     * 用户在 LSPosed 里勾选后不重启 App，旧快照不更新 → 首页三项不变。
     * 此处用 shell 实时读作用域文件（路径/格式已在本设备验证：
     * /data/adb/lspd/config/scope/com.hyperflowplus，每行一个包名），
     * 仅刷新 scope，connected/apiVersion 仍由 service 事件保证（模块真禁用 → onServiceDied 归零）。
     */
    fun refreshScopeFromShell(scopeLines: List<String>) {
        val cur = mutableSnapshot.value
        if (!cur.connected) return
        val scopes = scopeLines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        if (scopes.isNotEmpty() && scopes != cur.scope) {
            mutableSnapshot.value = cur.copy(scope = scopes)
        }
    }
}
