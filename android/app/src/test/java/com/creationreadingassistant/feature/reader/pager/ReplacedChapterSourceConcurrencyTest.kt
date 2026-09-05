package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceResult
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3.1 并发收紧（handoff 开放项 4）：多线程同时请求同一章时，
 * 整章投影只执行一次；异章互不阻塞；缓存容量与超限回调语义在并发下不变。
 */
class ReplacedChapterSourceConcurrencyTest {

    private val pool = Executors.newFixedThreadPool(12)

    private fun replaceRule(pattern: String) = ReplaceRule(
        id = "replace-1",
        name = "测试规则",
        pattern = pattern,
        replacement = "",
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )

    /** 计数 + 人为放慢的委托：放大竞态窗口。 */
    private class CountingDelegate(
        chaptersText: String,
        chapterCount: Int,
        private val loadCounter: AtomicInteger = AtomicInteger(),
        private val delayMs: Long = 60,
    ) : PagedChapterSource {
        private val perChapter = "广告正文第%02d章".format(1)
        private val texts = (0 until chapterCount).map { "第${it + 1}章开头\n广告正文$it\n第${it + 1}章结尾" }
        override val chapterCount: Int = chapterCount
        override val totalChars: Int = texts.sumOf { it.length }
        override val replaceProjectionScopeIsComplete: Boolean get() = true
        override fun chapterTitle(index: Int): String = "第${index + 1}章"
        override fun chapterStartAbs(index: Int): Int = texts.take(index).sumOf { it.length }
        override fun loadChapter(index: Int): PagedChapterContent {
            loadCounter.incrementAndGet()
            Thread.sleep(delayMs) // 放大并发竞态窗口
            return PagedChapterContent(texts[index], emptyList())
        }
    }

    private fun <T> runConcurrently(threadCount: Int, action: (Int) -> T): List<T> {
        val barrier = CyclicBarrier(threadCount)
        val futures = (0 until threadCount).map { i ->
            pool.submit<T> {
                barrier.await(10, TimeUnit.SECONDS)
                action(i)
            }
        }
        return futures.map { it.get(30, TimeUnit.SECONDS) }
    }

    // ---------- ReplacedChapterSource：同章只投影一次 ----------

    @Test
    fun `并发同章加载只委托一次且结果一致`() {
        val loads = AtomicInteger()
        val delegate = CountingDelegate("", 3, loads, delayMs = 80)
        val source = ReplacedChapterSource(delegate, "book-c", listOf(replaceRule("广告")))

        val results = runConcurrently(8) { source.loadChapterText(1) }

        assertEquals(1, loads.get())
        assertEquals(8, results.size)
        assertTrue(results.all { it == results.first() })
        assertTrue(results.first().contains("正文1"))
    }

    @Test
    fun `并发后串行命中缓存不再加载`() {
        val loads = AtomicInteger()
        val delegate = CountingDelegate("", 3, loads)
        val source = ReplacedChapterSource(delegate, "book-c", listOf(replaceRule("广告")))

        runConcurrently(6) { source.loadChapterText(0) }
        assertEquals(source.loadChapterText(0), source.loadChapterText(0))

        assertEquals(1, loads.get())
    }

    @Test
    fun `projectionForChapter 并发同章只投影一次且实例一致`() {
        val loads = AtomicInteger()
        val delegate = CountingDelegate("", 2, loads, delayMs = 70)
        val source = ReplacedChapterSource(delegate, "book-c", listOf(replaceRule("广告")))

        val projections = runConcurrently(8) { source.projectionForChapter(1) }

        assertEquals(1, loads.get())
        val nonNull = projections.mapNotNull { it }
        assertEquals(8, nonNull.size)
        // 同一缓存条目 → 同一实例（data class 等值也行，同实例更强）
        assertTrue(nonNull.all { it === nonNull.first() })
    }

    @Test
    fun `异章并发各自只加载一次`() {
        val loads = AtomicInteger()
        // 章数必须 ≤ 缓存容量 3：超出容量的章会被 LRU 逐出，同章后续请求合法地重新加载
        val delegate = CountingDelegate("", 3, loads, delayMs = 50)
        val source = ReplacedChapterSource(delegate, "book-c", listOf(replaceRule("广告")))

        val results = runConcurrently(12) { i -> source.loadChapterText(i % 3) }

        assertEquals(3, loads.get())
        assertTrue(results.filterIndexed { i, _ -> i % 3 == 0 }.all { it.contains("正文0") })
    }

