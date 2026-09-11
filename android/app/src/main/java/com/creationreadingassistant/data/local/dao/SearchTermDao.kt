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

    /**
     * 按「书 + 文本基准」清理（§6.6.6）。
     *
     * 只删指定基准的行，另一基准原样保留 —— 显示文通道改为逐章即时投影后，若第 k 章
     * 投影抛错，需要精确回收该基准**已落盘**的行；此前只有 [deleteByBook]（全基准，
     * 会误删 original）与 [deleteByChapter]（不限基准，同样误删），无法精确清理。
     */
    @Query("DELETE FROM search_terms WHERE book_id = :bookId AND text_basis = :textBasis")
    suspend fun deleteByBookAndBasis(bookId: String, textBasis: String)

    @Query("SELECT COUNT(*) FROM search_terms")
    suspend fun countTerms(): Long

    @Query("SELECT COUNT(DISTINCT book_id) FROM search_terms")
    suspend fun countIndexedBooks(): Long

    @Query("SELECT * FROM search_terms WHERE term = :term")
    suspend fun rowsByTerm(term: String): List<SearchTermRow>

    /**
     * 按「词 + 文本基准」取命中行（R2-S1.2 起查询侧的实际入口）。
     *
     * 基准必须进 WHERE：同一个 term 在 original / display 两种文本里各占一行，
     * 混在一起会让命中坐标和覆盖率都对不上。
     */
    @Query("SELECT * FROM search_terms WHERE term = :term AND text_basis = :textBasis")
    suspend fun rowsByTermForBasis(term: String, textBasis: String): List<SearchTermRow>

    @Query("SELECT DISTINCT book_id FROM search_terms")
    suspend fun distinctBookIds(): List<String>
}

/**
 * search_terms 实体：主键 = (term, book_id, chapter_index, text_basis) 四列复合主键。
 *
 * Room 通过在 @Entity 中指定 primaryKeys 来对齐 SQLite 侧的多主键 DDL；
 * 由于 Room 必须能通过主键唯一定位实体，我们不再额外提供自增 _rowid。
 *
 * text_basis：文本基准（`original` 原文 / `display` 替换显示文，见
 * [com.creationreadingassistant.feature.search.SearchTextBasis]）。R2-S1 口径是
 * 「原文与替换显示文都搜」，因此同一个 term 在同一章会因基准不同而各占一行，
 * 必须进主键，否则两种文本的 hits 会互相覆盖。
 *
 * offsets：用逗号分隔的「起始字符偏移:长度」，例如 "12:2,45:2,100:2"。
 * 偏移是**相对该基准文本**的字符位置；超长章节按截断语义只保留前若干处，
 * 避免单个 TEXT 列过大导致 SQLite page 拆分抖动。
 */
@Entity(
    tableName = "search_terms",
    primaryKeys = ["term", "book_id", "chapter_index", "text_basis"],
    indices = [
        Index(value = ["term"], name = "index_search_terms_term"),
        Index(value = ["book_id"], name = "index_search_terms_book"),
    ],
)
data class SearchTermRow(
    val term: String,
    val book_id: String,
    val chapter_index: Int,
    /** 文本基准 wire 值，见 [com.creationreadingassistant.feature.search.SearchTextBasis]。 */
    val text_basis: String,
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

/**
 * search_index_coverage 实体：主键 = (book_id, text_basis)。
 *
 * 每本书在**每一种文本基准**上各占一行，显式记录该基准的索引完成度。
 * 这是「索引仓储不能据类名推断全覆盖」的落地：搜索 UI 要回答
 * 「这本书搜到的东西是不是全的」，只能读这张表，不能靠 format/行数猜。
 *
 * coverage / text_basis 的具体取值见
 * [com.creationreadingassistant.feature.search.SearchCoverageState] /
 * [com.creationreadingassistant.feature.search.SearchTextBasis]。
 */
@Entity(
    tableName = "search_index_coverage",
    primaryKeys = ["book_id", "text_basis"],
    indices = [
        Index(value = ["coverage"], name = "index_search_index_coverage_coverage"),
    ],
)
data class SearchIndexCoverageRow(
    val book_id: String,
    /** 文本基准 wire 值。 */
    val text_basis: String,
    /** 建索引时书籍的 format（小写），用于「TXT/EPUB/Markdown 分别记录覆盖」。 */
    val format: String,
    /** 覆盖率状态 wire 值。 */
    val coverage: String,
    /** 实际纳入索引的章节数。 */
    @ColumnInfo(defaultValue = "0") val indexed_chapters: Int = 0,
    /** 本次逐章路径认定的章节总数。 */
    @ColumnInfo(defaultValue = "0") val total_chapters: Int = 0,
    /** 建索引时的分词器版本，与 [SearchIndexStateRow.tokenizer_version] 对齐。 */
    @ColumnInfo(defaultValue = "0") val tokenizer_version: Int = 0,
    /** 本行写入时刻；不适用 / 未真正建索引（[SearchCoverageState.NOT_APPLICABLE] 或 [SearchCoverageState.FAILED]）时为 0。 */
    @ColumnInfo(defaultValue = "0") val indexed_at: Long = 0,
    /** 为什么不是全量，取值见 [com.creationreadingassistant.feature.search.SearchCoverageReason]。 */
    val reason: String? = null,
)

@Dao
interface SearchIndexCoverageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<SearchIndexCoverageRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: SearchIndexCoverageRow)

    @Query("SELECT * FROM search_index_coverage WHERE book_id = :bookId ORDER BY text_basis ASC")
    suspend fun rowsForBook(bookId: String): List<SearchIndexCoverageRow>

    @Query(
        "SELECT * FROM search_index_coverage WHERE book_id = :bookId AND text_basis = :textBasis LIMIT 1",
    )
    suspend fun row(bookId: String, textBasis: String): SearchIndexCoverageRow?

    @Query("SELECT * FROM search_index_coverage ORDER BY book_id ASC, text_basis ASC")
    suspend fun all(): List<SearchIndexCoverageRow>

    @Query("SELECT * FROM search_index_coverage WHERE coverage IN (:states)")
    suspend fun rowsByStates(states: List<String>): List<SearchIndexCoverageRow>

    @Query("SELECT COUNT(*) FROM search_index_coverage WHERE coverage IN (:states)")
    suspend fun countByStates(states: List<String>): Long

    @Query("DELETE FROM search_index_coverage WHERE book_id = :bookId")
    suspend fun deleteByBook(bookId: String)

    @Query("DELETE FROM search_index_coverage")
    suspend fun deleteAll()
}
