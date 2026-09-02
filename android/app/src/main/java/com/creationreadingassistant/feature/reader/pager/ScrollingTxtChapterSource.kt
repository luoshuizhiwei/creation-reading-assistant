package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.layout.LayoutBlock

/**
 * Bounded scrolling-TXT source whose paging indices are [ReadingUnit] indices while replacement
 * scopes remain complete logical chapters. Both in-memory and file-backed readers use this seam.
 */
class ScrollingTxtChapterSource(
    private val units: List<ReadingUnit>,
    override val totalChars: Int,
    private val loadUnitText: (ReadingUnit) -> String,
    private val loadLogicalChapterText: (chapterIndex: Int) -> String,
    private val maxProjectionChars: Int = ReplaceProjectionScopeProvider.DEFAULT_MAX_CHARS_FOR_PROJECTION,
) : PagedChapterSource, ReplaceProjectionScopeProvider {

    override val replacementCoordinateSpace: ReplacementCoordinateSpace
        get() = ReplacementCoordinateSpace.SOURCE

    companion object {
        fun fromDocument(document: PlainTextDocument): ScrollingTxtChapterSource =
            ScrollingTxtChapterSource(
                units = document.readingUnits,
                totalChars = document.totalChars,
                loadUnitText = document::readUnit,
                loadLogicalChapterText = document::readChapterRawText,
            )

        fun fromText(
            text: String,
            units: List<ReadingUnit>,
        ): ScrollingTxtChapterSource {
            val chapterRanges = units.groupBy(ReadingUnit::chapterIndex).mapValues { (_, chapterUnits) ->
                val start = chapterUnits.first().charStart
                val end = chapterUnits.last().let { it.charStart + it.charCount }
                start to end
            }
            return ScrollingTxtChapterSource(
                units = units,
                totalChars = text.length,
                loadUnitText = { unit ->
                    text.substring(unit.charStart, unit.charStart + unit.charCount)
                },
                loadLogicalChapterText = { chapterIndex ->
                    val range = checkNotNull(chapterRanges[chapterIndex]) {
                        "Unknown logical chapter: $chapterIndex"
                    }
                    text.substring(range.first, range.second)
                },
            )
        }
    }

    private data class LogicalChapterScope(
        val firstSegmentIndex: Int,
        val charCount: Int,
        val complete: Boolean,
    )

    private val scopesByChapter: Map<Int, LogicalChapterScope> = buildScopes(units)

    init {
        require(totalChars >= 0) { "totalChars must be non-negative" }
        require(maxProjectionChars > 0) { "maxProjectionChars must be positive" }
        require(units.indices.all { units[it].unitIndex == it }) {
            "ReadingUnit indices must be contiguous and match their list positions"
        }
    }

    override val chapterCount: Int get() = units.size
    override val replaceProjectionScopeIsComplete: Boolean get() = false

    override fun chapterTitle(index: Int): String = units.getOrNull(index)?.title.orEmpty()

    override fun chapterStartAbs(index: Int): Int = units.getOrNull(index)?.charStart ?: 0

    override fun logicalChapterIndex(pagingIndex: Int): Int =
        units.getOrNull(pagingIndex)?.chapterIndex ?: pagingIndex

    override fun loadChapter(index: Int): PagedChapterContent {
        val unit = units.getOrNull(index) ?: return PagedChapterContent("", emptyList())
        val source = loadUnitText(unit)
        check(source.length == unit.charCount) {
            "ReadingUnit source length mismatch: unit=${unit.unitIndex}, expected=${unit.charCount}, actual=${source.length}"
        }
        return PagedChapterContent(
            text = source,
            blocks = TxtPageSource.paragraphsOf(source, unit.title).map(LayoutBlock::Text),
        )
    }

    override fun scopeForSegment(segmentIndex: Int): ReplaceProjectionScope {
        val unit = units.getOrNull(segmentIndex) ?: return ReplaceProjectionScope.Incomplete(-1)
        val scope = scopesByChapter[unit.chapterIndex]
            ?: return ReplaceProjectionScope.Incomplete(unit.chapterIndex)
        if (!scope.complete) return ReplaceProjectionScope.Incomplete(unit.chapterIndex)
        if (scope.charCount > maxProjectionChars) {
            return ReplaceProjectionScope.UnsupportedTooLarge(
                logicalChapterIndex = unit.chapterIndex,
                actualSourceLength = scope.charCount,
                maxSourceLength = maxProjectionChars,
            )
        }
        return ReplaceProjectionScope.Exact(
            logicalChapterIndex = unit.chapterIndex,
            firstSegmentIndex = scope.firstSegmentIndex,
            charCount = scope.charCount,
            loader = {
                val source = loadLogicalChapterText(unit.chapterIndex)
                check(source.length == scope.charCount) {
                    "Logical chapter source length mismatch: chapter=${unit.chapterIndex}, expected=${scope.charCount}, actual=${source.length}"
                }
                source
            },
        )
    }

    private fun buildScopes(sourceUnits: List<ReadingUnit>): Map<Int, LogicalChapterScope> {
        return sourceUnits.withIndex()
            .groupBy { it.value.chapterIndex }
            .mapValues { (_, indexedUnits) ->
                val first = indexedUnits.first()
                val last = indexedUnits.last()
                val contiguousIndices = last.index - first.index + 1 == indexedUnits.size
                var expectedStart = first.value.charStart
                var contiguousOffsets = true
                var total = 0L
                for ((_, unit) in indexedUnits) {
                    if (unit.charCount < 0 || unit.charStart != expectedStart) contiguousOffsets = false
                    expectedStart = unit.charStart + unit.charCount
                    total += unit.charCount.toLong()
                }
                LogicalChapterScope(
                    firstSegmentIndex = first.index,
                    charCount = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    complete = contiguousIndices && contiguousOffsets && total <= Int.MAX_VALUE,
                )
            }
    }
}
