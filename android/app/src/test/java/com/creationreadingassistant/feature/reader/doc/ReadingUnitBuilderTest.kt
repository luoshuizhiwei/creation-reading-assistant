package com.creationreadingassistant.feature.reader.doc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadingUnitBuilderTest {

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

    // ── E8-1: 正常章节 1:1 映射 ────────────────────────────────────────

    @Test
    fun `normal chapters map 1-to-1 to reading units`() {
        val chapters = listOf(
            DocChapter(0, "第一章", 0, 1000),
            DocChapter(1, "第二章", 1000, 2000),
            DocChapter(2, "第三章", 3000, 500),
        )
        val units = ReadingUnitBuilder.buildUnits(chapters, null)

        assertEquals(3, units.size)
        // Each unit maps 1:1 to a chapter
        assertEquals(0, units[0].unitIndex)
        assertEquals(0, units[0].chapterIndex)
        assertEquals("第一章", units[0].title)
        assertEquals(0, units[0].charStart)
        assertEquals(1000, units[0].charCount)

        assertEquals(1, units[1].unitIndex)
        assertEquals(1, units[1].chapterIndex)
        assertEquals(1000, units[1].charStart)
        assertEquals(2000, units[1].charCount)

        assertEquals(2, units[2].unitIndex)
        assertEquals(2, units[2].chapterIndex)
        assertEquals(3000, units[2].charStart)
        assertEquals(500, units[2].charCount)

        // No byte info in small-file mode
        assertTrue("byteStart should be null", units.all { it.byteStart == null })
        assertTrue("byteLength should be null", units.all { it.byteLength == null })
    }

    // ── E8-2: 超长章节切分为多个单元 ───────────────────────────────────

    @Test
    fun `super-long chapter splits into multiple units`() {
        // 章节 charCount = 200_000 > MAX_UNIT_CHARS (50_000) → 应切分为 4 个单元
        val chapters = listOf(DocChapter(0, "超长章", 0, 200_000))
        val units = ReadingUnitBuilder.buildUnits(chapters, null)

        assertEquals("Should split into 4 units (200K / 50K)", 4, units.size)
        assertTrue("All units <= MAX_UNIT_CHARS",
            units.all { it.charCount <= ReadingUnitBuilder.MAX_UNIT_CHARS })

        // Verify charCount distribution: 50K + 50K + 50K + 50K = 200K
        assertEquals(50_000, units[0].charCount)
        assertEquals(50_000, units[1].charCount)
        assertEquals(50_000, units[2].charCount)
        assertEquals(50_000, units[3].charCount)

        // Verify charStart continuity
        assertEquals(0, units[0].charStart)
        assertEquals(50_000, units[1].charStart)
        assertEquals(100_000, units[2].charStart)
        assertEquals(150_000, units[3].charStart)

        // All units belong to the same chapter
        assertTrue("All units should reference chapter 0",
            units.all { it.chapterIndex == 0 })
        assertTrue("All units should have same title",
            units.all { it.title == "超长章" })
    }

    // ── E8-3: 读取单元覆盖全书无间隙无重叠 ─────────────────────────────

    @Test
    fun `reading units cover entire chapter without gaps or overlaps`() {
        val chapters = listOf(
            DocChapter(0, "短章", 0, 1000),
            DocChapter(1, "超长章", 1000, 130_000), // 130K > 50K → 3 units
            DocChapter(2, "中章", 131_000, 30_000),
        )
        val units = ReadingUnitBuilder.buildUnits(chapters, null)

        // Expected: 1 (短章) + 3 (超长章: 50K+50K+30K) + 1 (中章) = 5
        assertEquals(5, units.size)

        // Verify unit indices are sequential starting from 0
        for (i in units.indices) {
            assertEquals(i, units[i].unitIndex)
        }

        // Verify coverage: units for chapter 1 (超长章) should cover 130_000 chars
        val ch1Units = units.filter { it.chapterIndex == 1 }
        assertEquals(3, ch1Units.size)
        val totalCh1Chars = ch1Units.sumOf { it.charCount }
        assertEquals(130_000, totalCh1Chars)

        // Verify no overlaps: each unit's charStart + charCount == next unit's charStart
        // (within same chapter)
        for (chapter in chapters) {
            val chapterUnits = units.filter { it.chapterIndex == chapter.index }
            if (chapterUnits.size <= 1) continue

            for (i in 0 until chapterUnits.size - 1) {
                val current = chapterUnits[i]
                val next = chapterUnits[i + 1]
                assertEquals(
                    "Unit ${current.unitIndex} end should equal unit ${next.unitIndex} start",
                    current.charStart + current.charCount,
                    next.charStart,
                )
            }

            // First unit starts at chapter start
            assertEquals(chapter.startOffset, chapterUnits.first().charStart)
            // Last unit ends at chapter end
            val lastUnit = chapterUnits.last()
            assertEquals(
                chapter.startOffset + chapter.charCount,
                lastUnit.charStart + lastUnit.charCount,
            )
        }
    }

    // ── E8-4: 空章节列表 → 空单元列表 ──────────────────────────────────

    @Test
    fun `empty chapters list produces empty units list`() {
        val units = ReadingUnitBuilder.buildUnits(emptyList(), null)
        assertTrue(units.isEmpty())
    }

    // ── E8-5: 恰好等于 MAX_UNIT_CHARS 的章节不切分 ─────────────────────

    @Test
    fun `chapter exactly at MAX_UNIT_CHARS is not split`() {
        val chapters = listOf(DocChapter(0, "边界章", 0, ReadingUnitBuilder.MAX_UNIT_CHARS))
        val units = ReadingUnitBuilder.buildUnits(chapters, null)

        assertEquals(1, units.size)
        assertEquals(ReadingUnitBuilder.MAX_UNIT_CHARS, units[0].charCount)
    }

    // ── E8-6: 略超 MAX_UNIT_CHARS 的章节切分为 2 个单元 ────────────────

    @Test
    fun `chapter slightly over MAX_UNIT_CHARS splits into 2 units`() {
        val chapters = listOf(DocChapter(0, "略超章", 0, ReadingUnitBuilder.MAX_UNIT_CHARS + 1))
        val units = ReadingUnitBuilder.buildUnits(chapters, null)

        assertEquals(2, units.size)
        assertEquals(ReadingUnitBuilder.MAX_UNIT_CHARS, units[0].charCount)
        assertEquals(1, units[1].charCount)
        assertEquals(ReadingUnitBuilder.MAX_UNIT_CHARS, units[1].charStart)
    }

    // ── G5 Category 1: Precise Boundary Tests ──────────────────────────

    @Test
    fun `UTF-8 front-Chinese back-ASCII exact decoding per unit`() {
        val file = track(TestFileGenerator.generateMixedDensityUtf8(500_000))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have multiple reading units", units.size > 1)

        // Verify each unit's byte range decodes to exactly charCount characters
        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
        }

        // Verify concatenation matches original file content exactly
        val concatenated = units.joinToString("") { doc.readUnit(it) }
        val expected = String(file.readBytes(), Charsets.UTF_8)
        assertEquals("Concatenated units must match original file exactly", expected, concatenated)
    }

    @Test
    fun `UTF-8 emoji at unit boundaries no split`() {
        val file = track(TestFileGenerator.generateEmojiAtBoundaries())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
            // Verify no U+FFFD replacement characters (indicates split emoji)
            val replacementCount = text.count { it == '\uFFFD' }
            assertEquals(
                "Unit ${unit.unitIndex} should have no replacement characters",
                0, replacementCount
            )
        }

        // Verify exact concatenation
        val concatenated = units.joinToString("") { doc.readUnit(it) }
        val expected = String(file.readBytes(), Charsets.UTF_8)
        assertEquals("Concatenated units must match original exactly", expected, concatenated)
    }

    @Test
    fun `GB18030 mixed encoding exact decoding per unit`() {
        val file = track(TestFileGenerator.generateGb18030Mixed())
        val index = TxtFileScanner.scan(file)
        assertEquals("GB18030", index.encoding)

        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
        }

        // Verify exact concatenation
        val gb18030 = java.nio.charset.Charset.forName("GB18030")
        val concatenated = units.joinToString("") { doc.readUnit(it) }
        val expected = String(file.readBytes(), gb18030)
        assertEquals("Concatenated GB18030 units must match original exactly", expected, concatenated)
    }

    @Test
    fun `UTF-16LE surrogate pair at boundary no split`() {
        val file = track(TestFileGenerator.generateUtf16LEWithEmoji())
        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-16LE", index.encoding)

        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
            // Verify no orphaned high surrogate at end of unit text
            if (text.isNotEmpty()) {
                assertFalse(
                    "Unit ${unit.unitIndex} should not end with high surrogate",
                    text.last().isHighSurrogate()
                )
            }
        }

        // Verify exact concatenation
        // Use UTF_16 (not UTF_16LE) so Java auto-detects and strips the BOM
        val concatenated = units.joinToString("") { doc.readUnit(it) }
        val expected = String(file.readBytes(), Charsets.UTF_16)
        assertEquals("Concatenated UTF-16LE units must match original exactly", expected, concatenated)
    }

    @Test
    fun `UTF-16BE surrogate pair at boundary no split`() {
        val file = track(TestFileGenerator.generateUtf16BEWithEmoji())
        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-16BE", index.encoding)

        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
            if (text.isNotEmpty()) {
                assertFalse(
                    "Unit ${unit.unitIndex} should not end with high surrogate",
                    text.last().isHighSurrogate()
                )
            }
        }

        // Verify exact concatenation
        // Use UTF_16 (not UTF_16BE) so Java auto-detects and strips the BOM
        val concatenated = units.joinToString("") { doc.readUnit(it) }
        val expected = String(file.readBytes(), Charsets.UTF_16)
        assertEquals("Concatenated UTF-16BE units must match original exactly", expected, concatenated)
    }

    // ── G5 Category 1: Additional Builder Tests ─────────────────────────

    @Test
    fun `reading units cover entire file without gaps for streaming mode`() {
        val file = track(TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        // Verify units cover entire file
        var totalChars = 0L
        for (unit in units) {
            totalChars += unit.charCount
        }
        assertEquals(
            "Sum of all unit charCounts must equal totalCharCount",
            index.totalCharCount, totalChars
        )

        // Verify no overlaps: each unit's charStart + charCount == next unit's charStart
        for (i in 0 until units.size - 1) {
            val current = units[i]
            val next = units[i + 1]
            assertEquals(
                "Unit ${current.unitIndex} end should equal unit ${next.unitIndex} start",
                current.charStart + current.charCount,
                next.charStart
            )
        }
    }

    @Test
    fun `mixed density UTF-8 file units decode exactly`() {
        val file = track(TestFileGenerator.generateMixedDensityUtf8(1_000_000))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have multiple reading units", units.size > 1)

        // Verify each unit decodes exactly
        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
        }
    }

    // ── G5 Category 1: Additional Builder Edge Cases ─────────────────────

    @Test
    fun `emoji at boundaries file units decode exactly`() {
        val file = track(TestFileGenerator.generateEmojiAtBoundaries())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        // Verify each unit decodes exactly with no replacement chars
        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
            val replacementCount = text.count { it == '\uFFFD' }
            assertEquals(
                "Unit ${unit.unitIndex} should have no replacement characters",
                0, replacementCount
            )
        }
    }

    @Test
    fun `UTF-16LE with emoji file units decode exactly`() {
        val file = track(TestFileGenerator.generateUtf16LEWithEmoji())
        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-16LE", index.encoding)

        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        // Verify each unit decodes exactly with no orphaned surrogates
        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
            if (text.isNotEmpty()) {
                assertFalse(
                    "Unit ${unit.unitIndex} should not end with high surrogate",
                    text.last().isHighSurrogate()
                )
            }
        }
    }

    @Test
    fun `UTF-16BE with emoji file units decode exactly`() {
        val file = track(TestFileGenerator.generateUtf16BEWithEmoji())
        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-16BE", index.encoding)

        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        // Verify each unit decodes exactly with no orphaned surrogates
        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
            if (text.isNotEmpty()) {
                assertFalse(
                    "Unit ${unit.unitIndex} should not end with high surrogate",
                    text.last().isHighSurrogate()
                )
            }
        }
    }

    // ── G5 Category 1: Additional Builder Integration Tests ──────────────

    @Test
    fun `GB18030 mixed file units decode exactly`() {
        val file = track(TestFileGenerator.generateGb18030Mixed())
        val index = TxtFileScanner.scan(file)
        assertEquals("GB18030", index.encoding)

        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        // Verify each unit decodes exactly
        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
        }
    }

    @Test
    fun `large no-TOC file units cover entire file`() {
        val file = track(TestFileGenerator.generateLargeNoToc(5L * 1024 * 1024))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have multiple reading units", units.size > 1)

        // Verify units cover entire file
        var totalChars = 0L
        for (unit in units) {
            totalChars += unit.charCount
        }
        assertEquals(
            "Sum of all unit charCounts must equal totalCharCount",
            index.totalCharCount, totalChars
        )

        // Verify each unit decodes exactly
        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
        }
    }

    @Test
    fun `large mixed UTF-8 file units cover entire file`() {
        val file = track(TestFileGenerator.generateLargeMixedUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have multiple reading units", units.size > 1)

        // Verify units cover entire file
        var totalChars = 0L
        for (unit in units) {
            totalChars += unit.charCount
        }
        assertEquals(
            "Sum of all unit charCounts must equal totalCharCount",
            index.totalCharCount, totalChars
        )

        // Verify each unit decodes exactly
        for (unit in units) {
            val text = doc.readUnit(unit)
            assertEquals(
                "Unit ${unit.unitIndex} decoded length must exactly match charCount",
                unit.charCount, text.length
            )
        }
    }

    // ── G5 Category 1: Additional Builder Edge Case Tests ────────────────

    @Test
    fun `UTF-16LE with emoji file units cover entire file`() {
        val file = track(TestFileGenerator.generateUtf16LEWithEmoji())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        // Verify units cover entire file
        var totalChars = 0L
        for (unit in units) {
            totalChars += unit.charCount
        }
        assertEquals(
            "Sum of all unit charCounts must equal totalCharCount",
            index.totalCharCount, totalChars
        )
    }

    @Test
    fun `UTF-16BE with emoji file units cover entire file`() {
        val file = track(TestFileGenerator.generateUtf16BEWithEmoji())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        // Verify units cover entire file
        var totalChars = 0L
        for (unit in units) {
            totalChars += unit.charCount
        }
        assertEquals(
            "Sum of all unit charCounts must equal totalCharCount",
            index.totalCharCount, totalChars
        )
    }

    @Test
    fun `GB18030 mixed file units cover entire file`() {
        val file = track(TestFileGenerator.generateGb18030Mixed())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        // Verify units cover entire file
        var totalChars = 0L
        for (unit in units) {
            totalChars += unit.charCount
        }
        assertEquals(
            "Sum of all unit charCounts must equal totalCharCount",
            index.totalCharCount, totalChars
        )
    }

    // ── G5 Category 1: Additional Builder Final Tests ────────────────────

    @Test
    fun `emoji at boundaries file units cover entire file`() {
        val file = track(TestFileGenerator.generateEmojiAtBoundaries())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have reading units", units.isNotEmpty())

        // Verify units cover entire file
        var totalChars = 0L
        for (unit in units) {
            totalChars += unit.charCount
        }
        assertEquals(
            "Sum of all unit charCounts must equal totalCharCount",
            index.totalCharCount, totalChars
        )
    }

    @Test
    fun `mixed density UTF-8 file units cover entire file`() {
        val file = track(TestFileGenerator.generateMixedDensityUtf8(5_000_000))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)

        assertTrue("Should have multiple reading units", units.size > 1)

        // Verify units cover entire file
        var totalChars = 0L
        for (unit in units) {
            totalChars += unit.charCount
        }
        assertEquals(
            "Sum of all unit charCounts must equal totalCharCount",
            index.totalCharCount, totalChars
        )
    }
}
