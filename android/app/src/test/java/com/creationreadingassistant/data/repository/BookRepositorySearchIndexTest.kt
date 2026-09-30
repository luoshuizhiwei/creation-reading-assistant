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
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.library.deletion.BookDeletionSnapshot
import com.creationreadingassistant.feature.library.deletion.LiveRelationTargets
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M-BUG-10 回归护栏：删除书必须清掉它的全文索引孤儿行，撤销删除必须重建索引，
 * 且重建失败只能告警、绝不让撤销操作崩溃。
 *
 * 这些用例是「行为护栏」而非「实现细节」：即便未来有人把清索引的调用摘掉，测试也应该 FAIL。
 * 验证方法：本文件随 M-BUG-10 修复一并落地；落地后用「临时移除修复再跑一次」确认它确实会
 * 在核心断言上失败（见会话记录），证明护栏有效而非恒真。
 */
class BookRepositorySearchIndexTest {

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
    private lateinit var searchIndexRepository: SearchIndexRepository

    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        bookDao = mockk(relaxed = true) {
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
        // 记录调用的假索引仓储：relaxed 既提供默认实现，又记录所有调用供 coVerify。
        searchIndexRepository = mockk(relaxed = true)

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
            searchIndexRepository = searchIndexRepository,
            context = mockk(relaxed = true),
        )
    }

    /** 删除书必须清掉该书的全文索引（term + coverage），否则已删书会冒进搜索结果。 */
    @Test
    fun `deleteBook clears search index for the deleted book`() = runTest {
        every { sessionDao.observeByBook("b1") } returns flowOf(emptyList())

        repository.deleteBook("b1")

        coVerify(exactly = 1) { searchIndexRepository.deleteIndexForBook("b1") }
    }

    /**
     * 批量删除：每本书都应在各自事务内清掉自己的索引孤儿行。
     * 用两条不同的书 id 验证「按书隔离」，而非只清了最后一本。
     */
    @Test
    fun `deleteBooksScoped clears search index for every deleted book`() = runTest {
        every { sessionDao.observeByBook("b1") } returns flowOf(emptyList())
        every { sessionDao.observeByBook("b2") } returns flowOf(emptyList())

        repository.deleteBooksScoped(listOf("b1", "b2"))

        coVerify(exactly = 1) { searchIndexRepository.deleteIndexForBook("b1") }
        coVerify(exactly = 1) { searchIndexRepository.deleteIndexForBook("b2") }
    }

    /**
     * 撤销恢复被删的书后必须重建其全文索引：增量索引游标只前进不回退，
     * 不重建的话该书在下次全量重建前永远不可全文搜索。
     * 这里用 getDeleted() 返回带本次戳的已删书，使 restoreOneSnapshot 判定 restoredBook = true。
     */
    @Test
    fun `restoreDeletion reindexes a successfully restored book`() = runTest {
        val stamp = "2026-01-02T00:00:00Z"
        val snapshot = BookDeletionSnapshot(
            bookId = "b1",
            deletedAt = stamp,
            book = BookEntity(id = "b1", title = "甲书", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
            progress = null,
            sessions = emptyList(),
            notes = emptyList(),
            highlights = emptyList(),
            file = null,
            content = null,
            contentPayloadRetained = false,
            tagIds = emptyList(),
            categoryIds = emptyList(),
            shelfLinks = emptyList(),
            chapterReads = emptyList(),
        )
        // 让 deletedBooks[bookId] 命中且戳与快照一致 → restoredBook = true。
        coEvery { bookDao.getDeleted() } returns listOf(
            BookEntity(id = "b1", title = "甲书", format = "txt", updated_at = "2026-01-01T00:00:00Z", deleted_at = stamp),
        )

        repository.restoreDeletion(snapshot, LiveRelationTargets.EMPTY)

        coVerify(exactly = 1) { searchIndexRepository.reindexBookById("b1") }
    }

    /**
     * 索引重建失败只能告警，绝不让撤销操作崩溃：书已经恢复，搜索缺失会在下次后台扫描自愈。
     * 这里让 reindexBookById 抛异常，断言 restoreDeletion 正常返回（不向上抛），
     * 且重建确实被尝试过一次（证明不是因为 restoredBook=false 而没走到重建）。
     */
    @Test
    fun `restoreDeletion survives index rebuild failure without crashing`() = runTest {
        val stamp = "2026-01-02T00:00:00Z"
        val snapshot = BookDeletionSnapshot(
            bookId = "b1",
            deletedAt = stamp,
            book = BookEntity(id = "b1", title = "甲书", format = "txt", updated_at = "2026-01-01T00:00:00Z"),
            progress = null,
            sessions = emptyList(),
            notes = emptyList(),
            highlights = emptyList(),
            file = null,
            content = null,
            contentPayloadRetained = false,
            tagIds = emptyList(),
            categoryIds = emptyList(),
            shelfLinks = emptyList(),
            chapterReads = emptyList(),
        )
        coEvery { bookDao.getDeleted() } returns listOf(
            BookEntity(id = "b1", title = "甲书", format = "txt", updated_at = "2026-01-01T00:00:00Z", deleted_at = stamp),
        )
        coEvery { searchIndexRepository.reindexBookById(any()) } coAnswers { throw RuntimeException("索引重建炸了") }

        // 不应抛出；若 runCatching 失效，这里会抛 IllegalStateException/原异常而 FAIL。
        repository.restoreDeletion(snapshot, LiveRelationTargets.EMPTY)

        // 重建确实被尝试过一次（证明走到了 reindex 分支，而非被短路跳过）。
        coVerify(exactly = 1) { searchIndexRepository.reindexBookById("b1") }
        // 书主行仍然被恢复了（upsert 假设 deleted_at 置空），证明重建失败没有阻断恢复提交。
        coVerify(exactly = 1) {
            bookDao.upsert(withArg { assertEquals("b1", it.id); assertTrue(it.deleted_at == null) })
        }
    }
}
