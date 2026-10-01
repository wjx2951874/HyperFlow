package com.hyperflowplus.ui

// V0.4.44：图标改用官方 path DSL 手写节点（moveTo/curveTo/lineTo 字面量）。
// 替换 V0.4.43 的手写字符串解析器——该解析器在 material 路径格式
// （命令字母紧贴数字、S/s 平滑命令）下抛 NumberFormatException，
// 导致启动首帧渲染即白屏闪退。
// 3 枚 Rounded 圆环图标（material-icons 标准 path 数据，Apache-2.0），
// 样式与 KernelSU/miuix 高频状态符同源（空心圆环）。S/s 反射控制点已折算为绝对 curveTo。

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object RoundedIcons {

    /** 空心圆环 + 对勾（CheckCircleOutline） */
    val CheckCircleOutline: ImageVector by lazy { buildCheck() }

    /** 空心圆环 + 叹号（ErrorOutline） */
    val ErrorOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "Rounded.ErrorOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 叹号上段
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(11f, 15f); horizontalLineTo(13f); verticalLineTo(17f); horizontalLineTo(11f); close()
                moveTo(11f, 7f); horizontalLineTo(13f); verticalLineTo(13f); horizontalLineTo(11f); close()
            }
            // 外圆环
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(6.48f, 22f, 12f, 22f, 12f, 22f)
                curveTo(22f, 17.52f, 22f, 12f, 22f, 12f)
                curveTo(22f, 6.48f, 12f, 2f, 12f, 2f)
                close()
            }
            // 内圆环
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(12f, 20f)
                curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
                curveTo(7.59f, 4f, 12f, 4f, 12f, 4f)
                curveTo(20f, 7.59f, 20f, 12f, 20f, 12f)
                curveTo(16.41f, 20f, 12f, 20f, 12f, 20f)
                close()
            }
        }.build()
    }

    /** 空心圆环 + 叉（Cancel） */
    val Cancel: ImageVector by lazy {
        ImageVector.Builder(
            name = "Rounded.Cancel",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(12f, 2f)
                curveTo(6.47f, 2f, 2f, 6.47f, 2f, 12f)
                curveTo(6.47f, 22f, 12f, 22f, 12f, 22f)
                curveTo(22f, 17.53f, 22f, 12f, 22f, 12f)
                curveTo(22f, 6.47f, 12f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
                curveTo(7.59f, 4f, 12f, 4f, 12f, 4f)
                curveTo(20f, 7.59f, 20f, 12f, 20f, 12f)
                curveTo(16.41f, 20f, 12f, 20f, 12f, 20f)
                close()
                moveTo(15.59f, 7f)
                lineTo(12f, 10.59f)
                lineTo(8.41f, 7f)
                lineTo(7f, 8.41f)
                lineTo(10.59f, 12f)
                lineTo(7f, 15.59f)
                lineTo(8.41f, 17f)
                lineTo(12f, 13.41f)
                lineTo(15.59f, 17f)
                lineTo(17f, 15.59f)
                lineTo(13.41f, 12f)
                lineTo(17f, 8.41f)
                close()
            }
        }.build()
    }

    private fun buildCheck(): ImageVector =
        ImageVector.Builder(
            name = "Rounded.CheckCircleOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 外圆环（offset=0.48: CheckCircleOutline 外圆曲率 6.48/17.52/22；Cancel 用 6.47/17.53）
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(6.48f, 22f, 12f, 22f, 12f, 22f)
                curveTo(22f, 17.52f, 22f, 12f, 22f, 12f)
                curveTo(22f, 6.48f, 12f, 2f, 12f, 2f)
                close()
            }
            // 内圆环
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(12f, 20f)
                curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
                curveTo(7.59f, 4f, 12f, 4f, 12f, 4f)
                curveTo(20f, 7.59f, 20f, 12f, 20f, 12f)
                curveTo(16.41f, 20f, 12f, 20f, 12f, 20f)
                close()
            }
            // 对勾
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(16.59f, 7.58f)
                lineTo(10f, 14.17f)
                lineTo(7.41f, 11.59f)
                lineTo(6f, 13f)
                lineTo(10f, 17f)
                lineTo(18f, 9f)
                lineTo(16.59f, 7.58f)
                close()
            }
        }.build()
}
