package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyReadingViewModelTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test fun timelineSeparatesReadingShelvedAndFinished() {
        val books = listOf(book("reading", "在读"), book("shelved", "搁置"), book("finished", "读完"))
        val progress = listOf(
            progress("reading", 35f, "reading", "2026-07-10T08:00:00Z"),
            progress("shelved", 48f, "shelved", "2026-06-08T08:00:00Z"),
            progress("finished", 100f, "finished", "2026-05-01T08:00:00Z"),
        )
        val state = buildMyReadingUiState(books, progress, emptyList(), "", MyReadingFilter.ALL, zone)

        assertEquals(3, state.counts[MyReadingFilter.ALL])
        assertEquals(1, state.counts[MyReadingFilter.READING])
        assertEquals(1, state.counts[MyReadingFilter.SHELVED])
        assertEquals(1, state.counts[MyReadingFilter.FINISHED])
        assertEquals(listOf(7, 6, 5), state.months.map { it.month })
    }

    @Test fun queryAndFilterAreAppliedTogether() {
        val books = listOf(book("a", "纸上远行"), book("b", "另一册"))
        val progress = listOf(
            progress("a", 20f, "shelved", "2026-07-10T08:00:00Z"),
            progress("b", 20f, "reading", "2026-07-11T08:00:00Z"),
        )
        val state = buildMyReadingUiState(books, progress, emptyList(), "纸上", MyReadingFilter.SHELVED, zone)

        assertEquals(listOf("a"), state.months.flatMap { it.items }.map { it.book.id })
    }

    @Test fun sessionOnlyBookStillAppearsAndUsesSessionDuration() {
        val sessions = listOf(
            ReadingSessionEntity(
                id = "s1",
                book_id = "a",
                started_at = "2026-07-01T08:00:00Z",
                duration_ms = 90_000,
                updated_at = "2026-07-01T08:02:00Z",
            ),
        )
        val state = buildMyReadingUiState(listOf(book("a", "测试书")), emptyList(), sessions, "", MyReadingFilter.ALL, zone)

        assertTrue(state.months.single().items.single().readingTimeMs == 90_000L)
    }

    private fun book(id: String, title: String) = BookEntity(
        id = id,
        title = title,
        format = "txt",
        size = 1,
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun progress(id: String, percent: Float, state: String, lastRead: String) = ReadingProgressEntity(
        book_id = id,
        progress_percent = percent,
        completion_state = state,
        last_read_at = lastRead,
        updated_at = lastRead,
    )
}
