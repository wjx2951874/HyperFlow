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
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 分组标题（设置/消息/首页分组用） */
@Composable
fun GroupTitle(text: String) {
    Text(
        text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
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

/**
 * 开关项（替代 miuix SwitchPreference）：**只有右侧 Switch 可点切换**，
 * 标题/摘要行不触发开关（用户要求：点击开关打开，而不是点击标题进行开关操作）。
 * onTitleClick 非空时标题列可点（供"彩蛋机关"等特殊入口用），默认 null 则标题完全不可点。
 */
@Composable
fun SwitchPref(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTitleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(
                    if (onTitleClick != null) Modifier.clickable(
                        // 机关入口（连点标题触发）：无涟漪无高亮，避免暴露可点击性
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTitleClick
                    ) else Modifier
                )
        ) {
            Text(
                title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackground
            )
            if (summary != null) {
                Text(
                    summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        // 只有 Switch 本身响应点击切换
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
