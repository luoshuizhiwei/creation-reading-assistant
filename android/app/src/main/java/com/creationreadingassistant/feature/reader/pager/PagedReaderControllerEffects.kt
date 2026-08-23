package com.creationreadingassistant.feature.reader.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.creationreadingassistant.feature.log.AppLog

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
) {
    val finishAutoPaging by rememberUpdatedState(onAutoPagingFinished)
    // 上报回调必须在每次重组后取最新：LaunchedEffect(controller) 只启动一次，
    // 若直接捕获 onPositionChanged 会冻结首次 callbacks（首次 nav 的旧偏移），
    // 导致翻页节流保存一直落库旧进度（进度看似保存了实为旧值）。
    val latestOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val latestOnPageIndexChanged by rememberUpdatedState(onPageIndexChanged)
    var revealProgress by revealProgressState

    // 新 controller（首开 / 配置变化重建）→ 回到锚点所在句
    LaunchedEffect(controller) { controller.open(anchor.intValue) }

    // 页变化 → 上报进度；锚点只在「用户真的离开了锚点所在页」时才移动。
    // 改字号/转屏引发的重排会让页首前移，若无条件把锚点改成新页首，
    // 连续几次重排锚点就会一路往回漂（每次退小半页）。
    LaunchedEffect(controller) {
        snapshotFlow { Triple(controller.chapterIndex, controller.pageIndex, controller.pageCount) }
            .collect { (_, _, count) ->
                if (count > 0 && !controller.isLayingOut) {
                    val range = controller.currentPageRangeAbs
                    if (range != null && anchor.intValue !in range) {
                        anchor.intValue = range.first
                    }
                    latestOnPositionChanged(controller.currentPageStartAbs, controller.progressPercent)
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

    // 外部跳转（进度条 / 目录）
    LaunchedEffect(jumpRequest.value) {
        val j = jumpRequest.value ?: return@LaunchedEffect
        controller.open(j)
        jumpRequest.value = null
    }

    // 选区（章内偏移区间）。翻页/外部清除时撤掉。
    LaunchedEffect(selectionCleared) { if (selectionCleared) selRange.value = null }
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
