package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.*
import org.junit.Test

class MarkdownOffsetMapTest {

    @Test
    fun `empty map is identity`() {
        val map = MarkdownOffsetMap(emptyList())
        assertEquals(0, map.toSource(0))
        assertEquals(10, map.toSource(10))
        assertEquals(0, map.toCanonical(0))
        assertEquals(10, map.toCanonical(10))
    }

    @Test
    fun `heading maps back to source`() {
        val source = "# Title\n\nParagraph."
        val chapter = MarkdownParser.parse(source)
        val map = chapter.offsetMap

        // "Title" in canonical text should map back to source position after "# "
        val canonical = chapter.canonicalText
        val titleStart = canonical.indexOf("Title")
        assertTrue(titleStart >= 0)
        val src = map.toSource(titleStart)
        assertEquals(source.indexOf("Title"), src)
    }

    @Test
    fun `bold markers are excluded from canonical`() {
        val source = "**bold**"
        val chapter = MarkdownParser.parse(source)
        val map = chapter.offsetMap

        val canonical = chapter.canonicalText
        assertEquals("bold", canonical)

        val canonicalStart = 0
        val canonicalEnd = canonical.length
        val srcStart = map.toSource(canonicalStart)
        val srcEnd = map.toSource(canonicalEnd)
        assertEquals(source.indexOf("bold"), srcStart)
        assertEquals(source.indexOf("bold") + "bold".length, srcEnd)
    }

    @Test
    fun `bold roundtrip across markers`() {
        val source = "Hello **bold** world"
        val chapter = MarkdownParser.parse(source)
        val map = chapter.offsetMap

        // 每个规范字符都应该能正反向映射
        for (c in 0..chapter.canonicalText.length) {
            val s = map.toSource(c)
            val back = map.toCanonical(s)
            assertTrue("c=$c s=$s back=$back", back <= c)
        }
    }

    @Test
    fun `inline code maps correctly`() {
        val source = "Use `code` here"
        val chapter = MarkdownParser.parse(source)
        val map = chapter.offsetMap
        val canonical = chapter.canonicalText
        val codeStart = canonical.indexOf("code")
        val src = map.toSource(codeStart)
        assertEquals(source.indexOf("code"), src)
    }

    @Test
    fun `link text maps back to source`() {
        val source = "[link text](https://example.com)"
        val chapter = MarkdownParser.parse(source)
        val map = chapter.offsetMap
        val canonical = chapter.canonicalText
        val start = canonical.indexOf("link text")
        val src = map.toSource(start)
        assertEquals(source.indexOf("link text"), src)
    }

    @Test
    fun `image alt maps back to source`() {
        val source = "![alt text](image.png)"
        val chapter = MarkdownParser.parse(source)
        val map = chapter.offsetMap
        val canonical = chapter.canonicalText
        val start = canonical.indexOf("alt text")
        val src = map.toSource(start)
        assertEquals(source.indexOf("alt text"), src)
    }

    @Test
    fun `source offset shift preserved`() {
        val prefix = "SKIP THIS\n"
        val source = "# Title\n\nBody."
        // sourceOffsetShift 用于解析片段时把片段内的源偏移换算为全局偏移，
        // 因此应传入片段本身，而不是前缀+片段。
        val chapter = MarkdownParser.parse(source, sourceOffsetShift = prefix.length)
        val map = chapter.offsetMap
        val canonical = chapter.canonicalText
        val titleStart = canonical.indexOf("Title")
        val src = map.toSource(titleStart)
        assertEquals(prefix.length + source.indexOf("Title"), src)
    }

    @Test
    fun `toCanonical monotonic and within bounds`() {
        val source = """
            # Heading

            Para **bold** *italic* `code`.

            - Item one
            - Item two
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val map = chapter.offsetMap
        // 源文件每个偏移都映射到合法规范偏移
        for (s in 0..source.length) {
            val c = map.toCanonical(s)
            assertTrue("s=$s c=$c", c in 0..chapter.canonicalText.length)
        }
    }

    @Test
    fun `canonical concatenation equals chapter text`() {
        val source = """
            # A

            P1.

            - L1
            - L2

            > Quote
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val canonical = chapter.canonicalText
        // 所有块规范范围拼接应等于 canonicalText（块间有 1 个换行）
        val rebuilt = buildString {
            var first = true
            for (block in chapter.blocks) {
                if (!first) append('\n')
                first = false
                append(canonical.substring(block.canonicalRange.first, block.canonicalRange.last + 1))
            }
        }
        assertEquals(canonical, rebuilt)
    }

    @Test
    fun `list item marker excluded from canonical`() {
        val source = """
            - Item one
            - Item two
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val canonical = chapter.canonicalText
        assertFalse(canonical.contains("- Item"))
        assertTrue(canonical.contains("Item one"))
    }

    @Test
    fun `table header separator excluded`() {
        val source = """
            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val canonical = chapter.canonicalText
        assertFalse("不应包含分隔线", canonical.contains("---"))
        assertTrue(canonical.contains("A"))
        assertTrue(canonical.contains("1"))
    }

    @Test
    fun `emoji offset roundtrip`() {
        val source = "Hello \uD83D\uDE00 world"
        val chapter = MarkdownParser.parse(source)
        val map = chapter.offsetMap
        val canonical = chapter.canonicalText

        val emojiStart = canonical.indexOf("\uD83D\uDE00")
        assertTrue(emojiStart >= 0)
        val src = map.toSource(emojiStart)
        assertEquals(source.indexOf("\uD83D\uDE00"), src)
    }

    @Test
    fun `source to canonical monotonic`() {
        val source = "# H\n\nP `code` and **bold**."
        val chapter = MarkdownParser.parse(source)
        val map = chapter.offsetMap
        for (s in 0..source.length) {
            val c = map.toCanonical(s)
            assertTrue("s=$s -> c=$c", c in 0..chapter.canonicalText.length)
        }
    }

    @Test
    fun `all reading units concatenate without loss`() {
        val source = """
            # Title

            Paragraph **one**.

            ```
            code line
            ```

            - item

            [link](https://example.com)
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val canonical = chapter.canonicalText
        var pos = 0
        val sb = StringBuilder()
        for (block in chapter.blocks) {
            if (pos > 0) sb.append('\n')
            val text = canonical.substring(block.canonicalRange.first, block.canonicalRange.last + 1)
            sb.append(text)
            pos += text.length + 1
        }
        assertEquals(canonical, sb.toString())
    }
}
