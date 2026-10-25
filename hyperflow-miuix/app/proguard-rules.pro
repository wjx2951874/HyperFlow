# HyperFlow R8 keep 规则（beta53：开 R8 让 Xposed 入口进主 dex）
# 背景：system_server 由 zygote fork，LSPosed 注入时只从主 dex（classes.dex）读 XposedEntry。
# AGP9+minSdk33 下 D8 独立模式忽略 multiDexKeep，唯一可靠手段是开 R8（minify）+ keep——
# keep 的类会进入主 dex（HyperCeiler 同款做法，故其 system_server 注入正常）。

# ===== Xposed 入口与 hooks（LSPosed 反射加载，必须保留且进主 dex）=====
-keep class com.hyperflowplus.XposedEntry { *; }
-keep class com.hyperflowplus.hooks.** { *; }
-keep class com.hyperflowplus.MiflowLog { *; }
-keep class com.hyperflowplus.BuildConfig { *; }
-keep class com.hyperflowplus.Config { *; }

# ===== kotlinx.serialization（miuix-nav Route 导航序列化）=====
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes SourceFile, LineNumberTable
-keep,includedescriptorclasses class com.hyperflowplus.**$$serializer { *; }
-keepclassmembers class com.hyperflowplus.** {
    *** Companion;
}
-keepclasseswithmembers class com.hyperflowplus.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== 反射/隐藏 API 桥 =====
-keep class org.lsposed.hiddenapibypass.** { *; }

# ===== libxposed api 为 compileOnly（运行时框架提供），仅抑制警告 =====
-dontwarn io.github.libxposed.**
