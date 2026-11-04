package com.creationreadingassistant.data.settings

import com.creationreadingassistant.data.repository.ConflictStrategy
import com.creationreadingassistant.testutil.testDataStoreContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同步偏好落库测试（决策②：同步开关真接线后补一个落库测试）。
 *
 * 验证 3 个同步项开关 + 冲突策略经 DataStore 真实读写往返、逐项独立持久化、坏值回退默认。
 * [com.creationreadingassistant.testutil.TestDataStoreDirs.root] 是进程级共享落盘，
 * 故每个用例「建立已知基线 → 写 → 读断言 → 还原默认」，避免用例间串扰（对齐 ShelfPrefsTest 惯例）。
 */
class SyncPrefsStoreTest {

    private fun store() = SyncPrefsStore(testDataStoreContext())

    /** 默认契约：全开 + 远端为准（与「我的 · 同步」子页初始展示、SyncOptions 无参语义一致）。 */
    @Test
    fun `default prefs are all-on and remote-wins`() {
        val defaults = SyncPrefs()
        assertTrue(defaults.syncBooks)
        assertTrue(defaults.syncProgress)
        assertTrue(defaults.syncInspirations)
        assertEquals(ConflictStrategy.REMOTE_WINS, defaults.conflictStrategy)
    }

    @Test
    fun `sync item toggles persist independently and round trip`() = runTest {
        val store = store()
        // 建立已知基线（共享落盘，避免受其它用例残留影响）
        store.setSyncBooks(true)
        store.setSyncProgress(true)
        store.setSyncInspirations(true)
        store.prefs.first { it.syncBooks && it.syncProgress && it.syncInspirations }

        store.setSyncBooks(false)
        store.setSyncInspirations(false)

        val loaded = store.prefs.first { !it.syncBooks && !it.syncInspirations }
        assertFalse(loaded.syncBooks)
        // 未改动的项保持原值 → 逐项独立落库、互不串扰
        assertTrue(loaded.syncProgress)
        assertFalse(loaded.syncInspirations)

        // 还原默认，避免影响共享落盘上的后续用例
        store.setSyncBooks(true)
        store.setSyncInspirations(true)
        store.prefs.first { it.syncBooks && it.syncInspirations }
    }

    @Test
    fun `conflict strategy persists and round trips`() = runTest {
        val store = store()

        store.setConflictStrategy(ConflictStrategy.LOCAL_WINS)
        assertEquals(
            ConflictStrategy.LOCAL_WINS,
            store.prefs.first { it.conflictStrategy == ConflictStrategy.LOCAL_WINS }.conflictStrategy,
        )

        // 还原默认（远端为准）
        store.setConflictStrategy(ConflictStrategy.REMOTE_WINS)
        assertEquals(
            ConflictStrategy.REMOTE_WINS,
            store.prefs.first { it.conflictStrategy == ConflictStrategy.REMOTE_WINS }.conflictStrategy,
        )
    }

    /** 落盘字符串 → 策略的解码：只认 remote/local，未知与缺失一律回退默认，绝不把坏值当成有效策略。 */
    @Test
    fun `fromWire rejects unknown values and falls back to default`() {
        assertEquals(ConflictStrategy.REMOTE_WINS, ConflictStrategy.fromWire("remote"))
        assertEquals(ConflictStrategy.LOCAL_WINS, ConflictStrategy.fromWire("local"))
        assertEquals(ConflictStrategy.DEFAULT, ConflictStrategy.fromWire("garbage"))
        assertEquals(ConflictStrategy.DEFAULT, ConflictStrategy.fromWire(""))
        assertEquals(ConflictStrategy.DEFAULT, ConflictStrategy.fromWire(null))
    }
}
