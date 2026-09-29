package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspirationDao {
    /**
     * 写入灵感记录（存在则更新）。
     *
     * ⚠️ 必须是 `@Upsert`，不能用 `@Insert(onConflict = REPLACE)`：`inspirations` 是
     * `inspiration_variants`（CASCADE）与 `notes`（SET NULL）的外键父表，而 Room 会
     * 打开 `PRAGMA foreign_keys = ON`。REPLACE 的「先删后插」会把这条灵感的全部
     * 变体正文物理删除、并把引用它的笔记来源置空。详见 [BookDao.upsert]。
     */
    @Upsert
    suspend fun upsert(inspiration: InspirationEntity)

    @Query("SELECT * FROM inspirations WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAllActive(): Flow<List<InspirationEntity>>

    @Query("SELECT created_at FROM inspirations WHERE deleted_at IS NULL")
    fun observeStatsCreatedRows(): Flow<List<StatsCreatedRow>>

    @Query("SELECT * FROM inspirations WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: String): InspirationEntity?

    /** 批量按 id 查询（仅活跃记录），供导入合并建 Map 消除 N+1。 */
    @Query("SELECT * FROM inspirations WHERE id IN (:ids) AND deleted_at IS NULL")
    suspend fun getByIds(ids: Collection<String>): List<InspirationEntity>

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

    /** 批量写入，供软删除/恢复等事务路径替代逐行 upsert。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAllActive(): Flow<List<NoteEntity>>

    @Query("SELECT created_at FROM notes WHERE deleted_at IS NULL")
    fun observeStatsCreatedRows(): Flow<List<StatsCreatedRow>>

    @Query("SELECT * FROM notes WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByBook(bookId: String): Flow<List<NoteEntity>>

    /** 删除快照：只读取目标书籍仍活跃的笔记。 */
    @Query("SELECT * FROM notes WHERE book_id IN (:bookIds) AND deleted_at IS NULL ORDER BY created_at DESC")
    suspend fun getActiveByBookIds(bookIds: Collection<String>): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND (title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' OR excerpt LIKE '%' || :q || '%') ORDER BY created_at DESC LIMIT 20")
    suspend fun search(q: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    /** 撤销冲突判断：按快照中的笔记 id 查询，保留软删除状态供调用者裁决。 */
    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<String>): List<NoteEntity>
}

@Dao
interface HighlightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(highlight: HighlightEntity)

    /** 批量写入，供软删除/恢复等事务路径替代逐行 upsert。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(highlights: List<HighlightEntity>)

    @Query("SELECT * FROM highlights WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByBook(bookId: String): Flow<List<HighlightEntity>>

    /** 删除快照：只读取目标书籍仍活跃的高亮。 */
    @Query("SELECT * FROM highlights WHERE book_id IN (:bookIds) AND deleted_at IS NULL ORDER BY created_at DESC")
    suspend fun getActiveByBookIds(bookIds: Collection<String>): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAllActive(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun getById(id: String): HighlightEntity?

    /** 撤销冲突判断：按快照中的高亮 id 查询，保留软删除状态供调用者裁决。 */
    @Query("SELECT * FROM highlights WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<String>): List<HighlightEntity>

    /** 全局搜索：按高亮正文 text 与备注 note 模糊检索（对齐网页 highlightHaystacks）。 */
    @Query("SELECT * FROM highlights WHERE deleted_at IS NULL AND (text LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%') ORDER BY created_at DESC LIMIT 20")
    suspend fun search(q: String): List<HighlightEntity>
}
