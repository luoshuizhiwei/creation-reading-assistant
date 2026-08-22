package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * BookRepository 单测：
 * - 软删除/恢复批量路径（会话/笔记/高亮走 upsertAll，时间戳一致）；
 * - 阅读进度与完成状态读写（完成态强转、clamp、completed_at 语义）；
 * - 状态变更 / 清缓存 / 基础信息更新等核心读写路径。
 */
class BookRepositoryTest {

    private lateinit var bookDao: BookDao
    private lateinit var bookContentDao: BookContentDao
    private lateinit var bookFileDao: BookFileDao
    private lateinit var progressDao: ReadingProgressDao
    private lateinit var sessionDao: ReadingSessionDao
    private lateinit var noteDao: NoteDao
    private lateinit var highlightDao: HighlightDao
    private lateinit var inspirationDao: InspirationDao
    private lateinit var bookTagDao: BookTagDao
    private lateinit var bookCategoryDao: BookCategoryDao
    private lateinit var shelfBookDao: ShelfBookDao
    private lateinit var chapterReadDao: ChapterReadDao

    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        bookDao = mockk(relaxed = true) {
            // 让事务包裹的代码块直接同步执行
            coEvery { runInTransaction(any()) } coAnswers {
                firstArg<suspend () -> Unit>().invoke()
            }
        }
        bookContentDao = mockk(relaxed = true)
        bookFileDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        sessionDao = mockk(relaxed = true)
        noteDao = mockk(relaxed = true)
        highlightDao = mockk(relaxed = true)
        inspirationDao = mockk(relaxed = true)
        bookTagDao = mockk(relaxed = true)
        bookCategoryDao = mockk(relaxed = true)
        shelfBookDao = mockk(relaxed = true)
        chapterReadDao = mockk(relaxed = true)
        repository = BookRepository(
            bookDao = bookDao,
            bookContentDao = bookContentDao,
            bookFileDao = bookFileDao,
            progressDao = progressDao,
            sessionDao = sessionDao,
            noteDao = noteDao,
            highlightDao = highlightDao,
            inspirationDao = inspirationDao,
            bookTagDao = bookTagDao,
            bookCategoryDao = bookCategoryDao,
            shelfBookDao = shelfBookDao,
            chapterReadDao = chapterReadDao,
        )
    }

    private fun session(id: String, bookId: String) = ReadingSessionEntity(
        id = id,
        book_id = bookId,
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun note(id: String, bookId: String?) = NoteEntity(
        id = id,
        book_id = bookId,
        title = "笔记$id",
        created_at = "2026-01-01T00:00:00Z",
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun highlight(id: String, bookId: String) = HighlightEntity(
        id = id,
        book_id = bookId,
        created_at = "2026-01-01T00:00:00Z",
        updated_at = "2026-01-01T00:00:00Z",
    )

    @Test
    fun `deleteBook batch soft deletes sessions notes and highlights`() = runTest {
        coEvery { progressDao.getByBook("b1") } returns
            ReadingProgressEntity(book_id = "b1", updated_at = "2026-01-01T00:00:00Z")
        every { sessionDao.observeByBook("b1") } returns
            flowOf(listOf(session("s1", "b1"), session("s2", "b1")))
        every { noteDao.observeAllActive() } returns
            flowOf(listOf(note("n1", "b1"), note("n2", "other")))
        every { highlightDao.observeByBook("b1") } returns
            flowOf(listOf(highlight("h1", "b1")))
        coEvery { bookContentDao.getByBook("b1") } returns null
        coEvery { bookFileDao.getByBook("b1") } returns null

        repository.deleteBook("b1")

        coVerify(exactly = 1) { bookDao.softDelete("b1", any()) }

        val sessionsSlot = slot<List<ReadingSessionEntity>>()
        coVerify(exactly = 1) { sessionDao.upsertAll(capture(sessionsSlot)) }
        val deletedSessions = sessionsSlot.captured
        assertEquals(setOf("s1", "s2"), deletedSessions.map { it.id }.toSet())
        assertTrue(deletedSessions.all { it.deleted_at != null })
        assertEquals(1, deletedSessions.map { it.deleted_at }.distinct().size)

        val notesSlot = slot<List<NoteEntity>>()
        coVerify(exactly = 1) { noteDao.upsertAll(capture(notesSlot)) }
        // 只软删除属于 b1 的笔记，其它书的笔记不受影响
        assertEquals(listOf("n1"), notesSlot.captured.map { it.id })
        assertTrue(notesSlot.captured.all { it.deleted_at != null })

        val highlightsSlot = slot<List<HighlightEntity>>()
        coVerify(exactly = 1) { highlightDao.upsertAll(capture(highlightsSlot)) }
        assertEquals(listOf("h1"), highlightsSlot.captured.map { it.id })
        assertTrue(highlightsSlot.captured.all { it.deleted_at != null })

        // 单条记录仍走原单条 upsert 路径
        coVerify(exactly = 1) { progressDao.upsert(withArg { assertEquals("b1", it.book_id); assertTrue(it.deleted_at != null) }) }
        coVerify(exactly = 1) { bookTagDao.clearByBook("b1") }
        coVerify(exactly = 1) { bookCategoryDao.clearByBook("b1") }
        coVerify(exactly = 1) { chapterReadDao.clearForBook("b1") }
        coVerify(exactly = 1) { shelfBookDao.clearByBook("b1") }
        // 批量路径不应再逐行调用单条 upsert
        coVerify(exactly = 0) { sessionDao.upsert(any()) }
        coVerify(exactly = 0) { noteDao.upsert(any()) }
        coVerify(exactly = 0) { highlightDao.upsert(any()) }
    }

    @Test
    fun `restoreBook batch restores sessions notes and highlights`() = runTest {
        val deletedBook = BookEntity(
            id = "b1",
            title = "甲书",
            format = "txt",
            updated_at = "2026-01-01T00:00:00Z",
            deleted_at = "2026-01-02T00:00:00Z",
        )
        coEvery { bookDao.getDeleted() } returns listOf(deletedBook)
        coEvery { progressDao.getDeleted() } returns listOf(
            ReadingProgressEntity(book_id = "b1", updated_at = "2026-01-01T00:00:00Z", deleted_at = "2026-01-02T00:00:00Z"),
        )
        coEvery { sessionDao.getDeleted() } returns listOf(
            session("s1", "b1").copy(deleted_at = "2026-01-02T00:00:00Z"),
            session("s9", "other").copy(deleted_at = "2026-01-02T00:00:00Z"),
        )
        coEvery { bookFileDao.getDeleted() } returns emptyList()
        coEvery { noteDao.getDeleted() } returns listOf(
            note("n1", "b1").copy(deleted_at = "2026-01-02T00:00:00Z"),
        )
        coEvery { highlightDao.getDeleted() } returns listOf(
            highlight("h1", "b1").copy(deleted_at = "2026-01-02T00:00:00Z"),
            highlight("h9", "other").copy(deleted_at = "2026-01-02T00:00:00Z"),
        )

        repository.restoreBook("b1")

        // 书籍与进度单条恢复
        coVerify(exactly = 1) { bookDao.upsert(withArg { assertEquals("b1", it.id); assertNull(it.deleted_at) }) }
        coVerify(exactly = 1) { progressDao.upsert(withArg { assertEquals("b1", it.book_id); assertNull(it.deleted_at) }) }

        val sessionsSlot = slot<List<ReadingSessionEntity>>()
        coVerify(exactly = 1) { sessionDao.upsertAll(capture(sessionsSlot)) }
        // 只恢复目标书的会话，其它书的已删记录保持不变
        assertEquals(listOf("s1"), sessionsSlot.captured.map { it.id })
        assertTrue(sessionsSlot.captured.all { it.deleted_at == null })

        val notesSlot = slot<List<NoteEntity>>()
        coVerify(exactly = 1) { noteDao.upsertAll(capture(notesSlot)) }
        assertEquals(listOf("n1"), notesSlot.captured.map { it.id })
        assertTrue(notesSlot.captured.all { it.deleted_at == null })

        val highlightsSlot = slot<List<HighlightEntity>>()
        coVerify(exactly = 1) { highlightDao.upsertAll(capture(highlightsSlot)) }
        assertEquals(listOf("h1"), highlightsSlot.captured.map { it.id })
        assertTrue(highlightsSlot.captured.all { it.deleted_at == null })

        coVerify(exactly = 0) { sessionDao.upsert(any()) }
        coVerify(exactly = 0) { noteDao.upsert(any()) }
        coVerify(exactly = 0) { highlightDao.upsert(any()) }
    }

    // ── 阅读进度 / 完成状态 ────────────────────────────────

    @Test
    fun `updateReadingProgress forces finished when percent reaches 99_5`() = runTest {
        coEvery { progressDao.getByBook("b1") } returns null

        repository.updateReadingProgress("b1", progressPercent = 99.5f, completionState = "reading")

        coVerify(exactly = 1) {
            progressDao.upsert(withArg {
                assertEquals("finished", it.completion_state)
                assertEquals(99.5f, it.progress_percent)
                assertTrue(it.completed_at != null)
                assertTrue(it.last_read_at != null)
            })
        }
    }

    @Test
    fun `updateReadingProgress keeps existing completed_at on repeat finish`() = runTest {
        coEvery { progressDao.getByBook("b1") } returns ReadingProgressEntity(
            book_id = "b1", updated_at = "2026-01-01T00:00:00Z",
            completion_state = "finished", completed_at = 123L,
        )

        repository.updateReadingProgress("b1", progressPercent = 100f, completionState = "finished")

        coVerify(exactly = 1) {
            progressDao.upsert(withArg {
                assertEquals("finished", it.completion_state)
                assertEquals(123L, it.completed_at)
            })
        }
    }

    @Test
    fun `updateReadingProgress clamps negative percent and clears completed_at when not finished`() = runTest {
        coEvery { progressDao.getByBook("b1") } returns ReadingProgressEntity(
            book_id = "b1", updated_at = "2026-01-01T00:00:00Z",
            last_read_at = "2026-01-02T00:00:00Z",
            completion_state = "finished", completed_at = 123L,
        )

        repository.updateReadingProgress("b1", progressPercent = -5f, completionState = "reading")

        coVerify(exactly = 1) {
            progressDao.upsert(withArg {
                // 负数进度被 clamp 到 0，且不视为一次阅读（last_read_at 保持）
                assertEquals(0f, it.progress_percent)
                assertEquals("reading", it.completion_state)
                assertNull(it.completed_at)
                assertEquals("2026-01-02T00:00:00Z", it.last_read_at)
            })
        }
    }

    @Test
    fun `updateReadingProgress with zero percent keeps last_read_at`() = runTest {
        coEvery { progressDao.getByBook("b1") } returns ReadingProgressEntity(
            book_id = "b1", updated_at = "2026-01-01T00:00:00Z",
            last_read_at = "2026-01-02T00:00:00Z",
        )

        repository.updateReadingProgress("b1", progressPercent = 0f, completionState = "reading")

        coVerify(exactly = 1) {
            progressDao.upsert(withArg {
                assertEquals("2026-01-02T00:00:00Z", it.last_read_at)
            })
        }
    }

    @Test
    fun `setReadingState shelved clears completed_at without touching percent`() = runTest {
        coEvery { progressDao.getByBook("b1") } returns ReadingProgressEntity(
            book_id = "b1", updated_at = "2026-01-01T00:00:00Z",
            progress_percent = 42f, completion_state = "finished", completed_at = 99L,
        )

        repository.setReadingState("b1", ReadingCompletionState.SHELVED)

        coVerify(exactly = 1) {
            progressDao.upsert(withArg {
                assertEquals("shelved", it.completion_state)
                assertNull(it.completed_at)
                assertEquals(42f, it.progress_percent)
            })
        }
    }

    @Test
    fun `setReadingState creates progress row for unknown book`() = runTest {
        coEvery { progressDao.getByBook("new-book") } returns null

        repository.setReadingState("new-book", ReadingCompletionState.FINISHED)

        coVerify(exactly = 1) {
            progressDao.upsert(withArg {
                assertEquals("new-book", it.book_id)
                assertEquals("finished", it.completion_state)
                assertTrue(it.completed_at != null)
            })
        }
    }

    // ── 清缓存 / 基础信息更新 ────────────────────────────────

    @Test
    fun `clearBookCache resets content status and soft deletes file`() = runTest {
        coEvery { bookDao.getById("b1") } returns BookEntity(
            id = "b1", title = "书", format = "txt",
            content_status = "available", local_uri = "file:///x",
            updated_at = "2026-01-01T00:00:00Z",
        )
        coEvery { bookContentDao.getByBook("b1") } returns
            BookContentEntity(book_id = "b1", reader_preview = "预览", epub_json = "{}")
        coEvery { bookFileDao.getByBook("b1") } returns
            BookFileEntity(
                book_id = "b1", file_name = "b1.txt", format = "txt",
                updated_at = "2026-01-01T00:00:00Z",
            )

        repository.clearBookCache("b1")

        coVerify(exactly = 1) {
            bookDao.upsert(withArg {
                assertEquals("missing", it.content_status)
                assertNull(it.local_uri)
                assertNull(it.local_content_path)
            })
        }
        coVerify(exactly = 1) {
            bookContentDao.upsert(withArg {
                assertNull(it.reader_preview)
                assertNull(it.epub_json)
            })
        }
        coVerify(exactly = 1) {
            bookFileDao.upsert(withArg { assertTrue(it.deleted_at != null) })
        }
        coVerify(exactly = 0) { chapterReadDao.clearForBook(any()) }
    }

    @Test
    fun `clearBookCache is no-op for unknown book`() = runTest {
        coEvery { bookDao.getById("none") } returns null
        coEvery { bookContentDao.getByBook("none") } returns null
        coEvery { bookFileDao.getByBook("none") } returns null

        repository.clearBookCache("none")

        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    @Test
    fun `updateBookInfo writes title author and description`() = runTest {
        coEvery { bookDao.getById("b1") } returns BookEntity(
            id = "b1", title = "旧名", format = "txt", updated_at = "2026-01-01T00:00:00Z",
        )

        repository.updateBookInfo("b1", title = "新名", author = "新作者", description = "简介")

        coVerify(exactly = 1) {
            bookDao.upsert(withArg {
                assertEquals("新名", it.title)
                assertEquals("新作者", it.author)
                assertEquals("简介", it.description)
            })
        }
    }

    @Test
    fun `updateBookCover writes cover data url`() = runTest {
        coEvery { bookDao.getById("b1") } returns BookEntity(
            id = "b1", title = "书", format = "txt", updated_at = "2026-01-01T00:00:00Z",
        )

        repository.updateBookCover("b1", "data:image/png;base64,abc")

        coVerify(exactly = 1) {
            bookDao.upsert(withArg { assertEquals("data:image/png;base64,abc", it.cover_data_url) })
        }
    }

    // ── 观察流委托 ─────────────────────────────────────────

    @Test
    fun `observe flows delegate to corresponding daos`() {
        every { bookDao.observeAllActive() } returns flowOf(emptyList())
        every { progressDao.observeAllActive() } returns flowOf(emptyList())
        every { noteDao.observeAllActive() } returns flowOf(emptyList())
        every { highlightDao.observeAllActive() } returns flowOf(emptyList())
        every { inspirationDao.observeAllActive() } returns flowOf(emptyList())
        every { sessionDao.observeAllActive() } returns flowOf(emptyList())

        repository.observeBooks()
        repository.observeProgress()
        repository.observeNotes()
        repository.observeHighlights()
        repository.observeInspirations()
        repository.observeSessions()

        verify(exactly = 1) { bookDao.observeAllActive() }
        verify(exactly = 1) { progressDao.observeAllActive() }
        verify(exactly = 1) { noteDao.observeAllActive() }
        verify(exactly = 1) { highlightDao.observeAllActive() }
        verify(exactly = 1) { inspirationDao.observeAllActive() }
        verify(exactly = 1) { sessionDao.observeAllActive() }
    }
}
