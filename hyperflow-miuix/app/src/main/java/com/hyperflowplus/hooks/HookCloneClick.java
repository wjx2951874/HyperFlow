package com.hyperflowplus.hooks;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

/**
 * 分身通知点击直达分身微信（V0.4.7）。
 *
 * 点击链路（已反编译确认）：
 *   接收端点击流转通知 → NotificationActionService →
 *   MiuiSynergySdk 跨设备请求发送端 → NotificationTransHandler.handleNotification →
 *   BridgeForListenerAndTransfer.query(notificationKey) →
 *   NotifTransListenerService.getPendingIntent(key) →
 *   getActiveNotifications([key]) → 取 contentIntent → launchAppFromPendingIntent。
 *
 * 问题：HookCloneBypass.markCloneKey 给分身通知 key 加了 "hf_clone_" 前缀，
 * 流转消息里的 notificationKey 带前缀，但发送端通知系统里只有原始 key
 * （"包名:id:999"），query 按带前缀的 key 查不到 → 点击失效或回退主应用。
 *
 * 修复：hook getPendingIntent，参数带 "hf_clone_" 前缀时剥掉前缀再查，
 * 命中发送端状态栏里分身微信（user 999）的原始通知，返回其 contentIntent
 * （本就指向分身微信），点击即直达分身空间，不再跳主微信。
 */
public class HookCloneClick {

    public static void install(ClassLoader cl) {
        try {
            Class<?> svc = Class.forName(
                    "com.xiaomi.dist.notification.listener.NotifTransListenerService", false, cl);
            Method m = svc.getDeclaredMethod("getPendingIntent", String.class);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            Object arg = chain.getArg(0);
                            if (arg instanceof String) {
                                String key = (String) arg;
                                if (key.startsWith(HookCloneBypass.HF_CLONE_PREFIX)) {
                                    String orig = key.substring(HookCloneBypass.HF_CLONE_PREFIX.length());
                                    MiflowLog.d("clone click: strip prefix -> " + orig);
                                    // 以还原后的原始 key 调用原方法（命中发送端分身微信 user999 通知）
                                    return chain.callOriginalMethod(new Object[]{orig});
                                }
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCloneClick installed");
        } catch (Throwable t) {
            MiflowLog.e("HookCloneClick install failed", t);
        }
    }
}
