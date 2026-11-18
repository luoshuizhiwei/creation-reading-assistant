package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.layout.AnywhereBreakOracle
import com.creationreadingassistant.feature.reader.layout.FakeTextRuler
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import com.creationreadingassistant.feature.reader.layout.LayoutParagraph
import com.creationreadingassistant.feature.reader.layout.TextRuler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PagedReaderControllerLifecycleTest {

    @Test
    fun `progress reuses chapter estimates and learns newly loaded character counts`() = runBlocking {
        val estimateReads = AtomicInteger()
        val counts = listOf(100, 240, 100)
        val source = object : PagedChapterSource {
            override val chapterCount = 3
            override val totalChars = 3000
            override val chapterLengthsAreEstimated = true
            override fun chapterTitle(index: Int) = "测试 EPUB"
            override fun chapterStartAbs(index: Int) = index * 1000
            override fun chapterEstimatedCharCount(index: Int): Int {
                estimateReads.incrementAndGet()
                return 1000
            }
            override fun loadChapter(index: Int): PagedChapterContent {
                val text = "文".repeat(counts[index])
                return PagedChapterContent(text, listOf(LayoutBlock.Text(LayoutParagraph(text))))
            }
        }
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = controller(source, scope)
        try {
            // 在实测章长到达前创建估算快照，加载和预取必须使它失效。
            assertEquals(0f, controller.progressPercent, 0f)
            controller.open(1000)
            withTimeout(5000) {
                controller.privateJob("layoutJob")!!.join()
                controller.privateJob("prefetchJob")!!.join()
            }
            repeat(100) {
                assertEquals(100f * 100 / 440, controller.progressPercent, 0.00001f)
            }
            assertEquals("chapter metadata should be read once per controller", 3, estimateReads.get())
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun `close stops CPU pagination before measuring remaining paragraphs or publishing interim pages`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val measured = AtomicInteger()
        val fake = FakeTextRuler(20f)
        val ruler = object : TextRuler {
            override val fontSizePx = fake.fontSizePx
            override val typefaceKey = fake.typefaceKey

            override fun getTextWidths(text: String, out: FloatArray) {
                fake.getTextWidths(text, out)
                if (measured.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
            }
        }
        val paragraph = "测试正文。".repeat(100)
        val source = object : PagedChapterSource {
            override val chapterCount = 1
            override val totalChars = paragraph.length * 200
            override fun chapterTitle(index: Int) = "测试 TXT"
            override fun chapterStartAbs(index: Int) = 0
            override fun loadChapter(index: Int) = PagedChapterContent(
                paragraph.repeat(200),
                List(200) { LayoutBlock.Text(LayoutParagraph(paragraph, charOffset = it * paragraph.length)) },
            )
        }
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = PagedReaderController(
            source, LayoutConfig(200f, 200f, fontSizePx = 20f), ruler, AnywhereBreakOracle(), scope,
        )
        try {
            controller.open(0)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            controller.privateJob("layoutJob")!!.invokeOnCompletion { completed.countDown() }
            controller.close()
            release.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals("cancelled pagination must stop before the next paragraph", 1, measured.get())
            assertNull("closed controller must not publish an interim layout", controller.layout)
        } finally {
            release.countDown()
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun `close cancels an in flight layout job`() {
        val source = BlockingLayoutSource()
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = controller(source, scope)

        try {
            controller.open(0)
            assertTrue("layout did not start", source.started.await(2, TimeUnit.SECONDS))
            val layoutJob = controller.privateJob("layoutJob")
            assertNotNull("expected an active layout job", layoutJob)

            controller.close()

            assertFalse("close must cancel the active layout job", layoutJob!!.isActive)
        } finally {
            source.release.countDown()
            scope.cancel()
        }
    }

    @Test
    fun `close cancels an in flight neighbor prefetch job`() {
        val source = BlockingPrefetchSource()
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = controller(source, scope)

        try {
            controller.open(0)
            assertTrue("neighbor prefetch did not start", source.prefetchStarted.await(2, TimeUnit.SECONDS))
            val prefetchJob = controller.privateJob("prefetchJob")
            assertNotNull("expected an active prefetch job", prefetchJob)
            assertTrue("expected the prefetch job to be active", prefetchJob!!.isActive)

            controller.close()

            assertFalse("close must cancel the active prefetch job", prefetchJob.isActive)
        } finally {
            source.releasePrefetch.countDown()
            scope.cancel()
        }
    }

    @Test
    fun `opening the prefetched chapter reuses its active read`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val neighborReads = AtomicInteger()
        val source = object : PagedChapterSource {
            override val chapterCount = 2
            override val totalChars = 2
            override fun chapterTitle(index: Int) = "测试 TXT"
            override fun chapterStartAbs(index: Int) = index

            override fun loadChapter(index: Int): PagedChapterContent {
                if (index == 1) {
                    neighborReads.incrementAndGet()
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                }
                val text = if (index == 0) "甲" else "乙"
                return PagedChapterContent(text, listOf(LayoutBlock.Text(LayoutParagraph(text))))
            }
        }
        // Controller calls and its host coroutine use the same dispatcher.
        val scope = CoroutineScope(coroutineContext + Job())
        val controller = controller(source, scope)
        try {
            controller.open(0)
            withTimeout(5000) { started.await() }
            controller.open(1)
            release.countDown()
            withTimeout(5000) { controller.privateJob("layoutJob")!!.join() }
            assertEquals(1, controller.chapterIndex)
            assertEquals(1, neighborReads.get())
            assertNull(controller.loadError)
        } finally {
            release.countDown()
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun `close leaves the shared scope alive`() {
        val source = BlockingLayoutSource()
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = controller(source, scope)

        try {
            controller.open(0)
            assertTrue("layout did not start", source.started.await(2, TimeUnit.SECONDS))

            controller.close()

            // close 只取消本 controller 派生的 job；宿主共享的 rememberCoroutineScope
            // 必须存活，否则同屏其他 effect 会被误杀。
            assertTrue("close must not cancel the shared scope", scope.isActive)
        } finally {
            source.release.countDown()
            scope.cancel()
        }
    }

    private fun controller(source: PagedChapterSource, scope: CoroutineScope) = PagedReaderController(
        source = source,
        cfg = LayoutConfig(
            contentWidthPx = 200f,
            contentHeightPx = 200f,
            fontSizePx = 20f,
        ),
        ruler = FakeTextRuler(fontSizePx = 20f),
        oracle = AnywhereBreakOracle(),
        scope = scope,
    )

    private fun PagedReaderController.privateJob(name: String): Job? =
        javaClass.getDeclaredField(name).let { field ->
            field.isAccessible = true
            field.get(this) as? Job
        }

    private class BlockingLayoutSource : PagedChapterSource {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        override val chapterCount = 1
        override val totalChars = 1
        override fun chapterTitle(index: Int) = ""
        override fun chapterStartAbs(index: Int) = 0

        override fun loadChapter(index: Int): PagedChapterContent {
            started.countDown()
            release.await(2, TimeUnit.SECONDS)
            return EMPTY_CHAPTER
        }
    }

    private class BlockingPrefetchSource : PagedChapterSource {
        val prefetchStarted = CountDownLatch(1)
        val releasePrefetch = CountDownLatch(1)

        override val chapterCount = 2
        override val totalChars = 2
        override fun chapterTitle(index: Int) = ""
        override fun chapterStartAbs(index: Int) = index

        override fun loadChapter(index: Int): PagedChapterContent {
            if (index == 1) {
                prefetchStarted.countDown()
                releasePrefetch.await(2, TimeUnit.SECONDS)
            }
            return EMPTY_CHAPTER
        }
    }

    private companion object {
        val EMPTY_CHAPTER = PagedChapterContent(text = "", blocks = emptyList())
    }
}
