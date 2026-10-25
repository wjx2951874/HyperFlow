# HyperFlow Xposed 入口必须留在主 dex（beta52 修复）
# 根因：system_server 由 zygote fork，LSPosed 在 zygote 期只从模块主 dex（classes.dex）加载
# XposedEntry 入口；Compose 引入后 D8 把 XposedEntry 及其依赖分到 classes2+，
# 导致 LSPosed 静默跳过 system_server 注入（modules.log 里 (system) 只有 HyperCeiler 而无 HyperFlow），
# 分身点击拉镜像主微信"同时打开"问题即因此从未生效。
# 参考：HyperCeiler 纯 Java 单 dex 入口在主 dex → system_server 注入正常。
-keep class com.hyperflowplus.XposedEntry { *; }
-keep class com.hyperflowplus.hooks.** { *; }
-keep class com.hyperflowplus.MiflowLog { *; }
-keep class com.hyperflowplus.BuildConfig { *; }
