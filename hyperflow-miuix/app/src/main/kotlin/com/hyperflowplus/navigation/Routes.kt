package com.hyperflowplus.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 1.1.0 导航栈路由（00 规范 §3.1）：key 必须 @Serializable —— miuix-nav 返回栈
 * 经 kotlinx-serialization JSON 持久化保存/恢复，不满足会运行时序列化失败。
 * 二级页（日志/许可/主题）统一走 NavDisplay 返回栈，修复「二级页返回直接回桌面」。
 */
@Serializable
sealed interface Route : NavKey {
    /** 主页：4 Tab 的 HorizontalPager 宿主（含消息详情/环境引导覆盖层） */
    @Serializable
    data object Main : Route

    /** 运行日志列表（设置页入口） */
    @Serializable
    data object Logs : Route

    /** 开源许可（关于页入口） */
    @Serializable
    data object Licenses : Route

    /** 主题与显示（设置页入口） */
    @Serializable
    data object Theme : Route

    /** 妙享直通 · 锁屏密码预设置录入 */
    @Serializable
    data object AuthSetup : Route
}
