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
}
