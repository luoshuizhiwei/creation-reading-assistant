package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Markdown 滚动导航的渲染单元 seam：
 * [MarkdownRenderModel.flatten]（doc 统一模型）产出与 LazyColumn 项一一对应的单元列表，
 * [markdownRenderUnitIndexForChapterOffset] 用 parser canonical mapping 找出
 * 包含命中的渲染单元索引——两者共享同一展平顺序，导航索引与滚动项索引严格一致。
 *
 * 坐标空间覆盖两种真实模式：
 * - 流式逐章：块 canonicalRange 为章内局部（[blocksGlobal] = false）；
 * - 小文件整本解析：块 canonicalRange 为全书全局（[blocksGlobal] = true）。
 */
class MarkdownRenderUnitTest {

    @Test
    fun `flatten keeps render order with nested containers`() {
        val chapter = MarkdownParser.parse(
            "> 引用段\n\n- 列表项\n\n| A |\n|---|\n| 1 |\n\n普通段落",
        )
        val units = MarkdownRenderModel.flatten(chapter)

        // 顺序与递归渲染一致：引用段、列表项、表、普通段落
        assertEquals(listOf(0, 1, 2, 3), units.map { it.index })
        // 引用段与列表项嵌套深度 1；表与普通段落为顶层
        assertEquals(listOf(1, 1, 0, 0), units.map { it.depth })
        // 顶层归属：引用=0、列表=1、表=2、普通段落=3（各自是独立顶层块）
        assertEquals(listOf(0, 1, 2, 3), units.map { it.topBlockIndex })
        // id 与阅读顺序一致且稳定
        assertEquals(listOf("b0.q0", "b1.l0.c0", "b2", "b3"), units.map { it.id })
    }

    @Test
    fun `index mapping picks containing unit in non-first chapter`() {
        val chapterBase = 500
        val chapter = MarkdownParser.parse("第一段内容。\n\n第二段出现**搜索词**。")
        val hitStart = chapter.canonicalText.indexOf("搜索词")

        val index = markdownRenderUnitIndexForChapterOffset(
            chapter = chapter,
            inChapter = hitStart,
            chapterBase = chapterBase,
            blocksGlobal = false,
        )

        // 第二个段落（canonical 起点 8）包含命中 → 渲染单元索引 1
        assertEquals(1, index)
        // parser 在顶层块之间只插一个换行分隔符：第一段 6 字符 → 第二段 canonical 起点 7
        assertEquals(7, chapter.blocks.filterIsInstance<MarkdownBlock.Paragraph>()[1].canonicalRange.first)
    }

    @Test
    fun `index mapping with global blocks compares absolute offsets`() {
        val source = "# 第一章\n\n第一章内容。\n\n# 第二章\n\n第二章出现**搜索词**。"
        val chapter = MarkdownParser.parse(source)
        val base = chapter.headings
            .first { it.level == 1 && it.title == "第二章" }
            .canonicalOffset
        val hitStart = chapter.canonicalText.indexOf("搜索词")

        // 小文件整本解析：canonicalRange 为全书坐标，章内偏移仍按章节起点换算后比较
        val index = markdownRenderUnitIndexForChapterOffset(
            chapter = chapter,
            inChapter = hitStart - base,
            chapterBase = base,
            blocksGlobal = true,
        )

        // 单元顺序：H1 第一章 / 第一段 / H1 第二章 / 第二段 → 命中在索引 3
        assertEquals(3, index)
    }

    @Test
    fun `table hit maps to table render unit`() {
        val chapter = MarkdownParser.parse("| A | B |\n|---|---|\n| 1 | 2 |")
        val table = chapter.blocks.filterIsInstance<MarkdownBlock.Table>().single()

        // 表格文本只出现一次，且位于表格 canonicalRange 内（修复前存在位于其前的幻影副本）
        val realHit = chapter.canonicalText.indexOf("2", startIndex = table.canonicalRange.first)
        assertEquals("单元格文本不应有幻影副本", realHit, chapter.canonicalText.indexOf("2"))
        val index = markdownRenderUnitIndexForChapterOffset(
            chapter = chapter,
            inChapter = realHit,
            chapterBase = 0,
            blocksGlobal = false,
        )

        assertEquals(0, index)
    }

    @Test
    fun `hit inside nested blockquote maps to nested leaf unit not container`() {
        val chapter = MarkdownParser.parse("> 第一段\n>\n> 第二段有**搜索词**")
        val hitStart = chapter.canonicalText.indexOf("搜索词")

        val index = markdownRenderUnitIndexForChapterOffset(
            chapter = chapter,
            inChapter = hitStart,
            chapterBase = 0,
            blocksGlobal = false,
        )

        // BlockQuote 容器不产生单元：嵌套第一段 / 第二段是两个叶单元，命中第二段 → 索引 1
        assertEquals(1, index)
        val units = MarkdownRenderModel.flatten(chapter)
        assertEquals(2, units.size)
        // 引用块内块之间插 1 个换行分隔符：第一段 3 字符 + 换行 → 第二段 canonical 起点 4
        assertEquals(4, units.last().canonicalRange.first)
    }

    @Test
    fun `offset before first unit yields null`() {
        val chapter = MarkdownParser.parse("第一段内容。")
        // 命中起点落在章首空白之前（理论上搜索不会产出，但映射必须安全）
        assertNull(
            markdownRenderUnitIndexForChapterOffset(
                chapter = chapter,
                inChapter = -1,
                chapterBase = 0,
                blocksGlobal = false,
            ),
        )
    }
}
