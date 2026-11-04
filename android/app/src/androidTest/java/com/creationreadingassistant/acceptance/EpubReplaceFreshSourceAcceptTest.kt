package com.creationreadingassistant.acceptance

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.reader.EpubParser
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.FileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * CRA-JOINT-ACCEPTANCE-TESTS-v1：EPUB「同一来源 URI V1→V2」的**真机仪器**验收。
 *
 * ## 与 JVM 定向测试的关系
 *
 * `EpubRepositoryReplaceTest`（JVM）已在 mock ContentResolver 上钉住 fresh-source 合同；
 * 本类把同一合同放到**真实 Android 运行时**上复验：真实 `ContentResolver` 读 file:// 来源、
 * 真实 `EpubParser`（不 mock）、真实 `EpubRepository.replaceStoredEpub`（不 mock）、
 * 真实 Room（隔离测试宿主私有库）。CI/JVM 桩无法覆盖的层（ContentResolver 行为、
 * 文件系统句柄、Room 落库）由本类补齐。
 *
 * ## 隔离与数据边界
 *
 * - 本类只在**隔离测试宿主**（独立 applicationId 后缀 + 独立 UID 的测试包）中运行；
 *   所读写的是该宿主自己的私有 filesDir 与私有 Room 库，不触碰正式包书库；
 * - 全部夹具书 id 来自 `EpubParser` 对 file:// 来源的**生产派生 id**，测试在
 *   [tearDown] 按记录的 id 精确删除 DB 行与内部副本文件；
 * - 不直接写 DB 冒充替换成功：书籍登记走生产 `openEpub`，替换走生产
 *   `replaceStoredEpub`，测试只做**读**断言与测试自有数据清理（DELETE 仅限本批 id）。
 *
 * ## 绑定要求
 *
 * 本类绑定「fresh-source 修复后」的生产合同（S04 §9-1 / CRA-ROLLBACK-WIP-RECONCILE-v2/v3）：
 * 在未合入 fresh-source 修复的树上，`jera_10` 会因替换退化为 V1→V1 no-op 而 FAIL，
 * `jera_20` 会因旧 `EpubParser` 早退分支不读来源而 FAIL —— 这正是它们的价值：
 * 证明修复确实存在于被测树。**未合入 v3 修复的最终树不得运行本类冒充通过。**
 */
