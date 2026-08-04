package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.data.repository.SyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileSyncEngineTest {

    private val syncRepository = mockk<SyncRepository>(relaxed = true)
    private val configStore = mockk<SyncConfigStore>(relaxed = true)
    private val bookDao = mockk<BookDao>(relaxed = true)

    private fun engine() = ProfileSyncEngine(syncRepository, configStore, bookDao)

    private fun book(
        id: String,
        status: String = "available",
        localUri: String? = null,
        localPath: String? = null,
    ) = BookEntity(
        id = id,
        title = "书-$id",
        format = "txt",
        content_status = status,
        local_uri = localUri,
        local_content_path = localPath,
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun pullResult(
        books: Int = 0,
        inspirations: Int = 0,
        progress: Int = 0,
        sessions: Int = 0,
        failures: List<SyncRepository.SyncFailedEntry> = emptyList(),
    ) = SyncRepository.PullResult(books, inspirations, progress, sessions, failures)

    private fun pushResult(
        applied: SyncContract.SyncPushResult.AppliedCount = SyncContract.SyncPushResult.AppliedCount(),
        conflicts: List<SyncRepository.SyncConflictEntry> = emptyList(),
        failures: List<SyncRepository.SyncFailedEntry> = emptyList(),
    ) = SyncRepository.PushResult(applied, conflicts, failures)

    @Test
    fun `runSync success path aggregates counts and pending downloads`() = runTest {
        coEvery { syncRepository.pull() } returns pullResult(books = 2, inspirations = 3, progress = 4, sessions = 5)
        coEvery { syncRepository.push() } returns pushResult(
            applied = SyncContract.SyncPushResult.AppliedCount(inspirations = 1, books = 2, progress = 3, sessions = 4),
        )
        coEvery { syncRepository.downloadPendingBooks(maxCount = 20) } returns
            SyncRepository.DownloadBooksResult(success = 6, failed = 0)
        every { bookDao.observeAllActive() } returns flowOf(
            listOf(
                book(id = "b1", localUri = "/cache/b1.txt"),
                book(id = "b2", status = "missing"),
            )
        )

        val outcome = engine().runSync(autoDownloadBooks = true)

        val result = outcome.result
        assertTrue(result.success)
        assertEquals(2, result.uploaded.books)
        assertEquals(1, result.uploaded.inspirations)
        assertEquals(3, result.uploaded.progress)
        assertEquals(4, result.uploaded.sessions)
        assertEquals(2, result.downloaded.books)
        assertEquals(3, result.downloaded.inspirations)
        assertEquals(6, result.downloaded.bookFiles)
        assertEquals(1, result.pendingDownloadCount)
        assertTrue(result.failedItems.isEmpty())
        assertTrue(result.conflicts.isEmpty())
        assertEquals("同步完成：拉取 2 书 / 3 灵感；推送 3 条；冲突 0；下载 6 本", outcome.message)
        assertEquals(
            listOf(
                "拉取完成：2 本 / 3 条 / 4 进度 / 5 会话",
                "推送完成：10 条",
                "开始下载书籍正文…",
                "下载完成：6 本成功，0 本失败",
            ),
            outcome.logs,
        )
    }

    @Test
    fun `runSync without autoDownload skips download step`() = runTest {
        coEvery { syncRepository.pull() } returns pullResult()
        coEvery { syncRepository.push() } returns pushResult()

        val outcome = engine().runSync(autoDownloadBooks = false)

        coVerify(exactly = 0) { syncRepository.downloadPendingBooks(any()) }
        assertEquals(0, outcome.result.downloaded.bookFiles)
        assertFalse(outcome.logs.any { it.startsWith("开始下载") || it.startsWith("下载完成") })
    }

    @Test
    fun `runSync collects pull and download failures and logs them`() = runTest {
        coEvery { syncRepository.pull() } returns pullResult(
            books = 1,
            failures = listOf(
                SyncRepository.SyncFailedEntry(type = "book", id = "b1", title = "书-b1", reason = "校验失败"),
                SyncRepository.SyncFailedEntry(type = "inspiration", id = "i1", title = null, reason = "超时"),
            ),
        )
        coEvery { syncRepository.push() } returns pushResult()
        coEvery { syncRepository.downloadPendingBooks(maxCount = 20) } returns
            SyncRepository.DownloadBooksResult(success = 0, failed = 1, failedItems = listOf(
                SyncRepository.SyncFailedEntry(type = "book_file", id = "b2", title = "书-b2", reason = "HTTP 500"),
            ))
        every { bookDao.observeAllActive() } returns flowOf(emptyList())

        val outcome = engine().runSync(autoDownloadBooks = true)

        val failures = outcome.result.failedItems
        assertEquals(3, failures.size)
        val bookFail = failures.first { it.type == "book" }
        assertEquals("b1", bookFail.bookId)
        val inspirationFail = failures.first { it.type == "inspiration" }
        assertNull(inspirationFail.bookId)
        val fileFail = failures.first { it.type == "book_file" }
        assertEquals("b2", fileFail.bookId)
        assertTrue(outcome.logs.contains("  拉取失败 2 项"))
        assertTrue(outcome.logs.contains("下载完成：0 本成功，1 本失败"))
    }

    @Test
    fun `runSync maps conflicts into ui items`() = runTest {
        coEvery { syncRepository.pull() } returns pullResult()
        coEvery { syncRepository.push() } returns pushResult(
            conflicts = listOf(
                SyncRepository.SyncConflictEntry(
                    type = "book", id = "b1", title = "书-b1",
                    remoteUpdatedAt = "r", localUpdatedAt = "l", resolution = "remote",
                ),
            ),
        )
        every { bookDao.observeAllActive() } returns flowOf(emptyList())

        val outcome = engine().runSync(autoDownloadBooks = false)

        assertEquals(1, outcome.result.conflicts.size)
        val conflict = outcome.result.conflicts[0]
        assertEquals("book", conflict.type)
        assertEquals("b1", conflict.id)
        assertEquals("remote", conflict.resolution)
        assertTrue(outcome.logs.contains("  冲突 1 条（服务端保留）"))
    }

    @Test
    fun `runSync failure path returns failed result with sync item`() = runTest {
        coEvery { syncRepository.pull() } throws IllegalStateException("网络不可达")
        every { bookDao.observeAllActive() } returns flowOf(emptyList())

        val outcome = engine().runSync(autoDownloadBooks = true)

        assertFalse(outcome.result.success)
        assertEquals(1, outcome.result.failedItems.size)
        val item = outcome.result.failedItems[0]
        assertEquals("sync", item.type)
        assertNull(item.bookId)
        assertEquals("网络不可达", item.reason)
        assertEquals("同步失败：网络不可达", outcome.message)
        assertEquals(listOf("同步失败：网络不可达"), outcome.logs)
    }

    @Test
    fun `retryBookFile returns success of underlying download`() = runTest {
        coEvery { syncRepository.downloadBookContent("b1") } returns Result.success(Unit)
        coEvery { syncRepository.downloadBookContent("b2") } throws IllegalStateException("boom")

        val engine = engine()
        assertTrue(engine.retryBookFile("b1"))
        assertFalse(engine.retryBookFile("b2"))
    }

    @Test
    fun `countPendingDownloads counts books without local content`() = runTest {
        every { bookDao.observeAllActive() } returns flowOf(
            listOf(
                book(id = "ok1", localUri = "/cache/ok1.txt"),
                book(id = "ok2", status = "available", localPath = "/cache/ok2.txt"),
                book(id = "missing", status = "missing"),
                book(id = "failed", status = "failed"),
                book(id = "downloading", status = "downloading"),
                book(id = "empty", status = "available"),
            )
        )

        val pending = engine().countPendingDownloads()

        assertEquals(4, pending)
    }

    @Test
    fun `countPendingDownloads tolerates dao errors`() = runTest {
        every { bookDao.observeAllActive() } throws RuntimeException("db closed")

        assertEquals(0, engine().countPendingDownloads())
    }

    @Test
    fun `toUiFailedItem keeps bookId only for book types`() {
        val engine = engine()
        val book = engine.toUiFailedItem(
            SyncRepository.SyncFailedEntry(type = "book", id = "b1", title = "t", reason = "r")
        )
        assertEquals("b1", book.bookId)
        assertEquals("t", book.title)
        assertEquals("r", book.reason)

        val file = engine.toUiFailedItem(
            SyncRepository.SyncFailedEntry(type = "book_file", id = "b2", title = null, reason = "r")
        )
        assertEquals("b2", file.bookId)

        val inspiration = engine.toUiFailedItem(
            SyncRepository.SyncFailedEntry(type = "inspiration", id = "i1", reason = "r")
        )
        assertNull(inspiration.bookId)
    }

    @Test
    fun `isBookDownloadedById requires available status and local content`() = runTest {
        val engine = engine()
        coEvery { bookDao.getById("available") } returns book(id = "available", localUri = "/c/a.txt")
        coEvery { bookDao.getById("missing") } returns book(id = "missing", status = "missing", localUri = "/c/m.txt")
        coEvery { bookDao.getById("noLocal") } returns book(id = "noLocal", status = "available")
        coEvery { bookDao.getById("unknown") } returns null

        assertTrue(engine.isBookDownloadedById("available"))
        assertFalse(engine.isBookDownloadedById("missing"))
        assertFalse(engine.isBookDownloadedById("noLocal"))
        assertFalse(engine.isBookDownloadedById("unknown"))
        assertFalse(engine.isBookDownloadedById(null))
    }
}
