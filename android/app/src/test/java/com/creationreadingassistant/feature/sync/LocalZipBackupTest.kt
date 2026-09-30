package com.creationreadingassistant.feature.sync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.creationreadingassistant.feature.log.AppLog
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
