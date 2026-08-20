package com.creationreadingassistant.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "chapter_reads",
    primaryKeys = ["book_id", "chapter_index"],
    indices = [Index("book_id")],
)
@Immutable
data class ChapterReadEntity(
    val book_id: String,
    val chapter_index: Int,
    val read_at: String,
)
