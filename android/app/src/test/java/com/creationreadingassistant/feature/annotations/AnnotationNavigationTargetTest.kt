package com.creationreadingassistant.feature.annotations

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R1-N1.1 类型化回源定位：typed stableId 的构造、解析与跨表严格匹配。
 *
 * 覆盖：同一 rawId 高亮/普通笔记各命中正确实体、bookmark 与普通 note 不串台、
 * typed 类型不匹配不降级、裸 rawId 旧兼容语义、无 locator 不携带定位目标。
 */
class AnnotationNavigationTargetTest {

    private fun highlight(id: String = "x1") = HighlightEntity(
        id = id,
        book_id = "b1",
        text = "高亮原文",
        created_at = "2026-08-01T10:00:00Z",
        updated_at = "2026-08-01T10:00:00Z",
    )

    private fun note(id: String = "x1", kind: String = "note") = NoteEntity(
        id = id,
        book_id = "b1",
        title = "笔记标题",
        body = "笔记正文",
        kind = kind,
        created_at = "2026-08-01T10:00:00Z",
        updated_at = "2026-08-01T10:00:00Z",
    )

    private fun entry(type: AnnotationType, rawId: String, hasLocator: Boolean) = AnnotationEntry(
        stableId = "${type.name.lowercase()}:$rawId",
        type = type,
        rawId = rawId,
        bookId = "b1",
        title = null,
        excerpt = null,
        annotation = null,
        chapterTitle = null,
        chapterIndex = null,
        charOffset = null,
        legacyOffset = null,
        hasLocator = hasLocator,
        color = null,
        progressPercent = null,
        createdAt = "2026-08-01T10:00:00Z",
        updatedAt = "2026-08-01T10:00:00Z",
    )

    // ---- 构造 ----

    @Test
    fun `navigationTargetId prefixes type in lowercase`() {
        assertEquals("highlight:x1", navigationTargetId(AnnotationType.HIGHLIGHT, "x1"))
        assertEquals("note:x1", navigationTargetId(AnnotationType.NOTE, "x1"))
        assertEquals("bookmark:x1", navigationTargetId(AnnotationType.BOOKMARK, "x1"))
    }

    // ---- 解析 ----

    @Test
    fun `parse typed stable ids into typed target`() {
        assertEquals(
            AnnotationNavigationTarget(AnnotationType.HIGHLIGHT, "abc"),
            parseAnnotationNavigationTarget("highlight:abc"),
        )
        assertEquals(
            AnnotationNavigationTarget(AnnotationType.NOTE, "abc"),
            parseAnnotationNavigationTarget("note:abc"),
        )
        assertEquals(
            AnnotationNavigationTarget(AnnotationType.BOOKMARK, "abc"),
            parseAnnotationNavigationTarget("bookmark:abc"),
        )
    }

    @Test
    fun `parse returns null for bare raw id or unknown prefix`() {
        assertNull(parseAnnotationNavigationTarget("abc"))
        assertNull(parseAnnotationNavigationTarget("abc-123-def"))
        assertNull(parseAnnotationNavigationTarget("unknown:abc"))
        assertNull(parseAnnotationNavigationTarget(""))
        assertNull(parseAnnotationNavigationTarget(null))
        assertNull(parseAnnotationNavigationTarget(":abc")) // 空前缀
        assertNull(parseAnnotationNavigationTarget("highlight:")) // 空 id
    }

    // ---- 类型化严格匹配 ----

    @Test
    fun `same raw id across highlight and note hits the right entity`() {
        val h = highlight(id = "x1")
        val n = note(id = "x1", kind = "note")

        assertEquals(
            h,
            resolveAnnotationTarget(
                AnnotationNavigationTarget(AnnotationType.HIGHLIGHT, "x1"),
                "x1",
                listOf(h),
                listOf(n),
            ),
        )
        assertEquals(
            n,
            resolveAnnotationTarget(
                AnnotationNavigationTarget(AnnotationType.NOTE, "x1"),
                "x1",
                listOf(h),
                listOf(n),
            ),
        )
    }

    @Test
    fun `bookmark and plain note with same id never cross match`() {
        val bm = note(id = "x2", kind = "bookmark")
        val plain = note(id = "x2", kind = "note")

        assertEquals(
            bm,
            resolveAnnotationTarget(
                AnnotationNavigationTarget(AnnotationType.BOOKMARK, "x2"),
                "x2",
                emptyList(),
                listOf(plain, bm),
            ),
        )
        assertEquals(
            plain,
            resolveAnnotationTarget(
                AnnotationNavigationTarget(AnnotationType.NOTE, "x2"),
                "x2",
                emptyList(),
                listOf(plain, bm),
            ),
        )
    }

    @Test
    fun `typed target with missing or mismatched record does not fall back across tables`() {
        val h = highlight(id = "h-only")
        val n = note(id = "n-only", kind = "note")

        // 类型对但记录不存在 → null
        assertNull(
            resolveAnnotationTarget(
                AnnotationNavigationTarget(AnnotationType.HIGHLIGHT, "ghost"),
                "ghost",
                listOf(h),
                listOf(n),
            ),
        )
        // 类型不符（高亮目标却只存在于笔记表）→ null，不退回 notes
        assertNull(
            resolveAnnotationTarget(
                AnnotationNavigationTarget(AnnotationType.HIGHLIGHT, "n-only"),
                "n-only",
                listOf(h),
                listOf(n),
            ),
        )
        // 类型不符（note 目标却只存在于高亮表）→ null，不退回 highlights
        assertNull(
            resolveAnnotationTarget(
                AnnotationNavigationTarget(AnnotationType.NOTE, "h-only"),
                "h-only",
                listOf(h),
                listOf(n),
            ),
        )
        // bookmark 目标指向普通笔记 → null
        assertNull(
            resolveAnnotationTarget(
                AnnotationNavigationTarget(AnnotationType.BOOKMARK, "n-only"),
                "n-only",
                listOf(h),
                listOf(n),
            ),
        )
    }

    // ---- 裸 rawId 旧兼容语义 ----

    @Test
    fun `bare raw id keeps legacy highlight-then-note fallback`() {
        val h = highlight(id = "x3")
        val n = note(id = "x3", kind = "note")

        // 高亮优先
        assertEquals(h, resolveAnnotationTarget(null, "x3", listOf(h), listOf(n)))
        // 无高亮时退到笔记
        assertEquals(n, resolveAnnotationTarget(null, "x3", emptyList(), listOf(n)))
        // 都无 → null
        assertNull(resolveAnnotationTarget(null, "ghost", emptyList(), emptyList()))
    }

    // ---- 无 locator 降级 ----

    @Test
    fun `no locator does not carry a navigation target`() {
        assertNull(
            entry(AnnotationType.HIGHLIGHT, "x4", hasLocator = false).navigationTargetOrNull(),
        )
        assertNull(
            entry(AnnotationType.NOTE, "x4", hasLocator = false).navigationTargetOrNull(),
        )
    }

    @Test
    fun `with locator carries the typed stable id`() {
        assertEquals(
            "highlight:x4",
            entry(AnnotationType.HIGHLIGHT, "x4", hasLocator = true).navigationTargetOrNull(),
        )
        assertEquals(
            "bookmark:x4",
            entry(AnnotationType.BOOKMARK, "x4", hasLocator = true).navigationTargetOrNull(),
        )
    }
}