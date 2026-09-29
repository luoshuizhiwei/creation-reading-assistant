package com.creationreadingassistant.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReaderCorrectionEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 外键父表「写入」不得摧毁子表数据（M-BUG-11，2026-09-17 真机数据丢失事故）。
 *
 * 事故现象：长时间阅读后应用卡一下，退出后发现书籍、阅读记录、灵感全没了，
 * 只有导入记录还在（它在 DataStore 里，不在 Room）；重新导入一本书后数据又都回来了。
 *
 * 根因：`BookDao.upsert` 等父表写入用的是 `@Insert(onConflict = REPLACE)`。
 * SQLite 的 `INSERT OR REPLACE` 是「先 DELETE 冲突行、再 INSERT」，而 Room 开库时
 * 会执行 `PRAGMA foreign_keys = ON`（见生成的 `AppDatabase_Impl`）。`books` 是 13 张
 * 子表的外键父表（12 张 CASCADE + inspirations SET NULL），于是**对一本书做一次普通
 * 写入**就会物理删除它的全部进度、会话、笔记、高亮、正文缓存与来源引用。
 *
 * 真机实测（设备上 319MB 库的副本）：一次 `INSERT OR REPLACE INTO books` 让
 * `reading_sessions` 从 208 行掉到 5 行、`reading_progress` 4 → 3、
 * `library_source_refs` 4 → 3。这组测试把「写父表 → 子表必须存活」钉死，
 * 任何人把 `@Upsert` 改回 `@Insert(REPLACE)` 都会在这里失败。
 */
@RunWith(AndroidJUnit4::class)
class ParentUpsertCascadeSafetyTest {

    private lateinit var db: AppDatabase

    private val now = "2026-09-17T00:00:00.000Z"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun book(id: String, title: String = "测试书") = BookEntity(
        id = id,
        title = title,
        format = "txt",
        size = 1024,
        local_uri = "file:///books/$id.txt",
        local_content_path = "/data/books/$id.txt",
        content_status = "available",
        imported_at = now,
        updated_at = now,
    )

    /** 为一本书铺满全部外键子表数据。 */
    private suspend fun seedChildren(bookId: String) {
        db.bookContentDao().upsert(BookContentEntity(book_id = bookId, reader_preview = "预览"))
        db.bookFileDao().upsert(
            BookFileEntity(book_id = bookId, file_name = "a.txt", format = "txt", size = 1024, updated_at = now),
        )
        db.readingProgressDao().upsert(
            ReadingProgressEntity(
                book_id = bookId,
                progress_percent = 42f,
                last_read_at = now,
                total_reading_time_ms = 3_600_000L,
                completion_state = "reading",
                current_location_json = """{"chapter":3}""",
                updated_at = now,
            ),
        )
        db.readingSessionDao().upsert(
            ReadingSessionEntity(
                id = "sess-1",
                book_id = bookId,
                duration_ms = 600_000L,
                progress_percent = 42f,
                created_at = now,
                updated_at = now,
            ),
        )
        db.noteDao().upsert(
            NoteEntity(id = "note-1", book_id = bookId, title = "书签", body = "内容", created_at = now, updated_at = now),
        )
        db.highlightDao().upsert(
            HighlightEntity(id = "hl-1", book_id = bookId, text = "高亮文本", created_at = now, updated_at = now),
        )
        db.tagDao().upsert(TagEntity(id = "tag-1", name = "标签", created_at = now, updated_at = now))
        db.bookTagDao().upsert(BookTagEntity(book_id = bookId, tag_id = "tag-1"))
        db.categoryDao().upsert(CategoryEntity(id = "cat-1", name = "分类", created_at = now, updated_at = now))
        db.bookCategoryDao().upsert(BookCategoryEntity(book_id = bookId, category_id = "cat-1"))
        db.shelfDao().upsert(ShelfEntity(id = "shelf-1", name = "书单", created_at = now, updated_at = now))
        db.shelfBookDao().upsert(ShelfBookEntity(shelf_id = "shelf-1", book_id = bookId))
        db.chapterReadDao().upsert(ChapterReadEntity(book_id = bookId, chapter_index = 1, read_at = now))
        db.readerTextRuleDao().upsert(
            ReaderTextRuleEntity(
                id = "rule-book", kind = "REPLACE", name = "按书替换", pattern = "旧词",
                replacement = "新词", builtin = false, enabled = true, scope = "PER_BOOK",
                book_id = bookId, position = 1, created_at = 1L, updated_at = 1L,
            ),
        )
        // 全局规则 book_id 为空、不受 books 级联影响，作为「整表未被清空」的对照。
        db.readerTextRuleDao().upsert(
            ReaderTextRuleEntity(
                id = "rule-global", kind = "TOC", name = "全局目录规则", pattern = null,
                replacement = "", builtin = false, enabled = true, scope = "GLOBAL",
                book_id = null, position = 2, created_at = 1L, updated_at = 1L,
            ),
        )
        db.readerCorrectionDao().upsert(
            ReaderCorrectionEntity(
                id = "corr-1", book_id = bookId, source_start = 100, source_end = 104,
                find_text = "错字", replace_text = "正字",
                status = ReaderCorrectionEntity.STATUS_ACTIVE, created_at = 1L, updated_at = 1L,
            ),
        )
        db.librarySourceRefDao().upsert(
            LibrarySourceRefEntity(
                book_id = bookId,
                root_id = null,
                provider_authority = "com.android.providers.downloads.documents",
                document_id = "doc-1",
                display_name = "a.txt",
                format = "txt",
                size = 1024L,
                last_modified = null,
                content_hash = null,
                candidate_fingerprint = null,
                last_seen_at = 1L,
                availability = "available",
            ),
        )
        // 灵感：source_book_id 指向本书（SET NULL），另有一条变体验证 inspirations 的级联。
        db.inspirationDao().upsert(
            InspirationEntity(id = "insp-1", title = "灵感", body = "正文", source_book_id = bookId, created_at = now, updated_at = now),
        )
        db.inspirationVariantDao().upsert(
            InspirationVariantEntity(id = "var-1", inspiration_id = "insp-1", content = "变体", created_at = now),
        )
    }

