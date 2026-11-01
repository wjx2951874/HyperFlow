package com.hyperflowplus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperFlow 通用弹窗容器。
 *
 * 按 miuix 文档 `docs/components/overlaydialog.md` 使用 [OverlayDialog]：
 * - 渲染在根 Scaffold 的 MiuixPopupHost（App 内浮层，不是系统 Dialog，KernelSU 环境不闪退）；
 * - 底部滑入弹簧 + 遮罩淡入淡出，关闭时完整播放退场；
 * - 遮罩铺满整个窗口（含状态栏/导航条区域），不再出现"上下两条没被压暗"的色带；
 * - 键盘弹窗由 DialogContentLayout 的 IME inset 处理，输入框贴输入法上方。
 *
 * 保留原有调用方式（title / summary / titleAction / centered / inApp）；
 * inApp 仅为兼容历史调用 —— 浮层本身就在 App 内渲染，键盘跟随不再分叉。
 */
@Composable
fun HyperDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    // v0.5.15：标题行右侧动作位（如环境检测弹窗右上角小"重新检测"按钮），不参与居中布局
    titleAction: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    bottomInset: Dp = smartInset,
    /** true=屏幕正中央对齐（缩放淡入），false=底部弹出（自下而上滑入） */
    centered: Boolean = false,
    @Suppress("UNUSED_PARAMETER") inApp: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    // 弹窗打开时接管返回键：先关弹窗，不让系统返回穿透到页面/退出 App
    BackHandler(enabled = show) { onDismiss() }

    val cardColor = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF262626)  // 黑色模式：浅黑卡片（非纯黑）
    } else {
        MiuixTheme.colorScheme.surface
    }
    // titleAction 需要把动作按钮叠在标题行右侧，OverlayDialog 的 string 标题槽放不下，
    // 此时自绘标题区；其余情况用组件标准标题样式。
    val customHeader = titleAction != null

    OverlayDialog(
        show = show,
        title = if (customHeader) null else title,
        summary = if (customHeader) null else summary,
        backgroundColor = cardColor,
        // 01 手册 §6：28dp 圆角卡片；底部弹层保留 20dp 外边距 + 悬浮胶囊避让
        outsideMargin = DpSize(20.dp, if (centered) 20.dp else bottomInset),
        insideMargin = DpSize(26.dp, 28.dp),
        cornerRadius = 28.dp,
        // 输入类弹窗走居中缩放；普通确认/说明走底部滑入
        largeScreen = if (centered) true else null,
        enableWindowDim = true,
        onDismissRequest = { onDismiss() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (customHeader) {
                Box(Modifier.fillMaxWidth()) {
                    if (title != null) {
                        Text(
                            title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    titleAction?.let {
                        Box(Modifier.align(Alignment.CenterEnd)) { it() }
                    }
                }
                Spacer(Modifier.height(10.dp))
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
            }
            content()
        }
    }
}

/** 智能底部间距：悬浮/液态玻璃开启时弹窗自动上移更多（MainActivity 每次重组时设置）。 */
var smartInset: Dp = 40.dp
