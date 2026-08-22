package com.creationreadingassistant.feature.reader.session

import android.os.SystemClock
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Reader 对本地会话模块的唯一输入；调用方不接触切段、门槛或 Room 字段。 */
data class ReadingActivity(
    val bookId: String,
    val active: Boolean,
    val progressPercent: Float?,
)

/**
 * 将阅读器的活动状态转换为可同步的本地阅读会话。
 *
 * 时长使用单调时钟，墙上时间只用于时间戳，因此系统校时不会放大阅读时长。
 * 写入挂在进程级 scope，避免页面 ViewModel 销毁时取消已经生成的会话。
 */
@Singleton
class ReadingSessionRecorder @Inject constructor(
    private val repository: BookRepository,
    private val deviceInfoProvider: DeviceInfoProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    internal var clock: ReadingSessionClock = AndroidReadingSessionClock
    internal var idFactory: () -> String = { UUID.randomUUID().toString() }

    private var segment: ActiveSegment? = null

    /**
     * 同步状态转换保证来自生命周期、加载状态和心跳的事件严格按调用顺序处理。
     * Room 写入在进程级 IO scope 异步完成，不阻塞主线程。
     */
    @Synchronized
    fun update(activity: ReadingActivity) {
        val normalized = activity.copy(
            active = activity.active && activity.bookId.isNotBlank(),
            progressPercent = activity.progressPercent?.takeIf { it.isFinite() }?.coerceIn(0f, 100f),
        )
        val nowWall = clock.wallNow()
        val nowElapsed = clock.elapsedRealtimeMs()
        val current = segment
        val shouldClose = current != null && (
            !normalized.active ||
                current.bookId != normalized.bookId ||
                elapsedSince(current, nowElapsed) >= ROLL_INTERVAL_MS
            )

        if (current != null && shouldClose) {
            val closingProgress = normalized.progressPercent.takeIf { current.bookId == normalized.bookId }
                ?: current.progressPercent
            closeSegment(current, nowWall, nowElapsed, closingProgress)
            segment = null
        } else if (current != null && normalized.active) {
            segment = current.copy(progressPercent = normalized.progressPercent)
        }

        if (normalized.active && segment == null) {
            segment = ActiveSegment(
                bookId = normalized.bookId,
                startedAt = nowWall,
                startedElapsedMs = nowElapsed,
                progressPercent = normalized.progressPercent,
            )
        }
    }

    private fun closeSegment(
        current: ActiveSegment,
        endedAt: Instant,
        endedElapsedMs: Long,
        endProgressPercent: Float?,
    ) {
        val durationMs = elapsedSince(current, endedElapsedMs).coerceAtMost(MAX_SEGMENT_MS)
        if (durationMs < MIN_SEGMENT_MS) return

        val startedAtIso = current.startedAt.toString()
        val endedAtIso = endedAt.toString()
        val deviceId = runCatching { deviceInfoProvider.provide().deviceId }
            .getOrDefault(UNKNOWN_DEVICE_ID)
        val progress = endProgressPercent ?: current.progressPercent
        val id = idFactory()
        val payload = ReadingSessionPayload(
            id = id,
            bookId = current.bookId,
            startedAt = startedAtIso,
            endedAt = endedAtIso,
            durationMs = durationMs,
            progressPercent = progress,
            createdAt = startedAtIso,
            deviceId = deviceId,
            revision = 1,
            updatedAt = endedAtIso,
        )
        val entity = ReadingSessionEntity(
            id = id,
            book_id = current.bookId,
            started_at = startedAtIso,
            ended_at = endedAtIso,
            duration_ms = durationMs,
            progress_percent = progress,
            created_at = startedAtIso,
            device_id = deviceId,
            revision = 1,
            payload = JSON.encodeToString(payload),
            updated_at = endedAtIso,
        )
        applicationScope.launch {
            runCatching { repository.saveReadingSession(entity) }
                .onFailure { AppLog.w("ReadingSession", "persist session failed: ${it.message}") }
        }
    }

    private fun elapsedSince(current: ActiveSegment, nowElapsedMs: Long): Long =
        (nowElapsedMs - current.startedElapsedMs).coerceAtLeast(0L)

    private data class ActiveSegment(
        val bookId: String,
        val startedAt: Instant,
        val startedElapsedMs: Long,
        val progressPercent: Float?,
    )

    private companion object {
        const val MIN_SEGMENT_MS = 30_000L
        const val ROLL_INTERVAL_MS = 5 * 60_000L
        const val MAX_SEGMENT_MS = 24 * 60 * 60_000L
        const val UNKNOWN_DEVICE_ID = "unknown-device"
        val JSON = Json { encodeDefaults = true }
    }
}

internal interface ReadingSessionClock {
    fun wallNow(): Instant
    fun elapsedRealtimeMs(): Long
}

private data object AndroidReadingSessionClock : ReadingSessionClock {
    override fun wallNow(): Instant = Instant.now()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}

@Serializable
private data class ReadingSessionPayload(
    val id: String,
    val bookId: String,
    val startedAt: String,
    val endedAt: String,
    val durationMs: Long,
    val progressPercent: Float?,
    val createdAt: String,
    val deviceId: String,
    val revision: Int,
    val updatedAt: String,
)
