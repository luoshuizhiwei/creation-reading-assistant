package com.creationreadingassistant.feature.reader.doc

/**
 * 纯语义渲染模型：把 [MarkdownParser.MarkdownChapter] 稳定地转换为按阅读顺序排列的
 * 扁平渲染单元（render units）。
 *
 * 设计目标：
 * - **纯 Kotlin、无 Compose、无 I/O**：滚动与分页两端共享同一份扁平模型，
 *   不再各自解释 Markdown 块树（列表标记、任务勾选、引用层级、表格、代码语言、
 *   行内样式在两端目前分别实现，语义容易漂移）。
 * - **不复制 canonical 内容、不改变 offset**：单元与行内 spans 只携带
 *   [canonicalRange] / [sourceRange] 与元数据；渲染文本由调用方从
 *   [MarkdownParser.MarkdownChapter.canonicalText] 按范围切片。
 * - **容器展开**：BlockQuote / 列表 / 任务列表不产生独立单元，其子块按出现顺序展开；
 *   列表项首块携带 marker（`• ` / `3. `），任务项首块携带 checked；续段不携带 marker。
 *   表格作为单个单元保留（表头与数据行必须同项渲染以维持列对齐），单元格不重复。
 * - **稳定 identity**：[MarkdownRenderUnit.id] 是结构路径（如 `b0.q0.l1.c0`），
 *   同一章节重复解析结果完全相等；[MarkdownRenderUnit.index] 是阅读顺序下标。
 *
 * 坐标约定与 [MarkdownBlock] / [MdInline] 一致：范围都是解析片段内坐标
 * （流式章节 = 章内局部；小文件整本解析 = 全书全局），调用方按既有章节基准换算。
 *
 * 本任务不接 Compose、不接 TTS、不加载图片；图片只以行内 span 元数据表达。
 */
object MarkdownRenderModel {

    /**
     * 将章节转换为按阅读顺序排列的扁平渲染单元。
     *
     * 顺序与递归渲染一致：BlockQuote / 列表 / 任务列表容器不产生单元，
     * 其子块按出现顺序展开；表格作为单个单元保留。
     */
    fun flatten(chapter: MarkdownParser.MarkdownChapter): List<MarkdownRenderUnit> {
        val out = ArrayList<MarkdownRenderUnit>()
        chapter.blocks.forEachIndexed { topIndex, block ->
            emitBlock(
                block,
                path = "b$topIndex",
                depth = 0,
                quoteDepth = 0,
                topBlockIndex = topIndex,
                out = out,
            )
        }
        return out
    }

    private fun emitBlock(
        block: MarkdownBlock,
        path: String,
        depth: Int,
        quoteDepth: Int,
        topBlockIndex: Int,
        out: MutableList<MarkdownRenderUnit>,
    ) {
        when (block) {
            is MarkdownBlock.BlockQuote ->
                block.blocks.forEachIndexed { i, child ->
                    emitBlock(child, "$path.q$i", depth + 1, quoteDepth + 1, topBlockIndex, out)
                }

            is MarkdownBlock.UnorderedList ->
                block.items.forEachIndexed { i, item ->
                    emitItem(item, "$path.l$i", depth + 1, quoteDepth, topBlockIndex, marker = "• ", ordinal = null, out = out)
                }

            is MarkdownBlock.OrderedList ->
                block.items.forEachIndexed { i, item ->
                    val ordinal = block.startNumber + i
                    emitItem(item, "$path.l$i", depth + 1, quoteDepth, topBlockIndex, marker = "$ordinal. ", ordinal = ordinal, out = out)
                }

            is MarkdownBlock.TaskList ->
                block.items.forEachIndexed { i, item ->
                    emitTaskItem(item, "$path.t$i", depth + 1, quoteDepth, topBlockIndex, out)
                }

            else -> emitLeaf(block, path, depth, quoteDepth, topBlockIndex, listMarker = null, ordinal = null, out = out)
        }
    }

    /** 列表项：首块为叶块时输出带 marker 的单元，其余块按普通块展开。 */
    private fun emitItem(
        item: List<MarkdownBlock>,
        path: String,
        depth: Int,
        quoteDepth: Int,
        topBlockIndex: Int,
        marker: String,
        ordinal: Int?,
        out: MutableList<MarkdownRenderUnit>,
    ) {
        if (item.isEmpty()) return
        item.forEachIndexed { childIndex, block ->
            if (childIndex == 0 && isLeafBlock(block)) {
                emitLeaf(block, "$path.c$childIndex", depth, quoteDepth, topBlockIndex, listMarker = marker, ordinal = ordinal, out = out)
            } else {
                emitBlock(block, "$path.c$childIndex", depth, quoteDepth, topBlockIndex, out)
            }
        }
    }

