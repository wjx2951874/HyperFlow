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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页：外观（玻璃）/ 关于（作者、版本与更新、引导） */
@Composable
fun SettingsScreen(state: HFState, onCheckUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current


    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        GroupTitle("外观")

        Card(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "配色风格（跟随壁纸 Monet 取色）",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp)
                )
                val options = listOf(
                    "标准" to "tonal_spot",   // 默认：最兼容的取色风格
                    "中性" to "neutral",
                    "鲜艳" to "vibrant",
                    "表现力" to "expressive",
                    "水果沙拉" to "fruit_salad",
                    "单色" to "monochrome"
                )
                options.chunked(3).forEach { rowOpts ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        rowOpts.forEach { (label, key) ->
                            val sel = state.paletteStyle == key
                            Text(
                                label,
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { state.setString("palette_style", key) }
                                    .padding(vertical = 10.dp)
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
                    onClick = { onCheckUpdate() }
                )
                ArrowPreference(
                    title = "重新查看引导",
                    summary = "查看功能说明与状态检测",
                    onClick = { state.showOnboarding = true }
                )
            }
        }
    }

}

