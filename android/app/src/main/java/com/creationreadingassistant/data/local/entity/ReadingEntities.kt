package com.creationreadingassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 阅读进度（每本书一条），对齐 V2 reading_progress 表。
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
)

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
