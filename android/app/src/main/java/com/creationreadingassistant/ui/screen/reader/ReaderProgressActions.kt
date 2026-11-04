package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.EpubDocument
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.EpubProgressCalibration
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.ui.screen.reader.tts.TtsEngineHost
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 阅读进度与定位动作（从 ReaderActions.kt 按领域拆出）：
 * 章节跳转、TXT/Markdown/EPUB 偏移跳转、进度持久化、百分比 seek。
 *
 * 行为保真原则与 State-holder 约定见 ReaderActions.kt 头注。
 */

/** 依据当前选区生成 locator_json（T1）。纯函数，无副作用。 */
internal fun computeLocatorJson(
    epubBook: EpubBook?,
    selectedGlobalOffset: Int,
    chapterStartOffsets: List<Int>,
    selectedText: String,
    selectedRangeStart: Int,
): String? = when {
    epubBook != null && selectedGlobalOffset >= 0 ->
        LocatorBuilder.forEpub(selectedGlobalOffset, chapterStartOffsets, selectedText)
    epubBook == null && selectedRangeStart >= 0 ->
        LocatorBuilder.forPlain(selectedRangeStart, selectedText)
    else -> null
}

/**
 * 依据**消费者快照**生成 locator_json（S07-F2.4）。
 *
 * 与 [computeLocatorJson] 的区别只在数据来源：这里文字与坐标都取自同一个不可变快照，
 * 因此正文活动选区被清理后仍能生成正确 locator。**禁止**混搭
 * （文字取快照、坐标取实时状态）—— 那会写出文字与位置互相矛盾的记录。
 */
internal fun computeLocatorJsonForSnapshot(
    epubBook: EpubBook?,
    chapterStartOffsets: List<Int>,
    snapshot: ReaderSelectionSnapshot?,
): String? = snapshot?.let {
    computeLocatorJson(
        epubBook = epubBook,
        selectedGlobalOffset = it.globalOffset,
        chapterStartOffsets = chapterStartOffsets,
        selectedText = it.text,
        selectedRangeStart = it.rangeStart,
    )
}

/**
 * 跳转到指定章节。pagerEngineOn 时走翻页定位，否则只更新 chapterIndex + LoadChapter。
 * chapterIndex 写入已转换的 [chapterIndexState]。
 *
 * [jumpToStart] 仅对翻页引擎生效：主动跳转（目录点击）传 true 落在章首；
 * **翻页跨章的被动章号同步必须传 false**——阅读器已把页面停在正确位置
 * （上一章末页 / 下一章首页），若再写 jumpRequest 会被 open(章首) 拽回章首
 * （真机反馈：章首往前翻直接跨章回到上一章第一页）。
 */
internal fun goToChapter(
    i: Int,
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument?,
    pagerEngineOn: Boolean,
    chapterStartOffsets: List<Int>,
    pagedJumpRequest: MutableState<Int?>,
    chapterIndexState: MutableIntState,
    tts: TtsEngineHost,
    onAction: (ReaderAction) -> Unit,
    bid: String,
    jumpToStart: Boolean = true,
    /**
     * M-BUG-08：为 true 时把分页落点定在目标章的**末尾**而不是章首。
     *
     * 用于「上一章」——对齐 md3 `ReadBook.moveToPrevChapter(toLast = true)` 的语义
     * （`durChapterPos = prevTextChapter.lastReadLength`，即回到上一章的最后位置）。
     * 末尾取「下一章章首 - 1」，因为 chapterStartOffsets 是各章起始偏移。
     */
    atChapterEnd: Boolean = false,
) {
    val maxIndex = when {
        epubBook != null -> epubBook.chapters.lastIndex
        markdownDocument != null -> markdownDocument.chapters.lastIndex
        else -> return
    }
    val clamped = i.coerceIn(0, maxIndex)
    if (pagerEngineOn && jumpToStart) {
        val chapterStart = chapterStartOffsets.getOrElse(clamped) { 0 }
        pagedJumpRequest.value = if (atChapterEnd) {
            // 目标章末尾 = 其下一章章首 - 1；已是最后一章时退化为该章章首。
            val nextStart = chapterStartOffsets.getOrNull(clamped + 1)
            if (nextStart != null) (nextStart - 1).coerceAtLeast(0) else chapterStart
        } else {
            chapterStart
        }
    }
    chapterIndexState.value = clamped
    tts.stop()
    // R6：章节块加载 + 进度保存统一由 ViewModel 处理。
    // persistProgress：被动跨章同步（jumpToStart=false，分页引擎已停在正确页位，
    // 可能是上一章末页）不得让 VM 写「章首」进度覆盖真实位置（merge 无条件采信
    // incoming locator；否则退出重进回到前一章开头）。分页模式的精确进度由
    // 防抖保存落库。
    onAction(ReaderAction.LoadChapter(bid, clamped, persistProgress = jumpToStart || !pagerEngineOn))
}

