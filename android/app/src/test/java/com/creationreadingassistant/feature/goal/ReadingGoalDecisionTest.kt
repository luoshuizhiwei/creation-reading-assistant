package com.creationreadingassistant.feature.goal

import com.creationreadingassistant.data.settings.ReadingGoalPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 阅读提醒判定（P3.2 片 3）纯函数测试：
 * 设计 §片 3 的「达成/未达成/无权限」三分支 + 关闭/前台静默。
 */
class ReadingGoalDecisionTest {

    private fun snapshot(
        dailyMinutes: Int = 30,
        reminderEnabled: Boolean = true,
        todayMs: Long = 0,
        granted: Boolean = true,
        foreground: Boolean = false,
    ) = ReadingGoalSnapshot(
        prefs = ReadingGoalPrefs(
            dailyMinutes = dailyMinutes,
            reminderEnabled = reminderEnabled,
        ),
        todayReadingMs = todayMs,
        notificationsGranted = granted,
        appInForeground = foreground,
    )

    @Test
    fun `目标未达成且条件满足时发送`() {
        assertEquals(GoalReminderDecision.NOTIFY, decideGoalReminder(snapshot(todayMs = 29 * 60_000L)))
    }

    @Test
    fun `今日已达成目标时静默`() {
        assertEquals(
            GoalReminderDecision.SKIP_ACHIEVED,
            decideGoalReminder(snapshot(todayMs = 30 * 60_000L)),
        )
    }

    @Test
    fun `无通知权限时静默`() {
        assertEquals(
            GoalReminderDecision.SKIP_NO_PERMISSION,
            decideGoalReminder(snapshot(granted = false)),
        )
    }

    @Test
    fun `目标关闭时静默`() {
        assertEquals(
            GoalReminderDecision.SKIP_DISABLED,
            decideGoalReminder(snapshot(dailyMinutes = 0)),
        )
    }

    @Test
    fun `提醒开关关闭时静默`() {
        assertEquals(
            GoalReminderDecision.SKIP_DISABLED,
            decideGoalReminder(snapshot(reminderEnabled = false)),
        )
    }

    @Test
    fun `应用前台阅读时静默`() {
        assertEquals(
            GoalReminderDecision.SKIP_FOREGROUND,
            decideGoalReminder(snapshot(foreground = true)),
        )
    }

    @Test
    fun `标题按剩余分钟取整`() {
        assertEquals(
            "今天的阅读目标还差 30 分钟",
            goalReminderTitle(snapshot(todayMs = 0)),
        )
        // 29 分 01 秒已读 → 剩 59 秒，向上取整为 1 分钟
        assertEquals(
            "今天的阅读目标还差 1 分钟",
            goalReminderTitle(snapshot(todayMs = 29 * 60_000L + 1_000L)),
        )
    }

    @Test
    fun `正文带目标分钟`() {
        assertEquals("目标 30 分钟 · 打开书架继续阅读", goalReminderBody(snapshot()))
    }

    @Test
    fun `剩余分钟向上取整且不为负`() {
        assertEquals(30, remainingMinutesOf(snapshot(todayMs = 0)))
        assertEquals(1, remainingMinutesOf(snapshot(todayMs = 30 * 60_000L - 1)))
        assertEquals(0, remainingMinutesOf(snapshot(todayMs = 30 * 60_000L)))
        assertEquals(0, remainingMinutesOf(snapshot(todayMs = 45 * 60_000L)))
    }
}
