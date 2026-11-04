package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.annotations.AnnotationType
import com.creationreadingassistant.feature.annotations.navigationTargetId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A01：目录与标记 →「笔记」Tab 的**数据源与 typed 回源目标**（纯逻辑，无 Compose）。
 *
 * 用户可见缺陷：当前书存在未删除高亮，打开「目录与标记 → 笔记」仍显示
 * 「暂无划线与笔记」。根因是生产宿主只把书签传给了 `TocSheet`，`notes` 走
 * `emptyList()` 默认值，高亮**根本没有参数入口**（`NotesTabContent` 只收 `List<NoteEntity>`）。
 *
 * 本文件把「哪些条目进入哪个 Tab、生成哪个 typed 目标」提取成可被 JVM 执行的契约：
 * - [tocNoteEntries]：笔记 Tab 的数据源（高亮 + 非书签笔记，书签不得混入）；
 * - [highlightNavigationTarget] / [noteNavigationTarget] / [noteAnnotationType]：
 *   三种类型的回源目标。
 *
 * 这些是**行为/契约测试**，不是源码字符串守卫。
 */
class TocNoteEntriesTest {

    private fun highlight(id: String, text: String = "摘录") = HighlightEntity(
        id = id,
        book_id = "book-1",
        text = text,
        created_at = "2026-09-21T00:00:00",
        updated_at = "2026-09-21T00:00:00",
    )

    private fun note(id: String, kind: String = "note", title: String = "标题") = NoteEntity(
        id = id,
        title = title,
        body = "正文",
        kind = kind,
        created_at = "2026-09-21T00:00:00",
        updated_at = "2026-09-21T00:00:00",
    )

    // ── 1. 只有高亮：进入笔记 Tab，不显示空态 ─────────────────────────

    @Test
    fun `highlights alone populate the notes tab`() {
        val entries = tocNoteEntries(
            highlights = listOf(highlight("h1"), highlight("h2")),
            notes = emptyList(),
        )

        assertEquals("两条高亮必须进入笔记 Tab", 2, entries.size)
        assertTrue("条目必须全是高亮类型", entries.all { it is TocNoteEntry.Highlight })
        assertFalse("有高亮时不得显示空态", entries.isEmpty())
    }

    @Test
    fun `a highlight produces a HIGHLIGHT typed navigation target`() {
        val entries = tocNoteEntries(highlights = listOf(highlight("h1")), notes = emptyList())

        val entry = entries.single()
        assertEquals(AnnotationType.HIGHLIGHT, entry.type)
        assertEquals("h1", entry.rawId)
        assertEquals(
            navigationTargetId(AnnotationType.HIGHLIGHT, "h1"),
            entry.navigationTarget,
        )
        assertEquals("highlight:h1", entry.navigationTarget)
    }

    // ── 2. 只有普通笔记：进入笔记 Tab，不显示空态 ─────────────────────

    @Test
    fun `plain notes alone populate the notes tab`() {
        val entries = tocNoteEntries(highlights = emptyList(), notes = listOf(note("n1")))

        assertEquals(1, entries.size)
        assertTrue(entries.single() is TocNoteEntry.Note)
        assertFalse(entries.isEmpty())
    }

    @Test
    fun `a plain note produces a NOTE typed navigation target`() {
        val entries = tocNoteEntries(highlights = emptyList(), notes = listOf(note("n1")))

        val entry = entries.single()
        assertEquals(AnnotationType.NOTE, entry.type)
        assertEquals("n1", entry.rawId)
        assertEquals("note:n1", entry.navigationTarget)
    }

    // ── 3. 只有书签：书签 Tab 独占，笔记 Tab 仍为空 ───────────────────

    @Test
    fun `bookmarks never enter the notes tab`() {
        val entries = tocNoteEntries(
            highlights = emptyList(),
            notes = listOf(note("b1", kind = "bookmark")),
        )

        assertTrue("书签必须留在书签 Tab，不得重复进入笔记 Tab", entries.isEmpty())
    }

    @Test
    fun `a bookmark keeps its own BOOKMARK typed target`() {
        // 书签 Tab 的条目由 bookmarks 列表直接渲染，其 typed 目标经 noteNavigationTarget 生成。
        assertEquals(
            "bookmark:b1",
            noteNavigationTarget(note("b1", kind = "bookmark")),
        )
        assertEquals(AnnotationType.BOOKMARK, noteAnnotationType(note("b1", kind = "bookmark")))
    }

    @Test
    fun `bookmarks and plain notes are separated by kind not by id`() {
        val entries = tocNoteEntries(
            highlights = emptyList(),
            notes = listOf(
                note("x", kind = "bookmark"),
                note("y", kind = "note"),
                note("z", kind = "bookmark"),
            ),
        )

        assertEquals("只有非书签笔记进入笔记 Tab", listOf("y"), entries.map { it.rawId })
    }

    // ── 4. 高亮与普通笔记同时存在：两类都显示 ─────────────────────────

    @Test
    fun `highlights and plain notes are both shown`() {
        val entries = tocNoteEntries(
            highlights = listOf(highlight("h1")),
            notes = listOf(note("n1"), note("b1", kind = "bookmark")),
        )

        assertEquals("高亮 + 非书签笔记都要在", 2, entries.size)
        assertEquals(setOf(AnnotationType.HIGHLIGHT, AnnotationType.NOTE), entries.map { it.type }.toSet())
        assertFalse("有内容时不得显示空态", entries.isEmpty())
    }

    // ── 5. 跨表相同 raw ID：节点 key 不冲突、类型不串 ─────────────────

