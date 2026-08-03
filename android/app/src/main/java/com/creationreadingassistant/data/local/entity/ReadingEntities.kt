package com.creationreadingassistant.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 书籍在阅读生命周期中的业务状态。字符串值保持与 Room 现有列兼容，不需要迁移表结构。
 */
enum class ReadingCompletionState(val storageValue: String) {
    READING("reading"),
    SHELVED("shelved"),
    FINISHED("finished");

    companion object {
        fun fromStorage(value: String?): ReadingCompletionState = when (value) {
            SHELVED.storageValue -> SHELVED
            FINISHED.storageValue, "completed" -> FINISHED
            else -> READING
        }
    }
}

/**
 * 阅读进度（每本书一条），对齐 V2 reading_progress 表。
 * 标记为 @Immutable 使 Compose 将其识别为稳定类型。
 */
@Serializable
@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["book_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
@Immutable
data class ReadingProgressEntity(
    @PrimaryKey val book_id: String,
    val progress_percent: Float = 0f,
    val last_read_at: String? = null,
    val total_reading_time_ms: Long = 0L,
    val completion_state: String = "reading",
    val current_location_json: String? = null,
    val payload: String? = null,
    val revision: Int = 1,
    val device_id: String? = null,
    val updated_at: String,
    /** 标记为已读完时写入完成时间（epoch millis），未读/在读为 null。用于「已读完」按完成时间倒序（对齐网页 completedAt）。 */
    val completed_at: Long? = null,
    val deleted_at: String? = null,
) {
    val readingState: ReadingCompletionState
        get() = ReadingCompletionState.fromStorage(completion_state)
}

/**
 * 合并阅读器的自动进度写入与现有业务状态。
 *
 * 自动翻页/滚动只负责位置和百分比，不能把用户明确设置的“搁置”或“读完”静默改回“在读”，
 * 也不能因构造一条最小实体而清空累计时长、同步信息等已有字段。
 */
fun mergeReaderProgress(
    existing: ReadingProgressEntity?,
    incoming: ReadingProgressEntity,
    nowIso: String,
    nowMillis: Long,
): ReadingProgressEntity {
    val percent = incoming.progress_percent.coerceIn(0f, 100f)
    val state = when {
        percent >= 99.5f -> ReadingCompletionState.FINISHED
        existing?.readingState == ReadingCompletionState.SHELVED -> ReadingCompletionState.SHELVED
        existing?.readingState == ReadingCompletionState.FINISHED -> ReadingCompletionState.FINISHED
        else -> ReadingCompletionState.READING
    }
    val base = existing ?: incoming
    return base.copy(
        progress_percent = percent,
        last_read_at = incoming.last_read_at ?: nowIso,
        completion_state = state.storageValue,
        current_location_json = incoming.current_location_json ?: existing?.current_location_json,
        payload = incoming.payload ?: existing?.payload,
        revision = maxOf(existing?.revision ?: 1, incoming.revision),
        device_id = incoming.device_id ?: existing?.device_id,
        updated_at = nowIso,
        completed_at = when (state) {
            ReadingCompletionState.FINISHED -> existing?.completed_at ?: nowMillis
            ReadingCompletionState.READING,
            ReadingCompletionState.SHELVED -> null
        },
        deleted_at = null,
    )
}

/**
 * 阅读会话（每次打开一段时间一条），对齐 V2 reading_sessions 表。
 */
@Serializable
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["book_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val book_id: String,
    val started_at: String? = null,
    val ended_at: String? = null,
    val duration_ms: Long = 0L,
    val progress_percent: Float? = null,
    val created_at: String? = null,
    val device_id: String? = null,
    val revision: Int = 1,
    val payload: String? = null,
    val updated_at: String,
    val deleted_at: String? = null,
)
