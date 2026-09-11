package com.creationreadingassistant.feature.annotations

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一条目模型单测：三类型投影、稳定 ID 类型前缀（不同表同 ID 不冲突）、
 * 摘录 / 批注分区、locator 解析与无 locator 降级字段。
 */
class AnnotationModelsTest {

    private fun highlight(
        id: String = "same-id",
        text: String = "高亮的原文",
        note: String? = "个人批注",
        locatorJson: String? = null,
        color: String? = "yellow",
    ) = HighlightEntity(
        id = id,
        book_id = "b1",
        text = text,
        note = note,
        color = color,
        chapter_title = "第二章",
        progress_percent = 0.4f,
        locator_json = locatorJson,
        created_at = "2026-08-01T10:00:00Z",
        updated_at = "2026-08-01T10:00:00Z",
    )

    private fun note(
        id: String = "same-id",
        kind: String = "note",
        title: String = "笔记标题",
        body: String = "笔记正文",
        excerpt: String? = "摘录的原文",
        locatorJson: String? = null,
    ) = NoteEntity(
        id = id,
        book_id = "b1",
        title = title,
        body = body,
        excerpt = excerpt,
        chapter_title = "第三章",
        progress_percent = 0.6f,
        kind = kind,
        locator_json = locatorJson,
        created_at = "2026-08-02T10:00:00Z",
        updated_at = "2026-08-02T10:00:00Z",
    )

    @Test
    fun `highlight projects excerpt and annotation separately`() {
        val entry = highlight().toAnnotationEntry()
        assertEquals(AnnotationType.HIGHLIGHT, entry.type)
        assertEquals("高亮的原文", entry.excerpt)
        assertEquals("个人批注", entry.annotation)
        assertEquals("yellow", entry.color)
        assertEquals("b1", entry.bookId)
        assertEquals("第二章", entry.chapterTitle)
    }

    @Test
    fun `note projects body as annotation and bookmark drops annotation`() {
        val noteEntry = note().toAnnotationEntry()
        assertEquals(AnnotationType.NOTE, noteEntry.type)
        assertEquals("摘录的原文", noteEntry.excerpt)
        assertEquals("笔记正文", noteEntry.annotation)

        val bookmark = note(kind = "bookmark", body = "书签正文不入批注").toAnnotationEntry()
        assertEquals(AnnotationType.BOOKMARK, bookmark.type)
        assertNull(bookmark.annotation)
    }

    @Test
    fun `stable ids carry type prefix so same raw id across tables never collides`() {
        val h = highlight(id = "x1").toAnnotationEntry()
        val n = note(id = "x1").toAnnotationEntry()
        val bm = note(id = "x1", kind = "bookmark").toAnnotationEntry()

        assertEquals("highlight:x1", h.stableId)
        assertEquals("note:x1", n.stableId)
        assertEquals("bookmark:x1", bm.stableId)
        assertEquals(3, setOf(h.stableId, n.stableId, bm.stableId).size)
        assertEquals("x1", h.rawId)
        assertEquals("x1", n.rawId)
    }

    @Test
    fun `blank fields are normalized to null`() {
        val entry = highlight(text = "  ", note = "  ", color = null).toAnnotationEntry()
        assertNull(entry.excerpt)
        assertNull(entry.annotation)
        assertNull(entry.color)

        val noteEntry = note(body = "  ", excerpt = "  ").toAnnotationEntry()
        assertNull(noteEntry.annotation)
        assertNull(noteEntry.excerpt)
    }

    @Test
    fun `locator v2 decodes into chapter and offsets`() {
        val locator = """{"v":2,"offset":1200,"ci":3,"co":45,"fp":"aabbccdd"}"""
        val entry = highlight(locatorJson = locator).toAnnotationEntry()
        assertTrue(entry.hasLocator)
        assertEquals(3, entry.chapterIndex)
        assertEquals(45, entry.charOffset)
        assertEquals(1200, entry.legacyOffset)
        assertEquals(45, entry.positionOffset)
    }

    @Test
    fun `positionOffset falls back to legacyOffset for txt`() {
        val locator = """{"v":2,"offset":9999}"""
        val entry = highlight(locatorJson = locator).toAnnotationEntry()
        assertTrue(entry.hasLocator)
        assertNull(entry.chapterIndex)
        assertNull(entry.charOffset)
        assertEquals(9999, entry.legacyOffset)
        assertEquals(9999, entry.positionOffset)
    }

    @Test
    fun `missing or undecodable locator degrades without fabricating offsets`() {
        val entry = highlight(locatorJson = null).toAnnotationEntry()
        assertFalse(entry.hasLocator)
        assertNull(entry.chapterIndex)
        assertNull(entry.charOffset)
        assertNull(entry.legacyOffset)
        assertNull(entry.positionOffset)

        val broken = highlight(locatorJson = "not-json").toAnnotationEntry()
        assertFalse(broken.hasLocator)
    }

    @Test
    fun `buildAnnotationEntries merges both tables without dropping records`() {
        val entries = buildAnnotationEntries(
            highlights = listOf(highlight(id = "h1"), highlight(id = "h2")),
            notes = listOf(note(id = "n1"), note(id = "bm1", kind = "bookmark")),
        )
        assertEquals(4, entries.size)
        assertEquals(
            listOf(AnnotationType.HIGHLIGHT, AnnotationType.HIGHLIGHT, AnnotationType.NOTE, AnnotationType.BOOKMARK),
            entries.map { it.type },
        )
    }
}