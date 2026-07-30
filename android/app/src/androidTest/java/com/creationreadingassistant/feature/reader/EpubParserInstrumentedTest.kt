package com.creationreadingassistant.feature.reader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubParserInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sourceFiles = mutableListOf<File>()
    private val importedIds = mutableListOf<String>()

    @After
    fun cleanUp() {
        sourceFiles.forEach(File::delete)
        importedIds.forEach { id ->
            File(context.filesDir, "books/epub/$id.epub").delete()
            File(context.filesDir, "books/epub/$id").deleteRecursively()
        }
    }

    @Test
    fun emptySpineFallsBackToReadableManifestItemsAndSkipsNav() = runBlocking {
        val id = "instrumented-empty-spine"
        importedIds += id
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

        val parsed = EpubParser.parse(context, Uri.fromFile(epub), expectedBookId = id)

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
        importedIds += id
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

        val parsed = EpubParser.parse(context, Uri.fromFile(epub), expectedBookId = id)

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
        importedIds += id
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

        val parsed = EpubParser.parse(context, Uri.fromFile(epub), expectedBookId = id)

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
        importedIds += id
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

        val parsed = EpubParser.parse(context, Uri.fromFile(epub), expectedBookId = id)

        // 封面应被过滤，只保留正文章节
        assertEquals(2, parsed.chapters.size)
        assertFalse("封面不应出现", parsed.chapters.any { it.entryPath.endsWith("cover.xhtml") })
        assertEquals("第一章", parsed.chapters[0].title)
        assertEquals("第二章", parsed.chapters[1].title)
    }

    private fun createEpub(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("neutral-epub-matrix-", ".epub", context.cacheDir)
        sourceFiles += file
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
