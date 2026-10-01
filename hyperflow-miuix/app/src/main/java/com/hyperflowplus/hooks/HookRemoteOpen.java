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
 * 功能②-2 点击分身通知 → 打开 999 空间微信/QQ（V0.4.43）。
 *
 * 机制：接收端（非 root 设备）点击分身通知 → 系统「跨设备镜像打开应用」把
 * 「打开应用」请求发回发送端 → 小米互联服务（com.milink.service）在 system_server
 * 执行 startActivity，系统按主空间解析包名 → 打开主空间微信/QQ。
 *
 * 本 hook 在 system_server 拦截 startActivity：
 *   条件 = 调用者是 com.milink.service（远程打开请求）&& 目标包 ∈ 微信/QQ
 *          && userId == 0 && 该应用在 999 空间存在（已开双开）
 *   → 把 userId 改写为 999 → 直接打开多开微信/QQ。
 *   其余情况（本地正常打开、其他应用、未开双开）全部走系统原逻辑，绝不误伤。
 *
 * 需要模块在 LSPosed 作用域勾选「android（系统框架）」。
 */
public class HookRemoteOpen {

    /** 适配的迷你应用包名（微信/QQ；其余暂不适配，走系统原逻辑开主空间） */
    private static final Set<String> TARGET_PKGS = new HashSet<String>() {{
        add("com.tencent.mm");
        add("com.tencent.mobileqq");
    }};

    /** 分身空间用户 id（MIUI 应用双开基于 user 999） */
    private static final int USER_CLONE = 999;

    public static void installSystem(ClassLoader cl) {
        try {
            Class<?> ams = Class.forName("com.android.server.am.ActivityManagerService", false, cl);
            int found = 0;
            for (Method m : ams.getDeclaredMethods()) {
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
            MiflowLog.i("HookRemoteOpen[system] installed, hooked " + found + " startActivity methods");
        } catch (Throwable t) {
            MiflowLog.e("HookRemoteOpen[system] install failed", t);
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
            // 1) 调用者必须是小米互联服务（远程镜像打开请求）；本机桌面/应用自己打开不拦截
            String caller = findCallingPackage(args);
            if (!"com.milink.service".equals(caller)) {
                return chain.proceed();
            }
            // 2) 目标包必须是微信/QQ
            Intent intent = findIntent(args);
            if (intent == null || intent.getComponent() == null) {
                return chain.proceed();
            }
            String pkg = intent.getComponent().getPackageName();
            if (!TARGET_PKGS.contains(pkg)) {
                return chain.proceed();
            }
            // 3) 当前请求是主空间（userId==0）且该应用已开双开（999 空间存在）
            int userId = findUserId(args);
            if (userId != 0) {
                return chain.proceed();
            }
            if (!new File("/data/user/" + USER_CLONE + "/" + pkg).exists()) {
                MiflowLog.d("clone open: " + pkg + " not dual-installed, fallback to main user");
                return chain.proceed();
            }
            // 4) 改写 userId → 999，让 system_server 在分身空间打开微信/QQ
            setUserId(args, USER_CLONE);
            MiflowLog.d("clone open redirected: " + pkg + " → user " + USER_CLONE);
            Config.bump(Config.CNT_CLONE);
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
