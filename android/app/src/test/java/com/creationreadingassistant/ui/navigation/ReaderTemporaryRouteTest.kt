package com.creationreadingassistant.ui.navigation

import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.ui.screen.reader.ReaderNavigationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2-J1.3 临时路由纯 JVM 回归：验证「普通 / 临时 / 返回」三态 route 构造、navigationMode 解析、
 * LIFO 返回目标与无效参数安全降级。不依赖 NavController，只在 source locator 字符串层断言。
 */
class ReaderTemporaryRouteTest {

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))

    // ── 1. 普通 sourceLocator 路由兼容 ───────────────────────────────

    @Test
    fun `missing or unknown navigationMode resolves to normal reading`() {
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode(null))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode(""))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode("normal"))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode("TEMPORARY"))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode("garbage"))
    }

    @Test
    fun `normal source route carries only sourceLocator and keeps bookId offset`() {
        val route = readerSourceRoute(target("book-a", 321))
        requireNotNull(route)

        assertTrue(route.startsWith("reader/book-a?sourceLocator="))
        assertFalse(route.contains("highlightId"))
        assertFalse(route.contains("navigationMode"))
        assertEquals(321, decodeSourceLocatorFromRoute(route)?.legacyOffset)
    }

    // ── 2. temporary 路由解析并触发临时模式 ───────────────────────────

    @Test
    fun `temporary route parses to temporary mode and carries sourceLocator`() {
        assertEquals(ReaderNavigationMode.TEMPORARY, readerNavigationMode("temporary"))

        val route = readerTemporaryRoute(target("book-b", 200))
        requireNotNull(route)

        assertTrue(route.contains("navigationMode=temporary"))
        assertTrue(route.contains("sourceLocator="))
        assertFalse(route.contains("highlightId"))
        assertEquals(200, decodeSourceLocatorFromRoute(route)?.legacyOffset)
    }

    // ── 3. 返回目标仅携带 sourceLocator，不带 highlightId ─────────────

    @Test
    fun `return route carries only sourceLocator without highlightId or navigationMode`() {
        val route = readerReturnRoute(target("book-a", 321))
        requireNotNull(route)

        assertTrue(route.startsWith("reader/book-a?sourceLocator="))
        assertFalse(route.contains("highlightId"))
        assertFalse(route.contains("navigationMode"))
        assertEquals(321, decodeSourceLocatorFromRoute(route)?.legacyOffset)
    }

    @Test
    fun `resolving temporary return never emits highlightId`() {
        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(target("book-a", 100))
        vm.beginTemporaryInspection(target("book-b", 200))

        val returnRoute = resolveTemporaryReturnRoute(vm)
        requireNotNull(returnRoute)

        assertFalse(returnRoute.contains("highlightId"))
        assertTrue(returnRoute.contains("sourceLocator="))
        // 单层临时：直接回到普通阅读处 book-a，不带 navigationMode
        assertTrue(returnRoute.startsWith("reader/book-a?sourceLocator="))
        assertFalse(returnRoute.contains("navigationMode"))
        assertEquals(100, decodeSourceLocatorFromRoute(returnRoute)?.legacyOffset)
    }

    // ── 4. 跨书与同书跨章均保持 bookId + source locator ───────────────

    @Test
    fun `cross-book nested temporary returns LIFO keeping bookId and source locator`() {
        val origin = target("book-a", 100)
        val hop1 = target("book-b", 200)
        val hop2 = target("book-c", 300)

        val vm = TemporaryReadingNavigationViewModel()
        vm.recordNormalReading(origin)
        vm.beginTemporaryInspection(hop1)
        vm.beginTemporaryInspection(hop2)

        // 第一层返回：回到中间层 book-b（仍 temporary）
        val intermediate = resolveTemporaryReturnRoute(vm)
        requireNotNull(intermediate)
        assertTrue(intermediate.startsWith("reader/book-b?sourceLocator="))
        assertTrue(intermediate.contains("navigationMode=temporary"))
        assertEquals(200, decodeSourceLocatorFromRoute(intermediate)?.legacyOffset)

        // 第二层返回：回到普通阅读处 book-a（无 navigationMode）
        val finalReturn = resolveTemporaryReturnRoute(vm)
        requireNotNull(finalReturn)
        assertTrue(finalReturn.startsWith("reader/book-a?sourceLocator="))
        assertFalse(finalReturn.contains("navigationMode"))
        assertEquals(100, decodeSourceLocatorFromRoute(finalReturn)?.legacyOffset)
    }

    @Test
    fun `same-book cross-chapter keeps same bookId with distinct source locators`() {
        val first = readerTemporaryRoute(target("book-a", 10))
        val second = readerTemporaryRoute(target("book-a", 20))
        requireNotNull(first)
        requireNotNull(second)

        assertTrue(first.startsWith("reader/book-a?"))
        assertTrue(second.startsWith("reader/book-a?"))
        assertEquals(10, decodeSourceLocatorFromRoute(first)?.legacyOffset)
        assertEquals(20, decodeSourceLocatorFromRoute(second)?.legacyOffset)
    }

    @Test
    fun `source target round-trips through the nav-arg decode`() {
        val json = LocatorCodec.encode(legacyOffset = 321, chapterIndex = 0, charOffset = 321, excerpt = null)
        val decoded = sourceTarget("book-a", json)

        assertNotNull(decoded)
        assertEquals("book-a", decoded!!.bookId)
        assertEquals(321, decoded.locator.legacyOffset)
        assertEquals(0, decoded.locator.chapterIndex)
        assertEquals(321, decoded.locator.charOffset)
    }

    // ── 5. 无效参数安全降级，不崩溃、不伪造目标 ────────────────────────

    @Test
    fun `invalid targets degrade to null without fabricating offsets`() {
        // 无返回位置：readerReturnRoute(null) 直接降级
        assertNull(readerReturnRoute(null))

        // 只有章节元组、无全局 offset 的目标：不能伪造 offset=0 的 route
        val chapterOnly = requireNotNull(
            SourceNavigationContract.target("book-a", ReaderLocator(null, 1, 20, null)),
        )
        assertNull(encodeSourceLocator(chapterOnly))
        assertNull(readerSourceRoute(chapterOnly))
        assertNull(readerTemporaryRoute(chapterOnly))

        // 空返回栈：不伪造目标，保持正常离开 reader 的语义
        val emptyVm = TemporaryReadingNavigationViewModel()
        assertNull(resolveTemporaryReturnRoute(emptyVm))

        // 无效 sourceLocator JSON / 空白 bookId：sourceTarget 也不会崩溃
        assertNull(sourceTarget(null, LocatorCodec.encode(1, 0, 1, null)))
        assertNull(sourceTarget("book-a", null))
        assertNull(sourceTarget("book-a", "not-a-locator-json"))
    }
}