    @Test
    fun `the same raw id across tables yields distinct list keys`() {
        // 高亮与笔记是两张表，UUID 撞车在理论上有可能；LazyColumn key 必须带类型前缀。
        val entries = tocNoteEntries(
            highlights = listOf(highlight("same-id")),
            notes = listOf(note("same-id")),
        )

        assertEquals(2, entries.size)
        val keys = entries.map { it.listKey }
        assertEquals("跨表相同 raw ID 必须产生不同的列表 key", 2, keys.toSet().size)
        assertEquals(setOf("highlight:same-id", "note:same-id"), keys.toSet())
    }

    @Test
    fun `the same raw id across tables keeps its own typed target`() {
        val entries = tocNoteEntries(
            highlights = listOf(highlight("same-id")),
            notes = listOf(note("same-id")),
        )

        val highlightEntry = entries.single { it.type == AnnotationType.HIGHLIGHT }
        val noteEntry = entries.single { it.type == AnnotationType.NOTE }

        assertEquals("highlight:same-id", highlightEntry.navigationTarget)
        assertEquals("note:same-id", noteEntry.navigationTarget)
        assertTrue(
            "同 raw ID 跨表必须产生不同 typed 目标，否则会跳错表",
            highlightEntry.navigationTarget != noteEntry.navigationTarget,
        )
    }

    @Test
    fun `a bookmark sharing a raw id with a highlight still stays out of the notes tab`() {
        // 书签与高亮同 raw ID：书签仍不得进入笔记 Tab，且两者 typed 目标不同。
        val entries = tocNoteEntries(
            highlights = listOf(highlight("dup")),
            notes = listOf(note("dup", kind = "bookmark")),
        )

        assertEquals(listOf(AnnotationType.HIGHLIGHT), entries.map { it.type })
        assertTrue(
            noteNavigationTarget(note("dup", kind = "bookmark")) !=
                highlightNavigationTarget(highlight("dup")),
        )
    }

    // ── 6. 两者都为空：才是空态 ───────────────────────────────────────

    @Test
    fun `the empty state is produced only when both sources are empty`() {
        assertTrue(
            "高亮与普通笔记都为空时才允许空态",
            tocNoteEntries(highlights = emptyList(), notes = emptyList()).isEmpty(),
        )
        assertFalse(
            "只有书签时笔记 Tab 仍为空（书签不算笔记 Tab 的内容）",
            tocNoteEntries(highlights = emptyList(), notes = listOf(note("b", kind = "bookmark")))
                .isNotEmpty(),
        )
    }

    // ── 7. 类型化回源目标：与既有 resolver 口径一致 ──────────────────

    @Test
    fun `every typed target round trips through the shared resolver parser`() {
        // 生成端（本文件）与解析端（AnnotationNavigationTarget）必须同一口径，
        // 否则阅读器消费 pendingHighlightId 时会解析不出类型。
        val targets = listOf(
            highlightNavigationTarget(highlight("h1")),
            noteNavigationTarget(note("n1")),
            noteNavigationTarget(note("b1", kind = "bookmark")),
        )

        assertEquals(
            listOf(
                AnnotationType.HIGHLIGHT to "h1",
                AnnotationType.NOTE to "n1",
                AnnotationType.BOOKMARK to "b1",
            ),
            targets.map { target ->
                val parsed = com.creationreadingassistant.feature.annotations
                    .parseAnnotationNavigationTarget(target)
                requireNotNull(parsed) { "typed 目标必须能被解析：$target" }
                parsed.type to parsed.rawId
            },
        )
    }

    @Test
    fun `a plain note target never resolves against the highlight table`() {
        // 跨表串位防线：note:{id} 只能匹配 notes 表里 kind != bookmark 的记录。
        val target = noteNavigationTarget(note("same-id"))
        val parsed = com.creationreadingassistant.feature.annotations
            .parseAnnotationNavigationTarget(target)
        val resolved = com.creationreadingassistant.feature.annotations.resolveAnnotationTarget(
            typed = parsed,
            rawParam = requireNotNull(parsed).rawId,
            highlights = listOf(highlight("same-id")),
            notes = listOf(note("same-id")),
        )

        assertTrue("note: 目标必须解析到笔记实体", resolved is NoteEntity)
        assertEquals("same-id", (resolved as NoteEntity).id)
        assertEquals("note", resolved.kind)
    }

    @Test
    fun `a highlight target never resolves against the notes table`() {
        val target = highlightNavigationTarget(highlight("same-id"))
        val parsed = com.creationreadingassistant.feature.annotations
            .parseAnnotationNavigationTarget(target)
        val resolved = com.creationreadingassistant.feature.annotations.resolveAnnotationTarget(
            typed = parsed,
            rawParam = requireNotNull(parsed).rawId,
            highlights = listOf(highlight("same-id")),
            notes = listOf(note("same-id")),
        )

        assertTrue("highlight: 目标必须解析到高亮实体", resolved is HighlightEntity)
    }

    // ── 8. 顺序稳定：不做额外排序、不引入新查询 ──────────────────────

    @Test
    fun `entry order follows the incoming list order`() {
        // 数据源顺序由 DAO 的 ORDER BY created_at DESC 决定；本函数不得再排序，
        // 否则会出现「列表顺序与数据库顺序不一致」的第二套真相。
        val entries = tocNoteEntries(
            highlights = listOf(highlight("h2"), highlight("h1")),
            notes = listOf(note("n2"), note("n1")),
        )

        assertEquals(listOf("h2", "h1", "n2", "n1"), entries.map { it.rawId })
    }
}
