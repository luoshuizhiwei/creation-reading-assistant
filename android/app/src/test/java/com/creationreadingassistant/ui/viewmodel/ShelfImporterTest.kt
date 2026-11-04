package com.creationreadingassistant.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceAvailability
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinator
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.library.LibraryRootStore
import com.creationreadingassistant.feature.library.LibrarySourceIndex
import com.creationreadingassistant.feature.library.LibrarySourceRef
import com.creationreadingassistant.feature.library.ShelfImporter
import com.creationreadingassistant.feature.library.SourceFingerprint
import com.creationreadingassistant.feature.library.SourceRefreshResult
import com.creationreadingassistant.feature.reader.EpubRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import io.mockk.unmockkStatic
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

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

    /** ZIP 本地文件头 `PK\x03\x04`，用于让 EPUB 用例通过格式真源校验。 */
    private val epubZipMagic = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    private lateinit var resolver: ContentResolver
    private lateinit var context: Context
    private lateinit var bookDao: BookDao
    private lateinit var bookContentDao: BookContentDao
    private lateinit var bookFileDao: BookFileDao
    private lateinit var repository: BookRepository
    private lateinit var epubRepository: EpubRepository
    private lateinit var historyStore: ImportHistoryStore
    private lateinit var sourceIndex: LibrarySourceIndex
    private lateinit var rootStore: LibraryRootStore
    private lateinit var shelfBooks: MutableStateFlow<List<BookEntity>>
    private lateinit var indexRefreshCoordinator: SearchIndexBookRefreshCoordinator
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
        bookDao = mockk {
            coEvery { getById(any()) } returns null
        }
        bookContentDao = mockk()
        bookFileDao = mockk {
            coEvery { getByBook(any()) } returns null
        }
        repository = mockk()
        epubRepository = mockk()
        historyStore = mockk {
            every { entries } returns MutableStateFlow(emptyList())
        }
        sourceIndex = mockk(relaxed = true)
        rootStore = mockk {
            coEvery { loadRoot() } returns null
        }
        shelfBooks = MutableStateFlow(emptyList())
        // 协调器 mock：默认 request() 不阻塞、不抛出（relaxed = false；必须显式 stub 或 verify）
        indexRefreshCoordinator = mockk {
            every { request(any()) } returns Unit
        }
        importer = ShelfImporter(
            context = context,
            repository = repository,
            bookDao = bookDao,
            bookContentDao = bookContentDao,
            bookFileDao = bookFileDao,
            epubRepository = epubRepository,
            importHistoryStore = historyStore,
            sourceIndex = sourceIndex,
            rootStore = rootStore,
            ioDispatcher = io,
            indexRefreshCoordinator = indexRefreshCoordinator,
        )
        importer.booksProvider = { shelfBooks.value }
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

    private fun uriMock(toStringValue: String, lastPath: String? = toStringValue.substringAfterLast('/')): Uri {
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
        every { getColumnIndex("last_modified") } returns 2
        // _size 列必须显式声明「非 null」：cursor 是**严格** mock，未 stub 的调用会抛
        // MockKException 而不是返回默认值。来源基线推进用例会读 size（M-FEAT-02）。
        every { isNull(1) } returns false
        every { isNull(2) } returns true
        every { moveToFirst() } returns true
        every { getString(0) } returns displayName
        every { getLong(1) } returns size
        every { close() } returns Unit
    }

    /** 让 TXT 导入走通的最小 stub 集。 */
    private fun stubTxtContent(name: String, content: String) {
        val size = content.toByteArray().size
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor(name, size.toLong())
        // answers 每次返回全新流：导入前的内容哈希预读会消费一次流，导入读取需要新实例（真实 SAF 行为）
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(content.toByteArray()) }
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { bookDao.upsert(any()) } returns Unit
        coEvery { bookContentDao.upsert(any()) } returns Unit
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
    }

    /** 构造一条来源引用；默认「无配置根 + 可定位 document id」即单文件选择场景。 */
    private fun sourceRef(
        bookId: String = "b1",
        documentId: String? = "primary:Books/b.txt",
        sizeBytes: Long = 10L,
        lastModifiedMillis: Long? = null,
    ): LibrarySourceRef = LibrarySourceRef(
        bookId = bookId,
        rootId = null,
        providerAuthority = "com.android.externalstorage.documents",
        documentId = documentId,
        displayName = "b.txt",
        format = "txt",
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        contentHash = null,
        candidateFingerprint = null,
        lastSeenAtMillis = 0L,
        availability = LibrarySourceAvailability.AVAILABLE,
    )

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
    fun `completed import summary is consumed once without clearing batch result`() = runTest {
        stubTxtContent("a.txt", "第一章 你好\n这是正文")
        importer.importFiles(listOf(uri("a.txt")))

        val completed = importer.importBatch.value
        assertTrue(completed.hasResult)
        assertFalse(completed.summaryNotified)

        importer.markImportBatchSummaryNotified("another-batch")
        assertFalse(importer.importBatch.value.summaryNotified)

        importer.markImportBatchSummaryNotified(completed.id)
        val notified = importer.importBatch.value
        assertTrue(notified.summaryNotified)
        assertTrue(notified.hasResult)
        assertEquals(completed.id, notified.id)
        assertEquals(completed.succeeded, notified.succeeded)
    }

    @Test
    fun `epub import creates book entry and book file record`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("a.epub", 100L)
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(epubZipMagic) }
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
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(epubZipMagic) }
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
    fun `epub named file with plain text content is rejected without parsing`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("fake.epub", 100L)
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream("这不是 ZIP 内容".toByteArray()) }
        coEvery { historyStore.addEntry(any()) } returns Unit

        importer.importFiles(listOf(uri("fake.epub")))

        val batch = importer.importBatch.value
        assertEquals(1, batch.failed)
        assertEquals(0, batch.succeeded)
        assertTrue(batch.failures.single().reason.contains("EPUB"))
        // 绝不进入 EPUB 解析
        coVerify(exactly = 0) { epubRepository.openEpub(any()) }
        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.status == "failed" && it.format == "epub" })
        }
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

        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream("x".toByteArray()) }
        importer.retryFailedImports()

        val batch = importer.importBatch.value
        assertNotEquals(firstBatchId, batch.id)
        assertEquals("重试失败项", batch.sourceLabel)
        assertEquals(1, batch.succeeded)
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
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("替换正文"))
        coEvery { bookDao.upsert(any()) } returns Unit
        coEvery { bookDao.runInTransaction(any()) } returns Unit

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertEquals("《书b1》正文已修复", msg)
        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.status == "success" })
        }
    }

    // ===== TXT 重新选文件：回滚必须区分阶段（旧副本未动 / 已备份并替换 / 原本无副本） =====

    /** 在 `filesDir/books/<id>/` 下预置一份「旧正文副本」，返回该文件。 */
    private fun seedExistingInternalCopy(bookId: String, fileName: String, content: String): File =
        File(File(tempFolder.root, "books/$bookId").also { it.mkdirs() }, fileName)
            .apply { writeBytes(content.toByteArray()) }

    /**
     * 复制 staging 阶段失败：**旧副本根本没被动过**，此时也不存在 backup。
     *
     * 旧实现 catch 里无条件 `internalFile.delete()`，会把用户**仅存**的正文删掉且无法还原。
     * 本用例是该缺陷的定向回归：修复前必红。
     */
    @Test
    fun `txt repair keeps existing copy when staging copy fails`() = runTest {
        val bookId = "b1"
        val fileName = "a.txt"
        val internalFile = seedExistingInternalCopy(bookId, fileName, "旧版正文，绝不能被删除")
        val oldBytes = internalFile.readBytes()

        stubTxtContent(fileName, "新正文")
        coEvery { bookDao.getById(any()) } returns book(bookId, format = "txt", fileName = fileName)
        coEvery { historyStore.addEntry(any()) } returns Unit
        // 复制阶段就失败：读取来源流时抛 IOException（staging 只有半截或为空）
        every { resolver.openInputStream(any()) } throws IOException("模拟复制失败")

        val msg = importer.repairFile(bookId, uri(fileName))

        assertTrue("必须报修复失败，msg=$msg", msg.contains("修复失败"))
        assertTrue("旧副本必须仍然存在", internalFile.exists())
        assertArrayEquals("旧副本必须逐字节保持原样", oldBytes, internalFile.readBytes())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        assertFalse("不得残留 backup", File(internalFile.parentFile, "$fileName.backup").exists())
        assertFalse("不得残留 staging", File(internalFile.parentFile, "$fileName.repairing").exists())
    }

    /** 旧副本已挪进 backup、新副本已落位后，数据库写入失败 ⇒ 必须逐字节还原旧副本。 */
    @Test
    fun `txt repair restores previous copy when book record write fails`() = runTest {
        val bookId = "b1"
        val fileName = "a.txt"
        val internalFile = seedExistingInternalCopy(bookId, fileName, "旧版正文，必须被还原")
        val oldBytes = internalFile.readBytes()

        stubTxtContent(fileName, "新版正文内容")
        coEvery { bookDao.getById(any()) } returns book(bookId, format = "txt", fileName = fileName)
        coEvery { historyStore.addEntry(any()) } returns Unit
        coEvery { bookDao.runInTransaction(any()) } throws RuntimeException("模拟数据库写失败")

        val msg = importer.repairFile(bookId, uri(fileName))

        assertTrue("必须报修复失败，msg=$msg", msg.contains("修复失败"))
        assertTrue("旧副本必须依然存在", internalFile.exists())
        assertArrayEquals("旧副本必须逐字节还原，不得残留新文件字节", oldBytes, internalFile.readBytes())
        assertFalse("不得残留 backup", File(internalFile.parentFile, "$fileName.backup").exists())
        assertFalse("不得残留 staging", File(internalFile.parentFile, "$fileName.repairing").exists())
    }

    /** 正常成功：新副本落位、哈希为新内容 MD5、临时文件清理干净。 */
    @Test
    fun `txt repair replaces copy and writes new content hash on success`() = runTest {
        val bookId = "b1"
        val fileName = "a.txt"
        val internalFile = seedExistingInternalCopy(bookId, fileName, "旧版正文")
        val newContent = "全新正文内容 V2"

        stubTxtContent(fileName, newContent)
        coEvery { bookDao.getById(any()) } returns book(bookId, format = "txt", fileName = fileName)
            .copy(content_hash = md5Hex(newContent))
        stubRunInTransactionExecuting()
        coEvery { bookContentDao.upsert(any()) } returns Unit
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
        val captured = mutableListOf<BookEntity>()
        coEvery { bookDao.upsert(capture(captured)) } returns Unit

        val msg = importer.repairFile(bookId, uri(fileName))

        assertEquals("《书b1》正文已修复", msg)
        assertArrayEquals("内部副本必须换为新内容", newContent.toByteArray(), internalFile.readBytes())
        val saved = captured.firstOrNull { it.id == bookId }
        assertTrue("必须写入书籍记录，captured=$captured", saved != null)
        assertEquals(
            "books.content_hash 必须为新副本 MD5",
            SourceFingerprint.md5Hex(newContent.toByteArray()),
            saved?.content_hash,
        )
        assertFalse("成功后不得残留 backup", File(internalFile.parentFile, "$fileName.backup").exists())
        assertFalse("成功后不得残留 staging", File(internalFile.parentFile, "$fileName.repairing").exists())
    }

    /** 原本没有本地副本：替换失败后只清理本次落位的新副本，不留半截状态。 */
    @Test
    fun `txt repair removes promoted copy when there was no previous copy and write fails`() = runTest {
        val bookId = "b1"
        val fileName = "a.txt"
        val internalDir = File(tempFolder.root, "books/$bookId").also { it.mkdirs() }
        val internalFile = File(internalDir, fileName)

        stubTxtContent(fileName, "首次落位的正文")
        coEvery { bookDao.getById(any()) } returns book(bookId, format = "txt", fileName = fileName)
        coEvery { historyStore.addEntry(any()) } returns Unit
        coEvery { bookDao.runInTransaction(any()) } throws RuntimeException("模拟数据库写失败")

        val msg = importer.repairFile(bookId, uri(fileName))

        assertTrue("必须报修复失败，msg=$msg", msg.contains("修复失败"))
        assertFalse("原本无副本时失败后不得留下半截副本", internalFile.exists())
        assertFalse("不得残留 backup", File(internalDir, "$fileName.backup").exists())
        assertFalse("不得残留 staging", File(internalDir, "$fileName.repairing").exists())
    }

    // ===== 崩溃残留守卫（第一道）：同名 backup 已存在时必须停止并保留现场 =====

    /**
     * target 缺失 + backup 存在：**backup 可能是上次崩溃后唯一完好的正文**。
     *
     * 旧实现在函数开头无条件 `backup.delete()`，会把这份仅存的正文永久销毁。
     * 本用例是该缺陷的定向回归：修复前必红。
     */
    @Test
    fun `txt repair stops and keeps residue when backup exists and target is missing`() = runTest {
        val bookId = "b1"
        val fileName = "a.txt"
        val internalDir = File(tempFolder.root, "books/$bookId").also { it.mkdirs() }
        val backup = File(internalDir, "$fileName.backup")
        backup.writeBytes("上次崩溃后仅存的旧正文".toByteArray())
        val backupBytes = backup.readBytes()

        stubTxtContent(fileName, "本次新正文")
        coEvery { bookDao.getById(any()) } returns book(bookId, format = "txt", fileName = fileName)
            .copy(content_hash = md5Hex("本次新正文"))
        coEvery { historyStore.addEntry(any()) } returns Unit

        val msg = importer.repairFile(bookId, uri(fileName))

        assertTrue("必须报修复失败，msg=$msg", msg.contains("修复失败"))
        // 提示语必须对**本现场（target 缺失）**也真实：点出残留、说明已停止、说明重试不会自动恢复。
        // 关键：不得**无条件**断言「原有文件已保留」—— 本现场根本没有这样一份文件，
        // 那样写会让用户误以为旧正文还在原处。故提示必须是条件式（「若仍存在…」）。
        assertTrue("提示必须点出未完成的替换残留，msg=$msg", msg.contains("未完成的替换残留"))
        assertTrue("提示必须说明本次替换已停止，msg=$msg", msg.contains("本次替换已停止"))
        assertTrue("提示必须说明重试不会自动恢复，msg=$msg", msg.contains("重复重试不会自动恢复"))
        assertTrue("提示必须说明残留备份已保留，msg=$msg", msg.contains("残留备份已保留"))
        assertTrue(
            "target 缺失时，对旧正文的表述必须是条件式（不得无条件断言其已保留），msg=$msg",
            msg.contains("若仍存在"),
        )
        assertFalse(
            "target 缺失时不得断言「原有文件…已保留」（本现场没有该文件），msg=$msg",
            msg.contains("原有文件与备份均已保留"),
        )
        assertFalse("不得暗示「确认后再重试」这类自助恢复路径，msg=$msg", msg.contains("再重试"))
        assertFalse("不得引导用户删除书籍，msg=$msg", msg.contains("删除"))
        assertTrue("backup 必须仍然存在", backup.exists())
        assertArrayEquals("backup 必须逐字节保持原样", backupBytes, backup.readBytes())
        assertFalse("target 不得被本次操作创建", File(internalDir, fileName).exists())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
    }

    /** target 与 backup 均存在：可能是「过期备份」也可能是「未提交的新副本」，无法判定 ⇒ 一并保留。 */
    @Test
    fun `txt repair stops and keeps both files when target and backup both exist`() = runTest {
        val bookId = "b1"
        val fileName = "a.txt"
        val internalFile = seedExistingInternalCopy(bookId, fileName, "现有正文")
        val targetBytes = internalFile.readBytes()
        val backup = File(internalFile.parentFile, "$fileName.backup")
        backup.writeBytes("上次遗留的副本".toByteArray())
        val backupBytes = backup.readBytes()

        stubTxtContent(fileName, "本次新正文")
        coEvery { bookDao.getById(any()) } returns book(bookId, format = "txt", fileName = fileName)
            .copy(content_hash = md5Hex("本次新正文"))
        coEvery { historyStore.addEntry(any()) } returns Unit

        val msg = importer.repairFile(bookId, uri(fileName))

        assertTrue("必须报修复失败，msg=$msg", msg.contains("修复失败"))
        // 提示语必须对**本现场（target 存在）**也真实：同一个字符串要同时覆盖两种现场，
        // 故对旧正文的表述必须是条件式；且不得因 target 存在就断言「旧正文一定在」。
        assertTrue("提示必须点出未完成的替换残留，msg=$msg", msg.contains("未完成的替换残留"))
        assertTrue("提示必须说明本次替换已停止，msg=$msg", msg.contains("本次替换已停止"))
        assertTrue("提示必须说明重试不会自动恢复，msg=$msg", msg.contains("重复重试不会自动恢复"))
        assertTrue("提示必须说明残留备份已保留，msg=$msg", msg.contains("残留备份已保留"))
        assertTrue(
            "对旧正文的表述必须是条件式（同一文案要覆盖 target 存在/缺失两种现场），msg=$msg",
            msg.contains("若仍存在"),
        )
        assertFalse("不得暗示「确认后再重试」这类自助恢复路径，msg=$msg", msg.contains("再重试"))
        assertFalse("不得引导用户删除书籍，msg=$msg", msg.contains("删除"))
        assertArrayEquals("target 必须逐字节未被改写", targetBytes, internalFile.readBytes())
        assertArrayEquals("backup 必须逐字节未被改写", backupBytes, backup.readBytes())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
    }

    /**
     * 更换文件名：DB 仍指向旧名，本次 safeName 是新名 —— 此时「本次 target 缺失」是**正常状态**，
     * 但**同名** backup 已存在 ⇒ 仍必须停止并保留，不得删除或自动恢复。
     */
    @Test
    fun `txt repair stops when a same-name residue exists under a newly chosen file name`() = runTest {
        val bookId = "b1"
        val newName = "b.txt"
        val internalDir = File(tempFolder.root, "books/$bookId").also { it.mkdirs() }
        // DB 指向的是旧名文件（未被本次流程引用）
        val oldFile = File(internalDir, "a.txt").apply { writeBytes("旧名正文".toByteArray()) }
        val oldBytes = oldFile.readBytes()
        val backup = File(internalDir, "$newName.backup")
        backup.writeBytes("与新名同名的历史残留".toByteArray())
        val backupBytes = backup.readBytes()

        stubTxtContent(newName, "本次新正文")
        coEvery { bookDao.getById(any()) } returns book(bookId, format = "txt", fileName = newName)
            .copy(local_content_path = oldFile.absolutePath)
        coEvery { historyStore.addEntry(any()) } returns Unit

        val msg = importer.repairFile(bookId, uri(newName))

        assertTrue("必须报修复失败，msg=$msg", msg.contains("修复失败"))
        assertArrayEquals("同残留不得被删除或改写", backupBytes, backup.readBytes())
        assertArrayEquals("DB 指向的旧名文件必须逐字节不变", oldBytes, oldFile.readBytes())
        assertFalse("本次新名 target 不得被创建", File(internalDir, newName).exists())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
    }

    /** 无残留时行为不变：正常替换成功，且成功后 backup 被清理（不留残留给下一次）。 */
    @Test
    fun `txt repair succeeds normally when no residue exists`() = runTest {
        val bookId = "b1"
        val fileName = "a.txt"
        val internalFile = seedExistingInternalCopy(bookId, fileName, "旧版正文")
        val newContent = "全新正文内容"

        stubTxtContent(fileName, newContent)
        coEvery { bookDao.getById(any()) } returns book(bookId, format = "txt", fileName = fileName)
            .copy(content_hash = md5Hex(newContent))
        stubRunInTransactionExecuting()
        coEvery { bookContentDao.upsert(any()) } returns Unit
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
        coEvery { bookDao.upsert(any()) } returns Unit

        val msg = importer.repairFile(bookId, uri(fileName))

        assertEquals("《书b1》正文已修复", msg)
        assertArrayEquals("内部副本必须换为新内容", newContent.toByteArray(), internalFile.readBytes())
        assertFalse(
            "成功后不得留下 backup 残留（否则下一次修复会被守卫挡住）",
            File(internalFile.parentFile, "$fileName.backup").exists(),
        )
        assertFalse("不得残留 staging", File(internalFile.parentFile, "$fileName.repairing").exists())
    }

    // ===== computeFileHash：策略性 null 与故障性异常必须分开 =====

    /**
     * `>32 MiB` 返回 `null` 是**既定策略**；而 ≤32 MiB 的空文件或读取失败必须**抛出**，
     * 不能以 `null` 冒充策略值写进 `books.content_hash` / `library_source_refs.content_hash`。
     */
    @Test
    fun `computeFileHash returns null only for oversized files and throws on real failures`() {
        // 1. 大文件：策略性 null
        val oversized = mockk<File>()
        every { oversized.length() } returns (33L * 1024 * 1024)
        assertNull("超过 32 MiB 必须按策略返回 null", importer.computeFileHash(oversized))

        // 2. 合法小文件：真实 MD5
        val valid = File(tempFolder.newFolder(), "valid.txt").apply { writeBytes("正文".toByteArray()) }
        assertEquals(
            "合法小文件必须算出正确 MD5",
            SourceFingerprint.md5Hex("正文".toByteArray()),
            importer.computeFileHash(valid),
        )

        // 3. 0 字节：属非法状态，必须抛出而不是返回 null
        val empty = File(tempFolder.newFolder(), "empty.txt").apply { writeBytes(ByteArray(0)) }
        assertThrows("0 字节文件必须抛出 IllegalArgumentException", IllegalArgumentException::class.java) {
            importer.computeFileHash(empty)
        }

        // 4. 长度看似合法但读取失败：I/O 异常必须传播，不得降级成「大文件策略」的 null
        val missingPath = File(tempFolder.root, "missing.txt")
        val unreadable = object : File(missingPath.absolutePath) {
            override fun length(): Long = 1L
        }
        assertThrows("小文件读取失败必须抛出 IOException，不能返回 null", IOException::class.java) {
            importer.computeFileHash(unreadable)
        }
    }

    /**
     * 小文件本次**测不到**哈希时，基线的 `content_hash` 必须是「未知(null)」，
     * 绝不能沿用上一版本的哈希 —— 那等于用旧内容的指纹冒充新内容。
     *
     * 同一条记录里 `size` 已推进到本次观测值，因此「旧哈希 + 新 size」是自相矛盾的配对：
     * 该行声称描述的是当前版本，哈希却来自上一个版本。
     *
     * 故障注入：DB 指向的副本是 0 字节（`computeFileHash` 抛出）+ 来源流再次打开返回 null
     * （`contentHashOrNull` 也拿不到）⇒ 只剩「保留旧值」这一级回退，正是被本用例否证的分支。
     */
    @Test
    fun `refresh with unmeasurable small-file hash records unknown hash rather than the stale one`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            // 本次观测到的来源大小 = 12（与基线 999 不同 ⇒ 若写入则 size 必须前进）
            every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", 12L)
            var opens = 0
            every { resolver.openInputStream(any()) } answers {
                if (opens++ == 0) ByteArrayInputStream("正文".toByteArray()) else null
            }
            val brokenCopy = File(tempFolder.newFolder(), "broken.txt").apply { writeBytes(ByteArray(0)) }
            coEvery { bookDao.getById("b1") } returns book("b1", format = "txt", fileName = "b.txt")
                .copy(local_content_path = brokenCopy.absolutePath, content_hash = md5Hex("正文"))
            coEvery { bookDao.runInTransaction(any()) } returns Unit
            coEvery { bookDao.upsert(any()) } returns Unit
            coEvery { bookContentDao.upsert(any()) } returns Unit
            coEvery { bookFileDao.upsert(any()) } returns Unit
            coEvery { historyStore.addEntry(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(
                sourceRef(sizeBytes = 999L, lastModifiedMillis = 1_000L).copy(contentHash = md5Hex("正文"))
            )

            val result = importer.refreshFromSource("b1")

            assertTrue("正文替换本身应成功，result=$result", result is SourceRefreshResult.Success)
            val refs = mutableListOf<LibrarySourceRefEntity>()
            coVerify(exactly = 1) { sourceIndex.record(capture(refs)) }
            val written = refs.single()
            assertNotEquals(
                "测不到哈希时不得沿用旧哈希（那是上一个版本的指纹）",
                "old_valid_hash",
                written.content_hash,
            )
            assertNull("未知必须写成 null，而不是任何猜测值", written.content_hash)
            assertEquals("size 必须推进到本次观测值", 12L, written.size)
            assertEquals("provider 未报 last_modified ⇒ 逐字段保留旧观测值", 1_000L, written.last_modified)
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    /**
     * 「大文件按既定策略不算全量哈希」与「小文件测不到哈希」是两回事，但都**不得**沿用旧哈希。
     *
     * 这里观测到的来源大小 > 32 MiB ⇒ `content_hash` 为策略性 null，同时 size 必须照常推进；
     * 与上一条用例合起来构成「null = 未知/不适用」的完整口径（旧哈希一律不被携带）。
     */
    @Test
    fun `large source file keeps policy null hash while still advancing size`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            val newContent = "V2 正文内容"
            stubTxtContent("b.txt", newContent)
            // 覆盖来源观测：声明一个 >32 MiB 的 size（策略阈值由此判定）
            every { resolver.query(any(), any(), any(), any(), any()) } returns
                cursor("b.txt", 33L * 1024 * 1024)
            coEvery { bookDao.getById("b1") } returns book("b1", format = "txt", fileName = "b.txt")
                .copy(content_hash = md5Hex(newContent))
            coEvery { bookDao.runInTransaction(any()) } returns Unit
            coEvery { bookContentDao.upsert(any()) } returns Unit
            coEvery { bookFileDao.upsert(any()) } returns Unit
            coEvery { historyStore.addEntry(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(
                sourceRef(sizeBytes = 10L, lastModifiedMillis = 500L).copy(contentHash = "old_valid_hash")
            )

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Success)
            val refs = mutableListOf<LibrarySourceRefEntity>()
            coVerify(exactly = 1) { sourceIndex.record(capture(refs)) }
            val written = refs.single()
            assertNull("大文件按策略不落全量哈希", written.content_hash)
            assertEquals("size 仍须推进到本次观测值", 33L * 1024 * 1024, written.size)
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    // ===== 按来源一键刷新（M-BUG-03 第二阶段） =====

    // ===== 重新定位：文件替换/回滚的阶段语义（CRA-RESELECT-ROLLBACK-FIX-v1/v2/v3 已验套件，
    //      CRA-ROLLBACK-WIP-RECONCILE-v1 并入；与上方 WIP 用例互为双口径覆盖） =====
    // 缺陷：暂存复制发生在旧目标备份**之前**，catch 无条件 internalFile.delete()。
    // 复制阶段失败时旧目标仍原地未动，却会被删掉且没有 backup 可恢复。

    /** 预置旧正文文件，返回 (文件, 原始字节)。 */
    private fun seedOldTxt(bookId: String, name: String, content: String): Pair<File, ByteArray> {
        val dir = File(File(tempFolder.root, "books"), bookId).also { it.mkdirs() }
        val f = File(dir, name).also { it.writeText(content) }
        return f to f.readBytes()
    }

    @Test
    fun `reselect txt keeps old content when provider cannot open the selected file`() = runTest {
        val (oldFile, oldBytes) = seedOldTxt("b1", "b.txt", "旧正文-必须保留")
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", 10L)
        every { resolver.openInputStream(any()) } throws IOException("注入：无法打开所选文件")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt")
        coEvery { historyStore.addEntry(any()) } returns Unit

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("必须报修复失败：$msg", msg.contains("修复失败"))
        assertTrue("旧正文文件必须仍然存在", oldFile.isFile)
        assertArrayEquals("尚未触碰旧目标时失败不得删除它", oldBytes, oldFile.readBytes())
    }

    @Test
    fun `reselect txt keeps old content when staging copy fails midway`() = runTest {
        val (oldFile, oldBytes) = seedOldTxt("b1", "b.txt", "旧正文-必须保留")
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", 10L)
        every { resolver.openInputStream(any()) } answers {
            var served = 0
            object : ByteArrayInputStream("新正文内容".toByteArray()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (served++ == 0) return super.read(b, off, len)
                    throw IOException("注入：复制中途失败")
                }
            }
        }
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt")
        coEvery { historyStore.addEntry(any()) } returns Unit

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("必须报修复失败：$msg", msg.contains("修复失败"))
        assertArrayEquals("复制中途失败（旧目标未动）不得删除旧正文", oldBytes, oldFile.readBytes())
    }

    @Test
    fun `reselect txt keeps old content when the backup cannot be established`() = runTest {
        val (oldFile, oldBytes) = seedOldTxt("b1", "b.txt", "旧正文-必须保留")
        // 备份路径被非空目录占用：backup.delete() 删不掉、renameTo 失败、copyTo 抛错
        val backupPath = File(oldFile.parentFile, "b.txt.backup").also { it.mkdirs() }
        File(backupPath, "occupied").writeText("x")
        stubTxtContent("b.txt", "新正文内容")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("新正文内容"))

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("必须报修复失败：$msg", msg.contains("修复失败"))
        assertArrayEquals("备份建立失败时旧正文必须原样保留", oldBytes, oldFile.readBytes())
    }

    @Test
    fun `reselect txt rolls back the file and rethrows on cancellation`() = runTest {
        val (oldFile, oldBytes) = seedOldTxt("b1", "b.txt", "旧正文-必须保留")
        stubTxtContent("b.txt", "新正文内容")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("新正文内容"))
        coEvery { bookDao.runInTransaction(any()) } throws kotlinx.coroutines.CancellationException("注入：取消")

        val thrown = runCatching { importer.repairFile("b1", uri("b.txt")) }.exceptionOrNull()

        assertTrue("取消必须原样上抛：$thrown", thrown is kotlinx.coroutines.CancellationException)
        assertArrayEquals("取消也必须回滚文件到旧字节", oldBytes, oldFile.readBytes())
    }

    @Test
    fun `reselect txt restores old content when the database write fails after the swap`() = runTest {
        val (oldFile, oldBytes) = seedOldTxt("b1", "b.txt", "旧正文-必须保留")
        stubTxtContent("b.txt", "新正文内容")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("新正文内容"))
        coEvery { bookDao.runInTransaction(any()) } throws IllegalStateException("注入：DB 写入失败")

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("必须报修复失败：$msg", msg.contains("修复失败"))
        assertArrayEquals("新目标已安装后失败必须恢复旧字节", oldBytes, oldFile.readBytes())
    }

    @Test
    fun `reselect txt replaces bytes and leaves no residue on the happy path`() = runTest {
        val (oldFile, _) = seedOldTxt("b1", "b.txt", "旧正文")
        stubTxtContent("b.txt", "新正文内容")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("新正文内容"))
        coEvery { bookDao.runInTransaction(any()) } returns Unit

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertEquals("《书b1》正文已修复", msg)
        assertEquals("正常成功路径必须换成新正文", "新正文内容", oldFile.readText())
        assertFalse("成功后不得留下 backup 残留", File(oldFile.parentFile, "b.txt.backup").exists())
        assertFalse("成功后不得留下 staging 残留", File(oldFile.parentFile, "b.txt.repairing").exists())
    }

    // ===== 既有 backup 残留保护（CRA-RESELECT-ROLLBACK-FIX-v2） =====
    // 上次中断后 target 可能缺失，`<name>.backup` 可能是唯一保存旧正文的文件。
    // 修复前入口无条件 backup.delete()：再次执行会先毁掉这份唯一的旧字节。

    @Test
    fun `reselect txt refuses replacement when a leftover backup is the only copy of the old text`() = runTest {
        val dir = File(File(tempFolder.root, "books"), "b1").also { it.mkdirs() }
        val target = File(dir, "b.txt")
        val backup = File(dir, "b.txt.backup").also { it.writeText("唯一旧正文") }
        val backupBytes = backup.readBytes()
        assertFalse("前置：target 必须缺失", target.exists())

        stubTxtContent("b.txt", "新正文内容")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("新正文内容"))

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("必须明确报失败：$msg", msg.contains("修复失败"))
        assertTrue("错误必须说明是遗留备份挡住替换：$msg", msg.contains("备份"))
        assertTrue("残留 backup 必须保留", backup.isFile)
        assertArrayEquals("backup 是唯一旧正文，字节必须原样保留", backupBytes, backup.readBytes())
        assertFalse("拒绝替换时不得写出目标", target.exists())
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
        coVerify(exactly = 0) { bookFileDao.upsert(any()) }
    }

    @Test
    fun `reselect txt refuses when both the target and a leftover backup exist`() = runTest {
        val (target, targetBytes) = seedOldTxt("b1", "b.txt", "当前正文")
        val backup = File(target.parentFile, "b.txt.backup").also { it.writeText("更旧的正文") }
        val backupBytes = backup.readBytes()

        stubTxtContent("b.txt", "新正文内容")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("新正文内容"))

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("必须明确报失败：$msg", msg.contains("修复失败"))
        assertArrayEquals("target 不得被覆盖", targetBytes, target.readBytes())
        assertTrue("残留 backup 不得被删除", backup.isFile)
        assertArrayEquals("残留 backup 字节不得被改写", backupBytes, backup.readBytes())
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    /**
     * N1—CRA-RESELECT-ROLLBACK-FIX-v3：backup 位被**非空目录**（含哨兵文件）占住时，
     * 入口守卫 `backup.isFile` 漏判 → 拒绝不发生。修复后：任何同名 backup 残留即拒绝，
     * target/backup 目录内字节不变、DB 未写、错误信息可观察（说明是备份残留）。
     */
    @Test
    fun `reselect txt refuses when the leftover backup is a non-empty directory`() = runTest {
        val (target, targetBytes) = seedOldTxt("b1", "b.txt", "当前正文-目录残留场景")
        val backupDir = File(target.parentFile, "b.txt.backup").also { it.mkdirs() }
        val sentinel = File(backupDir, "sentinel-old-bytes").also { it.writeText("目录内旧字节哨兵") }

        stubTxtContent("b.txt", "新正文内容")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("新正文内容"))

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("backup 为目录时必须明确拒绝替换：$msg", msg.contains("修复失败"))
        assertTrue("错误必须可观察（说明是备份残留挡住替换）：$msg", msg.contains("备份"))
        assertArrayEquals("target 不得被覆盖", targetBytes, target.readBytes())
        assertTrue("backup 目录必须原样保留", backupDir.isDirectory)
        assertEquals("哨兵文件必须原样保留", "目录内旧字节哨兵", sentinel.readText())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
    }

    @Test
    fun `reselect txt refuses when only the backup directory remains and no target exists`() = runTest {
        val dir = File(File(tempFolder.root, "books"), "b1").also { it.mkdirs() }
        val backupDir = File(dir, "b.txt.backup").also { it.mkdirs() }
        val sentinel = File(backupDir, "sentinel-only-copy").also { it.writeText("目录内唯一旧副本哨兵") }
        val target = File(dir, "b.txt")
        assertFalse("前置：target 必须缺失", target.exists())

        stubTxtContent("b.txt", "新正文内容")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("新正文内容"))

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("backup 目录残留时必须拒绝替换：$msg", msg.contains("修复失败"))
        assertTrue("错误必须可观察：$msg", msg.contains("备份"))
        assertFalse("拒绝替换时不得写出目标", target.exists())
        assertTrue("backup 目录必须原样保留", backupDir.isDirectory)
        assertEquals("哨兵文件必须原样保留", "目录内唯一旧副本哨兵", sentinel.readText())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
    }

    @Test
    fun `reselect txt keeps the backup and reports the original failure when the rollback itself fails`() = runTest {
        val (target, oldBytes) = seedOldTxt("b1", "b.txt", "旧正文-必须保留")
        val backup = File(target.parentFile, "b.txt.backup")
        stubTxtContent("b.txt", "新正文内容")
        coEvery { bookDao.getById(any()) } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("新正文内容"))
        coEvery { bookDao.runInTransaction(any()) } answers {
            // 注入：新正文已装上、回滚之前，目标位被非空目录占住 → 既删不掉也复位不回去
            target.delete()
            target.mkdirs()
            File(target, "blocked").writeText("x")
            throw IllegalStateException("注入：DB 写入失败")
        }

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("必须报告主异常而不是恢复异常：$msg", msg.contains("注入：DB 写入失败"))
        assertFalse("回滚失败不得提示恢复成功：$msg", msg.contains("已修复"))
        assertTrue("恢复失败必须保留 backup", backup.isFile)
        assertArrayEquals("恢复失败不得丢掉 backup 里的旧字节", oldBytes, backup.readBytes())
    }

    @Test
    fun `reselect txt installs content when no previous target exists`() = runTest {
        val dir = File(File(tempFolder.root, "books"), "b9").also { it.mkdirs() }
        val target = File(dir, "b.txt")
        assertFalse("前置：target 必须缺失", target.exists())
        stubTxtContent("b.txt", "首次正文")
        coEvery { bookDao.getById(any()) } returns book("b9", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("首次正文"))
        coEvery { bookDao.runInTransaction(any()) } returns Unit

        val msg = importer.repairFile("b9", uri("b.txt"))

        assertEquals("《书b9》正文已修复", msg)
        assertEquals("首次安装必须写入目标", "首次正文", target.readText())
        assertFalse("不得留下 backup 残留", File(dir, "b.txt.backup").exists())
        assertFalse("不得留下 staging 残留", File(dir, "b.txt.repairing").exists())
    }

    @Test
    fun `reselect txt removes only the newly written target when the first install fails`() = runTest {
        val dir = File(File(tempFolder.root, "books"), "b10").also { it.mkdirs() }
        val target = File(dir, "b.txt")
        assertFalse("前置：target 必须缺失", target.exists())
        stubTxtContent("b.txt", "首次正文")
        coEvery { bookDao.getById(any()) } returns book("b10", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("首次正文"))
        coEvery { bookDao.runInTransaction(any()) } throws IllegalStateException("注入：DB 写入失败")

        val msg = importer.repairFile("b10", uri("b.txt"))

        assertTrue("必须报修复失败：$msg", msg.contains("修复失败"))
        assertTrue("必须保留主异常文本：$msg", msg.contains("注入：DB 写入失败"))
        assertFalse("首次安装失败后不得留下目标残留", target.exists())
        assertFalse("不得留下 backup 残留", File(dir, "b.txt.backup").exists())
    }

    // ===== 按来源一键刷新（M-BUG-03 第二阶段） =====

    @Test
    fun `refresh without a source ref reports no source`() = runTest {
        coEvery { sourceIndex.snapshot() } returns emptyList()

        val result = importer.refreshFromSource("b1")

        assertEquals(SourceRefreshResult.NoSourceRef, result)
        coVerify(exactly = 0) { sourceIndex.record(any()) }
    }

    @Test
    fun `refresh without a locatable document id reports no source`() = runTest {
        coEvery { sourceIndex.snapshot() } returns listOf(sourceRef(documentId = null))

        val result = importer.refreshFromSource("b1")

        assertEquals(SourceRefreshResult.NoSourceRef, result)
        coVerify(exactly = 0) { sourceIndex.record(any()) }
    }

    @Test
    fun `refresh advances the source baseline after a successful repair`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            stubTxtContent("b.txt", "替换正文")
            // CRA-D2-RELINK-IDENTITY-FIX-v3：身份门只限定重新关联，不限定 refresh。
            // 恢复旧语义：旧哈希 ≠ 新内容时刷新仍成功，基线推进到新观测值。
            coEvery { bookDao.getById("b1") } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("旧正文，与替换正文不同"))
            coEvery { bookDao.runInTransaction(any()) } returns Unit
            // 基线停在导入时的旧观测值（1 字节）→ 检测层会持续报「可更新」。
            coEvery { sourceIndex.snapshot() } returns listOf(
                sourceRef(sizeBytes = 1L, lastModifiedMillis = 1_000L)
            )

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Success)
            // 关键不变量：成功后基线必须推进到本次观测值，否则「可更新」标签永远清不掉。
            coVerify(exactly = 1) {
                sourceIndex.record(match { it.book_id == "b1" && it.size == 12L })
            }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    @Test
    fun `refresh failure keeps the old baseline`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            // 原书是 EPUB，来源解析出的却是 TXT → 修复路径的格式一致性校验拒绝。
            every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", 10L)
            coEvery { bookDao.getById("b1") } returns book("b1", format = "epub", fileName = "a.epub")
            coEvery { historyStore.addEntry(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(sourceRef())

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Failed)
            // 失败不得写基线：写进去等于把「未刷新」谎报成「已是最新」。
            coVerify(exactly = 0) { sourceIndex.record(any()) }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    // ===== 来源引用（路线第 3 组） =====

    @Test
    fun `successful import records a source ref without fabricated root`() = runTest {
        stubTxtContent("a.txt", "第一章 你好\n这是正文")

        importer.importFiles(listOf(uri("a.txt")))

        assertEquals(1, importer.importBatch.value.succeeded)
        // JVM 环境无法解析文档 URI、也没有配置根目录：root_id/document_id 必须留空，
        // 绝不允许为了「看起来有来源」而伪造归属。
        coVerify(exactly = 1) {
            sourceIndex.record(
                match {
                    it.format == "txt" &&
                        it.display_name == "a.txt" &&
                        it.root_id == null &&
                        it.document_id == null &&
                        it.provider_authority.isEmpty() &&
                        it.availability == "available"
                }
            )
        }
    }

    @Test
    fun `duplicate import does not rewrite the source ref`() = runTest {
        stubTxtContent("a.txt", "x")
        shelfBooks.value = listOf(
            book("b1", localUri = "content://books/a.txt", format = "txt", fileName = "a.txt", size = 1)
        )

        importer.importFiles(listOf(uri("a.txt")))

        assertEquals(1, importer.importBatch.value.duplicates)
        coVerify(exactly = 0) { sourceIndex.record(any()) }
    }

    @Test
    fun `large file with live fingerprint ref is reported as duplicate`() = runTest {
        val size = 33L * 1024 * 1024
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("big.txt", size)
        every { resolver.openInputStream(any()) } answers { PatternStream(size) }
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
        val chunkMd5 = SourceFingerprint.md5Hex(ByteArray(SourceFingerprint.CHUNK_BYTES) { 0x41 })
        val fingerprint = SourceFingerprint.fromChunks(size, chunkMd5, chunkMd5)
        coEvery { sourceIndex.getByFingerprint(fingerprint) } returns listOf(
            LibrarySourceRef(
                bookId = "b1",
                rootId = null,
                providerAuthority = "com.android.externalstorage.documents",
                documentId = "primary:Elsewhere/big.txt",
                displayName = "big.txt",
                format = "txt",
                sizeBytes = size,
                lastModifiedMillis = null,
                contentHash = null,
                candidateFingerprint = fingerprint,
                lastSeenAtMillis = 0L,
                availability = LibrarySourceAvailability.AVAILABLE,
            )
        )
        shelfBooks.value = listOf(book("b1", format = "txt", fileName = "原文件名.txt", size = 1))

        importer.importFiles(listOf(uri("big.txt")))

        val batch = importer.importBatch.value
        assertEquals(1, batch.duplicates)
        assertEquals(0, batch.succeeded)
        // 重复导入不写引用，也不复制副本。
        coVerify(exactly = 0) { sourceIndex.record(any()) }
    }

    @Test
    fun `fingerprint of a removed book does not block reimport`() = runTest {
        val size = 33L * 1024 * 1024
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("big.txt", size)
        every { resolver.openInputStream(any()) } answers { PatternStream(size) }
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { bookDao.upsert(any()) } returns Unit
        coEvery { bookContentDao.upsert(any()) } returns Unit
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
        val chunkMd5 = SourceFingerprint.md5Hex(ByteArray(SourceFingerprint.CHUNK_BYTES) { 0x41 })
        val fingerprint = SourceFingerprint.fromChunks(size, chunkMd5, chunkMd5)
        coEvery { sourceIndex.getByFingerprint(fingerprint) } returns listOf(
            LibrarySourceRef(
                bookId = "removed-book",
                rootId = null,
                providerAuthority = "com.android.externalstorage.documents",
                documentId = "primary:Elsewhere/big.txt",
                displayName = "big.txt",
                format = "txt",
                sizeBytes = size,
                lastModifiedMillis = null,
                contentHash = null,
                candidateFingerprint = fingerprint,
                lastSeenAtMillis = 0L,
                availability = LibrarySourceAvailability.AVAILABLE,
            )
        )
        shelfBooks.value = emptyList()

        importer.importFiles(listOf(uri("big.txt")))

        val batch = importer.importBatch.value
        assertEquals(1, batch.succeeded)
        assertEquals(0, batch.duplicates)
    }

    // ===== S04-2：异步索引重建接缝（SearchIndexBookRefreshCoordinator） =====

    @Test
    fun `refreshFromSource success submits index refresh request exactly once with correct bookId`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            stubTxtContent("b.txt", "替换正文")
            coEvery { bookDao.getById("b1") } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("替换正文"))
            coEvery { bookDao.runInTransaction(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(sourceRef(sizeBytes = 1L, lastModifiedMillis = 1_000L))

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Success)
            // 关键不变量：成功后恰好提交一次，且传入正确的 bookId。
            verify(exactly = 1) { indexRefreshCoordinator.request("b1") }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    @Test
    fun `refreshFromSource failure does not submit index refresh request`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            // 原书是 EPUB，来源是 TXT → 修复路径格式校验拒绝
            every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", 10L)
            coEvery { bookDao.getById("b1") } returns book("b1", format = "epub", fileName = "a.epub")
            coEvery { historyStore.addEntry(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(sourceRef())

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Failed)
            // 失败不得提交索引重建请求
            verify(exactly = 0) { indexRefreshCoordinator.request(any()) }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    @Test
    fun `refreshFromSource NoSourceRef does not submit index refresh request`() = runTest {
        coEvery { sourceIndex.snapshot() } returns emptyList()

        val result = importer.refreshFromSource("b1")

        assertEquals(SourceRefreshResult.NoSourceRef, result)
        verify(exactly = 0) { indexRefreshCoordinator.request(any()) }
    }

    @Test
    fun `refreshFromSource missing documentId does not submit index refresh request`() = runTest {
        coEvery { sourceIndex.snapshot() } returns listOf(sourceRef(documentId = null))

        val result = importer.refreshFromSource("b1")

        assertEquals(SourceRefreshResult.NoSourceRef, result)
        verify(exactly = 0) { indexRefreshCoordinator.request(any()) }
    }

    @Test
    fun `refreshFromSource success result is unchanged even when coordinator request is called`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            stubTxtContent("b.txt", "替换正文")
            coEvery { bookDao.getById("b1") } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("替换正文"))
            coEvery { bookDao.runInTransaction(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(sourceRef(sizeBytes = 1L))

            val result = importer.refreshFromSource("b1")

            // 协调器调用不得改变原有 SourceRefreshResult.Success 文案和类型
            assertTrue("result 类型必须仍为 Success，result=$result", result is SourceRefreshResult.Success)
            assertTrue(
                "Success.message 必须包含书名，result=$result",
                (result as SourceRefreshResult.Success).message.contains("b1"),
            )
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    // ===== S04-3 / S04-4：来源基线重算与元数据缺失保护 =====

    @Test
    fun `refresh advances baseline with newly computed content hash instead of stale hash`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            stubTxtContent("b.txt", "全新正文内容")
            coEvery { bookDao.getById("b1") } returns book("b1", format = "txt", fileName = "b.txt").copy(content_hash = md5Hex("全新正文内容"))
            coEvery { bookDao.runInTransaction(any()) } returns Unit
            // 旧基线带有陈旧的 contentHash
            coEvery { sourceIndex.snapshot() } returns listOf(
                sourceRef(sizeBytes = 1L, lastModifiedMillis = 1_000L).copy(contentHash = "stale_hash_12345")
            )

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Success)
            val expectedHash = SourceFingerprint.md5Hex("全新正文内容".toByteArray())
            // 关键断言（S04-3）：新写入的记录包含基于新正文重新计算的 hash，而非旧的 stale_hash_12345
            coVerify(exactly = 1) {
                sourceIndex.record(match {
                    it.book_id == "b1" &&
                        it.content_hash == expectedHash &&
                        it.content_hash != "stale_hash_12345"
                })
            }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    @Test
    fun `refresh preserves old last_modified when provider returns null instead of overwriting`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            // provider 返回的 last_modified 列为 null
            val content = "正文"
            val size = content.toByteArray().size.toLong()
            every { resolver.query(any(), any(), any(), any(), any()) } returns mockk {
                every { getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
                every { getColumnIndex(OpenableColumns.SIZE) } returns 1
                every { getColumnIndex("last_modified") } returns 2
                every { isNull(1) } returns false
                every { isNull(2) } returns true // 模拟 provider 缺失 last_modified
                every { moveToFirst() } returns true
                every { getString(0) } returns "b.txt"
                every { getLong(1) } returns size
                every { close() } returns Unit
            }
            every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(content.toByteArray()) }
            every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
            coEvery { bookDao.upsert(any()) } returns Unit
            coEvery { bookContentDao.upsert(any()) } returns Unit
            coEvery { bookFileDao.upsert(any()) } returns Unit
            coEvery { historyStore.addEntry(any()) } returns Unit
            coEvery { bookDao.getById("b1") } returns book("b1", format = "txt", fileName = "b.txt")
                .copy(content_hash = md5Hex(content))
            coEvery { bookDao.runInTransaction(any()) } returns Unit
            // 旧基线存在有效时间戳 987654321L
            coEvery { sourceIndex.snapshot() } returns listOf(
                sourceRef(sizeBytes = 1L, lastModifiedMillis = 987654321L)
            )

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Success)
            // 关键断言（S04-4）：provider 返回 null 时，last_modified 必须保留旧基线，严禁被抹为 null
            coVerify(exactly = 1) {
                sourceIndex.record(match {
                    it.book_id == "b1" && it.last_modified == 987654321L
                })
            }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    @Test
    fun `refresh does not update baseline when provider is completely unreadable`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/b.txt", "b.txt")
            // 模拟 provider 完全不可读（例如 query 返回 null）
            every { resolver.query(any(), any(), any(), any(), any()) } returns null
            every { resolver.openInputStream(any()) } answers { ByteArrayInputStream("正文".toByteArray()) }
            every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
            coEvery { bookDao.upsert(any()) } returns Unit
            coEvery { bookContentDao.upsert(any()) } returns Unit
            coEvery { bookFileDao.upsert(any()) } returns Unit
            coEvery { historyStore.addEntry(any()) } returns Unit
            coEvery { bookDao.getById("b1") } returns book("b1", format = "txt", fileName = "b.txt")
                .copy(content_hash = md5Hex("正文"))
            coEvery { bookDao.runInTransaction(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(
                sourceRef(sizeBytes = 10L, lastModifiedMillis = 1000L)
            )

            val result = importer.refreshFromSource("b1")

            assertTrue("正文替换本身成功，result=$result", result is SourceRefreshResult.Success)
            // 关键断言：provider 完全不可读时，保持旧基线不写库（调用 0 次）
            coVerify(exactly = 0) { sourceIndex.record(any()) }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    // ===== S05-④：本批成功 id 接缝（供导入后「查看」定位绑定本批） =====

    /**
     * 「查看」定位必须只认**本批**成功的书。
     *
     * 旧实现让 UI 直接读 `ShelfPrefs.newBookIds.firstOrNull()` —— 那是跨批次的持久
     * 「未查看」集合，只有用户真正打开过那本书才会移除。于是第二批导入完成后点「查看」，
     * 可能滚到上一批遗留、始终没打开的书上。本接缝只记录本批的成功项。
     */
    @Test
    fun `batch succeeded ids contain exactly the newly imported books`() = runTest {
        stubTxtContent("a.txt", "第一章 你好\n这是正文")
        val upserted = mutableListOf<BookEntity>()
        coEvery { bookDao.upsert(capture(upserted)) } returns Unit

        importer.importFiles(listOf(uri("a.txt")))

        assertEquals(1, importer.importBatch.value.succeeded)
        assertEquals(
            "本批成功 id 必须与真实落库的书一致",
            upserted.map { it.id }.distinct(),
            importer.batchSuccesses.value.bookIds,
        )
        assertEquals(1, importer.batchSuccesses.value.bookIds.size)
    }

    /**
     * 批次 id 与成功 id 必须**一起**换代（复核补强）。
     *
     * UI 侧点「查看」时要按批次 id 核对，若两者分开写就可能出现「批次 id 已是新的、
     * 成功 id 还是旧的」半截状态，用户点旧提示会被带去新批次的书。
     */
    @Test
    fun `batch successes are tagged with the batch id they belong to`() = runTest {
        stubTxtContent("a.txt", "x")
        importer.importFiles(listOf(uri("a.txt")))
        val first = importer.importBatch.value
        assertEquals(first.id, importer.batchSuccesses.value.batchId)
        assertEquals(1, importer.batchSuccesses.value.bookIds.size)

        // 第二批：同一文件已在书架上 → 判为重复，本批没有新书。
        shelfBooks.value = listOf(
            book("b1", localUri = "content://books/a.txt", format = "txt", fileName = "a.txt", size = 1)
        )
        importer.importFiles(listOf(uri("a.txt")))

        val second = importer.importBatch.value
        assertNotEquals(first.id, second.id)
        // 关键：批次 id 已换代的同时，成功 id 列表必须**一起**被重置，不能残留上一批的。
        assertEquals(second.id, importer.batchSuccesses.value.batchId)
        assertTrue(
            "批次换代后成功 id 必须是空的，actual=${importer.batchSuccesses.value}",
            importer.batchSuccesses.value.bookIds.isEmpty(),
        )
    }

    /** 重复 / 跳过 / 失败都不算「本批成功」：列表长度必须与 succeeded 计数同口径。 */
    @Test
    fun `batch succeeded ids exclude duplicate entries in the same batch`() = runTest {
        stubTxtContent("a.txt", "x")
        val upserted = mutableListOf<BookEntity>()
        coEvery { bookDao.upsert(capture(upserted)) } returns Unit
        // 同一批里同一个文件出现两次：第二次按指纹判重，不应进入成功候选。
        val uriA = uri("a.txt")
        val uriB = uriMock("content://files/a.txt", "a.txt")

        importer.importFiles(listOf(uriA, uriB))

        val batch = importer.importBatch.value
        assertEquals(1, batch.succeeded)
        assertEquals(1, batch.duplicates)
        assertEquals(batch.succeeded, importer.batchSuccesses.value.bookIds.size)
        assertEquals(upserted.map { it.id }.distinct(), importer.batchSuccesses.value.bookIds)
    }

    /** 定长 `A` 字节流：避免在 JVM 测试里分配 33 MB 数组，同时支持 skip 语义。 */
    private class PatternStream(private val size: Long) : InputStream() {
        private var pos = 0L

        override fun read(): Int = if (pos >= size) -1 else {
            pos++
            0x41
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= size) return -1
            val n = minOf(len.toLong(), size - pos).toInt()
            java.util.Arrays.fill(b, off, off + n, 0x41)
            pos += n
            return n
        }

        override fun skip(n: Long): Long {
            val skipped = minOf(n, size - pos).coerceAtLeast(0)
            pos += skipped
            return skipped
        }
    }

    // ===== S04 书籍主记录哈希同步（books.content_hash 陈旧修复） =====

    /**
     * 让 bookDao.runInTransaction 真正执行回调块，使得内部的 upsert 等调用可被 coVerify 捕获。
     * JVM 测试中默认的 `coEvery { ... } returns Unit` 不执行回调，需要显式让它执行。
     */
    private fun stubRunInTransactionExecuting() {
        coEvery { bookDao.runInTransaction(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = args[0] as (suspend () -> Unit)
            block()
        }
    }

    @Test
    fun `txt source refresh updates books content_hash to match new file md5`() = runTest {
        // 断言：TXT 来源成功刷新后，books.content_hash 必须等于新文件 MD5
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            val newContent = "全新 TXT 正文内容，V3 版本"
            val newContentBytes = newContent.toByteArray()
            val expectedHash = SourceFingerprint.md5Hex(newContentBytes)
            stubTxtContent("b.txt", newContent)
            // CRA-D2-RELINK-IDENTITY-FIX-v3：身份门只限定重新关联，不限定 refresh。
            // 恢复旧语义：旧哈希 ≠ 新内容时刷新仍成功，books.content_hash 推进到新文件 MD5。
            val oldHash = md5Hex("旧内容，与全新 TXT 正文内容不同")
            coEvery { bookDao.getById("b1") } returns BookEntity(
                id = "b1",
                title = "测试书",
                format = "txt",
                original_file_name = "b.txt",
                size = 100,
                content_hash = oldHash,
                updated_at = "2026-01-01T00:00:00Z",
            )
            // 让 runInTransaction 真正执行回调，使 bookDao.upsert 参数可被 coVerify 捕获
            stubRunInTransactionExecuting()
            coEvery { bookDao.upsert(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(
                sourceRef(sizeBytes = 100L, lastModifiedMillis = 1_000L).copy(contentHash = oldHash)
            )

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Success)
            // 核心断言：books.content_hash 必须为新文件 MD5（旧哈希已被推进，不再保留）。
            assertNotEquals("新哈希必须不同于旧哈希", oldHash, expectedHash)
            coVerify(exactly = 1) {
                bookDao.upsert(match { entity ->
                    entity.id == "b1" && entity.content_hash == expectedHash
                })
            }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    @Test
    fun `md source refresh updates books content_hash to match new file md5`() = runTest {
        // MD 文件走同一 reselectPlainText 路径，哈希规则与 TXT 相同
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            val newContent = "## 全新 Markdown 正文"
            val newContentBytes = newContent.toByteArray()
            val expectedHash = SourceFingerprint.md5Hex(newContentBytes)
            val size = newContentBytes.size.toLong()
            every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.md", size)
            every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(newContentBytes) }
            every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
            coEvery { historyStore.addEntry(any()) } returns Unit
            // CRA-D2-RELINK-IDENTITY-FIX-v3：身份门只限定重新关联，不限定 refresh。
            // 恢复旧语义：旧哈希 ≠ 新内容时刷新仍成功。
            val oldHash = md5Hex("旧内容，与全新 MD 正文内容不同")
            coEvery { bookDao.getById("b1") } returns BookEntity(
                id = "b1",
                title = "测试 MD 书",
                format = "md",
                original_file_name = "b.md",
                size = 100,
                content_hash = oldHash,
                updated_at = "2026-01-01T00:00:00Z",
            )
            stubRunInTransactionExecuting()
            coEvery { bookDao.upsert(any()) } returns Unit
            coEvery { bookContentDao.upsert(any()) } returns Unit
            coEvery { bookFileDao.upsert(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(
                sourceRef(bookId = "b1", sizeBytes = 100L, lastModifiedMillis = 1_000L)
                    .copy(contentHash = oldHash)
            )

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Success)
            assertNotEquals("新哈希必须不同于旧哈希", oldHash, expectedHash)
            coVerify(exactly = 1) {
                bookDao.upsert(match { entity ->
                    entity.id == "b1" && entity.content_hash == expectedHash
                })
            }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    @Test
    fun `books content_hash is consistent with source ref content_hash after small file refresh`() = runTest {
        // 断言：小文件刷新后，books.content_hash 与 library_source_refs.content_hash 必须一致
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            val newContent = "小文件一致性测试内容"
            val newContentBytes = newContent.toByteArray()
            val expectedHash = SourceFingerprint.md5Hex(newContentBytes)
            stubTxtContent("b.txt", newContent)
            // CRA-D2-RELINK-IDENTITY-FIX-v3：身份门只限定重新关联，不限定 refresh。
            // 恢复旧语义：旧哈希 ≠ 新内容时刷新仍成功，且 books/source_refs 哈希一致推进。
            val oldHash = md5Hex("旧内容，与小文件一致性测试内容不同")
            coEvery { bookDao.getById("b1") } returns BookEntity(
                id = "b1",
                title = "一致性测试书",
                format = "txt",
                original_file_name = "b.txt",
                size = 10,
                content_hash = oldHash,
                updated_at = "2026-01-01T00:00:00Z",
            )
            stubRunInTransactionExecuting()
            coEvery { bookDao.upsert(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(
                sourceRef(sizeBytes = 10L, lastModifiedMillis = 500L).copy(contentHash = oldHash)
            )

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Success)
            assertNotEquals("新哈希必须不同于旧哈希", oldHash, expectedHash)
            val capturedBooks = mutableListOf<BookEntity>()
            coVerify(exactly = 1) { bookDao.upsert(capture(capturedBooks)) }
            val bookHash = capturedBooks.firstOrNull()?.content_hash
            val capturedRefs = mutableListOf<LibrarySourceRefEntity>()
            coVerify(exactly = 1) { sourceIndex.record(capture(capturedRefs)) }
            val refHash = capturedRefs.firstOrNull()?.content_hash
            assertEquals("books.content_hash 必须为新文件 MD5", expectedHash, bookHash)
            assertEquals("books.content_hash 与 library_source_refs.content_hash 必须一致", bookHash, refHash)
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    @Test
    fun `refresh failure does not update books content_hash`() = runTest {
        // 刷新失败时，books.content_hash 不得被修改
        mockkStatic(DocumentsContract::class)
        try {
            every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
            // 原书为 EPUB，来源为 TXT → 格式不一致，修复路径拒绝
            every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", 10L)
            coEvery { bookDao.getById("b1") } returns BookEntity(
                id = "b1",
                title = "失败保护书",
                format = "epub",
                original_file_name = "b.epub",
                size = 10,
                content_hash = "original_hash",
                updated_at = "2026-01-01T00:00:00Z",
            )
            coEvery { historyStore.addEntry(any()) } returns Unit
            coEvery { sourceIndex.snapshot() } returns listOf(sourceRef())

            val result = importer.refreshFromSource("b1")

            assertTrue("result=$result", result is SourceRefreshResult.Failed)
            // 失败时 bookDao.upsert 不得调用
            coVerify(exactly = 0) { bookDao.upsert(any()) }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    // ===== CRA-D2-RELINK-IDENTITY-FIX-v2：重新关联的内容身份门（staging 字节校验） =====
    //
    // 合同（任务书 §四/§五）：以实际准备换入的 staging 字节为身份校验对象，在旧正文
    // 备份/替换之前完成；同内容放行（bookId/用户记录保留）；内容明确不同拒绝；
    // UNKNOWN（任一侧哈希不可得）明确拒绝，绝不静默放行；校验/读取失败保留旧资产；
    // 同 URI 两次读取不同内容不能绕过门禁；普通 Duplicate 与软删除语义不变。

    /** 独立于 stubTxtContent 的内容 stub：显式控制 query 返回的 size（哈希门依赖它）。 */
    private fun stubRepairContent(name: String, content: String) {
        val size = content.toByteArray().size
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor(name, size.toLong())
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(content.toByteArray()) }
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { bookDao.upsert(any()) } returns Unit
        coEvery { bookContentDao.upsert(any()) } returns Unit
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
    }

    private fun md5Hex(content: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** 让 bookDao.runInTransaction 真正执行回调块，使内部 upsert 可被 coVerify 捕获。 */
    private fun stubRunInTransactionExecutingV2() {
        coEvery { bookDao.runInTransaction(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = args[0] as (suspend () -> Unit)
            block()
        }
    }

    @Test
    fun `repair accepts same-content staging bytes and keeps bookId and metadata`() = runTest {
        // 同内容：staging 字节哈希 == existing.content_hash → 换入，bookId/元数据不变。
        // runInTransaction 真执行回调，逐字段验证 upsert 收到的实体。
        val originalHash = md5Hex("原书正文")
        coEvery { bookDao.getById(any()) } returns book(
            "b1", format = "txt", fileName = "b.txt",
        ).copy(content_hash = originalHash)
        stubRunInTransactionExecutingV2()
        stubRepairContent("b.txt", "原书正文")

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertEquals("《书b1》正文已修复", msg)
        coVerify(exactly = 1) {
            bookDao.upsert(
                match {
                    // bookId 与用户元数据逐字段保留；content_status 恢复 available
                    it.id == "b1" && it.title == "书b1" && it.format == "txt" &&
                        it.content_hash == originalHash && it.content_status == "available"
                },
            )
        }
        // staging 已被清理，不留残留
        assertFalse(
            "staging 必须清理",
            File(tempFolder.root, "books/b1/reselect-staging.txt").exists(),
        )
    }

    @Test
    fun `repair rejects different-content file and leaves original state untouched`() = runTest {
        // 内容明确不同（同格式同扩展名）：拒绝，旧正文/DB 状态不变，无 staging 残留。
        coEvery { bookDao.getById(any()) } returns book(
            "b1", format = "txt", fileName = "b.txt",
        ).copy(content_hash = md5Hex("旧正文"))
        val internalFile = seedExistingInternalCopy("b1", "b.txt", "旧正文")
        val oldBytes = internalFile.readBytes()
        stubRepairContent("b.txt", "完全不同的新内容")

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("必须给出明确失败原因，msg=$msg", msg.contains("修复失败"))
        assertTrue("失败原因必须说明内容不一致，msg=$msg", msg.contains("内容与原书不一致"))
        assertTrue("旧正文文件必须仍然存在", internalFile.exists())
        assertArrayEquals("旧正文必须逐字节保持原样", oldBytes, internalFile.readBytes())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
        coVerify(exactly = 0) { bookFileDao.upsert(any()) }
        assertFalse("不得残留 staging", File(internalFile.parentFile, "b.txt.repairing").exists())
        assertFalse("不得残留 backup", File(internalFile.parentFile, "b.txt.backup").exists())
        assertFalse("身份门的 staging 必须清理", File(internalFile.parentFile, "reselect-staging.txt").exists())
    }

    @Test
    fun `repair rejects UNKNOWN identity when existing hash is missing`() = runTest {
        // UNKNOWN：existing.content_hash 为 null（历史数据/大文件策略）→ 明确拒绝，不放行。
        coEvery { bookDao.getById(any()) } returns book(
            "b1", format = "txt", fileName = "b.txt",
        ) // content_hash 默认 null
        val internalFile = seedExistingInternalCopy("b1", "b.txt", "旧正文")
        val oldBytes = internalFile.readBytes()
        stubRepairContent("b.txt", "新正文内容")

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("UNKNOWN 必须拒绝，msg=$msg", msg.contains("修复失败"))
        assertTrue("拒绝原因必须说明无法确认身份，msg=$msg", msg.contains("无法确认"))
        assertTrue("旧正文文件必须仍然存在", internalFile.exists())
        assertArrayEquals("旧正文必须逐字节保持原样", oldBytes, internalFile.readBytes())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
    }

    @Test
    fun `repair rejects when staging copy fails and keeps old assets`() = runTest {
        // 读取/暂存失败：不进入任何替换，旧正文与 DB 原样。
        coEvery { bookDao.getById(any()) } returns book(
            "b1", format = "txt", fileName = "b.txt",
        ).copy(content_hash = md5Hex("旧正文"))
        val internalFile = seedExistingInternalCopy("b1", "b.txt", "旧正文")
        val oldBytes = internalFile.readBytes()
        // 身份门 staging 阶段就失败：来源流抛 IOException
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", 4L)
        every { resolver.openInputStream(any()) } throws java.io.IOException("模拟读取失败")
        coEvery { historyStore.addEntry(any()) } returns Unit

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("读取失败必须反馈，msg=$msg", msg.contains("修复失败"))
        assertTrue("旧正文文件必须仍然存在", internalFile.exists())
        assertArrayEquals("旧正文必须逐字节保持原样", oldBytes, internalFile.readBytes())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
        assertFalse("不得残留身份门 staging", File(internalFile.parentFile, "reselect-staging.txt").exists())
        assertFalse("不得残留替换 staging", File(internalFile.parentFile, "b.txt.repairing").exists())
    }

    @Test
    fun `same-uri returning different bytes cannot bypass the identity gate`() = runTest {
        // 同 URI 脱节防护：身份门读取的是第一次复制进 staging 的字节；换入复用同一
        // staging 文件。provider 两次打开返回不同内容时，换入字节仍与校验字节一致。
        val firstContent = "原书正文"
        val secondContent = "中途被换掉的内容"
        coEvery { bookDao.getById(any()) } returns book(
            "b1", format = "txt", fileName = "b.txt",
        ).copy(content_hash = md5Hex(firstContent))
        val size = firstContent.toByteArray().size
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", size.toLong())
        val streams = java.util.concurrent.atomic.AtomicInteger(0)
        every { resolver.openInputStream(any()) } answers {
            if (streams.getAndIncrement() == 0) {
                ByteArrayInputStream(firstContent.toByteArray())
            } else {
                ByteArrayInputStream(secondContent.toByteArray())
            }
        }
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { bookDao.upsert(any()) } returns Unit
        coEvery { bookContentDao.upsert(any()) } returns Unit
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
        stubRunInTransactionExecutingV2()

        val msg = importer.repairFile("b1", uri("b.txt"))

        // 第一次流（身份门 staging）= 原书正文 → SAME 放行；换入复用 staging 字节，
        // 第二次 URI 读取（不同内容）根本没有发生——替换不再打开原 URI。
        assertEquals("《书b1》正文已修复", msg)
        val installed = File(tempFolder.root, "books/b1/b.txt")
        assertTrue("换入副本必须存在", installed.exists())
        assertArrayEquals("换入字节必须等于已校验的 staging 字节", firstContent.toByteArray(), installed.readBytes())
        assertNotEquals("不得换入第二次读取的内容", secondContent, installed.readText())
        // 来源流只被打开一次（身份门 staging），替换阶段零次
        assertEquals(1, streams.get())
    }

    @Test
    fun `duplicate import semantics unchanged for same content`() = runTest {
        // 普通导入 Duplicate 语义不变：同 content_hash 命中去重 → 不重复导入、不重新关联。
        val sameHash = md5Hex("重复内容")
        shelfBooks.value = listOf(
            book("existing-1", format = "txt", fileName = "dup.txt").copy(content_hash = sameHash),
        )
        stubTxtContent("dup.txt", "重复内容")

        importer.importFiles(listOf(uri("dup.txt")))

        val batch = importer.importBatch.value
        assertEquals(1, batch.completed)
        assertEquals(1, batch.duplicates)
        assertEquals(0, batch.succeeded)
        coVerify(exactly = 1) {
            historyStore.addEntry(match { it.isDuplicate && it.status == "duplicate" })
        }
        // 去重路径不得触发任何重新关联式 DB 写入
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
    }

    @Test
    fun `repair does not revive records unseen by the active-book dao filter`() = runTest {
        // 软删除语义：BookDao.getById（deleted_at IS NULL，源码 :59）查不到的记录一律
        // 「书籍记录不存在」。JVM 无 Room 基建，真实 SQL 过滤由 DAO 源码合同保证，
        // 此处锁定 repairFileTyped 对 getById==null 的行为：不写任何 DB、不复活、明确反馈。
        // 真实 Room 记录级验证留给最小设备/仪器后续合同（见报告未覆盖项）。
        coEvery { bookDao.getById(any()) } returns null

        val msg = importer.repairFile("deleted-1", uri("b.txt"))

        assertEquals("书籍记录不存在", msg)
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
    }

    // ===== CRA-D2-RELINK-IDENTITY-FIX-v3：三缺陷定向回归 =====

    /**
     * 缺陷1：staging 文件名 `reselect-staging.<format>` 与真实正文同名时，
     * v2 实现先 delete 再写（校验前删真实正文），finally 又删除安装后的正文。
     *
     * v3 修复：staging 移入自有子目录 `books/<id>/.reselect-staging/<uuid>.<format>`，
     * 不与任何真实正文同名，finally 只删自有 staging。
     */
    @Test
    fun `repair preserves real content file named reselect-staging txt`() = runTest {
        val realContent = "真实正文，绝不能被删除"
        val realFile = seedExistingInternalCopy("b1", "reselect-staging.txt", realContent)
        val realBytes = realFile.readBytes()
        coEvery { bookDao.getById(any()) } returns book(
            "b1", format = "txt", fileName = "reselect-staging.txt",
        ).copy(content_hash = md5Hex(realContent))
        stubRepairContent("reselect-staging.txt", realContent)
        stubRunInTransactionExecutingV2()

        val msg = importer.repairFile("b1", uri("reselect-staging.txt"))

        assertEquals("SAME 内容必须修复成功，msg=$msg", "《书b1》正文已修复", msg)
        assertTrue("真实正文文件必须仍然存在", realFile.exists())
        assertArrayEquals("真实正文必须逐字节保持原样", realBytes, realFile.readBytes())
        val stagingDir = File(tempFolder.root, "books/b1/.reselect-staging")
        assertFalse(
            "staging 子目录必须清理",
            stagingDir.exists() && stagingDir.listFiles()?.isNotEmpty() == true,
        )
    }

    /**
     * 缺陷3：staging 复制写部分字节后抛异常 —— v2 helper 无自身失败清理，
     * 半截 staging 文件残留（调用方 finally 从未进入）。
     *
     * v3 修复：helper 在自身失败路径清理本操作实际创建的文件。
     */
    @Test
    fun `partial staging copy failure cleans up this operation residue`() = runTest {
        val oldContent = "旧正文"
        val internalFile = seedExistingInternalCopy("b1", "b.txt", oldContent)
        val oldBytes = internalFile.readBytes()
        coEvery { bookDao.getById(any()) } returns book(
            "b1", format = "txt", fileName = "b.txt",
        ).copy(content_hash = md5Hex(oldContent))
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.txt", 100L)
        every { resolver.openInputStream(any()) } answers { PartialThenFailStream(10) }
        coEvery { historyStore.addEntry(any()) } returns Unit

        val msg = importer.repairFile("b1", uri("b.txt"))

        assertTrue("部分复制失败必须反馈，msg=$msg", msg.contains("修复失败"))
        assertFalse(
            "不得残留半截 staging 文件（v2 路径）",
            File(tempFolder.root, "books/b1/reselect-staging.txt").exists(),
        )
        val stagingDir = File(tempFolder.root, "books/b1/.reselect-staging")
        assertFalse(
            "不得残留 staging 子目录内容（v3 路径）",
            stagingDir.exists() && stagingDir.listFiles()?.isNotEmpty() == true,
        )
        assertTrue("旧正文必须仍然存在", internalFile.exists())
        assertArrayEquals("旧正文必须逐字节保持原样", oldBytes, internalFile.readBytes())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookDao.runInTransaction(any()) }
    }

    /**
     * EPUB 接缝：staging 移到自有子目录后，replaceStoredEpub 收到的 file URI 仍指向
     * 已校验的 staging 字节，EPUB 换入路径不受影响。
     *
     * v2 REPORT 声称"既有测试已经证明 EPUB 成功 repair"，但实际无对应用例 —— 本测试补齐。
     */
    @Test
    fun `epub repair SAME content succeeds via replaceStoredEpub seam`() = runTest {
        val epubBytes = epubZipMagic + "EPUB-CONTENT".toByteArray()
        val epubHash = SourceFingerprint.md5Hex(epubBytes)
        coEvery { bookDao.getById(any()) } returns book(
            "b1", format = "epub", fileName = "b.epub",
        ).copy(content_hash = epubHash)
        seedExistingInternalCopy("b1", "b.epub", "旧EPUB正文")
        val size = epubBytes.size
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor("b.epub", size.toLong())
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(epubBytes) }
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { bookDao.upsert(any()) } returns Unit
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
        val cachedPath = File(tempFolder.root, "books/epub/b1.epub")
            .also { it.parentFile?.mkdirs() }.absolutePath
        coEvery { epubRepository.replaceStoredEpub(any(), any(), any()) } returns EpubBook(
            id = "b1", title = "书b1", author = null, chapters = emptyList(),
            localUri = "file://cached-book", cachedEpubPath = cachedPath,
        )

        val msg = importer.repairFile("b1", uri("b.epub"))

        assertEquals("EPUB SAME 内容必须修复成功，msg=$msg", "《书b1》正文已修复", msg)
        coVerify(exactly = 1) { epubRepository.replaceStoredEpub(any(), any(), any()) }
        val stagingDir = File(tempFolder.root, "books/b1/.reselect-staging")
        assertFalse(
            "不得残留 staging 子目录内容",
            stagingDir.exists() && stagingDir.listFiles()?.isNotEmpty() == true,
        )
    }

    // ===== CRA-D2-RELINK-IDENTITY-FIX-v4：同书并发互斥与原子创建 =====
    //
    // 缺口：repairFile 与 refreshFromSource 对同书共用 .repairing/.backup 路径，
    // 无同书互斥；stageSelectedBytesForRepair 用 outputStream() 创建-or-截断，
    // 非原子排他创建。以下 5 条用例补能抓住缺口的红，修到绿。

    /** 并发测试公共 stub：A=repairFile(SAME) 用 content://books/b.txt，B=refresh 用 content://source/doc。 */
    private fun stubConcurrentRepairAndRefresh(
        originalContent: String,
        refreshContent: String,
    ) {
        seedExistingInternalCopy("b1", "b.txt", originalContent)
        coEvery { bookDao.getById("b1") } returns book("b1", format = "txt", fileName = "b.txt")
            .copy(content_hash = md5Hex(originalContent))
        coEvery { sourceIndex.snapshot() } returns listOf(sourceRef())
        every { DocumentsContract.buildDocumentUri(any(), any()) } returns uriMock("content://source/doc")
        every { resolver.query(any(), any(), any(), any(), any()) } answers {
            val u = args[0] as Uri
            if (u.toString() == "content://source/doc")
                cursor("b.txt", refreshContent.toByteArray().size.toLong())
            else
                cursor("b.txt", originalContent.toByteArray().size.toLong())
        }
        every { resolver.openInputStream(any()) } answers {
            val u = args[0] as Uri
            if (u.toString() == "content://source/doc")
                ByteArrayInputStream(refreshContent.toByteArray())
            else
                ByteArrayInputStream(originalContent.toByteArray())
        }
        every { resolver.takePersistableUriPermission(any(), any()) } returns Unit
        coEvery { bookDao.upsert(any()) } returns Unit
        coEvery { bookContentDao.upsert(any()) } returns Unit
        coEvery { bookFileDao.upsert(any()) } returns Unit
        coEvery { historyStore.addEntry(any()) } returns Unit
        coEvery { sourceIndex.record(any()) } returns Unit
    }

    /**
     * 同书 SAME relink(A) 与 changed-content refresh(B) 并发：B 必须等待 A 完整结束。
     *
     * v3 缺口：A 在 runInTransaction 挂起时已创建 .backup；B 无锁直接进入 reselectPlainText，
     * 撞 backup 残留守卫而失败。v4 用同书 Mutex 串行化：B 等 A 完整结束后才进入，
     * backup 已被 A 清理，B 成功换入自己的刷新字节。
     */
    @Test
    fun `concurrent same-book repair and refresh are serialized`() = runTest(UnconfinedTestDispatcher()) {
        mockkStatic(DocumentsContract::class)
        try {
            val originalContent = "原书正文"
            val refreshContent = "刷新后正文"
            stubConcurrentRepairAndRefresh(originalContent, refreshContent)

            val aBarrier = CompletableDeferred<Unit>()
            val txCount = AtomicInteger(0)
            coEvery { bookDao.runInTransaction(any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val block = args[0] as (suspend () -> Unit)
                if (txCount.incrementAndGet() == 1) { aBarrier.await(); block() } else block()
            }

            val a = async { importer.repairFile("b1", uri("b.txt")) }
            // A 已跑到 runInTransaction 并在 aBarrier 挂起；.backup 已存在
            val b = async { importer.refreshFromSource("b1") }
            // 无锁：B 直接进入 reselectPlainText，撞 backup 守卫失败
            // 有锁：B 在 Mutex 上等待，不进入 reselectPlainText

            aBarrier.complete(Unit)
            val aMsg = a.await()
            val bResult = b.await()

            assertEquals("A 应成功: $aMsg", "《书b1》正文已修复", aMsg)
            assertTrue("B 应成功（等待 A 完成后刷新）: $bResult", bResult is SourceRefreshResult.Success)
            val finalFile = File(tempFolder.root, "books/b1/b.txt")
            assertEquals("最终文件应为 B 的刷新内容", refreshContent, finalFile.readText())
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    /**
     * A 失败回滚不影响 B：A 的 runInTransaction 抛 DB 异常，回滚恢复旧正文；
     * B 等 A 释放锁后进入，backup 已被 A 的回滚消费/清理，B 成功换入刷新字节。
     */
    @Test
    fun `concurrent first failure rollback does not affect second`() = runTest(UnconfinedTestDispatcher()) {
        mockkStatic(DocumentsContract::class)
        try {
            val originalContent = "原书正文"
            val refreshContent = "刷新后正文"
            stubConcurrentRepairAndRefresh(originalContent, refreshContent)

            val aBarrier = CompletableDeferred<Unit>()
            val txCount = AtomicInteger(0)
            coEvery { bookDao.runInTransaction(any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val block = args[0] as (suspend () -> Unit)
                if (txCount.incrementAndGet() == 1) { aBarrier.await(); throw RuntimeException("DB 写入失败") }
                else block()
            }

            val a = async { importer.repairFile("b1", uri("b.txt")) }
            val b = async { importer.refreshFromSource("b1") }

            aBarrier.complete(Unit)
            val aMsg = a.await()
            val bResult = b.await()

            assertTrue("A 应失败: $aMsg", aMsg.contains("修复失败"))
            assertTrue("B 应成功（A 回滚不影响 B）: $bResult", bResult is SourceRefreshResult.Success)
            val finalFile = File(tempFolder.root, "books/b1/b.txt")
            assertEquals("最终文件应为 B 的刷新内容", refreshContent, finalFile.readText())
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    /**
     * 原子排他创建：staging 路径已存在另一操作的文件时，不得截断/覆盖。
     *
     * v3 缺口：stagedFile.outputStream() 是 create-or-truncate，静默覆盖预存在文件。
     * v4 用 createNewFile() —— 返回 false 即抛，预存在文件逐字节保留。
     */
    @Test
    fun `staging file creation is atomic and preserves unknown-owned file`() = runTest {
        val bookId = "b1"
        val fileName = "b.txt"
        val originalContent = "原书正文"
        seedExistingInternalCopy(bookId, fileName, originalContent)
        coEvery { bookDao.getById(any()) } returns book(bookId, format = "txt", fileName = fileName)
            .copy(content_hash = md5Hex(originalContent))
        stubRepairContent(fileName, originalContent)
        stubRunInTransactionExecutingV2()

        val fixedUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returns fixedUuid

        val stagingDir = File(tempFolder.root, "books/$bookId/.reselect-staging").also { it.mkdirs() }
        val stagedFile = File(stagingDir, "$fixedUuid.txt")
        val preExistingBytes = "OTHER_OPERATION_BYTES".toByteArray()
        stagedFile.writeBytes(preExistingBytes)

        val msg = importer.repairFile(bookId, uri(fileName))

        assertTrue("必须报修复失败: $msg", msg.contains("修复失败"))
        assertArrayEquals("不得截断/覆盖其他操作的暂存文件", preExistingBytes, stagedFile.readBytes())

        unmockkStatic(UUID::class)
    }

    /**
     * 取消不遗留锁：A 持锁在 barrier 挂起时被 cancel，锁必须在 finally 释放，
     * 后续 B 能正常进入并完成。
     */
    @Test
    fun `cancellation releases lock and allows subsequent operation`() = runTest(UnconfinedTestDispatcher()) {
        mockkStatic(DocumentsContract::class)
        try {
            val originalContent = "原书正文"
            val refreshContent = "刷新后正文"
            stubConcurrentRepairAndRefresh(originalContent, refreshContent)

            val aBarrier = CompletableDeferred<Unit>()
            val txCount = AtomicInteger(0)
            coEvery { bookDao.runInTransaction(any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val block = args[0] as (suspend () -> Unit)
                if (txCount.incrementAndGet() == 1) { aBarrier.await(); block() } else block()
            }

            val aJob = launch { importer.repairFile("b1", uri("b.txt")) }
            // A 持锁并在 aBarrier 挂起
            aJob.cancel()
            aJob.join()
            // A 已取消，锁应在 withLock finally 释放

            val bResult = importer.refreshFromSource("b1")
            assertTrue("B 应成功（取消不遗留锁）: $bResult", bResult is SourceRefreshResult.Success)
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    /** 先写指定字节数再抛 IOException，模拟复制中途失败（非 open 立即失败）。 */
    private class PartialThenFailStream(private val bytesBeforeFail: Int) : InputStream() {
        private var written = 0
        override fun read(): Int {
            if (written >= bytesBeforeFail) throw IOException("模拟复制中途失败")
            written++
            return 0x41
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (written >= bytesBeforeFail) throw IOException("模拟复制中途失败")
            val n = minOf(len, bytesBeforeFail - written)
            java.util.Arrays.fill(b, off, off + n, 0x41.toByte())
            written += n
            return n
        }
    }
}
