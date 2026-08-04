package com.creationreadingassistant.feature.reader.pager

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 零位图翻页容器。frame 只是页面对象引用；slide/cover 只改两个图层的 translationX。
 *
 * [turnRequest]：-1 上一页、+1 下一页、0 无请求。点按和外部控制都走这条，
 * 水平拖动则由容器自己跟手。提交后先等新页重组一帧再归零位移，避免旧页闪回。
 *
 * [effect] 额外支持 "reveal"（自动翻页专用）：当前页静止在下层，下一页从顶部
 * 按 [revealProgress] 逐步揭下（clip 顶部区域），交界处画一条 [revealDividerColor]
 * 的细线。拖动时当前页照常跟手（dx 平移），向右回拖时隐藏下一层。
 */
@Composable
fun <Frame : Any> PageTurner(
    currentFrame: Frame,
    previousFrame: Frame?,
    nextFrame: Frame?,
    effect: String,
    turnRequest: Int,
    onTurnRequestConsumed: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    revealProgress: Float = 0f,
    revealDividerColor: Color? = null,
    content: @Composable BoxScope.(frame: Frame, isCurrent: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxMaxHeight = maxHeight
        val normalizedEffect = if (effect == "curl") "cover" else effect
        val dx = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        var dragTotal by remember { mutableFloatStateOf(0f) }
        var turning by remember { mutableStateOf(false) }

        suspend fun settle(direction: Int, animate: Boolean) {
            if (turning || direction == 0) return
            val neighbor = if (direction < 0) previousFrame else nextFrame
            val canTurn = neighbor != null
            if (!canTurn) {
                dx.animateTo(0f, tween(120))
                return
            }
            turning = true
            try {
                if (animate && normalizedEffect in setOf("slide", "cover")) {
                    dx.animateTo(if (direction < 0) width else -width, tween(220))
                }
                if (direction < 0) onPrevious() else onNext()
                // 两段式提交：让新 currentFrame 先进入 Composition，再撤掉旧位移。
                withFrameNanos { }
                dx.snapTo(0f)
            } finally {
                turning = false
            }
        }

        LaunchedEffect(turnRequest) {
            if (turnRequest == 0) return@LaunchedEffect
            val direction = turnRequest
            onTurnRequestConsumed()
            // 使用 remember scope，让 request 被消费或 currentFrame 换新时不会取消
            // 已经开始的 settle；否则恰好会卡在提交后的旧位移上。
            scope.launch { settle(direction, animate = true) }
        }

        val dragModifier = Modifier.pointerInput(
            currentFrame,
            previousFrame,
            nextFrame,
            normalizedEffect,
            width,
        ) {
            detectHorizontalDragGestures(
                onDragStart = {
                    dragTotal = 0f
                },
                onDragCancel = {
                    scope.launch { dx.animateTo(0f, tween(120)) }
                },
                onDragEnd = {
                    val direction = when {
                        dragTotal > width * 0.18f -> -1
                        dragTotal < -width * 0.18f -> 1
                        else -> 0
                    }
                    scope.launch {
                        if (direction == 0) dx.animateTo(0f, tween(140))
                        else settle(direction, animate = true)
                    }
                },
            ) { _, amount ->
                if (turning) return@detectHorizontalDragGestures
                dragTotal += amount
                if (normalizedEffect in setOf("slide", "cover")) {
                    val target = (dx.value + amount).coerceIn(
                        if (nextFrame != null) -width else 0f,
                        if (previousFrame != null) width else 0f,
                    )
                    scope.launch { dx.snapTo(target) }
                }
            }
        }

        if (normalizedEffect == "fade") {
            Crossfade(
                targetState = currentFrame,
                animationSpec = tween(180),
                label = "pageFade",
                modifier = Modifier.fillMaxSize().then(dragModifier),
            ) { frame ->
                Box(Modifier.fillMaxSize()) { content(frame, frame == currentFrame) }
            }
        } else if (normalizedEffect == "slide" || normalizedEffect == "cover") {
            Box(Modifier.fillMaxSize().then(dragModifier)) {
                val offset = dx.value
                if (offset < 0f && nextFrame != null) {
                    // 下一页：slide 并排进入；cover 在当前页下方静止，由当前页向左揭开。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = if (normalizedEffect == "slide") width + offset else 0f
                            },
                    ) { content(nextFrame, false) }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = when {
                                normalizedEffect == "cover" && offset > 0f -> 0f
                                else -> offset
                            }
                        },
                ) { content(currentFrame, true) }
                if (offset > 0f && previousFrame != null) {
                    // 上一页在 cover 模式中从左侧盖回；slide 则与当前页并排移动。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = -width + offset },
                    ) { content(previousFrame, false) }
                }
            }
        } else if (normalizedEffect == "reveal") {
            // 自动翻页专用：下一页从顶部按 revealProgress 揭下，分界线贴在揭起底边。
            // 拖动时当前页跟手平移；向右回拖（上一页方向）隐藏下一层，避免混叠。
            Box(Modifier.fillMaxSize().then(dragModifier)) {
                val offset = dx.value
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = offset },
                ) { content(currentFrame, true) }
                if (offset <= 0f && nextFrame != null && revealProgress > 0f) {
                    val revealHeight = boxMaxHeight * revealProgress.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(revealHeight)
                            .align(Alignment.TopCenter),
                    ) { content(nextFrame, false) }
                    revealDividerColor?.let { color ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .align(Alignment.TopCenter)
                                .offset(y = revealHeight - 1.dp)
                                .background(color),
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().then(dragModifier)) { content(currentFrame, true) }
        }
    }
}
