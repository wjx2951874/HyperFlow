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
            hookCredentialCapture();   // 免预填：捕获系统每次构造的锁屏凭证（真实值）
            hookAuthUi(cl);   // A-2 成功回传 HAT 后自动关闭锁屏验证输入框
            MiflowLog.i("HookTrustAuthAuto: installed on Z0.b.g/h/c (A-2, auto-close UI)");
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
                                // A-2：h()（f489k=2，Bind_B/Risk_B）命中后后台静默校验并回传真 HAT。
                                // 失败/异常一律不拦，原 UI 照常留给用户手动，不主动重试（避免罚时）。
                                if ("h".equals(name)) {
                                    startAutoVerify(chain.getArgs());
                                }
                            } catch (Throwable t) {
                                MiflowLog.e("HookTrustAuthAuto: dump/auto failed", t);
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

    // ===== A-2 静默自动校验（真实 gatekeeper，绝不伪造成功）=====
    // 进程内累计失败计数：满 MAX_FAIL 次后本进程不再自动（避免连续错密码触发系统罚时）。
    // 注意：单次连接只校验一次，不主动重复提交；计数仅用于"放弃自动、回退手动"。
    private static final java.util.concurrent.atomic.AtomicInteger failCount =
            new java.util.concurrent.atomic.AtomicInteger(0);
    private static final int MAX_FAIL = 3;

    // 锁屏验证输入框（com.xiaomi.trustservice 的 LockScreenUIActivity）弱引用 + 成功标志：
    // A-2 后台回传真 HAT 后系统侧解锁已成立，但 UI 仍挂着等用户输入（用户实测"停在弹窗"），
    // 因此成功回传后自动 finish 该输入框；若 UI 后于校验弹出，onResume 时见 authOk=true 直接关。
    private static volatile java.lang.ref.WeakReference<android.app.Activity> authUi =
            new java.lang.ref.WeakReference<>(null);
    private static volatile boolean authOk = false;

    /** hook 锁屏验证输入框的 onResume：记录实例；A-2 已成功则立即自动关闭。 */
    private static void hookAuthUi(ClassLoader cl) {
        try {
            Class<?> act = cl.loadClass("com.xiaomi.trustservice.remoteservice.locksettings.LockScreenUIActivity");
            for (Method m : act.getDeclaredMethods()) {
                if (!m.getName().equals("onResume") || m.getParameterCount() != 0) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                try {
                                    Object self = chain.getThisObject();
                                    if (self instanceof android.app.Activity) {
                                        authUi = new java.lang.ref.WeakReference<>((android.app.Activity) self);
                                        MiflowLog.i("A-2: auth UI shown, authOk=" + authOk);
                                        if (authOk) {
                                            finishAuthUi();
                                        }
                                    }
                                } catch (Throwable t) {
                                    MiflowLog.e("A-2: ui hook err", t);
                                }
                                return chain.proceed();
                            }
                        });
                MiflowLog.i("A-2: hooked LockScreenUIActivity.onResume");
                return;
            }
            MiflowLog.w("A-2: LockScreenUIActivity.onResume not found");
        } catch (Throwable t) {
            MiflowLog.w("A-2: LockScreenUIActivity class not found, auto-close UI unavailable: " + t);
        }
    }

    /** 主线程 finish 锁屏验证输入框（A-2 成功回传 HAT 后调用）。 */
    private static void finishAuthUi() {
        try {
            final android.app.Activity a = authUi.get();
            if (a == null) {
                return;
            }
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                try {
                    if (!a.isFinishing()) {
                        a.finish();
                        MiflowLog.i("A-2: auth UI auto-closed after HAT sent");
                    }
                } catch (Throwable t) {
                    MiflowLog.e("A-2: finish auth UI err", t);
                }
            });
        } catch (Throwable t) {
            MiflowLog.e("A-2: finishAuthUi failed", t);
        }
    }

    // ===== 免预填：捕获系统每次构造的锁屏凭证（Z0.b.c(int,String)）=====
    // Android 系统不存锁屏密码明文（gatekeeper 只有不可逆 hash，root 也读不出），
    // 唯一能拿到"实际值"的时机是系统构造凭证的瞬间。trustservice 校验（授权盘输入完成
    // 或 A-2 注入）都会调 Z0.b.c(int,String)：1=createPattern / 3=createPin / 4=createPassword。
    // 这里在 proceed 后把真实凭证写入 trustservice 进程自己的 SharedPreferences，
    // App 侧（有 root）读回该文件 → 加密入库，之后 A-2 直接用，用户无需在设置页手动预填。
    // 注意：A-2 自己反射调 b.c 也会触发本 hook，记录的是注入值，幂等无害。
    private static void hookCredentialCapture() {
        try {
            for (Method m : bCls.getDeclaredMethods()) {
                if (!m.getName().equals("c")) {
                    continue;
                }
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 2 || pt[0] != int.class || pt[1] != String.class) {
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
                                    int type = (Integer) chain.getArgs().get(0);
                                    String raw = (String) chain.getArgs().get(1);
                                    captureCredential(type, raw);
                                } catch (Throwable t) {
                                    MiflowLog.e("A-2: capture credential err", t);
                                }
                                return result;
                            }
                        });
                MiflowLog.i("A-2: hooked Z0.b.c(int,String) for credential capture");
                return;
            }
            MiflowLog.w("A-2: Z0.b.c(int,String) not found, capture unavailable");
        } catch (Throwable t) {
            MiflowLog.w("A-2: hookCredentialCapture failed: " + t);
        }
    }

    private static void captureCredential(int type, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String t = type == 1 ? "pattern" : type == 4 ? "password" : type == 3 ? "pin" : null;
        if (t == null) {
            return;
        }
        android.content.SharedPreferences sp = capturedSp();
        if (sp == null) {
            return;
        }
        // commit() 同步落盘：App 侧读取时文件必须已写入
        sp.edit()
                .putString("hf_cap_type", t)
                .putString("hf_cap_secret", "pattern".equals(t) ? "" : raw)
                .putString("hf_cap_pattern", "pattern".equals(t) ? raw : "")
                .commit();
        MiflowLog.i("A-2: captured " + t + " (len=" + raw.length() + ") to trustservice sp");
    }

    /** trustservice 进程自己的 SharedPreferences（App 侧 root 读取该文件）。 */
    private static android.content.SharedPreferences capturedSp() {
        try {
            android.app.Application app = (android.app.Application)
                    Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (app == null) {
                return null;
            }
            return app.getSharedPreferences("hyperflow_captured", android.content.Context.MODE_PRIVATE);
        } catch (Throwable t) {
            MiflowLog.e("A-2: capturedSp failed", t);
            return null;
        }
    }

    private static void startAutoVerify(List<Object> args) {
        try {
            if (failCount.get() >= MAX_FAIL) {
                MiflowLog.i("A-2: failCount>=" + MAX_FAIL + ", give up auto this process");
                return;
            }
            if (args == null || args.size() < 4) {
                return;
            }
            final Object proxy = args.get(0);
            final byte[] challenge = args.get(1) instanceof byte[] ? (byte[]) args.get(1) : null;
            final String branch = String.valueOf(args.get(3));
            new Thread(() -> {
                try {
                    autoVerify(proxy, challenge, branch);
                } catch (Throwable t) {
                    MiflowLog.e("A-2: autoVerify crashed, pass through", t);
                }
            }, "hf-a2").start();
        } catch (Throwable t) {
            MiflowLog.e("A-2: startAutoVerify failed", t);
        }
    }

    private static void autoVerify(Object proxy, byte[] challenge, String branch) throws Throwable {
        String type = Config.getAuthType();
        String secret = Config.getAuthSecret();
        String pattern = Config.getAuthPattern();
        boolean has = ("pin".equals(type) || "password".equals(type)) && secret != null && !secret.isEmpty()
                || "pattern".equals(type) && pattern != null && !pattern.isEmpty();
        if (!has) {
            MiflowLog.i("A-2: no saved credential(type=" + type + "), pass through");
            return;
        }
        Object inst = readSingleton();
        if (inst == null) {
            MiflowLog.w("A-2: singleton null, pass through");
            return;
        }
        if (challenge == null) {
            MiflowLog.w("A-2: challenge null, pass through");
            return;
        }

        // f491c：LockPatternUtils 实例字段（按 simpleName 匹配，不硬编码混淆字段名）
        Object lpw = findInstanceFieldBySimpleName(inst, "LockPatternUtils");
        if (lpw == null) {
            MiflowLog.w("A-2: LockPatternUtils field not found, pass through");
            return;
        }

        // 复用系统自己的 b.c(int,String) 构造凭证：3=createPin / 4=createPassword / 1=createPattern
        int credType = "pattern".equals(type) ? 1 : "password".equals(type) ? 4 : 3;
        String raw = "pattern".equals(type) ? pattern : secret;
        Method cM = bCls.getDeclaredMethod("c", int.class, String.class);
        cM.setAccessible(true);
        Object credential = cM.invoke(inst, credType, raw);

        // verifyCredential(cred,0,1)；SDK>=35 用 isMatched 判定
        Class<?> lc = Class.forName("com.android.internal.widget.LockscreenCredential");
        Method vf = lpw.getClass().getMethod("verifyCredential", lc, int.class, int.class);
        Object resp = vf.invoke(lpw, credential, 0, 1);
        boolean matched = (boolean) resp.getClass().getMethod("isMatched").invoke(resp);
        if (!matched) {
            int n = failCount.incrementAndGet();
            MiflowLog.w("A-2: credential mismatch, fail=" + n + "/" + MAX_FAIL + ", pass through to manual");
            return;
        }
        MiflowLog.i("A-2: verifyCredential matched, fetching HAT");

        Long handle = (Long) resp.getClass().getDeclaredMethod("getGatekeeperPasswordHandle").invoke(resp);
        long ch = le8(challenge);
        Method vgh = lpw.getClass().getMethod("verifyGatekeeperPasswordHandle", long.class, long.class, int.class);
        Object hatResp = vgh.invoke(lpw, handle, ch, 0);
        byte[] hat = (byte[]) hatResp.getClass().getDeclaredMethod("getGatekeeperHAT").invoke(hatResp);
        if (hat == null || hat.length == 0) {
            MiflowLog.w("A-2: HAT empty, pass through");
            return;
        }

        // f489k=2 代理 k：Risk_B→x(byte[])；Bind_B→j(byte[])；Bind_A→l(p0c,hat) 两参暂不自动
        String methodName = "Risk_B".equals(branch) ? "x" : "Bind_B".equals(branch) ? "j" : null;
        if (methodName == null) {
            MiflowLog.w("A-2: branch=" + branch + " not auto-mapped yet, pass through");
            return;
        }
        Method back = proxy.getClass().getMethod(methodName, byte[].class);
        back.invoke(proxy, (Object) hat);
        failCount.set(0);
        authOk = true;
        finishAuthUi();   // 系统侧已解锁，自动关闭仍挂着的验证输入框
        MiflowLog.i("A-2: HAT sent via k." + methodName + "(" + branch + "), auto ok, closing UI");
    }

    /** challenge 前8字节 little-endian 转 long（复刻 Z0.b.e）。 */
    private static long le8(byte[] b) {
        long v = 0;
        int n = Math.min(8, b.length);
        for (int i = 0; i < n; i++) {
            v |= (b[i] & 255L) << (i * 8);
        }
        return v;
    }

    private static Object findInstanceFieldBySimpleName(Object inst, String simple) {
        for (Field f : bCls.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            try {
                Object v = f.get(inst);
                if (v != null && simple.equals(v.getClass().getSimpleName())) {
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
