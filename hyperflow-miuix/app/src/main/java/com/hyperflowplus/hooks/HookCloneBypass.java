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
 * 功能② 微信/QQ 分身（user 999）通知流转（V0.4.43 回退：只放行，不归一、不镜像）。
 *
 * 反编译确认（NotificationHandler）：
 *   private boolean isNotificationValid(StatusBarNotification sbn) {
 *       if (sbn.getUser().hashCode() == 999) { ... return false; }  // ← 分身被拒根源
 *       ...
 *   }
 *
 * 历史教训：
 *   - V0.2.4 加「user 归一」（反射把 sbn.user 改为主用户 0）后出现重复 bug：
 *     发送端 systemui 也持有该 sbn，user 被改后主空间通知栏多出一条一样的。
 *   - V0.4.43 镜像方案（system_server 再造 user 0 镜像通知，学双开通知桥）虽能防重复，
 *     但复杂度高且需额外作用域。用户实测确认：最初「只放行」方案本就不会让两个微信
 *     都显示，只是点击打不开分身微信。
 *
 * V0.4.43 最终方案：只放行、不碰 user——
 *   分身(999)通知 → isNotificationValid 直接返回 TRUE → 原样流转（数据保留 user 999，
 *   接收端按分身通知展示，不产生主空间重复）；标题加【分身】前缀供接收端分组。
 *   点击打开分身（拉到 999 空间微信/QQ）由 HookRemoteOpen（远程打开应用请求拦截）
 *   单独实现，与本文件解耦。
 */
public class HookCloneBypass {

    /** 自定义标记：分身通知（供第二道兜底 hook 识别，防【分身】前缀在第一道链路丢失） */
    private static final String HF_IS_CLONE = "hf_is_clone";

