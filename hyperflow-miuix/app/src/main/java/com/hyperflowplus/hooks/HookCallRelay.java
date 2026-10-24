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
 * 功能① 亮屏来电流转 · 精准放行（milink 进程，beta37 重构）。
 *
 * 反编译确认 milink 流转判定链（NotificationHandler.isDeviceSupported）：
 *   if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
 *   return false;   // ← 亮屏拒绝流转
 *
 * v0.6.10 教训：全局模拟锁屏会误伤正常通话流程。beta36 之前本模块采用"把 milink 进程内
 * KeyguardManager/PowerManager/Display 全 hook 成锁屏态"的方案——实测发现副作用：
 * 小米15 亮屏接听时，milink 处理 voip 会话建立会查 PowerManager.isInteractive()（被模拟成
 * false）→ 认为设备息屏 → 中断会话 → 小米15 弹"当前连接已断开，请回到原设备接听电话"，
 * 电话被直接挂断（间歇性，时序竞争）。
 *
 * beta37 重构：**收起全局状态模拟**，只精准 hook `NotificationHandler.isDeviceSupported`：
 * 仅对**来电（callType==1）且原判定为拒绝**时返回 true（放行流转）；
 * 短信/其他通知走原判定，不动；voip 会话建立不再受屏幕状态模拟干扰（恢复真实值）。
 * 锁屏双流转由 HookCallFilterPass 的真实锁屏判定防护（锁屏不放行监听路径）。
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
                                        return Boolean.TRUE;
                                    }
                                }
                            } catch (Throwable t) {
                                MiflowLog.e("HookCallRelay: intercept failed, pass through", t);
                            }
                            return result;
                        }
                    });
            MiflowLog.i("HookCallRelay: installed on NotificationHandler.isDeviceSupported (precise, no global screen simulation)");
        } catch (ClassNotFoundException e) {
            MiflowLog.w("HookCallRelay: NotificationHandler not found (version mismatch?), skip");
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay: install failed", t);
        }
    }
}
