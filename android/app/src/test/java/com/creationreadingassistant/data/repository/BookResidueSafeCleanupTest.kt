package com.creationreadingassistant.data.repository

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

/**
 * 「清理离线正文不得误毁唯一残留备份」数据保护链定向测试。
 *
 * 覆盖（全部使用合成目录 + mock DAO，不触真机与真实书库）：
 * - [BookRepository.clearBookCache]：TXT/EPUB 有残留时整本拒绝且文件与 DB 指针原样保留；
 *   无残留时保持原有清理功能；内容位置不可列举时保守拒绝。
 * - [deleteTreeSparingResidue]：残留保护删除器的兜底行为（发现标记立即中止且保留标记）。
 * - [OfflineBooksResidueClear]：「清空所有离线书籍」的逐条保护、部分清理与原行为兼容。
 */
class BookResidueSafeCleanupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val bookDao: BookDao = mockk(relaxed = true)
    private val bookContentDao: BookContentDao = mockk(relaxed = true)
    private val bookFileDao: BookFileDao = mockk(relaxed = true)

    private lateinit var repository: BookRepository
    private lateinit var filesRoot: File

    @Before
    fun setUp() {
        filesRoot = tmp.newFolder("files")
        val context = mockk<Context>()
        every { context.filesDir } returns filesRoot
        repository = BookRepository(
            bookDao = bookDao,
            bookContentDao = bookContentDao,
            bookFileDao = bookFileDao,
            progressDao = mockk(relaxed = true),
            sessionDao = mockk(relaxed = true),
            noteDao = mockk(relaxed = true),
            highlightDao = mockk(relaxed = true),
            inspirationDao = mockk(relaxed = true),
            bookTagDao = mockk(relaxed = true),
            bookCategoryDao = mockk(relaxed = true),
            shelfBookDao = mockk(relaxed = true),
            chapterReadDao = mockk(relaxed = true),
            context = context,
        )
    }

    // ── clearBookCache：TXT 残留 ─────────────────────────────

    @Test
    fun `clearBookCache refuses and preserves txt residue backup`() = runTest {
        val dir = File(filesRoot, "books/b1").apply { mkdirs() }
        File(dir, "book.txt").writeText("正文")
        File(dir, "book.txt.backup").writeText("旧正文备份")
        File(dir, "book.txt.repairing").writeText("新源暂存")

        val outcome = repository.clearBookCache("b1")

        assertEquals(BookCacheClearOutcome.RefusedResidue(residueCount = 2), outcome)
        assertTrue("残留 backup 必须保留", File(dir, "book.txt.backup").exists())
        assertTrue(File(dir, "book.txt.repairing").exists())
        assertTrue("原正文必须保留", File(dir, "book.txt").exists())
        assertTrue(dir.exists())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
        coVerify(exactly = 0) { bookFileDao.upsert(any()) }
    }

    // ── clearBookCache：EPUB 残留（三种标记）──────────────────

    @Test
    fun `clearBookCache refuses and preserves epub residue backup`() = runTest {
        val epubDir = File(filesRoot, "books/epub").apply { mkdirs() }
        File(epubDir, "b2.epub").writeBytes(byteArrayOf(1, 2, 3))
        File(epubDir, "b2.backup").writeBytes(byteArrayOf(9, 9))

        val outcome = repository.clearBookCache("b2")

        assertEquals(BookCacheClearOutcome.RefusedResidue(residueCount = 1), outcome)
        assertTrue(File(epubDir, "b2.backup").exists())
        assertTrue("同书 epub 正文必须一并保留", File(epubDir, "b2.epub").exists())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
        coVerify(exactly = 0) { bookFileDao.upsert(any()) }
    }

    @Test
    fun `clearBookCache refuses epub repairing and importing markers`() = runTest {
        val epubDir = File(filesRoot, "books/epub").apply { mkdirs() }
        File(epubDir, "rep1.repairing").writeBytes(byteArrayOf(1))
        File(epubDir, "imp1.importing").writeBytes(byteArrayOf(2))

        assertEquals(
            BookCacheClearOutcome.RefusedResidue(1),
            repository.clearBookCache("rep1"),
        )
        assertEquals(
            BookCacheClearOutcome.RefusedResidue(1),
            repository.clearBookCache("imp1"),
        )
        assertTrue(File(epubDir, "rep1.repairing").exists())
        assertTrue(File(epubDir, "imp1.importing").exists())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    // ── clearBookCache：无残留正常清理 ────────────────────────

    @Test
    fun `clearBookCache keeps original cleanup for clean txt book`() = runTest {
        val dir = File(filesRoot, "books/c1").apply { mkdirs() }
        File(dir, "book.txt").writeText("正文")
        stubBookRows("c1")

        val outcome = repository.clearBookCache("c1")

        assertEquals(BookCacheClearOutcome.Cleared, outcome)
        assertFalse("干净书籍目录应被完整删除", dir.exists())
        verifyDbPointersCleared("c1")
    }

    @Test
    fun `clearBookCache keeps original cleanup for clean epub book`() = runTest {
        val epubDir = File(filesRoot, "books/epub").apply { mkdirs() }
        File(epubDir, "c2.epub").writeBytes(byteArrayOf(1, 2, 3))
        stubBookRows("c2")

        val outcome = repository.clearBookCache("c2")

        assertEquals(BookCacheClearOutcome.Cleared, outcome)
        assertFalse("干净 epub 应被删除", File(epubDir, "c2.epub").exists())
        verifyDbPointersCleared("c2")
    }

    @Test
    fun `clearBookCache is no-op for unknown book without files`() = runTest {
        coEvery { bookDao.getById("none") } returns null
        coEvery { bookContentDao.getByBook("none") } returns null
        coEvery { bookFileDao.getByBook("none") } returns null

        val outcome = repository.clearBookCache("none")

        assertEquals(BookCacheClearOutcome.Cleared, outcome)
        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    // ── clearBookCache：EPUB 图片缓存目录 `books/epub/<id>/` ──────────

    @Test
    fun `clearBookCache reclaims clean epub image cache directory`() = runTest {
        // EpubParser 抽取章节内嵌图片时创建 books/epub/<id>/img/ 并写入图片。
        // 修复前只删 <id>.epub，该目录被留成无主文件、空间不释放。
        val epubDir = File(filesRoot, "books/epub").apply { mkdirs() }
        File(epubDir, "img1.epub").writeBytes(byteArrayOf(1, 2, 3))
        val imgCache = File(epubDir, "img1").apply { mkdirs() }
        File(imgCache, "img").mkdirs()
        val extracted = File(imgCache, "img/cover-0.jpg").apply { writeBytes(ByteArray(64) { 7 }) }
        stubBookRows("img1")

        val outcome = repository.clearBookCache("img1")

        assertEquals(BookCacheClearOutcome.Cleared, outcome)
        assertFalse("EPUB 内部副本应被删除", File(epubDir, "img1.epub").exists())
        assertFalse("图片缓存目录应被回收", imgCache.exists())
        assertFalse("抽取出的图片不得残留", extracted.exists())
        verifyDbPointersCleared("img1")
    }

    @Test
    fun `clearBookCache preserves epub image cache and db when residue inside it`() = runTest {
        // 图片缓存目录内出现残留标记 ⇒ 整本拒绝：文件与 DB 指针零改动。
        val epubDir = File(filesRoot, "books/epub").apply { mkdirs() }
        File(epubDir, "img2.epub").writeBytes(byteArrayOf(1, 2, 3))
        val imgCache = File(epubDir, "img2").apply { mkdirs() }
        val residue = File(imgCache, "img/part.backup").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9, 9))
        }
        stubBookRows("img2")

        val outcome = repository.clearBookCache("img2")

        assertEquals(BookCacheClearOutcome.RefusedResidue(residueCount = 1), outcome)
        assertTrue("残留标记必须保留", residue.exists())
        assertTrue("图片缓存目录必须保留", imgCache.exists())
        assertTrue("EPUB 内部副本必须保留", File(epubDir, "img2.epub").exists())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
        coVerify(exactly = 0) { bookFileDao.upsert(any()) }
    }

    @Test
    fun `clearBookCache only touches the target book image cache directory`() = runTest {
        // 关键边界：只处理目标书自己的 <id>/ 目录，books/epub/ 下其他书一概不碰。
        val epubDir = File(filesRoot, "books/epub").apply { mkdirs() }
        val mine = File(epubDir, "mine").apply { mkdirs() }
        val mineImgDir = File(mine, "img").apply { mkdirs() }
        File(mineImgDir, "a.jpg").writeBytes(byteArrayOf(1))
        val other = File(epubDir, "other").apply { mkdirs() }
        val otherImgDir = File(other, "img").apply { mkdirs() }
        val otherImg = File(otherImgDir, "b.jpg").apply { writeBytes(byteArrayOf(2)) }
        stubBookRows("mine")

        val outcome = repository.clearBookCache("mine")

        assertEquals(BookCacheClearOutcome.Cleared, outcome)
        assertFalse("目标书的图片缓存应被回收", mine.exists())
        assertTrue("其他书的图片缓存目录必须原样保留", other.exists())
        assertTrue("其他书的图片必须原样保留", otherImg.exists())
    }

    @Test
    fun `clearBookCache reports remains when epub image cache deletion fails`() = runTest {
        // 图片文件被占用：不得显示完全清理成功，须如实返回 ClearedWithRemains。
        Assume.assumeTrue(System.getProperty("os.name", "").contains("Windows", ignoreCase = true))
        val epubDir = File(filesRoot, "books/epub").apply { mkdirs() }
        val imgCache = File(epubDir, "img3").apply { mkdirs() }
        val locked = File(imgCache, "img/locked.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        stubBookRows("img3")

        FileOutputStream(locked).use {
            val outcome = repository.clearBookCache("img3")

            assertEquals(BookCacheClearOutcome.ClearedWithRemains, outcome)
            assertTrue("被占用的图片必须保留", locked.exists())
            // 未删净不中止流程：DB 指针照常清空（既有尽力而为边界）。
            verifyDbPointersCleared("img3")
        }
    }

    @Test
    fun `clearBookCache reports remains and keeps files when txt deletion fails`() = runTest {
        // Windows 语义：以输出流占用文件时 delete() 失败——确定性模拟"正文文件被占用"。
        // 该模拟依赖 Windows 独占删除语义，非 Windows 平台优雅跳过。
        Assume.assumeTrue(System.getProperty("os.name", "").contains("Windows", ignoreCase = true))
        val dir = File(filesRoot, "books/r1").apply { mkdirs() }
        val lockedFile = File(dir, "book.txt").apply { writeText("正文") }
        val deletableFile = File(dir, "cover.jpg").apply { writeText("x") }
        stubBookRows("r1")

        FileOutputStream(lockedFile).use {
            val outcome = repository.clearBookCache("r1")

            assertEquals(BookCacheClearOutcome.ClearedWithRemains, outcome)
            assertTrue("未删净的正文文件必须保留", lockedFile.exists())
            assertFalse("其余文件照常清理（不扩大失败范围）", deletableFile.exists())
            // 数据库边界不变：即便正文未删净，指针与书架状态也必须如实更新。
            verifyDbPointersCleared("r1")
        }
    }

    @Test
    fun `clearBookCache reports remains when epub deletion fails`() = runTest {
        Assume.assumeTrue(System.getProperty("os.name", "").contains("Windows", ignoreCase = true))
        val epubDir = File(filesRoot, "books/epub").apply { mkdirs() }
        val lockedEpub = File(epubDir, "r2.epub").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        stubBookRows("r2")

        FileOutputStream(lockedEpub).use {
            val outcome = repository.clearBookCache("r2")

            assertEquals(BookCacheClearOutcome.ClearedWithRemains, outcome)
            assertTrue("未删净的 EPUB 正文必须保留", lockedEpub.exists())
            verifyDbPointersCleared("r2")
        }
    }

    // ── clearBookCache：无法可靠判定 ──────────────────────────

    @Test
    fun `clearBookCache refuses unreliably scannable content location`() = runTest {
        File(filesRoot, "books").mkdirs()
        // books/blocked 是普通文件而非目录：listFiles() 返回 null，无法可靠判定 → 保守拒绝
        File(filesRoot, "books/blocked").writeText("not a directory")

        val outcome = repository.clearBookCache("blocked")

        assertEquals(BookCacheClearOutcome.RefusedUnsafeScan, outcome)
        assertTrue(File(filesRoot, "books/blocked").exists())
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
    }

    // ── clearBookCache：文件阶段中途遇新残留 → AbortedMidway ──

    @Test
    fun `clearBookCache aborts midway when residue appears after clean precheck`() = runTest {
        // 直接覆盖仓储层映射：预检 Clean → 文件阶段遇新残留标记 → AbortedMidway。
        // 这不是 deleteTreeSparingResidue 的单测：删除器自身的保留语义由既有
        // `sparing deleter ...` 用例覆盖；本例断言的是仓储把 Aborted 折叠成
        // AbortedMidway(RESIDUE_APPEARED)，并据此拦截全部数据库写入。
        val dir = File(filesRoot, "books/mid1").apply { mkdirs() }
        // 预检阶段：目录只有一个普通正文文件 → scanDirForResidue 判定 Clean。
        File(dir, "chapter-01.txt").writeText("第一章正文")
        stubBookRows("mid1")
        // 删除阶段：仅目标正文目录中途返回 ABORTED_RESIDUE。
        // try/finally 保证顶层静态 mock 一定解除：本类只有 @Before、没有 @After，
        // 若仓储调用或任一断言抛错，残留的静态 mock 会污染同类及同进程后续用例。
        try {
            abortFilePhase(TreeDeletionOutcome.ABORTED_RESIDUE)

            val outcome = repository.clearBookCache("mid1")

            assertEquals(
                BookCacheClearOutcome.AbortedMidway(MidwayAbortCause.RESIDUE_APPEARED),
                outcome,
            )
            // 中止不得回滚：目录与其中内容原样保留（删除器在此被桩掉，真实语义见既有用例）。
            assertTrue(dir.exists())
            assertTrue(File(dir, "chapter-01.txt").exists())
            // 核心：AbortedMidway 必须拦截数据库改动——三张表 upsert 均为 0。
            verifyDbUntouched()
        } finally {
            unmockFilePhase()
        }
    }

    @Test
    fun `clearBookCache aborts midway when content location becomes unreadable`() = runTest {
        // 同族覆盖：预检 Clean → 删除阶段位置不可列举 → AbortedMidway(LOCATION_UNREADABLE)。
        val dir = File(filesRoot, "books/mid2").apply { mkdirs() }
        File(dir, "chapter-01.txt").writeText("第一章正文")
        stubBookRows("mid2")
        // 同上：try/finally 兜底解除静态 mock（本类无 @After）。
        try {
            abortFilePhase(TreeDeletionOutcome.ABORTED_UNREADABLE)

            val outcome = repository.clearBookCache("mid2")

            assertEquals(
                BookCacheClearOutcome.AbortedMidway(MidwayAbortCause.LOCATION_UNREADABLE),
                outcome,
            )
            assertTrue(dir.exists())
            verifyDbUntouched()
        } finally {
            unmockFilePhase()
        }
    }

    // ── deleteTreeSparingResidue：删除器兜底 ──────────────────

    @Test
    fun `sparing deleter aborts on marker and keeps it`() {
        val dir = File(filesRoot, "books/d1").apply { mkdirs() }
        File(dir, "sub").mkdirs()
        File(dir, "a.txt").writeText("a")
        File(dir, "sub/b.txt").writeText("b")
        val marker = File(dir, "x.backup").apply { writeText("旧正文") }

        val result = deleteTreeSparingResidue(dir)

        assertEquals(TreeDeletionOutcome.ABORTED_RESIDUE, result.outcome)
        assertTrue("残留标记必须原样保留", marker.exists())
        assertTrue(dir.exists())
    }

    @Test
    fun `sparing deleter fully removes clean tree`() {
        val dir = File(filesRoot, "books/d2").apply { mkdirs() }
        File(dir, "sub").mkdirs()
        File(dir, "a.txt").writeText("a")
        File(dir, "sub/b.txt").writeText("b")

        val result = deleteTreeSparingResidue(dir)

        assertEquals(TreeDeletionOutcome.COMPLETED, result.outcome)
        assertTrue(result.deletedAny)
        assertFalse(dir.exists())
    }

    @Test
    fun `sparing deleter keeps later subtree untouched when marker appears mid-list`() {
        // 通过 mock listFiles 固定遍历顺序：先删 a.txt → 遇 x.backup 中止 → late/ 不得再动。
        val dir = File(filesRoot, "books/d3").apply { mkdirs() }
        val early = File(dir, "a.txt").apply { writeText("a") }
        val marker = File(dir, "x.backup").apply { writeText("旧正文") }
        val lateDir = File(dir, "late").apply { mkdirs() }
        val lateFile = File(lateDir, "b.txt").apply { writeText("b") }
        val orderedRoot = mockk<File> {
            every { listFiles() } returns arrayOf(early, marker, lateDir)
        }

        val result = deleteTreeSparingResidue(orderedRoot)

        assertEquals(TreeDeletionOutcome.ABORTED_RESIDUE, result.outcome)
        assertTrue("中止前已删除过文件，必须标记为部分执行", result.deletedAny)
        assertFalse("中止前的清理照常生效", early.exists())
        assertTrue("残留标记必须原样保留", marker.exists())
        assertTrue("中止后不得继续删除后续子树", lateFile.exists())
        assertTrue(lateDir.exists())
    }

    @Test
    fun `sparing deleter reports untouched abort when nothing was deleted before abort`() {
        // 首个条目即标记：未删除任何文件就被中止 → deletedAny=false（整条未动，非部分执行）。
        val markerOnly = mockk<File> {
            every { name } returns "x.backup"
            every { isDirectory } returns false
        }
        val markerRoot = mockk<File> {
            every { listFiles() } returns arrayOf(markerOnly)
        }
        assertEquals(
            TreeDeletionResult(TreeDeletionOutcome.ABORTED_RESIDUE, deletedAny = false),
            deleteTreeSparingResidue(markerRoot),
        )
        // 根目录本身不可列举：同样未动即中止。
        val unreadableRoot = mockk<File> {
            every { listFiles() } returns null
        }
        assertEquals(
            TreeDeletionResult(TreeDeletionOutcome.ABORTED_UNREADABLE, deletedAny = false),
            deleteTreeSparingResidue(unreadableRoot),
        )
    }

    @Test
    fun `sparing deleter reports completed with remains when file delete fails`() {
        // 普通文件删除失败：不中止、不计入完整清理，如实报告 COMPLETED_WITH_REMAINS。
        val failingFile = mockk<File> {
            every { name } returns "a.txt"
            every { isDirectory } returns false
            every { delete() } returns false
            every { exists() } returns true
        }
        val root = mockk<File> {
            every { listFiles() } returns arrayOf(failingFile)
            every { delete() } returns false
        }

        val result = deleteTreeSparingResidue(root)

        assertEquals(TreeDeletionOutcome.COMPLETED_WITH_REMAINS, result.outcome)
        assertFalse(result.deletedAny)
    }

    // ── OfflineBooksResidueClear：清空所有离线书籍 ────────────

    @Test
    fun `clear all preserves residue entries and clears clean ones`() {
        val books = File(filesRoot, "books").apply { mkdirs() }
        // 干净 TXT 目录
        File(books, "t1").mkdirs()
        File(books, "t1/a.txt").writeText("x")
        // 含嵌套残留的 TXT 目录
        File(books, "t2/sub").mkdirs()
        File(books, "t2/cur.txt").writeText("x")
        File(books, "t2/sub/old.backup").writeText("x")
        // epub 目录：干净、被同书残留保护、标记文件、无主杂项
        val epub = File(books, "epub").apply { mkdirs() }
        File(epub, "e1.epub").writeBytes(byteArrayOf(1))
        File(epub, "e2.epub").writeBytes(byteArrayOf(2))
        File(epub, "e2.backup").writeBytes(byteArrayOf(3))
        File(epub, "e3.repairing").writeBytes(byteArrayOf(4))
        File(epub, "stray.txt").writeText("x")

        val report = OfflineBooksResidueClear.clear(books)

        assertEquals(3, report.clearedEntries) // t1、e1.epub、stray.txt
        assertEquals(4, report.protectedEntries) // t2、e2.epub（同书保护）、e2.backup、e3.repairing
        assertEquals(0, report.unreadableEntries)
        assertFalse(report.rootUnreadable)
        assertFalse(File(books, "t1").exists())
        assertTrue("含残留的整本书目录必须保留", File(books, "t2/cur.txt").exists())
        assertTrue(File(books, "t2/sub/old.backup").exists())
        assertFalse(File(epub, "e1.epub").exists())
        assertTrue("同书残留存在时其 epub 必须保留", File(epub, "e2.epub").exists())
        assertTrue(File(epub, "e2.backup").exists())
        assertTrue(File(epub, "e3.repairing").exists())
        assertFalse(File(epub, "stray.txt").exists())
    }

    @Test
    fun `clear all without residue removes everything like before`() {
        val books = File(filesRoot, "books").apply { mkdirs() }
        File(books, "t1").mkdirs()
        File(books, "t1/a.txt").writeText("x")
        val epub = File(books, "epub").apply { mkdirs() }
        File(epub, "e1.epub").writeBytes(byteArrayOf(1))

        val report = OfflineBooksResidueClear.clear(books)

        assertEquals(2, report.clearedEntries)
        assertEquals(0, report.protectedEntries)
        assertFalse("无残留时保持原有整目录清理（含 epub 目录壳）", books.exists())
    }

    @Test
    fun `clear all on missing books dir is a no-op`() {
        val report = OfflineBooksResidueClear.clear(File(filesRoot, "books"))

        assertEquals(OfflineBooksClearReport(), report)
        assertEquals("没有需要清理的离线书籍文件。", report.userNotice())
    }

    @Test
    fun `clear all keeps stray residue marker at books root`() {
        val books = File(filesRoot, "books").apply { mkdirs() }
        File(books, "t1").mkdirs()
        File(books, "t1/a.txt").writeText("x")
        File(books, "orphan.backup").writeText("孤儿残留")

        val report = OfflineBooksResidueClear.clear(books)

        assertEquals(1, report.clearedEntries)
        assertEquals(1, report.protectedEntries)
        assertTrue(File(books, "orphan.backup").exists())
        assertFalse(File(books, "t1").exists())
    }

    // ── 结果与提示一致性：先成功、后遇不安全目录 ───────────────

    @Test
    fun `clear all reports partial execution when unsafe dir follows cleared entries`() {
        // 模拟「先清理 t1 成功，随后 t2 目录不可读取」：clearedEntries>0 且
        // unreadableEntries>0，提示必须说「部分执行」，不得声称「未删除任何文件」。
        val books = File(filesRoot, "books").apply { mkdirs() }
        val cleanDir = File(books, "t1").apply { mkdirs() }
        File(cleanDir, "a.txt").writeText("x")
        val unreadableDir = mockk<File> {
            every { name } returns "t2"
            every { isDirectory } returns true
            every { listFiles() } returns null
        }
        val booksDir = mockk<File> {
            every { exists() } returns true
            every { isDirectory } returns true
            every { listFiles() } returns arrayOf(cleanDir, unreadableDir)
            every { delete() } returns false
        }

        val report = OfflineBooksResidueClear.clear(booksDir)

        assertEquals(1, report.clearedEntries)
        assertEquals(0, report.protectedEntries)
        assertEquals(1, report.unreadableEntries)
        assertFalse(report.rootUnreadable)
        assertFalse("先成功的条目确实已清理", cleanDir.exists())
        val notice = report.userNotice()
        assertTrue(notice.contains("清空已部分执行"))
        assertTrue(notice.contains("已清理 1 个离线条目"))
        assertTrue(notice.contains("1 个条目无法读取"))
        assertFalse("不得虚报为完全未动", notice.contains("未删除任何文件"))
    }

    @Test
    fun `clear all counts partial residue abort when first dir partially deleted and others untouched`() {
        // 核心回归：首个目录内部先删除了文件、随后中止于新出现残留，其他条目均未清理。
        // 用 mock 固定遍历顺序并确定性模拟 TOCTOU：预检时标记名无后缀，删除阶段才暴露。
        val books = File(filesRoot, "books").apply { mkdirs() }
        val earlyFile = File(books, "placeholder-early.txt").apply { writeText("a") }
        val markerMock = mockk<File> {
            every { name } returnsMany listOf("innocent.txt", "x.backup")
            every { isDirectory } returns false
        }
        val partialDir = mockk<File> {
            every { name } returns "t1"
            every { isDirectory } returns true
            every { listFiles() } returns arrayOf(earlyFile, markerMock)
        }
        val protectedDir = mockk<File> {
            every { name } returns "t2"
            every { isDirectory } returns true
            every { listFiles() } returns arrayOf(mockk<File> { every { name } returns "old.backup" })
        }
        val booksDir = mockk<File> {
            every { exists() } returns true
            every { isDirectory } returns true
            every { listFiles() } returns arrayOf(partialDir, protectedDir)
            every { delete() } returns false
        }

        val report = OfflineBooksResidueClear.clear(booksDir)

        assertEquals(0, report.clearedEntries)
        assertEquals(1, report.partialResidueEntries)
        assertEquals(1, report.protectedEntries)
        assertEquals(0, report.unreadableEntries)
        assertEquals(0, report.remainsEntries)
        assertFalse("目录内部的部分删除必须实际发生", earlyFile.exists())
        val notice = report.userNotice()
        assertTrue(notice.contains("清空已部分执行"))
        assertTrue(notice.contains("1 个条目在清理中发现新的替换残留、已中止"))
        assertTrue(notice.contains("部分文件已被清理"))
        assertTrue(notice.contains("1 个条目包含未完成替换的残留备份"))
        assertFalse("不得虚报为未执行", notice.contains("清空未执行"))
        assertFalse(notice.contains("未删除任何文件"))
        assertFalse(notice.contains("已清空所有离线书籍与资源"))
    }

    @Test
    fun `clear all counts partial unreadable abort when first dir partially deleted and others untouched`() {
        // 核心回归：不可读取原因的"部分删除后中止"。子目录预检时可列举（空），
        // 删除阶段变为 null（returnsMany 确定性模拟 TOCTOU）。
        val books = File(filesRoot, "books").apply { mkdirs() }
        val earlyFile = File(books, "placeholder-early-u.txt").apply { writeText("a") }
        val unreadableChild = mockk<File> {
            every { name } returns "sub"
            every { isDirectory } returns true
            every { listFiles() } returnsMany listOf(arrayOf<File>(), null)
        }
        val partialDir = mockk<File> {
            every { name } returns "t1"
            every { isDirectory } returns true
            every { listFiles() } returns arrayOf(earlyFile, unreadableChild)
        }
        val unreadableDir = mockk<File> {
            every { name } returns "t2"
            every { isDirectory } returns true
            every { listFiles() } returns null
        }
        val booksDir = mockk<File> {
            every { exists() } returns true
            every { isDirectory } returns true
            every { listFiles() } returns arrayOf(partialDir, unreadableDir)
            every { delete() } returns false
        }

        val report = OfflineBooksResidueClear.clear(booksDir)

        assertEquals(0, report.clearedEntries)
        assertEquals(1, report.partialUnreadableEntries)
        assertEquals(1, report.unreadableEntries)
        assertEquals(0, report.protectedEntries)
        assertFalse(earlyFile.exists())
        val notice = report.userNotice()
        assertTrue(notice.contains("清空已部分执行"))
        assertTrue(notice.contains("1 个条目在清理中遇到不可读取的目录、已中止"))
        assertTrue(notice.contains("部分文件已被清理"))
        assertFalse(notice.contains("清空未执行"))
        assertFalse(notice.contains("未删除任何文件"))
        assertFalse(notice.contains("已清空所有离线书籍与资源"))
    }

    @Test
    fun `clear all counts zero-deletion residue abort as untouched protected entry`() {
        // 预检通过后、删除首个子项前即出现标记：未删除任何文件即中止 →
        // 计入"整条保留"（原样保留），不得计入部分执行。
        val markerMock = mockk<File> {
            every { name } returnsMany listOf("innocent.txt", "x.backup")
            every { isDirectory } returns false
        }
        val dir = mockk<File> {
            every { name } returns "t1"
            every { isDirectory } returns true
            every { listFiles() } returns arrayOf(markerMock)
        }
        val booksDir = mockk<File> {
            every { exists() } returns true
            every { isDirectory } returns true
            every { listFiles() } returns arrayOf(dir)
            every { delete() } returns false
        }

        val report = OfflineBooksResidueClear.clear(booksDir)

        assertEquals(1, report.protectedEntries)
        assertEquals(0, report.partialResidueEntries)
        assertEquals(0, report.clearedEntries)
        assertTrue(report.userNotice().contains("清空未执行"))
    }

    @Test
    fun `clear all does not claim fully cleared when file deletion fails`() {
        // 普通文件删除失败：既不得计入已清理，也不得称"已清空所有/没有需要清理"。
        val failingFile = mockk<File> {
            every { name } returns "f.txt"
            every { isDirectory } returns false
            every { delete() } returns false
            every { exists() } returns true
        }
        val booksDir = mockk<File> {
            every { exists() } returns true
            every { isDirectory } returns true
            every { listFiles() } returns arrayOf(failingFile)
            every { delete() } returns false
        }

        val report = OfflineBooksResidueClear.clear(booksDir)

        assertEquals(0, report.clearedEntries)
        assertEquals(1, report.remainsEntries)
        val notice = report.userNotice()
        assertTrue(notice.contains("清空未完成"))
        assertTrue(notice.contains("1 个条目未能完全删除"))
        assertFalse(notice.contains("已清空所有离线书籍与资源"))
        assertFalse(notice.contains("没有需要清理的离线书籍文件"))
    }

    @Test
    fun `user notice reports directory partial aborts distinctly from untouched entries`() {
        // 目录内部部分删除后中止：不得称"清空未执行"，也不得与"整条保留"混同。
        val partialResidue = OfflineBooksClearReport(partialResidueEntries = 1, protectedEntries = 1).userNotice()
        assertTrue(partialResidue.contains("清空已部分执行"))
        assertTrue(partialResidue.contains("1 个条目在清理中发现新的替换残留、已中止"))
        assertTrue(partialResidue.contains("部分文件已被清理"))
        assertTrue(partialResidue.contains("1 个条目包含未完成替换的残留备份"))
        assertFalse(partialResidue.contains("清空未执行"))

        val partialUnreadable = OfflineBooksClearReport(partialUnreadableEntries = 2).userNotice()
        assertTrue(partialUnreadable.contains("清空已部分执行"))
        assertTrue(partialUnreadable.contains("2 个条目在清理中遇到不可读取的目录、已中止"))

        // 仅有删除失败（无任何实际删除）："清空未完成"，且不得称"已清空所有"。
        val remainsOnly = OfflineBooksClearReport(remainsEntries = 1).userNotice()
        assertTrue(remainsOnly.contains("清空未完成"))
        assertTrue(remainsOnly.contains("1 个条目未能完全删除"))
        assertFalse(remainsOnly.contains("已清空所有离线书籍与资源"))
    }

    @Test
    fun `user notice distinguishes untouched partial and completed outcomes`() {
        // 完全未动（books/ 本身不可读）。
        assertEquals(
            "无法读取本地书籍目录，清空操作未执行、未删除任何文件。",
            OfflineBooksClearReport(rootUnreadable = true).userNotice(),
        )
        // 没有可清理内容。
        assertEquals("没有需要清理的离线书籍文件。", OfflineBooksClearReport().userNotice())
        // 完成。
        val completed = OfflineBooksClearReport(clearedEntries = 3).userNotice()
        assertTrue(completed.contains("已清空所有离线书籍与资源"))
        assertTrue(completed.contains("共清理 3 个条目"))
        // 完全未动（全部条目含残留）。
        val untouched = OfflineBooksClearReport(clearedEntries = 0, protectedEntries = 2).userNotice()
        assertTrue(untouched.contains("清空未执行"))
        assertTrue(untouched.contains("2 个条目包含未完成替换的残留备份"))
        assertFalse(untouched.contains("已清理"))
        // 部分执行（残留 + 不可读取混合）。
        val partial = OfflineBooksClearReport(clearedEntries = 1, protectedEntries = 2, unreadableEntries = 1).userNotice()
        assertTrue(partial.contains("清空已部分执行"))
        assertTrue(partial.contains("已清理 1 个离线条目"))
        assertTrue(partial.contains("2 个条目包含未完成替换的残留备份"))
        assertTrue(partial.contains("1 个条目无法读取"))
    }

    // ── 桩与断言辅助 ─────────────────────────────────────────

    /**
     * 确定性触发 AbortedMidway 的注入接缝（只在测试内生效，不改生产代码）。
     *
     * 为什么必须注入：`clearBookCache` 的预检（`scanBookContentResidue`）与文件阶段
     * （`deleteBookContentFiles`）扫描**完全相同的三处位置**，且两次扫描之间没有可挂测试钩的
     * 中间点。因此「预检判定 Clean」与「同一位置已存在真实残留」在真实文件系统上互斥——
     * AbortedMidway 只能经 TOCTOU 窗口（预检后、删除中新出现残留）触发，
     * 用真实文件+时序无法确定性复现。
     *
     * 接缝选择：桩掉文件阶段唯一的删除入口 `deleteTreeSparingResidue`（顶层 internal 函数），
     * 令其返回中止结果。这**不是**测删除器本身——删除器自身的保留语义由本文件既有的
     * `sparing deleter ...` 用例覆盖；这里断言的是**仓储层映射**：
     * `ContentFileDeletionOutcome.Aborted` → `BookCacheClearOutcome.AbortedMidway`，
     * 以及据此对三张表 upsert 的整段拦截。
     */
    private fun abortFilePhase(outcome: TreeDeletionOutcome): TreeDeletionResult {
        val result = TreeDeletionResult(outcome, deletedAny = true)
        mockkStatic(::deleteTreeSparingResidue)
        every { deleteTreeSparingResidue(any()) } returns result
        return result
    }

    private fun unmockFilePhase() {
        unmockkStatic(::deleteTreeSparingResidue)
    }

    /** 三张表的 upsert 均为 0：AbortedMidway 必须整段拦截数据库改动。 */
    private fun verifyDbUntouched() {
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { bookContentDao.upsert(any()) }
        coVerify(exactly = 0) { bookFileDao.upsert(any()) }
    }

    private fun stubBookRows(bookId: String) {
        coEvery { bookDao.getById(bookId) } returns BookEntity(
            id = bookId, title = "书", format = "txt",
            content_status = "available", local_uri = "file:///x",
            updated_at = "2026-01-01T00:00:00Z",
        )
        coEvery { bookContentDao.getByBook(bookId) } returns
            BookContentEntity(book_id = bookId, reader_preview = "预览", epub_json = "{}")
        coEvery { bookFileDao.getByBook(bookId) } returns
            BookFileEntity(
                book_id = bookId, file_name = "$bookId.txt", format = "txt",
                updated_at = "2026-01-01T00:00:00Z",
            )
    }

    private fun verifyDbPointersCleared(bookId: String) {
        coVerify(exactly = 1) {
            bookDao.upsert(withArg {
                assertEquals("missing", it.content_status)
                assertNull(it.local_uri)
                assertNull(it.local_content_path)
            })
        }
        coVerify(exactly = 1) {
            bookContentDao.upsert(withArg {
                assertNull(it.reader_preview)
                assertNull(it.epub_json)
            })
        }
        coVerify(exactly = 1) {
            bookFileDao.upsert(withArg { assertTrue(it.deleted_at != null) })
        }
    }
}
