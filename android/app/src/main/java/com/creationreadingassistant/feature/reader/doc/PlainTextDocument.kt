package com.creationreadingassistant.feature.reader.doc

/**
 * TXT / Markdown 的 [ReaderDocument] 实现。
 *
 * 全文已在内存里（TXT 有 8 MB 上限），所以章节偏移与字符数都是**精确值**，
 * 不像 EPUB 侧那样需要估算。
 *
 * 段落切分规则：按空行分段；单个换行视为段内换行，转成一个空格。
 * 这样诗歌、书信不会被拆成一堆碎块，而普通小说的自然段又能正确分开。
 */
class PlainTextDocument(
    private val fullText: String,
    private val tocRuleId: String = "builtin",
) : ReaderDocument {

    private val detected = TxtChapterDetector.detect(fullText, tocRuleId)

    override val chapters: List<DocChapter> = detected.mapIndexed { i, c ->
        DocChapter(
            index = i,
            title = c.title,
            startOffset = c.startOffset,
            charCount = c.charCount,
            charCountIsEstimated = false,
        )
    }

    override val totalChars: Int = fullText.length

    override fun blocks(chapterIndex: Int): List<DocBlock> {
        val c = detected.getOrNull(chapterIndex) ?: return emptyList()
        val raw = fullText.substring(c.startOffset, c.endOffset.coerceAtMost(fullText.length))
        return splitParagraphs(raw)
    }

    /** 章内纯文本。这里直接复用与 [blocks] 相同的切分，保证接口的不变式成立。 */
    override fun text(chapterIndex: Int): String =
        blocks(chapterIndex).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }

    private fun splitParagraphs(raw: String): List<DocBlock> {
        val out = ArrayList<DocBlock>()
        val sb = StringBuilder()
        var pendingBlank = false
        var first = true

        fun flush(isHeading: Boolean) {
            val t = sb.toString().trim()
            sb.setLength(0)
            if (t.isNotEmpty()) out.add(DocBlock.Text(t, isHeading))
        }

        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                pendingBlank = true
                continue
            }
            // 章节标题独占一块，并标记为 heading —— 排版层据此加大字号、做 keep-with-next
            if (first && TxtChapterDetector.isChapterTitle(trimmed, tocRuleId)) {
                flush(false)
                out.add(DocBlock.Text(trimmed, isHeading = true))
                first = false
                pendingBlank = false
                continue
            }
            first = false
            if (pendingBlank) {
                flush(false)
                pendingBlank = false
            } else if (sb.isNotEmpty()) {
                // 段内换行：中文正文不需要空格，直接接上；西文之间留一个空格
                val last = sb.last()
                if (last.isLetterOrDigit() && trimmed.first().isLetterOrDigit() &&
                    (last.code < 0x2E80 || trimmed.first().code < 0x2E80)
                ) {
                    sb.append(' ')
                }
            }
            sb.append(trimmed)
        }
        flush(false)
        return out
    }
}
