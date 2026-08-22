package com.creationreadingassistant.feature.reader.pager

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

    private fun replaceRule(pattern: String, replacement: String) = ReplaceRule(
        id = "replace-1",
        name = "测试规则",
        pattern = pattern,
        replacement = replacement,
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )
}
