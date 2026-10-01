package com.hyperflowplus.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 通用确认弹窗（Miuix WindowDialog 样式，与引导页/检测更新一致）。
 * - 左侧白色按钮（cancelText），右侧蓝色按钮（confirmText，倒计时内不可点）
 * - countdownSec > 0：确定按钮倒计时（期间点击 Toast 提示还剩 X 秒）
 * - onCancel：点左侧按钮时执行（默认只关闭弹窗）
 * - onConfirm：点右侧按钮时执行
 */
@Composable
fun ConfirmDialog(
    show: Boolean,
    title: String,
    content: String,
    cancelText: String = "取消",
    confirmText: String = "确定",
    countdownSec: Int = 0,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit = onDismiss
) {
    var count by remember(show, countdownSec) { mutableIntStateOf(countdownSec) }
    LaunchedEffect(show, countdownSec) {
        if (show && countdownSec > 0) {
            count = countdownSec
            while (count > 0) {
                delay(1000)
                count--
            }
        }
    }
    val ctx = LocalContext.current
    WindowDialog(
        title = title,
        summary = content,
        show = show,
        onDismissRequest = { onDismiss() }
    ) {
        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = { onCancel() },
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f)
            ) {
                Text(cancelText)
            }
            Spacer(Modifier.width(20.dp))
            Button(
                onClick = {
                    if (count > 0) {
                        Toast.makeText(ctx, "还剩 $count 秒，请先阅读风险说明", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm()
                    }
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f)
            ) {
                Text(confirmText)
            }
        }
    }
}
