package com.creationreadingassistant.feature.reader

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.log.AppLog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S04 EPUB 来源刷新：正文替换后的 books.content_hash 与回滚安全保护测试（Stage Q 强化版）。
 *
 * 覆盖：
 * 1. 小文件（≤32 MiB）成功替换后，books.content_hash 必须更新为新内部副本 MD5，清除旧哈希；
 * 2. 大文件（>32 MiB）成功替换后，books.content_hash 必须置为 null，不得保留旧哈希；
 * 3. 损坏/解析拒绝时快速失败，不得提前写入新哈希，旧 target 逐字节保持不变；
 * 4. 替换已落盘 target 之后的后置失败（如持久化异常），旧 target 必须完整逐字节从 backup 恢复，收尾清理完好；
 * 5. computeFileHash 语义严密性：大文件策略性返回 null，小文件异常必须抛出绝不能返回 null 伪装成大文件成功；
 * 6. **崩溃残留守卫**：操作开始前 `.backup` 已存在时（target 存在 / 缺失两种形态），
 *    必须拒绝本次替换、抛出可识别错误，且 target 与 backup 均逐字节保留、不写 DB；
 * 7. **无残留正常路径**：无残留时替换正常完成，且收尾不留 backup / staging。
 * 8. **`renameTo(backup)` 失败后的回退复制分支**（经 [FileOps] 接缝确定性注入）：
 *    · 回退复制成功 ⇒ 替换正常完成（回退分支的 happy path）；
 *    · 回退复制写出部分字节后抛错 ⇒ 旧 target 逐字节存活、残缺 backup 被清掉；
 *    · 回退复制正常返回但内容截断 ⇒ `require(backup.length() == target.length())` 必须拦下。
 * 9. **回滚恢复失败（backup→target，经同一接缝按 source 区分注入）**：
 *    · 恢复复制写出部分字节后抛错 ⇒ 原始 DAO 异常为主、恢复异常挂 suppressed、完整 backup 保留；
 *    · 恢复复制只写部分却正常返回 ⇒ 内容核对拦下、backup 不被删、核对失败挂 suppressed；
 *    · 恢复 rename 成功的既有行为回归（成功回滚路径不受本次改动影响）；
 *    · 恢复失败信息可从**调用方实际捕获的异常及其 cause 链**寻得（不固定对象身份、不只查注入实例），
 *      并写入 AppLog 诊断记录。
 * 10. **CRA-ROLLBACK-WIP-RECONCILE-v2**：守卫前移到 fresh-source 解析之前（残留现场不得先落
 *     `.importing`）；fresh-source 不可读时失败且不退回旧副本；建立备份与恢复复制的兜底分支
 *     改为**逐字节核对**（短写与等长错字节都拒绝），核对通过后才删 target / backup；
 *     正常路径核对通过后照常完成。
 *
 * **本片之后仍然失去的分支覆盖（不得声称已覆盖）**：
 * `require` 通过之后、`target.delete()` 之前进程被杀（无 fsync）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpubRepositoryReplaceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val ioDispatcher = Dispatchers.Unconfined
    private lateinit var resolver: ContentResolver
    private lateinit var context: Context
    private lateinit var bookDao: BookDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var repository: EpubRepository

    /**
     * 默认「直通真实文件系统」的接缝实现，等价于生产 [DefaultFileOps]。
     * 只有需要注入回退分支故障的用例才会改写它的行为。
     */
    private lateinit var fileOps: FakeFileOps

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        resolver = mockk()
        context = mockk {
            every { contentResolver } returns resolver
            every { filesDir } returns tempFolder.root
            every { cacheDir } returns tempFolder.root
        }
        bookDao = mockk {
            coEvery { update(any()) } returns 1
            coEvery { upsert(any()) } returns Unit
        }
        readingProgressDao = mockk {
            coEvery { getByBook(any()) } returns null
            coEvery { upsert(any()) } returns Unit
        }
        fileOps = FakeFileOps()
        repository = EpubRepository(
            context = context,
            bookDao = bookDao,
            readingProgressDao = readingProgressDao,
            ioDispatcher = ioDispatcher,
            fileOps = fileOps,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    /** 构造一个结构合法的最小 EPUB 文件。可附加 extraPadding 扩大文件体积。 */
    private fun createValidEpubFile(
        dest: File,
        title: String = "测试 EPUB",
        bodyText: String = "测试正文章节内容",
        extraPaddingBytes: Long = 0L,
    ): File {
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            zos.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
            // 1. mimetype (必须在首位，通常未压缩)
            val mime = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype")
            zos.putNextEntry(mimeEntry)
            zos.write(mime)
            zos.closeEntry()

            // 2. META-INF/container.xml
            val containerXml = """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent().toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(containerXml)
            zos.closeEntry()

            // 3. OEBPS/content.opf
            val opfXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:identifier id="BookId">test-epub-id</dc:identifier>
                  </metadata>
                  <manifest>
                    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                    ${if (extraPaddingBytes > 0L) """<item id="pad" href="pad.dat" media-type="application/octet-stream"/>""" else ""}
                  </manifest>
                  <spine>
                    <itemref idref="ch1"/>
                  </spine>
                </package>
            """.trimIndent().toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(opfXml)
            zos.closeEntry()

            // 4. OEBPS/ch1.xhtml
            val chapterHtml = """
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>$title</title></head>
                  <body><p>$bodyText</p></body>
                </html>
            """.trimIndent().toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write(chapterHtml)
            zos.closeEntry()

            // 5. 大文件垫片（若需要构造 >32 MiB 的合法超大 EPUB）
            if (extraPaddingBytes > 0L) {
                zos.putNextEntry(ZipEntry("OEBPS/pad.dat"))
                val buf = ByteArray(64 * 1024) { 0x42 }
                var remaining = extraPaddingBytes
                while (remaining > 0L) {
                    val toWrite = minOf(buf.size.toLong(), remaining).toInt()
                    zos.write(buf, 0, toWrite)
                    remaining -= toWrite
                }
                zos.closeEntry()
            }
        }
        return dest
    }

    /** 计算文件的真实 MD5 小写十六进制字符串。 */
    private fun md5Hex(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun mockUri(file: File, uriString: String = "content://media/test.epub"): Uri {
        val u = mockk<Uri>()
        every { u.toString() } returns uriString
        every { u.lastPathSegment } returns file.name
        every { Uri.fromFile(file) } returns u
        every { Uri.fromFile(any()) } answers {
            val f = firstArg<File>()
            val subUri = mockk<Uri>()
            every { subUri.toString() } returns "file://${f.absolutePath}"
            every { subUri.lastPathSegment } returns f.name
            subUri
        }
        every { resolver.openInputStream(u) } answers { file.inputStream() }
        return u
    }

    @Test
    fun `replaceStoredEpub updates books content_hash to match new file md5 for small files`() = runTest {
        val bookId = "epub_b1"
        val oldHash = "stale_v1_hash_99999"
        val existingBook = BookEntity(
            id = bookId,
            title = "旧测试书名",
            format = "epub",
            original_file_name = "old.epub",
            size = 1000,
            content_hash = oldHash,
            updated_at = "2026-01-01T00:00:00Z",
        )

        // 构造新来源 EPUB
        val newSourceFile = File(tempFolder.newFolder(), "new_source.epub")
        createValidEpubFile(newSourceFile, title = "新版本 EPUB", bodyText = "V2 全新章节正文内容")
        val newUri = mockUri(newSourceFile, "content://books/new_source.epub")

        val capturedBooks = mutableListOf<BookEntity>()
        coEvery { bookDao.update(capture(capturedBooks)) } returns 1

        val result = repository.replaceStoredEpub(existingBook, newUri, "new_source.epub")

        assertEquals("新书标题应解析自新文件", "新版本 EPUB", result.title)
        assertEquals("原书 ID 必须严格保持不变", bookId, result.id)

        // 验证内部副本文件已落盘
        val internalTarget = File(tempFolder.root, "books/epub/$bookId.epub")
        assertTrue("内部落盘 target 文件必须存在", internalTarget.isFile && internalTarget.length() > 0L)
        val expectedNewHash = md5Hex(internalTarget)

        // 核心断言：books.content_hash 必须更新为新内部副本的 MD5，不能保留旧哈希
        coVerify(exactly = 1) { bookDao.update(any()) }
        val updatedBook = capturedBooks.first()
        assertEquals("书籍 ID 必须不变", bookId, updatedBook.id)
        assertNotEquals("不得沿用旧文件的陈旧哈希", oldHash, updatedBook.content_hash)
        assertEquals("content_hash 必须为新落盘内部副本的 MD5", expectedNewHash, updatedBook.content_hash)
        assertEquals("local_content_path 必须指向新落盘 target", internalTarget.absolutePath, updatedBook.local_content_path)
    }

    @Test
    fun `replaceStoredEpub clears content_hash to null for large files over 32MiB`() = runTest {
        val bookId = "epub_b_large"
        val oldHash = "stale_hash_from_earlier"
        val existingBook = BookEntity(
            id = bookId,
            title = "旧超大 EPUB",
            format = "epub",
            original_file_name = "old_large.epub",
            size = 5000,
            content_hash = oldHash,
            updated_at = "2026-01-01T00:00:00Z",
        )

        // 构造大于 32 MiB 的合法 EPUB（32.5 MiB）
        val largeSourceFile = File(tempFolder.newFolder(), "large_source.epub")
        val padding = 32L * 1024 * 1024 + 512 * 1024 // 32.5 MB
        createValidEpubFile(largeSourceFile, title = "超大测试 EPUB", bodyText = "超大正文", extraPaddingBytes = padding)
        assertTrue("测试源文件体积必须超过 32 MiB", largeSourceFile.length() > 32 * 1024 * 1024L)
        val largeUri = mockUri(largeSourceFile, "content://books/large_source.epub")

        val capturedBooks = mutableListOf<BookEntity>()
        coEvery { bookDao.update(capture(capturedBooks)) } returns 1

        repository.replaceStoredEpub(existingBook, largeUri, "large_source.epub")

        // 核心断言：超大文件刷新后，content_hash 必须为 null，绝不能保留旧哈希！
        coVerify(exactly = 1) { bookDao.update(any()) }
        val updatedBook = capturedBooks.first()
        assertNotEquals("超大文件绝不得沿用旧哈希", oldHash, updatedBook.content_hash)
        assertNull("大于 32 MiB 的 EPUB content_hash 必须置为 null", updatedBook.content_hash)
    }

    @Test
    fun `replaceStoredEpub rejects corrupt file without writing book and preserves original target bytes`() = runTest {
        val bookId = "epub_b_fail"
        val oldHash = "immutable_original_hash"
        val existingBook = BookEntity(
            id = bookId,
            title = "防篡改测试书",
            format = "epub",
            original_file_name = "original.epub",
            size = 2048,
            content_hash = oldHash,
            updated_at = "2026-01-01T00:00:00Z",
        )

        // 构造一个已存在的旧合法副本
        val targetDir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val oldTarget = File(targetDir, "$bookId.epub")
        createValidEpubFile(oldTarget, title = "原版内容", bodyText = "原版不可损毁正文")
        val oldTargetBytes = oldTarget.readBytes()

        // 构造一个损坏的非合法 ZIP 文件作为新来源
        val corruptSourceFile = File(tempFolder.newFolder(), "corrupt.epub")
        corruptSourceFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)) // 损坏文件
        val corruptUri = mockUri(corruptSourceFile, "content://books/corrupt.epub")

        // 验证损坏文件必须抛出异常，且绝不误捕获 AssertionError
        var caughtCorrupt: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook, corruptUri, "corrupt.epub")
        } catch (e: Throwable) {
            caughtCorrupt = e
        }
        assertTrue("损坏文件必须抛出非 AssertionError 异常", caughtCorrupt is Exception && caughtCorrupt !is AssertionError)

        // 关键断言 1：bookDao.update / upsert 零次调用，新哈希绝不提前写入
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }

        // 关键断言 2：阅读记录绝对未被触碰
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }

        // 关键断言 3：旧副本逐字节完好无损保留（严格比较完整字节数组）
        assertTrue("原 target 文件必须依然存在", oldTarget.exists())
        assertArrayEquals("原 target 文件内容必须逐字节保持完全一致", oldTargetBytes, oldTarget.readBytes())

        // 关键断言 4：暂存与备份中间文件必须被彻底清理
        val staging = File(targetDir, "$bookId.repairing")
        val backup = File(targetDir, "$bookId.backup")
        assertTrue("staging 文件必须不存在", !staging.exists())
        assertTrue("backup 文件必须不存在", !backup.exists())
    }

    @Test
    fun `replaceStoredEpub restores original target bytes from backup when post-swap update fails`() = runTest {
        val bookId = "epub_b_swap_fail"
        val oldHash = "old_valid_hash"
        val existingBook = BookEntity(
            id = bookId,
            title = "已有书籍",
            format = "epub",
            original_file_name = "original.epub",
            size = 1024,
            content_hash = oldHash,
            updated_at = "2026-01-01T00:00:00Z",
        )

        // 1. 初始化已存在的旧内部副本 target（旧内容）
        val targetDir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val target = File(targetDir, "$bookId.epub")
        createValidEpubFile(target, title = "旧版目标内容", bodyText = "旧版重要数据必须被恢复")
        val oldTargetBytes = target.readBytes()

        // 2. 构造合法的新来源 EPUB（新内容）
        val newSourceFile = File(tempFolder.newFolder(), "valid_new.epub")
        createValidEpubFile(newSourceFile, title = "新版目标内容", bodyText = "新版正文数据")
        val newUri = mockUri(newSourceFile, "content://books/valid_new.epub")

        // 3. 模拟在 target 已经被替换为新文件之后，在持久化阶段（bookDao.update）抛出异常
        val simulatedDbException = RuntimeException("模拟数据库写失败")
        coEvery { bookDao.update(any()) } throws simulatedDbException

        // 4. 调用 replaceStoredEpub 并断言异常正确向上传播（且排除 AssertionError 掩盖）
        var caughtPostSwap: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook, newUri, "valid_new.epub")
        } catch (e: Throwable) {
            caughtPostSwap = e
        }
        assertTrue("持久化失败必须向上抛出 RuntimeException", caughtPostSwap is RuntimeException && caughtPostSwap !is AssertionError)
        assertEquals("模拟数据库写失败", caughtPostSwap?.message)

        // 5. 关键断言：验证旧 target 必须完整逐字节从 backup 恢复（不能残留新文件字节）
        assertTrue("target 文件必须依然存在", target.exists())
        assertArrayEquals("target 文件字节必须逐字节恢复为旧副本内容", oldTargetBytes, target.readBytes())

        // 6. 关键断言：staging 与 backup 临时文件必须被彻底清理
        val staging = File(targetDir, "$bookId.repairing")
        val backup = File(targetDir, "$bookId.backup")
        assertTrue("staging 文件必须不存在", !staging.exists())
        assertTrue("backup 文件必须不存在", !backup.exists())

        // 7. 关键断言：阅读记录零写入，内存缓存清除
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    // ===== CRA-ROLLBACK-WIP-RECONCILE-v3：fresh 解析成功后的失败也必须清理本次 .importing =====
    // EpubParser 只清理「解析阶段」的失败；parse 成功返回后，替换流程的后续失败
    // （建立备份 / DAO 持久化 / 回滚）都发生在 EpubRepository，validatedFile
    // （<id>.importing）的清理义务随之转移到 replaceStoredEpub 全程。
    // v2 只在成功路径删除 validatedFile —— 以下两条用例在 v2 实现上为红。

    @Test
    fun `replaceStoredEpub removes the fresh landing copy when the backup cannot be established`() = runTest {
        val bookId = "epub_b3_landing_backup_fail"
        val targetDir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val target = File(targetDir, "$bookId.epub")
        createValidEpubFile(target, title = "旧版目标内容", bodyText = "旧版正文必须存活")
        val oldTargetBytes = target.readBytes()

        val newSourceFile = File(tempFolder.newFolder(), "valid_new.epub")
        createValidEpubFile(newSourceFile, title = "新版目标内容", bodyText = "新版正文数据")
        val newUri = mockUri(newSourceFile, "content://books/landing_backup_fail.epub")

        // 注入「建立备份」失败：rename 失败 + 兜底复制中断（真实写出残缺 backup 后抛错）
        fileOps.renameResult = false
        fileOps.copyBehavior = { source, dest ->
            dest.outputStream().use { it.write(source.readBytes().copyOf(source.length().toInt() / 2)) }
            throw IOException("模拟备份建立失败")
        }

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "valid_new.epub")
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue(
            "注入的 IOException 必须向上传播，caught=${caught?.javaClass?.name}: ${caught?.message}",
            caught is IOException && caught !is AssertionError && caught.message == "模拟备份建立失败",
        )
        assertTrue("必须真的进入过回退复制分支", fileOps.copyCalls >= 1)

        // 失败后现场：旧 target 逐字节存活、残缺 backup 与 staging 清掉
        assertTrue("旧 target 必须依然存在", target.exists())
        assertArrayEquals("旧 target 必须逐字节完好", oldTargetBytes, target.readBytes())
        assertTrue("残缺 backup 必须被清除", !File(targetDir, "$bookId.backup").exists())
        assertTrue("staging 必须被清除", !File(targetDir, "$bookId.repairing").exists())

        // 本任务的关键断言：fresh 解析成功后落下的来源临时副本（<派生id>.importing，
        // 派生自 selectedUri）也必须清理 —— 按后缀全目录断言，不点名具体派生路径。
        val leftoverImporting1 = targetDir.listFiles { f -> f.name.endsWith(".importing") }.orEmpty()
        assertTrue(
            "fresh 解析落下的来源临时副本必须在备份建立失败后清理，实际残留：${leftoverImporting1.map { it.name }}",
            leftoverImporting1.isEmpty(),
        )
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    @Test
    fun `replaceStoredEpub removes the fresh landing copy when the database write fails`() = runTest {
        val bookId = "epub_b3_landing_dao_fail"
        val targetDir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val target = File(targetDir, "$bookId.epub")
        createValidEpubFile(target, title = "旧版目标内容", bodyText = "旧版正文必须被恢复")
        val oldTargetBytes = target.readBytes()

        val newSourceFile = File(tempFolder.newFolder(), "valid_new.epub")
        createValidEpubFile(newSourceFile, title = "新版目标内容", bodyText = "新版正文数据")
        val newUri = mockUri(newSourceFile, "content://books/landing_dao_fail.epub")

        coEvery { bookDao.update(any()) } throws RuntimeException("模拟数据库写失败")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "valid_new.epub")
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue(
            "持久化失败必须向上抛出 RuntimeException，caught=${caught?.javaClass?.name}",
            caught is RuntimeException && caught !is AssertionError && caught.message == "模拟数据库写失败",
        )

        assertTrue("target 必须依然存在", target.exists())
        assertArrayEquals("target 必须逐字节恢复为旧副本内容", oldTargetBytes, target.readBytes())
        assertTrue("staging 必须不存在", !File(targetDir, "$bookId.repairing").exists())
        assertTrue("backup 必须不存在", !File(targetDir, "$bookId.backup").exists())

        val leftoverImporting2 = targetDir.listFiles { f -> f.name.endsWith(".importing") }.orEmpty()
        assertTrue(
            "fresh 解析落下的来源临时副本必须在 DAO 失败后清理，实际残留：${leftoverImporting2.map { it.name }}",
            leftoverImporting2.isEmpty(),
        )
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    @Test
    fun `computeFileHash fails on invalid small file instead of returning null`() {
        // 1. 大文件（>32 MiB）按策略正常返回 null
        val largeFile = mockk<File>()
        every { largeFile.length() } returns (33L * 1024 * 1024)
        assertNull("大文件必须按策略返回 null", repository.computeFileHash(largeFile))

        // 2. 合法小文件返回有效 MD5
        val validFile = File(tempFolder.newFolder(), "valid.epub")
        createValidEpubFile(validFile, title = "小书", bodyText = "正文")
        val expectedHash = md5Hex(validFile)
        assertEquals("合法小文件必须计算出正确的 MD5", expectedHash, repository.computeFileHash(validFile))

        // 3. 0 字节文件属于非法状态，必须抛出异常，绝不能返回 null（旧代码会返回 null）
        val zeroFile = File(tempFolder.newFolder(), "zero.epub").apply { writeBytes(ByteArray(0)) }
        assertThrows("0 字节文件必须抛出 IllegalArgumentException，绝不能返回 null", IllegalArgumentException::class.java) {
            repository.computeFileHash(zeroFile)
        }

        // 4. 非空大小但无法打开的文件必须传播 I/O 异常，不能被降级成大文件策略的 null。
        //    length() 人为报告为小文件，实际路径不存在，确保覆盖的是 inputStream() 读取阶段。
        val missingPath = File(tempFolder.root, "missing.epub")
        val unreadableSmallFile = object : File(missingPath.absolutePath) {
            override fun length(): Long = 1L
        }
        assertThrows("小文件读取 I/O 异常必须向上传播，不能返回 null", IOException::class.java) {
            repository.computeFileHash(unreadableSmallFile)
        }
    }

    // ===== 前置失败（backup 尚未建立）时的回滚：旧 target 必须逐字节存活 =====

    /**
     * 构造「已存在且完好」的旧内部副本，返回 (文件, 原始字节)。
     * 这是前置失败用例的必要前提：旧实现会把它删掉且无处还原。
     */
    private fun seedExistingTarget(bookId: String): Pair<File, ByteArray> {
        val dir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val target = File(dir, "$bookId.epub")
        createValidEpubFile(target, title = "旧版内部副本", bodyText = "旧版正文，必须逐字节存活")
        return target to target.readBytes()
    }

    private fun existingBook(bookId: String, oldHash: String) = BookEntity(
        id = bookId,
        title = "已有书籍",
        format = "epub",
        original_file_name = "original.epub",
        size = 1024,
        content_hash = oldHash,
        updated_at = "2026-01-01T00:00:00Z",
    )

    /**
     * 可注入故障的 [FileOps]。默认行为与生产实现逐字同义（直通真实文件系统），
     * 只有显式改写 [renameResult] / [copyBehavior] / [restoreRenameResult] /
     * [restoreCopyBehavior] 的用例才会偏离真实语义。
     *
     * 正向（建立备份）与回滚（恢复）走同一接口方法，测试按 `source.name` 的后缀区分：
     * - 正向 rename：source 是 `<id>.epub`；回滚 rename：source 是 `<id>.backup`。
     * - 正向 copy：source 是 `<id>.epub`；回滚 copy：source 是 `<id>.backup`。
     *
     * [renameCalls] / [copyCalls] 是「确实进入了回退分支」的**正向证据**：
     * 没有它们，用例可能因为失败发生在更早的 staging 阶段而**假绿**
     * （旧断言里「backup 必须不存在」正是这种恒真断言的例子）。
     */
    private class FakeFileOps : FileOps {
        /** 建立备份阶段（source 为 `<id>.epub`）的 rename 结果。 */
        var renameResult: Boolean = true

        /** 回滚恢复阶段（source 为 `<id>.backup`）的 rename 结果；null 表示跟随 [renameResult]。 */
        var restoreRenameResult: Boolean? = null
        var renameCalls = 0
        var copyCalls = 0

        /** 建立备份阶段的复制行为；默认直通真实复制。 */
        var copyBehavior: (File, File) -> Unit = { source, dest ->
            source.copyTo(dest, overwrite = true)
        }

        /** 回滚恢复阶段的复制行为；null 表示跟随 [copyBehavior]。 */
        var restoreCopyBehavior: ((File, File) -> Unit)? = null

        private fun isRestorePhase(source: File): Boolean = source.name.endsWith(".backup")

        override fun renameTo(source: File, dest: File): Boolean {
            renameCalls++
            val shouldSucceed = if (isRestorePhase(source)) {
                restoreRenameResult ?: renameResult
            } else {
                renameResult
            }
            // 无论哪个阶段，"成功"都必须执行真实 rename —— 只返回 true 而不动文件
            // 会让所有成功回滚静默失效（本次修正前正是这个 bug 导致 4 个既有用例失败）。
            return if (shouldSucceed) source.renameTo(dest) else false
        }

        override fun copyTo(source: File, dest: File) {
            copyCalls++
            val behavior = if (isRestorePhase(source)) {
                restoreCopyBehavior ?: copyBehavior
            } else {
                copyBehavior
            }
            behavior(source, dest)
        }
    }

    // ===== 回退分支（renameTo 失败 → copyTo）：经 FileOps 接缝确定性覆盖 =====

    /**
     * **回退成功（happy path）**：`renameTo(backup)` 失败后退回 `copyTo(backup)`，复制完整通过长度
     * 校验，替换必须正常完成 —— 且旧副本经 backup 安全过渡，不留任何残留。
     *
     * 本用例同时是接缝的**行为等价性钉子**：`FakeFileOps` 默认直通真实文件系统，
     * 因此它证明「路由到接缝后，生产路径的语义没有漂移」。
     */
    @Test
    fun `replaceStoredEpub falls back to copy when rename fails and completes the swap`() = runTest {
        val bookId = "epub_rename_fallback_ok"
        val (target, oldBytes) = seedExistingTarget(bookId)

        // 注入：rename 失败 ⇒ 必须走 copyTo 回退
        fileOps.renameResult = false

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "回退新版", bodyText = "回退分支新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        val captured = mutableListOf<BookEntity>()
        coEvery { bookDao.update(capture(captured)) } returns 1

        val result = repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")

        // 正向证据：确实走到了 rename 尝试与回退复制（防假绿）
        assertTrue("必须尝试过 renameTo，实际=${fileOps.renameCalls}", fileOps.renameCalls >= 1)
        assertEquals("必须恰好走一次回退复制", 1, fileOps.copyCalls)

        // 替换正常完成
        assertEquals("原书 ID 必须保持不变", bookId, result.id)
        assertEquals("新书标题应解析自新文件", "回退新版", result.title)

        // target 已被新副本取代（旧字节必须不复存在）
        assertTrue("新内部副本必须已落盘", target.isFile && target.length() > 0L)
        assertFalse("旧内容必须已被新内容取代", oldBytes.contentEquals(target.readBytes()))

        // 收尾：backup 与 staging 都不得残留；旧副本的过渡文件必须已被消费掉
        assertTrue("成功后不得残留 backup", !File(target.parentFile, "$bookId.backup").exists())
        assertTrue("成功后不得残留 staging", !File(target.parentFile, "$bookId.repairing").exists())
        coVerify(exactly = 1) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        assertEquals("落库的必须是本次替换结果", bookId, captured.first().id)
        assertNotNull("成功后内存缓存应持有新副本", repository.cached(bookId))
    }

    /**
     * **回退复制中途抛错**：`copyTo(backup)` 写出部分字节后抛 I/O 异常。
     *
     * 这是 `require` 长度校验**之前**的失败，`backupReady` 仍为 false ⇒ 回滚走
     * `if (backup.exists()) backup.delete()` 分支。旧实现（把 `backup.exists()` 当「备份可用」判据）
     * 会在此时删掉完好的 target、再把残缺 backup 挪成 target，用坏文件覆盖好文件。
     *
     * 断言重点：target **逐字节**存活；残缺 backup 被清掉（它不是唯一副本，留着会让下次启动误判）；
     * DB 零写入。
     */
    @Test
    fun `replaceStoredEpub keeps target intact when fallback copy throws mid-write`() = runTest {
        val bookId = "epub_fallback_copy_throws"
        val (target, oldBytes) = seedExistingTarget(bookId)

        fileOps.renameResult = false
        val injected = IOException("模拟备份复制中途中断")
        fileOps.copyBehavior = { source, dest ->
            // 先真的写出一半字节（制造「残缺 backup」这一真实现场），再抛错
            val half = source.readBytes().copyOf(source.length().toInt() / 2)
            dest.outputStream().use { it.write(half) }
            throw injected
        }

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        // 注入的异常必须原样向上传播，不得被吞掉、替换成别的失败或伪装成 AssertionError。
        //
        // 注意：这里**不能**用 `caught === injected` 做身份判定。kotlinx.coroutines 的栈回溯恢复
        // （stack trace recovery）会在异常跨协程边界时**复制**异常对象 —— 副本保留原消息与原栈帧，
        // 但引用不同。实测两者消息相同、构造帧完全相同（均指向本用例内声明 injected 的那一行），
        // 而 identityHashCode 不同。因此判定改用「类型 + 本用例独有的注入消息」，
        // 并用 `!is AssertionError` 排除「断言被当成异常抛出」的假绿。
        assertTrue(
            "注入的 IOException 必须向上传播，caught=${caught?.javaClass?.name}: ${caught?.message}",
            caught is IOException && caught !is AssertionError && caught.message == injected.message,
        )

        // 正向证据：确实进入了回退分支，且复制确实被调用了一次
        assertTrue("必须尝试过 renameTo，实际=${fileOps.renameCalls}", fileOps.renameCalls >= 1)
        assertEquals("必须恰好走一次回退复制", 1, fileOps.copyCalls)

        // 最关键断言：旧 target 逐字节存活（copyTo 抛错时 target.delete() 不可能执行）
        assertTrue("旧 target 必须依然存在", target.exists())
        assertArrayEquals("旧 target 必须逐字节完好，绝不能被残缺备份覆盖", oldBytes, target.readBytes())

        // 残缺 backup 不是唯一副本 ⇒ 必须被清掉，避免下次启动被误当成可用备份
        assertTrue("残缺 backup 必须被清除", !File(target.parentFile, "$bookId.backup").exists())
        assertTrue("staging 必须被清除", !File(target.parentFile, "$bookId.repairing").exists())

        // 数据库零写入，内存缓存无残留
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /**
     * **回退复制正常返回但内容截断**：这是**唯一**能执行
     * `require(backup.length() == target.length()) { "备份旧副本时文件不完整" }` 的路径。
     *
     * 该分支此前从未被任何用例执行过（旧用例曾以负断言显式登记这一缺口）。
     * 本用例锁住「长度校验是残缺备份不得被采纳的唯一闸门」：异常消息必须可识别，
     * 且旧 target 逐字节完好、未被截断的 backup 覆盖。
     *
     * 注意：`require` 只比长度，**长度相同但内容不同**的损坏不会被拦下 —— 这是本片之后
     * 仍然存在的覆盖缺口，本用例不得被用来冒充它。
     */
    @Test
    fun `replaceStoredEpub rejects truncated fallback backup via length check`() = runTest {
        val bookId = "epub_fallback_copy_truncated"
        val (target, oldBytes) = seedExistingTarget(bookId)
        assertTrue("前置条件：旧副本长度必须大于 1 字节", oldBytes.size > 1)

        fileOps.renameResult = false
        fileOps.copyBehavior = { source, dest ->
            // 少写 1 字节后**正常返回**：长度校验是唯一能拦下它的闸门
            val truncated = source.readBytes().copyOf(source.length().toInt() - 1)
            dest.outputStream().use { it.write(truncated) }
        }

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        // 正向证据：长度校验必须真的执行过（消息可识别）
        assertTrue(
            "长度校验必须被触发，实际=${caught?.let { "${it::class.simpleName}: ${it.message}" }}",
            caught is IllegalArgumentException &&
                caught.message?.contains("备份旧副本时文件不完整") == true,
        )
        assertTrue("必须尝试过 renameTo，实际=${fileOps.renameCalls}", fileOps.renameCalls >= 1)
        assertEquals("必须恰好走一次回退复制", 1, fileOps.copyCalls)

        // 截断的 backup 绝不能被采纳：旧 target 逐字节存活
        assertTrue("旧 target 必须依然存在", target.exists())
        assertArrayEquals("截断备份绝不得覆盖完好旧副本", oldBytes, target.readBytes())
        assertTrue("截断的 backup 必须被清除", !File(target.parentFile, "$bookId.backup").exists())
        assertTrue("staging 必须被清除", !File(target.parentFile, "$bookId.repairing").exists())

        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /**
     * **前置失败**：staging 复制阶段就抛错，此时 backup 根本还没建立、target 仍是完好的旧副本。
     *
     * 旧实现的 catch 无条件 `target.delete()`，会在这里删掉用户**仅存**的正文，
     * 而 `backup.exists()` 为 false ⇒ 没有任何东西可以还原。本用例是该缺陷的定向回归。
     *
     * 失败注入点：把 `staging` 路径先建成**非空目录**，使 `staging.delete()` 失效、
     * 随后 `staging.outputStream()` 抛 `FileNotFoundException` —— 该失败发生在
     * `EpubParser.parse`（try 之外）**之后**、建立 backup **之前**，正是指定的窗口。
     */
    @Test
    fun `replaceStoredEpub keeps existing target when staging copy fails before backup`() = runTest {
        val bookId = "epub_pre_backup"
        val (target, oldBytes) = seedExistingTarget(bookId)

        // 让 staging 无法被当作文件写入：同名非空目录
        val stagingDir = File(target.parentFile, "$bookId.repairing")
        stagingDir.mkdirs()
        File(stagingDir, "occupied").writeBytes(byteArrayOf(1, 2, 3))

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue("前置失败必须向上抛出，caught=$caught", caught is Exception && caught !is AssertionError)
        assertTrue("旧 target 必须依然存在", target.exists())
        assertArrayEquals("旧 target 必须逐字节存活，绝不能被删", oldBytes, target.readBytes())
        // 数据库更新绝不发生
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        // backup 从未建立，不得留下任何备份文件
        assertTrue("backup 不得存在", !File(target.parentFile, "$bookId.backup").exists())
    }

    /**
     * **崩溃残留守卫（目录形态）**：操作开始前 `.backup` 已存在 ⇒ 拒绝本次替换。
     *
     * 本用例此前预置同名**非空目录**以触发 `renameTo` 失败 / `copyTo` 抛错，从而覆盖
     * 「备份复制中途失败」。加入入口守卫后，这一预置形态不再能到达复制分支 —— 它现在
     * 覆盖的是**守卫本身**：残留存在时必须原地停手。
     *
     * 断言要点（守卫合同）：抛可识别异常；旧 target 逐字节存活；**残留目录及其内容原样保留**
     * （绝不删、不覆盖、不自动恢复）；数据库零调用；内存缓存无残留。
     * 原「备份复制中途失败」分支的覆盖缺口已在类级文档登记。
     */
    @Test
    fun `replaceStoredEpub refuses and preserves target when a backup directory residue exists`() = runTest {
        val bookId = "epub_backup_dir_residue"
        val (target, oldBytes) = seedExistingTarget(bookId)

        // 残留：同名非空目录（上次崩溃可能留下任意形态）
        val backupDir = File(target.parentFile, "$bookId.backup")
        backupDir.mkdirs()
        val residueBytes = byteArrayOf(9, 9, 9)
        File(backupDir, "occupied").writeBytes(residueBytes)

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        // 可识别错误：消息必须点出残留，供上层直接展示
        assertTrue("残留存在时必须拒绝本次替换，caught=$caught", caught is IllegalStateException)
        assertTrue(
            "错误消息必须可识别为残留拒绝，实际=${caught?.message}",
            caught?.message?.contains("未完成的替换残留") == true,
        )
        // 提示语必须对**本现场（target 存在）**也真实：同一文案要覆盖两种现场，
        // 故对旧正文的表述必须是条件式；不得因 target 存在就断言「旧正文一定在」。
        assertTrue("提示必须说明本次替换已停止", caught?.message?.contains("本次替换已停止") == true)
        assertTrue(
            "提示必须说明重试不会自动恢复",
            caught?.message?.contains("重复重试不会自动恢复") == true,
        )
        assertTrue("提示必须说明残留备份已保留", caught?.message?.contains("残留备份已保留") == true)
        assertTrue(
            "对旧正文的表述必须是条件式（同一文案要覆盖 target 存在/缺失两种现场）",
            caught?.message?.contains("若仍存在") == true,
        )
        assertFalse("不得暗示「确认后再重试」这类自助恢复路径", caught?.message?.contains("再重试") == true)
        assertFalse("不得引导用户删除书籍", caught?.message?.contains("删除") == true)
        assertTrue("旧 target 必须依然存在", target.exists())
        assertArrayEquals("旧 target 必须逐字节存活，绝不能被删", oldBytes, target.readBytes())
        // 残留必须原样保留：不删、不覆盖、不自动恢复
        assertTrue("残留 backup 必须仍然存在", backupDir.exists())
        assertArrayEquals(
            "残留 backup 的内容必须逐字节不变",
            residueBytes,
            File(backupDir, "occupied").readBytes(),
        )
        // 数据库绝不写入
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("被拒绝后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /**
     * 取消同样必须走回滚，不得在 catch 顶部直接重抛 `CancellationException` 跳过它。
     *
     * 若为了「不吞取消」而在 catch 顶部提前 `throw e`，已改写的 target 会带着半截新副本留下，
     * 反而破坏原本完好的旧副本。本用例锁住「取消 ⇒ 旧副本逐字节还原」。
     */
    @Test
    fun `replaceStoredEpub still rolls back the file when the caller is cancelled`() = runTest {
        val bookId = "epub_cancelled"
        val (target, oldBytes) = seedExistingTarget(bookId)

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        // 在「target 已被新文件替换之后」的持久化阶段抛取消
        coEvery { bookDao.update(any()) } throws kotlinx.coroutines.CancellationException("模拟取消")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue("取消必须向上传播，caught=$caught", caught is kotlinx.coroutines.CancellationException)
        assertTrue("旧 target 必须依然存在", target.exists())
        assertArrayEquals("取消时也必须把旧副本逐字节还原", oldBytes, target.readBytes())
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /**
     * **崩溃残留守卫（真实文件形态 + target 缺失）**：与上面「同名非空目录」互补的另一半证据。
     *
     * 本用例此前把 backup 预置成**被其它句柄打开的普通文件**，用「真实文件却内容不全」的形态
     * 钉住「绝不把不完整副本提升为 target」。加入入口守卫后，该形态同样被守卫拦下 ——
     * 但它比目录形态多覆盖一件事：**target 缺失**时（K2：旧副本已挪进 backup、新副本尚未落位）
     * 守卫仍必须拒绝，且**不得**把这份残留当成正文自动恢复或清理。
     * 保留「持有写句柄」这一形态还额外证明：即使残留文件被其它句柄打开，守卫也只是拒绝，
     * 不会因删除/移动失败而抛出无关异常。
     *
     * 同时明确记录：本用例注入的形态**不是**「copyTo 自身抛错」，因此
     * `require(backup.length() == target.length())` 长度校验分支仍未被执行 ——
     * 该分支在本片之前也从未被覆盖，属已登记的覆盖缺口，不得用本用例冒充。
     */
    @Test
    fun `replaceStoredEpub refuses residue and never promotes backup when target is missing`() = runTest {
        val bookId = "epub_backup_file_residue_no_target"
        val targetDir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val target = File(targetDir, "$bookId.epub")
        // target 缺失：模拟上次崩在「旧副本已挪走、新副本尚未落位」之间
        assertTrue("前置条件：target 必须不存在", !target.exists())

        // backup = 已存在且被本测试持有写句柄的普通文件（残留形态）。
        // 注意顺序：先打开句柄（会截断文件）再写入，避免「写完后打开句柄」把内容截成 0 字节。
        val backup = File(targetDir, "$bookId.backup")
        val heldHandle = java.io.FileOutputStream(backup)
        val residueBytes = byteArrayOf(7, 7, 7)
        heldHandle.write(residueBytes)
        heldHandle.flush()
        try {
            val newSource = File(tempFolder.newFolder(), "new.epub")
            createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
            val newUri = mockUri(newSource, "content://books/new.epub")

            var caught: Throwable? = null
            try {
                repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
            } catch (e: Throwable) {
                caught = e
            }

            assertTrue("残留存在时必须拒绝本次替换，caught=$caught", caught is IllegalStateException)
            assertTrue(
                "错误消息必须可识别为残留拒绝，实际=${caught?.message}",
                caught?.message?.contains("未完成的替换残留") == true,
            )
            // 本现场 target **缺失** ⇒ 不得**无条件**断言「原有文件已保留」（根本没有该文件），
            // 否则会让用户误以为旧正文还在原处。提示必须是条件式。
            assertTrue("提示必须说明本次替换已停止", caught?.message?.contains("本次替换已停止") == true)
            assertTrue("提示必须说明残留备份已保留", caught?.message?.contains("残留备份已保留") == true)
            assertTrue(
                "target 缺失时对旧正文的表述必须是条件式",
                caught?.message?.contains("若仍存在") == true,
            )
            assertFalse(
                "target 缺失时不得断言「原有文件与备份均已保留」",
                caught?.message?.contains("原有文件与备份均已保留") == true,
            )
            // 长度校验分支仍未被执行（已知覆盖缺口，不得当作已覆盖）
            val reachedLengthCheck = caught is IllegalArgumentException &&
                caught.message?.contains("备份旧副本时文件不完整") == true
            assertTrue("长度校验分支未被本用例执行（已知缺口）", !reachedLengthCheck)
            // target 不得被凭空造出来，残留 backup 必须原样保留
            assertTrue("不得把残留 backup 提升为 target", !target.exists())
            assertArrayEquals("残留 backup 内容必须逐字节不变", residueBytes, backup.readBytes())
            coVerify(exactly = 0) { bookDao.update(any()) }
            coVerify(exactly = 0) { bookDao.upsert(any()) }
            coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        } finally {
            runCatching { heldHandle.close() }
        }
    }

    /**
     * **无残留正常路径**：不存在任何残留时，替换必须正常完成。
     *
     * 这是守卫的对照组 —— 证明守卫不会误伤正常替换：不抛异常、DB 正常写入、
     * 收尾不留 backup / staging。类级文档中「`renameTo` 失败退回复制」与长度校验两条分支
     * 在本用例里同样不会被执行（同目录 `renameTo` 成功），如实登记为缺口。
     */
    @Test
    fun `replaceStoredEpub succeeds and leaves no residue when no backup exists`() = runTest {
        val bookId = "epub_no_residue"
        val (target, oldBytes) = seedExistingTarget(bookId)
        assertTrue("前置条件：不得存在残留 backup", !File(target.parentFile, "$bookId.backup").exists())

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版书名", bodyText = "新版正文，与旧版不同")
        val newUri = mockUri(newSource, "content://books/new.epub")

        val captured = mutableListOf<BookEntity>()
        coEvery { bookDao.update(capture(captured)) } returns 1

        val result = repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")

        assertEquals("新书标题应解析自新文件", "新版书名", result.title)
        assertEquals("原书 ID 必须严格保持不变", bookId, result.id)
        assertTrue("新内部副本必须已落盘", target.isFile && target.length() > 0L)
        assertFalse("旧内容必须已被新内容取代", oldBytes.contentEquals(target.readBytes()))
        // 收尾清理：backup 与 staging 都不得残留
        assertTrue("成功后不得残留 backup", !File(target.parentFile, "$bookId.backup").exists())
        assertTrue("成功后不得残留 staging", !File(target.parentFile, "$bookId.repairing").exists())
        coVerify(exactly = 1) { bookDao.update(any()) }
        assertEquals("落库的必须是本次替换结果", bookId, captured.first().id)
    }

    // ===== S04「同一来源 URI 更新后仍读旧内容」定向回归 =====
    //
    // 根因不是「漏传 expectedBookId」，而是 EpubParser 的「文件已存在就直接沿用」早退分支：
    // 导入时 bookId 往往就等于 Uri 派生 id，于是 parse 的内部副本路径与替换链路的 target
    // 是同一个文件，早退必然命中，来源的新字节根本没被读过。

    /**
     * **同 URI V1→V2**：这是真机 FAIL 场景的最小复现。
     *
     * 构造方式刻意模拟真实导入：`bookId` 取 `EpubParser` 对同一 URI 字符串的派生 id，
     * 使 `parse` 的内部副本路径与 `replaceStoredEpub` 的 `target` **重合**。
     * 修复前，早退分支会命中旧 target 字节，替换退化为 no-op。
     *
     * 断言的是**内容字节**（V2 的正文章节必须真的出现在新内部副本里），
     * 而不是返回值 —— 只断言 title/id 无法区分「真的换了」和「沿用旧副本」。
     */
    @Test
    fun `replaceStoredEpub reads fresh bytes when source uri is unchanged but content changed`() = runTest {
        val uriString = "content://books/same_source.epub"
        // 与 EpubParser 派生规则一致：epub_<uri.hashCode().toString(36)>
        val bookId = "epub_" + uriString.hashCode().toString(36)

        val dir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val target = File(dir, "$bookId.epub")
        // V1：旧正文（旧内部副本已存在 —— 正是早退分支命中的前提）
        createValidEpubFile(target, title = "旧版书名", bodyText = "V1MARKER第一章旧内容")
        val v1Bytes = target.readBytes()
        assertTrue("前置：V1 必须包含 V1 标记", v1Bytes.toString(Charsets.UTF_8).contains("V1MARKER"))

        // V2：同一 URI、内容已更新
        val v2Source = File(tempFolder.newFolder(), "same_source.epub")
        createValidEpubFile(v2Source, title = "新版书名", bodyText = "V2MARKER第二章新内容")
        val sameUri = mockUri(v2Source, uriString)
        // 关键：旧内部副本存在时，parse 不得复用它
        assertTrue("前置：target（=parse 的派生路径）必须已存在", target.exists())

        val result = repository.replaceStoredEpub(existingBook(bookId, "old_hash"), sameUri, "same_source.epub")

        // 内容字节断言：新内部副本必须真的含 V2 正文、且不含 V1 正文
        val newBytes = target.readBytes()
        val asText = newBytes.toString(Charsets.UTF_8)
        assertTrue("新内部副本必须包含 V2 正文标记，实际未命中", asText.contains("V2MARKER"))
        assertTrue("新内部副本不得残留 V1 正文，实际未命中", !asText.contains("V1MARKER"))
        assertFalse("内部副本字节必须确实改变（不得是 no-op）", v1Bytes.contentEquals(newBytes))
        assertEquals("新标题应来自 V2", "新版书名", result.title)
        assertEquals("原书 ID 必须保持不变", bookId, result.id)
        // 不留任何中间态：.importing（本次来源副本）/ .repairing / .backup
        assertTrue("不得残留 .importing", !File(dir, "$bookId.importing").exists())
        assertTrue("不得残留 .repairing", !File(dir, "$bookId.repairing").exists())
        assertTrue("不得残留 .backup", !File(dir, "$bookId.backup").exists())
        coVerify(exactly = 1) { bookDao.update(any()) }
    }

    /**
     * **不同 URI**：对照组，证明上面那条不是靠「恰好换了个 id」通过的。
     *
     * 换一个 URI ⇒ 派生 id 与 bookId 不同 ⇒ 即便没有本次修复也不会撞上早退分支。
     * 该用例锁住「新鲜来源模式不得破坏本来正常的路径」。
     */
    @Test
    fun `replaceStoredEpub reads fresh bytes for a different source uri`() = runTest {
        val bookId = "epub_other_uri"
        val dir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val target = File(dir, "$bookId.epub")
        createValidEpubFile(target, title = "旧版书名", bodyText = "OLDOTHER旧内容")
        val oldBytes = target.readBytes()

        val newSource = File(tempFolder.newFolder(), "different.epub")
        createValidEpubFile(newSource, title = "新版书名", bodyText = "NEWOTHER新内容")
        val newUri = mockUri(newSource, "content://books/different.epub")

        val result = repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "different.epub")

        val asText = target.readBytes().toString(Charsets.UTF_8)
        assertTrue("必须包含新正文标记", asText.contains("NEWOTHER"))
        assertTrue("不得残留旧正文", !asText.contains("OLDOTHER"))
        assertFalse("字节必须确实改变", oldBytes.contentEquals(target.readBytes()))
        assertEquals("新标题应来自新文件", "新版书名", result.title)
        assertTrue("不得残留 .importing", !File(dir, "$bookId.importing").exists())
        assertTrue("不得残留 .repairing", !File(dir, "$bookId.repairing").exists())
        assertTrue("不得残留 .backup", !File(dir, "$bookId.backup").exists())
    }

    /**
     * **无效 EPUB**：必须拒绝、旧正文逐字节存活，且**不留本次产生的孤儿副本**。
     *
     * 两个独立的失败面：
     * 1. 复制成功但解析失败（`ZipFile` 抛错）—— 真机 §9-4 的孤儿副本正是这条路径；
     * 2. 回滚不得误伤旧 target / 备份。
     *
     * 断言点名 `.importing`（新鲜来源副本）必须不存在 —— 这是 §9-4 的直接回归。
     */
    @Test
    fun `replaceStoredEpub rejects invalid epub without orphan copy and preserves old target`() = runTest {
        val bookId = "epub_invalid_source"
        val (target, oldBytes) = seedExistingTarget(bookId)
        val dir = target.parentFile!!

        // 非 ZIP 字节：复制会成功、ZipFile 必然抛错
        val badSource = File(tempFolder.newFolder(), "bad.epub")
        badSource.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))
        val badUri = mockUri(badSource, "content://books/bad.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), badUri, "bad.epub")
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue("无效 EPUB 必须抛出，caught=$caught", caught is Exception && caught !is AssertionError)

        // 1) 旧正文逐字节存活
        assertTrue("旧 target 必须依然存在", target.exists())
        assertArrayEquals("旧 target 必须逐字节完好", oldBytes, target.readBytes())

        // 2) 不得留下本次产生的任何中间态（§9-4 直接回归）
        assertTrue("不得残留 .importing 孤儿副本", !File(dir, "$bookId.importing").exists())
        assertTrue("不得残留 .repairing", !File(dir, "$bookId.repairing").exists())
        assertTrue("不得残留 .backup", !File(dir, "$bookId.backup").exists())

        // 3) 目录内除旧 target 外不得有本次新增文件
        val extra = dir.listFiles()?.filter { it.name.startsWith(bookId) && it.name != "$bookId.epub" }
        assertTrue("本次操作不得留下任何书 id 前缀文件，实际=$extra", extra.isNullOrEmpty())

        // 4) DB 零写入
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        assertNull("失败后内存缓存不得残留该书", repository.cached(bookId))
    }

    /**
     * **新鲜来源不得污染 target**：解析阶段就必须落在 `.importing` 上。
     *
     * 这是修复的结构性保证 —— 若新鲜来源字节直接落进 target，那么在「备份尚未建立」
     * 到「解析完成」之间一旦失败，旧正文就已经被覆盖了，回滚也无从还原。
     * 本用例在解析成功后、替换完成前人为让后续阶段失败，验证此刻 target 仍是旧字节。
     */
    @Test
    fun `replaceStoredEpub keeps target untouched until backup is established`() = runTest {
        val bookId = "epub_fresh_not_on_target"
        val (target, oldBytes) = seedExistingTarget(bookId)
        val dir = target.parentFile!!

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "NEWGUARD新正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        // 在备份建立之后、提升之前注入失败（bookDao.update 在最后阶段）
        coEvery { bookDao.update(any()) } throws RuntimeException("模拟落库失败")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue("落库失败必须向上传播，caught=$caught", caught is RuntimeException)

        // 回滚后旧正文必须逐字节还原
        assertTrue("旧 target 必须依然存在", target.exists())
        assertArrayEquals("旧 target 必须逐字节还原", oldBytes, target.readBytes())
        assertTrue("不得残留 .importing", !File(dir, "$bookId.importing").exists())
        assertTrue("不得残留 .repairing", !File(dir, "$bookId.repairing").exists())
        assertTrue("不得残留 .backup", !File(dir, "$bookId.backup").exists())
    }

    // ===== 回滚恢复失败（backup→target）：经 FileOps 接缝按 source 区分注入 =====

    /**
     * 回滚恢复注入的公共骨架：
     * - `bookDao.update` 抛出原始异常（后置 DAO 故障，触发 backupReady=true 回滚）；
     * - 回滚 `renameTo` 按 source=`<id>.backup` 注入为 false，强制走恢复复制；
     * - 恢复复制按 source=`<id>.backup` 注入 [partialThen] 描述的故障形态。
     */
    private fun seedRestoreFailure(
        bookId: String,
        partialThen: (source: File, dest: File, oldBytes: ByteArray) -> Unit,
    ): Triple<File, ByteArray, RuntimeException> {
        val (target, oldBytes) = seedExistingTarget(bookId)
        val daoError = RuntimeException("模拟落库失败·恢复注入")
        coEvery { bookDao.update(any()) } throws daoError
        fileOps.restoreRenameResult = false
        fileOps.restoreCopyBehavior = { source, dest -> partialThen(source, dest, oldBytes) }
        return Triple(target, oldBytes, daoError)
    }

    /** 场景 A：恢复复制写出部分旧字节后抛 I/O 异常。 */
    @Test
    fun `replaceStoredEpub keeps full backup when restore copy throws after partial write`() = runTest {
        val bookId = "epub_restore_copy_throws"
        val (target, oldBytes, daoError) = seedRestoreFailure(bookId) { source, dest, bytes ->
            val half = bytes.copyOf(bytes.size / 2)
            dest.outputStream().use { it.write(half) }
            throw IOException("模拟恢复复制中途中断")
        }
        val backup = File(target.parentFile, "$bookId.backup")
        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        // 正向证据：确实走到了回滚恢复的复制分支（rename 被注入为 false、copy 被调用一次）
        assertTrue("必须尝试过 renameTo，实际=${fileOps.renameCalls}", fileOps.renameCalls >= 1)
        assertEquals("恢复复制必须恰好调用一次", 1, fileOps.copyCalls)

        // 主异常必须是原始 DAO 异常（类型 + 本用例独有消息），恢复异常只能作 suppressed。
        // 不做身份判定：协程栈回溯恢复会跨 withContext 复制异常对象（见既有用例注释）。
        assertTrue(
            "原始 DAO 异常必须向上传播，caught=${caught?.javaClass?.name}: ${caught?.message}",
            caught is RuntimeException && caught !is AssertionError && caught.message == daoError.message,
        )
        // suppressed 只能断言在**注入的同一实例**上：addSuppressed 发生在 catch 块内、
        // 重抛之前，而向上冒泡的是协程恢复后的**副本**（副本不带 suppressed —— 实测
        // caught.suppressed 为空）。MockK 抛出的正是 daoError 这个实例，因此
        // daoError.suppressed 就是生产代码写入的结果。
        val suppressed = daoError.suppressed
        assertTrue(
            "恢复段异常必须以 suppressed 形式保留在原异常上，实际=${suppressed.joinToString { "${it::class.simpleName}:${it.message}" }}",
            suppressed.any { it is IOException && it.message == "模拟恢复复制中途中断" },
        )

        // 完整 backup 必须保留（旧字节唯一完整在盘副本），恢复异常绝不顶替主异常。
        assertTrue("完整 backup 必须保留", backup.exists())
        assertArrayEquals("backup 必须仍含完整旧字节", oldBytes, backup.readBytes())

        // DB 零写入，内存缓存无残留
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /** 场景 B：恢复复制只写部分字节却正常返回 ⇒ 内容核对必须拦下，backup 不得被删。 */
    @Test
    fun `replaceStoredEpub keeps full backup when restore copy returns truncated`() = runTest {
        val bookId = "epub_restore_copy_truncated"
        val (target, oldBytes, daoError) = seedRestoreFailure(bookId) { source, dest, bytes ->
            // 少写 1 字节后正常返回：内容核对是删 backup 前的唯一闸门
            val truncated = bytes.copyOf(bytes.size - 1)
            dest.outputStream().use { it.write(truncated) }
        }
        val backup = File(target.parentFile, "$bookId.backup")
        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        // 正向证据：恢复复制被调用一次，且内容核对真的执行过
        assertEquals("恢复复制必须恰好调用一次", 1, fileOps.copyCalls)

        // 主异常仍是原始 DAO 异常；核对失败以 suppressed 保留（同上：断言在注入实例上）
        assertTrue(
            "原始 DAO 异常必须向上传播，caught=${caught?.javaClass?.name}: ${caught?.message}",
            caught is RuntimeException && caught !is AssertionError && caught.message == daoError.message,
        )
        val suppressed = daoError.suppressed
        assertTrue(
            "内容核对失败必须以 suppressed 保留，实际=${suppressed.joinToString { "${it::class.simpleName}:${it.message}" }}",
            suppressed.any { it.message?.contains("回滚恢复不完整") == true },
        )

        // 最关键断言：核对失败 ⇒ backup 绝不能被删，完整旧字节仍在磁盘上
        assertTrue("内容不一致时 backup 必须保留", backup.exists())
        assertArrayEquals("backup 必须仍含完整旧字节", oldBytes, backup.readBytes())

        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /** 回归：恢复 rename 成功的既有行为（成功回滚）不被本次改动破坏。 */
    @Test
    fun `replaceStoredEpub still restores target via rename when restore rename succeeds`() = runTest {
        val bookId = "epub_restore_rename_ok"
        val (target, oldBytes) = seedExistingTarget(bookId)
        val daoError = RuntimeException("模拟落库失败·rename 成功回归")
        coEvery { bookDao.update(any()) } throws daoError
        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue("原始 DAO 异常必须向上传播", caught is RuntimeException && caught.message == daoError.message)
        assertTrue("旧 target 必须存在", target.exists())
        assertArrayEquals("旧 target 必须逐字节还原（经 rename 恢复）", oldBytes, target.readBytes())
        assertTrue("成功恢复后 backup 必须被消费", !File(target.parentFile, "$bookId.backup").exists())
        assertEquals("rename 成功时不得走恢复复制", 0, fileOps.copyCalls)
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /**
     * **面向调用方的异常面断言**：恢复失败信息必须能从**实际捕获的异常及其 cause 链**中寻得，
     * 且应用诊断页（AppLog）必须留下记录。
     *
     * 背景（docs/coordination/2026-09-27-s04-epub-rollback-exception-surface-review.md §1）：
     * kotlinx.coroutines 1.10.2 的栈回溯恢复在「JVM 断言开启 + 跨拦截器 withContext」时会把异常
     * 复制为同类型副本——副本的 suppressed 为空，挂有 suppressed 的原实例位于其 cause 链；
     * 生产 Android（断言关闭）不复制，捕获的即原实例。因此这里刻意**不断言对象身份与位置**，
     * 只断言两条与形态无关的契约：
     * 1. 原始 DAO 异常（类型 + 本用例独有消息）能在捕获异常的 cause 链上寻得；
     * 2. 恢复失败（IOException）能在链上某异常的 suppressed 中寻得。
     * 「同实例」与「副本+cause」两种形态下都必须通过。
     */
    @Test
    fun `replaceStoredEpub surfaces restore failure through the caught exception chain`() = runTest {
        val bookId = "epub_restore_chain_surface"
        val logEntriesBefore = AppLog.entries.value.size
        val (target, oldBytes, daoError) = seedRestoreFailure(bookId) { _, dest, bytes ->
            val half = bytes.copyOf(bytes.size / 2)
            dest.outputStream().use { it.write(half) }
            throw IOException("模拟恢复复制中途中断·链面断言")
        }
        val backup = File(target.parentFile, "$bookId.backup")
        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull("必须捕获到异常", caught)

        // 正向证据：确实走到了回滚恢复的复制分支（rename 注入为 false、copy 恰好一次）
        assertTrue("必须尝试过 renameTo，实际=${fileOps.renameCalls}", fileOps.renameCalls >= 1)
        assertEquals("恢复复制必须恰好调用一次", 1, fileOps.copyCalls)

        // 捕获异常的 cause 链（限深防环）
        val chain = generateSequence(caught) { it.cause }.take(16).toList()

        // 1) 主异常在链上：类型 + 本用例独有消息（不做身份/位置固定断言）
        assertTrue(
            "原始 DAO 异常必须出现在捕获异常的 cause 链上，chain=${chain.map { "${it::class.simpleName}:${it.message}" }}",
            chain.any { it is RuntimeException && it.message == daoError.message },
        )
        // 2) 恢复失败信息在链上某异常的 suppressed 中——不是只检查注入实例
        assertTrue(
            "恢复失败必须能从捕获异常及其 cause 链的 suppressed 中寻得，chain=${chain.map { "${it::class.simpleName}:suppressed=${it.suppressed.toList()}" }}",
            chain.flatMap { it.suppressed.toList() }
                .any { it is IOException && it.message == "模拟恢复复制中途中断·链面断言" },
        )

        // 3) 诊断页记录已落盘（AppLog 环形缓冲），且不含书名/文件名/路径
        val newLogEntries = AppLog.entries.value.drop(logEntriesBefore)
        assertTrue(
            "恢复失败必须写入 AppLog 诊断记录，新增=${newLogEntries.map { "${it.module}:${it.message}" }}",
            newLogEntries.any { it.module == "EpubRepository" && it.message.startsWith("EPUB 回滚恢复未完成") },
        )
        assertFalse(
            "诊断记录不得携带文件名（bookId）",
            newLogEntries.any { it.message.contains(bookId) },
        )

        // 数据安全不变式：完整 backup 保留（逐字节），DB 零写入，缓存清空
        assertTrue("完整 backup 必须保留", backup.exists())
        assertArrayEquals("backup 必须仍含完整旧字节", oldBytes, backup.readBytes())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    // ===== CRA-ROLLBACK-WIP-RECONCILE-v2：守卫前移 / fresh-source 失败语义 / 逐字节核对 =====

    /**
     * **守卫前移**：残留 backup 存在时，守卫必须在 fresh-source 解析**之前**拒绝。
     *
     * 守卫旧位置在 parse 之后：同 URI 时 parse 的派生 id 与 bookId 重合，会在
     * books/epub/ 下先落下 `<id>.importing` 新来源副本，随后才被守卫拒绝 ——
     * 残留现场叠上一个本次产生的孤儿副本。修复后拒绝必须发生在任何来源读取之前。
     */
    @Test
    fun `replaceStoredEpub rejects residue before fresh-source parse leaves any importing copy`() = runTest {
        val uriString = "content://books/guard_same_source.epub"
        val bookId = "epub_" + uriString.hashCode().toString(36)
        val (target, oldBytes) = seedExistingTarget(bookId)
        val dir = target.parentFile!!
        val backup = File(dir, "$bookId.backup").also { it.writeBytes(byteArrayOf(5, 5, 5)) }

        // 关键：来源 URI 不可读。若守卫仍在 parse 之后，会先抛「无法读取来源」
        // 而不是残留拒绝，且可能在派生路径留下 .importing —— 两种偏差都会暴露旧位置。
        val unreadableUri = mockk<Uri>()
        every { unreadableUri.toString() } returns uriString
        every { resolver.openInputStream(unreadableUri) } throws IOException("模拟来源不可读")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), unreadableUri, "same.epub")
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue("残留存在时必须以残留拒绝收场，caught=$caught", caught is IllegalStateException)
        assertTrue(
            "错误消息必须可识别为残留拒绝",
            caught?.message?.contains("未完成的替换残留") == true,
        )
        assertTrue("守卫必须在读取来源之前拒绝：不得残留 .importing", !File(dir, "$bookId.importing").exists())
        assertTrue("旧 target 必须逐字节保留", target.exists() && oldBytes.contentEquals(target.readBytes()))
        assertTrue("残留 backup 必须原样保留", backup.exists() && backup.readBytes().contentEquals(byteArrayOf(5, 5, 5)))
        assertTrue("不得残留 staging", !File(dir, "$bookId.repairing").exists())
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
    }

    /**
     * **fresh-source 失败不得退回旧副本**：来源 URI 不可读时替换必须失败，
     * 旧 target 字节原样保留、无 `.importing` 残留、DAO 零写（S04 §9-1/§9-4 合同）。
     *
     * EpubParser 已被真实调用（同 URI 派生 id 与 target 重合），因此本用例钉住的是
     * 「fresh-source 模式禁用本地兜底 + 失败清理本次副本」的端到端语义。
     */
    @Test
    fun `replaceStoredEpub fails without falling back to old copy when source is unreadable`() = runTest {
        val uriString = "content://books/unreadable_source.epub"
        val bookId = "epub_" + uriString.hashCode().toString(36)
        val (target, oldBytes) = seedExistingTarget(bookId)
        val dir = target.parentFile!!

        val unreadableUri = mockk<Uri>()
        every { unreadableUri.toString() } returns uriString
        every { resolver.openInputStream(unreadableUri) } throws IOException("模拟来源不可读·fresh")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), unreadableUri, "same.epub")
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue(
            "来源不可读必须失败，且不得退回旧副本冒充新来源，caught=$caught",
            caught is Exception && caught !is AssertionError,
        )
        assertTrue("旧 target 必须逐字节保留", target.exists() && oldBytes.contentEquals(target.readBytes()))
        assertTrue("失败后不得残留 .importing", !File(dir, "$bookId.importing").exists())
        assertTrue("不得残留 staging", !File(dir, "$bookId.repairing").exists())
        assertTrue("不得产生 backup", !File(dir, "$bookId.backup").exists())
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /**
     * **建立备份：等长错字节**。S04 阻断①的另一半：`copyTo(backup)` 正常返回、
     * 长度与 target 完全相同、但**内容**不同 —— 只比长度的校验拦不下它，
     * 修复前 `target.delete()` 会删掉唯一完好的旧正文，让坏字节上位。
     * 修复后必须逐字节核对失败即拒绝，target 逐字节存活。
     */
    @Test
    fun `replaceStoredEpub rejects equal-length corrupted backup copy by byte comparison`() = runTest {
        val bookId = "epub_fallback_copy_corrupted"
        val (target, oldBytes) = seedExistingTarget(bookId)
        assertTrue("前置条件：旧副本长度必须大于 1 字节", oldBytes.size > 1)

        fileOps.renameResult = false
        fileOps.copyBehavior = { source, dest ->
            // 等长 + 首字节翻转：长度校验的盲区，逐字节核对是唯一闸门
            val corrupted = source.readBytes().copyOf()
            corrupted[0] = (corrupted[0] + 1).toByte()
            dest.outputStream().use { it.write(corrupted) }
        }

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue(
            "等长错字节的备份必须被逐字节核对拦下，实际=${caught?.let { "${it::class.simpleName}: ${it.message}" }}",
            caught is IllegalArgumentException &&
                caught.message?.contains("备份旧副本时文件不完整") == true,
        )
        assertTrue("必须尝试过 renameTo，实际=${fileOps.renameCalls}", fileOps.renameCalls >= 1)
        assertEquals("必须恰好走一次回退复制", 1, fileOps.copyCalls)
        assertTrue("旧 target 必须依然存在", target.exists())
        assertArrayEquals("等长坏备份绝不得覆盖完好旧副本", oldBytes, target.readBytes())
        assertTrue("被拒绝的坏 backup 必须被清除", !File(target.parentFile, "$bookId.backup").exists())
        assertTrue("staging 必须被清除", !File(target.parentFile, "$bookId.repairing").exists())
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /**
     * **恢复复制：等长错字节**。S04 阻断②的另一半：恢复 `copyTo(backup→target)` 正常
     * 返回、长度与 backup 相同、但内容不同 —— 修复前 `backup.delete()` 会删掉唯一完整
     * 旧字节。修复后必须核对失败 ⇒ 不删 backup、挂 suppressed、主异常不被顶替。
     */
    @Test
    fun `replaceStoredEpub keeps full backup when restore copy returns equal-length corrupted bytes`() = runTest {
        val bookId = "epub_restore_copy_corrupted"
        val (target, oldBytes, daoError) = seedRestoreFailure(bookId) { source, dest, bytes ->
            // 等长 + 首字节翻转：长度核对无效，逐字节核对是删 backup 前的唯一闸门
            val corrupted = source.readBytes().copyOf()
            corrupted[0] = (corrupted[0] + 1).toByte()
            dest.outputStream().use { it.write(corrupted) }
        }
        val backup = File(target.parentFile, "$bookId.backup")
        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")
        } catch (e: Throwable) {
            caught = e
        }

        assertEquals("恢复复制必须恰好调用一次", 1, fileOps.copyCalls)
        assertTrue(
            "原始 DAO 异常必须向上传播，caught=${caught?.javaClass?.name}: ${caught?.message}",
            caught is RuntimeException && caught !is AssertionError && caught.message == daoError.message,
        )
        val suppressed = daoError.suppressed
        assertTrue(
            "逐字节核对失败必须以 suppressed 保留，实际=${suppressed.joinToString { "${it::class.simpleName}:${it.message}" }}",
            suppressed.any { it.message?.contains("回滚恢复不完整") == true },
        )
        assertTrue("等长错字节时 backup 必须保留", backup.exists())
        assertArrayEquals("backup 必须仍含完整旧字节", oldBytes, backup.readBytes())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        assertNull("失败后内存缓存中不得残留该书", repository.cached(bookId))
    }

    /**
     * **正常路径的核对无害性**：回退复制完整落盘时，逐字节核对必须通过、
     * 随后才删旧 target，替换照常完成且零残留 —— 证明新闸门不误伤 happy path。
     */
    @Test
    fun `replaceStoredEpub deletes old target only after byte verification passes on happy path`() = runTest {
        val bookId = "epub_verify_then_delete_ok"
        val (target, oldBytes) = seedExistingTarget(bookId)

        fileOps.renameResult = false // 强制走 copyTo 兜底，让「核对通过后才删 target」被执行

        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "核对新版", bodyText = "逐字节核对通过后的新正文")
        val newUri = mockUri(newSource, "content://books/new.epub")

        val result = repository.replaceStoredEpub(existingBook(bookId, "old_hash"), newUri, "new.epub")

        assertTrue("必须尝试过 renameTo，实际=${fileOps.renameCalls}", fileOps.renameCalls >= 1)
        assertEquals("必须恰好走一次回退复制", 1, fileOps.copyCalls)
        assertEquals("原书 ID 必须保持不变", bookId, result.id)
        assertEquals("新书标题应解析自新文件", "核对新版", result.title)
        assertTrue("新副本必须已落位", target.isFile && target.length() > 0L)
        assertFalse("旧内容必须已被新内容取代", oldBytes.contentEquals(target.readBytes()))
        assertTrue("成功后不得残留 backup", !File(target.parentFile, "$bookId.backup").exists())
        assertTrue("成功后不得残留 staging", !File(target.parentFile, "$bookId.repairing").exists())
        coVerify(exactly = 1) { bookDao.update(any()) }
    }
}
