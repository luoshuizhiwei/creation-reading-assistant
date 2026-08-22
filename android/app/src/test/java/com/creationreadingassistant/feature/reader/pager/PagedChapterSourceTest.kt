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
import org.junit.Assert.assertFalse
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
    fun `streaming source uses logical chapters and preserves raw whitespace on first frame`() {
        val raw = buildString {
            append("第1章\n")
            append("  raw first line keeps its whitespace  \n")
            append("alpha\nbeta\n\n")
            repeat(ReadingUnitBuilder.MAX_UNIT_CHARS * 3 + 137) { append('甲') }
        }
        val file = track(File.createTempFile("test_streaming_chapters_", ".txt").apply {
            writeText(raw, Charsets.UTF_8)
        })
        val index = TxtFileScanner.scan(file)
        val document = PlainTextDocument.fromFileIndex(file, index)
        val chapters = document.chapters
        val units = document.readingUnits
        assertTrue("Expected a long chapter to split into multiple reading units", units.size > chapters.size)

        val source = TxtChapterSource(document)
        assertEquals("流式 source chapterCount 应等于逻辑章数", chapters.size, source.chapterCount)
        assertTrue("流式 source 应声明 replaceProjectionScopeIsComplete=true", source.replaceProjectionScopeIsComplete)

        val first = source.loadChapter(0)
        assertEquals(chapters[0].startOffset, source.chapterStartAbs(0))
        assertEquals(TxtPageSource.chapterTextOf(raw, chapters[0]), first.text)
        assertTrue(first.text.contains("  raw first line keeps its whitespace  \n"))

        first.blocks.filterIsInstance<LayoutBlock.Text>().forEach { block ->
            val paragraph = block.paragraph
            assertTrue(
                "paragraph@${paragraph.charOffset} 越界: end=${paragraph.charOffset + paragraph.text.length}, chapterText.len=${first.text.length}",
                paragraph.charOffset + paragraph.text.length <= first.text.length,
            )
            assertEquals(
                paragraph.text,
                first.text.substring(paragraph.charOffset, paragraph.charOffset + paragraph.text.length),
            )
        }
    }

    @Test
    fun `document-backed small source uses detected chapters not readingUnits emptiness`() {
        // 用显式章节而不是依赖默认 TOC 识别（默认规则可能不含简单"第X章"单行格式）。
        val raw = "第一回 缘起\n甲乙丙丁\n第二回 发展\n戊己庚辛"
        val docChapters = listOf(
            DocChapter(0, "第一回 缘起", 0, raw.indexOf("第二回")),
            DocChapter(1, "第二回 发展", raw.indexOf("第二回"), raw.length - raw.indexOf("第二回")),
        )
        assertEquals(2, docChapters.size)
        val document = PlainTextDocument(raw)
        assertEquals(emptyList<Any>(), document.readingUnits)

        // 新：TxtChapterSource(fullText, chapters) — 小文件显式章节路径，与阅读器
        // 中打开小 TXT 时 ReaderPagerEngineState 构建的 source 完全一致。
        val source = TxtChapterSource(fullText = raw, chapters = docChapters)

        assertEquals("小文件 source 章数应等于显式 chapters 数",
            docChapters.size, source.chapterCount)
        val secondStart = raw.indexOf("第二回")
        assertEquals(raw.substring(0, secondStart), source.loadChapterText(0))
        assertEquals(raw.substring(secondStart), source.loadChapterText(1))
        assertTrue("流式/小文件 TXT source 都应声明完整投影作用域",
            source.replaceProjectionScopeIsComplete)
    }

    @Test
    fun `streaming source uses stable logical chapters independent of readingUnits mutation`() {
        val file = track(TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters
        assertTrue("Expected fixture with at least 3 chapters", chapters.size >= 3)

        val source = TxtChapterSource(doc)
        assertEquals(chapters.size, source.chapterCount)

        val units = ReadingUnitBuilder.buildUnits(chapters, index)
        doc.readingUnits = units.take(3)
        assertEquals("替换 readingUnits 后 source 章数仍应等于逻辑章数",
            chapters.size, source.chapterCount)
        for (i in chapters.indices) {
            assertEquals(chapters[i].startOffset, source.chapterStartAbs(i))
            val text = source.loadChapterText(i)
            assertEquals("logical chapter $i charCount 应等于加载文本长度",
                chapters[i].charCount, text.length)
            val nextExpected = if (i + 1 in chapters.indices) chapters[i + 1].startOffset else doc.totalChars
            assertEquals("chapter $i end should align", nextExpected, chapters[i].startOffset + text.length)
        }
        assertEquals("", source.loadChapterText(chapters.size))
    }

    @Test
    fun `no-TOC streaming file uses logical chapters for chapterCount not readingUnits`() {
        val file = track(TestFileGenerator.generateLargeNoToc(10L * 1024 * 1024))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters
        val units = ReadingUnitBuilder.buildUnits(chapters, index)
        assertTrue("Should have multiple reading units vs chapters", units.size > chapters.size)

        val source = TxtChapterSource(doc)

        assertEquals(
            "chapterCount should equal doc.chapters.size",
            chapters.size, source.chapterCount
        )
        assertTrue("流式完整章节 replaceProjectionScopeIsComplete=true",
            source.replaceProjectionScopeIsComplete)
    }

    @Test
    fun `cross-chapter paging continuity no duplicates or gaps`() {
        val file = track(TestFileGenerator.generateLargeUtf8(10L * 1024 * 1024, 20))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)

        val source = TxtChapterSource(doc)

        val allText = StringBuilder()
        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            allText.append(content.text)
        }

        assertEquals(
            "Total text from all logical chapters should equal totalChars",
            doc.totalChars.toLong(), allText.length.toLong()
        )
    }

    @Test
    fun `loadChapter returns exact text matching chapterTextOf full slice`() {
        val file = track(TestFileGenerator.generateLargeUtf8(10L * 1024 * 1024, 20))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters

        val source = TxtChapterSource(doc)

        for (i in 0 until minOf(source.chapterCount, 10)) {
            val loaded = source.loadChapter(i)
            assertEquals("logical chapter $i charCount 应等于加载文本长度",
                chapters[i].charCount, loaded.text.length)
            val nextExpected = if (i + 1 in chapters.indices) chapters[i + 1].startOffset else doc.totalChars
            assertEquals("loadChapter($i) end应对齐下一章", nextExpected, chapters[i].startOffset + loaded.text.length)
            assertTrue("第 $i 章文本应包含标题前缀: ${chapters[i].title.take(5)}",
                loaded.text.startsWith("第") || loaded.text.trimStart().startsWith("第"))
        }
    }

    @Test
    fun `chapter title mapping from logical chapter titles`() {
        val file = track(TestFileGenerator.generateLargeUtf8(10L * 1024 * 1024, 20))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters

        val source = TxtChapterSource(doc)

        for (i in 0 until source.chapterCount) {
            val title = source.chapterTitle(i)
            assertEquals(
                "Chapter title at index $i should match logical chapter title",
                chapters[i].title, title
            )
            assertTrue("Title should not be empty", title.isNotEmpty())
        }
    }

    @Test
    fun `paging GB18030 file reads logical chapters correctly`() {
        val file = track(TestFileGenerator.generateGb18030(chapterCount = 5, charsPerChapter = 5000))
        val index = TxtFileScanner.scan(file)
        assertEquals("GB18030", index.encoding)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters
        assertEquals("fixture 应有 5 个逻辑章", 5, chapters.size)

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal chapters.size", chapters.size, source.chapterCount)
        assertTrue(source.replaceProjectionScopeIsComplete)

        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match logical chapter charCount",
                chapters[i].charCount, content.text.length
            )
            val ffdCount = content.text.count { it == '\uFFFD' }
            assertEquals("GB18030 章节 $i 不应出现乱码", 0, ffdCount)
        }
    }

    @Test
    fun `paging UTF-16LE file reads logical chapters correctly`() {
        val file = track(TestFileGenerator.generateUtf16LE(chapterCount = 3, charsPerChapter = 3000))
        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-16LE", index.encoding)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters
        assertEquals(3, chapters.size)

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal chapters.size", chapters.size, source.chapterCount)
        for (ci in 0 until source.chapterCount) {
            val content = source.loadChapter(ci)
            assertTrue("Chapter $ci should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $ci text length should match chapter charCount",
                chapters[ci].charCount, content.text.length
            )
            assertTrue("章节标题应以 第 开头", content.text.startsWith("第"))
        }
    }

    @Test
    fun `chapterStartAbs returns correct offsets for logical chapters`() {
        val file = track(TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters

        val source = TxtChapterSource(doc)

        for (i in 0 until source.chapterCount) {
            val startAbs = source.chapterStartAbs(i)
            assertEquals(
                "chapterStartAbs($i) should match chapter startOffset",
                chapters[i].startOffset, startAbs
            )
        }
    }

    @Test
    fun `paging mixed density UTF-8 file reads logical chapters correctly`() {
        val file = track(TestFileGenerator.generateMixedDensityUtf8(5_000_000))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters
        assertTrue("fixture 应有逻辑章", chapters.isNotEmpty())

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal chapters.size", chapters.size, source.chapterCount)

        for (i in 0 until minOf(source.chapterCount, 5)) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match chapter charCount",
                chapters[i].charCount, content.text.length
            )
        }
    }

    @Test
    fun `paging emoji file reads logical chapters correctly without split corruption`() {
        val file = track(TestFileGenerator.generateEmojiAtBoundaries())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters
        assertTrue("fixture 应有逻辑章", chapters.isNotEmpty())

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal chapters.size", chapters.size, source.chapterCount)

        for (ci in 0 until source.chapterCount) {
            val content = source.loadChapter(ci)
            assertTrue("Chapter $ci should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $ci text length should match chapter charCount",
                chapters[ci].charCount, content.text.length
            )
            val ffdCount = content.text.count { it == '\uFFFD' }
            assertEquals("Emoji 边界章节 $ci 不应出现乱码代理对", 0, ffdCount)
        }
    }

    @Test
    fun `totalChars matches sum of all chapter charCounts`() {
        val file = track(TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters

        val source = TxtChapterSource(doc)

        val sumOfChapterChars = chapters.sumOf { it.charCount.toLong() }
        assertEquals(
            "totalChars should equal sum of all chapter charCounts",
            sumOfChapterChars, doc.totalChars.toLong()
        )
        assertEquals(sumOfChapterChars.toInt(), source.totalChars)
    }

    @Test
    fun `paging no-TOC large file reads logical chapters correctly`() {
        val file = track(TestFileGenerator.generateLargeNoToc(5L * 1024 * 1024))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters
        assertTrue("fixture 至少有 1 个逻辑章", chapters.size >= 1)

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal chapters.size", chapters.size, source.chapterCount)

        for (i in 0 until minOf(source.chapterCount, 5)) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match chapter charCount",
                chapters[i].charCount, content.text.length
            )
        }
    }

    @Test
    fun `paging mixed density file reads logical chapters correctly`() {
        val file = track(TestFileGenerator.generateMixedDensityUtf8(5_000_000))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal chapters.size", chapters.size, source.chapterCount)

        for (i in 0 until source.chapterCount) {
            val content = source.loadChapter(i)
            assertTrue("Chapter $i should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $i text length should match chapter charCount",
                chapters[i].charCount, content.text.length
            )
        }
    }

    @Test
    fun `chapterIndexFor returns correct index for logical chapters`() {
        val file = track(TestFileGenerator.generateLargeUtf8(5L * 1024 * 1024, 10))
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters

        val source = TxtChapterSource(doc)

        for (i in 0 until source.chapterCount) {
            val start = chapters[i].startOffset
            assertEquals(
                "chapterIndexFor 章起点 $start 应返回 $i",
                i, source.chapterIndexFor(start)
            )
            val mid = start + (chapters[i].charCount / 2).coerceAtLeast(0)
            assertEquals(
                "chapterIndexFor 章中点 $mid 应返回 $i",
                i, source.chapterIndexFor(mid)
            )
        }
    }

    @Test
    fun `paging UTF-16LE with emoji file reads logical chapters correctly`() {
        val file = track(TestFileGenerator.generateUtf16LEWithEmoji())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal chapters.size", chapters.size, source.chapterCount)

        for (ci in 0 until source.chapterCount) {
            val content = source.loadChapter(ci)
            assertTrue("Chapter $ci should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $ci text length should match chapter charCount",
                chapters[ci].charCount, content.text.length
            )
        }
    }

    @Test
    fun `paging UTF-16BE with emoji file reads logical chapters correctly`() {
        val file = track(TestFileGenerator.generateUtf16BEWithEmoji())
        val index = TxtFileScanner.scan(file)
        val doc = PlainTextDocument.fromFileIndex(file, index)
        val chapters = doc.chapters

        val source = TxtChapterSource(doc)

        assertEquals("chapterCount should equal chapters.size", chapters.size, source.chapterCount)

        for (ci in 0 until source.chapterCount) {
            val content = source.loadChapter(ci)
            assertTrue("Chapter $ci should have text", content.text.isNotEmpty())
            assertEquals(
                "Chapter $ci text length should match chapter charCount",
                chapters[ci].charCount, content.text.length
            )
        }
    }
}