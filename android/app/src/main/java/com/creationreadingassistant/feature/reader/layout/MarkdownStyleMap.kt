package com.creationreadingassistant.feature.reader.layout

import com.creationreadingassistant.feature.reader.doc.MdInlineKind
import com.creationreadingassistant.feature.reader.doc.MdInlineSpan

/**
 * Markdown 行内样式 → 绘制层可识别的样式掩码。
 *
 * 掩码按位组合：同一簇可能同时是粗体 + 斜体 + 链接。
 */
object MarkdownStyleMap {
    const val BOLD = 1
    const val ITALIC = 2
    const val STRIKETHROUGH = 4
    const val CODE = 8
    const val LINK = 16

    fun maskFor(kind: MdInlineKind): Int = when (kind) {
        MdInlineKind.STRONG -> BOLD
        MdInlineKind.EMPHASIS -> ITALIC
        MdInlineKind.STRIKETHROUGH -> STRIKETHROUGH
        MdInlineKind.CODE -> CODE
        MdInlineKind.LINK -> LINK
    }

    fun computeClusterStyles(line: LayoutLine, spans: List<MdInlineSpan>): IntArray? =
        if (spans.isEmpty()) null else ParagraphStyles(spans).stylesFor(line)

    /** 每段只排序一次边界，按段内偏移顺序处理各行。 */
    internal class ParagraphStyles(spans: List<MdInlineSpan>, private val checkActive: () -> Unit = {}) {
        private data class Boundary(val offset: Int, val bit: Int, val delta: Int)

        private val boundaries = buildList {
            spans.forEachIndexed { index, span ->
                if ((index and 255) == 0) checkActive()
                if (span.start < span.end) {
                    val bit = maskFor(span.kind)
                    add(Boundary(span.start, bit, 1))
                    add(Boundary(span.end, bit, -1))
                }
            }
        }.sortedBy { it.offset }
        private val activeCounts = IntArray(5)
        private var activeMask = 0
        private var cursor = 0

        private fun consumeBoundary() {
            if ((cursor and 255) == 0) checkActive()
            val boundary = boundaries[cursor++]
            val index = Integer.numberOfTrailingZeros(boundary.bit)
            val count = activeCounts[index] + boundary.delta
            activeCounts[index] = count
            activeMask = if (count > 0) activeMask or boundary.bit else activeMask and boundary.bit.inv()
        }

        fun stylesFor(line: LayoutLine): IntArray? {
            val count = line.clusterCount
            if (count <= 0) return null
            val masks = IntArray(count + 1)
            for (i in 0 until count) {
                if ((i and 255) == 0) checkActive()
                val start = line.clusterStarts[i]
                val end = line.clusterStarts[i + 1]
                if (end <= start) continue
                while (cursor < boundaries.size && boundaries[cursor].offset <= start) consumeBoundary()
                var mask = activeMask
                // 簇内部即使样式开始后又结束，也要保留相交样式位。
                while (cursor < boundaries.size && boundaries[cursor].offset < end) {
                    consumeBoundary()
                    mask = mask or activeMask
                }
                masks[i] = mask
            }
            return masks
        }
    }
}
