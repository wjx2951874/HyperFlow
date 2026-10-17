package com.hyperflowplus.hooks;

import com.hyperflowplus.MiflowLog;

/**
 * 来电形态干预——已移除（v1.1.0-beta33.1）。
 *
 * 结论：模拟锁屏（HookCallRelay）真正生效后，milink 会走与真实锁屏完全相同的
 * 广播链路：对端支持 VoIP → 全屏接听；不支持 → 才降级推送卡片。形态判定完全由
 * milink 原生完成，无需手动打 notification_from_broadcast 标志 / 放行 filterCall。
 *
 * 历史教训：beta25 曾 hook handleNotificationTrans 谎报广播标志 + filterCall 放行，
 * 导致"来电接听中/挂断后残留错乱卡、亮屏出现本不该流转的卡片"——即用户实测的
 * "推送卡片"问题。原生逻辑自己会做对，干预只会破坏它。
 *
 * 保留本文件仅为装配兼容（XposedEntry 仍 installSafely 调用），不做任何 hook。
 */
public class HookCallVoip {

    public static void install(ClassLoader cl) {
        // 全部移除：形态判定交给 milink 原生（模拟锁屏已覆盖放行链路）
        MiflowLog.i("HookCallVoip: no-op (native voip flow via simulated keyguard)");
    }
}
