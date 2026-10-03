package com.hyperflowplus.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperflowplus.RootExec
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 引导页类型：按首页检测项点击进入 */
enum class GuideType(val title: String) {
    ROOT("Root 权限授权"),
    LSPOSED("LSPosed 框架"),
    MODULE_SCOPE("模块与作用域"),
    MILINK("小米互联服务")
}

private val CGreen = Color(0xFF34C759)
private val CRed = Color(0xFFFF3B30)

/**
 * 引导新页面（Miuix 返回箭头 + 大标题）：按检测项给步骤说明 + 一键操作。
 * 首页检测卡点击对应项后进入本页。
 */
@Composable
fun GuideScreen(type: GuideType, onBack: () -> Unit) {
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        // 页头：Miuix 返回 + 大标题
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
                type.title,
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    when (type) {
                        GuideType.ROOT -> {
                            GuideText("HyperFlow 需要 KernelSU 授予超级用户权限才能读取流转数据。")
                            Spacer(Modifier.height(10.dp))
                            GuideStep("1", "打开 KernelSU 应用")
                            GuideStep("2", "进入「超级用户」页面")
                            GuideStep("3", "找到 HyperFlow，点击允许并勾选（默认授予）")
                            GuideStep("4", "返回本页点击「重新检测」，绿色即授权成功")
                        }
                        GuideType.LSPOSED -> {
                            GuideText("LSPosed 框架用于加载本模块（HyperFlow），需先在 KernelSU 中启用。")
                            Spacer(Modifier.height(10.dp))
                            GuideStep("1", "打开 KernelSU 应用")
                            GuideStep("2", "进入「模块」（Zygisk/内嵌 LSPosed 管理）")
                            GuideStep("3", "安装/启用 LSPosed 框架（KernelSU 内嵌版已含）")
                            GuideStep("4", "重启设备后返回本页重新检测")
                        }
                        GuideType.MODULE_SCOPE -> {
                            GuideText("本模块需要：①在 LSPosed 中启用；②勾选推荐作用域（小米互联服务 + Android 系统框架）。本 App 自身无需被勾选。")
                            Spacer(Modifier.height(10.dp))
                            GuideStep("1", "点击下方按钮，自动写入启用 + 勾选配置")
                            GuideStep("2", "重启设备让配置生效")
                            GuideStep("3", "返回本页重新检测，两项变绿即完成")
                        }
                        GuideType.MILINK -> {
                            GuideText("小米互联服务（com.milink.service）是系统组件，负责通知流转数据源。")
                            Spacer(Modifier.height(10.dp))
                            GuideStep("1", "确认系统为小米澎湃 OS（HyperOS）")
                            GuideStep("2", "在系统设置中开启「互联互通 / 小米互联」")
                            GuideStep("3", "该服务为系统预装，无法由本应用自动安装")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 一键操作按钮
            when (type) {
                GuideType.ROOT -> GuideButton("打开 KernelSU 授权", primary = true) {
                    launchKernelSu(ctx)
                }
                GuideType.LSPOSED -> GuideButton("打开 KernelSU", primary = true) {
                    launchKernelSu(ctx)
                }
                GuideType.MODULE_SCOPE -> GuideButton("一键启用模块并勾选推荐作用域", primary = true) {
                    Thread {
                        runCatching { RootExec.su(fixLspScript()) }
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(ctx, "已写入 LSPosed 配置，重启设备后生效", Toast.LENGTH_LONG).show()
                        }
                    }.start()
                }
                GuideType.MILINK -> GuideButton("我已开启互联互通", primary = false) {
                    onBack()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideText(text: String) {
    Text(
        text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f)
    )
}

@Composable
private fun GuideStep(no: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            no,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun GuideButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        Text(text, fontSize = 15.sp)
    }
}

/** 打开 KernelSU 管理器（多包名兜底） */
private fun launchKernelSu(ctx: android.content.Context) {
    val pkgs = listOf(
        "me.weishu.kernelsu", "me.weishu.kernelsu.next",
        "com.rifsxd.ksunext", "com.rifsxd.ksu"
    )
    for (pkg in pkgs) {
        val out = runCatching { RootExec.su("pm path $pkg 2>/dev/null") }.getOrNull()
        if (!out.isNullOrBlank() && out.contains("package:")) {
            runCatching {
                RootExec.su("am start -n $pkg/.ui.activity.MainActivity 2>/dev/null || monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            }
            return
        }
    }
    Toast.makeText(ctx, "未找到 KernelSU，请从官网安装", Toast.LENGTH_SHORT).show()
}

/** 一键启用模块 + 勾选推荐作用域的 su 脚本（与首页 fixLsp 一致，供引导页复用）。
 *  仅写入：模块启用（modules.list）+ 推荐作用域 scope（milink + android，不含本 App——
 *  本 App 自身进程无需被 hook，LSPosed 里勾不了属正常）。 */
fun fixLspScript(): String = """for d in /data/adb/lspd/config /data/adb/modules/lsposed/config /data/adb/modules/zygisk_lsposed/config; do
  [ -e "${'$'}d" ] || continue
  mkdir -p "${'$'}d/scope"
  if [ -f "${'$'}d/modules.list" ]; then grep -q 'com.hyperflowplus' "${'$'}d/modules.list" || echo 'com.hyperflowplus' >> "${'$'}d/modules.list"; fi
  sc2="${'$'}d/scope/com.hyperflowplus"
  if [ -f "${'$'}sc2" ]; then
    grep -qx 'com.milink.service' "${'$'}sc2" || echo 'com.milink.service' >> "${'$'}sc2"
    grep -qx 'android' "${'$'}sc2" || echo 'android' >> "${'$'}sc2"
  else
    { echo 'com.milink.service'; echo 'android'; } > "${'$'}sc2"
  fi
done
chmod 644 /data/adb/lspd/config/scope/com.hyperflowplus 2>/dev/null
chown -R 0:0 /data/adb/lspd/config 2>/dev/null
echo done"""
