package com.creationreadingassistant.feature.reader.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.creationreadingassistant.feature.log.AppLog

/**
 * 程序化跳转（底栏进度条 / 目录 / 搜索命中 / 高亮回源）的选区失效通知（S07-F2.2）。
 *
 * 只走**选区通道**：一个空文本 `onSelect`，在 F2 已建立的接线里收敛到
 * `ReaderAction.ClearSelection`（完整五字段清除 + 收起色板行）。
 *
 * 为什么不复用 `onGesturePageTurn`：那条链在 VM 里是 `ReaderAction.PageTurn`，
 * 除了清选区还会**隐藏阅读页顶栏/底栏**。程序化跳转只是让旧选区失效，
 * 不等于用户执行了一次翻页手势；跨章跳转走 `LoadChapter` 也只清选区、不动 chrome。
 * 复用 `PageTurn` 会让「同章跳页收起菜单栏、跨章跳页不收起」这种不一致成为用户可见行为。
 *
 * 这里返回闭包而不是直接调用，便于 `PagedReaderControllerEffects` 把它当回调注入，
 * 也便于在 JVM 上断言它究竟走了哪条通道。
 */
internal fun programmaticJumpInvalidation(
    onSelect: (text: String, sourceStart: Int, sourceEnd: Int) -> Unit,
): () -> Unit = { onSelect("", -1, -1) }

/** 任务86-v1-A：只读诊断（不改变任何行为），与既有 CRA_CLAMP_DIAG 同一 tag。 */
private fun t86diag(event: String, vararg pairs: Pair<String, Any?>) {
    AppLog.debug(
        "CRA_CLAMP_DIAG",
        "event=$event " + pairs.joinToString(", ") { "${it.first}=${it.second}" },
    )
}

/**
 * 任务86-v1-A：分页位置上报裁决（纯函数，JVM 可直接回归）。
 *
 * 背景（85 实测）：退出阅读器时同一 bookId 的持久位置由 offset 1235（28.46%）回退到
 * 1034（23.83%），而期间没有任何用户翻页。`pagedAbsOffsetState` 只有一个写入方
 *（本文件的页变化回调，取 `controller.currentPageStartAbs`），因此只可能是 controller
 * 重排后把**页首**前移并上报。
 *
 * 裁决：只有「用户真的翻页（阅读锚点已不在当前页区间）」或「本会话首次落位」才上报。
 * 重排后锚点仍在页内时，页首前移不代表阅读位置变化，不得覆盖持久化位置。
 */
internal data class PagedPositionDecision(
    val anchorMoved: Boolean,
    val shouldReport: Boolean,
)

internal fun decidePagedPositionReport(
    everReported: Boolean,
    anchor: Int,
    range: IntRange?,
): PagedPositionDecision {
    val anchorMoved = range == null || anchor !in range
    return PagedPositionDecision(
        anchorMoved = anchorMoved,
        shouldReport = !everReported || anchorMoved,
    )
}

/**
 * 一次程序化跳转（进度条 / 目录 / 回源）是否会改变**可见页面身份**（S07-F2.1）。
 *
 * [currentPageRangeAbs] 是当前可见页覆盖的全书 source 区间，[targetOffset] 是跳转目标。
 * `PagedReaderController.open()` 是确定性的：它把目标落在「包含该偏移的那一页」上
 * （`chapterIndexFor(target)` → `globalSourceToLocalDisplay` → `pageIndexFor`）。
 * 因此当目标仍落在当前可见页区间内时，页号与章号都不会变，此时**不得**发选区失效事件
 * —— 否则「跳了个没动的位置」会把用户刚选的字清掉。
 *
 * 三个必须返回 false 的情形都落在这个判断里：
 * - 目标仍在当前可见页 → 身份不变；
 * - 还没有可见页（排版未就绪 / 源为空 / open 被取消）→ [currentPageRangeAbs] 为 null；
 * - 目标与当前页相同 → 同上。
 *
 * 调用点每次跳转只调用一次，所以「恰好发一次」由结构保证，不需要额外的去重状态。
 */
internal fun readerJumpClearsSelection(
    targetOffset: Int,
    currentPageRangeAbs: IntRange?,
): Boolean = currentPageRangeAbs != null && targetOffset !in currentPageRangeAbs

/**
 * 执行一次程序化跳转，并**至多一次**地通知选区失效（S07-F2.1）。
 *
 * 把「读当前页区间 → 判定 → open → 通知」四步收在一个函数里，好处是「恰好一次」
 * 由结构保证：通知只在 [readerJumpClearsSelection] 为 true 时发生，且本函数每次跳转
 * 只被调用一次（`LaunchedEffect(jumpRequest.value)` 消费后立刻把请求置 null）。
 *
 * [open] 与 [invalidateSelection] 都是注入项，因此本函数可在 JVM 上断言调用次数，
 * 不需要挂载 Compose 或真实分页控制器。
 *
 * @return 是否发出了选区失效通知。
 */
