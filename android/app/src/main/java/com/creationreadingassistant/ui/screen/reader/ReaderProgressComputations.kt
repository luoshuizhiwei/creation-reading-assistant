package com.creationreadingassistant.ui.screen.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 滚动列表位置快照。把 LazyListState 上高频变化的字段打包成 derivedStateOf，
 * 避免每次滚动像素变化都触发整个 ReaderScreen 的重组。
 */
internal data class PlainListSnapshot(
    val firstVisibleUnit: ReadingUnit?,
    val firstVisibleItemOffset: Int,
    val firstVisibleItemSize: Int,
    val reachedEnd: Boolean,
    val firstVisibleItemIndex: Int,
)

/**
 * Phase 5 结构拆分：[rememberReaderProgress] 的返回值。
 *
 * 仅暴露下游 ReaderScreen 仍需消费的派生值；中间态（plainListSnapshot /
 * streamingContentText / plainPercent / epubPercent / 阅读统计中间量）保留在
 * [rememberReaderProgress] 内部，不外泄，避免主函数重新持有这些值。
 */
internal data class ReaderProgressState(
    val plainListState: LazyListState,
    val epubListState: LazyListState,
    val visiblePlainOffset: Int,
    val contentText: String,
    val txtChapterIndex: Int,
    val chapterFade: Animatable<Float, AnimationVector1D>,
    val chapterFadeKey: Int,
    val currentChapterTitle: String,
    val progressPercent: Float,
    val chapterProgress: Float,
    val documentWordCount: Int,
    val savedBookReadingMs: Long,
    val readerSpeed: Int,
    val estimatedRemainingMs: Long,
    val bookmarksCount: Int,
    val inspirationsCount: Int,
)

/**
 * Phase 5 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 抽出的
 * 「进度计算」逻辑（原 L535-682）。
 *
 * 包含：epubPercent / plainListSnapshot / visiblePlainOffset / contentText /
 * txtChapterIndex / chapterFade / currentChapterTitle / plainPercent /
 * progressPercent / chapterProgress / 阅读统计派生值。
 *
 * ## 行为保真
 *
 * 每个 `remember` / `derivedStateOf` / `LaunchedEffect` 逐字搬运自 ReaderScreen，
 * 不改任何 key、防抖参数与帧参数。组合生命周期一致（同一 Composition 内的子
 * composable），derivedStateOf 与 LaunchedEffect 的重启语义与原内联写法等价。
 *
 * `streamingContentText` 作为内部中间态，仅被 `contentText` 的 remember 块读取，
 * 不暴露到 [ReaderProgressState]；外部只消费最终 `contentText`。
 *
 * @param inspirationsCount 上游直接传 `inspirations.size`，避免引入 InspirationEntity 依赖
 */
