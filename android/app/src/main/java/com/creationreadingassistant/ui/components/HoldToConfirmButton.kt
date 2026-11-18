package com.creationreadingassistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive

/**
 * 长按确认按钮（Hold to Confirm）：按住时外圈进度环顺时针走满（默认 1.5s），
 * 中途松手进度倒退回零，不触发确认；走满后按钮左右轻晃、变对勾，再执行
 * [onConfirmed]。适合删除等危险操作——长按本身就是确认，不再弹窗。
 *
 * 性能：进度环为一次性按住期间的 Canvas 重绘（仅按住时驱动帧循环），无循环动画；
 * 走满后的轻晃为约 300ms 的有限旋转动画，之后静止。
 */
@Composable
fun HoldToConfirmButton(
    contentDescription: String,
    confirmedLabel: String? = null,
    holdDurationMs: Long = 1500,
    diameter: Dp = 72.dp,
    enabled: Boolean = true,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    var holding by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val wobble = remember { Animatable(0f) }
    var confirmed by remember { mutableStateOf(false) }

    LaunchedEffect(holding, enabled, confirmed) {
        if (!holding || confirmed || !enabled) return@LaunchedEffect
        val from = progress.value
        val began = withFrameNanos { it }
        while (isActive && holding) {
            val now = withFrameNanos { it }
            val p = ((now - began) / 1e6 + from * holdDurationMs) / holdDurationMs
            if (p >= 1f) {
                progress.snapTo(1f)
                confirmed = true
                holding = false
                break
            }
            progress.snapTo(p.toFloat())
        }
    }
    LaunchedEffect(confirmed) {
        if (!confirmed) return@LaunchedEffect
        listOf(-7f, 7f, -4f, 0f).forEach { angle ->
            wobble.animateTo(angle, tween(90))
        }
        wobble.snapTo(0f)
        onConfirmed()
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .semantics { this.contentDescription = contentDescription }
                .pointerInput(enabled, confirmed) {
                    if (!enabled || confirmed) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            holding = true
                            try {
                                awaitRelease()
                            } finally {
                                if (!confirmed) {
                                    holding = false
                                    progress.animateTo(0f, tween(200))
                                }
                            }
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .size(diameter)
                    .graphicsLayer { rotationZ = wobble.value },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(diameter)) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(
                        size.width - inset * 2,
                        size.height - inset * 2,
                    )
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (progress.value > 0f) {
                        rotate(wobble.value) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress.value,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                if (confirmed) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = ringColor,
                        modifier = Modifier.size(diameter / 3),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = ringColor,
                        modifier = Modifier.size(diameter / 3),
                    )
                }
            }
            if (confirmedLabel != null) {
                Text(
                    if (confirmed) confirmedLabel else contentDescription,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (confirmed) ringColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
