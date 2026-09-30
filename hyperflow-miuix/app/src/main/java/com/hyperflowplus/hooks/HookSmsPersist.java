package com.hyperflowplus.hooks;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.RootExec;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能③ 流转短信持久化（V0.2 libxposed 重写；V0.2.5 改为直取入参注入）。
 *
 * 背景：接收端 SmsMessageHandler.handleMiMessage() 会把流转短信写入
 *   content://com.android.mms.flow.provider/messageflow（含来源设备名），
 *   但 OS4 短信App 没有展示"流转消息"的界面 → 数据在、看不见。
 *
 * 模块策略（两层，互补）：
 *   A. flow 归档：WebUI 内直接读 flow provider 展示（MIUI 风格），
 *      每条显示「时间｜来自设备」（来源设备在时间后紧跟，正文纯净）。
 *   B. 真实收件箱注入（本类）：V0.2.5 起**直接从 hook 入参 NotificationData
 *      反射取 号码/正文/设备名**（不再依赖 flow 查询的写入时序），
 *      以 root 注入 content://sms/inbox；入参读取失败时回退 flow 最新一条。
 *      号码为纯数字（验证码/106 号段等）才注入，正文为纯净短信内容。
 */
public class HookSmsPersist {

    /** 接收端从传输数据 focusParam 里解析出的原始发信号码（发送端 hook 注入，V0.3.14） */
    private static volatile String lastSmsNumber = null;

    /** 去重（V0.3.15）：同 address+body 在 6 秒内只注入一次，防 milink 双通道重复写 */
    private static volatile String dedupKey = "";
    private static volatile long dedupTime = 0;

    private static final String PROVIDER_AUTHORITY = "com.android.mms.flow.provider";
    private static final String PATH = "messageflow";
    private static final Uri FLOW_URI = Uri.parse("content://" + PROVIDER_AUTHORITY + "/" + PATH);