    @Test
    fun `并发突发下缓存容量仍不超过3`() {
        val loads = AtomicInteger()
        val delegate = CountingDelegate("", 8, loads, delayMs = 20)
        val source = ReplacedChapterSource(delegate, "book-c", listOf(replaceRule("广告")))

        runConcurrently(10) { i -> source.loadChapterText(i % 8) }
        // 串行补齐剩余章，制造淘汰
        (0 until 8).forEach { source.loadChapterText(it) }

        assertTrue("缓存容量应 ≤ 3，实际 ${source.inspectionCacheSize()}", source.inspectionCacheSize() <= 3)
    }

    @Test
    fun `超限章并发加载保持原文且回调仍只触发一次`() {
        val loads = AtomicInteger()
        val delegate = CountingDelegate("", 1, loads, delayMs = 60)
        val unsupported = mutableListOf<BoundedReplaceResult.UnsupportedTooLarge>()
        val source = ReplacedChapterSource(
            delegate = delegate,
            bookId = "book-c",
            rules = listOf(replaceRule("广告")),
            maxSourceLength = 3,
            onUnsupportedTooLarge = { synchronized(unsupported) { unsupported.add(it) } },
        )

        val results = runConcurrently(8) { source.loadChapterText(0) }

        assertTrue(results.all { it == results.first() })
        assertEquals(1, synchronized(unsupported) { unsupported.size })
    }

    // ---------- BoundedLruCache：并发基础语义 ----------

    @Test
    fun `缓存高并发读写不超容量不崩溃`() {
        val cache = BoundedLruCache<Int, String>(maxSize = 3)
        runConcurrently(10) { i ->
            repeat(50) { j ->
                val k = (i * 7 + j) % 10
                if (j % 2 == 0) cache.put(k, "v$i-$j") else cache.get(k)
            }
            true
        }
        assertTrue(cache.size <= 3)
    }

    @Test
    fun `并发同 key 产者最终值是其中之一且可读`() {
        val cache = BoundedLruCache<Int, String>(maxSize = 3)
        val writers = listOf("a", "b", "c", "d")
        runConcurrently(4) { i ->
            cache.put(7, writers[i])
            cache.get(7)
            writers[i]
        }
        assertTrue(cache.get(7) in writers)
    }

    @Test
    fun `LRU 访问序在并发 get 后仍淘汰最旧`() {
        val cache = BoundedLruCache<Int, String>(maxSize = 3)
        cache.put(1, "1"); cache.put(2, "2"); cache.put(3, "3")
        runConcurrently(4) { cache.get(1); true } // 1 变最新
        cache.put(4, "4") // 淘汰 2（最久未访问）
        assertEquals(listOf(1, 3, 4), cache.snapshotKeysForTest().sorted())
    }

    // ---------- ReplacedSegmentedChapterSource：同逻辑章只整章投影一次 ----------

    private class CountingScopeProvider(
        private val chapterCount: Int,
        val fullChapterLoads: AtomicInteger = AtomicInteger(),
    ) : ReplaceProjectionScopeProvider {
        var oversizedChapter: Int = -1
        override fun scopeForSegment(segmentIndex: Int): ReplaceProjectionScope {
            if (segmentIndex == oversizedChapter) {
                return ReplaceProjectionScope.UnsupportedTooLarge(
                    logicalChapterIndex = segmentIndex,
                    actualSourceLength = 999_999,
                    maxSourceLength = 1_000,
                )
            }
            return ReplaceProjectionScope.Exact(
                logicalChapterIndex = segmentIndex,
                charCount = 10,
                firstSegmentIndex = segmentIndex,
                loader = {
                    fullChapterLoads.incrementAndGet()
                    Thread.sleep(60) // 放大竞态
                    "第${segmentIndex + 1}章整章广告正文"
                },
            )
        }
    }

    private fun segmentedDelegate(segmentCount: Int): PagedChapterSource =
        object : PagedChapterSource {
            override val chapterCount: Int = segmentCount
            override val totalChars: Int = segmentCount * 10
            override fun chapterTitle(index: Int): String = "第${index + 1}章"
            override fun chapterStartAbs(index: Int): Int = index * 10
            override fun loadChapter(index: Int) =
                PagedChapterContent("段落$index 广告", emptyList())
        }

