package com.creationreadingassistant.feature.reader.pager

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.AnywhereBreakOracle
import com.creationreadingassistant.feature.reader.layout.FakeTextRuler
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨章导航回归：真机反馈「章节第一页往前翻页，跨过整章回到上一章第一页」。
 * 预期语义：章首 prev 落在上一章最后一页；章末 next 落在下一章第一页。
 * 缓存命中与未命中（异步 loadChapter）两条路径都必须满足。
 */
class PagedReaderControllerCrossChapterNavTest {

    private val baseControllerDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "paged-controller-test").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val controllerDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            baseControllerDispatcher.dispatch(context) {
                try {
                    block.run()
                } finally {
                    // JVM 单测没有 Compose 帧泵；每个 controller 任务结束后显式发布快照通知。
                    Snapshot.sendApplyNotifications()
                }
            }
        }
    }

    @After
    fun tearDown() {
        baseControllerDispatcher.close()
    }

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
    fun `prev from chapter head with prefetched cache lands on previous chapter last page`() = runBlocking {
        val scope = CoroutineScope(Job() + controllerDispatcher)
        val controller = buildController(scope)
        try {
            controller.open(4000)
            awaitControllerState(controller) { controller.layout != null && !controller.isLayingOut && controller.chapterIndex == 1 }
            assertEquals(0, controller.pageIndex)
            assertTrue("章内应有多页，否则夹具失效", controller.pageCount > 1)
            awaitControllerState(controller) { controller.frameAt(-1) != null }

            controller.prevPage()

            awaitControllerState(controller) { controller.chapterIndex == 0 && !controller.isLayingOut }
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
    fun `prev from chapter head without waiting for prefetch still lands on last page`() = runBlocking {
        val scope = CoroutineScope(Job() + controllerDispatcher)
        val controller = buildController(scope)
        try {
            controller.open(4000)
            awaitControllerState(controller) { controller.layout != null && !controller.isLayingOut && controller.chapterIndex == 1 }
            assertEquals(0, controller.pageIndex)

            // 不等预排：无论上一章是否已进缓存，prevPage 的两条路径
            // （applyChapter(lastIndex) / loadChapter { size - 1 }）都必须落在最后一页。
            controller.prevPage()

            awaitControllerState(controller) { controller.chapterIndex == 0 && !controller.isLayingOut }
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
    fun `next from chapter tail lands on next chapter first page`() = runBlocking {
        val scope = CoroutineScope(Job() + controllerDispatcher)
        val controller = buildController(scope)
        try {
            controller.open(4000)
            awaitControllerState(controller) { controller.layout != null && !controller.isLayingOut && controller.chapterIndex == 1 }
            awaitControllerState(controller) { controller.frameAt(1) != null }

            repeat(controller.pageCount) { controller.nextPage() }

            awaitControllerState(controller) { controller.chapterIndex == 2 && !controller.isLayingOut }
            assertEquals("跨章前进应落在下一章第一页", 0, controller.pageIndex)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun `next tap during initial layout is replayed after first page is ready`() = runBlocking {
        val scope = CoroutineScope(Job() + controllerDispatcher)
        val controller = buildController(scope)
        try {
            controller.open(0)
            controller.nextPage()

            awaitControllerState(controller) {
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
    fun `reversing during initial layout replaces pending forward turns`() = runBlocking {
        val scope = CoroutineScope(Job() + controllerDispatcher)
        val controller = buildController(scope)
        try {
            // 首批页面前连续入队，最终反向意图必须替换前向意图。
            controller.open(0)
            controller.nextPage()
            controller.nextPage()
            controller.prevPage()

            awaitControllerState(controller) { !controller.isLayingOut && controller.layout != null }
            assertEquals(0, controller.chapterIndex)
            assertEquals(0, controller.pageIndex)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    private suspend fun awaitControllerState(
        controller: PagedReaderController,
        condition: () -> Boolean,
    ) {
        try {
            // 唯一粗超时只负责把死锁变成可读失败；通过完全取决于下面的状态流发布。
            withTimeout(60_000) {
                snapshotFlow {
                    // chapterCache 本身不是 Compose State；cacheRevision 是它的发布信号。
                    controller.cacheRevision
                    condition()
                }.first { it }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError(
                "Timed out waiting for controller state after 60s: " +
                    "chapter=${controller.chapterIndex}, page=${controller.pageIndex}, " +
                    "layingOut=${controller.isLayingOut}, cacheRevision=${controller.cacheRevision}",
            ).apply { initCause(timeout) }
        }
    }
}
