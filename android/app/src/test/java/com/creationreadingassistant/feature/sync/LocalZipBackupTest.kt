package com.creationreadingassistant.feature.sync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import com.creationreadingassistant.feature.log.AppLog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 锁定 [LocalZipBackup] 的打包 / 解包 / ZipSlip 防护语义。
 *
 * 不依赖 Robolectric：用 mockk 伪造一个把 SAF URI 映射到真实临时文件的
 * [ContentResolver]，[JsonBridge] 也全程 relaxed mock（导出返回固定 JSON、
 * 导入为无副作用的桩），从而在没有真实 Room 数据库的情况下验证 ZIP 层的自身逻辑：
 * 1. 导出把 backup.json 与 books/ 源文件打进 ZIP
 * 2. 导入解包后调用 JsonBridge.importFromString 一次
 * 3. 缺少 backup.json 的 ZIP 被拒绝
 * 4. ZipSlip 入口名清理 + 相对路径计算（越界抛异常）
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalZipBackupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var zipFile: File
    private lateinit var filesDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        zipFile = temp.newFile("backup.zip")
        filesDir = temp.newFolder("files")
        cacheDir = temp.newFolder("cache")
        // 归属日志 / 残留报告用例直接断言诊断日志内容，先清空单例环形缓冲隔离用例。
        AppLog.clear()
    }

    private fun fakeContext(): Context {
        val cr = mockk<ContentResolver>(relaxed = true)
        // SAF URI 一律映射到同一个真实临时 zip 文件（导出写入、导入读出）。
        every { cr.openOutputStream(any()) } answers { zipFile.outputStream() }
        every { cr.openInputStream(any()) } answers { zipFile.inputStream() }
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns filesDir
        every { ctx.cacheDir } returns cacheDir
        every { ctx.contentResolver } returns cr
        return ctx
    }

    private fun subject(jsonBridge: JsonBridge = mockk(relaxed = true)) =
        LocalZipBackup(jsonBridge, Dispatchers.Unconfined)

    private fun writeZip(vararg entries: Pair<String, String>) {
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            entries.forEach { (name, body) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(body.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun listZipEntries(): List<String> =
        ZipInputStream(zipFile.inputStream()).use { zis ->
            val out = mutableListOf<String>()
            var e = zis.nextEntry
            while (e != null) {
                out += e.name
                e = zis.nextEntry
            }
            out
        }

    private fun readZipEntryBodies(): Map<String, String> =
        ZipInputStream(zipFile.inputStream()).use { zis ->
            val out = mutableMapOf<String, String>()
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) out[e.name] = zis.readBytes().toString(Charsets.UTF_8)
                e = zis.nextEntry
            }
            out
        }

    // ---- S14 残留归属收口（2026-09-28 第三窗口）夹具 ----
    //
    // 归属日志是 copyWithStaging 在保护性换入的第一次破坏性 rename 之前写入的
    // {dst 相对路径, backup 文件名, staging 文件名} 列表，是「本工具 in-flight」
    // 的唯一可靠证据。测试用固定 nano 值直接预置与真实写入完全相同的 JSON。
    // 名称字面量在此独立于生产常量写死：若生产端改名，用例会立刻失败。

    private fun journalFile(booksDir: File) = File(booksDir, ".cra-restore-journal.json")

    private fun writeJournal(booksDir: File, entries: List<Triple<String, String, String>>) {
        val body = entries.joinToString(",") { (rel, backup, staging) ->
            """{"rel":"$rel","backup":"$backup","staging":"$staging"}"""
        }
        journalFile(booksDir).writeText("[$body]")
    }

    private fun readJournalRaw(booksDir: File): String? =
        journalFile(booksDir).takeIf { it.isFile }?.readText()

    // ---- ZipSlip / 路径工具（P1-6 安全关键）----

    @Test
    fun `sanitizeEntryName strips traversal and backslashes`() {
        assertEquals("a/b.txt", subject().sanitizeEntryName("a/../../b.txt"))
        assertEquals("a/b", subject().sanitizeEntryName("a\\b"))
        assertEquals("b", subject().sanitizeEntryName("../b"))
        assertEquals("etc/passwd", subject().sanitizeEntryName("/etc/passwd"))
        assertFalse(subject().sanitizeEntryName("a/../b").contains(".."))
    }

    @Test
    fun `relativePathOf computes subpath with forward slashes`() {
        val base = File("/data/books")
        val child = File("/data/books/sub/one.txt")
        assertEquals("sub/one.txt", subject().relativePathOf(base, child))
    }

    @Test
    fun `relativePathOf throws when child escapes base`() {
        val base = File("/data/books")
        val child = File("/data/evil.txt")
        assertThrows(IllegalArgumentException::class.java) {
            subject().relativePathOf(base, child)
        }
    }

    // ---- 导出打包 ----

    @Test
    fun `exportZip packs backup json and books entries`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        File(booksDir, "foo.txt").writeText("hello")
        File(booksDir, "nested").also { it.mkdirs() }
        File(booksDir, "nested/bar.md").writeText("world")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        coEvery { jsonBridge.exportToString(any()) } returns "{\"schemaVersion\":1}"

        subject(jsonBridge).exportZip(fakeContext(), mockk<Uri>())

        val entries = listZipEntries()
        assertTrue("应包含 backup.json", entries.contains("backup.json"))
        assertTrue("应包含 books/foo.txt", entries.contains("books/foo.txt"))
        assertTrue("应包含嵌套 books/nested/bar.md", entries.contains("books/nested/bar.md"))
    }

    // ---- 导入解包 ----

    @Test
    fun `importZip invokes jsonBridge importFromString once`() = runTest {
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        var called = false
        // LocalZipBackup 现以显式回调（第三参）调用 importFromString 取回合并结果（S14）
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers { called = true }

        writeZip("backup.json" to "{\"schemaVersion\":1}", "books/x.txt" to "hi")

        subject(jsonBridge).importZip(fakeContext(), mockk<Uri>())

        assertTrue("解包后应调用 JsonBridge 入库", called)
    }

    @Test
    fun `importZip rejects zip missing backup json`() = runTest {
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        var called = false
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers { called = true }

        // 只有 books 入口，缺少 backup.json
        writeZip("books/x.txt" to "hi")

        val result = runCatching {
            subject(jsonBridge).importZip(fakeContext(), mockk<Uri>())
        }

        assertTrue("缺少 backup.json 应拒绝导入", result.isFailure)
        assertFalse("应未触发入库", called)
    }

    // ---- S14 安全修复：staging 替换 + 版本感知复制 ----

    /**
     * 成功路径：目标文件经 staging 完整复制并校验后原子替换，无 staging 残留。
     * 旧实现直接对 dst outputStream 写入——成功时结果相同，本用例锁定新实现
     * 的额外不变量（无残留中间文件）。
     */
    @Test
    fun `staged copy replaces target and leaves no staging residue`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("OLD")

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEW BYTES")

        subject().importZip(fakeContext(), mockk<Uri>())

        assertEquals("NEW BYTES", dst.readText())
        assertTrue(
            "替换成功后不得留下 staging 或保护性 backup 残留",
            dst.parentFile!!.listFiles()!!.none { it.name.endsWith(".staging") || it.name.contains(".restore-backup-") },
        )
    }

    /**
     * 旧行为可复现失败：直接对 dst 写流时 FileOutputStream 先截断既有文件，
     * 写中途失败即损坏唯一完好旧文件。新行为把写入限制在 staging 上，
     * 用测试接缝在 staging 写入中途抛出——断言旧目标逐字节保留、staging 被清理。
     */
    @Test
    fun `mid write failure via seam keeps old target intact`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val oldBody = "OLD-UNIQUE-CONTENT-THAT-MUST-NOT-BE-TRUNCATED"
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText(oldBody)

        val backup = subject()
        backup.fileCopyOperation = { input, _ ->
            val buf = ByteArray(4)
            input.read(buf)
            throw java.io.IOException("S14 注入：复制中途失败")
        }

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "replacement bytes that must never reach target")

        val result = runCatching { backup.importZip(fakeContext(), mockk<Uri>()) }

        assertTrue("注入的写中途失败必须上抛", result.exceptionOrNull() is java.io.IOException)
        assertEquals("旧目标必须逐字节保留", oldBody, dst.readText())
        assertTrue(
            "失败后不得留下 staging 或保护性 backup 残留",
            dst.parentFile!!.listFiles()!!.none { it.name.endsWith(".staging") || it.name.contains(".restore-backup-") },
        )
    }

    /**
     * 替换失败（跨平台）：目标位置被目录占用时直接拒绝替换——目录内容来历不明，
     * 不得被搬走后删除，也不得退回直接写流。断言异常、目录原样保留、无任何残留。
     */
    @Test
    fun `replace into directory target throws and preserves the obstacle`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val obstacle = File(booksDir, "b1/content.txt")
        obstacle.mkdirs()

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "must not replace a directory")

        val result = runCatching { subject().importZip(fakeContext(), mockk<Uri>()) }

        assertTrue("目录目标必须被拒绝替换", result.exceptionOrNull() is IllegalStateException)
        assertTrue("目录目标必须原样保留", obstacle.isDirectory)
        assertTrue(
            "不得留下 staging 或保护性 backup 残留",
            obstacle.parentFile!!.listFiles()!!.none { it.name.endsWith(".staging") || it.name.contains(".restore-backup-") },
        )
    }

    /**
     * 版本感知：DB 合并结果中被跳过（本地较新）的书籍，其源文件不得被备份旧字节
     * 覆盖；被接受的书籍照常恢复。保护依据是 [ImportMergeResult] 的实际合并结果，
     * 不看 ZIP 是否含文件。
     */
    @Test
    fun `files of skipped newer books are not overwritten by backup bytes`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val skippedFile = File(booksDir, "skipped/content.txt")
        skippedFile.parentFile!!.mkdirs()
        skippedFile.writeText("LOCAL-NEWER")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        val skippedPath = skippedFile.absolutePath
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            thirdArg<(ImportMergeResult) -> Unit>()(
                ImportMergeResult(
                    acceptedBookIds = setOf("b-accepted"),
                    skippedBookContentPaths = listOf(skippedPath),
                ),
            )
        }

        writeZip(
            "backup.json" to "{}",
            "books/skipped/content.txt" to "BACKUP-OLD",
            "books/accepted/content.txt" to "BACKUP-FOR-ACCEPTED",
        )

        subject(jsonBridge).importZip(fakeContext(), mockk<Uri>())

        assertEquals("本地较新书籍的文件必须原样保留", "LOCAL-NEWER", skippedFile.readText())
        assertEquals(
            "被接受书籍的文件必须照常恢复",
            "BACKUP-FOR-ACCEPTED",
            File(booksDir, "accepted/content.txt").readText(),
        )
    }

    // ---- S14 后提交窗口划分：隔离故障注入（W1/W2/W3/W4）----
    //
    // 把「DB 提交后、逐文件复制」的剩余非原子窗口按失败点精确划分，并给每个窗口
    // 一条可判别的 JVM 证据。DB 在 JVM 夹具中以 relaxed mock 表达：
    // 「importFromString 正常返回 + 回调收到 ImportMergeResult」即「事务已提交」，
    // 「importFromString 抛异常」即「事务回滚 / 未提交」。真实 Room 回滚、进程被杀
    // 后的 SQLite 恢复与跨 DB/文件整包原子性不属于 JVM 可证明范围，本节不宣称。

    /** 断言真实 books 目录下没有任何 staging / 保护性 backup 残留（含子目录）。 */
    private fun assertNoRestoreResidue(booksDir: File) {
        val residue = booksDir.walkTopDown()
            .filter { it.isFile }
            .filter { it.name.endsWith(".staging") || it.name.contains(".restore-backup-") }
            .toList()
        assertTrue("不得留下 staging 或保护性 backup 残留：$residue", residue.isEmpty())
    }

    /** 断言 cacheDir 下没有本类遗留的 zip-restore-* 临时目录（finally 清理生效）。 */
    private fun assertNoRestoreTmpLeft() {
        val left = cacheDir.listFiles()?.filter { it.name.startsWith("zip-restore-") } ?: emptyList()
        assertTrue("importZip 返回后不得遗留 zip-restore-* 临时目录：$left", left.isEmpty())
    }

    /**
     * W1-a（DB 事务失败）：importFromString 抛异常（等价于事务回滚）时，恢复必须
     * 整体放弃——真实 books 目录一个字节都不动、无任何复制残留、临时目录被清理。
     * 判别点：合并结果回调从未被调用（DB 未提交）。
     */
    @Test
    fun `dbImportFailureBeforeCommitLeavesFilesAndCacheUntouched`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val localOld = File(booksDir, "b1/content.txt")
        localOld.parentFile!!.mkdirs()
        localOld.writeText("LOCAL-OLD")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        var mergeReports = 0
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            throw IllegalStateException("S14 注入：DB 事务失败回滚")
        }

        writeZip(
            "backup.json" to "{}",
            "books/b1/content.txt" to "BACKUP-OLD",
            "books/fresh/content.txt" to "BACKUP-FRESH",
        )

        val result = runCatching { subject(jsonBridge).importZip(fakeContext(), mockk<Uri>()) }

        assertTrue("DB 失败必须上抛", result.exceptionOrNull() is IllegalStateException)
        assertEquals("DB 未提交：合并结果回调不得被调用", 0, mergeReports)
        assertEquals("既有旧文件必须逐字节保留", "LOCAL-OLD", localOld.readText())
        assertFalse("不得创建任何新书籍文件", File(booksDir, "fresh/content.txt").exists())
        assertNoRestoreResidue(booksDir)
        assertNoRestoreTmpLeft()
    }

    /**
     * W1-b（解压失败）：损坏的 ZIP 流在解压阶段抛异常时，同样必须 DB 未触碰、
     * 旧文件不动、临时目录清理。与 W1-a 共同覆盖「DB 提交前失败」的两个子窗口
     * （解压段 / DB 段），缺 backup.json 的拒绝已由既有用例覆盖。
     */
    @Test
    fun `corruptZipFailsBeforeDbAndLeavesFilesAndCacheUntouched`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val localOld = File(booksDir, "b1/content.txt")
        localOld.parentFile!!.mkdirs()
        localOld.writeText("LOCAL-OLD")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        var mergeReports = 0
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers { mergeReports++ }

        // 截断的 ZIP 头部字节：无论 ZipInputStream 在哪一步失败（或按 EOF 拒绝），
        // 结果必须是导入失败且零副作用。
        zipFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00))

        val result = runCatching { subject(jsonBridge).importZip(fakeContext(), mockk<Uri>()) }

        assertTrue("损坏 ZIP 必须导入失败", result.isFailure)
        assertEquals("DB 不得被触碰", 0, mergeReports)
        assertEquals("既有旧文件必须逐字节保留", "LOCAL-OLD", localOld.readText())
        assertNoRestoreResidue(booksDir)
        assertNoRestoreTmpLeft()
    }

    /**
     * W2（提交后首个文件失败）：DB 已提交（合并结果已回报一次）后，第一个文件的
     * staging 复制即失败——此时必须零目标文件被替换：本地旧文件逐字节保留、全新
     * 书籍文件不出现、无残留、临时目录清理。
     * 判别点：mergeReports == 1（与 W1 区分：DB 已提交）而文件集合完全未变
     * （与 W3 区分：尚无任何文件完成替换）。
     */
    @Test
    fun `firstFileFailureAfterCommitKeepsAllTargetsUntouchedWhileMergeResultReported`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val localOld = File(booksDir, "b1/content.txt")
        localOld.parentFile!!.mkdirs()
        val localOldBody = "LOCAL-OLD"
        localOld.writeText(localOldBody)

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        var mergeReports = 0
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            mergeReports++
            thirdArg<(ImportMergeResult) -> Unit>()(ImportMergeResult())
        }

        val backup = subject(jsonBridge)
        var copyAttempts = 0
        backup.fileCopyOperation = { _, _ ->
            copyAttempts++
            throw java.io.IOException("S14 注入：首个文件复制失败")
        }

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "BACKUP-OLD", "books/fresh/content.txt" to "BACKUP-FRESH")

        val result = runCatching { backup.importZip(fakeContext(), mockk<Uri>()) }

        assertTrue("注入失败必须上抛", result.exceptionOrNull() is java.io.IOException)
        assertEquals("DB 已提交：合并结果应恰好回报一次", 1, mergeReports)
        assertEquals("首个文件即失败：staging 复制只尝试了一次", 1, copyAttempts)
        assertEquals("本地旧文件必须逐字节保留", localOldBody, localOld.readText())
        assertFalse("全新书籍文件不得出现", File(booksDir, "fresh/content.txt").exists())
        assertNoRestoreResidue(booksDir)
        assertNoRestoreTmpLeft()
    }

    /**
     * W3（复制一部分后失败）：第一个文件完成替换、第二个文件 staging 失败——
     * 已完成者保持备份字节、未到达者缺席、受保护（本地较新）文件不被波及、
     * 无残留。这正是「DB=备份世界 ∪ 保留行、文件=新旧混合」中间态的判别证据：
     * 每个已存在的文件要么是完整备份字节、要么是原本地字节，绝无半写入。
     */
    @Test
    fun `partialCopyFailureRestoresOnlyCompletedFilesAndLeavesUnreachedAbsent`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val protectedFile = File(booksDir, "skipped/content.txt")
        protectedFile.parentFile!!.mkdirs()
        protectedFile.writeText("LOCAL-NEWER")
        val freshA = File(booksDir, "freshA/content.txt")
        val freshB = File(booksDir, "freshB/content.txt")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        var mergeReports = 0
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            mergeReports++
            thirdArg<(ImportMergeResult) -> Unit>()(
                ImportMergeResult(skippedBookContentPaths = listOf(protectedFile.absolutePath)),
            )
        }

        val backup = subject(jsonBridge)
        var copyAttempts = 0
        backup.fileCopyOperation = { input, output ->
            copyAttempts++
            if (copyAttempts >= 2) throw java.io.IOException("S14 注入：第二个文件复制失败")
            input.copyTo(output)
        }

        writeZip(
            "backup.json" to "{}",
            "books/freshA/content.txt" to "BACKUP-A",
            "books/freshB/content.txt" to "BACKUP-B",
            "books/skipped/content.txt" to "BACKUP-OLD",
        )

        val result = runCatching { backup.importZip(fakeContext(), mockk<Uri>()) }

        assertTrue("注入失败必须上抛", result.exceptionOrNull() is java.io.IOException)
        assertEquals("DB 已提交：合并结果应恰好回报一次", 1, mergeReports)
        // 受保护文件：与遍历顺序无关，一律不覆盖。
        assertEquals("本地较新书籍的文件必须原样保留", "LOCAL-NEWER", protectedFile.readText())
        // 恰好一个全新文件完成了替换，另一个未到达——顺序无关的集合级断言。
        val completed = listOf(freshA to "BACKUP-A", freshB to "BACKUP-B").count { (f, body) ->
            f.exists() && f.readText() == body
        }
        val absent = listOf(freshA, freshB).count { !it.exists() }
        assertEquals("恰好一个全新文件完成替换", 1, completed)
        assertEquals("另一个全新文件未到达、不得出现", 1, absent)
        assertNoRestoreResidue(booksDir)
        assertNoRestoreTmpLeft()
    }

    /**
     * W3 收敛（可重试恢复的 JVM 证明）：部分失败后，同一 subject、同一 ZIP 再次
     * importZip——文件级收敛到备份字节，受保护文件仍保持本地较新字节，无残留。
     * DB 侧幂等（revision 门控）由既有 MuMu 用例 6 与 SyncMergePolicy 证据覆盖，
     * 本用例只以「importFromString 被调用两次」记录重导入口径，不宣称真实 Room 幂等。
     */
    @Test
    fun `reimportAfterPartialFailureConvergesToBackupBytesAndKeepsProtectedFiles`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val protectedFile = File(booksDir, "skipped/content.txt")
        protectedFile.parentFile!!.mkdirs()
        protectedFile.writeText("LOCAL-NEWER")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        var mergeReports = 0
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            mergeReports++
            thirdArg<(ImportMergeResult) -> Unit>()(
                ImportMergeResult(skippedBookContentPaths = listOf(protectedFile.absolutePath)),
            )
        }

        val backup = subject(jsonBridge)
        var copyAttempts = 0
        backup.fileCopyOperation = { input, output ->
            copyAttempts++
            if (copyAttempts >= 2) throw java.io.IOException("S14 注入：首轮部分失败")
            input.copyTo(output)
        }

        writeZip(
            "backup.json" to "{}",
            "books/freshA/content.txt" to "BACKUP-A",
            "books/freshB/content.txt" to "BACKUP-B",
            "books/skipped/content.txt" to "BACKUP-OLD",
        )

        assertTrue("首轮必须失败", runCatching { backup.importZip(fakeContext(), mockk<Uri>()) }.isFailure)

        // 重导：还原默认复制行为（消除注入），同一 ZIP 再导一次。
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        backup.importZip(fakeContext(), mockk<Uri>())

        assertEquals("重导入应再次触发 DB 合并", 2, mergeReports)
        assertEquals("文件级收敛：freshA = 备份字节", "BACKUP-A", File(booksDir, "freshA/content.txt").readText())
        assertEquals("文件级收敛：freshB = 备份字节", "BACKUP-B", File(booksDir, "freshB/content.txt").readText())
        assertEquals("版本感知在重导时仍生效：本地较新文件不被旧备份覆盖", "LOCAL-NEWER", protectedFile.readText())
        assertNoRestoreResidue(booksDir)
        assertNoRestoreTmpLeft()
    }

    /**
     * W4 残留表征（进程被杀的后验子集）：进程终止不会执行 finally，因此会留下
     * books 目录内的 *.staging 残留与 cacheDir 的 zip-restore-* 临时目录。真实
     * kill -9 不可由 JVM 夹具证明（明确记为未验证），本用例证明的是**残留的后验
     * 后果**：预置的残留不阻塞下一次恢复（目标文件照常替换），但当前产品不清理
     * 旧残留、旧残留临时目录也原样保留——这是本轮表征的现状缺口，供收口决策引用。
     */
    @Test
    fun `residueFromKilledRestoreDoesNotBlockRecoveryAndIsNotSwept`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val localOld = File(booksDir, "b1/content.txt")
        localOld.parentFile!!.mkdirs()
        localOld.writeText("LOCAL-OLD")
        // 模拟一次被杀恢复留下的 staging 残留（nanoTime 固定值，与本次运行不冲突）。
        val stagingResidue = File(booksDir, "b1/content.txt.restore-1000000000000.staging")
        stagingResidue.writeText("PARTIAL-BYTES-FROM-A-KILLED-RUN")
        // 模拟被杀恢复遗留的 cacheDir 临时目录。
        val staleTmp = File(cacheDir, "zip-restore-999").also { it.mkdirs() }
        File(staleTmp, "orphan").writeText("x")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            thirdArg<(ImportMergeResult) -> Unit>()(ImportMergeResult())
        }

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "BACKUP-NEW", "books/fresh/content.txt" to "BACKUP-FRESH")

        subject(jsonBridge).importZip(fakeContext(), mockk<Uri>())

        assertEquals("残留不得阻塞恢复：既有文件照常替换", "BACKUP-NEW", localOld.readText())
        val fresh = File(booksDir, "fresh/content.txt")
        assertTrue("残留不得阻塞恢复：全新文件照常出现", fresh.exists())
        assertEquals("BACKUP-FRESH", fresh.readText())
        assertTrue("现状表征：旧 staging 残留不被清理（缺口，待收口轮裁决）", stagingResidue.isFile)
        assertTrue("现状表征：旧 zip-restore-* 临时目录不被清理（cache 层，低风险）", staleTmp.isDirectory)
        val newResidue = booksDir.walkTopDown()
            .filter { it.isFile && it != stagingResidue }
            .filter { it.name.endsWith(".staging") || it.name.contains(".restore-backup-") }
            .toList()
        assertTrue("本次运行自身不得新增 staging/backup 残留：$newResidue", newResidue.isEmpty())
        assertEquals("本次运行自己的临时目录必须被清理", 1, cacheDir.listFiles()?.count { it.name.startsWith("zip-restore-") })
    }

    // ---- S14 crash residue 安全收口（2026-09-28 第二窗口）----
    //
    // Codex 复核指出的关键状态：保护性换入先把 dst 改名为 *.restore-backup-*、再把
    // staging 改名为 dst。进程死于两次 rename 之间 → dst 缺失、backup 是唯一完整旧
    // 字节；死于新字节落位后、backup 删除前 → dst 与 backup 并存。因此：不得按后缀
    // 盲删 backup；恢复优先；状态不明保留并报错；导出只过滤能可靠认定的残留。
    // 真实 kill 不可由 JVM 夹具模拟——本节全部用「预置残留状态」驱动，真实 kill/重启
    // 记为未验证（见配套报告）。

    /**
     * T1（判别性，归属收口轮改写）：有归属日志的 crash 现场——dst 缺失、backup 与
     * staging 并存（kill 于两次 rename 之间）。导入开始时必须凭日志「完成被打断的
     * 换入」：日志证明 staging 已通过长度校验且 DB 事务已提交，因此 staging（完整
     * 新字节）优先落位为 dst、backup（被取代旧字节）按证据补完 ⑤ 清理、条目剪枝。
     * 恢复不得依赖 ZIP 是否含该书。
     */
    @Test
    fun `importRecoversMissingTargetFromLoneBackupResidue`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val backupResidue = File(booksDir, "b1/content.txt.restore-backup-1000000000000")
        backupResidue.parentFile!!.mkdirs()
        backupResidue.writeText("OLD-BYTES")
        val stagingResidue = File(booksDir, "b1/content.txt.restore-1000000000001.staging")
        stagingResidue.writeText("NEW-COMPLETE-BYTES")
        writeJournal(
            booksDir,
            listOf(
                Triple(
                    "b1/content.txt",
                    "content.txt.restore-backup-1000000000000",
                    "content.txt.restore-1000000000001.staging",
                ),
            ),
        )

        // b1 不在本次备份内：恢复只依据 books 目录与归属日志。
        writeZip("backup.json" to "{}", "books/b2/content.txt" to "B2")

        subject().importZip(fakeContext(), mockk<Uri>())

        val recovered = File(booksDir, "b1/content.txt")
        assertTrue("dst 缺失时必须凭日志完成被打断的换入", recovered.isFile)
        assertEquals(
            "恢复优先取日志归属的 staging（已校验完整新字节，与已提交 DB 一致）",
            "NEW-COMPLETE-BYTES",
            recovered.readText(),
        )
        assertFalse("backup 旧字节在换入完成后已被取代，按证据补完 ⑤ 清理", backupResidue.exists())
        assertFalse("staging 被落位消费（rename，而非复制后删除）", stagingResidue.exists())
        assertEquals("归属日志应收敛为空", "[]", readJournalRaw(booksDir)?.trim())
        assertEquals("B2", File(booksDir, "b2/content.txt").readText())
    }

    /**
     * T2（回归守卫）：dst 与 backup 并存（新字节已落位、backup 删除前被杀）。
     * dst 是已通过长度校验的完整新字节，优先于 backup；替换照常进行，植入的旧
     * backup 必须逐字节保留——锁定「绝不按后缀盲删 backup」。
     */
    @Test
    fun `backupAlongsideExistingTargetIsPreservedThroughReplace`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("NEW-LANDED")
        val plantedBackup = File(booksDir, "b1/content.txt.restore-backup-1000000000000")
        plantedBackup.writeText("OLD-BYTES-KEEP-ME")

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "ZIP-BYTES")

        subject().importZip(fakeContext(), mockk<Uri>())

        assertEquals("ZIP-BYTES", dst.readText())
        assertTrue("并存 backup 必须原样保留，不得盲删", plantedBackup.isFile)
        assertEquals("OLD-BYTES-KEEP-ME", plantedBackup.readText())
        val otherBackups = File(booksDir, "b1").listFiles()!!
            .filter { it.name.contains(".restore-backup-") && it != plantedBackup }
        assertTrue("本次换入自己的 backup 必须已清理，不得新增残留：$otherBackups", otherBackups.isEmpty())
    }

    /**
     * T3（回归守卫）：只有 staging 残留（半写入或已校验未落位，内容不可信）。
     * staging 不含「最近已提交」语义的字节，不作为恢复源；删除的可靠认定需要
     * 「恢复日志」元数据（当前不存在）——原样保留，且不得阻塞正常替换。
     * 覆盖 dst 存在与 dst 缺失两种形态。
     */
    @Test
    fun `stagingOnlyResidueIsPreservedAndDoesNotBlockReplace`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dstA = File(booksDir, "a/content.txt")
        dstA.parentFile!!.mkdirs()
        dstA.writeText("A-OLD")
        val stagingA = File(booksDir, "a/content.txt.restore-1000000000000.staging")
        stagingA.writeText("A-PARTIAL")
        val stagingB = File(booksDir, "b/content.txt.restore-1000000000001.staging")
        stagingB.parentFile!!.mkdirs()
        stagingB.writeText("B-JUNK")

        writeZip("backup.json" to "{}", "books/a/content.txt" to "A-ZIP", "books/b/content.txt" to "B-ZIP")

        subject().importZip(fakeContext(), mockk<Uri>())

        assertEquals("A-ZIP", dstA.readText())
        assertEquals("B-ZIP", File(booksDir, "b/content.txt").readText())
        assertEquals("dst 在：staging 残留原样保留", "A-PARTIAL", stagingA.readText())
        assertEquals("dst 缺失：staging 残留同样保留（不作为恢复源）", "B-JUNK", stagingB.readText())
    }

    /**
     * T4（判别性守卫）：普通书源文件名称恰好**形近**残留时不得被当成残留。
     * 识别只认本工具严格格式（`.restore-<≥13位数字>.staging` / `.restore-backup-<≥13位数字>`）；
     * 非数字后缀、位数不足、分隔符变体一律不匹配。完全同格式的命名学冲突需要
     * 「恢复日志」元数据才能消除（见报告接缝声明），本用例锁定形近空间的安全。
     */
    @Test
    fun `bookFilesWithResidueLikeNamesAreNeverTouched`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dir = File(booksDir, "b9").also { it.mkdirs() }
        File(dir, "content.txt").writeText("REAL")
        val decoyNames = listOf(
            "content.txt.restore-backup-重要",
            "content.txt.restore-backup-12ab",
            "content.txt.restore-123.staging",
            "content.txt.restore_backup_123.staging",
            "content.txt.restore-backup-",
        )
        decoyNames.forEach { File(dir, it).writeText("DECOY") }

        writeZip("backup.json" to "{}", "books/b9/content.txt" to "REAL-ZIP")

        subject().importZip(fakeContext(), mockk<Uri>())

        assertEquals("REAL-ZIP", File(dir, "content.txt").readText())
        decoyNames.forEach { name ->
            assertEquals("形近文件必须逐字节保留：$name", "DECOY", File(dir, name).readText())
        }
    }

    /**
     * T5（重导收敛 × 日志残留并存，归属收口轮改写）：备份含该书时，首轮导入即完成
     * 「日志恢复 + 替换」——日志归属的 staging 被恢复路径落位消费、backup 按证据
     * 清理，不再滞留；同 ZIP 再次导入幂等收敛，且不新增任何 backup 残留。
     */
    @Test
    fun `reimportWithResidueConvergesAndConsumesBackupKeepsStaging`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val backupResidue = File(booksDir, "b1/content.txt.restore-backup-1000000000000")
        backupResidue.parentFile!!.mkdirs()
        backupResidue.writeText("OLD")
        val stagingResidue = File(booksDir, "b1/content.txt.restore-1000000000001.staging")
        stagingResidue.writeText("JUNK")
        writeJournal(
            booksDir,
            listOf(
                Triple(
                    "b1/content.txt",
                    "content.txt.restore-backup-1000000000000",
                    "content.txt.restore-1000000000001.staging",
                ),
            ),
        )

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "ZIP-V")

        val backup = subject()
        backup.importZip(fakeContext(), mockk<Uri>())

        assertEquals("首轮：dst = 备份字节（恢复落位后立刻被本次导入替换）", "ZIP-V", File(booksDir, "b1/content.txt").readText())
        assertFalse("首轮：日志归属 backup 被证据清理消费", backupResidue.exists())
        assertFalse("首轮：日志归属 staging 被恢复路径落位消费", stagingResidue.exists())
        assertEquals("首轮：归属日志收敛为空", "[]", readJournalRaw(booksDir)?.trim())

        backup.importZip(fakeContext(), mockk<Uri>())

        assertEquals("重导：收敛到备份字节", "ZIP-V", File(booksDir, "b1/content.txt").readText())
        val backups = File(booksDir, "b1").listFiles()!!.filter { it.name.contains(".restore-backup-") }
        assertTrue("重导：不得留下 backup 残留：$backups", backups.isEmpty())
        assertEquals("重导：归属日志保持为空", "[]", readJournalRaw(booksDir)?.trim())
    }

    /**
     * T6（判别性，归属收口轮改写）：导出的过滤与打包以归属日志为唯一边界——
     * 日志归属的 staging 永不打包；日志归属的 backup 在 dst 存在时（已被取代）不打包；
     * 日志归属的 backup 在 dst 缺失时先复位 dst 再按正常书籍文件打包（备份链上
     * 拿到的是书本体，不是残留名）；无日志证据的同格式文件一律按普通文件打包
     * （漏打包才是数据丢失），形近命名同样照常打包。
     */
    @Test
    fun `exportFiltersOwnResidueKeepsOnlyCopyBackupAndLookalikes`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        File(booksDir, "b1/content.txt").let { it.parentFile!!.mkdirs(); it.writeText("REAL-1") }
        File(booksDir, "b1/content.txt.restore-1000000000000.staging").writeText("STAGING-JUNK")
        File(booksDir, "b2/old.txt").let { it.parentFile!!.mkdirs(); it.writeText("REAL-2") }
        File(booksDir, "b2/old.txt.restore-backup-1000000000000").writeText("SUPERSEDED-OLD")
        File(booksDir, "b3/only.txt.restore-backup-1000000000000").let { it.parentFile!!.mkdirs(); it.writeText("ONLY-KNOWN-BYTES") }
        File(booksDir, "b4/real.txt").let { it.parentFile!!.mkdirs(); it.writeText("REAL-4") }
        File(booksDir, "b4/real.txt.restore-backup-重要").writeText("DECOY-1")
        File(booksDir, "b4/real.txt.restore-123.staging").writeText("DECOY-2")
        writeJournal(
            booksDir,
            listOf(
                Triple("b1/content.txt", "content.txt.restore-backup-1000000000000", "content.txt.restore-1000000000000.staging"),
                Triple("b2/old.txt", "old.txt.restore-backup-1000000000000", "old.txt.restore-1000000000001.staging"),
                Triple("b3/only.txt", "only.txt.restore-backup-1000000000000", "only.txt.restore-1000000000002.staging"),
            ),
        )

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        coEvery { jsonBridge.exportToString(any()) } returns "{\"schemaVersion\":1}"

        subject(jsonBridge).exportZip(fakeContext(), mockk<Uri>())

        val bodies = readZipEntryBodies()
        assertTrue("正常书籍文件应打包", bodies.containsKey("books/b1/content.txt"))
        assertFalse("日志归属的 staging 残留（内容不可信）不得打包", bodies.containsKey("books/b1/content.txt.restore-1000000000000.staging"))
        assertTrue("正常书籍文件应打包", bodies.containsKey("books/b2/old.txt"))
        assertFalse("日志归属、dst 在的 backup（已被取代）不得打包", bodies.containsKey("books/b2/old.txt.restore-backup-1000000000000"))
        assertEquals(
            "dst 缺失的日志归属 backup 先复位 dst，备份链拿到正常书籍文件",
            "ONLY-KNOWN-BYTES",
            bodies["books/b3/only.txt"],
        )
        assertFalse("残留名复位后不应再出现在备份链上", bodies.containsKey("books/b3/only.txt.restore-backup-1000000000000"))
        assertTrue("正常书籍文件应打包", bodies.containsKey("books/b4/real.txt"))
        assertEquals("无日志证据的同格式文件必须按普通文件打包（漏打包才是数据丢失）", "DECOY-1", bodies["books/b4/real.txt.restore-backup-重要"])
        assertEquals("形近文件不得过滤", "DECOY-2", bodies["books/b4/real.txt.restore-123.staging"])
        assertEquals("可判定的日志条目应在导出前收敛为空", "[]", readJournalRaw(booksDir)?.trim())
    }

    // ---- S14 残留归属收口（2026-09-28 第三窗口）：严格同格式碰撞 / 超长数字 / 日志机制 ----
    //
    // Codex 复核指出的三个缺口：① exportZip 仅凭完整后缀跳过文件——普通书籍文件
    // 恰好同名时导出缺书（真正的数据丢失）；② importZip 仅凭文件名把普通文件改名；
    // ③ 超长数字 .toLong() 溢出使导入/导出崩溃。以下用例先红后绿，全部基于
    // 「完整命中残留格式的普通书籍文件」与「归属日志」两组夹具。

    /** C1（判别性）：完整命中残留格式但实为普通书籍文件——导出必须逐字节打包，绝不漏书。 */
    @Test
    fun `exportPacksOrdinaryFilesWithCompleteResidueFormatNames`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val ordinaryStaging = File(booksDir, "b1/notes.restore-1234567890123.staging")
        ordinaryStaging.parentFile!!.mkdirs()
        ordinaryStaging.writeText("ORDINARY-STAGING-NAME")
        File(booksDir, "b2/old.txt").let { it.parentFile!!.mkdirs(); it.writeText("ORDINARY-DST") }
        val ordinaryBackup = File(booksDir, "b2/old.txt.restore-backup-1234567890123")
        ordinaryBackup.writeText("ORDINARY-BACKUP-NAME")
        // 未写归属日志：本工具从未创建过这三个文件，命名学冲突无法排除

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        coEvery { jsonBridge.exportToString(any()) } returns "{}"
        subject(jsonBridge).exportZip(fakeContext(), mockk<Uri>())

        val bodies = readZipEntryBodies()
        assertEquals(
            "完整命中 staging 格式的普通书籍文件必须照常打包（否则导出缺书）",
            "ORDINARY-STAGING-NAME",
            bodies["books/b1/notes.restore-1234567890123.staging"],
        )
        assertEquals("ORDINARY-DST", bodies["books/b2/old.txt"])
        assertEquals(
            "完整命中 backup 格式（且同名 dst 存在）的普通书籍文件必须照常打包",
            "ORDINARY-BACKUP-NAME",
            bodies["books/b2/old.txt.restore-backup-1234567890123"],
        )
    }

    /** C2（判别性）：普通文件完整命中 backup 格式且 dst 缺失——导入绝不凭文件名擅自改名，并明确报告。 */
    @Test
    fun `importKeepsOrdinaryFileMatchingBackupFormatWhenTargetMissing`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val ordinary = File(booksDir, "b1/content.txt.restore-backup-1234567890123")
        ordinary.parentFile!!.mkdirs()
        ordinary.writeText("ORDINARY-BOOK-BYTES")

        writeZip("backup.json" to "{}", "books/other/content.txt" to "OTHER")

        subject().importZip(fakeContext(), mockk<Uri>())

        assertTrue("无归属证据时普通文件必须原名保留", ordinary.isFile)
        assertEquals("ORDINARY-BOOK-BYTES", ordinary.readText())
        assertFalse("不得凭文件名把普通文件改名为 dst", File(booksDir, "b1/content.txt").exists())
        val reported = AppLog.entries.value.any {
            it.level == AppLog.Level.WARN && it.message.contains("content.txt.restore-backup-1234567890123")
        }
        assertTrue("未自动清理的残留必须在诊断日志中明确报告", reported)
    }

    /** C3（判别性）：超长数字（远超 Long 范围）不得使导入或导出崩溃。 */
    @Test
    fun `superLongNanoDigitsDoNotCrashExportOrImport`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val longDigits = "1234567890123456789012345678901234567890" // 40 位纯数字
        val ordinary = File(booksDir, "b1/old.txt.restore-backup-$longDigits")
        ordinary.parentFile!!.mkdirs()
        ordinary.writeText("LONG-DIGITS-ORDINARY")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        coEvery { jsonBridge.exportToString(any()) } returns "{}"
        subject(jsonBridge).exportZip(fakeContext(), mockk<Uri>())

        assertEquals(
            "超长数字命名的普通文件必须照常打包",
            "LONG-DIGITS-ORDINARY",
            readZipEntryBodies()["books/b1/old.txt.restore-backup-$longDigits"],
        )

        // 导入路径的恢复扫描同样遍历同格式文件名，不得抛异常
        writeZip("backup.json" to "{}", "books/other/content.txt" to "OTHER")
        subject().importZip(fakeContext(), mockk<Uri>())

        assertTrue("导入后普通文件必须原样保留", ordinary.isFile)
        assertEquals("LONG-DIGITS-ORDINARY", ordinary.readText())
        assertFalse("超长数字同格式文件同样不得被改名为 dst", File(booksDir, "b1/old.txt").exists())
    }

    /** C4（判别性）：升级前旧版本留下的无日志残留——保留原名原字节并明确报告，绝不自动改名。 */
    @Test
    fun `unjournaledResidueFormatFilesAreKeptNotRenamedAndReported`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val backupResidue = File(booksDir, "b1/content.txt.restore-backup-1000000000000")
        backupResidue.parentFile!!.mkdirs()
        backupResidue.writeText("PRE-JOURNAL-OLD-BYTES")
        val stagingResidue = File(booksDir, "b1/content.txt.restore-1000000000001.staging")
        stagingResidue.writeText("PRE-JOURNAL-STAGING")
        // dst b1/content.txt 缺失、无归属日志：无可靠证据 → 宁可保留，明确报告未清理

        writeZip("backup.json" to "{}", "books/b2/content.txt" to "B2")

        subject().importZip(fakeContext(), mockk<Uri>())

        assertFalse("无证据：不得把 backup 改名为 dst（可能是恰好同名的普通书籍文件）", File(booksDir, "b1/content.txt").exists())
        assertEquals("旧字节必须原样保留", "PRE-JOURNAL-OLD-BYTES", backupResidue.readText())
        assertEquals("staging 同样原样保留", "PRE-JOURNAL-STAGING", stagingResidue.readText())
        val reported = AppLog.entries.value.any {
            it.level == AppLog.Level.WARN && it.message.contains("content.txt.restore-backup-1000000000000")
        }
        assertTrue("未自动清理的残留必须在诊断日志中明确报告", reported)
    }

    /** C5（判别性）：日志归属、dst 已存在的 backup（kill 于 ④ 后 ⑤ 前）——按证据补完 ⑤ 清理，条目剪枝。 */
    @Test
    fun `journalListedSupersededBackupIsCleanedWhenTargetPresent`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("NEW-LANDED")
        val backupResidue = File(booksDir, "b1/content.txt.restore-backup-1000000000000")
        backupResidue.writeText("OLD-BYTES")
        writeJournal(
            booksDir,
            listOf(
                Triple(
                    "b1/content.txt",
                    "content.txt.restore-backup-1000000000000",
                    "content.txt.restore-1000000000001.staging",
                ),
            ),
        )

        writeZip("backup.json" to "{}", "books/b2/content.txt" to "B2") // b1 不在 ZIP 内

        subject().importZip(fakeContext(), mockk<Uri>())

        assertEquals("dst 原样保留（ZIP 不含该书，不得被波及）", "NEW-LANDED", dst.readText())
        assertTrue("日志归属的 backup 已被取代（④ 完成态），按证据补完 ⑤ 清理", !backupResidue.exists())
        assertEquals("归属日志应收敛为空", "[]", readJournalRaw(booksDir)?.trim())
    }

    /** C6（判别性）：同一目标多轮 kill 累积多个日志条目——收敛到最近一次完整新字节，旧代文件按证据清理。 */
    @Test
    fun `multipleJournalEntriesForSameTargetConvergeToNewestBytes`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val b1 = File(booksDir, "b1/content.txt.restore-backup-1000000000000")
        b1.parentFile!!.mkdirs()
        b1.writeText("V1-OLD")
        val s1 = File(booksDir, "b1/content.txt.restore-1000000000001.staging")
        s1.writeText("V1-NEW")
        val b2 = File(booksDir, "b1/content.txt.restore-backup-2000000000000")
        b2.writeText("V2-OLD")
        val s2 = File(booksDir, "b1/content.txt.restore-2000000000001.staging")
        s2.writeText("V2-NEW")
        writeJournal(
            booksDir,
            listOf(
                Triple("b1/content.txt", "content.txt.restore-backup-1000000000000", "content.txt.restore-1000000000001.staging"),
                Triple("b1/content.txt", "content.txt.restore-backup-2000000000000", "content.txt.restore-2000000000001.staging"),
            ),
        )

        writeZip("backup.json" to "{}")

        subject().importZip(fakeContext(), mockk<Uri>())

        assertEquals("dst 必须收敛到最近一次换入的完整新字节", "V2-NEW", File(booksDir, "b1/content.txt").readText())
        assertTrue("旧代 backup（被两轮换入取代）按证据清理", !b1.exists())
        assertTrue("旧代 staging（同 op 无角色）按证据清理", !s1.exists())
        assertTrue("新一代 backup（dst 已由其 staging 落位）按证据清理", !b2.exists())
        assertFalse("新一代 staging 被落位消费", s2.exists())
        assertEquals("归属日志应收敛为空", "[]", readJournalRaw(booksDir)?.trim())
    }

    /** C7（异常路径）：日志文件残缺（进程死于写入中途）→ 按无证据保守处理：不失败、不改名、保留全部文件。 */
    @Test
    fun `corruptJournalFallsBackToKeepEverything`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val backupResidue = File(booksDir, "b1/content.txt.restore-backup-1000000000000")
        backupResidue.parentFile!!.mkdirs()
        backupResidue.writeText("OLD")
        journalFile(booksDir).writeText("{\"rel\": \"b1/cont") // 残缺 JSON

        writeZip("backup.json" to "{}")

        val result = runCatching { subject().importZip(fakeContext(), mockk<Uri>()) }

        assertFalse("损坏日志必须按无证据处理，不得让导入失败", result.isFailure)
        assertFalse("无证据：不得改名", File(booksDir, "b1/content.txt").exists())
        assertEquals("OLD", backupResidue.readText())
    }

    /** C8（日志自愈）：条目引用的文件全部不存在（消费完成后剪枝被杀中断）→ 剪枝，无副作用。 */
    @Test
    fun `staleJournalEntryWithoutFilesIsPruned`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        writeJournal(
            booksDir,
            listOf(
                Triple(
                    "b1/content.txt",
                    "content.txt.restore-backup-1000000000000",
                    "content.txt.restore-1000000000001.staging",
                ),
            ),
        )

        writeZip("backup.json" to "{}", "books/b2/content.txt" to "B2")

        subject().importZip(fakeContext(), mockk<Uri>())

        assertEquals("无文件可恢复的陈旧条目应被剪枝", "[]", readJournalRaw(booksDir)?.trim())
        assertEquals("B2", File(booksDir, "b2/content.txt").readText())
    }

    /** C9（日志边界）：归属日志是工具元数据——导出永不打包；外来 ZIP 不得借同名 entry 注入/覆盖本地日志。 */
    @Test
    fun `journalFileIsNeverPackedAndNeverImportedFromZip`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        File(booksDir, "real.txt").writeText("REAL")
        // 故意写一个无效条目：验证导入端的日志读取只会丢弃无效条目，不被外来内容劫持
        journalFile(booksDir).writeText("""[{"rel":"evil/x","backup":"y","staging":"z"}]""")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        coEvery { jsonBridge.exportToString(any()) } returns "{}"
        subject(jsonBridge).exportZip(fakeContext(), mockk<Uri>())
        assertFalse("归属日志不得进入备份链", listZipEntries().contains("books/.cra-restore-journal.json"))

        writeZip(
            "backup.json" to "{}",
            "books/injected.txt" to "INJ",
            "books/.cra-restore-journal.json" to "FORGED-BY-FOREIGN-ZIP",
        )
        subject().importZip(fakeContext(), mockk<Uri>())

        assertTrue("普通文件照常导入", File(booksDir, "injected.txt").readText() == "INJ")
        assertEquals(
            "外来 ZIP 不得覆盖本地归属日志（本地无效条目保留原样，每次读取被丢弃并告警）",
            """[{"rel":"evil/x","backup":"y","staging":"z"}]""",
            readJournalRaw(booksDir)?.trim(),
        )
    }

    /** C10（导出兜底）：日志归属、dst 缺失、staging 缺席的 backup——导出前先复位 dst，备份链拿到书本体。 */
    @Test
    fun `exportResolvesJournalBackedMissingTargetBeforePacking`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val backupResidue = File(booksDir, "b1/only.txt.restore-backup-1000000000000")
        backupResidue.parentFile!!.mkdirs()
        backupResidue.writeText("ONLY-KNOWN-BYTES")
        writeJournal(
            booksDir,
            listOf(
                Triple("b1/only.txt", "only.txt.restore-backup-1000000000000", "only.txt.restore-1000000000001.staging"),
            ),
        )

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        coEvery { jsonBridge.exportToString(any()) } returns "{}"
        subject(jsonBridge).exportZip(fakeContext(), mockk<Uri>())

        assertEquals(
            "导出前先按证据复位 dst（staging 缺席时以 backup 旧字节兜底）",
            "ONLY-KNOWN-BYTES",
            File(booksDir, "b1/only.txt").readText(),
        )
        val bodies = readZipEntryBodies()
        assertEquals("备份链上拿到的是正常书籍文件", "ONLY-KNOWN-BYTES", bodies["books/b1/only.txt"])
        assertFalse("残留名不应再出现在备份链上", bodies.containsKey("books/b1/only.txt.restore-backup-1000000000000"))
        assertEquals("归属日志应收敛为空", "[]", readJournalRaw(booksDir)?.trim())
    }

    // ---- S14 尾部窗口返修（2026-09-28 第四窗口）：③–④ kill 窗口 + 日志写入 fail-closed ----
    //
    // Codex 复核指出上一轮绿测未覆盖的两个真实点：
    // ① journalAdd 之后、dst.renameTo(backup) 之前被杀：dst 仍是旧字节、**完整**
    //    staging 已存在、backup 尚未产生。旧 recoverCrashResidues 仅在 !dst.exists()
    //    时落位 staging，随后 dst.isFile 分支删除 staging 并剪枝日志，把旧正文留在
    //    dst——与已提交 DB 不一致（已提交 DB 期望的是 staging 的完整新字节）。
    // ② writeJournal 吞掉写入失败、journalAdd 继续执行破坏性 rename：无法声称日志
    //    「先于每次破坏性 rename 持久化」。要求 fail-closed（或经验证的等价保护），
    //    保留旧字节与主异常。
    // 真实 kill -9 仍不可由 JVM 夹具证明：用例以「预置中间态 / 预置不可写」驱动，
    // 真实 kill 与 SQLite/WAL 组合态记为未验证（见配套报告）。

    /**
     * W5（判别性）：kill 于 ③（journalAdd）之后、④（dst.renameTo(backup)）之前——
     * 归属日志在案、dst 仍是被取代的旧字节、完整 staging 在、backup 尚未产生。
     * 恢复必须**补完这一次被打断的换入**：staging（已校验完整、与已提交 DB 一致）
     * 落位为 dst，旧字节移入 backup 后按证据补完 ⑤ 清理，条目剪枝。旧实现把
     * 「dst 存在」一律当 ⑤ 补完 → 删 staging、剪枝日志、dst 停在被取代的旧正文
     * （本用例在旧源码上红：断言 dst == staging 完整新字节失败）。
     */
    @Test
    fun `killedAfterJournalBeforeFirstRenameConvergesToStagingBytes`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("OLD-SUPERSEDED-BYTES")
        val stagingResidue = File(booksDir, "b1/content.txt.restore-1000000000001.staging")
        stagingResidue.writeText("NEW-COMPLETE-BYTES")
        // backup 尚未产生：进程恰在 ③ 之后、④ 之前被杀。
        writeJournal(
            booksDir,
            listOf(
                Triple(
                    "b1/content.txt",
                    "content.txt.restore-backup-1000000000000",
                    "content.txt.restore-1000000000001.staging",
                ),
            ),
        )

        // b1 不在本次备份内：恢复只依据 books 目录与归属日志。
        writeZip("backup.json" to "{}", "books/b2/content.txt" to "B2")

        subject().importZip(fakeContext(), mockk<Uri>())

        assertEquals(
            "dst 必须收敛到已提交 DB 对应的完整新字节（补完被打断的换入，而非删 staging 保留旧正文）",
            "NEW-COMPLETE-BYTES",
            dst.readText(),
        )
        assertFalse("staging 被落位消费（rename，而非复制后删除）", stagingResidue.exists())
        assertFalse(
            "旧字节移入的 backup 在 ⑤ 补完中被按证据清理",
            File(booksDir, "b1/content.txt.restore-backup-1000000000000").exists(),
        )
        assertEquals("归属日志应收敛为空", "[]", readJournalRaw(booksDir)?.trim())
        assertEquals("B2", File(booksDir, "b2/content.txt").readText())
        assertNoRestoreResidue(booksDir)
    }

    /**
     * W6（判别性，fail-closed）：归属日志无法持久化时，绝不允许随后的破坏性 rename。
     * 这里用「同名目录占用日志路径」制造确定性写失败（FileOutputStream 对目录必失败，
     * 跨平台一致）。旧实现吞掉写失败继续换入：Windows 下 dst.renameTo(backup) 成功 →
     * dst 被改成 ZIP 字节且**无任何归属证据**（本用例在旧源码上红：旧码看到 dst 已被
     * 替换）。新实现必须以写入失败为主异常（含 cause）中止换入，旧目标逐字节保留、
     * 无 staging/backup 残留。
     */
    @Test
    fun `journalWriteFailureAbortsSwapInAndKeepsOldTargetIntact`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        val oldBody = "OLD-MUST-SURVIVE-JOURNAL-WRITE-FAILURE"
        dst.writeText(oldBody)
        // 确定性制造归属日志写失败：日志路径被同名目录占用。
        File(booksDir, ".cra-restore-journal.json").mkdirs()

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "ZIP-NEW-BYTES")

        val result = runCatching { subject().importZip(fakeContext(), mockk<Uri>()) }

        assertTrue("日志写入失败必须中止本次换入（fail-closed）", result.isFailure)
        val failure = result.exceptionOrNull()
        assertTrue(
            "必须以归属日志写入失败为主异常并保留原始 cause：$failure",
            failure is IllegalStateException && failure.cause is java.io.IOException,
        )
        assertEquals("旧目标必须逐字节保留", oldBody, dst.readText())
        assertNoRestoreResidue(booksDir)
        assertNoRestoreTmpLeft()
    }

    // ── N1-repair-v1：整次导入的持久中断状态 ──────────────────────────────
    // 目标风险：DB 事务提交后、书籍文件复制完成前进程被杀，会留下「DB 已是备份世界、
    // 文件仍是新旧混合」的现场；重启后必须能识别该现场，且导出不得把混合状态扩散。

    /**
     * 真实风险：DB 已提交 + 文件复制中断 ⇒ 状态必须**持久**留下 db_committed，
     * 且**全新实例**（新进程/重启）只凭磁盘就能识别为未完成。
     */
    @Test
    fun `interruptedAfterDbCommitLeavesDurableStateReadableByNewInstance`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("OLD")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        val backup = subject(jsonBridge)
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("N1 注入：文件复制中断") }

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEW BYTES")

        val ctx = fakeContext()
        val result = runCatching { backup.importZip(ctx, mockk<Uri>()) }
        assertTrue("注入的中断必须上抛", result.isFailure)

        // 全新实例读磁盘：这正是「重启后能识别」的唯一依据。
        val state = LocalZipRestoreStateStore().read(ctx)
        assertTrue("中断后必须留下持久状态", state != null)
        assertEquals(
            "DB 已提交、文件未复制完必须记为 db_committed",
            LocalZipRestoreState.STAGE_DB_COMMITTED,
            state!!.stage,
        )
        assertFalse("未完成状态不得被当成完成", state.isComplete)
        assertTrue("失败摘要必须可观察", state.failureSummary.orEmpty().contains("文件复制中断"))
        assertEquals("旧目标必须逐字节保留", "OLD", dst.readText())
    }

    /**
     * 真实风险：状态本身写不进去时，绝不能带着「缺失归属」继续执行 DB 事务与文件换入。
     * 断言：begin 失败即中止，JsonBridge 从未被调用，书籍文件分毫未动。
     */
    @Test
    fun `stateWriteFailureStopsBeforeDbImportAndTouchesNothing`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("OLD-MUST-SURVIVE")

        // 确定性制造状态落盘失败：状态文件路径被同名目录占用。
        File(filesDir, LocalZipRestoreStateStore.STATE_FILE_NAME).mkdirs()

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        writeZip("backup.json" to "{}", "books/b1/content.txt" to "ZIP-NEW-BYTES")

        val result = runCatching { subject(jsonBridge).importZip(fakeContext(), mockk<Uri>()) }

        assertTrue("状态落盘失败必须上抛（fail-closed）", result.isFailure)
        coVerify(exactly = 0) { jsonBridge.importFromString(any(), any(), any()) }
        assertEquals("不得在无归属记录的情况下改动书籍文件", "OLD-MUST-SURVIVE", dst.readText())
        assertNoRestoreResidue(booksDir)
        assertNoRestoreTmpLeft()
    }

    /** 成功导入必须关闭完成标记：新实例读到 null，不能被误报成中断。 */
    @Test
    fun `completedImportClearsStateAndIsNotReportedAsInterrupted`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("OLD")

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEW BYTES")

        val ctx = fakeContext()
        subject().importZip(ctx, mockk<Uri>())

        assertEquals("NEW BYTES", dst.readText())
        assertNull(
            "完成后新实例不得再看到未完成状态",
            LocalZipRestoreStateStore().read(ctx),
        )
    }

    /**
     * 真实风险：未完成状态下导出会把「DB 与文件不一致」扩散进下一份备份。
     * 断言导出被**明确拒绝**，且拒绝路径不删除、不改名任何既有文件。
     */
    @Test
    fun `exportIsBlockedWhileRestoreUnfinishedAndDeletesNothing`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val book = File(booksDir, "b1/content.txt")
        book.parentFile!!.mkdirs()
        book.writeText("USER BYTES")

        val ctx = fakeContext()
        val store = LocalZipRestoreStateStore()
        var state = store.begin(ctx, "content://backup.zip", 1L)
        store.markStage(ctx, state, LocalZipRestoreState.STAGE_DB_COMMITTED, 2L)

        val result = runCatching { subject().exportZip(ctx, mockk<Uri>()) }

        val failure = result.exceptionOrNull()
        assertTrue("未完成状态下导出必须被阻止", failure is IllegalStateException)
        val message = failure!!.message.orEmpty()
        assertTrue("说明必须给出重试指引：$message", message.contains("导入整包备份"))
        assertTrue("说明必须点明混合状态风险：$message", message.contains("不一致"))
        assertTrue("被阻止的导出不得删除用户文件", book.isFile)
        assertEquals("USER BYTES", book.readText())
        assertEquals("被阻止的导出不得写出任何 ZIP 内容", 0L, zipFile.length())
    }

    /**
     * 真实风险：状态文件损坏时若按「无操作」处理，会放过混合状态；若按文件名清理，
     * 又会误删用户文件。断言：损坏 ⇒ 保守按未完成（阻止导出），且一个字节都不动。
     */
    @Test
    fun `corruptStateBlocksExportWithoutDeletingUserFiles`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val book = File(booksDir, "b1/content.txt")
        book.parentFile!!.mkdirs()
        book.writeText("USER BYTES")
        File(filesDir, LocalZipRestoreStateStore.STATE_FILE_NAME).writeText("{ 截断")

        val ctx = fakeContext()
        val result = runCatching { subject().exportZip(ctx, mockk<Uri>()) }

        assertTrue("损坏状态必须保守阻止导出", result.isFailure)
        assertTrue("用户文件不得被删除", book.isFile)
        assertEquals("USER BYTES", book.readText())
    }

    /**
     * 重试收敛：中断后用**同一份 ZIP** 再导入一次必须自愈（全量重写 + 版本感知跳过），
     * 且完成后状态被清除——不依赖任何整库回滚。
     */
    @Test
    fun `retryAfterInterruptionConvergesAndClearsState`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("OLD")

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "BACKUP BYTES")

        val ctx = fakeContext()
        val backup = subject()
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("第一次：复制中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        assertFalse(
            "第一次中断后应留下未完成状态",
            LocalZipRestoreStateStore().read(ctx)!!.isComplete,
        )

        // 重试：复用既有 journal 与 staging 逻辑，不做任何额外回滚。
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        backup.importZip(ctx, mockk<Uri>())

        assertEquals("重试后必须收敛到备份字节", "BACKUP BYTES", dst.readText())
        assertNull("收敛完成后状态必须被清除", LocalZipRestoreStateStore().read(ctx))
        assertNoRestoreResidue(booksDir)
        assertNoRestoreTmpLeft()
    }

    /** 失败摘要必须落盘（可观察），且记录失败不得把 db_committed 阶段降级。 */
    @Test
    fun `failureSummaryIsRecordedForInterruptedImport`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        File(booksDir, "b1/content.txt").also { it.parentFile!!.mkdirs(); it.writeText("OLD") }

        val backup = subject()
        val root = java.io.IOException("底层写失败")
        backup.fileCopyOperation = { _, _ -> throw root }

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEW")

        val ctx = fakeContext()
        runCatching { backup.importZip(ctx, mockk<Uri>()) }

        val state = LocalZipRestoreStateStore().read(ctx)!!
        assertEquals(LocalZipRestoreState.STAGE_DB_COMMITTED, state.stage)
        val summary = state.failureSummary.orEmpty()
        assertTrue("摘要必须含主异常类型：$summary", summary.contains("IOException"))
        assertTrue("摘要必须含原始 message：$summary", summary.contains("底层写失败"))
    }

    /**
     * 漏洞 1 校验：首次 DB 提交后正文未复制，第二次重试时即使真实 merge 因版本相等报告 skippedBookContentPaths，
     * 待修目标正文依然不能被跳过，必须收敛复制并关闭未完成状态。
     */
    @Test
    fun `sameZipRetryDoesNotSkipPendingTargetWhenMergeReportsSkipped`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        // 首次模拟：正文不存在（或未复制完成）

        writeZip("backup.json" to "{\"books\":[{\"id\":\"b1\"}]}", "books/b1/content.txt" to "NEW CONTENT")

        val ctx = fakeContext()
        val jsonBridge = mockk<JsonBridge>(relaxed = true)

        val backup = subject(jsonBridge)
        // 第一次导入：复制阶段失败
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("首次文件复制中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)

        val unfinishedState = LocalZipRestoreStateStore().read(ctx)
        assertNotNull(unfinishedState)
        assertEquals(LocalZipRestoreState.STAGE_DB_COMMITTED, unfinishedState!!.stage)
        assertTrue(unfinishedState.pendingTargets.containsKey("b1/content.txt"))

        // 第二次重试：模拟真实的严格大于策略（由于首次已提交 DB，本地版本等于备份版本，merge 将其判定为跳过）
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            val callback = thirdArg<(ImportMergeResult) -> Unit>()
            callback(ImportMergeResult(skippedBookContentPaths = listOf(dst.absolutePath)))
        }

        // 恢复正常文件复制
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        backup.importZip(ctx, mockk<Uri>())

        // 验证：尽管 merge 报告跳过，但属于待修目标且磁盘未就位，重试依然完成了复制并清除了状态！
        assertTrue("正文文件必须被补齐写入", dst.isFile)
        assertEquals("NEW CONTENT", dst.readText())
        assertNull("收敛完成后状态必须被清除", LocalZipRestoreStateStore().read(ctx))
    }

    /**
     * 漏洞 2 校验：A 中断后选择不含 A 目标的合法 B（或空备份），B 完成不能消除 A 的未完成保护，导出仍被阻断。
     */
    @Test
    fun `unfinishedAIsPreservedWhenDifferentZipBIsImportedAndExportRemainsBlocked`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val fileA = File(booksDir, "a/content.txt")
        fileA.parentFile!!.mkdirs()

        // 准备备份 A
        writeZip("backup.json" to "{\"name\":\"A\"}", "books/a/content.txt" to "A CONTENT")
        val ctx = fakeContext()
        val backup = subject()

        // 导入 A 中断
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("A 中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        assertFalse(fileA.exists())

        val stateAfterA = LocalZipRestoreStateStore().read(ctx)!!
        assertFalse(stateAfterA.isComplete)

        // 准备不同内容的合法备份 B（仅包含 b，不含 a）
        writeZip("backup.json" to "{\"name\":\"B\"}", "books/b/content.txt" to "B CONTENT")
        val fileB = File(booksDir, "b/content.txt")

        // 导入 B
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        backup.importZip(ctx, mockk<Uri>())

        // 验证：B 成功复制了 b，但 A 的未完成目标依然被守护，状态仍为未完成！
        assertTrue("B 的文件已复制", fileB.isFile)
        val stateAfterB = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("状态绝不能被清除", stateAfterB)
        assertFalse("A 未收敛，状态依然不是完成态", stateAfterB!!.isComplete)
        assertTrue("A 的目标仍在 unresolvedTargets 守护中", stateAfterB.unresolvedTargets.containsKey("a/content.txt"))

        // 导出仍被阻断
        val exportResult = runCatching { backup.exportZip(ctx, mockk<Uri>()) }
        assertTrue("由于 A 的未完成保护依然存在，导出必须被阻断", exportResult.isFailure)
    }

    /**
     * 漏洞 3 校验：可控交错测试。ZIP 导入与导出使用服务层互斥锁，导出在状态检查后导入启动，无法穿插执行，不产生混合导出。
     */
    @Test
    fun `concurrentExportAndImportAreMutexedPreventingMixedExport`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val b1 = File(booksDir, "b1/content.txt").also { it.parentFile!!.mkdirs(); it.writeText("INITIAL") }

        val ctx = fakeContext()
        val backup = subject()

        var exportPaused = true
        var importAttemptFinished = false

        backup.onExportCheckFinished = {
            while (exportPaused) {
                delay(10)
            }
        }

        // 启动导出协程
        val exportJob = launch(Dispatchers.IO) {
            backup.exportZip(ctx, mockk<Uri>())
        }

        // 稍等以确保 exportZip 已经获取互斥锁并停在 onExportCheckFinished
        delay(50)

        // 准备另一个备份包并启动导入
        val zipFileB = temp.newFile("import_b.zip")
        ZipOutputStream(zipFileB.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("backup.json"))
            zos.write("{}".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("books/b1/content.txt"))
            zos.write("MODIFIED BY IMPORT".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val cr = ctx.contentResolver
        every { cr.openInputStream(any()) } answers { zipFileB.inputStream() }

        val importJob = launch(Dispatchers.IO) {
            backup.importZip(ctx, mockk<Uri>())
            importAttemptFinished = true
        }

        delay(50)
        assertFalse("在导出持有锁期间，并发导入必须被阻塞排队，不得插队执行", importAttemptFinished)

        // 释放导出
        exportPaused = false
        exportJob.join()
        importJob.join()

        assertTrue("导出与导入在互斥串行化后均能完成", importAttemptFinished)
    }

    /**
     * 漏洞 4 校验：半写 staging 在创建前可靠记录归属日志（verified=false）。
     * 中断产生未校验 staging，重启/新实例重试时安全清理该残片且不误换入，导出绝不包含该 staging 残片。
     */
    @Test
    fun `tornStagingInterruptionLeavesJournaledResidueCleanedOnRetryAndExcludedFromExport`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt").also { it.parentFile!!.mkdirs(); it.writeText("ORIGINAL") }

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEW COMPLETE CONTENT")
        val ctx = fakeContext()
        val backup = subject()

        // 模拟半写中断：写入部分内容后抛出异常中断
        backup.fileCopyOperation = { input, output ->
            output.write("HALF-WRITTEN".toByteArray(Charsets.UTF_8))
            output.flush()
            throw java.io.IOException("半写中断")
        }

        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        assertEquals("原文件保持不变", "ORIGINAL", dst.readText())

        // 此时通过新实例重试
        val newBackup = subject()
        newBackup.fileCopyOperation = { input, output -> input.copyTo(output) }
        newBackup.importZip(ctx, mockk<Uri>())

        assertEquals("重试后收敛到完整内容", "NEW COMPLETE CONTENT", dst.readText())
        assertNull(LocalZipRestoreStateStore().read(ctx))

        // 执行导出，验证导出的 ZIP 绝无任何 staging 残片
        newBackup.exportZip(ctx, mockk<Uri>())
        val entries = listZipEntries()
        assertTrue("导出的 ZIP 包含正文", entries.contains("books/b1/content.txt"))
        assertFalse("导出的 ZIP 绝不得包含任何 staging 残片", entries.any { it.contains(".staging") })
    }

    /**
     * 漏洞 5 校验 (1)：deleteRecursively 返回 false 被准确检测为失败，且落盘于 failureSummary，不误清状态。
     */
    @Test
    fun `deleteRecursivelyReturnsFalseIsRecordedInFailureSummaryAndLeavesStateUnfinished`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        File(booksDir, "b1/content.txt").also { it.parentFile!!.mkdirs(); it.writeText("OLD") }

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEW")
        val ctx = fakeContext()
        val backup = subject()

        // 注入 deleteRecursively 返回 false
        backup.deleteRecursivelyOperation = { false }

        val result = runCatching { backup.importZip(ctx, mockk<Uri>()) }
        assertTrue("deleteRecursively 返回 false 必须判定为失败", result.isFailure)

        val state = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("清理失败不得误清状态", state)
        val summary = state!!.failureSummary.orEmpty()
        assertTrue("失败摘要必须包含 deleteRecursively 返回 false：$summary", summary.contains("deleteRecursively 返回 false"))
    }

    /**
     * 漏洞 5 校验 (2)：deleteRecursively 抛出 IOException 时，作为 suppressed 挂载到主异常上，并完整记录于 failureSummary。
     */
    @Test
    fun `deleteRecursivelyThrowsIOExceptionIsAddedAsSuppressedAndRecordedInSummary`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        File(booksDir, "b1/content.txt").also { it.parentFile!!.mkdirs(); it.writeText("OLD") }

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEW")
        val ctx = fakeContext()
        val backup = subject()

        val mainError = java.io.IOException("正文复制失败")
        backup.fileCopyOperation = { _, _ -> throw mainError }
        backup.deleteRecursivelyOperation = { throw java.io.IOException("清理临时目录失败IO") }

        val result = runCatching { backup.importZip(ctx, mockk<Uri>()) }
        assertTrue(result.isFailure)

        val thrown = result.exceptionOrNull()!!
        assertEquals("主异常不得被覆盖", "正文复制失败", thrown.message)
        assertTrue("清理异常必须被挂载为 suppressed", thrown.suppressed.any { it.message?.contains("清理临时目录失败IO") == true })

        val state = LocalZipRestoreStateStore().read(ctx)
        assertNotNull(state)
        val summary = state!!.failureSummary.orEmpty()
        assertTrue("摘要必须包含主异常：$summary", summary.contains("正文复制失败"))
        assertTrue("摘要必须包含 suppressed 清理异常：$summary", summary.contains("清理临时目录失败IO"))
    }

    // ════════════════════════════════════════════════════════════════════════
    //  N1-repair-v3 反例（四条路径的真实失败对照）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 反例1a：DB 已提交新记录、正文仍是旧内容（长度不同），同 ZIP 重试。
     *
     * v2 缺陷：重试逻辑只检查 `!dstFile.isFile`——文件存在（内容错误）就不从 skipped 移除，
     * merge 报告 skipped 后正文永远停留在旧内容。
     * v3 修复：重试时校验内容一致性（SHA-256），内容不符才保留 skipped，否则强制重写。
     */
    @Test
    fun `N1v3_W1a_sameZipRetryWithWrongContentDifferentLengthReCopies`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("OLD") // 3 bytes

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEW CONTENT") // 11 bytes

        val ctx = fakeContext()
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        val backup = subject(jsonBridge)

        // 第一次导入：文件复制阶段中断
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("首次文件复制中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        assertEquals("中断后旧正文必须保留", "OLD", dst.readText())

        // 第二次重试：merge 因等版本报告 skipped
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            thirdArg<(ImportMergeResult) -> Unit>()(
                ImportMergeResult(skippedBookContentPaths = listOf(dst.absolutePath)),
            )
        }
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        backup.importZip(ctx, mockk<Uri>())

        assertEquals("重试必须把正文补齐为备份内容（不能因文件存在而跳过）", "NEW CONTENT", dst.readText())
        assertNull("收敛后状态必须清除", LocalZipRestoreStateStore().read(ctx))
    }

    /**
     * 反例1b：旧正文与备份内容**长度相同但内容不同**，同 ZIP 重试。
     *
     * v2 缺陷：验证只比长度——长度相同就认为已就位，内容错误不可观察。
     * v3 修复：验证用 SHA-256 内容指纹，长度相同内容不同也能识别。
     */
    @Test
    fun `N1v3_W1b_sameZipRetryWithWrongContentSameLengthReCopies`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("OLDD") // 4 bytes

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEWW") // 4 bytes, same length

        val ctx = fakeContext()
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        val backup = subject(jsonBridge)

        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("首次文件复制中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)

        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            thirdArg<(ImportMergeResult) -> Unit>()(
                ImportMergeResult(skippedBookContentPaths = listOf(dst.absolutePath)),
            )
        }
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        backup.importZip(ctx, mockk<Uri>())

        assertEquals("长度相同但内容不同时也必须重写", "NEWW", dst.readText())
        assertNull(LocalZipRestoreStateStore().read(ctx))
    }

    /**
     * 反例2：DB 导入返回后、markDbCommitted 落盘前中断。
     *
     * 模拟方式：直接用 store.begin() 写出 STAGE_STARTED + 空 pending + 空指纹的持久状态
     * （这正是 DB 已提交但 markDbCommitted 未调用时的磁盘现场），**不用异常触发 finally**。
     * 随后同 ZIP 重试，merge 报告 skipped——v2 因 pending 为空不触发重试补偿，正文停留旧内容。
     */
    @Test
    fun `N1v3_W2_durableStartedStateWithEmptyPendingReCopiesOnRetry`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("OLD")

        val ctx = fakeContext()
        // 直接写出中断后的持久状态：STAGE_STARTED、空 pending、空指纹
        LocalZipRestoreStateStore().begin(ctx, "content://backup.zip", 1000L)

        writeZip("backup.json" to "{}", "books/b1/content.txt" to "NEW CONTENT")

        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            thirdArg<(ImportMergeResult) -> Unit>()(
                ImportMergeResult(skippedBookContentPaths = listOf(dst.absolutePath)),
            )
        }
        val backup = subject(jsonBridge)
        backup.importZip(ctx, mockk<Uri>())

        assertEquals("DB 已提交但正文未就位时重试必须补齐", "NEW CONTENT", dst.readText())
        assertNull(LocalZipRestoreStateStore().read(ctx))
    }

    /**
     * 反例3a：备份 A 中断后导入备份 B（同路径、版本较低）。
     *
     * v2 缺陷：isSameBackupRetry 因 pendingTargets 非空而对**任何**备份为真；
     * clear 只检查 isFile——文件存在（旧内容）就清状态，A 的义务被消除。
     * v3 修复：isSameBackupRetry 只按指纹判定；clear 校验内容指纹，内容不符不清状态。
     */
    @Test
    fun `N1v3_W3a_backupBLowerVersionDoesNotOverwriteAndPreservesAObligation`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL")

        // 导入 A：DB 提交、文件复制中断
        writeZip("backup.json" to "{\"name\":\"A\"}", "books/b1/content.txt" to "A CONTENT")
        val ctx = fakeContext()
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        val backup = subject(jsonBridge)

        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("A 中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        assertEquals("ORIGINAL", dst.readText())

        // 导入 B：同路径、版本较低 → merge 报告 skipped
        writeZip("backup.json" to "{\"name\":\"B\"}", "books/b1/content.txt" to "B CONTENT")
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            thirdArg<(ImportMergeResult) -> Unit>()(
                ImportMergeResult(skippedBookContentPaths = listOf(dst.absolutePath)),
            )
        }
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        backup.importZip(ctx, mockk<Uri>())

        assertEquals("B 版本较低不得覆盖", "ORIGINAL", dst.readText())
        val state = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("A 的义务未完成，状态不得清除", state)
        assertFalse("状态不得为完成态", state!!.isComplete)
        assertTrue("A 的目标仍在 unresolvedTargets 守护中", state.unresolvedTargets.containsKey("b1/content.txt"))
    }

    /**
     * 反例3b：backup.json 相同但正文内容不同 → 必须识别为不同内容身份。
     *
     * v2 缺陷：backupFingerprint 只 hash backup.json——两份 backup.json 都是 "{}" 但正文不同的
     * 备份被视为同一备份重试，A 的义务被 B 静默消除。
     * v3 修复：backupFingerprint 覆盖正文内容指纹，同 backup.json 不同正文判为不同备份。
     */
    @Test
    fun `N1v3_W3b_sameBackupJsonButDifferentContentIsNotSameBackupRetry`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL")

        // 导入 A：backup.json="{}", 正文="A CONTENT"
        writeZip("backup.json" to "{}", "books/b1/content.txt" to "A CONTENT")
        val ctx = fakeContext()
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        val backup = subject(jsonBridge)

        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("A 中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)

        // 导入 B：backup.json="{}"（相同），正文="B CONTENT"（不同）
        writeZip("backup.json" to "{}", "books/b1/content.txt" to "B CONTENT")
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            thirdArg<(ImportMergeResult) -> Unit>()(
                ImportMergeResult(skippedBookContentPaths = listOf(dst.absolutePath)),
            )
        }
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        backup.importZip(ctx, mockk<Uri>())

        val state = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("A 的义务未完成，状态不得被 B 清除", state)
        assertFalse("状态不得为完成态", state!!.isComplete)
        assertTrue("A 的目标仍在 unresolvedTargets 守护中", state.unresolvedTargets.containsKey("b1/content.txt"))
    }

    /**
     * 反例4：半写 staging 残片存在且 delete 返回 false。
     *
     * v2 缺陷：recoverCrashResidues 对 verified=false 条目调用 stagingFile.delete() 但不检查返回值，
     * 无论删除是否成功都 remaining.remove(entry)——归属日志被剪掉，残片成为无归属文件，
     * 后续导出会把残片当普通资产打包。
     * v3 修复：delete 返回 false 时保留归属条目，staging 仍在 stagingRels 中被导出过滤。
     */
    @Test
    fun `N1v3_W4_tornStagingDeleteFailureKeepsJournalEntryAndExcludesFromExport`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL")

        // 预置中断现场：半写 staging + 归属日志（verified=false）
        val stagingName = "content.txt.restore-1234567890123.staging"
        val stagingFile = File(booksDir, "b1/$stagingName")
        stagingFile.parentFile!!.mkdirs()
        stagingFile.writeText("HALF-WRITTEN")
        journalFile(booksDir).writeText(
            """[{"rel":"b1/content.txt","backup":"content.txt.restore-backup-1234567890123","staging":"$stagingName","verified":"false"}]""",
        )

        val ctx = fakeContext()
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        coEvery { jsonBridge.exportToString(any()) } returns "{}"

        val backup = subject(jsonBridge)
        // 注入 staging 删除失败
        backup.fileDeleteOperation = { _ -> false }

        // 导出触发 recoverCrashResidues
        backup.exportZip(ctx, mockk<Uri>())

        // 归属日志条目必须保留
        val journalAfter = readJournalRaw(booksDir)
        assertNotNull("删除失败时归属日志不得被剪掉", journalAfter)
        assertTrue("归属日志必须保留条目：$journalAfter", journalAfter!!.contains("b1/content.txt"))

        // staging 残片不得出现在导出 ZIP 中
        val entries = listZipEntries()
        assertFalse("导出绝不得包含 staging 残片", entries.any { it.contains(".staging") })
    }

    // ════════════════════════════════════════════════════════════════════════
    //  N1-repair-v4 反例（四条失败路径红测）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * N1-v4 路径 1 (P1)：本地严格较新 → 导入缺 backup.json 的坏 ZIP 留下 started 空身份 → 导入低版本合法 ZIP。
     *
     * 预期：DB 和正文必须同时保持较新内容；不允许空指纹意味着"全部源目标都可覆盖"。
     * 本地严格较新正文绝不能被低版本正文覆盖！
     */
    @Test
    fun `N1v4_P1_strictlyNewerProtectedAgainstEmptyFingerprintRetry`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        val newerContent = "LOCAL_STRICTLY_NEWER_CONTENT_V2"
        dst.writeText(newerContent)

        val ctx = fakeContext()
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        val backup = subject(jsonBridge)

        // 1. 尝试导入缺 backup.json 的坏 ZIP
        writeZip("books/other.txt" to "OTHER")
        val badZipResult = runCatching { backup.importZip(ctx, mockk<Uri>()) }
        assertTrue("坏 ZIP 必须导入失败", badZipResult.isFailure)

        val stateAfterBad = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("坏 ZIP 会留下 started 状态", stateAfterBad)
        assertEquals("坏 ZIP 状态为 started", LocalZipRestoreState.STAGE_STARTED, stateAfterBad!!.stage)
        assertEquals("坏 ZIP 留下空指纹", "", stateAfterBad.backupFingerprint)

        // 2. 接着导入低版本合法 ZIP（正文内容为 OLD，JsonBridge 合并时判定本地严格较新而跳过）
        writeZip(
            "backup.json" to "{\"schemaVersion\":1}",
            "books/b1/content.txt" to "OLD_BACKUP_CONTENT_V1",
        )

        // 模拟真实 JsonBridge：因为本地严格较新（例如本地 revision=5，备份 revision=1），将 dst 加入 skipped 和 strictlyNewer
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            val callback = thirdArg<(ImportMergeResult) -> Unit>()
            callback(
                ImportMergeResult(
                    acceptedBookIds = emptySet(),
                    skippedBookContentPaths = listOf(dst.absolutePath),
                    strictlyNewerBookContentPaths = listOf(dst.absolutePath),
                ),
            )
        }

        backup.importZip(ctx, mockk<Uri>())

        // 核心断言：本地严格较新的正文绝对不能被覆盖！
        assertEquals(
            "本地严格较新的正文必须保留，空指纹绝不得把严格较新文件强制重写！",
            newerContent,
            dst.readText(),
        )
    }

    /**
     * N1-v4 路径 2 (P2)：A 提交后正文未完成 → 导入不含该目标的 B → 返回原 A：必须验证回到 A 成功收敛。
     */
    @Test
    fun `N1v4_P2_abaConvergence`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val fileA = File(booksDir, "a/content.txt")
        fileA.parentFile!!.mkdirs()
        val fileB = File(booksDir, "b/content.txt")
        fileB.parentFile!!.mkdirs()

        val ctx = fakeContext()
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        val backup = subject(jsonBridge)

        // 1. 导入 A：包含 a/content.txt。DB 提交成功，但文件复制中断
        writeZip("backup.json" to "{\"schemaVersion\":1,\"name\":\"A\"}", "books/a/content.txt" to "CONTENT_A")
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("A 复制中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        assertFalse("A 正文未落盘", fileA.exists())

        // 2. 导入 B：包含 b/content.txt，不含 a。B 导入成功
        writeZip("backup.json" to "{\"schemaVersion\":1,\"name\":\"B\"}", "books/b/content.txt" to "CONTENT_B")
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            val callback = thirdArg<(ImportMergeResult) -> Unit>()
            callback(ImportMergeResult(acceptedBookIds = setOf("b")))
        }
        backup.importZip(ctx, mockk<Uri>())
        assertTrue("B 正文已就位", fileB.isFile)
        val stateAfterB = LocalZipRestoreStateStore().read(ctx)
        assertNotNull(stateAfterB)
        assertFalse("A 未完成，B 之后整个状态仍未完成", stateAfterB!!.isComplete)
        assertTrue("A 的目标仍在 unresolvedTargets 中", stateAfterB.unresolvedTargets.containsKey("a/content.txt"))

        // 3. 再次导入原 A：回到 A。由于之前 A 已入库，merge 将 a 判定为 equal (skipped)
        writeZip("backup.json" to "{\"schemaVersion\":1,\"name\":\"A\"}", "books/a/content.txt" to "CONTENT_A")
        coEvery { jsonBridge.importFromString(any(), any(), any()) } answers {
            val callback = thirdArg<(ImportMergeResult) -> Unit>()
            // 因为 A 之前已经写入过 DB，本次判断为 equal 版本并进入 skipped
            callback(
                ImportMergeResult(
                    acceptedBookIds = emptySet(),
                    skippedBookContentPaths = listOf(fileA.absolutePath),
                ),
            )
        }

        backup.importZip(ctx, mockk<Uri>())

        // 核心断言：回到原 A 必须成功补偿复制 a/content.txt，且状态完全收敛清除！
        assertTrue("回到 A 后 a/content.txt 必须被补齐写入", fileA.isFile)
        assertEquals("CONTENT_A", fileA.readText())
        val finalState = LocalZipRestoreStateStore().read(ctx)
        assertNull("A 成功收敛后，未完成状态必须被完全清除", finalState)
    }

    /**
     * N1-v4 路径 4 (P2)：复制半写失败且 staging 删除返回 false：覆盖复制 finally 分支，保留归属日志。
     */
    @Test
    fun `N1v4_P2_copyWithStagingFinallyDeleteFailurePreservesJournal`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL_CONTENT")

        writeZip("backup.json" to "{\"schemaVersion\":1}", "books/b1/content.txt" to "NEW_CONTENT")
        val ctx = fakeContext()
        val backup = subject()

        // 模拟半写中断（verified 仍为 false）
        backup.fileCopyOperation = { _, output ->
            output.write("HALF_DATA".toByteArray(Charsets.UTF_8))
            throw java.io.IOException("半写中断")
        }
        // 模拟 finally 中 staging 删除返回 false（例如文件被系统独占或只读）
        backup.fileDeleteOperation = { false }

        val result = runCatching { backup.importZip(ctx, mockk<Uri>()) }
        assertTrue("导入半写必须失败", result.isFailure)

        // 核心断言：在 copyWithStaging 的 finally 执行后，因为 staging 依然残留在磁盘且删除失败，
        // 归属日志绝不能被删除！必须保留该条目的归属记录！
        val journalRaw = readJournalRaw(booksDir)
        assertNotNull("删除失败时归属日志文件必须存在", journalRaw)
        assertTrue(
            "归属日志必须保留该 staging 的归属记录以守护残片：$journalRaw",
            journalRaw!!.contains("b1/content.txt") && journalRaw.contains(".staging"),
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    //  N1-repair-v5 反例（正文身份与旧状态完成判定）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 反例1（P1）：A 的 JSON 已提交、正文复制失败留下 hash(A正文) 义务。
     * 随后导入 JSON 相同、同路径但正文为 B 的 ZIP。真实 JsonBridge 返回 equal 分类。
     *
     * v4 缺陷：isEqualRel 单独授权补偿，B 正文写入，A 义务被同路径剪除，导出放行。
     * v5 修复：存在冲突义务时 equal 不得单独授权；A 义务保留，导出阻断。
     *
     * 关键：使用真实 JsonBridge 合并分类（受控 DAO），不 mock ImportMergeResult。
     */
    @Test
    fun `N1v5_P1_equalClassificationDoesNotBypassContentIdentity`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL")

        val ctx = fakeContext()
        val backup = subject(realJsonBridge(ctx, dst.absolutePath))

        // 1. 导入 A：同版本 book，正文 "A CONTENT"。真实 JsonBridge 返回 equal。
        //    文件复制中断，留下 hash(A CONTENT) 义务。
        writeZip(
            "backup.json" to backupJson(bookId = "b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z"),
            "books/b1/content.txt" to "A CONTENT",
        )
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("A 复制中断") }
        assertTrue("A 导入必须失败（文件复制中断）", runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)

        val stateAfterA = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("A 中断后状态必须存在", stateAfterA)
        assertFalse("A 状态不得为完成", stateAfterA!!.isComplete)
        assertTrue("A 的 pendingTargets 必须包含 b1/content.txt", stateAfterA.pendingTargets.containsKey("b1/content.txt"))

        // 2. 导入 B：同 JSON、同路径、同版本，但正文为 "B CONTENT"。
        //    真实 JsonBridge 仍返回 equal（DB 版本相等）。
        writeZip(
            "backup.json" to backupJson(bookId = "b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z"),
            "books/b1/content.txt" to "B CONTENT",
        )
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        backup.importZip(ctx, mockk<Uri>())

        // 核心断言：equal 只说明 DB 版本相等，不证明正文身份。
        // B 正文身份不同于 A 义务，不得写入；A 义务必须保留，状态不得完成。
        assertEquals(
            "equal 不得单独授权不同正文身份写入：本地正文必须保持 ORIGINAL",
            "ORIGINAL",
            dst.readText(),
        )
        val stateAfterB = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("A 义务必须保留，状态不得被清除", stateAfterB)
        assertFalse("状态不得为完成（A 义务未收敛）", stateAfterB!!.isComplete)
        assertTrue(
            "A 的义务仍需在 unresolvedTargets 或 pendingTargets 中",
            stateAfterB.unresolvedTargets.containsKey("b1/content.txt") ||
                stateAfterB.pendingTargets.containsKey("b1/content.txt"),
        )
    }

    // ── N1-repair-v6：组合重试路径的统一冲突义务保护 ─────────────────────
    // v5 只把冲突挂在 equalAuthorized 上；空指纹、最近一次指纹相同、路径相同三条
    // OR 仍可单独授权。以下四条用例覆盖 Codex 复核点名的三条可达路径与正向收敛。

    /** 读取当前状态里 rel 的义务值（unresolved 优先，其次 pending）。 */
    private fun obligationOf(state: LocalZipRestoreState?, rel: String): String? =
        state?.let { it.unresolvedTargets[rel] ?: it.pendingTargets[rel] }

    /**
     * 导出是否**因未完成状态**被阻止。
     *
     * 必须区分「被状态守卫阻止」与「其它原因导致的失败」——否则在真实 JsonBridge
     * （relaxed DAO）下导出本就可能因其它原因失败，断言会退化成空断言。
     */
    private suspend fun exportBlockedByUnfinishedState(
        backup: LocalZipBackup,
        ctx: Context,
    ): Boolean = runCatching { backup.exportZip(ctx, mockk<Uri>()) }
        .exceptionOrNull()?.message?.contains("上次整包恢复未完成") == true

    /**
     * 组合路径 1：A 正文复制中断 → B（同 JSON 不同正文）→ **再选择同一 B**。
     * 两次 B 都不得写入冲突正文、消除 A 义务或放行导出。
     * v5 会在第二次 B 经 isSameFingerprintCandidate 绕过（本用例在 v5 上失败）。
     */
    @Test
    fun `N1v6_C1_aToBToBDoesNotWriteConflictingContentOrDischargeObligation`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL")

        val ctx = fakeContext()
        val backup = subject(realJsonBridge(ctx, dst.absolutePath))
        val json = backupJson(bookId = "b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z")

        // 1) A：正文 "A CONTENT"，文件复制中断 → 落盘 sha("A CONTENT") 义务
        writeZip("backup.json" to json, "books/b1/content.txt" to "A CONTENT")
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("A 复制中断") }
        assertTrue("A 导入必须失败", runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        val obligationAfterA = obligationOf(LocalZipRestoreStateStore().read(ctx), "b1/content.txt")
        assertNotNull("A 义务必须落盘", obligationAfterA)

        backup.fileCopyOperation = { input, output -> input.copyTo(output) }

        // 2) 第一次 B（同 JSON、同路径、不同正文）
        writeZip("backup.json" to json, "books/b1/content.txt" to "B CONTENT")
        backup.importZip(ctx, mockk<Uri>())
        assertEquals("第一次 B 不得写入冲突正文", "ORIGINAL", dst.readText())
        assertEquals(
            "第一次 B 不得消除 A 义务",
            obligationAfterA,
            obligationOf(LocalZipRestoreStateStore().read(ctx), "b1/content.txt"),
        )

        // 3) 第二次 B：同一份 B 再选一次（指纹相同 + 路径属继承义务）
        backup.importZip(ctx, mockk<Uri>())
        assertEquals("第二次 B 同样不得写入冲突正文", "ORIGINAL", dst.readText())
        val afterB2 = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("第二次 B 后 A 义务必须仍在", afterB2)
        assertFalse("状态不得被清成完成", afterB2!!.isComplete)
        assertEquals(
            "A 义务内容不得被 B 的 SHA 替换",
            obligationAfterA,
            obligationOf(afterB2, "b1/content.txt"),
        )
        assertTrue(
            "导出必须被未完成状态阻止（而非其它原因）",
            exportBlockedByUnfinishedState(backup, ctx),
        )
    }

    /**
     * 组合路径 2：A 中断 → 缺 backup.json 的坏 ZIP（begin 已用**空指纹**继承 A 义务）→ B。
     * 空指纹不得解除 A 的冲突保护（v5 经 isStartedEmptyFingerprint 绕过，本用例在 v5 上失败）。
     */
    @Test
    fun `N1v6_C2_emptyFingerprintFromBadZipDoesNotDischargeObligation`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL")

        val ctx = fakeContext()
        val backup = subject(realJsonBridge(ctx, dst.absolutePath))
        val json = backupJson(bookId = "b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z")

        writeZip("backup.json" to json, "books/b1/content.txt" to "A CONTENT")
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("A 复制中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        val obligationAfterA = obligationOf(LocalZipRestoreStateStore().read(ctx), "b1/content.txt")
        assertNotNull(obligationAfterA)

        // 坏 ZIP：只有 books/，没有 backup.json → 校验失败，但 begin 已用空指纹接管
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        writeZip("books/b1/content.txt" to "BAD ZIP CONTENT")
        assertTrue("坏 ZIP 必须被拒绝", runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        val afterBad = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("坏 ZIP 后状态必须仍在", afterBad)
        assertTrue("坏 ZIP 必须留下空指纹现场", afterBad!!.backupFingerprint.isEmpty())
        assertEquals(
            "空指纹现场必须仍守护 A 义务",
            obligationAfterA,
            obligationOf(afterBad, "b1/content.txt"),
        )

        // B：同 JSON 不同正文 → 空指纹不得解除冲突保护
        writeZip("backup.json" to json, "books/b1/content.txt" to "B CONTENT")
        backup.importZip(ctx, mockk<Uri>())
        assertEquals("空指纹不得授权写入 B 的冲突正文", "ORIGINAL", dst.readText())
        val afterB = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("A 义务必须保留", afterB)
        assertFalse("状态不得被清成完成", afterB!!.isComplete)
        assertEquals("A 义务内容不得被替换", obligationAfterA, obligationOf(afterB, "b1/content.txt"))
        assertTrue(
            "导出必须被未完成状态阻止（而非其它原因）",
            exportBlockedByUnfinishedState(backup, ctx),
        )
    }

    /**
     * 组合路径 3：legacy 长度义务 → B → 再选择 B。
     * 不得把未知来源的长度义务替成 B 的 SHA 并清除（v5 第二次 B 经同指纹分支替换，本用例在 v5 上失败）。
     */
    @Test
    fun `N1v6_C3_legacyLengthObligationIsNotReplacedByRepeatedB`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL")

        val ctx = fakeContext()
        // 播种旧版本遗留的长度义务（非 64 位十六进制 → isLegacyLength）
        val store = LocalZipRestoreStateStore()
        val seeded = store.begin(ctx, "legacy-state", 1L)
        store.markDbCommitted(
            context = ctx,
            state = seeded,
            backupFingerprint = "",
            pendingTargets = mapOf("b1/content.txt" to "12345"),
            nowMillis = 2L,
        )

        val backup = subject(realJsonBridge(ctx, dst.absolutePath))
        val json = backupJson(bookId = "b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z")

        // 第一次 B
        writeZip("backup.json" to json, "books/b1/content.txt" to "B CONTENT")
        backup.importZip(ctx, mockk<Uri>())
        assertEquals("长度义务不得授权写入 B 的正文", "ORIGINAL", dst.readText())
        assertEquals(
            "legacy 长度义务不得被替换",
            "12345",
            obligationOf(LocalZipRestoreStateStore().read(ctx), "b1/content.txt"),
        )

        // 第二次 B（同指纹）
        backup.importZip(ctx, mockk<Uri>())
        assertEquals("第二次 B 同样不得写入", "ORIGINAL", dst.readText())
        val afterB2 = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("legacy 义务必须仍在", afterB2)
        assertTrue(
            "未知来源的长度义务不得被 B 的 SHA 替换并清除",
            obligationOf(afterB2, "b1/content.txt") == "12345",
        )
        assertTrue(
            "导出必须被未完成状态阻止（而非其它原因）",
            exportBlockedByUnfinishedState(backup, ctx),
        )
    }

    /**
     * 组合路径 4：上述阻断后重新选择**匹配原义务的 A** —— 可靠 SHA 义务应能补齐正文、收敛状态。
     * 防止统一守卫把正当的 A→B→A 补偿路径一并堵死。
     */
    @Test
    fun `N1v6_C4_reselectingMatchingAConvergesAfterBlockedRetries`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL")

        val ctx = fakeContext()
        val convergedBook = BookEntity(
            id = "b1", title = "测试书", format = "txt",
            revision = 2, updated_at = "2026-01-01T00:00:00Z",
            local_content_path = dst.absolutePath,
        )
        val backup = subject(realJsonBridge(ctx, dst.absolutePath, exportBooks = listOf(convergedBook)))
        val json = backupJson(bookId = "b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z")

        writeZip("backup.json" to json, "books/b1/content.txt" to "A CONTENT")
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("A 复制中断") }
        assertTrue(runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)

        // B 两次，均被阻断
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        writeZip("backup.json" to json, "books/b1/content.txt" to "B CONTENT")
        backup.importZip(ctx, mockk<Uri>())
        backup.importZip(ctx, mockk<Uri>())
        assertEquals("B 不得写入", "ORIGINAL", dst.readText())
        assertTrue(
            "阻断期间导出必须被未完成状态阻止",
            exportBlockedByUnfinishedState(backup, ctx),
        )

        // 重新选择匹配义务的原 A
        writeZip("backup.json" to json, "books/b1/content.txt" to "A CONTENT")
        backup.importZip(ctx, mockk<Uri>())

        assertEquals("重新选择原 A 必须补齐正文", "A CONTENT", dst.readText())
        assertNull("A 义务收敛后状态必须清除", LocalZipRestoreStateStore().read(ctx))
        assertFalse(
            "收敛后不得再以未完成状态阻止导出",
            exportBlockedByUnfinishedState(backup, ctx),
        )

        // ── N1-test-hardening-v7：门禁解除后必须能完成**真实导出**（而非仅"门禁解除"）──
        // 真实 JsonBridge.buildExport + 真实 ZIP 打包；随后打开实际输出核对内容。
        runCatching { backup.exportZip(ctx, mockk<Uri>()) }
            .onFailure { throw AssertionError("收敛后真实导出必须成功：${it.message}", it) }

        // 打开实际输出 ZIP：backup.json 可解析、含恢复的书与正文
        val bodies = readZipEntryBodies()
        assertTrue("ZIP 必须含 backup.json", bodies.containsKey("backup.json"))
        assertTrue("ZIP 必须含恢复的正文 books/b1/content.txt", bodies.containsKey("books/b1/content.txt"))
        val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(bodies.getValue("backup.json")).jsonObject
        val booksArr = parsed["books"]!!.jsonArray
        assertEquals("导出必须且只含恢复的那本书", 1, booksArr.size)
        assertEquals(
            "导出的书必须是 b1",
            "b1",
            booksArr[0].jsonObject["payload"]!!.jsonObject["id"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "ZIP 内正文必须等于收敛后的字节",
            "A CONTENT",
            bodies.getValue("books/b1/content.txt"),
        )
    }

    /**
     * N1-test-hardening-v7（正式回归）：**真实 accepted 分类**下的继承冲突义务保护。
     *
     * 与 C1–C4 的区别：C1–C4 的 local/remote 固定同 revision（真实 JsonBridge 判为
     * equal）。本用例让 B 携带**更高 revision**，经真实 [JsonBridge.importFromString]
     * 走 accepted 分支（`bookDao.upsert(revision=3)` 可核），随后断言：
     * 冲突正文不得写入、hash(A) 义务不得被 hash(B) 替换或剪除、状态仍未完成、
     * 导出明确因「上次整包恢复未完成」被阻止。
     *
     * 分类证据（不靠注释）：`coVerify { bookDao.upsert(withArg { it.revision == 3 }) }`
     * 证明 B 确实经真实合并走到 accepted 分支（accepted ⇒ upsert）。
     * 预期在 v5 生产上失败（accepted 曾授权冲突正文复制），在 v6 生产上通过。
     */
    @Test
    fun `N1v7_C5_acceptedConflictingVersionKeepsObligationAndBlocksExport`() = runTest {
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val dst = File(booksDir, "b1/content.txt")
        dst.parentFile!!.mkdirs()
        dst.writeText("ORIGINAL")

        val ctx = fakeContext()
        val backup = subject(realJsonBridge(ctx, dst.absolutePath))
        val jsonA = backupJson(bookId = "b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z")
        val jsonB = backupJson(bookId = "b1", revision = 3, updatedAt = "2026-02-01T00:00:00Z")

        // 1) A（rev2，与本地 equal）：正文复制中断 → 落盘 hash("A CONTENT") 义务
        writeZip("backup.json" to jsonA, "books/b1/content.txt" to "A CONTENT")
        backup.fileCopyOperation = { _, _ -> throw java.io.IOException("A 复制中断") }
        assertTrue("A 导入必须失败", runCatching { backup.importZip(ctx, mockk<Uri>()) }.isFailure)
        val obligationAfterA = obligationOf(LocalZipRestoreStateStore().read(ctx), "b1/content.txt")
        assertNotNull("A 义务必须落盘", obligationAfterA)

        // 2) B（rev3 > 本地 rev2）：真实 JsonBridge 必须**接受**（upsert revision=3 可核）
        backup.fileCopyOperation = { input, output -> input.copyTo(output) }
        writeZip("backup.json" to jsonB, "books/b1/content.txt" to "B CONTENT")
        backup.importZip(ctx, mockk<Uri>())
        // 分类证据：真实合并把 B 的 payload 写进 DAO（accepted ⇒ upsert，revision=3）
        coVerify(atLeast = 1) {
            (backingBookDao ?: throw AssertionError("realJsonBridge 未暴露 BookDao")).upsert(
                withArg { book -> require(book.revision == 3) { "B 必须以 revision=3 接受，实际 ${book.revision}" } },
            )
        }

        // accepted 分类**不得**授权写入冲突正文
        assertEquals("accepted 分类不得写入冲突正文", "ORIGINAL", dst.readText())
        val afterB = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("状态不得被清除", afterB)
        assertFalse("A 义务未收敛，状态不得为完成", afterB!!.isComplete)
        assertEquals(
            "A 义务不得被 hash(B) 替换",
            obligationAfterA,
            obligationOf(afterB, "b1/content.txt"),
        )
        assertTrue(
            "A 的义务仍须在 unresolvedTargets 或 pendingTargets 中守护",
            afterB.unresolvedTargets.containsKey("b1/content.txt") ||
                afterB.pendingTargets.containsKey("b1/content.txt"),
        )
        assertTrue(
            "导出必须明确因未完成状态被阻止",
            exportBlockedByUnfinishedState(backup, ctx),
        )
    }

    /** C4/C5 共用：realJsonBridge 内部创建的 BookDao mock 引用（分类证据用）。 */
    private var backingBookDao: BookDao? = null

    /**
     * 构造真实 JsonBridge（受控 DAO），local 书与 remote 同版本 → 真实 equal 分类。
     *
     * [exportBooks] 非空时补齐 [JsonBridge.buildExport] 所需的全部 DAO Flow/查询夹具
     * （N1-test-hardening-v7）：books=exportBooks，其余集合为空——使收敛后的
     * `exportZip` 走**真实 buildExport**（导出夹具仅是测试准备，生产导出门禁未动）。
     */
    private fun realJsonBridge(
        ctx: Context,
        localContentPath: String,
        exportBooks: List<BookEntity>? = null,
    ): JsonBridge {
        val bookDao = mockk<BookDao>(relaxed = true).also { backingBookDao = it }
        val inspirationDao = mockk<InspirationDao>(relaxed = true)
        val progressDao = mockk<ReadingProgressDao>(relaxed = true)
        val sessionDao = mockk<ReadingSessionDao>(relaxed = true)
        val noteDao = mockk<NoteDao>(relaxed = true)
        val highlightDao = mockk<HighlightDao>(relaxed = true)
        val tagDao = mockk<TagDao>(relaxed = true)
        val categoryDao = mockk<CategoryDao>(relaxed = true)
        val shelfDao = mockk<ShelfDao>(relaxed = true)
        val bookTagDao = mockk<BookTagDao>(relaxed = true)
        val bookCategoryDao = mockk<BookCategoryDao>(relaxed = true)
        val shelfBookDao = mockk<ShelfBookDao>(relaxed = true)
        val chapterReadDao = mockk<ChapterReadDao>(relaxed = true)

        // 事务桩：直接执行 block（JVM 可测，不依赖真实 Room）
        coEvery { bookDao.runInTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
        coEvery { noteDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { highlightDao.observeAllActive() } returns flowOf(emptyList())

        // local 书：revision=2, updatedAt 与 remote 相同 → equal 分类
        val localBook = BookEntity(
            id = "b1", title = "测试书", format = "txt",
            revision = 2, updated_at = "2026-01-01T00:00:00Z",
            local_content_path = localContentPath,
        )
        coEvery { bookDao.getByIds(any()) } returns listOf(localBook)

        // N1-test-hardening-v7：buildExport 所需 Flow/查询夹具（仅当调用方声明要真实导出）
        if (exportBooks != null) {
            coEvery { bookDao.observeAllActive() } returns flowOf(exportBooks)
            coEvery { inspirationDao.observeAllActive() } returns flowOf(emptyList())
            coEvery { progressDao.observeAllActive() } returns flowOf(emptyList())
            coEvery { sessionDao.observeAllActive() } returns flowOf(emptyList())
            coEvery { tagDao.getAllActive() } returns emptyList()
            coEvery { categoryDao.getAllActive() } returns emptyList()
            coEvery { shelfDao.getAllActive() } returns emptyList()
            coEvery { bookTagDao.getAllActive() } returns emptyList()
            coEvery { bookCategoryDao.getAllActive() } returns emptyList()
            coEvery { shelfBookDao.getAllActive() } returns emptyList()
            coEvery { chapterReadDao.getAll() } returns emptyList()
        }

        return JsonBridge(
            bookDao, inspirationDao, progressDao, sessionDao,
            noteDao, highlightDao, tagDao, categoryDao, shelfDao,
            bookTagDao, bookCategoryDao, shelfBookDao, chapterReadDao,
            Dispatchers.Unconfined,
        )
    }

    /** 构造含单本书籍的 LocalExport JSON（用于 backup.json）。 */
    private fun backupJson(
        bookId: String = "b1",
        revision: Int = 2,
        updatedAt: String = "2026-01-01T00:00:00Z",
    ): String {
        val json = Json { ignoreUnknownKeys = true }
        val book = BookEntity(id = bookId, title = "测试书", format = "txt", revision = revision, updated_at = updatedAt)
        val envelope = SyncEnvelope(
            id = bookId, type = "book", revision = revision,
            deviceId = "remote-device", updatedAt = updatedAt, deletedAt = null, payload = book,
        )
        val export = LocalExport(
            schemaVersion = 1,
            exportedAt = "2026-08-05T00:00:00Z",
            deviceId = "test-device",
            books = listOf(envelope),
            inspirations = emptyList(),
            progress = emptyList(),
            sessions = emptyList(),
        )
        return json.encodeToString(LocalExport.serializer(), export)
    }
}
