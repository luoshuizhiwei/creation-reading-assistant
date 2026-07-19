/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubDocumentLoaderTest {
    @Test
    fun initialLoadKeepsSpineAndImagesLazy() {
        EpubDocumentLoader.load(ByteArrayInputStream(minimalEpub())).use { document ->
            assertEquals(2, document.chapters.size)
            assertEquals("第一章", document.chapters[0].title)
            assertEquals("第二章", document.chapters[1].title)
            assertFalse(document.isChapterCached(0))
            assertFalse(document.isChapterCached(1))
            assertFalse(document.isResourceInitialized("OEBPS/x.png"))

            val first = document.chapterContent(0)
            assertTrue(first.text.contains("第一段正文"))
            assertTrue(document.isChapterCached(0))
            assertFalse(document.isChapterCached(1))

            val second = document.chapterContent(1)
            assertTrue(second.text.contains("[图片：插图]"))
            assertTrue(second.containsImages)
            assertFalse(document.isResourceInitialized("OEBPS/x.png"))
            assertEquals(0, document.chapterIndexForHref(document.chapters[0].href + "#start"))
            assertEquals(0, document.chapterIndexForHref(document.chapters[0].href.substringAfterLast('/')))
        }
    }

    @Test
    fun chapterCacheEvictsOlderContentInsteadOfRetainingWholeBook() {
        EpubDocumentLoader.load(ByteArrayInputStream(epubWithChapterCount(7))).use { document ->
            document.chapters.indices.forEach { index ->
                assertTrue(document.chapterContent(index).text.contains("第 ${index + 1} 章正文"))
            }
            assertTrue(document.cachedChapterCount() <= 4)
            assertFalse(document.isChapterCached(0))
            assertTrue(document.isChapterCached(document.chapters.lastIndex))
        }
    }

    private fun minimalEpub(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(path: String, text: String) = putBytes(zip, path, text.toByteArray(Charsets.UTF_8))
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            put(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?><package version="2.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>测试书</dc:title><dc:creator>测试作者</dc:creator><dc:identifier id="id">test</dc:identifier></metadata><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/><item id="img" href="x.png" media-type="image/png"/></manifest><spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            )
            put(
                "OEBPS/toc.ncx",
                """<?xml version="1.0" encoding="UTF-8"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head/><docTitle><text>测试书</text></docTitle><navMap><navPoint id="n1" playOrder="1"><navLabel><text>第一章</text></navLabel><content src="c1.xhtml"/></navPoint><navPoint id="n2" playOrder="2"><navLabel><text>第二章</text></navLabel><content src="c2.xhtml"/></navPoint></navMap></ncx>""",
            )
            put("OEBPS/c1.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>第一章</h1><p>第一段正文。</p></body></html>")
            put("OEBPS/c2.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>第二章</h1><p>第二段正文。</p><img src=\"x.png\" alt=\"插图\"/></body></html>")
            putBytes(zip, "OEBPS/x.png", byteArrayOf(1, 2, 3, 4))
        }
        return output.toByteArray()
    }

    private fun epubWithChapterCount(count: Int): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(path: String, text: String) = putBytes(zip, path, text.toByteArray(Charsets.UTF_8))
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            val manifest = (1..count).joinToString("") { index ->
                "<item id=\"c$index\" href=\"c$index.xhtml\" media-type=\"application/xhtml+xml\"/>"
            }
            val spine = (1..count).joinToString("") { index -> "<itemref idref=\"c$index\"/>" }
            val toc = (1..count).joinToString("") { index ->
                "<navPoint id=\"n$index\" playOrder=\"$index\"><navLabel><text>第 $index 章</text></navLabel><content src=\"c$index.xhtml\"/></navPoint>"
            }
            put(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?><package version="2.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>缓存测试书</dc:title><dc:identifier id="id">cache-test</dc:identifier></metadata><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>$manifest</manifest><spine toc="ncx">$spine</spine></package>""",
            )
            put(
                "OEBPS/toc.ncx",
                """<?xml version="1.0" encoding="UTF-8"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head/><docTitle><text>缓存测试书</text></docTitle><navMap>$toc</navMap></ncx>""",
            )
            (1..count).forEach { index ->
                put(
                    "OEBPS/c$index.xhtml",
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>第 $index 章</h1><p>第 $index 章正文。</p></body></html>",
                )
            }
        }
        return output.toByteArray()
    }

    private fun putBytes(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }
}
