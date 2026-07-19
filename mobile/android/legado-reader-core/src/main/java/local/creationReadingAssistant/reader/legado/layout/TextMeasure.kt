/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Adapted from Luoyacheng/legado-E at 21855a7bf901becfd1caba5cf30a3c84fd1533e1.
 */
package local.creationReadingAssistant.reader.legado.layout

import android.text.TextPaint
import android.util.SparseArray
import androidx.core.util.getOrDefault
import kotlin.math.ceil

/**
 * Measures Unicode code points and caches widths. Common CJK characters share
 * one measured width while ASCII and uncommon code points are cached exactly.
 */
class TextMeasure(private var paint: TextPaint) {
    private var chineseCommonWidth = paint.measureText("一")
    private val asciiWidths = FloatArray(128) { -1f }
    private val codePointWidths = SparseArray<Float>()

    private fun measureCodePoint(codePoint: Int): Float {
        if (codePoint < 128) return asciiWidths[codePoint]
        if (codePoint in 19968..40869) return chineseCommonWidth
        return codePointWidths.getOrDefault(codePoint, -1f)
    }

    private fun measureCodePoints(codePoints: List<Int>) {
        if (codePoints.isEmpty()) return
        val charArray = String(codePoints.toIntArray(), 0, codePoints.size).toCharArray()
        val widths = FloatArray(charArray.size)
        paint.getTextWidths(charArray, 0, charArray.size, widths)
        val logicalWidths = ArrayList<Float>(codePoints.size)
        val buffer = IntArray(1)
        for (index in charArray.indices) {
            if (charArray[index].isLowSurrogate()) continue
            val width = ceil(widths[index])
            logicalWidths.add(width)
            if (width == 0f && logicalWidths.size > 1) {
                val lastIndex = logicalWidths.lastIndex
                buffer[0] = codePoints[lastIndex - 1]
                logicalWidths[lastIndex - 1] = paint.measureText(String(buffer, 0, 1))
                buffer[0] = codePoints[lastIndex]
                logicalWidths[lastIndex] = paint.measureText(String(buffer, 0, 1))
            }
        }
        for (index in codePoints.indices) {
            val codePoint = codePoints[index]
            val width = logicalWidths[index]
            if (codePoint < 128) asciiWidths[codePoint] = width else codePointWidths[codePoint] = width
        }
    }

    fun measureTextSplit(text: String): Pair<ArrayList<String>, ArrayList<Float>> {
        var missing: HashSet<Int>? = null
        val codePoints = text.toCodePoints()
        val widths = ArrayList<Float>(codePoints.size)
        val words = ArrayList<String>(codePoints.size)
        val buffer = IntArray(1)
        for (codePoint in codePoints) {
            val width = measureCodePoint(codePoint)
            widths.add(width)
            if (width == -1f) {
                if (missing == null) missing = hashSetOf()
                missing.add(codePoint)
            }
            buffer[0] = codePoint
            words.add(String(buffer, 0, 1))
        }
        if (!missing.isNullOrEmpty()) {
            measureCodePoints(missing.toList())
            for (index in codePoints.indices) {
                if (widths[index] == -1f) widths[index] = measureCodePoint(codePoints[index])
            }
        }
        return words to widths
    }

    fun measureText(text: String): Float {
        var textWidth = 0f
        var missing: ArrayList<Int>? = null
        val codePoints = text.toCodePoints()
        for (codePoint in codePoints) {
            val width = measureCodePoint(codePoint)
            if (width == -1f) {
                if (missing == null) missing = arrayListOf()
                missing.add(codePoint)
            } else {
                textWidth += width
            }
        }
        if (!missing.isNullOrEmpty()) {
            measureCodePoints(missing.toHashSet().toList())
            for (codePoint in missing) textWidth += measureCodePoint(codePoint)
        }
        return textWidth
    }

    fun setPaint(paint: TextPaint) {
        this.paint = paint
        chineseCommonWidth = paint.measureText("一")
        codePointWidths.clear()
        asciiWidths.fill(-1f)
    }

    private fun String.toCodePoints(): List<Int> {
        val codePoints = ArrayList<Int>(length)
        val chars = toCharArray()
        var index = 0
        while (index < chars.size) {
            val first = chars[index++]
            var codePoint = first.code
            if (first.isHighSurrogate() && index < chars.size) {
                val second = chars[index]
                if (second.isLowSurrogate()) {
                    index++
                    codePoint = Character.toCodePoint(first, second)
                }
            }
            codePoints.add(codePoint)
        }
        return codePoints
    }
}