    @Test
    fun `同一逻辑章的 segment 并发访问只整章读取一次`() {
        val scopeLoads = AtomicInteger()
        val provider = CountingScopeProvider(3, scopeLoads)
        val source = ReplacedSegmentedChapterSource(
            delegate = segmentedDelegate(3),
            scopeProvider = provider,
            bookId = "book-c",
            rules = listOf(replaceRule("广告")),
        )

        // segment 0/1/2 属于逻辑章 0/1/2（此处 1 segment = 1 章），并发同打 1 章
        val results = runConcurrently(8) { source.projectionForChapter(1) }

        assertEquals(1, scopeLoads.get())
        assertTrue(results.mapNotNull { it }.all { it.projection.sourceText.contains("整章") })
    }

    @Test
    fun `segmented 超限章并发回调仍全局一次`() {
        val scopeLoads = AtomicInteger()
        val provider = CountingScopeProvider(2, scopeLoads).apply { oversizedChapter = 1 }
        val unsupported = mutableListOf<BoundedReplaceResult.UnsupportedTooLarge>()
        val source = ReplacedSegmentedChapterSource(
            delegate = segmentedDelegate(2),
            scopeProvider = provider,
            bookId = "book-c",
            rules = listOf(replaceRule("广告")),
            onUnsupportedTooLarge = { synchronized(unsupported) { unsupported.add(it) } },
        )

        val results = runConcurrently(8) { source.loadChapterText(1) }

        assertTrue(results.all { it == results.first() })
        assertEquals(0, scopeLoads.get()) // 超限章绝不整章读取
        assertEquals(1, synchronized(unsupported) { unsupported.size })
    }

    @Test
    fun `segmented 并发后缓存键集合与容量正确`() {
        val scopeLoads = AtomicInteger()
        val provider = CountingScopeProvider(6, scopeLoads)
        val source = ReplacedSegmentedChapterSource(
            delegate = segmentedDelegate(6),
            scopeProvider = provider,
            bookId = "book-c",
            rules = listOf(replaceRule("广告")),
        )

        runConcurrently(10) { i -> source.projectionForChapter(i % 6) }
        // 6 个逻辑章并发、缓存容量仅 3：并发访问必然发生 LRU 逐出，
        // 被逐出的章在后续请求中合法重新加载——加载次数只能断言下界
        //（每章至少被整章读取过一次），同章并发去重由条纹锁用例单独覆盖。
        assertTrue("每章至少投影一次，实际 ${scopeLoads.get()}", scopeLoads.get() >= 6)
        assertTrue(source.inspectionProjectionCacheSize() <= 3)
        assertNotNull(source.projectionForChapter(0))
    }

    @Test
    fun `BoundedLruCache 容量为3访问第4项淘汰最久未用项且re-access刷新权重`() {
        val cache = BoundedLruCache<Int, String>(maxSize = 3)
        cache.put(1, "one")
        cache.put(2, "two")
        cache.put(3, "three")
        assertEquals(3, cache.size)
        assertEquals(listOf(1, 2, 3), cache.snapshotKeysForTest())

        // 重新访问 1，访问序更新为 [2, 3, 1]
        assertEquals("one", cache.get(1))
        assertEquals(listOf(2, 3, 1), cache.snapshotKeysForTest())

        // 插入第 4 项，必须淘汰最久未访问的 2
        cache.put(4, "four")
        assertEquals(3, cache.size)
        assertEquals(listOf(3, 1, 4), cache.snapshotKeysForTest())
        assertFalse("key 2 必须被逐出", cache.contains(2))
        assertNull("get(2) 必须返回 null", cache.get(2))

        // 重新访问 3，访问序更新为 [1, 4, 3]
        assertEquals("three", cache.get(3))
        assertEquals(listOf(1, 4, 3), cache.snapshotKeysForTest())

        // 插入第 5 项，必须淘汰最久未访问的 1
        cache.put(5, "five")
        assertEquals(3, cache.size)
        assertEquals(listOf(4, 3, 5), cache.snapshotKeysForTest())
        assertFalse("key 1 必须被逐出", cache.contains(1))
    }

