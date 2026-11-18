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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
/**
 * M-BUG-10 回归护栏：删除书必须清掉它的全文索引孤儿行。
 *
 * 撤销恢复一侧的「异步索引重建接缝」已随全文索引下线删除（索引写入全部为 no-op），
 * 因此本文件只保留删除链路的护栏：即便未来有人把清索引的调用摘掉，测试也应 FAIL。
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
}
