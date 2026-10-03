package com.hyperflowplus.hooks;

import android.app.KeyguardManager;
import android.service.notification.StatusBarNotification;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① 亮屏强制流转（V0.2 libxposed 重写；v0.5.15.4 改回原版分流 + 模拟锁屏）。
 *
 * 反编译确认（NotificationHandler）：
 *   private boolean isDeviceSupported(StatusBarNotification sbn, DeviceSubInfo info) {
 *       ...（deviceSwitch / 订阅 / 灵动岛过滤）
 *       if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
 *       Log.e(TAG, "device is not locked and screen is on");
 *       return false;   // ← 亮屏拒绝流转
 *   }
 *
 * 方案（用户确认）：不改 milink 的来电分流决策（OS4 走 voip 全屏 / OS3 降级通知），
 * 只"模拟锁屏"让 milink 自己按原生锁屏逻辑跑：
 *   1) isDeviceSupported 直接返回 true —— 等效"模拟锁屏"（亮屏也放行，含来电通知）；
 *   2) KeyguardManager#isKeyguardLocked：milink 调用栈返回 true；
 *   3) PowerManager#isInteractive：milink 调用栈返回 false（模拟灭屏，
 *      覆盖来电广播链路里对屏幕状态的另一处门控）。
 * 来电通知不再短路（原短路是为防"广播 voip + 通知卡片"双显示，改回原版后
 * 由 milink 自身同 key 去重处理，不需要我们插手）。
 */
public class HookForceTransfer {

    public static void install(ClassLoader cl) {
        // 主方案：覆盖流转门控，模拟锁屏
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
                            if (!Config.isForceTransferEnabled()) {
                                return chain.proceed();
                            }
                            StatusBarNotification sbn = (StatusBarNotification) chain.getArg(0);
                            if (isRunnableStateCard(sbn)) {
                                MiflowLog.d("skip runnable-state card (not a real notification)");
                                return Boolean.FALSE;
                            }
                            // v0.5.15.4：来电不再短路（改回原版分流，去重交给 milink），
                            // 亮屏/锁屏一律放行，等效"设备已锁屏可流转"
                            Config.bump(Config.CNT_FORCE);
                            MiflowLog.d("force transfer: gate overridden (screen-on released, call included)");
                            return Boolean.TRUE;
                        }
                    });
            MiflowLog.i("HookForceTransfer[gate] installed");
        } catch (Throwable t) {
            MiflowLog.e("HookForceTransfer[gate] install failed", t);
        }

        // 兜底：KeyguardManager 层（调用栈含 milink 流转任意类即模拟锁屏，
        // 覆盖通知链路 + 广播链路——来电 voip 全屏需要广播链路也"看到"锁屏）
        try {
            Method m = KeyguardManager.class.getDeclaredMethod("isKeyguardLocked");
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (!Config.isForceTransferEnabled()) {
                                return chain.proceed();
                            }
                            if (calledFromMilink()) {
                                return Boolean.TRUE;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookForceTransfer[keyguard] installed");
        } catch (Throwable t) {
            MiflowLog.e("HookForceTransfer[keyguard] install failed", t);
        }

        // v0.5.15.4：模拟"灭屏"——来电广播链路在亮屏时可能因 PowerManager.isInteractive()
        // 判定拒绝流转（与 isKeyguardLocked 并列的门控），milink 调用栈内强制返回 false
        try {
            Method m = android.os.PowerManager.class.getDeclaredMethod("isInteractive");
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (!Config.isForceTransferEnabled()) {
                                return chain.proceed();
                            }
                            if (calledFromMilink()) {
                                return Boolean.FALSE;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookForceTransfer[interactive] installed");
        } catch (Throwable t) {
            MiflowLog.e("HookForceTransfer[interactive] install failed", t);
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

    /** 只要调用栈中出现 milink 流转监听/处理/广播类即放行（等效模拟锁屏） */
    private static boolean calledFromMilink() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : st) {
            if (e.getClassName().startsWith("com.xiaomi.dist.notification")) {
                return true;
            }
        }
        return false;
    }
}
