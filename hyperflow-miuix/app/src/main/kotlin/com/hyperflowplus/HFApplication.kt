package com.hyperflowplus

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** 全局崩溃捕获：闪退时把堆栈写到私有文件，下次启动自动展示，无需终端抓日志 */
class HFApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
}
