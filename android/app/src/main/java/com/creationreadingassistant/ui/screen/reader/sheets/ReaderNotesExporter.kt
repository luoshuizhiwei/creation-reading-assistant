package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.annotations.groupByChapterInDocumentOrder
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 导出时间戳格式（文件名与文档头共用，保持一致）。 */
private val EXPORT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** SAF 建议文件名：`《书名》笔记-yyyyMMdd-HHmm.md`，非法文件名字符替换为下划线。 */
internal fun notesExportFileName(bookTitle: String, now: LocalDateTime): String {
    val safeTitle = bookTitle.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "未命名" }
    val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    return "《${safeTitle}》笔记-$stamp.md"
}

/** 灵感 payload 解析口径与 InspirationViewModel 相同（ignoreUnknownKeys + encodeDefaults）。 */
private val inspirationPayloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 标签内会被 Markdown 解释为强调/链接/HTML/代码/删除线的字符。 */
private val MARKDOWN_INLINE_SPECIALS = setOf('\\', '*', '_', '[', ']', '<', '>', '`', '~')

/** 只用于标签的最小行内转义：反斜杠先被转义，避免二次解释。 */
private fun escapeMarkdownInline(text: String): String = buildString(text.length) {
    text.forEach { ch ->
        if (ch in MARKDOWN_INLINE_SPECIALS) append('\\')
        append(ch)
    }
}

/**
 * 导出用标签规范化（只作用于标签，不改正文/摘录/来源）：
 * - 去掉首尾空白，空白标签整体丢弃；
 * - 单个标签内的换行与连续空白折成单个空格，标签因此不会破坏 Markdown 段落结构；
 * - 按首次出现去重并保持顺序稳定；
 * - 对结果做最小 Markdown 行内转义，避免标签被当成强调/链接/HTML/代码。
 *
 * 返回空列表表示这条灵感没有可导出的标签，调用方不得输出空「标签：」行。
 */
internal fun normalizeExportTags(tags: List<String>): List<String> {
    val seen = HashSet<String>()
    val result = ArrayList<String>(tags.size)
    tags.forEach { raw ->
        val folded = raw.replace(Regex("""\s+"""), " ").trim()
        if (folded.isEmpty()) return@forEach
        if (!seen.add(folded)) return@forEach
        result += escapeMarkdownInline(folded)
    }
    return result
}

/**
 * 组装全书阅读笔记的 Markdown 导出内容：书摘（按章节分组，含批注）、
 * 笔记、书签、灵感四节；空节整体省略。纯函数，JVM 单测锁定结构。
 *
 * 灵感节来源补全：逐条输出 payload.source 与 payload.excerpts 的来源快照——
 * 书名/作者/章节/位置头行、原文摘录引用（多行完整保留在引用区）、locator 原始 JSON。
 * 来源数据全部来自 payload 快照，不查当前书库；重复来源按 InspirationSourceInfo
 * 完整数据类相等语义去重并稳定保留首次出现顺序，不丢任何非重复来源。
 * 个人正文（body）与原文摘录始终分区。
 *
 * 标签输出（93-v1-B 新增）：payload.tags 在正文与来源之外单独成行
 * 「标签：a、b」，规范化规则见 [normalizeExportTags]；无标签时不输出空行或空标题，
 * 不输出 categoryIds，也不改动正文/摘录/来源的既有渲染。
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
    val plainNotes = notes.filter { it.kind != "bookmark" }
    val bookmarks = notes.filter { it.kind == "bookmark" }
    val summaryItems = mutableListOf<String>()
    if (highlights.isNotEmpty()) summaryItems.add("${highlights.size} 条书摘")
    if (plainNotes.isNotEmpty()) summaryItems.add("${plainNotes.size} 条笔记")
    if (bookmarks.isNotEmpty()) summaryItems.add("${bookmarks.size} 条书签")
    if (inspirations.isNotEmpty()) summaryItems.add("${inspirations.size} 条灵感")
    if (summaryItems.isNotEmpty()) {
        appendLine("> 汇总统计：${summaryItems.joinToString(" · ")}")
    }
    appendLine()

    if (highlights.isNotEmpty()) {
        appendLine("---")
        appendLine()
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

    if (plainNotes.isNotEmpty()) {
        appendLine("---")
        appendLine()
        appendLine("## 笔记（共 ${plainNotes.size} 条）")
        appendLine()
        plainNotes.forEach { n ->
            appendLine("- **${n.title}**${if (n.body.isNotBlank()) "：${n.body}" else ""}")
            n.excerpt?.takeIf { it.isNotBlank() }?.let { appendLine("  > 摘录：$it") }
        }
        appendLine()
    }

    val bookmarksList = notes.filter { it.kind == "bookmark" }
    if (bookmarksList.isNotEmpty()) {
        appendLine("---")
        appendLine()
        appendLine("## 书签（共 ${bookmarksList.size} 条）")
        appendLine()
        bookmarksList.forEach { b ->
            appendLine("- ${b.title}")
            b.excerpt?.takeIf { it.isNotBlank() }?.let { appendLine("  > ${it}") }
        }
        appendLine()
    }

    if (inspirations.isNotEmpty()) {
        appendLine("---")
        appendLine()
        appendLine("## 灵感（共 ${inspirations.size} 条）")
        appendLine()
        inspirations.forEach { ins ->
            appendLine("### ${ins.title}")
            appendLine()
            if (ins.body.isNotBlank()) {
                appendLine(ins.body)
                appendLine()
            }
            if (!ins.payload.isNullOrBlank()) {
                val payload = try {
                    inspirationPayloadJson.decodeFromString(InspirationPayloadData.serializer(), ins.payload)
                } catch (e: SerializationException) {
                    throw SerializationException(
                        "灵感「${ins.title.ifBlank { "未命名灵感" }}」(${ins.id})的来源数据损坏，无法完整导出：${e.message}",
                        e,
                    )
                }
                val exportTags = normalizeExportTags(payload.tags)
                if (exportTags.isNotEmpty()) {
                    appendLine("标签：${exportTags.joinToString("、")}")
                    appendLine()
                }
                val sources = buildList {
                    payload.source?.let { add(it) }
                    addAll(payload.excerpts)
                }
                val seen = HashSet<InspirationSourceInfo>()
                sources.forEach { src ->
                    if (!seen.add(src)) return@forEach

                    val parts = buildList {
                        val title = src.bookTitle?.takeIf { it.isNotBlank() }
                        if (title != null) add("《$title》") else add("未记录书名")
                        src.bookAuthor?.takeIf { it.isNotBlank() }?.let { add(it) }
                        src.chapterTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
                        src.locationLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
                        src.progressPercent?.let { add(String.format(java.util.Locale.US, "%.1f%%", it)) }
                    }
                    appendLine("来源：${parts.joinToString(" · ")}")

                    src.excerpt?.takeIf { it.isNotBlank() }?.let { excerptText ->
                        val lines = excerptText.lines()
                        lines.forEachIndexed { index, line ->
                            if (index == 0) {
                                appendLine("> 摘录：$line")
                            } else if (line.isEmpty()) {
                                appendLine(">")
                            } else {
                                appendLine("> $line")
                            }
                        }
                    }

                    src.locatorJson?.takeIf { it.isNotBlank() }?.let { loc ->
                        appendLine("位置：$loc")
                    }
                    appendLine()
                }
            }
        }
    }
}
