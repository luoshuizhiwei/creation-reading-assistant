package com.creationreadingassistant.data.local.entity

import androidx.room.Entity

/**
 * 历史 locator 的本地解析缓存。它不参与同步，也不回写原实体，避免仅因懒迁移
 * bump revision/updated_at 并触发全设备同步风暴。
 */
@Entity(
    tableName = "reader_anchor_cache",
    primaryKeys = ["kind", "entity_id", "content_key"],
)
data class ReaderAnchorCacheEntity(
    val kind: String,
    val entity_id: String,
    val content_key: String,
    val chapter_index: Int,
    val char_offset: Int,
    /** 0 approximate / 1 recovered / 2 exact */
    val confidence: Int,
    val resolved_at: Long,
)
