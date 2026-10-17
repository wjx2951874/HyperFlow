package com.hyperflowplus.hooks;

import android.service.notification.StatusBarNotification;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① 亮屏强制流转（V0.2 libxposed 重写；v0.6.14 来电精准强制流转）。
 *
 * 反编译确认（NotificationHandler）：
 *   private boolean isDeviceSupported(StatusBarNotification sbn, DeviceSubInfo info) {
 *       ...（deviceSwitch / 订阅 / 灵动岛过滤）
 *       if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
 *       Log.e(TAG, "device is not locked and screen is on");
 *       return false;   // ← 亮屏拒绝流转
 *   }
 *
 * v0.6.14 门控（用户确认）：
 *   - 来电通知（CATEGORY_CALL / incallui / dialer / phone）→ 强制 TRUE（精准流转）：
 *     isDeviceSupported 首个参数就是 sbn，可精确区分来电，只对来电放行（等效仅来电的
 *     亮屏判定变可流转），亮屏来电也能流转；接收端能力判定（OS4→voip 全屏 / OS3·苹果
 *     →降级卡片）与同 key 去重交给 milink 原生——不碰 system_server、不碰其他通知；
 *   - 其他通知：isDeviceSupported 强制 true（功能①亮屏强制流转，v0.6.8 行为保留）；
 *   - 运行状态卡拦截保留（V0.3.16 起）。
 *   - 历史教训：v0.6.10 的 HookCallSimLock（system_server 全局模拟锁屏）误伤正常通话
 *     流程（isKeyguardLocked/isInteractive 被正常来电流程大量调用），连锁屏 voip 都断；
 *     已删除。全过滤来电卡片（v0.6.10）同样错误——卡片是 OS3/苹果接收端的降级通道。
 */
public class HookForceTransfer {

    public static void install(ClassLoader cl) {
        // v1.1.0-beta33.1：全部交给 milink 原生 —— 放行判定由 HookCallRelay 模拟锁屏完成
        // （原生锁屏广播链路自动走 voip 全屏接听，对端不支持才降级卡片）；运行状态卡拦截
        // 也一并移除（用户确认交给 milink 原生处理）。本文件保留仅为装配兼容，不做任何 hook。
        MiflowLog.i("HookForceTransfer: no-op (all native, simulated keyguard only)");
    }
}
