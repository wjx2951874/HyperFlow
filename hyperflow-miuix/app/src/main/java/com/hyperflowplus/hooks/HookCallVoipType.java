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
 * 设备区分（beta38）：**不硬编码任何设备类型，复用原生 VoipTransHelper.isVoipSupported
 * 判定**（反编译确认）：来电 + 呼叫转移开关 + 本机支持 voip + 远端设备 relayCall flags
 * 达标 → voip；任一不满足 → 保持 msg 降级通知。
 * - 小米15：原生判定通过 → 亮屏也标 voip 全屏接听（与锁屏广播路径的原生判定完全一致）
 * - 新款 iPhone（支持 voip）：原生判定通过 → 亮屏也收 voip 来电（用户实测新款 iPhone 认 voip）
 * - iPhone X 等原生不支持的：自动保持 msg 通知，不强标
 * 锁屏（广播来源）消息类型完全走原生 buildPlainMessage 判定，本 hook 不干预（锁屏原逻辑）。
 *
 * beta37 教训：deviceType 必须从 buildPlainMessage 真实参数（args[4]）取，不能反射
 * DeviceSubInfo.getDeviceType()（该方法不存在，拿到 -1 → 原生判定恒 false → 亮屏全降级通知）。
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
                                String deviceId = chain.getArgs().size() > 3
                                        && chain.getArgs().get(3) != null
                                        ? chain.getArgs().get(3).toString() : null;
                                // deviceType 必须取 buildPlainMessage 真实参数（args[4]），
                                // 不能反射 DeviceSubInfo（beta37 教训：反射拿 -1 → 原生判定恒 false）
                                int deviceType = -1;
                                if (chain.getArgs().size() > 4
                                        && chain.getArgs().get(4) instanceof Integer) {
                                    deviceType = ((Integer) chain.getArgs().get(4)).intValue();
                                }
                                String oldType = typeField.get(result).toString();
                                // 复用原生 isVoipSupported 判定：支持 voip 才改 voip，不支持保持 msg 降级通知
                                boolean voipSupported = false;
                                try {
                                    Object builderThis = chain.getThisObject();
                                    Field helperField = cls.getDeclaredField("mVoipTransHelper");
                                    helperField.setAccessible(true);
                                    Object helper = helperField.get(builderThis);
                                    Method isVoipM = helper.getClass().getMethod("isVoipSupported",
                                            StatusBarNotification.class, String.class, int.class);
                                    voipSupported = ((Boolean) isVoipM.invoke(helper,
                                            arg0, deviceId, Integer.valueOf(deviceType))).booleanValue();
                                } catch (Throwable t) {
                                    MiflowLog.e("HookCallVoipType: native isVoipSupported call failed, keep msg", t);
                                }
                                if (voipSupported) {
                                    typeField.set(result, "voip");
                                    MiflowLog.i("HookCallVoipType: incoming call type msg->voip (native isVoipSupported=true)"
                                            + ", deviceType=" + deviceType + ", oldType=" + oldType
                                            + ", fromBroadcast=" + fromBroadcast);
                                } else {
                                    // 原生判定不支持 voip（如 iPhone X）：保持 msg，对端降级为通知
                                    MiflowLog.i("HookCallVoipType: deviceType=" + deviceType
                                            + " keep msg (native isVoipSupported=false, degrade to notification)"
                                            + ", oldType=" + oldType);
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
