package com.hyperflowplus.ui

// V0.4.43：自建 3 个 Rounded 圆环图标（material-icons 标准 path 数据，Apache-2.0）。
// 替代 material-icons-extended 全量库（避免 APK 增大 20+MB）：仅保留用到的
// 圆环勾 / 圆环叹号 / 圆环叉，样式与 KernelSU/miuix 高频状态符同源（空心圆环）。

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object RoundedIcons {

    /** 空心圆环 + 对勾（CheckCircleOutline） */
    val CheckCircleOutline: ImageVector by lazy {
        fromPath(
            "Rounded.CheckCircleOutline",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z" +
                "M16.59,7.58L10,14.17l-2.59,-2.58L6,13l4,4 8,-8 -1.41,-1.42z"
        )
    }

    /** 空心圆环 + 叹号（ErrorOutline） */
    val ErrorOutline: ImageVector by lazy {
        fromPath(
            "Rounded.ErrorOutline",
            "M11,15h2v2h-2zM11,7h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z"
        )
    }

    /** 空心圆环 + 叉（Cancel） */
    val Cancel: ImageVector by lazy {
        fromPath(
            "Rounded.Cancel",
            "M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2z" +
                "M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z" +
                "M15.59,7L12,10.59 8.41,7 7,8.41 10.59,12 7,15.59 8.41,17 12,13.41 15.59,17 17,15.59 13.41,12 17,8.41z"
        )
    }

    private fun fromPath(name: String, data: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                parseMaterialPath(this, data)
            }
        }.build()

    /** 解析 material-icons path data（命令字母 + 空格分隔数字组），仅需 M/L/C/Q/Z */
    private fun parseMaterialPath(builder: androidx.compose.ui.graphics.vector.PathBuilder, data: String) {
        val tokens = data.trim().split(" ").filter { it.isNotEmpty() }
        var i = 0
        var cmd = ' '
        val nums = ArrayList<Float>()
        while (i < tokens.size) {
            val t = tokens[i]
            val c = t[0]
            if (c.isLetter()) {
                cmd = c
                i++
            }
            nums.clear()
            while (i < tokens.size) {
                val nt = tokens[i]
                if (nt[0].isLetter()) break
                nums.add(nt.toFloat())
                i++
            }
            when (cmd) {
                'M' -> { builder.moveTo(nums[0], nums[1]); cmd = 'L' }
                'm' -> { builder.moveTo(nums[0], nums[1]); cmd = 'l' }
                'L' -> builder.lineTo(nums[0], nums[1])
                'l' -> builder.lineTo(nums[0], nums[1])
                'C' -> builder.curveTo(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5])
                'c' -> builder.curveTo(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5])
                'Q' -> builder.quadTo(nums[0], nums[1], nums[2], nums[3])
                'q' -> builder.quadTo(nums[0], nums[1], nums[2], nums[3])
                'Z', 'z' -> builder.close()
            }
        }
    }
}
