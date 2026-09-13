package com.creationreadingassistant.feature.reader.pager

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.creationreadingassistant.ui.theme.ReaderPaperSurfaceSpec
import com.creationreadingassistant.ui.theme.readerPaperSurface

/**
 * 翻页容器（2026-09-07 任务 #10 丝滑版）：对标起点 / 番茄的跟手翻页手感。
 *
 * 上轮（任务 #2 修复版）已用三页 ImageBitmap 预渲染消除动画期每帧重录（Slow draw 428→37），
 * 但**捕获帧（拖拽起点 3 页 record + 3×toImageBitmap）与落位提交帧（重渲染新当前页）**仍把
 * 逐帧中位数拉高到 ~19ms（未达 8-13ms 丝滑门槛）。本版把这两类成本整体挪到**空闲期**：
 *
 * 1. **空闲后台预渲染**：settle 完成进入静止态后，[LaunchedEffect] 在空闲帧里把 prev/current/next
 *    各 record 到 GraphicsLayer 并转成位图缓存（键=帧身份）。用户下次拖拽时位图**已就绪**，
 *    动画起点零捕获帧，纯位图 + translationX 起步。捕获分摊到静止后的空闲帧，不堆在动画起点。
 * 2. **硬件位图缓存 + 角色轮换复用**：GraphicsLayer.toImageBitmap() 在 API26+ 返回 HARDWARE
 *    位图，无法绘入软件 Canvas（RGB_565 转换会抛 "Software rendering doesn't support hardware
 *    bitmaps"），故直接缓存硬件位图（GPU 常驻，绘制零拷贝）；settle 时按翻页方向轮换
 *    prev/current/next 三个位图引用（滑入页位图直接升为 current），每次翻页仅新捕获 next 一页，
 *    旧引用交 GC 释放，稳态常驻 3 张全屏位图、内存有界。
 * 3. **落位提交覆盖层**：settle 提交时把「滑入页位图」平移为新的 current 位图并作为
 *    [commitOverlay] 盖在 live 静止页之上 1~2 帧；live 页（供选区/高亮/TTS）在其下完成首绘后
 *    覆盖层撤除 → 提交帧的 ~13ms 重渲染被位图遮住，不再是可见 hitch。
 *
 * 手感增强（保留）：VelocityTracker 轻扫即翻（位移 OR 速度）；settle 以 fling 速度作
 * initialVelocity；时长按 [PageTurnAnimConfig.speed] × 剩余位移比例计算。
 *
 * 各模式语义保持不变：`none` 直接切 / `fade` 交叉淡入 / `slide` 并排滑动 /
 * `cover` 当前页揭开露出下层 / `reveal` 自动翻页揭下（[revealProgress] 驱动）。
 * 静止态 slide/cover 与 none 逐字一致（纯 Box，无 graphicsLayer），保 tap 命中（R1）；
 * [LaunchedEffect](normalizedEffect) 重置状态防运行时切换白屏（R2）。
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
    /** Moving and adjacent frames use the exact same opaque paper surface as the settled reader. */
    pageSurface: ReaderPaperSurfaceSpec = ReaderPaperSurfaceSpec.solid(Color.Transparent),
    /** 真实触发翻页（onPrevious / onNext 已被调用）后回调；用于通知外层隐藏阅读菜单栏。 */
    onPageTurned: () -> Unit = {},
    /**
     * 翻页动画基础时长（ms），取自 [PageTurnAnimConfig.speed]。单页满位移时的时长；
     * 实际 settle 时长 = speed × 剩余位移比例。值越小翻页越快。由外层从设置注入；未注入时用默认值。
     */
    speed: Float = PageTurnAnimConfig().speed,
    content: @Composable BoxScope.(frame: Frame, isCurrent: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxMaxHeight = maxHeight
        val normalizedEffect = if (effect == "curl") "cover" else effect
        val isSlideOrCover = normalizedEffect == "slide" || normalizedEffect == "cover"
        val isCover = normalizedEffect == "cover"
        val dx = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        var dragTotal by remember { mutableFloatStateOf(0f) }
        var turning by remember { mutableStateOf(false) }
        var engaged by remember { mutableStateOf(false) }

        // ── 三页位图缓存（任务 #10：空闲预渲染 / 角色轮换复用引用）──────────────
        val currentLayer = rememberGraphicsLayer()
        val nextLayer = rememberGraphicsLayer()
        val prevLayer = rememberGraphicsLayer()
        var bmpCurrent by remember { mutableStateOf<ImageBitmap?>(null) }
        var bmpNext by remember { mutableStateOf<ImageBitmap?>(null) }
        var bmpPrev by remember { mutableStateOf<ImageBitmap?>(null) }
        var keyCurrent by remember { mutableStateOf<Any?>(null) }
        var keyNext by remember { mutableStateOf<Any?>(null) }
        var keyPrev by remember { mutableStateOf<Any?>(null) }
        // 落位提交覆盖层：滑入页位图盖在 live 静止页上 1~2 帧，遮住提交帧重渲染。
        var commitOverlay by remember { mutableStateOf<ImageBitmap?>(null) }
        // 拟真立体光影：边缘投影与翻页微折痕渐变笔刷（remember 保证动画帧零分配）
        val dropShadowBrush = remember {
            Brush.horizontalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.28f),
                    Color.Black.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
            )
        }
        val creaseBrush = remember {
            Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.04f),
                    Color.Black.copy(alpha = 0.14f),
                ),
            )
        }
        // 单次 record 门控：静止态每页只在帧身份变化后录 1 帧，避免空闲帧持续重录变重。
        var recCur by remember { mutableStateOf(true) }
        var recNext by remember { mutableStateOf(true) }
        var recPrev by remember { mutableStateOf(true) }
        // 拖拽/动画可用纯位图分支的门控：current 必就绪，存在的邻页也必就绪，
        // 否则回退实时捕获分支，避免滑动中某页位图缺失露底。
        // 注：frameAt 每次返回新建 ReaderPageFrame（data class），引用身份===恒不成立；
        // 但其 page 取自缓存 layout.pages 同一实例、chapterText 同串，故用结构相等==作缓存键。
        val dragBitmapsReady = keyCurrent == currentFrame && bmpCurrent != null &&
            (nextFrame == null || (keyNext == nextFrame && bmpNext != null)) &&
            (previousFrame == null || (keyPrev == previousFrame && bmpPrev != null))
        // 预渲染窗口：仅在 settle 后的几帧内挂载隐藏捕获页（邻页全文 compose+layout 很贵），
        // 稳态空闲帧不挂载，避免每帧双页文本布局拉高 P50。
        var prerendering by remember { mutableStateOf(false) }

        // 把 layer 录制内容转为可显示位图；layer 未就绪返回 null。
        // 注：GraphicsLayer.toImageBitmap() 在 API26+ 返回 HARDWARE 位图，无法绘入软件 Canvas
        // （会抛 "Software rendering doesn't support hardware bitmaps"），故不做 RGB_565 软件池转换，
        // 直接缓存硬件位图并靠角色轮换复用引用（每次翻页仅新捕获 next 一页，旧引用交 GC 释放）。
        suspend fun grab(layer: GraphicsLayer): ImageBitmap? {
            if (layer.size == IntSize.Zero) return null
            return layer.toImageBitmap()
        }

        // translationX 表达式（draw 期求值：在 graphicsLayer block 内调用，读 dx.value 不触发重组）。
        val nextTx: () -> Float = { if (isCover) 0f else width + dx.value }
        val currentTx: () -> Float = { if (isCover && dx.value > 0f) 0f else dx.value }
        val prevTx: () -> Float = { -width + dx.value }

        // 空闲后台预渲染：静止且非翻页中时，等布局/首录完成后把三页转为位图缓存。
        // 捕获发生在 settle 之后的空闲帧，用户下次拖拽直接命中已就绪位图。
        LaunchedEffect(currentFrame, previousFrame, nextFrame, engaged, turning, isSlideOrCover) {
            if (!isSlideOrCover || engaged || turning) return@LaunchedEffect
            prerendering = true
            repeat(2) { withFrameNanos { } }
            if (keyCurrent != currentFrame) {
                bmpCurrent = grab(currentLayer)
                keyCurrent = currentFrame
            }
            if (nextFrame != null && keyNext != nextFrame) {
                bmpNext = grab(nextLayer)
                keyNext = nextFrame
            }
            if (previousFrame != null && keyPrev != previousFrame) {
                bmpPrev = grab(prevLayer)
                keyPrev = previousFrame
            }
            recCur = false
            recNext = false
            recPrev = false
            prerendering = false
        }

        // 帧身份变化 → 允许该页再录 1 帧；current 变化同时安排撤除提交覆盖层。
        LaunchedEffect(currentFrame) {
            recCur = true
            repeat(2) { withFrameNanos { } }
            commitOverlay = null
        }
        LaunchedEffect(nextFrame) { recNext = true }
        LaunchedEffect(previousFrame) { recPrev = true }

        // 兜底捕获：空闲预渲染未完成（如首翻）时，settle 前 await 就绪再启动动画。
        suspend fun ensureBitmaps() {
            if (dragBitmapsReady) return
            repeat(3) {
                withFrameNanos { }
                if (currentLayer.size != IntSize.Zero) {
                    bmpCurrent = grab(currentLayer)
                    bmpNext = if (nextFrame != null) grab(nextLayer) else null
                    bmpPrev = if (previousFrame != null) grab(prevLayer) else null
                    keyCurrent = currentFrame
                    keyNext = nextFrame
                    keyPrev = previousFrame
                    return
                }
            }
        }

        suspend fun settle(direction: Int, animate: Boolean, initialVelocity: Float = 0f) {
            if (turning || direction == 0) return
            val neighbor = if (direction < 0) previousFrame else nextFrame
            if (neighbor == null) {
                dx.animateTo(0f, tween(120))
                engaged = false
                return
            }
            turning = true
            val animatedSlide = animate && isSlideOrCover
            try {
                if (animatedSlide) {
                    if (!engaged) engaged = true
                    ensureBitmaps() // 动画循环外捕获；空闲已预渲染则立即返回
                    val target = if (direction < 0) width else -width
                    val remaining = abs(target - dx.value)
                    val ratio = (remaining / width).coerceIn(0.08f, 1f)
                    val base = speed.toInt().coerceAtLeast(70)
                    val durationMs = (speed * ratio).toInt().coerceIn(70, base)
                    dx.animateTo(
                        target,
                        tween(durationMs, easing = FastOutSlowInEasing),
                        initialVelocity = initialVelocity,
                    )
                }
                // 落位原子提交：换帧 + 位图缓存平移 + 覆盖层，合并进同一次重组。
                if (direction < 0) onPrevious() else onNext()
                onPageTurned()
                if (animatedSlide) {
                    // 位图缓存引用沿翻页方向轮换：滑入页位图成为新 current（零重渲染），
                    // 失效邻页引用置空交空闲预渲染补齐；各 ImageBitmap 为独立对象，无物理槽冲突。
                    if (direction > 0) {
                        keyPrev = keyCurrent; bmpPrev = bmpCurrent
                        keyCurrent = keyNext; bmpCurrent = bmpNext
                        keyNext = null; bmpNext = null
                    } else {
                        keyNext = keyCurrent; bmpNext = bmpCurrent
                        keyCurrent = keyPrev; bmpCurrent = bmpPrev
                        keyPrev = null; bmpPrev = null
                    }
                    commitOverlay = bmpCurrent
                } else {
                    repeat(2) { withFrameNanos { } }
                }
                dx.snapTo(0f)
                engaged = false
            } finally {
                turning = false
            }
        }

        // R2：运行时切换翻页效果时彻底重置动画/缓存状态，避免残留导致白屏/冻结。
        LaunchedEffect(normalizedEffect) {
            engaged = false
            turning = false
            dragTotal = 0f
            commitOverlay = null
            bmpCurrent = null; bmpNext = null; bmpPrev = null
            keyCurrent = null; keyNext = null; keyPrev = null
            dx.snapTo(0f)
        }

        LaunchedEffect(turnRequest) {
            if (turnRequest == 0) return@LaunchedEffect
            val direction = turnRequest
            onTurnRequestConsumed()
            // P0 修复：settle 必须跑在**不被 turnRequest key 取消**的作用域。
            // 旧实现把悬挂的 settle 直接放在本 LaunchedEffect 内：onTurnRequestConsumed()
            // 把 key 归 0 会重启/取消本协程 → slide/cover 的 dx.animateTo 被中断 →
            // onNext/onPrevious 永不执行 → tap 死锁（none/fade 同步提交不受影响故仅 slide/cover 死锁）。
            // 改由 rememberCoroutineScope（随组合生命周期、与 key 无关）驱动，动画协程不被自身 key 取消。
            scope.launch {
                val neighborFrame = if (direction < 0) previousFrame else nextFrame
                if (neighborFrame != null) {
                    settle(direction, animate = true)
                } else {
                    if (!turning) {
                        turning = true
                        try {
                            if (direction < 0) onPrevious() else onNext()
                            onPageTurned()
                            repeat(2) { withFrameNanos { } }
                            engaged = false
                            commitOverlay = null
                        } finally {
                            turning = false
                        }
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
            var velocityTracker = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = {
                    dragTotal = 0f
                    velocityTracker = VelocityTracker()
                    if (isSlideOrCover) {
                        engaged = true
                        // 拖拽起点异步补齐位图（空闲已预渲染则立即返回），捕获不占动画帧预算。
                        scope.launch { ensureBitmaps() }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        dx.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
                        engaged = false
                    }
                },
                onDragEnd = {
                    val velocityX = velocityTracker.calculateVelocity().x
                    val flingThreshold = 400f
                    val direction = when {
                        dragTotal > width * 0.18f || velocityX > flingThreshold -> -1
                        dragTotal < -width * 0.18f || velocityX < -flingThreshold -> 1
                        else -> 0
                    }
                    scope.launch {
                        if (direction == 0) {
                            dx.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
                            engaged = false
                        } else {
                            settle(direction, animate = true, initialVelocity = velocityX)
                        }
                    }
                },
            ) { change, amount ->
                if (turning) return@detectHorizontalDragGestures
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                dragTotal += amount
                if (isSlideOrCover) {
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                ) { content(frame, frame == currentFrame) }
            }
        } else if (isSlideOrCover) {
            Box(Modifier.fillMaxSize().clipToBounds().then(dragModifier)) {
                if (!engaged) {
                    // 静止态（R1）：与 none 分支逐字一致的纯 Box；额外把当前页单次 record 进
                    // currentLayer 供空闲预渲染，并挂隐藏捕获页预渲染 next/prev。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .drawWithContent {
                                if (recCur) {
                                    currentLayer.record(
                                        size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                    ) { this@drawWithContent.drawContent() }
                                }
                                drawContent()
                            },
                    ) {
                        // P1 修复：静止态录制/显示必须包含不透明纸色，否则 cover/slide 动画期
                        // 位图或兜底分支透明、露出底层背景（透明洞）。纸色默认 Transparent，
                        // 未注入时对真实宿主无视觉影响。
                        Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) {
                            content(currentFrame, true)
                        }
                    }
                    if (prerendering && nextFrame != null) {
                        CapturePage(nextLayer, { 0f }, pageSurface, hidden = true, record = { recNext }) {
                            content(nextFrame, false)
                        }
                    }
                    if (prerendering && previousFrame != null) {
                        CapturePage(prevLayer, { 0f }, pageSurface, hidden = true, record = { recPrev }) {
                            content(previousFrame, false)
                        }
                    }
                    // 落位提交覆盖层：滑入页位图盖住 live 首绘，提交帧不可见。
                    commitOverlay?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                        )
                    }
                } else if (dragBitmapsReady) {
                    if (isCover) {
                        // cover：当前页揭开、下层静止，需逐页独立变换。
                        // 1. 底层下页（向左翻时揭开露出）
                        bmpNext?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().graphicsLayer { translationX = nextTx() },
                            )
                        }
                        // 2. 当前页（向左翻时平移揭开，向右翻时在底层静止）
                        bmpCurrent?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().graphicsLayer { translationX = currentTx() },
                            )
                        }
                        // 3. 向左翻页（dx.value < 0）：揭起页微折痕与落到底层的外投阴影
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(16.dp)
                                .graphicsLayer {
                                    translationX = width + dx.value - 16.dp.toPx()
                                    alpha = if (nextFrame != null && dx.value < 0f) {
                                        ((-dx.value) / (width * 0.08f)).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                }
                                .background(creaseBrush),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(32.dp)
                                .graphicsLayer {
                                    translationX = width + dx.value
                                    alpha = if (nextFrame != null && dx.value < 0f) {
                                        ((-dx.value) / (width * 0.08f)).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                }
                                .background(dropShadowBrush),
                        )
                        // 4. 上层上一页（向右翻时从左侧滑入覆盖当前页）
                        bmpPrev?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    translationX = prevTx()
                                    alpha = if (previousFrame != null && dx.value > 0f) 1f else 0f
                                },
                            )
                        }
                        // 5. 向右翻页（dx.value > 0）：覆盖页微折痕与落到当前页的外投阴影
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(16.dp)
                                .graphicsLayer {
                                    translationX = dx.value - 16.dp.toPx()
                                    alpha = if (previousFrame != null && dx.value > 0f) {
                                        (dx.value / (width * 0.08f)).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                }
                                .background(creaseBrush),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(32.dp)
                                .graphicsLayer {
                                    translationX = dx.value
                                    alpha = if (previousFrame != null && dx.value > 0f) {
                                        (dx.value / (width * 0.08f)).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                }
                                .background(dropShadowBrush),
                        )
                    } else {
                        // slide：prev/current/next 同速平移。每页位图用**各自 graphicsLayer**
                        // 在 draw 期求 translationX（base + dx.value），不使用 offset/布局修饰符 →
                        // 动画帧只更新 3 个 RenderNode 变换，不触发 measure/layout、不重录子树。
                        // （任务 #10 诊断：T10 单容器平移 + offset{} 使动画帧每帧 measureAndLayout
                        // + Record View#draw 2-4ms，叠加捕获帧达 42ms；逐页 layer 消除 layout 相位。）
                        bmpPrev?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                                    .graphicsLayer { translationX = prevTx() },
                            )
                        }
                        bmpCurrent?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                                    .graphicsLayer { translationX = currentTx() },
                            )
                        }
                        bmpNext?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                                    .graphicsLayer { translationX = nextTx() },
                            )
                        }
                    }
                } else {
                    // 兜底（空闲预渲染/拖拽起点补齐均未就绪，极少见）：退化为静止当前页（等同 none）。
                    // **绝不**在动画帧内 record 正文 —— 旧实现在此分支每帧重录三页（CapturePage
                    // record={true}），是 42ms/帧 的直接来源。
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) {
                            content(currentFrame, true)
                        }
                    }
                }
            }
        } else if (normalizedEffect == "reveal") {
            // 自动翻页专用：下一页从顶部按 revealProgress 揭下，分界线贴在揭起底边。
            Box(Modifier.fillMaxSize().then(dragModifier)) {
                val offset = dx.value
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (offset != 0f || revealProgress > 0f) {
                                Modifier.readerPaperSurface(pageSurface)
                            } else {
                                Modifier
                            },
                        )
                        .clipToBounds()
                        .graphicsLayer { translationX = offset },
                ) { content(currentFrame, true) }
                if (offset <= 0f && nextFrame != null && revealProgress > 0f) {
                    val revealHeight = boxMaxHeight * revealProgress.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(revealHeight)
                            .align(Alignment.TopCenter)
                            .readerPaperSurface(pageSurface)
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
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .then(dragModifier),
            ) { content(currentFrame, true) }
        }
    }
}

/**
 * 捕获帧用的单页包装：外层 [graphicsLayer] 按 [tx] 定位（draw 期求值），
 * 内层 [drawWithContent] 在 [record] 为 true 的那一帧把「纸面 + 正文」录制到 [layer]
 * （不含位移），供 [GraphicsLayer.toImageBitmap] 转进位图池；非录制帧不绘制（隐藏页零成本）。
 *
 * [hidden] 为 true 时把节点偏移到可视区外（父容器 clipToBounds 裁掉），
 * 仅用于静止态空闲预渲染邻页：照常组合/录制但不显示、不挡 tap。
 */
@Composable
private fun BoxScope.CapturePage(
    layer: GraphicsLayer,
    tx: () -> Float,
    pageSurface: ReaderPaperSurfaceSpec,
    hidden: Boolean,
    record: () -> Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (hidden) Modifier.offset { IntOffset(100_000, 0) } else Modifier)
            .clipToBounds()
            .graphicsLayer { translationX = tx() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (record()) {
                        layer.record(
                            size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        ) { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) { content() }
        }
    }
}
