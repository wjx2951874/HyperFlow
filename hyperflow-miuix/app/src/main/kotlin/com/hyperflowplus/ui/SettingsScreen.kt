package com.hyperflowplus.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyperflowplus.BuildConfig
import com.hyperflowplus.Config
import com.hyperflowplus.HFState
import com.hyperflowplus.RootExec
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页：外观（玻璃）/ 关于（作者、版本与更新、引导） */
@Composable
fun SettingsScreen(state: HFState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    var showUpd by remember { mutableStateOf(false) }
    var updVer by remember { mutableStateOf("") }
    var updUrl by remember { mutableStateOf("") }
    var updLog by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        GroupTitle("外观")

        Card(Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "柔光玻璃",
                    summary = "AndroidLiquidGlass 磨砂玻璃（顶栏/底栏跟随壁纸模糊）",
                    checked = state.glassOn,
                    onCheckedChange = { state.set("glass_effect", it) }
                )
                if (state.glassOn) {
                    // 模糊强度三档（8 柔和 / 16 标准 / 24 强烈），点击即时生效，选中高亮
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        listOf(
                            "柔和" to 8,
                            "标准" to 16,
                            "强烈" to 24
                        ).forEach { (label, v) ->
                            val sel = state.glassBlur == v
                            Text(
                                label,
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { state.setInt("glass_blur", v) }
                                    .padding(vertical = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        GroupTitle("关于")

        Card(Modifier.fillMaxWidth()) {
            Column {
                ArrowPreference(
                    title = "HyperFlow",
                    summary = "澎湃OS 互联通知流转增强 · V${BuildConfig.VERSION_NAME}",
                    onClick = {}
                )
                ArrowPreference(
                    title = "作者",
                    summary = "酷安@翰德姆",
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://www.coolapk.com/u/4112338")
                                )
                            )
                        }
                    }
                )
                ArrowPreference(
                    title = "版本与更新",
                    summary = "V${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）· 点击检查更新",
                    onClick = {
                        checkUpdate(ctx,
                            onNew = { ver, url, log ->
                                updVer = ver; updUrl = url; updLog = log; showUpd = true
                            },
                            onNone = { Toast.makeText(ctx, "当前已是最新版本", Toast.LENGTH_SHORT).show() },
                            onError = { Toast.makeText(ctx, "检查更新失败：$it", Toast.LENGTH_SHORT).show() }
                        )
                    }
                )
                ArrowPreference(
                    title = "重新查看引导",
                    summary = "查看功能说明与状态检测",
                    onClick = { state.showOnboarding = true }
                )
            }
        }
    }

    if (showUpd) {
        OverlayDialog(
            title = "发现新版本 V$updVer",
            summary = updLog.ifEmpty { "点击按钮下载并一键安装（ksud module install）" },
            show = showUpd,
            onDismissRequest = { showUpd = false }
        ) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    text = "取消",
                    onClick = { showUpd = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = "下载并安装",
                    onClick = {
                        showUpd = false
                        Toast.makeText(ctx, "开始下载", Toast.LENGTH_SHORT).show()
                        Thread {
                            runCatching {
                                val f = java.io.File(
                                    ctx.getExternalFilesDir(null) ?: ctx.filesDir,
                                    "HyperFlow.zip"
                                )
                                val conn = java.net.URL(updUrl).openConnection()
                                conn.connectTimeout = 10000
                                conn.inputStream.use { input ->
                                    f.outputStream().use { out -> input.copyTo(out) }
                                }
                                RootExec.su("ksud module install '" + f.absolutePath + "' && reboot")
                            }
                        }.start()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun checkUpdate(
    ctx: android.content.Context,
    onNew: (String, String, String) -> Unit,
    onNone: () -> Unit,
    onError: (String) -> Unit
) {
    Thread {
        val urls = listOf(Config.UPDATE_JSON, Config.UPDATE_JSON_FALLBACK)
        try {
            var lastErr: Throwable? = null
            var text = ""
            for (u in urls) {
                try {
                    val conn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.instanceFollowRedirects = true
                    text = conn.inputStream.bufferedReader().use { it.readText() }
                    break
                } catch (t: Throwable) { lastErr = t }
            }
            if (text.isEmpty()) throw lastErr ?: RuntimeException("更新通道不可达")
            val json = org.json.JSONObject(text)
            val ver = json.optString("version", "")
            val vc = json.optInt("versionCode", 0)
            val zipUrl = json.optString("zipUrl", "")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (vc > BuildConfig.VERSION_CODE && zipUrl.isNotEmpty()) {
                    onNew(ver, zipUrl, json.optString("changelog", ""))
                } else {
                    onNone()
                }
            }
        } catch (t: Throwable) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { onError(t.message ?: "网络错误") }
        }
    }.start()
}
