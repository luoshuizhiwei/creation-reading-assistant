package com.creationreadingassistant.ui.screen.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.MotionTokens
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import com.creationreadingassistant.data.settings.ReaderSettings

/** The fixed value used when an edge gesture leaves follow-system mode. */
internal fun brightnessGestureInitialValue(
    readerBrightness: Int,
    lastFixedBrightness: Int,
): Int = if (readerBrightness < 0) {
    lastFixedBrightness.coerceIn(0, 100)
} else {
    readerBrightness.coerceIn(0, 100)
}

/** Avoid writing DataStore again when a gesture leaves a fixed value unchanged. */
internal fun shouldCommitBrightnessGesture(startBrightness: Int, finalBrightness: Int): Boolean =
    startBrightness < 0 || startBrightness.coerceIn(0, 100) != finalBrightness.coerceIn(0, 100)

/** A manual gesture always keeps the active and remembered fixed values together. */
internal fun ReaderSettings.withCommittedGestureBrightness(brightness: Int): ReaderSettings {
    val fixedBrightness = brightness.coerceIn(0, 100)
    return copy(brightness = fixedBrightness, lastFixedBrightness = fixedBrightness)
}

/**
 * 阅读器左/右边缘上下滑调亮度的 Modifier（起点读书同款交互）。
 *
 * 判定规则：
 * - 按下位置 x < 边缘宽度（dp，默认 18dp）→ 判定左边缘，进入亮度调节；
 * - 按下位置 x > 总宽度 - 边缘宽度 → 判定右边缘，进入亮度调节（默认右缘也启用，兼容单手左/右握）；
 * - 其余位置 → 本 Modifier 完全不处理、不 consume 事件，向下透传给翻页分区点击。
 *
 * 亮度变化规则：
 * - 屏幕全高的一次纵向滑动 = 亮度变化 ±100%（换算：每 1% 高度 = 1% 亮度，线性）；
 * - 手指下滑（y 增加）→ 亮度减小，上滑 → 增大；
 * - 如果当前 readerBrightness=-1（跟随系统），第一次触发时先切到 `lastFixedBrightness`
 *   作为初始值（自动退出"跟随系统"），之后再在这个值上增减。
 *
 * 回调契约：
 * - [onBrightnessChange]: 拖拽过程中每次变化都调用（建议写 ReaderSettings.brightness，
 *   DataStore 自己防抖节流即可）；
 * - [onGestureActiveChange]: 手势开始(true) / 结束(false) 各一次，外层用它控制 HUD 显隐。
 */
@Composable
internal fun Modifier.readerBrightnessEdgeGesture(
    readerBrightness: Int,
    lastFixedBrightness: Int,
    edgeWidthDp: Float = 18f,
    rightEdgeEnabled: Boolean = true,
    onBrightnessChange: (Int) -> Unit,
    onGestureActiveChange: (Boolean) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val currentReaderBrightness by rememberUpdatedState(readerBrightness)
    val currentLastFixed by rememberUpdatedState(lastFixedBrightness)
    val currentOnChange by rememberUpdatedState(onBrightnessChange)
    val currentOnActive by rememberUpdatedState(onGestureActiveChange)

    return pointerInput(edgeWidthDp, rightEdgeEnabled) {
        val edgeWidthPx = with(density) { Dp(edgeWidthDp).toPx() }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val x = down.position.x
            val inLeftEdge = x <= edgeWidthPx
            val inRightEdge = rightEdgeEnabled && x >= w - edgeWidthPx
            if (!inLeftEdge && !inRightEdge) return@awaitEachGesture

            // 判定命中亮度手势 → 记录初始亮度，进入纵向拖拽监听。
            val startingBrightness = currentReaderBrightness
            val initial = brightnessGestureInitialValue(startingBrightness, currentLastFixed)
            // 小震动：告诉用户「已经进入亮度调节模式」。
            scope.launch { haptic(HapticFeedbackType.TextHandleMove) }
            currentOnActive(true)
            var running = initial.toFloat()
            var lastReported: Int? = null
            @Suppress("UNUSED_VARIABLE")
            val velocityTracker = VelocityTracker()

            try {
                while (true) {
                    val event = awaitVerticalDragOrCancellation(pointerId = down.id)
                        ?: break
                    velocityTracker.addPosition(event.uptimeMillis, event.position)
                    val dy = event.positionChange().y
                    // h 像素对应 100% 亮度：每 1 像素 → 100/h 个百分点
                    val deltaPercent = -(dy / h.coerceAtLeast(1f)) * 100f
                    running = (running + deltaPercent).coerceIn(0f, 100f)
                    val next = running.toInt()
                    if (
                        next != lastReported &&
                        next != initial &&
                        shouldCommitBrightnessGesture(startingBrightness, next)
                    ) {
                        currentOnChange(next)
                        lastReported = next
                    }
                    // 防止事件继续被下一层 scrollable / clickable 消费（滚动 TXT 分支特别容易抢）。
                    event.consume()
                }
            } finally {
                currentOnActive(false)
            }
        }
    }
}

/**
 * 亮度调节 HUD：屏幕中心显示一个圆角胶囊，含☀️图标 + 百分比文字 + 0-100% 进度条。
 *
 * 显隐时序（起点同款）：
 * - [visible] 变 true → 立刻淡入（MotionTokens.Fast）到 1.0 显示；
 * - [visible] 变 false → 停留 1200ms 再淡出（Fast）。
 *   这样用户松手后还能看清当前亮度值，不会马上消失。
 *
 * [brightness] 只当 ≥0 时用百分比显示；<0 时显示「跟随系统」四个字。
 */
@Composable
internal fun ReaderBrightnessHUD(
    visible: Boolean,
    brightness: Int,
    modifier: Modifier = Modifier,
) {
    val component = LocalComponentSpec.current
    val alphaAnim = remember { Animatable(0f) }
    // 记录 HUD"消失延迟 + 淡出"的任务是否在跑：visible 从 true→false 启动，
    // 期间若 visible 又变回 true 立刻取消（回到常显状态）。
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val currentPercent = remember(brightness) {
        if (brightness < 0) -1 else brightness.coerceIn(0, 100)
    }
    val displayText = remember(currentPercent) {
        if (currentPercent < 0) "跟随系统" else "$currentPercent%"
    }
    // 进度条 0..1：跟随系统时退化成 0.65（中间位置，给个视觉锚点）。
    val progressF = remember(currentPercent) {
        if (currentPercent < 0) 0.65f else currentPercent / 100f
    }

    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) {
        if (visible) {
            hideJob?.cancel()
            hideJob = null
            alphaAnim.animateTo(1f, tween(MotionTokens.Fast))
        } else {
            val job = scope.launch {
                delay(1200)
                alphaAnim.animateTo(0f, tween(MotionTokens.Fast))
            }
            hideJob = job
        }
    }

    if (alphaAnim.value <= 0.001f && !visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alphaAnim.value),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = component.pillShape,
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.BrightnessLow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = displayText,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
            Spacer(Modifier.width(12.dp))
            LinearProgressIndicator(
                progress = { progressF },
                modifier = Modifier.width(120.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.18f),
                drawStopIndicator = {},
            )
        }
    }
}
