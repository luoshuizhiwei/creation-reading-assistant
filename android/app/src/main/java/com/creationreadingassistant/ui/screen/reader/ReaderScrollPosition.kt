package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.DocBlock
import androidx.compose.foundation.lazy.LazyListState
import com.creationreadingassistant.feature.reader.doc.ReadingUnit

internal fun canPersistPlainScrollPosition(plainScrollPositionRestored: Boolean): Boolean =
    plainScrollPositionRestored

internal fun canPersistPagedPosition(initialPositionPending: Boolean): Boolean =
    !initialPositionPending

internal fun canPersistLegacyScrollPosition(initialPositionPending: Boolean): Boolean =
    !initialPositionPending

/** Read the current LazyList state at save time rather than a stale composition-time offset. */
internal fun currentPlainListOffset(
    state: LazyListState,
    units: List<ReadingUnit>,
    fallbackOffset: Int,
): Int {
    val index = state.firstVisibleItemIndex
    val unit = units.getOrNull(index) ?: return fallbackOffset.coerceAtLeast(0)
    val visibleItem = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return unit.charStart
    if (visibleItem.size <= 0) return unit.charStart
    val fraction = (-visibleItem.offset).coerceIn(0, visibleItem.size).toFloat() / visibleItem.size
    return (unit.charStart + unit.charCount * fraction).toInt()
        .coerceIn(unit.charStart, unit.charStart + unit.charCount)
}

/**
 * LazyColumn 阅读位置的统一估算 seam。
 *
 * [itemStartOffsets] 与 [itemLengths] 必须处于同一个全书字符空间；图片或没有文本
 * 的渲染项长度传 0。这里只做坐标推导，不触碰 Compose 状态、数据库或滚动副作用。
 */
internal fun visibleScrollOffset(
    firstVisibleItemIndex: Int,
    firstVisibleItemOffset: Int,
    firstVisibleItemSize: Int,
    itemStartOffsets: List<Int>,
    itemLengths: List<Int>,
    endReached: Boolean,
    endOffset: Int,
): Int {
    if (endReached && endOffset >= 0) return endOffset
    val index = firstVisibleItemIndex.coerceAtLeast(0)
    val start = itemStartOffsets.getOrNull(index) ?: return 0
    val length = itemLengths.getOrNull(index)?.coerceAtLeast(0) ?: 0
    if (length == 0 || firstVisibleItemSize <= 0) return start
    val consumedPx = (-firstVisibleItemOffset).coerceIn(0, firstVisibleItemSize)
    val fraction = consumedPx.toFloat() / firstVisibleItemSize
    return (start + length * fraction).toInt().coerceIn(start, start + length)
}

/**
 * EPUB 章节的最后一个可定位文本坐标。
 *
 * [blockGlobalOffsets] 会在相邻文本块之间保留换行符，因此不能以各块纯文本长度简单求和；
 * 否则章节末尾的保存坐标会随着段落数而逐渐向前偏移。
 */
internal fun epubChapterEndOffset(
    blocks: List<DocBlock>,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
): Int {
    var endOffset = chapterBase
    blocks.forEachIndexed { index, block ->
        val text = (block as? DocBlock.Text)?.text ?: return@forEachIndexed
        val start = blockGlobalOffsets.getOrNull(index) ?: return@forEachIndexed
        if (start >= chapterBase) endOffset = maxOf(endOffset, start + text.length)
    }
    return endOffset
}

internal fun scrollProgressPercent(
    offset: Int,
    totalChars: Int,
    endReached: Boolean,
): Float {
    if (totalChars <= 0) return 0f
    if (endReached) return 100f
    return (offset * 100f / totalChars).coerceIn(0f, 100f)
}

internal fun chapterProgressPercent(
    offset: Int,
    chapterStart: Int,
    chapterLength: Int,
    endReached: Boolean,
): Float {
    if (chapterLength <= 0) return if (endReached) 100f else 0f
    if (endReached) return 100f
    return ((offset - chapterStart) * 100f / chapterLength).coerceIn(0f, 100f)
}

internal data class ReaderChapterNavigation(
    val currentIndex: Int,
    val chapterCount: Int,
) {
    val canGoPrevious: Boolean get() = currentIndex > 0
    val canGoNext: Boolean get() = currentIndex >= 0 && currentIndex < chapterCount - 1
}

internal fun readerChapterNavigation(currentIndex: Int, chapterCount: Int): ReaderChapterNavigation =
    ReaderChapterNavigation(
        currentIndex = currentIndex.coerceIn(0, (chapterCount - 1).coerceAtLeast(0)),
        chapterCount = chapterCount.coerceAtLeast(0),
    )
