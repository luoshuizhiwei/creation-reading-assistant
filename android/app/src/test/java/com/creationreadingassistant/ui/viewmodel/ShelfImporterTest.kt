package com.creationreadingassistant.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.EpubRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import io.mockk.unmockkStatic
import java.io.ByteArrayInputStream

/**
 * ShelfImporter 导入管线状态机测试。
 *
 * 覆盖：任务队列进度（成功/重复/跳过/失败）、批次内去重、书架重复检测、
 * 安全停止、防重入、重试失败项、示例书导入、修复缺失正文的关键分支。
 * （SAF 文件夹扫描依赖真实文件提供器，留仪器测试覆盖。）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShelfImporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val io = Dispatchers.Unconfined

    private lateinit var resolver: ContentResolver
    private lateinit var context: Context
    private lateinit var bookDao: BookDao
    private lateinit var bookContentDao: BookContentDao
    private lateinit var bookFileDao: BookFileDao
    private lateinit var repository: BookRepository
    private lateinit var epubRepository: EpubRepository
    private lateinit var historyStore: ImportHistoryStore
    private lateinit var shelfBooks: MutableStateFlow<List<BookEntity>>
    private lateinit var importer: ShelfImporter

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns uriMock("file://cached-book")
        every { Uri.parse(any()) } returns uriMock("content://books/retry.txt", "retry.txt")
        resolver = mockk()
        context = mockk {
            every { contentResolver } returns resolver
            every { filesDir } returns tempFolder.root
        }
        bookDao = mockk()
        bookContentDao = mockk()
        bookFileDao = mockk()
        repository = mockk()
        epubRepository = mockk()
        historyStore = mockk {
            every { entries } returns MutableStateFlow(emptyList())
        }
        shelfBooks = MutableStateFlow(emptyList())
        importer = ShelfImporter(
            context = context,
            repository = repository,
            bookDao = bookDao,
            bookContentDao = bookContentDao,
            bookFileDao = bookFileDao,
            epubRepository = epubRepository,
            importHistoryStore = historyStore,
            ioDispatcher = io,
            booksProvider = { shelfBooks.value },
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun book(
        id: String,
        localUri: String? = null,
        format: String = "txt",
        fileName: String? = null,
        size: Int = 0,
        title: String = "书$id",
    ): BookEntity = BookEntity(
        id = id,
        title = title,
        format = format,
        original_file_name = fileName,
        size = size,
        local_uri = localUri,
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun uriMock(toStringValue: String, lastPath: String? = null): Uri {
        val u = mockk<Uri>()
        every { u.toString() } returns toStringValue
        if (lastPath != null) every { u.lastPathSegment } returns lastPath
        return u
    }

    private fun uri(name: String): Uri {
        val u = mockk<Uri>()
        every { u.lastPathSegment } returns name
        every { u.toString() } returns "content://books/$name"
        return u
    }

    private fun cursor(displayName: String, size: Long): Cursor = mockk {
        every { getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { getColumnIndex(OpenableColumns.SIZE) } returns 1
        every { moveToFirst() } returns true
        every { getString(0) } returns displayName
        every { getLong(1) } returns size
        every { close() } returns Unit
    }

    /** 让 TXT 导入走通的最小 stub 集。 */
    private fun stubTxtContent(name: String, content: String) {
        val size = content.toByteArray().size
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor(name, size.toLong())
        every { resolver.openInputStream(any()) } returns ByteArrayInputStream(content.toByteArray())
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { bookDao.upsert(any()) } returns Unit
        coEvery { bookContentDao.upsert(any()) } returns Unit
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
    }

    @Test
    fun `txt import succeeds and updates batch task and history`() = runTest {
        stubTxtContent("a.txt", "第一章 你好\n这是正文")

        importer.importFiles(listOf(uri("a.txt")))

        val batch = importer.importBatch.value
        assertEquals("batch=$batch tasks=${importer.importTasks.value}", 1, batch.total)
        assertEquals(1, batch.completed)
        val task = importer.importTasks.value.single()
        assertEquals("task=$task batch=$batch", 1, batch.succeeded)
        assertEquals(0, batch.failed)
        assertFalse(batch.isRunning)
        assertFalse(batch.id.isBlank())

        assertEquals("a.txt", task.fileName)
        assertEquals("done", task.status)
        assertEquals("导入完成", task.phase)

        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.status == "success" && it.fileName == "a.txt" })
        }
    }

    @Test
    fun `epub import creates book entry and book file record`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("a.epub", 100L)
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { epubRepository.openEpub(any()) } returns EpubBook(
            id = "epub1",
            title = "样章",
            author = "作者",
            chapters = emptyList(),
            localUri = "content://books/a.epub",
            cachedEpubPath = "unused",
        )
        coEvery { bookDao.getById(any()) } returns null
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit

        importer.importFiles(listOf(uri("a.epub")))

        assertEquals(1, importer.importBatch.value.succeeded)
        coVerify(exactly = 1) {
            bookFileDao.upsert(match { it.format == "epub" && it.book_id == "epub1" })
        }
        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.status == "success" && it.format == "epub" })
        }
    }

    @Test
    fun `reimport of existing epub updates metadata instead of duplicating`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("a.epub", 100L)
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { epubRepository.openEpub(any()) } returns EpubBook(
            id = "epub1",
            title = "样章",
            author = null,
            chapters = emptyList(),
            localUri = "content://books/a.epub",
            cachedEpubPath = "unused",
        )
        coEvery { bookDao.getById(any()) } returns book("epub1", format = "epub", fileName = "old.epub")
        coEvery { bookDao.update(any()) } returns 1
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit

        importer.importFiles(listOf(uri("a.epub")))

        assertEquals(1, importer.importBatch.value.succeeded)
        coVerify(exactly = 1) { bookDao.update(match { it.original_file_name == "a.epub" }) }
    }

    @Test
    fun `book already on shelf is reported as duplicate`() = runTest {
        stubTxtContent("a.txt", "x")
        shelfBooks.value = listOf(
            book("b1", localUri = "content://books/a.txt", format = "txt", fileName = "a.txt", size = 1)
        )

        importer.importFiles(listOf(uri("a.txt")))

        val batch = importer.importBatch.value
        assertEquals(1, batch.completed)
        assertEquals(1, batch.duplicates)
        assertEquals(0, batch.succeeded)
        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.isDuplicate && it.status == "duplicate" })
        }
    }

    @Test
    fun `same file twice in one batch dedupes by fingerprint`() = runTest {
        stubTxtContent("a.txt", "x")
        val uriA = uri("a.txt")
        val uriB = uriMock("content://files/a.txt", "a.txt")

        importer.importFiles(listOf(uriA, uriB))

        val batch = importer.importBatch.value
        assertEquals(2, batch.total)
        assertEquals(1, batch.succeeded)
        assertEquals(1, batch.duplicates)
    }

    @Test
    fun `unsupported format is skipped with history record`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("a.docx", 10L)
        coEvery { historyStore.addEntry(any()) } returns Unit

        importer.importFiles(listOf(uri("a.docx")))

        val batch = importer.importBatch.value
        assertEquals(1, batch.skipped)
        assertEquals(1, batch.completed)
        assertEquals(0, batch.failed)
        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.status == "skipped" && it.error == "不支持的格式" })
        }
    }

    @Test
    fun `import failure marks batch failed and task error`() = runTest {
        stubTxtContent("a.txt", "x")
        every { resolver.openInputStream(any()) } returns null

        importer.importFiles(listOf(uri("a.txt")))

        val batch = importer.importBatch.value
        assertEquals(1, batch.failed)
        assertEquals(1, batch.completed)
        assertTrue(batch.failures.single().reason.contains("无法打开文件"))

        val task = importer.importTasks.value.single()
        assertEquals("error", task.status)
        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.status == "failed" && it.error == "无法打开文件" })
        }
    }

    @Test
    fun `stop after current halts remaining queue`() = runTest {
        stubTxtContent("a.txt", "x")
        coEvery { bookDao.upsert(any()) } coAnswers {
            importer.requestStopImport()
            Unit
        }

        importer.importFiles(listOf(uri("a.txt"), uri("b.txt")))

        val batch = importer.importBatch.value
        assertTrue(batch.stopRequested)
        assertEquals(2, batch.total)
        assertEquals(1, batch.completed)
        assertEquals(1, batch.succeeded)
        assertEquals(1, batch.stopped)
        assertFalse(batch.isRunning)
    }

    @Test
    fun `second batch while running is ignored`() = runTest {
        stubTxtContent("a.txt", "x")
        var secondAttempted = false
        coEvery { bookDao.upsert(any()) } coAnswers {
            if (!secondAttempted) {
                secondAttempted = true
                importer.importFiles(listOf(uri("b.txt")))
            }
            Unit
        }

        importer.importFiles(listOf(uri("a.txt")))

        assertTrue(secondAttempted)
        assertEquals(1, importer.importBatch.value.total)
        assertEquals(1, importer.importBatch.value.completed)
    }

    @Test
    fun `empty file list does not start a batch`() = runTest {
        importer.importFiles(emptyList())

        val batch = importer.importBatch.value
        assertEquals("", batch.id)
        assertFalse(batch.isRunning)
        assertTrue(importer.importTasks.value.isEmpty())
    }

    @Test
    fun `retry re-imports failed uris under retry label`() = runTest {
        stubTxtContent("a.txt", "x")
        every { resolver.openInputStream(any()) } returns null
        importer.importFiles(listOf(uri("a.txt")))
        assertEquals(1, importer.importBatch.value.failed)
        val firstBatchId = importer.importBatch.value.id

        every { resolver.openInputStream(any()) } returns ByteArrayInputStream("x".toByteArray())
        importer.retryFailedImports()

        val batch = importer.importBatch.value
        assertNotEquals(firstBatchId, batch.id)
        assertEquals("重试失败项", batch.sourceLabel)
        assertEquals(1, batch.succeeded)
    }

    @Test
    fun `sample import adds book and records history`() = runTest {
        coEvery { repository.addSampleBook("导入·新卷") } returns book("s1", format = "txt", title = "导入·新卷")
        coEvery { historyStore.addEntry(any()) } returns Unit

        importer.runSampleImport()

        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.status == "success" && it.bookTitle == "导入·新卷" })
        }
    }

    @Test
    fun `repair returns message when book is missing`() = runTest {
        coEvery { bookDao.getById(any()) } returns null

        val msg = importer.repairFile("missing", uri("a.txt"))

        assertEquals("书籍记录不存在", msg)
    }

    @Test
    fun `repair rejects format mismatch`() = runTest {
        coEvery { bookDao.getById(any()) } returns book("b1", format = "epub", fileName = "a.epub")
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", 10L)
        coEvery { historyStore.addEntry(any()) } returns Unit

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue(msg.contains("修复失败"))
        assertTrue(msg.contains("格式不一致"))
    }

    @Test
    fun `repair succeeds for matching txt replacement`() = runTest {
        stubTxtContent("b.txt", "替换正文")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt")
        coEvery { bookDao.upsert(any()) } returns Unit
        coEvery { bookDao.runInTransaction(any()) } returns Unit

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertEquals("《书b1》正文已修复", msg)
        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.status == "success" })
        }
    }
}
