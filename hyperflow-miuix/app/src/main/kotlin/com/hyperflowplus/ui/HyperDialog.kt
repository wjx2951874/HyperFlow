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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
    // v0.5.15：标题行右侧动作位（如环境检测弹窗右上角小"重新检测"按钮），不参与居中布局
    titleAction: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    bottomInset: Dp = smartInset,
    /** true=屏幕正中央对齐（输入类弹窗，避免底部随键盘上下飘）；false=底部弹出（默认） */
    centered: Boolean = false,
    /**
     * 渲染载体（双开标识输入框等带键盘的弹窗必须用 inApp=true）：
     *   - false（默认）= Popup 独立窗口：能盖住悬浮导航栏，但键盘弹出时 PopupWindow 默认
     *     ADJUST_PAN 会把整个窗口顶上去（卡片"飞上边"），且 Popup 内读不到真实 IME inset
     *     （imePadding 无效/乱蹦）。只适合无键盘的确认类弹窗。
     *   - true = App 内 Box 覆盖层（不建窗口）：在 MainActivity 内容树里渲染，
     *     imePadding 直接响应键盘 inset、跟随动画平滑，输入框紧贴输入法上方不蹦跳。
     *     代价：层级低于系统悬浮导航栏（键盘弹出时悬浮栏通常被系统顶走，影响可忽略）。
     */
    inApp: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    // beta51 修复：show 参数此前完全未被使用（缺少 if(show) 包裹）——
    // 只要 ConfirmDialog 被组合（无论 show 真假）弹窗就无条件渲染 →
    // 切消息 tab 未开启页"强制出现"弹窗、点取消后 showStartDialog=false 但弹窗不关（取消没反应）。
    // 现在 show=false 直接不渲染，所有调用方（含未自己包 if 的 MessagesScreen）显隐恢复正常。
    if (show) {
        if (inApp) {
        // App 内覆盖层：不建独立窗口，imePadding 在 MainActivity 内容树内真实响应键盘。
        // 键盘弹出时遮罩区域收窄到键盘上方，卡片贴键盘顶；键盘动画平滑跟随，不蹦跳。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = if (centered) Alignment.Center else Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = if (centered) 0.dp else bottomInset)
                    .clip(RoundedCornerShape(28.dp))
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
                    .padding(horizontal = 26.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (title != null) {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        titleAction?.let {
                            Box(Modifier.align(Alignment.CenterEnd)) { it() }
                        }
                    }
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
        return
    }
    // 窗口层 Popup 渲染：全屏遮罩盖住包括悬浮导航栏在内的一切（引导页同款层级），
    // 不受 Scaffold/悬浮栏遮挡；非系统 Dialog，KernelSU 环境不闪退
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            excludeFromSystemGesture = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = if (centered) Alignment.Center else Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = if (centered) 0.dp else bottomInset)
                    .clip(RoundedCornerShape(28.dp))
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
                    .padding(horizontal = 26.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (title != null) {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        titleAction?.let {
                            Box(Modifier.align(Alignment.CenterEnd)) { it() }
                        }
                    }
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
}

/** 智能底部间距：悬浮/液态玻璃开启时弹窗自动上移更多（MainActivity 每次重组时设置） */
var smartInset: Dp = 40.dp
