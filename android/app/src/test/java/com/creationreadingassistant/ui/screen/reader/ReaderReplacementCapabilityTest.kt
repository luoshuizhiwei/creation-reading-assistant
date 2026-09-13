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
        assertEquals(ReaderReplacementCapability.Available(), cap)
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
        assertEquals(ReaderReplacementCapability.Available(), cap)
    }

    @Test
    fun `all oversized scopes keep rules manageable with a persistent body notice`() {
        val cap = readerReplacementCapability(PagedReplacementAvailability.ALL_SCOPES_OVERSIZED)

        assertTrue(cap is ReaderReplacementCapability.Available)
        assertEquals(
            readerReplacementStartupNotice(PagedReplacementAvailability.ALL_SCOPES_OVERSIZED, pagerEngineOn = false),
            (cap as ReaderReplacementCapability.Available).bodyNotice,
        )
        assertEquals(cap.bodyNotice, replacementRulesTabBodyNotice(cap))
        assertTrue(cap.bodyNotice?.contains("不会对正文生效") == true)
    }

    @Test
    fun `partial replacement keeps rules manageable with a persistent body notice`() {
        val cap = readerReplacementCapability(PagedReplacementAvailability.PARTIALLY_APPLIED)

        assertTrue(cap is ReaderReplacementCapability.Available)
        assertEquals(
            readerReplacementStartupNotice(PagedReplacementAvailability.PARTIALLY_APPLIED, pagerEngineOn = false),
            (cap as ReaderReplacementCapability.Available).bodyNotice,
        )
        assertEquals(cap.bodyNotice, replacementRulesTabBodyNotice(cap))
        assertTrue(cap.bodyNotice?.contains("部分章节") == true)
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

    // ── 能力分类的穷尽性：防止新状态静默落到「可替换」 ──

    /**
     * 每一个 [PagedReplacementAvailability] 都必须被显式分类。
     *
     * 这是「不得伪装可用」的回归闸门：将来新增一个 availability 常量时，
     * 本用例会因为期望表里查不到而失败，逼迫作者明确回答「正文到底能不能替换」，
     * 而不是让它顺着兜底分支默认变成可用。
     *
     * 同时锁定另一半约束：诚实禁用的状态**必须**给出非空原因，
     * 不允许出现「按钮灰着但不说为什么」。
     */
    @Test
    fun `every availability is explicitly classified as manageable or honestly disabled`() {
        val manageable = setOf(
            PagedReplacementAvailability.APPLIED,
            PagedReplacementAvailability.NO_EFFECTIVE_RULES,
            PagedReplacementAvailability.PARTIALLY_APPLIED,
            PagedReplacementAvailability.ALL_SCOPES_OVERSIZED,
            PagedReplacementAvailability.UNVERIFIED_CHAPTER_LENGTHS,
        )
        val honestlyDisabled = setOf(
            PagedReplacementAvailability.PAGER_ENGINE_DISABLED,
            PagedReplacementAvailability.ESTIMATED_COORDINATES,
            PagedReplacementAvailability.NON_SOURCE_COORDINATES,
            PagedReplacementAvailability.INCOMPLETE_SCOPE,
            PagedReplacementAvailability.OVERSIZED_CURRENT_CHAPTER,
            PagedReplacementAvailability.SOURCE_UNAVAILABLE,
        )

        assertEquals(
            "新增 availability 必须显式分类，不能默认变成可用",
            PagedReplacementAvailability.entries.toSet(),
            manageable + honestlyDisabled,
        )

        manageable.forEach { availability ->
            assertTrue(
                "$availability 应允许管理规则",
                readerReplacementCapability(availability) is ReaderReplacementCapability.Available,
            )
        }
        honestlyDisabled.forEach { availability ->
            val capability = readerReplacementCapability(availability)
            assertTrue(
                "$availability 必须诚实禁用并给出原因",
                capability is ReaderReplacementCapability.Unavailable &&
                    capability.message.isNotBlank(),
            )
        }
    }
}
