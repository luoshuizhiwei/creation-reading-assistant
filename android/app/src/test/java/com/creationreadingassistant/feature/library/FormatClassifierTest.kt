package com.creationreadingassistant.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 格式真源分类器纯 JVM 测试。
 *
 * 覆盖：`.epub` 扩展名但正文为普通文本、声明 epub 且正文为 ZIP、缺失格式、
 * TXT/Markdown 正常归类、非 ZIP 拒绝、大小写归一与不足 4 字节正文。
 */
class FormatClassifierTest {

    private val zipMagic = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // PK\x03\x04
    private val plainText = "第一章 你好".toByteArray(Charsets.UTF_8)

    @Test
    fun `epub extension with plain text first bytes is rejected`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = "book.epub",
            firstBytes = plainText,
        )
        assertEquals(FormatClassifier.Verdict.Rejected("声明为 EPUB 但正文不是有效的 ZIP 文件", "epub"), verdict)
    }

    @Test
    fun `epub claim with zip magic is accepted as epub`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = "epub",
            fileName = null,
            firstBytes = zipMagic,
        )
        assertEquals(FormatClassifier.Verdict.Accepted("epub"), verdict)
    }

    @Test
    fun `zip bytes with epub extension are accepted as epub`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = "book.epub",
            firstBytes = zipMagic,
        )
        assertEquals(FormatClassifier.Verdict.Accepted("epub"), verdict)
    }

    @Test
    fun `epub claim with non-zip zip-like prefix is rejected`() {
        // PK 开头但不是 PK\x03\x04，仍不是有效 EPUB
        val verdict = FormatClassifier.classify(
            claimedFormat = "epub",
            fileName = null,
            firstBytes = byteArrayOf(0x50, 0x4B, 0x05, 0x06),
        )
        assertTrue(verdict is FormatClassifier.Verdict.Rejected)
    }

    @Test
    fun `missing format and filename with non-zip content is rejected`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = null,
            firstBytes = plainText,
        )
        assertTrue(verdict is FormatClassifier.Verdict.Rejected)
        assertEquals(null, (verdict as FormatClassifier.Verdict.Rejected).claimedFormat)
    }

    @Test
    fun `missing format and filename but zip bytes is recognized as epub`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = null,
            firstBytes = zipMagic,
        )
        assertEquals(FormatClassifier.Verdict.Accepted("epub"), verdict)
    }

    @Test
    fun `txt claim with zip bytes takes epub priority`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = "txt",
            fileName = "book.txt",
            firstBytes = zipMagic,
        )
        assertEquals(FormatClassifier.Verdict.Accepted("epub"), verdict)
    }

    @Test
    fun `md extension with zip bytes takes epub priority`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = "book.md",
            firstBytes = zipMagic,
        )
        assertEquals(FormatClassifier.Verdict.Accepted("epub"), verdict)
    }

    @Test
    fun `missing format with epub extension but plain text is rejected`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = "book.epub",
            firstBytes = plainText,
        )
        assertTrue(verdict is FormatClassifier.Verdict.Rejected)
        assertEquals("epub", (verdict as FormatClassifier.Verdict.Rejected).claimedFormat)
    }

    @Test
    fun `txt extension is accepted as txt`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = "book.txt",
            firstBytes = plainText,
        )
        assertEquals(FormatClassifier.Verdict.Accepted("txt"), verdict)
    }

    @Test
    fun `md extension is accepted as md`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = "book.md",
            firstBytes = "# title".toByteArray(),
        )
        assertEquals(FormatClassifier.Verdict.Accepted("md"), verdict)
    }

    @Test
    fun `markdown extension is normalized to md`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = "book.markdown",
            firstBytes = plainText,
        )
        assertEquals(FormatClassifier.Verdict.Accepted("md"), verdict)
    }

    @Test
    fun `markdown claimed format is normalized to md`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = "markdown",
            fileName = null,
            firstBytes = plainText,
        )
        assertEquals(FormatClassifier.Verdict.Accepted("md"), verdict)
    }

    @Test
    fun `uppercase claimed format is normalized`() {
        assertEquals(
            FormatClassifier.Verdict.Accepted("epub"),
            FormatClassifier.classify("EPUB", null, zipMagic),
        )
        assertEquals(
            FormatClassifier.Verdict.Accepted("md"),
            FormatClassifier.classify("Markdown", null, plainText),
        )
        assertEquals(
            FormatClassifier.Verdict.Accepted("txt"),
            FormatClassifier.classify("TXT", null, plainText),
        )
    }

    @Test
    fun `unsupported extension is rejected without a claimed format`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = null,
            fileName = "book.docx",
            firstBytes = plainText,
        )
        assertTrue(verdict is FormatClassifier.Verdict.Rejected)
        assertNull((verdict as FormatClassifier.Verdict.Rejected).claimedFormat)
    }

    @Test
    fun `fewer than four bytes cannot be epub`() {
        val verdict = FormatClassifier.classify(
            claimedFormat = "epub",
            fileName = null,
            firstBytes = byteArrayOf(0x50, 0x4B),
        )
        assertTrue(verdict is FormatClassifier.Verdict.Rejected)
    }
}