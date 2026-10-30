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
 * 方案：hook netbus.h.n(int)，结果码 0（成功）时通过 ActivityThread.mActivities
 * （框架字段，不随应用混淆变化）找前台 trustservice / lockscreen Activity，主线程 finish()。
 * 放在原逻辑 proceed 之后：Q0.d（取消超时/通知上层/起线程回传）已先入队执行，
 * 本 hook 只补最后一步 UI 收尾，不打断 HAT 回传链路。
 * 不依赖 silent_auth 门控——手动输入验证成功同样该关（用户实测手动也停留）。
 *
 * beta58：旧实现反射 LockScreenUIActivity.f1753J（混淆静态字段），用户 ROM 上字段名已变
 * （日志：HookTrustAuthUiClose: post-finish failed），改用 ActivityThread.mActivities 兜底拿实例。
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
                                    closeTopAuthActivity();
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

    /**
     * 通过 ActivityThread.mActivities 找前台授权 Activity 并 finish。
     * 不依赖混淆类名/字段（f1753J 在部分 ROM 已改名），mActivities 是框架字段稳定存在。
     */
    private static void closeTopAuthActivity() {
        try {
            Object at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null);
            if (at == null) return;
            Field mAct = at.getClass().getDeclaredField("mActivities");
            mAct.setAccessible(true);
            Object map = mAct.get(at);
            if (map == null) return;
            Method sizeM = map.getClass().getMethod("size");
            Method valueAtM = map.getClass().getMethod("valueAt", int.class);
            int n = (Integer) sizeM.invoke(map);
            for (int i = n - 1; i >= 0; i--) {
                Object rec = valueAtM.invoke(map, i);
                if (rec == null) continue;
                Field actF = rec.getClass().getDeclaredField("activity");
                actF.setAccessible(true);
                Object a = actF.get(rec);
                if (a instanceof Activity) {
                    final Activity act = (Activity) a;
                    final String cls = act.getClass().getName();
                    if (cls.contains("trustservice") || cls.contains("lockscreen")
                            || cls.contains("LockScreen")) {
                        act.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (!act.isFinishing()) {
                                        act.finish();
                                        MiflowLog.i("HookTrustAuthUiClose: verify OK, UI closed via mActivities: " + cls);
                                    }
                                } catch (Throwable t2) {
                                    MiflowLog.e("HookTrustAuthUiClose: finish failed", t2);
                                }
                            }
                        });
                        return;
                    }
                }
            }
            MiflowLog.w("HookTrustAuthUiClose: no auth activity found in mActivities");
        } catch (Throwable t) {
            MiflowLog.e("HookTrustAuthUiClose: closeTopAuthActivity failed", t);
        }
    }
}
