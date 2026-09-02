package com.creationreadingassistant.feature.sync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
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
        coEvery { jsonBridge.importFromString(any(), any()) } answers { called = true }

        writeZip("backup.json" to "{\"schemaVersion\":1}", "books/x.txt" to "hi")

        subject(jsonBridge).importZip(fakeContext(), mockk<Uri>())

        assertTrue("解包后应调用 JsonBridge 入库", called)
    }

    @Test
    fun `importZip rejects zip missing backup json`() = runTest {
        val jsonBridge = mockk<JsonBridge>(relaxed = true)
        var called = false
        coEvery { jsonBridge.importFromString(any(), any()) } answers { called = true }

        // 只有 books 入口，缺少 backup.json
        writeZip("books/x.txt" to "hi")

        val result = runCatching {
            subject(jsonBridge).importZip(fakeContext(), mockk<Uri>())
        }

        assertTrue("缺少 backup.json 应拒绝导入", result.isFailure)
        assertFalse("应未触发入库", called)
    }
}
