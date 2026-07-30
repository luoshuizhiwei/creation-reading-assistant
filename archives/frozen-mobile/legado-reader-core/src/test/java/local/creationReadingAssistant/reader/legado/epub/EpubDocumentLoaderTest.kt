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
            assertTrue(second.containsImages)
            assertTrue(second.html.contains("<img"))
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

    @Test
    fun loadsReadableSpineWithoutNavigationDocument() {
        EpubDocumentLoader.load(ByteArrayInputStream(epubWithoutToc())).use { document ->
            assertEquals(2, document.chapters.size)
            assertEquals("第 1 节", document.chapters[0].title)
            assertEquals("第 2 节", document.chapters[1].title)
            assertTrue(document.chapterText(0).contains("没有目录也能阅读"))
        }
    }

    @Test
    fun preservesNestedTocLevelsAndChineseInternalPaths() {
        EpubDocumentLoader.load(ByteArrayInputStream(epubWithNestedChineseToc())).use { document ->
            assertEquals(listOf("第一卷", "第一章 中文路径"), document.chapters.map { it.title })
            assertEquals(listOf(1, 2), document.chapters.map { it.level })
            assertTrue(document.chapterText(1).contains("中文内部路径正文"))
            assertEquals(1, document.chapterIndexForHref("正文/第一章.xhtml#开头"))
            assertEquals(1, document.chapterIndexForHref("%E7%AC%AC%E4%B8%80%E7%AB%A0.xhtml"))
        }
    }

    @Test
    fun rejectsMalformedArchivesAndBooksWithoutReadableSpine() {
        assertLoadFails("损坏的 EPUB 应拒绝") {
            EpubDocumentLoader.load(ByteArrayInputStream("not-a-zip".toByteArray()))
        }
        assertLoadFails("没有正文 spine 的 EPUB 应拒绝") {
            EpubDocumentLoader.load(ByteArrayInputStream(epubWithoutReadableSpine()))
        }
    }

    @Test
    fun rejectsArchivesWithMissingContainerOrPackageDocument() {
        assertLoadFails("缺少 container.xml 的 EPUB 应拒绝") {
            EpubDocumentLoader.load(
                ByteArrayInputStream(
                    zipArchive(
                        "mimetype" to "application/epub+zip",
                        "OPS/chapter.xhtml" to "<html><body>孤立正文</body></html>",
                    ),
                ),
            )
        }
        assertLoadFails("container 指向不存在 OPF 的 EPUB 应拒绝") {
            EpubDocumentLoader.load(
                ByteArrayInputStream(
                    zipArchive(
                        "mimetype" to "application/epub+zip",
                        "META-INF/container.xml" to
                            """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/missing.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                    ),
                ),
            )
        }
    }

    @Test
    fun rejectsSpineReferencesMissingFromManifest() {
        assertLoadFails("spine 引用不存在资源时不能进入空白阅读页") {
            EpubDocumentLoader.load(
                ByteArrayInputStream(
                    zipArchive(
                        "mimetype" to "application/epub+zip",
                        "META-INF/container.xml" to
                            """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                        "OPS/package.opf" to
                            """<?xml version="1.0"?><package version="3.0" xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>损坏目录</dc:title></metadata><manifest></manifest><spine><itemref idref="missing"/></spine></package>""",
                    ),
                ),
            )
        }
    }

    @Test
    fun recoversReadableTextFromTruncatedXhtml() {
        val archive = zipArchive(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "OPS/package.opf" to
                """<?xml version="1.0"?><package version="3.0" xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>截断章节</dc:title></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""",
            "OPS/chapter.xhtml" to "<html><body><h1>第一章</h1><p>即使标签被截断，正文也应保留",
        )

        EpubDocumentLoader.load(ByteArrayInputStream(archive)).use { document ->
            assertTrue(document.chapterText(0).contains("标签被截断"))
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

    private fun epubWithoutToc(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(path: String, text: String) = putBytes(zip, path, text.toByteArray(Charsets.UTF_8))
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            put(
                "OPS/package.opf",
                """<?xml version="1.0" encoding="UTF-8"?><package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>无目录测试</dc:title><dc:identifier id="id">no-toc</dc:identifier></metadata><manifest><item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="two.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            )
            put("OPS/one.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>没有目录也能阅读。</p></body></html>")
            put("OPS/two.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>第二节正文。</p></body></html>")
        }
        return output.toByteArray()
    }

    private fun epubWithNestedChineseToc(): ByteArray {
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
                """<?xml version="1.0" encoding="UTF-8"?><package version="2.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>中文路径测试</dc:title><dc:identifier id="id">zh-path</dc:identifier></metadata><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/><item id="volume" href="%E6%AD%A3%E6%96%87/%E5%8D%B7.xhtml" media-type="application/xhtml+xml"/><item id="chapter" href="%E6%AD%A3%E6%96%87/%E7%AC%AC%E4%B8%80%E7%AB%A0.xhtml" media-type="application/xhtml+xml"/></manifest><spine toc="ncx"><itemref idref="volume"/><itemref idref="chapter"/></spine></package>""",
            )
            put(
                "OEBPS/toc.ncx",
                """<?xml version="1.0" encoding="UTF-8"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head/><docTitle><text>中文路径测试</text></docTitle><navMap><navPoint id="volume" playOrder="1"><navLabel><text>第一卷</text></navLabel><content src="正文/卷.xhtml"/><navPoint id="chapter" playOrder="2"><navLabel><text>第一章 中文路径</text></navLabel><content src="正文/%E7%AC%AC%E4%B8%80%E7%AB%A0.xhtml#开头"/></navPoint></navPoint></navMap></ncx>""",
            )
            put("OEBPS/正文/卷.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>第一卷</h1><p>卷首正文。</p></body></html>")
            put("OEBPS/正文/第一章.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>第一章</h1><p id=\"开头\">中文内部路径正文。</p></body></html>")
        }
        return output.toByteArray()
    }

    private fun epubWithoutReadableSpine(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(path: String, text: String) = putBytes(zip, path, text.toByteArray(Charsets.UTF_8))
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            put(
                "OPS/package.opf",
                """<?xml version="1.0" encoding="UTF-8"?><package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>无正文</dc:title><dc:identifier id="id">no-body</dc:identifier></metadata><manifest><item id="image" href="cover.png" media-type="image/png"/></manifest><spine><itemref idref="image"/></spine></package>""",
            )
            putBytes(zip, "OPS/cover.png", byteArrayOf(1, 2, 3))
        }
        return output.toByteArray()
    }

    private fun assertLoadFails(message: String, block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(message, failed)
    }

    private fun zipArchive(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, text) -> putBytes(zip, path, text.toByteArray(Charsets.UTF_8)) }
        }
        return output.toByteArray()
    }

    private fun putBytes(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }
}
