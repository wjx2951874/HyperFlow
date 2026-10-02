package com.hyperflowplus

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 全局崩溃捕获：
 * 1) 每次闪退把堆栈写到私有文件 hf_crash.log（无需终端抓日志）；
 * 2) v0.5.11：连续闪退计数 —— 10 分钟内连续闪退达 3 次，自动捕获一次 logcat 快照
 *    存到 filesDir/hf_logs/，供反馈问题时一键分享/定位（替代原"调试模式"开关）。
 */
class HFApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 连续闪退计数（10 分钟内连续，超出重置）：
        // 达到 3 次 → 后台捕获一次完整日志，然后清零重新计数
        val p = getSharedPreferences("hf_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = p.getLong("crash_last_ts", 0L)
        val streak = if (now - last < 10 * 60 * 1000L) p.getInt("crash_count", 0) + 1 else 1
        p.edit().putInt("crash_count", streak).putLong("crash_last_ts", now).apply()
        if (streak >= 3) {
            p.edit().putInt("crash_count", 0).apply()
            Thread { runCatching { captureLogcat(this) } }.start()
        }

        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, "hf_crash.log").writeText(
                    "time=" + System.currentTimeMillis() + "\n" + sw.toString()
                )
            }
            prev?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        /**
         * 捕获 logcat 快照存到 filesDir/hf_logs/（含崩溃堆栈），返回日志文件。
         * 优先走 root（su 会话，HyperFlow 环境必在），失败回退普通 logcat。
         * 供首页"连点机型 3 次"机关与连续闪退自动捕获共用。
         */
        @JvmStatic
        fun captureLogcat(ctx: Context): File? = runCatching {
            val dir = File(ctx.filesDir, "hf_logs").apply { mkdirs() }
            val f = File(dir, "hf_log_${System.currentTimeMillis()}.txt")
            val log = runCatching {
                RootExec.su(
                    "logcat -d 2>/dev/null | grep -iE 'hyperflow|hyperflowplus|milink|LSPosed|AndroidRuntime|FATAL|Exception' | tail -800"
                )
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    ProcessBuilder("logcat", "-d")
                        .redirectErrorStream(true)
                        .start()
                        .inputStream.readBytes().toString(Charsets.UTF_8)
                        .lineSequence().filter {
                            it.contains("hyperflow", true) || it.contains("hyperflowplus", true) ||
                                    it.contains("milink", true) || it.contains("LSPosed", true) ||
                                    it.contains("AndroidRuntime", true) || it.contains("FATAL", true)
                        }.joinToString("\n").takeLast(60_000)
                }.getOrDefault("")
            f.writeText(
                "HyperFlow log @ " + System.currentTimeMillis() + " v" + BuildConfig.VERSION_NAME + "\n" +
                        "device=" + android.os.Build.MODEL + " / Android " + android.os.Build.VERSION.RELEASE +
                        " (API " + android.os.Build.VERSION.SDK_INT + ")\n\n" + log
            )
            f
        }.getOrNull()
    }
}
