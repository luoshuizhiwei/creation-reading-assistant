package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability
import com.creationreadingassistant.feature.reader.pager.loadScrollUnitContent
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReplacementCapabilityTest {

    @Test
    fun `scroll capability and rendered body share the prepared replacement source`() {
        val rules = listOf(
            ReplaceRule(
                id = "replace-literal",
                name = "replace-literal",
                pattern = "before",
                replacement = "after",
                enabled = true,
                position = 0,
                scope = RuleScope.PER_BOOK,
            ),
        )
        val units = listOf(
            com.creationreadingassistant.feature.reader.doc.ReadingUnit(
                unitIndex = 0,
                chapterIndex = 0,
                title = "测试 TXT",
                charStart = 0,
                charCount = "before body".length,
            ),
        )

        val prepared = requireNotNull(
            prepareScrollTxtReplacement(
                bookId = "book-under-test",
                streamingDocument = null,
                plainContent = "before body",
                readingUnits = units,
                rules = rules,
            ),
        )

        assertEquals(PagedReplacementAvailability.APPLIED, prepared.availability)
        assertEquals(
            "after body",
            prepared.source.loadScrollUnitContent(units.single()).displayText,
        )
    }

    // ── 直接基于 PagedReplacementAvailability（生产推荐路径） ─

    @Test
    fun `availability APPLIED produces Available capability`() {
        val cap = readerReplacementCapability(PagedReplacementAvailability.APPLIED)
        assertEquals(ReaderReplacementCapability.Available, cap)
    }

    @Test
    fun `availability SOURCE_UNAVAILABLE produces blocked when pager is off or not yet built`() {
        val cap = readerReplacementCapability(PagedReplacementAvailability.SOURCE_UNAVAILABLE)
        assertTrue(cap is ReaderReplacementCapability.Unavailable)
    }

    @Test
    fun `availability INCOMPLETE_SCOPE is blocked for segmented virtual units`() {
        val cap = readerReplacementCapability(PagedReplacementAvailability.INCOMPLETE_SCOPE)
        assertTrue(cap is ReaderReplacementCapability.Unavailable)
    }

    @Test
    fun `startup notice for incomplete scope describes document capability instead of assuming large file`() {
        val notice = readerReplacementStartupNotice(
            availability = PagedReplacementAvailability.INCOMPLETE_SCOPE,
            pagerEngineOn = true,
        )

        assertEquals("当前章节视图暂不支持正文替换净化，正文已保留原文。", notice)
        assertTrue(notice?.contains("流式大文件") == false)
    }

    @Test
    fun `startup replacement notice is not repeated after rotation restores consumed state`() {
        assertEquals(
            null,
            readerReplacementStartupNoticeIfNeeded(
                availability = PagedReplacementAvailability.ESTIMATED_COORDINATES,
                pagerEngineOn = true,
                alreadyShown = true,
            ),
        )
    }

    @Test
    fun `availability ESTIMATED_COORDINATES is blocked for EPUB and Markdown`() {
        val cap = readerReplacementCapability(PagedReplacementAvailability.ESTIMATED_COORDINATES)
        assertTrue(cap is ReaderReplacementCapability.Unavailable)
    }

    @Test
    fun `availability NON_SOURCE_COORDINATES is blocked for rendered Markdown`() {
        val cap = readerReplacementCapability(PagedReplacementAvailability.NON_SOURCE_COORDINATES)
        assertTrue(cap is ReaderReplacementCapability.Unavailable)
    }

    @Test
    fun `availability OVERSIZED_CURRENT_CHAPTER is blocked with message`() {
        val cap = readerReplacementCapability(PagedReplacementAvailability.OVERSIZED_CURRENT_CHAPTER)
        assertTrue(cap is ReaderReplacementCapability.Unavailable)
    }

    @Test
    fun `availability NO_EFFECTIVE_RULES keeps replacement management available`() {
        val cap = readerReplacementCapability(PagedReplacementAvailability.NO_EFFECTIVE_RULES)
        assertEquals(ReaderReplacementCapability.Available, cap)
    }

    @Test
    fun `legacy scroll stays blocked while complete-projection scroll uses prepared capability`() {
        assertEquals(
            PagedReplacementAvailability.PAGER_ENGINE_DISABLED,
            effectiveReplacementAvailability(
                pagerEngineOn = false,
                prepared = PagedReplacementAvailability.APPLIED,
            ),
        )
        assertEquals(
            PagedReplacementAvailability.APPLIED,
            effectiveReplacementAvailability(
                pagerEngineOn = false,
                prepared = PagedReplacementAvailability.APPLIED,
                scrollProjectionOn = true,
            ),
        )
        assertEquals(
            PagedReplacementAvailability.NO_EFFECTIVE_RULES,
            effectiveReplacementAvailability(
                pagerEngineOn = false,
                prepared = PagedReplacementAvailability.NO_EFFECTIVE_RULES,
                scrollProjectionOn = true,
            ),
        )
        assertEquals(
            PagedReplacementAvailability.APPLIED,
            effectiveReplacementAvailability(
                pagerEngineOn = true,
                prepared = PagedReplacementAvailability.APPLIED,
            ),
        )
        assertEquals(
            PagedReplacementAvailability.SOURCE_UNAVAILABLE,
            effectiveReplacementAvailability(pagerEngineOn = true, prepared = null),
        )
    }

    @Test
    fun `applied scrolling projection never shows a pager-only startup notice`() {
        assertEquals(
            null,
            readerReplacementStartupNotice(
                availability = PagedReplacementAvailability.APPLIED,
                pagerEngineOn = false,
            ),
        )
    }

    // ── 兼容旧入口（带 isTxt / readerMode / pagerEngineOn 显式参数的派生版） ──

    @Test
    fun `small TXT with active paged engine can manage replacement rules via legacy entry`() {
        val capability = readerReplacementCapability(
            isTxt = true,
            hasStreamingDocument = false,
            readerMode = "paged",
            pagerEngineOn = true,
            replaceProjectionScopeIsComplete = true,
        )
        assertEquals(ReaderReplacementCapability.Available, capability)
    }

    @Test
    fun `scroll or legacy reader keeps source text and blocks replacement management via legacy entry`() {
        val scroll = readerReplacementCapability(
            isTxt = true,
            hasStreamingDocument = false,
            readerMode = "scroll",
            pagerEngineOn = false,
        )
        val legacyPaged = readerReplacementCapability(
            isTxt = true,
            hasStreamingDocument = false,
            readerMode = "paged",
            pagerEngineOn = false,
        )
        assertTrue(
            "scroll must be Unavailable",
            scroll is ReaderReplacementCapability.Unavailable
        )
        assertTrue(
            "legacyPaged must be Unavailable",
            legacyPaged is ReaderReplacementCapability.Unavailable
        )
    }

    @Test
    fun `complete-projection streaming available while incomplete scope stays blocked via legacy entry`() {
        val complete = readerReplacementCapability(
            isTxt = true,
            hasStreamingDocument = true,
            readerMode = "paged",
            pagerEngineOn = true,
            replaceProjectionScopeIsComplete = true,
        )
        assertEquals(ReaderReplacementCapability.Available, complete)

        val incomplete = readerReplacementCapability(
            isTxt = true,
            hasStreamingDocument = true,
            readerMode = "paged",
            pagerEngineOn = true,
            replaceProjectionScopeIsComplete = false,
        )
        assertTrue(
            "incomplete scope must be Unavailable",
            incomplete is ReaderReplacementCapability.Unavailable
        )
    }

    @Test
    fun `EPUB and Markdown keep source text even in paged mode via legacy entry`() {
        val capability = readerReplacementCapability(
            isTxt = false,
            hasStreamingDocument = false,
            readerMode = "paged",
            pagerEngineOn = true,
        )
        assertTrue(
            "non-TXT must be Unavailable",
            capability is ReaderReplacementCapability.Unavailable
        )
    }
}
