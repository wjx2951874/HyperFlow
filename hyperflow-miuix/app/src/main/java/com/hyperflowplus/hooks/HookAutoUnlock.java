package com.hyperflowplus.hooks;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能⑤ 妙享直通：远控连接时自动跳过密码验证。
 *
 * 机制：Hook 小米互联服务的密码验证逻辑，当检测到远控连接需要验证时，
 * 直接返回验证成功，跳过手动输入密码的步骤。
 *
 * 作用域：com.milink.service（小米互联服务进程）
 */
public class HookAutoUnlock {

    public static void install(ClassLoader cl) {
        if (!Config.getBool(Config.KEY_SILENT_AUTH, false)) {
            MiflowLog.i("HookAutoUnlock: silent_auth disabled, skip");
            return;
        }

        MiflowLog.i("HookAutoUnlock: installing silent auth hook...");

        try {
            // Hook MiLinkUnlockAppFunctions 的解锁方法
            Class<?> unlockClass = cl.loadClass("com.milink.service.appfunctions.MiLinkUnlockAppFunctions");
            int hooked = 0;
            for (Method method : unlockClass.getDeclaredMethods()) {
                String name = method.getName();
                // 匹配解锁相关的方法
                if (name.contains("unlock") || name.contains("verify") || name.contains("auth")) {
                    try {
                        method.setAccessible(true);
                        XposedEntry.get().hook(method)
                                .setExceptionMode(ExceptionMode.PROTECTIVE)
                                .intercept(new Hooker() {
                                    @Override
                                    public Object intercept(Chain chain) throws Throwable {
                                        MiflowLog.i("HookAutoUnlock: intercept unlock request, auto success");
                                        // 直接返回成功，跳过原方法
                                        // TODO: 需要根据实际 UnlockResult 类结构创建成功对象
                                        return null;
                                    }
                                });
                        hooked++;
                    } catch (Throwable ignored) {
                    }
                }
            }
            MiflowLog.i("HookAutoUnlock: installed, hooked " + hooked + " methods");
        } catch (ClassNotFoundException e) {
            MiflowLog.e("HookAutoUnlock: unlock class not found", e);
        } catch (Throwable t) {
            MiflowLog.e("HookAutoUnlock: install failed", t);
        }
    }
}
