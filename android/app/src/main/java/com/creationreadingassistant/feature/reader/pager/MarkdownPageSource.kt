package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderContent
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderSpan
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderUnit
import com.creationreadingassistant.feature.reader.doc.MdInlineKind
import com.creationreadingassistant.feature.reader.doc.MdInlineSpan
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import com.creationreadingassistant.feature.reader.layout.LayoutParagraph

/**
 * Markdown 语义章 → 排版引擎可消费的 [LayoutBlock] 序列。
 *
 * 关键纪律与 [TxtPageSource] 一致：**段落文本必须是 [MarkdownChapter.canonicalText]
 * 的原样切片，偏移必须可逆。** 搜索、TTS、Locator、选区、高亮、书签全部建立在
 * [canonicalText] 的字符偏移之上，排版层只负责把同一文本空间画出来。
 *
 * 结构与滚动端共享唯一语义输入：先经 [MarkdownRenderModel.flatten] 得到统一的
 * 扁平渲染单元，再由纯适配 seam [LayoutBlockAdapter] 生成 [LayoutBlock]；
 * **不再直接递归 Markdown AST**。
 *
 * 图片不占字符；它挂在图片说明（alt）文本的起点，与 EPUB 图片挂载策略一致。
 */
object MarkdownPageSource {

    /** 章的规范阅读文本。 */
    fun chapterTextOf(chapter: MarkdownParser.MarkdownChapter): String = chapter.canonicalText

    /** 将 Markdown 语义章转换为排版块序列。 */
    fun layoutBlocksOf(chapter: MarkdownParser.MarkdownChapter): List<LayoutBlock> =
        LayoutBlockAdapter.toLayoutBlocks(
            units = MarkdownRenderModel.flatten(chapter),
            canonicalText = chapter.canonicalText,
        )
}

/**
 * 纯适配 seam：统一渲染模型 → [LayoutBlock]。
 *
 * 只消费 [MarkdownRenderUnit] 与 canonical 文本切片，不读 AST。与旧逐块实现对齐：
 * - 标题 → HEADING_x（参与 keep-with-next，不做首行缩进/两端对齐）；
 * - 段落 → BODY；引用中的普通段落 → QUOTE（indentLevel 已包含引用层级）；
 * - 无序列表项 → LIST_ITEM_BULLET，有序列表项 → LIST_ITEM_NUMBER；
 *   任务项 → TASK_ITEM_UNCHECKED / TASK_ITEM_CHECKED；
 *   引用中的列表保留列表角色，marker 不占 text 偏移；
 * - 图片不占字符，按 Image span 的 canonical 起点挂 anchor；
 * - 代码块逐非空行 → CODE_BLOCK + codeLanguage；
 * - 表格逐行 → TABLE_HEADER（首行）/ TABLE_ROW（其余行）；
 * - 分隔线 → HORIZONTAL_RULE。
 */
internal object LayoutBlockAdapter {

    fun toLayoutBlocks(
        units: List<MarkdownRenderUnit>,
        canonicalText: String,
    ): List<LayoutBlock> {
        val out = mutableListOf<LayoutBlock>()
        for (unit in units) {
            emitUnit(unit, canonicalText, out)
        }
        return out
    }

    private fun emitUnit(
        unit: MarkdownRenderUnit,
        canonicalText: String,
        out: MutableList<LayoutBlock>,
    ) {
        when (val content = unit.content) {
            is MarkdownRenderContent.Heading -> out += textBlock(
                text = canonicalText.sliceRange(unit.canonicalRange),
                role = headingRole(content.level),
                charOffset = unit.canonicalRange.first,
                indentLevel = unit.depth,
                inlineSpans = content.spans.toLayoutSpans(),
            )

            is MarkdownRenderContent.Paragraph ->
                emitParagraph(
                    unit,
                    canonicalText,
                    content.spans,
                    role = paragraphRole(unit),
                    marker = null,
                    out,
                )

            is MarkdownRenderContent.ListItem -> emitParagraph(
                unit,
                canonicalText,
                content.spans,
                role = if (content.ordinal != null) {
                    BlockRole.LIST_ITEM_NUMBER
                } else {
                    BlockRole.LIST_ITEM_BULLET
                },
                marker = content.marker,
                out,
            )

            is MarkdownRenderContent.TaskItem -> emitParagraph(
                unit,
                canonicalText,
                content.spans,
                role = if (content.checked) {
                    BlockRole.TASK_ITEM_CHECKED
                } else {
                    BlockRole.TASK_ITEM_UNCHECKED
                },
                marker = if (content.checked) "[x] " else "[ ] ",
                out,
            )

            is MarkdownRenderContent.CodeBlock ->
                emitCodeLines(canonicalText, unit, content.language, out)

            is MarkdownRenderContent.Table ->
                emitTableLines(canonicalText, unit, out)

            MarkdownRenderContent.HorizontalRule -> out += textBlock(
                text = canonicalText.sliceRange(unit.canonicalRange),
                role = BlockRole.HORIZONTAL_RULE,
                charOffset = unit.canonicalRange.first,
            )
        }
    }

