package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 锁死旧版偏移公式。
 *
 * 这些用例的期望值是**手工按重构前的代码逐步算出来的**，不是从当前实现反推的。
 * 任何一条失败都意味着历史高亮/笔记的定位会整体漂移，必须当成回归而不是"更新期望值"。
 *
 * 参照实现（重构前 ReaderScreen.kt）：
 *
 *     var len = 0L
 *     for (ch in chapters) {
 *         offsets.add(len.toInt())
 *         len = len + ch.estimatedTextLength.coerceAtLeast(1) + 1L
 *     }
 */
class LegacyOffsetCodecTest {

    @Test
    fun `chapter offsets include the plus one separator per chapter`() {
        // 手算：0 → 0+100+1=101 → 101+200+1=302 → 302+50+1=353
        val lengths = listOf(100, 200, 50)
        assertEquals(listOf(0, 101, 302), LegacyOffsetCodec.chapterStartOffsets(lengths))
        assertEquals(353, LegacyOffsetCodec.totalChars(lengths))
    }

    @Test
    fun `zero length chapter still occupies one position`() {
        // estimate 至少取 1，否则相邻两章偏移相同、无法区分
        // 手算：0 → 0+1+1=2 → 2+1+1=4 → 4+10+1=15
        val lengths = listOf(0, 0, 10)
        assertEquals(listOf(0, 2, 4), LegacyOffsetCodec.chapterStartOffsets(lengths))
        assertEquals(15, LegacyOffsetCodec.totalChars(lengths))
    }

    @Test
    fun `single chapter`() {
        assertEquals(listOf(0), LegacyOffsetCodec.chapterStartOffsets(listOf(500)))
        assertEquals(501, LegacyOffsetCodec.totalChars(listOf(500)))
    }

    @Test
    fun `empty book`() {
        assertEquals(emptyList<Int>(), LegacyOffsetCodec.chapterStartOffsets(emptyList()))
        assertEquals(0, LegacyOffsetCodec.totalChars(emptyList()))
    }

    @Test
    fun `saturates instead of overflowing on absurd lengths`() {
        val huge = listOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        val offsets = LegacyOffsetCodec.chapterStartOffsets(huge)
        assertEquals(0, offsets[0])
        assertEquals(Int.MAX_VALUE, offsets[2])
        assertEquals(Int.MAX_VALUE, LegacyOffsetCodec.totalChars(huge))
    }

    @Test
    fun `block offsets accumulate length plus one and mark images as minus one`() {
        // 手算，chapterBase = 1000：
        //   文本(5)  → 1000，acc=1006
        //   图片     → -1
        //   文本(3)  → 1006，acc=1010
        //   文本(0)  → 1010，acc=1011
        val lengths = listOf<Int?>(5, null, 3, 0)
        assertEquals(listOf(1000, -1, 1006, 1010), LegacyOffsetCodec.blockOffsets(lengths, 1000))
    }

    @Test
    fun `block offsets from zero base`() {
        assertEquals(listOf(0, 4, 9), LegacyOffsetCodec.blockOffsets(listOf(3, 4, 10), 0))
    }

    // ── locator 编解码 ───────────────────────────────────────────────────

    @Test
    fun `locator round trips`() {
        assertEquals(12345, LegacyOffsetCodec.decodeLocator(LegacyOffsetCodec.encodeLocator(12345)))
        assertEquals("""{"offset":0}""", LegacyOffsetCodec.encodeLocator(0))
    }

    @Test
    fun `locator decoding tolerates extra fields and whitespace`() {
        // 超集双写的前提：新版在同一份 JSON 里加字段，旧版仍读得到 offset
        assertEquals(7, LegacyOffsetCodec.decodeLocator("""{"v":2,"ci":3,"co":7,"offset": 7 ,"href":"a.xhtml"}"""))
        assertEquals(42, LegacyOffsetCodec.decodeLocator("""{ "offset"  :  42 }"""))
    }

    @Test
    fun `locator decoding returns null on garbage`() {
        assertNull(LegacyOffsetCodec.decodeLocator(null))
        assertNull(LegacyOffsetCodec.decodeLocator(""))
        assertNull(LegacyOffsetCodec.decodeLocator("   "))
        assertNull(LegacyOffsetCodec.decodeLocator("""{"chapter":3}"""))
        assertNull(LegacyOffsetCodec.decodeLocator("not json at all"))
        // 负数不匹配 \d+，按无效处理
        assertNull(LegacyOffsetCodec.decodeLocator("""{"offset":-5}"""))
    }
}
