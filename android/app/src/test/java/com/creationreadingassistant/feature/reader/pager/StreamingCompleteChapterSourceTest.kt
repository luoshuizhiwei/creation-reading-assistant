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

    @Test
    fun `exact cross unit boundary and cyclic ring regex with textOffsetMap bidirectional roundtrip`() {
        val file = File.createTempFile("test_cross_boundary_roundtrip_", ".txt")
        tempFiles.add(file)

        // ReadingUnitBuilder.MAX_UNIT_CHARS is 50,000.
        // To split chapter 0 into multiple ReadingUnits, chapter 0 must exceed 50,000 chars.
        val crossTokenPrefix = "<<<CROSS_UNIT_"
        val crossTokenSuffix = "BOUNDARY_TOKEN>>>"
        val crossToken = crossTokenPrefix + crossTokenSuffix
        val ringPattern = "环形广告_"

        val paddingBefore = buildString {
            // ~49,990 chars before the boundary
            var i = 0
            while (length < 49_980) {
                append("填充前缀正文$i ")
                i++
            }
        }
        val chapter1Header = "第一章 跨边界与环形正则\n"
        val ch1Body = buildString {
            append(paddingBefore)
            append(crossTokenPrefix) // near unit boundary
            append(crossTokenSuffix)
            append("\n\n正文中间段落包含环形重复模式：\n")
            repeat(10) { append(ringPattern) }
            append("\n包含待删除标记：DELETE_ME_NOW\n")
            for (i in 1..2500) {
                append("填充后缀正文$i ")
            }
        }
        val fullContent = "$chapter1Header$ch1Body\n\n第二章 后续章\n后续正文"
        RandomAccessFile(file, "rw").use { it.write(fullContent.toByteArray(Charsets.UTF_8)) }

        val index = TxtFileScanner.scan(file)
        val streamingDoc = PlainTextDocument.fromFileIndex(file, index)
        val ch0Units = streamingDoc.readingUnits.filter { it.chapterIndex == 0 }
        assertTrue("第一章必须切为多个 reading units，实际=${ch0Units.size}", ch0Units.size >= 2)

        val source = TxtChapterSource(streamingDoc)
        val rules = listOf(
            replaceRule(pattern = crossToken, replacement = "[跨单元净化]"),
            replaceRule(pattern = ringPattern, replacement = "[环形替换]"),
            replaceRule(pattern = "DELETE_ME_NOW", replacement = ""),
        )
        val prepared = preparePagedReplacement(source, "cross-roundtrip-book", rules)
        assertEquals(PagedReplacementAvailability.APPLIED, prepared.availability)
        val wrapped = prepared.source as ReplacedChapterSource

        val dispText = wrapped.loadChapterText(0)
        assertFalse("原跨单元 token 不应残存", dispText.contains(crossToken))
        assertTrue("跨单元替换标记必须生效", dispText.contains("[跨单元净化]"))
        assertFalse("原环形 pattern 不应残存", dispText.contains(ringPattern))
        assertTrue("环形替换标记必须生效", dispText.contains("[环形替换]"))
        assertFalse("待删除标记必须被删除", dispText.contains("DELETE_ME_NOW"))

        val exactProjection = wrapped.projectionForChapter(0)
        assertNotNull("投影必须为 Exact", exactProjection)
        val offsetMap = exactProjection!!.projection.offsetMap
        val rawChapterText = source.loadChapterText(0)

        // 1. TextOffsetMap 单调性与 round trip floor 不变量
        var prevSource = -1
        for (d in 0..dispText.length) {
            val s = offsetMap.toSource(d)
            assertTrue("toSource 单调不减 d=$d", s >= prevSource)
            prevSource = s
            assertTrue("toSource 不越界 d=$d s=$s", s in 0..rawChapterText.length)
            assertTrue("display round trip floor d=$d", offsetMap.toDisplay(s) <= d)
        }

        var prevDisplay = -1
        for (s in 0..rawChapterText.length) {
            val d = offsetMap.toDisplay(s)
            assertTrue("toDisplay 单调不减 s=$s", d >= prevDisplay)
            prevDisplay = d
            assertTrue("toDisplay 不越界 s=$s d=$d", d in 0..dispText.length)
            assertTrue("source round trip floor s=$s", offsetMap.toSource(d) <= s)
        }

        // 2. 未替换区域（如前缀和后缀）双向严格精确映射（0 漂移往返）
        val prefixLen = chapter1Header.length + 1000
        for (pos in 0 until prefixLen) {
            assertEquals("prefix source->display->source exact at $pos", pos, offsetMap.toSource(offsetMap.toDisplay(pos)))
            assertEquals("prefix display->source->display exact at $pos", pos, offsetMap.toDisplay(offsetMap.toSource(pos)))
        }

        // 3. 跨单元标记精确端点双向映射（source↔display 往返无偏移）
        val crossTokenSourceStart = rawChapterText.indexOf(crossToken)
        val markerDispStart = dispText.indexOf("[跨单元净化]")
        assertEquals("跨单元起点 source->display 映射无偏移", markerDispStart, offsetMap.toDisplay(crossTokenSourceStart))
        assertEquals("跨单元起点 display->source 映射无偏移", crossTokenSourceStart, offsetMap.toSource(markerDispStart))

        // 4. 全局 source 坐标映射验证
        val globalSourceIdx = exactProjection.localDisplayToGlobalSource(markerDispStart)
        assertEquals(
            "跨单元标记映射回全局 source 必须对齐原始 token 起点",
            fullContent.indexOf(crossToken),
            globalSourceIdx,
        )
    }

    @Test
    fun `LRU 3-chapter eviction and rule key change rejects stale projections`() {
        val (streamingDoc, _, _) = createStreamingDoc(chapterCount = 5, charsPerChapterBody = 5000)
        val source = TxtChapterSource(streamingDoc)
        assertEquals(5, source.chapterCount)

        val rulesV1 = listOf(replaceRule("广告", "[版本1]"))
        val wrappedV1 = ReplacedChapterSource(source, "book-lru", rulesV1)

        // 依次访问 0, 1, 2 章，填满容量为 3 的缓存
        wrappedV1.loadChapterText(0)
        wrappedV1.loadChapterText(1)
        wrappedV1.loadChapterText(2)
        assertEquals(3, wrappedV1.inspectionCacheSize())
        assertEquals(listOf(0, 1, 2), wrappedV1.inspectionCacheKeysForTest())

        // 访问第 0 章使之成为最近访问项，顺序变为 [1, 2, 0]
        wrappedV1.loadChapterText(0)
        assertEquals(listOf(1, 2, 0), wrappedV1.inspectionCacheKeysForTest())

        // 访问第 3 章（第 4 个不同章节），必须淘汰最久未访问的第 1 章
        wrappedV1.loadChapterText(3)
        assertEquals(3, wrappedV1.inspectionCacheSize())
        assertEquals(listOf(2, 0, 3), wrappedV1.inspectionCacheKeysForTest())
        assertFalse("第 1 章必须已被 LRU 淘汰逐出缓存", wrappedV1.inspectionCacheKeysForTest().contains(1))

        // 规则变更：规则 key 变化后，旧投影绝对不被复用
        val rulesV2 = listOf(replaceRule("广告", "[版本2]"))
        val preparedV2 = preparePagedReplacement(source, "book-lru", rulesV2)
        val wrappedV2 = preparedV2.source as ReplacedChapterSource
        assertFalse(
            "规则变更后 profile key 必须不同",
            wrappedV1.replaceProfileKey == wrappedV2.replaceProfileKey,
        )

        val textV2 = wrappedV2.loadChapterText(0)
        assertTrue("新 source 必须产出版本2替换结果", textV2.contains("[版本2]"))
        assertFalse("旧投影残留版本1绝不被复用", textV2.contains("[版本1]"))
    }
}
