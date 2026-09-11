package com.creationreadingassistant.feature.annotations

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 导出时间戳格式（文件名与文档头共用）。 */
private val EXPORT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private val EXPORT_FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

/** SAF 建议文件名：`阅读笔记-yyyyMMdd-HHmm.md`（跨书批量导出，无单一书名）。 */
fun annotationsExportFileName(now: LocalDateTime): String =
    "阅读笔记-${now.format(EXPORT_FILE_STAMP)}.md"

/**
 * 「我的 → 阅读笔记」批量导出 Markdown：只导出**选中的条目**。
 *
 * 结构：文档头（导出时间 + 条目计数）→ 按书分组（书名升序仅作分组稳定性，
 * 组内条目按调用方给定的顺序，通常为文档顺序或时间倒序）→ 每条含
 * 章节来源、原文摘录（> 引用块）、个人批注、创建时间。
 * 缺书（bookId 为空或映射缺失）归「未关联书籍」组，缺章显示「未记录章节」。
 * 书摘 / 批注 / 书签三类型分别标注；特殊字符原样输出（Markdown 引用块内
 * 不再做转义，保持原文保真）。
 */
fun buildAnnotationsExportMarkdown(
    entries: List<AnnotationEntry>,
    bookTitles: Map<String, String>,
    exportedAt: LocalDateTime,
): String = buildString {
    appendLine("# 阅读笔记")
    appendLine()
    appendLine("> 导出于 ${exportedAt.format(EXPORT_TIME_FORMAT)} · 共 ${entries.size} 条")
    appendLine()

    val typeLabel: (AnnotationType) -> String = {
        when (it) {
            AnnotationType.HIGHLIGHT -> "书摘"
            AnnotationType.NOTE -> "批注"
            AnnotationType.BOOKMARK -> "书签"
        }
    }

    // 缺书（bookId 为空或映射缺失）统一归并到同一个「未关联书籍」组
    entries
        .groupBy { it.bookId?.takeIf { id -> id in bookTitles } }
        .entries
        .sortedBy { (key, _) -> key?.let { bookTitles[it] } ?: "" }
        .forEach { (key, bookEntries) ->
            val bookTitle = key?.let { bookTitles[it] } ?: "未关联书籍"
            appendLine("## 《$bookTitle》")
            appendLine()
            bookEntries.forEachIndexed { index, entry ->
                appendLine("### ${index + 1}. [${typeLabel(entry.type)}] ${entry.chapterTitle ?: "未记录章节"}")
                appendLine()
                entry.excerpt?.takeIf { it.isNotBlank() }?.let {
                    appendLine("> $it".replace("\n", "\n> "))
                    appendLine()
                }
                entry.annotation?.takeIf { it.isNotBlank() }?.let {
                    appendLine("批注：$it")
                }
                if (entry.type == AnnotationType.BOOKMARK) {
                    entry.title?.takeIf { it.isNotBlank() }?.let { appendLine("书签：$it") }
                }
                appendLine("记录于 ${entry.createdAt.take(10)}")
                appendLine()
            }
        }
}