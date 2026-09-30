package com.hyperflowplus.hooks;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.service.notification.StatusBarNotification;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * V0.3.14 发送端补号：流转协议（NotificationMessage）本身不含原始号码，
 * 发送侧把短信通知 title 当"发送人"（系统识别出的服务商名），号段只在源设备
 * 短信收件箱里。本 hook 在发送侧构造完成后，查询本机收件箱最新一条短信，
 * 把原始 address 反射写入 NotificationMessage.focusParam（接收端原本不消费该字段），
 * 由接收端 HookSmsPersist 读出用于注入。查不到则原样传输，不影响流转。
 */
public class HookSmsSenderEnrich {

    private static final Set<String> SMS_PKGS = new HashSet<>(Arrays.asList(
            "com.android.mms", "com.miui.mms", "com.android.phone",
            "com.android.messaging", "com.google.android.apps.messaging"));

    public static void install(ClassLoader cl) {
        try {
            Class<?> builder = Class.forName("com.xiaomi.dist.notification.listener.handle.NotificationBuilder", false, cl);
            Class<?> sbn = StatusBarNotification.class;
            Class<?> devInfo = Class.forName("com.xiaomi.continuity.networking.TrustedDeviceInfo", false, cl);
            Method m = builder.getDeclaredMethod("buildNotificationMessage",
                    sbn, String.class, devInfo, String.class);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            Object arg = chain.getArg(0);
                            if (result == null || !(arg instanceof StatusBarNotification)) {
                                return result;
                            }
                            String pkg = ((StatusBarNotification) arg).getPackageName();
                            if (!SMS_PKGS.contains(pkg)) {
                                return result;
                            }
                            Context ctx = Config.getContext();
                            if (ctx == null) {
                                return result;
                            }
                            String number = queryLatestSmsAddress(ctx);
                            if (number == null || number.length() == 0) {
                                return result;
                            }
                            try {
                                Field f = result.getClass().getDeclaredField("focusParam");
                                f.setAccessible(true);
                                f.set(result, number);
                                MiflowLog.d("sender enriched: " + pkg + " -> " + number);
                            } catch (Throwable t) {
                                MiflowLog.w("focusParam set failed: " + t.getMessage());
                            }
                            return result;
                        }
                    });
            MiflowLog.i("HookSmsSenderEnrich installed");
        } catch (Throwable t) {
            MiflowLog.e("HookSmsSenderEnrich install failed", t);
        }
    }

    /** 本机收件箱最新一条的 address（源设备收到刚流转短信时即最新） */
    private static String queryLatestSmsAddress(Context ctx) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(Uri.parse("content://sms/inbox"),
                    new String[]{"address"}, null, null, "date DESC LIMIT 1");
            if (c == null || !c.moveToFirst()) {
                return null;
            }
            String a = c.getString(0);
            return a == null ? null : a.trim();
        } catch (Throwable t) {
            MiflowLog.w("query sms inbox failed (permission?): " + t.getMessage());
            return null;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
