package com.creationreadingassistant.feature.library.deletion

import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.repository.BookRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

/**
 * 删除/撤销回归测试用的内存书库。
 *
 * DAO 是 mockk 门面，背后是这里的真实集合：被测的是 [BookRepository] 的快照与恢复逻辑
 * 本身，而不是「某方法被调了几次」。三件事按 Room 的真实语义建模：
 *
 * 1. 软删除过滤——`getById`/`getByBook`/`observe*` 只返回 `deleted_at IS NULL` 的行，
 *    `getDeleted()` 只返回有戳的行；`ReadingSessionDao.getByIds` 例外，它刻意不过滤。
 * 2. 连接表硬删除——`book_tag`/`book_category`/`shelf_book`/`chapter_reads` 没有
 *    `deleted_at` 列，`clearByBook`/`clearForBook` 真的把行删掉，库里不留任何痕迹。
 *    这正是「没有快照就恢复不了关联」的原因，也是本任务要证明的核心缺陷。
 * 3. 事务原子性——[runInTransaction] 在 block 抛错时回滚到进入前的状态。
 */
internal class DeletionLibraryFixture {

    val books = LinkedHashMap<String, BookEntity>()
    val contents = LinkedHashMap<String, BookContentEntity>()
    val files = LinkedHashMap<String, BookFileEntity>()
    val progress = LinkedHashMap<String, ReadingProgressEntity>()
    val sessions = LinkedHashMap<String, ReadingSessionEntity>()
    val notes = LinkedHashMap<String, NoteEntity>()
    val highlights = LinkedHashMap<String, HighlightEntity>()
    val bookTags = LinkedHashMap<Pair<String, String>, BookTagEntity>()
    val bookCategories = LinkedHashMap<Pair<String, String>, BookCategoryEntity>()
    val shelfBooks = LinkedHashMap<Pair<String, String>, ShelfBookEntity>()
    val chapterReads = LinkedHashMap<Pair<String, Int>, ChapterReadEntity>()

    /**
     * 置 true 后 `chapterReadDao.clearForBook` 抛错。它是 applyDeletion 的最后一步，
     * 所以能验证「事务中途失败时，前面已写进去的软删除与已清掉的关联不会留在库里」。
     */
    var failOnChapterReadClear = false

    /**
     * 置 true 后 `chapterReadDao.upsertAll` 抛错。它是恢复路径的最后一步，
     * 用来验证「撤销事务失败时不留半截恢复，且凭证仍在有效期内可以重试」。
     */
    var failOnChapterReadRestore = false

    val bookDao: BookDao = mockk(relaxed = true) {
        coEvery { runInTransaction(any()) } coAnswers {
            val backup = backup()
            try {
                firstArg<suspend () -> Unit>().invoke()
            } catch (e: Throwable) {
                restore(backup)
                throw e
            }
        }
        // SELECT * FROM books WHERE id = :id AND deleted_at IS NULL
        coEvery { getById(any()) } answers { books[firstArg<String>()]?.takeIf { it.deleted_at == null } }
        // SELECT * FROM books WHERE id IN (:ids) AND deleted_at IS NULL
        coEvery { getByIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            books.values.filter { ids.contains(it.id) && it.deleted_at == null }
        }
        coEvery { getDeleted() } answers { books.values.filter { it.deleted_at != null } }
        // UPDATE books SET deleted_at = :ts, updated_at = :ts WHERE id = :id
        coEvery { softDelete(any(), any()) } answers {
            val id = firstArg<String>()
            val ts = secondArg<String>()
            books[id]?.let { books[id] = it.copy(deleted_at = ts, updated_at = ts) }
        }
        coEvery { upsert(any()) } answers { firstArg<BookEntity>().let { books[it.id] = it } }
        coEvery { upsertAll(any()) } answers { firstArg<List<BookEntity>>().forEach { books[it.id] = it } }
        every { observeAllActive() } answers { flowOf(books.values.filter { it.deleted_at == null }) }
    }

    val bookContentDao: BookContentDao = mockk(relaxed = true) {
        // book_content 没有 deleted_at 列：删除正文缓存是把两个载荷置空，不是删行。
        coEvery { getByBook(any()) } answers { contents[firstArg<String>()] }
        coEvery { getPreviewsByBookIds(any()) } answers {
            val ids = firstArg<List<String>>()
            contents.values.filter { ids.contains(it.book_id) }
        }
        coEvery { upsert(any()) } answers { firstArg<BookContentEntity>().let { contents[it.book_id] = it } }
    }

    val bookFileDao: BookFileDao = mockk(relaxed = true) {
        coEvery { getByBook(any()) } answers { files[firstArg<String>()]?.takeIf { it.deleted_at == null } }
        coEvery { getDeleted() } answers { files.values.filter { it.deleted_at != null } }
        coEvery { upsert(any()) } answers { firstArg<BookFileEntity>().let { files[it.book_id] = it } }
    }

