package com.hyperflowplus.hooks;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 临时观测 hook（诊断用，不改任何行为）——挂在 com.xiaomi.mirror 进程。
 *
 * 目的：复现「点分身通知 → 跳主微信」一次，看清本机到底是
 *   (a) mirror 自己 Context.startActivity 重新起 u0 主微信，还是
 *   (b) 只 send 远端带来的 999 PendingIntent。
 * 只 hook 不混淆、跨版本稳定的框架 API（Context.startActivity / PendingIntent.send），
 * 混淆类（RelayAppLocalHelper 等）尽力附加，失败静默。
 *
 * 诊断完成后本文件可整体移除，不参与正式逻辑。
 */
public class HookMirrorObserve {

    public static void install(ClassLoader cl) {
        hookStartActivity(cl);
        hookPendingIntentSend(cl);
        hookObfuscatedRelay(cl);
        hookAtmProxy(cl);
        MiflowLog.i("HookMirrorObserve installed (diagnostic, read-only)");
    }

    /** 观测 mirror 进程内所有 IActivityTaskManager.startActivity* Binder 代理调用（框架类不混淆，全路径兜底） */
    private static void hookAtmProxy(ClassLoader cl) {
        try {
            Class<?> proxy = Class.forName("android.app.IActivityTaskManager$Stub$Proxy", false, cl);
            int found = 0;
            for (Method m : proxy.getDeclaredMethods()) {
                if (!m.getName().startsWith("startActivity")) {
                    continue;
                }
                boolean hasIntent = false;
                for (Class<?> t : m.getParameterTypes()) {
                    if (Intent.class.isAssignableFrom(t)) {
                        hasIntent = true;
                        break;
                    }
                }
                if (!hasIntent) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                try {
                                    StringBuilder sb = new StringBuilder();
                                    for (Object a : chain.getArgs()) {
                                        if (a instanceof Intent) {
                                            Intent it = (Intent) a;
                                            sb.append(" intent=").append(it.getComponent())
                                              .append(" flags=0x").append(Integer.toHexString(it.getFlags()))
                                              .append(" userId=").append(it.getIntExtra("android.intent.extra.USER_ID", -1));
                                        } else {
                                            sb.append(" ").append(a == null ? "null" : a.getClass().getSimpleName());
                                        }
                                    }
                                    MiflowLog.i("[MIRROR-OBS] ATM." + m.getName() + sb
                                            + " stack=" + stackTop());
                                } catch (Throwable t) {
                                    MiflowLog.w("[MIRROR-OBS] ATM observe failed: " + t.getMessage());
                                }
                                return chain.proceed();
                            }
                        });
                found++;
            }
            MiflowLog.i("[MIRROR-OBS] ATM proxy hooked " + found + " startActivity* methods");
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-OBS] hookAtmProxy failed: " + t.getMessage());
        }
    }

    /** 观测 mirror 进程内所有 Context.startActivity：打 component/pkg/flags/目标屏 + 调用栈 */
    private static void hookStartActivity(ClassLoader cl) {
        try {
            Class<?> ctx = Class.forName("android.content.Context", false, cl);
            for (Method m : ctx.getDeclaredMethods()) {
                if (!m.getName().equals("startActivity")) {
                    continue;
                }
                Class<?>[] pt = m.getParameterTypes();
                boolean ok = false;
                for (Class<?> p : pt) {
                    if (Intent.class.isAssignableFrom(p)) {
                        ok = true;
                    }
                }
                if (!ok) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                try {
                                    List<Object> args = chain.getArgs();
                                    Intent intent = null;
                                    Bundle opts = null;
                                    for (Object a : args) {
                                        if (a instanceof Intent) {
                                            intent = (Intent) a;
                                        } else if (a instanceof Bundle) {
                                            opts = (Bundle) a;
                                        }
                                    }
                                    int displayId = -1;
                                    if (opts != null) {
                                        displayId = opts.getInt("android.activity.launchDisplayId", -1);
                                    }
                                    String comp = intent == null ? "null" : String.valueOf(intent.getComponent());
                                    String pkg = intent == null ? "null" : intent.getPackage();
                                    int flags = intent == null ? 0 : intent.getFlags();
                                    MiflowLog.i("[MIRROR-OBS] startActivity comp=" + comp
                                            + " pkg=" + pkg + " flags=0x" + Integer.toHexString(flags)
                                            + " launchDisplayId=" + displayId
                                            + " stack=" + stackTop());
                                } catch (Throwable t) {
                                    MiflowLog.w("[MIRROR-OBS] startActivity observe failed: " + t.getMessage());
                                }
                                return chain.proceed();
                            }
                        });
            }
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-OBS] hookStartActivity failed: " + t.getMessage());
        }
    }

    /** 观测 mirror 进程内所有 PendingIntent.send：打 toString/requestCode/目标屏 + 调用栈 */
    private static void hookPendingIntentSend(ClassLoader cl) {
        try {
            Class<?> pi = Class.forName("android.app.PendingIntent", false, cl);
            for (Method m : pi.getDeclaredMethods()) {
                if (!m.getName().equals("send")) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                try {
                                    List<Object> args = chain.getArgs();
                                    int requestCode = -1;
                                    Bundle opts = null;
                                    for (Object a : args) {
                                        if (a instanceof Integer) {
                                            requestCode = (Integer) a;
                                        } else if (a instanceof Bundle) {
                                            opts = (Bundle) a;
                                        }
                                    }
                                    int displayId = opts == null ? -1
                                            : opts.getInt("android.activity.launchDisplayId", -1);
                                    MiflowLog.i("[MIRROR-OBS] PendingIntent.send rc=" + requestCode
                                            + " displayId=" + displayId
                                            + " stack=" + stackTop());
                                } catch (Throwable t) {
                                    MiflowLog.w("[MIRROR-OBS] send observe failed: " + t.getMessage());
                                }
                                return chain.proceed();
                            }
                        });
            }
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-OBS] hookPendingIntentSend failed: " + t.getMessage());
        }
    }

    /** 尽力附加：本次解包的混淆 RelayAppLocalHelper（C0689s0）关键方法，失败静默 */
    private static void hookObfuscatedRelay(ClassLoader cl) {
        String[][] targets = {
                {"com.xiaomi.mirror.relay.C0689s0", "n0"},   // openAppMirrorByRelay
                {"com.xiaomi.mirror.relay.C0689s0", "D0"},   // startActivity
                {"com.xiaomi.mirror.relay.C0689s0", "E0"},   // startActivityInner
                {"H3.g", "x"},                                // ShareNotificationHelper.launchTargetApp
                {"com.xiaomi.mirror.provider.CallProvider", "o"}, // launchTargetApp
        };
        for (String[] t : targets) {
            try {
                Class<?> cls = Class.forName(t[0], false, cl);
                for (Method m : cls.getDeclaredMethods()) {
                    if (!m.getName().equals(t[1])) {
                        continue;
                    }
                    m.setAccessible(true);
                    XposedEntry.get().hook(m)
                            .setExceptionMode(ExceptionMode.PROTECTIVE)
                            .intercept(new Hooker() {
                                @Override
                                public Object intercept(Chain chain) throws Throwable {
                                    try {
                                        MiflowLog.i("[MIRROR-OBS] " + t[0] + "." + t[1]
                                                + " args=" + argSummary(chain.getArgs()));
                                    } catch (Throwable ignored) {
                                    }
                                    return chain.proceed();
                                }
                            });
                }
            } catch (Throwable ignored) {
                // 混淆类名跨版本漂移，静默跳过
            }
        }
    }

    private static String argSummary(List<Object> args) {
        if (args == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("[");
        for (Object a : args) {
            if (sb.length() > 1) {
                sb.append(",");
            }
            sb.append(a == null ? "null" : a.getClass().getSimpleName() + "=" + abbrev(String.valueOf(a)));
        }
        return sb.append("]").toString();
    }

    private static String abbrev(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    /** 取调用栈里 mirror/系统相关的前几帧，定位是哪条自起路径 */
    private static String stackTop() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StackTraceElement e : st) {
            String cn = e.getClassName();
            if (cn.startsWith("com.xiaomi.mirror") || cn.startsWith("android.app")
                    || cn.contains("RelayApp") || cn.contains("ShareNotification")
                    || cn.contains("SystemOsHelper")) {
                sb.append(cn).append(".").append(e.getMethodName()).append(";");
                if (++n >= 5) {
                    break;
                }
            }
        }
        return sb.length() == 0 ? "(mirror frame not found)" : sb.toString();
    }
}
