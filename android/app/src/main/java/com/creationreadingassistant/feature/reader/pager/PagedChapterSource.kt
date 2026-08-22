package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.layout.LayoutBlock

data class PagedChapterContent(
    val text: String,
    val blocks: List<LayoutBlock>,
)

interface PagedChapterSource {

    val chapterCount: Int

    fun chapterTitle(index: Int): String

    fun chapterStartAbs(index: Int): Int

    val totalChars: Int

    val chapterLengthsAreEstimated: Boolean get() = false

    fun logicalChapterIndex(pagingIndex: Int): Int = pagingIndex

    val replaceProjectionScopeIsComplete: Boolean get() = false

    fun chapterEstimatedCharCount(index: Int): Int {
        val start = chapterStartAbs(index)
        val next = if (index + 1 < chapterCount) chapterStartAbs(index + 1) else totalChars
        return (next - start - 1).coerceAtLeast(0)
    }

    fun loadChapter(index: Int): PagedChapterContent

    fun loadChapterText(index: Int): String = loadChapter(index).text

    fun chapterIndexFor(absOffset: Int): Int {
        if (chapterCount == 0) return 0
        var lo = 0
        var hi = chapterCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (chapterStartAbs(mid) <= absOffset) lo = mid else hi = mid - 1
        }
        return lo
    }
}

interface ReplaceProjectionScopeProvider {
    companion object {
        const val DEFAULT_MAX_CHARS_FOR_PROJECTION: Int = 256_000
    }
    fun scopeForSegment(segmentIndex: Int): ReplaceProjectionScope
}

sealed class ReplaceProjectionScope {
    abstract val logicalChapterIndex: Int

    data class Exact internal constructor(
        override val logicalChapterIndex: Int,
        val firstSegmentIndex: Int,
        val charCount: Int,
        internal val loader: () -> String,
    ) : ReplaceProjectionScope() {
        fun loadFullChapterText(): String = loader()
    }

    data class UnsupportedTooLarge(
        override val logicalChapterIndex: Int,
        val actualSourceLength: Int,
        val maxSourceLength: Int,
    ) : ReplaceProjectionScope()

    data class Incomplete(
        override val logicalChapterIndex: Int,
    ) : ReplaceProjectionScope()
}

