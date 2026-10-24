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
 *
 * 修复：hook filterCall，对**来电（callType==1，INCOMING）**一律强制放行（返回 false，
 * 不过滤），通知监听路径即走完整流转链，不再依赖补发广播。
 * 去电（callType==2）与 category 非 call 保持原过滤，避免误流转。
 *
 * beta37 曾加入"真实锁屏时不放行监听路径 + 同通知 3s 去重"，实测翻车：
 * ①锁屏时其实没有独立的广播路径兜底（拦截监听路径后锁屏来电直接全灭）；
 * beta38 恢复为：**来电一律放行、不做锁屏区分**（锁屏/亮屏都走监听路径流转），
 * 且**同一来电通知 3 秒内只放行第一次**（系统会把同一通知回调多次，只让 milink
 * 处理一次 → 每台接收设备永远只收 1 份，消除"多份会话冲突挂断"）。
 *
 * 门控：功能① force_transfer（与 HookCallRelay 同开关）。
 */
public class HookCallFilterPass {

    private static final String CALL_TYPE = "android.callType";
    private static final int CALL_INCOMING = 1;

    /** 同一来电通知 3 秒内只放行第一次（系统会把同一通知回调多次，只让 milink 处理一次）。 */
    private static final long DUP_WINDOW_MS = 3000L;
    private static final java.util.Map<String, Long> releasedOnce =
            new java.util.concurrent.ConcurrentHashMap<>(16);

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
                                            String key = ((StatusBarNotification) arg0).getKey();
                                            long now = android.os.SystemClock.elapsedRealtime();
                                            Long last = releasedOnce.get(key);
                                            if (last != null && (now - last.longValue()) < DUP_WINDOW_MS) {
                                                // 同一来电通知的重复回调：跳过，让 milink 只处理第一次
                                                MiflowLog.i("HookCallFilterPass: same incoming call within window, skip (only transfer once)");
                                            } else {
                                                releasedOnce.put(key, Long.valueOf(now));
                                                MiflowLog.i("HookCallFilterPass: callType=" + callType
                                                        + " (incoming) filter released once, allow transfer");
                                                return Boolean.FALSE;
                                            }
                                        } else {
                                            MiflowLog.i("HookCallFilterPass: filtered, callType=" + callType
                                                    + " kept filtered");
                                        }
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
