package com.creationreadingassistant.feature.reader.doc

/**
 * Markdown 语义模型，独立于 Compose UI。
 *
 * 坐标约定（与 [MarkdownDocument] 保持一致）：
 * - [sourceRange]：解码后源文本中的 [start, end) 字符偏移（UTF-16 code units）。
 * - [canonicalRange]：规范阅读文本中的 [start, end) 字符偏移。
 *   规范文本 = 移除 Markdown 标记但保留可见字符与必要换行后的文本。
 *   搜索、TTS、Locator、选区、高亮、书签全部基于规范文本偏移。
 */
sealed interface MarkdownBlock {
    val sourceRange: IntRange
    val canonicalRange: IntRange

    data class Heading(
        val level: Int,
        val inlines: List<MdInline>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock

    data class Paragraph(
        val inlines: List<MdInline>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock

    data class OrderedList(
        val startNumber: Int,
        val items: List<List<MarkdownBlock>>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock

    data class UnorderedList(
        val items: List<List<MarkdownBlock>>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock

    data class TaskList(
        val items: List<TaskListItem>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock {
        data class TaskListItem(
            val checked: Boolean,
            val blocks: List<MarkdownBlock>,
        )
    }

    data class BlockQuote(
        val blocks: List<MarkdownBlock>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock

    data class FencedCodeBlock(
        val language: String?,
        val content: String,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock

    data class IndentedCodeBlock(
        val content: String,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock

    data class HorizontalRule(
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock

    data class Table(
        val header: List<TableCell>,
        val rows: List<List<TableCell>>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MarkdownBlock {
        data class TableCell(
            val inlines: List<MdInline>,
            val alignment: TableAlignment = TableAlignment.NONE,
        )

        enum class TableAlignment { LEFT, CENTER, RIGHT, NONE }
    }
}

/** Markdown 行内节点。 */
sealed interface MdInline {
    val sourceRange: IntRange
    val canonicalRange: IntRange

    data class Text(
        val text: String,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MdInline

    data class Strong(
        val children: List<MdInline>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MdInline

    data class Emphasis(
        val children: List<MdInline>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MdInline

    data class Strikethrough(
        val children: List<MdInline>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MdInline

    data class Code(
        val text: String,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MdInline

    data class Link(
        val url: String,
        val title: String?,
        val children: List<MdInline>,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MdInline

    /** 图片以占位/说明形式进入规范文本，便于 TTS 朗读与搜索命中。 */
    data class Image(
        val alt: String,
        val url: String,
        val title: String?,
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MdInline

    data class HardLineBreak(
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MdInline

    data class SoftLineBreak(
        override val sourceRange: IntRange,
        override val canonicalRange: IntRange,
    ) : MdInline
}

/** 行内样式区间，供 UI 层映射为 SpanStyle/字体/颜色。 */
data class MdInlineSpan(
    val start: Int,
    val end: Int,
    val kind: MdInlineKind,
)

enum class MdInlineKind {
    STRONG,
    EMPHASIS,
    STRIKETHROUGH,
    CODE,
    LINK,
}


