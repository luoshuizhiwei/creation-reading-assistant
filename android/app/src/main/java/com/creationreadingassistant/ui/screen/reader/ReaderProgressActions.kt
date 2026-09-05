package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
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
) {
    val maxIndex = when {
        epubBook != null -> epubBook.chapters.lastIndex
        markdownDocument != null -> markdownDocument.chapters.lastIndex
        else -> return
    }
    val clamped = i.coerceIn(0, maxIndex)
    if (pagerEngineOn && jumpToStart) {
        pagedJumpRequest.value = chapterStartOffsets.getOrElse(clamped) { 0 }
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
        val totalChars = bookIndex?.totalChars?.coerceAtLeast(1) ?: 1
        val percent = (globalOffset * 100f / totalChars).coerceIn(0f, 100f)
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
        val absoluteOffset = if (pagerEngineOn && pagedAbsOffset >= 0) {
            pagedAbsOffset
        } else {
            visiblePlainOffset
        }.coerceAtLeast(0)
        val totalChars = when {
            markdownDocument != null -> markdownDocument.totalChars
            txtStreamingDocument != null -> txtStreamingDocument.totalChars
            else -> plainContent.length
        }.coerceAtLeast(1)
        val percent = (absoluteOffset * 100f / totalChars).coerceIn(0f, 100f)
        onAction(
            ReaderAction.SaveProgress(
                ReadingProgressEntity(
                    book_id = bid,
                    progress_percent = percent,
                    completion_state = if (percent >= 99.9f) "finished" else "reading",
                    current_location_json = LocatorBuilder.progressJson(
                        legacyOffset = absoluteOffset,
                        chapterIndex = 0,
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
) {
    val percent = p.coerceIn(0f, 100f)
    if (epubBook != null) {
        val total = bookIndex?.totalChars?.coerceAtLeast(0) ?: 0
        val targetOffset = (percent / 100f * total).toInt()
        if (pagerEngineOn) {
            if (total > 0) pagedJumpRequest.value = targetOffset
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
