package com.hyperflowplus.hooks;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 来电跨设备接听（voip 全屏）—— 亮屏也生效。
 *
 * 背景（反编译 com.milink 确认）：
 *   NotificationBuilder.buildPlainMessage 里：
 *     str4 = "msg";
 *     if (notification_from_broadcast == true && isVoipSupported(...)) {
 *         str4 = TYPE_VOIP;   // ← 只有"广播链路"来的来电才走 voip 全屏接听
 *     }
 *   锁屏时系统来电广播被 milink 收到（notification_from_broadcast=true）→ 全屏接听；
 *   亮屏时来电走"通知监听链路"（onNotificationPosted），该标志为 false → 形态不是全屏接听。
 *
 * beta25 失败教训（本版修复）：
 *   - 旧版对"所有 call 类别通知"无脑设 broadcast=true / 放行过滤，
 *     来电接听中、挂断后的通知（callType 不再等于 1）也被强制走 voip → 残留错乱卡；
 *   - 旧版混入 iOS 强开 voip（与本需求无关，且可能改变 iOS 设备行为）。
 *
 * 本版门控：
 *   - 仅 callType == 1（来电中）设 notification_from_broadcast=true 并放行过滤；
 *   - 去电（2）/未接（3）/未知（0 或缺失）一律交给原生；
 *   - 由 HookForceTransfer 负责 isDeviceSupported 对"来电中"强制放行，
 *     本 hook 只负责把流转形态修正为 voip 全屏（跨设备接听）。
 */
public class HookCallVoip {

    /** milink 内部标志：通知来自系统来电广播（广播链路）→ buildPlainMessage 据此走 voip */
    private static final String NOTIFICATION_FROM_BROADCAST = "notification_from_broadcast";
    private static final String PACKAGE_INCALLUI = "com.android.incallui";
    /** 通知 extras 里的来电类型：1=来电中（incoming）2=去电 3=未接 */
    private static final int CALL_TYPE_INCOMING = 1;

    public static void install(ClassLoader cl) {
        // 0. 来电通知进入流转处理前：把"监听链路"伪装成"广播链路"（仅来电中），
        //    让 buildPlainMessage 走 voip 全屏分支。接听/挂断后 callType 变化即不再设置。
        try {
            Class<?> handlerCls = Class.forName(
                    "com.xiaomi.dist.notification.listener.handle.NotificationHandler", false, cl);
            Method handleNotificationTrans = handlerCls.getDeclaredMethod(
                    "handleNotificationTrans", StatusBarNotification.class);
            handleNotificationTrans.setAccessible(true);
            XposedEntry.get().hook(handleNotificationTrans)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            StatusBarNotification sbn = (StatusBarNotification) chain.getArg(0);
                            if (isIncomingCall(sbn)) {
                                Notification n = sbn.getNotification();
                                Bundle extras = n != null ? n.extras : null;
                                if (extras != null
                                        && !extras.getBoolean(NOTIFICATION_FROM_BROADCAST, false)) {
                                    extras.putBoolean(NOTIFICATION_FROM_BROADCAST, true);
                                    MiflowLog.d("handleNotificationTrans: voip flag set for incoming call, pkg="
                                            + sbn.getPackageName() + " key=" + sbn.getKey());
                                }
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallVoip: handleNotificationTrans hooked (incoming-call only)");
        } catch (Throwable t) {
            MiflowLog.e("HookCallVoip: handleNotificationTrans hook failed", t);
        }

        // 1. 亮屏时来电通知的过滤：仅来电中放行（返回 false = 不拦截），
        //    去电/未接/未知交给原生（避免非来电窗口的通知被意外放行）。
        try {
            Class<?> filterCls = Class.forName(
                    "com.xiaomi.dist.notification.listener.handle.AppNotificationFilter", false, cl);
            Method filterCall = filterCls.getDeclaredMethod("filterCall", StatusBarNotification.class);
            filterCall.setAccessible(true);
            XposedEntry.get().hook(filterCall)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            StatusBarNotification sbn = (StatusBarNotification) chain.getArg(0);
                            if (isIncomingCall(sbn)) {
                                MiflowLog.d("filterCall: bypass for incoming call, pkg="
                                        + sbn.getPackageName() + " key=" + sbn.getKey());
                                return Boolean.FALSE;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallVoip: filterCall hooked (incoming-call only)");
        } catch (Throwable t) {
            MiflowLog.e("HookCallVoip: filterCall hook failed", t);
        }
    }

    /** 是否为"来电中"通知：包名 incallui / category call，且 extras 无 callType 或 callType==1。
     *  去电(2)/未接(3)/已结束均不满足 → 不设 voip 标志、不放行过滤。 */
    private static boolean isIncomingCall(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            Notification n = sbn.getNotification();
            if (n == null || n.extras == null) {
                return false;
            }
            boolean looksCall = PACKAGE_INCALLUI.equals(pkg)
                    || (pkg != null && (pkg.contains("incallui") || pkg.contains("dialer")
                            || "com.android.phone".equals(pkg)))
                    || Notification.CATEGORY_CALL.equals(n.category);
            if (!looksCall) {
                return false;
            }
            int callType = n.extras.getInt("android.callType", CALL_TYPE_INCOMING);
            // 无 callType（-1/缺失）按来电中处理（早期来电通知可能没带）；明确为去电/未接则排除
            return callType == CALL_TYPE_INCOMING;
        } catch (Throwable t) {
            return false;
        }
    }
}
