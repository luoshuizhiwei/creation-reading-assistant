package com.creationreadingassistant.feature.goal

import com.creationreadingassistant.feature.goal.ReadingGoalScheduler.Companion.delayUntilNextReminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

/** 下一个提醒时刻计算（P3.2 片 3 调度对齐）：今天未到→今天，已过→明天。 */
class ReadingGoalDelayTest {

    private val now = LocalDateTime.of(2026, 8, 22, 20, 0)

    @Test
    fun `今天未到提醒时刻按今天算`() {
        assertEquals(Duration.ofHours(1), delayUntilNextReminder(21 * 60, now))
    }

    @Test
    fun `已过提醒时刻顺延到明天`() {
        // now=20:00，19:00 已过 → 明天 19:00，差 23 小时
        assertEquals(Duration.ofHours(23), delayUntilNextReminder(19 * 60, now))
    }

    @Test
    fun `恰好到点顺延到明天同一时刻`() {
        assertEquals(Duration.ofHours(24), delayUntilNextReminder(20 * 60, now))
    }

    @Test
    fun `零点整提醒`() {
        assertEquals(Duration.ofHours(4), delayUntilNextReminder(0, now))
    }
}
