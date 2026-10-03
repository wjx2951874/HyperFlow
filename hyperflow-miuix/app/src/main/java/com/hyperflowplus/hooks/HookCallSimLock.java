package com.hyperflowplus.hooks;

import android.app.KeyguardManager;
import android.os.PowerManager;

import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * v0.6.10：来电链路强制"模拟锁屏/灭屏"——注入 system_server（android 作用域）。
 *
 * 背景：来电 voip 全屏流转（广播链路）的亮屏放行决策在 system_server 的 telecom/phone，
 * milink 进程的 hook 管不到（v0.5.15.4 的模拟锁屏只装在 milink，亮屏来电仍收不到电话）。
 *
 * 本 hook 白名单限定"来电链路"调用栈，模拟锁屏让系统以为设备处于锁屏/灭屏，
 * 亮屏来电也走 voip 广播流转 → 接收端全屏接听（与其他工具一致）。
 * 非来电调用栈一律 proceed，不影响系统其他 isKeyguardLocked/isInteractive 调用。
 *
 * 白名单：system_server 里的 telecom 服务（com.android.server.telecom.*）、
 * com.android.phone、incallui/dialer、android.telecom.*。
 */
public class HookCallSimLock {

    /** 来电链路白名单前缀：命中即视为"系统在做来电决策"，模拟锁屏/灭屏 */
    private static final String[] CALL_STACK_PREFIXES = {
            "com.android.server.telecom",
            "com.android.phone",
            "com.android.incallui",
            "com.android.dialer",
            "android.telecom"
    };

    public static void install(ClassLoader cl) {
        // ① KeyguardManager#isKeyguardLocked → 来电链路模拟"已锁屏"
        try {
            Method m = KeyguardManager.class.getDeclaredMethod("isKeyguardLocked");
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (calledFromCallStack()) {
                                MiflowLog.d("call simlock: isKeyguardLocked -> true (telecom stack)");
                                return Boolean.TRUE;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallSimLock[keyguard] installed (system_server, telecom-only)");
        } catch (Throwable t) {
            MiflowLog.e("HookCallSimLock[keyguard] install failed", t);
        }

        // ② PowerManager#isInteractive → 来电链路模拟"灭屏"
        try {
            Method m = PowerManager.class.getDeclaredMethod("isInteractive");
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (calledFromCallStack()) {
                                MiflowLog.d("call simlock: isInteractive -> false (telecom stack)");
                                return Boolean.FALSE;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallSimLock[interactive] installed (system_server, telecom-only)");
        } catch (Throwable t) {
            MiflowLog.e("HookCallSimLock[interactive] install failed", t);
        }
    }

    /** 当前调用栈是否属于来电决策链路 */
    private static boolean calledFromCallStack() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            for (String prefix : CALL_STACK_PREFIXES) {
                if (c.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }
}