    public static void install(ClassLoader cl) {
        Hooker hooker = new Hooker() {
            @Override
            public Object intercept(Chain chain) throws Throwable {
                Object result = chain.proceed(); // 先执行原逻辑（写 flow）
                if (!Config.isSmsPersistEnabled()) {
                    return result;
                }
                Config.bump(Config.CNT_SMS);
                Context ctx = Config.getContext();
                if (ctx != null) {
                    // 优先：从入参 NotificationData 直接取数据（V0.2.5）
                    boolean handled = false;
                    java.util.List<Object> args = chain.getArgs();
                    if (args != null && !args.isEmpty() && args.get(0) != null) {
                        handled = injectFromParam(ctx, args.get(0));
                    }
                    if (!handled) {
                        // 兜底：flow 最新一条
                        persistLatestFlow(ctx);
                    }
                }
                return result;
            }
        };

        // 小米流转短信（ref=xiaomi）
        try {
            Class<?> smsHandler = Class.forName("com.xiaomi.dist.notification.handler.handler.SmsMessageHandler", false, cl);
            Class<?> notifData = Class.forName("com.xiaomi.dist.notification.handler.data.NotificationData", false, cl);
            Method m = smsHandler.getDeclaredMethod("handleMiMessage", notifData);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(hooker);
            MiflowLog.i("HookSmsPersist[mi] installed");
        } catch (Throwable t) {
            MiflowLog.e("HookSmsPersist[mi] install failed", t);
        }

        // V0.3.14：接收端在 NotificationData 生成前，从传输数据 focusParam 读出
        // 发送端注入的原始号码（协议本身不带号码，见 NotificationMessage 字段）
        try {
            Class<?> proc = Class.forName("com.xiaomi.dist.notification.trans.client.receive.NotificationProcessor", false, cl);
            Class<?> msg = Class.forName("com.xiaomi.dist.notification.common.data.NotificationMessage", false, cl);
            Class<?> plain = Class.forName("com.xiaomi.dist.notification.trans.NotificationTransMessage$MessageContent$PlainMessage", false, cl);
            Method m = proc.getDeclaredMethod("generateNotificationData", Context.class, msg, plain);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            Object nm = chain.getArg(1);
                            if (nm != null) {
                                try {
                                    String fp = (String) nm.getClass().getMethod("getFocusParam").invoke(nm);
                                    if (fp != null) {
                                        String num = normalizeNumber(fp);
                                        if (num != null) {
                                            lastSmsNumber = num;
                                            MiflowLog.d("sender number captured: " + num);
                                        }
                                    }
                                } catch (Throwable ignored) {
                                }
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookSmsPersist[number] installed");
        } catch (Throwable t) {
            MiflowLog.e("HookSmsPersist[number] install failed", t);
        }

        // 苹果互联流转短信（ref=iphone）
        try {
            Class<?> smsHandler = Class.forName("com.xiaomi.dist.notification.handler.handler.SmsMessageHandler", false, cl);
            Class<?> notifData = Class.forName("com.xiaomi.dist.notification.handler.data.NotificationData", false, cl);
            Method m = smsHandler.getDeclaredMethod("handleMessage", notifData);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(hooker);
            MiflowLog.i("HookSmsPersist[iphone] installed");
        } catch (Throwable t) {
            MiflowLog.e("HookSmsPersist[iphone] install failed", t);
        }
    }

    /**
     * V0.3.13：直接从入参反射取 号码/正文/时间/设备名 并注入（字段名按反编译定稿：
     * NotificationData{ title, message, date, verifyCode, sourceDeviceInfo{deviceName,...} }，
     * 此前误用 flow 表列名 content_* 导致恒失败，只能走 fallback）。
     * 返回是否已尝试处理。
     */
    private static boolean injectFromParam(Context ctx, Object arg) {
        try {
            String title = getFieldStr(arg, "title");
            if (title == null || title.isEmpty()) {
                MiflowLog.w("param title empty, fallback to flow");
                return false;
            }
            String number = normalizeNumber(title);
            if (number == null) {
                number = lastSmsNumber; // 发送端塞入的原始号段
            }
            if (number == null) {
                // 兜底：title 是服务商名称（腾讯科技/小米）——写入 address 列保证进收件箱
                number = title.trim();
                MiflowLog.d("inbox inject with sender-name: " + number);
            }
            // 正文：message 优先，其次 subtitle（部分短信正文在副标题）
            String desc = getFieldStr(arg, "message");
            if (desc == null || desc.isEmpty()) {
                desc = getFieldStr(arg, "subtitle");
            }
            if (desc == null) {
                desc = "";
            }
            String timeStr = getFieldStr(arg, "date");
            long dateMs = parseFlowTime(timeStr);
            // V0.3.15：过滤系统流转回执（milink 自己的"流转成功"提醒，无发信号码）
            if (isSystemReceipt(title, number)) {
                MiflowLog.d("skip system receipt: " + title);
                return true;
            }
            if (!dedup(number, desc)) {
                MiflowLog.d("dedup skip (param): " + number);
                return true;
            }
            boolean ok = insertSms(ctx, number, desc, dateMs);
            if (ok) {
                Config.bump(Config.CNT_SMS_INJECT);
                MiflowLog.d("injected via param: " + number + " device=" + deviceNameOf(arg));
            } else {
                MiflowLog.w("inject via param failed (su?)");
            }
            return true;
        } catch (Throwable t) {
            MiflowLog.w("injectFromParam failed, fallback to flow: " + t.getMessage());
            return false;
        }
    }

    /** 入参 sourceDeviceInfo.deviceName（嵌套反射） */
    private static String deviceNameOf(Object arg) {
        try {
            Object sdi = getField(arg, "sourceDeviceInfo");
            if (sdi == null) {
                return "未知设备";
            }
            Field f = sdi.getClass().getDeclaredField("deviceName");
            f.setAccessible(true);
            Object v = f.get(sdi);
            return v == null ? "未知设备" : String.valueOf(v);
        } catch (Throwable t) {
            return "未知设备";
        }
    }

    private static String getFieldStr(Object o, String name) {
        Object v = getField(o, name);
        return v == null ? null : String.valueOf(v);
    }

    private static Object getField(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读取最新一条 flow 记录，解析并注入收件箱（V0.2.5 兜底） */
    private static void persistLatestFlow(Context ctx) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(FLOW_URI, null, null, null, "_id DESC LIMIT 1");
            if (c == null || !c.moveToFirst()) {
                MiflowLog.w("flow query empty");
                return;
            }
            String deviceName = getCol(c, "content_device_name", "Xiaomi 设备");
            String title = getCol(c, "content_title", "");
            String desc = getCol(c, "content_description", "");
            String timeStr = getCol(c, "content_time", "");

            if (title == null || title.isEmpty()) {
                return;
            }
            // 号码兜底链：容错解析 → 发送端原始号段 → 服务商名称（保证写入）
            String number = normalizeNumber(title);
            if (number == null) {
                number = lastSmsNumber;
            }
            if (number == null) {
                number = title.trim();
                MiflowLog.d("inbox inject with sender-name (flow): " + number);
            }
            // V0.2.8：不再限制号码长度 —— 所有带号码短信都注入
            long dateMs = parseFlowTime(timeStr);
            String body = desc == null ? "" : desc;
            if (isSystemReceipt(title, number)) {
                MiflowLog.d("skip system receipt (flow): " + title);
                return;
            }
            if (!dedup(number, body)) {
                MiflowLog.d("dedup skip (flow): " + number);
                return;
            }
            boolean ok = insertSms(ctx, number, body, dateMs);
            if (ok) {
                Config.bump(Config.CNT_SMS_INJECT);
                MiflowLog.d("injected to inbox: " + number + " from " + deviceName);
            } else {
                MiflowLog.w("inject to inbox failed: " + number);
            }
        } catch (Throwable t) {
            MiflowLog.e("persistLatestFlow failed", t);
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String getCol(Cursor c, String name, String def) {
        int idx = c.getColumnIndex(name);
        if (idx < 0) {
            return def;
        }
        String v = c.getString(idx);
        return v == null ? def : v;
    }

    /**
     * V0.3.15：注入优先走 milink 进程自身权限（系统应用通常可直写 sms/inbox），
     * 被拒（SecurityException）再回退 root content insert。
     */
    private static boolean insertSms(Context ctx, String address, String body, long dateMs) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("address", address);
            cv.put("body", body);
            cv.put("date", Long.valueOf(dateMs));
            cv.put("read", Integer.valueOf(1));
            cv.put("type", Integer.valueOf(1));
            android.net.Uri u = ctx.getContentResolver().insert(
                    Uri.parse("content://sms/inbox"), cv);
            if (u != null) {
                MiflowLog.d("inbox insert direct ok: " + address);
                return true;
            }
            MiflowLog.w("direct insert returned null, fallback su");
        } catch (SecurityException se) {
            MiflowLog.w("direct insert denied (" + se.getMessage() + "), fallback su");
        } catch (Throwable t) {
            MiflowLog.w("direct insert error, fallback su: " + t.getMessage());
        }
        return RootExec.insertSms(ctx, address, body, dateMs);
    }

