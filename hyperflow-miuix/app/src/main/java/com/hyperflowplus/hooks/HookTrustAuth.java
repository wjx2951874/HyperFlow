package com.hyperflowplus.hooks;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.Chain;

/**
 * 妙享直通 · 授权盘自动填（作用域：com.xiaomi.trustservice）。
 *
 * 授权盘宿主是小米信任服务（非 milink、非系统 Keyguard）：
 *   前台 Activity = com.xiaomi.trustservice/.remoteservice.locksettings.LockScreenUIActivity。
 * 输入完成统一走到混淆类 {@code A.d.e(String)}：
 *   - 0 NUMERIC(PIN)  → Z0.b.c(3,str)  createPin
 *   - 1 GESTURE(图案) → Z0.b.c(1,str)  createPattern
 *   - 2 ALPHANUMERIC  → Z0.b.c(4,str)  createPassword
 *
 * 路线（见 trustservice-auth-chain.md）：不伪造"成功"，而是把本地留存密码喂入、
 * 走系统 gatekeeper 真实校验拿到真 HAT（绑定分支必需）。错密码会触发罚时，
 * 因此只替换入参、不主动重试；留存值由用户自己录入，保证正确。
 *
 * PIN / 混合 / 图案自动替换；图案编码 = 点编号 1..9 按连线顺序直接拼接（见 MiuiLockPatternView）。
 */
public class HookTrustAuth {

    public static void install(ClassLoader cl) {
        if (!Config.getBool(Config.KEY_SILENT_AUTH, false)) {
            MiflowLog.i("HookTrustAuth: silent_auth disabled, skip");
            return;
        }
        String type = Config.getAuthType();
        String secret = Config.getAuthSecret();
        String pattern = Config.getAuthPattern();
        boolean ready = ("pin".equals(type) || "password".equals(type)) && secret != null && !secret.isEmpty()
                || "pattern".equals(type) && pattern != null && !pattern.isEmpty();
        if (!ready) {
            MiflowLog.i("HookTrustAuth: no saved credential, skip (type=" + type + ")");
            return;
        }

        MiflowLog.i("HookTrustAuth: installing on A.d.e(String), auth_type=" + type);
        try {
            Class<?> d = cl.loadClass("A.d");
            Method e = d.getDeclaredMethod("e", String.class);
            e.setAccessible(true);
            XposedEntry.get().hook(e)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            try {
                                String t = null;
                                String secret = null;
                                String pattern = null;
                                // 免预填实时通道：优先读公共文件（HookKeyguardCapture 每次解锁成功都会
                                // 刷新 /data/system/hyperflow_captured）——用户只要日常解锁过一次，
                                // 这里就能拿到最新真实密码，即使 App 从未打开/从未入库也能直接连接。
                                String[] cap = CapturedCred.readPublic();
                                if (cap == null) {
                                    // 本进程（trustservice）私有 sp：手动输入时 HookTrustAuth 捕获写入，
                                    // 下次连接免预填直读（不依赖 system_server 捕获/App 同步）
                                    cap = CapturedCred.readLocalSp();
                                }
                                if (cap != null) {
                                    t = cap[0];
                                    secret = cap[1];
                                    pattern = cap[2];
                                } else {
                                    // 回退：App 预存（用户手动预填或 App 启动自动同步的加密凭证）
                                    t = Config.getAuthType();
                                    secret = Config.getAuthSecret();
                                    pattern = Config.getAuthPattern();
                                }
                                String inject = null;
                                if ("pin".equals(t) || "password".equals(t)) {
                                    if (secret != null && !secret.isEmpty()) inject = secret;
                                } else if ("pattern".equals(t)) {
                                    // MiuiLockPatternView 编码：3x3 点编号 1..9(row*3+col+1)，
                                    // 按连线顺序直接拼接、无分隔（如对角线 1-5-9 = "159"），
                                    // 与 A.d.e(str) 收到的串一致，直接替换入参。
                                    if (pattern != null && !pattern.isEmpty()) inject = pattern;
                                }
                                if (inject != null) {
                                    // libxposed Chain 无 setArgs，getArgs() 返回只读 List（写抛
                                    // UnsupportedOperationException）。改参必须复制成新数组走
                                    // proceed(Object[])。
                                    List<Object> args = chain.getArgs();
                                    MiflowLog.i("HookTrustAuth: hit, orig=" + args.get(0)
                                            + " -> inject=" + inject + " (type=" + t + ")");
                                    Object[] newArgs = args.toArray();
                                    newArgs[0] = inject;
                                    return chain.proceed(newArgs);
                                }
                                // 免预填捕获：没有留存值可注入 = 用户正在手动输入。
                                // 此刻 A.d.e(String) 的入参就是真实明文（PIN/混合=字符串，
                                // 图案=3x3 点编号串），直接捕获落盘，下次连接即可自动填。
                                // 类型码 = A.d 实例字段 a：1=PIN / 2=图案 / 3=混合（jadx 实锤）。
                                try {
                                    Object self = chain.getThisObject();
                                    java.lang.reflect.Field f = self.getClass().getDeclaredField("a");
                                    f.setAccessible(true);
                                    int rawType = f.getInt(self);
                                    String capType = rawType == 1 ? "pin"
                                            : rawType == 2 ? "pattern"
                                            : rawType == 3 ? "password" : null;
                                    if (capType != null) {
                                        String manual = String.valueOf(chain.getArgs().get(0));
                                        if (manual != null && !manual.isEmpty()) {
                                            CapturedCred.write(capType, manual);
                                            MiflowLog.i("HookTrustAuth: captured manual input type="
                                                    + capType + " len=" + manual.length());
                                        }
                                    }
                                } catch (Throwable t3) {
                                    MiflowLog.w("HookTrustAuth: capture manual input failed: " + t3);
                                }
                            } catch (Throwable t2) {
                                MiflowLog.e("HookTrustAuth: fill failed, pass through", t2);
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i("HookTrustAuth: installed");
        } catch (ClassNotFoundException e1) {
            MiflowLog.e("HookTrustAuth: class A.d not found", e1);
        } catch (Throwable t) {
            MiflowLog.e("HookTrustAuth: install failed", t);
        }
    }
}
