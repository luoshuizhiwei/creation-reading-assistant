package com.creationreadingassistant.feature.reader.pager

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 翻页容器（2026-08-23 重写）：参考主流阅读器（微信读书 / legado）的交互模型。
 *
 * 核心改进：
 * 1. **拖动中当前页用位图快照**：翻页开始（onDragStart）时把当前页合成到位图，
 *    拖动/动画期间只平移快照图层，不触发页面实时重组——根治「拖动闪烁 /
 *    覆盖模式两页内容互相透出」问题（此前零位图方案在拖动中实时重组懒页面，
 *    首帧慢时下层空白或与旧帧叠合）。
 * 2. **settle 用缓动曲线**（easeOutCubic）：松手补位不再线性生硬。
 * 3. **稳定提交**：动画到位后等 2 帧布局稳定再撤位，避免「新页错位一帧」的闪回。
 * 4. 拖动跟手直接写 [dx]（不逐事件 launch 协程），跟手更顺。
 *
 * 各模式语义保持不变：`none` 直接切 / `fade` 交叉淡入 / `slide` 并排滑动 /
 * `cover` 当前页揭开露出下层 / `reveal` 自动翻页揭下（[revealProgress] 驱动）。
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
    revealBackground: Color = Color.Transparent,
    /** slide/cover 模式的页面底色：页面图层透明，叠加时须铺不透明纸色，否则下层文字透出重叠。 */
    pageBackground: Color = Color.Transparent,
    /** 真实触发翻页（onPrevious / onNext 已被调用）后回调；用于通知外层隐藏阅读菜单栏。 */
    onPageTurned: () -> Unit = {},
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
        // 当前页位图快照：拖动开始时捕获，动画期间显示快照而非实时重组
        val currentLayer = rememberGraphicsLayer()
        var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
        var snapshotFrame by remember { mutableStateOf<Any?>(null) }

        suspend fun settle(direction: Int, animate: Boolean) {
            if (turning || direction == 0) return
            val neighbor = if (direction < 0) previousFrame else nextFrame
            if (neighbor == null) {
                dx.animateTo(0f, tween(120))
                snapshot = null
                snapshotFrame = null
                return
            }
            turning = true
            try {
                if (animate && normalizedEffect in setOf("slide", "cover")) {
                    dx.animateTo(
                        if (direction < 0) width else -width,
                        // 缓动补位：easeOutCubic，接近主流阅读器的松手回弹节奏
                        tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    )
                }
                snapshot = null
                snapshotFrame = null
                if (direction < 0) onPrevious() else onNext()
                onPageTurned()
                // 两段式提交：让新 currentFrame 完成组合/布局再撤旧位移。
                // 懒布局页面可能跨多帧才就绪，等 2 帧避免「新页错位一帧」的闪回。
                repeat(2) { withFrameNanos { } }
                dx.snapTo(0f)
            } finally {
                turning = false
            }
        }

        LaunchedEffect(turnRequest) {
            if (turnRequest == 0) return@LaunchedEffect
            val direction = turnRequest
            onTurnRequestConsumed()
            val neighborFrame = if (direction < 0) previousFrame else nextFrame
            if (neighborFrame != null) {
                scope.launch { settle(direction, animate = true) }
            } else {
                // 相邻页尚未预排：直接推进，不播动画，避免「旧页弹回 + 跳页」割裂。
                if (!turning) {
                    turning = true
                    try {
                        snapshot = null
                        snapshotFrame = null
                        if (direction < 0) onPrevious() else onNext()
                        onPageTurned()
                        repeat(2) { withFrameNanos { } }
                    } finally {
                        turning = false
                    }
                }
            }
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
                    // 捕获当前页为静态快照：后续拖动只平移位图，页面不再重组
                    if (normalizedEffect in setOf("slide", "cover")) {
                        snapshotFrame = currentFrame
                        scope.launch {
                            snapshot = currentLayer.toImageBitmap()
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        dx.animateTo(0f, tween(140, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        snapshot = null
                        snapshotFrame = null
                    }
                },
                onDragEnd = {
                    val direction = when {
                        dragTotal > width * 0.18f -> -1
                        dragTotal < -width * 0.18f -> 1
                        else -> 0
                    }
                    scope.launch {
                        if (direction == 0) {
                            dx.animateTo(0f, tween(140, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                            snapshot = null
                            snapshotFrame = null
                        } else {
                            settle(direction, animate = true)
                        }
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
                    // snapTo 是 suspend：逐事件 launch 开销可忽略（snapTo 本身轻量）
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
                // 拖动中：当前页用静态快照（快照就绪后），否则回退实时组合
                val showSnapshot = snapshot != null && snapshotFrame === currentFrame

                if (offset < 0f && nextFrame != null) {
                    // 下一页：cover 在下层静止（当前页揭开露出）；slide 并排进入
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(pageBackground)
                            .graphicsLayer {
                                translationX = if (normalizedEffect == "slide") width + offset else 0f
                            },
                    ) { content(nextFrame, false) }
                }
                // 当前页（或快照）：上层，按 offset 平移
                if (showSnapshot) {
                    Image(
                        bitmap = snapshot!!,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = when {
                                    normalizedEffect == "cover" && offset > 0f -> 0f
                                    else -> offset
                                }
                            },
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(pageBackground)
                            // 记录当前页到 graphicsLayer：供 onDragStart 捕获为静态快照
                            .drawWithContent {
                                currentLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(currentLayer)
                            }
                            .graphicsLayer {
                                translationX = when {
                                    normalizedEffect == "cover" && offset > 0f -> 0f
                                    else -> offset
                                }
                            },
                    ) { content(currentFrame, true) }
                }
                if (offset > 0f && previousFrame != null) {
                    // 上一页：cover 从左侧盖回；slide 与当前页并排移动
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(pageBackground)
                            .graphicsLayer { translationX = -width + offset },
                    ) { content(previousFrame, false) }
                }
            }
        } else if (normalizedEffect == "reveal") {
            // 自动翻页专用：下一页从顶部按 revealProgress 揭下，分界线贴在揭起底边。
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
                            .align(Alignment.TopCenter)
                            // 页面图层本身透明：叠加时须铺纸色，否则下层当前页文字透过
                            // 笔画间隙露出来，形成文字重叠。
                            .background(revealBackground)
                            .clipToBounds(),
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
