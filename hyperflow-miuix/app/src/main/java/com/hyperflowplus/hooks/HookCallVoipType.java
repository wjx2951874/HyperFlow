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
                                // deviceType/deviceId 获取（beta49 重写，对齐反编译最新签名）：
                                //   buildPlainMessage(sbn, DeviceSubInfo, TrustedDeviceInfo, String, int)
                                //   args[1] = DeviceSubInfo（接收端设备对象，含 deviceId/deviceType）
                                //   args[4] = i10（消息体普通 int，NOT deviceType——beta45/46/47/48 拿错参数，
                                //             漂移成 148748/195737 乱值；恰好小值时优先采用 → 判定全错 → 亮屏手机号）
                                //   小米原生自己就是 deviceSubInfo.getDeviceType()（jadx 实锤方法存在且未混淆）。
                                int deviceType = -1;
                                String deviceId = null;
                                Object dsi = chain.getArgs().size() > 1
                                        ? chain.getArgs().get(1) : null; // DeviceSubInfo
                                if (dsi != null) {
                                    try {
                                        Method gt = dsi.getClass().getMethod("getDeviceType");
                                        gt.setAccessible(true);
                                        deviceType = ((Integer) gt.invoke(dsi)).intValue();
                                    } catch (Throwable t) {
                                        MiflowLog.e("HookCallVoipType: getDeviceType() reflect failed, fallback field scan", t);
                                        deviceType = findDeviceTypeFromSubInfo(dsi);
                                    }
                                    try {
                                        Method gid = dsi.getClass().getMethod("getDeviceId");
                                        gid.setAccessible(true);
                                        deviceId = (String) gid.invoke(dsi);
                                    } catch (Throwable t) {
                                        MiflowLog.e("HookCallVoipType: getDeviceId() reflect failed", t);
                                    }
                                }
                                String oldType = typeField.get(result).toString();
                                // beta45 规则（用户拍板"用原生的那个逻辑"）：
                                //   去掉 HookCallVoipForce 后，完全走原生 isVoipSupported 判定——
                                //   - 原生判定支持 voip（新款 iPhone / 小米15 / 其他 Android）→ 标 voip 全屏接听
                                //   - 原生判定不支持 voip（iPhone X 等）→ 保持 msg 降级通知
                                //   - 判定失败（deviceType 拿不到 / 反射异常 / 原生调用抛错）→
                                //     拦截本次流转（return null 不发通知）——voip 发不出来就别发通知，
                                //     避免"本来该 voip 的设备收到一条只有手机号的降级通知"。
                                // 注意：不能再用 deviceType 硬编码（11=iPhone 一律降级会误伤新款
                                // iPhone——新款也 deviceType=11 但原生判定支持 voip）。
                                boolean voipSupported = false;
                                boolean verdictOk = false;
                                if (deviceType > 0) {
                                    try {
                                        Object builderThis = chain.getThisObject();
                                        Field helperField = cls.getDeclaredField("mVoipTransHelper");
                                        helperField.setAccessible(true);
                                        Object helper = helperField.get(builderThis);
                                        Method isVoipM = helper.getClass().getMethod("isVoipSupported",
                                                StatusBarNotification.class, String.class, int.class);
                                        voipSupported = ((Boolean) isVoipM.invoke(helper,
                                                arg0, deviceId, Integer.valueOf(deviceType))).booleanValue();
                                        verdictOk = true;
                                    } catch (Throwable t) {
                                        MiflowLog.e("HookCallVoipType: native isVoipSupported call failed, drop transfer", t);
                                    }
                                }
                                if (!verdictOk) {
                                    // 判定失败：voip 发不出来就不发（用户要求），拦截本次流转
                                    MiflowLog.w("HookCallVoipType: voip verdict failed (deviceType=" + deviceType
                                            + "), drop transfer (no degraded notification for voip-capable device)");
                                    return null;
                                }
                                if (voipSupported) {
                                    typeField.set(result, "voip");
                                    MiflowLog.i("HookCallVoipType: incoming call type msg->voip (native isVoipSupported=true)"
                                            + ", deviceType=" + deviceType + ", oldType=" + oldType
                                            + ", fromBroadcast=" + fromBroadcast);
                                } else {
                                    // 原生判定明确不支持 voip（如 iPhone X）：保持 msg，对端降级为通知
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

    /** 反射 DeviceSubInfo（args[1]）内部字段找合法 deviceType：
     *  遍历实例字段，取 int 且值在 1..1000（合法 deviceType：1=手机 4=平板 11=iPhone…）。
     *  不硬编码混淆字段名（jadx 字段名运行时不保留）；找不到返回 -1。 */
    private static int findDeviceTypeFromSubInfo(Object dsi) {
        if (dsi == null) {
            return -1;
        }
        try {
            for (Field f : dsi.getClass().getDeclaredFields()) {
                if (f.getType() != int.class) {
                    continue;
                }
                f.setAccessible(true);
                int v = f.getInt(dsi);
                if (v > 0 && v <= 1000) {
                    MiflowLog.i("HookCallVoipType: deviceType=" + v + " from DeviceSubInfo field " + f.getName());
                    return v;
                }
            }
        } catch (Throwable t) {
            MiflowLog.e("HookCallVoipType: findDeviceTypeFromSubInfo failed", t);
        }
        return -1;
    }
}
