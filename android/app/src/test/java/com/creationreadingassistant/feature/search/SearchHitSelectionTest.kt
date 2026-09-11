package com.creationreadingassistant.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 命中坐标挑选与 offsets 解析的纯函数测试。
 *
 * 这些规则决定了「点搜索结果会跳到哪」——写错不会崩溃，只会**静默跳错地方**，
 * 所以必须逐条钉死，不能只测「能跑通」。
 */
class SearchHitSelectionTest {

    private fun match(
        chapter: Int,
        term: String,
        span: SearchMatchSpan? = null,
        hits: Int = 1,
        qCount: Int = 1,
        bookId: String = "b1",
        basis: SearchTextBasis = SearchTextBasis.ORIGINAL,
    ) = RawTermMatch(
        bookId = bookId,
        textBasis = basis,
        chapterIndex = chapter,
        term = term,
        hits = hits,
        queryTermCount = qCount,
        span = span,
    )

    // ── SearchOffsets.parse ────────────────────────────────────────────

    @Test
    fun `parses the first span of an offsets csv`() {
        val parsed = SearchOffsets.parse("12:2,45:2,100:2")
        assertEquals(1, parsed.size)
        assertEquals(SearchMatchSpan(12, 2), parsed[0])
    }

    @Test
    fun `parses up to the requested limit`() {
        val parsed = SearchOffsets.parse("1:2,3:2,5:2", limit = 2)
        assertEquals(listOf(SearchMatchSpan(1, 2), SearchMatchSpan(3, 2)), parsed)
    }

    @Test
    fun `blank or malformed offsets yield no span instead of throwing`() {
        assertTrue(SearchOffsets.parse(null).isEmpty())
        assertTrue(SearchOffsets.parse("").isEmpty())
        assertTrue(SearchOffsets.parse("   ").isEmpty())
        assertTrue("无分隔符的段应被跳过", SearchOffsets.parse("12").isEmpty())
        assertTrue("非数字应被跳过", SearchOffsets.parse("a:b").isEmpty())
        assertTrue("负偏移应被跳过", SearchOffsets.parse("-1:2").isEmpty())
        assertTrue("零长度应被跳过", SearchOffsets.parse("4:0").isEmpty())
    }

    @Test
    fun `a bad segment does not invalidate the good ones`() {
        // 宁可少一个精确偏移，也不要把这本书从结果里弄丢。
        assertEquals(listOf(SearchMatchSpan(7, 3)), SearchOffsets.parse("x:y,7:3", limit = 4))
    }

    // ── 坐标挑选：多词共现优先 ──────────────────────────────────────────

    @Test
    fun `chapter matching more distinct query terms wins over higher frequency`() {
        val matches = listOf(
            match(chapter = 1, term = "世界", span = SearchMatchSpan(10, 2), hits = 50),
            match(chapter = 2, term = "世界", span = SearchMatchSpan(20, 2), hits = 1),
            match(chapter = 2, term = "和平", span = SearchMatchSpan(25, 2), hits = 1),
        )
        // 第 2 章两个查询词都命中，哪怕词频低得多也应该选中它。
        val (chapter, span) = SearchHitSelection.pickCoordinate(matches)
        assertEquals(2, chapter)
        assertEquals(SearchMatchSpan(20, 2), span)
    }

    @Test
    fun `tie on distinct terms falls back to the earliest chapter`() {
        val matches = listOf(
            match(chapter = 5, term = "世界", span = SearchMatchSpan(1, 2)),
            match(chapter = 2, term = "和平", span = SearchMatchSpan(9, 2)),
        )
        assertEquals(2, SearchHitSelection.pickCoordinate(matches).first)
    }

    @Test
    fun `within the chosen chapter the earliest span wins`() {
        val matches = listOf(
            match(chapter = 3, term = "世界", span = SearchMatchSpan(80, 2)),
            match(chapter = 3, term = "和平", span = SearchMatchSpan(30, 2)),
        )
        assertEquals(SearchMatchSpan(30, 2), SearchHitSelection.pickCoordinate(matches).second)
    }

    @Test
    fun `metadata only hit has no coordinate`() {
        val (chapter, span) = SearchHitSelection.pickCoordinate(
            listOf(match(chapter = 0, term = "世界")),
        )
        assertEquals(0, chapter)
        assertNull(span)
    }

    @Test
    fun `empty matches degrade to chapter zero without throwing`() {
        assertEquals(0 to null, SearchHitSelection.pickCoordinate(emptyList()))
    }

    // ── 打分 ───────────────────────────────────────────────────────────

    @Test
    fun `score accumulates hits times query term occurrences`() {
        val matches = listOf(
            match(chapter = 1, term = "世界", hits = 4, qCount = 2),
            match(chapter = 1, term = "和平", hits = 3, qCount = 1),
        )
        assertEquals(4 * 2 + 3 * 1, SearchHitSelection.scoreOf(matches))
    }

    // ── SearchHit 坐标换算 ──────────────────────────────────────────────

    @Test
    fun `reader chapter index is zero based while index chapter is one based`() {
        val hit = SearchHit(
            bookId = "b",
            score = 1,
            textBasis = SearchTextBasis.ORIGINAL,
            indexChapterIndex = 4,
            charOffset = 12,
            matchLength = 2,
            coverage = SearchCoverageState.FULL,
            coverageReason = null,
        )
        assertEquals(3, hit.readerChapterIndex)
        assertTrue(hit.hasSourcePosition)
        assertTrue(!hit.isMetadataHit)
        assertTrue(!hit.isPreviewHit)
    }

    @Test
    fun `chapter zero without offset is a metadata hit and cannot jump`() {
        val metadata = SearchHit("b", 1, SearchTextBasis.ORIGINAL, 0, null, null, null, null)
        assertTrue(metadata.isMetadataHit)
        assertNull(metadata.readerChapterIndex)
        assertTrue(!metadata.hasSourcePosition)
    }

    @Test
    fun `chapter zero with offset is a preview hit`() {
        val preview = SearchHit("b", 1, SearchTextBasis.ORIGINAL, 0, 5, 2, null, null)
        assertTrue(preview.isPreviewHit)
        assertNull("预览命中没有对应的读者章节", preview.readerChapterIndex)
    }
}
