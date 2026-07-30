package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspirationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(inspiration: InspirationEntity)

    @Query("SELECT * FROM inspirations WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAllActive(): Flow<List<InspirationEntity>>

    @Query("SELECT created_at FROM inspirations WHERE deleted_at IS NULL")
    fun observeStatsCreatedRows(): Flow<List<StatsCreatedRow>>

    @Query("SELECT * FROM inspirations WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: String): InspirationEntity?

    @Query("SELECT * FROM inspirations WHERE deleted_at IS NULL AND (title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%') ORDER BY updated_at DESC LIMIT 20")
    suspend fun search(q: String): List<InspirationEntity>

    @Query("SELECT * FROM inspirations WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<InspirationEntity>

    @Query("SELECT * FROM inspirations WHERE source_book_id = :bookId AND deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeByBook(bookId: String): Flow<List<InspirationEntity>>
}

@Dao
interface InspirationVariantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(variant: InspirationVariantEntity)

    @Query("DELETE FROM inspiration_variants WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM inspiration_variants WHERE inspiration_id = :inspirationId")
    fun observeByInspiration(inspirationId: String): Flow<List<InspirationVariantEntity>>
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAllActive(): Flow<List<NoteEntity>>

    @Query("SELECT created_at FROM notes WHERE deleted_at IS NULL")
    fun observeStatsCreatedRows(): Flow<List<StatsCreatedRow>>

    @Query("SELECT * FROM notes WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByBook(bookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND (title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' OR excerpt LIKE '%' || :q || '%') ORDER BY created_at DESC LIMIT 20")
    suspend fun search(q: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?
}

@Dao
interface HighlightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(highlight: HighlightEntity)

    @Query("SELECT * FROM highlights WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByBook(bookId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAllActive(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun getById(id: String): HighlightEntity?

    /** 全局搜索：按高亮正文 text 与备注 note 模糊检索（对齐网页 highlightHaystacks）。 */
    @Query("SELECT * FROM highlights WHERE deleted_at IS NULL AND (text LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%') ORDER BY created_at DESC LIMIT 20")
    suspend fun search(q: String): List<HighlightEntity>
}
