package com.creationreadingassistant.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingStatsAggregationPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aggregatesUseOccurredTimeAndExcludeAbnormalDurations() = runBlocking {
        val dao = database.readingSessionDao()
        database.bookDao().upsert(
            BookEntity(
                id = "book-stats",
                title = "测试 TXT",
                format = "txt",
                updated_at = "2026-08-20T10:00:00Z",
            ),
        )

        listOf(
            session(
                id = "started-today",
                startedAt = "2026-08-20T01:00:00Z",
                createdAt = "2026-08-18T01:00:00Z",
                durationMs = 10_000L,
            ),
            session(
                id = "fallback-created-today",
                startedAt = null,
                createdAt = "2026-08-20T02:00:00Z",
                durationMs = 20_000L,
            ),
            session(
                id = "started-yesterday",
                startedAt = "2026-08-19T23:00:00Z",
                createdAt = "2026-08-20T03:00:00Z",
                durationMs = 40_000L,
            ),
            session(
                id = "zero-duration",
                startedAt = "2026-08-20T04:00:00Z",
                createdAt = "2026-08-20T04:00:00Z",
                durationMs = 0L,
            ),
            session(
                id = "overlong-duration",
                startedAt = "2026-08-20T05:00:00Z",
                createdAt = "2026-08-20T05:00:00Z",
                durationMs = 86_400_001L,
            ),
        ).forEach { dao.upsert(it) }

        val todayStart = Instant.parse("2026-08-20T00:00:00Z").epochSecond
        val tomorrowStart = Instant.parse("2026-08-21T00:00:00Z").epochSecond

        assertEquals(70_000L, dao.sumAllActiveDuration())
        assertEquals(
            30_000L,
            dao.sumOccurredDurationBetween(
                startEpochSecond = todayStart,
                endEpochSecond = tomorrowStart,
                coarseIso = "2026-08-18T00:00:00Z",
            ),
        )
        assertEquals(
            30_000L,
            dao.sumOccurredDurationSince(
                startEpochSecond = todayStart,
                coarseIso = "2026-08-18T00:00:00Z",
            ),
        )
    }

    private fun session(
        id: String,
        startedAt: String?,
        createdAt: String,
        durationMs: Long,
    ) = ReadingSessionEntity(
        id = id,
        book_id = "book-stats",
        started_at = startedAt,
        ended_at = startedAt,
        duration_ms = durationMs,
        created_at = createdAt,
        updated_at = createdAt,
    )
}
