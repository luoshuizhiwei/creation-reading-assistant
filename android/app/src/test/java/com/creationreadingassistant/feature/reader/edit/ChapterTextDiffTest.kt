package com.creationreadingassistant.feature.reader.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 章节编辑差异引擎的纯 JVM 测试。
 *
 * 核心不变式（[assertRoundTrip]）：
 *   把差异块按区间倒序应用回原文，**必须**逐字还原出编辑后的文本。
 *   这一条同时覆盖了偏移计算、裁剪、插入归一化的正确性。
 *
 * 第二条不变式：每个块都必须满足纠错表的落库前提
 * （`localEnd > localStart` 且 `originalText` 非空白），否则会被 `RulesRepository` 拒绝。
 */
class ChapterTextDiffTest {

    @Test
    fun `identical text produces no change`() {
        assertEquals(ChapterDiffResult.NoChange, ChapterTextDiff.diff("一二三", "一二三"))
    }

    @Test
    fun `an empty original produces no change`() {
        assertEquals(ChapterDiffResult.NoChange, ChapterTextDiff.diff("", "新增内容"))
    }

    @Test
    fun `a single character replacement yields one block`() {
        val blocks = assertRoundTrip("abc", "axc")

        assertEquals(1, blocks.size)
        assertEquals(1, blocks[0].localStart)
        assertEquals(2, blocks[0].localEnd)
        assertEquals("b", blocks[0].originalText)
        assertEquals("x", blocks[0].replacementText)
    }

    @Test
    fun `a word replacement keeps offsets tight`() {
        val original = "今天天气很好，我去公园散步。"
        val edited = "今天天气不错，我去公园散步。"

        val blocks = assertRoundTrip(original, edited)

        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertEquals("很好", block.originalText)
        assertEquals("不错", block.replacementText)
        // 锚点区间必须落在原文内且非空
        assertTrue(block.localEnd > block.localStart)
        assertEquals(original.substring(block.localStart, block.localEnd), block.originalText)
    }

    @Test
    fun `a deletion produces a block with empty replacement`() {
        val blocks = assertRoundTrip("hello world", "hello")

        assertEquals(1, blocks.size)
        assertEquals(" world", blocks[0].originalText)
        assertEquals("", blocks[0].replacementText)
    }

    @Test
    fun `a pure insertion is normalised into a one character replacement`() {
        val blocks = assertRoundTrip("abc", "abXc")

        assertEquals(1, blocks.size)
        val block = blocks[0]
        // 纯插入原文区间为空，纠错表拒绝落库 → 必须归一化成非空区间
        assertTrue("插入必须被扩成非空锚点", block.localEnd > block.localStart)
        assertFalse("锚点不能是空白", block.originalText.isBlank())
        // abc → abXc：字符级裁剪后是「b → bX」，多出来的 X 挂在左锚点上。
        assertEquals("b", block.originalText)
        assertEquals("bX", block.replacementText)
    }

    @Test
    fun `an insertion at the very beginning is normalised rightwards`() {
        val blocks = assertRoundTrip("abc", "Xabc")

        assertEquals(1, blocks.size)
        assertTrue(blocks[0].localEnd > blocks[0].localStart)
        assertFalse(blocks[0].originalText.isBlank())
    }

    @Test
    fun `an insertion at the end is normalised leftwards`() {
        val blocks = assertRoundTrip("abc", "abcX")

        assertEquals(1, blocks.size)
        assertTrue(blocks[0].localEnd > blocks[0].localStart)
        assertFalse(blocks[0].originalText.isBlank())
    }

    @Test
    fun `edits in two different paragraphs yield separate blocks`() {
        val original = "第一段落文字。\n第二段落文字。\n第三段落文字。"
        val edited = "第一段落改过。\n第二段落文字。\n第三段落改过。"

        val blocks = assertRoundTrip(original, edited)

        assertEquals(2, blocks.size)
        // 块之间不应重叠
        assertTrue(blocks[0].localEnd <= blocks[1].localStart)
    }

    @Test
    fun `deleting a whole paragraph round trips`() {
        val original = "甲\n乙\n丙"
        val edited = "甲\n丙"

        assertRoundTrip(original, edited)
    }

    @Test
    fun `every block satisfies the correction storage preconditions`() {
        val cases = listOf(
            "abc" to "axc",
            "abc" to "abXc",
            "abc" to "Xabc",
            "abc" to "abcX",
            "hello world" to "hello",
            "今天天气很好" to "今天天气不错",
            "甲\n乙\n丙" to "甲\n丙",
        )

        cases.forEach { (original, edited) ->
            val blocks = assertRoundTrip(original, edited)
            blocks.forEach { block ->
                assertTrue("$original → $edited: 区间必须非空", block.localEnd > block.localStart)
                assertFalse("$original → $edited: findText 不能空白", block.originalText.isBlank())
                assertTrue("$original → $edited: 区间必须落在原文内", block.localEnd <= original.length)
            }
        }
    }

    @Test
    fun `too many scattered edits are rejected as too large`() {
        // 每隔一行改一处 → 每处各自成块，总数超过上限。
        val original = (0 until 500).joinToString("\n") { "第 $it 行" }
        val edited = (0 until 500).joinToString("\n") { index ->
            if (index % 2 == 0) "第 $index 行改" else "第 $index 行"
        }

        val result = ChapterTextDiff.diff(original, edited)

        assertTrue("应判定为改动过大，实际 $result", result is ChapterDiffResult.TooLarge)
        assertTrue((result as ChapterDiffResult.TooLarge).blockCount > ChapterTextDiff.MAX_BLOCKS)
    }

    @Test
    fun `a large but contiguous edit stays within one block`() {
        val original = (0 until 300).joinToString("\n") { "行 $it" }
        val edited = (0 until 300).joinToString("\n") { index ->
            if (index in 100..150) "改过的行 $index" else "行 $index"
        }

        val blocks = assertRoundTrip(original, edited)

        assertEquals("连续修改应合并为一块", 1, blocks.size)
    }

    // ── 工具 ────────────────────────────────────────────────────────────

    /** 断言差异可还原，并返回块列表。 */
    private fun assertRoundTrip(original: String, edited: String): List<ChapterEditBlock> {
        val result = ChapterTextDiff.diff(original, edited)
        assertTrue("期望 Blocks，实际 $result", result is ChapterDiffResult.Blocks)
        val blocks = (result as ChapterDiffResult.Blocks).blocks
        assertTrue("至少应有一块", blocks.isNotEmpty())
        assertEquals("应用差异块后必须还原出编辑后的文本", edited, applyBlocks(original, blocks))
        return blocks
    }

    private fun applyBlocks(original: String, blocks: List<ChapterEditBlock>): String {
        var result = original
        blocks.sortedByDescending { it.localStart }.forEach { block ->
            result = result.substring(0, block.localStart) +
                block.replacementText +
                result.substring(block.localEnd)
        }
        return result
    }
}
