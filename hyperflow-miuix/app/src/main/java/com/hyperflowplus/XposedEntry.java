package com.hyperflowplus;

import com.hyperflowplus.hooks.HookAutoUnlock;
import com.hyperflowplus.hooks.HookCallRelay;
import com.hyperflowplus.hooks.HookCloneBypass;
import com.hyperflowplus.hooks.HookCloneClick;
import com.hyperflowplus.hooks.HookForceTransfer;
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
        if (!"com.milink.service".equals(param.getPackageName())) {
            return;
        }
        try {
            ClassLoader cl = param.getDefaultClassLoader();
            MiflowLog.i("=== HyperFlow V0.3.11 loaded in " + param.getPackageName()
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
            installSafely("HookCloneClick", new Runnable() {
                @Override public void run() { HookCloneClick.install(cl); }
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
}
