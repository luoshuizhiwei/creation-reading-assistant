package com.creationreadingassistant.feature.reader.doc

/**
 * 规范阅读文本字符偏移 ↔ 解码后源文本字符偏移 的双向映射。
 *
 * [MarkdownBlock] 与 [MdInline] 的 [sourceRange] / [canonicalRange] 都是解析片段内的
 * 全局坐标，因此映射表直接以这些边界为分段点，不需要再叠加 block 基准。
 *
 * 对任意规范偏移 c，映射到源偏移 s = c + delta(c)，其中 delta 是分段常数函数。
 */
class MarkdownOffsetMap(points: List<DeltaPoint>) {

    private val points: Array<DeltaPoint> = points.sortedBy { it.canonicalOffset }.toTypedArray()

    /** 规范偏移 → 源偏移。 */
    fun toSource(canonicalOffset: Int): Int {
        if (points.isEmpty()) return canonicalOffset
        val idx = floorIndex(canonicalOffset)
        val base = points[idx].sourceDelta
        return canonicalOffset + base
    }

    /** 源偏移 → 规范偏移（最近 floor）。 */
    fun toCanonical(sourceOffset: Int): Int {
        if (points.isEmpty()) return sourceOffset
        // 源偏移随规范偏移单调不减；二分找最后一个满足 c + delta(c) <= sourceOffset 的 c。
        var lo = 0
        var hi = points.lastIndex
        var result = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val p = points[mid]
            if (p.canonicalOffset + p.sourceDelta <= sourceOffset) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val p = points[result]
        val c = p.canonicalOffset
        val s = c + p.sourceDelta
        return c + (sourceOffset - s).coerceAtLeast(0)
    }

