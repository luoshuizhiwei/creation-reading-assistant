package com.creationreadingassistant.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 标签，对齐 V2 tags 表（name 唯一）。
 * 标记为 @Immutable 使 Compose 将其识别为稳定类型。
 */
@Serializable
@Entity(tableName = "tags")
@Immutable
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String? = null,
    val type: String? = null,
    val sort_order: Int = 0,
    val created_at: String? = null,
    val device_id: String? = null,
    val revision: Int = 1,
    val payload: String? = null,
    val updated_at: String,
    val deleted_at: String? = null,
)

/**
 * 分类（可嵌套 parent_id），对齐 V2 categories 表（name 唯一）。
 * 标记为 @Immutable 使 Compose 将其识别为稳定类型。
 */
@Serializable
@Entity(tableName = "categories")
@Immutable
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val cover_tone: String? = null,
    val parent_id: String? = null,
    val sort_order: Int = 0,
    val created_at: String? = null,
    val device_id: String? = null,
    val revision: Int = 1,
    val payload: String? = null,
    val updated_at: String,
    val deleted_at: String? = null,
)

/**
 * 书架，对齐 V2 shelves 表。
 * 标记为 @Immutable 使 Compose 将其识别为稳定类型。
 */
@Serializable
@Entity(tableName = "shelves")
@Immutable
data class ShelfEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sort_order: Int = 0,
    val created_at: String? = null,
    val device_id: String? = null,
    val revision: Int = 1,
    val payload: String? = null,
    val updated_at: String,
    val deleted_at: String? = null,
)

/**
 * 书-标签 连接表（N:N），对齐 V2 book_tag。
 */
@Entity(
    tableName = "book_tag",
    primaryKeys = ["book_id", "tag_id"],
    indices = [Index("tag_id")],
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
)
@kotlinx.serialization.Serializable
data class BookTagEntity(
    val book_id: String,
    val tag_id: String,
)

/**
 * 书-分类 连接表（N:N），对齐 V2 book_category。
 */
@Entity(
    tableName = "book_category",
    primaryKeys = ["book_id", "category_id"],
    indices = [Index("category_id")],
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.CASCADE),
    ],
)
@kotlinx.serialization.Serializable
data class BookCategoryEntity(
    val book_id: String,
    val category_id: String,
)

/**
 * 书架-书 连接表（带排序），对齐 V2 shelf_book。
 */
@Entity(
    tableName = "shelf_book",
    primaryKeys = ["shelf_id", "book_id"],
    indices = [Index("book_id")],
    foreignKeys = [
        ForeignKey(entity = ShelfEntity::class, parentColumns = ["id"], childColumns = ["shelf_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
    ],
)
@kotlinx.serialization.Serializable
data class ShelfBookEntity(
    val shelf_id: String,
    val book_id: String,
    val position: Int = 0,
)
