package com.creationreadingassistant.feature.reader

import com.creationreadingassistant.domain.model.EpubBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 锁定 EPUB 文本抽取的几处正确性缺陷。
 *
 * 这些 bug 的共同点是「坏得很安静」：搜索恒返回 0 条、实体显示成字面量、诗歌被粘成一段，
 * 都不会抛异常也不会有日志，只有对着真实书翻才发现。没有测试兜着就会改回去。
 */
class EpubParserTextTest {

    private fun zipWith(vararg entries: Pair<String, String>): String {
        val f = File.createTempFile("epub-test-", ".epub").apply { deleteOnExit() }
        ZipOutputStream(f.outputStream()).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return f.absolutePath
    }

    private fun zipWithBytes(vararg entries: Pair<String, ByteArray>): String {
        val f = File.createTempFile("epub-test-", ".epub").apply { deleteOnExit() }
        ZipOutputStream(f.outputStream()).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return f.absolutePath
    }

    private fun textOf(html: String, entry: String = "OEBPS/ch1.xhtml"): String =
        EpubParser.loadChapterText(zipWith(entry to html), entry, entry.substringBeforeLast('/', ""))

    private fun blocksOf(html: String, entry: String = "OEBPS/ch1.xhtml"): List<EpubBlock> =
        EpubParser.loadChapterBlocks(zipWith(entry to html), entry, entry.substringBeforeLast('/', ""))

    // ── 标签扫描器：</head> 退出条件 ──────────────────────────────────────

    @Test
    fun `extracts body text after head block`() {
        // 曾经的 bug：'/' 被 isLetter() 丢弃，"/head" 永远拼不出来，
        // skipDepth 自增后再不归零，于是整章正文被吞 —— 全书搜索恒 0 条。
        val t = textOf(
            "<html><head><title>T</title></head><body><p>AAA正文</p><p>BBB正文</p></body></html>",
        )
        assertTrue("正文应被抽取，实际=<$t>", t.contains("AAA正文"))
        assertTrue("正文应被抽取，实际=<$t>", t.contains("BBB正文"))
    }

    @Test
    fun `head with attributes does not swallow body`() {
        // 另一半：<head profile="x"> 时标签名会拼成 headprofilex 匹配不上，
        // 反倒是随后的 </head> 命中开标签分支使 skipDepth++，正文照样丢，还泄漏 <title>。
        val t = textOf(
            "<html><head profile=\"x\"><title>标题</title></head><body><p>正文内容</p></body></html>",
        )
        assertTrue("正文应被抽取，实际=<$t>", t.contains("正文内容"))
        assertTrue("<title> 不应泄漏为正文，实际=<$t>", !t.contains("标题"))
    }

    @Test
    fun `script and style blocks are skipped`() {
        val t = textOf(
            "<html><head></head><body><script>var a=1;</script><style>p{color:red}</style><p>可见</p></body></html>",
        )
        assertTrue(t.contains("可见"))
        assertTrue("脚本内容不应出现，实际=<$t>", !t.contains("var a"))
        assertTrue("样式内容不应出现，实际=<$t>", !t.contains("color"))
    }

    // ── 实体解码：两条路径必须一致 ────────────────────────────────────────

    @Test
    fun `render path decodes extended and hex entities`() {
        // 渲染路径此前只认 8 个命名实体 + 十进制，于是 calibre 导出的中文书里
        // 引号和破折号会原样显示成 &ldquo; &mdash;。
        val blocks = blocksOf(
            "<html><head></head><body><p>&ldquo;引用&rdquo;&mdash;&hellip;&#x201C;X&#x201D;</p></body></html>",
        )
        val text = blocks.filterIsInstance<EpubBlock.Text>().joinToString(" ") { it.text }
        assertTrue("不应残留任何实体字面量，实际=<$text>", !text.contains("&"))
        assertTrue(text.contains("“") && text.contains("”"))
        assertTrue(text.contains("—"))
        assertTrue(text.contains("…"))
    }

    @Test
    fun `numeric char reference beyond BMP survives`() {
        // Int.toChar() 只取低 16 位，会把 U+1F600 截成 U+F600 私用区方块。
        val blocks = blocksOf("<html><head></head><body><p>笑&#128512;脸</p></body></html>")
        val text = blocks.filterIsInstance<EpubBlock.Text>().joinToString(" ") { it.text }
        assertTrue("emoji 应完整保留，实际=<$text>", text.contains("😀"))
    }

