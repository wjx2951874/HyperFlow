package io.github.libxposed.api;

import java.lang.reflect.Member;

public abstract class XposedModule {

    public abstract void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param);

    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
    }

    public void onHotReloading(XposedModuleInterface.HotReloadingParam param) {
    }

    public void onHotReloaded(XposedModuleInterface.HotReloadedParam param) {
    }

    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
    }

    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
    }

    public void onSystemServerLoaded(XposedModuleInterface.SystemServerLoadedParam param) {
    }

    public int getApiVersion() {
        return XposedInterface.API_VERSION;
    }

    public String getFrameworkName() {
        return "libxposed";
    }

    public void log(int priority, String tag, String msg) {
    }

    public void log(int priority, String tag, String msg, Throwable tr) {
    }

    public XposedInterface.HookBuilder hook(Member member) {
        return null;
    }
}
