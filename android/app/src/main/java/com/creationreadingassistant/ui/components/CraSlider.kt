package com.creationreadingassistant.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 统一进度条 / 滑杆（设置子页、阅读菜单进度条、TTS 面板、进度跳转面板共用）。
 *
 * ## 为什么要自绘外观
 *
 * 原生 M3 `Slider` 一旦带 `steps`，轨道会按步进切成「缺口 + 短段」——步数多时渲染成
 * 一排碎点（真机设置页字号 / 页边距滑杆即此观感，用户反馈「不够美观」）。这里用
 * M3 1.3 的 `thumb` / `track` 槽位整体重绘：**手势、步进吸附与无障碍语义仍全部由 M3
 * 承担**，视觉换成连续圆角轨道 + 末端停点指示 + 可按压放大的描边圆拇指。
 *
 * ## 视觉（对照 M3 Expressive 与主流阅读器，色板沿用本项目低饱和纸墨风）
 *
 * - 轨道：连续圆角条（[trackHeight]），已走段 = [tint]，未走段 = onSurface 10%；
 *   末端一颗停点小圆（Expressive 标志性细节）。步进**只吸附取值、不画刻度**——
 *   精确值由调用方的值标签（如「25 sp」）承载；
 * - 拇指：20dp 圆，底色 surface + 2.5dp [tint] 描边；按住时 1.15 倍弹簧放大，
 *   给到「抓住了」的实体感（reducedMotion 时关闭动效只保留描边）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CraSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    /** 轨道粗细（dp）。阅读菜单进度条与设置滑杆保持同一粗细以统一视觉语言。 */
    trackHeight: Int = 6,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "craSliderThumbScale",
    )
    val scheme = MaterialTheme.colorScheme
    val activeColor = if (enabled) tint else scheme.onSurface.copy(alpha = 0.38f)
    val inactiveColor = if (enabled) scheme.onSurface.copy(alpha = 0.10f) else scheme.onSurface.copy(alpha = 0.06f)
    // 停点比轨道未走段略实一点，但不抢拇指
    val stopDotColor = scheme.onSurface.copy(alpha = if (enabled) 0.28f else 0.14f)
    val thumbFill = scheme.surface
    val thumbBorder = if (enabled) tint else scheme.onSurface.copy(alpha = 0.38f)

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.heightIn(min = 48.dp),
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .background(color = thumbFill, shape = CircleShape)
                    .border(width = 2.5.dp, color = thumbBorder, shape = CircleShape),
            )
        },
        track = {
            val layoutDirection = LocalLayoutDirection.current
            val isRtl = layoutDirection == LayoutDirection.Rtl
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
                val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight.dp),
                ) {
                    val r = size.height / 2f
                    // 未走段打底
                    drawRoundRect(
                        color = inactiveColor,
                        cornerRadius = CornerRadius(r, r),
                    )
                    val geom = computeCraSliderTrackGeometry(
                        totalWidth = size.width,
                        height = size.height,
                        fraction = fraction,
                        isRtl = isRtl,
                        strokeInsetPx = 1.dp.toPx(),
                    )
                    // 已走段（不足一个半径时退化为圆头短条）
                    if (geom.hasActiveTrack) {
                        drawRoundRect(
                            color = activeColor,
                            topLeft = geom.activeTrackTopLeft,
                            size = geom.activeTrackSize,
                            cornerRadius = CornerRadius(r, r),
                        )
                    }
                    // 末端停点指示（Expressive 细节）：未走段末尾一颗实心小圆
                    if (geom.hasStopDot) {
                        drawCircle(
                            color = stopDotColor,
                            radius = size.height * 0.45f,
                            center = geom.stopDotCenter,
                        )
                    }
                    // 已走段外侧一圈细描边，增加「填充到位」的体积感
                    if (geom.hasOutline) {
                        drawRoundRect(
                            color = activeColor.copy(alpha = 0.22f),
                            topLeft = geom.outlineTopLeft,
                            size = geom.outlineSize,
                            cornerRadius = CornerRadius(r + 1.dp.toPx(), r + 1.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            }
        },
    )
}

/**
 * CraSlider 轨道绘制几何数据。
 */
internal data class CraSliderTrackGeometry(
    val activeTrackTopLeft: Offset,
    val activeTrackSize: Size,
    val stopDotCenter: Offset,
    val outlineTopLeft: Offset,
    val outlineSize: Size,
    val hasActiveTrack: Boolean,
    val hasStopDot: Boolean,
    val hasOutline: Boolean,
)

/**
 * 计算 CraSlider 轨道的绘制几何参数。
 *
 * ## RTL 与 LTR 镜像规范
 * - LTR 下：已走段从左侧起点 (x=0) 向右填充至 x=activeW，未走段停点位于最右侧 (x=totalWidth-offset)；
 * - RTL 下：已走段从右侧起点 (x=totalWidth) 向左填充至 x=totalWidth-activeW，未走段停点位于最左侧 (x=offset)；
 * 确保已走段延伸方向、未走段收缩方向与 M3 Slider 拇指位置及数值增长方向在两种布局下完全一致。
 */
internal fun computeCraSliderTrackGeometry(
    totalWidth: Float,
    height: Float,
    fraction: Float,
    isRtl: Boolean,
    strokeInsetPx: Float = 1f,
): CraSliderTrackGeometry {
    val r = height / 2f
    val activeW = totalWidth * fraction.coerceIn(0f, 1f)
    val hasActive = activeW > 0f
    val effectiveActiveW = if (hasActive) maxOf(activeW, height) else 0f

    val activeTopLeft = if (!isRtl) {
        Offset(0f, 0f)
    } else {
        Offset(totalWidth - effectiveActiveW, 0f)
    }
    val activeSize = Size(effectiveActiveW, height)

    val stopDotCenter = if (!isRtl) {
        Offset(totalWidth - height * 0.55f, height / 2f)
    } else {
        Offset(height * 0.55f, height / 2f)
    }
    val hasStopDot = fraction < 1f

    val hasOutline = activeW > height
    val outlineTopLeft = if (!isRtl) {
        Offset(-strokeInsetPx, -strokeInsetPx)
    } else {
        Offset(totalWidth - activeW - strokeInsetPx, -strokeInsetPx)
    }
    val outlineSize = Size(activeW + 2f * strokeInsetPx, height + 2f * strokeInsetPx)

    return CraSliderTrackGeometry(
        activeTrackTopLeft = activeTopLeft,
        activeTrackSize = activeSize,
        stopDotCenter = stopDotCenter,
        outlineTopLeft = outlineTopLeft,
        outlineSize = outlineSize,
        hasActiveTrack = hasActive,
        hasStopDot = hasStopDot,
        hasOutline = hasOutline,
    )
}
