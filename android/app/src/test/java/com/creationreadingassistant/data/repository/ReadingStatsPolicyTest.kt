package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.StatsProgressRow
import com.creationreadingassistant.data.local.dao.StatsSessionRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReadingStatsPolicyTest {

    @Test
    fun `streak is derived from occurred dates with current and longest runs`() {
        val rows = listOf(
            session("2026-08-20T01:00:00Z"),
            session("2026-08-19T01:00:00Z"),
            session("2026-08-17T01:00:00Z"),
            session("2026-08-16T01:00:00Z"),
            session("2026-08-15T01:00:00Z"),
        )

        assertEquals(
            ReadingStreak(current = 2, longest = 3),
            computeReadingStreak(rows, today = LocalDate.of(2026, 8, 20)),
        )
    }

    @Test
    fun `streak ignores malformed timestamps and abnormal durations`() {
        val rows = listOf(
            session("2026-08-20T01:00:00Z", durationMs = 0),
            session("2026-08-19T01:00:00Z", durationMs = 24 * 60 * 60_000L + 1),
            session("not-an-instant"),
            session("2026-08-16T01:00:00Z"),
            session("2026-08-15T01:00:00Z"),
        )

        assertEquals(
            ReadingStreak(current = 0, longest = 2),
            computeReadingStreak(rows, today = LocalDate.of(2026, 8, 20)),
        )
    }

    private fun session(occurredAt: String, durationMs: Long = 30_000): StatsSessionRow = StatsSessionRow(
        book_id = "book-1",
        occurred_at = occurredAt,
        duration_ms = durationMs,
        progress_percent = null,
    )

    // ── S05-①：统计侧完成态判定（isReadingFinishedRow） ─────────

    @Test
    fun `finished row counting requires explicit state not stale percent`() {
        val rows = listOf(
            row("b1", percent = 100f, state = "finished"),   // 真实读完
            row("b2", percent = 99.7f, state = "finished"),  // 真实读到末尾
            row("b3", percent = 100f, state = "reading"),    // 取消读完后的残余 100%
            row("b4", percent = 30f, state = "reading"),
            row("b5", percent = 0f, state = "completed"),    // 历史值兼容
        )

        assertEquals(3, rows.count { it.isReadingFinishedRow() })
        assertEquals(false, rows.first { it.book_id == "b3" }.isReadingFinishedRow())
    }

    private fun row(bookId: String, percent: Float, state: String) = StatsProgressRow(
        book_id = bookId,
        progress_percent = percent,
        completion_state = state,
    )
}
