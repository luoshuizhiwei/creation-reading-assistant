package com.creationreadingassistant.ui.screen.stats

import com.creationreadingassistant.data.local.dao.StatsSessionRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 今日目标时长口径（P3.2 片 2）：occurred 口径 + 片 1 异常时长过滤，
 * 与 computeStats/提醒判定共用同一套语义。
 */
class ComputeTodayReadingMsTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 22)

    private fun session(iso: String, minutes: Long) =
        StatsSessionRow(book_id = "b", occurred_at = iso, duration_ms = minutes * 60_000, progress_percent = null)

    @Test
    fun `只统计今天的会话`() {
        val sessions = listOf(
            session("2026-08-22T10:00:00Z", 10),
            session("2026-08-21T10:00:00Z", 20),
        )
        assertEquals(10 * 60_000L, computeTodayReadingMs(sessions, today))
    }

    @Test
    fun `排除非正与超过24小时的异常时长`() {
        val sessions = listOf(
            session("2026-08-22T01:00:00Z", 0),
            session("2026-08-22T02:00:00Z", -5),
            session("2026-08-22T03:00:00Z", 25 * 60), // 25h 超上限
            session("2026-08-22T04:00:00Z", 30),
        )
        assertEquals(30 * 60_000L, computeTodayReadingMs(sessions, today))
    }

    @Test
    fun `occurred 为空回退今天并计入`() {
        // sessionDateKey 对 null occurred_at 回退 today（与统计页同语义）
        val sessions = listOf(session("", 15).let { it.copy(occurred_at = null) })
        assertEquals(15 * 60_000L, computeTodayReadingMs(sessions, today))
    }

    @Test
    fun `空列表为零`() {
        assertEquals(0L, computeTodayReadingMs(emptyList(), today))
    }

    @Test
    fun `跨零点后昨日会话不计入今天`() {
        val yesterday = today.minusDays(1)
        // 与生产同口径：本地时区中午 12 点的 instant，避免 UTC 字面量在东八区落到"今天"
        val yesterdayNoonIso = yesterday.atStartOfDay(java.time.ZoneId.systemDefault())
            .plusHours(12).toInstant().toString()
        val todayNoonIso = today.atStartOfDay(java.time.ZoneId.systemDefault())
            .plusHours(12).toInstant().toString()
        val sessions = listOf(session(yesterdayNoonIso, 40), session(todayNoonIso, 10))
        assertEquals(10 * 60_000L, computeTodayReadingMs(sessions, today))
        assertEquals(40 * 60_000L, computeTodayReadingMs(sessions, yesterday))
    }

    @Test
    fun `GoalUi 达成判定`() {
        val goal = GoalUi(todayReadingMs = 30 * 60_000L, dailyGoalMinutes = 30, streakDays = 3)
        assertEquals(true, goal.achieved)
        val notYet = goal.copy(todayReadingMs = 29 * 60_000L)
        assertEquals(false, notYet.achieved)
        val disabled = goal.copy(dailyGoalMinutes = 0)
        assertEquals(false, disabled.achieved)
    }
}
