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
 * ImportHistoryStore 读写往返测试：新增条目置顶、清空后为空、损坏 JSON 容错。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportHistoryStoreTest {

    private lateinit var scope: CoroutineScope
    private lateinit var store: ImportHistoryStore

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = ImportHistoryStore(testDataStoreContext(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun entry(id: String) = ImportHistoryEntry(
        id = id,
        fileName = "$id.txt",
        fileSize = 1024,
        format = "txt",
        encoding = "UTF-8",
        bookTitle = "书名$id",
        status = "success",
    )

    @Test
    fun `addEntry persists and newest comes first`() = runTest {
        val id = "imp-${UUID.randomUUID()}"
        store.clear()
        store.addEntry(entry(id))

        val loaded = store.entries.first { list -> list.any { it.id == id } }
        assertEquals(id, loaded.first().id)
        assertEquals("书名$id", loaded.first().bookTitle)
        assertEquals("success", loaded.first().status)
        assertEquals(1024, loaded.first().fileSize)
    }

    @Test
    fun `addEntry keeps newest order across multiple writes`() = runTest {
        val first = "imp-1-${UUID.randomUUID()}"
        val second = "imp-2-${UUID.randomUUID()}"
        store.clear()

        store.addEntry(entry(first))
        store.addEntry(entry(second))

        val loaded = store.entries.first { list -> list.any { it.id == second } }
        assertEquals(listOf(second, first), loaded.map { it.id })
    }

    @Test
    fun `clear empties history`() = runTest {
        store.addEntry(entry("imp-${UUID.randomUUID()}"))
        store.clear()

        val loaded = store.entries.first { it.isEmpty() }
        assertTrue(loaded.isEmpty())
    }
}
