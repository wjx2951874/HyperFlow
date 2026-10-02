package com.hyperflowplus.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 开源库条目 */
private data class Lib(
    val name: String,
    val version: String,
    val author: String,
    val license: String,
    val url: String,
    val licenseText: String
)

// Apache License 2.0 节选（完整文本见官网）
private val APACHE2 = """
Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

1. Definitions.
"License" shall mean the terms and conditions for use, reproduction, and distribution as defined by Sections 1 through 9 of this document.

2. Grant of Copyright License.
Subject to the terms and conditions of this License, each Contributor hereby grants to You a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable copyright license to reproduce, prepare Derivative Works of, publicly display, publicly perform, sublicense, and distribute the Work and such Derivative Works in Source or Object form.

3. Grant of Patent License.
Subject to the terms and conditions of this License, each Contributor hereby grants to You a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable (except as stated in this section) patent license to make, have made, use, offer to sell, sell, import, and otherwise transfer the Work.

4. Redistribution.
You may reproduce and distribute copies of the Work or Derivative Works thereof in any medium, with or without modifications, and in Source or Object form, provided that You meet the following conditions:
(a) You must give any other recipients of the Work or Derivative Works a copy of this License; and
(b) You must cause any modified files to carry prominent notices stating that You changed the files; and
(c) You must retain, in the Source form of any Derivative Works that You distribute, all copyright, patent, trademark, and attribution notices from the Source form of the Work; and
(d) If the Work includes a "NOTICE" text file, any Derivative Works that You distribute must include a readable copy of the attribution notices contained within such NOTICE file.

完整条款见 https://www.apache.org/licenses/LICENSE-2.0
""".trimIndent()

// GNU GPL v3 节选（完整文本见官网）
private val GPL3 = """
GNU GENERAL PUBLIC LICENSE
Version 3, 29 June 2007

Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>

The GNU General Public License is a free, copyleft license for software and other kinds of works.

The licenses for most software and other practical works are designed to take away your freedom to share and change the works. By contrast, the GNU General Public License is intended to guarantee your freedom to share and change all versions of a program--to make sure it remains free software for all its users.

When you convey a copy of a covered work, you must also convey, for each covered work that you convey, the corresponding source code under the terms of this License, on one of the following ways:
(a) Convey the object code in, or embodied in, a physical product; or
(b) Convey the object code in a material medium (offering at least three days and at a location customary for such offers); or
(c) Convey the object code via a network server, free of charge; or
(d) Convey the object code in, or embodied in, a physical product, accompanied by a written offer valid for at least three years to give any third party access to the corresponding source code.

完整条款见 https://www.gnu.org/licenses/gpl-3.0.txt
""".trimIndent()

private val LIBS = listOf(
    Lib("Miuix", "0.9.4", "compose-miuix-ui", "Apache License 2.0",
        "https://github.com/compose-miuix-ui/miuix", APACHE2),
    Lib("KernelSU", "—", "tiann", "GPL-3.0",
        "https://github.com/tiann/KernelSU", GPL3),
    Lib("LSPosed", "—", "LSPosed", "GPL-3.0",
        "https://github.com/LSPosed/LSPosed", GPL3),
    Lib("libxposed-api", "内置", "LSPosed", "GPL-3.0",
        "https://github.com/LSPosed/LSPosed", GPL3),
    Lib("AndroidX / Jetpack Compose", "—", "The Android Open Source Project", "Apache License 2.0",
        "https://developer.android.com/jetpack", APACHE2)
)

/** 开放源代码许可页（AboutLibraries 样式：每库一卡，点开弹窗看许可全文 + 访问主页） */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf<Lib?>(null) }

    Column(Modifier.fillMaxSize()) {
        // 页头：返回 + 大标题（与其他页顶栏统一）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "开放源代码许可",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            LIBS.forEach { lib ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = lib }
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                lib.name,
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                modifier = Modifier.weight(1f)
                            )
                            if (lib.version.isNotEmpty() && lib.version != "—") {
                                Text(
                                    lib.version,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            lib.author,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            lib.license,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.9f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // 许可弹窗（覆盖层方案）：许可全文可滚动 + 左白"访问主页"/右蓝"关闭"
    selected?.let { lib ->
        HyperDialog(
            show = true,
            title = lib.name,
            summary = "许可证：${lib.license}",
            
            onDismiss = { selected = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    lib.licenseText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url))) }
                        selected = null
                    },
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("访问主页")
                }
                Spacer(Modifier.width(20.dp))
                Button(
                    onClick = { selected = null },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("关闭")
                }
            }
        }
    }
}