    private fun emitParagraph(
        unit: MarkdownRenderUnit,
        canonicalText: String,
        spans: List<MarkdownRenderSpan>,
        role: BlockRole,
        marker: String?,
        out: MutableList<LayoutBlock>,
    ) {
        // 图片作为独立 Image 块挂在 alt 文本起点；alt 文本仍保留在段落内供 TTS/搜索使用。
        for (image in spans.filterIsInstance<MarkdownRenderSpan.Image>()) {
            out += LayoutBlock.Image(
                sourceKey = image.source,
                widthPx = 0f,
                heightPx = 0f,
                anchorOffset = unit.canonicalRange.first + image.start,
            )
        }
        out += textBlock(
            text = canonicalText.sliceRange(unit.canonicalRange),
            role = role,
            charOffset = unit.canonicalRange.first,
            indentLevel = unit.depth,
            listMarker = marker,
            inlineSpans = spans.toLayoutSpans(),
        )
    }

    /** 引用中的普通段落用 QUOTE；非引用段落保持 BODY。 */
    private fun paragraphRole(unit: MarkdownRenderUnit): BlockRole =
        if (unit.blockquoteDepth > 0) BlockRole.QUOTE else BlockRole.BODY

    private fun emitCodeLines(
        canonicalText: String,
        unit: MarkdownRenderUnit,
        language: String?,
        out: MutableList<LayoutBlock>,
    ) {
        val text = canonicalText.sliceRange(unit.canonicalRange)
        var lineStart = 0
        while (lineStart <= text.length) {
            val nl = text.indexOf('\n', lineStart)
            val lineEnd = if (nl >= 0) nl else text.length
            if (lineEnd > lineStart) {
                out += textBlock(
                    text = text.substring(lineStart, lineEnd),
                    role = BlockRole.CODE_BLOCK,
                    charOffset = unit.canonicalRange.first + lineStart,
                    codeLanguage = language,
                )
            }
            if (nl < 0) break
            lineStart = nl + 1
        }
    }

    private fun emitTableLines(
        canonicalText: String,
        unit: MarkdownRenderUnit,
        out: MutableList<LayoutBlock>,
    ) {
        val text = canonicalText.sliceRange(unit.canonicalRange)
        var lineStart = 0
        var isHeader = true
        while (lineStart <= text.length) {
            val nl = text.indexOf('\n', lineStart)
            val lineEnd = if (nl >= 0) nl else text.length
            if (lineEnd > lineStart) {
                out += textBlock(
                    text = text.substring(lineStart, lineEnd),
                    role = if (isHeader) BlockRole.TABLE_HEADER else BlockRole.TABLE_ROW,
                    charOffset = unit.canonicalRange.first + lineStart,
                )
                isHeader = false
            }
            if (nl < 0) break
            lineStart = nl + 1
        }
    }

    private fun textBlock(
        text: String,
        role: BlockRole,
        charOffset: Int,
        indentLevel: Int = 0,
        listMarker: String? = null,
        inlineSpans: List<MdInlineSpan> = emptyList(),
        codeLanguage: String? = null,
    ): LayoutBlock.Text = LayoutBlock.Text(
        LayoutParagraph(
            text = text,
            role = role,
            charOffset = charOffset,
            indentLevel = indentLevel,
            listMarker = listMarker,
            inlineSpans = inlineSpans,
            codeLanguage = codeLanguage,
        ),
    )

    private fun headingRole(level: Int): BlockRole = when (level.coerceIn(1, 6)) {
        1 -> BlockRole.HEADING_1
        2 -> BlockRole.HEADING_2
        3 -> BlockRole.HEADING_3
        4 -> BlockRole.HEADING_4
        5 -> BlockRole.HEADING_5
        else -> BlockRole.HEADING_6
    }
}

private fun String.sliceRange(range: IntRange): String =
    substring(range.first, range.last + 1)

private fun MarkdownRenderSpan.toLayoutSpan(): MdInlineSpan? = when (this) {
    is MarkdownRenderSpan.Strong -> MdInlineSpan(start, end, MdInlineKind.STRONG)
    is MarkdownRenderSpan.Emphasis -> MdInlineSpan(start, end, MdInlineKind.EMPHASIS)
    is MarkdownRenderSpan.Strikethrough -> MdInlineSpan(start, end, MdInlineKind.STRIKETHROUGH)
    is MarkdownRenderSpan.Code -> MdInlineSpan(start, end, MdInlineKind.CODE)
    is MarkdownRenderSpan.Link -> MdInlineSpan(start, end, MdInlineKind.LINK)
    is MarkdownRenderSpan.Plain,
    is MarkdownRenderSpan.Image -> null
}

private fun List<MarkdownRenderSpan>.toLayoutSpans(): List<MdInlineSpan> =
    mapNotNull { it.toLayoutSpan() }
