package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Markdown 滚动模式搜索命中的公共换算 seam：
 * 输入 [SearchHitTarget] 的全书/章节绝对区间 + 当前章节的 Markdown 解析产物
 * （块级 canonicalRange，即「原文 → 可见文本」的 offset mapping），
 * 输出当前滚动渲染文本单元上的精确局部高亮区间（[MarkdownScrollHit]）。
 *
 * 覆盖两种真实坐标空间：
 * - 流式章节：块 canonicalRange 为章内局部，[blockGlobalBase] = 章节全书起点；
 * - 小文件整本解析：块 canonicalRange 为全书全局，[blockGlobalBase] = 0。
 */
class MarkdownScrollHighlightTest {

    private fun target(
        bookKey: String = "book-1",
        chapterIndex: Int = 1,
        abs: IntRange,
    ) = SearchHitTarget(
        bookKey = bookKey,
        chapterIndex = chapterIndex,
        absoluteRange = abs,
        resultIndex = 0,
    )

    @Test
    fun `streamed non-first chapter hit maps to paragraph local range`() {
        val base = 1234
        val chapter = MarkdownParser.parse(
            source = "这是正文，包含**搜索词**和更多内容。",
            sourceOffsetShift = base,
        )
        val canonical = chapter.canonicalText
        val hitStart = canonical.indexOf("搜索词") // 测试侧仅用于构造输入，实现不得 substring
        val paragraph = chapter.blocks.filterIsInstance<MarkdownBlock.Paragraph>().single()

        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = base,
            chapterLength = canonical.length,
            blockGlobalBase = base,
            target = target(chapterIndex = 1, abs = base + hitStart until base + hitStart + 3),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        // 强强调标记 `**` 被 parser 扣除：局部区间落在可见文本「搜索词」上（7..10）
        assertEquals(
            listOf(MarkdownScrollHit(canonicalStart = paragraph.canonicalRange.first, localRange = 7 until 10)),
            hits,
        )
    }

    @Test
    fun `hit in second top-level paragraph picks its own block start`() {
        val chapter = MarkdownParser.parse("第一段内容。\n\n第二段里出现**搜索词**。")
        val canonical = chapter.canonicalText
        val hitStart = canonical.indexOf("搜索词")
        val paragraphs = chapter.blocks.filterIsInstance<MarkdownBlock.Paragraph>()

        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = canonical.length,
            blockGlobalBase = 0,
            target = target(chapterIndex = 1, abs = hitStart until hitStart + 3),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        // 第二段块起点 = 7（第一段 6 字符 + 块间换行），段内局部 6..9
        assertEquals(
            listOf(MarkdownScrollHit(canonicalStart = paragraphs[1].canonicalRange.first, localRange = 6 until 9)),
            hits,
        )
        assertEquals(7, paragraphs[1].canonicalRange.first)
    }

