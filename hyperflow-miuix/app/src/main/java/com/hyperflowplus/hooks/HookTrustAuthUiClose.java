package com.hyperflowplus.hooks;

import android.app.Activity;

import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.Chain;

/**
 * 妙享直通 · 授权盘验证成功后自动关闭 UI（作用域：com.xiaomi.trustservice）。
 *
 * 修复现象：验证成功后 LockScreenUIActivity 停留在输入框页不关闭。
 * 根因（jadx 反编译 MiTrustService.apk 实锤）：
 *   - A.d.e(String) 输入完成 → 起 Z0.a 线程做系统 gatekeeper 真实校验，回调 netbus.h。
 *   - netbus.h.n(int) 是唯一收尾入口：post Q0.d 到 AuthInputView 主线程。
 *   - Q0.d.run() 结果码：
 *       i2 == 0  成功 → LockScreenUIActivity.o()(取消超时) + Q0.a.j()(通知上层回传 HAT)
 *                    + s()(起线程) —— 原系统逻辑【不调用 finish()】，UI 停留。
 *       i2 == -1 错误 → AuthInputView.a.a()
 *       i2 > 0   罚时 → AuthInputView.a(4, 秒数) 倒计时
 *   - LockScreenUIActivity.f1753J 是静态字段，持有当前 Activity 实例。
 *
 * 方案：hook netbus.h.n(int)，结果码 0（成功）时通过静态字段 f1753J 拿到 Activity，
 * 主线程 finish()。放在原逻辑 proceed 之后：Q0.d（取消超时/通知上层/起线程回传）
 * 已先入队执行，本 hook 只补最后一步 UI 收尾，不打断 HAT 回传链路。
 * 不依赖 silent_auth 门控——手动输入验证成功同样该关（用户实测手动也停留）。
 */
public class HookTrustAuthUiClose {

    public static void install(ClassLoader cl) {
        try {
            Class<?> h = cl.loadClass("com.xiaomi.continuity.netbus.h");
            Method n = h.getDeclaredMethod("n", int.class);
            n.setAccessible(true);
            XposedEntry.get().hook(n)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            // 先放行原收尾逻辑（post Q0.d：取消超时 / 通知上层 / 起线程回传 HAT）
                            Object result = chain.proceed();
                            try {
                                int code = (Integer) chain.getArgs().get(0);
                                if (code == 0) {
                                    // 校验成功：原逻辑不关 UI，这里补 finish
                                    Class<?> actCls = cl.loadClass(
                                            "com.xiaomi.trustservice.remoteservice.locksettings.LockScreenUIActivity");
                                    Field f = actCls.getDeclaredField("f1753J");
                                    f.setAccessible(true);
                                    Object act = f.get(null);
                                    if (act instanceof Activity) {
                                        final Activity a = (Activity) act;
                                        a.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    if (!a.isFinishing()) {
                                                        a.finish();
                                                        MiflowLog.i("HookTrustAuthUiClose: verify OK, UI closed");
                                                    }
                                                } catch (Throwable t2) {
                                                    MiflowLog.e("HookTrustAuthUiClose: finish failed", t2);
                                                }
                                            }
                                        });
                                    }
                                }
                            } catch (Throwable t) {
                                MiflowLog.e("HookTrustAuthUiClose: post-finish failed, pass through", t);
                            }
                            return result;
                        }
                    });
            MiflowLog.i("HookTrustAuthUiClose: installed on netbus.h.n(int)");
        } catch (ClassNotFoundException e) {
            MiflowLog.e("HookTrustAuthUiClose: netbus.h not found (maybe ROM changed)", e);
        } catch (Throwable t) {
            MiflowLog.e("HookTrustAuthUiClose: install failed", t);
        }
    }
}
