package com.creationreadingassistant.ui.navigation

import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.ui.screen.reader.ReaderNavigationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * R2-J1-I.2 临时查阅入口回归。
 *
 * 覆盖「统一笔记条目的 source 坐标 → 临时查阅 route → 协调器返回栈 → LIFO 返回普通阅读位置」
 * 这条真实状态机链路，以及入口可见性判据 [hasTemporaryInspectionSource] 与 route 可构造性
 * **必须一致**（否则会出现「按钮显示但点了没反应」）。
 *
 * 边界说明：本类不驱动 Compose，因此**不能**证明 UI 接线已通。生产调用点由本切片报告中的
 * 接线审计（grep 到 file:line）另行确认。
 */
class AnnotationTemporaryInspectionEntryTest {

    // ── 1. 有效全局 source 坐标 → 可回退的临时查阅 route ──────────────────

    @Test
    fun `valid source builds temporary route carrying sourceLocator only`() {
        val route = readerTemporaryRouteForSource(
            bookId = "book-a",
            legacyOffset = 1234,
            chapterIndex = 3,
            charOffset = 178,
        )

        assertNotNull(route)
        assertTrue(route!!.startsWith("reader/book-a?"))
        assertTrue(route.contains("sourceLocator="))
        assertTrue(route.contains("navigationMode=temporary"))
        // 临时查阅状态不得混进 highlightId / noteId
        assertFalse(route.contains("highlightId="))
        assertFalse(route.contains("noteId="))
    }

    @Test
    fun `temporary route round-trips to the original source coordinates`() {
        val route = requireNotNull(
            readerTemporaryRouteForSource("book-a", legacyOffset = 1234, chapterIndex = 3, charOffset = 178)
        )
        val locator = requireNotNull(decodeSourceLocatorFromRoute(route))

        assertEquals(1234, locator.legacyOffset)
        assertEquals(3, locator.chapterIndex)
        assertEquals(178, locator.charOffset)
    }

    @Test
    fun `non-ascii bookId survives the route round-trip`() {
        val route = requireNotNull(readerTemporaryRouteForSource("书-1", 42, null, null))
        val (bookId, locatorJson) = parseRouteAsReaderRouteWould(route)

        assertEquals("书-1", bookId)
        val destination = requireNotNull(sourceTarget(bookId, locatorJson))
        assertEquals(42, destination.locator.legacyOffset)
    }

    // ── 2. ReaderRoute 侧解析：temporary 模式 + 目的地与条目坐标一致 ──────

    @Test
    fun `route parses into temporary mode with the annotation as destination`() {
        val route = requireNotNull(readerTemporaryRouteForSource("book-a", 1234, 3, 178))
        val (bookId, locatorJson) = parseRouteAsReaderRouteWould(route)

        // navigationMode 必须是 temporary，否则 ReaderRoute 不会推进返回栈
        assertEquals(
            ReaderNavigationMode.TEMPORARY,
            readerNavigationMode(queryParam(route, "navigationMode")),
        )
        assertEquals("book-a", bookId)

        val destination = requireNotNull(sourceTarget(bookId, locatorJson))
        assertEquals("book-a", destination.bookId)
        assertEquals(1234, destination.locator.legacyOffset)
        assertEquals(3, destination.locator.chapterIndex)
        assertEquals(178, destination.locator.charOffset)
    }

    // ── 3. 端到端：进入临时查阅 → 可返回 → 回到进入前的普通阅读位置 ────────

