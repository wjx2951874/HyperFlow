package io.github.libxposed.api;

public interface XposedModuleInterface {

    interface PackageLoadedParam {
        String getPackageName();
        ClassLoader getDefaultClassLoader();
    }

    interface ModuleLoadedParam {
    }

    interface HotReloadingParam {
    }

    interface HotReloadedParam {
    }

    interface PackageReadyParam {
    }

    interface SystemServerStartingParam {
    }

    interface SystemServerLoadedParam {
    }
}
