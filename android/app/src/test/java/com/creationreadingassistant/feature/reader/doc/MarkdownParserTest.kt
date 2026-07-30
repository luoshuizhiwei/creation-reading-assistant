package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.*
import org.junit.Test

class MarkdownParserTest {

    // ── H1～H6 标题 ────────────────────────────────────────────

    @Test
    fun `parse heading levels`() {
        val source = """
            # H1
            ## H2
            ### H3
            #### H4
            ##### H5
            ###### H6
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val headings = chapter.blocks.filterIsInstance<MarkdownBlock.Heading>()
        assertEquals(6, headings.size)
        for ((i, h) in headings.withIndex()) {
            assertEquals(i + 1, h.level)
            assertEquals("H${i + 1}", h.inlines.joinCanonicalText())
        }
    }

    // CommonMark 将 7 个及以上 # 视为普通段落，此行为不在 clamp 路径触发；
    // 若 future commonmark 版本改变，这里会回归为段落仍然可读。

    // ── 段落 ──────────────────────────────────────────────────

    @Test
    fun `parse plain paragraph`() {
        val source = "This is a plain paragraph."
        val chapter = MarkdownParser.parse(source)
        assertEquals(1, chapter.blocks.size)
        val p = chapter.blocks.single() as MarkdownBlock.Paragraph
        assertEquals("This is a plain paragraph.", p.inlines.joinCanonicalText())
    }

    @Test
    fun `multiple paragraphs separated by blank line`() {
        val source = "First paragraph.\n\nSecond paragraph."
        val chapter = MarkdownParser.parse(source)
        assertEquals(2, chapter.blocks.size)
        val texts = chapter.blocks.map { (it as MarkdownBlock.Paragraph).inlines.joinCanonicalText() }
        assertEquals(listOf("First paragraph.", "Second paragraph."), texts)
    }

    // ── 无序列表 ──────────────────────────────────────────────

    @Test
    fun `parse unordered list`() {
        val source = """
            - Apple
            - Banana
            - Cherry
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val list = chapter.blocks.single() as MarkdownBlock.UnorderedList
        assertEquals(3, list.items.size)
        assertEquals("Apple", list.items[0].singleParagraphText())
        assertEquals("Banana", list.items[1].singleParagraphText())
        assertEquals("Cherry", list.items[2].singleParagraphText())
    }

    // ── 有序列表 ──────────────────────────────────────────────

    @Test
    fun `parse ordered list`() {
        val source = """
            1. First
            2. Second
            3. Third
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val list = chapter.blocks.single() as MarkdownBlock.OrderedList
        assertEquals(1, list.startNumber)
        assertEquals(3, list.items.size)
        assertEquals("First", list.items[0].singleParagraphText())
    }

    @Test
    fun `ordered list start number respected`() {
        val source = """
            5. Fifth
            6. Sixth
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val list = chapter.blocks.single() as MarkdownBlock.OrderedList
        assertEquals(5, list.startNumber)
    }

    // ── 任务列表 ──────────────────────────────────────────────

    @Test
    fun `parse task list`() {
        val source = """
            - [ ] Unchecked
            - [x] Checked
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val list = chapter.blocks.single() as MarkdownBlock.TaskList
        assertEquals(2, list.items.size)
        assertFalse(list.items[0].checked)
        assertTrue(list.items[1].checked)
        assertEquals("Unchecked", list.items[0].blocks.singleParagraphText())
        assertEquals("Checked", list.items[1].blocks.singleParagraphText())
    }

    // ── 嵌套列表 ──────────────────────────────────────────────

    @Test
    fun `parse nested list`() {
        val source = """
            - Outer
              - Inner
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val outer = chapter.blocks.single() as MarkdownBlock.UnorderedList
        val innerList = outer.items[0].lastOrNull() as? MarkdownBlock.UnorderedList
        assertNotNull("嵌套列表应被解析", innerList)
        assertEquals("Inner", innerList!!.items[0].singleParagraphText())
    }

    // ── 引用块 ────────────────────────────────────────────────

    @Test
    fun `parse block quote`() {
        val source = "> This is a quote."
        val chapter = MarkdownParser.parse(source)
        val quote = chapter.blocks.single() as MarkdownBlock.BlockQuote
        assertEquals("This is a quote.", quote.blocks.singleParagraphText())
    }

    @Test
    fun `nested quote`() {
        val source = "> Outer\n>> Inner"
        val chapter = MarkdownParser.parse(source)
        val quote = chapter.blocks.single() as MarkdownBlock.BlockQuote
        val inner = quote.blocks.lastOrNull() as? MarkdownBlock.BlockQuote
        assertNotNull("嵌套引用应被解析", inner)
    }

    // ── 围栏代码块 ────────────────────────────────────────────

