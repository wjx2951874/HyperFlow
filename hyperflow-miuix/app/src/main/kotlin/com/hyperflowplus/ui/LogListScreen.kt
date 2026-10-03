package com.hyperflowplus.ui

import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.hyperflowplus.BuildConfig
import com.hyperflowplus.HFApplication
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志列表（v0.5.15.2）：列出 filesDir/hf_logs 下所有已捕获的日志文件。
 * 点击某条 → 弹窗查看内容；弹窗内可 分享 / 下载（存到系统下载目录）/ 删除。
 * 空态提示捕获方式：首页"系统"行连点 5 次 或 应用连续闪退 3 次自动捕获。
 */
@Composable
fun LogListScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var logs by remember {
        mutableStateOf(
            File(ctx.filesDir, "hf_logs").listFiles()
                ?.filter { it.isFile && it.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        )
    }
    var viewing by remember { mutableStateOf<File?>(null) }
    var confirmDel by remember { mutableStateOf<File?>(null) }
    var confirmDelAll by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    fun refresh() {
        logs = File(ctx.filesDir, "hf_logs").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteFile(f: File) {
        f.delete()
        refresh()
    }

    fun shareFile(f: File) {
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, "com.hyperflowplus.fileprovider", f
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "HyperFlow v${BuildConfig.VERSION_NAME} 日志")
                putExtra(Intent.EXTRA_TEXT, "HyperFlow v${BuildConfig.VERSION_NAME} 日志\n详见附件文件")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "分享日志文件"))
        }.onFailure {
            Toast.makeText(ctx, "无可用分享应用", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadFile(f: File) {
        Thread {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "HyperFlow_${f.name}")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HyperFlow")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                val ok = uri != null && ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(f.readBytes())
                } != null
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        ctx,
                        if (ok) "已保存到 下载/HyperFlow/" else "下载保存失败",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(ctx, "下载保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // V0.6.15.1：顶部栏改用 Miuix TopAppBar（KSU 单行样式，与开源许可页完全一致：
        // 箭头贴左上 32dp、标题同行、右侧 actions 放数量+一键删除）
        TopAppBar(
            title = "日志列表",
            color = MiuixTheme.colorScheme.surface,
            titleColor = MiuixTheme.colorScheme.onSurface,
            navigationIcon = { MiuixBackButton(onClick = onBack) },
            actions = {
                Text(
                    "${logs.size} 份",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(end = 8.dp)
                )
                // v0.5.15.5：一键删除全部（数量 > 0 时显示，删除前弹确认）
                if (logs.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除全部",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable { confirmDelAll = true }
                    )
                }
            }
        )
        Spacer(Modifier.height(2.dp))

        if (logs.isEmpty()) {
            // 空态
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无日志\n\n首页「系统」行连点 5 次，或应用连续闪退 3 次，\n会自动捕获日志出现在这里",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(logs, key = { it.absolutePath }) { f ->
                val sizeKb = if (f.length() > 0) (f.length() / 1024).toString() else "0"
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { viewing = f }
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                fmt.format(Date(f.lastModified())),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${sizeKb} KB · ${f.name}",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        // 行内快捷操作：分享 / 删除
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "分享",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable { shareFile(f) }
                        )
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable { confirmDel = f }
                        )
                    }
                }
            }
            item {
                Spacer(
                    Modifier.height(
                        if (MainHolder.bottomPad == androidx.compose.ui.unit.Dp.Unspecified) 0.dp
                        else MainHolder.bottomPad
                    )
                )
            }
        }
    }

    // 查看弹窗：滚动展示内容 + 分享 / 下载 / 删除
    viewing?.let { vf ->
        HyperDialog(
            title = "查看日志",
            show = true,
            onDismiss = { viewing = null }
        ) {
            Column(Modifier.fillMaxWidth()) {
                val content = remember(vf) { runCatching { vf.readText() }.getOrDefault("(读取失败)") }
                Text(
                    vf.name,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        content,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            shareFile(vf)
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Share, null, tint = MiuixTheme.colorScheme.onPrimary, modifier = Modifier.height(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("分享") } }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { downloadFile(vf) },
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.height(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("下载") } }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { confirmDel = vf; viewing = null },
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.height(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("删除") } }
                }
            }
        }
    }

    // 删除确认弹窗
    confirmDel?.let { df ->
        HyperDialog(
            title = "删除日志",
            show = true,
            onDismiss = { confirmDel = null }
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "确定删除这份日志吗？\n${df.name}",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { confirmDel = null },
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            deleteFile(df)
                            confirmDel = null
                            Toast.makeText(ctx, "已删除", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) { Text("删除") }
                }
            }
        }
    }

    // v0.5.15.5：一键删除全部确认弹窗
    if (confirmDelAll) {
        HyperDialog(
            title = "删除全部日志",
            show = true,
            onDismiss = { confirmDelAll = false }
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "确定删除全部 ${logs.size} 份日志吗？\n此操作不可恢复。",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { confirmDelAll = false },
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            File(ctx.filesDir, "hf_logs").listFiles()
                                ?.filter { it.isFile && it.name.endsWith(".txt") }
                                ?.forEach { it.delete() }
                            confirmDelAll = false
                            refresh()
                            Toast.makeText(ctx, "已删除全部日志", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) { Text("全部删除") }
                }
            }
        }
    }
}
