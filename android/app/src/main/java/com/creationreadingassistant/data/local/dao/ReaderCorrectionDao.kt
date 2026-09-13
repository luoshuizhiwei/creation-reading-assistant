package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.creationreadingassistant.data.local.entity.ReaderCorrectionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 单处纠错记录 DAO（v13）。只服务 [ReaderCorrectionEntity]；
 * 与 reader_text_rules 分表：纠错有独立的撤销语义（status 翻转），不混入规则表。
 */
@Dao
interface ReaderCorrectionDao {

    @Query("SELECT * FROM reader_text_corrections WHERE book_id = :bookId ORDER BY source_start ASC, id ASC")
    fun observeForBook(bookId: String): Flow<List<ReaderCorrectionEntity>>

    @Query("SELECT * FROM reader_text_corrections WHERE book_id = :bookId ORDER BY source_start ASC, id ASC")
    suspend fun listForBook(bookId: String): List<ReaderCorrectionEntity>

    @Query("SELECT * FROM reader_text_corrections WHERE id = :id")
    suspend fun getById(id: String): ReaderCorrectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReaderCorrectionEntity)

    @Query(
        "UPDATE reader_text_corrections SET status = :status, updated_at = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updateStatus(id: String, status: String, updatedAt: Long): Int

    @Query("DELETE FROM reader_text_corrections WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM reader_text_corrections WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: String): Int
}
