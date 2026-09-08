package com.creationreadingassistant.feature.reader.pager

import android.text.TextPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.PageHitTest
import com.creationreadingassistant.feature.reader.layout.pageAccessibleText
import com.creationreadingassistant.ui.theme.ReaderPaperSurfaceSpec
import com.creationreadingassistant.ui.theme.readerPaperSurface
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** 手动 `reveal` 不能交给仅支持自动揭页的 [PageTurner]，由外层保留相邻帧实现真实显露。 */
internal enum class PagedReaderPageTurnRenderer { PAGE_TURNER, OUTER_REVEAL }

internal fun pagedReaderPageTurnRenderer(
    pageTurnEffect: String,
    autoPageIntervalMillis: Long?,
): PagedReaderPageTurnRenderer =
    if (pageTurnEffect == "reveal" && autoPageIntervalMillis == null) {
        PagedReaderPageTurnRenderer.OUTER_REVEAL
    } else {
        PagedReaderPageTurnRenderer.PAGE_TURNER
    }

/** 外层 reveal 的绘制不变量：目标页的纸面背景和正文必须在同一裁切层内。 */
internal data class ManualRevealLayerPolicy(
    val keepsCurrentFrameVisible: Boolean,
    val clipsTargetPaperWithTargetContent: Boolean,
    val commitsPageAfterReveal: Boolean,
)

internal fun manualRevealLayerPolicy(): ManualRevealLayerPolicy = ManualRevealLayerPolicy(
    keepsCurrentFrameVisible = true,
    clipsTargetPaperWithTargetContent = true,
    commitsPageAfterReveal = true,
)

/**
 * 任务 #15 结构拆分：从 [PagedReaderHost] 抽出的页面渲染层
 * （加载/错误占位 → underlay 汇总 → 手势 Modifier → [PageTurner]）。
 *
 * 渲染体与手势闭包逐字搬运；手势协程仍只随 [controller] 重启，
 * 分区模式经 rememberUpdatedState 透传，页面/章起点在闭包内现读 controller。
 */