/** TXT 跳转统一入口：分页引擎开着走翻页定位，否则滚动列表。两条路都以全书字符偏移为准。 */
internal fun jumpToPlainOffset(
    offset: Int,
    pagerEngineOn: Boolean,
    readingUnits: List<ReadingUnit>,
    scope: CoroutineScope,
    plainListState: LazyListState,
    pagedJumpRequest: MutableState<Int?>,
) {
    if (pagerEngineOn) {
        pagedJumpRequest.value = offset
    } else if (readingUnits.isNotEmpty()) {
        scope.launch { plainListState.scrollToItem(unitIndexForOffset(readingUnits, offset)) }
    }
}

/**
 * 纯 TXT 滚动模式的「上一章 / 下一章」目标全书偏移（S07-F2）。
 *
 * 这条路径走 [jumpToPlainOffset]，**不经过** `LoadChapter`，因此调用方必须自己清选区。
 * 该函数把「是否有可跳目标」显式表达为 null，调用方据此决定是否真的发生了章跳转 ——
 * 只有真的跳了才清选区，不在首章/末章/目录未识别时把「什么都没发生」伪装成一次导航。
 *
 * @param direction -1 = 上一章（落到上一章末尾），+1 = 下一章（落到下一章章首）。
 */
internal fun txtScrollChapterTargetOffset(
    direction: Int,
    txtChapters: List<DocChapter>,
    txtChapterIndex: Int,
): Int? = when {
    // 上一章末尾 = 本章章首 - 1；章首不可得即无可跳目标。
    direction < 0 -> txtChapters.getOrNull(txtChapterIndex)?.startOffset?.let { (it - 1).coerceAtLeast(0) }
    direction > 0 -> txtChapters.getOrNull(txtChapterIndex + 1)?.startOffset
    else -> null
}

/**
 * 进度持久化触发点可能来自分页回调、生命周期或 DisposableEffect。它们持有的闭包可能早于
 * 最近一次重组，因此这里必须在真正执行保存时读取 State-holder，而不能捕获组合期的 Int 快照。
 */
internal data class ReaderProgressSnapshot(
    val pagedAbsOffset: Int,
    val chapterIndex: Int,
)

internal fun currentReaderProgressSnapshot(
    pagedAbsOffsetState: MutableIntState,
    chapterIndexState: MutableIntState,
): ReaderProgressSnapshot = ReaderProgressSnapshot(
    pagedAbsOffset = pagedAbsOffsetState.intValue,
    chapterIndex = chapterIndexState.intValue,
)

/**
 * 持久化当前阅读进度。EPUB 走 SaveEpubProgress，TXT/Markdown 走 SaveProgress。
 * 逐字搬运自 ReaderScreen，不改任何偏移计算或落库字段。
 */
