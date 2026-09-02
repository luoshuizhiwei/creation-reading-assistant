package com.creationreadingassistant.ui.screen.stats

import com.creationreadingassistant.data.local.dao.StatsSessionRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HeatmapBuildTest {

    private fun instantOn(day: LocalDate): String =
        day.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    @Test
    fun `empty sessions produce 365 zero-duration cells ending today`() {
        val today = LocalDate.of(2026, 6, 1)
        val cells = buildHeatmap(emptyList(), today = today)
        assertEquals(365, cells.size)
        assertTrue(cells.all { it.durationMs == 0L })
        assertEquals(LocalDate.of(2025, 6, 2), cells.first().date)
        assertEquals(today, cells.last().date)
    }

    @Test
    fun `buckets same-day sessions and normalizes intensity to max`() {
        val day = LocalDate.of(2026, 5, 20)
        val instant = instantOn(day)
        val sessions = listOf(
            StatsSessionRow(book_id = "b1", occurred_at = instant, duration_ms = 120_000, progress_percent = 1f),
            StatsSessionRow(book_id = "b1", occurred_at = instant, duration_ms = 60_000, progress_percent = 1f),
            StatsSessionRow(book_id = "b2", occurred_at = instant, duration_ms = 60_000, progress_percent = 1f),
        )
        val cells = buildHeatmap(sessions, today = day)
        val cell = cells.first { it.date == day }
        assertEquals(240_000, cell.durationMs)
        assertEquals(1f, cell.intensity, 0.0001f)
    }

    @Test
    fun `invalid durations are excluded from the heatmap`() {
        val day = LocalDate.of(2026, 5, 20)
        val instant = instantOn(day)
        val sessions = listOf(
            StatsSessionRow(book_id = "b1", occurred_at = instant, duration_ms = 0, progress_percent = 1f),
            StatsSessionRow(book_id = "b1", occurred_at = instant, duration_ms = -5, progress_percent = 1f),
        )
        val cells = buildHeatmap(sessions, today = day)
        assertEquals(0L, cells.first { it.date == day }.durationMs)
    }

    @Test
    fun `out-of-window sessions are ignored`() {
        val today = LocalDate.of(2026, 6, 1)
        val old = today.minusDays(400)
        val sessions = listOf(
            StatsSessionRow(book_id = "b1", occurred_at = instantOn(old), duration_ms = 90_000, progress_percent = 1f),
        )
        val cells = buildHeatmap(sessions, today = today)
        // 窗口仅覆盖 today-364 .. today，old 落在窗口外，所有格均为 0
        assertTrue(cells.all { it.durationMs == 0L })
    }
}
