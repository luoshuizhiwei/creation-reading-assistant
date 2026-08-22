package com.creationreadingassistant.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 阅读目标偏好收敛（P3.2 片 2）：0=关闭，5–600 有效，提醒时刻 0–1439。 */
class GoalStoreClampTest {

    @Test
    fun `每日分钟收敛`() {
        assertEquals(0, GoalStore.clampDailyMinutes(0))
        assertEquals(0, GoalStore.clampDailyMinutes(-3))
        assertEquals(5, GoalStore.clampDailyMinutes(5))
        assertEquals(600, GoalStore.clampDailyMinutes(600))
        assertEquals(600, GoalStore.clampDailyMinutes(99999))
        assertEquals(45, GoalStore.clampDailyMinutes(45))
    }

    @Test
    fun `提醒时刻收敛`() {
        assertEquals(0, GoalStore.clampReminderMinute(0))
        assertEquals(1439, GoalStore.clampReminderMinute(1439))
        assertEquals(1439, GoalStore.clampReminderMinute(2000))
        assertEquals(0, GoalStore.clampReminderMinute(-30))
        assertEquals(1260, GoalStore.clampReminderMinute(1260))
    }

    @Test
    fun `时刻格式化`() {
        assertEquals("21:00", GoalStore.formatMinuteOfDay(21 * 60))
        assertEquals("00:00", GoalStore.formatMinuteOfDay(0))
        assertEquals("23:59", GoalStore.formatMinuteOfDay(24 * 60 - 1))
        assertEquals("00:00", GoalStore.formatMinuteOfDay(-1))
    }

    @Test
    fun `偏好派生态`() {
        val off = ReadingGoalPrefs()
        assertEquals(false, off.goalEnabled)
        assertEquals(false, off.reminderActive)

        val goalOnly = ReadingGoalPrefs(dailyMinutes = 30)
        assertEquals(true, goalOnly.goalEnabled)
        assertEquals(false, goalOnly.reminderActive)

        val both = ReadingGoalPrefs(dailyMinutes = 30, reminderEnabled = true)
        assertEquals(true, both.reminderActive)

        // 目标关闭时提醒开关即使打开也不生效
        val reminderWithoutGoal = ReadingGoalPrefs(dailyMinutes = 0, reminderEnabled = true)
        assertEquals(false, reminderWithoutGoal.reminderActive)
    }
}
