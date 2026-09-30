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
 * 功能① 亮屏强制流转（V0.2 libxposed 重写）。
 *
 * 反编译确认（NotificationHandler）：
 *   private boolean isDeviceSupported(StatusBarNotification sbn, DeviceSubInfo info) {
 *       ...（deviceSwitch / 订阅 / 灵动岛过滤）
 *       if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
 *       Log.e(TAG, "device is not locked and screen is on");
 *       return false;   // ← 亮屏拒绝流转
 *   }
 *
 * 主方案：拦截 isDeviceSupported 直接返回 true —— 等效"模拟锁屏"，
 * 亮屏时 milink 照常放行（副作用：互联侧看到设备为"已锁屏/可流转"，用户已接受）。
 * 兜底：拦截 KeyguardManager#isKeyguardLocked，仅当调用栈含 NotificationHandler 时放行。
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
                            if (isRunnableStateCard((StatusBarNotification) chain.getArg(0))) {
                                MiflowLog.d("skip runnable-state card (not a real notification)");
                                return Boolean.FALSE;
                            }
                            Config.bump(Config.CNT_FORCE);
                            MiflowLog.d("force transfer: gate overridden (screen-on released)");
                            return Boolean.TRUE;
                        }
                    });
            MiflowLog.i("HookForceTransfer[gate] installed");
        } catch (Throwable t) {
            MiflowLog.e("HookForceTransfer[gate] install failed", t);
        }

        // 兜底：KeyguardManager 层（栈中含 NotificationHandler 即放行）
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
                            if (calledFromMilinkNotificationHandler()) {
                                return Boolean.TRUE;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookForceTransfer[keyguard] installed");
        } catch (Throwable t) {
            MiflowLog.e("HookForceTransfer[keyguard] install failed", t);
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

    /** 只要调用栈中出现流转监听处理类即放行（容错内联/合成方法） */
    private static boolean calledFromMilinkNotificationHandler() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : st) {
            if ("com.xiaomi.dist.notification.listener.handle.NotificationHandler".equals(e.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
