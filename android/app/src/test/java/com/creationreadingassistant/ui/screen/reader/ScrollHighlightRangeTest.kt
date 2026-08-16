package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 滚动模式搜索命中高亮的公共换算 seam：
 * 全书字符区间 → 单个文本段（ReadingUnit / DocBlock.Text）内的局部区间。
 * TXT 滚动、EPUB 滚动（含 legacy 翻页）共用同一换算，越界段安全返回 null。
 */
class ScrollHighlightRangeTest {

    @Test
    fun `range fully inside segment maps to local offsets`() {
        // 段从全书偏移 10 起、长 5（10..14），命中 12..14 → 段内 2..4
        assertEquals(2 until 4, intersectTextRange(textStart = 10, textLength = 5, rangeAbs = 12 until 14))
    }

    @Test
    fun `range straddles segment start and clamps`() {
        assertEquals(0 until 2, intersectTextRange(textStart = 10, textLength = 5, rangeAbs = 8 until 12))
    }

    @Test
    fun `range straddles segment end and clamps`() {
        assertEquals(3 until 5, intersectTextRange(textStart = 10, textLength = 5, rangeAbs = 13 until 16))
    }

    @Test
    fun `range covering whole segment maps to full local range`() {
        assertEquals(0 until 5, intersectTextRange(textStart = 10, textLength = 5, rangeAbs = 5 until 20))
    }

    @Test
    fun `range before segment returns null`() {
        assertNull(intersectTextRange(textStart = 10, textLength = 5, rangeAbs = 0 until 10))
    }

    @Test
    fun `range after segment returns null`() {
        assertNull(intersectTextRange(textStart = 10, textLength = 5, rangeAbs = 15 until 20))
    }

    @Test
    fun `empty segment returns null`() {
        assertNull(intersectTextRange(textStart = 10, textLength = 0, rangeAbs = 10 until 12))
    }
}
