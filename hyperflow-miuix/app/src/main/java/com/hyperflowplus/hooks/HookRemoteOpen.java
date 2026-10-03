package com.hyperflowplus.hooks;

import android.content.Intent;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能②-2 点击分身通知 → 打开 999 空间微信/QQ/飞书/钉钉/企业微信（V0.4.43 引入，
 * V0.5.1 重写，V0.5.4 扩展适配飞书/钉钉/企业微信）。
 *
 * 机制：接收端（非 root 设备）点击分身通知 → 系统「跨设备镜像打开应用」把
 * 「打开应用」请求发回发送端 → 小米互联服务（com.milink.service）在 system_server
 * 执行 startActivity，系统按主空间解析包名 → 打开主空间应用。
 *
 * 本 hook 在 system_server 拦截 startActivity：
 *   条件 = 调用者是 com.milink.service（远程打开请求）或本 App 消息页 && 目标包 ∈
 *          支持应用集（微信/QQ/飞书/钉钉/企业微信，与小米互联「支持的应用」列表对齐）
 *          && userId == 0（或无 userId 参数的默认主空间）&& 该应用在 999 空间存在（已开双开）
 *   → 把 userId 改写为 999 → 直接打开多开应用。
 *   其余情况（本地正常打开、其他应用、未开双开）全部走系统原逻辑，绝不误伤。
 *
 * V0.5.1 重写要点（用户实测修复）：
 *   - 加 ALLOWED_CALLERS / EXCLUDED_CALLERS 白黑名单（此前条件过宽，点击主空间通知也会被改写）
 *   - findUserId 对「无 userId 参数重载」返回 -1 也视为主空间
 *   - 无 userId 参数时吞掉原调用 return null，改用 IActivityTaskManager.startActivityAsUser
 *     11 参重载反射以 user 999 重发；反射失败兜底 proceed 走原逻辑，绝不误伤本地打开
 *
 * 需要模块在 LSPosed 作用域勾选「android（系统框架）」。
 */
public class HookRemoteOpen {

    /**
     * 适配的迷你应用包名（与小米互联「支持的应用」列表中的第三方 App 对齐）。
     * 微信/QQ 为 V0.4.43 首批；V0.5.4 扩展飞书/钉钉/企业微信（含飞书国际版 Lark）。
     * 其余应用暂不适配，走系统原逻辑开主空间（可随时按需添加，无需改其他逻辑）。
     */
    private static final Set<String> TARGET_PKGS = new HashSet<String>() {{
        add("com.tencent.mm");              // 微信
        add("com.tencent.mobileqq");        // QQ
        add("com.ss.android.lark");         // 飞书
        add("com.larksuite.cn");            // 飞书国际版 Lark
        add("com.alibaba.android.rimet");   // 钉钉
        add("com.tencent.wework");          // 企业微信
    }};

    /** 允许改写为分身的调用方：小米互联（远程镜像打开）+ 本 App 消息页点击 */
    private static final Set<String> ALLOWED_CALLERS = new HashSet<String>() {{
        add("com.milink.service");
        add("com.hyperflowplus");
    }};

    /** 明确排除的调用方（本机桌面/系统 launcher 手动打开主空间应用，绝不改写） */
    private static final Set<String> EXCLUDED_CALLERS = new HashSet<String>() {{
        add("com.miui.home");
        add("com.android.launcher3");
        add("com.miui.systemui");
        add("android");
    }};

    /** 分身空间用户 id（MIUI 应用双开基于 user 999） */
    private static final int USER_CLONE = 999;

    /**
     * 双类 hook（v0.6.0）：Android 12+ 的 Activity 启动主路径在
     * ActivityTaskManagerService（ATMS），AMS.startActivity 在多数机型已不被调用。
     * 只 hook AMS 时 milink 的"远程打开"请求会漏网 → 先按 user 0 打开主空间应用（"先开 0"），
     * 后续再被某个重载改写才开 999（"再开 999"）。两处都 hook 后，第一发请求
     * 就被改到 999，主空间 0 不再出现。
     *
     * 幂等性：改写 userId=999 后，AMS 内部委托给 ATMS 的调用携带 user 999，
     * 两个 hook 的 redirectToClone 都会因 userId != 0（==999）直接 proceed，
     * 不会二次改写/二次重发 → 天然幂等，不会"双 999"。
     */
    public static void installSystem(ClassLoader cl) {
        String[] hostClasses = {
                "com.android.server.am.ActivityManagerService",   // 经典路径
                "com.android.server.wm.ActivityTaskManagerService" // Android 12+ 主路径（v0.6.0 补）
        };
        for (String host : hostClasses) {
            try {
                Class<?> cls = Class.forName(host, false, cl);
                int found = 0;
                for (Method m : cls.getDeclaredMethods()) {
                    // 拦截所有带 Intent 参数的 startActivity / startActivityAsUser（多版本签名兜底）
                    if (!m.getName().equals("startActivity") && !m.getName().equals("startActivityAsUser")) {
                        continue;
                    }
                    if (!hasIntentParam(m)) {
                        continue;
                    }
                    try {
                        m.setAccessible(true);
                        XposedEntry.get().hook(m)
                                .setExceptionMode(ExceptionMode.PROTECTIVE)
                                .intercept(new Hooker() {
                                    @Override
                                    public Object intercept(Chain chain) throws Throwable {
                                        return redirectToClone(chain);
                                    }
                                });
                        found++;
                    } catch (Throwable ignored) {
                    }
                }
                MiflowLog.i("HookRemoteOpen[" + host + "] installed, hooked " + found + " startActivity methods");
            } catch (Throwable t) {
                MiflowLog.w("HookRemoteOpen[" + host + "] not available: " + t.getMessage());
            }
        }
    }