    // ── <br /> 换行 ────────────────────────────────────────────────────

    @Test
    fun `br with space before slash breaks the line`() {
        // <br /> 是 XHTML 最常见写法，此前用等值比较只认 <br> 和 <br/>，
        // 于是诗歌、书信被粘连成一整段。
        val blocks = blocksOf("<html><head></head><body><p>第一行<br />第二行</p></body></html>")
        val texts = blocks.filterIsInstance<EpubBlock.Text>().map { it.text }
        assertEquals("应切成两块，实际=$texts", 2, texts.size)
        assertEquals("第一行", texts[0])
        assertEquals("第二行", texts[1])
    }

    // ── href percent-decode ──────────────────────────────────────────────

    @Test
    fun `percent encoded entry path resolves`() {
        // href 含空格或非 ASCII 时会被 percent-encode，而 ZipEntry 名是原始字符串，
        // 不解码就 getEntry()==null，整章空白。
        val entry = "OEBPS/第 一 章.xhtml"
        val path = zipWith(entry to "<html><head></head><body><p>中文正文</p></body></html>")
        val encoded = "OEBPS/%E7%AC%AC%20%E4%B8%80%20%E7%AB%A0.xhtml"
        val t = EpubParser.loadChapterText(path, encoded, "OEBPS")
        assertTrue("解码后应能命中条目，实际=<$t>", t.contains("中文正文"))
    }

    @Test
    fun `encoded relative image path is extracted without escaping cache`() {
        val html = """
            <html><head></head><body>
            <p>图片之前</p>
            <img src="../图片/%E5%B0%81%E9%9D%A2%20%E5%9B%BE.png"/>
            <p>图片之后</p>
            </body></html>
        """.trimIndent()
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
        val path = zipWithBytes(
            "OEBPS/text/ch1.xhtml" to html.toByteArray(),
            "OEBPS/图片/封面 图.png" to imageBytes,
        )

        val blocks = EpubParser.loadChapterBlocks(path, "OEBPS/text/ch1.xhtml", "OEBPS/text")
        val image = blocks.filterIsInstance<EpubBlock.Image>().single()
        val extracted = File(image.filePath)

        assertTrue(extracted.isFile)
        assertTrue(imageBytes.contentEquals(extracted.readBytes()))
        assertTrue(blocks.filterIsInstance<EpubBlock.Text>().any { it.text == "图片之前" })
        assertTrue(blocks.filterIsInstance<EpubBlock.Text>().any { it.text == "图片之后" })
    }

    @Test
    fun `svg image href is extracted as an image block`() {
        val html = """
            <html><head></head><body>
            <svg><image xlink:href="../images/cover.jpg"/></svg>
            </body></html>
        """.trimIndent()
        val path = zipWithBytes(
            "OEBPS/text/cover.xhtml" to html.toByteArray(),
            "OEBPS/images/cover.jpg" to byteArrayOf(9, 8, 7),
        )

        val blocks = EpubParser.loadChapterBlocks(path, "OEBPS/text/cover.xhtml", "OEBPS/text")

        assertEquals(1, blocks.filterIsInstance<EpubBlock.Image>().size)
    }

    @Test
    fun `missing image does not hide surrounding text`() {
        val blocks = blocksOf(
            "<html><head></head><body><p>前文</p><img src=\"missing.png\"/><p>后文</p></body></html>",
        )
        val texts = blocks.filterIsInstance<EpubBlock.Text>().map { it.text }

        assertEquals(listOf("前文", "后文"), texts)
        assertFalse(blocks.any { it is EpubBlock.Image })
    }

    // ── 搜索与渲染必须同源（P0 核心不变式）────────────────────────────────