    public static void install(ClassLoader cl) {
        try {
            Class<?> handler = Class.forName("com.xiaomi.dist.notification.listener.handle.NotificationHandler", false, cl);
            Method m = handler.getDeclaredMethod("isNotificationValid", StatusBarNotification.class);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (!Config.isCloneTransferEnabled()) {
                                return chain.proceed();
                            }
                            Object arg = chain.getArg(0);
                            if (!(arg instanceof StatusBarNotification)) {
                                return chain.proceed();
                            }
                            StatusBarNotification sbn = (StatusBarNotification) arg;
                            Notification n = sbn.getNotification();
                            CharSequence title = n == null ? null : n.extras.getCharSequence(Notification.EXTRA_TITLE);
                            CharSequence text = n == null ? null : n.extras.getCharSequence(Notification.EXTRA_TEXT);
                            boolean hasContent = (title != null && title.length() > 0)
                                    || (text != null && text.length() > 0);
                            if (!hasContent) {
                                return chain.proceed();
                            }
                            // group summary（flag 512）保留原逻辑，避免通知组重复折叠
                            if ((n.flags & 512) != 0) {
                                return chain.proceed();
                            }
                            if (sbn.getUser() != null && sbn.getUser().hashCode() == 999) {
                                // 只放行，不改 user（归一会导致发送端 systemui 主空间也显示一条）。
                                // 标题加【分身】前缀（只改流转数据，发送端已显示的通知不受影响）；
                                // 接收端按分身空间处理原样展示。
                                markCloneTitle(sbn);
                                Config.bump(Config.CNT_CLONE);
                                MiflowLog.d("clone notification released: " + sbn.getPackageName());
                                return Boolean.TRUE;
                            }
                            // 普通用户交给原生处理，不要全量放行！
                            // 之前全量放行导致运行状态卡片（短信正在运行）也流转了。
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCloneBypass installed");
        } catch (Throwable t) {
            MiflowLog.e("HookCloneBypass install failed", t);
        }

        // 第二道兜底：发送端序列化前（buildPlainMessage）强制打【分身】前缀。
        // 即使 isNotificationValid 里的 markCloneTitle 标记在链路中丢失，
        // 只要 extras 里留有 hf_is_clone 标记，这里也能保证流转数据标题带前缀。
        try {
            Class<?> builder = Class.forName("com.xiaomi.dist.notification.listener.handle.NotificationBuilder", false, cl);
            for (Method m : builder.getDeclaredMethods()) {
                if (!m.getName().equals("buildPlainMessage") || m.getParameterCount() != 5) {
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
                                    Notification n = sbn.getNotification();
                                    if (n != null && n.extras != null
                                            && n.extras.getBoolean(HF_IS_CLONE, false)) {
                                        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                                        String pf = bracketPrefix();
                                        if (title != null && !title.toString().startsWith(pf)) {
                                            n.extras.putCharSequence(Notification.EXTRA_TITLE, pf + title);
                                            MiflowLog.d("clone title guard applied: " + pf + title);
                                        }
                                    }
                                }
                                return chain.proceed();
                            }
                        });
                MiflowLog.i("HookCloneBypass[guard] installed");
                break;
            }
        } catch (Throwable t) {
            MiflowLog.e("HookCloneBypass[guard] install failed", t);
        }
    }

    /** 当前配置的带括号标识前缀（【双开】，文字可在 App 内自定义） */
    private static String bracketPrefix() {
        return "【" + Config.getClonePrefix() + "】";
    }

    /** 双开通知标题加标识前缀（仅流转数据生效，防重复叠加） */
    private static void markCloneTitle(StatusBarNotification sbn) {
        try {
            Notification n = sbn.getNotification();
            if (n == null || n.extras == null) {
                return;
            }
            // beta61 修复：把 contentIntent 重建为 userId=999 的 PendingIntent，让接收端点击
            // 分身通知时直接开分身空间。mirror 进程实测走 PendingIntent.send（原样 send 发送端
            // 带过来的 contentIntent）——重建成功就只开 999，重建失败就开 u0 → 双开。
            //
            // 实锤根因（hf_b60_clean logcat）：beta59 反射用了不存在的 API——
            //   PendingIntent 没有 getRequestCode()/getFlags() 公开方法，也没有
            //   5 参 getActivity(Context,int,Intent,int,UserHandle) 重载（5 参是带 Bundle 的），
            //   getMethod 必然抛 NoSuchMethodException，重建失败（日志 rebuild failed: null）。
            // 正确 API 是 @hide 的 getActivityAsUser(Context,int,Intent,int,Bundle,UserHandle)。
            // requestCode/flags 原值跨进程拿不到，用合理默认值重建即可（requestCode=0、
            // flags=FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE=0xC000000），不影响"打开分身应用"目标。
            // 同时给 intent 打 hf_is_clone 标记，供 system_server 侧 PendingIntent 路径兜底识别。
            try {
                android.app.PendingIntent pi = n.contentIntent;
                if (pi == null) {
                    MiflowLog.w("markCloneTitle: contentIntent is null, skip rebuild");
                } else {
                    java.lang.reflect.Method gm = android.app.PendingIntent.class.getMethod("getIntent");
                    Intent ci = (Intent) gm.invoke(pi);
                    if (ci == null) {
                        MiflowLog.w("markCloneTitle: getIntent() returned null (cross-process restricted), skip rebuild");
                    } else {
                        ci.putExtra(HF_IS_CLONE, true);
                        // ActivityThread 是 @hide 类，公共 stub 里没有，用反射拿 currentApplication
                        Class<?> atCls = Class.forName("android.app.ActivityThread");
                        Object app = atCls.getMethod("currentApplication").invoke(null);
                        android.content.Context ctx = (android.content.Context) app;
                        if (ctx == null) {
                            MiflowLog.w("markCloneTitle: currentApplication() is null, skip rebuild");
                        } else {
                            Object uh = Class.forName("android.os.UserHandle")
                                    .getConstructor(int.class).newInstance(999);
                            java.lang.reflect.Method gam = android.app.PendingIntent.class.getMethod(
                                    "getActivityAsUser", android.content.Context.class, int.class,
                                    Intent.class, int.class, android.os.Bundle.class,
                                    android.os.UserHandle.class);
                            android.app.PendingIntent newPi = (android.app.PendingIntent) gam.invoke(
                                    null, ctx, 0, ci, 0xC000000, null, uh);
                            if (newPi == null) {
                                MiflowLog.w("markCloneTitle: getActivityAsUser returned null, skip rebuild");
                            } else {
                                n.contentIntent = newPi;
                                MiflowLog.d("clone contentIntent rebuilt -> user 999");
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                MiflowLog.w("markCloneTitle contentIntent rebuild failed: "
                        + t.getClass().getName() + ": " + t.getMessage());
            }
            CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title == null || title.length() == 0) {
                return;
            }
            String s = title.toString();
            String pf = bracketPrefix();
            if (s.startsWith(pf)) {
                // 已加过前缀：仍写入自定义标记，供第二道兜底 hook 使用
                n.extras.putBoolean(HF_IS_CLONE, true);
                return;
            }
            n.extras.putCharSequence(Notification.EXTRA_TITLE, pf + s);
            n.extras.putBoolean(HF_IS_CLONE, true);
            MiflowLog.d("clone title marked: " + pf + s);
        } catch (Throwable t) {
            MiflowLog.w("markCloneTitle failed: " + t.getMessage());
        }
    }
}
