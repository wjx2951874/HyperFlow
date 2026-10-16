package com.hyperflowplus.hooks;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能⑤ 妙享直通：远控连接时自动跳过密码验证。
 *
 * 机制：Hook MiLinkUnlockAppFunctions.unlockSwitch 方法，
 * 当检测到远控连接需要验证时，直接返回 UnlockResult(success=true)，
 * 跳过手动输入密码的步骤。
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
            // Hook MiLinkUnlockAppFunctions.unlockSwitch 方法
            Class<?> unlockClass = cl.loadClass("com.milink.service.appfunctions.MiLinkUnlockAppFunctions");
            Class<?> resultClass = cl.loadClass("com.milink.service.appfunctions.UnlockResult");

            int hooked = 0;
            for (Method method : unlockClass.getDeclaredMethods()) {
                String name = method.getName();
                // 匹配 unlockSwitch 方法
                if (name.equals("unlockSwitch")) {
                    try {
                        method.setAccessible(true);
                        XposedEntry.get().hook(method)
                                .setExceptionMode(ExceptionMode.PROTECTIVE)
                                .intercept(new Hooker() {
                                    @Override
                                    public Object intercept(Chain chain) throws Throwable {
                                        MiflowLog.i("HookAutoUnlock: intercept unlockSwitch, auto success");

                                        // 创建成功的 UnlockResult 对象
                                        Object successResult = createSuccessResult(resultClass);
                                        return successResult;
                                    }
                                });
                        hooked++;
                        MiflowLog.i("HookAutoUnlock: hooked unlockSwitch method");
                    } catch (Throwable e) {
                        MiflowLog.e("HookAutoUnlock: hook unlockSwitch failed", e);
                    }
                }
            }
            MiflowLog.i("HookAutoUnlock: installed, hooked " + hooked + " methods");
        } catch (ClassNotFoundException e) {
            MiflowLog.e("HookAutoUnlock: class not found", e);
        } catch (Throwable t) {
            MiflowLog.e("HookAutoUnlock: install failed", t);
        }
    }

    /**
     * 创建成功的 UnlockResult 对象
     * UnlockResult(success=true)
     */
    private static Object createSuccessResult(Class<?> resultClass) {
        try {
            // 尝试找 UnlockResult(boolean success) 构造函数
            Constructor<?> constructor = resultClass.getConstructor(boolean.class);
            return constructor.newInstance(true);
        } catch (NoSuchMethodException e) {
            MiflowLog.w("HookAutoUnlock: no boolean constructor, trying no-arg...");
            try {
                // 尝试无参构造函数
                Constructor<?> constructor = resultClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object instance = constructor.newInstance();
                // 设置 success 字段为 true
                try {
                    java.lang.reflect.Field successField = resultClass.getDeclaredField("success");
                    successField.setAccessible(true);
                    successField.setBoolean(instance, true);
                } catch (NoSuchFieldException ignored) {
                }
                return instance;
            } catch (Throwable t) {
                MiflowLog.e("HookAutoUnlock: createSuccessResult failed", t);
                return null;
            }
        } catch (Throwable t) {
            MiflowLog.e("HookAutoUnlock: createSuccessResult failed", t);
            return null;
        }
    }
}
