package com.hyperflowplus.hooks;

import android.app.KeyguardManager;
import android.os.PowerManager;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModule;

/**
 * v1.1.0-beta24：模拟锁屏，让 milink 以为屏幕是关着的、并且是锁屏状态。
 *
 * Hook 多个方法：
 *   - PowerManager.isInteractive() → false
 *   - PowerManager.isScreenOn() → false
 *   - KeyguardManager.isKeyguardLocked() → true
 *
 * 这样 milink 就会以为屏幕是关着的，然后自动走锁屏的流转逻辑。
 */
public class HookCallRelay {

    public static void install(ClassLoader cl) {
        XposedModule xp = com.hyperflowplus.XposedEntry.get();

        // Hook PowerManager.isInteractive()
        try {
            Class<?> pmCls = Class.forName("android.os.PowerManager", false, cl);
            Method isInteractive = pmCls.getDeclaredMethod("isInteractive");
            xp.hook(isInteractive)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (Config.isForceTransferEnabled()) {
                                return false;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallRelay: PowerManager.isInteractive() hooked");
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay: isInteractive hook failed", t);
        }

        // Hook PowerManager.isScreenOn()
        try {
            Class<?> pmCls = Class.forName("android.os.PowerManager", false, cl);
            Method isScreenOn = pmCls.getDeclaredMethod("isScreenOn");
            xp.hook(isScreenOn)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (Config.isForceTransferEnabled()) {
                                return false;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallRelay: PowerManager.isScreenOn() hooked");
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay: isScreenOn hook failed", t);
        }

        // Hook KeyguardManager.isKeyguardLocked()
        try {
            Class<?> kmCls = Class.forName("android.app.KeyguardManager", false, cl);
            Method isKeyguardLocked = kmCls.getDeclaredMethod("isKeyguardLocked");
            xp.hook(isKeyguardLocked)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (Config.isForceTransferEnabled()) {
                                return true;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallRelay: KeyguardManager.isKeyguardLocked() hooked");
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay: isKeyguardLocked hook failed", t);
        }
    }
}
