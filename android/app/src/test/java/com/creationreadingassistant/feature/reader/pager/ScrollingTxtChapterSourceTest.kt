package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollingTxtChapterSourceTest {

    @Test
    fun `complete logical chapter projects once across scrolling unit boundaries`() {
        val chapter = "abXYZcd"
        val units = listOf(
            unit(index = 0, start = 100, length = 3),
            unit(index = 1, start = 103, length = 4),
        )
        var chapterReads = 0
        val delegate = ScrollingTxtChapterSource(
            units = units,
            totalChars = 107,
            loadUnitText = { unit ->
                val local = unit.charStart - 100
                chapter.substring(local, local + unit.charCount)
            },
            loadLogicalChapterText = {
                chapterReads += 1
                chapter
            },
        )
        val prepared = preparePagedReplacement(
            delegate = delegate,
            bookId = "book",
            rules = listOf(replaceRule()),
        )

        assertEquals(PagedReplacementAvailability.APPLIED, prepared.availability)
        val projected = prepared.source as ProjectedChapterSource
        val first = projected.loadScrollUnitContent(units[0])
        val second = projected.loadScrollUnitContent(units[1])

        assertEquals("abQcd", first.displayText + second.displayText)
        // 滚动正文虽按 ReadingUnit 渲染，替换与持久化坐标仍以整段逻辑章 source 为准。
        assertEquals(102, first.localDisplayToGlobalSource(2))
        assertEquals(103, first.localDisplayToGlobalSource(3))
        assertEquals(2 to 3, first.globalSourceRangeToLocalDisplay(102, 105))
        assertEquals(0 to 2, second.globalSourceRangeToLocalDisplay(105, 107))
        assertEquals("logical chapter must be read and projected only once", 1, chapterReads)
        assertTrue(delegate.scopeForSegment(0) is ReplaceProjectionScope.Exact)
    }

    @Test
    fun `oversized logical chapter falls back to source without reading the full chapter`() {
        val chapter = "1234567"
        val units = listOf(unit(index = 0, start = 0, length = chapter.length))
        var fullChapterRead = false
        val delegate = ScrollingTxtChapterSource(
            units = units,
            totalChars = chapter.length,
            loadUnitText = { chapter },
            loadLogicalChapterText = {
                fullChapterRead = true
                chapter
            },
            maxProjectionChars = 6,
        )
        val prepared = preparePagedReplacement(
            delegate = delegate,
            bookId = "book",
            rules = listOf(replaceRule()),
            maxSourceLength = 6,
        )

        assertTrue(delegate.scopeForSegment(0) is ReplaceProjectionScope.UnsupportedTooLarge)
        assertEquals(chapter, prepared.source.loadScrollUnitContent(units[0]).displayText)
        assertFalse("oversized source must be rejected before full-chapter IO", fullChapterRead)
    }

    @Test
    fun `noncontiguous units expose incomplete scope and retain source text`() {
        val units = listOf(
            unit(index = 0, start = 0, length = 2),
            unit(index = 1, start = 3, length = 2),
        )
        val delegate = ScrollingTxtChapterSource(
            units = units,
            totalChars = 5,
            loadUnitText = { unit -> if (unit.unitIndex == 0) "ab" else "cd" },
            loadLogicalChapterText = { error("incomplete scope must not load a chapter") },
        )
        val prepared = preparePagedReplacement(
            delegate = delegate,
            bookId = "book",
            rules = listOf(replaceRule()),
        )

        assertTrue(delegate.scopeForSegment(0) is ReplaceProjectionScope.Incomplete)
        assertEquals("ab", prepared.source.loadScrollUnitContent(units[0]).displayText)
        assertEquals("cd", prepared.source.loadScrollUnitContent(units[1]).displayText)
    }

    private fun unit(index: Int, start: Int, length: Int) = ReadingUnit(
        unitIndex = index,
        chapterIndex = 0,
        title = "chapter",
        charStart = start,
        charCount = length,
    )

    private fun replaceRule() = ReplaceRule(
        id = "cross-unit",
        name = "cross-unit",
        pattern = "XYZ",
        replacement = "Q",
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )
}
