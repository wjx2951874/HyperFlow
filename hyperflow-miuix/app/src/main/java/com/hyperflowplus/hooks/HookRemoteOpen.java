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

    // v1.1.0-beta10：分身通知打开 Intent 上的自描述标记（HookCloneBypass.markCloneTitle 写入）。
    // 命中即确认这是"点击分身通知"发起的打开，直接改 999，不再依赖时序反推。
    private static final String HF_IS_CLONE = "hf_is_clone";

    // v1.1.0-beta9：记录最近一次 u999 打开时间（仅作标记丢失时的兜底反推）
    private static long lastCloneOpenTime = 0;

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
        add("com.xiaomi.mirror");   // V1.1.0：小米 15 等机型点击流转通知走 mirror 进程 RelayAppLocalHelper
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
                    // 拦截所有带 Intent 参数的 startActivity 系列（startActivity / startActivityAsUser /
                    // startActivityAsCaller / startActivityWithOptions / startActivityForUser 等，多版本签名兜底）。
                    // beta58：milink 打开应用的请求实测走 startActivityAsCaller/WithOptions/ForUser（beta57 只 hook
                    // startActivity/startActivityAsUser 时完全漏掉，主微信被直接打开 → 双开），必须全前缀覆盖。
                    if (!m.getName().startsWith("startActivity")) {
                        continue;
                    }
                    if (!hasIntentParam(m)) {
                        continue;
                    }
                    // beta54：按真实签名定位参数，不再"末尾找 Integer"猜 userId——
                    // startActivity 的 10/11 参重载末尾 int 是 flags（0x24000000 之类），
                    // 被 findUserId 误当成 userId → userId 非 0/-1/999 → 直接放行 → 主微信被拉起
                    // （这正是"苹果时好时坏、小米两个都开"的根因：走 AsUser 就改写成功，走 startActivity 就放行）
                    int[] idx = locateIndices(m);
                    try {
                        m.setAccessible(true);
                        XposedEntry.get().hook(m)
                                .setExceptionMode(ExceptionMode.PROTECTIVE)
                                .intercept(new Hooker() {
                                    @Override
                                    public Object intercept(Chain chain) throws Throwable {
                                        return redirectToClone(chain, idx);
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

    /**
     * 定位关键参数索引：
     * [0] = callingPackage 参数索引（startActivity 系列 Binder 签名的 index 1，callingFeatureId 紧随其后）
     * [1] = userId 参数索引：startActivityAsUser 系列的末尾 int 是 userId；startActivity 系列没有 userId
     *       参数（默认主空间 user 0），记 -1，按 0 语义处理。
     */
    private static int[] locateIndices(Method m) {
        Class<?>[] types = m.getParameterTypes();
        // callingPackage：index 1（IApplicationThread caller 之后第一个 String）；找不到时 -1 走 Binder 反查
        int pkgIdx = -1;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == String.class) {
                pkgIdx = i;
                break;
            }
        }
        // userId：带用户语义的重载（startActivityAsUser / startActivityAsCaller / startActivityForUser）
        // 末尾 int 是 userId；纯 startActivity / startActivityWithOptions 没有 userId 参数（默认主空间 0），记 -1。
        int userIdIdx = -1;
        String mn = m.getName();
        if (mn.equals("startActivityAsUser") || mn.equals("startActivityAsCaller")
                || mn.equals("startActivityForUser")) {
            for (int i = types.length - 1; i >= 0; i--) {
                if (types[i] == int.class) {
                    userIdIdx = i;
                    break;
                }
            }
        }
        return new int[]{pkgIdx, userIdIdx};
    }

    private static boolean hasIntentParam(Method m) {
        for (Class<?> t : m.getParameterTypes()) {
            if (Intent.class.isAssignableFrom(t)) {
                return true;
            }
        }
        return false;
    }

    private static Object redirectToClone(Chain chain, int[] idx) throws Throwable {
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
            String caller = findCallingPackage(args, idx);
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
            // 3) 当前请求是主空间（userId==0；startActivity 系列无 userId 参数=默认主空间 0）
            //    且该应用已开双开（999 空间存在）
            // beta54：userIdIdx==-1（startActivity 无 userId 参数）→ 按主空间 0 语义处理，
            // 不再被"末尾 flags int"误判成非 0 用户而放行（这是两个都开的根因）
            int userId = idx[1] >= 0 ? ((Integer) args.get(idx[1])).intValue() : 0;
            MiflowLog.d("clone open check: caller=" + caller + " pkg=" + pkg + " userId=" + userId
                + " argsSize=" + args.size() + " argsTypes=" + args.stream().map(o -> o == null ? "null" : o.getClass().getSimpleName()).collect(java.util.stream.Collectors.joining(",")));
            
            // 识别"分身通知点击"（V1.1.0-beta10 重构：请求自描述，摆脱时序竞态）
            // 之前的反推法（先收到 u999 打开、3 秒内再收到 u0 → 认为分身通知）有竞态：
            // 第一发 u0 经常在 u999 之前到达 → 被判成"主通知"放行 → 主微信先被拉起，
            // 随后分身再开 → "两个微信都起来"。现在优先看请求自带的 hf_is_clone 标记：
            boolean isCloneMarked = intent.getBooleanExtra(HF_IS_CLONE, false);
            if (!isCloneMarked) {
                // 标记缺失时兜底用旧的"最近 u999 打开时间"反推（兼容标记在链路中丢失的情况）
                if (userId == USER_CLONE) {
                    lastCloneOpenTime = System.currentTimeMillis();
                    MiflowLog.d("clone open: recorded clone open at " + lastCloneOpenTime);
                    return chain.proceed();
                }
                if (userId != 0) {
                    return chain.proceed();
                }
                long sinceClone = System.currentTimeMillis() - lastCloneOpenTime;
                if (sinceClone > 3000) {
                    MiflowLog.d("clone open: no clone marker & no recent clone open (" + sinceClone + "ms ago), main notification, skip");
                    return chain.proceed();
                }
            }
            if (!new File("/data/user/" + USER_CLONE + "/" + pkg).exists()) {
                MiflowLog.d("clone open: " + pkg + " not dual-installed, fallback to main user");
                return chain.proceed();
            }
            MiflowLog.d("clone open: detected clone notification (marker=" + isCloneMarked + "), redirecting to user " + USER_CLONE);
            if (idx[1] >= 0) {
                // 4a) 有 userId 参数（startActivityAsUser）：直接改写为 999
                args.set(idx[1], USER_CLONE);
                MiflowLog.d("clone open redirected: " + pkg + " → user " + USER_CLONE);
                Config.bump(Config.CNT_CLONE);
            } else {
                // 4b) startActivity 无 userId 参数（系统默认主空间打开）：
                //     吞掉原调用，改用 IActivityTaskManager.startActivityAsUser 以 999 重发。
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

    /**
     * v1.1.0-beta5：callingPackage 不是 startActivity 的参数，而是 system_server 内部通过
     * Binder.getCallingPackage() 解析的。之前从 args 里找 String 参数，永远找不到。
     * 修复：用 Binder.getCallingUid() 获取调用者 uid，再通过 IPackageManager 反查包名。
     * system_server 里没有 Application，所以用 ServiceManager.getService("package") 获取。
     *
     * v1.1.0-beta54：优先按方法签名定位的 callingPackage 参数索引直接取值
     * （startActivity/startActivityAsUser 的 Binder 签名 index 1 就是 callingPackage，
     * 跨进程 Binder 调用时该参数由系统填充，可信）；拿不到再走 uid 反查兜底。
     */
    private static String findCallingPackage(List<Object> args, int[] idx) {
        // 1) 按签名索引直接取 callingPackage（可信，且避免 ATMS 层 Binder.getCallingUid 失真成 1000）
        if (idx[0] >= 0 && idx[0] < args.size()) {
            Object v = args.get(idx[0]);
            if (v instanceof String) {
                String s = (String) v;
                if (ALLOWED_CALLERS.contains(s) || EXCLUDED_CALLERS.contains(s)) {
                    MiflowLog.d("clone open: callingPackage(param)=" + s);
                    return s;
                }
            }
        }
        // 2) 兜底：从 args 里找白/黑名单 String（兼容某些机型把 callingPackage 放其它位置）
        for (Object a : args) {
            if (a instanceof String) {
                String s = (String) a;
                if (ALLOWED_CALLERS.contains(s) || EXCLUDED_CALLERS.contains(s)) {
                    return s;
                }
            }
        }
        // 3) v1.1.0-beta5：用 Binder.getCallingUid() 获取调用者 uid，反查包名
        try {
            int callingUid = android.os.Binder.getCallingUid();
            MiflowLog.d("clone open: callingUid=" + callingUid);
            // system_server 里用 ServiceManager 获取 IPackageManager
            Object pm = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "package");
            if (pm != null) {
                Object ipm = Class.forName("android.content.pm.IPackageManager$Stub")
                    .getMethod("asInterface", android.os.IBinder.class).invoke(null, pm);
                Method getPackagesForUid = ipm.getClass().getMethod("getPackagesForUid", int.class);
                String[] pkgs = (String[]) getPackagesForUid.invoke(ipm, callingUid);
                if (pkgs != null) {
                    for (String pkg : pkgs) {
                        MiflowLog.d("clone open: uid=" + callingUid + " pkg=" + pkg);
                        if (ALLOWED_CALLERS.contains(pkg)) return pkg;
                    }
                }
            }
        } catch (Throwable t) {
            MiflowLog.w("clone open: findCallingPackage failed: " + t.getMessage());
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

    /** 把 userId 参数改写为分身空间（beta54 起按精确索引改写） */
    private static void setUserId(List<Object> args, int userIdIdx, int userId) {
        if (userIdIdx >= 0 && userIdIdx < args.size()) {
            args.set(userIdIdx, userId);
        }
    }
}
