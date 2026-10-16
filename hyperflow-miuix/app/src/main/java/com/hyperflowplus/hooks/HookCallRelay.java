package com.hyperflowplus.hooks;

import android.os.PowerManager;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModule;

/**
 * v1.1.0：亮屏来电流转修复。
 *
 * 参考"仅亮屏流转.apk"的做法：在 com.milink.service 进程内 hook
 * PowerManager.isInteractive()，当开关开启时强制返回 false，
 * 让 milink 以为屏幕始终处于锁屏状态 → 来电走 voip 全屏接听路径，
 * 而不是亮屏时拒绝流转。
 *
 * 与 v0.5.15.4 的区别：
 *   - 之前 hook system_server 全局 → 误伤正常通话流程
 *   - 本方案只在 com.milink.service 进程内 hook → 不影响系统其他地方
 *
 * 开关：Config.isForceTransferEnabled()（复用亮屏强制流转开关）
 */
public class HookCallRelay {

    public static void install(ClassLoader cl) {
        try {
            Class<?> pmCls = Class.forName("android.os.PowerManager", false, cl);
            Method isInteractive = pmCls.getDeclaredMethod("isInteractive");
            XposedModule xp = com.hyperflowplus.XposedEntry.get();
            xp.hook(isInteractive)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (Config.isForceTransferEnabled()) {
                                // 让 milink 以为屏幕没亮 → 走锁屏 voip 路径
                                return false;
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookCallRelay: PowerManager.isInteractive() hooked in milink process (v1.1.0 screen-on call relay)");
        } catch (Throwable t) {
            MiflowLog.e("HookCallRelay install failed", t);
        }
    }
}
