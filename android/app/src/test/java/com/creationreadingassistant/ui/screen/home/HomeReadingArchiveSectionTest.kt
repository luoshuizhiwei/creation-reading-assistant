package com.creationreadingassistant.ui.screen.home

import com.creationreadingassistant.data.settings.DurationUnit
import com.creationreadingassistant.ui.util.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页阅读档案卡的时长文本（S15-F1/F2 迁移护栏）。
 *
 * 该卡片原先自带 `formatArchiveDuration` 私有实现：向下取整 + `coerceAtLeast(1)`
 * （不足一分钟强制「1 分钟」）+ 整点省略分钟。它与公共 formatter 的「不足一分钟
 * 显示 0 分钟」契约相反，且不读全局单位偏好。
 *
 * 私有实现已删除，卡片改调公共 [formatDuration]。本用例锁定迁移后的实际输出，
 * 尤其是**不足一分钟不再被改写成「1 分钟」**——那正是旧实现与全局策略分叉的地方。
 */
class HomeReadingArchiveSectionTest {

    @Test
    fun `archive duration now follows the shared formatter`() {
        assertEquals("0 分钟", formatDuration(0L, DurationUnit.HOURS))
        // 旧实现把 30 秒改写成「1 分钟」；公共策略统一显示「0 分钟」。
        assertEquals("0 分钟", formatDuration(30_000L, DurationUnit.HOURS))
        assertEquals("1 小时 0 分钟", formatDuration(3_600_000L, DurationUnit.HOURS))
        assertEquals("1 小时 30 分钟", formatDuration(5_430_000L, DurationUnit.HOURS))
        assertEquals("2 分钟", formatDuration(164_875L, DurationUnit.HOURS))
    }

    @Test
    fun `archive duration honours the global unit preference`() {
        // 旧实现完全不读单位偏好：无论用户选什么，30 小时都显示成「30 小时」。
        assertEquals("30 小时 0 分钟", formatDuration(108_000_000L, DurationUnit.HOURS))
        assertEquals("1 天 6 小时", formatDuration(108_000_000L, DurationUnit.DAYS))
    }
}