    @Test
    fun `多线程并发混合读取同一章节整章投影只计算一次绝无竞态重复投影`() {
        val loads = AtomicInteger()
        val delegate = CountingDelegate("", 2, loads, delayMs = 60)
        val source = ReplacedChapterSource(delegate, "book-c", listOf(replaceRule("广告")))

        val threadCount = 12
        val barrier = CyclicBarrier(threadCount)
        val textResults = mutableListOf<String>()
        val projectionResults = mutableListOf<BoundedReplaceResult.Exact?>()

        val futures = (0 until threadCount).map { i ->
            pool.submit {
                barrier.await(10, TimeUnit.SECONDS)
                when (i % 3) {
                    0 -> {
                        val t = source.loadChapterText(0)
                        synchronized(textResults) { textResults.add(t) }
                    }
                    1 -> {
                        val c = source.loadChapter(0)
                        synchronized(textResults) { textResults.add(c.text) }
                    }
                    else -> {
                        val p = source.projectionForChapter(0)
                        synchronized(projectionResults) { projectionResults.add(p) }
                    }
                }
            }
        }
        futures.forEach { it.get(30, TimeUnit.SECONDS) }

        assertEquals("同一章节并发混合读取仅委托投影计算一次", 1, loads.get())
        assertTrue("所有文本读取结果一致", textResults.all { it == textResults.first() })
        val nonNullProjections = projectionResults.filterNotNull()
        assertTrue("投影结果实例必须唯一同一引用", nonNullProjections.all { it === nonNullProjections.first() })
    }

    @Test
    fun `条纹锁保证异章并发加载不阻塞且各自独立投影`() {
        val chapter0Started = CountDownLatch(1)
        val chapter0CanFinish = CountDownLatch(1)
        val chapter1Done = CountDownLatch(1)

        val delegate = object : PagedChapterSource {
            override val chapterCount: Int = 2
            override val totalChars: Int = 100
            override val replaceProjectionScopeIsComplete: Boolean get() = true
            override fun chapterTitle(index: Int) = "第${index + 1}章"
            override fun chapterStartAbs(index: Int) = index * 50
            override fun loadChapter(index: Int): PagedChapterContent {
                return if (index == 0) {
                    chapter0Started.countDown()
                    chapter0CanFinish.await(10, TimeUnit.SECONDS)
                    PagedChapterContent("章0广告文本", emptyList())
                } else {
                    PagedChapterContent("章1广告文本", emptyList())
                }
            }
        }
        val source = ReplacedChapterSource(delegate, "book-stripes", listOf(replaceRule("广告")))

        // 线程 1 请求第 0 章（会被阻塞在 loadChapter 内）
        val future0 = pool.submit<String> { source.loadChapterText(0) }
        assertTrue("章 0 加载必须已启动", chapter0Started.await(5, TimeUnit.SECONDS))

        // 线程 2 请求第 1 章（异章条纹锁不同，绝不应被线程 1 阻塞）
        val future1 = pool.submit<String> {
            val res = source.loadChapterText(1)
            chapter1Done.countDown()
            res
        }

        // 章 1 必须在章 0 释放之前就已经执行完毕！
        val ch1FinishedEarly = chapter1Done.await(3, TimeUnit.SECONDS)
        assertTrue("异章请求绝不应被其他章节条纹锁阻塞", ch1FinishedEarly)

        // 释放章 0
        chapter0CanFinish.countDown()
        val text0 = future0.get(10, TimeUnit.SECONDS)
        val text1 = future1.get(10, TimeUnit.SECONDS)

        assertTrue(text0.contains("章0"))
        assertTrue(text1.contains("章1"))
    }

    @Test
    fun `高并发多线程跨多章节密集淘汰绝无死锁或容量泄漏`() {
        val loads = AtomicInteger()
        val delegate = CountingDelegate("", 10, loads, delayMs = 5)
        val source = ReplacedChapterSource(delegate, "book-churn", listOf(replaceRule("广告")))

        val threadCount = 12
        val operationsPerThread = 40
        val barrier = CyclicBarrier(threadCount)

        val futures = (0 until threadCount).map { threadIdx ->
            pool.submit {
                barrier.await(10, TimeUnit.SECONDS)
                for (op in 0 until operationsPerThread) {
                    val ch = (threadIdx * 7 + op) % 10
                    val text = source.loadChapterText(ch)
                    assertTrue(text.isNotEmpty())
                }
            }
        }
        futures.forEach { it.get(20, TimeUnit.SECONDS) }

        assertTrue(
            "高频并发 LRU 淘汰后缓存容量恒 <= 3，实际=${source.inspectionCacheSize()}",
            source.inspectionCacheSize() <= 3,
        )
    }
}
