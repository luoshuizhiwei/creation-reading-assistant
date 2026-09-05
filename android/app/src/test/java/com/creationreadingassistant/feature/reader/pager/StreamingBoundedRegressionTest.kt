package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TestFileGenerator
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceProjector
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.min

class StreamingBoundedRegressionTest {

    private val tempFiles = mutableListOf<File>()
    private fun File.track(): File = also { tempFiles += it }

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun indexOf(file: File): TxtFileIndex = TxtFileScanner.scan(file)

    private fun buildStreamingSource(file: File): TxtChapterSource {
        val fileIndex = indexOf(file)
        val doc = PlainTextDocument.fromFileIndex(file, fileIndex)
        return TxtChapterSource.fromStreaming(doc, fileIndex)
    }

    private fun replaceRule(pattern: String, replacement: String): ReplaceRule = ReplaceRule(
        id = "r-$pattern",
        name = "test",
        pattern = pattern,
        replacement = replacement,
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )

    @Test
    fun `1 noToc 50MB TXT multi segmented paging`() {
        val size50MB = 50L * 1024L * 1024L
        val file = TestFileGenerator.generateLargeNoToc(size50MB).track()
        val src = buildStreamingSource(file)
        assertTrue("50MB noToc segments=" + src.chapterCount, src.chapterCount >= 100)
        var lastEnd = 0
        for (i in 0 until src.chapterCount) {
            val startAbs = src.chapterStartAbs(i)
            assertEquals("seg $i start=$startAbs not after previous end=$lastEnd", lastEnd, startAbs)
            val content = src.loadChapter(i)
            assertTrue(
                "seg $i length ${content.text.length} exceeds MAX_WINDOW_CHARS=${PlainTextDocument.MAX_WINDOW_CHARS}",
                content.text.length in 1..PlainTextDocument.MAX_WINDOW_CHARS
            )
            lastEnd = startAbs + content.text.length
        }
        assertEquals("totalChars must match final segment end", src.totalChars, lastEnd)
    }

    @Test
    fun `2 segment text bounded by MAX_WINDOW_CHARS for first 20`() {
        val size10MB = 10L * 1024L * 1024L
        val file = TestFileGenerator.generateLargeNoToc(size10MB).track()
        val src = buildStreamingSource(file)
        val upper = PlainTextDocument.MAX_WINDOW_CHARS
        for (i in 0 until minOf(src.chapterCount, 20)) {
            val content = src.loadChapter(i)
            assertTrue(
                "seg $i length=${content.text.length} upper=$upper",
                content.text.length in 1..upper
            )
        }
    }

    @Test
    fun `3 noToc 5MB multiple segments`() {
        val size5MB = 5L * 1024L * 1024L
        val file = TestFileGenerator.generateLargeNoToc(size5MB).track()
        val src = buildStreamingSource(file)
        assertTrue("5MB noToc segments=" + src.chapterCount, src.chapterCount > 1)
    }

    @Test
    fun `4 all segment offsets continuous no gaps no overlaps`() {
        val file = TestFileGenerator.generateUtf8(chapterCount = 30, charsPerChapter = 20_000).track()
        val src = buildStreamingSource(file)
        assertTrue("segments must exceed chapters", src.chapterCount >= 30)
        var lastEnd: Int? = null
        for (i in 0 until src.chapterCount) {
            val startAbs = src.chapterStartAbs(i)
            val content = src.loadChapter(i)
            if (lastEnd != null) {
                assertEquals("seg $i start=$startAbs not after previous end=$lastEnd", lastEnd, startAbs)
            }
            val next = if (i + 1 < src.chapterCount) src.chapterStartAbs(i + 1) else src.totalChars
            val gap = (next - startAbs) - content.text.length
            assertTrue(
                "seg $i gap=$gap between segment.len=${content.text.length} and next-start delta=${next - startAbs}",
                gap in -2..2
            )
            lastEnd = startAbs + content.text.length
        }
    }

    @Test
    fun `7 oversized chapter filtered via metadata without full allocation`() {
        val sizeBytes = 400_000L * 3L + 2_000L
        val file = TestFileGenerator.generateLargeUtf8(targetSizeBytes = sizeBytes, chapterCount = 1).track()
        val src = buildStreamingSource(file)
        val provider = src.asReplaceProjectionScopeProvider()
        assertNotNull("streaming TXT must expose provider", provider)
        val scope = provider!!.scopeForSegment(0)
        assertTrue("scope must be UnsupportedTooLarge, actual=$scope", scope is ReplaceProjectionScope.UnsupportedTooLarge)
        val big = scope as ReplaceProjectionScope.UnsupportedTooLarge
        assertTrue(
            "actual=${big.actualSourceLength} < max allowed for projection (${BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS})",
            big.actualSourceLength >= BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS
        )
        assertTrue("oversized chapter should split into >=8 segments, segs=${src.chapterCount}", src.chapterCount >= 8)
        for (i in 0 until src.chapterCount) {
            val c = src.loadChapter(i)
            assertTrue(
                "seg $i len ${c.text.length} out of MAX_WINDOW_CHARS bounds",
                c.text.length in 1..PlainTextDocument.MAX_WINDOW_CHARS
            )
        }
        var callCount = 0
        val prepared = preparePagedReplacement(
            delegate = src,
            bookId = "test-book",
            rules = listOf(replaceRule("测试", "替换")),
            onUnsupportedTooLarge = { callCount++ },
        )
        val replacedSrc = prepared.source as ReplacedSegmentedChapterSource
        for (i in 0 until src.chapterCount) {
            val raw = src.loadChapter(i)
            val replaced = replacedSrc.loadChapter(i)
            assertEquals("seg $i text unchanged", raw.text, replaced.text)
            assertNull("oversized chapter projection must be null", replacedSrc.projectionForChapter(i))
        }
        assertEquals("oversized callback fired exactly once", 1, callCount)
    }

