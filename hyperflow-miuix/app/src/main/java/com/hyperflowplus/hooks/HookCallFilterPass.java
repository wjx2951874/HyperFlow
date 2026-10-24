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
 * 修复：hook filterCall，对**来电（callType==1，INCOMING）且真实亮屏**强制放行（返回 false，
 * 不过滤），通知监听路径即走完整流转链，不再依赖补发广播。
 * 去电（callType==2）与 category 非 call 保持原过滤，避免误流转。
 *
 * 锁屏双流转防护（beta45 重建，用户拍板"锁屏用原逻辑、只加亮屏的"）：
 *   锁屏来电 incallui 走 ON_CALL_VOIP 广播路径（通知自带 NOTIFICATION_FROM_BROADCAST=true，
 *   filterCall 原生放行、原生 buildPlainMessage 判定 voip）→ 广播路径独享一份 voip。
 *   若同时放行监听路径（fromBroadcast=false）的原始通知，则锁屏会多一份降级通知/voip
 *   —— 用户实测"锁屏通知和 voip 一起来"（beta44）。因此：**真实锁屏时不放行监听路径**，
 *   **真实亮屏才放行**。
 *   锁屏判定用进程内 PowerManager/KeyguardManager（beta38 起 HookCallRelay 不再模拟全局
 *   屏幕状态，进程内 API 为真实值）；查询失败 fallback 为亮屏（放行），宁可偶发双份
 *   也不全灭（beta37 教训：跨进程 Binder 失败 fallback 成锁屏不放行 → 来电全灭）。
 * 重复流转去重：同一来电通知 3 秒内只放行第一次（系统会把同一通知回调多次，只让 milink
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

    /** 真实屏幕状态（beta45）：进程内 API（beta38 起 HookCallRelay 不再模拟全局屏幕状态，
     *  进程内 PowerManager/KeyguardManager 为真实值）。查询失败 fallback 亮屏（放行），
     *  宁可偶发双份也不全灭（beta37 教训）。 */
    private static boolean isScreenOn() {
        try {
            android.content.Context ctx = (android.content.Context)
                    Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication").invoke(null);
            if (ctx == null) {
                return true;
            }
            android.os.PowerManager pm = (android.os.PowerManager) ctx
                    .getSystemService(android.content.Context.POWER_SERVICE);
            if (pm != null && pm.isInteractive()) {
                return true;
            }
            android.app.KeyguardManager kg = (android.app.KeyguardManager) ctx
                    .getSystemService(android.content.Context.KEYGUARD_SERVICE);
            if (kg != null && kg.isKeyguardLocked()) {
                return false;
            }
            return pm != null && pm.isInteractive();
        } catch (Throwable t) {
            MiflowLog.e("HookCallFilterPass: screen state check failed, treat as screen-on", t);
        }
        return true; // fallback：亮屏放行
    }

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
                                            // beta45：锁屏来电走原生广播路径（一份 voip），监听路径保持
                                            // 原生过滤（防锁屏双份）；真实亮屏才放行监听路径。
                                            if (!isScreenOn()) {
                                                MiflowLog.i("HookCallFilterPass: incoming while keyguard locked, keep native filter"
                                                        + " (broadcast path exclusive, avoid duplicate voip+notification)");
                                                return result;
                                            }
                                            String key = ((StatusBarNotification) arg0).getKey();
                                            long now = android.os.SystemClock.elapsedRealtime();
                                            Long last = releasedOnce.get(key);
                                            if (last != null && (now - last.longValue()) < DUP_WINDOW_MS) {
                                                // 同一来电通知的重复回调：跳过，让 milink 只处理第一次
                                                MiflowLog.i("HookCallFilterPass: same incoming call within window, skip (only transfer once)");
                                            } else {
                                                releasedOnce.put(key, Long.valueOf(now));
                                                MiflowLog.i("HookCallFilterPass: callType=" + callType
                                                        + " (incoming) filter released once (screen-on), allow transfer");
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
