package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import com.creationreadingassistant.feature.reader.layout.LayoutParagraph

/** EPUB 内容块到单一文本空间与分页块的纯函数适配层。 */
object EpubPageSource {

    fun chapterTextOf(blocks: List<DocBlock>): String =
        blocks.filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }

    /**
     * 图片不占字符；它挂在后继文本块的起点，章末图片挂在章文本末尾。
     * 所有文本段落都是 [chapterTextOf] 结果的原样切片，偏移可逆。
     */
    fun layoutBlocksOf(blocks: List<DocBlock>): List<LayoutBlock> {
        if (blocks.isEmpty()) return emptyList()

        val textStarts = IntArray(blocks.size) { -1 }
        var cursor = 0
        val textIndices = blocks.indices.filter { blocks[it] is DocBlock.Text }
        textIndices.forEachIndexed { order, index ->
            textStarts[index] = cursor
            cursor += (blocks[index] as DocBlock.Text).text.length
            if (order < textIndices.lastIndex) cursor++
        }
        val chapterLength = cursor

        val nextTextStart = IntArray(blocks.size) { chapterLength }
        var next = chapterLength
        for (index in blocks.indices.reversed()) {
            if (textStarts[index] >= 0) next = textStarts[index]
            nextTextStart[index] = next
        }

        val out = ArrayList<LayoutBlock>()
        blocks.forEachIndexed { index, block ->
            when (block) {
                is DocBlock.Text -> {
                    val blockStart = textStarts[index]
                    var lineStart = 0
                    while (lineStart <= block.text.length) {
                        val nl = block.text.indexOf('\n', lineStart)
                        val lineEnd = if (nl >= 0) nl else block.text.length
                        var start = lineStart
                        while (start < lineEnd && block.text[start].isWhitespace()) start++
                        var end = lineEnd
                        while (end > start && block.text[end - 1].isWhitespace()) end--
                        if (end > start) {
                            out += LayoutBlock.Text(
                                LayoutParagraph(
                                    text = block.text.substring(start, end),
                                    role = if (block.isHeading) BlockRole.HEADING else BlockRole.BODY,
                                    charOffset = blockStart + start,
                                ),
                            )
                        }
                        if (nl < 0) break
                        lineStart = nl + 1
                    }
                }

                is DocBlock.Image -> out += LayoutBlock.Image(
                    sourceKey = block.path,
                    widthPx = block.width.toFloat(),
                    heightPx = block.height.toFloat(),
                    anchorOffset = nextTextStart[index],
                )
            }
        }
        return out
    }
}
