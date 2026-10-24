package com.hyperflowplus.hooks;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① iPhone voip 接听强制放行（milink 进程）。
 *
 * 背景（官方 2026-10-24 澎湃OS 3 最新升级宣布 iPhone 可接听小米来电，依赖 iOS 端
 * "小米互联服务" App 注册 relayCall 服务 com.ios.phone；但该服务当前未注册，
 * milink 的 isRemoteVoipSupported(iphoneId, 11) 查服务拿不到 data → voip supported=false
 * → 转发消息只带 "msg" 类型 → iPhone 只收通知卡片、无 CallKit 接听 UI）。
 *
 * 本 hook：仅对 deviceType==11（iPhone，日志实锤 ECD92C64 deviceType=11）强制
 * isRemoteVoipSupported 返回 true，让 milink 把消息标记为 "voip" 类型推送，
 * 由 iOS 端小米互联服务收到后决定是否弹 CallKit 接听（实测定论）。
 * Xiaomi 手机（deviceType==1）保持原判定不动（接收端 OS 版本不够则本就无接听能力，不强改）。
 *
 * 门控：force_iphone_voip（默认开）。
 * 注意：本 hook 只改远端 voip 能力判定，不碰 NOTIFICATION_FROM_BROADCAST 标志
 * （历史教训 beta25：手动打标志导致错乱卡，见 HookCallVoip 注释，勿再犯）。
 */
public class HookCallVoipForce {

    private static final int DEVICE_TYPE_IPHONE = 11;

    public static void install(ClassLoader cl) {
        if (!Config.isForceIphoneVoipEnabled()) {
            MiflowLog.i("HookCallVoipForce: force_iphone_voip off, skip");
            return;
        }
        try {
            Class<?> cls = cl.loadClass("com.xiaomi.dist.notification.listener.voip.VoipTransHelper");
            Method m = cls.getDeclaredMethod("isRemoteVoipSupported", String.class, int.class);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                Object arg1 = chain.getArgs().get(1);
                                if (arg1 instanceof Integer && ((Integer) arg1).intValue() == DEVICE_TYPE_IPHONE) {
                                    MiflowLog.i("HookCallVoipForce: deviceType=11 (iPhone) voip forced true, original=" + result);
                                    return Boolean.TRUE;
                                }
                            } catch (Throwable t) {
                                MiflowLog.e("HookCallVoipForce: intercept failed, pass through", t);
                            }
                            return result;
                        }
                    });
            MiflowLog.i("HookCallVoipForce: installed on VoipTransHelper.isRemoteVoipSupported");
        } catch (ClassNotFoundException e) {
            MiflowLog.w("HookCallVoipForce: VoipTransHelper not found (version mismatch?), skip");
        } catch (Throwable t) {
            MiflowLog.e("HookCallVoipForce: install failed", t);
        }
    }
}
