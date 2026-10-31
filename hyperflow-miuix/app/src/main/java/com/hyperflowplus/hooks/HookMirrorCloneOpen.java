package com.hyperflowplus.hooks;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * beta65：分身通知点击双开 —— mirror 进程「源头单点修复」。
 *
 * 反编译实锤（跨屏协同服务 18.02.01-26.09.10，com.xiaomi.mirror）：
 *
 *   对端点击通知 → NotificationInteractionMessage(type=7) →
 *   MirrorNotificationListenerService.u(messageId)：
 *     ① V.E().U0(...) 建投屏屏并按包名 openAppMirrorByRelay →
 *        RelayAppLocalHelper.startActivityInner → SystemOsHelper.h(sendPendingIntentCompat)
 *        → send 镜像自建的 u0 主应用 PendingIntent（带投屏 displayId）→ 主微信被拉起 ✗
 *        （日志实锤：PendingIntent.send 栈 = SystemOsHelper.h ← s0.E0 ← s0.C ← d0.a）
 *     ② 完成回调 a(l0,pkg,cn,pi,Z)：o0.e().l(...) 把 u0 task 关联到投屏屏，
 *        再 send 通知原始 contentIntent（微信分身自带的 user 999 PI）→ 分身被开 ✓
 *   两条线并发 = 主微信 + 分身同时出现 = 双开。
 *
 * 之前在 system_server 下游拦 startActivity / moveRootTaskToDisplay，信息已丢失
 * （caller 都是 mirror、task 都是 u0），只能靠时序猜，所以时好时坏。
 * 本修复回到信息还在的源头，全程只改「输入参数」，不动控制流：
 *
 *   Hook A（置标记）：MirrorNotificationListenerService 内部分发（参数
 *     NotificationInteractionMessage，稳定类名）：type=7 时用 messageId 查
 *     messageId→sbn map（ConcurrentHashMap 字段，稳定类型），sbn.user==999
 *     （或 extras 带 hf_is_clone）→ 置一次性 guard。
 *   Hook B（改 send）：SystemOsHelper.h(PendingIntent, Bundle)（sendPendingIntentCompat，
 *     稳定类名）：guard 生效期间，该 PI 是 mirror 同进程自建（getIntent() 可读），
 *     包名 ∈ 支持应用集且 999 已装 → 就地把参数换成 user 999 重建的 PI。
 *     读不到 intent = 外部跨 uid PI（微信 999 自建，本来就开分身）→ 保守放行。
 *   Hook C（防搬 task）：MirrorNotificationListenerService 的 static 完成回调
 *     （参数含 PendingIntent+ComponentName）：guard 生效期间把 packageName 参数
 *     置空 → 跳过 o0.e().l(...) 对 u0 task 的搬运/关联；send 分身 PI 照常执行。
 *
 * 点击主微信通知（sbn user=0）不置 guard，零影响；正常流转不走点击链路，零影响。
 *
 * 需要 LSPosed 作用域勾选 com.xiaomi.mirror。
 */
public class HookMirrorCloneOpen {

    private static final String HF_IS_CLONE = "hf_is_clone";
    private static final int USER_CLONE = 999;

    /** Hook B 用的一次性 guard：分身点击后的自建 u0 PI 替换窗口 */
    private static volatile long sGuardSend = 0;
    /** Hook C 用的一次性 guard：分身点击后的 u0 task 搬运跳过窗口 */
    private static volatile long sGuardRelay = 0;
    private static final long GUARD_MS = 15000;

    /**
     * beta68（真·根因）：「这次打开的是 999 分身」的确认窗口。
     *
     * 反编译 + 双份日志实锤的真实机制：
     *   ① SystemOsHelper.h send 通知自带的 user 999 分身 PI → 分身被打开（正确）
     *   ② 同时 ShareNotificationHelper 按【包名】找 task（它不知道有 999 这回事），
     *      找到 u0 主微信的 task → SystemUtils.moveRootTaskToDisplayCompat
     *      → IActivityTaskManager.moveRootTaskToDisplay → 主微信 task 被搬到投屏屏显示
     *   ①+② 并发 = 双开。主微信不是被「拉起」，是被「搬过来」的
     *   （这也解释了「时好时坏」：取决于主微信 task 是否还在后台）。
     *
     * 所以修复不在 send 层（PI 本来就是对的 999），而在搬运层：
     * 确认本次是分身点击后，禁止把 u0 主应用 task 搬到投屏屏。
     */
    private static volatile long sCloneOpening = 0;
    private static final long CLONE_WINDOW_MS = 6000;

