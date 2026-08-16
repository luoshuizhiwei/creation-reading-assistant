package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainTextDocumentTest {

    private fun body(n: Int = 600) = "正文内容。".repeat(n / 5)

    @Test
    fun `text equals blocks joined by newline for every chapter`() {
        // ReaderDocument 接口的核心不变式。破了它，章内精确跳转就会跳偏。
        val text = buildString {
            append("第一章 起\n").append(body()).append("\n\n").append(body()).append("\n")
            append("第二章 承\n").append(body())
        }
        val doc = PlainTextDocument(text)
        doc.chapters.forEach { c ->
            val joined = doc.blocks(c.index)
                .filterIsInstance<DocBlock.Text>()
                .joinToString("\n") { it.text }
            assertEquals("第 ${c.index} 章的 text() 必须与 blocks() 同源", joined, doc.text(c.index))
        }
    }

    @Test
    fun `chapter offsets are exact and contiguous`() {
        val text = "第一章 起\n" + body() + "\n第二章 承\n" + body()
        val doc = PlainTextDocument(text)
        assertEquals(2, doc.chapters.size)
        assertEquals(0, doc.chapters[0].startOffset)
        assertEquals(
            doc.chapters[0].startOffset + doc.chapters[0].charCount,
            doc.chapters[1].startOffset,
        )
        assertEquals(text.length, doc.totalChars)
        // TXT 侧的字符数是精确值，不是估算
        assertTrue(doc.chapters.none { it.charCountIsEstimated })
    }

    @Test
    fun `blank line separates paragraphs but single newline does not`() {
        // 注意：「这是新的一段。」后面必须再跟空行，否则按规则它会和后续正文接成同一段——
        // 这正是本用例要验证的行为，别把它当成 bug 改掉。
        val text = "第一章 起\n" + body() + "\n上一段。\n这行是同一段的续行。\n\n这是新的一段。\n\n" + body()
        val doc = PlainTextDocument(text)
        val texts = doc.blocks(0).filterIsInstance<DocBlock.Text>().map { it.text }
        assertTrue("单个换行不应分段，实际块数=${texts.size}", texts.any { it.contains("上一段。这行是同一段的续行。") })
        assertTrue("空行应分段，实际=$texts", texts.any { it == "这是新的一段。" })
    }

    @Test
    fun `single newline keeps following text in the same paragraph`() {
        val text = "第一章 起\n\n第一句。\n第二句。\n\n另一段。\n\n" + body()
        val doc = PlainTextDocument(text)
        val texts = doc.blocks(0).filterIsInstance<DocBlock.Text>().map { it.text }
        assertTrue("单换行应并入同段，实际=$texts", texts.contains("第一句。第二句。"))
        assertTrue(texts.contains("另一段。"))
    }

    @Test
    fun `chapter title becomes a heading block`() {
        val doc = PlainTextDocument("第一章 起\n" + body())
        val first = doc.blocks(0).filterIsInstance<DocBlock.Text>().first()
        assertTrue("章节标题应标为 heading", first.isHeading)
        assertEquals("第一章 起", first.text)
    }

    @Test
    fun `locate maps global offset back to chapter and inner offset`() {
        val text = "第一章 起\n" + body() + "\n第二章 承\n" + body()
        val doc = PlainTextDocument(text)
        val c1 = doc.chapters[1]
        val (chapter, inner) = doc.locate(c1.startOffset + 10)
        assertEquals(1, chapter)
        assertEquals(10, inner)
        // 边界与越界都要落在合法范围内
        assertEquals(0, doc.locate(0).first)
        assertEquals(doc.chapters.size - 1, doc.locate(Int.MAX_VALUE).first)
        assertEquals(0, doc.locate(-100).first)
    }

    @Test
    fun `handles text without any chapter heading`() {
        val doc = PlainTextDocument(body(5000))
        assertEquals(1, doc.chapters.size)
        assertEquals("全文", doc.chapters[0].title)
        assertTrue(doc.blocks(0).isNotEmpty())
    }

    // ── profile 小文件：自定义模式 + density 语义 ─────────────────────

    @Test
    fun `small file with custom profile detects custom chapters and marks heading`() {
        val profile = TxtTocProfile(
            key = "custom-small",
            patterns = listOf(Regex("^foo-\\d+ 起$"), Regex("^第\\d+章 承$")),
            densityGuard = false,
        )
        val text = "foo-1 起\n" + body() + "\n第2章 承\n" + body()
        val doc = PlainTextDocument(text, profile)

        assertEquals(listOf("foo-1 起", "第2章 承"), doc.chapters.map { it.title })
        val first = doc.blocks(0).filterIsInstance<DocBlock.Text>().first()
        assertTrue("章节标题应标为 heading", first.isHeading)
        assertEquals("foo-1 起", first.text)
        // 接口不变式：text() == blocks() joined
        assertEquals(
            doc.text(0),
            doc.blocks(0).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text },
        )
    }

    @Test
    fun `small file profile with density guard off keeps dense headings`() {
        val profile = TxtTocProfile(
            key = "loose-small",
            patterns = listOf(Regex("^\\d+\\. 小标题\\d+$")),
            densityGuard = false,
        )
        val text = (1..20).joinToString("\n") { "$it. 小标题$it" }
        val doc = PlainTextDocument(text, profile)
        assertEquals(20, doc.chapters.size)
    }

    @Test
    fun `small file profile with density guard on falls back to full text`() {
        val profile = TxtTocProfile(
            key = "dense-small",
            patterns = listOf(Regex("^第\\d+章$")),
            densityGuard = true,
        )
        val text = (1..20).joinToString("\n") { "第${it}章" }
        val doc = PlainTextDocument(text, profile)
        assertEquals(1, doc.chapters.size)
        assertEquals("全文", doc.chapters[0].title)
    }

    @Test
    fun `small file legacy non-builtin ruleId keeps density off semantics`() {
        val text = (1..20).joinToString("\n") { "第${it}章" }
        assertEquals("标准密度保护：密集目录整体作废", 1, PlainTextDocument(text).chapters.size)
        assertEquals("非 builtin ruleId 关闭 density：20 章保留", 20, PlainTextDocument(text, "num-dot").chapters.size)
    }
}
