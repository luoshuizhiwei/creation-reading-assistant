package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 书内搜索「命中导航器」公共 seam（BookSearchSession / SearchHitNavigator）：
 * currentIndex / select / next / previous / currentTarget，空结果安全、边界明确（循环）。
 */
class SearchHitNavigatorTest {

    private fun result(
        index: Int,
        chapterIndex: Int = -1,
        range: IntRange = index * 10 until index * 10 + 2,
    ) = BookSearchResult(
        occurrenceIndex = index,
        snippet = "s$index",
        progressPercent = index * 0.1f,
        chapterIndex = chapterIndex,
        chapterTitle = if (chapterIndex >= 0) "章$chapterIndex" else "全文",
        charOffset = range.first,
        absoluteRange = range,
    )

    @Test
    fun `select returns unified target and records currentIndex`() {
        val s = BookSearchSession(bookKey = "book-1")
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0, chapterIndex = 2), result(1, chapterIndex = -1)))

        val target = s.select(0)

        assertEquals(0, s.currentIndex)
        assertEquals(
            SearchHitTarget(bookKey = "book-1", chapterIndex = 2, absoluteRange = 0 until 2, resultIndex = 0),
            target,
        )
        assertEquals(target, s.currentTarget)
    }

    @Test
    fun `select out of range is safe and keeps previous selection`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0)))
        s.select(0)

        assertNull(s.select(5))
        assertNull(s.select(-1))

        assertEquals(0, s.currentIndex)
    }

    @Test
    fun `next and previous wrap around at boundaries`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0), result(1), result(2)))

        // 未选中时 next 从第一处开始
        assertEquals(0, s.next()?.resultIndex)
        assertEquals(1, s.next()?.resultIndex)
        assertEquals(2, s.next()?.resultIndex)
        // 末处 next 循环回第一处
        assertEquals(0, s.next()?.resultIndex)
        // previous 从第一处循环回末处
        assertEquals(2, s.previous()?.resultIndex)
        assertEquals(1, s.previous()?.resultIndex)
        // 未选中时 previous 从末处开始
        val s2 = BookSearchSession()
        s2.onQueryChanged("测试")
        val run2 = s2.beginRun()
        s2.onSearchCompleted(run2, listOf(result(0), result(1), result(2)))
        assertEquals(2, s2.previous()?.resultIndex)
    }

    @Test
    fun `empty results are safe for select next previous`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, emptyList())

        assertNull(s.select(0))
        assertNull(s.next())
        assertNull(s.previous())
        assertEquals(-1, s.currentIndex)
        assertNull(s.currentTarget)
    }

    @Test
    fun `new query resets current hit`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0)))
        s.select(0)

        s.onQueryChanged("测试2")

        assertEquals(-1, s.currentIndex)
        assertNull(s.currentTarget)
    }

    @Test
    fun `completed search resets current hit`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0)))
        s.select(0)

        s.onQueryChanged("测试2")
        val run2 = s.beginRun()
        s.onSearchCompleted(run2, listOf(result(0), result(1)))

        assertEquals(-1, s.currentIndex)
        assertNull(s.currentTarget)
    }

    @Test
    fun `blank query clears current hit`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0)))
        s.select(0)

        s.onQueryChanged("")

        assertEquals(-1, s.currentIndex)
        assertNull(s.currentTarget)
        assertEquals(emptyList<BookSearchResult>(), s.results)
    }

    @Test
    fun `currentTarget tracks latest selection`() {
        val s = BookSearchSession(bookKey = "b")
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0), result(1)))

        s.select(1)
        val target = s.currentTarget

        assertEquals(1, target?.resultIndex)
        assertEquals("b", target?.bookKey)
        assertEquals(10 until 12, target?.absoluteRange)
        assertEquals(target, s.currentTarget)
    }

    @Test
    fun `consuming focus request does not clear current hit highlight`() {
        // 验收 3：命中高亮（currentTarget）只在 Sheet 关闭后持续到新查询/清除，
        // 不因聚焦请求被消费/ack 而清掉。ack 只清聚焦请求状态，与会话命中相互独立。
        val s = BookSearchSession(bookKey = "book-1")
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0, chapterIndex = 2, range = 100 until 103)))
        s.select(0)
        val target = s.currentTarget

        // 模拟 ReaderContentHost 消费聚焦请求并 ack（成功滚动）
        SearchScrollFocusConsumer(
            SearchScrollFocusRequest(
                bookKey = target!!.bookKey,
                chapterIndex = target.chapterIndex,
                renderUnitIndex = 0,
                resultIndex = target.resultIndex,
            ),
        ).consume(currentBookKey = target.bookKey, currentChapterIndex = target.chapterIndex, renderUnitsReady = true)

        assertEquals(target, s.currentTarget)
        assertEquals(0, s.currentIndex)
        assertEquals(100 until 103, s.currentTarget?.absoluteRange)
    }

    // ── 状态保留：关闭/重开面板的守卫判定 ───────────────────────

    @Test
    fun `completed same query does not restart search`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0)))

        assertEquals(false, s.shouldRestartSearch("测试"))
    }

    @Test
    fun `cancelled same query does not restart search`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        s.cancel()

        assertEquals(false, s.shouldRestartSearch("测试"))
    }

    @Test
    fun `changed query restarts search regardless of phase`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, listOf(result(0)))

        assertEquals(true, s.shouldRestartSearch("测试2"))
    }

    @Test
    fun `searching same query restarts search after panel reopen`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")

        // 面板在搜索中途关闭再打开：查询未变但运行已随面板销毁，
        // 必须允许重启，否则永久卡在 SEARCHING。
        assertEquals(true, s.shouldRestartSearch("测试"))
    }

    @Test
    fun `idle blank query does not schedule a run`() {
        val s = BookSearchSession()

        assertEquals(false, s.shouldRestartSearch(""))
    }

    @Test
    fun `run context is recorded and survives completion`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run = s.beginRun("ctx-doc-v1")
        s.onSearchCompleted(run, listOf(result(0)))

        assertEquals(true, s.matchesSearchContext("ctx-doc-v1"))
        assertEquals(false, s.matchesSearchContext("ctx-doc-v2"))
    }

    @Test
    fun `new run replaces old context`() {
        val s = BookSearchSession()
        s.onQueryChanged("测试")
        val run1 = s.beginRun("ctx-doc-v1")
        s.onSearchCompleted(run1, listOf(result(0)))

        s.onQueryChanged("测试")
        val run2 = s.beginRun("ctx-doc-v2")
        s.onSearchCompleted(run2, listOf(result(0)))

        assertEquals(true, s.matchesSearchContext("ctx-doc-v2"))
        assertEquals(false, s.matchesSearchContext("ctx-doc-v1"))
    }
}
