package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.annotations.groupByChapterInDocumentOrder
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 导出时间戳格式（文件名与文档头共用，保持一致）。 */
private val EXPORT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** SAF 建议文件名：`《书名》笔记-yyyyMMdd-HHmm.md`，非法文件名字符替换为下划线。 */
internal fun notesExportFileName(bookTitle: String, now: LocalDateTime): String {
    val safeTitle = bookTitle.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "未命名" }
    val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    return "《${safeTitle}》笔记-$stamp.md"
}

/**
 * 组装全书阅读笔记的 Markdown 导出内容：书摘（按章节分组，含批注）、
 * 笔记、书签、灵感四节；空节整体省略。纯函数，JVM 单测锁定结构。
 */
internal fun buildNotesExportMarkdown(
    bookTitle: String,
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
    inspirations: List<InspirationEntity>,
    exportedAt: LocalDateTime,
): String = buildString {
    appendLine("# 《${bookTitle.ifBlank { "未命名" } }》阅读笔记")
    appendLine()
    appendLine("> 导出于 ${exportedAt.format(EXPORT_TIME_FORMAT)}")
    appendLine()

    if (highlights.isNotEmpty()) {
        appendLine("## 书摘（共 ${highlights.size} 条）")
        appendLine()
        groupByChapterInDocumentOrder(
            highlights,
            chapterTitleOf = { it.chapter_title },
            chapterIndexOf = { LocatorCodec.decode(it.locator_json)?.chapterIndex },
            createdAtOf = { it.created_at },
        ).forEach { (chapter, items) ->
            appendLine("### ${chapter?.takeIf { it.isNotBlank() } ?: "未分类"}")
            appendLine()
            items.forEachIndexed { i, h ->
                appendLine("${i + 1}. ${h.text}")
                h.note?.takeIf { it.isNotBlank() }?.let { appendLine("   批注：$it") }
            }
            appendLine()
        }
    }

    val plainNotes = notes.filter { it.kind != "bookmark" }
    if (plainNotes.isNotEmpty()) {
        appendLine("## 笔记（共 ${plainNotes.size} 条）")
        appendLine()
        plainNotes.forEach { n ->
            appendLine("- **${n.title}**${if (n.body.isNotBlank()) "：${n.body}" else ""}")
            n.excerpt?.takeIf { it.isNotBlank() }?.let { appendLine("  > 摘录：$it") }
        }
        appendLine()
    }

    val bookmarks = notes.filter { it.kind == "bookmark" }
    if (bookmarks.isNotEmpty()) {
        appendLine("## 书签（共 ${bookmarks.size} 条）")
        appendLine()
        bookmarks.forEach { b ->
            appendLine("- ${b.title}")
            b.excerpt?.takeIf { it.isNotBlank() }?.let { appendLine("  > ${it}") }
        }
        appendLine()
    }

    if (inspirations.isNotEmpty()) {
        appendLine("## 灵感（共 ${inspirations.size} 条）")
        appendLine()
        inspirations.forEach { ins ->
            appendLine("### ${ins.title}")
            appendLine()
            if (ins.body.isNotBlank()) {
                appendLine(ins.body)
                appendLine()
            }
        }
    }
}
