package com.creationreadingassistant.feature.reader.doc

import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.SourceSpan
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/**
 * 原生 Markdown 语义解析器。
 *
 * 基于 commonmark-java 0.29.0（BSD-2-Clause），启用 GFM 表格/删除线/任务列表扩展，
 * 并打开 source spans 以建立「规范阅读文本」与「解码后源文本」的双向偏移映射。
 *
 * 解析单位是章节或 ReadingUnit 片段，不加载全书。解析产物：
 * - [MarkdownChapter.blocks]：独立于 Compose 的语义块树
 * - [MarkdownChapter.canonicalText]：移除 Markdown 标记、保留可见字符与必要分隔符的规范文本
 * - [MarkdownChapter.offsetMap]：规范偏移 ↔ 源偏移映射
 *
 * 搜索、TTS、Locator、选区、高亮、书签全部基于 [canonicalText] 偏移。
 */
object MarkdownParser {

    private val extensions: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
    )

    private val parser: Parser = Parser.builder()
        .extensions(extensions)
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        .build()

    /** 解析单个章节/片段。 */
    fun parse(source: String, sourceOffsetShift: Int = 0): MarkdownChapter {
        return Session(source, sourceOffsetShift, parser).parse()
    }

    /** 解析结果。 */
    data class MarkdownChapter(
        val blocks: List<MarkdownBlock>,
        val canonicalText: String,
        val offsetMap: MarkdownOffsetMap,
        val headings: List<MdHeading>,
    )

    /** 目录项。 */
    data class MdHeading(
        val level: Int,
        val title: String,
        val canonicalOffset: Int,
    )

    private class Session(
        private val source: String,
        private val sourceOffsetShift: Int,
        private val parser: Parser,
    ) {
        private val canonical = StringBuilder()
        private val blocks = mutableListOf<MarkdownBlock>()
        private val headings = mutableListOf<MdHeading>()

        fun parse(): MarkdownChapter {
            val document = parser.parse(source)
            document.accept(BlockVisitor())
            finish()
            val resultBlocks = blocks.toList()
            return MarkdownChapter(
                blocks = resultBlocks,
                canonicalText = canonical.toString(),
                offsetMap = MarkdownOffsetMap.fromBlocks(resultBlocks),
                headings = headings.toList(),
            )
        }

        private fun canonicalOffset() = canonical.length

        private fun appendCanonical(text: String) {
            canonical.append(text)
        }

        private fun finish() {
            // 空内容时 offsetMap 仍要能从 0 映射到 0
        }

        private inner class BlockVisitor : AbstractVisitor() {
            override fun visit(document: Document) {
                var first = true
                var node: Node? = document.firstChild
                while (node != null) {
                    processTopLevelBlock(node, first)
                    first = false
                    node = node.next
                }
            }

            private fun processTopLevelBlock(node: Node, isFirst: Boolean) {
                if (!canProduceBlock(node)) return

                // 块之间插入一个换行分隔符（规范阅读文本的一部分）。
                // 必须在 buildBlock 之前追加，否则规范文本里会出现“前一块末尾直接接后一块”的丢分隔符问题。
                if (!isFirst) appendCanonical("\n")

                val blockStartCanonical = canonicalOffset()
                val block = buildBlock(node) ?: return
                blocks.add(block)

                if (block is MarkdownBlock.Heading) {
                    val title = block.inlines.joinCanonicalText()
                    headings.add(MdHeading(block.level, title, blockStartCanonical))
                }
            }

            private fun canProduceBlock(node: Node): Boolean = when (node) {
                is Heading,
                is Paragraph,
                is BulletList,
                is OrderedList,
                is BlockQuote,
                is FencedCodeBlock,
                is IndentedCodeBlock,
                is ThematicBreak,
                is TableBlock -> true
                else -> false
            }
        }

        private fun buildBlock(node: Node): MarkdownBlock? {
            val sourceRange = sourceRangeOf(node)
            return when (node) {
                is Heading -> buildHeading(node, sourceRange)
                is Paragraph -> buildParagraph(node, sourceRange)
                is BulletList -> buildBulletList(node, sourceRange)
                is OrderedList -> buildOrderedList(node, sourceRange)
                is BlockQuote -> buildBlockQuote(node, sourceRange)
                is FencedCodeBlock -> buildFencedCodeBlock(node, sourceRange)
                is IndentedCodeBlock -> buildIndentedCodeBlock(node, sourceRange)
                is ThematicBreak -> buildHorizontalRule(node, sourceRange)
                is TableBlock -> buildTable(node, sourceRange)
                is HtmlBlock -> null // 原始 HTML 按安全文本处理：不进入规范阅读文本
                else -> null
            }
        }

        private fun buildHeading(node: Heading, sourceRange: IntRange): MarkdownBlock {
            val inlines = processInlines(node)
            val canonicalRange = inlinesCanonicalRange(inlines)
            return MarkdownBlock.Heading(
                level = node.level.coerceIn(1, 6),
                inlines = inlines,
                sourceRange = sourceRange,
                canonicalRange = canonicalRange,
            )
        }

        private fun buildParagraph(node: Paragraph, sourceRange: IntRange): MarkdownBlock {
            val inlines = processInlines(node)
            val canonicalRange = inlinesCanonicalRange(inlines)
            return MarkdownBlock.Paragraph(
                inlines = inlines,
                sourceRange = sourceRange,
                canonicalRange = canonicalRange,
            )
        }

        private fun buildBulletList(node: BulletList, sourceRange: IntRange): MarkdownBlock {
            val items = collectListItems(node)
            val canonicalRange = itemsCanonicalRange(items.map { it.blocks })
            return if (items.any { it.isTask }) {
                MarkdownBlock.TaskList(
                    items = items.map {
                        MarkdownBlock.TaskList.TaskListItem(
                            checked = it.checked,
                            blocks = it.blocks,
                        )
                    },
                    sourceRange = sourceRange,
                    canonicalRange = canonicalRange,
                )
            } else {
                MarkdownBlock.UnorderedList(
                    items = items.map { it.blocks },
                    sourceRange = sourceRange,
                    canonicalRange = canonicalRange,
                )
            }
        }

        private fun buildOrderedList(node: OrderedList, sourceRange: IntRange): MarkdownBlock {
            val items = collectListItems(node)
            val canonicalRange = itemsCanonicalRange(items.map { it.blocks })
            return MarkdownBlock.OrderedList(
                startNumber = node.startNumber,
                items = items.map { it.blocks },
                sourceRange = sourceRange,
                canonicalRange = canonicalRange,
            )
        }

        private data class ListItemData(
            val blocks: List<MarkdownBlock>,
            val isTask: Boolean,
            val checked: Boolean,
        )

        private fun buildBlockQuote(node: BlockQuote, sourceRange: IntRange): MarkdownBlock {
            val innerBlocks = mutableListOf<MarkdownBlock>()
            var child = node.firstChild
            while (child != null) {
                buildBlock(child)?.let { innerBlocks.add(it) }
                child = child.next
            }
            val canonicalRange = blocksCanonicalRange(innerBlocks)
            return MarkdownBlock.BlockQuote(
                blocks = innerBlocks,
                sourceRange = sourceRange,
                canonicalRange = canonicalRange,
            )
        }

        private fun buildFencedCodeBlock(node: FencedCodeBlock, sourceRange: IntRange): MarkdownBlock {
            val content = node.literal ?: ""
            val canonicalStart = canonicalOffset()
            appendCanonical(content)
            val canonicalEnd = canonicalOffset()
            return MarkdownBlock.FencedCodeBlock(
                language = node.info?.takeIf { it.isNotBlank() },
                content = content,
                sourceRange = sourceRange,
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun buildIndentedCodeBlock(node: IndentedCodeBlock, sourceRange: IntRange): MarkdownBlock {
            val content = node.literal ?: ""
            val canonicalStart = canonicalOffset()
            appendCanonical(content)
            val canonicalEnd = canonicalOffset()
            return MarkdownBlock.IndentedCodeBlock(
                content = content,
                sourceRange = sourceRange,
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun buildHorizontalRule(node: ThematicBreak, sourceRange: IntRange): MarkdownBlock {
            val canonicalStart = canonicalOffset()
            appendCanonical("──────")
            val canonicalEnd = canonicalOffset()
            return MarkdownBlock.HorizontalRule(
                sourceRange = sourceRange,
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun buildTable(node: TableBlock, sourceRange: IntRange): MarkdownBlock {
            val header = mutableListOf<MarkdownBlock.Table.TableCell>()
            val rows = mutableListOf<List<MarkdownBlock.Table.TableCell>>()
            var child = node.firstChild
            while (child != null) {
                when (child) {
                    is TableHead -> {
                        var row = child.firstChild
                        while (row != null) {
                            if (row is TableRow) header.addAll(cellsOf(row))
                            row = row.next
                        }
                    }
                    is TableBody -> {
                        var row = child.firstChild
                        while (row != null) {
                            if (row is TableRow) rows.add(cellsOf(row))
                            row = row.next
                        }
                    }
                }
                child = child.next
            }
            val canonicalStart = canonicalOffset()
            val headerTexts = header.map { it.inlines.joinCanonicalText() }
            if (headerTexts.isNotEmpty()) {
                appendCanonical(headerTexts.joinToString(" | ", "| ", " |"))
            }
            for (row in rows) {
                appendCanonical("\n")
                val texts = row.map { it.inlines.joinCanonicalText() }
                appendCanonical(texts.joinToString(" | ", "| ", " |"))
            }
            val canonicalEnd = canonicalOffset()
            return MarkdownBlock.Table(
                header = header,
                rows = rows,
                sourceRange = sourceRange,
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun cellsOf(row: TableRow): List<MarkdownBlock.Table.TableCell> {
            val out = mutableListOf<MarkdownBlock.Table.TableCell>()
            var cell = row.firstChild
            while (cell != null) {
                if (cell is TableCell) {
                    val inlines = processInlines(cell)
                    out.add(
                        MarkdownBlock.Table.TableCell(
                            inlines = inlines,
                            alignment = when (cell.alignment) {
                                TableCell.Alignment.LEFT -> MarkdownBlock.Table.TableAlignment.LEFT
                                TableCell.Alignment.CENTER -> MarkdownBlock.Table.TableAlignment.CENTER
                                TableCell.Alignment.RIGHT -> MarkdownBlock.Table.TableAlignment.RIGHT
                                else -> MarkdownBlock.Table.TableAlignment.NONE
                            },
                        ),
                    )
                }
                cell = cell.next
            }
            return out
        }

        private fun collectListItems(listNode: Node): List<ListItemData> {
            val items = mutableListOf<ListItemData>()
            var child = listNode.firstChild
            while (child != null) {
                if (child is ListItem) {
                    val innerBlocks = mutableListOf<MarkdownBlock>()
                    var inner = child.firstChild
                    var isTask = false
                    var checked = false
                    if (inner is TaskListItemMarker) {
                        isTask = true
                        checked = inner.isChecked
                        inner = inner.next
                    }
                    while (inner != null) {
                        buildBlock(inner)?.let { innerBlocks.add(it) }
                        inner = inner.next
                    }
                    items.add(ListItemData(innerBlocks, isTask, checked))
                }
                child = child.next
            }
            return items
        }

        private fun processInlines(parent: Node): List<MdInline> {
            val out = mutableListOf<MdInline>()
            var child = parent.firstChild
            while (child != null) {
                buildInline(child)?.let { out.add(it) }
                child = child.next
            }
            return out
        }

        private fun buildInline(node: Node): MdInline? {
            return when (node) {
                is Text -> buildText(node)
                is StrongEmphasis -> buildContainerInline(node, MdInlineKind.STRONG)
                is Emphasis -> buildContainerInline(node, MdInlineKind.EMPHASIS)
                is Strikethrough -> buildContainerInline(node, MdInlineKind.STRIKETHROUGH)
                is Code -> buildCode(node)
                is Link -> buildLink(node)
                is Image -> buildImage(node)
                is SoftLineBreak -> buildSoftLineBreak(node)
                is HardLineBreak -> buildHardLineBreak(node)
                is HtmlInline -> null
                else -> null
            }
        }

        private fun buildText(node: Text): MdInline.Text {
            val literal = node.literal ?: ""
            val canonicalStart = canonicalOffset()
            appendCanonical(literal)
            val canonicalEnd = canonicalOffset()
            val sourceRange = sourceRangeOf(node)
            return MdInline.Text(
                text = literal,
                sourceRange = sourceRange,
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun buildCode(node: Code): MdInline.Code {
            val literal = node.literal ?: ""
            val canonicalStart = canonicalOffset()
            appendCanonical(literal)
            val canonicalEnd = canonicalOffset()
            val sourceRange = codeSourceRange(node)
            return MdInline.Code(
                text = literal,
                sourceRange = sourceRange,
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun buildContainerInline(node: Node, kind: MdInlineKind): MdInline {
            val sourceRange = sourceRangeOf(node)
            val canonicalStart = canonicalOffset()
            val children = processInlines(node)
            val canonicalEnd = canonicalOffset()
            return when (kind) {
                MdInlineKind.STRONG -> MdInline.Strong(children, sourceRange, canonicalStart until canonicalEnd)
                MdInlineKind.EMPHASIS -> MdInline.Emphasis(children, sourceRange, canonicalStart until canonicalEnd)
                MdInlineKind.STRIKETHROUGH -> MdInline.Strikethrough(children, sourceRange, canonicalStart until canonicalEnd)
                else -> MdInline.Strong(children, sourceRange, canonicalStart until canonicalEnd)
            }
        }

        private fun buildLink(node: Link): MdInline.Link {
            val sourceRange = sourceRangeOf(node)
            val canonicalStart = canonicalOffset()
            val children = processInlines(node)
            val canonicalEnd = canonicalOffset()
            return MdInline.Link(
                url = node.destination ?: "",
                title = node.title,
                children = children,
                sourceRange = sourceRange,
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun buildImage(node: Image): MdInline.Image {
            val alt = extractAltFromImage(node).ifBlank { node.title ?: "图片" }
            val canonicalStart = canonicalOffset()
            appendCanonical(alt)
            val canonicalEnd = canonicalOffset()
            val sourceRange = imageAltSourceRange(node)
            return MdInline.Image(
                alt = alt,
                url = node.destination ?: "",
                title = node.title,
                sourceRange = sourceRange,
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun buildSoftLineBreak(node: SoftLineBreak): MdInline.SoftLineBreak {
            val canonicalStart = canonicalOffset()
            appendCanonical(" ")
            val canonicalEnd = canonicalOffset()
            return MdInline.SoftLineBreak(
                sourceRange = sourceRangeOf(node),
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun buildHardLineBreak(node: HardLineBreak): MdInline.HardLineBreak {
            val canonicalStart = canonicalOffset()
            appendCanonical("\n")
            val canonicalEnd = canonicalOffset()
            return MdInline.HardLineBreak(
                sourceRange = sourceRangeOf(node),
                canonicalRange = canonicalStart until canonicalEnd,
            )
        }

        private fun extractAltFromImage(node: Image): String {
            val sb = StringBuilder()
            var child = node.firstChild
            while (child != null) {
                if (child is Text) sb.append(child.literal ?: "")
                child = child.next
            }
            return sb.toString().ifBlank { "图片" }
        }

        private fun sourceRangeOf(node: Node): IntRange {
            val spans = node.sourceSpans
            if (spans.isNullOrEmpty()) return 0 until 0
            val start = spans.minOf { it.inputIndex }
            val end = spans.maxOf { it.inputIndex + it.length }
            return (sourceOffsetShift + start) until (sourceOffsetShift + end)
        }

        /** 行内代码的源范围，扣除前后反引号围栏。 */
        private fun codeSourceRange(node: Code): IntRange {
            val full = sourceRangeOf(node)
            if (full.isEmpty()) return full
            // sourceRangeOf 返回的是全局文件坐标；而这里的 source 是本次解析的局部片段，
            // 因此 substring 前要先换算为局部坐标。
            val localStart = full.first - sourceOffsetShift
            val localEnd = full.last - sourceOffsetShift
            if (localStart < 0 || localEnd > source.length || localEnd <= localStart) return full
            val text = source.substring(localStart, localEnd)
            var fenceLen = 0
            while (fenceLen < text.length && text[fenceLen] == '`') fenceLen++
            if (fenceLen == 0) return full
            val contentStart = full.first + fenceLen
            val contentEnd = full.last - fenceLen
            if (contentEnd <= contentStart) return full
            return contentStart until contentEnd
        }

        /** 图片 alt 文本的源范围，扣除 `![` 与 `](url)`。 */
        private fun imageAltSourceRange(node: Image): IntRange {
            val full = sourceRangeOf(node)
            if (full.isEmpty()) return full
            val localStart = full.first - sourceOffsetShift
            val localEnd = full.last - sourceOffsetShift
            if (localStart < 0 || localEnd > source.length || localEnd <= localStart) return full
            val text = source.substring(localStart, localEnd)
            if (!text.startsWith("![")) return full
            val altStart = full.first + 2
            var i = 2
            while (i < text.length) {
                when (text[i]) {
                    '\\' -> i += 2
                    ']' -> return altStart until (full.first + i)
                    else -> i++
                }
            }
            return full
        }

        private fun inlinesCanonicalRange(inlines: List<MdInline>): IntRange {
            if (inlines.isEmpty()) return 0 until 0
            return inlines.first().canonicalRange.first until inlines.last().canonicalRange.last + 1
        }

        private fun itemsCanonicalRange(items: List<List<MarkdownBlock>>): IntRange {
            val all = items.flatten()
            return blocksCanonicalRange(all)
        }

        private fun blocksCanonicalRange(blocks: List<MarkdownBlock>): IntRange {
            if (blocks.isEmpty()) return 0 until 0
            return blocks.first().canonicalRange.first until blocks.last().canonicalRange.last + 1
        }

        private fun List<MdInline>.joinCanonicalText(): String = buildString {
            for (inline in this@joinCanonicalText) {
                append(inline.canonicalText())
            }
        }

        private fun MdInline.canonicalText(): String = when (this) {
            is MdInline.Text -> text
            is MdInline.Code -> text
            is MdInline.Strong -> children.joinCanonicalText()
            is MdInline.Emphasis -> children.joinCanonicalText()
            is MdInline.Strikethrough -> children.joinCanonicalText()
            is MdInline.Link -> children.joinCanonicalText()
            is MdInline.Image -> alt
            is MdInline.HardLineBreak -> "\n"
            is MdInline.SoftLineBreak -> " "
        }
    }
}
