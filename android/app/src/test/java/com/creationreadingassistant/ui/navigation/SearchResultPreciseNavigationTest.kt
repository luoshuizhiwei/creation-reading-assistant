package com.creationreadingassistant.ui.navigation

import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索命中 → 精确跳转 route 的纯函数测试（R2-S1.4）。
 *
 * 重点钉死两件事：
 *  1. 换算出来的全书偏移**原样**进 route，回读一致；
 *  2. 换算不出来时**返回 null**，绝不退化成一个看起来能跳的假位置。
 *
 * 第 2 点尤其关键：伪造 offset=0 会让用户跳到书的最开头，还以为跳转成功了，
 * 这类 bug 不会崩溃、只会让人困惑。
 */
class SearchResultPreciseNavigationTest {

    @Test
    fun `resolved offset produces a temporary route carrying exactly that offset`() {
        val route = readerTemporaryRouteForSource(
            bookId = "book-42",
            legacyOffset = 123_456,
            chapterIndex = null,
            charOffset = null,
        )
        assertNotNull("有全书偏移就必须能造出 route", route)
        val locator = decodeSourceLocatorFromRoute(route!!)
        assertNotNull(locator)
        assertEquals(123_456, locator!!.legacyOffset)
        assertTrue(
            "必须是临时查阅，否则回不到搜索前的阅读位置",
            route.contains("navigationMode=temporary"),
        )
    }

    @Test
    fun `route survives the source navigation contract round trip`() {
        val bookId = "书-甲"
        val route = readerTemporaryRouteForSource(bookId, 789, null, null)
        assertNotNull(route)
        val locator = decodeSourceLocatorFromRoute(route!!)
        val target = SourceNavigationContract.target(bookId, locator)
        assertNotNull("定位符必须被 SourceNavigationContract 接受，否则阅读器会拒绝跳转", target)
        assertEquals(bookId, target!!.bookId)
        assertEquals(789, target.locator.legacyOffset)
    }

    @Test
    fun `unresolvable coordinates yield no route instead of a fake position`() {
        assertNull("null 偏移不得伪造 0", readerTemporaryRouteForSource("b", null, null, null))
        assertNull("负偏移不得被接受", readerTemporaryRouteForSource("b", -1, null, null))
        assertNull("空白书籍 ID 无效", readerTemporaryRouteForSource("  ", 5, null, null))
        assertNull("null 书籍 ID 无效", readerTemporaryRouteForSource(null, 5, null, null))
    }

    @Test
    fun `zero offset is a legitimate position and is preserved`() {
        // 0 = 书的最开头，是合法位置；必须与「换算不出来」明确区分开，
        // 否则「跳到开头」和「跳不了」会被混为一谈。
        val route = readerTemporaryRouteForSource("b", 0, null, null)
        assertNotNull(route)
        assertEquals(0, decodeSourceLocatorFromRoute(route!!)!!.legacyOffset)
    }

    @Test
    fun `chapter coordinates alone are not enough for a route`() {
        // 只有 (章, 章内偏移) 而没有全书偏移时，既有契约要求拒绝 ——
        // 搜索侧因此必须自己换算出全书偏移（见 SearchIndexRepository.resolveLegacyOffset）。
        assertNull(
            readerTemporaryRouteForSource(
                bookId = "b",
                legacyOffset = null,
                chapterIndex = 3,
                charOffset = 120,
            ),
        )
    }
}
