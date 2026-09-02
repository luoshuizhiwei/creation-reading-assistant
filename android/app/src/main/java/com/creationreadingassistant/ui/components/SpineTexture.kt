package com.creationreadingassistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * P1-第五批-3b：书脊纹理。
 *
 * 为占位封面的左侧 3~4dp 窄条画微纹理，形成「实体书书脊」观感，
 * 与封面低饱和渐变 + 斜向高光三层叠加后，视觉语言统一。
 *
 * 纹理按书名/ID hash 稳定分配（5 种：布纹横纹 / 皮纹颗粒 / 毛边纸纤维 /
 * 烫金细竖线 / 麻面密点），确保同一本书在书架、首页继续阅读、详情等处
 * 始终是同一条纹理，跨卡片保持视觉身份一致。
 *
 * 所有纹理均为程序化几何：不依赖位图资源，也不会在 RecyclerView 中膨胀 drawable，
 * 随封面容器尺寸自适应。
 */
internal enum class SpineTextureKind {
    /** 布纹横纹：水平等距细线，深浅交替 */
    CLOTH,
    /** 皮纹颗粒：稀疏伪随机椭圆 + 中缝压痕线 */
    LEATHER,
    /** 毛边纸纤维：不规则斜线 + 散点，米白封面尤显自然 */
    RICE_PAPER,
    /** 烫金细竖线：多道金色垂直光丝（仅在亮色封面上明显） */
    GILT,
    /** 麻面密点：整齐网格状小点，密度 60% 左右 */
    LINEN,
}

/** 按任意 ID（hashCode）稳定分配一种书脊纹理。 */
internal fun spineKindOf(id: Any): SpineTextureKind {
    val h = id.hashCode()
    val spread = ((h xor (h ushr 16)) and 0x7fffffff)
    return SpineTextureKind.entries[spread % SpineTextureKind.entries.size]
}

/**
 * 在左侧 4dp 宽度内绘制书脊纹理。
 *
 * @param kind   纹理类型
 * @param baseInk 纹理主色（基于封面底色自动取反，确保可读）
 */
@Composable
internal fun SpineTexture(
    kind: SpineTextureKind,
    baseInk: Color,
    modifier: Modifier = Modifier,
    width: Dp = 4.dp,
) {
    // 4 套颜色层级：主纹 / 次纹 / 亮纹 / 压痕
    val ink = baseInk
    val soft = baseInk.copy(alpha = baseInk.alpha.coerceAtMost(0.55f) * 0.55f)
    val sheen = Color.White.copy(alpha = (baseInk.alpha.coerceAtMost(0.42f)) * 0.30f)
    val seam = baseInk.copy(alpha = (baseInk.alpha.coerceAtMost(0.55f)) * 0.90f)

    Canvas(modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        when (kind) {
            SpineTextureKind.CLOTH -> drawCloth(w, h, ink, soft, sheen)
            SpineTextureKind.LEATHER -> drawLeather(w, h, ink, soft, seam)
            SpineTextureKind.RICE_PAPER -> drawRicePaper(w, h, ink, soft)
            SpineTextureKind.GILT -> drawGilt(w, h, ink, sheen)
            SpineTextureKind.LINEN -> drawLinen(w, h, ink, soft)
        }
    }
}

private fun DrawScope.drawCloth(w: Float, h: Float, ink: Color, soft: Color, sheen: Color) {
    // 布纹：每 3.2px 一道横纹，主纹 + 次纹交替
    var y = 0f
    var step = 0
    while (y < h) {
        val color = if (step and 1 == 0) ink else soft
        drawLine(color = color, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1.1f)
        // 右侧 1px 光泽带（书脊弯曲处受光）
        drawLine(
            color = sheen,
            start = Offset(w - 0.6f, y),
            end = Offset(w, y),
            strokeWidth = 0.8f,
        )
        y += 2.8f
        step += 1
    }
}

