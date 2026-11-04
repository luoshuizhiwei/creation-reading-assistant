package com.creationreadingassistant.data.settings

import com.creationreadingassistant.testutil.testDataStoreContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * ShelfPrefs 读写往返测试：视图/排序偏好、最近搜索（去重置顶、无痕模式不落盘）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShelfPrefsTest {

    private lateinit var scope: CoroutineScope
    private lateinit var prefs: ShelfPrefs

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        prefs = ShelfPrefs(testDataStoreContext(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `view mode and sort mode round trip`() = runTest {
        prefs.setViewMode("list")
        prefs.setSortMode("title")

        assertEquals("list", prefs.viewMode.first { it == "list" })
        assertEquals("title", prefs.sortMode.first { it == "title" })

        // 还原常用默认，避免影响共享落盘上的后续观察
        prefs.setViewMode("grid")
        prefs.setSortMode("recent")
    }

    @Test
    fun `recordSearch persists with newest first`() = runTest {
        val term = "搜索-${UUID.randomUUID()}"
        prefs.recordSearch(term)

        val loaded = prefs.recentSearches.first { it.contains(term) }
        assertEquals(term, loaded.first())
        assertTrue(loaded.size <= 8)

        prefs.clearRecentSearches()
        val afterClear = prefs.recentSearches.first { !it.contains(term) }
        assertFalse(afterClear.contains(term))
    }

    @Test
    fun `private search mode prevents recording`() = runTest {
        val term = "无痕-${UUID.randomUUID()}"
        prefs.setPrivateSearch(true)
        prefs.recordSearch(term)

        val loaded = prefs.recentSearches.first()
        assertFalse(loaded.contains(term))

        // 还原，避免共享落盘上的无痕态影响其它测试
        prefs.setPrivateSearch(false)
        assertEquals(false, prefs.privateSearch.first { !it })
    }

    // ── 「新导入未查看」角标集合 ──────────────────────────────

    @Test
    fun `new book ids round trip and survive as a set`() = runTest {
        val ids = listOf("nb-${UUID.randomUUID()}", "nb-${UUID.randomUUID()}")
        prefs.addNewBookIds(ids)

        val loaded = prefs.newBookIds.first { it.containsAll(ids) }
        assertTrue(loaded.containsAll(ids))

        prefs.clearAllNewBookIds()
    }

    @Test
    fun `adding new book ids deduplicates and puts newest first`() = runTest {
        val a = "nb-a-${UUID.randomUUID()}"
        val b = "nb-b-${UUID.randomUUID()}"
        prefs.clearAllNewBookIds()
        prefs.addNewBookIds(listOf(a))
        prefs.addNewBookIds(listOf(b, a))

        val loaded = prefs.newBookIds.first { it.contains(a) && it.contains(b) }
        assertEquals(setOf(a, b), loaded)

        prefs.clearAllNewBookIds()
    }

    @Test
    fun `clearing one new book id keeps the others`() = runTest {
        val keep = "nb-keep-${UUID.randomUUID()}"
        val drop = "nb-drop-${UUID.randomUUID()}"
        prefs.clearAllNewBookIds()
        prefs.addNewBookIds(listOf(keep, drop))
        // 先确认两个都在，再删一个——否则下面的 first{} 会被 StateFlow 的
        // 初始空集合直接满足，测试变成「永远通过」的假绿。
        assertTrue(prefs.newBookIds.first { it.contains(keep) && it.contains(drop) }.contains(keep))

        prefs.clearNewBookId(drop)

        val loaded = prefs.newBookIds.first { it.contains(keep) && !it.contains(drop) }
        assertTrue(loaded.contains(keep))
        assertFalse(loaded.contains(drop))

        prefs.clearAllNewBookIds()
    }

    @Test
    fun `blank ids are ignored`() = runTest {
        val seed = "nb-seed-${UUID.randomUUID()}"
        prefs.clearAllNewBookIds()
        prefs.addNewBookIds(listOf(seed))
        prefs.addNewBookIds(listOf("", "  "))

        val loaded = prefs.newBookIds.first { it.contains(seed) }
        assertEquals(setOf(seed), loaded)
    }
}
