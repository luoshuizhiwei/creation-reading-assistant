package com.creationreadingassistant.ui.screen.stats

import com.creationreadingassistant.data.local.dao.StatsBookRow
import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsComputationTest {

    @Test
    fun `an empty past week does not show books or streaks from another week`() {
        val anchor = LocalDate.of(2026, 11, 9)
        val outsideWeek = anchor.plusDays(8).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        val stats = computeStats(
            period = StatsPeriod.WEEK,
            anchor = anchor,
            sessions = listOf(StatsSessionRow("book", outsideWeek, 30_000, 100f)),
            progress = listOf(StatsProgressRow("book", 100f, "finished", outsideWeek)),
            books = listOf(StatsBookRow("book", 1024, "available")),
            inspirations = emptyList(),
            notes = emptyList(),
        )

        assertEquals(0, stats.readBooks)
        assertEquals(0, stats.completed)
        assertEquals(0, stats.streakLongest)
        assertEquals(1, stats.status.completed)
    }

    @Test
    fun `dashboard streak does not treat malformed occurred time as today`() {
        val stats = computeStats(
            period = StatsPeriod.TOTAL,
            anchor = LocalDate.now(),
            sessions = listOf(
                StatsSessionRow(
                    book_id = "book-1",
                    occurred_at = "not-an-instant",
                    duration_ms = 30_000,
                    progress_percent = 1f,
                ),
            ),
            progress = emptyList(),
            books = emptyList(),
            inspirations = emptyList(),
            notes = emptyList(),
        )

        assertEquals(0, stats.streakCurrent)
        assertEquals(0, stats.streakLongest)
    }

    @Test
    fun `book status distribution requires explicit finished not stale percent`() {
        // S05-① 判别用例：b1 = 真实读完；b2 = 取消读完后的残余 100%（reading）→ 在读；
        // b3 = 普通在读。统计页书态分布不得把 b2 计入已完成。
        val stats = computeStats(
            period = StatsPeriod.TOTAL,
            anchor = LocalDate.now(),
            sessions = emptyList(),
            progress = listOf(
                StatsProgressRow(book_id = "b1", progress_percent = 100f, completion_state = "finished"),
                StatsProgressRow(book_id = "b2", progress_percent = 100f, completion_state = "reading"),
                StatsProgressRow(book_id = "b3", progress_percent = 30f, completion_state = "reading"),
            ),
            books = listOf(
                StatsBookRow(id = "b1", size = 1024, content_status = "available"),
                StatsBookRow(id = "b2", size = 1024, content_status = "available"),
                StatsBookRow(id = "b3", size = 1024, content_status = "available"),
            ),
            inspirations = emptyList(),
            notes = emptyList(),
        )

        assertEquals(1, stats.status.completed)
        assertEquals(2, stats.status.reading)
        assertEquals(1, stats.completed)
    }
}
