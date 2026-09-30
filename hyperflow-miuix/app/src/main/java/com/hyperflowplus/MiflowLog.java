package com.hyperflowplus;

import android.util.Log;

/**
 * 模块日志（V0.2）。
 * 优先走 libxposed 框架日志（LSPosed 管理器「日志」页可见，milink 的 logcat 被压制也看不到）；
 * 框架不可用时回退 android.util.Log。
 */
public final class MiflowLog {
    public static final String TAG = "HyperFlowPlus";

    private MiflowLog() {
    }

    public static void v(String msg) {
        log(Log.VERBOSE, msg, null);
    }

    public static void d(String msg) {
        log(Log.DEBUG, msg, null);
    }

    public static void i(String msg) {
        log(Log.INFO, msg, null);
    }

    public static void w(String msg) {
        log(Log.WARN, msg, null);
    }

    public static void e(String msg) {
        log(Log.ERROR, msg, null);
    }

    public static void e(String msg, Throwable t) {
        log(Log.ERROR, msg, t);
    }

    private static void log(int pri, String msg, Throwable t) {
        try {
            XposedEntry m = XposedEntry.get();
            if (m != null) {
                if (t != null) {
                    m.log(pri, TAG, msg, t);
                } else {
                    m.log(pri, TAG, msg);
                }
                return;
            }
        } catch (Throwable ignored) {
        }
        if (t != null) {
            Log.e(TAG, msg, t);
        } else {
            Log.println(pri, TAG, msg);
        }
    }
}
