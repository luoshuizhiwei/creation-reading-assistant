package com.creationreadingassistant.feature.reader.doc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Tests for PlainTextDocument bounded read APIs:
 * - readWindow (small-file and streaming modes)
 * - readWindowAround
 * - unitIndexForOffset
 */
class BoundedReadApiTest {

    private val tempFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    // ── readWindow: small-file mode ──────────────────────────────────

    @Test
    fun `readWindow small file at start`() {
        val text = "第一章 测试\n" + "正文内容测试。".repeat(100)
        val doc = PlainTextDocument(text)
        val window = doc.readWindow(0, 10)
        assertEquals(text.substring(0, 10), window)
    }

    @Test
    fun `readWindow small file at middle`() {
        val text = "第一章 测试\n" + "正文内容测试。".repeat(100)
        val doc = PlainTextDocument(text)
        val window = doc.readWindow(20, 10)
        assertEquals(text.substring(20, 30), window)
    }

    @Test
    fun `readWindow small file at end`() {
        val text = "第一章 测试\n" + "正文内容测试。".repeat(100)
        val doc = PlainTextDocument(text)
        val window = doc.readWindow(text.length - 5, 10)
        assertEquals(text.substring(text.length - 5), window)
    }

    @Test
    fun `readWindow small file clamped to MAX_WINDOW_CHARS`() {
        val text = "A".repeat(200_000)
        val doc = PlainTextDocument(text)
        val window = doc.readWindow(0, 200_000)
        // Should be clamped to MAX_WINDOW_CHARS
        assertTrue("Window should be <= MAX_WINDOW_CHARS", window.length <= PlainTextDocument.MAX_WINDOW_CHARS)
    }

    @Test
    fun `readWindow small file returns empty for out-of-range`() {
        val doc = PlainTextDocument("Hello world")
        assertEquals("", doc.readWindow(100, 10))  // start >= totalChars
        assertEquals("", doc.readWindow(0, 0))     // maxChars <= 0
        // readWindow(-5, 10) coerces start to 0, returns 10 chars
        assertEquals("Hello worl", doc.readWindow(-5, 10))
    }

    // ── readWindow: streaming mode ───────────────────────────────────

    @Test
    fun `readWindow streaming at start matches reference`() {
        val (file, content, index) = createStreamingFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val refDoc = PlainTextDocument(content)

        val window = doc.readWindow(0, 50)
        val expected = refDoc.readWindow(0, 50)
        assertEquals(expected, window)
    }

    @Test
    fun `readWindow streaming at middle matches reference`() {
        val (file, content, index) = createStreamingFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val refDoc = PlainTextDocument(content)

        val midOffset = content.length / 2
        val window = doc.readWindow(midOffset, 100)
        val expected = refDoc.readWindow(midOffset, 100)
        assertEquals(expected, window)
    }

    @Test
    fun `readWindow streaming at end matches reference`() {
        val (file, content, index) = createStreamingFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val refDoc = PlainTextDocument(content)

        val endOffset = content.length - 20
        val window = doc.readWindow(endOffset, 50)
        val expected = refDoc.readWindow(endOffset, 50)
        assertEquals(expected, window)
    }

    @Test
    fun `readWindow streaming clamped to MAX_WINDOW_CHARS`() {
        val (file, _, index) = createStreamingFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val window = doc.readWindow(0, 200_000)
        assertTrue("Window should be <= MAX_WINDOW_CHARS", window.length <= PlainTextDocument.MAX_WINDOW_CHARS)
    }

    // ── readWindow: encoding tests ───────────────────────────────────

    @Test
    fun `readWindow streaming UTF-8 encoding boundary`() {
        val (file, content, index) = createStreamingFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val refDoc = PlainTextDocument(content)

        // Read at various positions to exercise encoding boundary handling
        for (offset in listOf(0, 10, 50, 100, content.length / 3)) {
            val window = doc.readWindow(offset, 30)
            val expected = content.substring(offset, (offset + 30).coerceAtMost(content.length))
            assertEquals("UTF-8 window at offset $offset", expected, window)
        }
    }

    @Test
    fun `readWindow streaming GB18030 encoding`() {
        val file = TestFileGenerator.generateGb18030(chapterCount = 3, charsPerChapter = 500)
        tempFiles.add(file)
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)

