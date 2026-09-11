package com.creationreadingassistant.ui.navigation

import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.screen.reader.ReaderNavigationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2-J1.5 路由层端到端旅程回归：验证 route 构造、解析与 LIFO 返回的完整链路。
 *
 * 覆盖：
 * 1. 普通 sourceLocator 路由不带 navigationMode
 * 2. 临时查阅路由带 navigationMode=temporary
 * 3. 返回路由根据栈深度决定是否继续 temporary
 * 4. 跨书和同书跨章跳转的 route 构造正确
 * 5. 无效参数安全降级，不伪造 route
 */
class ReaderTemporaryRouteJourneyTest {

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    // ── 1. 普通 sourceLocator 路由 ───────────────────────────────────────

    @Test
    fun `normal source route does not carry navigationMode`() {
        val target = target("book-a", 100)
        val route = readerSourceRoute(target)

        assertTrue(route != null)
        assertTrue(route!!.contains("reader/book-a"))
        assertTrue(route.contains("sourceLocator="))
        assertFalse(route.contains("navigationMode="))
        assertFalse(route.contains("highlightId="))
    }

    // ── 2. 临时查阅路由带 navigationMode=temporary ──────────────────────

    @Test
    fun `temporary route carries navigationMode=temporary`() {
        val target = target("book-b", 200)
        val route = readerTemporaryRoute(target)

        assertTrue(route != null)
        assertTrue(route!!.contains("reader/book-b"))
        assertTrue(route.contains("sourceLocator="))
        assertTrue(route.contains("navigationMode=temporary"))
        assertFalse(route.contains("highlightId="))
    }

    // ── 3. 返回路由根据栈深度决定是否继续 temporary ────────────────────

    @Test
    fun `return route from last level does not carry navigationMode`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)

        vm.recordNormalReading(a)
        vm.beginTemporaryInspection(b)

        // 单层临时，返回时栈空，应回到普通阅读
        val returnRoute = resolveTemporaryReturnRoute(vm)
        assertTrue(returnRoute != null)
        assertTrue(returnRoute!!.contains("reader/book-a"))
        assertTrue(returnRoute.contains("sourceLocator="))
        assertFalse(returnRoute.contains("navigationMode="))
    }

    @Test
    fun `return route from intermediate level carries navigationMode=temporary`() {
        val vm = TemporaryReadingNavigationViewModel()
        val a = target("book-a", 100)
        val b = target("book-b", 200)
        val c = target("book-c", 300)

        vm.recordNormalReading(a)
        vm.beginTemporaryInspection(b)
        vm.beginTemporaryInspection(c)

        // 第一层返回：从 C 回到 B，栈仍非空，应继续 temporary
        val returnRoute1 = resolveTemporaryReturnRoute(vm)
        assertTrue(returnRoute1 != null)
        assertTrue(returnRoute1!!.contains("reader/book-b"))
        assertTrue(returnRoute1.contains("sourceLocator="))
        assertTrue(returnRoute1.contains("navigationMode=temporary"))

        // 第二层返回：从 B 回到 A，栈空，应回到普通阅读
        val returnRoute2 = resolveTemporaryReturnRoute(vm)
        assertTrue(returnRoute2 != null)
        assertTrue(returnRoute2!!.contains("reader/book-a"))
        assertTrue(returnRoute2.contains("sourceLocator="))
        assertFalse(returnRoute2.contains("navigationMode="))
    }

    // ── 4. 跨书和同书跨章跳转的 route 构造 ──────────────────────────────

    @Test
    fun `cross-book journey constructs correct routes`() {
        val bookA = target("book-a", 100)
        val bookB = target("book-b", 200)
        val bookC = target("book-c", 300)

        val routeA = readerSourceRoute(bookA)
        val routeB = readerTemporaryRoute(bookB)
        val routeC = readerTemporaryRoute(bookC)

        assertTrue(routeA!!.contains("reader/book-a"))
        assertTrue(routeB!!.contains("reader/book-b"))
        assertTrue(routeC!!.contains("reader/book-c"))

        // 验证 bookId 正确编码
        assertTrue(routeA.contains("book-a"))
        assertTrue(routeB.contains("book-b"))
        assertTrue(routeC.contains("book-c"))
    }

    @Test
    fun `same-book cross-chapter journey constructs correct routes`() {
        val chapter1 = target("book-a", 100)
        val chapter2 = target("book-a", 500)

        val route1 = readerSourceRoute(chapter1)
        val route2 = readerTemporaryRoute(chapter2)

        // 同一本书，但 offset 不同
        assertTrue(route1!!.contains("reader/book-a"))
        assertTrue(route2!!.contains("reader/book-a"))

        // sourceLocator 应包含不同的 offset
        assertTrue(route1.contains("sourceLocator="))
        assertTrue(route2.contains("sourceLocator="))
    }

    // ── 5. 无效参数安全降级 ─────────────────────────────────────────────

    @Test
    fun `invalid target degrades to null route`() {
        // 无 global offset 的 target
        val chapterOnly = SourceNavigationContract.target(
            "book-a",
            ReaderLocator(null, 1, 50, null)
        )
        assertTrue(chapterOnly != null)

        // 无法构造 route（缺少 legacyOffset）
        val route = readerSourceRoute(chapterOnly!!)
        assertNull(route)
    }

    @Test
    fun `empty return stack returns null route`() {
        val vm = TemporaryReadingNavigationViewModel()

        // 未记录任何位置，栈空
        val returnRoute = resolveTemporaryReturnRoute(vm)
        assertNull(returnRoute)
    }

    @Test
    fun `navigationMode parsing degrades unknown values to NORMAL`() {
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode(null))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode(""))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode("normal"))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode("TEMPORARY")) // 大小写敏感
        assertEquals(ReaderNavigationMode.TEMPORARY, readerNavigationMode("temporary"))
    }

    // ── 6. route 往返一致性 ─────────────────────────────────────────────

    @Test
    fun `route round-trip preserves target information`() {
        val original = target("book-a", 123)
        val route = readerSourceRoute(original)
        assertTrue(route != null)

        // 从 route 解析出 target
        val locator = decodeSourceLocatorFromRoute(route!!)
        val parsed = if (locator?.legacyOffset != null) {
            sourceTarget("book-a", LocatorCodec.encode(locator.legacyOffset!!, locator.chapterIndex, locator.charOffset, null))
        } else null
        assertTrue(parsed != null)
        assertEquals(original.bookId, parsed!!.bookId)
        assertEquals(original.locator.legacyOffset, parsed.locator.legacyOffset)
    }

    @Test
    fun `temporary route round-trip preserves navigationMode`() {
        val original = target("book-b", 456)
        val route = readerTemporaryRoute(original)
        assertTrue(route != null)

        // 验证 route 包含 navigationMode=temporary
        assertTrue(route!!.contains("navigationMode=temporary"))

        // 提取 navigationMode
        val mode = extractNavMode(route)
        assertEquals(ReaderNavigationMode.TEMPORARY, mode)
    }
}

private fun extractNavMode(route: String): ReaderNavigationMode {
    val query = route.substringAfter('?', missingDelimiterValue = "")
    val navModeParam = query.split('&')
        .firstOrNull { it.startsWith("navigationMode=") }
        ?.removePrefix("navigationMode=")
    return readerNavigationMode(navModeParam)
}
