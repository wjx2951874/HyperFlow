package com.hyperflowplus;

import com.hyperflowplus.hooks.HookAutoUnlock;
import com.hyperflowplus.hooks.HookCallRelay;
import com.hyperflowplus.hooks.HookCloneBypass;
import com.hyperflowplus.hooks.HookForceTransfer;
import com.hyperflowplus.hooks.HookRemoteOpen;
import com.hyperflowplus.hooks.HookSmsPersist;
import com.hyperflowplus.hooks.HookSmsSenderEnrich;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * HyperFlow V0.3.11 —— libxposed API 102 入口（LSPosed/Vector 新框架）。
 *
 * 注册方式（META-INF/xposed/）：
 *   java_init.list → com.hyperflowplus.XposedEntry
 *   module.prop    → minApiVersion=101 / targetApiVersion=102
 *   scope.list     → com.milink.service（作用域内置，无需手动勾选）
 *
 * 作用域内置后框架只向 com.milink.service 进程注入本模块；
 * 入口在 onPackageLoaded 中检查包名并装配 hook。
 * V0.2.6 起不再启动 HTTP WebUI：配置读写全部由 App 经 su 完成。
 */
public class XposedEntry extends XposedModule {

    private static volatile XposedEntry instance;

    public XposedEntry() {
        instance = this;
    }

    public static XposedEntry get() {
        return instance;
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        // 运行态标记：模块被 LSPosed 真正注入加载时写时间戳。
        // 首页检测据此识别"框架/作用域是否真的生效"（配置态 db/scope 在框架关闭时残留会误判）。
        markLoaded();
        String pkgName = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();
        if ("android".equals(pkgName)) {
            // system_server：拦截"远程打开应用"请求，把分身通知的点击改为打开 999 空间
            // 微信/QQ（需要作用域勾选 android）
            try {
                MiflowLog.i("=== HyperFlow loaded in system_server (clone open redirect) ===");
                installSafely("HookRemoteOpen[system]", new Runnable() {
                    @Override public void run() { HookRemoteOpen.installSystem(cl); }
                });
            } catch (Throwable t) {
                MiflowLog.e("XposedEntry system init failed", t);
            }
            return;
        }
        if (!"com.milink.service".equals(pkgName)) {
            return;
        }
        try {
            MiflowLog.i("=== HyperFlow V0.3.11 loaded in " + pkgName
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

    /** 写运行态标记（双路径，任一命中即视为"模块被 LSP 真正加载"）：
     *  1) /data/adb/hyperflowplus/xposed_loaded —— zygote/system_server（root 进程）可写
     *  2) /data/user/0/com.hyperflowplus/files/xposed_loaded —— App 自身进程可写
     *     （作用域勾选本 App 后 onPackageLoaded("com.hyperflowplus") 触发，普通进程有权限）
     *  失败静默，不影响功能。
     */
    private static void markLoaded() {
        long now = System.currentTimeMillis();
        writeStamp("/data/adb/hyperflowplus/xposed_loaded", now);
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