    /** 同 address+body 在 6 秒内视为重复（milink 双通道/回调重复触发） */
    private static boolean dedup(String address, String body) {
        long now = System.currentTimeMillis();
        String k = address + "|" + (body == null ? "" : body);
        if (k.equals(dedupKey) && now - dedupTime < 6000) {
            return false;
        }
        dedupKey = k;
        dedupTime = now;
        return true;
    }

    /** 系统流转回执：无数字号码且标题是系统提醒语（流转/同步/提醒等），不入收件箱 */
    private static boolean isSystemReceipt(String title, String number) {
        if (number != null && !number.isEmpty()) {
            return false; // 有号码的正常短信
        }
        if (title == null) {
            return true;
        }
        String t = title.trim();
        return t.contains("流转") || t.contains("同步") || t.contains("提醒")
                || t.contains("通知") || t.contains("已送达") || t.contains("成功");
    }

    /** 提取纯数字号码（V0.3.13 容错：+86 / 空格 / 横线 / 括号 / 短号前的 * 等常见格式）；仍是联系人名称则返回 null */
    private static String normalizeNumber(String title) {
        String t = title.trim();
        t = t.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                .replace("+86", "").replace("（", "").replace("）", "");
        if (t.startsWith("+")) {
            t = t.substring(1);
        }
        if (t.startsWith("86") && t.length() > 11) {
            t = t.substring(2);
        }
        if (t.length() < 4) {
            return null; // 过短不可能是号码
        }
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch < '0' || ch > '9') {
                return null;
            }
        }
        return t;
    }

    /** flow 的 content_time 形如 20260927T112518；解析失败用当前时间 */
    private static long parseFlowTime(String timeStr) {
        if (timeStr != null && !timeStr.isEmpty()) {
            try {
                return new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).parse(timeStr).getTime();
            } catch (Throwable ignored) {
                try {
                    return Long.parseLong(timeStr);
                } catch (Throwable ignored2) {
                }
            }
        }
        return System.currentTimeMillis();
    }
}
