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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * SearchHistoryStore 读写往返测试：新词置顶、去重、空白忽略、清空。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchHistoryStoreTest {

    private lateinit var scope: CoroutineScope
    private lateinit var store: SearchHistoryStore

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = SearchHistoryStore(testDataStoreContext(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `add persists with newest first and dedupes`() = runTest {
        val a = "词甲-${UUID.randomUUID()}"
        val b = "词乙-${UUID.randomUUID()}"
        store.clear()

        store.add(a)
        store.add(b)
        // 重复词再次添加 → 去重并置顶
        store.add(a)

        val loaded = store.history.first { it.contains(a) && it.contains(b) }
        assertEquals(a, loaded.first())
        // 去重：重复词只保留一条
        assertEquals(1, loaded.count { it == a })
        assertTrue(loaded.contains(b))
    }

    @Test
    fun `blank terms are ignored`() = runTest {
        val real = "真词-${UUID.randomUUID()}"
        store.clear()
        store.add(real)
        store.add("   ")

        val loaded = store.history.first { it.contains(real) }
        // 空白词未落盘：历史里只有真词一条
        assertEquals(listOf(real), loaded)
    }

    @Test
    fun `clear empties persisted history`() = runTest {
        store.add("临时-${UUID.randomUUID()}")
        store.clear()

        val loaded = store.history.first { it.isEmpty() }
        assertTrue(loaded.isEmpty())
    }
}
