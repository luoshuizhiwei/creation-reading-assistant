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
 * 4. **动画现场快照 [TurnScene]**（2026-09-17 修 slide/cover「闪一下 + 顿一下 + 快翻空白」）：
 *    拖拽起点/动画起点冻结参与本次翻页的三帧，动画期间渲染只读快照。此前渲染读实时
 *    currentFrame，而换帧发生在动画结束的同一帧 ⇒ 位图就绪判定瞬间翻转、渲染分支从位图
 *    分支掉回实时分支（=闪屏，且该帧要实时组合整页正文=卡顿）。详见 [TurnScene]。
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
        // 连点积压队列 + 排空闸门（S06-⑤）：动画期间到达的翻页请求不再被 settle 的
        // `if (turning) return` 无声吞掉，而是排队到当前动画完整落位后按 FIFO 消费。
        // 队列本身不参与渲染，故用普通容器承载（见 [TurnGate]）以免无谓重组。
        val pendingTurns = remember { PendingTurnQueue() }
        val turnGate = remember { TurnGate() }
        // 每次翻页提交后递增：作为排空效果的 key，保证下一轮用的是换帧后的新闭包。
        var turnEpoch by remember { mutableStateOf(0) }
        // 一次拖拽/翻页动画的渲染现场快照：非 null ⇔ 动画进行中（取代旧的 `engaged` 布尔）。
        // 动画期间所有渲染只读 [scene]，绝不读实时 currentFrame/nextFrame/previousFrame ——
        // 详见 [beginTurn] 的注释：实时读取会在换帧那一帧翻转渲染分支，是闪屏与卡顿的根因。
        var scene by remember { mutableStateOf<TurnScene<Frame>?>(null) }

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
        // layer 内容身份追踪：record 发生时记下录制的帧身份（draw 期写入 / 协程期读取），
        // 供 ensureBitmaps/空闲预渲染校验 layer 内容未过期。修复快速连翻时兜底捕获把
        // 上一轮邻页的过期录制晋升为“新鲜”位图 → 动画放错页/提交后闪变。
        val recordedCurrent = remember { RecordedFrameSlot() }
        val recordedNext = remember { RecordedFrameSlot() }
        val recordedPrev = remember { RecordedFrameSlot() }
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
        // 拖拽/动画可用纯位图分支的门控：**针对当前渲染现场**（静止=实时三帧，动画中=快照三帧）
        // 判定：current 必就绪，存在的邻页也必就绪，否则回退实时渲染分支。
        // 关键：动画期间必须按[快照]的帧身份判定，不能按实时 currentFrame —— 换帧发生在
        // 动画结束的同一帧，而位图引用要等那之后才轮换，实时判定会在最后一帧把
        // dragBitmapsReady 打为 false，使渲染从位图分支翻回实时分支 ⇒ 可见闪屏（P0 闪屏根因）。
        // 注：frameAt 每次返回新建 ReaderPageFrame（data class），引用身份===恒不成立；
        // 但其 page 取自缓存 layout.pages 同一实例、chapterText 同串，故用结构相等==作缓存键。
        val sceneCur = scene?.current ?: currentFrame
        val sceneNext = scene?.next ?: nextFrame
        val scenePrev = scene?.previous ?: previousFrame
        val dragBitmapsReady = keyCurrent == sceneCur && bmpCurrent != null &&
            (sceneNext == null || (keyNext == sceneNext && bmpNext != null)) &&
            (scenePrev == null || (keyPrev == scenePrev && bmpPrev != null))
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
        // 晋升纪律（P0 修复）：仅当「layer 内容身份 == 当前帧」且抓取非空时才写 key/bitmap，
        // 否则保持 key 为空让下次重试 —— 修复旧版把空抓取/过期录制无条件标记为新鲜，
        // 导致 dragBitmapsReady 判定通过但动画期露底/提交后闪变且永不补抓。
        LaunchedEffect(currentFrame, previousFrame, nextFrame, scene, turning, isSlideOrCover) {
            if (!isSlideOrCover || scene != null || turning) return@LaunchedEffect
            if (dragBitmapsReady) return@LaunchedEffect
            prerendering = true
            var attempts = 0
            while (attempts < 5) {
                withFrameNanos { }
                if (keyCurrent != sceneCur || bmpCurrent == null) {
                    if (recordedCurrent.frame == sceneCur) {
                        grab(currentLayer)?.let { bmpCurrent = it; keyCurrent = sceneCur }
                    }
                }
                if (sceneNext != null && (keyNext != sceneNext || bmpNext == null)) {
                    if (recordedNext.frame == sceneNext) {
                        grab(nextLayer)?.let { bmpNext = it; keyNext = sceneNext }
                    }
                }
                if (scenePrev != null && (keyPrev != scenePrev || bmpPrev == null)) {
                    if (recordedPrev.frame == scenePrev) {
                        grab(prevLayer)?.let { bmpPrev = it; keyPrev = scenePrev }
                    }
                }
                val ready = keyCurrent == sceneCur && bmpCurrent != null &&
                    (sceneNext == null || (keyNext == sceneNext && bmpNext != null)) &&
                    (scenePrev == null || (keyPrev == scenePrev && bmpPrev != null))
                if (ready || ++attempts >= 5) break
            }
            if (keyCurrent == sceneCur && bmpCurrent != null) recCur = false
            if (sceneNext == null || (keyNext == sceneNext && bmpNext != null)) recNext = false
            if (scenePrev == null || (keyPrev == scenePrev && bmpPrev != null)) recPrev = false
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

        // 兜底捕获：把「静止态已录好」的层内容抓成位图。P0 修复：只晋升「layer 内容身份
        // == 当前帧」且抓取非空的结果，过期录制保持 key 为空，走兜底实时渲染分支（正确）。
        //
        // **绝不在此等帧**（2026-09-18 真机反馈「翻页前停顿一下才翻」）：捕获页只挂在静止态，
        // 一旦进入动画（scene != null）邻页就**不可能补录** —— 缺谁的位图只会一直缺，
        // 原地等 3 帧是每帧 16ms 的纯白停顿。录好了就立即抓，没录好立即返回走实时兜底。
        suspend fun ensureBitmaps() {
            if (dragBitmapsReady) return
            if (recordedCurrent.frame == sceneCur) {
                grab(currentLayer)?.let { bmpCurrent = it; keyCurrent = sceneCur }
            }
            if (sceneNext != null && recordedNext.frame == sceneNext) {
                grab(nextLayer)?.let { bmpNext = it; keyNext = sceneNext }
            }
            if (scenePrev != null && recordedPrev.frame == scenePrev) {
                grab(prevLayer)?.let { bmpPrev = it; keyPrev = scenePrev }
            }
        }

        suspend fun settle(direction: Int, animate: Boolean, initialVelocity: Float = 0f) {
            if (turning || direction == 0) return
            val neighbor = if (direction < 0) previousFrame else nextFrame
            if (neighbor == null) {
                dx.animateTo(0f, tween(120))
                scene = null
                return
            }
            turning = true
            val animatedSlide = animate && isSlideOrCover
            try {
                if (animatedSlide) {
                    // 冻结动画现场：此后整个动画期间的渲染只读这份快照。
                    if (scene == null) scene = TurnScene(currentFrame, previousFrame, nextFrame)
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
                // 落位原子提交：换帧 + 位图缓存轮换，合并进同一次重组。
                // 注意：**提交后仍不清 scene** —— 此刻快照里的三帧与刚轮换的位图引用仍一一
                // 对应（旧 current→prev、旧 next→current），继续用快照渲染完全正确。
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
                    // 提交帧：dx 归零 + 撤掉快照交给 live 页。覆盖层遮住 live 首绘；
                    // 覆盖层在 **turning 窗口之外**等两帧后撤除（见下方）。
                    dx.snapTo(0f)
                    scene = null
                } else {
                    dx.snapTo(0f)
                    scene = null
                }
            } finally {
                turning = false
            }
            // 提交帧保护必须放在 turning 窗口**之外**：turning=true 期间 tap 会被直接吞掉，
            // 若在这两帧里等待，用户连续点按就变成「翻完了停一下才有反应」（真机反馈）。
            // 等 2 帧让 live 新页与 commitOverlay 同屏后撤覆盖层，避免中间出现无覆盖裸帧。
            if (animatedSlide) {
                repeat(2) { withFrameNanos { } }
                commitOverlay = null
            }
        }

        /**
         * 邻页快照缺失时的翻页（原 `LaunchedEffect(turnRequest)` 的 else 分支，语义原样保留）：
         * 仍把这一翻页交给外层并给 live 页两帧时间，不进入 slide/cover 的位图动画。
         */
        suspend fun turnWithoutNeighborSnapshot(direction: Int) {
            if (turning) return
            turning = true
            try {
                if (direction < 0) onPrevious() else onNext()
                onPageTurned()
                repeat(2) { withFrameNanos { } }
                scene = null
                commitOverlay = null
            } finally {
                turning = false
            }
        }

        // R2：运行时切换翻页效果时彻底重置动画/缓存状态，避免残留导致白屏/冻结。
        LaunchedEffect(normalizedEffect) {
            scene = null
            turning = false
            // 效果切换会把 dx/位图/快照全部作废，尚未开始的积压也一并丢弃（不留半程请求）。
            pendingTurns.clear()
            dragTotal = 0f
            commitOverlay = null
            bmpCurrent = null; bmpNext = null; bmpPrev = null
            keyCurrent = null; keyNext = null; keyPrev = null
            recordedCurrent.frame = null
            recordedNext.frame = null
            recordedPrev.frame = null
            dx.snapTo(0f)
        }

        // 排空一次只放一个请求。为什么不写成「一个协程里循环 settle 到队列为空」：局部 `settle`
        // 捕获的是它被创建那次重组的 currentFrame/previousFrame/nextFrame，第一次提交后外层已换帧，
        // 同一协程继续循环就会用**旧帧**冻结 TurnScene ⇒ 第 2..N 次动画播过期内容。因此每次提交后
        // 递增 turnEpoch，让本 effect 在换帧后的**新**闭包里重新装配一次排空。
        // 入队 / 上限 / 反向替换的策略在 [PendingTurnQueue]，有独立 JVM 测试钉住。
        LaunchedEffect(turnEpoch, turnRequest) {
            // 先无条件入队再考虑排空：若连「消费」都被 pumping/turning 挡住，外层归零
            // 请求的契约就被破坏（下一次同向点按不再改变 key，请求会永久消失）。
            if (turnRequest != 0) {
                val requested = turnRequest
                onTurnRequestConsumed()
                pendingTurns.enqueue(requested)
            }
            if (turnGate.pumping || turning || turnGate.dragging) return@LaunchedEffect
            val direction = pendingTurns.poll() ?: return@LaunchedEffect
            turnGate.pumping = true
            // settle 必须跑在**不被 turnRequest / turnEpoch key 取消**的作用域。
            // 旧实现把悬挂的 settle 直接放在本 LaunchedEffect 内：key 变化会重启/取消本协程 →
            // slide/cover 的 dx.animateTo 被中断 → onNext/onPrevious 永不执行 → tap 死锁
            // （none/fade 同步提交不受影响故仅 slide/cover 死锁）。
            scope.launch {
                if (turning || turnGate.dragging) {
                    // 出队后、启动前被拖拽动画抢占：请求放回队首，本次 epoch 递增会重试，
                    // 不把它悄悄丢掉（这是「一次点按丢一页」里唯一的窄竞态窗口）。
                    pendingTurns.requeueFront(direction)
                    turnGate.pumping = false
                    turnEpoch++
                    return@launch
                }
                try {
                    val neighborFrame = if (direction < 0) previousFrame else nextFrame
                    if (neighborFrame == null) {
                        turnWithoutNeighborSnapshot(direction)
                    } else {
                        settle(direction, animate = true)
                    }
                } finally {
                    turnGate.pumping = false
                    turnEpoch++ // 用提交后的新帧重新装配一次排空
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
                    turnGate.dragging = true
                    if (isSlideOrCover) {
                        // 冻结现场：拖拽起点就定死参与本次手势的三帧，后续渲染不随实时帧变化。
                        if (scene == null) scene = TurnScene(currentFrame, previousFrame, nextFrame)
                        // 拖拽起点异步补齐位图（空闲已预渲染则立即返回），捕获不占动画帧预算。
                        scope.launch { ensureBitmaps() }
                    }
                },
                onDragCancel = {
                    turnGate.dragging = false
                    scope.launch {
                        dx.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
                        scene = null
                        // 手势让位后补一次排空：拖拽期间到达的点按不该烂在队列里等下一次输入。
                        turnEpoch++
                    }
                },
                onDragEnd = {
                    turnGate.dragging = false
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
                            scene = null
                        } else {
                            settle(direction, animate = true, initialVelocity = velocityX)
                        }
                        turnEpoch++ // 松手动画落地后重新装配排空（拖拽本身不入队，见报告语义说明）
                    }
                },
            ) { change, amount ->
                if (turning) return@detectHorizontalDragGestures
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                dragTotal += amount
                if (isSlideOrCover) {
                    val target = (dx.value + amount).coerceIn(
                        if (sceneNext != null) -width else 0f,
                        if (scenePrev != null) width else 0f,
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
                if (scene == null) {
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
                                    recordedCurrent.frame = currentFrame
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
                    if (prerendering && sceneNext != null) {
                        CapturePage(
                            nextLayer, { 0f }, pageSurface,
                            hidden = true, record = { recNext },
                            onRecorded = { recordedNext.frame = sceneNext },
                        ) {
                            content(sceneNext, false)
                        }
                    }
                    if (prerendering && scenePrev != null) {
                        CapturePage(
                            prevLayer, { 0f }, pageSurface,
                            hidden = true, record = { recPrev },
                            onRecorded = { recordedPrev.frame = scenePrev },
                        ) {
                            content(scenePrev, false)
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
                                    alpha = if (sceneNext != null && dx.value < 0f) {
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
                                    alpha = if (sceneNext != null && dx.value < 0f) {
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
                                    alpha = if (scenePrev != null && dx.value > 0f) 1f else 0f
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
                                    alpha = if (scenePrev != null && dx.value > 0f) {
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
                                    alpha = if (scenePrev != null && dx.value > 0f) {
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
                    // 兜底（位图未就绪或已过期，如首翻/快速连翻）：实时渲染 + graphicsLayer
                    // 平移，拖拽/动画全程跟手，修复旧版“页面冻结拖不动→动画结束硬跳”被
                    // 观感为翻页变滑动。相邻页实时组合（仅位图缺失的这一次翻页付出成本）；
                    // **绝不**在动画帧内 record 正文（旧实现每帧重录三页是 42ms/帧的来源）。
                    // 空白（P0 根因之二）：兜底分支原先只给**邻页自身**套纸色，邻页为 null
                    // （高速翻页时邻章尚未预排 → frameAt 返回 null）就什么都不画，当前页平移
                    // 走后露出底层背景 = 空白页。改为**父容器常驻不透明纸色**：邻页缺失时
                    // 至少是一张空白纸，而不是透明洞。
                    if (isCover) {
                        // cover 兜底（对齐位图分支语义）：向前翻时下层静止渲染下一页、
                        // 当前页平移揭开；向后翻时当前页静止、上一页从左侧滑入覆盖。
                        // 邻页只在**真的露出时**才组合（dx 严格越过 0），静止态零额外成本。
                        Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds())
                        if (dx.value < 0f) {
                            sceneNext?.let { frame ->
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .readerPaperSurface(pageSurface)
                                        .clipToBounds(),
                                ) { content(frame, false) }
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = if (dx.value > 0f) 0f else dx.value }
                                .clipToBounds(),
                        ) {
                            Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) {
                                content(sceneCur, true)
                            }
                        }
                        if (dx.value > 0f) {
                            scenePrev?.let { frame ->
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { translationX = -width + dx.value }
                                        .clipToBounds(),
                                ) {
                                    Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) {
                                        content(frame, false)
                                    }
                                }
                            }
                        }
                    } else {
                        // slide 兜底：prev/current/next 实时同速平移（tx 表达式与位图分支一致）。
                        // 同样先铺一层不透明纸色兜住邻页缺失，避免高速翻页露出透明洞。
                        Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds())
                        scenePrev?.let { frame ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = prevTx() }
                                    .clipToBounds(),
                            ) {
                                Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) {
                                    content(frame, false)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = currentTx() }
                                .clipToBounds(),
                        ) {
                            Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) {
                                content(sceneCur, true)
                            }
                        }
                        sceneNext?.let { frame ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = nextTx() }
                                    .clipToBounds(),
                            ) {
                                Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) {
                                    content(frame, false)
                                }
                            }
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
 * [onRecorded] 在录制发生的那一帧回调，用于登记 layer 内容身份（帧身份），
 * 供位图晋升前校验内容未过期。
 */
