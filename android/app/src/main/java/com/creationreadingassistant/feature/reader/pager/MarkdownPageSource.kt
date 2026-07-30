package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import com.creationreadingassistant.feature.reader.layout.LayoutParagraph
import com.creationreadingassistant.feature.reader.doc.MdInline
import com.creationreadingassistant.feature.reader.doc.MdInlineKind
import com.creationreadingassistant.feature.reader.doc.MdInlineSpan
import com.creationreadingassistant.feature.reader.doc.MarkdownBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownParser

/**
 * Markdown 语义章 → 排版引擎可消费的 [LayoutBlock] 序列。
 *
 * 关键纪律与 [TxtPageSource] 一致：**段落文本必须是 [MarkdownChapter.canonicalText]
 * 的原样切片，偏移必须可逆。** 搜索、TTS、Locator、选区、高亮、书签全部建立在
 * [canonicalText] 的字符偏移之上，排版层只负责把同一文本空间画出来。
 *
 * 图片不占字符；它挂在图片说明（alt）文本的起点，与 EPUB 图片挂载策略一致。
 */
object MarkdownPageSource {

    /** 章的规范阅读文本。 */
    fun chapterTextOf(chapter: MarkdownParser.MarkdownChapter): String = chapter.canonicalText

    /** 将 Markdown 语义章转换为排版块序列。 */
    fun layoutBlocksOf(chapter: MarkdownParser.MarkdownChapter): List<LayoutBlock> {
        val ctx = RenderContext(chapter.canonicalText)
        for (block in chapter.blocks) {
            ctx.renderBlock(block, indentLevel = 0)
        }
        return ctx.blocks
    }

    private class RenderContext(private val canonicalText: String) {
        val blocks = mutableListOf<LayoutBlock>()

        /** 列表项前缀标记，将挂在下一个生成的文本段落上。 */
        private var pendingMarker: String? = null

        fun renderBlock(block: MarkdownBlock, indentLevel: Int) {
            val marker = pendingMarker
            pendingMarker = null
            when (block) {
                is MarkdownBlock.Heading -> renderHeading(block, marker)
                is MarkdownBlock.Paragraph -> renderParagraph(block, indentLevel, marker)
                is MarkdownBlock.BlockQuote -> renderQuote(block, indentLevel)
                is MarkdownBlock.UnorderedList -> renderUnorderedList(block, indentLevel)
                is MarkdownBlock.OrderedList -> renderOrderedList(block, indentLevel)
                is MarkdownBlock.TaskList -> renderTaskList(block, indentLevel)
                is MarkdownBlock.FencedCodeBlock -> renderCodeBlock(block)
                is MarkdownBlock.IndentedCodeBlock -> renderCodeBlock(block)
                is MarkdownBlock.Table -> renderTable(block)
                is MarkdownBlock.HorizontalRule -> renderHorizontalRule(block)
            }
        }

        private fun slice(range: IntRange): String {
            val start = range.first.coerceIn(0, canonicalText.length)
            val end = (range.last + 1).coerceIn(start, canonicalText.length)
            return canonicalText.substring(start, end)
        }

        private fun renderHeading(block: MarkdownBlock.Heading, marker: String?) {
            blocks += LayoutBlock.Text(
                LayoutParagraph(
                    text = slice(block.canonicalRange),
                    role = headingRole(block.level),
                    charOffset = block.canonicalRange.first,
                    listMarker = marker,
                    inlineSpans = collectInlineSpans(block.inlines, block.canonicalRange.first),
                ),
            )
        }

        private fun renderParagraph(block: MarkdownBlock.Paragraph, indentLevel: Int, marker: String?) {
            // 图片作为独立 Image 块挂在 alt 文本起点；alt 文本仍保留在段落内供 TTS/搜索使用。
            for (image in extractImages(block.inlines, block.canonicalRange.first)) {
                blocks += LayoutBlock.Image(
                    sourceKey = image.url,
                    widthPx = 0f,
                    heightPx = 0f,
                    anchorOffset = image.canonicalOffset,
                )
            }
            blocks += LayoutBlock.Text(
                LayoutParagraph(
                    text = slice(block.canonicalRange),
                    role = BlockRole.BODY,
                    charOffset = block.canonicalRange.first,
                    indentLevel = indentLevel,
                    listMarker = marker,
                    inlineSpans = collectInlineSpans(block.inlines, block.canonicalRange.first),
                ),
            )
        }

        private fun renderQuote(block: MarkdownBlock.BlockQuote, indentLevel: Int) {
            var first = true
            for (b in block.blocks) {
                if (first) {
                    pendingMarker = null // 引用块本身不需要标记，但保留层级
                    first = false
                }
                renderBlock(b, indentLevel + 1)
            }
        }

        private fun renderUnorderedList(block: MarkdownBlock.UnorderedList, indentLevel: Int) {
            for (item in block.items) {
                renderListItem(item, indentLevel + 1, "• ")
            }
        }

        private fun renderOrderedList(block: MarkdownBlock.OrderedList, indentLevel: Int) {
            for ((i, item) in block.items.withIndex()) {
                val number = block.startNumber + i
                renderListItem(item, indentLevel + 1, "$number. ")
            }
        }

        private fun renderTaskList(block: MarkdownBlock.TaskList, indentLevel: Int) {
            for (item in block.items) {
                val marker = if (item.checked) "[x] " else "[ ] "
                renderListItem(item.blocks, indentLevel + 1, marker)
            }
        }

