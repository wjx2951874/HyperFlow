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
 * v1.1.0-beta25：修复亮屏来电流转 + iOS 强开 voip
 *
 * 反编译确认（NotificationBuilder.buildPlainMessage）：
 *   String str4 = "msg";
 *   if (notification_from_broadcast == true && isVoipSupported(...)) {
 *       str4 = TYPE_VOIP;  // ← 只有从广播来的才走 voip 路径
 *   }
 *
 * 问题：亮屏时来电通知是直接监听的（notification_from_broadcast = false），
 *       所以不走 voip 路径，导致亮屏时电话不能流转。
 *
 * 方案：
 *   1. hook AppNotificationFilter.filterCall()：让亮屏时的来电通知也不过滤（放行）
 *   2. hook VoipTransHelper.isRemoteVoipSupported()：对于 iOS 设备，强制返回 true（强开）
 *   3. （buildPlainMessage 的 hook 后面再完善）
 */
public class HookCallVoip {

    private static final String NOTIFICATION_FROM_BROADCAST = "notification_from_broadcast";
    private static final String PACKAGE_INCALLUI = "com.android.incallui";
    private static final int DEVICE_TYPE_IOS = 11;

    public static void install(ClassLoader cl) {
        // 0. hook NotificationHandler.handleNotificationTrans()：来电通知手动设置 notification_from_broadcast = true
        try {
            Class<?> handlerCls = Class.forName("com.xiaomi.dist.notification.listener.handle.NotificationHandler", false, cl);
            Method handleNotificationTrans = handlerCls.getDeclaredMethod("handleNotificationTrans", StatusBarNotification.class);
            handleNotificationTrans.setAccessible(true);
            XposedEntry.get().hook(handleNotificationTrans)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (!Config.isForceTransferEnabled()) {
                                return chain.proceed();
                            }
                            StatusBarNotification sbn = (StatusBarNotification) chain.getArg(0);
                            String pkg = sbn.getPackageName();
                            Notification n = sbn.getNotification();
                            String category = n != null ? n.category : null;

                            // 只处理来电通知（incallui 或 category == call）
                            boolean isCall = PACKAGE_INCALLUI.equals(pkg) || "call".equals(category);
                            if (!isCall) {
                                return chain.proceed();
                            }

                            // 检查是不是去电（callType == 2）
                            Bundle extras = n.extras;
                            int callType = extras != null ? extras.getInt("android.callType", -1) : -1;
                            if (callType == 2) {
                                return chain.proceed();  // 去电不处理
                            }

                            // 来电通知：手动设置 notification_from_broadcast = true
                            // 这样 buildPlainMessage 里的条件就满足了，会自动走 voip 路径
                            if (extras != null && !extras.getBoolean(NOTIFICATION_FROM_BROADCAST, false)) {
                                extras.putBoolean(NOTIFICATION_FROM_BROADCAST, true);
                                MiflowLog.d("handleNotificationTrans: force notification_from_broadcast=true for incoming call, pkg=" + pkg);
                            }

                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallVoip: NotificationHandler.handleNotificationTrans() hooked");
        } catch (Throwable t) {
            MiflowLog.e("HookCallVoip: handleNotificationTrans hook failed", t);
        }

        // 1. hook AppNotificationFilter.filterCall()：让亮屏时的来电通知也不过滤
        try {
            Class<?> filterCls = Class.forName("com.xiaomi.dist.notification.listener.handle.AppNotificationFilter", false, cl);
            Method filterCall = filterCls.getDeclaredMethod("filterCall", StatusBarNotification.class);
            filterCall.setAccessible(true);
            XposedEntry.get().hook(filterCall)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (!Config.isForceTransferEnabled()) {
                                return chain.proceed();
                            }
                            StatusBarNotification sbn = (StatusBarNotification) chain.getArg(0);
                            String pkg = sbn.getPackageName();
                            Notification n = sbn.getNotification();
                            String category = n != null ? n.category : null;

                            // 只处理来电通知（incallui 或 category == call）
                            boolean isCall = PACKAGE_INCALLUI.equals(pkg) || "call".equals(category);
                            if (!isCall) {
                                return chain.proceed();
                            }

                            // 检查是不是去电（callType == 2）
                            Bundle extras = n.extras;
                            int callType = extras != null ? extras.getInt("android.callType", -1) : -1;
                            if (callType == 2) {
                                return chain.proceed();  // 去电还是过滤掉
                            }

                            // 来电通知：不过滤（放行）
                            MiflowLog.d("filterCall: bypass filter for incoming call, pkg=" + pkg);
                            return Boolean.FALSE;
                        }
                    });
            MiflowLog.i("HookCallVoip: AppNotificationFilter.filterCall() hooked");
        } catch (Throwable t) {
            MiflowLog.e("HookCallVoip: filterCall hook failed", t);
        }

        // 2. hook VoipTransHelper.isRemoteVoipSupported()：iOS 设备强制返回 true
        try {
            Class<?> voipCls = Class.forName("com.xiaomi.dist.notification.listener.voip.VoipTransHelper", false, cl);
            Method isRemoteVoipSupported = voipCls.getDeclaredMethod("isRemoteVoipSupported", String.class, int.class);
            isRemoteVoipSupported.setAccessible(true);
            XposedEntry.get().hook(isRemoteVoipSupported)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (!Config.isForceTransferEnabled()) {
                                return chain.proceed();
                            }
                            String deviceId = (String) chain.getArg(0);
                            int deviceType = (int) chain.getArg(1);

                            // iOS 设备（deviceType == 11）：强制返回 true
                            if (deviceType == DEVICE_TYPE_IOS) {
                                MiflowLog.d("isRemoteVoipSupported: force return true for iOS device, deviceId=" + deviceId);
                                return Boolean.TRUE;
                            }

                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallVoip: VoipTransHelper.isRemoteVoipSupported() hooked");
        } catch (Throwable t) {
            MiflowLog.e("HookCallVoip: isRemoteVoipSupported hook failed", t);
        }
    }
}
