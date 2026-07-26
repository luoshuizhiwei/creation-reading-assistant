package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.creationreadingassistant.data.local.entity.SyncAccountEntity
import com.creationreadingassistant.data.local.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: SyncAccountEntity)

    @Query("SELECT * FROM sync_accounts WHERE deleted_at IS NULL ORDER BY created_at ASC")
    fun observeAllActive(): Flow<List<SyncAccountEntity>>
}

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: SyncStateEntity)

    @Query("SELECT payload FROM sync_state WHERE `key` = :key")
    suspend fun getPayload(key: String): String?

    @Query("SELECT * FROM sync_state")
    fun observeAll(): Flow<List<SyncStateEntity>>
}