    /**
     * 返回 (按书规则数, 规则总数)。
     *
     * `reader_text_rules` 的 DAO 只暴露 `observeAll()` 一个读取入口，没有「按 book_id 查询」
     * 的方法；本轮不为此给生产 DAO 加接口（产品代码不需要这条路径），故在测试内按 book_id 分流。
     */
    private suspend fun ruleCounts(db: AppDatabase, bookId: String): Pair<Int, Int> {
        val all = db.readerTextRuleDao().observeAll().first()
        return all.count { it.book_id == bookId } to all.size
    }

    /**
     * 核心回归：重写书籍行（`bookDao.upsert`）后，全部子表数据必须原样存活。
     *
     * 这就是事故里那条路径：阅读器/导入/同步任意一次「写书籍记录」，在 REPLACE 语义下
     * 都会把这本书的进度、会话、笔记、高亮、正文与来源引用一起抹掉。
     */
    @Test
    fun upsertingBookPreservesAllChildRows() = runTest {
        val id = "book-1"
        db.bookDao().upsert(book(id))
        seedChildren(id)

        // 铺数据本身要成立（否则下面的断言会因前置条件不满足而失去意义）。
        assertEquals(1, db.readingSessionDao().observeByBook(id).first().size)
        assertEquals(1, db.noteDao().observeByBook(id).first().size)
        assertEquals(1, db.highlightDao().observeByBook(id).first().size)
        assertEquals(1, db.inspirationVariantDao().observeByInspiration("insp-1").first().size)
        assertEquals(1, db.librarySourceRefDao().getAll().size)
        assertEquals(1, db.readerCorrectionDao().listForBook(id).size)
        assertEquals(Pair(1, 2), ruleCounts(db, id))

        // ← 事故触发点：一次普通的书籍写入。
        db.bookDao().upsert(book(id, title = "改过的书名"))

        assertNotNull("阅读进度被级联删除了", db.readingProgressDao().getByBook(id))
        assertEquals(42f, db.readingProgressDao().getByBook(id)!!.progress_percent, 0.001f)
        assertEquals(3_600_000L, db.readingProgressDao().getByBook(id)!!.total_reading_time_ms)
        assertNotNull("正文缓存被级联删除了", db.bookContentDao().getByBook(id))
        assertNotNull("文件记录被级联删除了", db.bookFileDao().getByBook(id))
        assertEquals("会话被级联删除了", 1, db.readingSessionDao().observeByBook(id).first().size)
        assertEquals("笔记被级联删除了", 1, db.noteDao().observeByBook(id).first().size)
        assertEquals("高亮被级联删除了", 1, db.highlightDao().observeByBook(id).first().size)
        assertEquals("标签关联被级联删除了", 1, db.bookTagDao().getByBookIds(listOf(id)).size)
        assertEquals("分类关联被级联删除了", 1, db.bookCategoryDao().getByBookIds(listOf(id)).size)
        assertEquals("书单关联被级联删除了", 1, db.shelfBookDao().getByBookIds(listOf(id)).size)
        assertEquals("已读章节被级联删除了", 1, db.chapterReadDao().getByBookIds(listOf(id)).size)
        assertEquals("来源引用被级联删除了", 1, db.librarySourceRefDao().getAll().size)

        // 纠错记录（v13 表）与按书文本规则（v9 表）同样是 books 的 CASCADE 子表，
        // 之前从未进入种子数据 —— 只有它们被写坏时，现有断言不会报警。
        val corrections = db.readerCorrectionDao().listForBook(id)
        assertEquals("纠错记录被级联删除了", 1, corrections.size)
        assertEquals("纠错内容被改写", "错字", corrections.first().find_text)
        val (perBookRules, totalRules) = ruleCounts(db, id)
        assertEquals("按书文本规则被级联删除了", 1, perBookRules)
        assertEquals("全局规则不应受书籍写入影响", 2, totalRules)

        // 灵感的 source_book_id 必须仍然指向这本书（SET NULL 会把它置空）。
        val inspiration = db.inspirationDao().getById("insp-1")
        assertNotNull(inspiration)
        assertEquals("灵感的来源书籍被置空了", id, inspiration!!.source_book_id)
        assertEquals(
            "灵感变体被级联删除了",
            1,
            db.inspirationVariantDao().observeByInspiration("insp-1").first().size,
        )

        // 写入本身必须生效。
        assertEquals("改过的书名", db.bookDao().getById(id)!!.title)
    }