    @Test
    fun `small-file global blocks restrict highlight to current chapter span`() {
        val source = "# 第一章\n\n第一章内容。\n\n# 第二章\n\n第二章出现**搜索词**。"
        val chapter = MarkdownParser.parse(source)
        val canonical = chapter.canonicalText
        val h2 = chapter.headings.first { it.level == 1 && it.title == "第二章" }
        val base = h2.canonicalOffset
        val chapterLength = canonical.length - base
        val hitStart = canonical.indexOf("搜索词")
        val paragraph2 = chapter.blocks.filterIsInstance<MarkdownBlock.Paragraph>().last()

        // 小文件整本解析：块 canonicalRange 为全书坐标 → blockGlobalBase = 0
        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = base,
            chapterLength = chapterLength,
            blockGlobalBase = 0,
            target = target(chapterIndex = 1, abs = hitStart until hitStart + 3),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        assertEquals(
            listOf(MarkdownScrollHit(canonicalStart = paragraph2.canonicalRange.first, localRange = 5 until 8)),
            hits,
        )

        // 第一章内的命中（全书区间在本章 span 之外）不得高亮
        val chapterOneHit = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = base,
            chapterLength = chapterLength,
            blockGlobalBase = 0,
            target = target(chapterIndex = 1, abs = 0 until 3),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )
        assertEquals(emptyList<MarkdownScrollHit>(), chapterOneHit)
    }

    @Test
    fun `cross-book stale target yields no highlight`() {
        val chapter = MarkdownParser.parse("包含**搜索词**。")
        val canonical = chapter.canonicalText
        val hitStart = canonical.indexOf("搜索词")

        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = canonical.length,
            blockGlobalBase = 0,
            target = target(bookKey = "other-book", chapterIndex = 1, abs = hitStart until hitStart + 3),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        assertEquals(emptyList<MarkdownScrollHit>(), hits)
    }

    @Test
    fun `target of another chapter yields no highlight`() {
        val chapter = MarkdownParser.parse("包含**搜索词**。")
        val canonical = chapter.canonicalText
        val hitStart = canonical.indexOf("搜索词")

        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = canonical.length,
            blockGlobalBase = 0,
            target = target(chapterIndex = 0, abs = hitStart until hitStart + 3),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        assertEquals(emptyList<MarkdownScrollHit>(), hits)
    }

    @Test
    fun `range outside chapter span yields no highlight`() {
        val chapter = MarkdownParser.parse("包含**搜索词**。")
        val canonical = chapter.canonicalText
        val base = 500

        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = base,
            chapterLength = canonical.length,
            blockGlobalBase = base,
            target = target(chapterIndex = 1, abs = base + canonical.length + 10 until base + canonical.length + 13),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        assertEquals(emptyList<MarkdownScrollHit>(), hits)
    }

    @Test
    fun `hit spanning strong marker boundary maps to visible text only`() {
        val chapter = MarkdownParser.parse("**加粗**和搜索词")
        val canonical = chapter.canonicalText
        val hitStart = canonical.indexOf("加粗和搜")
        val paragraph = chapter.blocks.filterIsInstance<MarkdownBlock.Paragraph>().single()

        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = canonical.length,
            blockGlobalBase = 0,
            target = target(chapterIndex = 1, abs = hitStart until hitStart + 4),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        // 规范文本「加粗和搜…」从 0 起：跨 Strong 与普通文本的命中映射为连续可见区间
        assertEquals(0, hitStart)
        assertEquals(
            listOf(MarkdownScrollHit(canonicalStart = paragraph.canonicalRange.first, localRange = 0 until 4)),
            hits,
        )
    }

    @Test
    fun `table cell hit maps to exact cell local range`() {
        val chapter = MarkdownParser.parse(
            """
            | A | B |
            |---|---|
            | 1 | 2 |
            """.trimIndent(),
        )
        val canonical = chapter.canonicalText
        val table = chapter.blocks.filterIsInstance<MarkdownBlock.Table>().single()

        // 表头第一格「A」
        val hitA = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = canonical.length,
            blockGlobalBase = 0,
            target = target(chapterIndex = 1, abs = canonical.indexOf("A") until canonical.indexOf("A") + 1),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )
        assertEquals(
            listOf(MarkdownScrollHit(canonicalStart = table.header[0].inlines.first().canonicalRange.first, localRange = 0 until 1)),
            hitA,
        )

        // 数据行第二格「2」
        val hit2 = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = canonical.length,
            blockGlobalBase = 0,
            target = target(chapterIndex = 1, abs = canonical.indexOf("2") until canonical.indexOf("2") + 1),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )
        assertEquals(
            listOf(MarkdownScrollHit(canonicalStart = table.rows[0][1].inlines.first().canonicalRange.first, localRange = 0 until 1)),
            hit2,
        )

        // 修复后 canonical 无重复副本：唯一的「A」就是表头单元格本身（hitA 已覆盖其映射）
        assertEquals("单元格文本应只出现一次", canonical.indexOf("A"), canonical.lastIndexOf("A"))
    }

    @Test
    fun `code block hit maps to content range`() {
        val chapter = MarkdownParser.parse("```\nval query = 搜索词\n```")
        val canonical = chapter.canonicalText
        val code = chapter.blocks.filterIsInstance<MarkdownBlock.FencedCodeBlock>().single()
        val localStart = code.content.indexOf("搜索词")
        assertEquals(12, localStart)

        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = canonical.length,
            blockGlobalBase = 0,
            target = target(chapterIndex = 1, abs = canonical.indexOf("搜索词") until canonical.indexOf("搜索词") + 3),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        assertEquals(
            listOf(MarkdownScrollHit(canonicalStart = code.canonicalRange.first, localRange = 12 until 15)),
            hits,
        )
    }

    @Test
    fun `hit inside blockquote maps to nested leaf paragraph not container`() {
        val chapter = MarkdownParser.parse("> 第一段\n>\n> 第二段有**搜索词**")
        val canonical = chapter.canonicalText
        val quote = chapter.blocks.filterIsInstance<MarkdownBlock.BlockQuote>().single()
        val nested = quote.blocks.filterIsInstance<MarkdownBlock.Paragraph>().last()
        val hitStart = canonical.indexOf("搜索词")

        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = canonical.length,
            blockGlobalBase = 0,
            target = target(chapterIndex = 1, abs = hitStart until hitStart + 3),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        // 嵌套第二段块起点 = 4（首段 3 字符 + 块间换行），段内局部 4..7；不得映射到容器 BlockQuote 起点 0
        assertEquals(4, nested.canonicalRange.first)
        assertEquals(
            listOf(MarkdownScrollHit(canonicalStart = 4, localRange = 4 until 7)),
            hits,
        )
        assertEquals(0, quote.canonicalRange.first)
    }

    @Test
    fun `empty absolute range yields no highlight`() {
        val chapter = MarkdownParser.parse("包含**搜索词**。")
        val hits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = chapter.canonicalText.length,
            blockGlobalBase = 0,
            target = target(chapterIndex = 1, abs = 5 until 5),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )

        assertEquals(emptyList<MarkdownScrollHit>(), hits)
    }
}
