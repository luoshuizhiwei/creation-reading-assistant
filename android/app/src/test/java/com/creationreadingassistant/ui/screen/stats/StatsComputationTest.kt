package com.creationreadingassistant.ui.screen.stats

import com.creationreadingassistant.data.local.dao.StatsSessionRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsComputationTest {

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
}
