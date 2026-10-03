package com.hyperflowplus;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;

/**
 * root 通道：把流转短信以真实短信形式注入收件箱。
 * 方式：把 content insert 命令写成脚本文件（避免 shell 引号问题），再通过 su 以 root 执行。
 * 前提：com.milink.service 已在 KernelSU 超级用户白名单中（ksu 辅助模块或手动添加）。
 */
public final class RootExec {
    // KernelSU/Magisk 常见 su 路径；按顺序尝试（KSU 新版挂载在 /debug_ramdisk/ksu）
    private static final String[] SU_CANDIDATES = {
            "/system/bin/su",
            "/data/adb/ksu/bin/su",
            "/debug_ramdisk/ksu/bin/su",
            "/debug_ramdisk/ksu/su",
            "su"
    };
    private static final String CONTENT_CMD = "content insert --uri content://sms/inbox";

    // V0.3.0：su 路径缓存（找到一次就记住，避免每次启动循环探测 5 个路径导致卡顿）
    private static volatile String suCache;
    private static volatile long suMissTs;

    private RootExec() {
    }

    /**
     * @param address 发信号码（纯数字）
     * @param body    正文（模块负责加"来自设备名｜"前缀）
     * @param dateMs  时间（epoch 毫秒）
     * @return true=注入成功（exit 0）
     */
    public static boolean insertSms(Context ctx, String address, String body, long dateMs) {
        File script = null;
        try {
            script = new File(ctx.getFilesDir(), "msms.sh");
            StringBuilder sb = new StringBuilder();
            sb.append(CONTENT_CMD);
            sb.append(" --bind address:s:").append(shq(address));
            sb.append(" --bind body:s:").append(shq(body));
            sb.append(" --bind read:i:1");
            sb.append(" --bind seen:i:1");
            // V0.6.16.4：显式 type=1（收件箱）——root content insert 若不写 type，
            // 默认值可能不是收件箱（1），短信 App 收件箱将不显示已写入的流转短信
            sb.append(" --bind type:i:1");
            sb.append(" --bind date:l:").append(dateMs);
            sb.append("\n");
            FileWriter w = new FileWriter(script);
            w.write(sb.toString());
            w.close();

            String su = firstAvailableSu();
            if (su == null) {
                MiflowLog.w("su not found, add com.milink.service to KSU allowlist");
                return false;
            }
            Process p = new ProcessBuilder(su, "-c", "sh " + script.getAbsolutePath()).start();
            String out = readAll(p.getInputStream());
            String err = readAll(p.getErrorStream());
            int code = p.waitFor();
            MiflowLog.d("insertSms code=" + code + " out=" + out + " err=" + err);
            return code == 0;
        } catch (Throwable t) {
            MiflowLog.e("insertSms failed", t);
            return false;
        } finally {
            if (script != null) {
                try {
                    // 脚本内含正文，用完即删
                    script.delete();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** su 是否可用（WebUI 状态展示用） */
    public static boolean isSuAvailable() {
        try {
            String su = firstAvailableSu();
            if (su == null) {
                return false;
            }
            Process p = new ProcessBuilder(su, "-c", "id -u").start();
            String out = readAll(p.getInputStream()).trim();
            int code = p.waitFor();
            return code == 0 && "0".equals(out);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String firstAvailableSu() {
        if (suCache != null) {
            return suCache;
        }
        long now = System.currentTimeMillis();
        if (now - suMissTs < 2000) {
            return null; // 2 秒内探测失败不再重试，避免疯狂起进程
        }
        for (String c : SU_CANDIDATES) {
            try {
                Process p = new ProcessBuilder(c, "-c", "id -u").start();
                String out = readAll(p.getInputStream()).trim();
                if (p.waitFor() == 0 && "0".equals(out)) {
                    suCache = c;
                    return c;
                }
            } catch (Throwable ignored) {
            }
        }
        suMissTs = now;
        return null;
    }

    /** 对外：取可用 su 路径，找不到返回 fallback（WebUI 卸载等调用） */
    public static String suPathOr(String fallback) {
        String su = firstAvailableSu();
        return su == null ? fallback : su;
    }

    /** 强制重探（如用户刚授权后） */
    public static void resetSuCache() {
        suCache = null;
        suMissTs = 0;
    }

    /** 以 su 运行整条命令，返回 stdout（失败返回 null） */
    public static String su(String cmd) {
        try {
            String su = firstAvailableSu();
            if (su == null) {
                return null;
            }
            Process p = new ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start();
            String out = readAll(p.getInputStream());
            p.waitFor();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 以 su 把 json 写入全局配置（单引号安全转义） */
    public static void writeJson(Context ctx, String path, String json) {
        String esc = json == null ? "" : json.replace("'", "'\\''");
        su("mkdir -p $(dirname " + path + ") && echo '" + esc + "' > " + path + " && chmod 644 " + path);
    }

    /** 通用 root 执行：以 su 运行命令，返回退出码+输出 */
    public static ExecResult exec(String... cmd) {
        try {
            String su = firstAvailableSu();
            if (su == null) {
                return new ExecResult(-1, "", "no su available (add apps to KSU allowlist)");
            }
            String[] full = new String[cmd.length + 2];
            full[0] = su;
            full[1] = "-c";
            System.arraycopy(cmd, 0, full, 2, cmd.length);
            Process p = new ProcessBuilder(full).start();
            String out = readAll(p.getInputStream());
            String err = readAll(p.getErrorStream());
            int code = p.waitFor();
            return new ExecResult(code, out, err);
        } catch (Throwable t) {
            return new ExecResult(-1, "", String.valueOf(t));
        }
    }

    public static final class ExecResult {
        public final int code;
        public final String out;
        public final String err;

        public ExecResult(int code, String out, String err) {
            this.code = code;
            this.out = out == null ? "" : out;
            this.err = err == null ? "" : err;
        }

        public boolean ok() {
            return code == 0;
        }
    }

    /** shell 单引号包裹，内部单引号用 '\'' 转义 */
    private static String shq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toString("UTF-8");
    }
}