@RunWith(AndroidJUnit4::class)
class EpubReplaceFreshSourceAcceptTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repository: EpubRepository

    /** 本批创建的生产派生书 id（清理用）。 */
    private val createdBookIds = mutableListOf<String>()

    /** 本批创建的来源文件与目录（清理用）。 */
    private val createdFiles = mutableListOf<File>()

    private val sourceDir: File
        get() = File(context.filesDir, "jera-src").also { it.mkdirs() }

    @Before
    fun setUp() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .allowMainThreadQueries()
            .build()
        // FileOps 直通实现：与生产 DefaultFileOps（private）逐字同义，测试自有代码。
        repository = EpubRepository(
            context = context,
            bookDao = db.bookDao(),
            readingProgressDao = db.readingProgressDao(),
            ioDispatcher = Dispatchers.IO,
            fileOps = PassthroughFileOps(),
        )
    }

    @After
    fun tearDown() {
        var cleanupFailure: Throwable? = null
        try {
            createdBookIds.forEach(::deleteBookRowsByActualSchema)
        } catch (t: Throwable) {
            cleanupFailure = t
        } finally {
            try {
                deleteFixtureFiles()
            } catch (t: Throwable) {
                cleanupFailure?.addSuppressed(t) ?: run { cleanupFailure = t }
            } finally {
                try {
                    db.close()
                } catch (t: Throwable) {
                    cleanupFailure?.addSuppressed(t) ?: run { cleanupFailure = t }
                }
            }
        }
        cleanupFailure?.let { throw AssertionError("jera 测试清理失败", it) }
    }

    /**
     * Room v15 的七张关联表并不共享一个列名：只有 books 用 id，余下六张均用
     * book_id。按实际 schema 分开删除，避免首条 SQL 的 no-such-column 让 finally
     * 之前的测试文件遗留在隔离宿主中。
     */
    private fun deleteBookRowsByActualSchema(bookId: String) {
        db.openHelper.writableDatabase.apply {
            listOf("highlights", "notes", "reading_sessions").forEach { table ->
                execSQL("DELETE FROM $table WHERE book_id = ?", arrayOf(bookId))
            }
            listOf("book_files", "book_content", "reading_progress").forEach { table ->
                execSQL("DELETE FROM $table WHERE book_id = ?", arrayOf(bookId))
            }
            execSQL("DELETE FROM books WHERE id = ?", arrayOf(bookId))
        }
    }

    private fun deleteFixtureFiles() {
        val epubDir = File(context.filesDir, "books/epub")
        createdBookIds.forEach { id ->
            listOf("$id.epub", "$id.importing", "$id.repairing", "$id.backup").forEach { name ->
                File(epubDir, name).delete()
            }
            File(epubDir, id).deleteRecursively() // 图片缓存目录
        }
        createdFiles.forEach { f ->
            f.delete()
            f.parentFile?.takeIf { it.absolutePath == sourceDir.absolutePath }?.delete()
        }
        sourceDir.deleteRecursively()
    }

    // ── 夹具：构造最小合法 EPUB（真实 ZIP 结构，与 EpubParserInstrumentedTest 同构） ──

    private fun containerXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun opfXml(title: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title>
            <dc:identifier id="uid">jera-acceptance</dc:identifier>
          </metadata>
          <manifest>
            <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="c1"/>
          </spine>
        </package>
    """.trimIndent()

    private fun chapterXml(body: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head><title>章</title></head>
          <body><p>$body</p></body>
        </html>
    """.trimIndent()

    /** 在 [dest] 写出一个最小合法 EPUB；标题与正文可参数化以区分 V1/V2。 */
    private fun writeEpub(dest: File, title: String, body: String): File {
        dest.parentFile!!.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            fun entry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", containerXml())
            entry("OPS/package.opf", opfXml(title))
            entry("OPS/text/ch1.xhtml", chapterXml(body))
        }
        createdFiles += dest
        return dest
    }

    private fun md5Hex(f: File): String =
        MessageDigest.getInstance("MD5").digest(f.readBytes())
            .joinToString("") { "%02x".format(it) }

    /** EPUB 是 DEFLATED ZIP；正文标记必须从章节 entry 解压后读取，不能扫描压缩字节。 */
    private fun chapterText(f: File): String = ZipFile(f).use { zip ->
        val chapter = requireNotNull(zip.getEntry("OPS/text/ch1.xhtml")) {
            "夹具 EPUB 缺少 OPS/text/ch1.xhtml：${f.absolutePath}"
        }
        zip.getInputStream(chapter).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun chapterContains(f: File, marker: String): Boolean = chapterText(f).contains(marker)

    /** 生产派生书 id 的内部副本（target）文件。 */
    private fun targetFile(bookId: String): File =
        File(File(context.filesDir, "books/epub"), "$bookId.epub")

    private fun epubDir(): File = File(context.filesDir, "books/epub")

    /**
     * **主场景（jera_10）**：真实导入 V1 → 同一来源 URI 覆盖为 V2 → 真实替换。
     *
     * 断言：V2 字节（而非缓存的 V1）成为内部副本；DB 记录由**生产代码**更新
     * （标题、content_hash=新内部副本 MD5、id 不变）；`.importing`/`.repairing`/`.backup`
     * 零残留。
     */
    @Test
    fun jera_10_replaceSameUriReadsFreshV2Bytes() = runBlocking {
        // 1. V1 来源落盘
        val source = writeEpub(
            File(sourceDir, "same_source.epub"),
            title = "联合验收旧版甲",
            body = "V1MARKER旧正文第一段甲甲甲",
        )
        val uri = Uri.fromFile(source)

        // 2. 真实生产导入：EpubParser 解析 + persistOpenedBook 登记入库
        val v1Book = repository.openEpub(uri)
        val bookId = v1Book.id
        createdBookIds += bookId
        assertEquals("联合验收旧版甲", v1Book.title)

        val stored: BookEntity = db.bookDao().getById(bookId).let {
            assertNotNull("生产导入必须已在 Room 登记（bookId=$bookId）", it)
            it!!
        }
        assertEquals("内部副本必须已落盘于派生路径", targetFile(bookId).absolutePath, stored.local_content_path)
        val target = targetFile(bookId)
        assertTrue("V1 内部副本的章节必须含 V1 标记", chapterContains(target, "V1MARKER"))
        val v1Bytes = target.readBytes()

        // 3. 同一 URI 的内容更新为 V2（模拟"同源文件更新后重新选择"）
        writeEpub(
            File(sourceDir, "same_source.epub"),
            title = "联合验收新版乙",
            body = "V2MARKER新正文第二段乙乙乙",
        )
        assertTrue("前置：源文件仍为同一 URI", uri == Uri.fromFile(source))

        // 4. 真实生产替换（不 mock Parser / Repository）
        val replaced = repository.replaceStoredEpub(stored, uri, "same_source.epub")

        // 5. 内部副本字节必须真的换成 V2
        assertEquals("返回书 id 必须不变", bookId, replaced.id)
        assertEquals("新标题必须来自 V2 解析", "联合验收新版乙", replaced.title)
        val newBytes = target.readBytes()
        assertTrue("前置：V2 来源章节必须含 V2 标记", chapterContains(source, "V2MARKER"))
        assertTrue("新内部副本章节必须含 V2 标记", chapterContains(target, "V2MARKER"))
        assertFalse("新内部副本章节不得残留 V1 正文", chapterContains(target, "V1MARKER"))
        assertFalse("内部副本字节必须确实改变（不得 no-op）", v1Bytes.contentEquals(newBytes))
        assertArrayEquals("target 必须与 V2 来源逐字节一致", source.readBytes(), newBytes)

        // 6. DB 记录由生产代码更新（本测试从不直写 books 行）
        val after: BookEntity = db.bookDao().getById(bookId).let {
            assertNotNull("替换后记录必须仍在", it)
            it!!
        }
        assertEquals(bookId, after.id)
        assertEquals("DB 标题必须来自 V2", "联合验收新版乙", after.title)
        assertEquals("local_content_path 必须仍指向派生 target", target.absolutePath, after.local_content_path)
        assertEquals("content_hash 必须等于新内部副本 MD5（小文件策略）", md5Hex(target), after.content_hash)

        // 7. 临时文件零残留
        assertFalse("不得残留 .importing", File(epubDir(), "$bookId.importing").exists())
        assertFalse("不得残留 .repairing", File(epubDir(), "$bookId.repairing").exists())
        assertFalse("不得残留 .backup", File(epubDir(), "$bookId.backup").exists())
    }

    /**
     * **来源不可读（jera_20）**：同 URI 的来源字节消失后，替换必须失败，
     * 旧内部副本逐字节保留、DB 记录不变、临时文件零残留。
     *
     * fresh-source 合同要求「来源读取失败时绝不退回旧内部副本冒充新来源」：
     * 未合入 fresh-source 修复的树上，`EpubParser` 的早退分支不读来源，
     * 替换会以 V1→V1 no-op「成功」——本用例在该树上 FAIL，用于钉住 v3。
     */
    @Test
    fun jera_20_unreadableSourceFailsAndKeepsOldCopy() = runBlocking {
        val source = writeEpub(
            File(sourceDir, "vanish_source.epub"),
            title = "联合验收留存版",
            body = "KEEPOLDMARKER旧正文留存段",
        )
        val uri = Uri.fromFile(source)
        val v1Book = repository.openEpub(uri)
        val bookId = v1Book.id
        createdBookIds += bookId
        val stored = requireNotNull(db.bookDao().getById(bookId)) { "导入必须已登记（bookId=$bookId）" }
        val target = targetFile(bookId)
        val oldBytes = target.readBytes()
        val snapshot = db.bookDao().getById(bookId)!!

        // 来源消失：同 URI 再也不可读
        assertTrue(source.delete())

        var caught: Throwable? = null
        try {
            repository.replaceStoredEpub(stored, uri, "vanish_source.epub")
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue("来源不可读必须失败，caught=$caught", caught is Exception)

        // 旧字节逐字节保留
        assertTrue("旧 target 必须存在", target.isFile)
        assertArrayEquals("旧 target 必须逐字节完好", oldBytes, target.readBytes())

        // DB 零写入（与替换前快照逐字段一致）
        val after = requireNotNull(db.bookDao().getById(bookId)) { "记录必须仍在" }
        assertEquals("title 不得被改写", snapshot.title, after.title)
        assertEquals("content_hash 不得被改写", snapshot.content_hash, after.content_hash)
        assertEquals("updated_at 不得被改写", snapshot.updated_at, after.updated_at)
        assertEquals("local_uri 不得被改写", snapshot.local_uri, after.local_uri)
        assertEquals("local_content_path 不得被改写", snapshot.local_content_path, after.local_content_path)
        assertEquals("size 不得被改写", snapshot.size, after.size)

        // 临时文件零残留
        assertFalse("不得残留 .importing", File(epubDir(), "$bookId.importing").exists())
        assertFalse("不得残留 .repairing", File(epubDir(), "$bookId.repairing").exists())
        assertFalse("不得残留 .backup", File(epubDir(), "$bookId.backup").exists())
    }

    /** 与生产 `DefaultFileOps` 逐字同义的直通实现（生产实现为 private，无法直接复用）。 */
    private class PassthroughFileOps : FileOps {
        override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)
        override fun copyTo(source: File, dest: File) {
            source.copyTo(dest, overwrite = true)
        }
    }
}
