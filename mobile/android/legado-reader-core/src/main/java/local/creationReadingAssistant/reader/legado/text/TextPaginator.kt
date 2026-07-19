/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Text measurement and punctuation rules are adapted from Luoyacheng/legado-E
 * at 21855a7bf901becfd1caba5cf30a3c84fd1533e1.
 */
package local.creationReadingAssistant.reader.legado.text

import android.text.TextPaint
import local.creationReadingAssistant.reader.legado.layout.TextMeasure
import kotlin.math.max
import kotlin.math.min

data class TextPaginationConfig(
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val lineHeightPx: Float,
    val paragraphSpacingPx: Float,
    val indentPx: Float,
)

/**
 * Deterministic, offset-based paginator. It never uses scrollLeft or CSS column
 * rounding, so the same content/settings/viewport produce the same boundaries.
 */
class TextPaginator(
    private val content: String,
    private val paint: TextPaint,
    private val config: TextPaginationConfig,
) {
    companion object {
        private val prohibitedAtLineStart = hashSetOf(
            '，', '。', '：', '？', '！', '、', '”', '’', '）', '》', '】', ')', '>', ']', '}',
            ',', '.', '?', '!', ':', '」', '；', ';',
        )
        private val prohibitedAtLineEnd = hashSetOf('“', '（', '《', '【', '‘', '(', '<', '[', '{', '「')
        private const val MEASURE_WINDOW_CHARS = 768
    }

    private val measure = TextMeasure(paint)

    init {
        require(config.contentWidthPx > 0)
        require(config.contentHeightPx > 0)
        require(config.lineHeightPx > 0f)
    }

    fun pageStartingAt(requestedOffset: Int): TextPage {
        if (content.isEmpty()) return TextPage(0, 0, emptyList())
        var cursor = requestedOffset.coerceIn(0, content.length)
        if (cursor >= content.length) cursor = max(0, content.length - 1)
        val pageStart = cursor
        val lines = arrayListOf<TextLine>()
        var usedHeight = 0f

        while (cursor < content.length) {
            if (usedHeight + config.lineHeightPx > config.contentHeightPx && lines.isNotEmpty()) break

            val newlineLength = newlineLengthAt(cursor)
            if (newlineLength > 0) {
                lines.add(TextLine("", cursor, cursor + newlineLength, paragraphEnd = true))
                cursor += newlineLength
                usedHeight += config.lineHeightPx + config.paragraphSpacingPx
                continue
            }

            val paragraphEnd = findParagraphEnd(cursor)
            var firstLine = isParagraphStart(cursor)

            while (cursor < paragraphEnd) {
                if (usedHeight + config.lineHeightPx > config.contentHeightPx && lines.isNotEmpty()) {
                    return TextPage(pageStart, cursor, lines)
                }
                val indent = if (firstLine) config.indentPx else 0f
                val availableWidth = max(1f, config.contentWidthPx - indent)
                val lineEnd = findLineEnd(cursor, paragraphEnd, availableWidth)
                val safeEnd = if (lineEnd > cursor) lineEnd else nextCodePointOffset(cursor)
                val reachesParagraphEnd = safeEnd >= paragraphEnd
                lines.add(
                    TextLine(
                        text = content.substring(cursor, safeEnd),
                        startOffset = cursor,
                        endOffset = safeEnd,
                        indentPx = indent,
                        paragraphEnd = reachesParagraphEnd,
                    ),
                )
                cursor = safeEnd
                usedHeight += config.lineHeightPx
                firstLine = false
                if (reachesParagraphEnd) usedHeight += config.paragraphSpacingPx
            }

            if (cursor >= paragraphEnd && cursor < content.length) {
                cursor += newlineLengthAt(cursor)
            }
        }

        if (cursor <= pageStart && cursor < content.length) cursor = nextCodePointOffset(cursor)
        return TextPage(pageStart, cursor.coerceAtMost(content.length), lines)
    }

    /**
     * Finds the previous page by replaying deterministic boundaries from a
     * bounded probe. During normal reading the view's history cache is exact;
     * this path is used only after restoring an arbitrary locator.
     */
    fun previousPageBefore(currentStart: Int, estimatedPageChars: Int): TextPage? {
        if (currentStart <= 0 || content.isEmpty()) return null
        var probe = max(0, currentStart - max(estimatedPageChars * 4, 1024))
        probe = alignToCodePoint(probe)
        var previous: TextPage? = null
        var guard = 0
        while (probe < currentStart && guard < 128) {
            val page = pageStartingAt(probe)
            if (page.endOffset >= currentStart) break
            previous = page
            if (page.endOffset <= probe) break
            probe = page.endOffset
            guard++
        }
        if (previous != null) return previous

        // The probe landed inside the immediately preceding page. Widen once
        // and replay so a restored locator can still navigate backward.
        if (probe > 0) {
            var wideProbe = max(0, currentStart - max(estimatedPageChars * 12, 4096))
            wideProbe = alignToCodePoint(wideProbe)
            var candidate: TextPage? = null
            guard = 0
            while (wideProbe < currentStart && guard < 512) {
                val page = pageStartingAt(wideProbe)
                if (page.endOffset >= currentStart) break
                candidate = page
                if (page.endOffset <= wideProbe) break
                wideProbe = page.endOffset
                guard++
            }
            return candidate
        }
        return null
    }

    private fun findLineEnd(start: Int, paragraphEnd: Int, availableWidth: Float): Int {
        val sampleEnd = min(paragraphEnd, start + MEASURE_WINDOW_CHARS)
        val sample = content.substring(start, sampleEnd)
        val (words, widths) = measure.measureTextSplit(sample)
        if (words.isEmpty()) return start

        var usedWidth = 0f
        var wordCount = 0
        while (wordCount < words.size) {
            val nextWidth = widths[wordCount]
            if (wordCount > 0 && usedWidth + nextWidth > availableWidth) break
            usedWidth += nextWidth
            wordCount++
            if (usedWidth >= availableWidth) break
        }
        if (wordCount == 0) wordCount = 1

        // Avoid an opening punctuation at the end of the current line.
        while (wordCount > 1 && words[wordCount - 1].singleOrNull() in prohibitedAtLineEnd) {
            wordCount--
        }

        // Pull one or two closing punctuation marks into the current line when
        // the overhang stays below half an em, matching Chinese reading habits.
        var overhang = 0f
        var pulled = 0
        while (wordCount < words.size && pulled < 2 &&
            words[wordCount].singleOrNull() in prohibitedAtLineStart) {
            overhang += widths[wordCount]
            if (usedWidth + overhang > availableWidth + paint.textSize * 0.5f) break
            wordCount++
            pulled++
        }

        var consumedChars = 0
        for (index in 0 until wordCount) consumedChars += words[index].length
        return (start + consumedChars).coerceAtMost(paragraphEnd)
    }

    private fun findParagraphEnd(start: Int): Int {
        var cursor = start
        while (cursor < content.length && content[cursor] != '\n' && content[cursor] != '\r') cursor++
        return cursor
    }

    private fun isParagraphStart(offset: Int): Boolean {
        if (offset <= 0) return true
        return content[offset - 1] == '\n' || content[offset - 1] == '\r'
    }

    private fun newlineLengthAt(offset: Int): Int {
        if (offset !in content.indices) return 0
        return when (content[offset]) {
            '\r' -> if (offset + 1 < content.length && content[offset + 1] == '\n') 2 else 1
            '\n' -> 1
            else -> 0
        }
    }

    private fun nextCodePointOffset(offset: Int): Int {
        if (offset >= content.length) return content.length
        val first = content[offset]
        return if (first.isHighSurrogate() && offset + 1 < content.length && content[offset + 1].isLowSurrogate()) {
            offset + 2
        } else {
            offset + 1
        }
    }

    private fun alignToCodePoint(offset: Int): Int {
        if (offset <= 0 || offset >= content.length) return offset.coerceIn(0, content.length)
        return if (content[offset].isLowSurrogate() && content[offset - 1].isHighSurrogate()) offset - 1 else offset
    }
}
