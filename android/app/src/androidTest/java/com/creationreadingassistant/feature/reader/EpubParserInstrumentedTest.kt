package com.creationreadingassistant.feature.reader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S04 隔离修复（对应设计文档 §3.7）：
 *
 * 此前本测试把真实 [Context] 直接交给 [EpubParser.parse]，且用例 id 是固定字符串，
 * 于是内部副本落在**正式** `filesDir/books/epub/`，`@After` 再按固定 bookId 去
 * 正式目录删文件 —— 与真机上用户已导入的书共用一个目录，属同一类缺陷。
 *
 * 现在的隔离方式：
 * - 所有写入（合成 EPUB 源文件、`EpubParser` 内部副本、img 缓存）都经
 *   [IsolatedTestContext] 重定向到本次测试唯一的 [testRoot]（UUID 命名）。
 * - [IsolatedTestContext] 不覆盖 `contentResolver`，仍委托真实实现只读源
 *   （`Uri.fromFile`），读源能力不受影响。
 * - [cleanUp] 只删经 [IsolatedTestContext.assertInsideRoot] 确认的该根目录，
 *   **绝不**按固定 bookId 在正式 `filesDir` 删除任何东西。
 * - [assertRootDisjointFromRealStorage] 是运行时防线：确认测试根与正式
 *   `filesDir`（尤其 `books/epub`）互不包含、不相交，防线失守立即失败而非照常删除。
 */
class EpubParserInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** 本次测试专属唯一根目录；所有合成源文件与内部副本都落在它之下。 */
    private val testRoot = File(
        context.cacheDir,
        "epub-parser-isolated-test/" + UUID.randomUUID(),
    )

    private val isolated = IsolatedTestContext(context, testRoot)

    @Before
    fun setUp() {
        // 防线先于任何目录创建：若 testRoot 构造将来被改到正式 filesDir 下，
        // 必须在落盘前就失败，而不是先污染后报警（S04 独立审查 P2-1）。
        assertRootDisjointFromRealStorage()
        testRoot.mkdirs()
    }

    @After
    fun cleanUp() {
        // 只清理本测试自己创建的唯一根；先经路径防线确认，失败即抛出而非照常删除。
        isolated.assertInsideRoot(testRoot)
        assertRootDisjointFromRealStorage()
        // deleteRecursively 返回 false 且目录确实存在时，说明有残留：不能静默吞掉，
        // 但此时不应再抛异常覆盖可能存在的原始测试失败 —— 用断言显式报告
        // （S04 独立审查 P2-2）。testRoot 不存在（如 @Before 防线已失败）视为已清理，
        // 不报告。残留只可能发生在自身 cacheDir 子树内（防线已确认 testRoot
        // 不在正式目录下）。
        if (testRoot.exists() && !testRoot.deleteRecursively()) {
            throw AssertionError(
                "测试根 $testRoot 删除失败，存在残留文件。" +
                    "残留仅位于本测试自身 cacheDir 子树内，但请检查是否有文件被占用。",
            )
        }
    }

    /**
     * 运行时防线：测试根与正式 `filesDir`（尤其 `books/epub`）必须互不相交。
     * 任何一侧包含另一侧都视为防线失守，立即失败。
     */
    private fun assertRootDisjointFromRealStorage() {
        val root = testRoot.canonicalFile
        val realFiles = context.filesDir.canonicalFile
        val realEpubRoot = File(realFiles, "books/epub").canonicalFile
        check(!(root == realFiles || root.path.startsWith(realFiles.path + File.separator))) {
            "测试根 $root 落在正式 filesDir $realFiles 内，防线失守，拒绝继续。"
        }
        check(!(root == realEpubRoot || realEpubRoot.path.startsWith(root.path + File.separator))) {
            "正式 books/epub（$realEpubRoot）落入测试根 $root 内，防线失守，拒绝继续。"
        }
    }

    @Test
    fun emptySpineFallsBackToReadableManifestItemsAndSkipsNav() = runBlocking {
        val id = "instrumented-empty-spine"
        val epub = createEpub(
            "META-INF/container.xml" to containerXml(),
            "OPS/package.opf" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>测试 EPUB</dc:title>
                    <dc:creator>测试作者</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="text/第一章.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/第二章.xhtml" media-type="application/xhtml+xml"/>
                    <item id="css" href="style.css" media-type="text/css"/>
                  </manifest>
                  <spine/>
                </package>
            """.trimIndent(),
            "OPS/nav.xhtml" to "<html><body><nav>目录</nav></body></html>",
            "OPS/text/第一章.xhtml" to "<html><body><h1>第一章</h1><p>正文一</p></body></html>",
            "OPS/text/第二章.xhtml" to "<html><body><h1>第二章</h1><p>正文二</p></body></html>",
            "OPS/style.css" to "body { color: black; }",
        )

        val parsed = EpubParser.parse(isolated, Uri.fromFile(epub), expectedBookId = id)

        assertEquals("测试 EPUB", parsed.title)
        assertEquals(listOf("第一章", "第二章"), parsed.chapters.map { it.title })
        assertFalse(parsed.chapters.any { it.entryPath.endsWith("nav.xhtml") })
        assertTrue(EpubParser.loadChapterText(
            parsed.cachedEpubPath,
            parsed.chapters.first().entryPath,
            parsed.chapters.first().chapterDir,
        ).contains("正文一"))
    }

    /** EPUB 3 Navigation Document 作为目录源：章节标题应来自 nav 中的 <a> 文本。 */
    @Test
    fun epub3Nav文档作为目录源() = runBlocking {
        val id = "instrumented-epub3-nav-toc"
        val epub = createEpub(
            "META-INF/container.xml" to containerXml(),
            "OPS/package.opf" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>EPUB3 目录测试</dc:title>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="cover"/>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent(),
            "OPS/nav.xhtml" to """
                <html xmlns:epub="http://www.idpf.org/2007/ops"><body>
                <nav epub:type="toc">
                  <ol>
                    <li><a href="text/ch1.xhtml">起始之章</a></li>
                    <li><a href="text/ch2.xhtml">终结之章</a></li>
                  </ol>
                </nav>
                </body></html>
            """.trimIndent(),
            "OPS/cover.xhtml" to "<html><body><h1>Cover</h1></body></html>",
            "OPS/text/ch1.xhtml" to "<html><body><h1>实际标题一</h1><p>正文一</p></body></html>",
            "OPS/text/ch2.xhtml" to "<html><body><h1>实际标题二</h1><p>正文二</p></body></html>",
        )

        val parsed = EpubParser.parse(isolated, Uri.fromFile(epub), expectedBookId = id)

        // 应使用 nav 文档中的标题，而非 HTML 中的 h1
        assertEquals(2, parsed.chapters.size)
        assertEquals("起始之章", parsed.chapters[0].title)
        assertEquals("终结之章", parsed.chapters[1].title)
        // 封面不应出现在目录中
        assertFalse("封面不应出现在目录中", parsed.chapters.any { it.entryPath.endsWith("cover.xhtml") })
    }

    /** EPUB 2 NCX 作为目录源：章节标题应来自 navLabel。 */
    @Test
    fun epub2Ncx作为目录源() = runBlocking {
        val id = "instrumented-epub2-ncx-toc"
        val epub = createEpub(
            "META-INF/container.xml" to containerXml(),
            "OPS/package.opf" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>EPUB2 NCX 测试</dc:title>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="cover"/>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent(),
            "OPS/toc.ncx" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <navMap>
                    <navPoint id="np1" playOrder="1">
                      <navLabel><text>第一章 起源</text></navLabel>
                      <content src="text/ch1.xhtml"/>
                    </navPoint>
                    <navPoint id="np2" playOrder="2">
                      <navLabel><text>第二章 发展</text></navLabel>
                      <content src="text/ch2.xhtml"/>
                    </navPoint>
                  </navMap>
                </ncx>
            """.trimIndent(),
            "OPS/cover.xhtml" to "<html><body><h1>Cover</h1></body></html>",
            "OPS/text/ch1.xhtml" to "<html><body><h1>HTML标题一</h1><p>正文一</p></body></html>",
            "OPS/text/ch2.xhtml" to "<html><body><h1>HTML标题二</h1><p>正文二</p></body></html>",
        )

        val parsed = EpubParser.parse(isolated, Uri.fromFile(epub), expectedBookId = id)

        // 应使用 NCX 中的标题
        assertEquals(2, parsed.chapters.size)
        assertEquals("第一章 起源", parsed.chapters[0].title)
        assertEquals("第二章 发展", parsed.chapters[1].title)
        // 封面不应出现在目录中
        assertFalse(parsed.chapters.any { it.entryPath.endsWith("cover.xhtml") })
    }

    /** 无 TOC 时回退到 spine，应过滤封面等非正文条目。 */
    @Test
    fun 无目录时回退到spine并过滤封面() = runBlocking {
        val id = "instrumented-no-toc-filter"
        val epub = createEpub(
            "META-INF/container.xml" to containerXml(),
            "OPS/package.opf" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>无目录测试</dc:title>
                  </metadata>
                  <manifest>
                    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml" properties="cover"/>
                    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="cover"/>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent(),
            "OPS/cover.xhtml" to """<html><body><img src="cover.jpg"/></body></html>""",
            "OPS/text/ch1.xhtml" to "<html><body><h1>第一章</h1><p>正文一</p></body></html>",
            "OPS/text/ch2.xhtml" to "<html><body><h1>第二章</h1><p>正文二</p></body></html>",
        )

        val parsed = EpubParser.parse(isolated, Uri.fromFile(epub), expectedBookId = id)

        // 封面应被过滤，只保留正文章节
        assertEquals(2, parsed.chapters.size)
        assertFalse("封面不应出现", parsed.chapters.any { it.entryPath.endsWith("cover.xhtml") })
        assertEquals("第一章", parsed.chapters[0].title)
        assertEquals("第二章", parsed.chapters[1].title)
    }

    private fun createEpub(vararg entries: Pair<String, String>): File {
        // 合成源文件也落在测试专属根内，绝不落正式 cacheDir。
        val dir = File(testRoot, "src").also { it.mkdirs() }
        val file = File.createTempFile("neutral-epub-matrix-", ".epub", dir)
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        return file
    }

    private fun containerXml() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()
}
