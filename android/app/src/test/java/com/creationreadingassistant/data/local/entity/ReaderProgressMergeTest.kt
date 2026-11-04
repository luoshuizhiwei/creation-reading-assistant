package com.creationreadingassistant.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReaderProgressMergeTest {
    private val nowIso = "2026-08-02T02:00:00Z"
    private val nowMs = 1_785_610_800_000L

    @Test fun reopeningFinishedBookKeepsFinishedStateAndOriginalCompletionTime() {
        val existing = progress(88f, "finished").copy(completed_at = 123L, total_reading_time_ms = 9_000L)
        val merged = mergeReaderProgress(existing, progress(60f, "reading"), nowIso, nowMs)

        assertEquals(ReadingCompletionState.FINISHED, merged.readingState)
        assertEquals(123L, merged.completed_at)
        assertEquals(9_000L, merged.total_reading_time_ms)
    }

    @Test fun automaticSaveCannotRestoreShelvedBookSilently() {
        val merged = mergeReaderProgress(
            progress(40f, "shelved"),
            progress(42f, "reading"),
            nowIso,
            nowMs,
        )

        assertEquals(ReadingCompletionState.SHELVED, merged.readingState)
        assertEquals(42f, merged.progress_percent)
    }

    @Test fun reachingEndCreatesCompletionTimestamp() {
        val merged = mergeReaderProgress(progress(90f, "reading"), progress(100f, "finished"), nowIso, nowMs)

        assertEquals(ReadingCompletionState.FINISHED, merged.readingState)
        assertEquals(nowMs, merged.completed_at)
        assertNotNull(merged.last_read_at)
    }

    @Test
    fun differentLegacyChaptersAreDifferentPositions() {
        val old = ReadingProgressEntity(book_id = "test", progress_percent = 50f, current_location_json = """{"chapter":1,"offset":20}""", updated_at = "old")
        val incoming = ReadingProgressEntity(book_id = "test", progress_percent = 50f, current_location_json = """{"chapter":2,"offset":20}""", updated_at = "new")
        assertEquals("Different legacy chapters must persist", incoming.current_location_json, mergeReaderProgress(old, incoming, "new", 100L).current_location_json)
    }

    @Test
    fun percentOnlyMovementBelowInventedToleranceStillPersists() {
        val old = ReadingProgressEntity(book_id = "test", progress_percent = 50f, updated_at = "old", last_read_at = "old")
        val incoming = ReadingProgressEntity(book_id = "test", progress_percent = 50.005f, updated_at = "new")
        val result = mergeReaderProgress(old, incoming, "new", 100L)
        assertEquals("Real incoming progress must not be silently dropped", incoming.progress_percent, result.progress_percent, 0f)
    }

    @Test
    fun ordinaryReopenAtSamePositionStillUpdatesLastRead() {
        val old = ReadingProgressEntity(book_id = "test", progress_percent = 50f, updated_at = "old", last_read_at = "old")
        val incoming = ReadingProgressEntity(book_id = "test", progress_percent = 50f, updated_at = "new")
        val result = mergeReaderProgress(old, incoming, "new", 100L)
        assertEquals("A general merge guard cannot distinguish ordinary entry from temporary return", "new", result.last_read_at)
    }

    private fun progress(percent: Float, state: String) = ReadingProgressEntity(
        book_id = "test-book",
        progress_percent = percent,
        completion_state = state,
        current_location_json = """{"offset":10}""",
        updated_at = "2026-08-01T00:00:00Z",
    )
}

