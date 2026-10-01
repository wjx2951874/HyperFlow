package com.hyperflowplus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
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
