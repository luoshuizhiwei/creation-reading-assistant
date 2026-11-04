package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownRenderContent
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderKind
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.ui.screen.reader.MarkdownPersistentHighlights.projectPersistentHighlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F3 任务 C：持久高亮的**纯函数投影**规则。
 *
 * 规则（任务书）：
 * 1. 已保存 locator/source length 与当前渲染单元 source 区间求交；
 * 2. 交集换算成当前单元的局部可见区间；
 * 3. 禁止 selectedText 搜索 / substring 首次命中 / 章节内字符串猜测；
 * 4. 重复文本按 source 坐标分别落到正确块；
 * 5. 跨两个相邻单元时两边各渲染自己的交集；
 * 6. stale book/chapter、空区间、非法坐标安全忽略；
 * 7. 搜索 / TTS / 持久高亮可叠加但不得互相冒充颜色与语义；
 * 8. 不可渲染时数据仍保留（本层只跳过，不删除、不改写数据库）；
 * 9. 只能整块时诚实限定为整块，不生成虚假局部范围。
 */
class MarkdownPersistentHighlightProjectionTest {

    private fun chapter(src: String) = MarkdownParser.parse(src)

    @Test
    fun `a highlight intersecting one unit is projected to that unit`() {
        val ch = chapter("甲段落\n\n乙段落\n")
        val units = MarkdownRenderModel.flatten(ch)
        val first = units[0]

        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(
                savedHighlight(start = first.canonicalRange.first, length = 2),
            ),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        val hit = spans.getValue(first.id)
        assertEquals(1, hit.size)
        assertEquals(0 until 2, hit[0].localRange)
    }

    @Test
    fun `a highlight crossing two adjacent units renders its own intersection on each`() {
        val ch = chapter("甲段落\n\n乙段落\n")
        val units = MarkdownRenderModel.flatten(ch)
        val first = units[0]
        val second = units[1]

        // 跨度覆盖第一块尾部到第二块头部
        val start = first.canonicalRange.last // 第一块最后一个字符
        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(savedHighlight(start = start, length = 4)),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        assertTrue("第一块必须有自己的交集", spans.getValue(first.id).isNotEmpty())
        assertTrue("第二块必须有自己的交集", spans.getValue(second.id).isNotEmpty())
        // 两边交集互不越界
        val firstTextLen = ch.canonicalText.substring(first.canonicalRange.first, first.canonicalRange.last + 1).length
        spans.getValue(first.id).forEach { span -> assertTrue(span.localRange.last <= firstTextLen) }
    }

    @Test
    fun `duplicate text highlights land on their own blocks by source coordinate`() {
        val ch = chapter("重复\n\n重复\n")
        val units = MarkdownRenderModel.flatten(ch)
            .filter { it.kind == MarkdownRenderKind.PARAGRAPH }
        assertEquals(2, units.size)

        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(
                savedHighlight(start = units[1].canonicalRange.first, length = 2),
            ),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        assertTrue("坐标落第二块时第一块不得命中", spans[units[0].id].orEmpty().isEmpty())
        assertEquals(1, spans.getValue(units[1].id).size)
    }

    @Test
    fun `stale book is ignored`() {
        val ch = chapter("段落\n")
        val units = MarkdownRenderModel.flatten(ch)
        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(savedHighlight(start = 0, length = 2, bookKey = "other-book")),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        assertTrue(spans.values.all { it.isEmpty() })
    }

    @Test
    fun `a highlight outside the current chapter span is ignored`() {
        // F3.1（Qoder P2-1）：不再用「手工填 chapterIndex」制造假反例 —— 记录里
        // 根本没有真实章身份（`LocatorBuilder.forPlain` 恒写 ci = 0）。这里改用
        // 几何反例：高亮 [5000, 5010) 完全落在当前章 span [0, 1000) 之外。
        val ch = chapter("段落\n")
        val units = MarkdownRenderModel.flatten(ch)
        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(savedHighlight(start = 5000, length = 10)),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        assertTrue(
            "完全位于当前章 span 之外的高亮不得投影",
            spans.values.all { it.isEmpty() },
        )
    }

