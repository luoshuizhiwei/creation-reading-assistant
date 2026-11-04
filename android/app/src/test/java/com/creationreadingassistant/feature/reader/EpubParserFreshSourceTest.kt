package com.creationreadingassistant.feature.reader

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `EpubParser.parse` 的「来源字节新鲜性」与「失败不留孤儿副本」定向测试。
 *
 * 锁两条真机已观察到的缺陷（S04 阶段 A 验收报告 §9-1 / §9-4）：
 *
 * 1. **同 URI V1→V2 更新失效**：`parse` 原本「内部副本已存在就直接沿用」，
 *    而导入时若按 Uri 派生 id，该副本路径就等于书的正式内部副本 —— 于是来源的新字节
 *    根本没被读过。修复后 [requireFreshSource] 强制从来源重新取字节。
 * 2. **损坏 EPUB 留下孤儿副本**：原本「先落盘、后校验」，校验失败只删 staging、
 *    不删已落位的正式副本，私有目录因此永久积累无人引用的文件。
 *
 * 本文件在 **parser 层**直接断言，因为这些不变量属于 `EpubParser` 自身，
 * 不能依赖调用方（`EpubRepository`）的兜底清理来证明。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpubParserFreshSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var resolver: ContentResolver
    private lateinit var context: Context

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        resolver = mockk()
        context = mockk {
            every { contentResolver } returns resolver
            every { filesDir } returns tempFolder.root
            every { cacheDir } returns tempFolder.root
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    /** 构造一个结构合法的最小 EPUB。bodyText 用于内容字节断言。 */
    private fun createValidEpub(dest: File, title: String, bodyText: String) {
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            zos.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opentest">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:identifier id="BookId">test-epub-id</dc:identifier>
                  </metadata>
                  <manifest>
                    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="ch1"/></spine>
                </package>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>$title</title></head>
                  <body><p>$bodyText</p></body>
                </html>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zos.closeEntry()
        }
    }

    /** 把 `file` 挂到一个 mock Uri 上，使 resolver.openInputStream 返回它的字节。 */
    private fun mockUri(file: File, uriString: String): Uri {
        val u = mockk<Uri>()
        every { u.toString() } returns uriString
        every { u.lastPathSegment } returns file.name
        every { Uri.fromFile(file) } returns u
        every { resolver.openInputStream(u) } answers { file.inputStream() }
        return u
    }

    /**
     * **同 URI 更新（V1→V2）**：`requireFreshSource = true` 时必须读来源的新字节。
     *
     * 关键构造：内部副本已存在（且内容为 V1）—— 这正是旧实现早退命中的前提。
     * 断言的是**解析到的文件字节含 V2 正文**，不是返回值；只断言 title 无法区分
     * 「真的换了」与「沿用旧副本」。
     */
    @Test
    fun `fresh source parses new bytes when internal copy already exists`() = runTest {
        val id = "epub_fresh_v1v2"
        val dir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val internal = File(dir, "$id.epub")
        createValidEpub(internal, "旧版书名", "V1MARKER旧正文")

        val v2Source = File(tempFolder.newFolder(), "source.epub")
        createValidEpub(v2Source, "新版书名", "V2MARKER新正文")
        val uri = mockUri(v2Source, "content://books/source.epub")

        val parsed = EpubParser.parse(
            context = context,
            uri = uri,
            expectedBookId = id,
            requireFreshSource = true,
        )

        val bytes = File(parsed.cachedEpubPath).readBytes()
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue("必须解析到 V2 正文，实际未命中", text.contains("V2MARKER"))
        assertFalse("不得沿用 V1 正文，实际未命中", text.contains("V1MARKER"))
        assertEquals("标题必须来自 V2", "新版书名", parsed.title)

        // 新鲜来源**绝不能**覆盖正式内部副本：此刻它仍是唯一完好的旧正文。
        assertArrayEquals(
            "正式内部副本在提升前必须仍为 V1",
            internal.readBytes(),
            File(dir, "$id.epub").readBytes(),
        )
        // 新鲜字节落在 .importing 上
        assertTrue("新鲜来源应落在 .importing", parsed.cachedEpubPath.endsWith("$id.importing"))
    }

    /**
     * **默认（非新鲜）路径仍需可复用既有副本** —— 对照，证明上一条不是靠「一律重读」通过的。
     *
     * 打开已登记的书属于这一档：副本已在盘上、来源 Uri 权限可能已失效，
     * 此时复用既有副本是正确且必要的。
     */
    @Test
    fun `default path still reuses existing internal copy`() = runTest {
        val id = "epub_reuse_existing"
        val dir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val internal = File(dir, "$id.epub")
        createValidEpub(internal, "既有书名", "EXISTING既有正文")

        // 来源刻意不可读：若复用失效，本用例会抛错 ⇒ 证明确实走了复用分支
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://books/unavailable.epub"
        every { uri.lastPathSegment } returns "unavailable.epub"
        every { resolver.openInputStream(uri) } returns null

        val parsed = EpubParser.parse(
            context = context,
            uri = uri,
            expectedBookId = id,
            fallbackLocalPath = internal.absolutePath,
        )

        assertEquals("必须复用既有副本的内容", "既有书名", parsed.title)
        assertEquals("解析的应是正式内部副本", internal.absolutePath, parsed.cachedEpubPath)
        assertTrue("不得新增 .importing", !File(dir, "$id.importing").exists())
    }

    /**
     * **损坏 EPUB 不留孤儿副本**（真机 §9-4 的直接回归）。
     *
     * 走**默认**路径（`landing` = 正式内部副本）—— 真机观察到的孤儿正是
     * `books/epub/<derived-id>.epub` 这种正式路径，而非 `.importing`。
     * 复制会成功、`ZipFile` 必然抛错；抛错后**正式副本也不得留在盘上**。
     */
    @Test
    fun `corrupt epub leaves no orphan copy on disk`() = runTest {
        val id = "epub_corrupt_orphan"
        val dir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val badSource = File(tempFolder.newFolder(), "bad.epub")
        badSource.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))
        val uri = mockUri(badSource, "content://books/bad.epub")

        var caught: Throwable? = null
        try {
            EpubParser.parse(context = context, uri = uri, expectedBookId = id)
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue("损坏 EPUB 必须抛出，caught=$caught", caught is Exception && caught !is AssertionError)

        // 核心断言：不得留下任何本次产生的文件
        assertTrue("不得留下正式副本（孤儿）", !File(dir, "$id.epub").exists())
        assertTrue("不得留下 .importing", !File(dir, "$id.importing").exists())
        val leftovers = dir.listFiles()?.filter { it.name.startsWith(id) }
        assertTrue("目录内不得残留任何该书文件，实际=$leftovers", leftovers.isNullOrEmpty())
    }

    /**
     * **合法 ZIP 但不是 EPUB**（缺 `META-INF/container.xml`）同样不得留孤儿。
     *
     * 与上一条的区别：这条的失败发生在 **`ZipFile` 构造成功之后**的解析阶段，
     * 是一个**独立的失败面**。只覆盖「损坏 ZIP」会漏掉它 —— 那条失败在更外层
     * （`ZipFile` 构造），走的是另一条清理路径。
     */
    @Test
    fun `non-epub zip leaves no orphan copy on disk`() = runTest {
        val id = "epub_non_epub_zip"
        val dir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val notAnEpub = File(tempFolder.newFolder(), "plain.zip")
        ZipOutputStream(notAnEpub.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("hello.txt"))
            zos.write("not an epub".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val uri = mockUri(notAnEpub, "content://books/plain.zip")

        var caught: Throwable? = null
        try {
            EpubParser.parse(context = context, uri = uri, expectedBookId = id)
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue("非 EPUB 的 ZIP 必须抛出，caught=$caught", caught is Exception && caught !is AssertionError)

        assertTrue("不得留下正式副本（孤儿）", !File(dir, "$id.epub").exists())
        assertTrue("不得留下 .importing", !File(dir, "$id.importing").exists())
        val leftovers = dir.listFiles()?.filter { it.name.startsWith(id) }
        assertTrue("目录内不得残留任何该书文件，实际=$leftovers", leftovers.isNullOrEmpty())
    }

    /**
     * **新鲜来源模式下失败，不得删掉已有的正式副本**。
     *
     * 与上面互补：清理只针对「本次产生的」文件。正式副本不是本次写入的，
     * 失败时**绝不能**删它 —— 那正是用户仅存的正文（替换链路里它就是待替换的旧 target）。
     */
    @Test
    fun `fresh source failure never deletes the existing internal copy`() = runTest {
        val id = "epub_keep_existing_on_failure"
        val dir = File(tempFolder.root, "books/epub").also { it.mkdirs() }
        val existing = File(dir, "$id.epub")
        createValidEpub(existing, "既有书名", "KEEPME既有正文")
        val before = existing.readBytes()

        // 来源不可读 ⇒ 新鲜来源模式下必须失败（且不得退回「复用旧副本」的兜底）
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://books/unavailable.epub"
        every { uri.lastPathSegment } returns "unavailable.epub"
        every { resolver.openInputStream(uri) } returns null

        var caught: Throwable? = null
        try {
            EpubParser.parse(
                context = context,
                uri = uri,
                expectedBookId = id,
                requireFreshSource = true,
            )
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue("不可读来源必须抛出，caught=$caught", caught is Exception && caught !is AssertionError)

        assertTrue("既有正式副本必须仍然存在", existing.exists())
        assertArrayEquals("既有正式副本必须逐字节不变", before, existing.readBytes())
        assertTrue("不得留下 .importing", !File(dir, "$id.importing").exists())
    }
}
