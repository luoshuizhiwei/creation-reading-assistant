package com.creationreadingassistant.feature.annotations

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的 → 阅读笔记」批量导出 Markdown 单测：只导出传入（选中）条目、
 * 按书分组保留来源、三类型标注、特殊字符原样保真。
 */
class AnnotationExportTest {

    private val exportedAt = LocalDateTime.of(2026, 9, 9, 10, 30)

    private fun entry(
        stableId: String,
        type: AnnotationType = AnnotationType.HIGHLIGHT,
        bookId: String? = "b1",
        excerpt: String? = "原文摘录",
        annotation: String? = "个人批注",
        chapterTitle: String? = "第一章",
        createdAt: String = "2026-08-01T10:00:00Z",
    ) = AnnotationEntry(
        stableId = stableId,
        type = type,
        rawId = stableId,
        bookId = bookId,
        title = "书签标题",
        excerpt = excerpt,
        annotation = annotation,
        chapterTitle = chapterTitle,
        chapterIndex = 0,
        charOffset = 10,
        legacyOffset = null,
        hasLocator = true,
        color = "yellow",
        progressPercent = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `exports only the given selected entries`() {
        val md = buildAnnotationsExportMarkdown(
            entries = listOf(entry("h1"), entry("n1", type = AnnotationType.NOTE)),
            bookTitles = mapOf("b1" to "书甲"),
            exportedAt = exportedAt,
        )
        assertTrue(md.startsWith("# 阅读笔记"))
        assertTrue(md.contains("> 导出于 2026-09-09 10:30 · 共 2 条"))
        assertTrue(md.contains("## 《书甲》"))
        assertFalse(md.contains("未选中的内容"))
    }

    @Test
    fun `type labels chapter fallback and fields structure`() {
        val md = buildAnnotationsExportMarkdown(
            entries = listOf(
                entry("h1", type = AnnotationType.HIGHLIGHT),
                entry("n1", type = AnnotationType.NOTE, chapterTitle = null),
                entry("bm1", type = AnnotationType.BOOKMARK, excerpt = null, annotation = null, chapterTitle = "第二章"),
            ),
            bookTitles = mapOf("b1" to "书甲"),
            exportedAt = exportedAt,
        )
        assertTrue(md.contains("### 1. [书摘] 第一章"))
        assertTrue(md.contains("> 原文摘录"))
        assertTrue(md.contains("批注：个人批注"))
        assertTrue(md.contains("### 2. [批注] 未记录章节"))
        assertTrue(md.contains("### 3. [书签] 第二章"))
        assertTrue(md.contains("书签：书签标题"))
        assertTrue(md.contains("记录于 2026-08-01"))
    }

    @Test
    fun `entries without book fall into unlinked group`() {
        val md = buildAnnotationsExportMarkdown(
            entries = listOf(entry("h1", bookId = null), entry("h2", bookId = "missing")),
            bookTitles = mapOf("missing-id" to "不相关"),
            exportedAt = exportedAt,
        )
        // bookId null 与映射缺失合并进同一个「未关联书籍」组（各出现一次，共 1 组）
        assertEquals(1, Regex("未关联书籍").findAll(md).count())
        assertTrue(md.contains("共 2 条"))
    }

    @Test
    fun `special characters are preserved verbatim`() {
        val tricky = "含*星号*、#井号、[方括号]、<尖括号> 与换行\n第二行"
        val md = buildAnnotationsExportMarkdown(
            entries = listOf(entry("h1", excerpt = tricky)),
            bookTitles = mapOf("b1" to "书甲"),
            exportedAt = exportedAt,
        )
        assertTrue(md.contains("含*星号*、#井号、[方括号]、<尖括号> 与换行"))
        assertTrue(md.contains("> 第二行"))
    }

    @Test
    fun `multi-line excerpt keeps quote prefix on every line`() {
        val md = buildAnnotationsExportMarkdown(
            entries = listOf(entry("h1", excerpt = "第一行\n第二行")),
            bookTitles = mapOf("b1" to "书甲"),
            exportedAt = exportedAt,
        )
        assertTrue(md.contains("> 第一行\n> 第二行"))
    }

    @Test
    fun `export file name has stamp and extension`() {
        val name = annotationsExportFileName(LocalDateTime.of(2026, 9, 9, 10, 30))
        assertEquals("阅读笔记-20260909-1030.md", name)
    }
}