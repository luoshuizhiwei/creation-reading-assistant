package com.creationreadingassistant.ui.screen.reader.tts

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [buildSentenceHighlighted] 的偏移基准换算：
 * - ttsSentenceRange 是章内偏移（相对 TTS contentText），需减去 blockLocalBase（块在章内偏移）；
 * - searchRangeAbs 是全书偏移（相对 [com.creationreadingassistant.ui.screen.reader.SearchHitTarget]），
 *   需减去 blockGlobalOffset（块在全书偏移）。
 *
 * 非首章（chapterBase > 0）下两个基准不同：旧实现把 searchRangeAbs 误减 blockLocalBase，
 * 导致全书高亮区间在非首章全部越界。
 */
class BuildSentenceHighlightedTest {

    /** 非首章：章起始 1000，块起始在章内偏移 10 → 全书偏移 1010。 */
    private val chapterBase = 1000
    private val blockGlobalOffset = 1010
    private val blockText = "第一句正文内容。第二句正文内容。"

    private val bg = Color(0xFFE8F5E9)

    /** 返回带背景样式的区间列表（含首不含尾）。 */
    private fun backgroundRanges(ann: AnnotatedString): List<IntRange> =
        ann.spanStyles
            .filter { it.item.background != Color.Unspecified }
            .map { it.start until it.end }

    @Test
    fun `search highlight uses book global offset in non-first chapter`() {
        val ann = buildSentenceHighlighted(
            text = blockText,
            blockGlobalOffset = blockGlobalOffset,
            chapterBase = chapterBase,
            ttsSentenceRange = null,
            bg = bg,
            searchRangeAbs = (blockGlobalOffset + 2) to (blockGlobalOffset + 5),
        )
        // 命中在全书 1012..1015 → 块内 2..5
        assertEquals(listOf(2 until 5), backgroundRanges(ann))
    }

    @Test
    fun `search highlight starting exactly at block start maps to local zero`() {
        val ann = buildSentenceHighlighted(
            text = blockText,
            blockGlobalOffset = blockGlobalOffset,
            chapterBase = chapterBase,
            ttsSentenceRange = null,
            bg = bg,
            searchRangeAbs = blockGlobalOffset to (blockGlobalOffset + 3),
        )
        assertEquals(listOf(0 until 3), backgroundRanges(ann))
    }

    @Test
    fun `tts sentence highlight still uses chapter relative range`() {
        // 章内 12..20 = 块内（12 - 10）..（20 - 10）= 2..10
        val ann = buildSentenceHighlighted(
            text = blockText,
            blockGlobalOffset = blockGlobalOffset,
            chapterBase = chapterBase,
            ttsSentenceRange = 12 to 20,
            bg = bg,
        )
        assertEquals(listOf(2 until 10), backgroundRanges(ann))
    }

    @Test
    fun `search and tts highlights coexist with independent bases`() {
        val ann = buildSentenceHighlighted(
            text = blockText,
            blockGlobalOffset = blockGlobalOffset,
            chapterBase = chapterBase,
            ttsSentenceRange = 12 to 20,
            bg = bg,
            searchRangeAbs = (blockGlobalOffset + 5) to (blockGlobalOffset + 8),
        )
        // TTS：2..10；搜索：5..8（全书偏移基准）
        assertEquals(listOf(2 until 10, 5 until 8), backgroundRanges(ann))
    }

    @Test
    fun `search range outside block is skipped safely`() {
        val ann = buildSentenceHighlighted(
            text = blockText,
            blockGlobalOffset = blockGlobalOffset,
            chapterBase = chapterBase,
            ttsSentenceRange = null,
            bg = bg,
            searchRangeAbs = (blockGlobalOffset - 50) to (blockGlobalOffset - 40),
        )
        assertEquals(emptyList<IntRange>(), backgroundRanges(ann))
    }

    @Test
    fun `no ranges returns plain text without styles`() {
        val ann = buildSentenceHighlighted(
            text = blockText,
            blockGlobalOffset = blockGlobalOffset,
            chapterBase = chapterBase,
            ttsSentenceRange = null,
            bg = bg,
        )
        assertEquals(emptyList<IntRange>(), backgroundRanges(ann))
        assertEquals(blockText, ann.text)
    }
}
