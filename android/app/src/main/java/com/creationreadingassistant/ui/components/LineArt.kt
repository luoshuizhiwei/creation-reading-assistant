package com.creationreadingassistant.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 线描插画（A 档空状态美化）。
 *
 * 仅用 `onSurface` 低透明度描边绘制书签 / 书本轮廓，呼应「墨笺」品牌的中式书卷气质，
 * **不引入任何新颜色 / 新字号**（遵守 2026-07-28 冻结设计）。深色态自动跟随 onSurface，
 * 低透明度保证不与背景冲突。
 */

private fun lineArtStroke(widthPx: Float): DrawStroke =
    DrawStroke(width = widthPx.coerceAtLeast(2f))

/** 书签 / 旗标轮廓（底部 V 形缺口）。 */
@Composable
fun LineArtBookmark(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 64.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
) {
    Box(
        modifier
            .size(sizeDp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val stroke = lineArtStroke(size.minDimension * 0.055f)
                val left = w * 0.32f
                val right = w * 0.68f
                val top = h * 0.14f
                val bottom = h * 0.84f
                val cx = w / 2f
                val notch = h * 0.12f
                val path = Path().apply {
                    moveTo(left, top)
                    lineTo(right, top)
                    lineTo(right, bottom)
                    lineTo(cx, bottom - notch)
                    lineTo(left, bottom)
                    close()
                }
                drawPath(path, tint, style = stroke)
            },
    )
}

/** 摊开的书本轮廓（两页 + 书脊 + 页内文字线）。 */
@Composable
fun LineArtBook(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 72.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
) {
    Box(
        modifier
            .size(sizeDp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val stroke = lineArtStroke(size.minDimension * 0.05f)
                val cx = w / 2f
                val top = h * 0.18f
                val bottom = h * 0.82f
                val left = w * 0.16f
                val right = w * 0.84f
                val dip = h * 0.04f

                // 左页
                val leftPage = Path().apply {
                    moveTo(left, top)
                    lineTo(cx, top + dip)
                    lineTo(cx, bottom - dip)
                    lineTo(left, bottom)
                    close()
                }
                // 右页
                val rightPage = Path().apply {
                    moveTo(right, top)
                    lineTo(cx, top + dip)
                    lineTo(cx, bottom - dip)
                    lineTo(right, bottom)
                    close()
                }
                drawPath(leftPage, tint, style = stroke)
                drawPath(rightPage, tint, style = stroke)

                // 书脊
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(cx, top),
                    end = androidx.compose.ui.geometry.Offset(cx, bottom),
                    strokeWidth = stroke.width,
                )

                // 页内文字线（左右各两条）
                val lineInset = w * 0.1f
                val ly1 = top + (bottom - top) * 0.4f
                val ly2 = top + (bottom - top) * 0.62f
                drawLine(tint, Offset(left + lineInset, ly1), Offset(cx - lineInset, ly1 + dip * 0.3f), stroke.width)
                drawLine(tint, Offset(left + lineInset, ly2), Offset(cx - lineInset, ly2 + dip * 0.3f), stroke.width)
                drawLine(tint, Offset(cx + lineInset, ly1 + dip * 0.3f), Offset(right - lineInset, ly1), stroke.width)
                drawLine(tint, Offset(cx + lineInset, ly2 + dip * 0.3f), Offset(right - lineInset, ly2), stroke.width)
            },
    )
}
