/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.markdown

import local.creationReadingAssistant.reader.legado.text.TextChapter

data class MarkdownReaderDocument(
    val text: String,
    val chapters: List<TextChapter>,
)

/**
 * Converts local Markdown into stable, readable native text without creating a
 * WebView DOM. Block semantics are retained for pagination, search and TOC;
 * inline formatting markers are removed while their visible content remains.
 */
object MarkdownBookParser {
    private const val MAX_CHAPTERS = 20_000
    private val atxHeading = Regex("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$")
    private val setextHeading = Regex("^\\s{0,3}(=+|-+)\\s*$")
    private val fencedCode = Regex("^\\s{0,3}(`{3,}|~{3,}).*$")
    private val horizontalRule = Regex("^\\s{0,3}(?:([-*_])\\s*){3,}$")
    private val tableSeparator = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$")
    private val taskItem = Regex("""^(\s*)[-+*]\s+\[([ xX])]\s+(.*)$""")
    private val unorderedItem = Regex("^(\\s*)[-+*]\\s+(.*)$")
    private val blockQuote = Regex("^\\s{0,3}>\\s?(.*)$")
    private val image = Regex("""!\[([^]]*)]\(([^)]+)\)""")
    private val link = Regex("""\[([^]]+)]\(([^)]+)\)""")
    private val inlineCode = Regex("`([^`]+)`")
    private val htmlTag = Regex("<[^>]+>")

    fun parse(markdown: String): MarkdownReaderDocument {
        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return MarkdownReaderDocument("", emptyList())
        val lines = normalized.split('\n')
        val output = StringBuilder(normalized.length)
        val chapters = arrayListOf<TextChapter>()
        var inCodeBlock = false
        var lineIndex = 0

        fun appendBlock(value: String) {
            if (value.isBlank()) {
                if (output.isNotEmpty() && !output.endsWith("\n\n")) output.append('\n')
                return
            }
            output.append(value.trimEnd()).append('\n')
        }

        fun appendHeading(title: String, level: Int) {
            val visible = normalizeInline(title).trim()
            if (visible.isBlank()) return
            if (output.isNotEmpty() && !output.endsWith("\n\n")) output.append('\n')
            val offset = output.length
            output.append(visible).append("\n\n")
            if (chapters.size < MAX_CHAPTERS) {
                chapters += TextChapter(chapters.size, visible.take(120), offset, level.coerceIn(1, 6))
            }
        }

        while (lineIndex < lines.size) {
            val line = lines[lineIndex]
            if (fencedCode.matches(line)) {
                inCodeBlock = !inCodeBlock
                if (!inCodeBlock) appendBlock("")
                lineIndex += 1
                continue
            }
            if (inCodeBlock) {
                appendBlock("    $line")
                lineIndex += 1
                continue
            }

            val atx = atxHeading.matchEntire(line)
            if (atx != null) {
                appendHeading(atx.groupValues[2], atx.groupValues[1].length)
                lineIndex += 1
                continue
            }
            if (lineIndex + 1 < lines.size) {
                val setext = setextHeading.matchEntire(lines[lineIndex + 1])
                if (setext != null && line.isNotBlank()) {
                    appendHeading(line, if (setext.groupValues[1].startsWith('=')) 1 else 2)
                    lineIndex += 2
                    continue
                }
            }
            if (horizontalRule.matches(line)) {
                appendBlock("────────")
                lineIndex += 1
                continue
            }
            if (tableSeparator.matches(line)) {
                lineIndex += 1
                continue
            }

            val task = taskItem.matchEntire(line)
            if (task != null) {
                val checked = task.groupValues[2].isNotBlank()
                appendBlock("${task.groupValues[1]}${if (checked) "☑" else "☐"} ${normalizeInline(task.groupValues[3])}")
                lineIndex += 1
                continue
            }
            val bullet = unorderedItem.matchEntire(line)
            if (bullet != null) {
                appendBlock("${bullet.groupValues[1]}• ${normalizeInline(bullet.groupValues[2])}")
                lineIndex += 1
                continue
            }
            val quote = blockQuote.matchEntire(line)
            if (quote != null) {
                appendBlock("│ ${normalizeInline(quote.groupValues[1])}")
                lineIndex += 1
                continue
            }
            if (line.count { it == '|' } >= 2) {
                val cells = line.trim().trim('|').split('|').map { normalizeInline(it).trim() }
                appendBlock(cells.joinToString(" ｜ "))
                lineIndex += 1
                continue
            }

            appendBlock(normalizeInline(line))
            lineIndex += 1
        }

        val text = output.toString().trimEnd()
        val normalizedChapters = when {
            text.isBlank() -> emptyList()
            chapters.isEmpty() -> listOf(TextChapter(0, "正文", 0))
            chapters.first().startOffset > 0 -> listOf(TextChapter(0, "开始", 0)) + chapters
            else -> chapters
        }.mapIndexed { index, chapter -> chapter.copy(index = index) }
        return MarkdownReaderDocument(text, normalizedChapters)
    }

    private fun normalizeInline(value: String): String {
        return value
            .replace(image) { match ->
                val label = match.groupValues[1].trim().ifBlank { "插图" }
                "[图片：$label]"
            }
            .replace(link) { match ->
                val label = match.groupValues[1].trim()
                val target = match.groupValues[2].trim()
                if (target.isBlank() || target == label) label else "$label（$target）"
            }
            .replace(inlineCode, "$1")
            .replace("**", "")
            .replace("__", "")
            .replace("~~", "")
            .replace(Regex("(?<!\\*)\\*(?!\\*)"), "")
            .replace(Regex("(?<!_)_(?!_)"), "")
            .replace(htmlTag, "")
            .replace("\\*", "*")
            .replace("\\_", "_")
            .replace("\\#", "#")
            .trimEnd()
    }
}
