package com.hyperflowplus.hooks;

import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 免预填 · 日常解锁捕获（作用域：system_server）。
 *
 * 系统锁屏解锁（Keyguard 输入密码/图案，含"重启后需输入本机锁屏密码"验证）发生在
 * system_server 进程的 LockPatternUtils.verifyCredential —— hook 它，校验成功（isMatched）
 * 后从凭证对象提取明文，经 {@link CapturedCred} 落盘公共文件。
 * 这样用户只要日常解锁过一次手机，App 侧就能读回真实密码，A-2 全自动，免预填、免在授权盘输入。
 *
 * 只捕获"校验成功"的凭证：输错的值永远不会入库；换密码后首次解锁成功即自动刷新。
 * 指纹/面部等生物解锁不走 verifyCredential，不影响（密码设备重启后首次解锁必输密码，必被捕获）。
 */
public class HookKeyguardCapture {

    public static void install(ClassLoader cl) {
        // 主通道：LockSettingsService.checkCredential —— 系统锁屏（Keyguard）输密码/图案解锁
        // 时真正调用的入口（ILockSettings.checkCredential 的实现），重启后首次解锁必走它。
        if (hookCheckCredential(cl)) {
            return;
        }
        // 兜底：LockPatternUtils.verifyCredential（部分 ROM 的便捷入口）
        try {
            Class<?> lpu = Class.forName("com.android.internal.widget.LockPatternUtils");
            final Class<?> lc = Class.forName("com.android.internal.widget.LockscreenCredential");
            for (Method m : lpu.getDeclaredMethods()) {
                if (!m.getName().equals("verifyCredential")) {
                    continue;
                }
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 3 || pt[0] != lc || pt[1] != int.class || pt[2] != int.class) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                Object result = chain.proceed();
                                try {
                                    boolean matched = (boolean) result.getClass()
                                            .getMethod("isMatched").invoke(result);
                                    if (matched) {
                                        capture(chain.getArgs().get(0));
                                        try {
                                            Long handle = (Long) result.getClass()
                                                    .getMethod("getGatekeeperPasswordHandle").invoke(result);
                                            if (handle != null && handle.longValue() != 0L) {
                                                CapturedCred.writeHandle(handle.longValue());
                                            }
                                        } catch (Throwable t) {
                                            MiflowLog.w("KeyguardCapture: handle cache failed: " + t);
                                        }
                                    }
                                } catch (Throwable t) {
                                    MiflowLog.e("KeyguardCapture: hook err", t);
                                }
                                return result;
                            }
                        });
                MiflowLog.i("HookKeyguardCapture: hooked LockPatternUtils.verifyCredential (fallback)");
                return;
            }
            MiflowLog.w("HookKeyguardCapture: verifyCredential(LockscreenCredential,int,int) not found");
        } catch (Throwable t) {
            MiflowLog.w("HookKeyguardCapture: install failed: " + t);
        }
    }

    /** 主通道：hook LockSettingsService.checkCredential(LockscreenCredential,int,int) */
    private static boolean hookCheckCredential(ClassLoader cl) {
        try {
            // beta61 修复：AOSP 中 LockSettingsService 的真实路径是
            // com.android.server.locksettings.LockSettingsService（services.jar），
            // 旧代码写的 com.android.internal.widget.LockSettingsService 不存在，
            // 必然 ClassNotFoundException（真机日志实锤），主通道从未生效。
            // 双类名兜底，避免个别 ROM 分支路径不同。
            final Class<?> lc = Class.forName("com.android.internal.widget.LockscreenCredential");
            Class<?> lss = null;
            String usedName = null;
            for (String name : new String[]{
                    "com.android.server.locksettings.LockSettingsService",
                    "com.android.internal.widget.LockSettingsService"}) {
                try {
                    lss = Class.forName(name, false, cl);
                    usedName = name;
                    break;
                } catch (Throwable ignore) {
                }
            }
            if (lss == null) {
                MiflowLog.w("HookKeyguardCapture: LockSettingsService not found in either path");
                return false;
            }
            MiflowLog.i("HookKeyguardCapture: LockSettingsService = " + usedName);
            for (Method m : lss.getDeclaredMethods()) {
                if (!m.getName().equals("checkCredential")) {
                    continue;
                }
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 3 || pt[0] != lc || pt[1] != int.class || pt[2] != int.class) {
                    continue;
                }
                m.setAccessible(true);
                XposedEntry.get().hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                Object result = chain.proceed();
                                try {
                                    boolean matched = (boolean) result.getClass()
                                            .getMethod("isMatched").invoke(result);
                                    if (matched) {
                                        capture(chain.getArgs().get(0));
                                        try {
                                            Long handle = (Long) result.getClass()
                                                    .getMethod("getGatekeeperPasswordHandle").invoke(result);
                                            if (handle != null && handle.longValue() != 0L) {
                                                CapturedCred.writeHandle(handle.longValue());
                                            }
                                        } catch (Throwable t) {
                                            MiflowLog.w("KeyguardCapture: handle cache failed: " + t);
                                        }
                                    }
                                } catch (Throwable t) {
                                    MiflowLog.e("KeyguardCapture: hook err", t);
                                }
                                return result;
                            }
                        });
                MiflowLog.i("HookKeyguardCapture: hooked LockSettingsService.checkCredential (system unlock)");
                return true;
            }
            MiflowLog.w("HookKeyguardCapture: LockSettingsService.checkCredential not found");
        } catch (Throwable t) {
            MiflowLog.w("HookKeyguardCapture: checkCredential hook failed: " + t);
        }
        return false;
    }

    /** 从校验成功的凭证对象提取明文+类型并落盘。 */
    private static void capture(Object cred) {
        try {
            Class<?> c = cred.getClass();
            String type = null;
            if ((boolean) c.getMethod("isPattern").invoke(cred)) {
                type = "pattern";
            } else if ((boolean) c.getMethod("isPin").invoke(cred)) {
                type = "pin";
            } else if ((boolean) c.getMethod("isPassword").invoke(cred)) {
                type = "password";
            }
            String raw = (String) c.getMethod("getCredential").invoke(cred);
            if (type != null && raw != null && !raw.isEmpty()) {
                CapturedCred.write(type, raw);
            }
        } catch (Throwable t) {
            MiflowLog.e("KeyguardCapture: extract err", t);
        }
    }
}
