package com.hyperflowplus.hooks;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * A方案 · 第1版（A-1 观测，不抢流程、不回传）。
 *
 * 挂在 com.xiaomi.trustservice 的 LockSettingUtils（混淆类 {@code Z0.b}）会话建立方法
 * {@code g(...)}（Bind_A 分支，f489k=1）与 {@code h(...)}（Bind_B/Risk_B，f489k=2）。
 *
 * 目的：proceed() 让原流程照常走（UI 照弹、不扰乱），随后在后台遍历静态单例的全部
 * 声明字段，按"类型 + 值摘要"打印出来，用于在真实连接日志里定位：
 *   - byte[] challenge（HAT 挑战，前8字节转 long 给 verifyGatekeeperPasswordHandle）
 *   - 回传代理对象（Y0.g / remoteauthservice.k，A-2 拿 HAT 后调 c/e/v 或 l/j/x）
 *   - 静态分支标识 f489k / f490l
 *
 * 注意：jadx 给的 f494g/f492e 等字段名是占位、运行时不保留，因此这里绝不硬编码字段名，
 * 一律按遍历 + 类型匹配，A-2 再据日志结果写死反射。
 */
public class HookTrustAuthAuto {

    private static Class<?> bCls;

    public static void install(ClassLoader cl) {
        if (!Config.getBool(Config.KEY_SILENT_AUTH, false)) {
            MiflowLog.i("HookTrustAuthAuto: silent_auth disabled, skip");
            return;
        }
        try {
            bCls = cl.loadClass("Z0.b");
            hookMethod("g");
            hookMethod("h");
            MiflowLog.i("HookTrustAuthAuto: installed on Z0.b.g/h (A-1 observe only)");
        } catch (ClassNotFoundException e) {
            MiflowLog.w("HookTrustAuthAuto: Z0.b not found (version mismatch?), skip");
        } catch (Throwable t) {
            MiflowLog.e("HookTrustAuthAuto: install failed", t);
        }
    }

    private static void hookMethod(final String name) {
        for (Method m : bCls.getDeclaredMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            Class<?>[] pt = m.getParameterTypes();
            boolean hasByteArray = false;
            for (Class<?> p : pt) {
                if (p == byte[].class) {
                    hasByteArray = true;
                }
            }
            if (!hasByteArray) {
                continue;
            }
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                dumpSingleton(name, chain.getArgs());
                            } catch (Throwable t) {
                                MiflowLog.e("HookTrustAuthAuto: dump failed", t);
                            }
                            return result;
                        }
                    });
            MiflowLog.i("HookTrustAuthAuto: hooked Z0.b." + name
                    + "(" + pt.length + " args)");
            return;
        }
        MiflowLog.w("HookTrustAuthAuto: Z0.b." + name + " not found");
    }

    /** proceed 后遍历静态单例全部字段，按类型/值摘要打印。 */
    private static void dumpSingleton(String where, List<Object> args) {
        MiflowLog.i("===== A-1 session establish [" + where + "] args=" + describeArgs(args) + " =====");
        // 取静态单例字段（类型为 b 自身、非空者）
        Object inst = readSingleton();
        if (inst == null) {
            MiflowLog.w("A-1: singleton instance null after " + where);
            return;
        }
        MiflowLog.i("A-1 singleton class=" + inst.getClass().getName());
        // 实例字段
        for (Field f : bCls.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(mod)) {
                continue;
            }
            f.setAccessible(true);
            try {
                Object v = f.get(inst);
                MiflowLog.i("A-1 instField " + f.getType().getSimpleName()
                        + " " + f.getName() + " = " + summarize(v));
            } catch (Throwable t) {
                MiflowLog.w("A-1 read instField " + f.getName() + " failed: " + t);
            }
        }
        // 静态字段（分支标识等）
        for (Field f : bCls.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (!java.lang.reflect.Modifier.isStatic(mod)) {
                continue;
            }
            f.setAccessible(true);
            try {
                Object v = f.get(null);
                MiflowLog.i("A-1 staticField " + f.getType().getSimpleName()
                        + " " + f.getName() + " = " + summarize(v));
            } catch (Throwable t) {
                MiflowLog.w("A-1 read staticField " + f.getName() + " failed: " + t);
            }
        }
    }

    /** 找类型为 b 自身的静态非空字段作为单例。 */
    private static Object readSingleton() {
        for (Field f : bCls.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            try {
                Object v = f.get(null);
                if (v != null && bCls.isInstance(v)) {
                    return v;
                }
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

    private static String describeArgs(List<Object> args) {
        StringBuilder sb = new StringBuilder("[");
        if (args != null) {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(summarize(args.get(i)));
            }
        }
        return sb.append("]").toString();
    }

    private static String summarize(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof byte[]) {
            return "byte[" + ((byte[]) v).length + "]";
        }
        if (v instanceof CharSequence) {
            String s = v.toString();
            return "\"" + (s.length() > 40 ? s.substring(0, 40) + "…" : s) + "\"";
        }
        return v.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(v));
    }
}
