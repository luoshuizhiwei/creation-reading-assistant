package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterReadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChapterReadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<ChapterReadEntity>)

    @Query("SELECT chapter_index FROM chapter_reads WHERE book_id = :bookId ORDER BY chapter_index ASC")
    fun observeReadChapters(bookId: String): Flow<List<Int>>

    @Query("SELECT COUNT(1) FROM chapter_reads WHERE book_id = :bookId")
    fun observeReadCount(bookId: String): Flow<Int>

    @Query("SELECT COUNT(1) FROM chapter_reads WHERE book_id = :bookId")
    suspend fun readCount(bookId: String): Int

    /**
     * 取单章最近一次阅读时间（用于 TOC 渲染「最近读过」和验证冲突 REPLACE 确实更新了时间戳）。
     * 未读过时返回 null。
     */
    @Query("SELECT read_at FROM chapter_reads WHERE book_id = :bookId AND chapter_index = :chapterIndex LIMIT 1")
    suspend fun getReadAt(bookId: String, chapterIndex: Int): String?

    @Query("DELETE FROM chapter_reads WHERE book_id = :bookId")
    suspend fun clearForBook(bookId: String)

    /** 备份导出：全量已读标记。 */
    @Query("SELECT * FROM chapter_reads")
    suspend fun getAll(): List<ChapterReadEntity>

    /** 备份恢复：清空后重建。 */
    @Query("DELETE FROM chapter_reads")
    suspend fun clearAll()
}
