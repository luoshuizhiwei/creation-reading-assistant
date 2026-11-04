package com.creationreadingassistant.ui.screen.reader

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选区粒度收敛（S2）的纯 JVM 测试。
 *
 * 断言刻意写成**跨 JDK 稳定**：中文分词在不同 JDK/ICU 版本下差异很大
 * （有的切成词、有的把整串汉字当一个词），所以中文用例只断言"不会被整段选中"
 * 与"区间合法"，不断言具体是哪几个字。
 */
class ReaderSelectionGranularityTest {

    @Test
    fun `an english word is selected as a whole`() {
        val text = "hello world"

        assertEquals(0..5, wordRangeAt(text, offset = 2))
        assertEquals(6..11, wordRangeAt(text, offset = 7))
    }

    @Test
    fun `a whitespace-only span falls back to a single character`() {
        val text = "hello world"
        val range = wordRangeAt(text, offset = 5)

        assertTrue("空白不应被整段选中", range.last - range.first <= MAX_WORD_CHARS)
        assertTrue(range.first in 0..text.length && range.last in 0..text.length)
    }

    @Test
    fun `a long chinese sentence is never selected entirely`() {
        val text = "这是一段很长的中文句子用来验证不会被整段选中"

        val range = wordRangeAt(text, offset = 3)

        assertTrue("区间必须合法", range.first >= 0 && range.last <= text.length && range.last > range.first)
        assertTrue(
            "单个词不得超过 $MAX_WORD_CHARS 字（当前 ${range.last - range.first}）",
            range.last - range.first <= MAX_WORD_CHARS,
        )
        assertTrue("绝不能把整句都选上", range.last - range.first < text.length)
    }

    @Test
    fun `a tiny max word length degrades to a single character`() {
        val text = "hello world"

        val range = wordRangeAt(text, offset = 2, maxWordLength = 1)

        assertEquals(1, range.last - range.first)
    }

    @Test
    fun `empty text never crashes`() {
        assertEquals(0..0, wordRangeAt("", offset = 0))
    }

    @Test
    fun `an out of bounds offset is coerced`() {
        val text = "hello"

        val range = wordRangeAt(text, offset = 999)

        assertTrue(range.first in 0..text.length && range.last in 0..text.length)
        assertTrue(range.last > range.first)
    }

    @Test
    fun `clamping keeps the raw selection when no anchor was observed`() {
        val selection = TextRange(3, 40)

        // 没监听到长按（例如用户直接拖选）→ 绝不猜、绝不改。
        assertEquals(selection, clampedInitialSelection("some text", selection, anchorOffset = null))
    }

    @Test
    fun `clamping shrinks the initial selection to the word at the anchor`() {
        val text = "hello world"
        val wideSelection = TextRange(0, text.length)

        val clamped = clampedInitialSelection(text, wideSelection, anchorOffset = 7)

        assertEquals(TextRange(6, 11), clamped)
    }
}
