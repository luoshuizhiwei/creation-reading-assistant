package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 一次性搜索滚动聚焦请求（[SearchScrollFocusRequest]）的匹配/消费纯 JVM seam。
 *
 * 请求携带身份（所属书 / 目标章 / 渲染单元索引 / 搜索命中下标），
 * [SearchScrollFocusConsumer] 只在「当前书 + 当前章」与请求一致且渲染单元就绪时
 * 消费一次；三类结果：
 * - [SearchScrollFocusOutcome.Scroll]：身份匹配且就绪，已消费，调用方负责滚动并 ack；
 * - [SearchScrollFocusOutcome.Pending]：身份匹配但渲染单元尚未就绪（章节加载中），
 *   请求保留 pending，调用方不得 ack；
 * - [SearchScrollFocusOutcome.Discarded]：无请求 / 已消费 / 身份不匹配（跨章跨书 stale），
 *   调用方应 ack 清除。
 */
class SearchScrollFocusRequestTest {

    private fun request(
        bookKey: String = "book-1",
        chapterIndex: Int = 2,
        renderUnitIndex: Int? = 5,
        resultIndex: Int = 3,
    ) = SearchScrollFocusRequest(
        bookKey = bookKey,
        chapterIndex = chapterIndex,
        renderUnitIndex = renderUnitIndex,
        resultIndex = resultIndex,
    )

    @Test
    fun `matching book and chapter with ready units consumes once and clears`() {
        val consumer = SearchScrollFocusConsumer(request())

        assertEquals(
            SearchScrollFocusOutcome.Scroll(5),
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
        assertNull(consumer.pendingRequest)
    }

    @Test
    fun `not ready keeps request pending and never acks early`() {
        val consumer = SearchScrollFocusConsumer(request())

        // 章节加载中：不得 ack，请求必须保留，等待就绪后重试
        assertEquals(
            SearchScrollFocusOutcome.Pending,
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = false),
        )
        assertEquals(request(), consumer.pendingRequest)
        // 连续多次未就绪：仍然保留（不提前 ack 丢请求）
        assertEquals(
            SearchScrollFocusOutcome.Pending,
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = false),
        )
        assertEquals(request(), consumer.pendingRequest)
        // 就绪后消费一次并清除
        assertEquals(
            SearchScrollFocusOutcome.Scroll(5),
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
        assertNull(consumer.pendingRequest)
    }

    @Test
    fun `repeated consume does not fire twice`() {
        val consumer = SearchScrollFocusConsumer(request())

        assertEquals(
            SearchScrollFocusOutcome.Scroll(5),
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
        assertEquals(
            SearchScrollFocusOutcome.Discarded,
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
        assertEquals(
            SearchScrollFocusOutcome.Discarded,
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
    }

    @Test
    fun `cross-chapter stale request is discarded without scrolling`() {
        val consumer = SearchScrollFocusConsumer(request(chapterIndex = 2))

        // 用户已手动切到第 3 章：旧请求（目标第 2 章）不得滚动且被丢弃
        assertEquals(
            SearchScrollFocusOutcome.Discarded,
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 3, renderUnitsReady = true),
        )
        assertNull(consumer.pendingRequest)
        // 即使稍后切回目标章，旧请求也已丢弃，不得再滚动
        assertEquals(
            SearchScrollFocusOutcome.Discarded,
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
    }

    @Test
    fun `cross-book stale request is discarded without scrolling`() {
        val consumer = SearchScrollFocusConsumer(request(bookKey = "old-book"))

        // 切书后旧书请求不得在新书消费/滚动
        assertEquals(
            SearchScrollFocusOutcome.Discarded,
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
        assertNull(consumer.pendingRequest)
        assertEquals(
            SearchScrollFocusOutcome.Discarded,
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
    }

    @Test
    fun `no pending request is a safe no-op`() {
        val consumer = SearchScrollFocusConsumer(null)

        assertEquals(
            SearchScrollFocusOutcome.Discarded,
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
        assertNull(consumer.pendingRequest)
    }

    @Test
    fun `null render unit index consumes request but does not scroll`() {
        val consumer = SearchScrollFocusConsumer(request(renderUnitIndex = null))

        // 无可定位单元：请求仍被消费（ack），滚动目标为 null
        assertEquals(
            SearchScrollFocusOutcome.Scroll(null),
            consumer.consume(currentBookKey = "book-1", currentChapterIndex = 2, renderUnitsReady = true),
        )
        assertNull(consumer.pendingRequest)
    }
}