private fun DrawScope.drawLeather(w: Float, h: Float, ink: Color, soft: Color, seam: Color) {
    // 1. 中缝压痕线：中间偏左 0.6dp 宽暗带
    val midX = w * 0.45f
    drawLine(
        color = seam,
        start = Offset(midX, 0f),
        end = Offset(midX, h),
        strokeWidth = (w * 0.24f).coerceAtLeast(0.9f),
    )
    // 2. 稀疏椭圆颗粒（伪随机）
    var t = 7f
    while (t < h) {
        val seed = abs(sin(t * 0.21f) * 997f)
        val cx = (seed % w * 0.9f) + w * 0.05f
        val cy = t
        val rx = 0.9f + (seed % 3) * 0.35f
        val ry = 1.2f + (seed % 4) * 0.30f
        val oval = Path().apply {
            moveTo(cx - rx, cy)
            cubicTo(cx - rx, cy - ry, cx + rx, cy - ry, cx + rx, cy)
            cubicTo(cx + rx, cy + ry, cx - rx, cy + ry, cx - rx, cy)
            close()
        }
        drawPath(oval, if ((seed.toInt() and 1) == 0) soft else ink)
        t += 6.2f
    }
}

private fun DrawScope.drawRicePaper(w: Float, h: Float, ink: Color, soft: Color) {
    // 毛边纸：沿垂直方向每隔约 4.5px 一根纤维，角度 ±15°，长度 6-14px
    var t = 0f
    while (t < h) {
        val seed = abs(sin(t * 0.137f + w) * 1331f)
        val angle = (seed % 30f) - 15f // -15~15°
        val len = 6f + (seed % 8f)
        val cx = w * 0.25f + (seed % (w * 0.6f))
        val cy = t
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val dx = kotlin.math.cos(rad) * len * 0.5f
        val dy = kotlin.math.sin(rad) * len * 0.5f
        drawLine(
            color = if ((seed.toInt() and 3) == 0) ink else soft,
            start = Offset(cx - dx, cy - dy),
            end = Offset(cx + dx, cy + dy),
            strokeWidth = 0.8f + (seed % 2) * 0.35f,
        )
        // 散点
        drawCircle(
            color = soft,
            radius = 0.55f,
            center = Offset(cx, (cy + len).coerceAtMost(h - 1f)),
        )
        t += 4.5f
    }
}

private fun DrawScope.drawGilt(w: Float, h: Float, ink: Color, sheen: Color) {
    // 烫金：2-3 道均匀分布的超细金色竖线 + 1 道压痕
    val colors = listOf(
        ink,
        sheen,
        ink.copy(alpha = (ink.alpha.coerceAtMost(0.8f)) * 0.78f),
    )
    val count = 3
    for (i in 0 until count) {
        val x = w * ((i + 1).toFloat() / (count + 1))
        drawLine(
            color = colors[i % colors.size],
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = if (i == 1) 1.1f else 0.85f,
        )
    }
    // 右侧 0.5px 高光边
    drawLine(
        color = sheen,
        start = Offset(w - 0.4f, 0f),
        end = Offset(w, h),
        strokeWidth = 0.5f,
    )
}

private fun DrawScope.drawLinen(w: Float, h: Float, ink: Color, soft: Color) {
    // 麻面密点：3x3 网格，偶数格画主色，奇数格画次色
    val stepX = 2.4f.coerceAtLeast(w * 0.28f)
    val stepY = 2.2f
    var gy = 0.5f
    var row = 0
    while (gy < h) {
        var gx = 0.6f
        var col = 0
        while (gx < w) {
            val c = if ((row + col) and 1 == 0) ink else soft
            val r = if ((col xor row) and 3 == 0) 0.5f else 0.38f
            drawCircle(color = c, radius = r, center = Offset(gx, gy))
            gx += stepX
            col += 1
        }
        gy += stepY
        row += 1
    }
}
