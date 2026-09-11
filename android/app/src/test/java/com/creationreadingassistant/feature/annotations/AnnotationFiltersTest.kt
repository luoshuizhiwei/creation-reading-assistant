package com.creationreadingassistant.feature.annotations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一笔记筛选 / 排序 / 章节分组单测。
 * 重点回归：同书排序按 locator 文档序（chapterIndex → 章内偏移），
 * 不得按中文标题字典序（"第十章" 字典序在 "第二章" 之前，但文档序在后）。
 */
class AnnotationFiltersTest {

    private fun entry(
        stableId: String,
        type: AnnotationType = AnnotationType.HIGHLIGHT,
        bookId: String? = "b1",
        title: String? = null,
        excerpt: String? = "一段原文摘录",
        annotation: String? = null,
        chapterTitle: String? = "第一章",
        chapterIndex: Int? = 0,
        positionOffset: Int? = 0,
        createdAt: String = "2026-08-01T00:00:00Z",
    ) = AnnotationEntry(
        stableId = stableId,
        type = type,
        rawId = stableId,
        bookId = bookId,
        title = title,
        excerpt = excerpt,
        annotation = annotation,
        chapterTitle = chapterTitle,
        chapterIndex = chapterIndex,
        charOffset = positionOffset,
        legacyOffset = null,
        hasLocator = chapterIndex != null || positionOffset != null,
        color = null,
        progressPercent = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    // ---- 筛选 ----

    @Test
    fun `filter by type book and keyword intersect`() {
        val entries = listOf(
            entry("h1", bookId = "b1", excerpt = "星辰大海", annotation = "宇宙意象"),
            entry("h2", bookId = "b2", excerpt = "完全无关", annotation = null),
            entry("n1", type = AnnotationType.NOTE, bookId = "b1", excerpt = null, annotation = "关于星辰的批注"),
            entry("bm1", type = AnnotationType.BOOKMARK, bookId = "b1", chapterTitle = "星辰章"),
        )

        val typesOnly = filterAnnotations(entries, AnnotationFilterState(types = setOf(AnnotationType.HIGHLIGHT)))
        assertEquals(listOf("h1", "h2"), typesOnly.map { it.stableId })

        val bookOnly = filterAnnotations(entries, AnnotationFilterState(bookId = "b1"))
        assertEquals(listOf("h1", "n1", "bm1"), bookOnly.map { it.stableId })

        val keyword = filterAnnotations(entries, AnnotationFilterState(keyword = "星辰"))
        assertEquals(listOf("h1", "n1", "bm1"), keyword.map { it.stableId })

        val combined = filterAnnotations(
            entries,
            AnnotationFilterState(types = setOf(AnnotationType.HIGHLIGHT), bookId = "b1", keyword = "星辰"),
        )
        assertEquals(listOf("h1"), combined.map { it.stableId })
    }

    @Test
    fun `empty type set yields empty result but default filter keeps all`() {
        val entries = listOf(entry("h1"), entry("n1", type = AnnotationType.NOTE))
        assertTrue(AnnotationFilterState().isDefault)
        assertEquals(2, filterAnnotations(entries, AnnotationFilterState()).size)
        assertTrue(filterAnnotations(entries, AnnotationFilterState(types = emptySet())).isEmpty())
    }

    @Test
    fun `keyword match is case-insensitive and trimmed across all fields`() {
        val entries = listOf(
            entry("t1", title = "Journey To The West"),
            entry("e1", excerpt = "the EXCERPT text"),
            entry("a1", annotation = "the annotation"),
            entry("c1", chapterTitle = "The Beginning"),
        )
        val filter = AnnotationFilterState(keyword = "  the  ")
        assertEquals(4, filterAnnotations(entries, filter).size)
    }

    // ---- 排序 ----

    @Test
    fun `cross-book view sorts by creation time descending`() {
        val entries = listOf(
            entry("old", createdAt = "2026-08-01T00:00:00Z", chapterIndex = 0),
            entry("new", bookId = "b2", createdAt = "2026-09-01T00:00:00Z", chapterIndex = 0),
            entry("mid", createdAt = "2026-08-15T00:00:00Z", chapterIndex = 0),
        )
        val sorted = sortAnnotationEntries(entries, AnnotationFilterState())
        assertEquals(listOf("new", "mid", "old"), sorted.map { it.stableId })
    }

    @Test
    fun `single book sorts in document order not chinese dictionary order`() {
        // 字典序陷阱："第十章" < "第二章"；文档序应为 ci=1（第二章）在前
        val entries = listOf(
            entry("ch10", chapterTitle = "第十章", chapterIndex = 9, positionOffset = 5, createdAt = "2026-08-03T00:00:00Z"),
            entry("ch2-b", chapterTitle = "第二章", chapterIndex = 1, positionOffset = 200, createdAt = "2026-08-01T00:00:00Z"),
            entry("ch2-a", chapterTitle = "第二章", chapterIndex = 1, positionOffset = 100, createdAt = "2026-08-02T00:00:00Z"),
            entry("legacy", chapterTitle = "旧记录", chapterIndex = null, positionOffset = null, createdAt = "2026-08-10T00:00:00Z"),
        )
        val sorted = sortAnnotationEntries(entries, AnnotationFilterState(bookId = "b1"))
        // 文档序：第二章内偏移升序 → 第十章 → 无 locator 历史记录殿后
        assertEquals(listOf("ch2-a", "ch2-b", "ch10", "legacy"), sorted.map { it.stableId })
    }

    @Test
    fun `same chapter and offset breaks tie by newest creation`() {
        val entries = listOf(
            entry("older", chapterIndex = 2, positionOffset = 50, createdAt = "2026-08-01T00:00:00Z"),
            entry("newer", chapterIndex = 2, positionOffset = 50, createdAt = "2026-08-05T00:00:00Z"),
        )
        val sorted = sortAnnotationEntries(entries, AnnotationFilterState(bookId = "b1"))
        assertEquals(listOf("newer", "older"), sorted.map { it.stableId })
    }

    // ---- 章节分组 ----

    @Test
    fun `chapter groups follow document order and unindexed group goes last`() {
        val entries = listOf(
            entry("g10", chapterTitle = "第十章", chapterIndex = 9),
            entry("g2a", chapterTitle = "第二章", chapterIndex = 1, positionOffset = 30, createdAt = "2026-08-01T00:00:00Z"),
            entry("g2b", chapterTitle = "第二章", chapterIndex = 1, positionOffset = 80, createdAt = "2026-08-02T00:00:00Z"),
            entry("gnull", chapterTitle = "旧记录", chapterIndex = null, positionOffset = null, createdAt = "2026-08-20T00:00:00Z"),
        )
        val groups = chapterGroupsInDocumentOrder(entries)
        assertEquals(listOf("第二章", "第十章", "旧记录"), groups.map { it.first })
        assertEquals(listOf("g2a", "g2b"), groups.first { it.first == "第二章" }.second.map { it.stableId })
    }

    @Test
    fun `groups without chapter index order by newest created descending`() {
        val entries = listOf(
            entry("older-noindex", chapterTitle = "甲旧", chapterIndex = null, createdAt = "2026-08-01T00:00:00Z"),
            entry("newer-noindex", chapterTitle = "乙新", chapterIndex = null, createdAt = "2026-08-09T00:00:00Z"),
        )
        val groups = chapterGroupsInDocumentOrder(entries)
        assertEquals(listOf("乙新", "甲旧"), groups.map { it.first })
    }
}