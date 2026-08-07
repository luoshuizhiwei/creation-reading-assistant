package com.creationreadingassistant.data.repository

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.remote.SyncApi
import com.creationreadingassistant.data.remote.SyncApiProvider
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.domain.model.SyncEnvelope
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.File

/**
 * SyncRepository 直接单测：
 * - pull 事务路径与逐类型计数；
 * - 冲突合并组合行为（委托 SyncMergePolicy：revision 优先，等 revision 比较 updated_at）；
 * - 失败条目记录（单条失败不中断整批）；
 * - 正文下载 tmp+rename 原子落盘的成功与错误分支。
 */
class SyncRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var apiProvider: SyncApiProvider
    private lateinit var api: SyncApi
    private lateinit var configStore: SyncConfigStore
    private lateinit var deviceInfoProvider: DeviceInfoProvider
    private lateinit var bookDao: BookDao
    private lateinit var bookFileDao: BookFileDao
    private lateinit var inspirationDao: InspirationDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var readingSessionDao: ReadingSessionDao
    private lateinit var context: Context

    private lateinit var repository: SyncRepository

    private val device = SyncContract.DeviceInfo(
        deviceId = "dev-1",
        name = "Test Phone",
        platform = SyncContract.SyncPlatform.android,
        pairedAt = "2026-01-01T00:00:00Z",
        lastSeenAt = "2026-01-01T00:00:00Z",
    )

    @Before
    fun setUp() {
        api = mockk()
        apiProvider = mockk {
            every { current() } returns api
        }
        configStore = mockk(relaxed = true)
        deviceInfoProvider = mockk {
            every { provide() } returns device
        }
        bookDao = mockk(relaxed = true) {
            // 事务包裹的代码块直接同步执行
            coEvery { runInTransaction(any()) } coAnswers {
                firstArg<suspend () -> Unit>().invoke()
            }
        }
        bookFileDao = mockk(relaxed = true)
        inspirationDao = mockk(relaxed = true)
        readingProgressDao = mockk(relaxed = true)
        readingSessionDao = mockk(relaxed = true)
        context = mockk {
            every { cacheDir } returns tempFolder.root
        }
        repository = SyncRepository(
            apiProvider = apiProvider,
            configStore = configStore,
            deviceInfoProvider = deviceInfoProvider,
            bookDao = bookDao,
            bookFileDao = bookFileDao,
            inspirationDao = inspirationDao,
            readingProgressDao = readingProgressDao,
            readingSessionDao = readingSessionDao,
            context = context,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun emptyManifest() = SyncContract.SyncManifest(
        device = device,
        generatedAt = "2026-01-01T00:00:00Z",
        inspirations = emptyList(),
        books = emptyList(),
        progress = emptyList(),
        sessions = emptyList(),
        bookFiles = emptyList(),
    )

    private fun bookEnvelope(
        id: String,
        revision: Int,
        updatedAt: String,
        title: String = "远端书$id",
    ): SyncEnvelope<JsonObject> = SyncEnvelope(
        id = id,
        type = "book",
        revision = revision,
        deviceId = "remote-dev",
        updatedAt = updatedAt,
        payload = buildJsonObject { put("title", title) },
    )

    private fun pullResponse(
        books: List<SyncEnvelope<JsonObject>> = emptyList(),
        inspirations: List<SyncEnvelope<JsonObject>> = emptyList(),
        progress: List<SyncEnvelope<JsonObject>> = emptyList(),
        sessions: List<SyncEnvelope<JsonObject>> = emptyList(),
    ) = SyncContract.SyncPullResponse(
        manifest = emptyManifest(),
        inspirations = inspirations,
        books = books,
        progress = progress,
        sessions = sessions,
    )

    // ── pull 事务路径 ────────────────────────────────────────────

    @Test
    fun `pull applies all envelopes within transaction and counts per type`() = runTest {
        coEvery { bookDao.getById(any()) } returns null
        coEvery { inspirationDao.getById(any()) } returns null
        coEvery { readingProgressDao.getByBook(any()) } returns null
        coEvery { readingSessionDao.getById(any()) } returns null
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(bookEnvelope("b1", 1, "2026-02-01T00:00:00Z")),
            inspirations = listOf(
                SyncEnvelope(
                    id = "i1", type = "inspiration", revision = 1, deviceId = "remote-dev",
                    updatedAt = "2026-02-01T00:00:00Z",
                    payload = buildJsonObject { put("title", "灵感"); put("body", "内容") },
                ),
            ),
            progress = listOf(
                SyncEnvelope(
                    id = "b1", type = "progress", revision = 1, deviceId = "remote-dev",
                    updatedAt = "2026-02-01T00:00:00Z", payload = buildJsonObject {},
                ),
            ),
            sessions = listOf(
                SyncEnvelope(
                    id = "s1", type = "session", revision = 1, deviceId = "remote-dev",
                    updatedAt = "2026-02-01T00:00:00Z",
                    payload = buildJsonObject { put("bookId", "b1") },
                ),
            ),
        )

        val result = repository.pull()

        assertEquals(1, result.books)
        assertEquals(1, result.inspirations)
        assertEquals(1, result.progress)
        assertEquals(1, result.sessions)
        assertTrue(result.failedItems.isEmpty())

        // 全部写入包在同一个事务中
        coVerify(exactly = 1) { bookDao.runInTransaction(any()) }

        val bookSlot = slot<BookEntity>()
        coVerify(exactly = 1) { bookDao.upsert(capture(bookSlot)) }
        assertEquals("b1", bookSlot.captured.id)
        assertEquals("远端书b1", bookSlot.captured.title)
        // payload 真相源列原样落库
        assertTrue(bookSlot.captured.payload!!.contains("远端书b1"))

        val inspirationSlot = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(inspirationSlot)) }
        assertEquals("灵感", inspirationSlot.captured.title)

        val progressSlot = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { readingProgressDao.upsert(capture(progressSlot)) }
        assertEquals("b1", progressSlot.captured.book_id)

        val sessionSlot = slot<ReadingSessionEntity>()
        coVerify(exactly = 1) { readingSessionDao.upsert(capture(sessionSlot)) }
        assertEquals("s1", sessionSlot.captured.id)
        assertEquals("b1", sessionSlot.captured.book_id)

        // pull 成功后记录同步时间
        verify { configStore.markSyncedAt(any()) }
    }

    @Test
    fun `pull without pairing fails with clear message`() = runTest {
        every { apiProvider.current() } returns null

        val error = runCatching { repository.pull() }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("尚未配对"))
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
    }

    // ── 冲突合并（委托 SyncMergePolicy 的组合行为）────────────────

    @Test
    fun `pull accepts remote when revision is higher regardless of timestamps`() = runTest {
        coEvery { bookDao.getById("b1") } returns BookEntity(
            id = "b1", title = "本地旧书", format = "txt",
            revision = 1, updated_at = "2026-05-01T00:00:00Z",
        )
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(bookEnvelope("b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z")),
        )

        val result = repository.pull()

        assertEquals(1, result.books)
        coVerify(exactly = 1) { bookDao.upsert(withArg { assertEquals("远端书b1", it.title); assertEquals(2, it.revision) }) }
    }

    @Test
    fun `pull keeps local when remote revision is lower`() = runTest {
        coEvery { bookDao.getById("b1") } returns BookEntity(
            id = "b1", title = "本地新书", format = "txt",
            revision = 3, updated_at = "2026-01-01T00:00:00Z",
        )
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(bookEnvelope("b1", revision = 2, updatedAt = "2026-06-01T00:00:00Z")),
        )

        val result = repository.pull()

        // 跳过的信封仍计入处理数，但不落库也不算失败
        assertEquals(1, result.books)
        assertTrue(result.failedItems.isEmpty())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    @Test
    fun `pull with equal revision compares updated_at`() = runTest {
        coEvery { bookDao.getById(any()) } returns BookEntity(
            id = "b1", title = "本地书", format = "txt",
            revision = 2, updated_at = "2026-03-01T00:00:00Z",
        )
        // 远端时间更新 → 接受
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(bookEnvelope("b1", revision = 2, updatedAt = "2026-04-01T00:00:00Z")),
        )
        val newer = repository.pull()
        assertEquals(1, newer.books)
        coVerify(exactly = 1) { bookDao.upsert(any()) }

        // 远端时间更旧 → 保留本地（仍计入处理数，但不新增写入）
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(bookEnvelope("b1", revision = 2, updatedAt = "2026-02-01T00:00:00Z")),
        )
        val older = repository.pull()
        assertEquals(1, older.books)
        coVerify(exactly = 1) { bookDao.upsert(any()) } // 仍是上一次的那次写入
    }

    // ── 失败条目处理 ─────────────────────────────────────────────

    @Test
    fun `pull records failed item without aborting remaining envelopes`() = runTest {
        coEvery { bookDao.getById(any()) } returns null
        coEvery { inspirationDao.getById(any()) } returns null
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(
                bookEnvelope("bad", 1, "2026-02-01T00:00:00Z", title = "坏书"),
                bookEnvelope("ok", 1, "2026-02-01T00:00:00Z", title = "好书"),
            ),
            inspirations = listOf(
                SyncEnvelope(
                    id = "i1", type = "inspiration", revision = 1, deviceId = "remote-dev",
                    updatedAt = "2026-02-01T00:00:00Z", payload = buildJsonObject {},
                ),
            ),
        )
        // 只有 id=bad 的书籍写入抛错
        coEvery { bookDao.upsert(any()) } coAnswers {
            if (firstArg<BookEntity>().id == "bad") throw IllegalStateException("boom")
        }

        val result = repository.pull()

        assertEquals(1, result.books)
        assertEquals(1, result.inspirations)
        assertEquals(1, result.failedItems.size)
        val failed = result.failedItems.single()
        assertEquals("book", failed.type)
        assertEquals("bad", failed.id)
        assertEquals("boom", failed.reason)
        assertTrue(failed.title!!.contains("坏书"))
        // 成功条目不受影响
        coVerify(exactly = 1) { bookDao.upsert(withArg { assertEquals("ok", it.id) }) }
        coVerify(exactly = 1) { inspirationDao.upsert(any()) }
    }

    // ── 正文下载：tmp + rename 原子落盘 ──────────────────────────

    @Test
    fun `downloadBookContent writes tmp then renames and marks available`() = runTest {
        val book = BookEntity(id = "b1", title = "书", format = "txt", updated_at = "2026-01-01T00:00:00Z")
        coEvery { bookDao.getById("b1") } returns book
        coEvery { api.getBookFile("b1") } returns Response.success(
            "正文内容".toByteArray().toResponseBody("application/octet-stream".toMediaType()),
        )

        val result = repository.downloadBookContent("b1")

        assertTrue(result.isSuccess)
        val file = File(tempFolder.root, "books/b1/content.txt")
        assertTrue(file.exists())
        assertEquals("正文内容", String(file.readBytes()))
        assertFalse(File(tempFolder.root, "books/b1/content.txt.tmp").exists())

        val fileSlot = slot<BookFileEntity>()
        coVerify(exactly = 1) { bookFileDao.upsert(capture(fileSlot)) }
        assertEquals("b1", fileSlot.captured.book_id)
        assertEquals(file.absolutePath, fileSlot.captured.local_uri)
        assertEquals("正文内容".toByteArray().size, fileSlot.captured.size)

        // 最后一次 book upsert 标记 available
        coVerify { bookDao.upsert(withArg { assertEquals("available", it.content_status) }) }
    }

    @Test
    fun `downloadBookContent http error marks failed`() = runTest {
        val book = BookEntity(id = "b1", title = "书", format = "txt", updated_at = "2026-01-01T00:00:00Z")
        coEvery { bookDao.getById("b1") } returns book
        coEvery { api.getBookFile("b1") } returns Response.error(
            404, "not found".toResponseBody("text/plain".toMediaType()),
        )

        // 失败分支会向外抛出异常（同时回写 failed 状态）
        val error = runCatching { repository.downloadBookContent("b1") }.exceptionOrNull()

        assertTrue(error!!.message!!.contains("HTTP 404"))
        // 失败后回写 failed 状态
        coVerify { bookDao.upsert(withArg { assertEquals("failed", it.content_status) }) }
        coVerify(exactly = 0) { bookFileDao.upsert(any()) }
    }

    @Test
    fun `downloadBookContent without pairing fails`() = runTest {
        every { apiProvider.current() } returns null
        val book = BookEntity(id = "b1", title = "书", format = "txt", updated_at = "2026-01-01T00:00:00Z")
        coEvery { bookDao.getById("b1") } returns book

        val error = runCatching { repository.downloadBookContent("b1") }.exceptionOrNull()

        assertTrue(error!!.message!!.contains("尚未配对"))
        coVerify { api wasNot Called }
    }

    @Test
    fun `downloadBookContent rename failure deletes tmp and marks failed`() = runTest {
        val book = BookEntity(id = "b1", title = "书", format = "txt", updated_at = "2026-01-01T00:00:00Z")
        coEvery { bookDao.getById("b1") } returns book
        coEvery { api.getBookFile("b1") } returns Response.success(
            "x".toByteArray().toResponseBody("application/octet-stream".toMediaType()),
        )
        // 目标位置预先被一个目录占用 → renameTo 失败，走错误分支
        File(tempFolder.root, "books/b1").mkdirs()
        File(tempFolder.root, "books/b1/content.txt").mkdir()

        val error = runCatching { repository.downloadBookContent("b1") }.exceptionOrNull()

        assertTrue(error!!.message!!.contains("重命名"))
        // 半截 tmp 文件被清理
        assertFalse(File(tempFolder.root, "books/b1/content.txt.tmp").exists())
        coVerify { bookDao.upsert(withArg { assertEquals("failed", it.content_status) }) }
        coVerify(exactly = 0) { bookFileDao.upsert(any()) }
    }

    // ── 同步后批量下载 ───────────────────────────────────────────

    @Test
    fun `downloadPendingBooks downloads manifest books and records failures`() = runTest {
        val pending = BookEntity(
            id = "p1", title = "待下载", format = "txt",
            content_status = "missing", updated_at = "2026-01-01T00:00:00Z",
        )
        val broken = BookEntity(
            id = "p2", title = "坏下载", format = "txt",
            content_status = "missing", updated_at = "2026-01-01T00:00:00Z",
        )
        every { bookDao.observeAllActive() } returns flowOf(listOf(pending, broken))
        coEvery { api.manifest() } returns emptyManifest().copy(
            bookFiles = listOf(
                SyncContract.BookFileManifest("p1", "p1.txt", "txt", null, 4, 0),
                SyncContract.BookFileManifest("p2", "p2.txt", "txt", null, 4, 0),
            ),
        )
        coEvery { api.getBookFile("p1") } returns Response.success(
            "abcd".toByteArray().toResponseBody("application/octet-stream".toMediaType()),
        )
        coEvery { api.getBookFile("p2") } returns Response.error(
            500, "boom".toResponseBody("text/plain".toMediaType()),
        )

        val result = repository.downloadPendingBooks()

        assertEquals(1, result.success)
        assertEquals(1, result.failed)
        val failed = result.failedItems.single()
        assertEquals("book_file", failed.type)
        assertEquals("p2", failed.id)
        assertEquals("坏下载", failed.title)
        assertTrue(failed.reason.contains("HTTP 500"))
        assertTrue(File(tempFolder.root, "books/p1/content.txt").exists())
        // 失败的书被标记 failed
        coVerify { bookDao.upsert(withArg { assertEquals("p2", it.id); assertEquals("failed", it.content_status) }) }
    }

    @Test
    fun `downloadPendingBooks skips books already available or with local files`() = runTest {
        val available = BookEntity(
            id = "a1", title = "已就绪", format = "txt",
            content_status = "available", updated_at = "2026-01-01T00:00:00Z",
        )
        val localFile = BookEntity(
            id = "l1", title = "本地文件", format = "txt",
            content_status = "missing", local_uri = "file:///local.txt",
            updated_at = "2026-01-01T00:00:00Z",
        )
        every { bookDao.observeAllActive() } returns flowOf(listOf(available, localFile))
        coEvery { api.manifest() } returns emptyManifest().copy(
            bookFiles = listOf(
                SyncContract.BookFileManifest("a1", "a1.txt", "txt", null, 1, 0),
                SyncContract.BookFileManifest("l1", "l1.txt", "txt", null, 1, 0),
            ),
        )

        val result = repository.downloadPendingBooks()

        assertEquals(0, result.success)
        assertEquals(0, result.failed)
        assertNull(result.failedItems.firstOrNull())
        coVerify(exactly = 0) { api.getBookFile(any()) }
    }
}