    @Test
    fun `chapter text equals blocks joined by newline`() {
        // ReaderScreen.computeBlockGlobalOffsets 按 block.text.length + 1 累加偏移，
        // 也就是隐含假设「全章文本 == 各块以单个 \n 拼接」。此前搜索走流式状态机、
        // 渲染走正则 extractBlocks，两套实现产出长度不同，这个假设是破的 ——
        // 搜索命中的偏移和渲染用的偏移对不上。这条断言就是那个契约本身。
        val html = """
            <html><head><title>T</title></head><body>
            <h2>第一章 起</h2>
            <p>第一段正文，包含&ldquo;引号&rdquo;与破折号&mdash;&mdash;还有省略号&hellip;</p>
            <p>第二段<br />换行后的内容</p>
            <blockquote>引用段落</blockquote>
            </body></html>
        """.trimIndent()
        val path = zipWith("OEBPS/ch1.xhtml" to html)
        val blocks = EpubParser.loadChapterBlocks(path, "OEBPS/ch1.xhtml", "OEBPS")
        val text = EpubParser.loadChapterText(path, "OEBPS/ch1.xhtml", "OEBPS")
        val joined = blocks.filterIsInstance<EpubBlock.Text>().joinToString("\n") { it.text }
        assertEquals("搜索文本必须与渲染块同源", joined, text)
        assertTrue("应至少切出 4 块，实际=${blocks.size}", blocks.size >= 4)
    }

    // ── 大章不再降级成假段落 ──────────────────────────────────────────────

    @Test
    fun `large chapter keeps paragraph boundaries`() {
        // 此前单章 > 2MB 会降级成 loadChapterText().chunked(2000)：段落边界全丢、
        // isHeading 全 false，排版层会每 2000 字加一个假首行缩进。
        // 现在大章小章走同一条流式路径，没有降级分支。
        val para = "这是一个用来把章节撑大的段落。".repeat(40)   // ≈ 600 字
        val body = buildString {
            append("<html><head></head><body><h1>大章标题</h1>")
            repeat(4000) { append("<p>").append(para).append("</p>") }  // ≈ 2.4M 字符 > 2MB
            append("</body></html>")
        }
        val path = zipWith("OEBPS/big.xhtml" to body)
        val blocks = EpubParser.loadChapterBlocks(path, "OEBPS/big.xhtml", "OEBPS")
        val texts = blocks.filterIsInstance<EpubBlock.Text>()

        assertTrue("标题角色必须保留", texts.first().isHeading)
        assertEquals("大章标题", texts.first().text)
        // 真段落长度应等于 para，而不是被切成 2000 字一块
        val bodyBlocks = texts.drop(1)
        assertTrue("应切出大量真段落，实际=${bodyBlocks.size}", bodyBlocks.size > 100)
        assertTrue(
            "段落内容应完整而非按 2000 字机械截断，实际首段长度=${bodyBlocks[0].text.length}",
            bodyBlocks[0].text == para,
        )
    }

    // ── HTML 注释不泄漏 ──────────────────────────────────────────────────

    @Test
    fun `html comments are not leaked as body text`() {
        // 注释里可以合法出现 '>'，必须等到 "-->" 才算闭合。
        val blocks = blocksOf(
            "<html><head></head><body><!--[if IE]><p>请升级浏览器</p><![endif]--><p>正文</p></body></html>",
        )
        val text = blocks.filterIsInstance<EpubBlock.Text>().joinToString(" ") { it.text }
        assertTrue("注释内容不应出现，实际=<$text>", !text.contains("请升级"))
        assertTrue(text.contains("正文"))
    }

    @Test
    fun `attribute containing angle bracket does not leak`() {
        val blocks = blocksOf("<html><head></head><body><p>前<img alt=\"a > b\" src=\"x.png\"/>后</p></body></html>")
        val text = blocks.filterIsInstance<EpubBlock.Text>().joinToString(" ") { it.text }
        assertTrue("标签残片不应渲染为正文，实际=<$text>", !text.contains("src="))
    }

    @Test
    fun `malformed unclosed paragraph keeps readable text`() {
        val blocks = blocksOf(
            "<html><head></head><body><p>第一段<p>第二段<div>第三段",
        )
        val text = blocks.filterIsInstance<EpubBlock.Text>().joinToString("|") { it.text }

        assertTrue(text.contains("第一段"))
        assertTrue(text.contains("第二段"))
        assertTrue(text.contains("第三段"))
    }