    @Test
    fun `empty and illegal ranges are ignored`() {
        val ch = chapter("段落\n")
        val units = MarkdownRenderModel.flatten(ch)
        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(
                savedHighlight(start = 0, length = 0),
                savedHighlight(start = -5, length = 3),
                savedHighlight(start = 999_999, length = 3),
            ),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        assertTrue(spans.values.all { it.isEmpty() })
    }

    @Test
    fun `non text units never receive a persistent highlight`() {
        val ch = chapter("段落\n\n---\n")
        val units = MarkdownRenderModel.flatten(ch)
        val rule = units.single { it.kind == MarkdownRenderKind.HORIZONTAL_RULE }
        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(savedHighlight(start = rule.canonicalRange.first, length = 5)),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        assertTrue("分隔线不得承载持久高亮", spans[rule.id].orEmpty().isEmpty())
    }

    @Test
    fun `table cells receive their own intersection`() {
        val ch = chapter("| 甲 | 乙 |\n| --- | --- |\n| 丙 | 丁 |\n")
        val units = MarkdownRenderModel.flatten(ch)
        val table = units.single { it.kind == MarkdownRenderKind.TABLE }
        val cell = (table.content as MarkdownRenderContent.Table)
            .rows.first().first()

        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(savedHighlight(start = cell.canonicalRange.first, length = 1)),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        assertTrue("表格单元格必须承载自己的交集", spans.getValue(table.id).isNotEmpty())
    }

    @Test
    fun `highlight beyond the unit end is clipped not dropped`() {
        val ch = chapter("段落\n")
        val units = MarkdownRenderModel.flatten(ch)
        val unit = units[0]
        val textLen = ch.canonicalText.substring(unit.canonicalRange.first, unit.canonicalRange.last + 1).length
        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(savedHighlight(start = unit.canonicalRange.first, length = 999)),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        val hit = spans.getValue(unit.id).single()
        assertEquals(0, hit.localRange.first)
        assertEquals(textLen, hit.localRange.last + 1)
    }

    private fun savedHighlight(
        start: Int,
        length: Int,
        bookKey: String = "book-1",
    ): MarkdownPersistentHighlights.SavedHighlight = MarkdownPersistentHighlights.SavedHighlight(
        bookKey = bookKey,
        start = start,
        length = length,
        colorKey = "yellow",
    )

    @Test
    fun `streaming chapter applies the chapter base before intersecting`() {
        // 流式逐章解析：单元 canonicalRange 是章内局部坐标，已保存高亮是全书坐标。
        // 不叠加 chapterBase 就会整体错位（回归钉子）。
        val ch = MarkdownParser.parse("甲段落\n", sourceOffsetShift = 500)
        val units = MarkdownRenderModel.flatten(ch)
        val unit = units.single { it.kind == MarkdownRenderKind.PARAGRAPH }

        val withBase = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(savedHighlight(start = 500, length = 2)),
            currentBookKey = "book-1",
            chapterSpan = 500 until 510,
            blockGlobalBase = 500,
        )
        assertEquals(0 until 2, withBase.getValue(unit.id).single().localRange)

        val withoutBase = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(savedHighlight(start = 0, length = 2)),
            currentBookKey = "book-1",
            chapterSpan = 0 until 10,
            blockGlobalBase = 0,
        )
        assertEquals(0 until 2, withoutBase.getValue(unit.id).single().localRange)
    }

    @Test
    fun `table cell spans carry their own canonical start discriminator`() {
        val ch = MarkdownParser.parse("| 甲 | 乙 |\n| --- | --- |\n| 丙 | 丁 |\n")
        val units = MarkdownRenderModel.flatten(ch)
        val table = units.single { it.kind == MarkdownRenderKind.TABLE }
        val cells = (table.content as MarkdownRenderContent.Table).rows.first()
        val firstCell = cells[0]

        val spans = projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(savedHighlight(start = firstCell.canonicalRange.first, length = 1)),
            currentBookKey = "book-1",
            chapterSpan = 0 until 1000,
            blockGlobalBase = 0,
        )
        val hit = spans.getValue(table.id).single()
        assertEquals(
            "单元格落点必须带自己的 canonical 起点，否则多个单元格无法区分",
            firstCell.canonicalRange.first,
            hit.cellCanonicalStart,
        )
    }
}