@Suppress("LongParameterList")
@Composable
internal fun PagedReaderPageSurface(
    controller: PagedReaderController,
    cfg: LayoutConfig,
    paint: TextPaint,
    headingPaint: TextPaint,
    textColor: Color,
    selRange: MutableState<IntRange?>,
    turnRequest: MutableIntState,
    revealProgress: Float,
    tapZoneMode: String,
    density: Density,
    horizontalInsetDp: Dp,
    contentWidthDp: Dp,
    autoPageIntervalMillis: Long?,
    pageTurnEffect: String,
    /** 翻页动画基础时长（ms），来自 ReaderSettings.pageTurnSpeed；仅作为 [PageTurner] 的 speed 入参。 */
    pageTurnSpeed: Float,
    pageSurface: ReaderPaperSurfaceSpec,
    ttsRangeAbs: Pair<Int, Int>?,
    ttsHighlightColor: Color,
    selectionColor: Color,
    persistentHighlights: List<Pair<IntRange, Color>>,
    searchHitRangeAbs: Pair<Int, Int>?,
    searchHighlightColor: Color,
    onSelect: (String, Int, Int) -> Unit,
    onGesturePageTurn: () -> Unit,
    onToggleControls: () -> Unit,
    onStopAutoPaging: () -> Unit,
) {
    val stopAutoPaging by rememberUpdatedState(onStopAutoPaging)
    val page = controller.currentPage
    if (page == null) {
        val message = controller.loadError
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (message == null) {
                        // 与既有「正在加载章节」约定一致：加载框给明确的加载语义
                        Modifier.semantics { contentDescription = "正在加载正文" }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (message == null) {
                CircularProgressIndicator()
            } else {
                Text(message, color = textColor)
            }
        }
    } else {
        // TTS 当前句：全书偏移 → 章内偏移
        val chStart = controller.currentChapterStartAbs
        val ttsRects = remember(page, ttsRangeAbs, chStart, controller) {
            val r = ttsRangeAbs ?: return@remember emptyList()
            val (start, end) = controller.currentChapterSourceRangeToLocalDisplay(r.first, r.second)
            PageSelection.rectsForRange(page, cfg, start, end)
        }
        val selRects = remember(page, selRange.value) {
            val r = selRange.value ?: return@remember emptyList()
            PageSelection.rectsForRange(page, cfg, r.first, r.last + 1)
        }
        val selectionHandles = remember(page, selRange.value) {
            val r = selRange.value ?: return@remember null
            PageSelection.calculateSelectionHandles(page, cfg, r)
        }
        // 已存高亮：常驻底色。放最底层，TTS 句与活动选区盖在其上。
        val hlUnderlays = remember(page, persistentHighlights, chStart, controller) {
            persistentHighlights.mapNotNull { (range, color) ->
                val (start, end) = controller.currentChapterSourceRangeToLocalDisplay(
                    range.first,
                    range.last + 1,
                )
                val rects = PageSelection.rectsForRange(
                    page, cfg, start, end,
                )
                if (rects.isEmpty()) null else color to rects
            }
        }
        // 搜索命中：命中所在章未加载时 page==null 不绘制；跳转完成后
        // 本页渲染时才有 rects，天然满足「先完成跳转/加载，再显示高亮」。
        val searchRects = remember(page, searchHitRangeAbs, chStart, controller) {
            val r = searchHitRangeAbs ?: return@remember emptyList()
            val (start, end) = controller.currentChapterSourceRangeToLocalDisplay(r.first, r.second)
            PageSelection.rectsForRange(page, cfg, start, end)
        }
        val underlays: List<Pair<Color, List<PageHitTest.Rect>>> = hlUnderlays + listOf(
            searchHighlightColor to searchRects,
            ttsHighlightColor to ttsRects,
            selectionColor to selRects,
        )
        // 手势协程只随 controller 重启，闭包捕获的组合期快照会冻结在首次触摸前
        // （对抗性复核反编译 compose-ui 1.7 证实：key 不变时 update 不重启协程）。
        // 因此分区模式经 rememberUpdatedState 透传，页面/章起点在闭包内现读 controller。
        val tapZone by rememberUpdatedState(tapZoneMode)
        val handleHitRadiusPx = with(density) { 24.dp.toPx() }
        val contentInsetPx = with(density) { horizontalInsetDp.toPx() }
        val pageTurnRenderer = pagedReaderPageTurnRenderer(pageTurnEffect, autoPageIntervalMillis)
        val manualRevealProgress = remember { Animatable(0f) }
        var manualRevealTarget by remember { mutableStateOf<PagedReaderController.ReaderPageFrame?>(null) }
        var manualRevealDirection by remember { mutableStateOf(1) }
        val manualRevealScope = rememberCoroutineScope()

        fun requestManualReveal(direction: Int) {
            if (direction == 0 || manualRevealTarget != null) return
            val target = controller.frameAt(direction)
            if (target == null) {
                // 跨章相邻页尚未预排时沿用既有直接加载路径；不能以静默降级替代 reveal。
                if (direction < 0) controller.prevPage() else controller.nextPage()
                onGesturePageTurn()
                return
            }
            manualRevealTarget = target
            manualRevealDirection = direction
            manualRevealScope.launch {
                manualRevealProgress.snapTo(0f)
                manualRevealProgress.animateTo(
                    1f,
                    tween(durationMillis = pageTurnSpeed.toInt().coerceIn(150, 800)),
                )
                selRange.value = null
                if (direction < 0) controller.prevPage() else controller.nextPage()
                onGesturePageTurn()
                manualRevealTarget = null
                manualRevealProgress.snapTo(0f)
            }
        }

        LaunchedEffect(turnRequest.intValue, pageTurnRenderer) {
            if (pageTurnRenderer != PagedReaderPageTurnRenderer.OUTER_REVEAL) return@LaunchedEffect
            val direction = turnRequest.intValue
            if (direction != 0) {
                turnRequest.intValue = 0
                requestManualReveal(direction)
            }
        }

        val gestures = Modifier
                .pointerInput(controller) {
                    // 选区把手拖拽：down 命中把手圆点附近才接管（消费后续事件），
                    // 其余点按/翻页手势不受影响。拖拽中实时钳制区间并同步外部待保存选区。
                    // 注意：不能用 awaitEachGesture —— 长按选句时 down 已被 tap 手势消费，
                    // awaitEachGesture 会因此永久退出；这里用常驻循环保持存活。
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val page = controller.currentPage ?: continue
                            val r = selRange.value ?: continue
                            if (r.isEmpty()) continue
                            val handles = PageSelection.calculateSelectionHandles(page, cfg, r)
                            if (!handles.isActive) continue
                            // 局部捕获可空把手：判空后直接命中检测，无需强解包
                            val leftHandle = handles.left
                            val rightHandle = handles.right
                        val side = when {
                            leftHandle != null &&
                                handleDist(down.position, Offset(leftHandle.x + contentInsetPx, leftHandle.y)) <= handleHitRadiusPx ->
                                PageSelection.HandleSide.LEFT
                            rightHandle != null &&
                                handleDist(down.position, Offset(rightHandle.x + contentInsetPx, rightHandle.y)) <= handleHitRadiusPx ->
                                PageSelection.HandleSide.RIGHT
                            else -> continue
                        }
                            var currentSel = r
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                change.consume()
                                val curPage = controller.currentPage ?: break
                                // 拖拽中重排/跨章：放弃拖拽（翻页路径会清选区，不残留）
                                if (curPage !== page) break
                                currentSel = PageSelection.adjustHandle(
                                    curPage, cfg, currentSel, side, change.position.x - contentInsetPx, change.position.y,
                                )
                                selRange.value = currentSel
                                onSelect(
                                    controller.chapterText.substring(currentSel.first, currentSel.last + 1),
                                    controller.currentChapterLocalDisplayToGlobalSource(currentSel.first),
                                    controller.currentChapterLocalDisplayToGlobalSource(currentSel.last + 1),
                                )
                                if (!change.pressed) break
                            }
                        }
                    }
                }
                .pointerInput(controller) {
                    detectTapGestures(
                        onLongPress = press@{ offset ->
                            // 长按选中一句，交给外部工具条做高亮/笔记/灵感。
                            // 不可用外层捕获的 page/chStart：那是冻结快照，
                            // 翻页后会按旧页几何选错句、跨章后偏移错位入库。
                            val curPage = controller.currentPage ?: return@press
                            val ch = PageSelection.offsetAt(curPage, cfg, offset.x - contentInsetPx, offset.y)
                            val sent = PageSelection.sentenceAround(controller.chapterText, ch)
                            if (!sent.isEmpty()) {
                                selRange.value = sent
                                onSelect(
                                    controller.chapterText.substring(sent.first, sent.last + 1),
                                    controller.currentChapterLocalDisplayToGlobalSource(sent.first),
                                    controller.currentChapterLocalDisplayToGlobalSource(sent.last + 1),
                                )
                            }
                        },
                        onTap = { offset ->
                            if (selRange.value != null) {
                                // 有选区时，任何点按先撤选区，不翻页
                                selRange.value = null
                                onSelect("", -1, -1)
                            } else {
                                when (
                                    resolveReaderTapAction(
                                        x = offset.x,
                                        y = offset.y,
                                        width = size.width.toFloat(),
                                        height = size.height.toFloat(),
                                        tapZoneMode = tapZone,
                                        canPrevious = controller.frameAt(-1) != null,
                                        canNext = controller.frameAt(1) != null,
                                    )
                                ) {
                                    ReaderTapAction.PREVIOUS_PAGE -> {
                                        stopAutoPaging()
                                        if (pageTurnRenderer == PagedReaderPageTurnRenderer.OUTER_REVEAL) {
                                            requestManualReveal(-1)
                                        } else {
                                            onGesturePageTurn()
                                            turnRequest.intValue = -1
                                        }
                                    }
                                    ReaderTapAction.NEXT_PAGE -> {
                                        stopAutoPaging()
                                        if (pageTurnRenderer == PagedReaderPageTurnRenderer.OUTER_REVEAL) {
                                            requestManualReveal(1)
                                        } else {
                                            onGesturePageTurn()
                                            turnRequest.intValue = 1
                                        }
                                    }
                                    ReaderTapAction.TOGGLE_CONTROLS -> {
                                        stopAutoPaging()
                                        onToggleControls()
                                    }
                                    ReaderTapAction.NONE -> Unit
                                }
                            }
                        },
                    )
                }
        val manualRevealDrag = if (pageTurnRenderer == PagedReaderPageTurnRenderer.OUTER_REVEAL) {
            Modifier.pointerInput(controller, manualRevealTarget) {
                var dragTotal = 0f
                var velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragTotal = 0f
                        velocityTracker = VelocityTracker()
                    },
                    onDragEnd = {
                        val velocityX = velocityTracker.calculateVelocity().x
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val direction = when {
                            dragTotal > width * 0.18f || velocityX > 400f -> -1
                            dragTotal < -width * 0.18f || velocityX < -400f -> 1
                            else -> 0
                        }
                        requestManualReveal(direction)
                    },
                ) { change, amount ->
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    dragTotal += amount
                    change.consume()
                }
            }
        } else {
            Modifier
        }

        // 读取 revision 让相邻章预排完成后触发重组，跨章拖动立即拿到邻帧。
        @Suppress("UNUSED_VARIABLE")
        val cacheRevision = controller.cacheRevision
        val frame = controller.frameAt(0) ?: return
        val previous = controller.frameAt(-1)
        val next = controller.frameAt(1)
        @Composable
        fun renderFrame(rendered: PagedReaderController.ReaderPageFrame, isCurrent: Boolean) {
            // 语义文本与绘制切片同源；非当前帧（动画过渡中的邻页）不暴露，
            // 避免 TalkBack 读到上一页 stale 文本或动画中间帧的重复正文。
            val accessibleText = remember(rendered.page, rendered.chapterText) {
                pageAccessibleText(rendered.page, rendered.chapterText)
            }
            Box(
                Modifier
                    .offset(x = horizontalInsetDp)
                    .width(contentWidthDp)
                    .fillMaxHeight(),
            ) {
                PageLayer(
                    page = rendered.page,
                    chapterText = rendered.chapterText,
                    cfg = cfg,
                    paint = paint,
                    headingPaint = headingPaint,
                    underlays = if (isCurrent) underlays else emptyList(),
                    handles = if (isCurrent) selectionHandles else null,
                    accessibleText = if (isCurrent) {
                        if (accessibleText.isEmpty()) "本页无正文" else accessibleText
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (pageTurnRenderer == PagedReaderPageTurnRenderer.OUTER_REVEAL) {
            // 手动 reveal：先保留当前页，再从上（上一页则从下）裁切显露已预排的目标页；
            // 动画结束才更新 controller，故不会把 reveal 转译成 PageTurner 的 none/fade/cover。
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "分页正文已就绪" }
                    .then(gestures)
                    .then(manualRevealDrag),
            ) {
                Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface).clipToBounds()) {
                    renderFrame(frame, true)
                }
                manualRevealTarget?.let { target ->
                    // 关键层级：裁切必须包住目标纸面和正文。若先把 readerPaperSurface 放在
                    // drawWithContent 外，它会作为不透明背景覆盖当前页，留下“上少量文字、下白纸”。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .drawWithContent {
                                val revealHeight = size.height * manualRevealProgress.value
                                if (manualRevealDirection > 0) {
                                    clipRect(bottom = revealHeight) { this@drawWithContent.drawContent() }
                                } else {
                                    clipRect(top = size.height - revealHeight) { this@drawWithContent.drawContent() }
                                }
                            },
                    ) {
                        Box(Modifier.fillMaxSize().readerPaperSurface(pageSurface)) {
                            renderFrame(target, false)
                        }
                    }
                }
            }
        } else {
            PageTurner(
                currentFrame = frame,
                previousFrame = previous,
                nextFrame = next,
                effect = if (autoPageIntervalMillis != null) "reveal" else pageTurnEffect,
                // 翻页速度由设置层注入；PageTurner 内部动画/位图预渲染逻辑保持不变。
                speed = pageTurnSpeed,
                turnRequest = turnRequest.intValue,
                onTurnRequestConsumed = { turnRequest.intValue = 0 },
                onPrevious = {
                    selRange.value = null
                    controller.prevPage()
                },
                onNext = {
                    selRange.value = null
                    controller.nextPage()
                },
                onPageTurned = onGesturePageTurn,
                revealProgress = revealProgress,
                revealDividerColor = MaterialTheme.colorScheme.primary,
                pageSurface = pageSurface,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "分页正文已就绪" }
                    .then(gestures),
            ) { rendered, isCurrent ->
                renderFrame(rendered, isCurrent)
            }
        }
    }
}

private fun handleDist(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)
