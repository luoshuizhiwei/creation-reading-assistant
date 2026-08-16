package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.layout.AnywhereBreakOracle
import com.creationreadingassistant.feature.reader.layout.FakeTextRuler
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PagedReaderControllerLifecycleTest {

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