    private static boolean hasIntentParam(Method m) {
        for (Class<?> t : m.getParameterTypes()) {
            if (Intent.class.isAssignableFrom(t)) {
                return true;
            }
        }
        return false;
    }

    private static Object redirectToClone(Chain chain) throws Throwable {
        List<Object> args = chain.getArgs();
        if (args == null || args.isEmpty()) {
            return chain.proceed();
        }
        try {
            if (!Config.isCloneTransferEnabled()) {
                return chain.proceed();
            }
            // 1) 调用者必须属于允许集（小米互联远程打开 / 本 App 消息页）；
            //    明确排除桌面/系统（手动开主空间绝不改写）
            String caller = findCallingPackage(args);
            if (caller == null || EXCLUDED_CALLERS.contains(caller)) {
                return chain.proceed();
            }
            if (!ALLOWED_CALLERS.contains(caller)) {
                return chain.proceed();
            }
            // 2) 目标包必须是支持应用集（微信/QQ/飞书/钉钉/企业微信）
            Intent intent = findIntent(args);
            if (intent == null || intent.getComponent() == null) {
                return chain.proceed();
            }
            String pkg = intent.getComponent().getPackageName();
            if (!TARGET_PKGS.contains(pkg)) {
                return chain.proceed();
            }
            // 3) 当前请求是主空间（userId==0 或签名无 userId 参数=默认主空间）
            //    且该应用已开双开（999 空间存在）
            int userId = findUserId(args);
            if (userId != 0 && userId != -1) {
                return chain.proceed();
            }
            if (!new File("/data/user/" + USER_CLONE + "/" + pkg).exists()) {
                MiflowLog.d("clone open: " + pkg + " not dual-installed, fallback to main user");
                return chain.proceed();
            }
            if (userId == 0) {
                // 4a) 有 userId 参数：改写为 999，让 system_server 在分身空间打开
                setUserId(args, USER_CLONE);
                MiflowLog.d("clone open redirected: " + pkg + " → user " + USER_CLONE);
                Config.bump(Config.CNT_CLONE);
            } else {
                // 4b) 签名无 userId 参数（系统默认主空间打开）：
                //     吞掉原调用，改用 IActivityTaskManager.startActivityAsUser 重发到 999。
                //     反射失败/不可用时兜底走原逻辑，绝不误伤本地打开。
                if (relaunchAsClone(chain, intent, pkg)) {
                    MiflowLog.d("clone open relaunched: " + pkg + " → user " + USER_CLONE);
                    Config.bump(Config.CNT_CLONE);
                    return null;
                }
            }
        } catch (Throwable t) {
            MiflowLog.w("redirectToClone failed: " + t.getMessage());
        }
        return chain.proceed();
    }

    /** 找 callingPackage 参数（String 类型且值 = com.milink.service 的那个） */
    private static String findCallingPackage(List<Object> args) {
        for (Object a : args) {
            if (a instanceof String) {
                String s = (String) a;
                if ("com.milink.service".equals(s)) {
                    return s;
                }
            }
        }
        return null;
    }

    private static Intent findIntent(List<Object> args) {
        for (Object a : args) {
            if (a instanceof Intent) {
                return (Intent) a;
            }
        }
        return null;
    }

    /** 找 userId 参数：签名末尾的 int（startActivity 系列最后一个 int 基本都是 userId） */
    private static int findUserId(List<Object> args) {
        for (int i = args.size() - 1; i >= 0; i--) {
            if (args.get(i) instanceof Integer) {
                return (Integer) args.get(i);
            }
        }
        return -1;
    }

    /**
     * 无 userId 参数重载（startActivity(intent)）的兜底：吞掉原调用，
     * 通过 IActivityTaskManager.startActivityAsUser 在分身空间(999)重发打开请求。
     * 任何反射失败都返回 false，由调用方走原逻辑。
     */
    private static boolean relaunchAsClone(Chain chain, Intent intent, String pkg) {
        try {
            Class<?> atmCls = Class.forName("android.app.IActivityTaskManager");
            Object atm = atmCls.getMethod("getService").invoke(null);
            if (atm == null) {
                return false;
            }
            Method startAsUser = null;
            for (Method m : atmCls.getDeclaredMethods()) {
                if (m.getName().equals("startActivityAsUser") && m.getParameterCount() == 11) {
                    startAsUser = m;
                    break;
                }
            }
            if (startAsUser == null) {
                return false;
            }
            startAsUser.setAccessible(true);
            // startActivityAsUser(IApplicationThread caller, String callingPackage, Intent intent,
            //   String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags,
            //   ProfilerInfo profilerInfo, Bundle bOptions, int userId)
            startAsUser.invoke(atm, new Object[]{
                    null, "com.milink.service", intent, null, null, null, -1, 0,
                    null, null, USER_CLONE
            });
            return true;
        } catch (Throwable t) {
            MiflowLog.w("relaunchAsClone failed: " + t.getMessage());
            return false;
        }
    }

    /** 把 userId 参数改写为分身空间 */
    private static void setUserId(List<Object> args, int userId) {
        for (int i = args.size() - 1; i >= 0; i--) {
            if (args.get(i) instanceof Integer) {
                args.set(i, userId);
                return;
            }
        }
    }
}
