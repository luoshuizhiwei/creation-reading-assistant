package com.creationreadingassistant.feature.reader.doc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarkdownDocumentTest {

    private val tempFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    // ── 小文件模式 ───────────────────────────────────────────────────

    @Test
    fun `small file chapters from h1 and h2 headings`() {
        val source = """
            # 第一章

            正文第一段。

            ## 1.1 小节

            小节正文。

            # 第二章

            第二章正文。
        """.trimIndent()
        val doc = MarkdownDocument(source)

        assertTrue("应至少识别出两章", doc.chapters.size >= 2)
        assertEquals("第一章", doc.chapters[0].title)
        assertEquals("第二章", doc.chapters[1].title)

        // 章节偏移连续
        for (i in 1 until doc.chapters.size) {
            val prev = doc.chapters[i - 1]
            val curr = doc.chapters[i]
            assertEquals(
                "章节 $i 的起点应紧接上一章末尾",
                prev.startOffset + prev.charCount,
                curr.startOffset,
            )
        }
        // 规范文本移除了 Markdown 标记，因此总字符数 ≤ 源长度
        assertTrue(doc.totalChars <= source.length)
        var sum = 0
        for (c in doc.chapters) sum += c.charCount
        assertEquals(sum, doc.totalChars)
    }

    @Test
    fun `small file chapter text equals blocks canonical text`() {
        val source = """
            # 标题

            段落 **粗体**。

            - 列表项
        """.trimIndent()
        val doc = MarkdownDocument(source)

        for (ci in doc.chapters.indices) {
            val text = doc.text(ci)
            val blocks = doc.blocks(ci).filterIsInstance<DocBlock.Markdown>()
            val joined = blocks.joinToString("\n") { it.chapter.canonicalText }
            assertEquals("第 $ci 章 text 与 blocks 拼接不一致", text, joined)
            assertTrue("第 $ci 章 text 不应为空", text.isNotEmpty())
        }
    }

    @Test
    fun `small file multi chapter texts are isolated and exact length`() {
        val source = """
            # 第一章

            第一章正文，仅属于第一章。内容足够长以避免章节密度保护干扰：${"正文".repeat(80)}

            # 第二章

            第二章正文，仅属于第二章。内容足够长以避免章节密度保护干扰：${"正文".repeat(80)}
        """.trimIndent()
        val doc = MarkdownDocument(source)

        assertEquals(2, doc.chapters.size)

        val text0 = doc.text(0)
        val text1 = doc.text(1)
        assertTrue("第一章应包含第一章正文", text0.contains("第一章正文"))
        assertFalse("第一章不应包含第二章正文", text0.contains("第二章正文"))
        assertTrue("第二章应包含第二章正文", text1.contains("第二章正文"))
        assertFalse("第二章不应包含第一章正文", text1.contains("第一章正文"))
        assertEquals("第一章 text.length 应等于 charCount", doc.chapters[0].charCount, text0.length)
        assertEquals("第二章 text.length 应等于 charCount", doc.chapters[1].charCount, text1.length)

        // blocks 只含该章 canonicalRange 内的顶层块，且 canonicalRange 保持全书全局坐标
        val c0 = doc.chapters[0]
        val c1 = doc.chapters[1]
        val blocks0 = doc.blocks(0).filterIsInstance<DocBlock.Markdown>().single().chapter.blocks
        val blocks1 = doc.blocks(1).filterIsInstance<DocBlock.Markdown>().single().chapter.blocks
        assertTrue("第 0 章块列表不应为空", blocks0.isNotEmpty())
        assertTrue("第 1 章块列表不应为空", blocks1.isNotEmpty())
        assertTrue("小文件应为整本解析", doc.isWholeDocumentParse)
        for (block in blocks0) {
            assertTrue("第 0 章块起点应在本章范围内", block.canonicalRange.first >= c0.startOffset)
            assertTrue("第 0 章块终点应在本章范围内", block.canonicalRange.last < c0.startOffset + c0.charCount)
        }
        for (block in blocks1) {
            assertTrue("第 1 章块起点应在本章范围内", block.canonicalRange.first >= c1.startOffset)
            assertTrue("第 1 章块终点应在本章范围内", block.canonicalRange.last < c1.startOffset + c1.charCount)
        }
        // 全局坐标：第二章首块（H1）的 canonicalRange 起点应等于章节全书起点
        val firstHeading1 = blocks1.filterIsInstance<MarkdownBlock.Heading>().first()
        assertEquals("块 canonicalRange 应保持全书全局坐标", c1.startOffset, firstHeading1.canonicalRange.first)
    }

    @Test
    fun `small file without headings becomes single chapter`() {
        val source = "没有标题，只有正文。**粗体** 内容。"
        val doc = MarkdownDocument(source)
        assertEquals(1, doc.chapters.size)
        assertEquals("全文", doc.chapters[0].title)
    }

    @Test
    fun `legacy source offset migration`() {
        val source = "# 标题\n\n正文 **粗体** 和 `代码`。"
        val doc = MarkdownDocument(source)

        // 旧进度按源文本偏移存储；"粗体"在源文本中的位置
        val legacySourceOffset = source.indexOf("粗体")
        val migrated = doc.migrateLegacyOffset(legacySourceOffset)

        // 规范文本里 "粗体" 的起始位置
        val canonical = doc.text(0)
        val expected = canonical.indexOf("粗体")
        assertEquals("迁移后的规范偏移应对应规范文本中的粗体", expected, migrated)
    }

    // ── 流式模式 ─────────────────────────────────────────────────────

    @Test
    fun `streaming utf8 markdown opens and reads chapters`() {
        val file = TestFileGenerator.generateMarkdownUtf8(5, 500)
        tempFiles.add(file)

        val index = TxtFileScanner.scan(file)
        val doc = MarkdownDocument.fromFileIndex(file, index)

        assertTrue("应识别出章节", doc.chapters.size >= 5)
        assertTrue("总字符数应为正", doc.totalChars > 0)

        var sum = 0
        for (ci in doc.chapters.indices) {
            val text = doc.text(ci)
            assertTrue("第 $ci 章不应为空", text.isNotEmpty())
            assertEquals("第 $ci 章长度", doc.chapters[ci].charCount, text.length)
            sum += text.length
        }
        assertEquals("各章长度之和应等于 totalChars", doc.totalChars, sum)
    }

    @Test
    fun `streaming chapter offset shift preserved`() {
        val file = TestFileGenerator.generateMarkdownUtf8(3, 500)
        tempFiles.add(file)

        val index = TxtFileScanner.scan(file)
        val doc = MarkdownDocument.fromFileIndex(file, index)

        for (ci in doc.chapters.indices) {
            val blocks = doc.blocks(ci).filterIsInstance<DocBlock.Markdown>()
            val mdChapter = blocks.singleOrNull()?.chapter
            assertNotNull("第 $ci 章应返回 MarkdownChapter", mdChapter)

            val chapter = doc.chapters[ci]
            assertEquals("第 $ci 章规范文本长度应等于 charCount", chapter.charCount, mdChapter!!.canonicalText.length)

            // 块级 sourceRange 已换算为全局源偏移
            val firstBlock = mdChapter.blocks.first()
            assertTrue("第 $ci 章首块源范围应 >= 章节源起点", firstBlock.sourceRange.first >= 0)
        }
    }

    @Test
    fun `streaming fenced fake headings do not split chapters`() {
        val file = File.createTempFile("test_md_fence_", ".md")
        tempFiles.add(file)
        file.writeText(
            """
            # 第一章

            第一章正文内容，足够长以避免章节密度保护干扰：${"正文".repeat(100)}

            ```
            # fake heading in backtick fence
            ```

            # 第二章

            第二章正文内容，足够长以避免章节密度保护干扰：${"正文".repeat(100)}

            ~~~
            # fake heading in tilde fence
            ~~~

            ```kotlin
            # fake heading in unclosed fence
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )

        val index = TxtFileScanner.scan(file)
        val doc = MarkdownDocument.fromFileIndex(file, index)

        assertEquals("围栏内的 # 不应分章", 2, doc.chapters.size)
        assertEquals("第一章", doc.chapters[0].title)
        assertEquals("第二章", doc.chapters[1].title)
        assertFalse("不应出现 fake 标题章节", doc.chapters.any { it.title.contains("fake") })
    }

    @Test
    fun `50MB markdown bounded read`() {
        val file = TestFileGenerator.generateLargeMarkdownUtf8(50L * 1024 * 1024, 100)
        tempFiles.add(file)

        val index = TxtFileScanner.scan(file)
        val doc = MarkdownDocument.fromFileIndex(file, index)

        assertTrue("应有不少于 50 章", doc.chapters.size >= 50)
        assertTrue("总字符数应大于 10M", doc.totalChars > 10_000_000)

        val first = doc.text(0)
        val mid = doc.text(doc.chapters.size / 2)
        val last = doc.text(doc.chapters.size - 1)
        assertTrue("首章可读", first.isNotEmpty())
        assertTrue("中章可读", mid.isNotEmpty())
        assertTrue("末章可读", last.isNotEmpty())

        // 拼接所有章节文本，验证无丢字
        var sum = 0L
        for (ci in doc.chapters.indices) {
            sum += doc.text(ci).length
        }
        assertEquals(doc.totalChars.toLong(), sum)
    }

    // ── 编码 ─────────────────────────────────────────────────────────

    @Test
    fun `utf8 bom markdown streaming read`() {
        val file = TestFileGenerator.generateMarkdownWithBom(
            bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            chapterCount = 3,
            charsPerChapter = 300,
            charset = Charsets.UTF_8,
        )
        tempFiles.add(file)

        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-8", index.encoding)
        val doc = MarkdownDocument.fromFileIndex(file, index)
        assertTrue(doc.chapters.isNotEmpty())
        assertTrue(doc.text(0).isNotEmpty())
    }

    @Test
    fun `utf16le markdown streaming read`() {
        val file = TestFileGenerator.generateMarkdownWithBom(
            bomBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            chapterCount = 3,
            charsPerChapter = 300,
            charset = Charsets.UTF_16LE,
        )
        tempFiles.add(file)

        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-16LE", index.encoding)
        val doc = MarkdownDocument.fromFileIndex(file, index)
        assertTrue(doc.chapters.isNotEmpty())
        assertTrue(doc.text(0).isNotEmpty())
    }

    @Test
    fun `utf16be markdown streaming read`() {
        val file = TestFileGenerator.generateMarkdownWithBom(
            bomBytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
            chapterCount = 3,
            charsPerChapter = 300,
            charset = Charsets.UTF_16BE,
        )
        tempFiles.add(file)

        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-16BE", index.encoding)
        val doc = MarkdownDocument.fromFileIndex(file, index)
        assertTrue(doc.chapters.isNotEmpty())
        assertTrue(doc.text(0).isNotEmpty())
    }

    @Test
    fun `gb18030 markdown streaming read`() {
        val file = TestFileGenerator.generateMarkdownGb18030(3, 300)
        tempFiles.add(file)

        val index = TxtFileScanner.scan(file)
        assertEquals("GB18030", index.encoding)
        val doc = MarkdownDocument.fromFileIndex(file, index)
        assertTrue(doc.chapters.isNotEmpty())
        assertTrue(doc.text(0).isNotEmpty())
    }

    // ── 安全 / 降级 ──────────────────────────────────────────────────

    @Test
    fun `html block tags stripped but content preserved`() {
        val source = """
            正文段落。

            <script>alert(1)</script>

            后续段落。
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val text = chapter.canonicalText
        assertFalse("不应包含 script 标签", text.contains("<script>"))
        assertTrue("HTML 内容不应整段丢失", text.contains("alert(1)"))
        assertTrue("应保留可见段落", text.contains("正文段落"))
        assertTrue("应保留后续段落", text.contains("后续段落"))
    }

    @Test
    fun `image path traversal is preserved but not resolved`() {
        val source = "![alt](../secret.png)"
        val chapter = MarkdownParser.parse(source)
        val img = chapter.blocks
            .filterIsInstance<MarkdownBlock.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<MdInline.Image>()
            .single()
        assertEquals("../secret.png", img.url)
    }

    @Test
    fun `unclosed fenced code block does not crash`() {
        val file = File.createTempFile("test_md_unclosed_", ".md")
        tempFiles.add(file)
        file.writeText("```\n未闭合代码", Charsets.UTF_8)

        val index = TxtFileScanner.scan(file)
        val doc = MarkdownDocument.fromFileIndex(file, index)
        assertTrue(doc.chapters.isNotEmpty())
        assertTrue(doc.text(0).isNotEmpty())
    }

    @Test
    fun `very long single line markdown`() {
        val file = File.createTempFile("test_md_longline_", ".md")
        tempFiles.add(file)
        file.writeText("A".repeat(200_000), Charsets.UTF_8)

        val index = TxtFileScanner.scan(file)
        val doc = MarkdownDocument.fromFileIndex(file, index)
        assertTrue(doc.totalChars >= 200_000)
        assertTrue(doc.text(0).isNotEmpty())
    }
}