    /** 任务项：首块为叶块时输出带 checked 的单元，其余块按普通块展开。 */
    private fun emitTaskItem(
        item: MarkdownBlock.TaskList.TaskListItem,
        path: String,
        depth: Int,
        quoteDepth: Int,
        topBlockIndex: Int,
        out: MutableList<MarkdownRenderUnit>,
    ) {
        if (item.blocks.isEmpty()) return
        item.blocks.forEachIndexed { childIndex, block ->
            if (childIndex == 0 && isLeafBlock(block)) {
                val content = MarkdownRenderContent.TaskItem(
                    checked = item.checked,
                    spans = spansOf(block),
                )
                out += MarkdownRenderUnit(
                    index = out.size,
                    id = "$path.c$childIndex",
                    kind = MarkdownRenderKind.TASK_ITEM,
                    depth = depth,
                    blockquoteDepth = quoteDepth,
                    topBlockIndex = topBlockIndex,
                    canonicalRange = block.canonicalRange,
                    sourceRange = block.sourceRange,
                    content = content,
                )
            } else {
                emitBlock(block, "$path.c$childIndex", depth, quoteDepth, topBlockIndex, out)
            }
        }
    }

    private fun isLeafBlock(block: MarkdownBlock): Boolean = when (block) {
        is MarkdownBlock.BlockQuote,
        is MarkdownBlock.UnorderedList,
        is MarkdownBlock.OrderedList,
        is MarkdownBlock.TaskList -> false
        else -> true
    }

    private fun emitLeaf(
        block: MarkdownBlock,
        path: String,
        depth: Int,
        quoteDepth: Int,
        topBlockIndex: Int,
        listMarker: String?,
        ordinal: Int?,
        out: MutableList<MarkdownRenderUnit>,
    ) {
        val content: MarkdownRenderContent = when (block) {
            is MarkdownBlock.Heading -> MarkdownRenderContent.Heading(level = block.level, spans = spansOf(block))
            is MarkdownBlock.Paragraph -> if (listMarker != null) {
                MarkdownRenderContent.ListItem(marker = listMarker, ordinal = ordinal, spans = spansOf(block))
            } else {
                MarkdownRenderContent.Paragraph(spans = spansOf(block))
            }
            is MarkdownBlock.FencedCodeBlock -> MarkdownRenderContent.CodeBlock(language = block.language)
            is MarkdownBlock.IndentedCodeBlock -> MarkdownRenderContent.CodeBlock(language = null)
            is MarkdownBlock.Table -> tableOf(block)
            is MarkdownBlock.HorizontalRule -> MarkdownRenderContent.HorizontalRule
            else -> error("unreachable: container block $block")
        }
        val kind = when (block) {
            is MarkdownBlock.Heading -> MarkdownRenderKind.HEADING
            is MarkdownBlock.Paragraph -> if (listMarker != null) MarkdownRenderKind.LIST_ITEM else MarkdownRenderKind.PARAGRAPH
            is MarkdownBlock.FencedCodeBlock,
            is MarkdownBlock.IndentedCodeBlock -> MarkdownRenderKind.CODE_BLOCK
            is MarkdownBlock.Table -> MarkdownRenderKind.TABLE
            is MarkdownBlock.HorizontalRule -> MarkdownRenderKind.HORIZONTAL_RULE
            else -> error("unreachable: container block $block")
        }
        out += MarkdownRenderUnit(
            index = out.size,
            id = path,
            kind = kind,
            depth = depth,
            blockquoteDepth = quoteDepth,
            topBlockIndex = topBlockIndex,
            canonicalRange = block.canonicalRange,
            sourceRange = block.sourceRange,
            content = content,
        )
    }

    // ── 表格 ────────────────────────────────────────────────────────

    private fun tableOf(block: MarkdownBlock.Table): MarkdownRenderContent.Table {
        val header = block.header.mapIndexed { columnIndex, cell ->
            renderCell(cell, rowIndex = 0, columnIndex = columnIndex)
        }
        val rows = block.rows.mapIndexed { rowIndex, row ->
            row.mapIndexed { columnIndex, cell ->
                renderCell(cell, rowIndex = rowIndex + 1, columnIndex = columnIndex)
            }
        }
        return MarkdownRenderContent.Table(header = header, rows = rows)
    }

