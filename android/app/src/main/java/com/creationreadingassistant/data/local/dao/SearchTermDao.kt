package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.ColumnInfo

/**
 * P2-FTS5 MVP：search_terms 表的写入 DAO。
 *
 * 此版本仅提供增量 upsert + 单章清理 + 整表查询计数接口；真正的
 * 跨书 MATCH 查询下一批接入 SearchIndexRepository（查询侧会走临时
 * SQLiteStatement 按 Bigram/Unigram hit 权重排序，或等 FTS5 虚拟表落地后切。）
 */
@Dao
interface SearchTermDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<SearchTermRow>)

    @Query("DELETE FROM search_terms WHERE book_id = :bookId")
    suspend fun deleteByBook(bookId: String)

    @Query("DELETE FROM search_terms WHERE book_id = :bookId AND chapter_index = :chapterIndex")
    suspend fun deleteByChapter(bookId: String, chapterIndex: Int)

    @Query("SELECT COUNT(*) FROM search_terms")
    suspend fun countTerms(): Long

    @Query("SELECT COUNT(DISTINCT book_id) FROM search_terms")
    suspend fun countIndexedBooks(): Long

    @Query("SELECT * FROM search_terms WHERE term = :term")
    suspend fun rowsByTerm(term: String): List<SearchTermRow>

    @Query("SELECT DISTINCT book_id FROM search_terms")
    suspend fun distinctBookIds(): List<String>
}

/**
 * search_terms 实体：主键 = (term, book_id, chapter_index) 三列复合主键。
 *
 * Room 通过在 @Entity 中指定 primaryKeys 来对齐 SQLite 侧的多主键 DDL；
 * 由于 Room 要求实体必须可通过主键唯一定位，我们不再额外提供自增 _rowid。
 *
 * offsets：用逗号分隔的「起始字符偏移:长度」，例如 "12:2,45:2,100:2"。
 * MVP 阶段只写 hits 计数即可（搜索排序够用），offsets 可留作后续预览高亮；
 * 超 256K 字符的长章同净化规则原则——offsets 存空串、仅保留 hits，避免
 * 单个 BLOB/TEXT 过大导致 SQLite page 拆分抖动。
 */
@Entity(
    tableName = "search_terms",
    primaryKeys = ["term", "book_id", "chapter_index"],
    indices = [
        Index(value = ["term"], name = "index_search_terms_term"),
        Index(value = ["book_id"], name = "index_search_terms_book"),
    ],
)
data class SearchTermRow(
    val term: String,
    val book_id: String,
    val chapter_index: Int,
    @ColumnInfo(defaultValue = "0") val hits: Int,
    val offsets: String? = null,
)

/** search_index_state 实体：单行状态表，固定 id=1。 */
@Entity(tableName = "search_index_state")
data class SearchIndexStateRow(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "0") val tokenizer_version: Int,
    val last_scanned_book_id: String?,
    @ColumnInfo(defaultValue = "0") val last_scanned_chapter_index: Int,
    @ColumnInfo(defaultValue = "0") val built_at: Long,
)

@Dao
interface SearchIndexStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(row: SearchIndexStateRow)

    @Query("SELECT * FROM search_index_state WHERE id = 1 LIMIT 1")
    suspend fun get(): SearchIndexStateRow?
}
