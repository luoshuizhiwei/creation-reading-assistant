package com.creationreadingassistant.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.feature.library.deletion.LiveRelationTargets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Qoder 的删除/撤销 JVM 测试以替身仓储模拟 Room 查询语义。
 * 这里以真实 SQLite/Room 验证快照路径的外键、软删除和连接表恢复契约；不依赖设备中的用户数据。
 */
@RunWith(AndroidJUnit4::class)
class BookDeletionPersistenceTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BookRepository(
            bookDao = database.bookDao(),
            bookContentDao = database.bookContentDao(),
            bookFileDao = database.bookFileDao(),
            progressDao = database.readingProgressDao(),
            sessionDao = database.readingSessionDao(),
            noteDao = database.noteDao(),
            highlightDao = database.highlightDao(),
            inspirationDao = database.inspirationDao(),
            bookTagDao = database.bookTagDao(),
            bookCategoryDao = database.bookCategoryDao(),
            shelfBookDao = database.shelfBookDao(),
            chapterReadDao = database.chapterReadDao(),
            // R2 checkpoint 给 BookRepository 增加了 @ApplicationContext context（必填），
            // 两个 androidTest 调用点同步补齐（workbuddy-r3.md §5.2 门禁阻塞项）。
            context = ApplicationProvider.getApplicationContext(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun scopedDeleteAndRestorePreserveOnlyThisOperationInRealRoom() = runTest {
        seedTargetBookAndRelations()
        seedUnrelatedBook()

        val snapshot = repository.deleteBookScoped(TARGET_BOOK, DELETION_STAMP)

        assertNull(database.bookDao().getById(TARGET_BOOK))
        assertEquals(DELETION_STAMP, database.bookDao().getDeleted().single { it.id == TARGET_BOOK }.deleted_at)
        assertNull(database.readingProgressDao().getByBook(TARGET_BOOK))
        assertNull(database.bookFileDao().getByBook(TARGET_BOOK))
        assertTrue(database.noteDao().observeByBook(TARGET_BOOK).first().isEmpty())
        assertTrue(database.highlightDao().observeByBook(TARGET_BOOK).first().isEmpty())
        assertTrue(database.bookTagDao().getByBookIds(listOf(TARGET_BOOK)).isEmpty())
        assertTrue(database.bookCategoryDao().getByBookIds(listOf(TARGET_BOOK)).isEmpty())
        assertTrue(database.shelfBookDao().getByBookIds(listOf(TARGET_BOOK)).isEmpty())
        assertTrue(database.chapterReadDao().getByBookIds(listOf(TARGET_BOOK)).isEmpty())
        assertEquals(null, database.bookContentDao().getByBook(TARGET_BOOK)?.reader_preview)

        // 同一事务只作用于目标书籍，不能碰到同样有关系的另一册书。
        assertNotNull(database.bookDao().getById(OTHER_BOOK))
        assertEquals(listOf(OTHER_TAG), database.bookTagDao().getByBookIds(listOf(OTHER_BOOK)).map { it.tag_id })

        val report = repository.restoreDeletion(
            snapshot = snapshot,
            liveTargets = LiveRelationTargets(
                tagIds = setOf(TARGET_TAG),
                categoryIds = setOf(TARGET_CATEGORY),
                shelfIds = setOf(TARGET_SHELF),
            ),
        )

        assertTrue(report.restoredBook)
        assertTrue(report.restoredProgress)
        assertTrue(report.restoredFile)
        assertTrue(report.restoredContent)
        assertEquals(1, report.restoredSessionCount)
        assertEquals(1, report.restoredNoteCount)
        assertEquals(1, report.restoredHighlightCount)
        assertEquals(1, report.restoredTagCount)
        assertEquals(1, report.restoredCategoryCount)
        assertEquals(1, report.restoredShelfLinkCount)
        assertEquals(1, report.restoredChapterReadCount)

        assertNotNull(database.bookDao().getById(TARGET_BOOK))
        assertNotNull(database.readingProgressDao().getByBook(TARGET_BOOK))
        assertNotNull(database.bookFileDao().getByBook(TARGET_BOOK))
        assertEquals("正文预览", database.bookContentDao().getByBook(TARGET_BOOK)?.reader_preview)
        assertEquals(listOf(ACTIVE_NOTE), database.noteDao().observeByBook(TARGET_BOOK).first().map { it.id })
        assertEquals(listOf(ACTIVE_HIGHLIGHT), database.highlightDao().observeByBook(TARGET_BOOK).first().map { it.id })
        assertEquals(listOf(TARGET_TAG), database.bookTagDao().getByBookIds(listOf(TARGET_BOOK)).map { it.tag_id })
        assertEquals(listOf(TARGET_CATEGORY), database.bookCategoryDao().getByBookIds(listOf(TARGET_BOOK)).map { it.category_id })
        assertEquals(listOf(TARGET_SHELF), database.shelfBookDao().getByBookIds(listOf(TARGET_BOOK)).map { it.shelf_id })
        assertEquals(listOf(2), database.chapterReadDao().getByBookIds(listOf(TARGET_BOOK)).map { it.chapter_index })

        // 本次操作开始前已经删除的标注不属于 snapshot，真实 Room 恢复后仍须保持已删除。
        assertFalse(database.noteDao().observeByBook(TARGET_BOOK).first().any { it.id == HISTORICAL_NOTE })
        assertFalse(database.highlightDao().observeByBook(TARGET_BOOK).first().any { it.id == HISTORICAL_HIGHLIGHT })
        assertEquals(HISTORICAL_DELETION_STAMP, database.noteDao().getDeleted().single { it.id == HISTORICAL_NOTE }.deleted_at)
        assertEquals(HISTORICAL_DELETION_STAMP, database.highlightDao().getDeleted().single { it.id == HISTORICAL_HIGHLIGHT }.deleted_at)
    }

    private suspend fun seedTargetBookAndRelations() {
        database.bookDao().upsert(book(TARGET_BOOK))
        database.bookContentDao().upsert(BookContentEntity(book_id = TARGET_BOOK, reader_preview = "正文预览"))
        database.bookFileDao().upsert(
            BookFileEntity(
                book_id = TARGET_BOOK,
                file_name = "content.txt",
                format = "txt",
                updated_at = INITIAL_STAMP,
            ),
        )
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                book_id = TARGET_BOOK,
                progress_percent = 25f,
                updated_at = INITIAL_STAMP,
            ),
        )
        database.readingSessionDao().upsert(
            ReadingSessionEntity(
                id = TARGET_SESSION,
                book_id = TARGET_BOOK,
                created_at = INITIAL_STAMP,
                updated_at = INITIAL_STAMP,
            ),
        )
        database.noteDao().upsert(
            NoteEntity(
                id = ACTIVE_NOTE,
                book_id = TARGET_BOOK,
                title = "测试笔记",
                created_at = INITIAL_STAMP,
                updated_at = INITIAL_STAMP,
            ),
        )
        database.noteDao().upsert(
            NoteEntity(
                id = HISTORICAL_NOTE,
                book_id = TARGET_BOOK,
                title = "历史已删笔记",
                created_at = INITIAL_STAMP,
                updated_at = HISTORICAL_DELETION_STAMP,
                deleted_at = HISTORICAL_DELETION_STAMP,
            ),
        )
        database.highlightDao().upsert(
            HighlightEntity(
                id = ACTIVE_HIGHLIGHT,
                book_id = TARGET_BOOK,
                text = "测试高亮",
                created_at = INITIAL_STAMP,
                updated_at = INITIAL_STAMP,
            ),
        )
        database.highlightDao().upsert(
            HighlightEntity(
                id = HISTORICAL_HIGHLIGHT,
                book_id = TARGET_BOOK,
                text = "历史已删高亮",
                created_at = INITIAL_STAMP,
                updated_at = HISTORICAL_DELETION_STAMP,
                deleted_at = HISTORICAL_DELETION_STAMP,
            ),
        )
        database.tagDao().upsert(TagEntity(id = TARGET_TAG, name = "测试标签", updated_at = INITIAL_STAMP))
        database.categoryDao().upsert(CategoryEntity(id = TARGET_CATEGORY, name = "测试分类", updated_at = INITIAL_STAMP))
        database.shelfDao().upsert(ShelfEntity(id = TARGET_SHELF, name = "测试书架", updated_at = INITIAL_STAMP))
        database.bookTagDao().upsert(BookTagEntity(book_id = TARGET_BOOK, tag_id = TARGET_TAG))
        database.bookCategoryDao().upsert(BookCategoryEntity(book_id = TARGET_BOOK, category_id = TARGET_CATEGORY))
        database.shelfBookDao().upsert(ShelfBookEntity(shelf_id = TARGET_SHELF, book_id = TARGET_BOOK))
        database.chapterReadDao().upsert(ChapterReadEntity(book_id = TARGET_BOOK, chapter_index = 2, read_at = INITIAL_STAMP))
    }

    private suspend fun seedUnrelatedBook() {
        database.bookDao().upsert(book(OTHER_BOOK))
        database.tagDao().upsert(TagEntity(id = OTHER_TAG, name = "另一标签", updated_at = INITIAL_STAMP))
        database.bookTagDao().upsert(BookTagEntity(book_id = OTHER_BOOK, tag_id = OTHER_TAG))
    }

    private fun book(id: String) = BookEntity(
        id = id,
        title = "测试 TXT",
        format = "txt",
        updated_at = INITIAL_STAMP,
    )

    private companion object {
        const val INITIAL_STAMP = "2026-09-10T00:00:00Z"
        const val DELETION_STAMP = "2026-09-10T01:00:00Z"
        const val HISTORICAL_DELETION_STAMP = "2026-09-09T01:00:00Z"
        const val TARGET_BOOK = "book-target"
        const val OTHER_BOOK = "book-other"
        const val TARGET_SESSION = "session-target"
        const val ACTIVE_NOTE = "note-active"
        const val HISTORICAL_NOTE = "note-historical"
        const val ACTIVE_HIGHLIGHT = "highlight-active"
        const val HISTORICAL_HIGHLIGHT = "highlight-historical"
        const val TARGET_TAG = "tag-target"
        const val OTHER_TAG = "tag-other"
        const val TARGET_CATEGORY = "category-target"
        const val TARGET_SHELF = "shelf-target"
    }
}
