package com.hyperflowplus.hooks;

import android.app.KeyguardManager;
import android.os.PowerManager;
import android.view.Display;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① 解锁时流转：在亮屏（解锁）状态下，向 milink 完整模拟"锁屏"状态，
 * 让它走与真实锁屏完全相同的流转逻辑（来电跨设备接听等）。
 *
 * 反编译确认 milink 的锁屏判定链（NotificationHandler.isDeviceSupported）：
 *   if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
 *   return false;   // ← 亮屏拒绝流转
 *
 * "模拟锁屏所有操作" = 把 milink 进程内能查到的锁屏/屏幕状态 API 全部按"锁屏态"返回：
 *   KeyguardManager  : isKeyguardLocked / isKeyguardSecure / inKeyguardRestrictedInputMode / isDeviceLocked
 *   PowerManager     : isInteractive / isScreenOn
 *   Display          : getState（→ OFF）/ isOn（→ false）
 *   WindowManager    : isKeyguardLocked（旧版 API，兼容老调用方）
 *
 * 全部受功能①开关（force_transfer）门控：关闭 = 零干预，保持小米原生（仅真实锁屏流转）；
 * 开启 = 亮屏也按锁屏全套逻辑流转。
 *
 * 历史教训（v0.6.10）：在 system_server 全局模拟锁屏会误伤正常通话流程
 * （isKeyguardLocked/isInteractive 被系统正常来电流程大量调用），已删除。
 * 本模块只 hook milink 进程（XposedEntry 注入点），影响范围仅 milink 自己的判断，不碰系统。
 */
public class HookCallRelay {

    public static void install(ClassLoader cl) {
        boolean enabled = Config.isForceTransferEnabled();

        // ---------- KeyguardManager 锁屏判定四件套 ----------
        hookBool(cl, "android.app.KeyguardManager", "isKeyguardLocked", true, enabled);
        hookBool(cl, "android.app.KeyguardManager", "isKeyguardSecure", true, enabled);
        hookBool(cl, "android.app.KeyguardManager", "inKeyguardRestrictedInputMode", true, enabled);
        hookBool(cl, "android.app.KeyguardManager", "isDeviceLocked", true, enabled);

        // ---------- PowerManager 亮屏判定两件套 ----------
        hookBool(cl, "android.os.PowerManager", "isInteractive", false, enabled);
        hookBool(cl, "android.os.PowerManager", "isScreenOn", false, enabled);

        // ---------- Display 状态：模拟屏幕关闭 ----------
        hookDisplayState(cl, enabled);
        hookDisplayIsOn(cl, enabled);

        // ---------- WindowManager 旧版锁屏 API（deprecated，兼容老调用方） ----------
        hookBool(cl, "android.view.WindowManager", "isKeyguardLocked", true, enabled);
        hookBool(cl, "android.view.WindowManager", "isKeyguardSecure", true, enabled);
    }

    /**
     * 通用布尔 hook：switch 开时强制返回 lockValue（Keyguard 类为 true / PowerManager 类为 false）。
     * 类或方法不存在时静默跳过（不同 Android 版本 API 差异）。
     */
    private static void hookBool(ClassLoader cl, String clsName, String methodName,
                                 boolean lockValue, boolean enabled) {
        try {
            Class<?> cls = Class.forName(clsName, false, cl);
            Method m;
            try {
                m = cls.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                try {
                    m = cls.getMethod(methodName);
                } catch (NoSuchMethodException e2) {
                    MiflowLog.i("HookCallRelay: skip " + clsName + "." + methodName + " (not found)");
                    return;
                }
            }
            m.setAccessible(true);
            com.hyperflowplus.XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (enabled && Config.isForceTransferEnabled()) {
                                return Boolean.valueOf(lockValue);
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallRelay: " + clsName + "." + methodName
                    + " -> " + lockValue + " (gated by force_transfer)");
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay: " + clsName + "." + methodName + " hook failed", t);
        }
    }

    /** Display.getState() → Display.STATE_OFF（模拟屏幕关闭，milink 屏幕状态判定）。 */
    private static void hookDisplayState(ClassLoader cl, boolean enabled) {
        try {
            Class<?> dCls = Class.forName("android.view.Display", false, cl);
            Method m = dCls.getDeclaredMethod("getState");
            m.setAccessible(true);
            com.hyperflowplus.XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (enabled && Config.isForceTransferEnabled()) {
                                return Integer.valueOf(Display.STATE_OFF);
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallRelay: Display.getState -> STATE_OFF (gated)");
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay: Display.getState hook failed", t);
        }
    }

    /** Display.isOn() → false（同屏幕关闭语义）。 */
    private static void hookDisplayIsOn(ClassLoader cl, boolean enabled) {
        try {
            Class<?> dCls = Class.forName("android.view.Display", false, cl);
            Method m = dCls.getDeclaredMethod("isOn");
            m.setAccessible(true);
            com.hyperflowplus.XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (enabled && Config.isForceTransferEnabled()) {
                                return Boolean.FALSE;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallRelay: Display.isOn -> false (gated)");
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay: Display.isOn hook failed", t);
        }
    }
}