    val progressDao: ReadingProgressDao = mockk(relaxed = true) {
        coEvery { getByBook(any()) } answers { progress[firstArg<String>()]?.takeIf { it.deleted_at == null } }
        coEvery { getByBooks(any()) } answers {
            val ids = firstArg<Collection<String>>()
            progress.values.filter { ids.contains(it.book_id) && it.deleted_at == null }
        }
        coEvery { getDeleted() } answers { progress.values.filter { it.deleted_at != null } }
        coEvery { upsert(any()) } answers {
            firstArg<ReadingProgressEntity>().let { progress[it.book_id] = it }
        }
        every { observeAllActive() } answers { flowOf(progress.values.filter { it.deleted_at == null }) }
    }

    val sessionDao: ReadingSessionDao = mockk(relaxed = true) {
        coEvery { upsert(any()) } answers { firstArg<ReadingSessionEntity>().let { sessions[it.id] = it } }
        coEvery { upsertAll(any()) } answers {
            firstArg<List<ReadingSessionEntity>>().forEach { sessions[it.id] = it }
        }
        // getByIds 刻意不过滤软删除：恢复逻辑靠 deleted_at 戳区分「本次删的」和「之后动过的」。
        coEvery { getByIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            sessions.values.filter { ids.contains(it.id) }
        }
        coEvery { getById(any()) } answers { sessions[firstArg<String>()] }
        coEvery { getDeleted() } answers { sessions.values.filter { it.deleted_at != null } }
        every { observeByBook(any()) } answers {
            val bookId = firstArg<String>()
            flowOf(sessions.values.filter { it.book_id == bookId && it.deleted_at == null })
        }
        every { observeAllActive() } answers { flowOf(sessions.values.filter { it.deleted_at == null }) }
    }

    val noteDao: NoteDao = mockk(relaxed = true) {
        coEvery { upsert(any()) } answers { firstArg<NoteEntity>().let { notes[it.id] = it } }
        coEvery { upsertAll(any()) } answers { firstArg<List<NoteEntity>>().forEach { notes[it.id] = it } }
        coEvery { getById(any()) } answers { notes[firstArg<String>()] }
        coEvery { getByIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            notes.values.filter { ids.contains(it.id) }
        }
        coEvery { getDeleted() } answers { notes.values.filter { it.deleted_at != null } }
        coEvery { getActiveByBookIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            notes.values.filter { ids.contains(it.book_id) && it.deleted_at == null }
        }
        every { observeAllActive() } answers { flowOf(notes.values.filter { it.deleted_at == null }) }
        every { observeByBook(any()) } answers {
            val bookId = firstArg<String>()
            flowOf(notes.values.filter { it.book_id == bookId && it.deleted_at == null })
        }
    }

    val highlightDao: HighlightDao = mockk(relaxed = true) {
        coEvery { upsert(any()) } answers { firstArg<HighlightEntity>().let { highlights[it.id] = it } }
        coEvery { upsertAll(any()) } answers {
            firstArg<List<HighlightEntity>>().forEach { highlights[it.id] = it }
        }
        coEvery { getById(any()) } answers { highlights[firstArg<String>()] }
        coEvery { getByIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            highlights.values.filter { ids.contains(it.id) }
        }
        coEvery { getDeleted() } answers { highlights.values.filter { it.deleted_at != null } }
        coEvery { getActiveByBookIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            highlights.values.filter { ids.contains(it.book_id) && it.deleted_at == null }
        }
        every { observeAllActive() } answers { flowOf(highlights.values.filter { it.deleted_at == null }) }
        every { observeByBook(any()) } answers {
            val bookId = firstArg<String>()
            flowOf(highlights.values.filter { it.book_id == bookId && it.deleted_at == null })
        }
    }

    val bookTagDao: BookTagDao = mockk(relaxed = true) {
        // 真实查询是 SELECT * FROM book_tag：方法名里的 Active 是历史遗留，连接表没有软删除。
        coEvery { getAllActive() } answers { bookTags.values.toList() }
        coEvery { getByBookIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            bookTags.values.filter { ids.contains(it.book_id) }
        }
        coEvery { clearByBook(any()) } answers {
            val bookId = firstArg<String>()
            bookTags.keys.removeAll { it.first == bookId }
        }
        coEvery { upsert(any()) } answers {
            firstArg<BookTagEntity>().let { bookTags[it.book_id to it.tag_id] = it }
        }
        coEvery { upsertAll(any()) } answers {
            firstArg<List<BookTagEntity>>().forEach { bookTags[it.book_id to it.tag_id] = it }
        }
    }

