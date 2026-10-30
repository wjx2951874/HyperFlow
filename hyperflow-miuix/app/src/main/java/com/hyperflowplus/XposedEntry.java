package com.hyperflowplus;

import com.hyperflowplus.hooks.HookAutoUnlock;
import com.hyperflowplus.hooks.HookCallRelay;
import com.hyperflowplus.hooks.HookCallVoip;
import com.hyperflowplus.hooks.HookCallVoipNotify;
import com.hyperflowplus.hooks.HookCallFilterPass;
import com.hyperflowplus.hooks.HookCallVoipType;
import com.hyperflowplus.hooks.HookCloneBypass;
import com.hyperflowplus.hooks.HookForceTransfer;
import com.hyperflowplus.hooks.HookKeyguardCapture;
import com.hyperflowplus.hooks.HookMirrorObserve;
import com.hyperflowplus.hooks.HookRemoteOpen;
import com.hyperflowplus.hooks.HookSmsPersist;
import com.hyperflowplus.hooks.HookSmsSenderEnrich;
import com.hyperflowplus.hooks.HookTrustAuth;
import com.hyperflowplus.hooks.HookTrustAuthAuto;
import com.hyperflowplus.hooks.HookTrustAuthAutoFill;
import com.hyperflowplus.hooks.HookTrustAuthUiClose;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HyperFlow —— libxposed API 102 入口（LSPosed/Vector 新框架）。
 *
 * 注册方式（META-INF/xposed/）：
 *   java_init.list → com.hyperflowplus.XposedEntry
 *   module.prop    → minApiVersion=101 / targetApiVersion=102
 *   scope.list     → com.milink.service（旧版框架作用域文件）
 *
 * 作用域（新版框架）由 AndroidManifest meta-data + res/values/arrays.xml 提供，预勾选 3 项：
 *   com.milink.service（小米互联：通知流转 6 个 hook，写 milink 数据目录探针）
 *   android（经典 LSPosed 的系统框架标识：点击分身通知 → 打开 999 空间微信/QQ，HookRemoteOpen，写 /data/adb 探针）
 *   system（新版 Vector 的系统框架标识，JingMatrix 分支用 system 而非 android）
 * 本 App（com.hyperflowplus）自身进程无需被 hook，不在推荐作用域内。
 *
 * 入口在 onPackageLoaded 中按包名分发装配 hook：
 *   - android            → 注入 system_server（HookRemoteOpen）
 *   - com.milink.service → 注入小米互联服务（其余 6 个 hook）
 *   - 其他包名           → 仅写运行态标记，不装配
 * V0.2.6 起不再启动 HTTP WebUI：配置读写全部由 App 经 su 完成。
 */
public class XposedEntry extends XposedModule {

    private static volatile XposedEntry instance;

    // system_server 级 hook 幂等闸门：onSystemServerStarting 与 onPackageLoaded("android"/"system")
    // 双通道都可能触发，只允许真正安装一次，避免同一方法被重复 hook。
    private static final AtomicBoolean SYSTEM_HOOKS_INSTALLED = new AtomicBoolean();

    public XposedEntry() {
        instance = this;
    }

    public static XposedEntry get() {
        return instance;
    }

