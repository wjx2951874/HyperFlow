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
 * 功能① 亮屏来电流转 · 过滤放行（milink 进程）。
 *
 * 根因（hf_light2.log 实锤 + 反编译交叉验证）：
 *   incallui 来电通知通过 NotificationListener 即时到达 milink（onNotificationPosted），
 *   但被 AppNotificationFilter.filterCall() 静默过滤：
 *     if (!"call".equals(category)) return true;
 *     if (callType == 2) return true;                          // 去电
 *     boolean fromBroadcast = extras.getBoolean(NOTIFICATION_FROM_BROADCAST, false);
 *     return !fromBroadcast && mVoipTransHelper.isLocalVoipSupported();
 *   —— 亮屏通知走监听路径：fromBroadcast=false；本机 com.android.phone 声明 supportiOS=true
 *      → isLocalVoipSupported()=true → 返回 true（过滤）→ "notification is invalid, skip transfer"
 *      → 不流转。
 *   锁屏能流转是因为锁屏走广播路径（ON_CALL_VOIP 广播通知自带 NOTIFICATION_FROM_BROADCAST=true → 放行）。
 *
 * 修复：hook filterCall，对**来电（callType==1，INCOMING）**强制放行（返回 false，不过滤），
 * 通知监听路径即走完整流转链，不再依赖 incallui 补发广播（补发方案 beta33.9 未触发，见交接 §2）。
 * 去电（callType==2）与 category 非 call 保持原过滤，避免误流转。
 *
 * 门控：功能① force_transfer（与 HookCallRelay 同开关）。
 */
public class HookCallFilterPass {

    private static final String CALL_TYPE = "android.callType";
    private static final int CALL_INCOMING = 1;

    public static void install(ClassLoader cl) {
        if (!Config.isForceTransferEnabled()) {
            MiflowLog.i("HookCallFilterPass: force_transfer off, skip");
            return;
        }
        try {
            Class<?> cls = cl.loadClass("com.xiaomi.dist.notification.listener.handle.AppNotificationFilter");
            Method filterCall = cls.getDeclaredMethod("filterCall", StatusBarNotification.class);
            filterCall.setAccessible(true);
            XposedEntry.get().hook(filterCall)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                // 只有"被过滤"才考虑放行；已放行的不动
                                if (!(result instanceof Boolean) || ((Boolean) result).booleanValue()) {
                                    Object arg0 = chain.getArgs().get(0);
                                    if (arg0 instanceof StatusBarNotification) {
                                        Notification n = ((StatusBarNotification) arg0).getNotification();
                                        Bundle ex = n != null ? n.extras : null;
                                        int callType = ex != null ? ex.getInt(CALL_TYPE, -1) : -1;
                                        if (callType == CALL_INCOMING) {
                                            MiflowLog.i("HookCallFilterPass: callType=" + callType
                                                    + " (incoming) filter released, allow transfer");
                                            return Boolean.FALSE;
                                        }
                                        MiflowLog.i("HookCallFilterPass: filtered, callType=" + callType
                                                + " kept filtered");
                                    }
                                }
                            } catch (Throwable t) {
                                MiflowLog.e("HookCallFilterPass: intercept failed, pass through", t);
                            }
                            return result;
                        }
                    });
            MiflowLog.i("HookCallFilterPass: installed on AppNotificationFilter.filterCall");
        } catch (ClassNotFoundException e) {
            MiflowLog.w("HookCallFilterPass: AppNotificationFilter not found (version mismatch?), skip");
        } catch (Throwable t) {
            MiflowLog.e("HookCallFilterPass: install failed", t);
        }
    }
}
