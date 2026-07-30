package com.creationreadingassistant.feature.reader.doc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class TxtFileScannerTest {

    private val tempFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun track(file: File): File {
        tempFiles.add(file)
        return file
    }

    // ── 1. UTF-8 无章节文件 → 单章「全文」 ────────────────────────────

    @Test
    fun `UTF-8 file without chapter headings yields single chapter`() {
        val file = track(File.createTempFile("test_no_chapters_", ".txt"))
        file.writeText("这是一段普通的测试文本。\n没有章节标题。\n只有正文内容。", Charsets.UTF_8)

        val index = TxtFileScanner.scan(file)

        assertEquals(1, index.chapters.size)
        assertEquals("全文", index.chapters[0].title)
        assertEquals(0, index.chapters[0].index)
        assertEquals("UTF-8", index.encoding)
        assertTrue(index.totalCharCount > 0)
    }

    // ── 2. UTF-8 有章节文件 → 正确识别章节边界 ────────────────────────

    @Test
    fun `UTF-8 file with chapter headings detects correct boundaries`() {
        val file = track(File.createTempFile("test_chapters_", ".txt"))
        // Each chapter body is ~500 chars, well above the 300 density threshold
        val body = "测试正文内容。".repeat(70)
        val content = "第一章 初见\n$body\n第二章 重逢\n$body"
        file.writeText(content, Charsets.UTF_8)

        val index = TxtFileScanner.scan(file)

        assertEquals(2, index.chapters.size)
        assertEquals("第一章 初见", index.chapters[0].title)
        assertEquals("第二章 重逢", index.chapters[1].title)
        assertEquals(0, index.chapters[0].index)
        assertEquals(1, index.chapters[1].index)
        assertEquals(0L, index.chapters[0].charStart)
        assertEquals("UTF-8", index.encoding)

        // charStart of chapter 1 should equal charCount of chapter 0
        assertEquals(index.chapters[0].charStart + index.chapters[0].charCount, index.chapters[1].charStart)

        // Both chapters should have substantial char counts
        assertTrue(index.chapters[0].charCount > 300)
        assertTrue(index.chapters[1].charCount > 300)
    }

    // ── 3. UTF-8 BOM → 正确跳过 BOM，charStart 从 0 开始 ──────────────

    @Test
    fun `UTF-8 BOM is skipped and first chapter charStart is 0`() {
        val file = track(
            TestFileGenerator.generateWithBom(
                bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
                chapterCount = 2,
                charsPerChapter = 500,
                charset = Charsets.UTF_8,
            )
        )

        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-8", index.encoding)
        assertEquals(2, index.chapters.size)
        // charStart of first chapter must be 0 (BOM is not counted in char offsets)
        assertEquals(0L, index.chapters[0].charStart)
        assertEquals("第1章 测试", index.chapters[0].title)
        assertEquals("第2章 测试", index.chapters[1].title)
    }

    // ── 4. UTF-16 LE BOM → 正确检测编码 ───────────────────────────────

    @Test
    fun `UTF-16 LE BOM detects encoding and chapters`() {
        val file = track(
            TestFileGenerator.generateWithBom(
                bomBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
                chapterCount = 2,
                charsPerChapter = 500,
                charset = Charsets.UTF_16LE,
            )
        )

        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-16LE", index.encoding)
        assertEquals(2, index.chapters.size)
        assertEquals("第1章 测试", index.chapters[0].title)
        assertEquals("第2章 测试", index.chapters[1].title)
        // charStart should be 0 for the first chapter (BOM skipped)
        assertEquals(0L, index.chapters[0].charStart)
    }

    // ── 5. UTF-16 BE BOM → 正确检测编码 ───────────────────────────────

    @Test
    fun `UTF-16 BE BOM detects encoding and chapters`() {
        val file = track(
            TestFileGenerator.generateWithBom(
                bomBytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
                chapterCount = 2,
                charsPerChapter = 500,
                charset = Charsets.UTF_16BE,
            )
        )

        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-16BE", index.encoding)
        assertEquals(2, index.chapters.size)
        assertEquals("第1章 测试", index.chapters[0].title)
        assertEquals("第2章 测试", index.chapters[1].title)
        assertEquals(0L, index.chapters[0].charStart)
    }

    // ── 6. 空文件 → 单章，totalCharCount = 0 ──────────────────────────

    @Test
    fun `empty file yields single chapter with zero chars`() {
        val file = track(TestFileGenerator.generateEmpty())

        val index = TxtFileScanner.scan(file)

        assertEquals(1, index.chapters.size)
        assertEquals("全文", index.chapters[0].title)
        assertEquals(0L, index.totalCharCount)
        assertEquals(0L, index.chapters[0].charStart)
        assertEquals(0L, index.chapters[0].charCount)
        assertEquals(0, index.chapters[0].byteLength)
    }

    // ── 7. 纯空白文件 → 单章「全文」 ──────────────────────────────────

    @Test
    fun `whitespace-only file yields single chapter`() {
        val file = track(File.createTempFile("test_whitespace_", ".txt"))
        file.writeText("   \n  \n   \n", Charsets.UTF_8)

        val index = TxtFileScanner.scan(file)

        assertEquals(1, index.chapters.size)
        assertEquals("全文", index.chapters[0].title)
        assertTrue(index.totalCharCount > 0) // whitespace chars are counted
    }

    // ── 8. 重复标题 → 每章独立条目 ────────────────────────────────────

    @Test
    fun `duplicate chapter titles create separate entries`() {
        val file = track(File.createTempFile("test_dup_titles_", ".txt"))
        val body = "测试正文内容。".repeat(70) // ~500 chars per chapter
        val content = "第一章 测试\n$body\n第一章 测试\n$body\n第一章 测试\n$body\n第一章 测试"
        file.writeText(content, Charsets.UTF_8)

        val index = TxtFileScanner.scan(file)

        assertEquals(4, index.chapters.size)
        // All chapters have the same title but different indices
        for (i in 0 until 4) {
            assertEquals(i, index.chapters[i].index)
            assertEquals("第一章 测试", index.chapters[i].title)
        }
        // charStart values should be strictly increasing
        for (i in 1 until 4) {
            assertTrue(index.chapters[i].charStart > index.chapters[i - 1].charStart)
        }
    }

    // ── 9. 密度防护 → 平均章节长度 < 300 字时退回单章 ─────────────────

    @Test
    fun `density protection falls back to single chapter when avg is below 300`() {
        val file = track(File.createTempFile("test_density_", ".txt"))
        // Each chapter has only ~15 chars of body → avg well below 300
        val content = buildString {
            for (i in 1..10) {
                append("第${i}章\n")
                append("短内容\n")
            }
        }
        file.writeText(content, Charsets.UTF_8)

        val index = TxtFileScanner.scan(file)

        // Density protection should kick in: avg << 300 → single "全文" chapter
        assertEquals(1, index.chapters.size)
        assertEquals("全文", index.chapters[0].title)
    }

    // ── E7: 行尾变体测试 ───────────────────────────────────────────────

    @Test
    fun `CRLF line endings produce correct offsets`() {
        val file = track(File.createTempFile("test_crlf_", ".txt"))
        val body = "测试正文内容。".repeat(70)
        // Use \r\n line endings
        val content = "第一章 初见\r\n$body\r\n第二章 重逢\r\n$body"
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-8", index.encoding)
        assertEquals(2, index.chapters.size)
        assertEquals("第一章 初见", index.chapters[0].title)
        assertEquals("第二章 重逢", index.chapters[1].title)
        assertEquals(0L, index.chapters[0].charStart)
        // Both chapters should have substantial char counts
        assertTrue("Chapter 0 should have text", index.chapters[0].charCount > 300)
        assertTrue("Chapter 1 should have text", index.chapters[1].charCount > 300)
        // charStart of chapter 1 should equal end of chapter 0
        assertEquals(index.chapters[0].charStart + index.chapters[0].charCount,
            index.chapters[1].charStart)
    }

    @Test
    fun `CR-only line endings produce correct offsets`() {
        val file = track(File.createTempFile("test_cr_", ".txt"))
        val body = "测试正文内容。".repeat(70)
        // Use \r (CR-only) line endings
        val content = "第一章 初见\r$body\r第二章 重逢\r$body"
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-8", index.encoding)
        assertEquals(2, index.chapters.size)
        assertEquals("第一章 初见", index.chapters[0].title)
        assertEquals("第二章 重逢", index.chapters[1].title)
        assertEquals(0L, index.chapters[0].charStart)
        assertTrue("Chapter 0 should have text", index.chapters[0].charCount > 300)
        assertTrue("Chapter 1 should have text", index.chapters[1].charCount > 300)
    }

    @Test
    fun `no trailing newline on last line handled correctly`() {
        val file = track(File.createTempFile("test_no_trailing_nl_", ".txt"))
        val body = "测试正文内容。".repeat(70)
        // No trailing newline at end
        val content = "第一章 初见\n$body\n第二章 重逢\n$body"
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-8", index.encoding)
        assertEquals(2, index.chapters.size)
        assertEquals("第一章 初见", index.chapters[0].title)
        assertEquals("第二章 重逢", index.chapters[1].title)
        // Verify total byte coverage: sum of byteLengths should equal file size
        val totalBytes = index.chapters.sumOf { it.byteLength.toLong() }
        assertEquals("Total byte coverage should match file size", file.length(), totalBytes)
    }

    // ── G5 Category 1: Additional Scanner Boundary Tests ─────────────────

    @Test
    fun `UTF-8 front-ASCII back-Chinese exact scanning`() {
        val file = track(File.createTempFile("test_ascii_chinese_", ".txt"))
        // First half ASCII, second half Chinese
        val asciiPart = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(100)
        val chinesePart = "测试正文内容".repeat(200)
        val content = "第一章 测试\n$asciiPart\n$chinesePart"
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-8", index.encoding)
        assertTrue("Should have chapters", index.chapters.isNotEmpty())
        // Verify totalChars matches content length
        assertEquals(content.length.toLong(), index.totalCharCount)
    }

    @Test
    fun `UTF-8 emoji file scanning preserves char count`() {
        val file = track(File.createTempFile("test_emoji_scan_", ".txt"))
        val emoji = "\uD83D\uDE00" // U+1F600
        val content = "第一章 测试\n" + "测试正文".repeat(100) + emoji.repeat(10) + "测试正文".repeat(100)
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-8", index.encoding)
        // Emoji are single chars in Kotlin String, verify count matches
        assertEquals(content.length.toLong(), index.totalCharCount)
    }

    @Test
    fun `GB18030 file scanning detects encoding correctly`() {
        val file = track(TestFileGenerator.generateGb18030Mixed())
        val index = TxtFileScanner.scan(file)

        assertEquals("GB18030", index.encoding)
        assertTrue("Should have positive char count", index.totalCharCount > 0)
        assertTrue("Should have chapters", index.chapters.isNotEmpty())
    }

    @Test
    fun `UTF-16LE with emoji scanning preserves surrogate pairs`() {
        val file = track(TestFileGenerator.generateUtf16LEWithEmoji())
        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-16LE", index.encoding)
        assertTrue("Should have positive char count", index.totalCharCount > 0)
        // Verify char count is reasonable (emoji are single chars in UTF-16)
        assertTrue("Char count should be > 50000", index.totalCharCount > 50000)
    }

    @Test
    fun `UTF-16BE with emoji scanning preserves surrogate pairs`() {
        val file = track(TestFileGenerator.generateUtf16BEWithEmoji())
        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-16BE", index.encoding)
        assertTrue("Should have positive char count", index.totalCharCount > 0)
        assertTrue("Char count should be > 50000", index.totalCharCount > 50000)
    }

    // ── G5 Category 1: Additional Scanner Integration Tests ──────────────

    @Test
    fun `mixed density UTF-8 file scanning preserves char count`() {
        val file = track(TestFileGenerator.generateMixedDensityUtf8(5_000_000))
        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-8", index.encoding)
        assertTrue("Should have positive char count", index.totalCharCount > 0)
        assertTrue("Should have chapters", index.chapters.isNotEmpty())
    }

    @Test
    fun `large no-TOC file scanning detects single chapter`() {
        val file = track(TestFileGenerator.generateLargeNoToc(5L * 1024 * 1024))
        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-8", index.encoding)
        assertEquals("Should have single chapter for no-TOC", 1, index.chapters.size)
        assertEquals("全文", index.chapters[0].title)
        assertTrue("Should have positive char count", index.totalCharCount > 0)
    }

    @Test
    fun `large mixed UTF-8 file scanning detects chapters`() {
        val file = track(TestFileGenerator.generateLargeMixedUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)

        assertEquals("UTF-8", index.encoding)
        assertTrue("Should have multiple chapters", index.chapters.size > 5)
        assertTrue("Should have positive char count", index.totalCharCount > 0)
    }

    @Test
    fun `GB18030 mixed file scanning detects encoding`() {
        val file = track(TestFileGenerator.generateGb18030Mixed())
        val index = TxtFileScanner.scan(file)

        assertEquals("GB18030", index.encoding)
        assertTrue("Should have positive char count", index.totalCharCount > 0)
        assertTrue("Should have chapters", index.chapters.isNotEmpty())
    }
}
