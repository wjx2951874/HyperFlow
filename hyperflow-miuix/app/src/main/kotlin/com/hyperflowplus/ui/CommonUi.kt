package com.hyperflowplus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分组标题（设置/消息/首页分组用）；onTitleClick 非空时支持无反馈连点机关入口。
 *
 * 规格对齐 miuix SmallTitle（01 手册 §3）：subtitle 14sp Bold + onBackgroundVariant，
 * 左侧 28dp 缩进 = 卡片外边距 12dp + 行内边距 16dp，与卡内标题落在同一条竖线上。
 * 注意：使用本组件的页面滚动容器不要再加水平 padding，12dp 由 Card 自带。
 */
@Composable
fun GroupTitle(text: String, onTitleClick: (() -> Unit)? = null) {
    Text(
        text,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        modifier = Modifier
            .padding(start = 28.dp, top = 8.dp, end = 28.dp, bottom = 8.dp)
            .then(
                if (onTitleClick != null) Modifier.clickable(
                    // 机关入口：无涟漪无高亮，避免暴露可点击性
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTitleClick
                ) else Modifier
            )
    )
}

/** 统一 MIUI 返回箭头（V0.6.15）：MiuixIcons.Back，32dp 左上标准尺寸，所有二级页共用 */
@Composable
fun MiuixBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            modifier = Modifier.size(32.dp)
        )
    }
}

