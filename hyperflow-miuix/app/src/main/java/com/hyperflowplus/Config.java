package com.hyperflowplus;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 模块配置与运行时计数器（V0.2.6 纯 App 架构）。
 *
 * 配置存储：全局文件 /data/adb/hyperflowplus/config.json（root 可读写）。
 *   - App 进程（com.hyperflowplus）：su 直读直写，秒级
 *   - hook 进程（com.milink.service）：su 读取 + 3 秒缓存（通知触发时懒加载）
 * 计数器存储：milink 进程 SharedPreferences（hook 内 bump 就地写，
 *   App 通过 su 解析 milink 的 prefs XML 展示）。
 */
public final class Config {
    private static final String PREFS = "hyperflowplus_cfg";
    public static final String GLOBAL_CFG = "/data/adb/hyperflowplus/config.json";
    // 更新通道（App 内并行尝试，哪个通用哪个）：
    // - raw.githubusercontent：永远最新，但国内常超时
    // - 镜像站有 CDN 缓存，必须带时间戳参数穿透
    public static String[] updateJsonUrls() {
        String t = "?t=" + System.currentTimeMillis();
        String raw = "https://raw.githubusercontent.com/wjx2951874/HyperFlow/main/update.json" + t;
        return new String[]{
                "https://ghproxy.net/" + raw,
                "https://ghfast.top/" + raw,
                "https://gh-proxy.com/" + raw,
                raw
        };
    }

    // v1.1.0-beta9：Beta 通道 URL
    public static String[] updateJsonUrlsBeta() {
        String t = "?t=" + System.currentTimeMillis();
        String raw = "https://raw.githubusercontent.com/wjx2951874/HyperFlow/beta/update.json" + t;
        return new String[]{
                "https://ghproxy.net/" + raw,
                "https://ghfast.top/" + raw,
                "https://gh-proxy.com/" + raw,
                raw
        };
    }

    // 功能开关（全局配置 key）
    public static final String KEY_FORCE_TRANSFER = "force_transfer";       // 功能① 亮屏强制流转
    public static final String KEY_CLONE_TRANSFER = "clone_transfer";       // 功能② 双开通知流转
    public static final String KEY_CLONE_PREFIX = "clone_prefix";           // 功能② 双开标题标识前缀文字（默认"双开"，实际包成【双开】）
    public static final String KEY_SMS_PERSIST = "sms_persist";             // 功能③ 短信持久化
    public static final String KEY_ARCHIVE_APP = "archive_app";                // ③ App 内消息归档开关（关=消息页不显示）
    public static final String KEY_SMS_NUMERIC_ONLY = "sms_numeric_only";   // ③ 仅纯数字号码写入收件箱
    public static final String KEY_AUTO_UNLOCK = "auto_unlock";             // 功能⑤ 妙享直通：远控授权自动跳过（旧开关，兼容）
    public static final String KEY_AUTO_UNLOCK_PASSWORD = "auto_unlock_pwd"; // 功能⑤ 旧版明文密码（已被 KEY_AUTH_* AES 加密凭证取代，仅兼容读取）
    public static final String KEY_SILENT_AUTH = "silent_auth";              // 妙享直通：远控连接自动跳过密码验证（HookAutoUnlock.unlockSwitch 直过）
    // 妙享直通预存锁屏凭证（供 HookTrustAuth 在 trustservice 授权盘自动填入）
    public static final String KEY_AUTH_TYPE = "auth_type";                 // pin / password / pattern
    public static final String KEY_AUTH_SECRET = "auth_secret";             // AES-GCM 加密的 PIN/混合密码
    public static final String KEY_AUTH_PATTERN = "auth_pattern";           // 图案点序列(0-8，逗号分隔)
    public static final String KEY_GLASS = "glass_effect";                  // UI 玻璃效果开关（毛玻璃卡片）
    public static final String KEY_NAV_FLOAT = "nav_float";                 // 主题：悬浮导航栏开关
    public static final String KEY_DEBUG_MODE = "debug_mode";               // 调试：调试模式（保存详细日志）
    public static final String KEY_ARCHIVE_SORT = "archive_sort";           // 消息列表排序 name_asc/name_desc/time_asc/time_desc（默认 name_asc 按发送人）
    public static final String KEY_DETAIL_SORT = "detail_sort";             // 正文列表排序 desc/asc（默认 desc 新在前）
    public static final String KEY_MSG_LIVE_ONLY = "msg_live_only";         // 消息第一层：仅显示实时获取（本地隐藏不删）/ 本地+实时一起显示

    // 运行时计数器（milink prefs）
    public static final String CNT_FORCE = "cnt_force";
    public static final String CNT_CALL_RELAY = "cnt_call_relay";       // ① hook 触发次数（亮屏被放行的通知数）
    public static final String CNT_CLONE = "cnt_clone";       // ② hook 触发次数（被放行的分身通知数）
    public static final String CNT_SMS = "cnt_sms";           // ③ hook 触发次数（收到的流转短信数）
    public static final String CNT_SMS_INJECT = "cnt_sms_inject"; // ③ 成功注入收件箱次数
    public static final String CNT_SMS_SKIP = "cnt_sms_skip"; // ③ 因号码非纯数字跳过收件箱次数

    private static SharedPreferences sp;
    private static Context app;