    private fun renderCell(
        cell: MarkdownBlock.Table.TableCell,
        rowIndex: Int,
        columnIndex: Int,
    ): MarkdownRenderCell {
        val canonicalRange = inlinesRange(cell.inlines) { it.canonicalRange }
        val sourceRange = inlinesRange(cell.inlines) { it.sourceRange }
        return MarkdownRenderCell(
            rowIndex = rowIndex,
            columnIndex = columnIndex,
            alignment = when (cell.alignment) {
                MarkdownBlock.Table.TableAlignment.LEFT -> MarkdownRenderAlignment.LEFT
                MarkdownBlock.Table.TableAlignment.CENTER -> MarkdownRenderAlignment.CENTER
                MarkdownBlock.Table.TableAlignment.RIGHT -> MarkdownRenderAlignment.RIGHT
                MarkdownBlock.Table.TableAlignment.NONE -> MarkdownRenderAlignment.NONE
            },
            canonicalRange = canonicalRange,
            sourceRange = sourceRange,
            spans = collectSpans(cell.inlines, base = canonicalRange.first),
        )
    }

    /** 空行内列表 → 空范围 `0 until 0`；否则为首个到末个节点范围。 */
    private fun inlinesRange(
        inlines: List<MdInline>,
        rangeOf: (MdInline) -> IntRange,
    ): IntRange {
        if (inlines.isEmpty()) return 0 until 0
        return rangeOf(inlines.first()).first until rangeOf(inlines.last()).last + 1
    }

    // ── 行内 spans ──────────────────────────────────────────────────

    private fun spansOf(block: MarkdownBlock): List<MarkdownRenderSpan> {
        val inlines = when (block) {
            is MarkdownBlock.Heading -> block.inlines
            is MarkdownBlock.Paragraph -> block.inlines
            else -> emptyList()
        }
        return collectSpans(inlines, base = block.canonicalRange.first)
    }

    /**
     * 扁平化行内节点为 spans。容器节点（Emphasis/Strong/Strikethrough/Link）先输出
     * 覆盖自身范围的 span，再递归子节点；叶子节点（Text/Code/换行/Image）输出
     * 覆盖各自文本范围的 span。因此同一文本区间的样式 span 与叶 span 可重叠。
     *
     * [base] 是所属文本载体（叶单元或表格单元格）的 canonicalRange.first，
     * span 的 [MarkdownRenderSpan.start]/[end] 相对该 base。
     */
    private fun collectSpans(inlines: List<MdInline>, base: Int): List<MarkdownRenderSpan> {
        val out = ArrayList<MarkdownRenderSpan>()
        for (inline in inlines) {
            val start = inline.canonicalRange.first - base
            val end = inline.canonicalRange.last + 1 - base
            when (inline) {
                is MdInline.Text -> out += MarkdownRenderSpan.Plain(start, end, inline.sourceRange)
                is MdInline.Code -> out += MarkdownRenderSpan.Code(start, end, inline.sourceRange)
                is MdInline.HardLineBreak,
                is MdInline.SoftLineBreak -> out += MarkdownRenderSpan.Plain(start, end, inline.sourceRange)

                is MdInline.Emphasis -> {
                    out += MarkdownRenderSpan.Emphasis(start, end, inline.sourceRange)
                    out += collectSpans(inline.children, base)
                }
                is MdInline.Strong -> {
                    out += MarkdownRenderSpan.Strong(start, end, inline.sourceRange)
                    out += collectSpans(inline.children, base)
                }
                is MdInline.Strikethrough -> {
                    out += MarkdownRenderSpan.Strikethrough(start, end, inline.sourceRange)
                    out += collectSpans(inline.children, base)
                }
                is MdInline.Link -> {
                    out += MarkdownRenderSpan.Link(
                        destination = inline.url,
                        title = inline.title,
                        start = start,
                        end = end,
                        sourceRange = inline.sourceRange,
                    )
                    out += collectSpans(inline.children, base)
                }
                is MdInline.Image -> out += MarkdownRenderSpan.Image(
                    source = inline.url,
                    title = inline.title,
                    alt = inline.alt,
                    start = start,
                    end = end,
                    sourceRange = inline.sourceRange,
                )
            }
        }
        return out
    }
}