internal fun applyProgrammaticJump(
    targetOffset: Int,
    currentPageRangeAbs: IntRange?,
    open: (Int) -> Unit,
    invalidateSelection: () -> Unit,
): Boolean {
    val clearsSelection = readerJumpClearsSelection(targetOffset, currentPageRangeAbs)
    open(targetOffset)
    if (clearsSelection) {
        invalidateSelection()
    }
    return clearsSelection
}

/**
 * VM 清空选区后，宿主本地 `selRange` 的去留（S07-F2.1）。
 *
 * `selectionCleared` 派生自 VM 的 `selectedText.isBlank()`。VM 已清而宿主仍画着旧色块，
 * 用户看到的就仍是残留选区，所以这里必须丢弃；VM 未清时原样保留，不得抢先撤掉
 * 一个仍然有效的选区。
 */
internal fun shouldDropLocalSelection(
    selectionCleared: Boolean,
    local: IntRange?,
): IntRange? = if (selectionCleared) null else local

/**
 * 任务 #15 结构拆分：从 [PagedReaderHost] 抽出的控制器 effect 组
 * （锚点恢复 / 页变化上报 / 外部跳转 / 选区清除 / 外部翻页请求 / 自动翻页揭动画）。
 *
 * effect 体逐字搬运，key 与启动条件不变。[revealProgressState] 由调用方持有：
 * 本组 effect 写，页面层（PageTurner）读，与原主函数局部状态同一对象。
 */
