package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderContent
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderKind
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F3 / F3.1：Markdown 渲染单元 → 选区坐标的映射证明（纯 JVM，无 Compose）。
 *
 * F3.1 改名：被测函数由 `markdownUnitSourceRange` 改为 [markdownUnitCanonicalRange]
 * / [markdownCellCanonicalRange]（它们返回 canonical 空间，与 `sourceRange` 不是
 * 同一个空间，旧名会诱导混写）。文件名因范围完整性要求保持不变。
 *
 * 坐标空间：Markdown 持久化统一用 **canonical** 空间 —— 章 [startOffset]/[charCount]、
 * 分页 [com.creationreadingassistant.feature.reader.pager.MarkdownChapterSource]、
 * 搜索、TTS、既有 [markdownUnitGlobalOffset] 全部是 canonical 坐标。这里断言的
 * 「source 半开区间」即该空间的半开区间，**不是** display offset，也**不是**源码
 * [MarkdownRenderUnit.sourceRange]。
 *
 * 三条硬约束（任务书）：
 * - 只有能精确映射成半开区间时才允许提交选区；
 * - 非文本单元（分隔线、表格整体）不得生成伪选区；
 * - display offset ≠ source offset，映射必须走既有 canonical 契约，不得 substring 猜测。
 */
class MarkdownUnitSourceRangeTest {

    private fun chapterOf(source: String) = MarkdownParser.parse(source)

    private fun unitsOf(source: String) = MarkdownRenderModel.flatten(chapterOf(source))

    @Test
    fun `whole document parse unit carries an exact half open range`() {
        val src = "第一章正文。\n"
        val units = unitsOf(src)
        val paragraph = units.single { it.kind == MarkdownRenderKind.PARAGRAPH }

        // blockGlobalBase = 0（整本解析的 canonicalRange 已是全书全局）
        val range = requireNotNull(markdownUnitCanonicalRange(paragraph, blockGlobalBase = 0))
        assertEquals(0, range.first)
        assertEquals(src.trimEnd().length, range.last + 1)
    }

    @Test
    fun `streaming chapter unit adds its chapter base`() {
        // 流式逐章解析：canonicalRange 是**章内局部**坐标，必须叠加 chapterBase
        // 才是全书坐标（与 markdownScrollSearchHits 的 blockGlobalBase 同一换算）。
        val src = "章二内容。\n"
        val units = MarkdownRenderModel.flatten(MarkdownParser.parse(src, sourceOffsetShift = 100))
        val paragraph = units.single { it.kind == MarkdownRenderKind.PARAGRAPH }

        val local = requireNotNull(markdownUnitCanonicalRange(paragraph, blockGlobalBase = 0))
        assertEquals("章内局部起点", 0, local.first)

        val global = requireNotNull(markdownUnitCanonicalRange(paragraph, blockGlobalBase = 100))
        assertEquals(100, global.first)
        assertEquals(100 + src.trimEnd().length, global.last + 1)
    }

    @Test
    fun `duplicate visible text maps to distinct ranges`() {
        val src = "重复文本\n\n重复文本\n"
        val chapter = chapterOf(src)
        val units = unitsOf(src).filter { it.kind == MarkdownRenderKind.PARAGRAPH }
        assertEquals(2, units.size)

        val first = requireNotNull(markdownUnitCanonicalRange(units[0], blockGlobalBase = 0))
        val second = requireNotNull(markdownUnitCanonicalRange(units[1], blockGlobalBase = 0))
        assertTrue("重复文本必须落到两个互不相交的坐标区间", first.last < second.first)
        assertEquals(
            "重复文本",
            chapter.canonicalText.substring(units[0].canonicalRange.first, units[0].canonicalRange.last + 1),
        )
    }

    @Test
    fun `heading list quote and code block all resolve ranges`() {
        val src = "# 标题\n\n- 列表项\n\n> 引用文本\n\n```\ncode();\n```\n"
        val units = unitsOf(src)
        listOf(
            MarkdownRenderKind.HEADING,
            MarkdownRenderKind.LIST_ITEM,
            MarkdownRenderKind.PARAGRAPH, // 引用内的段落
            MarkdownRenderKind.CODE_BLOCK,
        ).forEach { kind ->
            val unit = units.first { it.kind == kind }
            val range = requireNotNull(markdownUnitCanonicalRange(unit, blockGlobalBase = 0))
            assertTrue("$kind 必须有非空选区区间", range.last > range.first)
        }
    }

