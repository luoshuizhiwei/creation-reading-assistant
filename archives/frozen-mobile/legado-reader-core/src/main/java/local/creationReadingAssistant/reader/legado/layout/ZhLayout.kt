/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Adapted from Luoyacheng/legado-E at 21855a7bf901becfd1caba5cf30a3c84fd1533e1.
 */
package local.creationReadingAssistant.reader.legado.layout

import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.text.Layout
import android.text.TextPaint
import java.util.WeakHashMap
import kotlin.math.max

/** Chinese punctuation-aware line breaker adapted from Legado. */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class ZhLayout(
    text: CharSequence,
    textPaint: TextPaint,
    width: Int,
    words: List<String>,
    widths: List<Float>,
    indentSize: Int,
) : Layout(text, textPaint, width, Alignment.ALIGN_NORMAL, 0f, 0f) {
    companion object {
        private val prohibitedAtLineStart = hashSetOf(
            "，", "。", "：", "？", "！", "、", "”", "’", "）", "》", "}",
            "】", ")", ">", "]", ",", ".", "?", "!", ":", "」", "；", ";",
        )
        private val prohibitedAtLineEnd = hashSetOf(
            "“", "（", "《", "【", "‘", "(", "<", "[", "{", "「",
        )
        private val cjkWidthCache = WeakHashMap<Paint, Float>()
    }

    private val growth = 10
    private val currentPaint = textPaint
    private val cjkWidth = cjkWidthCache[textPaint]
        ?: desiredWidth("我", textPaint).also { cjkWidthCache[textPaint] = it }

    private var internalLineCount = 0
    var lineStarts = IntArray(growth)
        private set
    var lineWidths = FloatArray(growth)
        private set

    private enum class BreakMode { NORMAL, MOVE_ONE, MOVE_MANY, COMPRESS_1, COMPRESS_2, COMPRESS_3 }

    init {
        require(words.size == widths.size) { "words and widths must have the same size" }
        if (words.isEmpty()) {
            lineStarts[0] = 0
            lineStarts[1] = 0
            lineWidths[0] = 0f
            internalLineCount = 1
        } else {
            layoutLines(words, widths, width, indentSize)
        }
    }

    private fun layoutLines(words: List<String>, widths: List<Float>, maxWidth: Int, indentSize: Int) {
        var line = 0
        var lineWidth = 0f
        var previousWidth = 0f
        var textLength = 0

        words.forEachIndexed { index, word ->
            val currentWidth = widths[index]
            var mode = BreakMode.NORMAL
            var shouldBreak = false
            lineWidth += currentWidth
            var carryWidth = 0f
            var movedCharacters = 0

            if (lineWidth > maxWidth) {
                mode = when {
                    index >= 1 && isProhibitedAtLineEnd(words[index - 1]) -> {
                        if (index >= 2 && isProhibitedAtLineEnd(words[index - 2])) BreakMode.COMPRESS_2
                        else BreakMode.MOVE_ONE
                    }
                    isProhibitedAtLineStart(words[index]) -> {
                        when {
                            index >= 1 && isProhibitedAtLineStart(words[index - 1]) -> BreakMode.COMPRESS_1
                            index >= 2 && isProhibitedAtLineEnd(words[index - 2]) -> BreakMode.COMPRESS_3
                            else -> BreakMode.MOVE_ONE
                        }
                    }
                    else -> BreakMode.NORMAL
                }

                var needsSafeBreak = false
                var movedWordCount = 0
                if (mode == BreakMode.COMPRESS_1 &&
                    (isCompressible(widths[index]) || isCompressible(widths[index - 1]))) needsSafeBreak = true
                if (mode == BreakMode.COMPRESS_2 &&
                    (isCompressible(widths[index - 1]) || isCompressible(widths[index - 2]))) needsSafeBreak = true
                if (mode == BreakMode.COMPRESS_3 &&
                    (isCompressible(widths[index]) || isCompressible(widths[index - 2]))) needsSafeBreak = true
                if (mode > BreakMode.MOVE_MANY && index < words.lastIndex &&
                    isProhibitedAtLineStart(words[index + 1])) needsSafeBreak = true

                var movedTextLength = 0
                if (needsSafeBreak && index > 2) {
                    val start = if (line == 0) indentSize else getLineStart(line)
                    mode = BreakMode.NORMAL
                    for (cursor in index downTo (1 + start)) {
                        if (cursor == index) {
                            movedWordCount = 0
                            previousWidth = 0f
                        } else {
                            movedWordCount++
                            movedTextLength += words[cursor].length
                            previousWidth += widths[cursor]
                        }
                        if (!isProhibitedAtLineStart(words[cursor]) &&
                            !isProhibitedAtLineEnd(words[cursor - 1])) {
                            mode = BreakMode.MOVE_MANY
                            break
                        }
                    }
                }

                when (mode) {
                    BreakMode.NORMAL -> {
                        carryWidth = currentWidth
                        ensureLineCapacity(line + 1)
                        lineStarts[line + 1] = textLength
                        movedCharacters = 1
                    }
                    BreakMode.MOVE_ONE -> {
                        carryWidth = currentWidth + previousWidth
                        ensureLineCapacity(line + 1)
                        lineStarts[line + 1] = textLength - words[index - 1].length
                        movedCharacters = 2
                    }
                    BreakMode.MOVE_MANY -> {
                        carryWidth = currentWidth + previousWidth
                        ensureLineCapacity(line + 1)
                        lineStarts[line + 1] = textLength - movedTextLength
                        movedCharacters = movedWordCount + 1
                    }
                    BreakMode.COMPRESS_1,
                    BreakMode.COMPRESS_2,
                    BreakMode.COMPRESS_3 -> {
                        carryWidth = 0f
                        ensureLineCapacity(line + 1)
                        lineStarts[line + 1] = textLength + word.length
                        movedCharacters = 0
                    }
                }
                shouldBreak = true
            }

            if (shouldBreak) {
                ensureLineCapacity(line)
                lineWidths[line] = lineWidth - carryWidth
                lineWidth = carryWidth
                line++
                ensureLineCapacity(line + 1)
            }

            if (words.lastIndex == index) {
                if (!shouldBreak) {
                    ensureLineCapacity(line + 1)
                    lineStarts[line + 1] = textLength + word.length
                    lineWidths[line] = lineWidth
                    line++
                    ensureLineCapacity(line + 1)
                } else if (movedCharacters > 0) {
                    ensureLineCapacity(line + 1)
                    lineStarts[line + 1] = lineStarts[line] + movedCharacters
                    lineWidths[line] = lineWidth
                    line++
                    ensureLineCapacity(line + 1)
                }
            }
            textLength += word.length
            previousWidth = currentWidth
        }
        internalLineCount = line
    }

    private fun ensureLineCapacity(line: Int) {
        if (lineStarts.size <= line + 1) {
            lineStarts = lineStarts.copyOf(line + growth)
            lineWidths = lineWidths.copyOf(line + growth)
        }
    }

    private fun isProhibitedAtLineStart(value: String): Boolean = prohibitedAtLineStart.contains(value)

    private fun isProhibitedAtLineEnd(value: String): Boolean = prohibitedAtLineEnd.contains(value)

    private fun isCompressible(width: Float): Boolean = width < cjkWidth

    private val punctuationGap = (cjkWidth / 12.75).toFloat()

    fun trailingPunctuationOffset(value: String): Float {
        val bounds = Rect()
        currentPaint.getTextBounds(value, 0, 1, bounds)
        return max(bounds.left.toFloat() - punctuationGap, 0f)
    }

    fun leadingPunctuationOffset(value: String): Float {
        val bounds = Rect()
        currentPaint.getTextBounds(value, 0, 1, bounds)
        val distance = max(cjkWidth - bounds.right.toFloat() - punctuationGap, 0f)
        return cjkWidth / 2 - distance
    }

    private fun desiredWidth(value: String, paint: TextPaint): Float {
        var width = paint.measureText(value)
        // API 35 introduced the letter-spacing contribution used by upstream.
        // Keep the module compilable with the host application's compileSdk 34.
        if (Build.VERSION.SDK_INT >= 35) {
            width += paint.letterSpacing * paint.textSize
        }
        return width
    }

    override fun getLineCount(): Int = internalLineCount
    override fun getLineTop(line: Int): Int = 0
    override fun getLineDescent(line: Int): Int = 0
    override fun getLineStart(line: Int): Int = lineStarts[line]
    override fun getParagraphDirection(line: Int): Int = 0
    override fun getLineContainsTab(line: Int): Boolean = false
    override fun getLineDirections(line: Int): Directions? = null
    override fun getTopPadding(): Int = 0
    override fun getBottomPadding(): Int = 0
    override fun getLineWidth(line: Int): Float = lineWidths[line]
    override fun getEllipsisStart(line: Int): Int = 0
    override fun getEllipsisCount(line: Int): Int = 0
}