@Suppress("LongParameterList")
@Composable
internal fun rememberReaderProgress(
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument?,
    chapterIndex: Int,
    readingUnits: List<ReadingUnit>,
    pagerEngineOn: Boolean,
    pagedAbsOffset: Int,
    pagedPercent: Float,
    plainContent: String,
    txtStreamingDocument: PlainTextDocument?,
    chapterBlocks: List<DocBlock>,
    txtChapters: List<DocChapter>,
    chapterStartOffsets: List<Int>,
    sessions: List<ReadingSessionEntity>,
    bookSize: Int,
    savedTotalReadingMs: Long,
    sessionStartProgress: Float,
    activeReadingMs: Long,
    notes: List<NoteEntity>,
    inspirationsCount: Int,
    bookIndex: BookIndex?,
): ReaderProgressState {
    // 进度计算
    val epubPercent = if (epubBook != null) {
        val size = epubBook!!.chapters.size
        if (size <= 1) if (chapterIndex == 0) 100f else 0f else (chapterIndex.toFloat() / (size - 1)) * 100f
    } else 0f

    val plainListState = rememberLazyListState()

    // 把 LazyListState 上高频变化的字段包进 derivedStateOf，避免每次滚动触发大范围重组。
    // 只有当派生对象字段变化时，下游消费方（visiblePlainOffset/plainPercent）才重算。
    val plainListSnapshot by remember(plainListState, readingUnits) {
        derivedStateOf {
            val firstItem = plainListState.layoutInfo.visibleItemsInfo.firstOrNull()
            val idx = plainListState.firstVisibleItemIndex
            PlainListSnapshot(
                firstVisibleUnit = readingUnits.getOrNull(idx),
                firstVisibleItemOffset = firstItem?.offset ?: 0,
                firstVisibleItemSize = firstItem?.size ?: 0,
                reachedEnd = !plainListState.canScrollForward && idx > 0,
                firstVisibleItemIndex = idx,
            )
        }
    }
    val firstPlainItemSize = plainListSnapshot.firstVisibleItemSize
    val firstPlainFraction = if (firstPlainItemSize > 0) {
        (-plainListSnapshot.firstVisibleItemOffset).coerceAtLeast(0).toFloat() / firstPlainItemSize
    } else {
        0f
    }
    val firstPlainUnit = plainListSnapshot.firstVisibleUnit
    val visiblePlainOffset = when {
        // 分页引擎开启时，位置的真源是引擎上报的页首偏移，滚动列表根本不在屏上
        pagerEngineOn && pagedAbsOffset >= 0 -> pagedAbsOffset
        firstPlainUnit != null ->
            firstPlainUnit.charStart + (firstPlainUnit.charCount * firstPlainFraction).toInt()
        else -> 0
    }

    // 正文文本（TTS / 选择用）：EPUB 取当前章节，TXT 流式取当前可见窗口，小文件取全文
    // 必须在 plainListState / pagerEngineOn / readingUnits / chapterStartOffsets / visiblePlainOffset 之后定义
    val streamingContentTextState = remember(txtStreamingDocument) { mutableStateOf("") }
    var streamingContentText by streamingContentTextState
    val streamingWindowAnchor = (visiblePlainOffset / 2_000) * 2_000
    LaunchedEffect(txtStreamingDocument, streamingWindowAnchor) {
        val document = txtStreamingDocument ?: return@LaunchedEffect
        streamingContentText = withContext(Dispatchers.IO) {
            // 流式模式：磁盘读取不得发生在组合线程。
            document.readWindowAround(streamingWindowAnchor, 0, 8000)
        }
    }
    val contentText = remember(
        epubBook,
        markdownDocument,
        chapterBlocks,
        txtStreamingDocument,
        streamingContentText,
        plainContent,
    ) {
        when {
            epubBook != null ->
                chapterBlocks.filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }
            markdownDocument != null ->
                chapterBlocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()?.chapter?.canonicalText ?: ""
            txtStreamingDocument != null -> streamingContentText
            else -> plainContent
        }
    }

    val epubListState = rememberLazyListState()
    // TXT 当前所在章：按当前可见偏移反查。必须放在 visiblePlainOffset 之后。
    val txtChapterIndex = if (txtChapters.isEmpty()) {
        0
    } else {
        txtChapters.indexOfLast { it.startOffset <= visiblePlainOffset }.coerceAtLeast(0)
    }
    // C6：章节淡入动画由 ReaderRuntimeEffects 驱动；chapterFade/chapterFadeKey 跨 effect 共享
    val chapterFade = remember { Animatable(1f) }
    val chapterFadeKey = if (epubBook != null) chapterIndex else txtChapterIndex
    // 顶栏副行与 TTS、书签都用它。TXT 此前恒为空串只能显示「正文」，
    // 现在有章节识别了就跟着滚动位置走。
    val currentChapterTitle = when {
        epubBook != null -> epubBook!!.chapters.getOrNull(chapterIndex)?.title ?: ""
        markdownDocument != null -> markdownDocument.chapters.getOrNull(chapterIndex)?.title ?: ""
        else -> txtChapters.getOrNull(txtChapterIndex)?.title ?: ""
    }
    val plainPercent = when {
        markdownDocument != null && pagerEngineOn && pagedAbsOffset >= 0 -> pagedPercent
        plainContent.isEmpty() && txtStreamingDocument == null -> 0f
        pagerEngineOn && pagedAbsOffset >= 0 -> pagedPercent
        plainListSnapshot.reachedEnd -> 100f
        txtStreamingDocument != null -> {
            val total = txtStreamingDocument!!.totalChars.coerceAtLeast(1)
            (visiblePlainOffset * 100f / total).coerceIn(0f, 100f)
        }
        else -> (visiblePlainOffset * 100f / plainContent.length).coerceIn(0f, 100f)
    }
    val progressPercent = when {
        // 分页引擎的进度按全书字符偏移算（EPUB 分母为估算值，够显示与存档用）
        pagerEngineOn && pagedAbsOffset >= 0 -> pagedPercent
        epubBook != null -> epubPercent
        else -> plainPercent
    }

    // 章节内进度（0-100）：底部进度条专用，显示当前章节内的阅读位置
    val chapterProgress: Float = run {
        val offsetInChapter: Int
        val chapterLen: Int
        if (epubBook != null) {
            val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
            offsetInChapter = (if (pagerEngineOn && pagedAbsOffset >= 0) pagedAbsOffset else visiblePlainOffset) - base
            chapterLen = contentText.length.coerceAtLeast(1)
        } else if (markdownDocument != null) {
            val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
            offsetInChapter = (if (pagerEngineOn && pagedAbsOffset >= 0) pagedAbsOffset else base) - base
            chapterLen = contentText.length.coerceAtLeast(1)
        } else {
            val ch = txtChapters.getOrNull(txtChapterIndex)
            val base = ch?.startOffset ?: 0
            offsetInChapter = (if (pagerEngineOn && pagedAbsOffset >= 0) pagedAbsOffset else visiblePlainOffset) - base
            chapterLen = (ch?.charCount ?: plainContent.length).coerceAtLeast(1)
        }
        (offsetInChapter * 100f / chapterLen).coerceIn(0f, 100f)
    }

    // 阅读统计派生值（对照 web：bookReadingTimeMs / estimateBookReadingSpeed）
    val plainWordCount = remember(plainContent) { plainContent.count { !it.isWhitespace() } }
    val documentWordCount = when {
        epubBook != null -> bookIndex?.totalChars ?: 0
        markdownDocument != null -> markdownDocument.totalChars
        txtStreamingDocument != null -> txtStreamingDocument!!.totalChars
        else -> plainWordCount
    }
    val sessionReadingMs = remember(sessions) { sessions.sumOf { it.duration_ms }.coerceAtLeast(0L) }
    val savedBookReadingMs = kotlin.math.max(savedTotalReadingMs, sessionReadingMs).coerceAtLeast(0L)
    val effectiveWordCount = if (documentWordCount > 0) documentWordCount else kotlin.math.max(1, bookSize / 3)
    val currentWords = kotlin.math.max(0L, kotlin.math.round(effectiveWordCount * kotlin.math.max(0f, progressPercent - sessionStartProgress) / 100f).toLong())
    val readerSpeed: Int = run {
        val cur = if (activeReadingMs >= 10_000L && currentWords > 0)
            kotlin.math.round(currentWords / (activeReadingMs / 60_000.0)).toInt() else 0
        if (cur > 0) cur
        else if (savedBookReadingMs > 0L && progressPercent > 0f)
            kotlin.math.round((effectiveWordCount * progressPercent / 100f) / (savedBookReadingMs / 60_000.0)).toInt()
        else 300
    }
    val remainingWords = kotlin.math.max(0L, kotlin.math.round(effectiveWordCount * (1f - progressPercent / 100f)).toLong())
    val estimatedRemainingMs = if (readerSpeed > 0) kotlin.math.round(remainingWords / readerSpeed.toDouble() * 60_000.0).toLong() else 0L
    val bookmarksCount = remember(notes) { notes.count { it.kind == "bookmark" } }

    return ReaderProgressState(
        plainListState = plainListState,
        epubListState = epubListState,
        visiblePlainOffset = visiblePlainOffset,
        contentText = contentText,
        txtChapterIndex = txtChapterIndex,
        chapterFade = chapterFade,
        chapterFadeKey = chapterFadeKey,
        currentChapterTitle = currentChapterTitle,
        progressPercent = progressPercent,
        chapterProgress = chapterProgress,
        documentWordCount = documentWordCount,
        savedBookReadingMs = savedBookReadingMs,
        readerSpeed = readerSpeed,
        estimatedRemainingMs = estimatedRemainingMs,
        bookmarksCount = bookmarksCount,
        inspirationsCount = inspirationsCount,
    )
}
