package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TestFileGenerator
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceProjector
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceResult
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class StreamingCompleteChapterSourceTest {

    private val tempFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun createStreamingDoc(
        chapterCount: Int = 3,
        charsPerChapterBody: Int = 80_000,
    ): Triple<PlainTextDocument, PlainTextDocument, String> {
        val file = File.createTempFile("test_stream_chapter_", ".txt")
        tempFiles.add(file)

        val lines = mutableListOf<String>()
        for (i in 1..chapterCount) {
            lines.add("第${i}章 标题")
            val bodySeed = "这是章节正文内容，包含广告字样需要被替换。".repeat(100)
            val body = buildString {
                var len = 0
                while (len < charsPerChapterBody) {
                    append(bodySeed)
                    append("\n\n")
                    len += bodySeed.length + 2
                }
            }
            lines.add(body)
        }
        val content = lines.joinToString("\n")
        RandomAccessFile(file, "rw").use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file)
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)
        val referenceDoc = PlainTextDocument(content)
        return Triple(streamingDoc, referenceDoc, content)
    }

    private fun replaceRule(pattern: String, replacement: String) = ReplaceRule(
        id = "r-$pattern",
        name = "测试规则",
        pattern = pattern,
        replacement = replacement,
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )

    @Test
    fun `streaming chapter source chapterCount equals DocChapter count not readingUnits count`() {
        val (streamingDoc, referenceDoc, _) = createStreamingDoc(chapterCount = 4)
        val units = streamingDoc.readingUnits
        assertTrue(
            "测试数据要切出多个 reading units，实际=${units.size}",
            units.size > referenceDoc.chapters.size,
        )
        val source = TxtChapterSource(streamingDoc)
        assertEquals(
            "流式 source 章数应等于 DocChapter 数量",
            referenceDoc.chapters.size,
            source.chapterCount,
        )
        assertTrue(
            "reading units 数应多于章节数（验证测试数据有效）",
            units.size > source.chapterCount,
        )
    }

    @Test
    fun `streaming chapter source declares complete replacement scope`() {
        val (streamingDoc, _, _) = createStreamingDoc()
        val source = TxtChapterSource(streamingDoc)
        assertTrue(
            "流式 source 必须声明 replaceProjectionScopeIsComplete=true",
            source.replaceProjectionScopeIsComplete,
        )
    }

    @Test
    fun `streaming chapter start offsets match reference document exactly`() {
        val (streamingDoc, referenceDoc, _) = createStreamingDoc(chapterCount = 5)
        val source = TxtChapterSource(streamingDoc)
        assertEquals(referenceDoc.chapters.size, source.chapterCount)
        for (ci in 0 until source.chapterCount) {
            assertEquals(
                "chapter $ci 起点偏移不精确",
                referenceDoc.chapters[ci].startOffset,
                source.chapterStartAbs(ci),
            )
        }
    }

    @Test
    fun `streaming source totalChars matches reference exactly`() {
        val (streamingDoc, referenceDoc, _) = createStreamingDoc()
        val source = TxtChapterSource(streamingDoc)
        assertEquals(referenceDoc.totalChars, source.totalChars)
    }

    @Test
    fun `streaming loadChapter returns complete logical chapter spanning original reading units`() {
        val (streamingDoc, referenceDoc, rawContent) = createStreamingDoc(chapterCount = 3)
        val source = TxtChapterSource(streamingDoc)
        val refSource = TxtChapterSource(rawContent, referenceDoc.chapters)
        for (ci in 0 until source.chapterCount) {
            val chapterContent = source.loadChapter(ci)
            val referenceText = refSource.loadChapterText(ci)
            assertEquals(
                "第 $ci 章完整内容长度不匹配",
                referenceText.length,
                chapterContent.text.length,
            )
            assertEquals(
                "第 $ci 章完整内容不匹配",
                referenceText,
                chapterContent.text,
            )
            val paragraphs = chapterContent.blocks
                .filterIsInstance<LayoutBlock.Text>()
                .map { it.paragraph }
            for (p in paragraphs) {
                assertTrue(
                    "第 $ci 章 paragraph@${p.charOffset} 偏移+长度越界",
                    p.charOffset + p.text.length <= chapterContent.text.length,
                )
                val slice = chapterContent.text.substring(
                    p.charOffset,
                    p.charOffset + p.text.length,
                )
                assertEquals(
                    "第 $ci 章 paragraph@${p.charOffset} 文本与章内切片不一致",
                    p.text,
                    slice,
                )
            }
        }
    }

    @Test
    fun `streaming chapter text position plus startAbs equals global source offset`() {
        val (streamingDoc, referenceDoc, rawContent) = createStreamingDoc(chapterCount = 4)
        val source = TxtChapterSource(streamingDoc)
        val refSource = TxtChapterSource(rawContent, referenceDoc.chapters)
        val fullReference = buildString {
            for (ci in 0 until referenceDoc.chapters.size) append(refSource.loadChapterText(ci))
        }
        for (ci in 0 until source.chapterCount) {
            val chStart = source.chapterStartAbs(ci)
            val chText = source.loadChapterText(ci)
            val samplePositions = listOf(0, chText.length / 4, chText.length / 2,
                chText.length * 3 / 4, (chText.length - 1).coerceAtLeast(0))
            for (localPos in samplePositions) {
                val global = chStart + localPos
                val expected = fullReference.getOrNull(global)
                val actual = chText.getOrNull(localPos)
                assertEquals(
                    "章 $ci 位置 $localPos (全局 $global) 字符不一致",
                    expected, actual,
                )
            }
        }
    }

    @Test
    fun `regex spanning original reading unit boundaries matches in replaced streaming source`() {
        val file = File.createTempFile("test_cross_unit_", ".txt")
        tempFiles.add(file)
        val chapterTitle = "第一章 跨单元正则"
        val unitBody = buildString {
            append("普通内容前缀，此处跨广告匹配：")
            for (i in 1..3500) append("填充内容$i ")
            append("广告")
            for (i in 1..3500) append("连接内容$i ")
            append("词结束")
        }
        val rawContent = "$chapterTitle\n$unitBody\n第二章 跳过\n填充第二章内容".trimIndent()
        RandomAccessFile(file, "rw").use {
            it.write(rawContent.toByteArray(Charsets.UTF_8))
        }
        val index = TxtFileScanner.scan(file)
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)
        val referenceDoc = PlainTextDocument(rawContent)
        val ch0Units = streamingDoc.readingUnits.filter { it.chapterIndex == 0 }
        assertTrue(
            "测试前置：第一章应被切为多个 reading units，实际=${ch0Units.size}",
            ch0Units.size >= 2,
        )
        val streamingSource = TxtChapterSource(streamingDoc)
        val refSource = TxtChapterSource(rawContent, referenceDoc.chapters)
        val rules = listOf(replaceRule(pattern = "广告", replacement = "[已净化]"))
        val wrappedStreaming = ReplacedChapterSource(
            delegate = streamingSource,
            bookId = "book-stream",
            rules = rules,
        )
        val wrappedRef = ReplacedChapterSource(
            delegate = refSource,
            bookId = "book-ref",
            rules = rules,
        )
        val streamCh0 = wrappedStreaming.loadChapterText(0)
        val refCh0 = wrappedRef.loadChapterText(0)
        assertEquals(
            "流式与小文件路径的第一章投影结果必须完全一致（跨 unit 边界正则不漏匹配）",
            refCh0, streamCh0,
        )
        val projection = wrappedStreaming.projectionForChapter(0)
        assertNotNull("第一章投影应为 Exact", projection)
        val firstMarker = streamCh0.indexOf("[已净化]")
        assertTrue("[已净化] 标记应存在", firstMarker >= 0)
        val globalSource = projection!!.localDisplayToGlobalSource(firstMarker)
        assertEquals(
            "display 位置映射回全局 source 偏移应对应 rawContent 中首个 '广告'",
            rawContent.indexOf("广告"),
            globalSource,
        )
    }

    @Test
    fun `oversized streaming chapter stays source and reports unsupported once`() {
        val (streamingDoc, _, _) = createStreamingDoc(chapterCount = 1, charsPerChapterBody = 300_000)
        val source = TxtChapterSource(streamingDoc)
        val chapterLen = source.chapterEstimatedCharCount(0)
        assertTrue(
            "测试前置：首章应 > DEFAULT_MAX_SOURCE_CHARS ($chapterLen)",
            chapterLen > BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS,
        )
        val reported = mutableListOf<BoundedReplaceResult.UnsupportedTooLarge>()
        val wrapped = ReplacedChapterSource(
            delegate = source,
            bookId = "big-book",
            rules = listOf(replaceRule(pattern = "广告", replacement = "")),
            onUnsupportedTooLarge = reported::add,
        )
        val text1 = wrapped.loadChapterText(0)
        val text2 = wrapped.loadChapterText(0)
        assertEquals(source.loadChapterText(0), text1)
        assertEquals(text1, text2)
        assertNull("超限章节不应保存 Exact 投影", wrapped.projectionForChapter(0))
        assertEquals(1, reported.size)
        assertEquals(
            BoundedReplaceResult.UnsupportedTooLarge(
                actualSourceLength = text1.length,
                maxSourceLength = BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS,
            ),
            reported.single(),
        )
    }

    @Test
    fun `preparePagedReplacement wraps streaming complete source with rules applied`() {
        val (streamingDoc, _, _) = createStreamingDoc(chapterCount = 2, charsPerChapterBody = 10_000)
        val source = TxtChapterSource(streamingDoc)
        val rules = listOf(replaceRule(pattern = "广告", replacement = ""))
        val prepared = preparePagedReplacement(source, "book-1", rules)
        assertEquals(PagedReplacementAvailability.APPLIED, prepared.availability)
        assertTrue(prepared.source is ReplacedChapterSource)
        val wrapped = prepared.source as ReplacedChapterSource
        val ch0 = wrapped.loadChapterText(0)
        assertFalse(
            "应用规则后第一章不应再含 '广告'",
            ch0.contains("广告"),
        )
    }

    @Test
    fun `UTF-16LE streaming chapter source text offsets match reference`() {
        val file = TestFileGenerator.generateUtf16LE(chapterCount = 3, charsPerChapter = 60_000)
        tempFiles.add(file)
        val index = TxtFileScanner.scan(file)
        assertEquals("UTF-16LE", index.encoding)
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)
        val source = TxtChapterSource(streamingDoc)
        assertEquals(3, source.chapterCount)
        assertTrue("流式完整章节 replaceProjectionScopeIsComplete=true",
            source.replaceProjectionScopeIsComplete)
        for (ci in 0 until source.chapterCount) {
            val text = source.loadChapterText(ci)
            val charStart = source.chapterStartAbs(ci)
            assertTrue("第 $ci 章标题应包含 '第'", text.startsWith("第"))
            assertTrue("第 $ci 章字符数应>0", text.length > 0)
            val nextStart = if (ci + 1 < source.chapterCount) {
                source.chapterStartAbs(ci + 1)
            } else {
                source.totalChars
            }
            assertEquals(
                "第 $ci 章 (start=$charStart,len=${text.length}) 结尾应对齐下一章起点 $nextStart",
                nextStart,
                charStart + text.length,
            )
        }
    }

    @Test
    fun `GB18030 streaming chapter source reads complete chapters correctly`() {
        val file = TestFileGenerator.generateGb18030(chapterCount = 3, charsPerChapter = 60_000)
        tempFiles.add(file)
        val index = TxtFileScanner.scan(file)
        assertEquals("GB18030", index.encoding)
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)
        val source = TxtChapterSource(streamingDoc)
        assertTrue(source.replaceProjectionScopeIsComplete)
        val wrapped = ReplacedChapterSource(
            delegate = source,
            bookId = "gb-book",
            rules = listOf(replaceRule(pattern = "内容", replacement = "[净化]")),
        )
        for (ci in 0 until source.chapterCount) {
            val text = wrapped.loadChapterText(ci)
            val ffdCount = text.count { it == '\uFFFD' }
            assertEquals("GB18030 章节 $ci 投影后不应出现乱码", 0, ffdCount)
        }
    }

    @Test
    fun `streaming chapterIndexFor returns correct chapter for global offset`() {
        val (streamingDoc, referenceDoc, rawContent) = createStreamingDoc(chapterCount = 5)
        val source = TxtChapterSource(streamingDoc)
        val refSource = TxtChapterSource(rawContent, referenceDoc.chapters)
        val probes = buildList {
            for (ci in 0 until source.chapterCount) {
                add(source.chapterStartAbs(ci))
                add(source.chapterStartAbs(ci) + source.chapterEstimatedCharCount(ci) / 2)
            }
            add((source.totalChars - 1).coerceAtLeast(0))
        }
        for (offset in probes) {
            assertEquals(
                "offset=$offset 的章号判定与小文件路径不一致",
                refSource.chapterIndexFor(offset),
                source.chapterIndexFor(offset),
            )
        }
    }

    @Test
    fun `legacy virtual-unit scope cannot be wrapped by replaced source`() {
        val virtualUnitSource = object : PagedChapterSource {
            override val chapterCount: Int = 1
            override val totalChars: Int = 4
            override fun chapterTitle(index: Int) = "虚拟单元"
            override fun chapterStartAbs(index: Int) = 0
            override fun loadChapter(index: Int) =
                PagedChapterContent("广告正文", emptyList())
        }
        val prepared = preparePagedReplacement(
            delegate = virtualUnitSource,
            bookId = "virt-book",
            rules = listOf(replaceRule(pattern = "广告", replacement = "")),
        )
        assertEquals(PagedReplacementAvailability.INCOMPLETE_SCOPE, prepared.availability)
        assertSame(virtualUnitSource, prepared.source)
    }
}
