package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DocumentCacheTest {

    private fun chapter(text: String) = listOf<DocBlock>(DocBlock.Text(text))

    @Test
    fun `second get hits cache and does not reload`() {
        val cache = DocumentCache()
        val loads = AtomicInteger()
        repeat(3) {
            cache.get(0) { loads.incrementAndGet(); chapter("内容") }
        }
        assertEquals("同一章只应解析一次", 1, loads.get())
    }

    @Test
    fun `evicts least recently used beyond capacity`() {
        val cache = DocumentCache(maxChapters = 2)
        val loads = AtomicInteger()
        val load = { _: Int -> loads.incrementAndGet(); chapter("内容") }

        cache.get(0, keepIndex = 0, loader = load)
        cache.get(1, keepIndex = 1, loader = load)
        cache.get(2, keepIndex = 2, loader = load)   // 挤掉第 0 章
        assertEquals(3, loads.get())
        assertTrue(cache.cachedChapterCount() <= 2)

        cache.get(0, keepIndex = 0, loader = load)   // 已被淘汰，需重新解析
        assertEquals(4, loads.get())
    }

    @Test
    fun `keeps current chapter even under pressure`() {
        val cache = DocumentCache(maxChapters = 2)
        val loads = AtomicInteger()
        val load = { _: Int -> loads.incrementAndGet(); chapter("内容") }

        cache.get(5, keepIndex = 5, loader = load)          // 当前章
        cache.get(6, keepIndex = 5, loader = load)
        cache.get(7, keepIndex = 5, loader = load)          // 触发淘汰，但要保住第 5 章
        val before = loads.get()
        cache.get(5, keepIndex = 5, loader = load)          // 应命中缓存
        assertEquals("当前正在读的章不应被预取挤掉", before, loads.get())
    }

    @Test
    fun `char budget also triggers eviction`() {
        // 只限章数挡不住「整本压在一个超长单章」的情况，所以字符数也要限
        val cache = DocumentCache(maxChapters = 10, maxChars = 100)
        val loads = AtomicInteger()
        val load = { _: Int -> loads.incrementAndGet(); chapter("x".repeat(80)) }
        cache.get(0, keepIndex = 0, loader = load)
        cache.get(1, keepIndex = 1, loader = load)
        cache.get(2, keepIndex = 2, loader = load)
        assertTrue("超字符预算应触发淘汰，实际驻留 ${cache.cachedChapterCount()} 章", cache.cachedChapterCount() < 3)
    }

    @Test
    fun `concurrent gets for same chapter load only once`() {
        // 单飞：预取协程与前台翻页可能同时要同一章
        val cache = DocumentCache()
        val loads = AtomicInteger()
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        repeat(8) {
            Thread {
                start.await()
                cache.get(3) {
                    loads.incrementAndGet()
                    Thread.sleep(30)     // 拉长窗口，让竞争真的发生
                    chapter("内容")
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue("线程未在预期时间内完成", done.await(10, TimeUnit.SECONDS))
        assertEquals("并发取同一章只应解析一次", 1, loads.get())
    }

    @Test
    fun `clear drops everything`() {
        val cache = DocumentCache()
        val loads = AtomicInteger()
        val load = { _: Int -> loads.incrementAndGet(); chapter("内容") }
        cache.get(0, loader = load)
        cache.clear()
        assertEquals(0, cache.cachedChapterCount())
        cache.get(0, loader = load)
        assertEquals(2, loads.get())
    }
}
