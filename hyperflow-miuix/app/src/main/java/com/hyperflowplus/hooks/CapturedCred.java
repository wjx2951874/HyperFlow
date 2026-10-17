package com.hyperflowplus.hooks;

import com.hyperflowplus.MiflowLog;

/**
 * 捕获凭证的公共落盘通道（免预填）。
 *
 * 主路径：/data/system/hyperflow_captured —— system_server 与 trustservice（system uid）都可写，
 * 普通 App 进程读不到（更私密），App 侧用 root 读回后加密入库。
 * 写公共文件失败（个别 ROM 进程 SELinux 域限制）时回退 trustservice 进程私有 SharedPreferences，
 * App 侧两个位置都会读，优先公共文件。
 *
 * 文件格式（JSON，单行）：
 *   {"type":"pin|password|pattern","secret":"...","pattern":"..."}
 * 只由"校验成功"的捕获写入，输错的密码永远不落盘。
 */
public class CapturedCred {

    /** 公共文件：system uid 进程可写，App root 可读；普通进程不可读。 */
    public static final String CAP_FILE = "/data/system/hyperflow_captured";

    /** 回退位置：trustservice 进程私有 sp（旧版通道，兼容读取）。 */
    public static final String FALLBACK_SP = "hyperflow_captured";
    public static final String KEY_TYPE = "hf_cap_type";
    public static final String KEY_SECRET = "hf_cap_secret";
    public static final String KEY_PATTERN = "hf_cap_pattern";

    /**
     * 写入捕获的凭证：先写公共文件，再尝试写 trustservice 私有 sp（非 trustservice 进程
     * 写 sp 会失败被吞，无害；公共文件成功即够）。
     */
    public static void write(String type, String raw) {
        if (type == null || raw == null || raw.isEmpty()) {
            return;
        }
        boolean pubOk = writePublic(type, raw);
        writeSp(type, raw);
        MiflowLog.i("CapturedCred: " + type + " (len=" + raw.length() + ")"
                + (pubOk ? " -> " + CAP_FILE : " -> sp-only"));
    }

    private static boolean writePublic(String type, String raw) {
        try {
            String secret = "pattern".equals(type) ? "" : esc(raw);
            String pattern = "pattern".equals(type) ? esc(raw) : "";
            String json = "{\"type\":\"" + esc(type) + "\",\"secret\":\"" + secret
                    + "\",\"pattern\":\"" + pattern + "\"}\n";
            // 单引号转义后整段写入，避免 shell 拆词；644 让 App(root) 与 system 都能读
            String cmd = "echo '" + json.replace("'", "'\\''") + "' > " + CAP_FILE
                    + " && chmod 644 " + CAP_FILE;
            Process p = new ProcessBuilder("/system/bin/sh", "-c", cmd)
                    .redirectErrorStream(true).start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Throwable t) {
            MiflowLog.w("CapturedCred: writePublic failed: " + t);
            return false;
        }
    }

    private static void writeSp(String type, String raw) {
        try {
            android.app.Application app = (android.app.Application)
                    Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (app == null) {
                return;
            }
            android.content.SharedPreferences sp =
                    app.getSharedPreferences(FALLBACK_SP, android.content.Context.MODE_PRIVATE);
            sp.edit()
                    .putString(KEY_TYPE, type)
                    .putString(KEY_SECRET, "pattern".equals(type) ? "" : raw)
                    .putString(KEY_PATTERN, "pattern".equals(type) ? raw : "")
                    .commit();
        } catch (Throwable t) {
            // 非 trustservice 进程写不了它的私有 sp，静默忽略
        }
    }

    /** JSON 字符串转义（密码为数字/点序串，极少特殊字符，兜底处理即可）。 */
    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "").replace("\r", "");
    }
}
