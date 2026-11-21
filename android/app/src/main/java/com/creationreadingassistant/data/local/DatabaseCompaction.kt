package com.creationreadingassistant.data.local

import android.content.Context
import android.os.SystemClock
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** SQLite 页指标；字节值统一由 [DatabaseCompactionPolicy] 计算，避免调用方公式漂移。 */
data class DatabaseCompactionMetrics(
    val pageCount: Long,
    val pageSize: Long,
    val freelistCount: Long,
)

data class DatabaseCompactionInspection(
    val totalBytes: Long,
    val metrics: DatabaseCompactionMetrics,
    val availableBytes: Long,
    val freelistBytes: Long,
    val expectedCompactedBytes: Long,
    val requiredFreeBytes: Long,
) {
    val hasWorthwhileFreelist: Boolean get() = freelistBytes >= DatabaseCompactionPolicy.MIN_FREELIST_BYTES
}

data class DatabaseCompactionReport(
    val timestamp: String,
    val reason: DatabaseCompactionReason,
    /** true 表示确实进入了 checkpoint + VACUUM 尝试，包括中途失败。 */
    val executed: Boolean,
    val beforeBytes: Long,
    val afterBytes: Long,
    val durationMs: Long,
    val metrics: DatabaseCompactionMetrics,
    val availableBytes: Long = 0L,
    val expectedCompactedBytes: Long = 0L,
    val requiredFreeBytes: Long = 0L,
    val errorMessage: String? = null,
) {
    fun diagnosticLine(): String =
        "time=$timestamp executed=$executed beforeBytes=$beforeBytes afterBytes=$afterBytes " +
            "durationMs=$durationMs reason=$reason freelistBytes=${DatabaseCompactionPolicy.freelistBytes(metrics)} " +
            "availableBytes=$availableBytes expectedCompactedBytes=$expectedCompactedBytes " +
            "requiredFreeBytes=$requiredFreeBytes" + (errorMessage?.let { " error=$it" } ?: "")
}

enum class DatabaseCompactionReason {
    SUCCESS,
    ALREADY_DONE,
    BELOW_THRESHOLD,
    INSUFFICIENT_SPACE,
    DEFERRED_FOREGROUND,
    DEFERRED_READING_SESSION,
    FAILED,
}

object DatabaseCompactionPolicy {
    const val MIN_FREELIST_BYTES = 200L * 1024L * 1024L

    fun freelistBytes(metrics: DatabaseCompactionMetrics): Long =
        metrics.freelistCount * metrics.pageSize

    fun expectedCompactedBytes(metrics: DatabaseCompactionMetrics): Long =
        (metrics.pageCount - metrics.freelistCount).coerceAtLeast(0L) * metrics.pageSize

    /** VACUUM 需要 live DB 的临时副本，另留 50% 余量；向上取整到 1.5 倍。 */
    fun requiredFreeBytes(metrics: DatabaseCompactionMetrics): Long {
        val expected = expectedCompactedBytes(metrics)
        return expected + (expected + 1L) / 2L
    }
}

interface DatabaseCompactionBackend {
    suspend fun isDone(): Boolean
    suspend fun markDone()
    fun readMetrics(): DatabaseCompactionMetrics
    fun totalBytes(): Long
    fun hasOpenReadingSession(): Boolean
    fun checkpointAndVacuum()
}

interface DatabaseSpaceProvider {
    fun availableBytes(): Long
}

/**
 * 一次性数据库回收持有者。自动路径只可在后台且无未结束阅读会话时运行；
 * 手动路径仍需用户确认，但共享同一互斥、空间门槛、日志和完成信号。
 */
