package com.hyperflowplus.hooks;

import android.app.Notification;
import android.os.UserHandle;
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
 * 功能② 微信/QQ 分身（user 999）通知流转（V0.2 libxposed 重写，V0.2.4 加 user 归一）。
 *
 * 反编译确认（NotificationHandler）：
 *   private boolean isNotificationValid(StatusBarNotification sbn) {
 *       if (sbn.getUser().hashCode() == 999) { ... return false; }  // ← 分身被拒根源
 *       ...
 *   }
 *
 * 拦截 isNotificationValid：通知来自 user 999 且有内容 → 直接返回 true 短路；
 * 否则交给原逻辑。注意流转链路为 isNotificationValid → isDeviceSupported，
 * 亮屏时后者由 HookForceTransfer 覆盖为 true。
 *
 * V0.2.4 修复"有声无影"：仅放行还不够——流转数据仍携带 user 999，
 * 接收端按分身空间处理 → 提示音响但状态栏不显示。
 * 放行时把 sbn 的 user 反射归一为主用户(0)，接收端即按普通通知展示。
 */
public class HookCloneBypass {

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
                                // V0.2.4：user 999 → 主用户(0)，让接收端按普通通知展示（否则有声无影）
                                normalizeUser(sbn);
                                // V0.3.15：key 加前缀 —— 主/分身同联系人通知的 key 原本相同，
                                // 分身后到会覆盖主微信已流转的消息（用户实测复现），加前缀后互不干扰
                                markCloneKey(sbn);
                                // V0.2.5：标题加【分身】前缀，接收端可区分主/次（只改回调对象，
                                // 影响后续流转序列化；发送端已显示的通知由 systemui 持有，不受影响）
                                markCloneTitle(sbn);
                                Config.bump(Config.CNT_CLONE);
                                MiflowLog.d("clone notification released: " + sbn.getPackageName());
                                return Boolean.TRUE;
                            }
                            // V0.3.13：普通用户全量放行 —— 电话/短信/所有应用的通知
                            // 都跳过 mAppNotificationFilter 过滤（默认只处理微信/QQ/短信/电话，
                            // 其中电话在本地 Voip 支持时被 filterCall 拦截、短信通知被 filterSms 拦截）
                            MiflowLog.d("all-app notification released: " + sbn.getPackageName());
                            return Boolean.TRUE;
                        }
                    });
            MiflowLog.i("HookCloneBypass installed");
        } catch (Throwable t) {
            MiflowLog.e("HookCloneBypass install failed", t);
        }
    }

    /** 把 StatusBarNotification 的 user 反射改为主用户(0)，让接收端按普通通知展示 */
    private static void normalizeUser(StatusBarNotification sbn) {
        try {
            Class<?> c = sbn.getClass();
            Field f = null;
            // Android 各版本字段名：mUser / mUserHandle
            try {
                f = c.getDeclaredField("mUser");
            } catch (NoSuchFieldException e1) {
                try {
                    f = c.getDeclaredField("mUserHandle");
                } catch (NoSuchFieldException e2) {
                    // 遍历查找 UserHandle 类型字段（防版本改名）
                    for (Field cand : c.getDeclaredFields()) {
                        if (UserHandle.class.isAssignableFrom(cand.getType())) {
                            f = cand;
                            break;
                        }
                    }
                }
            }
            if (f == null) {
                MiflowLog.w("normalizeUser: no user field found");
                return;
            }
            f.setAccessible(true);
            // 精简 android.jar 无 SYSTEM/of 编译期可见，用运行时反射取（Android 17 一定有）
            Object systemUh = UserHandle.class.getField("SYSTEM").get(null);
            f.set(sbn, systemUh);
            MiflowLog.d("clone user normalized to main user");
        } catch (Throwable t) {
            MiflowLog.w("normalizeUser failed: " + t.getMessage());
        }
    }

    /** 分身通知 key 加前缀，与主应用同联系人通知的 key 区分开（防覆盖） */
    private static void markCloneKey(StatusBarNotification sbn) {
        try {
            for (Field f : sbn.getClass().getDeclaredFields()) {
                if (f.getType() == String.class && f.getName().toLowerCase().contains("key")) {
                    f.setAccessible(true);
                    Object v = f.get(sbn);
                    if (v != null && !v.toString().startsWith("hf_clone_")) {
                        f.set(sbn, "hf_clone_" + v);
                        MiflowLog.d("clone key marked to avoid collision");
                        return;
                    }
                }
            }
            MiflowLog.w("markCloneKey: no string key field found");
        } catch (Throwable t) {
            MiflowLog.w("markCloneKey failed: " + t.getMessage());
        }
    }

    /** 分身通知标题加【分身】前缀（仅流转数据生效，防重复叠加） */
    private static void markCloneTitle(StatusBarNotification sbn) {
        try {
            Notification n = sbn.getNotification();
            if (n == null || n.extras == null) {
                return;
            }
            CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title == null || title.length() == 0) {
                return;
            }
            String s = title.toString();
            if (s.startsWith("【分身】")) {
                return; // 防同通知多次回调重复叠加
            }
            n.extras.putCharSequence(Notification.EXTRA_TITLE, "【分身】" + s);
            MiflowLog.d("clone title marked: 【分身】" + s);
        } catch (Throwable t) {
            MiflowLog.w("markCloneTitle failed: " + t.getMessage());
        }
    }
}