@Suppress("LongParameterList")
internal fun persistCurrentProgress(
    bid: String,
    loadedBook: ReaderLoadedBook?,
    error: String?,
    pendingInitialPosition: Boolean,
    epubBook: EpubBook?,
    pagerEngineOn: Boolean,
    pagedAbsOffset: Int,
    pagedSource: PagedChapterSource?,
    chapterIndex: Int,
    epubListState: LazyListState,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    chapterBlocks: List<DocBlock>,
    bookIndex: BookIndex?,
    chapterStartOffsets: List<Int>,
    visiblePlainOffset: Int,
    markdownDocument: ReaderDocument?,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    epubDocument: EpubDocument? = null,
    plainListState: LazyListState? = null,
    readingUnits: List<ReadingUnit> = emptyList(),
    onAction: (ReaderAction) -> Unit,
) {
    if (
        bid.isBlank() ||
        loadedBook == null ||
        error != null ||
        !canPersistLegacyScrollPosition(pendingInitialPosition)
    ) return
    if (epubBook != null) {
        val chapterOffset = if (pagerEngineOn && pagedAbsOffset >= 0) {
            val currentChapter = pagedSource?.chapterIndexFor(pagedAbsOffset) ?: chapterIndex
            pagedAbsOffset - (pagedSource?.chapterStartAbs(currentChapter) ?: chapterBase)
        } else {
            val itemIndex = epubListState.firstVisibleItemIndex
            val blockStart = blockGlobalOffsets.getOrElse(itemIndex) { chapterBase }
            val baseOffset = (blockStart - chapterBase).coerceAtLeast(0)
            val blockLength = (chapterBlocks.getOrNull(itemIndex) as? DocBlock.Text)
                ?.text
                ?.length
                ?: 0
            val visibleItem = epubListState.layoutInfo.visibleItemsInfo.firstOrNull()
            val fraction = if (visibleItem != null && visibleItem.size > 0) {
                epubListState.firstVisibleItemScrollOffset.toFloat() / visibleItem.size
            } else {
                0f
            }
            baseOffset + (blockLength * fraction).toInt()
        }.coerceAtLeast(0)
        val currentChapter = if (pagerEngineOn && pagedAbsOffset >= 0) {
            pagedSource?.chapterIndexFor(pagedAbsOffset) ?: chapterIndex
        } else {
            chapterIndex
        }
        val globalOffset = chapterStartOffsets.getOrElse(currentChapter) { 0 } + chapterOffset
        // P5 进度校准：百分比走真实字符口径（逐章实测回填），locator/absoluteOffset
        // 仍保持估算混合空间不变，历史定位数据不受影响。无实测数据时与旧公式逐值一致。
        val percent = epubDocument?.let { doc ->
            EpubProgressCalibration.percentFor(
                model = EpubProgressCalibration.Model(
                    estimatedLengths = doc.estimatedChapterLengths(),
                    measuredRealChars = doc.measuredChapterCharsSnapshot(),
                ),
                chapterIndex = currentChapter,
                offsetInChapter = chapterOffset,
            )
        } ?: run {
            val totalChars = bookIndex?.totalChars?.coerceAtLeast(1) ?: 1
            (globalOffset * 100f / totalChars).coerceIn(0f, 100f)
        }
        onAction(
            ReaderAction.SaveEpubProgress(
                bookId = bid,
                chapterIndex = currentChapter,
                percent = percent,
                offsetInChapter = chapterOffset,
                absoluteOffset = globalOffset,
            )
        )
    } else {
        val livePlainOffset = if (plainListState != null && readingUnits.isNotEmpty()) {
            if (!plainListState.canScrollForward && txtStreamingDocument != null) {
                txtStreamingDocument.totalChars
            } else {
                currentPlainListOffset(plainListState, readingUnits, fallbackOffset = visiblePlainOffset)
            }
        } else {
            visiblePlainOffset
        }
        val absoluteOffset = if (pagerEngineOn && pagedAbsOffset >= 0) {
            pagedAbsOffset
        } else {
            livePlainOffset
        }.coerceAtLeast(0)
        val totalChars = when {
            markdownDocument != null -> markdownDocument.totalChars
            txtStreamingDocument != null -> txtStreamingDocument.totalChars
            else -> plainContent.length
        }.coerceAtLeast(1)
        val percent = (absoluteOffset * 100f / totalChars).coerceIn(0f, 100f)
        // TXT/Markdown 也存真实章号：书架进度卡显示「第 N 章」读的就是这个 ci。
        // 此前恒写 0，书架永远显示「第1章」（用户 2026-09-18 反馈）。退出保存是离开阅读器
        // 前的最后一次落库，漏了它前面的防抖保存就白做。
        // 章号来源优先级：分页 source 反推（TXT/EPUB 同款二分）→ 全书章起点反推 →
        // 会话当前章号。不能只信 chapterStartOffsets：小 TXT 分页下 bookIndex 为 null，
        // 它是空列表。
        val chapterForLocator = pagedSource
            ?.takeIf { pagerEngineOn && pagedAbsOffset >= 0 }
            ?.runCatching { chapterIndexFor(absoluteOffset) }
            ?.getOrNull()
            ?: chapterStartOffsets
                .takeIf { it.isNotEmpty() }
                ?.let { com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping
                    .toChapterOffset(absoluteOffset, it).first }
            ?: chapterIndex.coerceAtLeast(0)
        onAction(
            ReaderAction.SaveProgress(
                ReadingProgressEntity(
                    book_id = bid,
                    progress_percent = percent,
                    completion_state = if (percent >= 99.9f) "finished" else "reading",
                    current_location_json = LocatorBuilder.progressJson(
                        legacyOffset = absoluteOffset,
                        chapterIndex = chapterForLocator,
                        charOffset = absoluteOffset,
                        space = if (markdownDocument != null) "canonical" else null,
                    ),
                    updated_at = nowIso(),
                )
            )
        )
    }
}

