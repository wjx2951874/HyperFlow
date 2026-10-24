package com.hyperflowplus.hooks;

import android.os.Handler;
import android.os.Looper;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 免预填 · 授权盘自动填（作用域：com.xiaomi.trustservice）。
 *
 * 背景（trustservice 反编译实锤）：A.d.e(String) 是"输入完成提交"入口，只在用户
 * 输入内容达到要求时被调用（T0/b.java case 0：数字键盘点击、长度满后调
 * ((A.d) listener).e(串)）。因此原 HookTrustAuth 只 hook A.d.e 无法实现"免预填"——
 * 下次连接用户不输入，A.d.e 根本没有触发机会，留存凭证永远用不上。
 *
 * 本 hook 解决触发时机：授权盘视图（Q0.c 的三个实现类）通过
 * setAuthInputCompleteListener(Q0.b) 把 A.d 实例注入视图——hook 它，在授权盘
 * 显示时就拿到 A.d 实例，延迟数百毫秒（视图就绪）后直接调 A.d.e(留存凭证)，
 * 等效于"替用户瞬间输满并提交"，走系统 gatekeeper 真实校验（拿真 HAT）。
 *
 * 凭证来源与 HookTrustAuth 一致（三级回退）：
 *   ① readLocalSp()（trustservice 私有 sp：用户手动输入一次即捕获，最可靠）
 *   ② readPublic()（system_server 日常解锁捕获的公共文件）
 *   ③ Config 预存（用户手动预填/App 同步的加密凭证）
 *
 * 只注入一次（每次授权盘出现注入一次）；旧密码注入失败 → 授权盘正常报错、
 * 用户手动重输（不主动重试，避免 gatekeeper 罚时）。
 */
public class HookTrustAuthAutoFill {

    private static final String TAG = "HyperFlow";

    /** Q0.c 的三个输入视图实现类（数字 PIN / 字母数字密码 / 图案）。 */
    private static final String[] INPUT_VIEWS = {
            "com.xiaomi.trustservice.lockscreenui.numeric.NumericKeypadView",
            "com.xiaomi.trustservice.lockscreenui.keypad.AlphanumericKeypadView",
            "com.xiaomi.trustservice.lockscreenui.gesture.GestureDrawingView"
    };

    public static void install(ClassLoader cl) {
        if (!Config.getBool(Config.KEY_SILENT_AUTH, false)) {
            MiflowLog.i(TAG + ": HookTrustAuthAutoFill silent_auth disabled, skip");
            return;
        }
        try {
            Class<?> iface = cl.loadClass("Q0.b");
            for (String viewCls : INPUT_VIEWS) {
                try {
                    Class<?> cls = Class.forName(viewCls, false, cl);
                    Method m = cls.getDeclaredMethod("setAuthInputCompleteListener", iface);
                    m.setAccessible(true);
                    XposedEntry.get().hook(m)
                            .setExceptionMode(ExceptionMode.PROTECTIVE)
                            .intercept(new Hooker() {
                                @Override
                                public Object intercept(Chain chain) throws Throwable {
                                    Object ret = chain.proceed();
                                    try {
                                        maybeAutoFill(chain);
                                    } catch (Throwable t) {
                                        MiflowLog.e(TAG + ": auto-fill error", t);
                                    }
                                    return ret;
                                }
                            });
                    MiflowLog.i(TAG + ": HookTrustAuthAutoFill hooked " + viewCls);
                } catch (Throwable t) {
                    MiflowLog.w(TAG + ": skip " + viewCls + " (" + t + ")");
                }
            }
        } catch (Throwable t) {
            MiflowLog.e(TAG + ": HookTrustAuthAutoFill install failed", t);
        }
    }

    /** 授权盘显示时：有留存凭证则延迟注入 A.d.e(凭证)，等效自动输满提交。 */
    private static void maybeAutoFill(Chain chain) {
        final Object listener = chain.getArgs().get(0); // Q0.b = A.d 实例
        if (listener == null) {
            return;
        }
        // 凭证三级回退
        String[] cap = CapturedCred.readLocalSp();
        if (cap == null) {
            cap = CapturedCred.readPublic();
        }
        if (cap == null) {
            String t = Config.getAuthType();
            String s = Config.getAuthSecret();
            String p = Config.getAuthPattern();
            if (("pin".equals(t) || "password".equals(t)) && s != null && !s.isEmpty()) {
                cap = new String[]{t, s, ""};
            } else if ("pattern".equals(t) && p != null && !p.isEmpty()) {
                cap = new String[]{t, "", p};
            }
        }
        if (cap == null) {
            return;
        }
        final String inject = toInject(cap);
        if (inject == null) {
            return;
        }
        final String type = cap[0];
        // 延迟 500ms：等授权盘视图完全就绪（入场动画/初始化）后再提交，避免过早调用异常
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Method e = listener.getClass().getDeclaredMethod("e", String.class);
                    e.setAccessible(true);
                    e.invoke(listener, inject);
                    MiflowLog.i(TAG + ": auto-filled auth (type=" + type + ", len=" + inject.length() + ")");
                } catch (Throwable t) {
                    MiflowLog.e(TAG + ": auto-fill invoke failed", t);
                }
            }
        }, 500L);
    }

    /** 按凭证类型构造 A.d.e 入参：pin/password=明文，pattern=3x3 点编号串。 */
    private static String toInject(String[] cap) {
        String type = cap[0];
        if ("pin".equals(type) || "password".equals(type)) {
            String s = cap[1];
            return (s != null && !s.isEmpty()) ? s : null;
        }
        if ("pattern".equals(type)) {
            String p = cap[2];
            return (p != null && !p.isEmpty()) ? p : null;
        }
        return null;
    }
}
