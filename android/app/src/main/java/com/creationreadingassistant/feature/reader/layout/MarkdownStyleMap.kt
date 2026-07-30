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

    /**
     * 根据 [spans] 为 [line] 的每簇生成样式掩码。
     * 返回值长度 = clusterCount + 1（末位占位 0）。
     */
    fun computeClusterStyles(line: LayoutLine, spans: List<MdInlineSpan>): IntArray? {
        if (spans.isEmpty()) return null
        val n = line.clusterCount
        if (n <= 0) return null
        val arr = IntArray(n + 1)
        val lineStart = line.startInText
        for (j in 0 until n) {
            val cs = line.clusterStarts[j] - lineStart
            val ce = line.clusterStarts[j + 1] - lineStart
            if (ce <= cs) continue
            var mask = 0
            for (span in spans) {
                if (span.start < ce && span.end > cs) {
                    mask = mask or maskFor(span.kind)
                }
            }
            arr[j] = mask
        }
        return arr
    }
}