/** R6：进度滑块跳转（TXT 定位到百分比；EPUB 跳到对应章节；分页引擎按全书偏移精确定位）。 */
@Suppress("LongParameterList")
internal fun seekToPercent(
    p: Float,
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument? = null,
    pagerEngineOn: Boolean,
    bookIndex: BookIndex?,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    pagedJumpRequest: MutableState<Int?>,
    goToChapter: (Int) -> Unit,
    jumpToPlainOffset: (Int) -> Unit,
    jumpToMarkdownOffset: (Int) -> Unit = {},
    epubDocument: EpubDocument? = null,
) {
    val percent = p.coerceIn(0f, 100f)
    if (epubBook != null) {
        val total = bookIndex?.totalChars?.coerceAtLeast(0) ?: 0
        val targetOffset = (percent / 100f * total).toInt()
        if (pagerEngineOn) {
            // P5 校准：百分比是真实字符口径，换算回分页混合空间（估算章基址 + 章内真实偏移）；
            // 无实测数据时校准比 r=1，与旧 est 空间公式逐值一致。
            val calibrated = epubDocument?.let { doc ->
                EpubProgressCalibration.mixedGlobalOffsetForPercent(
                    model = EpubProgressCalibration.Model(
                        estimatedLengths = doc.estimatedChapterLengths(),
                        measuredRealChars = doc.measuredChapterCharsSnapshot(),
                    ),
                    percent = percent,
                )
            }
            if (calibrated != null) {
                pagedJumpRequest.value = calibrated
            } else if (total > 0) {
                pagedJumpRequest.value = targetOffset
            }
        } else {
            val sz = epubBook.chapters.size
            val targetChapter = chapterIndexForBookOffset(
                chapterStartOffsets = bookIndex?.chapterStartOffsets.orEmpty(),
                targetOffset = targetOffset,
                totalChars = total,
            ) ?: (percent / 100f * sz).toInt()
            if (sz > 0) goToChapter(targetChapter.coerceIn(0, sz - 1))
        }
    } else if (markdownDocument != null) {
        val targetOffset = (percent / 100f * markdownDocument.totalChars.coerceAtLeast(0)).toInt()
        if (pagerEngineOn) pagedJumpRequest.value = targetOffset else jumpToMarkdownOffset(targetOffset)
    } else if (plainContent.isNotEmpty() || txtStreamingDocument != null) {
        val totalLen = txtStreamingDocument?.totalChars ?: plainContent.length
        jumpToPlainOffset((percent / 100f * totalLen).toInt())
    }
}

/** 章节内进度跳转：将章节内百分比转换为全书绝对偏移后定位。 */
@Suppress("LongParameterList")
internal fun seekToChapterPercent(
    p: Float,
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument? = null,
    chapterStartOffsets: List<Int>,
    chapterIndex: Int,
    contentText: String,
    pagerEngineOn: Boolean,
    txtChapters: List<com.creationreadingassistant.feature.reader.doc.DocChapter>,
    txtChapterIndex: Int,
    plainContent: String,
    pagedJumpRequest: MutableState<Int?>,
    jumpToPlainOffset: (Int) -> Unit,
    jumpToEpubOffset: (Int) -> Unit = {},
    jumpToMarkdownOffset: (Int) -> Unit = {},
) {
    val clamped = p.coerceIn(0f, 100f)
    if (epubBook != null) {
        val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
        val chLen = contentText.length.coerceAtLeast(1)
        val absOffset = base + (clamped / 100f * chLen).toInt()
        if (pagerEngineOn) {
            pagedJumpRequest.value = absOffset
        } else {
            jumpToEpubOffset(absOffset)
        }
    } else if (markdownDocument != null) {
        val chapter = markdownDocument.chapters.getOrNull(chapterIndex)
        val base = chapter?.startOffset ?: 0
        val chapterLength = chapter?.charCount?.coerceAtLeast(1) ?: 1
        val absOffset = base + (clamped / 100f * chapterLength).toInt()
        if (pagerEngineOn) pagedJumpRequest.value = absOffset else jumpToMarkdownOffset(absOffset)
    } else {
        val ch = txtChapters.getOrNull(txtChapterIndex)
        val base = ch?.startOffset ?: 0
        val chLen = (ch?.charCount ?: plainContent.length).coerceAtLeast(1)
        val absOffset = base + (clamped / 100f * chLen).toInt()
        jumpToPlainOffset(absOffset)
    }
}
