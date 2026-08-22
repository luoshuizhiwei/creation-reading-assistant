package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.AnywhereBreakOracle
import com.creationreadingassistant.feature.reader.layout.FakeTextRuler
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedReaderControllerProjectionTest {

    @Test
    fun `controller translates projected page positions and source opens`() {
        val removedPrefix = "广告".repeat(100)
        val sourceText = removedPrefix + "正文".repeat(200)
        val rawSource = TxtChapterSource(
            fullText = sourceText,
            chapters = listOf(DocChapter(0, "", 0, sourceText.length)),
        )
        val source = ReplacedChapterSource(
            delegate = rawSource,
            bookId = "book-1",
            rules = listOf(replaceRule(pattern = "广告", replacement = "")),
        )
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val controller = PagedReaderController(
            source = source,
            cfg = LayoutConfig(
                contentWidthPx = 80f,
                contentHeightPx = 80f,
                fontSizePx = 20f,
            ),
            ruler = FakeTextRuler(fontSizePx = 20f),
            oracle = AnywhereBreakOracle(),
            scope = scope,
        )

        try {
            controller.open(0)
            waitUntil { controller.layout != null && !controller.isLayingOut }
            controller.nextPage()

            val projection = source.projectionForChapter(0)!!
            val page = controller.currentPage!!
            val expectedStart = projection.localDisplayToGlobalSource(page.startCharOffset)
            val expectedEnd = projection.localDisplayToGlobalSource(page.endCharOffset)
            assertTrue("fixture must place the second page after display offset zero", page.startCharOffset > 0)
            assertNotEquals("fixture must expose source/display drift", page.startCharOffset, expectedStart)
            assertEquals(
                expectedStart,
                controller.currentChapterLocalDisplayToGlobalSource(page.startCharOffset),
            )
            assertEquals(
                page.startCharOffset to page.endCharOffset,
                controller.currentChapterSourceRangeToLocalDisplay(expectedStart, expectedEnd),
            )
            assertEquals(expectedStart, controller.currentPageStartAbs)
            assertEquals(expectedStart until expectedEnd, controller.currentPageRangeAbs)

            val sourceTarget = removedPrefix.length + 20
            val expectedDisplayTarget = projection.globalSourceToLocalDisplay(sourceTarget)
            val expectedPage = controller.layout!!.pageIndexFor(expectedDisplayTarget)
            val naivePage = controller.layout!!.pageIndexFor(sourceTarget)
            assertNotEquals("fixture must distinguish source and display page lookup", naivePage, expectedPage)

            controller.open(sourceTarget)
            waitUntil { !controller.isLayingOut && controller.pageIndex == expectedPage }
            assertEquals(expectedPage, controller.pageIndex)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun `page index cache identity changes with the replacement profile`() {
        val rawSource = TxtChapterSource(
            fullText = "广告正文",
            chapters = listOf(DocChapter(0, "", 0, 4)),
        )
        val first = ReplacedChapterSource(
            delegate = rawSource,
            bookId = "book-1",
            rules = listOf(replaceRule(pattern = "广告", replacement = "")),
        )
        val second = ReplacedChapterSource(
            delegate = rawSource,
            bookId = "book-1",
            rules = listOf(replaceRule(pattern = "正文", replacement = "内容")),
        )

        assertNotEquals(
            pageIndexContentKey("book-1|toc=default", first),
            pageIndexContentKey("book-1|toc=default", second),
        )
        assertEquals("", pageIndexContentKey("", first))
        assertEquals("book-1|toc=default", pageIndexContentKey("book-1|toc=default", rawSource))
    }

    private fun replaceRule(pattern: String, replacement: String) = ReplaceRule(
        id = "replace-1",
        name = "测试规则",
        pattern = pattern,
        replacement = replacement,
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition()) {
            if (System.nanoTime() >= deadline) error("Timed out waiting for controller state")
            Thread.sleep(10)
        }
    }
}
