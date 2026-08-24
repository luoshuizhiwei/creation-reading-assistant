package com.creationreadingassistant.ui.screen.shelf

import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookDetailStatsPolicyTest {
    @Test
    fun `session total fills stale zero progress duration`() {
        val sessions = listOf(session("s1", 120_000L), session("s2", 300_000L))

        assertEquals(420_000L, bookDetailReadingTimeMs(0L, sessions))
    }

    @Test
    fun `progress duration remains authoritative when larger than session total`() {
        val sessions = listOf(session("s1", 120_000L))

        assertEquals(600_000L, bookDetailReadingTimeMs(600_000L, sessions))
    }

    private fun session(id: String, durationMs: Long) = ReadingSessionEntity(
        id = id,
        book_id = "book-1",
        duration_ms = durationMs,
        updated_at = "2026-08-24T00:00:00Z",
    )
}
