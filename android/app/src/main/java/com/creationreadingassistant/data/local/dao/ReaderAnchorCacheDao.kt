package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.creationreadingassistant.data.local.entity.ReaderAnchorCacheEntity

@Dao
interface ReaderAnchorCacheDao {
    @Query(
        "SELECT * FROM reader_anchor_cache " +
            "WHERE kind = :kind AND entity_id = :entityId AND content_key = :contentKey",
    )
    suspend fun get(kind: String, entityId: String, contentKey: String): ReaderAnchorCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReaderAnchorCacheEntity)

    @Query("DELETE FROM reader_anchor_cache WHERE content_key = :contentKey")
    suspend fun clearBook(contentKey: String)
}
