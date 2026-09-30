package com.hyperflowplus.hooks;

import android.app.Notification;
import android.content.Intent;
import android.service.notification.StatusBarNotification;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能：来电在线接听（亮屏 + 锁屏都走全屏接听，而不是通知卡片）。
 *
 * 反编译确认（V0.3.16，milink）：
 * 来电在发送端有两条流转路径：
 *   ① 广播链路：系统来电广播 → NotifTransReceiver.buildSbnFromIntent()
 *      （extras 打上 notification_from_broadcast=true）→ handleNotificationTrans()
 *      → buildPlainMessage() 判 voip → 接收端 CallHandler.sendVoipBroadcast() 全屏接听。
 *      锁屏时正常；亮屏时无广播 → 没有全屏。
 *   ② 通知链路：InCallUI 发布的来电通知 → onNotificationPosted() → isDeviceSupported()
 *      → buildPlainMessage() 判 msg → 接收端 showNotification() 通知卡片。
 *      亮屏被 isDeviceSupported 拦截，模块 force true 后以通知形态流转（用户实测）。
 *
 * 现状副作用（用户实测）：
 *   - 锁屏：广播 voip（全屏）+ 通知 msg（多余卡片）→ 双显示；
 *   - 亮屏：只有通知卡片，没有全屏接听。
 *
 * 方案：
 *   1) 短路来电广播（buildSbnFromIntent 拦截返回 null）——去掉锁屏时重复的通知卡片；
 *   2) 通知链路来电通知强制走 voip：buildPlainMessage 前把
 *      notification_from_broadcast 打标 + isVoipSupported 强制 true
 *      ——亮屏来电也变 voip 流 → 接收端全屏接听；锁屏同理（单一来源，无重复）。
 */
public class HookCallRelay {

    private static final String EXTRA_FROM_BROADCAST = "notification_from_broadcast";

    public static void install(ClassLoader cl) {
        // ① 来电广播短路：防锁屏时"广播 voip + 通知 msg"双显示
        try {
            Class<?> receiver = Class.forName("com.xiaomi.dist.notification.listener.NotifTransReceiver", false, cl);
            for (Method m : receiver.getDeclaredMethods()) {
                if (!m.getName().equals("buildSbnFromIntent")) {
                    continue;
                }
                if (m.getParameterCount() != 1 || !Intent.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                if (!Config.isForceTransferEnabled()) {
                                    return chain.proceed();
                                }
                                Intent it = (Intent) chain.getArg(0);
                                if (isCallIntent(it)) {
                                    MiflowLog.d("call broadcast short-circuited (avoid dual display)");
                                    return null;
                                }
                                return chain.proceed();
                            }
                        });
                MiflowLog.i("HookCallRelay[broadcast] installed");
                break;
            }
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay[broadcast] install failed", t);
        }

        // ② 通知链路：来电通知打上广播标记，令 buildPlainMessage 走 voip
        try {
            Class<?> builder = Class.forName("com.xiaomi.dist.notification.listener.handle.NotificationBuilder", false, cl);
            for (Method m : builder.getDeclaredMethods()) {
                if (!m.getName().equals("buildPlainMessage")) {
                    continue;
                }
                if (m.getParameterCount() != 5) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                Object arg0 = chain.getArg(0);
                                if (arg0 instanceof StatusBarNotification) {
                                    StatusBarNotification sbn = (StatusBarNotification) arg0;
                                    if (isCallSbn(sbn)) {
                                        try {
                                            sbn.getNotification().extras.putBoolean(EXTRA_FROM_BROADCAST, true);
                                            MiflowLog.d("call notification flagged as voip source");
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                }
                                return chain.proceed();
                            }
                        });
                MiflowLog.i("HookCallRelay[flag] installed");
                break;
            }
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay[flag] install failed", t);
        }

        // ③ isVoipSupported 强制 true：绕过 relay call 开关/本机支持/远端能力检查
        try {
            Class<?> helper = Class.forName("com.xiaomi.dist.notification.listener.voip.VoipTransHelper", false, cl);
            for (Method m : helper.getDeclaredMethods()) {
                if (!m.getName().equals("isVoipSupported")) {
                    continue;
                }
                if (m.getParameterCount() != 3 || m.getReturnType() != boolean.class) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                Object arg0 = chain.getArg(0);
                                if (arg0 instanceof StatusBarNotification) {
                                    StatusBarNotification sbn = (StatusBarNotification) arg0;
                                    if (isCallSbn(sbn)) {
                                        Config.bump(Config.CNT_CALL_RELAY);
                                        MiflowLog.d("voip supported forced true (call)");
                                        return Boolean.TRUE;
                                    }
                                }
                                return chain.proceed();
                            }
                        });
                MiflowLog.i("HookCallRelay[voip] installed");
                break;
            }
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay[voip] install failed", t);
        }
    }

    /** 广播 intent 是否为来电（pkg 为 incallui 或 notification 类别为 call） */
    private static boolean isCallIntent(Intent it) {
        try {
            String pkg = it.getStringExtra("pkg");
            if (pkg != null && (pkg.contains("incallui") || pkg.contains("dialer")
                    || "com.android.phone".equals(pkg))) {
                return true;
            }
            Notification n = (Notification) it.getParcelableExtra("notification");
            return n != null && n.category != null
                    && n.category.equals(Notification.CATEGORY_CALL);
        } catch (Throwable t) {
            return false;
        }
    }

    /** sbn 是否为来电通知：类别 call 或包名 incallui/dialer/phone */
    private static boolean isCallSbn(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (pkg != null && (pkg.contains("incallui") || pkg.contains("dialer")
                    || "com.android.phone".equals(pkg))) {
                return true;
            }
            Notification n = sbn.getNotification();
            return n != null && n.category != null
                    && n.category.equals(Notification.CATEGORY_CALL);
        } catch (Throwable t) {
            return false;
        }
    }
}