    /** 批量写入同样不得级联删除子表。 */
    @Test
    fun upsertingBooksInBatchPreservesChildRows() = runTest {
        val id = "book-2"
        db.bookDao().upsert(book(id))
        seedChildren(id)

        db.bookDao().upsertAll(listOf(book(id, title = "批量改过")))

        assertNotNull("批量写入后阅读进度丢失", db.readingProgressDao().getByBook(id))
        assertEquals(1, db.readingSessionDao().observeByBook(id).first().size)
        assertEquals(1, db.bookTagDao().getByBookIds(listOf(id)).size)
        assertEquals("批量改过", db.bookDao().getById(id)!!.title)
    }

    /** 灵感父表写入不得删掉它的变体、也不得把引用它的笔记来源置空。 */
    @Test
    fun upsertingInspirationPreservesVariantsAndNoteLink() = runTest {
        db.bookDao().upsert(book("book-3"))
        db.inspirationDao().upsert(
            InspirationEntity(id = "insp-3", title = "灵感", body = "正文", source_book_id = "book-3", created_at = now, updated_at = now),
        )
        db.inspirationVariantDao().upsert(
            InspirationVariantEntity(id = "var-3", inspiration_id = "insp-3", content = "变体", created_at = now),
        )
        db.noteDao().upsert(
            NoteEntity(id = "note-3", book_id = "book-3", inspiration_id = "insp-3", title = "笔记", created_at = now, updated_at = now),
        )

        db.inspirationDao().upsert(
            InspirationEntity(id = "insp-3", title = "改过的灵感", body = "正文", source_book_id = "book-3", created_at = now, updated_at = now),
        )

        assertEquals("改过的灵感", db.inspirationDao().getById("insp-3")!!.title)
        assertEquals(
            "灵感变体被级联删除了",
            1,
            db.inspirationVariantDao().observeByInspiration("insp-3").first().size,
        )
        assertEquals(
            "笔记的灵感来源被置空了",
            "insp-3",
            db.noteDao().getById("note-3")!!.inspiration_id,
        )
    }

    /** 标签/分类/书单的父表写入不得清空它们与书籍的关联。 */
    @Test
    fun upsertingTaxonomyParentsPreservesJoinRows() = runTest {
        db.bookDao().upsert(book("book-4"))
        db.tagDao().upsert(TagEntity(id = "tag-4", name = "标签", created_at = now, updated_at = now))
        db.bookTagDao().upsert(BookTagEntity(book_id = "book-4", tag_id = "tag-4"))
        db.categoryDao().upsert(CategoryEntity(id = "cat-4", name = "分类", created_at = now, updated_at = now))
        db.bookCategoryDao().upsert(BookCategoryEntity(book_id = "book-4", category_id = "cat-4"))
        db.shelfDao().upsert(ShelfEntity(id = "shelf-4", name = "书单", created_at = now, updated_at = now))
        db.shelfBookDao().upsert(ShelfBookEntity(shelf_id = "shelf-4", book_id = "book-4"))

        db.tagDao().upsert(TagEntity(id = "tag-4", name = "改名标签", created_at = now, updated_at = now))
        db.categoryDao().upsert(CategoryEntity(id = "cat-4", name = "改名分类", created_at = now, updated_at = now))
        db.shelfDao().upsert(ShelfEntity(id = "shelf-4", name = "改名书单", created_at = now, updated_at = now))

        assertEquals("改名标签", db.tagDao().getById("tag-4")!!.name)
        assertEquals(1, db.bookTagDao().getByBookIds(listOf("book-4")).size)
        assertEquals(1, db.bookCategoryDao().getByBookIds(listOf("book-4")).size)
        assertEquals(1, db.shelfBookDao().getByBookIds(listOf("book-4")).size)
    }

    /** 软删除（UPDATE deleted_at）走的是 UPDATE，不应触发任何级联 —— 防回归。 */
    @Test
    fun softDeletePreservesChildRows() = runTest {
        val id = "book-5"
        db.bookDao().upsert(book(id))
        seedChildren(id)

        db.bookDao().softDelete(id, now)

        assertNotNull("软删除不应物理删除子行", db.readingProgressDao().getByBook(id))
        assertEquals(1, db.readingSessionDao().observeByBook(id).first().size)
        assertEquals(1, db.bookTagDao().getByBookIds(listOf(id)).size)
        assertEquals(1, db.librarySourceRefDao().getAll().size)
        assertNull("活跃查询应看不到软删除的书", db.bookDao().getById(id))
    }
}