    @Test
    fun `8 oversized chapter concatenated segments equals original prefix`() {
        val sizeBytes = 350_000L * 3L
        val file = TestFileGenerator.generateLargeUtf8(sizeBytes, 1).track()
        val src = buildStreamingSource(file)
        val all = ArrayList<String>(src.chapterCount)
        for (i in 0 until src.chapterCount) all.add(src.loadChapter(i).text)
        val concatenated = all.joinToString("")
        val original = file.readText(Charsets.UTF_8)
        val minLen = minOf(concatenated.length, original.length)
        assertEquals(
            "concatenated != original prefix (len=$minLen)",
            original.take(minLen),
            concatenated.take(minLen)
        )
    }

    @Test
    fun `9 oversized callback fires at most once`() {
        val sizeBytes = 350_000L * 3L
        val file = TestFileGenerator.generateLargeUtf8(sizeBytes, 1).track()
        val src = buildStreamingSource(file)
        var callCount = 0
        val prepared = preparePagedReplacement(
            delegate = src,
            bookId = "test-book",
            rules = listOf(replaceRule("X", "Y")),
            onUnsupportedTooLarge = { callCount++ },
        )
        assertTrue("need >=6 segments but got ${src.chapterCount}", src.chapterCount >= 6)
        repeat(3) {
            for (i in 0 until src.chapterCount) prepared.source.loadChapter(i)
        }
        assertEquals("oversized callback count must be exactly 1", 1, callCount)
    }

    @Test
    fun `13 encoding offsets match for UTF8 UTF16LE GB18030`() {
        val names = listOf("UTF-8", "UTF-16LE", "GB18030")
        val factories: List<() -> File> = listOf(
            { TestFileGenerator.generateUtf8(10, 10_000).track() },
            { TestFileGenerator.generateUtf16LE(10, 10_000).track() },
            { TestFileGenerator.generateGb18030(10, 10_000).track() },
        )
        for (k in 0 until 3) {
            val name = names[k]
            val file = factories[k]()
            val fileIndex: TxtFileIndex = indexOf(file)
            val doc = PlainTextDocument.fromFileIndex(file, fileIndex)
            val src = TxtChapterSource.fromStreaming(doc, fileIndex)
            val provider = src.asReplaceProjectionScopeProvider()
                ?: error("$name provider missing")
            val seen = HashSet<Int>()
            var segment = 0
            val chapterLimit = 10.coerceAtMost(fileIndex.chapters.size)
            for (chapterIdx in 0 until chapterLimit) {
                while (segment < src.chapterCount) {
                    if (provider.scopeForSegment(segment).logicalChapterIndex == chapterIdx) break
                    segment++
                }
                if (segment >= src.chapterCount) break
                val expectedStart = fileIndex.chapters[chapterIdx].charStart.toInt()
                val actualStart = src.chapterStartAbs(segment)
                assertEquals("$name ch$chapterIdx start mismatch", expectedStart, actualStart)
                assertFalse("$name ch$chapterIdx seen twice", seen.contains(chapterIdx))
                seen.add(chapterIdx)
            }
            assertTrue("$name seen=${seen.size} < 8", seen.size >= 8)
        }
    }

    @Test
    fun `small file TxtChapterSource uses logical chapters one to one`() {
        val text = "First\nABC\nSecond\nXYZ"
        val secondStart = text.indexOf("Second")
        val chapters = listOf(
            DocChapter(0, "First", 0, secondStart),
            DocChapter(1, "Second", secondStart, text.length - secondStart),
        )
        val src = TxtChapterSource(text, chapters)
        assertEquals("small file chapterCount", 2, src.chapterCount)
        assertEquals(text.substring(0, secondStart), src.loadChapterText(0))
        assertEquals(text.substring(secondStart), src.loadChapterText(1))
        assertTrue("small file should expose old complete-scope", src.replaceProjectionScopeIsComplete)
        assertNull("small file must NOT expose segmented provider", src.asReplaceProjectionScopeProvider())
    }
}