@Singleton
class DatabaseCompaction internal constructor(
    private val backend: DatabaseCompactionBackend,
    private val spaceProvider: DatabaseSpaceProvider,
    private val ioDispatcher: CoroutineDispatcher,
    private val isAppInForeground: () -> Boolean = { false },
    private val logger: (DatabaseCompactionReport) -> Unit = {},
    private val elapsedRealtime: () -> Long = { 0L },
) {
    @Inject constructor(
        database: AppDatabase,
        settingsStore: SettingsStore,
        @ApplicationContext context: Context,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        backend = RoomDatabaseCompactionBackend(database, settingsStore, context),
        spaceProvider = StatFsDatabaseSpaceProvider(context),
        ioDispatcher = ioDispatcher,
        isAppInForeground = { isAppForeground(context) },
        logger = { report ->
            if (report.reason == DatabaseCompactionReason.FAILED) {
                AppLog.e("DatabaseMaintenance", report.diagnosticLine())
            } else {
                AppLog.event("DatabaseMaintenance", report.diagnosticLine())
            }
        },
        elapsedRealtime = { SystemClock.elapsedRealtime() },
    )

    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _events = MutableStateFlow<DatabaseCompactionReport?>(null)
    val events: StateFlow<DatabaseCompactionReport?> = _events.asStateFlow()

    suspend fun inspect(): DatabaseCompactionInspection = withContext(ioDispatcher) {
        val metrics = backend.readMetrics()
        DatabaseCompactionInspection(
            totalBytes = backend.totalBytes(),
            metrics = metrics,
            availableBytes = spaceProvider.availableBytes(),
            freelistBytes = DatabaseCompactionPolicy.freelistBytes(metrics),
            expectedCompactedBytes = DatabaseCompactionPolicy.expectedCompactedBytes(metrics),
            requiredFreeBytes = DatabaseCompactionPolicy.requiredFreeBytes(metrics),
        )
    }

    suspend fun compactOnce(manual: Boolean = false): DatabaseCompactionReport = mutex.withLock {
        _running.value = true
        try {
            withContext(ioDispatcher) { compactOnceLocked(manual) }
        } finally {
            _running.value = false
        }
    }

    private suspend fun compactOnceLocked(manual: Boolean): DatabaseCompactionReport {
        if (!manual && backend.isDone()) {
            return finish(
                DatabaseCompactionReport(
                    timestamp = Instant.now().toString(),
                    reason = DatabaseCompactionReason.ALREADY_DONE,
                    executed = false,
                    beforeBytes = 0L,
                    afterBytes = 0L,
                    durationMs = 0L,
                    metrics = DatabaseCompactionMetrics(0L, 0L, 0L),
                ),
            )
        }
        if (!manual && isAppInForeground()) {
            return finish(
                DatabaseCompactionReport(
                    timestamp = Instant.now().toString(),
                    reason = DatabaseCompactionReason.DEFERRED_FOREGROUND,
                    executed = false,
                    beforeBytes = 0L,
                    afterBytes = 0L,
                    durationMs = 0L,
                    metrics = DatabaseCompactionMetrics(0L, 0L, 0L),
                ),
            )
        }
        if (!manual && backend.hasOpenReadingSession()) {
            return finish(
                DatabaseCompactionReport(
                    timestamp = Instant.now().toString(),
                    reason = DatabaseCompactionReason.DEFERRED_READING_SESSION,
                    executed = false,
                    beforeBytes = 0L,
                    afterBytes = 0L,
                    durationMs = 0L,
                    metrics = DatabaseCompactionMetrics(0L, 0L, 0L),
                ),
            )
        }

        val metrics = backend.readMetrics()
        val beforeBytes = backend.totalBytes()
        val availableBytes = spaceProvider.availableBytes()
        val expectedBytes = DatabaseCompactionPolicy.expectedCompactedBytes(metrics)
        val requiredBytes = DatabaseCompactionPolicy.requiredFreeBytes(metrics)
        if (DatabaseCompactionPolicy.freelistBytes(metrics) < DatabaseCompactionPolicy.MIN_FREELIST_BYTES) {
            backend.markDone()
            return finish(
                DatabaseCompactionReport(
                    timestamp = Instant.now().toString(),
                    reason = DatabaseCompactionReason.BELOW_THRESHOLD,
                    executed = false,
                    beforeBytes = beforeBytes,
                    afterBytes = beforeBytes,
                    durationMs = 0L,
                    metrics = metrics,
                    availableBytes = availableBytes,
                    expectedCompactedBytes = expectedBytes,
                    requiredFreeBytes = requiredBytes,
                ),
            )
        }
        if (availableBytes < requiredBytes) {
            return finish(
                DatabaseCompactionReport(
                    timestamp = Instant.now().toString(),
                    reason = DatabaseCompactionReason.INSUFFICIENT_SPACE,
                    executed = false,
                    beforeBytes = beforeBytes,
                    afterBytes = beforeBytes,
                    durationMs = 0L,
                    metrics = metrics,
                    availableBytes = availableBytes,
                    expectedCompactedBytes = expectedBytes,
                    requiredFreeBytes = requiredBytes,
                ),
            )
        }

        val startedMs = elapsedRealtime()
        return try {
            backend.checkpointAndVacuum()
            backend.markDone()
            finish(
                DatabaseCompactionReport(
                    timestamp = Instant.now().toString(),
                    reason = DatabaseCompactionReason.SUCCESS,
                    executed = true,
                    beforeBytes = beforeBytes,
                    afterBytes = backend.totalBytes(),
                    durationMs = (elapsedRealtime() - startedMs).coerceAtLeast(0L),
                    metrics = metrics,
                    availableBytes = availableBytes,
                    expectedCompactedBytes = expectedBytes,
                    requiredFreeBytes = requiredBytes,
                ),
            )
        } catch (cancelled: CancellationException) {
            finish(
                failureReport(
                    beforeBytes = beforeBytes,
                    startedMs = startedMs,
                    metrics = metrics,
                    availableBytes = availableBytes,
                    expectedBytes = expectedBytes,
                    requiredBytes = requiredBytes,
                    error = cancelled,
                ),
            )
            throw cancelled
        } catch (error: Exception) {
            finish(
                failureReport(
                    beforeBytes = beforeBytes,
                    startedMs = startedMs,
                    metrics = metrics,
                    availableBytes = availableBytes,
                    expectedBytes = expectedBytes,
                    requiredBytes = requiredBytes,
                    error = error,
                ),
            )
        }
    }

    private fun failureReport(
        beforeBytes: Long,
        startedMs: Long,
        metrics: DatabaseCompactionMetrics,
        availableBytes: Long,
        expectedBytes: Long,
        requiredBytes: Long,
        error: Exception,
    ): DatabaseCompactionReport = DatabaseCompactionReport(
        timestamp = Instant.now().toString(),
        reason = DatabaseCompactionReason.FAILED,
        executed = true,
        beforeBytes = beforeBytes,
        afterBytes = runCatching { backend.totalBytes() }.getOrDefault(beforeBytes),
        durationMs = (elapsedRealtime() - startedMs).coerceAtLeast(0L),
        metrics = metrics,
        availableBytes = availableBytes,
        expectedCompactedBytes = expectedBytes,
        requiredFreeBytes = requiredBytes,
        errorMessage = "${error.javaClass.simpleName}: ${error.message}",
    )

    private fun finish(report: DatabaseCompactionReport): DatabaseCompactionReport {
        _events.value = report
        if (report.reason != DatabaseCompactionReason.ALREADY_DONE &&
            report.reason != DatabaseCompactionReason.DEFERRED_FOREGROUND &&
            report.reason != DatabaseCompactionReason.DEFERRED_READING_SESSION
        ) {
            logger(report)
        }
        return report
    }
}