/**
 * 扁平渲染单元（阅读顺序）。
 *
 * [index] 为阅读顺序下标（即扁平列表中的位置）；[id] 为结构路径，稳定且唯一。
 * [depth] 为容器嵌套深度（blockquote + 列表 + 任务列表逐层 +1），渲染缩进据此还原；
 * [blockquoteDepth] 单独表达引用块嵌套层数，供引用样式/TTS 语义区分。
 * [topBlockIndex] 为该单元所属顶层块在 [MarkdownParser.MarkdownChapter.blocks] 中的下标，
 * 渲染层据此还原「顶层块间间距、容器内无间距」的既有排版语义。
 * [canonicalRange]/[sourceRange] 直接沿用解析产物，不改变 offset。
 */
data class MarkdownRenderUnit(
    val index: Int,
    val id: String,
    val kind: MarkdownRenderKind,
    val depth: Int,
    val blockquoteDepth: Int,
    val topBlockIndex: Int,
    val canonicalRange: IntRange,
    val sourceRange: IntRange,
    val content: MarkdownRenderContent,
)

/** 渲染单元种类。 */
enum class MarkdownRenderKind {
    HEADING,
    PARAGRAPH,
    LIST_ITEM,
    TASK_ITEM,
    CODE_BLOCK,
    TABLE,
    HORIZONTAL_RULE,
}

/** 渲染单元的语义内容。 */
sealed interface MarkdownRenderContent {
    data class Heading(
        val level: Int,
        val spans: List<MarkdownRenderSpan>,
    ) : MarkdownRenderContent

    data class Paragraph(
        val spans: List<MarkdownRenderSpan>,
    ) : MarkdownRenderContent

    /** 列表项：首块叶单元。 [marker] 为渲染前缀（`• ` / `3. `），[ordinal] 为有序序号。 */
    data class ListItem(
        val marker: String,
        val ordinal: Int?,
        val spans: List<MarkdownRenderSpan>,
    ) : MarkdownRenderContent

    /** 任务项：首块叶单元，[checked] 为勾选状态（marker 由 UI 按状态生成）。 */
    data class TaskItem(
        val checked: Boolean,
        val spans: List<MarkdownRenderSpan>,
    ) : MarkdownRenderContent

    data class CodeBlock(
        val language: String?,
    ) : MarkdownRenderContent

    data class Table(
        val header: List<MarkdownRenderCell>,
        val rows: List<List<MarkdownRenderCell>>,
    ) : MarkdownRenderContent

    /** 分隔线：无文本结构单元。 */
    data object HorizontalRule : MarkdownRenderContent
}

/**
 * 表格单元格。每个单元格在表格中出现且只出现一次；
 * 空单元格（无行内节点）的范围为 `0 until 0`。
 */
data class MarkdownRenderCell(
    val rowIndex: Int,
    val columnIndex: Int,
    val alignment: MarkdownRenderAlignment,
    val canonicalRange: IntRange,
    val sourceRange: IntRange,
    val spans: List<MarkdownRenderSpan>,
)

/** 表格单元格对齐。 */
enum class MarkdownRenderAlignment {
    LEFT,
    CENTER,
    RIGHT,
    NONE,
}

/**
 * 行内渲染 span。
 *
 * [start]/[end] 相对所属文本载体（叶单元或表格单元格）的 canonicalRange.first；
 * [sourceRange] 沿用解析产物。容器 span（Emphasis/Strong/Strikethrough/Link）与
 * 叶 span（Plain/Code/Image）可重叠；叶 span 按阅读顺序无缝覆盖载体文本。
 */
sealed interface MarkdownRenderSpan {
    val start: Int
    val end: Int
    val sourceRange: IntRange

    data class Plain(
        override val start: Int,
        override val end: Int,
        override val sourceRange: IntRange,
    ) : MarkdownRenderSpan

    data class Emphasis(
        override val start: Int,
        override val end: Int,
        override val sourceRange: IntRange,
    ) : MarkdownRenderSpan

    data class Strong(
        override val start: Int,
        override val end: Int,
        override val sourceRange: IntRange,
    ) : MarkdownRenderSpan

    data class Strikethrough(
        override val start: Int,
        override val end: Int,
        override val sourceRange: IntRange,
    ) : MarkdownRenderSpan

    data class Code(
        override val start: Int,
        override val end: Int,
        override val sourceRange: IntRange,
    ) : MarkdownRenderSpan

    data class Link(
        val destination: String,
        val title: String?,
        override val start: Int,
        override val end: Int,
        override val sourceRange: IntRange,
    ) : MarkdownRenderSpan

    data class Image(
        val source: String,
        val title: String?,
        val alt: String,
        override val start: Int,
        override val end: Int,
        override val sourceRange: IntRange,
    ) : MarkdownRenderSpan
}
