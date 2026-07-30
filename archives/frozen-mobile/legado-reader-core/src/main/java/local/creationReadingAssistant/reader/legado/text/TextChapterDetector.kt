/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.text

data class TextChapter(
    val index: Int,
    val title: String,
    val startOffset: Int,
    val level: Int = 1,
)

/** Lightweight heading scanner for local TXT books. */
object TextChapterDetector {
    private const val MAX_TITLE_LENGTH = 80
    private const val MAX_CHAPTERS = 20_000
    private val chineseHeading = Regex(
        "^(?:正文\\s*)?(?:第[0-9零一二三四五六七八九十百千万两〇○]+[卷部篇章回节集幕话折]|序章|序言|楔子|引子|前言|后记|尾声|终章|番外|序)(?:[\\s　:：·.-]*.*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val numberedHeading = Regex(
        "^(?:[0-9]{1,4}|[一二三四五六七八九十百千]+)[.、)）．]\\s*.*$",
        RegexOption.IGNORE_CASE,
    )
    private val parenHeading = Regex(
        "^[（(][0-9一二三四五六七八九十百千]+[)）]\\s*.*$",
    )
    private val englishHeading = Regex(
        "^(?:chapter|part|volume|book)\\s+[0-9ivxlcdm]+(?:[\\s:：.-]+.*)?$",
        RegexOption.IGNORE_CASE,
    )

    fun detect(content: String): List<TextChapter> {
        if (content.isBlank()) return emptyList()
        val chapters = ArrayList<TextChapter>()
        var lineStart = 0
        var cursor = 0
        while (cursor <= content.length && chapters.size < MAX_CHAPTERS) {
            val atEnd = cursor == content.length
            val char = if (atEnd) '\n' else content[cursor]
            if (char == '\n' || char == '\r') {
                val raw = content.substring(lineStart, cursor).trim()
                if (isHeading(raw)) {
                    chapters += TextChapter(chapters.size, raw, lineStart)
                }
                if (!atEnd && char == '\r' && cursor + 1 < content.length && content[cursor + 1] == '\n') cursor += 1
                lineStart = cursor + 1
            }
            cursor += 1
        }
        if (chapters.isEmpty()) {
            return listOf(TextChapter(0, "正文", 0))
        }
        if (chapters.first().startOffset > 0) {
            chapters.add(0, TextChapter(0, "开始", 0))
        }
        return chapters.mapIndexed { index, chapter -> chapter.copy(index = index) }
    }

    private fun isHeading(value: String): Boolean {
        if (value.isBlank() || value.length > MAX_TITLE_LENGTH) return false
        return chineseHeading.matches(value) || englishHeading.matches(value)
            || numberedHeading.matches(value) || parenHeading.matches(value)
    }
}
