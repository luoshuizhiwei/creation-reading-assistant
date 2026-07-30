package com.creationreadingassistant.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 连续圆角（squircle）形状。
 *
 * 用超椭圆（superellipse）公式拟合 iOS 风格的连续圆角，
 * 避免引入 `androidx.graphics:graphics-shapes` 外部依赖，保证在 minSdk 24 上零兼容风险。
 *
 * @param radius 圆角半径
 * @param smoothing 平滑系数 0..1，越大角越「方」（连续曲率越强）。iOS 经验值约 0.62。
 */
fun SquircleShape(radius: Dp, smoothing: Float = 0.62f): Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rPx = radius.value * density.density
        val path = Path()
        drawSquirclePath(path, size, rPx, smoothing)
        return Outline.Generic(path)
    }
}

/** 带符号幂：sign(v) * |v|^p，用于超椭圆参数方程。 */
private fun signPow(v: Float, p: Float): Float = if (v >= 0f) v.pow(p) else -(-v).pow(p)

/** 生成一段超椭圆圆角弧，以 lineTo 连接采样点（足够平滑）。 */
private fun squircleArc(
    path: Path,
    cx: Float,
    cy: Float,
    r: Float,
    n: Float,
    startAngle: Float,
    endAngle: Float,
    segments: Int,
) {
    for (i in 0..segments) {
        val a = startAngle + (endAngle - startAngle) * (i.toFloat() / segments)
        val x = cx + r * signPow(cos(a), 2f / n)
        val y = cy + r * signPow(sin(a), 2f / n)
        path.lineTo(x, y)
    }
}

/** 在 [path] 上绘制一个超椭圆圆角矩形轮廓。 */
private fun drawSquirclePath(path: Path, size: Size, r: Float, smoothing: Float) {
    if (r <= 0f || size.width <= 0f || size.height <= 0f) {
        path.addRect(Rect(0f, 0f, size.width, size.height))
        return
    }
    val radius = minOf(r, size.width / 2f, size.height / 2f)
    val w = size.width
    val h = size.height
    // 超椭圆指数：smoothing 越大越方。映射至 ~[2,6]
    val n = 2f + smoothing * 4f
    val segments = 18

    path.moveTo(radius, 0f)
    path.lineTo(w - radius, 0f)
    // 右上角：(w-r, r) 从 -90° 到 0°
    squircleArc(path, w - radius, radius, radius, n, -Math.PI.toFloat() / 2f, 0f, segments)
    path.lineTo(w, h - radius)
    // 右下角：(w-r, h-r) 从 0° 到 90°
    squircleArc(path, w - radius, h - radius, radius, n, 0f, Math.PI.toFloat() / 2f, segments)
    path.lineTo(radius, h)
    // 左下角：(r, h-r) 从 90° 到 180°
    squircleArc(path, radius, h - radius, radius, n, Math.PI.toFloat() / 2f, Math.PI.toFloat(), segments)
    path.lineTo(0f, radius)
    // 左上角：(r, r) 从 180° 到 270°
    squircleArc(path, radius, radius, radius, n, Math.PI.toFloat(), Math.PI.toFloat() * 1.5f, segments)
    path.close()
}
