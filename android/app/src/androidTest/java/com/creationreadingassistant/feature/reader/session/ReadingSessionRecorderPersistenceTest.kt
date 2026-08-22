package com.creationreadingassistant.feature.reader.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ReadingSessionRecorderPersistenceTest {
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
    fun eligibleForegroundSegmentReachesRoomAndStatsAggregates() = runBlocking {
        val now = "2026-08-20T00:00:00Z"
        database.bookDao().upsert(
            BookEntity(
                id = "book-1",
                title = "测试 TXT",
                format = "txt",
                updated_at = now,
            ),
        )
        val repository = BookRepository(
            bookDao = database.bookDao(),
            bookContentDao = database.bookContentDao(),
            bookFileDao = database.bookFileDao(),
            progressDao = database.readingProgressDao(),
            sessionDao = database.readingSessionDao(),
            noteDao = database.noteDao(),
            highlightDao = database.highlightDao(),
            inspirationDao = database.inspirationDao(),
            bookTagDao = database.bookTagDao(),
            bookCategoryDao = database.bookCategoryDao(),
            shelfBookDao = database.shelfBookDao(),
            chapterReadDao = database.chapterReadDao(),
        )
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val clock = FakeClock()
        val recorder = ReadingSessionRecorder(
            repository = repository,
            deviceInfoProvider = DeviceInfoProvider(context),
            applicationScope = testScope,
        ).also {
            it.clock = clock
            it.idFactory = { "session-1" }
        }

        recorder.update(ReadingActivity("book-1", active = true, progressPercent = 10f))
        clock.advance(30_000)
        recorder.update(ReadingActivity("book-1", active = false, progressPercent = 12f))

        val rows = database.readingSessionDao().observeByBook("book-1").first()
        assertEquals(1, rows.size)
        assertEquals(30_000L, rows.single().duration_ms)
        assertEquals(12f, rows.single().progress_percent)
        assertEquals(30_000L, database.readingSessionDao().sumAllActiveDuration())
        assertEquals(30_000L, database.readingSessionDao().sumOccurredDurationBetween(
            startEpochSecond = Instant.parse("2026-08-20T00:00:00Z").epochSecond,
            endEpochSecond = Instant.parse("2026-08-21T00:00:00Z").epochSecond,
            coarseIso = "2026-08-18T00:00:00Z",
        ))
    }

    private class FakeClock : ReadingSessionClock {
        private var wall = Instant.parse("2026-08-20T01:00:00Z")
        private var elapsed = 1_000L

        override fun wallNow(): Instant = wall
        override fun elapsedRealtimeMs(): Long = elapsed

        fun advance(ms: Long) {
            wall = wall.plusMillis(ms)
            elapsed += ms
        }
    }
}
