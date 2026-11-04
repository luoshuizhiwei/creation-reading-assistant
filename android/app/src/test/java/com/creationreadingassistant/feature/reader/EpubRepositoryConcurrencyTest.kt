package com.creationreadingassistant.feature.reader

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 同书并发合同（CRA-ROLLBACK-WIP-RECONCILE-v3）。
 *
 * 前提事实：`replaceStoredEpub` 可被多个 ViewModel 协程先后触发（批量修复、重试、
 * 重新选择），`EpubParser.parse` 的普通落位段（打开未落盘的书 / 迁移）与替换流程共用
 * 按 id 确定路径的 `<id>.importing` / `<id>.epub`。单个协程内的 for-loop 只保证协程内
 * 串行，不构成跨协程互斥。
 *
 * 时序控制手段：[EpubSwapGate.waiters] 是闸的等待/持有计数 —— 测试先持闸、再启动被测
 * 协程、等计数到位后注入现场、再放闸。所有分支点都由闸/Deferred 事件驱动，
 * 不依赖任何 sleep 或时序运气。
 */
@Suppress("EXPERIMENTAL_COROUTINES_API")
class EpubRepositoryConcurrencyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var resolver: ContentResolver
    private lateinit var context: Context
    private lateinit var bookDao: BookDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var repository: EpubRepository

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
            coEvery { getById(any()) } returns null
            coEvery { update(any()) } returns 1
            coEvery { upsert(any()) } returns Unit
        }
        readingProgressDao = mockk {
            coEvery { getByBook(any()) } returns null
            coEvery { upsert(any()) } returns Unit
        }
        repository = EpubRepository(
            context = context,
            bookDao = bookDao,
            readingProgressDao = readingProgressDao,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            fileOps = object : FileOps {
                override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)
                override fun copyTo(source: File, dest: File) {
                    source.copyTo(dest, overwrite = true)
                }
            },
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    /** 构造结构合法的最小 EPUB（与 EpubRepositoryReplaceTest 的夹具同构）。 */
    private fun createValidEpubFile(dest: File, title: String, bodyText: String): File {
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            zos.setLevel(Deflater.NO_COMPRESSION)
            fun put(name: String, bytes: ByteArray) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
            put("mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))
            put(
                "META-INF/container.xml",
                """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""".toByteArray(),
            )
            put(
                "OEBPS/content.opf",
                """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>$title</dc:title><dc:language>zh</dc:language><dc:identifier id="uid">$title</dc:identifier>
  </metadata>
  <manifest><item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/></manifest>
  <spine><itemref idref="ch1"/></spine>
</package>""".toByteArray(),
            )
            put(
                "OEBPS/ch1.xhtml",
                """<?xml version="1.0"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>$title</title></head>
<body><p>$bodyText</p></body></html>""".toByteArray(),
            )
        }
        return dest
    }

    private fun mockUri(file: File, uriString: String): Uri {
        val u = mockk<Uri>()
        every { u.toString() } returns uriString
        every { u.lastPathSegment } returns file.name
        every { Uri.fromFile(file) } returns u
        every { Uri.fromFile(any()) } answers {
            val f = firstArg<File>()
            val sub = mockk<Uri>()
            every { sub.toString() } returns "file://${f.absolutePath}"
            every { sub.lastPathSegment } returns f.name
            sub
        }
        every { resolver.openInputStream(u) } answers { file.inputStream() }
        return u
    }

    private fun existingBook(bookId: String) = BookEntity(
        id = bookId,
        title = "并发测试书",
        format = "epub",
        original_file_name = "original.epub",
        size = 1024,
        content_hash = "old_hash",
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun epubDir(): File = File(tempFolder.root, "books/epub")

    @Test
    fun `replaceStoredEpub waits on the swap gate and re-evaluates residue at gate entry`() = runBlocking {
        val bookId = "epub_c1_gate_residue"
        val target = File(epubDir(), "$bookId.epub")
        createValidEpubFile(target, title = "旧版", bodyText = "旧版正文")
        val newSource = File(tempFolder.newFolder(), "new.epub")
        createValidEpubFile(newSource, title = "新版", bodyText = "新版正文")
        val newUri = mockUri(newSource, "content://books/c1_new.epub")

        // 1. 测试持闸（闸内第一行信号，证明确实已持闸），随后挂起等测试放行。
        val holderInGate = CompletableDeferred<Unit>()
        val park = CompletableDeferred<Unit>()
        val holder = async {
            EpubSwapGate.withGate {
                holderInGate.complete(Unit)
                park.await()
            }
        }
        holderInGate.await()

        // 2. 启动替换：此时闸被占，替换必须停在闸外（守卫都还没跑）。
        val caughtByGate = CompletableDeferred<Throwable?>()
        val replaceJob = launch {
            val result = runCatching {
                repository.replaceStoredEpub(existingBook(bookId), newUri, "new.epub")
            }.exceptionOrNull()
            caughtByGate.complete(result)
        }
        withTimeout(10_000) { EpubSwapGate.waiters.first { it >= 2 } }

        // 3. 替换被闸挡住的窗口期内注入 backup 残留 —— 模拟「另一替换先失败并留下现场」。
        val residue = File(epubDir(), "$bookId.backup")
        residue.writeBytes("并发窗口注入的残留备份".toByteArray())
        val residueBytes = residue.readBytes()

        // 4. 放行持闸者 → 替换进入闸内，守卫必须按**进闸时**的现场拒绝。
        park.complete(Unit)
        holder.await()
        replaceJob.join()
        assertTrue("替换任务必须已结束（被守卫拒绝）", replaceJob.isCompleted)

        // 5. 拒绝必须发生在任何写入之前：守卫按进闸时现场拒绝，DB 零写、残留原样、无 .importing。
        val gateRefusal = caughtByGate.await()
        assertTrue(
            "必须以残留守卫拒绝结束，实际：${gateRefusal?.javaClass?.name}: ${gateRefusal?.message}",
            gateRefusal is IllegalStateException && gateRefusal.message?.contains("残留") == true,
        )
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        assertTrue("残留 backup 必须原样保留", residue.exists())
        assertArrayEquals("残留 backup 字节不得被改写", residueBytes, residue.readBytes())
        assertTrue(
            "拒绝路径不得留下任何 .importing 副本",
            epubDir().listFiles { f -> f.name.endsWith(".importing") }.orEmpty().isEmpty(),
        )
    }

    @Test
    fun `plain parse landing waits for an in-flight replace instead of deleting its importing copy`() = runBlocking {
        // 1. 测试持闸；此时书尚无内部副本（<派生id>.epub 不存在）。
        val holderInGate = CompletableDeferred<Unit>()
        val park = CompletableDeferred<Unit>()
        val holder = async {
            EpubSwapGate.withGate {
                holderInGate.complete(Unit)
                park.await()
            }
        }
        holderInGate.await()

        // 2. 模拟替换已落在 .importing 上的中间现场（fresh 解析产物，闸内独占）。
        epubDir().mkdirs()
        val inFlightImporting = File(epubDir(), "epub_someid.importing")
        inFlightImporting.writeBytes("替换流程正在使用的来源副本".toByteArray())

        // 3. 启动普通 parse（打开未落盘的书）：落位段必须等闸，不得此刻删/占 .importing。
        val openSource = File(tempFolder.newFolder(), "open.epub")
        createValidEpubFile(openSource, title = "首次打开", bodyText = "首次落盘正文")
        val openJob = launch {
            repository.openEpub(mockUri(openSource, "content://books/open.epub"))
        }
        withTimeout(10_000) { EpubSwapGate.waiters.first { it >= 2 } }
        assertTrue("普通 parse 在等闸期间不得落位内部副本", epubDir().listFiles { f -> f.name.endsWith(".epub") }.orEmpty().isEmpty())

        // 4. 放行：parse 落位自己的副本；替换现场文件不受牵连。
        park.complete(Unit)
        holder.await()
        openJob.join()
        assertTrue("普通 parse 必须在放行后完成落位", openJob.isCompleted)
        assertTrue(
            "落位后的内部副本必须存在",
            epubDir().listFiles { f -> f.name.endsWith(".epub") }.orEmpty().isNotEmpty(),
        )
        assertArrayEquals(
            "替换流程的 .importing 不得被并发 parse 删除",
            "替换流程正在使用的来源副本".toByteArray(),
            inFlightImporting.readBytes(),
        )
    }

    @Test
    fun `plain parse rechecks target after queued replace and preserves installed bytes`() = runBlocking {
        val uriString = "content://books/queued-open.epub"
        val source = File(tempFolder.newFolder(), "queued-open.epub")
        createValidEpubFile(source, title = "旧来源", bodyText = "旧来源正文")
        val uri = mockUri(source, uriString)
        val id = "epub_${uriString.hashCode().toString(36)}"
        val target = File(epubDir(), "$id.epub")
        val installed = File(tempFolder.newFolder(), "installed.epub")
        createValidEpubFile(installed, title = "替换新版", bodyText = "替换新版正文")
        val installedBytes = installed.readBytes()

        val inGate = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async {
            EpubSwapGate.withGate {
                inGate.complete(Unit)
                release.await()
            }
        }
        inGate.await()
        val parse = async { EpubParser.parse(context, uri) }
        withTimeout(10_000) { EpubSwapGate.waiters.first { it >= 2 } }
        target.parentFile!!.mkdirs()
        target.writeBytes(installedBytes)
        release.complete(Unit)
        holder.await()

        val parsed = withTimeout(10_000) { parse.await() }
        assertEquals("替换新版", parsed.title)
        assertArrayEquals("排队期间安装的新副本不得被旧来源覆盖", installedBytes, target.readBytes())
    }

    @Test
    fun `replacement reparse failure exits gate and restores old target`() = runBlocking {
        val bookId = "epub_c4_reparse_failure"
        val target = File(epubDir(), "$bookId.epub")
        createValidEpubFile(target, title = "旧版", bodyText = "旧版完整正文")
        val oldBytes = target.readBytes()
        val source = File(tempFolder.newFolder(), "replacement.epub")
        createValidEpubFile(source, title = "新版", bodyText = "新版完整正文")
        val sourceUri = mockUri(source, "content://books/c4_replacement.epub")
        var corruptPromotedTarget = true
        every { Uri.fromFile(any()) } answers {
            val file = firstArg<File>()
            if (file.absolutePath == target.absolutePath && corruptPromotedTarget) {
                corruptPromotedTarget = false
                file.writeBytes("not an epub".toByteArray())
            }
            val result = mockk<Uri>()
            every { result.toString() } returns "file://${file.absolutePath}"
            every { result.lastPathSegment } returns file.name
            result
        }

        val failure = withTimeout(10_000) {
            runCatching { repository.replaceStoredEpub(existingBook(bookId), sourceUri, "replacement.epub") }
                .exceptionOrNull()
        }
        assertTrue("reparse must not self-deadlock until test timeout", failure !is TimeoutCancellationException)
        assertTrue("second parse must fail with a parse error, not gate timeout: $failure", failure != null)
        assertTrue("the original target must be restored", target.exists())
        assertArrayEquals("the original bytes must be restored", oldBytes, target.readBytes())
        assertTrue("operation must leave the gate available", withTimeout(2_000) {
            EpubSwapGate.withGate { true }
        })
    }

    @Test
    fun `two concurrent replaces on the same book converge to one consistent install`() = runBlocking {
        val bookId = "epub_c3_two_replaces"
        val target = File(epubDir(), "$bookId.epub")
        createValidEpubFile(target, title = "旧版", bodyText = "旧版正文")

        val sourceA = File(tempFolder.newFolder(), "a.epub")
        val sourceB = File(tempFolder.newFolder(), "b.epub")
        createValidEpubFile(sourceA, title = "来源A", bodyText = "来源A正文")
        createValidEpubFile(sourceB, title = "来源B", bodyText = "来源B正文")
        val bytesA = sourceA.readBytes()
        val bytesB = sourceB.readBytes()
        val uriA = mockUri(sourceA, "content://books/c3_a.epub")
        val uriB = mockUri(sourceB, "content://books/c3_b.epub")

        // 同书两路替换并发启动（真实 Parser，全程持闸）。
        val jobs = listOf(uriA, uriB).map { uri ->
            async {
                try {
                    repository.replaceStoredEpub(existingBook(bookId), uri, "replace.epub")
                    null
                } catch (e: Throwable) {
                    e
                }
            }
        }
        val errors = jobs.awaitAll()
        assertTrue(
            "两路替换都必须正常完成（闸保证串行）：${errors.filterNotNull()}",
            errors.all { it == null },
        )

        val finalBytes = target.readBytes()
        assertTrue(
            "最终 target 必须是两路来源之一的完整字节（不许交错损坏）",
            finalBytes.contentEquals(bytesA) || finalBytes.contentEquals(bytesB),
        )
        assertTrue(
            "并发完成后不得留下 .importing 残留",
            epubDir().listFiles { f -> f.name.endsWith(".importing") }.orEmpty().isEmpty(),
        )
        assertTrue(
            "并发完成后不得留下 .backup 残留",
            epubDir().listFiles { f -> f.name.endsWith(".backup") }.orEmpty().isEmpty(),
        )
        assertTrue(
            "并发完成后不得留下 .repairing 残留",
            epubDir().listFiles { f -> f.name.endsWith(".repairing") }.orEmpty().isEmpty(),
        )
        coVerify(atLeast = 2) { bookDao.update(any()) }
    }
}