        // Read a window and verify it's non-empty and properly decoded
        val window = doc.readWindow(0, 50)
        assertTrue("GB18030 window should not be empty", window.isNotEmpty())
        assertTrue("GB18030 window should contain Chinese chars",
            window.any { it.code > 0x4E00 })
    }

    @Test
    fun `readWindow streaming UTF-16LE encoding`() {
        val file = TestFileGenerator.generateUtf16LE(chapterCount = 3, charsPerChapter = 500)
        tempFiles.add(file)
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)

        val window = doc.readWindow(0, 50)
        assertTrue("UTF-16LE window should not be empty", window.isNotEmpty())
    }

    // ── readWindowAround ─────────────────────────────────────────────

    @Test
    fun `readWindowAround centered correctly`() {
        val text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val doc = PlainTextDocument(text)
        val window = doc.readWindowAround(10, before = 3, after = 5)
        // Should read from offset 7 (10-3) for 8 chars (3+5)
        assertEquals("HIJKLMNO", window)
    }

    @Test
    fun `readWindowAround clamped at start`() {
        val text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val doc = PlainTextDocument(text)
        val window = doc.readWindowAround(2, before = 10, after = 5)
        // Should read from offset 0 (clamped) for 15 chars (10+5)
        assertEquals(text.substring(0, 15), window)
    }

    @Test
    fun `readWindowAround clamped at end`() {
        val text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val doc = PlainTextDocument(text)
        val window = doc.readWindowAround(24, before = 2, after = 10)
        // Should read from offset 22 for min(12, MAX_WINDOW_CHARS) chars
        assertEquals(text.substring(22), window)
    }

    @Test
    fun `readWindowAround clamped to MAX_WINDOW_CHARS`() {
        val text = "A".repeat(200_000)
        val doc = PlainTextDocument(text)
        val window = doc.readWindowAround(100_000, before = 80_000, after = 80_000)
        assertTrue("Window should be <= MAX_WINDOW_CHARS", window.length <= PlainTextDocument.MAX_WINDOW_CHARS)
    }

    // ── unitIndexForOffset ───────────────────────────────────────────

    @Test
    fun `unitIndexForOffset empty list returns -1`() {
        val doc = PlainTextDocument("test")
        // readingUnits defaults to empty
        assertEquals(-1, doc.unitIndexForOffset(0))
    }

    @Test
    fun `unitIndexForOffset single unit`() {
        val doc = PlainTextDocument("test")
        doc.readingUnits = listOf(
            ReadingUnit(unitIndex = 0, chapterIndex = 0, title = "全文", charStart = 0, charCount = 4)
        )
        assertEquals(0, doc.unitIndexForOffset(0))
        assertEquals(0, doc.unitIndexForOffset(3))
        assertEquals(-1, doc.unitIndexForOffset(4)) // past end
    }

    @Test
    fun `unitIndexForOffset multiple units`() {
        val doc = PlainTextDocument("test")
        doc.readingUnits = listOf(
            ReadingUnit(unitIndex = 0, chapterIndex = 0, title = "章一", charStart = 0, charCount = 10),
            ReadingUnit(unitIndex = 1, chapterIndex = 1, title = "章二", charStart = 10, charCount = 10),
            ReadingUnit(unitIndex = 2, chapterIndex = 2, title = "章三", charStart = 20, charCount = 10),
        )
        assertEquals(0, doc.unitIndexForOffset(0))
        assertEquals(0, doc.unitIndexForOffset(5))
        assertEquals(0, doc.unitIndexForOffset(9))
        assertEquals(1, doc.unitIndexForOffset(10))
        assertEquals(1, doc.unitIndexForOffset(15))
        assertEquals(2, doc.unitIndexForOffset(20))
        assertEquals(2, doc.unitIndexForOffset(29))
        assertEquals(-1, doc.unitIndexForOffset(30)) // past end
    }

    @Test
    fun `unitIndexForOffset boundary offsets`() {
        val doc = PlainTextDocument("test")
        doc.readingUnits = listOf(
            ReadingUnit(unitIndex = 0, chapterIndex = 0, title = "章一", charStart = 0, charCount = 5),
            ReadingUnit(unitIndex = 1, chapterIndex = 1, title = "章二", charStart = 5, charCount = 5),
        )
        // Boundary: exactly at start of unit 0
        assertEquals(0, doc.unitIndexForOffset(0))
        // Boundary: exactly at start of unit 1
        assertEquals(1, doc.unitIndexForOffset(5))
        // Boundary: last char of unit 0
        assertEquals(0, doc.unitIndexForOffset(4))
        // Boundary: last char of unit 1
        assertEquals(1, doc.unitIndexForOffset(9))
        // Past end
        assertEquals(-1, doc.unitIndexForOffset(10))
    }

    @Test
    fun `unitIndexForOffset negative offset returns -1`() {
        val doc = PlainTextDocument("test")
        doc.readingUnits = listOf(
            ReadingUnit(unitIndex = 0, chapterIndex = 0, title = "全文", charStart = 0, charCount = 4)
        )
        // Negative offset: binary search won't find any unit with charStart <= -1
        // since all charStart >= 0. But the algorithm starts result at -1 and only
        // updates when charStart <= offset. Since 0 > -1, result stays -1.
        assertEquals(-1, doc.unitIndexForOffset(-1))
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun createStreamingFile(): Triple<File, String, TxtFileIndex> {
        val file = File.createTempFile("test_bounded_", ".txt")
        tempFiles.add(file)
        val body = "测试正文内容。".repeat(70)
        val lines = mutableListOf<String>()
        for (i in 1..3) {
            lines.add("第${i}章 测试")
            lines.add(body)
        }
        val content = lines.joinToString("\n")
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }
        val index = TxtFileScanner.scan(file)
        return Triple(file, content, index)
    }

    // ── G5 Category 3: Bounded Read API Encoding Tests ─────────────────

    @Test
    fun `readWindow UTF-8 multibyte char at window end not split`() {
        // Create a file with Chinese text where multi-byte chars might be at window boundaries
        val file = File.createTempFile("test_utf8_boundary_", ".txt")
        tempFiles.add(file)
        val content = "测试正文内容。".repeat(1000)
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val refDoc = PlainTextDocument(content)

        // Read windows at various positions and verify no partial chars
        for (offset in listOf(0, 10, 50, 100, 500, 1000, 5000)) {
            val window = doc.readWindow(offset, 30)
            val expected = refDoc.readWindow(offset, 30)
            assertEquals("UTF-8 window at offset $offset should match reference", expected, window)
            // Verify no replacement characters
            val replacementCount = window.count { it == '\uFFFD' }
            assertEquals("No replacement chars at offset $offset", 0, replacementCount)
        }
    }

    @Test
    fun `readWindow GB18030 4-byte boundary no garbled`() {
        val file = TestFileGenerator.generateGb18030(chapterCount = 3, charsPerChapter = 2000)
        tempFiles.add(file)
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)

        // Read at multiple positions and verify no garbled chars
        for (offset in listOf(0, 100, 500, 1000, 2000, 3000)) {
            val window = doc.readWindow(offset, 200)
            assertTrue("GB18030 window at $offset should not be empty", window.isNotEmpty())
            val replacementCount = window.count { it == '\uFFFD' }
            assertEquals("GB18030 no garbled at offset $offset", 0, replacementCount)
        }
    }

    @Test
    fun `readWindow UTF-16 no replacement chars at multiple positions`() {
        val file = TestFileGenerator.generateUtf16LEWithEmoji()
        tempFiles.add(file)
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)

        // Read at multiple positions including near emoji locations
        for (offset in listOf(0, 100, 500, 1000, 49980, 49990, 50000, 50010)) {
            if (offset >= doc.totalChars) continue
            val window = doc.readWindow(offset, 100)
            if (window.isNotEmpty()) {
                // Verify no replacement characters (garbled text)
                val replacementCount = window.count { it == '\uFFFD' }
                assertEquals(
                    "UTF-16LE window at $offset should have no replacement chars",
                    0, replacementCount
                )
            }
        }
    }

    @Test
    fun `readWindowAround clamping at file start and end`() {
        val (file, content, index) = createStreamingFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val refDoc = PlainTextDocument(content)

        // At file start (before=1000)
        val startWindow = doc.readWindowAround(100, before = 1000, after = 100)
        val expectedStart = refDoc.readWindowAround(100, before = 1000, after = 100)
        assertEquals("readWindowAround at start should clamp correctly", expectedStart, startWindow)

        // At file end (after=1000)
        val endOffset = content.length - 50
        val endWindow = doc.readWindowAround(endOffset, before = 100, after = 1000)
        val expectedEnd = refDoc.readWindowAround(endOffset, before = 100, after = 1000)
        assertEquals("readWindowAround at end should clamp correctly", expectedEnd, endWindow)
    }

    @Test
    fun `readWindow MAX_WINDOW_CHARS cap enforced`() {
        val (file, _, index) = createStreamingFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)

        // Request 200K chars, verify returns <= 100K
        val window = doc.readWindow(0, 200_000)
        assertTrue(
            "Window should be <= MAX_WINDOW_CHARS (100K), got ${window.length}",
            window.length <= PlainTextDocument.MAX_WINDOW_CHARS
        )

        // Also verify at non-zero offset
        val window2 = doc.readWindow(1000, 200_000)
        assertTrue(
            "Window at offset 1000 should be <= MAX_WINDOW_CHARS",
            window2.length <= PlainTextDocument.MAX_WINDOW_CHARS
        )
    }

    // ── G5 Category 3: Additional Bounded Read Tests ─────────────────────

    @Test
    fun `readWindow streaming UTF-16BE encoding boundary`() {
        val file = TestFileGenerator.generateUtf16BE(chapterCount = 3, charsPerChapter = 500)
        tempFiles.add(file)
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)

        // Read at multiple positions and verify no garbled chars
        for (offset in listOf(0, 50, 100, 200, 500)) {
            val window = doc.readWindow(offset, 100)
            assertTrue("UTF-16BE window at $offset should not be empty", window.isNotEmpty())
            // Verify no orphaned surrogates
            if (window.isNotEmpty()) {
                assertTrue("UTF-16BE should not end with high surrogate at $offset",
                    !window.last().isHighSurrogate())
            }
        }
    }

    @Test
    fun `readWindowAround streaming mode matches small-file mode`() {
        // Create a file that can be tested in both modes
        val text = "第一章 测试\n" + "测试正文内容。".repeat(200)
        val refDoc = PlainTextDocument(text)

        val file = File.createTempFile("test_window_around_", ".txt")
        tempFiles.add(file)
        RandomAccessFile(file, "rw").use { it.write(text.toByteArray(Charsets.UTF_8)) }
        val index = TxtFileScanner.scan(file)
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)

        // Compare readWindowAround at multiple positions
        for (offset in listOf(0, 50, 100, 500, 1000, text.length - 50)) {
            val refWindow = refDoc.readWindowAround(offset, 100, 100)
            val streamingWindow = streamingDoc.readWindowAround(offset, 100, 100)
            assertEquals(
                "readWindowAround at offset $offset should match between modes",
                refWindow, streamingWindow
            )
        }
    }

    @Test
    fun `readWindow large file at chapter boundaries`() {
        val file = TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 20)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Read window at each chapter start
            for (chapter in index.chapters.take(5)) {
                val window = doc.readWindow(chapter.charStart.toInt(), 100)
                assertTrue(
                    "Window at chapter ${chapter.index} should not be empty",
                    window.isNotEmpty()
                )
                // Window should start with chapter title
                assertTrue(
                    "Window at chapter ${chapter.index} should start with title",
                    window.startsWith(chapter.title)
                )
            }
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 3: Additional Encoding Tests ─────────────────────────

    @Test
    fun `readWindow GB18030 at multiple positions no garbled`() {
        val file = TestFileGenerator.generateGb18030(chapterCount = 5, charsPerChapter = 2000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Read at many positions across the file
            for (offset in listOf(0, 50, 100, 500, 1000, 2000, 3000, 5000, 8000)) {
                if (offset >= doc.totalChars) continue
                val window = doc.readWindow(offset, 100)
                assertTrue("GB18030 window at $offset should not be empty", window.isNotEmpty())
                val replacementCount = window.count { it == '\uFFFD' }
                assertEquals("GB18030 no garbled at offset $offset", 0, replacementCount)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readWindow UTF-16LE at multiple positions no orphaned surrogates`() {
        val file = TestFileGenerator.generateUtf16LE(chapterCount = 5, charsPerChapter = 2000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Read at many positions across the file
            for (offset in listOf(0, 50, 100, 500, 1000, 2000, 3000, 5000, 8000)) {
                if (offset >= doc.totalChars) continue
                val window = doc.readWindow(offset, 100)
                if (window.isNotEmpty()) {
                    assertTrue("UTF-16LE should not end with high surrogate at $offset",
                        !window.last().isHighSurrogate())
                    assertTrue("UTF-16LE should not start with low surrogate at $offset",
                        !window.first().isLowSurrogate())
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readWindow UTF-16BE at multiple positions no orphaned surrogates`() {
        val file = TestFileGenerator.generateUtf16BE(chapterCount = 5, charsPerChapter = 2000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Read at many positions across the file
            for (offset in listOf(0, 50, 100, 500, 1000, 2000, 3000, 5000, 8000)) {
                if (offset >= doc.totalChars) continue
                val window = doc.readWindow(offset, 100)
                if (window.isNotEmpty()) {
                    assertTrue("UTF-16BE should not end with high surrogate at $offset",
                        !window.last().isHighSurrogate())
                    assertTrue("UTF-16BE should not start with low surrogate at $offset",
                        !window.first().isLowSurrogate())
                }
            }
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 3: Additional Window Tests ───────────────────────────

    @Test
    fun `readWindow mixed density UTF-8 at multiple positions`() {
        val file = TestFileGenerator.generateMixedDensityUtf8(5_000_000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Read at many positions across the file
            for (offset in listOf(0, 100, 500, 1000, 5000, 10000, 50000, 100000)) {
                if (offset >= doc.totalChars) continue
                val window = doc.readWindow(offset, 100)
                assertTrue("Window at $offset should not be empty", window.isNotEmpty())
                val replacementCount = window.count { it == '\uFFFD' }
                assertEquals("No replacement chars at offset $offset", 0, replacementCount)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readWindow emoji file at multiple positions no garbled`() {
        val file = TestFileGenerator.generateEmojiAtBoundaries()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Read at positions near emoji boundary
            for (offset in listOf(0, 100, 500, 1000, 49980, 49990, 50000, 50010)) {
                if (offset >= doc.totalChars) continue
                val window = doc.readWindow(offset, 100)
                assertTrue("Window at $offset should not be empty", window.isNotEmpty())
                val replacementCount = window.count { it == '\uFFFD' }
                assertEquals("No replacement chars at offset $offset", 0, replacementCount)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readWindowAround large file at multiple positions`() {
        val file = TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Test readWindowAround at multiple positions
            for (offset in listOf(0, 1000, 10000, 50000, 100000, doc.totalChars - 100)) {
                if (offset < 0 || offset >= doc.totalChars) continue
                val window = doc.readWindowAround(offset, 100, 100)
                assertTrue("Window at $offset should not be empty", window.isNotEmpty())
            }
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 3: Additional Integration Tests ──────────────────────

    @Test
    fun `readWindow GB18030 mixed file at multiple positions`() {
        val file = TestFileGenerator.generateGb18030Mixed()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Read at many positions across the file
            for (offset in listOf(0, 100, 500, 1000, 5000, 10000, 20000)) {
                if (offset >= doc.totalChars) continue
                val window = doc.readWindow(offset, 100)
                assertTrue("GB18030 window at $offset should not be empty", window.isNotEmpty())
                val replacementCount = window.count { it == '\uFFFD' }
                assertEquals("GB18030 no garbled at offset $offset", 0, replacementCount)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readWindow UTF-16LE with emoji no replacement chars`() {
        val file = TestFileGenerator.generateUtf16LEWithEmoji()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Read at positions near emoji boundary
            for (offset in listOf(0, 100, 500, 1000, 49980, 49990, 50000, 50010)) {
                if (offset >= doc.totalChars) continue
                val window = doc.readWindow(offset, 100)
                if (window.isNotEmpty()) {
                    val replacementCount = window.count { it == '\uFFFD' }
                    assertEquals(
                        "UTF-16LE window at $offset should have no replacement chars",
                        0, replacementCount
                    )
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readWindow UTF-16BE with emoji no replacement chars`() {
        val file = TestFileGenerator.generateUtf16BEWithEmoji()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Read at positions near emoji boundary
            for (offset in listOf(0, 100, 500, 1000, 49980, 49990, 50000, 50010)) {
                if (offset >= doc.totalChars) continue
                val window = doc.readWindow(offset, 100)
                if (window.isNotEmpty()) {
                    val replacementCount = window.count { it == '\uFFFD' }
                    assertEquals(
                        "UTF-16BE window at $offset should have no replacement chars",
                        0, replacementCount
                    )
                }
            }
        } finally {
            file.delete()
        }
    }
}
