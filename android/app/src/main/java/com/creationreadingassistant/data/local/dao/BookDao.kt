package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    /**
     * 更新已存在的书籍记录。
     *
     * 与 SQLite 的 REPLACE 不同，UPDATE 不会先删除旧行，因此不会触发 books 外键的级联删除。
     */
    @Update
    suspend fun update(book: BookEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(books: List<BookEntity>)

    @Query("SELECT * FROM books WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAllActive(): Flow<List<BookEntity>>

    @Query("SELECT id, size, content_status FROM books WHERE deleted_at IS NULL")
    fun observeStatsRows(): Flow<List<StatsBookRow>>

    @Query("SELECT * FROM books WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: String): BookEntity?

    @Query(
        "SELECT * FROM books " +
            "WHERE deleted_at IS NULL AND LOWER(format) = 'epub' AND size <= 0 " +
            "AND ((local_uri IS NOT NULL AND local_uri != '') " +
            "OR (local_content_path IS NOT NULL AND local_content_path != ''))"
    )
    suspend fun getEpubBooksNeedingSizeRepair(): List<BookEntity>

    @Query("UPDATE books SET size = :size WHERE id = :id AND size <= 0")
    suspend fun updateSizeIfMissing(id: String, size: Int): Int

    @Query("UPDATE books SET deleted_at = :ts, updated_at = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: String)

    @Query("SELECT COUNT(*) FROM books WHERE deleted_at IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT * FROM books WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<BookEntity>

    /** 在事务中执行任意挂起代码块（用于 Repository 层的事务化操作）。 */
    @Transaction
    suspend fun runInTransaction(block: suspend () -> Unit) {
        block()
    }

    @Query(
        "SELECT DISTINCT b.* FROM books b " +
            "LEFT JOIN book_tag bt ON b.id = bt.book_id " +
            "LEFT JOIN tags t ON bt.tag_id = t.id " +
            "WHERE b.deleted_at IS NULL AND (" +
            "b.title LIKE '%' || :q || '%' OR " +
            "b.author LIKE '%' || :q || '%' OR " +
            "b.original_file_name LIKE '%' || :q || '%' OR " +
            "t.name LIKE '%' || :q || '%'" +
            ") ORDER BY b.updated_at DESC LIMIT 20"
    )
    suspend fun search(q: String): List<BookEntity>
}

@Dao
interface BookContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(content: BookContentEntity)

    @Query("SELECT * FROM book_content WHERE book_id = :bookId")
    suspend fun getByBook(bookId: String): BookContentEntity?

    @Query("SELECT COUNT(*) FROM book_content WHERE reader_preview IS NOT NULL OR epub_json IS NOT NULL")
    fun observeCachedCount(): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(LENGTH(CAST(reader_preview AS BLOB)) + LENGTH(CAST(epub_json AS BLOB))), 0) " +
            "FROM book_content WHERE reader_preview IS NOT NULL OR epub_json IS NOT NULL"
    )
    fun observeCachedBytes(): Flow<Long>
}

@Dao
interface BookFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: BookFileEntity)

    @Query("SELECT * FROM book_files WHERE book_id = :bookId AND deleted_at IS NULL")
    suspend fun getByBook(bookId: String): BookFileEntity?

    @Query("SELECT COUNT(*) FROM book_files WHERE deleted_at IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM book_files WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<BookFileEntity>
}
