package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定阅读笔记导出 Markdown 的结构：书摘按章节分组（含批注）、笔记与书签按 kind
 * 拆分、灵感独立成节、空节整体省略；文件名对非法字符做净化。
 */
class NotesExportTest {

    private val exportedAt = LocalDateTime.of(2026, 8, 16, 9, 30)

    private fun highlight(
        id: String,
        text: String,
        chapter: String? = null,
        note: String? = null,
        locatorJson: String? = null,
    ) = HighlightEntity(
        id = id, book_id = "b1", text = text, note = note,
        chapter_title = chapter, locator_json = locatorJson,
        created_at = "2026-08-01T00:00:00Z", updated_at = "2026-08-01T00:00:00Z",
    )

    private fun note(
        id: String,
        title: String,
        body: String = "",
        excerpt: String? = null,
        kind: String = "note",
    ) = NoteEntity(
        id = id, book_id = "b1", title = title, body = body, excerpt = excerpt,
        kind = kind, created_at = "2026-08-01T00:00:00Z", updated_at = "2026-08-01T00:00:00Z",
    )

    private fun inspiration(id: String, title: String, body: String) = InspirationEntity(
        id = id, title = title, body = body,
        created_at = "2026-08-01T00:00:00Z", updated_at = "2026-08-01T00:00:00Z",
    )

    @Test
    fun `full export contains all four sections with counts and grouping`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = listOf(
                highlight("h1", "第一句", chapter = "第二章", locatorJson = """{"v":2,"offset":100,"ci":1,"co":10}"""),
                highlight("h2", "第二句", chapter = "第一章", locatorJson = """{"v":2,"offset":0,"ci":0,"co":5}"""),
                highlight("h3", "带批注的句子", chapter = "第一章", note = "这里写得好", locatorJson = """{"v":2,"offset":10,"ci":0,"co":80}"""),
                highlight("h4", "无章节的句子"),
            ),
            notes = listOf(
                note("n1", "伏笔", body = "注意主角的怀表", excerpt = "他摸了摸怀表"),
                note("bm1", "第三章开头", excerpt = "三年后", kind = "bookmark"),
            ),
            inspirations = listOf(inspiration("i1", "如果", "如果怀表是倒着走的呢？")),
            exportedAt = exportedAt,
        )

        assertTrue(md.startsWith("# 《测试书》阅读笔记"))
        assertTrue(md.contains("> 导出于 2026-08-16 09:30"))

        // 书摘：计数 + 章节按文档序分组（locator ci 升序）+ 批注缩进 + 无 locator 归「未分类」殿后
        assertTrue(md.contains("## 书摘（共 4 条）"))
        val ch1 = md.indexOf("### 第一章")
        val ch2 = md.indexOf("### 第二章")
        val unclassified = md.indexOf("### 未分类")
        assertTrue(ch1 in 0 until ch2) // ci=0 的第一章先于 ci=1 的第二章（非中文标题字典序）
        assertTrue(ch2 in 0 until unclassified) // 无 locator 的章节组排最后
        assertTrue(md.contains("1. 第二句"))
        assertTrue(md.contains("2. 带批注的句子"))
        assertTrue(md.contains("   批注：这里写得好"))
        assertTrue(md.contains("### 未分类"))
        assertTrue(md.contains("1. 无章节的句子"))

        // 笔记与书签按 kind 拆分
        assertTrue(md.contains("## 笔记（共 1 条）"))
        assertTrue(md.contains("- **伏笔**：注意主角的怀表"))
        assertTrue(md.contains("  > 摘录：他摸了摸怀表"))
        assertTrue(md.contains("## 书签（共 1 条）"))
        assertTrue(md.contains("- 第三章开头"))
        assertFalse(md.contains("**第三章开头**")) // 书签不进笔记节

        // 灵感
        assertTrue(md.contains("## 灵感（共 1 条）"))
        assertTrue(md.contains("### 如果"))
        assertTrue(md.contains("如果怀表是倒着走的呢？"))
    }

    @Test
    fun `empty sections are omitted`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "只有笔记",
            highlights = emptyList(),
            notes = listOf(note("n1", "一条", body = "内容")),
            inspirations = emptyList(),
            exportedAt = exportedAt,
        )
        assertTrue(md.contains("## 笔记（共 1 条）"))
        assertFalse(md.contains("书摘"))
        assertFalse(md.contains("书签"))
        assertFalse(md.contains("灵感"))
    }

    @Test
    fun `blank book title falls back to 未命名`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = emptyList(),
            exportedAt = exportedAt,
        )
        assertTrue(md.startsWith("# 《未命名》阅读笔记"))
    }

    @Test
    fun `export file name sanitizes illegal characters`() {
        val name = notesExportFileName("测书名/含:非*法?\"字符<>|", exportedAt)
        assertTrue(name.endsWith(".md"))
        assertFalse(name.any { it in setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|') })
        assertTrue(name.startsWith("《测"))
        assertTrue(name.contains("20260816-0930"))
    }

    @Test
    fun `blank title file name falls back`() {
        val name = notesExportFileName("  ", exportedAt)
        assertTrue(name.startsWith("《未命名》笔记-"))
    }
}