class TxtChapterSource(
    private val fullText: String,
    private val chapters: List<DocChapter>,
) : PagedChapterSource {

    constructor(document: PlainTextDocument) : this(
        fullText = "",
        chapters = document.chapters,
    ) { _document = document }

    private var _document: PlainTextDocument? = null
    private var streamingIndex: TxtFileIndex? = null
    private var streamingUnits: List<ReadingUnit>? = null
    private var logicalChapterFirstSegmentMap: Map<Int, Int>? = null
    private val isDocumentBacked: Boolean get() = _document != null

    companion object {
        fun fromStreaming(document: PlainTextDocument, fileIndex: TxtFileIndex): TxtChapterSource {
            val src = TxtChapterSource("", document.chapters)
            src._document = document
            src.streamingIndex = fileIndex
            src.streamingUnits = document.readingUnits
            val firstSeg = mutableMapOf<Int, Int>()
            document.readingUnits.forEachIndexed { segIdx, unit ->
                firstSeg.getOrPut(unit.chapterIndex) { segIdx }
            }
            src.logicalChapterFirstSegmentMap = firstSeg
            return src
        }
    }

    override val chapterCount: Int
        get() = streamingUnits?.size ?: chapters.size

    override val replaceProjectionScopeIsComplete: Boolean
        get() = streamingUnits == null

    fun asReplaceProjectionScopeProvider(): ReplaceProjectionScopeProvider? {
        val units = streamingUnits
        val doc = _document
        if (units == null || doc == null) return null
        val firstSegMap = logicalChapterFirstSegmentMap ?: emptyMap()
        return object : ReplaceProjectionScopeProvider {
            override fun scopeForSegment(segmentIndex: Int): ReplaceProjectionScope {
                if (segmentIndex !in units.indices) return ReplaceProjectionScope.Incomplete(-1)
                val unit = units[segmentIndex]
                val chapter = chapters.getOrNull(unit.chapterIndex)
                    ?: return ReplaceProjectionScope.Incomplete(unit.chapterIndex)
                val charCount = chapter.charCount
                val firstSeg = firstSegMap[unit.chapterIndex] ?: segmentIndex
                val maxChars = ReplaceProjectionScopeProvider.DEFAULT_MAX_CHARS_FOR_PROJECTION
                return if (charCount > maxChars) {
                    ReplaceProjectionScope.UnsupportedTooLarge(
                        logicalChapterIndex = unit.chapterIndex,
                        actualSourceLength = charCount,
                        maxSourceLength = maxChars,
                    )
                } else {
                    ReplaceProjectionScope.Exact(
                        logicalChapterIndex = unit.chapterIndex,
                        firstSegmentIndex = firstSeg,
                        charCount = charCount,
                        loader = { doc.readChapterRawText(unit.chapterIndex) },
                    )
                }
            }
        }
    }

    override fun chapterTitle(index: Int): String {
        val units = streamingUnits
        if (units != null) {
            val chapterIdx = units.getOrNull(index)?.chapterIndex ?: return ""
            return chapters.getOrNull(chapterIdx)?.title ?: ""
        }
        return chapters.getOrNull(index)?.title ?: ""
    }

    override fun chapterStartAbs(index: Int): Int {
        streamingUnits?.let { units ->
            return units.getOrNull(index)?.charStart ?: 0
        }
        streamingIndex?.let { return it.chapters.getOrNull(index)?.charStart?.toInt() ?: 0 }
        return chapters.getOrNull(index)?.startOffset ?: 0
    }

    override fun logicalChapterIndex(pagingIndex: Int): Int {
        streamingUnits?.let { units ->
            return units.getOrNull(pagingIndex)?.chapterIndex ?: pagingIndex
        }
        return pagingIndex
    }

    override val totalChars: Int
        get() {
            streamingIndex?.let { return it.totalCharCount.toInt() }
            return _document?.totalChars ?: fullText.length
        }

    override fun loadChapter(index: Int): PagedChapterContent {
        val units = streamingUnits
        if (units != null) {
            val unit = units.getOrNull(index) ?: return PagedChapterContent("", emptyList())
            val doc = requireNotNull(_document) { "streamingUnits 非空时必有 PlainTextDocument 背书" }
            val text = doc.readUnit(unit)
            return PagedChapterContent(
                text = text,
                blocks = TxtPageSource.paragraphsOf(text, unit.title).map(LayoutBlock::Text),
            )
        }
        val c = chapters.getOrNull(index) ?: return PagedChapterContent("", emptyList())
        val text = if (isDocumentBacked) {
            _document?.readChapterRawText(index).orEmpty()
        } else {
            TxtPageSource.chapterTextOf(fullText, c)
        }
        return PagedChapterContent(
            text = text,
            blocks = TxtPageSource.paragraphsOf(text, c.title).map(LayoutBlock::Text),
        )
    }
}

class EpubChapterSource(
    private val titles: List<String>,
    private val chapterStartOffsets: List<Int>,
    override val totalChars: Int,
    private val loadBlocks: (Int) -> List<DocBlock>,
) : PagedChapterSource {

    override val chapterCount: Int get() = titles.size
    override val chapterLengthsAreEstimated: Boolean get() = true
    override fun chapterTitle(index: Int): String = titles.getOrNull(index) ?: ""
    override fun chapterStartAbs(index: Int): Int = chapterStartOffsets.getOrNull(index) ?: 0
    override fun loadChapter(index: Int): PagedChapterContent {
        val blocks = loadBlocks(index)
        return PagedChapterContent(
            text = EpubPageSource.chapterTextOf(blocks),
            blocks = EpubPageSource.layoutBlocksOf(blocks),
        )
    }
}
