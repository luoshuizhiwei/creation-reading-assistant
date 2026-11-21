package com.creationreadingassistant.data.local

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseCompactionTest {

    @Test
    fun `low freelist skips vacuum and marks completion`() = runTest {
        val backend = FakeBackend(
            metrics = DatabaseCompactionMetrics(
                pageCount = 1_024,
                pageSize = 4_096,
                freelistCount = 1,
            ),
        )
        val compaction = DatabaseCompaction(
            backend = backend,
            spaceProvider = FakeSpaceProvider(Long.MAX_VALUE),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val report = compaction.compactOnce()

        assertEquals(0, backend.vacuumCalls)
        assertTrue(backend.done)
        assertEquals(DatabaseCompactionReason.BELOW_THRESHOLD, report.reason)
    }

    @Test
    fun `large freelist with enough space vacuums once then skips completed marker`() = runTest {
        val backend = FakeBackend(
            metrics = DatabaseCompactionMetrics(
                pageCount = 300_000,
                pageSize = 4_096,
                freelistCount = 60_000,
            ),
        )
        val compaction = DatabaseCompaction(
            backend = backend,
            spaceProvider = FakeSpaceProvider(2_000_000_000L),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val first = compaction.compactOnce()
        val second = compaction.compactOnce()

        assertEquals(1, backend.vacuumCalls)
        assertTrue(backend.done)
        assertEquals(DatabaseCompactionReason.SUCCESS, first.reason)
        assertEquals(DatabaseCompactionReason.ALREADY_DONE, second.reason)
    }

    @Test
    fun `insufficient free space skips vacuum and leaves completion unset`() = runTest {
        val backend = FakeBackend(
            metrics = DatabaseCompactionMetrics(
                pageCount = 300_000,
                pageSize = 4_096,
                freelistCount = 60_000,
            ),
        )
        val compaction = DatabaseCompaction(
            backend = backend,
            spaceProvider = FakeSpaceProvider(1_000_000_000L),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val report = compaction.compactOnce()

        assertEquals(0, backend.vacuumCalls)
        assertFalse(backend.done)
        assertEquals(DatabaseCompactionReason.INSUFFICIENT_SPACE, report.reason)
    }

    @Test
    fun `existing completion marker returns before reading database metrics`() = runTest {
        val backend = FakeBackend(
            metrics = DatabaseCompactionMetrics(pageCount = 0, pageSize = 0, freelistCount = 0),
            done = true,
        )
        val compaction = DatabaseCompaction(
            backend = backend,
            spaceProvider = FakeSpaceProvider(0L),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val report = compaction.compactOnce()

        assertEquals(0, backend.vacuumCalls)
        assertEquals(0, backend.metricsReads)
        assertEquals(DatabaseCompactionReason.ALREADY_DONE, report.reason)
    }

    @Test
    fun `volume estimates cover zero pages full freelist and rounded space requirement`() {
        val zeroPages = DatabaseCompactionMetrics(pageCount = 0, pageSize = 4_096, freelistCount = 0)
        val allFree = DatabaseCompactionMetrics(pageCount = 100, pageSize = 4_096, freelistCount = 100)
        val oddLiveBytes = DatabaseCompactionMetrics(pageCount = 1_001, pageSize = 1, freelistCount = 0)

        assertEquals(0L, DatabaseCompactionPolicy.expectedCompactedBytes(zeroPages))
        assertEquals(0L, DatabaseCompactionPolicy.requiredFreeBytes(zeroPages))
        assertEquals(409_600L, DatabaseCompactionPolicy.freelistBytes(allFree))
        assertEquals(0L, DatabaseCompactionPolicy.expectedCompactedBytes(allFree))
        assertEquals(0L, DatabaseCompactionPolicy.requiredFreeBytes(allFree))
        assertEquals(1_001L, DatabaseCompactionPolicy.expectedCompactedBytes(oddLiveBytes))
        assertEquals(1_502L, DatabaseCompactionPolicy.requiredFreeBytes(oddLiveBytes))
    }

    private class FakeBackend(
        var metrics: DatabaseCompactionMetrics,
        var done: Boolean = false,
    ) : DatabaseCompactionBackend {
        var vacuumCalls = 0
        var metricsReads = 0

        override suspend fun isDone(): Boolean = done

        override suspend fun markDone() {
            done = true
        }

        override fun readMetrics(): DatabaseCompactionMetrics {
            metricsReads += 1
            return metrics
        }

        override fun totalBytes(): Long = metrics.pageCount * metrics.pageSize

        override fun hasOpenReadingSession(): Boolean = false

        override fun checkpointAndVacuum() {
            vacuumCalls += 1
        }
    }

    private class FakeSpaceProvider(
        private val available: Long,
    ) : DatabaseSpaceProvider {
        override fun availableBytes(): Long = available
    }
}