    private static final Set<String> TARGET_PKGS = new HashSet<String>() {{
        add("com.tencent.mm");
        add("com.tencent.mobileqq");
        add("com.ss.android.lark");
        add("com.larksuite.cn");
        add("com.alibaba.android.rimet");
        add("com.tencent.wework");
    }};

    public static void install(ClassLoader cl) {
        hookDispatch(cl);
        hookSendPendingIntentCompat(cl);
        hookCompletionCallback(cl);
        hookTaskMove(cl);
    }

    // ------------------------------------------------------------------
    // Hook A：消息分发置 guard
    // ------------------------------------------------------------------
    private static void hookDispatch(ClassLoader cl) {
        try {
            Class<?> svc = Class.forName(
                    "com.xiaomi.mirror.notification.MirrorNotificationListenerService", false, cl);
            Class<?> msgCls = Class.forName(
                    "com.xiaomi.mirror.message.NotificationInteractionMessage", false, cl);
            final Field fType = msgCls.getField("type");
            final Field fMsgId = msgCls.getField("messageId");

            // messageId→sbn map：按类型定位（ConcurrentHashMap 字段），不写死混淆字段名
            Field mapF = null;
            for (Field f : svc.getDeclaredFields()) {
                if (ConcurrentHashMap.class.isAssignableFrom(f.getType())) {
                    mapF = f;
                    mapF.setAccessible(true);
                    break;
                }
            }
            if (mapF == null) {
                MiflowLog.w("[MIRROR-FIX] dispatch skip: no ConcurrentHashMap field");
                return;
            }
            final Field fMap = mapF;

            // 分发方法在内部类（extends Binder）里，参数含 NotificationInteractionMessage
            int hooked = 0;
            for (Class<?> inner : svc.getDeclaredClasses()) {
                for (Method m : inner.getDeclaredMethods()) {
                    boolean match = false;
                    for (Class<?> p : m.getParameterTypes()) {
                        if (p == msgCls) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) {
                        continue;
                    }
                    final Field fMapLocal = fMap, fTypeLocal = fType, fMsgIdLocal = fMsgId;
                    m.setAccessible(true);
                    XposedEntry.get().hook(m)
                            .setExceptionMode(ExceptionMode.PROTECTIVE)
                            .intercept(new Hooker() {
                                @Override
                                public Object intercept(Chain chain) throws Throwable {
                                    try {
                                        armGuardFromMessage(chain, fMapLocal, fTypeLocal, fMsgIdLocal);
                                    } catch (Throwable t) {
                                        MiflowLog.w("[MIRROR-FIX] dispatch observe failed: " + t.getMessage());
                                    }
                                    return chain.proceed();
                                }
                            });
                    hooked++;
                }
            }
            MiflowLog.i("[MIRROR-FIX] HookA dispatch hooked " + hooked + " methods");
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-FIX] hookDispatch failed: " + t.getMessage());
        }
    }

    /** type=7（完整打开路径）且对应原始通知是分身 → 置一次性 guard */
    private static void armGuardFromMessage(Chain chain, Field fMap, Field fType, Field fMsgId) throws Throwable {
        if (!Config.isCloneTransferEnabled()) {
            return;
        }
        List<Object> args = chain.getArgs();
        Object msg = args == null ? null : args.get(0);
        if (msg == null) {
            return;
        }
        int type = fType.getInt(msg);
        if (type != 7) {
            return; // type=6 轻量路径纯 send 通知原 PI（999 自带），无双开，不处理
        }
        String messageId = (String) fMsgId.get(msg);
        if (messageId == null) {
            return;
        }
        // 通过 this$0 拿外部 service 实例，再查 messageId→sbn map
        Object service = chain.getThisObject();
        if (service == null) {
            return;
        }
        Object outer = null;
        for (Field f : service.getClass().getDeclaredFields()) {
            String n = f.getName();
            if (n.startsWith("this$") || n.startsWith("this$0")) {
                f.setAccessible(true);
                outer = f.get(service);
                break;
            }
        }
        if (outer == null) {
            return;
        }
        Object map = fMap.get(outer);
        if (!(map instanceof ConcurrentHashMap)) {
            return;
        }
        Object sbn = ((ConcurrentHashMap) map).get(messageId);
        if (sbn == null) {
            MiflowLog.d("[MIRROR-FIX] type=7 but no sbn for " + messageId);
            return;
        }
        if (isCloneSbn(sbn)) {
            long now = System.currentTimeMillis();
            sGuardSend = now;
            sGuardRelay = now;
            MiflowLog.i("[MIRROR-FIX] clone notification click armed (messageId=" + messageId + ")");
        }
    }

    /** sbn.user==999 或 extras 带 hf_is_clone 即分身通知 */
    private static boolean isCloneSbn(Object sbn) throws Throwable {
        Method getUser = sbn.getClass().getMethod("getUser");
        Object uh = getUser.invoke(sbn);
        if (uh != null) {
            Method hc = uh.getClass().getMethod("hashCode");
            if (((Integer) hc.invoke(uh)).intValue() == USER_CLONE) {
                return true;
            }
        }
        Method getNotification = sbn.getClass().getMethod("getNotification");
        Object n = getNotification.invoke(sbn);
        if (n != null) {
            Field extrasF = n.getClass().getField("extras");
            Object extras = extrasF.get(n);
            if (extras instanceof Bundle) {
                return ((Bundle) extras).getBoolean(HF_IS_CLONE, false);
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Hook B：sendPendingIntentCompat —— 自建 u0 PI 就地替换为 999
    // ------------------------------------------------------------------
    private static void hookSendPendingIntentCompat(ClassLoader cl) {
        try {
            Class<?> sys = Class.forName("com.xiaomi.mirror.utils.SystemOsHelper", false, cl);
            int hooked = 0;
            for (Method m : sys.getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 2 || pt[0] != PendingIntent.class || pt[1] != Bundle.class) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                try {
                                    redirectPendingIntent(chain);
                                } catch (Throwable t) {
                                    MiflowLog.w("[MIRROR-FIX] send redirect failed: " + t.getMessage());
                                }
                                return chain.proceed();
                            }
                        });
                hooked++;
            }
            MiflowLog.i("[MIRROR-FIX] HookB sendPendingIntentCompat hooked " + hooked + " methods");
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-FIX] hookSendPendingIntentCompat failed: " + t.getMessage());
        }
    }

    private static void redirectPendingIntent(Chain chain) throws Throwable {
        List<Object> args = chain.getArgs();
        if (args == null || args.isEmpty() || !(args.get(0) instanceof PendingIntent)) {
            return;
        }
        PendingIntent pi = (PendingIntent) args.get(0);

        // 无条件观测：creator 包名/user 是公开 API（跨进程/跨设备可读），intent 内容在
        // system uid（mirror）下通常可读。三者叠加可一锤定音判断「这次 send 的 PI 是主还是分身」。
        String creatorPkg = null;
        int userId = -1;
        String intentPkg = null;
        boolean cloneFlag = false;
        try {
            creatorPkg = pi.getCreatorPackage();
            android.os.UserHandle uh = pi.getCreatorUserHandle();
            if (uh == null) {
                userId = -1;
            } else {
                try {
                    // getIdentifier() 是 @hide，编译不可见但运行时存在；反射取
                    userId = ((Integer) android.os.UserHandle.class
                            .getMethod("getIdentifier").invoke(uh)).intValue();
                } catch (Throwable t2) {
                    userId = uh.hashCode(); // UserHandle.hashCode() 即 userId
                }
            }
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-FIX] read creator failed: " + t.getMessage());
        }
        try {
            // getIntent() 是 @hide，反射取（同进程 PI 可读；跨 uid 会抛 SecurityException）
            Intent it = (Intent) PendingIntent.class.getMethod("getIntent").invoke(pi);
            if (it != null) {
                cloneFlag = it.getBooleanExtra(HF_IS_CLONE, false);
                if (it.getPackage() != null) {
                    intentPkg = it.getPackage();
                } else if (it.getComponent() != null) {
                    intentPkg = it.getComponent().getPackageName();
                }
            }
        } catch (Throwable t) {
            // 跨 uid 读不到（非 system）→ 忽略，靠 creator 字段兜底
        }
        String pkg = intentPkg != null ? intentPkg : creatorPkg;
        MiflowLog.i("[MIRROR-FIX] h() creator=" + creatorPkg + " user=" + userId
                + " intentPkg=" + intentPkg + " clone=" + cloneFlag);

        if (!Config.isCloneTransferEnabled()) {
            return;
        }

        // beta68 真·根因：PI 的 creator user 已是 999（分身自带 PI，日志实锤），
        // send 层无需改动 —— 但要置标记，禁止随后把 u0 主应用 task 搬到投屏屏，
        // 那才是主微信冒出来的真正来源。点主微信通知时 user=0，不会置标记，零误伤。
        if (pkg != null && TARGET_PKGS.contains(pkg) && userId == USER_CLONE) {
            sCloneOpening = System.currentTimeMillis();
            MiflowLog.i("[MIRROR-FIX] clone open confirmed user=999 (" + pkg
                    + ") -> block u0 task move " + CLONE_WINDOW_MS + "ms");
            return;
        }

        // 兜底：分身信号存在但 user 在跨设备链路里丢了（落到 u0）→ 就地重建为 999。
        boolean isCloneSignal = cloneFlag || userId == USER_CLONE;
        if (pkg == null || !TARGET_PKGS.contains(pkg) || !isCloneSignal) {
            return;
        }
        if (!new File("/data/user/" + USER_CLONE + "/" + pkg).exists()) {
            return; // 未开双开的应用
        }
        PendingIntent newPi = rebuildAsClone(pkg);
        if (newPi != null) {
            args.set(0, newPi);
            Config.bump(Config.CNT_CLONE);
            MiflowLog.i("[MIRROR-FIX] clone PI user lost(0), redirected to 999: " + pkg);
        }
    }

    /** 同进程自建 PI 可读 getIntent()；跨 uid 读会抛 SecurityException → 返回 null（保守放行） */
    private static String readPiPackage(PendingIntent pi) {
        if (pi == null) {
            return null;
        }
        try {
            Intent it = (Intent) PendingIntent.class.getMethod("getIntent").invoke(pi);
            if (it == null) {
                return null;
            }
            if (it.getPackage() != null) {
                return it.getPackage();
            }
            ComponentName cn = it.getComponent();
            return cn == null ? null : cn.getPackageName();
        } catch (Throwable t) {
            MiflowLog.d("[MIRROR-FIX] readPiPackage blocked (external PI): " + t.getMessage());
            return null;
        }
    }

    /** 用「打开分身应用」的隐式 Intent 造 user 999 的 PI（同 HookCloneBypass beta61 方案） */
    private static PendingIntent rebuildAsClone(String pkg) {
        try {
            Class<?> atCls = Class.forName("android.app.ActivityThread");
            Context ctx = (Context) atCls.getMethod("currentApplication").invoke(null);
            if (ctx == null) {
                return null;
            }
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setPackage(pkg);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launch.putExtra(HF_IS_CLONE, true);
            Object uh = Class.forName("android.os.UserHandle")
                    .getMethod("of", int.class).invoke(null, USER_CLONE);
            Method gam = PendingIntent.class.getMethod(
                    "getActivityAsUser", Context.class, int.class, Intent.class,
                    int.class, Bundle.class, android.os.UserHandle.class);
            // FLAG_UPDATE_CURRENT(0x08000000) | FLAG_IMMUTABLE(0x04000000)
            return (PendingIntent) gam.invoke(null, ctx, 0, launch, 0x0C000000, null, uh);
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-FIX] rebuildAsClone failed: " + t.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Hook C：完成回调 —— guard 期间置空 packageName，跳过 u0 task 搬运
    // ------------------------------------------------------------------
    private static void hookCompletionCallback(ClassLoader cl) {
        try {
            Class<?> svc = Class.forName(
                    "com.xiaomi.mirror.notification.MirrorNotificationListenerService", false, cl);
            int hooked = 0;
            for (Method m : svc.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                boolean hasPi = false, hasCn = false;
                for (Class<?> p : m.getParameterTypes()) {
                    if (p == PendingIntent.class) {
                        hasPi = true;
                    }
                    if (p == ComponentName.class) {
                        hasCn = true;
                    }
                }
                if (!hasPi || !hasCn) {
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
                                    long guard = sGuardRelay;
                                    if (Config.isCloneTransferEnabled() && guard != 0
                                            && System.currentTimeMillis() - guard <= GUARD_MS
                                            && args != null) {
                                        // 回调形如 a(device, packageName, cn, pi, display)：
                                        // packageName 置空 → 跳过 o0.e().l(cn,0,display,false)
                                        // 对主空间 task 的搬运/关联；pi.send（999 分身）照常。
                                        for (int i = 0; i < args.size(); i++) {
                                            if (args.get(i) instanceof String) {
                                                args.set(i, "");
                                                break;
                                            }
                                        }
                                        sGuardRelay = 0; // 一次性
                                        MiflowLog.i("[MIRROR-FIX] completion callback: u0 task relay skipped, clone PI send kept");
                                    }
                                } catch (Throwable t) {
                                    MiflowLog.w("[MIRROR-FIX] completion cb failed: " + t.getMessage());
                                }
                                return chain.proceed();
                            }
                        });
                hooked++;
            }
            MiflowLog.i("[MIRROR-FIX] HookC completion callback hooked " + hooked + " methods");
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-FIX] hookCompletionCallback failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Hook D（beta68 真·根因）：分身点击期间，禁止把 u0 主应用 task 搬到投屏屏
    // ------------------------------------------------------------------
    private static void hookTaskMove(ClassLoader cl) {
        try {
            // 框架 Binder 代理类，永不混淆、跨版本稳定
            //（HookMirrorObserve 已实锤 hook 成功：ATM proxy hooked 6 methods）
            Class<?> proxy = Class.forName("android.app.IActivityTaskManager$Stub$Proxy", false, cl);
            int hooked = 0;
            for (Method m : proxy.getDeclaredMethods()) {
                if (!m.getName().contains("moveRootTaskToDisplay")) {
                    continue;
                }
                m.setAccessible(true);
                final Method mm = m;
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                try {
                                    long t = sCloneOpening;
                                    if (t != 0 && System.currentTimeMillis() - t <= CLONE_WINDOW_MS) {
                                        List<Object> as = chain.getArgs();
                                        int taskId = extractTaskId(as);
                                        int userId = queryTaskUserId(taskId);
                                        // 只拦主空间(u0) task：主微信才不该出现在投屏屏；
                                        // 分身(999) task 放行，让分身正常上投屏屏。
                                        if (userId == 0) {
                                            MiflowLog.i("[MIRROR-FIX] BLOCKED u0 task move "
                                                    + mm.getName() + " taskId=" + taskId + " userId=0");
                                            return null; // 吞掉主微信 task 搬运
                                        }
                                        MiflowLog.i("[MIRROR-FIX] allow task move taskId=" + taskId
                                                + " userId=" + userId + " (clone/non-u0)");
                                    }
                                } catch (Throwable e) {
                                    MiflowLog.w("[MIRROR-FIX] task move check failed: " + e.getMessage());
                                }
                                return chain.proceed();
                            }
                        });
                hooked++;
            }
            MiflowLog.i("[MIRROR-FIX] HookD task-move hooked " + hooked + " methods");
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-FIX] hookTaskMove failed: " + t.getMessage());
        }
    }

    /** 从 moveRootTaskToDisplay(int taskId, int displayId[, boolean]) 取第一个 int = taskId */
    private static int extractTaskId(List<Object> args) {
        if (args == null) {
            return -1;
        }
        for (Object a : args) {
            if (a instanceof Integer) {
                return ((Integer) a).intValue();
            }
        }
        return -1;
    }

    /**
     * 反查 taskId 属于哪个 user（0=主空间，999=分身）。
     * mirror 是 system uid，可调 IActivityTaskManager.getTaskInfo(taskId)；
     * TaskInfo.userId 是 @hide 字段，运行时存在，反射取。
     */
    private static int queryTaskUserId(int taskId) {
        if (taskId < 0) {
            return -1;
        }
        try {
            Class<?> atm = Class.forName("android.app.ActivityTaskManager");
            Object svc = atm.getMethod("getService").invoke(null);
            Object info = svc.getClass().getMethod("getTaskInfo", int.class).invoke(svc, taskId);
            if (info != null) {
                Field f = info.getClass().getDeclaredField("userId");
                f.setAccessible(true);
                return f.getInt(info);
            }
        } catch (Throwable t) {
            MiflowLog.w("[MIRROR-FIX] queryTaskUserId(" + taskId + ") failed: " + t.getMessage());
        }
        return -1;
    }
}
