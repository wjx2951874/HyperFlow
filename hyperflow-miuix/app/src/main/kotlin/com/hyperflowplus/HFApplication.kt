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
         * 捕获诊断包存到 filesDir/hf_logs/（logcat 快照 + root 环境诊断段），返回日志文件。
         * 优先走 root（su 会话，HyperFlow 环境必在），失败回退普通 logcat。
         * 供首页"连点机型 5 次"机关与连续闪退自动捕获共用。
         *
         * v0.5.15.2：logcat 只抓得到走 android.util.Log 的日志；模块注入日志（MiflowLog）
         * 走 LSP 框架通道、milink 的 logcat 被 MIUI 压制，抓不到。所以新增"诊断段"，
         * 把检测日志(detect.log)、三个运行时探针、LSPosed 目录树、作用域文件、
         * modules_config.db 作用域表、框架包一并打包——一份日志即可完整定位环境问题。
         */
        @JvmStatic
        fun captureLogcat(ctx: Context): File? = runCatching {
            val dir = File(ctx.filesDir, "hf_logs").apply { mkdirs() }
            val f = File(dir, "hf_log_${System.currentTimeMillis()}.txt")
            val log = runCatching {
                RootExec.su(
                    "logcat -d 2>/dev/null | grep -iE 'hyperflow|hyperflowplus|milink|LSPosed|libxposed|Xposed|AndroidRuntime|FATAL|Exception' | tail -800"
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
                                    it.contains("libxposed", true) || it.contains("Xposed", true) ||
                                    it.contains("AndroidRuntime", true) || it.contains("FATAL", true) ||
                                    it.contains("Exception", true)
                        }.joinToString("\n").takeLast(60_000)
                }.getOrDefault("")
            // 诊断段：检测日志 + 探针时间戳 + LSPosed 目录/作用域/数据库 + 框架包（root 抓取）
            val diag = runCatching {
                RootExec.su(
                    "echo '==DETECT_LOG==';\n" +
                            "cat /data/adb/hyperflowplus/detect.log 2>/dev/null || echo '(无检测日志)';\n" +
                            "echo '==PROBES==';\n" +
                            "echo -n 'xposed_loaded(adb): '; cat /data/adb/hyperflowplus/xposed_loaded 2>/dev/null || echo '(无)';\n" +
                            "echo -n 'hf_loaded(milink): '; cat /data/user/0/com.milink.service/files/hf_loaded 2>/dev/null || echo '(无)';\n" +
                            "echo -n 'xposed_loaded(app): '; cat /data/user/0/com.hyperflowplus/files/xposed_loaded 2>/dev/null || echo '(无)';\n" +
                            "echo '==LSPD_TREE==';\n" +
                            "for d in /data/adb/lspd/config /data/adb/modules/lsposed/config /data/adb/modules/zygisk_lsposed/config; do [ -e \"\$d\" ] && { echo \"-- \$d\"; ls -la \"\$d\" 2>/dev/null | head -25; }; done;\n" +
                            "echo '==SCOPE_FILES==';\n" +
                            "for sc in /data/adb/lspd/config/scope/com.hyperflowplus /data/adb/modules/lsposed/config/scope/com.hyperflowplus /data/adb/modules/zygisk_lsposed/config/scope/com.hyperflowplus; do [ -f \"\$sc\" ] && { echo \"-- \$sc\"; cat \"\$sc\" 2>/dev/null; }; done;\n" +
                            "echo '==MODULES_LIST==';\n" +
                            "cat /data/adb/lspd/config/modules.list 2>/dev/null; cat /data/adb/modules/lsposed/config/modules.list 2>/dev/null;\n" +
                            "echo '==DB_SCOPE==';\n" +
                            "if command -v sqlite3 >/dev/null 2>&1; then " +
                            "for db in /data/adb/lspd/config/modules_config.db /data/adb/modules/lsposed/config/modules_config.db /data/adb/modules/zygisk_lsposed/config/modules_config.db; do [ -f \"\$db\" ] && { echo \"-- \$db\"; sqlite3 \"\$db\" 'SELECT m.module_pkg_name,s.app_pkg_name,s.user_id FROM modules m LEFT JOIN scope s ON s.mid=m.mid' 2>/dev/null; }; done; " +
                            "else echo '(环境无 sqlite3 命令，db 无法脚本写入，需在 LSPosed UI 手动勾选)'; fi;\n" +
                            "echo '==FRAMEWORK==';\n" +
                            "pm list packages 2>/dev/null | grep -iE 'lsp|matrix|vector|xposed';\n" +
                            "ps -A 2>/dev/null | grep -iE 'lspd|zygisk|ksud' | head -5;\n" +
                            "echo '==END_DIAG=='"
                )
            }.getOrNull() ?: ""
            f.writeText(
                "HyperFlow log @ " + System.currentTimeMillis() + " v" + BuildConfig.VERSION_NAME + "\n" +
                        "device=" + android.os.Build.MODEL + " / Android " + android.os.Build.VERSION.RELEASE +
                        " (API " + android.os.Build.VERSION.SDK_INT + ")\n\n" + log +
                        "\n\n================== 诊断段（环境与注入证据） ==================\n" + diag
            )
            f
        }.getOrNull()
    }
}
