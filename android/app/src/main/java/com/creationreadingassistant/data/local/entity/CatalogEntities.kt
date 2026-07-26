package com.creationreadingassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 书籍主表 —— 对齐 mobile/src/storage/mobile-schema.ts 的 V2 books 表。
 * payload TEXT 保留为「无损真相源」，与现有同步契约一致。
 */
@Serializable
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String? = null,
    val format: String,
    val original_file_name: String? = null,
    val content_hash: String? = null,
    val size: Int = 0,
    val local_uri: String? = null,
    val local_content_path: String? = null,
    val content_status: String = "available",
    val cover_data_url: String? = null,
    val description: String? = null,
    val imported_at: String? = null,
    val device_id: String? = null,
    val payload: String? = null,
    val revision: Int = 1,
    val updated_at: String,
    val deleted_at: String? = null,
)

/**
 * 书籍正文（预览/ epub 解析结果），与 books 一对一。
 */
@Serializable
@Entity(
    tableName = "book_content",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["book_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class BookContentEntity(
    @PrimaryKey val book_id: String,
    val reader_preview: String? = null,
    val epub_json: String? = null,
)

/**
 * 书籍文件元信息（含 chunk 化传输所需字段），与 books 一对一。
 */
@Serializable
@Entity(
    tableName = "book_files",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["book_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class BookFileEntity(
    @PrimaryKey val book_id: String,
    val file_name: String,
    val format: String,
    val content_hash: String? = null,
    val size: Int = 0,
    val local_uri: String? = null,
    val payload: String? = null,
    val updated_at: String,
    val deleted_at: String? = null,
)
