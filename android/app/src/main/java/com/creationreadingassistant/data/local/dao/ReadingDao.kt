package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId AND deleted_at IS NULL")
    suspend fun getByBook(bookId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAllActive(): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<ReadingProgressEntity>
}

@Dao
interface ReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByBook(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAllActive(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE id = :id")
    suspend fun getById(id: String): ReadingSessionEntity?

    @Query("SELECT * FROM reading_sessions WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<ReadingSessionEntity>
}