    // 全局配置缓存（hook 进程内，V0.3.0 缩短到 800ms → 开关切换秒级生效）
    private static volatile JSONObject cfgCache;
    private static volatile long cfgTs;

    private static final long CFG_TTL_MS = 800L;

    public static void init(Context ctx) {
        if (ctx != null) {
            app = ctx.getApplicationContext();
        }
    }

    /** 惰性获取 milink 进程的 Application（首次真正需要时） */
    public static Context getContext() {
        if (app == null) {
            try {
                Object at = Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null);
                if (at instanceof Context) {
                    app = (Context) at;
                }
            } catch (Throwable t) {
                MiflowLog.e("getContext failed", t);
            }
        }
        return app;
    }

    private static synchronized SharedPreferences sp() {
        if (sp == null) {
            Context c = getContext();
            if (c != null) {
                sp = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            }
        }
        return sp;
    }

    // ---------- 全局配置（hook 侧：su 读 + 缓存） ----------

    private static JSONObject cfg() {
        long now = System.currentTimeMillis();
        if (cfgCache == null || now - cfgTs > CFG_TTL_MS) {
            try {
                String su = RootExec.suPathOr(null);
                if (su != null) {
                    Process p = new ProcessBuilder(su, "-c", "cat " + GLOBAL_CFG + " 2>/dev/null").start();
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = r.readLine()) != null) {
                        sb.append(l);
                    }
                    if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) && sb.length() > 0) {
                        cfgCache = new JSONObject(sb.toString());
                        cfgTs = now;
                    }
                }
            } catch (Throwable t) {
                MiflowLog.w("cfg read failed: " + t.getMessage());
            }
        }
        return cfgCache;
    }

    public static boolean getBool(String key, boolean def) {
        JSONObject c = cfg();
        if (c == null) {
            return def;
        }
        return c.optBoolean(key, def);
    }

    public static String getString(String key, String def) {
        JSONObject c = cfg();
        if (c == null) {
            return def;
        }
        return c.optString(key, def);
    }

    public static int getInt(String key, int def) {
        JSONObject c = cfg();
        if (c == null) {
            return def;
        }
        return c.optInt(key, def);
    }

    // ---------- 计数器（milink prefs） ----------

    public static synchronized void bump(String key) {
        SharedPreferences s = sp();
        if (s != null) {
            s.edit().putInt(key, getIntFromSp(key, 0) + 1).apply();
        }
    }

    private static int getIntFromSp(String key, int def) {
        SharedPreferences s = sp();
        return s == null ? def : s.getInt(key, def);
    }

    public static boolean isForceTransferEnabled() {
        return getBool(KEY_FORCE_TRANSFER, false);
    }

    public static boolean isCloneTransferEnabled() {
        return getBool(KEY_CLONE_TRANSFER, true);
    }

    /** 双开标题标识前缀文字（不含方括号）；空白回退默认"双开"。hook 端与 App 端共用。 */
    public static String getClonePrefix() {
        String p = getString(KEY_CLONE_PREFIX, "双开").trim();
        return p.isEmpty() ? "双开" : p;
    }

    public static boolean isSmsPersistEnabled() {
        // V0.4.7：默认关闭 —— 写入系统短信会触发短信通知流转回环（发送端收到两次）。
        // 建议使用 App 内「消息」页归档；如需写入可在设置中手动开启。
        return getBool(KEY_SMS_PERSIST, false);
    }

    public static boolean isSmsNumericOnly() {
        // V0.2.8：默认 false —— 所有带号码的短信都注入（用户要求不只验证码）
        return getBool(KEY_SMS_NUMERIC_ONLY, false);
    }

    public static boolean isAutoUnlockEnabled() {
        return getBool(KEY_AUTO_UNLOCK, false); // P1 默认关闭
    }

    // ===== 妙享直通预存锁屏凭证（本机混淆级 AES-GCM，非云端） =====
    // 密钥由内置常量 SHA-256 派生，App 与 hook 进程(trustservice)用同一代码即可复现，
    // 只做"配置文件不明文一眼可见"的本机保护，不等于硬件级加密。
    private static final byte[] AUTH_KEY;
    static {
        byte[] seed = ("HyperFlow::miAuth::v1::" + "9f3c2a71e5b4").getBytes(StandardCharsets.UTF_8);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            AUTH_KEY = md.digest(seed);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 明文 → Base64( iv(12) || GCM密文+tag )；失败返回 null */
    public static String authEncrypt(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AUTH_KEY, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Throwable t) {
            MiflowLog.e("authEncrypt failed", t);
            return null;
        }
    }

    /** authEncrypt 的逆；失败/被篡改返回 null */
    public static String authDecrypt(String packed) {
        if (packed == null || packed.isEmpty()) return null;
        try {
            byte[] all = Base64.getDecoder().decode(packed);
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            byte[] ct = new byte[all.length - 12];
            System.arraycopy(all, 12, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AUTH_KEY, "AES"), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String getAuthType() {
        return getString(KEY_AUTH_TYPE, "");
    }

    /** 解密后的 PIN/混合密码；无或失败返回 null */
    public static String getAuthSecret() {
        return authDecrypt(getString(KEY_AUTH_SECRET, ""));
    }

    public static String getAuthPattern() {
        return getString(KEY_AUTH_PATTERN, "");
    }
}
