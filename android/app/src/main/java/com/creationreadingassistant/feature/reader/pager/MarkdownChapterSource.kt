package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownOffsetMap
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MdInline

/**
 * Markdown 章节来源。
 *
 * 与 [TxtChapterSource] / [EpubChapterSource] 并列，负责把 [MarkdownDocument]
 * 的一章交给分页引擎。所有偏移都基于 [MarkdownParser.MarkdownChapter.canonicalText]
 * 字符空间，与搜索、TTS、Locator、选区、高亮、书签保持一致。
 *
 * ## 坐标契约（CRA-MD-PAGED-CHAPTER-FIX-v1）
 *
 * 分页引擎的契约是「块偏移相对 [PagedChapterContent.text]」。整本解析
 * （[MarkdownDocument.isWholeDocumentParse]）的章视图 canonicalText 是章局部切片，
 * 但块/单元的 canonicalRange 保持全书全局坐标（`MarkdownDocument.blocks` 的既有约定，
 * 滚动端以 blocksGlobal 基准消费）。若把该视图原样交给分页器，startOffset>0 的章在
 * 排版切片时越界（真机实报 `StringIndexOutOfBoundsException: length=353; index=382`，
 * 目录选章后正文停留上一章）—— [loadChapter] 因此把章视图整体平移到章内坐标；
 * 流式逐章解析的章视图本就是章内坐标（delta=0，原样返回），滚动端不经过本 source，
 * 两条路径互不影响。
 */
class MarkdownChapterSource(
    private val document: MarkdownDocument,
) : PagedChapterSource {

    override val chapterCount: Int get() = document.chapters.size
    override val totalChars: Int get() = document.totalChars
    override val replacementCoordinateSpace: ReplacementCoordinateSpace
        get() = ReplacementCoordinateSpace.CANONICAL_DISPLAY

    override fun chapterTitle(index: Int): String =
        document.chapters.getOrNull(index)?.title ?: ""

    override fun chapterStartAbs(index: Int): Int =
        document.chapters.getOrNull(index)?.startOffset ?: 0

    override fun loadChapter(index: Int): PagedChapterContent {
        val blocks = document.blocks(index)
        val markdown = blocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()
            ?: return PagedChapterContent("", emptyList())
        val chapter = markdown.chapter
        val local = if (document.isWholeDocumentParse) {
            shiftedToChapterLocal(chapter, document.chapters.getOrNull(index)?.startOffset ?: 0)
        } else {
            chapter
        }
        return PagedChapterContent(
            text = local.canonicalText,
            blocks = MarkdownPageSource.layoutBlocksOf(local),
        )
    }

    /**
     * 把整本解析产物的 canonical 坐标平移为章内坐标：块/行内的 canonicalRange 已是
     * 全书全局坐标，减去 [delta]（章全局起点）即得章内坐标。canonicalText / sourceRange
     * 不变：前者本就是章局部切片，后者不属于分页坐标契约。
     */
    private fun shiftedToChapterLocal(
        chapter: MarkdownParser.MarkdownChapter,
        delta: Int,
    ): MarkdownParser.MarkdownChapter {
        if (delta == 0) return chapter
        val blocks = chapter.blocks.map { it.shiftedBy(-delta) }
        return MarkdownParser.MarkdownChapter(
            blocks = blocks,
            canonicalText = chapter.canonicalText,
            offsetMap = MarkdownOffsetMap.fromBlocks(blocks),
            headings = chapter.headings.map { heading ->
                heading.copy(canonicalOffset = heading.canonicalOffset - delta)
            },
        )
    }

    private fun MarkdownBlock.shiftedBy(delta: Int): MarkdownBlock = when (this) {
        is MarkdownBlock.Heading -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            inlines = inlines.map { it.shiftedBy(delta) },
        )
        is MarkdownBlock.Paragraph -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            inlines = inlines.map { it.shiftedBy(delta) },
        )
        is MarkdownBlock.OrderedList -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            items = items.map { row -> row.map { it.shiftedBy(delta) } },
        )
        is MarkdownBlock.UnorderedList -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            items = items.map { row -> row.map { it.shiftedBy(delta) } },
        )
        is MarkdownBlock.TaskList -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            items = items.map { item -> item.copy(blocks = item.blocks.map { it.shiftedBy(delta) }) },
        )
        is MarkdownBlock.BlockQuote -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            blocks = blocks.map { it.shiftedBy(delta) },
        )
        is MarkdownBlock.FencedCodeBlock -> copy(canonicalRange = canonicalRange.shiftedBy(delta))
        is MarkdownBlock.IndentedCodeBlock -> copy(canonicalRange = canonicalRange.shiftedBy(delta))
        is MarkdownBlock.HorizontalRule -> copy(canonicalRange = canonicalRange.shiftedBy(delta))
        is MarkdownBlock.Table -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            header = header.map { cell -> cell.copy(inlines = cell.inlines.map { it.shiftedBy(delta) }) },
            rows = rows.map { row ->
                row.map { cell -> cell.copy(inlines = cell.inlines.map { it.shiftedBy(delta) }) }
            },
        )
    }

    private fun MdInline.shiftedBy(delta: Int): MdInline = when (this) {
        is MdInline.Text -> copy(canonicalRange = canonicalRange.shiftedBy(delta))
        is MdInline.Strong -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            children = children.map { it.shiftedBy(delta) },
        )
        is MdInline.Emphasis -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            children = children.map { it.shiftedBy(delta) },
        )
        is MdInline.Strikethrough -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            children = children.map { it.shiftedBy(delta) },
        )
        is MdInline.Code -> copy(canonicalRange = canonicalRange.shiftedBy(delta))
        is MdInline.Link -> copy(
            canonicalRange = canonicalRange.shiftedBy(delta),
            children = children.map { it.shiftedBy(delta) },
        )
        is MdInline.Image -> copy(canonicalRange = canonicalRange.shiftedBy(delta))
        is MdInline.HardLineBreak -> copy(canonicalRange = canonicalRange.shiftedBy(delta))
        is MdInline.SoftLineBreak -> copy(canonicalRange = canonicalRange.shiftedBy(delta))
    }

    /** 全书全局坐标 → 章内坐标（[delta] = 章全局起点）。 */
    private fun IntRange.shiftedBy(delta: Int): IntRange = (first + delta)..(last + delta)
}
