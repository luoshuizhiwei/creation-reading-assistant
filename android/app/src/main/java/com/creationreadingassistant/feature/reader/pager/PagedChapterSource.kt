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

/**
 * 翻页引擎的章节内容来源。TXT / EPUB 的差别全部收在这个接口后面，
 * 控制器与宿主只认它。
 *
 * 偏移语义（必须与各格式**既有的 locator 语义一致**，否则历史高亮会错位）：
 * - TXT：全书就是一个大字符串，[chapterStartAbs] 是精确偏移。
 * - EPUB：全书偏移是 LegacyOffsetCodec 按「解压字节数估算 + 每章 +1」算出的
 *   **估算基准**。它不精确（中文书偏大约 3 倍），但选区/高亮/进度从一开始
 *   就按这套基准落库 —— 翻页引擎必须沿用同一基准，绝不能自算一套。
 */
interface PagedChapterSource {

    val chapterCount: Int

    fun chapterTitle(index: Int): String

    /** 该章起点的全书偏移（TXT 精确 / EPUB 估算基准）。 */
    fun chapterStartAbs(index: Int): Int

    /** 全书总字符数（进度百分比的分母；EPUB 为估算值）。 */
    val totalChars: Int

    /** EPUB 为 true；TXT 的章节长度本来就是精确字符数。 */
    val chapterLengthsAreEstimated: Boolean get() = false

    fun chapterEstimatedCharCount(index: Int): Int {
        val start = chapterStartAbs(index)
        val next = if (index + 1 < chapterCount) chapterStartAbs(index + 1) else totalChars
        return (next - start - 1).coerceAtLeast(0)
    }

    /**
     * 取整章纯文本。**可能做 IO（EPUB 解压），必须在后台线程调。**
     *
     * EPUB 侧必须满足冻结不变式：
     * `text == blocks.filterIsInstance<Text>().joinToString("\n") { it.text }`
     * —— 搜索、TTS、选区偏移全都建立在这条之上。
     */
    fun loadChapter(index: Int): PagedChapterContent

    fun loadChapterText(index: Int): String = loadChapter(index).text

    /** 全书偏移 → 章号（二分，取最后一个起点 ≤ offset 的章）。 */
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

/** TXT：全书一个大字符串 + 已识别的章节表。 */
class TxtChapterSource(
    private val fullText: String,
    private val chapters: List<DocChapter>,
) : PagedChapterSource {

    /**
     * 通过 [PlainTextDocument] 构建。支持流式大文件模式：
     * - 小文件路径：document.text(index) 从内存 fullText 切片
     * - 流式路径：document.text(index) 按需从文件读取章节字节
     *
     * 如果 document.readingUnits 非空，则使用 ReadingUnit 作为虚拟可分页章节，
     * 解决无目录或超大章节的分页问题。
     */
    constructor(document: PlainTextDocument) : this(
        fullText = "",
        chapters = document.chapters,
    ) {
        _document = document
        _readingUnits = document.readingUnits
    }

    private var _document: PlainTextDocument? = null

    // ── Streaming mode (explicit file-index path for large files) ──
    private var streamingDocument: PlainTextDocument? = null
    private var streamingIndex: TxtFileIndex? = null

    /** ReadingUnit 列表：非空时作为虚拟可分页章节使用。 */
    private var _readingUnits: List<ReadingUnit> = emptyList()

    companion object {
        /**
         * 流式大文件工厂方法。不加载全文到内存，章节内容按需从文件读取。
         * [document] 由 [PlainTextDocument.fromFileIndex] 创建，[fileIndex] 由 TxtFileScanner 扫描得到。
         */
        fun fromStreaming(document: PlainTextDocument, fileIndex: TxtFileIndex): TxtChapterSource {
            val src = TxtChapterSource("", document.chapters)
            src._document = document
            src.streamingDocument = document
            src.streamingIndex = fileIndex
            return src
        }
    }

    override val chapterCount: Int
        get() = if (_readingUnits.isNotEmpty()) _readingUnits.size else chapters.size

    override fun chapterTitle(index: Int): String {
        if (_readingUnits.isNotEmpty()) return _readingUnits.getOrNull(index)?.title ?: ""
        return chapters.getOrNull(index)?.title ?: ""
    }

    override fun chapterStartAbs(index: Int): Int {
        if (_readingUnits.isNotEmpty()) return _readingUnits.getOrNull(index)?.charStart ?: 0
        streamingIndex?.let { return it.chapters.getOrNull(index)?.charStart?.toInt() ?: 0 }
        return chapters.getOrNull(index)?.startOffset ?: 0
    }

    override val totalChars: Int
        get() {
            streamingIndex?.let { return it.totalCharCount.toInt() }
            return _document?.totalChars ?: fullText.length
        }

    override fun loadChapter(index: Int): PagedChapterContent {
        // ReadingUnit 虚拟章节路径：按 unit 有界读取
        if (_readingUnits.isNotEmpty()) {
            val unit = _readingUnits.getOrNull(index)
                ?: return PagedChapterContent("", emptyList())
            val text = streamingDocument?.readUnit(unit)
                ?: _document?.readUnit(unit)
                ?: return PagedChapterContent("", emptyList())
            return PagedChapterContent(
                text = text,
                blocks = TxtPageSource.paragraphsOf(text, unit.title).map(LayoutBlock::Text),
            )
        }
        val c = chapters.getOrNull(index) ?: return PagedChapterContent("", emptyList())
        val text = streamingDocument?.text(index)
            ?: _document?.text(index)
            ?: TxtPageSource.chapterTextOf(fullText, c)
        return PagedChapterContent(
            text = text,
            blocks = TxtPageSource.paragraphsOf(text, c.title).map(LayoutBlock::Text),
        )
    }
}

/**
 * EPUB：按需解压取章文本。
 *
 * @param chapterStartOffsets 来自 LegacyOffsetCodec 的既有估算基准（勿自算）
 * @param loadBlocks 按章取文档块，调用方通常接 [ReaderDocument.blocks]。
 */
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
