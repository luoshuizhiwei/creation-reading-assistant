package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnitBuilder
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.doc.TestFileGenerator
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PagedChapterSourceTest {

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

    @Test
    fun `txt source preserves exact chapter slices and offsets`() {
        val text = "第一章\n甲乙\n第二章\n丙丁"
        val secondStart = text.indexOf("第二章")
        val source = TxtChapterSource(
            fullText = text,
            chapters = listOf(
                DocChapter(0, "第一章", 0, secondStart),
                DocChapter(1, "第二章", secondStart, text.length - secondStart),
            ),
        )

        assertEquals(2, source.chapterCount)
        assertEquals(text.substring(0, secondStart), source.loadChapterText(0))
        assertEquals(text.substring(secondStart), source.loadChapterText(1))
        assertEquals(secondStart, source.chapterStartAbs(1))
        assertEquals(1, source.chapterIndexFor(text.length - 1))
        assertEquals(text.length, source.totalChars)
    }

    @Test
    fun `epub source uses legacy chapter bases for binary lookup`() {
        val loads = mutableListOf<Int>()
        val source = EpubChapterSource(
            titles = listOf("一", "二", "三"),
            chapterStartOffsets = listOf(0, 101, 302),
            totalChars = 603,
            loadBlocks = { index ->
                loads += index
                listOf(DocBlock.Text("chapter-$index"))
            },
        )

        assertEquals(0, source.chapterIndexFor(-50))
        assertEquals(0, source.chapterIndexFor(100))
        assertEquals(1, source.chapterIndexFor(101))
        assertEquals(1, source.chapterIndexFor(301))
        assertEquals(2, source.chapterIndexFor(9999))
        assertEquals("chapter-2", source.loadChapterText(2))
        assertEquals(listOf(2), loads)
    }

    @Test
    fun `epub page source keeps text offsets reversible and images zero-width`() {
        val blocks = listOf(
            DocBlock.Image("cover", 800, 1200),
            DocBlock.Text("　标题　", isHeading = true),
            DocBlock.Image("middle", 640, 480),
            DocBlock.Text(" 第一行 \n\n第二行 "),
            DocBlock.Image("ending", 0, 0),
        )
        val text = EpubPageSource.chapterTextOf(blocks)
        val layout = EpubPageSource.layoutBlocksOf(blocks)

        assertEquals("　标题　\n 第一行 \n\n第二行 ", text)
        val paragraphs = layout.filterIsInstance<LayoutBlock.Text>().map { it.paragraph }
        paragraphs.forEach { paragraph ->
            assertEquals(
                paragraph.text,
                text.substring(paragraph.charOffset, paragraph.charOffset + paragraph.text.length),
            )
        }
        assertEquals(listOf(0, 5, text.length), layout.filterIsInstance<LayoutBlock.Image>().map { it.anchorOffset })
        assertEquals(com.creationreadingassistant.feature.reader.layout.BlockRole.HEADING, paragraphs.first().role)
    }

    @Test
    fun `streaming source sees construction-time units on first frame and uses bounded raw ranges`() {
        val raw = buildString {
            append("第1章\n")
            append("  raw first line keeps its whitespace  \n")
            append("alpha\nbeta\n\n")
            // 注意：ReadingUnitBuilder 的 nearCeiling 规则会把距 hardEnd 1000 字符内的
            // checkpoint 并入当前 unit，MAX*2+137 只会得到 2 个 unit；用 MAX*3+137
            // 保证首章必然拆成 ≥3 个虚拟 unit。
            repeat(ReadingUnitBuilder.MAX_UNIT_CHARS * 3 + 137) { append('甲') }
        }
        val file = track(File.createTempFile("test_streaming_units_", ".txt").apply {
            writeText(raw, Charsets.UTF_8)
        })
        val index = TxtFileScanner.scan(file)
        val document = PlainTextDocument.fromFileIndex(file, index)

        // 单一真相在文档构造层：fromFileIndex 构造时即构建 readingUnits，
        // 首个组合帧 TxtChapterSource 构造时 units 已就绪，不依赖组合层写回。
        val units = document.readingUnits
        assertTrue("Expected a long chapter to split into multiple units", units.size > 2)
        val source = TxtChapterSource(document)
        assertEquals(units.size, source.chapterCount)

        // 首帧即可按 ReadingUnit 有界读取，绝不回退整章规范化路径。
        val first = source.loadChapter(0)
        assertEquals(units[0].charStart, source.chapterStartAbs(0))
        assertEquals(document.readUnit(units[0]), first.text)
        assertTrue(first.text.contains("  raw first line keeps its whitespace  \n"))

        first.blocks.filterIsInstance<LayoutBlock.Text>().forEach { block ->
            val paragraph = block.paragraph
            assertEquals(
                paragraph.text,
                first.text.substring(paragraph.charOffset, paragraph.charOffset + paragraph.text.length),
            )
        }

        val boundedUnit = units[1]
        val bounded = source.loadChapter(1)
        assertEquals(boundedUnit.charStart, source.chapterStartAbs(1))
        assertEquals(boundedUnit.charCount, bounded.text.length)
        assertTrue(bounded.text.length <= ReadingUnitBuilder.MAX_UNIT_CHARS)
        assertEquals(document.readUnit(boundedUnit), bounded.text)
    }

    @Test
    fun `document-backed source with empty units never falls back to whole chapter text`() {
        // 防御不变式：document 在场但 readingUnits 为空时，source 必须保持空
        // （chapterCount == 0 / loadChapter 返回空内容），不得回退 document.text(index)
        // 整章规范化路径 —— 那会 trim/重排段落、破坏 raw 字符偏移，
        // 与渲染/搜索/TTS/Locator 的原始字符空间不一致。
        val document = PlainTextDocument("第一章\n甲乙\n第二章\n丙丁")
        assertTrue("fixture must expose loadable whole-chapter text", document.text(0).isNotEmpty())

        val source = TxtChapterSource(document)

        assertEquals(0, source.chapterCount)
        assertEquals("", source.loadChapterText(0))
    }

    @Test
    fun `streaming source follows later reading unit replacements`() {
        val file = track(TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)
        assertEquals(units.size, source.chapterCount)

        // 发布后的 units 被整体替换（例如重新扫描）时，同一 source 实例必须跟随新列表，
        // 而不是保留构造时的快照。
        val replaced = units.take(3)
        assertTrue("Expected fixture with at least 3 units", replaced.size >= 3)
        doc.readingUnits = replaced

        assertEquals(replaced.size, source.chapterCount)
        for (i in replaced.indices) {
            assertEquals(replaced[i].charStart, source.chapterStartAbs(i))
            assertEquals(doc.readUnit(replaced[i]), source.loadChapterText(i))
        }
        assertEquals("", source.loadChapterText(replaced.size))
    }

    // ── G5 Category 4: Paging Tests ─────────────────────────────────────

    @Test
    fun `no-TOC streaming file uses readingUnits for chapterCount`() {
        val file = track(TestFileGenerator.generateLargeNoToc(10L * 1024 * 1024))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        // chapterCount should equal readingUnits.size (not 1)
        assertTrue("Should have multiple reading units", units.size > 1)
        assertEquals(
            "chapterCount should equal readingUnits.size",
            units.size, source.chapterCount
        )
    }

    @Test
    fun `cross-unit paging continuity no duplicates or gaps`() {
        val file = track(TestFileGenerator.generateLargeUtf8(10L * 1024 * 1024, 20))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        // Page through all units sequentially, collect all text
        val allText = StringBuilder()
        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            allText.append(content.text)
        }

        // Verify total length matches doc.totalChars
        assertEquals(
            "Total text from all units should equal totalChars",
            doc.totalChars.toLong(), allText.length.toLong()
        )
    }

    @Test
    fun `loadChapter returns exact text matching readUnit`() {
        val file = track(TestFileGenerator.generateLargeUtf8(10L * 1024 * 1024, 20))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        // For each unit, verify loadChapter(i).text == doc.readUnit(readingUnits[i])
        for (i in 0 until minOf(source.chapterCount, 10)) {
            val loaded = source.loadChapter(i)
            val expected = doc.readUnit(units[i])
            assertEquals(
                "loadChapter($i) should match readUnit for unit ${units[i].unitIndex}",
                expected, loaded.text
            )
        }
    }

    @Test
    fun `chapter title mapping from readingUnit titles`() {
        val file = track(TestFileGenerator.generateLargeUtf8(10L * 1024 * 1024, 20))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        // Verify readingUnit titles map correctly
        for (i in 0 until source.chapterCount) {
            val title = source.chapterTitle(i)
            assertEquals(
                "Chapter title at index $i should match unit title",
                units[i].title, title
            )
            assertTrue("Title should not be empty", title.isNotEmpty())
        }
    }

    // ── G5 Category 4: Additional Paging Tests ──────────────────────────

    @Test
    fun `paging GB18030 file uses readingUnits correctly`() {
        val file = track(TestFileGenerator.generateGb18030(chapterCount = 5, charsPerChapter = 5000))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        // Verify chapterCount matches units size
        assertEquals("chapterCount should equal readingUnits.size", units.size, source.chapterCount)

        // Verify each chapter can be loaded
        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match unit charCount",
                units[i].charCount, content.text.length
            )
        }
    }

    @Test
    fun `paging UTF-16LE file uses readingUnits correctly`() {
        val file = track(TestFileGenerator.generateUtf16LE(chapterCount = 3, charsPerChapter = 3000))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal readingUnits.size", units.size, source.chapterCount)

        // Verify each chapter can be loaded and has correct length
        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match unit charCount",
                units[i].charCount, content.text.length
            )
        }
    }

    @Test
    fun `chapterStartAbs returns correct offsets for reading units`() {
        val file = track(TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        // Verify chapterStartAbs returns correct offset for each unit
        for (i in 0 until source.chapterCount) {
            val startAbs = source.chapterStartAbs(i)
            assertEquals(
                "chapterStartAbs($i) should match unit charStart",
                units[i].charStart, startAbs
            )
        }
    }

    // ── G5 Category 4: Additional Paging Edge Cases ──────────────────────

    @Test
    fun `paging mixed density UTF-8 file uses readingUnits correctly`() {
        val file = track(TestFileGenerator.generateMixedDensityUtf8(5_000_000))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal readingUnits.size", units.size, source.chapterCount)

        // Verify each chapter can be loaded and has correct length
        for (i in 0 until minOf(source.chapterCount, 5)) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match unit charCount",
                units[i].charCount, content.text.length
            )
        }
    }

    @Test
    fun `paging emoji file uses readingUnits correctly`() {
        val file = track(TestFileGenerator.generateEmojiAtBoundaries())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal readingUnits.size", units.size, source.chapterCount)

        // Verify each chapter can be loaded and has correct length
        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match unit charCount",
                units[i].charCount, content.text.length
            )
        }
    }

    @Test
    fun `totalChars matches sum of all unit charCounts`() {
        val file = track(TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        // Verify totalChars matches sum of all unit charCounts
        val sumOfUnitChars = units.sumOf { it.charCount.toLong() }
        assertEquals(
            "totalChars should equal sum of all unit charCounts",
            sumOfUnitChars, doc.totalChars.toLong()
        )
    }

    // ── G5 Category 4: Additional Paging Integration Tests ───────────────

    @Test
    fun `paging no-TOC large file uses readingUnits correctly`() {
        val file = track(TestFileGenerator.generateLargeNoToc(5L * 1024 * 1024))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal readingUnits.size", units.size, source.chapterCount)

        // Verify first few chapters can be loaded
        for (i in 0 until minOf(source.chapterCount, 5)) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match unit charCount",
                units[i].charCount, content.text.length
            )
        }
    }

    @Test
    fun `paging mixed density file uses readingUnits correctly`() {
        val file = track(TestFileGenerator.generateMixedDensityUtf8(5_000_000))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal readingUnits.size", units.size, source.chapterCount)

        // Verify each chapter can be loaded
        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match unit charCount",
                units[i].charCount, content.text.length
            )
        }
    }

    @Test
    fun `chapterIndexFor returns correct index for reading units`() {
        val file = track(TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        // Verify chapterIndexFor returns correct index for each unit's start
        for (i in 0 until source.chapterCount) {
            val offset = units[i].charStart
            val chapterIdx = source.chapterIndexFor(offset)
            assertEquals(
                "chapterIndexFor offset ${offset} should return $i",
                i, chapterIdx
            )
        }
    }
    @Test
    fun `paging UTF-16LE with emoji file uses readingUnits correctly`() {
        val file = track(TestFileGenerator.generateUtf16LEWithEmoji())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal readingUnits.size", units.size, source.chapterCount)

        // Verify each chapter can be loaded
        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match unit charCount",
                units[i].charCount, content.text.length
            )
        }
    }

    // ── G5 Category 4: Additional Paging Final Tests ─────────────────────

    @Test
    fun `paging UTF-16BE with emoji file uses readingUnits correctly`() {
        val file = track(TestFileGenerator.generateUtf16BEWithEmoji())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val units = ReadingUnitBuilder.buildUnits(doc.chapters, index)
        doc.readingUnits = units

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal readingUnits.size", units.size, source.chapterCount)

        // Verify each chapter can be loaded
        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match unit charCount",
                units[i].charCount, content.text.length
            )
        }
    }
}