    private fun floorIndex(canonicalOffset: Int): Int {
        var lo = 0
        var hi = points.lastIndex
        var result = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (points[mid].canonicalOffset <= canonicalOffset) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    data class DeltaPoint(
        /** 规范文本偏移 */
        val canonicalOffset: Int,
        /** 源偏移 - 规范偏移 */
        val sourceDelta: Int,
    )

    override fun toString(): String = points.joinToString(prefix = "[", postfix = "]") { "(${it.canonicalOffset},${it.sourceDelta})" }

    companion object {

        /** 从一组 MarkdownBlock 构建全章映射。 */
        fun fromBlocks(blocks: List<MarkdownBlock>): MarkdownOffsetMap {
            val events = mutableListOf<DeltaPoint>()
            events.add(DeltaPoint(0, 0))
            walkBlocks(blocks, events)
            return MarkdownOffsetMap(mergeEvents(events))
        }

        private fun walkBlocks(
            blocks: List<MarkdownBlock>,
            events: MutableList<DeltaPoint>,
        ) {
            for (block in blocks) {
                val cStart = block.canonicalRange.first
                val sStart = block.sourceRange.first
                events.add(DeltaPoint(cStart, sStart - cStart))
                val hasInlineEvents = collectInlineEvents(block, events)
                // 块尾事件：没有行内内容的块（代码块、分隔线等）需要它作为边界；
                // 有行内内容的块，其末尾已由最后一个行内节点覆盖，再添加块尾事件会
                // 把规范文本末尾映射到 Markdown 闭合标记之后。
                if (!hasInlineEvents) {
                    val cEnd = block.canonicalRange.last + 1
                    val sEnd = block.sourceRange.last + 1
                    events.add(DeltaPoint(cEnd, sEnd - cEnd))
                }

                val nested = when (block) {
                    is MarkdownBlock.BlockQuote -> block.blocks
                    is MarkdownBlock.OrderedList -> block.items.flatten()
                    is MarkdownBlock.UnorderedList -> block.items.flatten()
                    is MarkdownBlock.TaskList -> block.items.flatMap { it.blocks }
                    else -> emptyList()
                }
                if (nested.isNotEmpty()) {
                    walkBlocks(nested, events)
                }
            }
        }

        private fun collectInlineEvents(
            block: MarkdownBlock,
            events: MutableList<DeltaPoint>,
        ): Boolean {
            val inlines = when (block) {
                is MarkdownBlock.Heading -> block.inlines
                is MarkdownBlock.Paragraph -> block.inlines
                is MarkdownBlock.Table -> (block.header + block.rows.flatten()).flatMap { it.inlines }
                is MarkdownBlock.BlockQuote,
                is MarkdownBlock.OrderedList,
                is MarkdownBlock.UnorderedList,
                is MarkdownBlock.TaskList,
                is MarkdownBlock.FencedCodeBlock,
                is MarkdownBlock.IndentedCodeBlock,
                is MarkdownBlock.HorizontalRule -> return false
            }
            for (inline in inlines) {
                collectInlineNodeEvents(inline, events)
            }
            return inlines.isNotEmpty()
        }

        private fun collectInlineNodeEvents(
            node: MdInline,
            events: MutableList<DeltaPoint>,
        ) {
            val cStart = node.canonicalRange.first
            val cEnd = node.canonicalRange.last + 1
            val sStart = node.sourceRange.first
            val sEnd = node.sourceRange.last + 1

            when (node) {
                is MdInline.Text,
                is MdInline.Code,
                is MdInline.HardLineBreak,
                is MdInline.SoftLineBreak -> {
                    // 叶子节点：规范文本与源文本并行，记录自身 delta 以覆盖容器边界。
                    val delta = sStart - cStart
                    events.add(DeltaPoint(cStart, delta))
                    events.add(DeltaPoint(cEnd, delta))
                }

                is MdInline.Strong,
                is MdInline.Emphasis,
                is MdInline.Strikethrough,
                is MdInline.Link -> {
                    val children = when (node) {
                        is MdInline.Strong -> node.children
                        is MdInline.Emphasis -> node.children
                        is MdInline.Strikethrough -> node.children
                        is MdInline.Link -> node.children
                        else -> emptyList()
                    }
                    // 容器节点只贡献“打开”事件：用第一个子节点的源起点计算 delta，
                    // 这样规范文本开头能跳过 `**`、`[` 等标记。
                    // 结束位置由子节点的结束事件覆盖，避免容器闭合标记把规范末尾映射到源末尾之外。
                    if (children.isNotEmpty()) {
                        val openDelta = children.first().sourceRange.first - cStart
                        events.add(DeltaPoint(cStart, openDelta))
                    }

                    for (child in children) {
                        collectInlineNodeEvents(child, events)
                    }
                }

                is MdInline.Image -> {
                    // 图片在规范文本中是 alt 文本，源文本中是 `![alt](url)`。
                    // MdInline.Image 的 sourceRange 已经被修正为仅 alt 部分。
                    val delta = sStart - cStart
                    events.add(DeltaPoint(cStart, delta))
                    events.add(DeltaPoint(cEnd, delta))
                }
            }
        }

        private fun mergeEvents(events: List<DeltaPoint>): List<DeltaPoint> {
            if (events.isEmpty()) return emptyList()
            // 同一 canonical 偏移处，取最大 sourceDelta。
            // 块边界事件给出“块起点”的 delta；行内节点事件给出“可见文本起点”的 delta，
            // 后者更精确（已扣除 Markdown 标记），因此必须保留最大值。
            val sorted = events.sortedWith(compareBy({ it.canonicalOffset }, { -it.sourceDelta }))
            val out = mutableListOf<DeltaPoint>()
            var currentCanonical = sorted[0].canonicalOffset
            var currentDelta = sorted[0].sourceDelta
            for (i in 1 until sorted.size) {
                val p = sorted[i]
                if (p.canonicalOffset == currentCanonical) {
                    if (p.sourceDelta > currentDelta) currentDelta = p.sourceDelta
                } else {
                    out.add(DeltaPoint(currentCanonical, currentDelta))
                    currentCanonical = p.canonicalOffset
                    currentDelta = p.sourceDelta
                }
            }
            out.add(DeltaPoint(currentCanonical, currentDelta))
            // 保证首点从 0 开始，便于 floor 查找
            if (out[0].canonicalOffset != 0) {
                out.add(0, DeltaPoint(0, out[0].sourceDelta))
            }
            return out
        }
    }
}
