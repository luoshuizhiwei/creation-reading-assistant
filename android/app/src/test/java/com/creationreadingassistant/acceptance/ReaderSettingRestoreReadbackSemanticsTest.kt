package com.creationreadingassistant.acceptance

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务50-v1：设置恢复**回读机制**的离线语义验证（JVM，可离线执行）。
 *
 * 背景（v9 真实实验）：`SettingsStore.updateReader` 写入 DataStore 返回后，探针立即读
 * `settingsStore.reader.value.readerMode` 判恢复，得到 `readerModeRestored=false` 与
 * `"Reader mode did not return to its original value"`。但 `reader` 是由**另一条**上游
 * 收集驱动的 StateFlow，写入返回并不保证其 `.value` 已更新，因此"立即读一次"会把
 * **延迟到达**误判为恢复失败。
 *
 * 本测试用真实的 kotlinx-coroutines 语义固定三件事，正是修复所依赖的保证：
 * 1. 写入返回后立即读 `.value` 可能仍是旧值（⇒ 不能据此判失败）；
 * 2. 在有界超时内 `first { it == expected }` 会等到真实发射后返回（⇒ 延迟到达不误判）；
 * 3. 始终不满足时 `withTimeoutOrNull` 返回 null（⇒ 超时如实失败，不静默改绿）。
 *
 * 边界说明：本文件只验证**机制语义**；探针自身的 androidTest 代码路径需设备执行，
 * 本任务标 NOT_RUN。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSettingRestoreReadbackSemanticsTest {

    private companion object {
        const val READBACK_TIMEOUT_MS = 15_000L
    }

    /** 模拟 SettingsStore.reader 这种"由另一条上游驱动"的 StateFlow。 */
    private class LaggingSettingFlow(initial: String) {
        val state = MutableStateFlow(initial)

        /** 写入返回（等价 ds.edit{} 完成），但 StateFlow 的更新**稍后**才到达。 */
        suspend fun writeThenLag(newValue: String, lagMillis: Long) {
            delay(lagMillis)
            state.value = newValue
        }
    }

    @Test
    fun `immediate value read right after the write can still be stale`() = runTest {
        val store = LaggingSettingFlow("scroll")
        val job = launch { store.writeThenLag("paged", lagMillis = 500) }
        // 写入协程刚起步、StateFlow 尚未更新：此刻的立即读仍是旧值。
        assertEquals(
            "写入返回后立即读 StateFlow.value 可能仍是旧值，不能据此判恢复失败",
            "scroll",
            store.state.value,
        )
        job.join()
        assertEquals("上游发射后 StateFlow 才反映新值", "paged", store.state.value)
    }

    @Test
    fun `bounded flow wait resolves when the restored value arrives late`() = runTest {
        val store = LaggingSettingFlow("scroll")
        val job = launch { store.writeThenLag("paged", lagMillis = 500) }
        val observed = withTimeoutOrNull(READBACK_TIMEOUT_MS) {
            store.state.first { it == "paged" }
        }
        job.join()
        assertEquals("延迟到达的恢复值必须被有界等待观察到，不得误判为失败", "paged", observed)
        assertNotEquals("等待结果不能是超时哨兵", null, observed)
    }

    @Test
    fun `bounded flow wait reports timeout instead of silently succeeding`() = runTest {
        val store = LaggingSettingFlow("scroll")
        // 上游永不发射期望值（真实恢复失败）：有界等待必须返回 null 而不是成功。
        val observed = withTimeoutOrNull(READBACK_TIMEOUT_MS) {
            store.state.first { it == "paged" }
        }
        assertNull("真实恢复失败时必须有界超时返回 null（如实失败），不得静默改绿", observed)
        assertEquals("超时后 StateFlow 仍是未恢复的旧值", "scroll", store.state.value)
    }

    @Test
    fun `predicate that already holds resolves without waiting`() = runTest {
        val store = LaggingSettingFlow("paged")
        val observed = withTimeoutOrNull(READBACK_TIMEOUT_MS) {
            store.state.first { it == "paged" }
        }
        assertEquals("已满足期望值时立即返回，不引入额外等待", "paged", observed)
        assertTrue("期望值匹配时必须返回期望值", observed == "paged")
    }
}