    @Test
    fun `oversized chapter fails with the same controlled error for render and search`() {
        val html = "<html><body><p>" +
            "a".repeat(8 * 1024 * 1024 + 1) +
            "</p></body></html>"
        val path = zipWith("OEBPS/huge.xhtml" to html)

        val renderError = runCatching {
            EpubParser.loadChapterBlocks(path, "OEBPS/huge.xhtml", "OEBPS")
        }.exceptionOrNull()
        val searchError = runCatching {
            EpubParser.loadChapterText(path, "OEBPS/huge.xhtml", "OEBPS")
        }.exceptionOrNull()

        assertTrue(renderError is IllegalStateException)
        assertTrue(searchError is IllegalStateException)
        assertEquals(renderError?.message, searchError?.message)
    }

    // ── headings ────────────────────────────────────────────────────────

    @Test
    fun `heading blocks are flagged`() {
        val blocks = blocksOf("<html><head></head><body><h2>第一章</h2><p>正文</p></body></html>")
        val texts = blocks.filterIsInstance<EpubBlock.Text>()
        assertTrue("应识别出标题块，实际=$texts", texts.any { it.isHeading && it.text.contains("第一章") })
        assertTrue(texts.any { !it.isHeading && it.text.contains("正文") })
    }

    // ── TOC 解析辅助测试 ──────────────────────────────────────────────────

    @Test
    fun `nav document regex matches epub3 toc nav`() {
        val navRegex = Regex(
            """<nav[^>]*(?:epub:type\s*=\s*["']toc["']|role\s*=\s*["']doc-toc["'])[^>]*>(.*?)</nav>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val html = """
            <html><body>
            <nav epub:type="toc">
              <ol>
                <li><a href="ch1.xhtml">第一章</a></li>
                <li><a href="ch2.xhtml">第二章</a></li>
              </ol>
            </nav>
            </body></html>
        """.trimIndent()
        val match = navRegex.find(html)
        assertTrue("应匹配 epub:type='toc' 的 nav", match != null)
        val content = match!!.groupValues[1]
        assertTrue("应包含第一章链接", content.contains("ch1.xhtml"))
        assertTrue("应包含第二章链接", content.contains("ch2.xhtml"))
    }

    @Test
    fun `nav document regex matches doc-toc role`() {
        val navRegex = Regex(
            """<nav[^>]*(?:epub:type\s*=\s*["']toc["']|role\s*=\s*["']doc-toc["'])[^>]*>(.*?)</nav>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val html = """<html><body><nav role="doc-toc"><ol><li><a href="ch1.xhtml">第一章</a></li></ol></nav></body></html>"""
        val match = navRegex.find(html)
        assertTrue("应匹配 role='doc-toc' 的 nav", match != null)
    }

    @Test
    fun `nav document regex does not match non-toc nav`() {
        val navRegex = Regex(
            """<nav[^>]*(?:epub:type\s*=\s*["']toc["']|role\s*=\s*["']doc-toc["'])[^>]*>(.*?)</nav>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val html = """<html><body><nav epub:type="landmarks"><a href="ch1.xhtml">正文</a></nav></body></html>"""
        val match = navRegex.find(html)
        assertTrue("不应匹配 landmarks nav", match == null)
    }

    @Test
    fun `link extraction regex parses anchor tags`() {
        val linkRegex = Regex(
            """<a\s[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val navContent = """
            <ol>
              <li><a href="text/ch1.xhtml">第一章 开始</a></li>
              <li><a href="text/ch2.xhtml#sec1">第二章 <span>第二节</span></a></li>
            </ol>
        """.trimIndent()
        val links = linkRegex.findAll(navContent).toList()
        assertEquals(2, links.size)
        assertEquals("text/ch1.xhtml", links[0].groupValues[1])
        assertEquals("第一章 开始", links[0].groupValues[2].trim())
        assertEquals("text/ch2.xhtml#sec1", links[1].groupValues[1])
    }

    @Test
    fun `non-content path patterns detect cover and copyright`() {
        val pattern = Regex("cover|titlepage|title-page|frontmatter|copyright|colophon|dedication|frontispiece", RegexOption.IGNORE_CASE)
        assertTrue(pattern.containsMatchIn("OEBPS/cover.xhtml"))
        assertTrue(pattern.containsMatchIn("text/titlepage.html"))
        assertTrue(pattern.containsMatchIn("OEBPS/copyright.xhtml"))
        assertTrue(pattern.containsMatchIn("content/colophon.xhtml"))
        assertFalse(pattern.containsMatchIn("text/chapter1.xhtml"))
        assertFalse(pattern.containsMatchIn("OEBPS/第一章.xhtml"))
    }
}