    @Test
    fun `table cell resolves its own range`() {
        val src = "| 甲 | 乙 |\n| --- | --- |\n| 丙 | 丁 |\n"
        val chapter = chapterOf(src)
        val table = unitsOf(src).single { it.kind == MarkdownRenderKind.TABLE }
        val cell = (table.content as MarkdownRenderContent.Table).rows.first().first()

        // F3.1（Qoder P3-1）：`markdownCellSourceRange` 无生产调用方（本轮表格单元格
        // 不可整块选择），已删除 —— 保留它等于维护一个只有测试在用的「假能力」。
        // 这里改为断言**生产真实行为**：整表不可选，单元格坐标只在持久高亮投影里
        // 作为落点判别符使用（见 MarkdownPersistentHighlightProjectionTest）。
        val range = markdownUnitCanonicalRange(table, blockGlobalBase = 0)
        assertNull("整表不可整块选择 ⇒ 单元格不得单独产出整块选区", range)
        assertTrue(
            "单元格 canonical 起点必须能与整表区分（供高亮落点判别）",
            cell.canonicalRange.first >= table.canonicalRange.first,
        )
        assertEquals(
            "丙",
            chapter.canonicalText.substring(cell.canonicalRange.first, cell.canonicalRange.last + 1),
        )
        // 历史 bug 回归：整表不得被当成文本载体（旧实现会写出字面量 "[Table]"）。
        // 由投影层承担该约束（表格只按单元格落点），见
        // MarkdownPersistentHighlightProjectionTest 的单元格落点判别用例。
        assertFalse("整表不得可整块选择", markdownUnitIsBlockSelectable(table))
    }

    @Test
    fun `non text units never produce a selectable range`() {
        val src = "段落\n\n---\n\n尾段\n"
        val rule = unitsOf(src).single { it.kind == MarkdownRenderKind.HORIZONTAL_RULE }
        assertNull("分隔线不得生成选区", markdownUnitCanonicalRange(rule, blockGlobalBase = 0))
    }

    @Test
    fun `table as a whole never produces a block selection`() {
        val src = "| 甲 | 乙 |\n| --- | --- |\n| 丙 | 丁 |\n"
        val table = unitsOf(src).single { it.kind == MarkdownRenderKind.TABLE }
        assertNull(
            "表格整体不得生成伪选区（历史上会写出字面量 \"[Table]\"）",
            markdownUnitCanonicalRange(table, blockGlobalBase = 0),
        )
    }

    @Test
    fun `code block language label never enters the range`() {
        // 语言标签是纯展示，不得进入 offset 空间。
        val src = "```kotlin\nval x = 1\n```\n"
        val chapter = chapterOf(src)
        val code = unitsOf(src).single { it.kind == MarkdownRenderKind.CODE_BLOCK }
        val range = requireNotNull(markdownUnitCanonicalRange(code, blockGlobalBase = 0))
        val text = chapter.canonicalText.substring(code.canonicalRange.first, code.canonicalRange.last + 1)
        assertTrue("语言标签不得出现在选区文本里", !text.contains("kotlin"))
        assertEquals(text.length, range.last + 1 - range.first)
    }

    @Test
    fun `invalid block global base yields no selection`() {
        val paragraph = unitsOf("段落\n").single { it.kind == MarkdownRenderKind.PARAGRAPH }
        assertNull(markdownUnitCanonicalRange(paragraph, blockGlobalBase = -1))
    }

    @Test
    fun `selection range never uses display offset as source offset`() {
        // display 文本（去标记）比源码短；若误把 display offset 当坐标，
        // 区间长度会等于 canonical 长度而非源码长度 —— 这里钉死两者确实不同，
        // 并且选区长度必须等于 canonical 切片长度（canonical 空间坐标）。
        val src = "**加粗**文本\n"
        val chapter = chapterOf(src)
        val unit = unitsOf(src).single { it.kind == MarkdownRenderKind.PARAGRAPH }
        val range = requireNotNull(markdownUnitCanonicalRange(unit, blockGlobalBase = 0))

        assertTrue("源码含标记符，必然长于 canonical 文本", src.trimEnd().length > chapter.canonicalText.length)
        assertEquals(chapter.canonicalText.length, range.last + 1 - range.first)
        // 且明确不等于源码长度（证明没有被误当成 source 空间）
        assertTrue(range.last + 1 - range.first != src.trimEnd().length)
    }
}
