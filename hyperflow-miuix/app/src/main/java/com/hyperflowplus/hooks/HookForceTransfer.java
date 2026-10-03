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
 * 功能① 亮屏强制流转（V0.2 libxposed 重写；v0.6.10 来电强制全屏流转）。
 *
 * 反编译确认（NotificationHandler）：
 *   private boolean isDeviceSupported(StatusBarNotification sbn, DeviceSubInfo info) {
 *       ...（deviceSwitch / 订阅 / 灵动岛过滤）
 *       if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
 *       Log.e(TAG, "device is not locked and screen is on");
 *       return false;   // ← 亮屏拒绝流转
 *   }
 *
 * v0.6.10 门控（用户确认：来电强制流转，其他通知维持功能①）：
 *   - 来电通知（CATEGORY_CALL / incallui / dialer / phone）→ 直接拦截（FALSE），
 *     voip 全屏走广播链路：system_server 的 HookCallSimLock 已模拟锁屏放行亮屏来电，
 *     卡片若流转会出现"电话+卡片"双份（锁屏）与接听后"电话"通知残留，一律抑制；
 *   - 其他通知：isDeviceSupported 强制 true（功能①亮屏强制流转，v0.6.8 行为保留）；
 *   - 运行状态卡拦截保留（V0.3.16 起）。
 *   - 注：KeyguardManager/PowerManager 模拟锁屏不再装在 milink 进程（v0.5.15.4 产物，
 *     管不到 system_server 的来电决策，且会把系统原本拒绝的普通通知也放行）；
 *     来电链路的模拟锁屏由 android 作用域（system_server）的 HookCallSimLock 承担，
 *     白名单限定 telecom/phone 调用栈。
 */
public class HookForceTransfer {

    public static void install(ClassLoader cl) {
        // v0.6.10：只改电话，其他通知维持 v0.6.8 的强制流转（用户明确"其他先不要改"）。
        //
        // 上一版（v0.5.15.4 起"模拟锁屏"）isDeviceSupported 一律 true + Keyguard/PowerManager
        // 模拟锁屏，来电通知也被强制放行（亮屏出现系统本不该流转的来电卡片、锁屏双份、
        // 接听后"电话"通知）——用户实测确认不是 milink 原始逻辑。
        //
        // 本版门控逻辑：
        //   - 来电通知（isCallSbn：CATEGORY_CALL / incallui / dialer / phone）→ 直接 proceed，
        //     完全交给 milink 原生判定（亮屏拒绝 / 锁屏按 OS4 走 voip 全屏、OS3 降级通知卡），
        //     模块零干预；
        //   - 不装 KeyguardManager/PowerManager 模拟锁屏 —— 否则来电 proceed 后 milink 内部
        //     锁屏判断仍被模拟成"锁屏"，来电就不是原始逻辑了（广播链路来电 voip 同样受影响）；
        //   - 其他通知：维持 v0.6.8 行为（isDeviceSupported 强制 true = 亮屏强制流转功能①）；
        //   - 运行状态卡拦截保留（V0.3.16 起，运行时状态同步，非真实通知）。
        try {
            Class<?> handler = Class.forName("com.xiaomi.dist.notification.listener.handle.NotificationHandler", false, cl);
            Class<?> devInfo = Class.forName("com.xiaomi.dist.notification.common.data.DeviceSubInfo", false, cl);
            Method m = handler.getDeclaredMethod("isDeviceSupported", StatusBarNotification.class, devInfo);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            StatusBarNotification sbn = (StatusBarNotification) chain.getArg(0);
                            if (isCallSbn(sbn)) {
                                // 来电通知卡片一律抑制：voip 全屏流转走广播链路（system_server 的
                                // HookCallSimLock 已模拟锁屏放行），卡片若再流转会出现"电话+卡片"双份
                                //（锁屏）与接听后"电话"通知残留。抑制后接收端只收 voip 全屏，单一来源。
                                MiflowLog.d("call notification: suppress card (voip via broadcast, simlock gate)");
                                return Boolean.FALSE;
                            }
                            if (isRunnableStateCard(sbn)) {
                                MiflowLog.d("skip runnable-state card (not a real notification)");
                                return Boolean.FALSE;
                            }
                            // 其他通知：功能①亮屏强制流转（v0.6.8 行为，本轮不动）
                            Config.bump(Config.CNT_FORCE);
                            MiflowLog.d("force transfer: gate overridden (non-call notification)");
                            return Boolean.TRUE;
                        }
                    });
            MiflowLog.i("HookForceTransfer[gate] installed (call=stock, others=forced)");
        } catch (Throwable t) {
            MiflowLog.e("HookForceTransfer[gate] install failed", t);
        }
    }

    /**
     * V0.3.16：拦截"应用运行状态"流转卡（如"短信正在运行/如需回复请前往/停止应用"），
     * 这类是 milink 的运行时状态同步，不是真实通知，不应跨设备流转。
     */
    private static boolean isRunnableStateCard(StatusBarNotification sbn) {
        try {
            android.app.Notification n = sbn.getNotification();
            if (n == null) {
                return false;
            }
            StringBuilder sb = new StringBuilder();
            if (n.tickerText != null) {
                sb.append(n.tickerText);
            }
            if (n.extras != null) {
                CharSequence t = n.extras.getCharSequence("android.title");
                CharSequence tx = n.extras.getCharSequence("android.text");
                if (t != null) {
                    sb.append(' ').append(t);
                }
                if (tx != null) {
                    sb.append(' ').append(tx);
                }
            }
            String s = sb.toString();
            return s.contains("正在运行") || s.contains("如需回复请前往")
                    || s.contains("停止应用") || s.contains("了解详情")
                    || s.contains("点按即可");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 是否为来电相关通知：类别 call 或包名 incallui/dialer/phone（v0.6.9 移植 v0.5.15.3） */
    private static boolean isCallSbn(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (pkg != null && (pkg.contains("incallui") || pkg.contains("dialer")
                    || "com.android.phone".equals(pkg))) {
                return true;
            }
            android.app.Notification n = sbn.getNotification();
            return n != null && n.category != null
                    && n.category.equals(android.app.Notification.CATEGORY_CALL);
        } catch (Throwable t) {
            return false;
        }
    }
}