        private fun renderListItem(itemBlocks: List<MarkdownBlock>, indentLevel: Int, marker: String) {
            if (itemBlocks.isEmpty()) return
            pendingMarker = marker
            renderBlock(itemBlocks.first(), indentLevel)
            for (b in itemBlocks.drop(1)) {
                renderBlock(b, indentLevel)
            }
        }

        private fun renderCodeBlock(block: MarkdownBlock.FencedCodeBlock) {
            val language = block.language
            renderCodeLines(slice(block.canonicalRange), block.canonicalRange.first, language)
        }

        private fun renderCodeBlock(block: MarkdownBlock.IndentedCodeBlock) {
            renderCodeLines(slice(block.canonicalRange), block.canonicalRange.first, language = null)
        }

        private fun renderCodeLines(text: String, baseOffset: Int, language: String?) {
            var lineStart = 0
            while (lineStart <= text.length) {
                val nl = text.indexOf('\n', lineStart)
                val lineEnd = if (nl >= 0) nl else text.length
                if (lineEnd > lineStart) {
                    blocks += LayoutBlock.Text(
                        LayoutParagraph(
                            text = text.substring(lineStart, lineEnd),
                            role = BlockRole.CODE_BLOCK,
                            charOffset = baseOffset + lineStart,
                            codeLanguage = language,
                        ),
                    )
                }
                if (nl < 0) break
                lineStart = nl + 1
            }
        }

        private fun renderTable(block: MarkdownBlock.Table) {
            val text = slice(block.canonicalRange)
            var lineStart = 0
            var isHeader = true
            while (lineStart <= text.length) {
                val nl = text.indexOf('\n', lineStart)
                val lineEnd = if (nl >= 0) nl else text.length
                if (lineEnd > lineStart) {
                    blocks += LayoutBlock.Text(
                        LayoutParagraph(
                            text = text.substring(lineStart, lineEnd),
                            role = if (isHeader) BlockRole.TABLE_HEADER else BlockRole.TABLE_ROW,
                            charOffset = block.canonicalRange.first + lineStart,
                        ),
                    )
                    isHeader = false
                }
                if (nl < 0) break
                lineStart = nl + 1
            }
        }

        private fun renderHorizontalRule(block: MarkdownBlock.HorizontalRule) {
            blocks += LayoutBlock.Text(
                LayoutParagraph(
                    text = slice(block.canonicalRange),
                    role = BlockRole.HORIZONTAL_RULE,
                    charOffset = block.canonicalRange.first,
                ),
            )
        }

        private fun headingRole(level: Int): BlockRole = when (level.coerceIn(1, 6)) {
            1 -> BlockRole.HEADING_1
            2 -> BlockRole.HEADING_2
            3 -> BlockRole.HEADING_3
            4 -> BlockRole.HEADING_4
            5 -> BlockRole.HEADING_5
            else -> BlockRole.HEADING_6
        }
    }

    private data class InlineImage(
        val url: String,
        val canonicalOffset: Int,
    )

    private fun extractImages(inlines: List<MdInline>, baseOffset: Int): List<InlineImage> {
        val out = mutableListOf<InlineImage>()
        fun walk(nodes: List<MdInline>) {
            for (node in nodes) {
                when (node) {
                    is MdInline.Image -> out += InlineImage(
                        url = node.url,
                        canonicalOffset = baseOffset + node.canonicalRange.first,
                    )
                    is MdInline.Strong -> walk(node.children)
                    is MdInline.Emphasis -> walk(node.children)
                    is MdInline.Strikethrough -> walk(node.children)
                    is MdInline.Link -> walk(node.children)
                    else -> Unit
                }
            }
        }
        walk(inlines)
        return out
    }

    private fun collectInlineSpans(inlines: List<MdInline>, baseOffset: Int): List<MdInlineSpan> {
        val out = mutableListOf<MdInlineSpan>()
        fun walk(nodes: List<MdInline>) {
            for (node in nodes) {
                when (node) {
                    is MdInline.Strong -> emitSpan(node, MdInlineKind.STRONG, baseOffset, out)
                    is MdInline.Emphasis -> emitSpan(node, MdInlineKind.EMPHASIS, baseOffset, out)
                    is MdInline.Strikethrough -> emitSpan(node, MdInlineKind.STRIKETHROUGH, baseOffset, out)
                    is MdInline.Link -> emitSpan(node, MdInlineKind.LINK, baseOffset, out)
                    is MdInline.Code -> {
                        out += MdInlineSpan(
                            start = node.canonicalRange.first - baseOffset,
                            end = node.canonicalRange.last + 1 - baseOffset,
                            kind = MdInlineKind.CODE,
                        )
                    }
                    is MdInline.Text,
                    is MdInline.Image,
                    is MdInline.HardLineBreak,
                    is MdInline.SoftLineBreak -> Unit
                }
            }
        }
        walk(inlines)
        return out
    }

    private fun emitSpan(
        node: MdInline,
        kind: MdInlineKind,
        baseOffset: Int,
        out: MutableList<MdInlineSpan>,
    ) {
        out += MdInlineSpan(
            start = node.canonicalRange.first - baseOffset,
            end = node.canonicalRange.last + 1 - baseOffset,
            kind = kind,
        )
        val children = when (node) {
            is MdInline.Strong -> node.children
            is MdInline.Emphasis -> node.children
            is MdInline.Strikethrough -> node.children
            is MdInline.Link -> node.children
            else -> emptyList()
        }
        collectInlineSpans(children, baseOffset)
            .mapTo(out) { it }
    }
}
