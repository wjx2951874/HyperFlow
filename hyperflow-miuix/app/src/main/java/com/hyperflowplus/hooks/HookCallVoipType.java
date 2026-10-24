package com.hyperflowplus.hooks;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① 亮屏来电 voip 形态（milink 进程）。
 *
 * 用户实测（beta35）：亮屏来电对端收到的仍是"通知"而非 voip 全屏接听；
 * 锁屏正常 voip。原因（反编译 NotificationBuilder.buildPlainMessage）：
 *   String type = "msg";
 *   if (extras.getBoolean(NOTIFICATION_FROM_BROADCAST, false)   // ← 亮屏监听路径无此标志
 *       && mVoipTransHelper.isVoipSupported(sbn, deviceId, deviceType)) {
 *       type = "voip";
 *   }
 * 消息类型只在"广播来源"时才判定 voip；亮屏走监听路径（无广播标志）→ 恒 "msg" → 对端收通知卡片。
 *
 * 修复：hook buildPlainMessage 返回值，当通知为**来电（callType==1）且非广播来源**时，
 * 反射把 PlainMessage.messageType 改为 "voip"，让亮屏来电与锁屏同形态（远端全屏接听）。
 * 只改消息 type 字段：不谎报 NOTIFICATION_FROM_BROADCAST 标志、不碰 filterCall、
 * 不干预 handleNotificationTrans（beta25 教训：谎报标志导致错乱卡，勿再犯）。
 *
 * 设备区分（beta38）：**只对小米设备（deviceType==1）**改 voip；**iPhone（deviceType=11）
 * 保持 msg**。用户实测：iOS 端"小米互联服务"App 只认 msg 通知、不处理 voip 消息，
 * beta36 对 iPhone 也改 voip 后 iPhone 连通知都不显示；beta37 改用原生 isVoipSupported
 * 判定后实测对小米设备也不通过（亮屏降回通知）。beta38 恢复 beta36 验证过的
 * "deviceType==1 → voip"（小米15 亮屏全屏接听），iPhone 保持 msg 恢复来电通知。
 *
 * 门控：功能① force_transfer。
 */
public class HookCallVoipType {

    private static final String CALL_TYPE = "android.callType";
    private static final int CALL_INCOMING = 1;
    private static final String NOTIFICATION_FROM_BROADCAST = "notification_from_broadcast";

    public static void install(ClassLoader cl) {
        if (!Config.isForceTransferEnabled()) {
            MiflowLog.i("HookCallVoipType: force_transfer off, skip");
            return;
        }
        try {
            Class<?> cls = cl.loadClass("com.xiaomi.dist.notification.listener.handle.NotificationBuilder");
            Class<?> dsiCls = cl.loadClass("com.xiaomi.dist.notification.common.data.DeviceSubInfo");
            Class<?> tdiCls = cl.loadClass("com.xiaomi.continuity.networking.TrustedDeviceInfo");
            Method m = cls.getDeclaredMethod("buildPlainMessage",
                    StatusBarNotification.class, dsiCls, tdiCls, String.class, int.class);
            m.setAccessible(true);
            Class<?> plainCls = cl.loadClass(
                    "com.xiaomi.dist.notification.listener.data.NotificationTransMessage$MessageContent$PlainMessage");
            final Field typeField = plainCls.getDeclaredField("messageType");
            typeField.setAccessible(true);

            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                Object arg0 = chain.getArgs().get(0);
                                Object arg1 = chain.getArgs().get(1); // DeviceSubInfo
                                if (!(arg0 instanceof StatusBarNotification) || arg1 == null) {
                                    return result;
                                }
                                Notification n = ((StatusBarNotification) arg0).getNotification();
                                Bundle ex = n != null ? n.extras : null;
                                if (ex == null) {
                                    return result;
                                }
                                boolean fromBroadcast = ex.getBoolean(NOTIFICATION_FROM_BROADCAST, false);
                                if (fromBroadcast) {
                                    return result; // 广播来源（锁屏）走原生判定，不干预
                                }
                                int callType = ex.getInt(CALL_TYPE, -1);
                                if (callType != CALL_INCOMING) {
                                    return result; // 只处理来电
                                }
                                String deviceId = chain.getArgs().get(3) != null
                                        ? chain.getArgs().get(3).toString() : null;
                                int deviceType = -1;
                                try {
                                    Method gt = arg1.getClass().getMethod("getDeviceType");
                                    deviceType = ((Integer) gt.invoke(arg1)).intValue();
                                } catch (Throwable ignored) {
                                }
                                String oldType = typeField.get(result).toString();
                                if (deviceType == 1) {
                                    typeField.set(result, "voip");
                                    MiflowLog.i("HookCallVoipType: incoming call type msg->voip, deviceType=" + deviceType
                                            + ", oldType=" + oldType + ", fromBroadcast=" + fromBroadcast);
                                } else {
                                    // iPhone 等非小米设备：保持 msg（iOS App 只认 msg，改 voip 会连通知都不显示）
                                    MiflowLog.i("HookCallVoipType: deviceType=" + deviceType
                                            + " not xiaomi, keep msg (oldType=" + oldType + "), iOS app only shows msg");
                                }
                            } catch (Throwable t) {
                                MiflowLog.e("HookCallVoipType: intercept failed, pass through", t);
                            }
                            return result;
                        }
                    });
            MiflowLog.i("HookCallVoipType: installed on NotificationBuilder.buildPlainMessage");
        } catch (ClassNotFoundException e) {
            MiflowLog.w("HookCallVoipType: NotificationBuilder not found (version mismatch?), skip");
        } catch (Throwable t) {
            MiflowLog.e("HookCallVoipType: install failed", t);
        }
    }
}
