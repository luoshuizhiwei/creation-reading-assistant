package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument

/**
 * Markdown 章节来源。
 *
 * 与 [TxtChapterSource] / [EpubChapterSource] 并列，负责把 [MarkdownDocument]
 * 的一章交给分页引擎。所有偏移都基于 [MarkdownParser.MarkdownChapter.canonicalText]
 * 字符空间，与搜索、TTS、Locator、选区、高亮、书签保持一致。
 */
class MarkdownChapterSource(
    private val document: MarkdownDocument,
) : PagedChapterSource {

    override val chapterCount: Int get() = document.chapters.size
    override val totalChars: Int get() = document.totalChars

    override fun chapterTitle(index: Int): String =
        document.chapters.getOrNull(index)?.title ?: ""

    override fun chapterStartAbs(index: Int): Int =
        document.chapters.getOrNull(index)?.startOffset ?: 0

    override fun loadChapter(index: Int): PagedChapterContent {
        val blocks = document.blocks(index)
        val markdown = blocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()
            ?: return PagedChapterContent("", emptyList())
        return PagedChapterContent(
            text = markdown.chapter.canonicalText,
            blocks = MarkdownPageSource.layoutBlocksOf(markdown.chapter),
        )
    }
}
