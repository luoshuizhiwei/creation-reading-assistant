package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.AnywhereBreakOracle
import com.creationreadingassistant.feature.reader.layout.FakeTextRuler
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨章导航回归：真机反馈「章节第一页往前翻页，跨过整章回到上一章第一页」。
 * 预期语义：章首 prev 落在上一章最后一页；章末 next 落在下一章第一页。
 * 缓存命中与未命中（异步 loadChapter）两条路径都必须满足。
 */
class PagedReaderControllerCrossChapterNavTest {

    private fun buildController(scope: CoroutineScope): PagedReaderController {
        val chapters = listOf(
            DocChapter(0, "第一章", 0, 4000),
            DocChapter(1, "第二章", 4000, 4000),
            DocChapter(2, "第三章", 8000, 4000),
        )
        val text = "字".repeat(12000)
        return PagedReaderController(
            source = TxtChapterSource(fullText = text, chapters = chapters),
            cfg = LayoutConfig(
                contentWidthPx = 80f,
                contentHeightPx = 80f,
                fontSizePx = 20f,
            ),
            ruler = FakeTextRuler(fontSizePx = 20f),
            oracle = AnywhereBreakOracle(),
            scope = scope,
        )
    }

    @Test
    fun `prev from chapter head with prefetched cache lands on previous chapter last page`() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = buildController(scope)
        try {
            controller.open(4000)
            waitUntil { controller.layout != null && !controller.isLayingOut && controller.chapterIndex == 1 }
            assertEquals(0, controller.pageIndex)
            assertTrue("章内应有多页，否则夹具失效", controller.pageCount > 1)
            waitUntil { controller.frameAt(-1) != null }

            controller.prevPage()

            waitUntil { controller.chapterIndex == 0 && !controller.isLayingOut }
            assertEquals(
                "跨章回退（缓存命中）应落在上一章最后一页",
                controller.layout!!.pages.lastIndex,
                controller.pageIndex,
            )
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun `prev from chapter head without waiting for prefetch still lands on last page`() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = buildController(scope)
        try {
            controller.open(4000)
            waitUntil { controller.layout != null && !controller.isLayingOut && controller.chapterIndex == 1 }
            assertEquals(0, controller.pageIndex)

            // 不等预排：无论上一章是否已进缓存，prevPage 的两条路径
            // （applyChapter(lastIndex) / loadChapter { size - 1 }）都必须落在最后一页。
            controller.prevPage()

            waitUntil { controller.chapterIndex == 0 && !controller.isLayingOut }
            assertEquals(
                "跨章回退（未预排，异步加载）应落在上一章最后一页",
                controller.layout!!.pages.lastIndex,
                controller.pageIndex,
            )
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun `next from chapter tail lands on next chapter first page`() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = buildController(scope)
        try {
            controller.open(4000)
            waitUntil { controller.layout != null && !controller.isLayingOut && controller.chapterIndex == 1 }
            waitUntil { controller.frameAt(1) != null }

            repeat(controller.pageCount) { controller.nextPage() }

            waitUntil { controller.chapterIndex == 2 && !controller.isLayingOut }
            assertEquals("跨章前进应落在下一章第一页", 0, controller.pageIndex)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun `next tap during initial layout is replayed after first page is ready`() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = buildController(scope)
        try {
            controller.open(0)
            controller.nextPage()

            waitUntil {
                !controller.isLayingOut &&
                    (controller.pageIndex > 0 || controller.chapterIndex > 0)
            }
            assertTrue("首章应有足够页数验证排版期间的下一页意图", controller.pageIndex > 0)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun `multiple turns during initial layout are replayed in input order`() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = buildController(scope)
        try {
            controller.open(0)
            controller.nextPage()
            controller.nextPage()
            controller.prevPage()

            waitUntil { !controller.isLayingOut && controller.pageIndex == 1 }
            assertEquals("首屏排版期间的连续手势应按顺序回放", 0, controller.chapterIndex)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            if (System.nanoTime() >= deadline) error("Timed out waiting for controller state")
            Thread.sleep(10)
        }
    }
}
