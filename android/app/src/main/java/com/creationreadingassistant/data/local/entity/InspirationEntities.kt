package com.creationreadingassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 灵感（核心创作条目），对齐 V2 inspirations 表。
 */
@Serializable
@Entity(
    tableName = "inspirations",
    indices = [Index("source_book_id")],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["source_book_id"],
        onDelete = ForeignKey.SET_NULL,
    )],
)
data class InspirationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String = "",
    val type: String = "note",
    val status: String = "inbox",
    val source_book_id: String? = null,
    val payload: String? = null,
    val created_at: String,
    val device_id: String? = null,
    val revision: Int = 1,
    val updated_at: String,
    val deleted_at: String? = null,
)

/**
 * 灵感的 AI 变体（多版本改写），对齐 V2 inspiration_variants 表。
 */
@Serializable
@Entity(
    tableName = "inspiration_variants",
    indices = [Index("inspiration_id")],
    foreignKeys = [ForeignKey(
        entity = InspirationEntity::class,
        parentColumns = ["id"],
        childColumns = ["inspiration_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class InspirationVariantEntity(
    @PrimaryKey val id: String,
    val inspiration_id: String,
    val kind: String? = null,
    val content: String? = null,
    val prompt: String? = null,
    val model: String? = null,
    val payload: String? = null,
    val created_at: String,
)

/**
 * 笔记（可挂书或挂灵感），对齐 V2 notes 表。
 */
@Serializable
@Entity(
    tableName = "notes",
    indices = [Index("book_id"), Index("inspiration_id")],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InspirationEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspiration_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val book_id: String? = null,
    val inspiration_id: String? = null,
    val title: String,
    val body: String = "",
    val excerpt: String? = null,
    val chapter_title: String? = null,
    val progress_percent: Float? = null,
    val kind: String = "note",
    val locator_json: String? = null,
    val payload: String? = null,
    val created_at: String,
    val device_id: String? = null,
    val revision: Int = 1,
    val updated_at: String,
    val deleted_at: String? = null,
)

/**
 * 高亮标注，对齐 V2 highlights 表。
 */
@Serializable
@Entity(
    tableName = "highlights",
    indices = [Index("book_id")],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["book_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class HighlightEntity(
    @PrimaryKey val id: String,
    val book_id: String,
    val text: String = "",
    val note: String? = null,
    val color: String? = null,
    val chapter_title: String? = null,
    val progress_percent: Float? = null,
    val locator_json: String? = null,
    val payload: String? = null,
    val created_at: String,
    val device_id: String? = null,
    val revision: Int = 1,
    val updated_at: String,
    val deleted_at: String? = null,
)
