package com.hyperflowplus.hooks;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① 解锁时流转 · 亮屏放行（milink 进程，beta43 修正）。
 *
 * 反编译确认 milink 流转判定链（NotificationHandler.isDeviceSupported）：
 *   if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
 *   return false;   // ← 亮屏拒绝流转
 *
 * 注意：isDeviceSupported 是**所有通知**（短信/微信/来电等）的流转判定点，
 * 不是来电专用。beta37 曾只对来电放行 → 普通通知亮屏不再流转（beta43 实测回归）。
 *
 * v0.6.10 / beta36 教训：全局模拟锁屏（把 KeyguardManager/PowerManager/Display 全 hook
 * 成锁屏态）会误伤正常通话流程——小米15 亮屏接听时 milink 查 PowerManager.isInteractive()
 * （被模拟成 false）→ 认为设备息屏 → 中断会话 → 电话被挂断（间歇性时序竞争）。
 *
 * beta43 修复：**不模拟任何全局状态**，只精准 hook `NotificationHandler.isDeviceSupported`：
 * 原判定为拒绝（亮屏）时一律返回 true 放行——来电与普通通知同等待遇，
 * 恢复 beta36 之前"解锁时流转"的完整行为。voip 会话建立不再受屏幕状态模拟干扰
 * （PowerManager 等返回真实值，电话流程安全）。
 *
 * 门控：功能① force_transfer。
 */
public class HookCallRelay {

    private static final String CALL_TYPE = "android.callType";
    private static final int CALL_INCOMING = 1;

    public static void install(ClassLoader cl) {
        if (!Config.isForceTransferEnabled()) {
            MiflowLog.i("HookCallRelay: force_transfer off, skip");
            return;
        }
        try {
            Class<?> cls = cl.loadClass("com.xiaomi.dist.notification.listener.handle.NotificationHandler");
            Class<?> dsiCls = cl.loadClass("com.xiaomi.dist.notification.common.data.DeviceSubInfo");
            Method m = cls.getDeclaredMethod("isDeviceSupported", StatusBarNotification.class, dsiCls);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                // 原判定已放行 → 不动
                                if (result instanceof Boolean && ((Boolean) result).booleanValue()) {
                                    return result;
                                }
                                Object arg0 = chain.getArgs().get(0);
                                if (arg0 instanceof StatusBarNotification) {
                                    Notification n = ((StatusBarNotification) arg0).getNotification();
                                    Bundle ex = n != null ? n.extras : null;
                                    int callType = ex != null ? ex.getInt(CALL_TYPE, -1) : -1;
                                    if (callType == CALL_INCOMING) {
                                        MiflowLog.i("HookCallRelay: incoming call isDeviceSupported false->true, allow transfer");
                                    } else {
                                        MiflowLog.i("HookCallRelay: notification isDeviceSupported false->true, allow transfer (screen-on, non-call)");
                                    }
                                    return Boolean.TRUE;
                                }
                            } catch (Throwable t) {
                                MiflowLog.e("HookCallRelay: intercept failed, pass through", t);
                            }
                            return result;
                        }
                    });
            MiflowLog.i("HookCallRelay: installed on NotificationHandler.isDeviceSupported (all notifications, no global screen simulation)");
        } catch (ClassNotFoundException e) {
            MiflowLog.w("HookCallRelay: NotificationHandler not found (version mismatch?), skip");
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay: install failed", t);
        }
    }
}
