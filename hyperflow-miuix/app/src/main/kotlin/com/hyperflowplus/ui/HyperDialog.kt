package com.hyperflowplus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperFlow 通用弹窗容器（MIUI/HyperOS 风格卡片，App 内 Box 覆盖层实现）。
 *
 * 实现说明：全屏 Box 遮罩 + 圆角卡片，不创建独立 Window，不依赖系统 Dialog
 * （部分 KernelSU 环境会闪退，用户多轮实测：仅 App 内覆盖层稳定）。
 * 显隐由 AnimatedVisibility(visible = show) 正确驱动 enter/exit，多弹窗切换不再卡死。
 *
 * 视觉：框体从屏幕底部往上弹出，标题/正文居中；遮罩轻黑 0.35；
 * 黑色模式卡片浅黑（0xFF262626，非纯黑）。
 * 交互：点击卡片外遮罩区 = onDismiss；卡片内点击不冒泡。
 */
@Composable
fun HyperDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    onDismiss: () -> Unit,
    bottomInset: Dp = 48.dp,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    AnimatedVisibility(
        visible = show,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280)) + fadeIn(),
        exit = fadeOut(tween(150))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = bottomInset)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (MiuixTheme.colorScheme.background.luminance() < 0.5f)
                            Color(0xFF262626)  // 黑色模式：浅黑卡片（非纯黑）
                        else MiuixTheme.colorScheme.surface
                    )
                    // 空 clickable 消费卡片内点击，防止冒泡到遮罩层误关
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (title != null) {
                    Text(
                        title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (summary != null) {
                    Text(
                        summary,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                }
                content()
            }
        }
    }
}
