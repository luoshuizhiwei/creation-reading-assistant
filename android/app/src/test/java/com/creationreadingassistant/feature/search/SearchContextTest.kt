package com.creationreadingassistant.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上下文窗口抽取的边界测试。
 *
 * 这里的 bug 表现是「片段错位 / 命中被切掉」，不会崩溃，只能靠断言兜住。
 */
class SearchContextTest {

    /** 10 个数字 + 26 个大写字母，长度 36。 */
    private val body = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    @Test
    fun `extracts a window around the match with correct relative highlight range`() {
        // start = max(20-5, 0) = 15；end = min(20+3+5, 36) = 28
        val ctx = SearchContext.extract(body, offset = 20, matchLength = 3, radius = 5)
        checkNotNull(ctx)
        assertEquals(body.substring(15, 28), ctx.text)
        assertEquals(5, ctx.matchStart)
        assertEquals(8, ctx.matchEndExclusive)
        assertTrue("窗口两侧都还有正文，应标为已截断", ctx.clipped)
    }

    @Test
    fun `window covering the whole body is not clipped`() {
        val shortBody = "0123456789"
        val ctx = SearchContext.extract(shortBody, offset = 4, matchLength = 2, radius = 100)
        checkNotNull(ctx)
        assertEquals(shortBody, ctx.text)
        assertEquals(4, ctx.matchStart)
        assertEquals(6, ctx.matchEndExclusive)
        assertTrue("窗口已覆盖全文，不应标为截断", !ctx.clipped)
    }

    @Test
    fun `clips at the beginning of the text`() {
        // start = 0（左侧被吃掉 9 个字符），end = min(1+2+10, 36) = 13
        val ctx = SearchContext.extract(body, offset = 1, matchLength = 2, radius = 10)
        checkNotNull(ctx)
        assertEquals(body.substring(0, 13), ctx.text)
        assertEquals(1, ctx.matchStart)
        assertEquals(3, ctx.matchEndExclusive)
        assertTrue(ctx.clipped)
    }

    @Test
    fun `clips at the end of the text`() {
        // offset = 34，start = 24，end = 36（右侧被吃掉 8 个字符）
        val ctx = SearchContext.extract(body, offset = body.length - 2, matchLength = 2, radius = 10)
        checkNotNull(ctx)
        assertEquals(body.substring(body.length - 12), ctx.text)
        assertEquals(10, ctx.matchStart)
        assertEquals(12, ctx.matchEndExclusive)
        assertTrue(ctx.clipped)
    }

    @Test
    fun `match longer than the remaining text is truncated to fit`() {
        // offset = 30，可用长度 36-30 = 6，故命中长度被压到 6
        val ctx = SearchContext.extract(body, offset = 30, matchLength = 999, radius = 2)
        checkNotNull(ctx)
        assertEquals(body.length - 30, ctx.matchEndExclusive - ctx.matchStart)
        assertEquals(body.substring(28), ctx.text)
    }

    @Test
    fun `out of range offsets are rejected instead of producing a shifted snippet`() {
        assertNull(SearchContext.extract(body, offset = -1, matchLength = 1))
        assertNull(SearchContext.extract(body, offset = body.length + 1, matchLength = 1))
    }

    @Test
    fun `an offset exactly at the end is allowed and yields an empty match`() {
        // 偏移 == length 是合法边界（光标在文末），不应被当成越界丢掉整条命中
        val ctx = SearchContext.extract(body, offset = body.length, matchLength = 2, radius = 2)
        checkNotNull(ctx)
        assertEquals(0, ctx.matchEndExclusive - ctx.matchStart)
    }

    @Test
    fun `zero radius still yields a window carrying exactly the match`() {
        val ctx = SearchContext.extract(body, offset = 10, matchLength = 4, radius = 0)
        checkNotNull(ctx)
        assertEquals(body.substring(10, 14), ctx.text)
        assertEquals(0, ctx.matchStart)
        assertEquals(4, ctx.matchEndExclusive)
    }

    @Test
    fun `negative match length degrades to an empty match rather than throwing`() {
        val ctx = SearchContext.extract(body, offset = 10, matchLength = -5, radius = 2)
        checkNotNull(ctx)
        assertEquals(0, ctx.matchEndExclusive - ctx.matchStart)
    }
}
