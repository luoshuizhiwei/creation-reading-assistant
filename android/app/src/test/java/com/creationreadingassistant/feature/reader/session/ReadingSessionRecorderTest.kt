package com.creationreadingassistant.feature.reader.session

import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.data.repository.BookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReadingSessionRecorderTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val repository = mockk<BookRepository>(relaxed = true)
    private val deviceInfoProvider = mockk<DeviceInfoProvider>()
    private val clock = FakeReadingSessionClock()
    private lateinit var recorder: ReadingSessionRecorder

    @Before
    fun setUp() {
        every { deviceInfoProvider.provide() } returns SyncContract.DeviceInfo(
            deviceId = "device-1",
            name = "test-device",
            platform = SyncContract.SyncPlatform.android,
            pairedAt = "2026-08-20T00:00:00Z",
            lastSeenAt = "2026-08-20T00:00:00Z",
        )
        coEvery { repository.saveReadingSession(any()) } returns Unit
        recorder = ReadingSessionRecorder(repository, deviceInfoProvider, scope).also {
            it.clock = clock
            it.idFactory = { "session-${clock.elapsedRealtimeMs()}" }
        }
    }

    @Test
    fun `inactive and sub-threshold time do not write records`() {
        recorder.update(ReadingActivity("book-1", active = false, progressPercent = 10f))
        recorder.update(ReadingActivity("book-1", active = true, progressPercent = 10f))
        clock.advance(elapsedMs = 29_999, wallMs = 29_999)
        recorder.update(ReadingActivity("book-1", active = false, progressPercent = 11f))
        scope.runCurrent()

        coVerify(exactly = 0) { repository.saveReadingSession(any()) }
    }

    @Test
    fun `pause writes eligible segment using monotonic duration and complete sync payload`() {
        recorder.update(ReadingActivity("book-1", active = true, progressPercent = 12f))
        clock.advance(elapsedMs = 30_000, wallMs = 7_200_000)
        recorder.update(ReadingActivity("book-1", active = false, progressPercent = 13.5f))
        scope.runCurrent()

        coVerify(exactly = 1) {
            repository.saveReadingSession(withArg { session ->
                assertEquals("book-1", session.book_id)
                assertEquals(30_000L, session.duration_ms)
                assertEquals(13.5f, session.progress_percent)
                assertEquals("device-1", session.device_id)
                assertEquals(1, session.revision)
                assertEquals("2026-08-20T00:00:00Z", session.started_at)
                assertEquals("2026-08-20T02:00:00Z", session.ended_at)
                assertEquals(session.started_at, session.created_at)
                assertEquals(session.ended_at, session.updated_at)
                assertTrue(session.payload.orEmpty().contains("\"durationMs\":30000"))
                assertTrue(session.payload.orEmpty().contains("\"bookId\":\"book-1\""))
            })
        }
    }

    @Test
    fun `five minute heartbeat rolls segment and continues timing`() {
        recorder.update(ReadingActivity("book-1", active = true, progressPercent = 20f))
        clock.advance(elapsedMs = 300_000, wallMs = 300_000)
        recorder.update(ReadingActivity("book-1", active = true, progressPercent = 30f))
        clock.advance(elapsedMs = 30_000, wallMs = 30_000)
        recorder.update(ReadingActivity("book-1", active = false, progressPercent = 31f))
        scope.runCurrent()

        coVerify(exactly = 1) {
            repository.saveReadingSession(match { it.duration_ms == 300_000L && it.progress_percent == 30f })
        }
        coVerify(exactly = 1) {
            repository.saveReadingSession(match { it.duration_ms == 30_000L && it.progress_percent == 31f })
        }
    }

    @Test
    fun `book switch closes old segment without contaminating new book`() {
        recorder.update(ReadingActivity("book-1", active = true, progressPercent = 40f))
        clock.advance(elapsedMs = 45_000, wallMs = 45_000)
        recorder.update(ReadingActivity("book-2", active = true, progressPercent = 1f))
        clock.advance(elapsedMs = 30_000, wallMs = 30_000)
        recorder.update(ReadingActivity("book-2", active = false, progressPercent = 2f))
        recorder.update(ReadingActivity("book-2", active = false, progressPercent = 2f))
        scope.runCurrent()

        coVerify(exactly = 1) {
            repository.saveReadingSession(match {
                it.book_id == "book-1" && it.duration_ms == 45_000L && it.progress_percent == 40f
            })
        }
        coVerify(exactly = 1) {
            repository.saveReadingSession(match { it.book_id == "book-2" && it.duration_ms == 30_000L })
        }
    }

    @Test
    fun `persistence failure is contained after the segment is closed`() {
        coEvery { repository.saveReadingSession(any()) } throws IllegalStateException("disk unavailable")
        recorder.update(ReadingActivity("book-1", active = true, progressPercent = 1f))
        clock.advance(elapsedMs = 30_000, wallMs = 30_000)
        recorder.update(ReadingActivity("book-1", active = false, progressPercent = 2f))

        scope.runCurrent()

        coVerify(exactly = 1) { repository.saveReadingSession(any()) }
    }

    private class FakeReadingSessionClock : ReadingSessionClock {
        private var wall = Instant.parse("2026-08-20T00:00:00Z")
        private var elapsed = 100_000L

        override fun wallNow(): Instant = wall

        override fun elapsedRealtimeMs(): Long = elapsed

        fun advance(elapsedMs: Long, wallMs: Long) {
            elapsed += elapsedMs
            wall = wall.plusMillis(wallMs)
        }
    }
}
