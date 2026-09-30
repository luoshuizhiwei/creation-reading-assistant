package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F3.1 任务 C：非分页 / 旧 EPUB 文本块的持久高亮投影（纯函数）。
 *
 * 输入：文本块半开区间 `[gOff, gOff + block.text.length)` 与已保存高亮的
 * (起点, source length)。输出：该块内的**局部**半开区间。
 *
 * 硬约束（任务书）：
 * - 不使用文本搜索、indexOf 或首次匹配（输入里根本没有文本字段）；
 * - 重复段落按各自 gOff 区分，不得串块；
 * - 跨两个文本块的高亮，两块各渲染自己的交集；
 * - stale / 非法 / 负坐标、空区间安全忽略；
 * - 图片、Markdown 块、非文本块不得渲染伪高亮。
 */
class EpubBlockPersistentHighlightTest {

    // ── 基本投影 ────────────────────────────────────────────────────────

    @Test
    fun `a highlight fully inside one block projects to its local range`() {
        val r = epubBlockHighlightLocalRange(
            blockStart = 100,
            blockLength = 10,
            absStart = 102,
            absLength = 3,
        )
        assertEquals(2 until 5, r)
    }

    @Test
    fun `a highlight covering the whole block yields the full local range`() {
        val r = epubBlockHighlightLocalRange(
            blockStart = 50,
            blockLength = 6,
            absStart = 50,
            absLength = 6,
        )
        assertEquals(0 until 6, r)
    }

    // ── 裁剪而非丢弃 ────────────────────────────────────────────────────

    @Test
    fun `a highlight starting before the block is clipped to the block start`() {
        val r = epubBlockHighlightLocalRange(
            blockStart = 100,
            blockLength = 10,
            absStart = 90,
            absLength = 15, // 覆盖到 105
        )
        assertEquals(0 until 5, r)
    }

    @Test
    fun `a highlight extending past the block is clipped to the block end`() {
        val r = epubBlockHighlightLocalRange(
            blockStart = 100,
            blockLength = 10,
            absStart = 105,
            absLength = 999,
        )
        assertEquals(5 until 10, r)
    }

    // ── 重复段落不串块 ──────────────────────────────────────────────────

    @Test
    fun `duplicate paragraphs land only on the block matching gOff`() {
        // 三个 gOff 不同的同文本块：0、11、22（每块长 10 + 分隔符 1）
        val first = epubBlockHighlightLocalRange(0, 10, absStart = 2, absLength = 3)
        val second = epubBlockHighlightLocalRange(11, 10, absStart = 2, absLength = 3)
        val third = epubBlockHighlightLocalRange(22, 10, absStart = 2, absLength = 3)

        assertEquals("只有第一块命中", 2 until 5, first)
        assertNull("第二块不得命中落在第一块的坐标", second)
        assertNull("第三块不得命中落在第一块的坐标", third)
    }

    @Test
    fun `the same absolute highlight reaches each block only via its own gOff`() {
        val r1 = epubBlockHighlightLocalRange(0, 10, absStart = 8, absLength = 6)
        val r2 = epubBlockHighlightLocalRange(11, 10, absStart = 8, absLength = 6)
        assertEquals("第一块渲染尾部交集", 8 until 10, r1)
        assertEquals("第二块渲染头部交集", 0 until 3, r2)
    }

    // ── 跨两个相邻文本块：各渲染自己的交集 ──────────────────────────────

    @Test
    fun `a highlight crossing two adjacent blocks renders its own intersection on each`() {
        val spanStart = 8
        val spanLength = 6 // [8, 14)
        val r1 = epubBlockHighlightLocalRange(0, 10, spanStart, spanLength)
        val r2 = epubBlockHighlightLocalRange(11, 10, spanStart, spanLength)
        assertEquals(8 until 10, r1)
        assertEquals(0 until 3, r2)
        // 两段交集长度之和 == 高亮总长 **减去** 块间分隔符的 1 个字符：
        // 坐标 10 是 LegacyOffsetCodec 为相邻块预留的换行分隔符，它不属于任何块的文本，
        // 因此不会被任何块渲染 —— 这是正确性（不是丢字），本用例把它钉死。
        assertEquals(
            spanLength - 1,
            (r1!!.last + 1 - r1.first) + (r2!!.last + 1 - r2.first),
        )
    }

    // ── stale / 非法 / 负坐标 / 空区间 ──────────────────────────────────

    @Test
    fun `zero and negative lengths are ignored`() {
        assertNull(epubBlockHighlightLocalRange(100, 10, 102, 0))
        assertNull(epubBlockHighlightLocalRange(100, 10, 102, -5))
    }

    @Test
    fun `negative absolute start is ignored`() {
        assertNull(epubBlockHighlightLocalRange(100, 10, -1, 5))
    }

    @Test
    fun `unknown block offset is ignored`() {
        assertNull("gOff = -1 表示坐标未知", epubBlockHighlightLocalRange(-1, 10, 100, 5))
    }

    @Test
    fun `empty and non text blocks are ignored`() {
        assertNull(epubBlockHighlightLocalRange(100, 0, 100, 5))
        assertNull(epubBlockHighlightLocalRange(100, -3, 100, 5))
    }

    @Test
    fun `a highlight entirely outside the block is not projected`() {
        assertNull(epubBlockHighlightLocalRange(100, 10, 500, 5))
        assertNull(epubBlockHighlightLocalRange(100, 10, 90, 5)) // 刚好在块前结束
    }

    @Test
    fun `touching only the boundary yields nothing`() {
        // 高亮在块起点之前结束（end == blockStart）不算命中
        assertNull(epubBlockHighlightLocalRange(100, 10, 95, 5))
        // 高亮从块终点开始也不算命中
        assertNull(epubBlockHighlightLocalRange(100, 10, 110, 5))
    }

    // ── 非文本块不得渲染伪高亮 ──────────────────────────────────────────

    @Test
    fun `non text blocks never render a highlight`() {
        // 图片块 gOff = -1（LegacyOffsetCodec 约定），Markdown 块同样
        assertTrue(
            "图片 / Markdown 块不产生局部区间",
            epubBlockHighlightLocalRange(-1, 0, 0, 10) == null,
        )
    }

    // ── 溢出安全 ────────────────────────────────────────────────────────

    @Test
    fun `extreme offsets do not overflow or crash`() {
        val r = epubBlockHighlightLocalRange(
            blockStart = Int.MAX_VALUE - 10,
            blockLength = 10,
            absStart = Int.MAX_VALUE - 5,
            absLength = 100,
        )
        assertEquals(5 until 10, r)
    }
}