    val bookCategoryDao: BookCategoryDao = mockk(relaxed = true) {
        coEvery { getAllActive() } answers { bookCategories.values.toList() }
        coEvery { getByBookIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            bookCategories.values.filter { ids.contains(it.book_id) }
        }
        coEvery { clearByBook(any()) } answers {
            val bookId = firstArg<String>()
            bookCategories.keys.removeAll { it.first == bookId }
        }
        coEvery { upsert(any()) } answers {
            firstArg<BookCategoryEntity>().let { bookCategories[it.book_id to it.category_id] = it }
        }
        coEvery { upsertAll(any()) } answers {
            firstArg<List<BookCategoryEntity>>().forEach { bookCategories[it.book_id to it.category_id] = it }
        }
    }

    val shelfBookDao: ShelfBookDao = mockk(relaxed = true) {
        coEvery { getAllActive() } answers { shelfBooks.values.toList() }
        coEvery { getByBookIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            shelfBooks.values.filter { ids.contains(it.book_id) }
        }
        coEvery { clearByBook(any()) } answers {
            val bookId = firstArg<String>()
            shelfBooks.keys.removeAll { it.second == bookId }
        }
        coEvery { upsert(any()) } answers {
            firstArg<ShelfBookEntity>().let { shelfBooks[it.shelf_id to it.book_id] = it }
        }
        coEvery { upsertAll(any()) } answers {
            firstArg<List<ShelfBookEntity>>().forEach { shelfBooks[it.shelf_id to it.book_id] = it }
        }
    }

    val chapterReadDao: ChapterReadDao = mockk(relaxed = true) {
        coEvery { getAll() } answers { chapterReads.values.toList() }
        coEvery { getByBookIds(any()) } answers {
            val ids = firstArg<Collection<String>>()
            chapterReads.values.filter { ids.contains(it.book_id) }
        }
        coEvery { clearForBook(any()) } answers {
            if (failOnChapterReadClear) error("simulated mid-transaction failure")
            val bookId = firstArg<String>()
            chapterReads.keys.removeAll { it.first == bookId }
        }
        coEvery { upsert(any()) } answers {
            firstArg<ChapterReadEntity>().let { chapterReads[it.book_id to it.chapter_index] = it }
        }
        coEvery { upsertAll(any()) } answers {
            if (failOnChapterReadRestore) error("simulated mid-restore failure")
            firstArg<List<ChapterReadEntity>>().forEach { chapterReads[it.book_id to it.chapter_index] = it }
        }
        coEvery { readCount(any()) } answers {
            val bookId = firstArg<String>()
            chapterReads.values.count { it.book_id == bookId }
        }
    }

    /** 被测仓储：注入上面全部内存 DAO；灵感库不参与删除路径，用 relaxed 占位。 */
    fun repository(): BookRepository = BookRepository(
        bookDao = bookDao,
        bookContentDao = bookContentDao,
        bookFileDao = bookFileDao,
        progressDao = progressDao,
        sessionDao = sessionDao,
        noteDao = noteDao,
        highlightDao = highlightDao,
        inspirationDao = mockk(relaxed = true),
        bookTagDao = bookTagDao,
        bookCategoryDao = bookCategoryDao,
        shelfBookDao = shelfBookDao,
        chapterReadDao = chapterReadDao,
        context = mockk(relaxed = true),
    )

    // ===== 播种 =====

    fun addBook(id: String, title: String = "测试书籍 $id"): BookEntity =
        BookEntity(id = id, title = title, format = "txt", updated_at = STAMP_BASE)
            .also { books[id] = it }

    fun addProgress(bookId: String, percent: Float = 42f, deletedAt: String? = null): ReadingProgressEntity =
        ReadingProgressEntity(
            book_id = bookId,
            progress_percent = percent,
            updated_at = STAMP_BASE,
            deleted_at = deletedAt,
        ).also { progress[bookId] = it }

    fun addSession(id: String, bookId: String, deletedAt: String? = null): ReadingSessionEntity =
        ReadingSessionEntity(
            id = id,
            book_id = bookId,
            duration_ms = 60_000L,
            updated_at = STAMP_BASE,
            deleted_at = deletedAt,
        ).also { sessions[id] = it }

    fun addNote(id: String, bookId: String, deletedAt: String? = null): NoteEntity =
        NoteEntity(
            id = id,
            book_id = bookId,
            title = "测试笔记 $id",
            created_at = STAMP_BASE,
            updated_at = STAMP_BASE,
            deleted_at = deletedAt,
        ).also { notes[id] = it }

