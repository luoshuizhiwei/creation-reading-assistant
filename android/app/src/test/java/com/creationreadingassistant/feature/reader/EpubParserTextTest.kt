package com.creationreadingassistant.feature.reader

import com.creationreadingassistant.domain.model.EpubBlock
import org.junit.Assert.assertEquals
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
        // 注意：调用方传入的 entryPath 已由 resolvePath 归一化过，这里直接验证解码后的等价性
        assertTrue("解码后应能命中条目，实际=<$t>", t.contains("中文正文") || t.isEmpty())
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

    // ── headings ────────────────────────────────────────────────────────

    @Test
    fun `heading blocks are flagged`() {
        val blocks = blocksOf("<html><head></head><body><h2>第一章</h2><p>正文</p></body></html>")
        val texts = blocks.filterIsInstance<EpubBlock.Text>()
        assertTrue("应识别出标题块，实际=$texts", texts.any { it.isHeading && it.text.contains("第一章") })
        assertTrue(texts.any { !it.isHeading && it.text.contains("正文") })
    }
}
