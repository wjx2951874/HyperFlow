package com.hyperflowplus.hooks;

import com.hyperflowplus.MiflowLog;

/**
 * 来电在线接听（亮屏 + 锁屏都走全屏接听，而不是通知卡片）。
 *
 * v0.5.15.4 改回原版逻辑：本类不再装任何 hook。
 *
 * 原实现（v0.4.27 起）叠了两层 hack：
 *   ② buildPlainMessage 前给来电通知打 broadcast 标、③ isVoipSupported 强制 true，
 *   配合 HookForceTransfer 对来电通知短路 —— 结果亮屏时广播链路无广播、
 *   通知链路又被短路，来电完全不流转（用户实测）。
 *
 * 用户确认方案：milink 原生分流不碰（OS4 设备走 voip 全屏接听、
 * OS3 不支持设备降级为通知卡片），只由 HookForceTransfer 做"模拟锁屏"
 * （isDeviceSupported=true + isKeyguardLocked=true + isInteractive=false，
 * 后两者仅在 milink 调用栈内生效），让 milink 自己按原生锁屏逻辑跑。
 * 去重（广播 voip 与通知卡片的同 key 合并）也由 milink 原生处理。
 */
public class HookCallRelay {

    public static void install(ClassLoader cl) {
        // v0.5.15.4：改回原版，由 milink 原生分流 + HookForceTransfer 模拟锁屏，
        // 不再注入任何来电 hack。
        MiflowLog.i("HookCallRelay: reverted to stock flow (simulated-lock via HookForceTransfer)");
    }
}
