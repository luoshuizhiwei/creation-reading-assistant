package com.creationreadingassistant.data.settings

import com.creationreadingassistant.testutil.testDataStoreContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * ContinueReadingStore 读写往返测试（真实 DataStore 落盘到临时目录）。
 * 「从继续阅读移除」的书籍 ID 集合：remove 后可见、clear 后恢复。
 */
class ContinueReadingStoreTest {

    private val store = ContinueReadingStore(testDataStoreContext())

    @Test
    fun `remove and clear round trip`() = runTest {
        val bookId = "book-${UUID.randomUUID()}"

        store.remove(bookId)
        val afterRemove = store.removedIds.first { it.containsKey(bookId) }
        assertTrue(afterRemove.containsKey(bookId))
        // 移除时间戳是合法 ISO 串
        assertTrue(afterRemove[bookId]!!.contains("T"))

        store.clear(bookId)
        val afterClear = store.removedIds.first { !it.containsKey(bookId) }
        assertFalse(afterClear.containsKey(bookId))
    }

    @Test
    fun `removing one book does not affect others`() = runTest {
        val a = "book-a-${UUID.randomUUID()}"
        val b = "book-b-${UUID.randomUUID()}"

        store.remove(a)
        store.remove(b)
        val both = store.removedIds.first { it.containsKey(a) && it.containsKey(b) }
        assertTrue(both[a] != null && both[b] != null)

        store.clear(a)
        val after = store.removedIds.first { !it.containsKey(a) }
        assertFalse(after.containsKey(a))
        assertTrue(after.containsKey(b))

        // 清理现场，避免跨运行累积
        store.clear(b)
    }
}
