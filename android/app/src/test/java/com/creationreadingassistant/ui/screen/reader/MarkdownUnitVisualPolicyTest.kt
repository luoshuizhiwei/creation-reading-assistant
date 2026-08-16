package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 滚动渲染纯视觉/身份策略（[MarkdownUnitVisualPolicy]）的 JVM 契约测试。
 *
 * 锁定三件事：
 * - 单元 key = 结构 id（LazyColumn / Column 的稳定 identity，不能用下标）；
 * - blockquote 竖向引用轨道几何（每层一条、随深度外扩）与「不遮字」不变式；
 * - 代码语言标签只对非空 language 显示，且只是展示文案（不进 offset 空间）。
 */
class MarkdownUnitVisualPolicyTest {

    private val nestedSource = """
        > 引用段

        - 列表项

        > - 引用中的列表
        >   - 更深
    """.trimIndent()

    @Test
    fun `unit keys are stable structural ids in reading order`() {
        val units = MarkdownRenderModel.flatten(MarkdownParser.parse(nestedSource))
        val keys = MarkdownUnitVisualPolicy.unitKeys(units)

        assertEquals(units.map { it.id }, keys)
        assertEquals(units.map { it.id }, units.map(MarkdownUnitVisualPolicy::unitKey))
        assertEquals("key 必须唯一（LazyColumn 要求）", keys.size, keys.toSet().size)

        // 同一章节重复解析 → key 稳定，与下标无关
        val again = MarkdownUnitVisualPolicy.unitKeys(
            MarkdownRenderModel.flatten(MarkdownParser.parse(nestedSource)),
        )
        assertEquals(keys, again)
        assertTrue("key 不应退化为下标", keys != units.indices.map { it.toString() })
    }

    @Test
    fun `no quote rails for non quote units`() {
        assertEquals(emptyList<Float>(), MarkdownUnitVisualPolicy.quoteRailOffsetsDp(0))
        assertEquals(0f, MarkdownUnitVisualPolicy.quoteRailRightEdgeDp(0), 0.001f)
        assertTrue(MarkdownUnitVisualPolicy.quoteRailsStayLeftOfText(0, 0))
        assertTrue(MarkdownUnitVisualPolicy.quoteRailsStayLeftOfText(0, 3))
    }

    @Test
    fun `quote rails grow one per nesting level`() {
        assertEquals(listOf(0f), MarkdownUnitVisualPolicy.quoteRailOffsetsDp(1))
        assertEquals(listOf(0f, 6f), MarkdownUnitVisualPolicy.quoteRailOffsetsDp(2))
        assertEquals(listOf(0f, 6f, 12f), MarkdownUnitVisualPolicy.quoteRailOffsetsDp(3))

        assertEquals(
            MarkdownUnitVisualPolicy.QUOTE_RAIL_WIDTH_DP,
            MarkdownUnitVisualPolicy.quoteRailRightEdgeDp(1),
            0.001f,
        )
        assertEquals(
            MarkdownUnitVisualPolicy.QUOTE_RAIL_WIDTH_DP * 2 + MarkdownUnitVisualPolicy.QUOTE_RAIL_GAP_DP,
            MarkdownUnitVisualPolicy.quoteRailRightEdgeDp(2),
            0.001f,
        )
    }

    @Test
    fun `quote rails never reach the text indentation`() {
        // 最坏情况：无列表嵌套时 totalDepth == blockquoteDepth，文本左缘 = 16dp × depth
        for (depth in 1..6) {
            assertTrue(
                "depth=$depth 轨道不得遮字",
                MarkdownUnitVisualPolicy.quoteRailsStayLeftOfText(depth, depth),
            )
        }
        // 列表嵌套只会把文本推得更右，同样不遮字
        assertTrue(MarkdownUnitVisualPolicy.quoteRailsStayLeftOfText(1, 3))
        assertTrue(MarkdownUnitVisualPolicy.quoteRailsStayLeftOfText(3, 5))
    }

    @Test
    fun `quote rail constants are low contrast and lightweight`() {
        assertTrue("轨道不透明度必须低对比", MarkdownUnitVisualPolicy.QUOTE_RAIL_ALPHA < 0.5f)
        assertTrue("单层轨道宽度必须轻量", MarkdownUnitVisualPolicy.QUOTE_RAIL_WIDTH_DP <= 4f)
    }

    @Test
    fun `code language label only for non blank language`() {
        assertNull(MarkdownUnitVisualPolicy.codeLanguageLabel(null))
        assertNull(MarkdownUnitVisualPolicy.codeLanguageLabel(""))
        assertNull(MarkdownUnitVisualPolicy.codeLanguageLabel("   "))
        assertEquals("kotlin", MarkdownUnitVisualPolicy.codeLanguageLabel("kotlin"))
        assertEquals("kotlin", MarkdownUnitVisualPolicy.codeLanguageLabel(" kotlin "))
    }
}