    @Test
    fun `parse fenced code block`() {
        val source = """
            ```kotlin
            val x = 1
            ```
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val code = chapter.blocks.single() as MarkdownBlock.FencedCodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.content.trim())
    }

    @Test
    fun `fenced code block without language`() {
        val source = """
            ```
            plain code
            ```
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val code = chapter.blocks.single() as MarkdownBlock.FencedCodeBlock
        assertNull(code.language)
    }

    // ── 缩进代码块 ────────────────────────────────────────────

    @Test
    fun `parse indented code block`() {
        val source = "    val x = 1\n    val y = 2"
        val chapter = MarkdownParser.parse(source)
        val code = chapter.blocks.single() as MarkdownBlock.IndentedCodeBlock
        assertTrue(code.content.contains("val x = 1"))
    }

    // ── 行内代码 ──────────────────────────────────────────────

    @Test
    fun `parse inline code`() {
        val source = "Use `println()` for output."
        val chapter = MarkdownParser.parse(source)
        val p = chapter.blocks.single() as MarkdownBlock.Paragraph
        val code = p.inlines.filterIsInstance<MdInline.Code>()
        assertEquals(1, code.size)
        assertEquals("println()", code[0].text)
    }

    // ── 粗体、斜体、删除线 ────────────────────────────────────

    @Test
    fun `parse bold italic strikethrough`() {
        val source = "**bold** *italic* ~~strike~~"
        val chapter = MarkdownParser.parse(source)
        val p = chapter.blocks.single() as MarkdownBlock.Paragraph
        val text = p.inlines.joinCanonicalText()
        assertEquals("bold italic strike", text)
        assertEquals(1, p.inlines.filterIsInstance<MdInline.Strong>().size)
        assertEquals(1, p.inlines.filterIsInstance<MdInline.Emphasis>().size)
        assertEquals(1, p.inlines.filterIsInstance<MdInline.Strikethrough>().size)
    }

    // ── 分隔线 ────────────────────────────────────────────────

    @Test
    fun `parse horizontal rule`() {
        val source = "---"
        val chapter = MarkdownParser.parse(source)
        val hr = chapter.blocks.single() as MarkdownBlock.HorizontalRule
        assertNotNull(hr)
    }

    // ── 表格 ──────────────────────────────────────────────────

