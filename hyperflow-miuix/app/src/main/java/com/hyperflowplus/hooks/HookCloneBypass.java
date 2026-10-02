package com.hyperflowplus.hooks;

import android.app.Notification;
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

    /** 最近放行的通知 key → 时间戳：短窗口内同 key 二次出现拦截（防双链路重复流转）。
     *  key = 包名|标题|正文 前 96 字符（**纯内容指纹，不含 id/tag**）——
     *  v0.5.13：实测双流转的"两条"通常来自不同链路对同一 sbn 的重复处理，
     *  而重构/镜像后的 sbn id/tag 可能不同，含 id/tag 的 key 会漏拦。纯内容 key 才稳。
     *  窗口 60s（两条链路间隔毫秒级，60s 足够；同内容真消息 60s 内再次出现的概率可忽略）。
     *  除内存表外同步写跨进程共享文件：双链路若发生在不同进程（milink/dist 等），
     *  每个进程都有独立静态表，单靠内存表拦不住第二条。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> RELEASED_KEYS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DUP_WINDOW_MS = 60000L;

    /** 跨进程去重文件候选路径（按可写性依次尝试；App 检测脚本读取全部路径求并集）。
     *  v0.5.13：/data/misc 优先 —— dist 通知链路在 system_server（system uid），
     *  /data/adb 与 /data/local/tmp 通常不可写；/data/misc 属 system 可写目录。 */
    private static final String[] DUP_FILE_CANDS = {
            "/data/misc/hyperflowplus_released_keys",
            "/data/adb/hyperflowplus/released_keys",
            "/data/local/tmp/hyperflowplus_released_keys",
    };
    private static volatile String dupFileWritable = null;

    private static boolean isDuplicate(String key, long now) {
        Long last = RELEASED_KEYS.get(key);
        if (last != null && now - last < DUP_WINDOW_MS) {
            return true;
        }
        // 跨进程：读共享文件里是否已有同 key（文件行格式：时间戳|key）
        String f = dupFileWritable;
        if (f != null) {
            try {
                java.io.File file = new java.io.File(f);
                if (file.exists()) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        int sp = line.indexOf('|');
                        if (sp <= 0) continue;
                        long ts = Long.parseLong(line.substring(0, sp));
                        if (now - ts < DUP_WINDOW_MS && line.substring(sp + 1).equals(key)) {
                            br.close();
                            return true;
                        }
                    }
                    br.close();
                }
            } catch (Throwable ignored) { }
        }
        return false;
    }

    private static void recordReleased(String key, long now) {
        RELEASED_KEYS.put(key, now);
        String f = dupFileWritable;
        if (f == null) {
            // install 时可能暂不可写（如 system_server 刚启动），首次放行时重试探测
            pickDupFile();
            f = dupFileWritable;
        }
        if (f != null) {
            try {
                java.io.File file = new java.io.File(f);
                java.io.File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                // 读回旧行，仅保留窗口内的（v0.5.13：写时清理，防共享文件无限增长）；
                // 截断到 200 行，再追加新行
                java.util.List<String> keep = new java.util.ArrayList<>();
                if (file.exists()) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        int sp = line.indexOf('|');
                        if (sp <= 0) continue;
                        try {
                            if (now - Long.parseLong(line.substring(0, sp)) < DUP_WINDOW_MS) {
                                keep.add(line);
                            }
                        } catch (Throwable ignored) {
                        }
                        if (keep.size() > 200) break;
                    }
                    br.close();
                }
                java.io.FileWriter w = new java.io.FileWriter(f, false);
                for (String l : keep) w.write(l + "\n");
                w.write(now + "|" + key + "\n");
                w.close();
            } catch (Throwable ignored) { }
        }
    }

    private static void pickDupFile() {
        for (String cand : DUP_FILE_CANDS) {
            try {
                java.io.File f = new java.io.File(cand);
                java.io.File parent = f.getParentFile();
                if (parent != null) parent.mkdirs();
                java.io.FileWriter w = new java.io.FileWriter(cand, true);
                w.close();
                dupFileWritable = cand;
                return;
            } catch (Throwable ignored) { }
        }
    }

    public static void install(ClassLoader cl) {
        pickDupFile();
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
                            // 去重检查前置（v0.5.13）：必须在任何放行/跳过分支之前执行——
                            // 第二条链路可能以 group summary / 镜像等形态到达，若先去处理
                            // summary 再回来查重就拦不住。内容指纹去掉了 id/tag（重构的 sbn
                            // id/tag 可能不同），窗口 60s。
                            String titleTxt = "";
                            if (n != null && n.extras != null) {
                                CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                                CharSequence x = n.extras.getCharSequence(Notification.EXTRA_TEXT);
                                String tStr = (t == null ? "" : t).toString();
                                String xStr = (x == null ? "" : x).toString();
                                if (tStr.startsWith("【分身】")) tStr = tStr.substring(3);
                                titleTxt = tStr + "|" + xStr;
                                if (titleTxt.length() > 96) titleTxt = titleTxt.substring(0, 96);
                            }
                            String dupKey = sbn.getPackageName() + "|" + titleTxt;
                            long now = System.currentTimeMillis();
                            if (isDuplicate(dupKey, now)) {
                                MiflowLog.d("duplicate flow blocked: " + dupKey);
                                return Boolean.FALSE;
                            }
                            recordReleased(dupKey, now);
                            // group summary（flag 512）保留原逻辑，避免通知组重复折叠
                            if ((n.flags & 512) != 0) {
                                return chain.proceed();
                            }
                            if (sbn.getUser() != null && sbn.getUser().hashCode() == 999) {
                                // 分身通知：只放行，不改 user（归一会导致发送端 systemui 主空间也显示一条）。
                                // 标题加【分身】前缀（只改流转数据，发送端已显示的通知不受影响）；
                                // 接收端按分身空间处理原样展示。
                                markCloneTitle(sbn);
                                Config.bump(Config.CNT_CLONE);
                                MiflowLog.d("clone notification released: " + sbn.getPackageName());
                                return Boolean.TRUE;
                            }
                            // 其他应用（电话/短信等）全量放行
                            MiflowLog.d("all-app notification released: " + sbn.getPackageName());
                            return Boolean.TRUE;
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
                                        if (title != null && !title.toString().startsWith("【分身】")) {
                                            n.extras.putCharSequence(Notification.EXTRA_TITLE, "【分身】" + title);
                                            MiflowLog.d("clone title guard applied: 【分身】" + title);
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
                // 已加过前缀：仍写入自定义标记，供第二道兜底 hook 使用
                n.extras.putBoolean(HF_IS_CLONE, true);
                return;
            }
            n.extras.putCharSequence(Notification.EXTRA_TITLE, "【分身】" + s);
            n.extras.putBoolean(HF_IS_CLONE, true);
            MiflowLog.d("clone title marked: 【分身】" + s);
        } catch (Throwable t) {
            MiflowLog.w("markCloneTitle failed: " + t.getMessage());
        }
    }
}