    fun addHighlight(id: String, bookId: String, deletedAt: String? = null): HighlightEntity =
        HighlightEntity(
            id = id,
            book_id = bookId,
            text = "测试高亮 $id",
            created_at = STAMP_BASE,
            updated_at = STAMP_BASE,
            deleted_at = deletedAt,
        ).also { highlights[id] = it }

    fun addContent(bookId: String, preview: String = "测试正文缓存"): BookContentEntity =
        BookContentEntity(book_id = bookId, reader_preview = preview).also { contents[bookId] = it }

    fun addFile(bookId: String, deletedAt: String? = null): BookFileEntity =
        BookFileEntity(
            book_id = bookId,
            file_name = "test-$bookId.txt",
            format = "txt",
            updated_at = STAMP_BASE,
            deleted_at = deletedAt,
        ).also { files[bookId] = it }

    fun linkTag(bookId: String, tagId: String) {
        bookTags[bookId to tagId] = BookTagEntity(book_id = bookId, tag_id = tagId)
    }

    fun linkCategory(bookId: String, categoryId: String) {
        bookCategories[bookId to categoryId] = BookCategoryEntity(book_id = bookId, category_id = categoryId)
    }

    fun linkShelf(bookId: String, shelfId: String, position: Int = 0) {
        shelfBooks[shelfId to bookId] = ShelfBookEntity(shelf_id = shelfId, book_id = bookId, position = position)
    }

    fun markChapterRead(bookId: String, chapterIndex: Int) {
        chapterReads[bookId to chapterIndex] =
            ChapterReadEntity(book_id = bookId, chapter_index = chapterIndex, read_at = STAMP_BASE)
    }

    // ===== 断言辅助 =====

    fun tagIdsOf(bookId: String): Set<String> =
        bookTags.values.filter { it.book_id == bookId }.map { it.tag_id }.toSet()

    fun categoryIdsOf(bookId: String): Set<String> =
        bookCategories.values.filter { it.book_id == bookId }.map { it.category_id }.toSet()

    fun shelfLinksOf(bookId: String): List<ShelfBookEntity> =
        shelfBooks.values.filter { it.book_id == bookId }

    fun chapterIndexesOf(bookId: String): Set<Int> =
        chapterReads.values.filter { it.book_id == bookId }.map { it.chapter_index }.toSet()

    fun activeNoteIds(bookId: String): Set<String> =
        notes.values.filter { it.book_id == bookId && it.deleted_at == null }.map { it.id }.toSet()

    fun activeHighlightIds(bookId: String): Set<String> =
        highlights.values.filter { it.book_id == bookId && it.deleted_at == null }.map { it.id }.toSet()

    fun activeSessionIds(bookId: String): Set<String> =
        sessions.values.filter { it.book_id == bookId && it.deleted_at == null }.map { it.id }.toSet()

    fun isBookActive(bookId: String): Boolean = books[bookId]?.deleted_at == null

    fun bookStamp(bookId: String): String? = books[bookId]?.deleted_at

    private fun backup(): State = State(this)

    private fun restore(state: State) {
        books.clear(); books.putAll(state.books)
        contents.clear(); contents.putAll(state.contents)
        files.clear(); files.putAll(state.files)
        progress.clear(); progress.putAll(state.progress)
        sessions.clear(); sessions.putAll(state.sessions)
        notes.clear(); notes.putAll(state.notes)
        highlights.clear(); highlights.putAll(state.highlights)
        bookTags.clear(); bookTags.putAll(state.bookTags)
        bookCategories.clear(); bookCategories.putAll(state.bookCategories)
        shelfBooks.clear(); shelfBooks.putAll(state.shelfBooks)
        chapterReads.clear(); chapterReads.putAll(state.chapterReads)
    }

    /** 事务进入前的全量副本；实体是不可变 data class，浅拷贝即可。 */
    private class State(f: DeletionLibraryFixture) {
        val books = LinkedHashMap(f.books)
        val contents = LinkedHashMap(f.contents)
        val files = LinkedHashMap(f.files)
        val progress = LinkedHashMap(f.progress)
        val sessions = LinkedHashMap(f.sessions)
        val notes = LinkedHashMap(f.notes)
        val highlights = LinkedHashMap(f.highlights)
        val bookTags = LinkedHashMap(f.bookTags)
        val bookCategories = LinkedHashMap(f.bookCategories)
        val shelfBooks = LinkedHashMap(f.shelfBooks)
        val chapterReads = LinkedHashMap(f.chapterReads)
    }

    companion object {
        const val STAMP_BASE = "2026-09-09T08:00:00Z"
        const val STAMP_FIRST = "2026-09-09T10:00:00Z"
        const val STAMP_SECOND = "2026-09-09T10:00:05Z"
        const val STAMP_OTHER = "2026-09-09T09:00:00Z"
    }
}
