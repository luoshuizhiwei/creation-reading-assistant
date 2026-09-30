package com.creationreadingassistant.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M-BUG-03「来源内容有更新」判定的边界测试。
 *
 * 核心约定：**只有能证明变化时才判定为更新**。观测列缺失、基线不可比时一律不报，
 * 因为误报（没改过却显示「可更新」）比漏报更难排查，也会诱导用户做无意义的重导。
 */
class ShelfSourceUpdateCheckerTest {

    @Test
    fun `size change is an update`() {
        assertTrue(
            isSourceContentUpdated(
                baselineSize = 1_000,
                baselineLastModified = 100L,
                observedSize = 1_200,
                observedLastModified = 100L,
            ),
        )
    }

    @Test
    fun `modification time change is an update`() {
        assertTrue(
            isSourceContentUpdated(
                baselineSize = 1_000,
                baselineLastModified = 100L,
                observedSize = 1_000,
                observedLastModified = 200L,
            ),
        )
    }

    @Test
    fun `identical size and time is not an update`() {
        assertFalse(
            isSourceContentUpdated(
                baselineSize = 1_000,
                baselineLastModified = 100L,
                observedSize = 1_000,
                observedLastModified = 100L,
            ),
        )
    }

    @Test
    fun `missing observed size alone never reports an update`() {
        // provider 不返回 size 列：不能因为「拿不到」就报更新。
        assertFalse(
            isSourceContentUpdated(
                baselineSize = 1_000,
                baselineLastModified = 100L,
                observedSize = null,
                observedLastModified = 100L,
            ),
        )
    }

    @Test
    fun `null baseline modification time is not comparable`() {
        // 导入时基线没有 last_modified：无法比较，不得据此报更新（size 相同）。
        assertFalse(
            isSourceContentUpdated(
                baselineSize = 1_000,
                baselineLastModified = null,
                observedSize = 1_000,
                observedLastModified = 999L,
            ),
        )
    }

    @Test
    fun `both observations missing never reports an update`() {
        assertFalse(
            isSourceContentUpdated(
                baselineSize = 1_000,
                baselineLastModified = 100L,
                observedSize = null,
                observedLastModified = null,
            ),
        )
    }
}