    @Test
    fun `entry route drives a returnable cross-book temporary inspection`() {
        val vm = TemporaryReadingNavigationViewModel()

        // 用户正在读 book-a 的全局 500 处；该位置由 ReaderProgressEffects 上报为普通阅读位置
        val normalReading = requireNotNull(
            SourceNavigationContract.target("book-a", ReaderLocator(500, 0, 500, null))
        )
        vm.recordNormalReading(normalReading)
        assertFalse("普通阅读位置本身不构成待返回目标", vm.hasReturnableTarget)

        // 从统一笔记条目（属于另一本书 book-b）点击「临时查看来源」
        val entryRoute = requireNotNull(readerTemporaryRouteForSource("book-b", 900, 1, 40))
        val (bookId, locatorJson) = parseRouteAsReaderRouteWould(entryRoute)
        val destination = requireNotNull(sourceTarget(bookId, locatorJson))

        // ReaderRoute 在 navigationMode=temporary 时推进返回栈（入口自身不推进）
        vm.beginTemporaryInspection(destination)
        assertTrue(vm.hasReturnableTarget)
        assertEquals(destination, vm.state.value.active)

        // 返回按钮 / 顶栏 Back / 系统 Back 共用 resolveTemporaryReturnRoute
        val returnRoute = requireNotNull(resolveTemporaryReturnRoute(vm))
        assertTrue(returnRoute.startsWith("reader/book-a?"))
        assertFalse("最后一层返回后应恢复普通阅读语义", returnRoute.contains("navigationMode="))
        assertEquals(normalReading, vm.state.value.active)
        assertFalse(vm.hasReturnableTarget)
    }

    // ── 4. 入口可见性判据必须与可跳转性一致 ──────────────────────────────

    @Test
    fun `eligibility predicate matches route constructibility`() {
        assertTrue(hasTemporaryInspectionSource("book-a", 10))
        assertTrue(hasTemporaryInspectionSource("book-a", 0))
        // 只有章节元组、无全局偏移：route 需要全局偏移才能跨渲染模式往返 → 不可用
        assertFalse(hasTemporaryInspectionSource("book-a", null))
        assertFalse(hasTemporaryInspectionSource(null, 10))
        assertFalse(hasTemporaryInspectionSource("   ", 10))
        assertFalse(hasTemporaryInspectionSource("book-a", -1))
    }

    @Test
    fun `predicate and route builder agree on every rejected input`() {
        val cases = listOf(
            Triple("book-a", null as Int?, null as Int?),
            Triple(null as String?, 10 as Int?, null as Int?),
            Triple("book-a", -1 as Int?, null as Int?),
        )
        cases.forEach { (bookId, offset, chapter) ->
            val eligible = hasTemporaryInspectionSource(bookId, offset)
            val route = readerTemporaryRouteForSource(bookId, offset, chapter, chapter)
            assertEquals(
                "可见性判据与 route 可构造性必须一致：bookId=$bookId offset=$offset",
                eligible,
                route != null,
            )
        }
    }

    // ── 5. 无效坐标安全降级：不伪造 offset=0 的假位置 ────────────────────

    @Test
    fun `chapter-only source without global offset degrades to null route`() {
        assertNull(readerTemporaryRouteForSource("book-a", null, 3, 178))
    }

    @Test
    fun `incomplete chapter coordinates degrade to null route`() {
        assertNull(readerTemporaryRouteForSource("book-a", null, 3, null))
        assertNull(readerTemporaryRouteForSource("book-a", null, null, 178))
    }

    @Test
    fun `missing book or negative offset degrades to null route`() {
        assertNull(readerTemporaryRouteForSource(null, 1234, 3, 178))
        assertNull(readerTemporaryRouteForSource("", 1234, 3, 178))
        assertNull(readerTemporaryRouteForSource("book-a", -5, null, null))
    }
}

// ============================== 辅助 ==============================

/** 取 route 的查询参数原值（未解码）。 */
private fun queryParam(route: String, key: String): String? =
    route.substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .firstOrNull { it.startsWith("$key=") }
        ?.removePrefix("$key=")

/**
 * 按 ReaderRoute 的解析口径还原 route：bookId 与 sourceLocator 各做一次 percent-decode。
 * 导航组件会把查询组件解码后再交给 ReaderRoute，这里显式复现该步，避免测试替生产「补课」。
 */
private fun parseRouteAsReaderRouteWould(route: String): Pair<String, String?> {
    val bookIdEncoded = route.removePrefix("reader/").substringBefore('?')
    val bookId = URLDecoder.decode(bookIdEncoded, StandardCharsets.UTF_8.name())
    val locatorEncoded = queryParam(route, "sourceLocator") ?: return bookId to null
    val locatorJson = URLDecoder.decode(locatorEncoded, StandardCharsets.UTF_8.name())
    return bookId to locatorJson
}