    /**
     * Vector/JingMatrix 系框架在 system_server 启动时必然回调此方法（startBootstrapServices 触发，
     * 见 Vector SystemServerHookers.StartBootstrapServicesHooker → dispatchSystemServerStarting），
     * 是"新式模块注入 system_server"最可靠的入口（对照 HyperModifier MyHyperModifier.onSystemServerStarting）。
     * onPackageLoaded 依赖 LoadedApk hook 命中，在部分框架/system_server 场景不触发，故双通道双保险。
     */
    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        markLoaded();
        ClassLoader cl = param.getClassLoader();
        try {
            MiflowLog.i("=== HyperFlow onSystemServerStarting (system_server entry) ===");
            installSystemHooks(cl);
        } catch (Throwable t) {
            MiflowLog.e("XposedEntry onSystemServerStarting init failed", t);
        }
    }

    /** 安装 system_server 级 hook（分身点击拦截 + 锁屏凭证捕获），幂等。 */
    private static void installSystemHooks(ClassLoader cl) {
        if (!SYSTEM_HOOKS_INSTALLED.compareAndSet(false, true)) {
            MiflowLog.i("system hooks already installed, skip");
            return;
        }
        installSafely("HookRemoteOpen[system]", new Runnable() {
            @Override public void run() { HookRemoteOpen.installSystem(cl); }
        });
        installSafely("HookKeyguardCapture", new Runnable() {
            @Override public void run() { HookKeyguardCapture.install(cl); }
        });
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        // 运行态标记：模块被 LSPosed 真正注入加载时写时间戳。
        // 首页检测据此识别"框架/作用域是否真的生效"（配置态 db/scope 在框架关闭时残留会误判）。
        markLoaded();
        String pkgName = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();
        if ("android".equals(pkgName) || "system".equals(pkgName)) {
            // system_server：拦截"远程打开应用"请求，把分身通知的点击改为打开 999 空间
            // 微信/QQ（需要作用域勾选 android）；并捕获日常锁屏解锁的凭证（免预填）。
            // pkgName 双兼容：经典 LSPosed 传 "android"，Vector/JingMatrix 分支传 "system"
            //（用户实测勾了 android 但 system_server 没加载 —— JingMatrix 作用域里
            //  "系统框架 system" 与 "Android系统 android" 是两个条目，注入用 system）。
            try {
                MiflowLog.i("=== HyperFlow loaded in system_server (clone open redirect + keyguard capture, pkgName=" + pkgName + ") ===");
                installSystemHooks(cl);
            } catch (Throwable t) {
                MiflowLog.e("XposedEntry system init failed", t);
            }
            return;
        }
        if ("com.xiaomi.trustservice".equals(pkgName)) {
            // 妙享直通授权盘：自动填入留存锁屏密码走真实校验（HookTrustAuth）
            try {
                MiflowLog.i("=== HyperFlow loaded in com.xiaomi.trustservice (auth auto-fill) ===");
                installSafely("HookTrustAuth", new Runnable() {
                    @Override public void run() { HookTrustAuth.install(cl); }
                });
                // A方案-1：观测会话建立点 g/h，dump 单例字段（只读，不抢流程），为 A-2 HAT 回传定位
                installSafely("HookTrustAuthAuto", new Runnable() {
                    @Override public void run() { HookTrustAuthAuto.install(cl); }
                });
                // 免预填自动填：授权盘显示时（setAuthInputCompleteListener 注入 A.d 实例）直接提交留存凭证
                installSafely("HookTrustAuthAutoFill", new Runnable() {
                    @Override public void run() { HookTrustAuthAutoFill.install(cl); }
                });
                // 验证成功后自动关闭授权盘 UI（netbus.h.n(int) 结果码 0 → finish）
                installSafely("HookTrustAuthUiClose", new Runnable() {
                    @Override public void run() { HookTrustAuthUiClose.install(cl); }
                });
            } catch (Throwable t) {
                MiflowLog.e("XposedEntry trustservice init failed", t);
            }
            return;
        }
        if ("com.xiaomi.mirror".equals(pkgName)) {
            // 诊断期：观测分身通知/镜像打开到底走自起 u0 还是转发 999 PendingIntent（只读，不改行为）
            try {
                MiflowLog.i("=== HyperFlow loaded in com.xiaomi.mirror (clone-open observe) ===");
                installSafely("HookMirrorObserve", new Runnable() {
                    @Override public void run() { HookMirrorObserve.install(cl); }
                });
            } catch (Throwable t) {
                MiflowLog.e("XposedEntry mirror init failed", t);
            }
            return;
        }
        if ("com.android.incallui".equals(pkgName)) {
            // 电话（InCallUI）：亮屏来电时向 milink 补发 ON_CALL_VOIP 流转广播。
            // 根因（hf_lock/hf_call3 双日志实锤）：锁屏来电 InCallUI 状态机 INCOMING →
            // StatusBarNotifier.resendNotification → 发 ON_CALL_VOIP 广播给 milink → 流转成功；
            // 亮屏来电 Telecom 不绑定 InCallUI（状态机不启动），InCallUI 只发前台通知、
            // 不发 ON_CALL_VOIP 广播 → milink 收不到 → 流转链在源头断掉。
            // HookCallVoipNotify 在亮屏来电时（notifyNotification 被调用，日志实锤）按原生
            // 广播格式补发 ON_CALL_VOIP，milink NotifTransReceiver 收到即走完整流转链。
            // 零 UI 干预：不模拟锁屏、不启动全屏，亮屏来电横幅仍由 SystemUI 原生显示。
            try {
                MiflowLog.i("=== HyperFlow loaded in com.android.incallui (voip relay for screen-on call) ===");
                installSafely("HookCallVoipNotify", new Runnable() {
                    @Override public void run() { HookCallVoipNotify.install(cl); }
                });
            } catch (Throwable t) {
                MiflowLog.e("XposedEntry incallui init failed", t);
            }
            return;
        }
        if (pkgName == null || !pkgName.startsWith("com.milink")) {
            return;
        }
        try {
            // V0.6.16.4：版本号改为运行时读取（不再写死 V0.3.11，日志里一眼区分注入版本）
            MiflowLog.i("=== HyperFlow v" + BuildConfig.VERSION_NAME + " loaded in " + pkgName
                    + " api=" + getApiVersion() + " framework=" + getFrameworkName() + " ===");

            installSafely("HookForceTransfer", new Runnable() {
                @Override public void run() { HookForceTransfer.install(cl); }
            });
            installSafely("HookCallRelay", new Runnable() {
                @Override public void run() { HookCallRelay.install(cl); }
            });
            installSafely("HookCloneBypass", new Runnable() {
                @Override public void run() { HookCloneBypass.install(cl); }
            });
            installSafely("HookSmsPersist", new Runnable() {
                @Override public void run() { HookSmsPersist.install(cl); }
            });
            installSafely("HookSmsSenderEnrich", new Runnable() {
                @Override public void run() { HookSmsSenderEnrich.install(cl); }
            });
            installSafely("HookAutoUnlock", new Runnable() {
                @Override public void run() { HookAutoUnlock.install(cl); }
            });
            installSafely("HookCallVoip", new Runnable() {
                @Override public void run() { HookCallVoip.install(cl); }
            });
            installSafely("HookCallFilterPass", new Runnable() {
                @Override public void run() { HookCallFilterPass.install(cl); }
            });
            // beta45：移除 HookCallVoipForce（强制 iPhone voip）——用户拍板用原生 isVoipSupported
            // 判定：支持 voip 的设备（新款 iPhone/小米15）亮屏也标 voip；不支持（iPhone X）降级通知。
            installSafely("HookCallVoipType", new Runnable() {
                @Override public void run() { HookCallVoipType.install(cl); }
            });
        } catch (Throwable t) {
            MiflowLog.e("XposedEntry init failed", t);
        }
    }

    private static void installSafely(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            MiflowLog.e(name + " failed", t);
        }
    }

    /** 写运行态标记（四路径，按注入进程区分，任一命中即视为"模块被 LSP 真正加载"）：
     *  1) /data/adb/hyperflowplus/xposed_loaded —— zygote/system_server（root 进程，对应 android 作用域）可写
     *  2) /data/user/0/com.milink.service/files/hf_loaded —— milink 服务进程（非 root、非本 App uid，
     *     只能写自己的数据目录；对应 com.milink.service 作用域）可写
     *  3) /data/user/0/com.xiaomi.trustservice/files/hf_loaded —— 信任服务进程（对应 com.xiaomi.trustservice
     *     作用域，妙享直通 A 方案依赖；检测端按开关 silent_auth 判定是否要求）
     *  4) /data/user/0/com.hyperflowplus/files/xposed_loaded —— 本 App 自身进程可写（保留，兼容旧版）
     *  检测端按路径区分：1=android 作用域已生效，2=milink 作用域已生效，3=trustservice 作用域已生效。
     *  本 App 自身已从推荐作用域移除（无需被 hook），路径 4 仅作兼容。失败静默，不影响功能。
     */
    private static void markLoaded() {
        long now = System.currentTimeMillis();
        writeStamp("/data/adb/hyperflowplus/xposed_loaded", now);
        writeStamp("/data/user/0/com.milink.service/files/hf_loaded", now);
        writeStamp("/data/user/0/com.xiaomi.trustservice/files/hf_loaded", now);
        writeStamp("/data/user/0/com.hyperflowplus/files/xposed_loaded", now);
    }

    private static void writeStamp(String path, long now) {
        try {
            java.io.File f = new java.io.File(path);
            f.getParentFile().mkdirs();
            java.io.PrintWriter w = new java.io.PrintWriter(f, "UTF-8");
            w.println(now);
            w.close();
        } catch (Throwable ignored) {
        }
    }
}
