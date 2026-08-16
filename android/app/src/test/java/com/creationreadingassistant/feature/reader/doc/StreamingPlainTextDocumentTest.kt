package com.creationreadingassistant.feature.reader.doc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class StreamingPlainTextDocumentTest {

    private val tempFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    /**
     * Build a multi-chapter text with explicit `\n` line endings and write to a temp file
     * using [RandomAccessFile] to guarantee raw byte output (no platform line-ending conversion).
     * Returns the file, the canonical content string, and the scanner index.
     */
    private fun createMultiChapterFile(chapterCount: Int = 3): Triple<File, String, TxtFileIndex> {
        val file = File.createTempFile("test_streaming_", ".txt")
        tempFiles.add(file)
        val body = "测试正文内容。".repeat(70) // ~490 chars per chapter
        val lines = mutableListOf<String>()
        for (i in 1..chapterCount) {
            lines.add("第${i}章 测试")
            lines.add(body)
        }
        val content = lines.joinToString("\n")
        // Write raw bytes via RandomAccessFile to bypass any platform line-ending conversion
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file)
        return Triple(file, content, index)
    }

    // ── 1. text(ci) 返回正确章文本 ────────────────────────────────────

    @Test
    fun `text returns correct chapter text for each chapter`() {
        val (file, content, index) = createMultiChapterFile()
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)
        val referenceDoc = PlainTextDocument(content)

        assertEquals(referenceDoc.chapters.size, streamingDoc.chapters.size)
        for (i in streamingDoc.chapters.indices) {
            assertEquals(
                "Chapter $i text should match reference",
                referenceDoc.text(i),
                streamingDoc.text(i),
            )
        }
    }

    // ── 2. 偏移可逆性 — text(ci)[k] == fullText[charStart + k] ────────

    @Test
    fun `chapter text offsets are consistent with chapter start offsets`() {
        val (file, content, index) = createMultiChapterFile()
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)
        val referenceDoc = PlainTextDocument(content)

        for (ci in streamingDoc.chapters.indices) {
            val chapterText = streamingDoc.text(ci)
            val refText = referenceDoc.text(ci)
            assertEquals("Chapter $ci text mismatch", refText, chapterText)

            // Verify the reference text matched the full text at chapter offset
            val refChapter = referenceDoc.chapters[ci]
            for (k in refText.indices) {
                assertEquals(
                    "Offset reversibility: chapter $ci, pos $k",
                    content[refChapter.startOffset + k],
                    refText[k],
                )
            }
        }
    }

    // ── 3. totalChars 正确 ────────────────────────────────────────────

    @Test
    fun `totalChars matches file content length`() {
        val (file, content, index) = createMultiChapterFile()
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)

        // totalChars from streaming doc should match the reference doc
        val referenceDoc = PlainTextDocument(content)
        assertEquals(referenceDoc.totalChars, streamingDoc.totalChars)
        // And should be positive
        assertTrue("totalChars should be positive", streamingDoc.totalChars > 0)
    }

    // ── 4. chapters 列表正确 ──────────────────────────────────────────

    @Test
    fun `chapters list matches reference document`() {
        val (file, content, index) = createMultiChapterFile()
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)
        val referenceDoc = PlainTextDocument(content)

        assertEquals(referenceDoc.chapters.size, streamingDoc.chapters.size)

        for (i in streamingDoc.chapters.indices) {
            val sc = streamingDoc.chapters[i]
            val rc = referenceDoc.chapters[i]
            assertEquals("Chapter $i title", rc.title, sc.title)
            assertEquals("Chapter $i startOffset", rc.startOffset, sc.startOffset)
            assertEquals("Chapter $i charCount", rc.charCount, sc.charCount)
        }
    }

    // ── 5. 多次读取同一章结果一致 ─────────────────────────────────────

    @Test
    fun `reading same chapter multiple times returns identical results`() {
        val (file, _, index) = createMultiChapterFile()
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)

        for (ci in streamingDoc.chapters.indices) {
            val first = streamingDoc.text(ci)
            val second = streamingDoc.text(ci)
            val third = streamingDoc.text(ci)
            assertEquals("Chapter $ci: first and second read differ", first, second)
            assertEquals("Chapter $ci: second and third read differ", second, third)
            assertTrue("Chapter $ci text should not be empty", first.isNotEmpty())
        }
    }

    // ── 6. blocks(ci) 返回正确段落 ────────────────────────────────────

    @Test
    fun `blocks returns correct paragraph structure`() {
        val (file, content, index) = createMultiChapterFile()
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)
        val referenceDoc = PlainTextDocument(content)

        for (ci in streamingDoc.chapters.indices) {
            val streamingBlocks = streamingDoc.blocks(ci)
            val referenceBlocks = referenceDoc.blocks(ci)

            assertEquals(
                "Chapter $ci: block count should match",
                referenceBlocks.size,
                streamingBlocks.size,
            )

            for (bi in streamingBlocks.indices) {
                val sb = streamingBlocks[bi]
                val rb = referenceBlocks[bi]
                assertNotNull("Chapter $ci block $bi should not be null", sb)
                if (sb is DocBlock.Text && rb is DocBlock.Text) {
                    assertEquals("Chapter $ci block $bi text", rb.text, sb.text)
                    assertEquals("Chapter $ci block $bi isHeading", rb.isHeading, sb.isHeading)
                }
            }
        }

        // Verify the invariant: text(ci) == blocks(ci) joined
        for (ci in streamingDoc.chapters.indices) {
            val joined = streamingDoc.blocks(ci)
                .filterIsInstance<DocBlock.Text>()
                .joinToString("\n") { it.text }
            assertEquals(
                "Chapter $ci: text() must equal blocks() joined",
                streamingDoc.text(ci),
                joined,
            )
        }
    }

    // ── E2: 50MB 多章节文件流式读取 ────────────────────────────────────

    @Test
    fun `50MB multi-chapter file scans and reads correctly`() {
        // NOTE: 目标 50MB，生成可能需要 10-30 秒
        val file = TestFileGenerator.generateLargeUtf8(50L * 1024 * 1024, 100)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // 验证章节数
            assertTrue("Should have ~100 chapters, got ${index.chapters.size}",
                index.chapters.size in 90..110)

            // 验证首章、中间章、末章可读
            val firstText = doc.text(0)
            val midText = doc.text(index.chapters.size / 2)
            val lastText = doc.text(index.chapters.size - 1)
            assertTrue("First chapter not empty", firstText.isNotEmpty())
            assertTrue("Mid chapter not empty", midText.isNotEmpty())
            assertTrue("Last chapter not empty", lastText.isNotEmpty())

            // 验证 totalChars 合理（50MB 中文 UTF-8 ≈ 16M+ 字符）
            assertTrue("Total chars should be > 10M for 50MB file, got ${doc.totalChars}",
                doc.totalChars > 10_000_000)
        } finally {
            file.delete()
        }
    }

    // ── E3: 50MB 无目录文件切分为有界读取单元 ──────────────────────────

    @Test
    fun `50MB no-TOC file splits into bounded reading units`() {
        val file = TestFileGenerator.generateLargeNoToc(50L * 1024 * 1024)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // 应该只有 1 章"全文"
            assertEquals(1, index.chapters.size)

            // ReadingUnitBuilder 应将其切分为多个虚拟单元
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
            assertTrue("Should have many reading units, got ${units.size}", units.size > 100)
            // Units are split at line boundaries near MAX_UNIT_CHARS intervals.
            // Each unit may slightly exceed MAX_UNIT_CHARS due to line boundary
            // alignment, but must never exceed MAX_UNIT_CHARS + max line length.
            assertTrue("Each unit should be approximately <= MAX_UNIT_CHARS",
                units.all { it.charCount <= ReadingUnitBuilder.MAX_UNIT_CHARS + 1000 })

            // 读取中间单元
            val midUnit = units[units.size / 2]
            val text = doc.readUnit(midUnit)
            assertTrue("Mid unit text not empty", text.isNotEmpty())
            // Checkpoint-aligned byte boundaries must decode to EXACTLY charCount characters
            assertEquals(
                "Unit ${midUnit.unitIndex} decoded length must exactly match charCount",
                midUnit.charCount, text.length
            )

            // Verify ALL units: each unit's decoded text length == charCount,
            // and concatenated text has correct total length
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    // ── E4: GB18030 多章节流式读取 ─────────────────────────────────────

    @Test
    fun `GB18030 multi-chapter streaming read`() {
        val file = TestFileGenerator.generateGb18030(chapterCount = 10, charsPerChapter = 5000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("GB18030", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)

            // 验证每章可读
            for (ci in 0 until index.chapters.size.coerceAtLeast(1)) {
                val text = doc.text(ci)
                assertTrue("Chapter $ci should have text", text.isNotEmpty())
            }

            // 验证 totalChars 合理
            assertTrue("Total chars should be positive", doc.totalChars > 0)

            // 验证 ReadingUnit 精确读取
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
            assertTrue("Should have reading units", units.isNotEmpty())
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertTrue("Unit ${unit.unitIndex} text not empty", unitText.isNotEmpty())
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
            }
        } finally {
            file.delete()
        }
    }

    // ── E5: UTF-16LE 流式读取 ──────────────────────────────────────────

    @Test
    fun `UTF-16LE multi-chapter streaming read`() {
        val file = TestFileGenerator.generateUtf16LE(chapterCount = 5, charsPerChapter = 3000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("UTF-16LE", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)

            // 验证每章可读
            for (ci in 0 until index.chapters.size) {
                val text = doc.text(ci)
                assertTrue("Chapter $ci should have text", text.isNotEmpty())
            }

            // 验证 ReadingUnit 精确读取
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertTrue("Unit ${unit.unitIndex} text not empty", unitText.isNotEmpty())
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
            }
        } finally {
            file.delete()
        }
    }

    // ── E5b: UTF-16BE 流式读取 ─────────────────────────────────────────

    @Test
    fun `UTF-16BE multi-chapter streaming read`() {
        val file = TestFileGenerator.generateUtf16BE(chapterCount = 5, charsPerChapter = 3000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("UTF-16BE", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)

            // 验证每章可读
            for (ci in 0 until index.chapters.size) {
                val text = doc.text(ci)
                assertTrue("Chapter $ci should have text", text.isNotEmpty())
            }

            // 验证 ReadingUnit 精确读取
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertTrue("Unit ${unit.unitIndex} text not empty", unitText.isNotEmpty())
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
            }
        } finally {
            file.delete()
        }
    }

    // ── G2: readWindow tests ─────────────────────────────────────────────

    @Test
    fun `readWindow on streaming UTF-8 at start matches canonical substring`() {
        val (file, content, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val window = doc.readWindow(0, 100)
        assertEquals(content.substring(0, 100), window)
    }

    @Test
    fun `readWindow on streaming UTF-8 at middle matches canonical substring`() {
        val (file, content, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val start = content.length / 2
        val len = 200
        val window = doc.readWindow(start, len)
        val expected = content.substring(start, minOf(start + len, content.length))
        assertEquals(expected, window)
    }

    @Test
    fun `readWindow on streaming UTF-8 at end returns remaining text`() {
        val (file, content, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val start = content.length - 50
        val window = doc.readWindow(start, 1000)
        assertEquals(content.substring(start), window)
    }

    @Test
    fun `readWindow on streaming GB18030 has no garbled chars`() {
        val file = TestFileGenerator.generateGb18030(chapterCount = 3, charsPerChapter = 2000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            // Read a window from the middle
            val mid = doc.totalChars / 2
            val window = doc.readWindow(mid, 500)
            assertTrue("Window should not be empty", window.isNotEmpty())
            // Verify no unexpected replacement characters (garbled)
            val replacementCount = window.count { it == '\uFFFD' }
            assertEquals("GB18030 readWindow should have no replacement chars", 0, replacementCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readWindow on streaming UTF-16LE does not split surrogate pairs`() {
        val file = TestFileGenerator.generateUtf16LE(chapterCount = 3, charsPerChapter = 2000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val mid = doc.totalChars / 2
            val window = doc.readWindow(mid, 500)
            assertTrue("Window should not be empty", window.isNotEmpty())
            // Verify no orphaned high surrogate at end
            if (window.isNotEmpty()) {
                assertTrue("Last char should not be a high surrogate",
                    !window.last().isHighSurrogate())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readWindow never returns more than MAX_WINDOW_CHARS`() {
        val (file, _, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val window = doc.readWindow(0, Int.MAX_VALUE)
        assertTrue("Window length should be <= MAX_WINDOW_CHARS",
            window.length <= PlainTextDocument.MAX_WINDOW_CHARS)
    }

    @Test
    fun `readWindowAround returns correct centering and clamping`() {
        val (file, content, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val center = content.length / 2
        val before = 100
        val after = 100
        val window = doc.readWindowAround(center, before, after)
        val expectedStart = maxOf(0, center - before)
        val expectedLen = minOf(before + after, PlainTextDocument.MAX_WINDOW_CHARS)
        val expected = content.substring(expectedStart, minOf(expectedStart + expectedLen, content.length))
        assertEquals(expected, window)
    }

    @Test
    fun `readWindowAround clamps at start of file`() {
        val (file, content, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val window = doc.readWindowAround(50, 200, 100)
        // start = max(0, 50-200) = 0, length = min(300, MAX_WINDOW_CHARS) = 300
        val expected = content.substring(0, minOf(300, content.length))
        assertEquals(expected, window)
    }

    // ── G2: unitIndexForOffset tests ─────────────────────────────────────

    @Test
    fun `unitIndexForOffset returns -1 for empty reading units`() {
        val (file, _, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        // 流式文档构造时预建 readingUnits（单一真相，首帧可用）；显式置空锁定
        // 「空 units → -1」契约，与 BoundedReadApiTest 的 empty-list 用例一致。
        doc.readingUnits = emptyList()
        assertEquals(-1, doc.unitIndexForOffset(0))
    }

    @Test
    fun `streaming document precomputes reading units at construction`() {
        val (file, _, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        // P1-A 单一真相：构造期即构建 units（首帧可用，组合层不再 composition 写回）
        assertTrue(doc.readingUnits.isNotEmpty())
        assertEquals(0, doc.unitIndexForOffset(doc.readingUnits.first().charStart))
    }

    @Test
    fun `unitIndexForOffset returns correct index for valid offset`() {
        val (file, _, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        // Test offset at the start of each unit
        for (unit in units) {
            val idx = doc.unitIndexForOffset(unit.charStart)
            assertEquals("Offset ${unit.charStart} should map to unit ${unit.unitIndex}",
                unit.unitIndex, idx)
        }
    }

    @Test
    fun `unitIndexForOffset returns -1 for offset beyond all units`() {
        val (file, _, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        // Offset beyond the last unit
        val beyondOffset = units.last().charStart + units.last().charCount + 100
        assertEquals(-1, doc.unitIndexForOffset(beyondOffset))
    }

    // ── G5 Category 2: 50MB Exact Verification Tests ─────────────────────

    @Test
    fun `50MB no-TOC mixed UTF-8 exact verification`() {
        val file = TestFileGenerator.generateLargeMixedUtf8(50L * 1024 * 1024, 50)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have many reading units", units.size > 50)

            // Verify first unit text starts with expected prefix
            val firstUnitText = doc.readUnit(units.first())
            assertTrue("First unit should start with chapter title",
                firstUnitText.startsWith("第1章"))

            // Verify ALL units concatenated length == totalChars
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `50MB multi-chapter exact with hash verification`() {
        val file = TestFileGenerator.generateLargeUtf8(50L * 1024 * 1024, 100)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            // Verify chapter count
            assertTrue("Should have ~100 chapters", index.chapters.size in 90..110)

            // Verify ALL units: each unit's decoded text length == charCount
            val concatenated = StringBuilder()
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                concatenated.append(unitText)
            }

            // Verify total length matches
            assertEquals(doc.totalChars.toLong(), concatenated.length.toLong())

            // Verify content hash consistency: read same units twice, compare
            val concatenated2 = StringBuilder()
            for (unit in units) {
                concatenated2.append(doc.readUnit(unit))
            }
            assertEquals("Two reads should produce identical concatenation",
                concatenated.toString(), concatenated2.toString())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `50MB readWindow consistency at multiple positions`() {
        val file = TestFileGenerator.generateLargeUtf8(50L * 1024 * 1024, 100)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            // Build canonical text from all units
            val canonical = StringBuilder()
            for (unit in units) {
                canonical.append(doc.readUnit(unit))
            }

            // Verify readWindow at positions 0%, 25%, 50%, 75%, end
            val positions = listOf(
                0,
                doc.totalChars / 4,
                doc.totalChars / 2,
                doc.totalChars * 3 / 4,
                doc.totalChars - 100
            )

            for (pos in positions) {
                val windowLen = 500
                val window = doc.readWindow(pos, windowLen)
                val expected = canonical.substring(
                    pos.toInt(),
                    minOf(pos.toInt() + windowLen, canonical.length)
                )
                assertEquals(
                    "readWindow at position $pos should match canonical text",
                    expected, window
                )
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `50MB unit byte boundary precision zero tolerance`() {
        val file = TestFileGenerator.generateLargeUtf8(50L * 1024 * 1024, 100)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            // For every unit, verify doc.readUnit(unit).length == unit.charCount
            var mismatchCount = 0
            for (unit in units) {
                val text = doc.readUnit(unit)
                if (text.length != unit.charCount) {
                    mismatchCount++
                }
            }
            assertEquals(
                "All units must have exact charCount match (zero tolerance)",
                0, mismatchCount
            )
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 8: Anchor/Locator Tests ──────────────────────────────

    @Test
    fun `anchor jump UTF-8 readWindowAround contains highlight position`() {
        val file = TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            // Build canonical text
            val canonical = StringBuilder()
            for (unit in units) {
                canonical.append(doc.readUnit(unit))
            }

            // Simulate highlight at known offset (25% into the file)
            val highlightOffset = canonical.length / 4
            val before = 200
            val after = 200
            val window = doc.readWindowAround(highlightOffset, before, after)

            // Verify window contains text around the highlight position
            assertTrue("Window should not be empty", window.isNotEmpty())
            val expectedStart = maxOf(0, highlightOffset - before)
            val expectedEnd = minOf(highlightOffset + after, canonical.length)
            val expected = canonical.substring(expectedStart, expectedEnd)
            assertEquals("readWindowAround should contain highlight position", expected, window)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `anchor jump GB18030 readWindowAround contains highlight position`() {
        val file = TestFileGenerator.generateGb18030(chapterCount = 5, charsPerChapter = 5000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Simulate highlight at middle of file
            val highlightOffset = doc.totalChars / 2
            val before = 100
            val after = 100
            val window = doc.readWindowAround(highlightOffset, before, after)

            assertTrue("GB18030 window should not be empty", window.isNotEmpty())
            // Verify no garbled chars
            val replacementCount = window.count { it == '\uFFFD' }
            assertEquals("GB18030 anchor window should have no replacement chars", 0, replacementCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `anchor jump UTF-16LE readWindowAround contains highlight position`() {
        val file = TestFileGenerator.generateUtf16LEWithEmoji()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Simulate highlight at middle of file
            val highlightOffset = doc.totalChars / 2
            val before = 100
            val after = 100
            val window = doc.readWindowAround(highlightOffset, before, after)

            assertTrue("UTF-16LE window should not be empty", window.isNotEmpty())
            // Verify no orphaned surrogates
            if (window.isNotEmpty()) {
                assertTrue("Should not end with high surrogate", !window.last().isHighSurrogate())
                assertTrue("Should not start with low surrogate", !window.first().isLowSurrogate())
            }
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 9/11: Additional Integration Tests ───────────────────

    @Test
    fun `readWindow and readUnit consistency for streaming file`() {
        val file = TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            // Build canonical text from units
            val canonical = StringBuilder()
            for (unit in units) {
                canonical.append(doc.readUnit(unit))
            }

            // Verify readWindow at unit boundaries matches canonical
            for (unit in units.take(5)) {
                val window = doc.readWindow(unit.charStart, unit.charCount)
                val expected = canonical.substring(
                    unit.charStart,
                    minOf(unit.charStart + unit.charCount, canonical.length)
                )
                assertEquals(
                    "readWindow at unit ${unit.unitIndex} boundary should match canonical",
                    expected, window
                )
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `unitIndexForOffset works correctly with streaming reading units`() {
        val file = TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
            doc.readingUnits = units

            // Verify unitIndexForOffset returns correct index for each unit's start
            for (unit in units) {
                val idx = doc.unitIndexForOffset(unit.charStart)
                assertEquals(
                    "Offset ${unit.charStart} should map to unit ${unit.unitIndex}",
                    unit.unitIndex, idx
                )
            }

            // Verify offset past all units returns -1
            val beyondOffset = units.last().charStart + units.last().charCount + 100
            assertEquals(-1, doc.unitIndexForOffset(beyondOffset))
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 2: Additional 50MB Tests ─────────────────────────────

    @Test
    fun `50MB GB18030 file exact verification`() {
        val file = TestFileGenerator.generateGb18030(chapterCount = 100, charsPerChapter = 50000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("GB18030", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have many reading units", units.size > 10)

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `50MB UTF-16LE file exact verification`() {
        val file = TestFileGenerator.generateUtf16LE(chapterCount = 100, charsPerChapter = 50000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("UTF-16LE", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have many reading units", units.size > 10)

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `50MB no-TOC file readWindow at unit boundaries`() {
        val file = TestFileGenerator.generateLargeNoToc(50L * 1024 * 1024)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            // Build canonical text from all units
            val canonical = StringBuilder()
            for (unit in units) {
                canonical.append(doc.readUnit(unit))
            }

            // Verify readWindow at each unit boundary matches canonical
            for (unit in units.take(10)) {
                val window = doc.readWindow(unit.charStart, unit.charCount)
                val expected = canonical.substring(
                    unit.charStart,
                    minOf(unit.charStart + unit.charCount, canonical.length)
                )
                assertEquals(
                    "readWindow at unit ${unit.unitIndex} boundary should match canonical",
                    expected, window
                )
            }
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 8: Additional Anchor Tests ───────────────────────────

    @Test
    fun `anchor jump UTF-16BE readWindowAround contains highlight position`() {
        val file = TestFileGenerator.generateUtf16BEWithEmoji()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Simulate highlight at middle of file
            val highlightOffset = doc.totalChars / 2
            val before = 100
            val after = 100
            val window = doc.readWindowAround(highlightOffset, before, after)

            assertTrue("UTF-16BE window should not be empty", window.isNotEmpty())
            // Verify no orphaned surrogates
            if (window.isNotEmpty()) {
                assertTrue("Should not end with high surrogate", !window.last().isHighSurrogate())
                assertTrue("Should not start with low surrogate", !window.first().isLowSurrogate())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `anchor jump mixed density UTF-8 readWindowAround contains highlight`() {
        val file = TestFileGenerator.generateMixedDensityUtf8(5_000_000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Simulate highlight at middle of file
            val highlightOffset = doc.totalChars / 2
            val before = 200
            val after = 200
            val window = doc.readWindowAround(highlightOffset, before, after)

            assertTrue("Window should not be empty", window.isNotEmpty())
            // Verify no replacement chars
            val replacementCount = window.count { it == '\uFFFD' }
            assertEquals("No replacement chars in anchor window", 0, replacementCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `anchor jump emoji file readWindowAround contains highlight`() {
        val file = TestFileGenerator.generateEmojiAtBoundaries()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)

            // Simulate highlight near emoji boundary
            val highlightOffset = 49990 // Near the emoji boundary
            val before = 100
            val after = 100
            val window = doc.readWindowAround(highlightOffset, before, after)

            assertTrue("Window should not be empty", window.isNotEmpty())
            // Verify no replacement chars
            val replacementCount = window.count { it == '\uFFFD' }
            assertEquals("No replacement chars in emoji anchor window", 0, replacementCount)
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 2: Additional Large File Tests ───────────────────────

    @Test
    fun `10MB mixed UTF-8 exact verification`() {
        val file = TestFileGenerator.generateLargeMixedUtf8(10L * 1024 * 1024, 20)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have multiple reading units", units.size > 5)

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `10MB no-TOC file exact verification`() {
        val file = TestFileGenerator.generateLargeNoToc(10L * 1024 * 1024)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have multiple reading units", units.size > 5)

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `5MB GB18030 file exact verification`() {
        val file = TestFileGenerator.generateGb18030(chapterCount = 20, charsPerChapter = 20000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("GB18030", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have reading units", units.isNotEmpty())

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 2: Additional Encoding Tests ─────────────────────────

    @Test
    fun `5MB UTF-16BE file exact verification`() {
        val file = TestFileGenerator.generateUtf16BE(chapterCount = 20, charsPerChapter = 20000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("UTF-16BE", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have reading units", units.isNotEmpty())

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `mixed density UTF-8 file exact verification`() {
        val file = TestFileGenerator.generateMixedDensityUtf8(5_000_000)
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have multiple reading units", units.size > 1)

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `emoji at boundaries file exact verification`() {
        val file = TestFileGenerator.generateEmojiAtBoundaries()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have reading units", units.isNotEmpty())

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                // Verify no replacement chars
                val replacementCount = unitText.count { it == '\uFFFD' }
                assertEquals(
                    "Unit ${unit.unitIndex} should have no replacement characters",
                    0, replacementCount
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    // ── G5 Category 2: Additional Large File Integration Tests ───────────

    @Test
    fun `UTF-16LE with emoji file exact verification`() {
        val file = TestFileGenerator.generateUtf16LEWithEmoji()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("UTF-16LE", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have reading units", units.isNotEmpty())

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `UTF-16BE with emoji file exact verification`() {
        val file = TestFileGenerator.generateUtf16BEWithEmoji()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("UTF-16BE", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have reading units", units.isNotEmpty())

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `GB18030 mixed file exact verification`() {
        val file = TestFileGenerator.generateGb18030Mixed()
        tempFiles.add(file)
        try {
            val index = TxtFileScanner.scan(file)
            assertEquals("GB18030", index.encoding)

            val doc = PlainTextDocument.fromFileIndex(file, index)
            val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

            assertTrue("Should have reading units", units.isNotEmpty())

            // Verify ALL units: each unit's decoded text length == charCount
            var totalUnitChars = 0L
            for (unit in units) {
                val unitText = doc.readUnit(unit)
                assertEquals(
                    "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                    unit.charCount, unitText.length
                )
                totalUnitChars += unitText.length
            }
            assertEquals(
                "Sum of all unit text lengths must equal totalChars",
                doc.totalChars.toLong(), totalUnitChars
            )
        } finally {
            file.delete()
        }
    }

    // ── profile 流式：自定义规则 + key + 首标题 heading ───────────────

    @Test
    fun `streaming profile scan detects custom chapters and reports key`() {
        val profile = TxtTocProfile(
            key = "stream-custom-1",
            patterns = listOf(Regex("^foo-\\d+ 测试$")),
            densityGuard = false,
        )
        val file = File.createTempFile("test_stream_profile_", ".txt")
        tempFiles.add(file)
        val body = "测试正文内容。".repeat(70)
        val content = "foo-1 测试\n$body\nfoo-2 测试\n$body"
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file, profile)

        assertEquals("stream-custom-1", index.detectedRuleId)
        assertEquals(listOf("foo-1 测试", "foo-2 测试"), index.chapters.map { it.title })
    }

    @Test
    fun `streaming profile document marks first heading equal to chapter title`() {
        val profile = TxtTocProfile(
            key = "stream-custom-2",
            patterns = listOf(Regex("^foo-\\d+ 测试$")),
            densityGuard = false,
        )
        val file = File.createTempFile("test_stream_profile_doc_", ".txt")
        tempFiles.add(file)
        val body = "测试正文内容。".repeat(70)
        val content = "foo-1 测试\n$body\nfoo-2 测试\n$body"
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file, profile)
        val doc = PlainTextDocument.fromFileIndex(file, index)

        val first = doc.blocks(0).filterIsInstance<DocBlock.Text>().first()
        assertTrue("首标题应标为 heading", first.isHeading)
        assertEquals("foo-1 测试", first.text)
        // 接口不变式：text() == blocks() joined
        assertEquals(
            doc.text(0),
            doc.blocks(0).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text },
        )
    }

    @Test
    fun `streaming legacy builtin document heading still equals chapter title`() {
        // 回归：legacy 扫描（detectedRuleId=builtin）首块 heading 语义不变
        val (file, _, index) = createMultiChapterFile()
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val first = doc.blocks(0).filterIsInstance<DocBlock.Text>().first()
        assertTrue(first.isHeading)
        assertEquals("第1章 测试", first.text)
        assertEquals("第1章 测试", index.chapters[0].title)
    }
}