    @Test
    fun `parse table`() {
        val source = """
            | Name | Age |
            |------|-----|
            | Alice | 30 |
            | Bob | 25 |
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val table = chapter.blocks.single() as MarkdownBlock.Table
        assertEquals(2, table.header.size)
        assertEquals(2, table.rows.size)
        assertEquals("Alice", table.rows[0][0].inlines.joinCanonicalText())
    }

    // ── 链接 ──────────────────────────────────────────────────

    @Test
    fun `parse link`() {
        val source = "[Google](https://google.com)"
        val chapter = MarkdownParser.parse(source)
        val p = chapter.blocks.single() as MarkdownBlock.Paragraph
        val link = p.inlines.filterIsInstance<MdInline.Link>().single()
        assertEquals("https://google.com", link.url)
        assertEquals("Google", link.children.joinCanonicalText())
    }

    // ── 图片 ──────────────────────────────────────────────────

    @Test
    fun `parse image`() {
        val source = "![Alt text](image.png)"
        val chapter = MarkdownParser.parse(source)
        val p = chapter.blocks.single() as MarkdownBlock.Paragraph
        val img = p.inlines.filterIsInstance<MdInline.Image>().single()
        assertEquals("image.png", img.url)
        assertEquals("Alt text", img.alt)
    }

    @Test
    fun `image without alt falls back`() {
        val source = "![](image.png)"
        val chapter = MarkdownParser.parse(source)
        val p = chapter.blocks.single() as MarkdownBlock.Paragraph
        val img = p.inlines.filterIsInstance<MdInline.Image>().single()
        assertEquals("图片", img.alt)
    }

    // ── 空行与段落间距 ────────────────────────────────────────

    @Test
    fun `blank lines between blocks`() {
        val source = "Para1.\n\n\nPara2."
        val chapter = MarkdownParser.parse(source)
        // 多余空行在 CommonMark 中通常被忽略，只保留两个段落
        assertEquals(2, chapter.blocks.filterIsInstance<MarkdownBlock.Paragraph>().size)
    }

    // ── 中英文混排、emoji、组合字符 ─────────────────────────────

    @Test
    fun `chinese and english mixed`() {
        val source = "中文**加粗** English *italic* 混合。"
        val chapter = MarkdownParser.parse(source)
        val text = chapter.canonicalText
        assertTrue(text.contains("中文"))
        assertTrue(text.contains("English"))
        assertTrue(text.contains("混合"))
    }

    @Test
    fun `emoji in text`() {
        val source = "Hello \uD83D\uDE00 world"
        val chapter = MarkdownParser.parse(source)
        assertTrue(chapter.canonicalText.contains("\uD83D\uDE00"))
    }

    // ── 规范文本完整性 ────────────────────────────────────────

    @Test
    fun `canonical text removes markdown markers`() {
        val source = "# Title\n\nParagraph with **bold**."
        val chapter = MarkdownParser.parse(source)
        val text = chapter.canonicalText
        assertFalse("不应有 #", text.contains("# Title"))
        assertTrue("应保留 Title", text.contains("Title"))
        assertFalse("不应有 **", text.contains("**"))
        assertTrue("应保留 bold", text.contains("bold"))
    }

    @Test
    fun `headings extracted`() {
        val source = "# One\n\n## Two\n\n### Three"
        val chapter = MarkdownParser.parse(source)
        assertEquals(3, chapter.headings.size)
        assertEquals("One", chapter.headings[0].title)
        assertEquals(1, chapter.headings[0].level)
        assertEquals("Three", chapter.headings[2].title)
    }

    // ── 损坏/边缘 Markdown ────────────────────────────────────

    @Test
    fun `unclosed code fence`() {
        val source = "```\nunclosed"
        val chapter = MarkdownParser.parse(source)
        // CommonMark 会将其解析为代码块直到文档结束
        assertEquals(1, chapter.blocks.size)
    }

    @Test
    fun `empty document`() {
        val chapter = MarkdownParser.parse("")
        assertEquals(0, chapter.blocks.size)
        assertEquals("", chapter.canonicalText)
    }

    @Test
    fun `only whitespace`() {
        val chapter = MarkdownParser.parse("   \n\n   ")
        assertEquals(0, chapter.blocks.size)
    }

    @Test
    fun `malicious link does not crash`() {
        val source = "[click](javascript:alert(1))"
        val chapter = MarkdownParser.parse(source)
        val link = (chapter.blocks.single() as MarkdownBlock.Paragraph)
            .inlines.filterIsInstance<MdInline.Link>().single()
        assertEquals("javascript:alert(1)", link.url)
    }

    @Test
    fun `very long single line`() {
        val longText = "A".repeat(100_000)
        val chapter = MarkdownParser.parse(longText)
        assertEquals(1, chapter.blocks.size)
        assertEquals(100_000, chapter.canonicalText.length)
    }

    @Test
    fun `empty list item`() {
        val source = "- "
        val chapter = MarkdownParser.parse(source)
        // CommonMark 会将其解析为一个空列表项
        val list = chapter.blocks.single() as MarkdownBlock.UnorderedList
        assertEquals(1, list.items.size)
    }

    @Test
    fun `image url with path traversal is preserved`() {
        val source = "![alt](../../etc/passwd)"
        val chapter = MarkdownParser.parse(source)
        val img = (chapter.blocks.single() as MarkdownBlock.Paragraph)
            .inlines.filterIsInstance<MdInline.Image>().single()
        assertEquals("../../etc/passwd", img.url)
        assertEquals("alt", img.alt)
    }

    @Test
    fun `remote image url does not crash`() {
        val source = "![desc](https://example.com/img.png)"
        val chapter = MarkdownParser.parse(source)
        val img = (chapter.blocks.single() as MarkdownBlock.Paragraph)
            .inlines.filterIsInstance<MdInline.Image>().single()
        assertEquals("https://example.com/img.png", img.url)
    }

    @Test
    fun `combining characters kept in canonical text`() {
        val source = "中文 **加粗** 和 emoji \uD83D\uDE00。"
        val chapter = MarkdownParser.parse(source)
        val text = chapter.canonicalText
        assertTrue(text.contains("中文"))
        assertTrue(text.contains("加粗"))
        assertTrue(text.contains("\uD83D\uDE00"))
        assertFalse(text.contains("**"))
    }

    // ── 辅助 ──────────────────────────────────────────────────

    private fun List<MdInline>.joinCanonicalText(): String = buildString {
        for (inline in this@joinCanonicalText) append(inline.canonicalText())
    }

    private fun MdInline.canonicalText(): String = when (this) {
        is MdInline.Text -> text
        is MdInline.Code -> text
        is MdInline.Strong -> children.joinCanonicalText()
        is MdInline.Emphasis -> children.joinCanonicalText()
        is MdInline.Strikethrough -> children.joinCanonicalText()
        is MdInline.Link -> children.joinCanonicalText()
        is MdInline.Image -> alt
        is MdInline.HardLineBreak -> "\n"
        is MdInline.SoftLineBreak -> " "
    }

    private fun List<MarkdownBlock>.singleParagraphText(): String {
        val p = filterIsInstance<MarkdownBlock.Paragraph>().firstOrNull()
            ?: error("Expected at least one paragraph")
        return p.inlines.joinCanonicalText()
    }
}
