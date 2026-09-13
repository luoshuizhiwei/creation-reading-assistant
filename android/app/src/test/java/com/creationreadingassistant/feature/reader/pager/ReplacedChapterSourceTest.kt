package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import com.creationreadingassistant.feature.reader.rules.ReplaceProfile
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacedChapterSourceTest {

    @Test
    fun `decorator projects display text while source coordinates stay authoritative`() {
        val sourceText = "第一章\n广告正文\n第二章\n尾声"
        val secondStart = sourceText.indexOf("第二章")
        val delegate = TxtChapterSource(
            fullText = sourceText,
            chapters = listOf(
                DocChapter(0, "第一章", 0, secondStart),
                DocChapter(1, "第二章", secondStart, sourceText.length - secondStart),
            ),
        )
        val rules = listOf(replaceRule(pattern = "广告", replacement = ""))
        val source = ReplacedChapterSource(
            delegate = delegate,
            bookId = "book-1",
            rules = rules,
        )

        val first = source.loadChapter(0)
        val projection = source.projectionForChapter(0)

        assertEquals("第一章\n正文\n", first.text)
        first.blocks.filterIsInstance<LayoutBlock.Text>().forEach { block ->
            val paragraph = block.paragraph
            assertEquals(
                paragraph.text,
                first.text.substring(paragraph.charOffset, paragraph.charOffset + paragraph.text.length),
            )
        }
        assertNotNull(projection)
        assertEquals("第一章\n广告正文\n", projection!!.projection.sourceText)
        assertEquals("第一章\n正文\n", projection.projection.displayText)
        assertEquals(0, projection.scopeSourceBase)
        assertEquals(secondStart, source.chapterStartAbs(1))
        assertEquals(sourceText.length, source.totalChars)
        assertEquals(ReplaceProfile.key("book-1", rules), source.replaceProfileKey)
    }

    @Test
    fun `oversized chapter stays source exact and reports unsupported only once`() {
        val sourceText = "广告正文"
        val delegate = TxtChapterSource(
            fullText = sourceText,
            chapters = listOf(DocChapter(0, "", 0, sourceText.length)),
        )
        val unsupported = mutableListOf<BoundedReplaceResult.UnsupportedTooLarge>()
        val source = ReplacedChapterSource(
            delegate = delegate,
            bookId = "book-1",
            rules = listOf(replaceRule(pattern = "广告", replacement = "")),
            maxSourceLength = 3,
            onUnsupportedTooLarge = unsupported::add,
        )

        assertEquals(sourceText, source.loadChapterText(0))
        assertNull(source.projectionForChapter(0))
        assertEquals(sourceText, source.loadChapterText(0))
        assertEquals(
            listOf(BoundedReplaceResult.UnsupportedTooLarge(sourceText.length, 3)),
            unsupported,
        )
    }

    @Test
    fun `decorator rejects estimated EPUB coordinate sources`() {
        val epub = EpubChapterSource(
            titles = listOf("第一章"),
            chapterStartOffsets = listOf(0),
            totalChars = 100,
            loadBlocks = { emptyList() },
        )

        assertThrows(IllegalArgumentException::class.java) {
            ReplacedChapterSource(
                delegate = epub,
                bookId = "book-1",
                rules = listOf(replaceRule(pattern = "广告", replacement = "")),
            )
        }
    }

    @Test
    fun `decorator rejects exact sources whose paging units are not complete replacement scopes`() {
        val virtualUnits = object : PagedChapterSource {
            override val chapterCount: Int = 1
            override val totalChars: Int = 4
            override fun chapterTitle(index: Int): String = "虚拟单元"
            override fun chapterStartAbs(index: Int): Int = 0
            override fun loadChapter(index: Int) = PagedChapterContent("广告正文", emptyList())
        }

        assertThrows(IllegalArgumentException::class.java) {
            ReplacedChapterSource(
                delegate = virtualUnits,
                bookId = "book-1",
                rules = listOf(replaceRule(pattern = "广告", replacement = "")),
            )
        }
    }

    @Test
    fun `factory wraps complete TXT but leaves incomplete streaming scope explicit`() {
        val complete = TxtChapterSource(
            fullText = "广告正文",
            chapters = listOf(DocChapter(0, "", 0, 4)),
        )
        val incomplete = object : PagedChapterSource {
            override val chapterCount: Int = 1
            override val totalChars: Int = 4
            override fun chapterTitle(index: Int): String = "虚拟单元"
            override fun chapterStartAbs(index: Int): Int = 0
            override fun loadChapter(index: Int) = PagedChapterContent("广告正文", emptyList())
        }
        val rules = listOf(replaceRule(pattern = "广告", replacement = ""))

        val applied = preparePagedReplacement(complete, "book-1", rules)
        val unsupported = preparePagedReplacement(incomplete, "book-1", rules)

        assertEquals(PagedReplacementAvailability.APPLIED, applied.availability)
        assertTrue(applied.source is ReplacedChapterSource)
        assertEquals(PagedReplacementAvailability.INCOMPLETE_SCOPE, unsupported.availability)
        assertSame(incomplete, unsupported.source)
    }

    @Test
    fun `no rules keeps only projection-capable sources manageable`() {
        val completeTxt = TxtChapterSource(
            fullText = "正文",
            chapters = listOf(DocChapter(0, "", 0, 2)),
        )
        val estimatedEpub = EpubChapterSource(
            titles = listOf("章节"),
            chapterStartOffsets = listOf(0),
            totalChars = 2,
            loadBlocks = { emptyList() },
        )
        val incomplete = object : PagedChapterSource {
            override val chapterCount: Int = 1
            override val totalChars: Int = 2
            override fun chapterTitle(index: Int): String = "章节"
            override fun chapterStartAbs(index: Int): Int = 0
            override fun loadChapter(index: Int) = PagedChapterContent("正文", emptyList())
        }

        assertEquals(
            PagedReplacementAvailability.NO_EFFECTIVE_RULES,
            preparePagedReplacement(completeTxt, "book-1", emptyList()).availability,
        )
        // EPUB 空规则：结构保真投影路径已放开，delegate 原样透传（可管理、零开销）
        val epubNoRules = preparePagedReplacement(estimatedEpub, "book-1", emptyList())
        assertEquals(
            PagedReplacementAvailability.NO_EFFECTIVE_RULES,
            epubNoRules.availability,
        )
        assertSame(estimatedEpub, epubNoRules.source)
        assertEquals(
            PagedReplacementAvailability.INCOMPLETE_SCOPE,
            preparePagedReplacement(incomplete, "book-1", emptyList()).availability,
        )
    }

    private fun replaceRule(pattern: String, replacement: String) = ReplaceRule(
        id = "replace-1",
        name = "测试规则",
        pattern = pattern,
        replacement = replacement,
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )

    // ── R4：单处纠错走 TXT 装配链 + EPUB 装配期可用性收窄 ────────────────

    private fun correctionRule(
        sourceStart: Int,
        sourceEnd: Int,
        findText: String,
        replaceText: String,
    ) = ReplaceRule(
        id = "correction:c1",
        name = "单处纠错",
        pattern = "",
        replacement = replaceText,
        enabled = true,
        position = Int.MAX_VALUE,
        scope = RuleScope.PER_BOOK,
        anchor = com.creationreadingassistant.feature.reader.rules.CorrectionAnchor(
            sourceStart = sourceStart,
            sourceEnd = sourceEnd,
            findText = findText,
        ),
    )

    private fun epubSource(
        chapterStarts: List<Int>,
        totalChars: Int,
        chapterTexts: List<String>,
    ) = EpubChapterSource(
        titles = chapterTexts.mapIndexed { i, _ -> "第${i + 1}章" },
        chapterStartOffsets = chapterStarts,
        totalChars = totalChars,
        loadBlocks = { index -> listOf(DocBlock.Text(chapterTexts[index], isHeading = false)) },
    )

    @Test
    fun `txt decorator applies correction anchored in a later chapter via scoped base`() {
        val sourceText = "第一章开头 第二章正文"
        val secondStart = sourceText.indexOf("第二章")
        val delegate = TxtChapterSource(
            fullText = sourceText,
            chapters = listOf(
                DocChapter(0, "第一章", 0, secondStart),
                DocChapter(1, "第二章", secondStart, sourceText.length - secondStart),
            ),
        )
        // 锚点锚定第二章内的「章正文」，scopeSourceBase 由装配链传入章起点
        val anchorStart = sourceText.indexOf("章正文")
        val source = ReplacedChapterSource(
            delegate = delegate,
            bookId = "book-1",
            rules = listOf(correctionRule(anchorStart, anchorStart + 3, "章正文", "章正文改")),
        )

        val second = source.loadChapter(1)
        assertTrue("第二章正文应含纠错结果", second.text.contains("章正文改"))
        val projection = source.projectionForChapter(1)
        assertNotNull(projection)
        // 持久化坐标仍是全书 source：display 反查回锚点起点
        assertEquals(anchorStart, projection?.localDisplayToGlobalSource(second.text.indexOf("章正文改")))
        // 未触及的第一章保持原样
        assertEquals("第一章开头 ", source.loadChapter(0).text)
    }

    @Test
    fun `epub availability stays applied when byte bound guarantees all chapters within limit`() {
        val source = epubSource(
            chapterStarts = listOf(0, 100, 200),
            totalChars = 300,
            chapterTexts = listOf("甲", "乙", "丙"),
        )
        val prepared = preparePagedReplacement(source, "epub", listOf(replaceRule("甲", "A")))
        assertEquals(PagedReplacementAvailability.APPLIED, prepared.availability)
    }

    @Test
    fun `epub availability degrades to oversized when verified chapter exceeds limit`() {
        val longText = "字".repeat(300)
        val source = epubSource(
            chapterStarts = listOf(0, 100, 900),
            totalChars = 1000,
            chapterTexts = listOf("甲", longText, "丙"),
        )
        val prepared = preparePagedReplacement(source, "epub", listOf(replaceRule("甲", "A")))
        // 章 1 的字节估算上界 800 > 默认上限不可行？此处显式传小上限验证分类逻辑
        val preparedSmall = com.creationreadingassistant.feature.reader.pager.preparePagedReplacement(
            delegate = source,
            bookId = "epub",
            rules = listOf(replaceRule("甲", "A")),
            maxSourceLength = 256,
        )
        // 章 1 实际 300 字符 > 256、章 0/2 均 ≤ 256 → 混合降级
        assertEquals(PagedReplacementAvailability.PARTIALLY_APPLIED, preparedSmall.availability)
        // 渲染层仍精确：章 1 保留原文，章 0 正常替换
        val projected = preparedSmall.source as ProjectedChapterSource
        assertNull(projected.projectionForChapter(1))
        assertNotNull(projected.projectionForChapter(0))
    }

    @Test
    fun `epub availability degrades to all oversized when no chapter is projectable`() {
        val longText = "字".repeat(400)
        val source = epubSource(
            chapterStarts = listOf(0, 900),
            totalChars = 1800,
            chapterTexts = listOf(longText, longText),
        )
        val prepared = preparePagedReplacement(
            delegate = source,
            bookId = "epub",
            rules = listOf(replaceRule("甲", "A")),
            maxSourceLength = 256,
        )
        assertEquals(PagedReplacementAvailability.ALL_SCOPES_OVERSIZED, prepared.availability)
    }

    @Test
    fun `epub availability reports unverified when candidates exceed verify budget`() {
        // 8 章全部字节上界超限且实际也超限：装配期验证预算（16 章）内可全验 → 不应出现 UNVERIFIED
        // 构造 24 章 → 超出预算上限，剩余章节诚实降级
        val longText = "字".repeat(300)
        val chapterCount = 24
        val starts = (0 until chapterCount).map { it * 900 }
        val texts = (0 until chapterCount).map { longText }
        val source = epubSource(starts, 900 * chapterCount, texts)
        val prepared = preparePagedReplacement(
            delegate = source,
            bookId = "epub",
            rules = listOf(replaceRule("甲", "A")),
            maxSourceLength = 256,
        )
        assertEquals(PagedReplacementAvailability.UNVERIFIED_CHAPTER_LENGTHS, prepared.availability)
    }
}