@Suppress("LongParameterList")
@Composable
internal fun PagedReaderControllerEffects(
    controller: PagedReaderController,
    anchor: MutableIntState,
    pageInfo: MutableState<PageInfo>,
    onPositionChanged: (absOffset: Int, percent: Float) -> Unit,
    onPageIndexChanged: (bookId: String, chapterIndex: Int, pageIndex: Int, pageCount: Int, absStart: Int, absEnd: Int, percent: Float) -> Unit,
    bookId: String,
    jumpRequest: MutableState<Int?>,
    selectionCleared: Boolean,
    selRange: MutableState<IntRange?>,
    externalTurnRequest: MutableState<Int?>,
    turnRequest: MutableIntState,
    autoPageIntervalMillis: Long?,
    onAutoPagingFinished: () -> Unit,
    revealProgressState: MutableFloatState,
    /**
     * 选区失效通知（S07-F2.1 / F2.2）：可见页面身份确实变化后调用一次。
     *
     * 宿主应当传入 [programmaticJumpInvalidation]（走选区通道 → `ClearSelection`），
     * **不要**传翻页手势回调 —— 那会在清选区之外顺带隐藏顶栏/底栏，
     * 而程序化跳转并不等于一次翻页手势。本层不接触任何选区字段清单。
     */
    onSelectionInvalidated: () -> Unit = {},
) {
    val finishAutoPaging by rememberUpdatedState(onAutoPagingFinished)
    // 上报回调必须在每次重组后取最新：LaunchedEffect(controller) 只启动一次，
    // 若直接捕获 onPositionChanged 会冻结首次 callbacks（首次 nav 的旧偏移），
    // 导致翻页节流保存一直落库旧进度（进度看似保存了实为旧值）。
    val latestOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val latestOnPageIndexChanged by rememberUpdatedState(onPageIndexChanged)
    var revealProgress by revealProgressState

    // 新 controller（首开 / 配置变化重建）→ 回到锚点所在句
    LaunchedEffect(controller) {
        t86diag("paged_controller_open", "target" to anchor.intValue)
        controller.open(anchor.intValue)
    }

    // 页变化 → 上报进度；锚点只在「用户真的离开了锚点所在页」时才移动。
    // 改字号/转屏引发的重排会让页首前移，若无条件把锚点改成新页首，
    // 连续几次重排锚点就会一路往回漂（每次退小半页）。
    // 任务86-v1-A：本宿主会话是否已上报过位置。remember 无 key，跨 controller 重建保留，
    // 用来区分「本会话首次落位」与「重建后的过渡排版」。
    val positionEverReported = remember { mutableStateOf(false) }
    LaunchedEffect(controller) {
        snapshotFlow { Triple(controller.chapterIndex, controller.pageIndex, controller.pageCount) }
            .collect { (_, _, count) ->
                if (count > 0 && !controller.isLayingOut) {
                    val range = controller.currentPageRangeAbs
                    val decision = decidePagedPositionReport(
                        everReported = positionEverReported.value,
                        anchor = anchor.intValue,
                        range = range,
                    )
                    if (decision.anchorMoved && range != null) {
                        anchor.intValue = range.first
                    }
                    // 任务86-v1-A：持久化位置只在「用户真的翻页（锚点离开旧页）」或
                    // 「本会话首次落位」时更新。退出/重排引发的过渡排版会把页首前移
                    //（85 实测 1235→1034），不得用它覆盖用户实际阅读位置。
                    t86diag(
                        "paged_position_emit",
                        "chapter" to controller.chapterIndex,
                        "pageIndex" to controller.pageIndex,
                        "pageCount" to controller.pageCount,
                        "pageStartAbs" to controller.currentPageStartAbs,
                        "anchor" to anchor.intValue,
                        "anchorMoved" to decision.anchorMoved,
                        "reported" to decision.shouldReport,
                        "percent" to controller.progressPercent,
                    )
                    if (decision.shouldReport) {
                        positionEverReported.value = true
                        latestOnPositionChanged(controller.currentPageStartAbs, controller.progressPercent)
                    }
                    latestOnPageIndexChanged(
                        bookId,
                        controller.chapterIndex,
                        controller.pageIndex,
                        controller.pageCount,
                        controller.currentPageStartAbs,
                        controller.currentPageRangeAbs?.last ?: controller.currentPageStartAbs,
                        controller.progressPercent,
                    )
                    pageInfo.value = PageInfo(
                        chapterIndex = controller.chapterIndex,
                        pageIndex = controller.pageIndex,
                        pageCount = controller.pageCount,
                        progressPercent = controller.progressPercent,
                    )
                }
            }
    }

    // 外部跳转（进度条 / 目录 / 回源）
    LaunchedEffect(jumpRequest.value) {
        val j = jumpRequest.value ?: return@LaunchedEffect
        // S07-F2.1：先读**当前可见页区间**再跳。open 是确定性的（把目标落在包含该偏移的
        // 那一页上），所以「目标是否落在当前页内」同步决定了这次跳转会不会改变页面身份；
        // 不必等 open 的异步排版落定。身份确实变化才发一次选区失效事件。
        applyProgrammaticJump(
            targetOffset = j,
            currentPageRangeAbs = controller.currentPageRangeAbs,
            open = controller::open,
            invalidateSelection = onSelectionInvalidated,
        )
        jumpRequest.value = null
    }

    // 选区（章内偏移区间）。翻页/外部清除时撤掉。
    LaunchedEffect(selectionCleared) { selRange.value = shouldDropLocalSelection(selectionCleared, selRange.value) }
    LaunchedEffect(externalTurnRequest.value) {
        val direction = externalTurnRequest.value ?: return@LaunchedEffect
        turnRequest.intValue = direction
        externalTurnRequest.value = null
    }

    // 自动翻页揭动画进度（0..1）。revealer 存活跨 effect 重启：
    // 暂停（interval→null）再恢复（interval 恢复）时从原进度续跑（冻结续跑）。
    val revealer = remember { AutoRevealProgress() }
    LaunchedEffect(controller, autoPageIntervalMillis) {
        val interval = autoPageIntervalMillis ?: return@LaunchedEffect
        if (interval <= 0L) return@LaunchedEffect
        AppLog.debug("AutoPagingDebug", "effect start: interval=$interval")
        var previousFrame = withFrameNanos { it }
        // 手动翻页 / 跨章 / 自动提交后（页或章变化）从新页从头揭
        var lastSeen = controller.chapterIndex to controller.pageIndex
        var layoutCount = 0
        while (true) {
            val frame = withFrameNanos { it }
            val now = controller.chapterIndex to controller.pageIndex
            if (now != lastSeen) {
                revealer.reset()
                lastSeen = now
            }
            // 排版中不推进揭动画（当前页尚未稳定），帧时间交给 250ms 上限兜底
            if (controller.isLayingOut) {
                layoutCount++
                revealProgress = revealer.value
                continue
            }
            val elapsed = frame - previousFrame
            previousFrame = frame
            if (revealer.advance(elapsed, interval)) {
                AppLog.debug("AutoPagingDebug", "turn: interval=$interval elapsed=$elapsed layoutSkip=$layoutCount progress=${revealer.value}")
                layoutCount = 0
                if (!controller.canGoNext) {
                    finishAutoPaging()
                    break
                } else if (controller.frameAt(1) != null) {
                    turnRequest.intValue = 1
                } else {
                    // 相邻章尚未预排完成时直接发起加载，不让自动翻页空等一整个周期。
                    controller.nextPage()
                }
            }
            revealProgress = revealer.value
        }
    }
}
