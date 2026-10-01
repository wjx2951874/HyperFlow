package com.hyperflowplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperFlow 通用弹窗容器（MIUI/HyperOS 风格卡片，App 内覆盖层实现）。
 *
 * 实现说明：与引导页同款方案——全屏 Box 遮罩 + 圆角卡片，不创建独立 Window，
 * 不依赖 miuix WindowDialog/OverlayDialog/系统 Dialog（这些在部分 KernelSU 环境会闪退，
 * 用户多轮实测：凡弹窗相关均闪退，仅引导页覆盖层稳定）。故全部弹窗统一走本组件。
 *
 * 交互：点击卡片外遮罩区 = onDismiss（同 MIUI 弹窗点外部关闭）；卡片内点击不冒泡。
 *
 * 用法：
 *   HyperDialog(show = visible, title = "标题", summary = "说明", onDismiss = { visible = false }) {
 *       Row { Button(...) "取消" ; Button(...) "确定" }   // 按钮区由调用方自由摆放
 *   }
 */
@Composable
fun HyperDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    if (!show) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MiuixTheme.colorScheme.surface)
                // 空 clickable 消费卡片内点击，防止冒泡到遮罩层误关
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            if (title != null) {
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
            }
            if (summary != null) {
                Text(
                    summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(20.dp))
            }
            content()
        }
    }
}