@Composable
private fun BoxScope.CapturePage(
    layer: GraphicsLayer,
    tx: () -> Float,
    pageSurface: ReaderPaperSurfaceSpec,
    hidden: Boolean,
    record: () -> Boolean,
    onRecorded: () -> Unit,
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
                        onRecorded()
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) { content() }
        }
    }
}

/**
 * 一次翻页动画的「渲染现场」快照。
 *
 * 为什么需要它（P0 闪屏/空白根因）：`settle()` 里 `onNext()` 换帧发生在 `dx` 动画结束的
 * **同一帧**，而位图引用要等换帧之后才轮换。若动画期间的渲染读实时 `currentFrame`，
 * 就会在最后一帧出现「帧已换、位图未换」的错位：位图就绪判定瞬间为 false，渲染从位图
 * 分支翻回实时分支，并把**已经翻过去的新帧**当旧帧画出来 —— 观感即「文字闪一下」，
 * 且这一帧要实时组合整页正文（十几到几十毫秒）⇒ 卡顿。
 *
 * 冻结后：动画全程只认快照里的三帧，换帧/位图轮换都在同一份现场上做，分支不会翻转。
 */
private data class TurnScene<Frame>(
    val current: Frame,
    val previous: Frame?,
    val next: Frame?,
)

/**
 * 各 GraphicsLayer 最近一次录制内容对应的帧身份（draw 期写入 / 协程期读取）。
 * 普通可变引用而非 snapshot state：不参与重组，仅供位图晋升前校验 layer 内容未过期。
 */
private class RecordedFrameSlot { var frame: Any? = null }

/**
 * 排空闸门：[pumping] 保证同一时刻只有一个串行泵在消费队列；[dragging] 让泵避开
 * 进行中的拖拽手势，防止排队请求在手指下面把 dx 抢走。
 * 二者都不参与渲染，故用普通可变容器而非 snapshot state（同 [RecordedFrameSlot]）。
 */
private class TurnGate {
    var pumping = false
    var dragging = false
}
