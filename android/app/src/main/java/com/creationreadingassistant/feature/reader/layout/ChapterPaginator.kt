package com.creationreadingassistant.feature.reader.layout

/**
 * 行 → 页。
 *
 * 三个要点：
 *
 * 1. **只缓存 `pageStarts: IntArray`。** 一章 60 页仅 240 字节，而整章行对象常驻要
 *    0.5 MB/章。页按需重排约 1ms，完全够用。这个策略成立的前提是
 *    「排版是纯函数」——同一配置下重排必然得到逐字段相同的结果，由单测锁定。
 *
 * 2. **keep-with-next**：标题不能孤零零留在页底。它下面至少要跟一行正文，
 *    否则整块推到次页。
 *
 * 3. **底部均摊摊到「行」而不是「段」**。候选方案里有一个摊到段（slice）上，
 *    而中文小说里「单段跨整页」是常见页型，那种页只有一个 slice，均摊完全不生效。
 */
object ChapterPaginator {

    /**
     * @param lineTops 每行在页内的 y 顶点（已含底部均摊）
     * @param lineParaOffsets 每行所属段落的段首在章内的字符偏移。
     *        [LayoutLine] 里的一切偏移（startInText / clusterStarts）都是**段内**的，
     *        绘制与选区要换算回章内偏移，必须知道该行属于哪个段。
     */
    class Page(
        val index: Int,
        val startCharOffset: Int,
        val endCharOffset: Int,
        val lines: List<LayoutLine>,
        val lineTops: FloatArray,
        val lineParaOffsets: IntArray,
        /** 本页图片，与 [lines] 平行存在；纯文字页恒为空。 */
        val images: List<PlacedImage> = emptyList(),
    )

    class ChapterLayout(
        val pages: List<Page>,
        /**
         * 每页首字符在章内的偏移。这是唯一需要持久化的东西。
         *
         * 含图片时允许非严格递增：纯图片页和后继文字页可以有相同起点。
         * [pageIndexFor] 有意取最后一个 `<=`，恢复位置时优先落到可定位的文字页。
         */
        val pageStarts: IntArray,
        val charCount: Int,
    ) {
        /** 章内字符偏移 → 页号。位置恢复的真源是偏移，不是页号。 */
        fun pageIndexFor(charOffset: Int): Int {
            if (pageStarts.isEmpty()) return 0
            var lo = 0
            var hi = pageStarts.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (pageStarts[mid] <= charOffset) lo = mid else hi = mid - 1
            }
            return lo
        }
    }

    private data class Item(val line: LayoutLine, val para: LayoutParagraph, val isParaEnd: Boolean)

    private sealed interface BlockItem {
        val startOffset: Int
        val endOffset: Int

        data class Text(
            val line: LayoutLine,
            val para: LayoutParagraph,
            val isParaEnd: Boolean,
        ) : BlockItem {
            override val startOffset: Int get() = para.charOffset + line.startInText
            override val endOffset: Int get() = para.charOffset + line.endInText
        }

        data class Image(
            val block: LayoutBlock.Image,
            val width: Float,
            val height: Float,
        ) : BlockItem {
            override val startOffset: Int get() = block.anchorOffset
            override val endOffset: Int get() = block.anchorOffset
        }
    }

    /** 按需流式驱动段落装行，避免大章节全量 map 的瞬时内存峰值与 GC 停顿。 */
    private class ParagraphItemStream(
        private val paragraphs: List<LayoutParagraph>,
        private val cfg: LayoutConfig,
        private val ruler: TextRuler,
        private val oracle: BreakOracle,
    ) {
        private var paraIndex = 0
        private val deque = ArrayDeque<Item>()

        val hasNext: Boolean
            get() {
                fillIfNeeded()
                return deque.isNotEmpty()
            }

        fun peek(): Item? {
            fillIfNeeded()
            return deque.firstOrNull()
        }

        fun poll(): Item? {
            fillIfNeeded()
            return if (deque.isNotEmpty()) deque.removeFirst() else null
        }

        fun pushBack(item: Item) {
            deque.addFirst(item)
        }

        private fun fillIfNeeded() {
            while (deque.isEmpty() && paraIndex < paragraphs.size) {
                val p = paragraphs[paraIndex++]
                if (p.text.isEmpty()) continue
                val lines = LineComposer.layoutParagraph(p, cfg, ruler, oracle)
                lines.forEachIndexed { i, ln ->
                    ln.listMarker = p.listMarker
                    deque.addLast(Item(ln, p, i == lines.lastIndex))
                }
            }
        }
    }

    /** 混排图文按需流式驱动装行。 */
    private class BlockItemStream(
        private val blocks: List<LayoutBlock>,
        private val cfg: LayoutConfig,
        private val ruler: TextRuler,
        private val oracle: BreakOracle,
    ) {
        private var blockIndex = 0
        private val deque = ArrayDeque<BlockItem>()

        val hasNext: Boolean
            get() {
                fillIfNeeded()
                return deque.isNotEmpty()
            }

        fun peek(): BlockItem? {
            fillIfNeeded()
            return deque.firstOrNull()
        }

        fun poll(): BlockItem? {
            fillIfNeeded()
            return if (deque.isNotEmpty()) deque.removeFirst() else null
        }

        fun pushBack(item: BlockItem) {
            deque.addFirst(item)
        }

        private fun fillIfNeeded() {
            while (deque.isEmpty() && blockIndex < blocks.size) {
                val block = blocks[blockIndex++]
                when (block) {
                    is LayoutBlock.Text -> {
                        val para = block.paragraph
                        if (para.text.isNotEmpty()) {
                            val lines = LineComposer.layoutParagraph(para, cfg, ruler, oracle)
                            lines.forEachIndexed { i, line ->
                                line.listMarker = para.listMarker
                                deque.addLast(BlockItem.Text(line, para, i == lines.lastIndex))
                            }
                        }
                    }
                    is LayoutBlock.Image -> {
                        val intrinsicW = if (block.widthPx > 0f && block.heightPx > 0f) block.widthPx else cfg.contentWidthPx
                        val intrinsicH = if (block.widthPx > 0f && block.heightPx > 0f) block.heightPx else cfg.contentWidthPx * 0.75f
                        val availableImageHeight = (cfg.contentHeightPx - cfg.contentTopPaddingPx).coerceAtLeast(1f)
                        val scale = minOf(1f, cfg.contentWidthPx / intrinsicW, availableImageHeight / intrinsicH)
                        deque.addLast(BlockItem.Image(block, intrinsicW * scale, intrinsicH * scale))
                    }
                }
            }
        }
    }

    /**
     * 纯文本流式排版主干。
     * 当 [onInitialPages] 非空且已排页数达到 [initialPageThreshold] 且覆盖 [targetOffset] 时，
     * 立即产出阶段性 [ChapterLayout] 供首屏无缝即时渲染。
     */
    fun paginateProgressiveText(
        paragraphs: List<LayoutParagraph>,
        cfg: LayoutConfig,
        ruler: TextRuler,
        oracle: BreakOracle,
        targetOffset: Int = 0,
        initialPageThreshold: Int = 2,
        onInitialPages: ((ChapterLayout) -> Unit)? = null,
    ): ChapterLayout {
        val stream = ParagraphItemStream(paragraphs, cfg, ruler, oracle)
        if (!stream.hasNext) {
            // 空白章也必须有一页，否则 pageStarts 声称有页而 pages 为空，
            // 控制器会结束排版但 currentPage 永远为 null，UI 随之永久转圈。
            val emptyPage = Page(
                index = 0,
                startCharOffset = 0,
                endCharOffset = 0,
                lines = emptyList(),
                lineTops = FloatArray(0),
                lineParaOffsets = IntArray(0),
            )
            return ChapterLayout(listOf(emptyPage), intArrayOf(0), 0)
        }

        val pages = ArrayList<Page>()
        val starts = ArrayList<Int>()
        val charCount = paragraphs.sumOf { it.text.length }
        var initialDispatched = false

        while (stream.hasNext) {
            val pageLines = ArrayList<LayoutLine>()
            val pageItems = ArrayList<Item>()
            var used = cfg.contentTopPaddingPx

            while (stream.hasNext) {
                val it = stream.peek() ?: break
                val h = lineHeight(it.line, cfg)
                val extra = if (it.isParaEnd) cfg.paragraphSpacingPx else 0f
                if (pageLines.isNotEmpty() && used + h > cfg.contentHeightPx + LineComposer.EPS) break
                stream.poll()
                pageLines.add(it.line)
                pageItems.add(it)
                used += h + extra
            }

            // keep-with-next：末行是标题且后面还有内容 → 把标题推到次页
            if (pageLines.size > 1 && stream.hasNext) {
                val lastIdx = pageItems.lastIndex
                if (pageItems[lastIdx].line.role.isHeading()) {
                    val removed = pageItems.removeAt(lastIdx)
                    pageLines.removeAt(lastIdx)
                    stream.pushBack(removed)
                    used -= lineHeight(removed.line, cfg)
                    if (removed.isParaEnd) used -= cfg.paragraphSpacingPx
                }
            }

            val tops = distributeBottomSlack(pageLines, { pageItems[it].isParaEnd }, used, cfg)
            val startOffset = absoluteOffset(pageItems.first().para, pageItems.first().line.startInText)
            val endOffset = absoluteOffset(pageItems.last().para, pageItems.last().line.endInText)

            starts.add(startOffset)
            val page = Page(
                index = pages.size,
                startCharOffset = startOffset,
                endCharOffset = endOffset,
                lines = pageLines.toList(),
                lineTops = tops,
                lineParaOffsets = IntArray(pageItems.size) { pageItems[it].para.charOffset },
            )
            pages.add(page)

            if (!initialDispatched && onInitialPages != null) {
                val satisfiedCount = pages.size >= initialPageThreshold
                val satisfiedOffset = if (targetOffset <= 0) true else page.endCharOffset >= targetOffset
                if (satisfiedCount && satisfiedOffset && stream.hasNext) {
                    initialDispatched = true
                    onInitialPages(ChapterLayout(pages.toList(), starts.toIntArray(), charCount))
                }
            }
        }

        return ChapterLayout(pages, starts.toIntArray(), charCount)
    }

    fun paginate(
        paragraphs: List<LayoutParagraph>,
        cfg: LayoutConfig,
        ruler: TextRuler,
        oracle: BreakOracle,
    ): ChapterLayout = paginateProgressiveText(
        paragraphs = paragraphs,
        cfg = cfg,
        ruler = ruler,
        oracle = oracle,
        targetOffset = 0,
        initialPageThreshold = Int.MAX_VALUE,
        onInitialPages = null,
    )

    /**
     * 文本与块级图片混合渐进式分页。
     */
    fun paginateProgressive(
        blocks: List<LayoutBlock>,
        cfg: LayoutConfig,
        ruler: TextRuler,
        oracle: BreakOracle,
        targetOffset: Int = 0,
        initialPageThreshold: Int = 2,
        onInitialPages: ((ChapterLayout) -> Unit)? = null,
    ): ChapterLayout {
        if (blocks.all { it is LayoutBlock.Text }) {
            return paginateProgressiveText(
                paragraphs = blocks.map { (it as LayoutBlock.Text).paragraph },
                cfg = cfg,
                ruler = ruler,
                oracle = oracle,
                targetOffset = targetOffset,
                initialPageThreshold = initialPageThreshold,
                onInitialPages = onInitialPages,
            )
        }

        val stream = BlockItemStream(blocks, cfg, ruler, oracle)
        if (!stream.hasNext) {
            return ChapterLayout(
                pages = listOf(
                    Page(
                        index = 0,
                        startCharOffset = 0,
                        endCharOffset = 0,
                        lines = emptyList(),
                        lineTops = FloatArray(0),
                        lineParaOffsets = IntArray(0),
                    ),
                ),
                pageStarts = intArrayOf(0),
                charCount = 0,
            )
        }

        val pages = ArrayList<Page>()
        val starts = ArrayList<Int>()
        val charCount = blocks.maxOfOrNull { block ->
            when (block) {
                is LayoutBlock.Text -> block.paragraph.charOffset + block.paragraph.text.length
                is LayoutBlock.Image -> block.anchorOffset
            }
        } ?: 0
        var initialDispatched = false

        while (stream.hasNext) {
            val pageItems = ArrayList<BlockItem>()
            var used = cfg.contentTopPaddingPx

            while (stream.hasNext) {
                val item = stream.peek() ?: break
                val height = itemHeight(item, cfg)
                if (pageItems.isNotEmpty() && used + height > cfg.contentHeightPx + LineComposer.EPS) break
                stream.poll()
                pageItems += item
                used += height + spacingAfter(item, cfg)
            }

            // 标题的下一项可以是正文行，也可以是图片；两种情况都不能把标题孤留页底。
            if (pageItems.size > 1 && stream.hasNext) {
                val last = pageItems.last()
                if (last is BlockItem.Text && last.line.role.isHeading()) {
                    val removed = pageItems.removeAt(pageItems.lastIndex)
                    stream.pushBack(removed)
                    used -= itemHeight(last, cfg) + spacingAfter(last, cfg)
                }
            }

            val slack = (cfg.contentHeightPx - used).coerceAtLeast(0f)
            val gaps = (pageItems.size - 1).coerceAtLeast(1)
            val perGap = if (slack < cfg.lineHeightPx) slack / gaps else 0f
            val lines = ArrayList<LayoutLine>()
            val lineTops = ArrayList<Float>()
            val paraOffsets = ArrayList<Int>()
            val images = ArrayList<PlacedImage>()
            var y = cfg.contentTopPaddingPx

            pageItems.forEachIndexed { index, item ->
                when (item) {
                    is BlockItem.Text -> {
                        lines += item.line
                        lineTops += y
                        paraOffsets += item.para.charOffset
                    }

                    is BlockItem.Image -> {
                        images += PlacedImage(
                            sourceKey = item.block.sourceKey,
                            left = (cfg.contentWidthPx - item.width) / 2f,
                            top = y,
                            width = item.width,
                            height = item.height,
                            anchorOffset = item.block.anchorOffset,
                        )
                    }
                }
                y += itemHeight(item, cfg) + spacingAfter(item, cfg)
                if (index < pageItems.lastIndex) y += perGap
            }

            val startOffset = pageItems.first().startOffset
            val endOffset = pageItems.last().endOffset
            starts += startOffset
            val page = Page(
                index = pages.size,
                startCharOffset = startOffset,
                endCharOffset = endOffset,
                lines = lines,
                lineTops = lineTops.toFloatArray(),
                lineParaOffsets = paraOffsets.toIntArray(),
                images = images,
            )
            pages += page

            if (!initialDispatched && onInitialPages != null) {
                val satisfiedCount = pages.size >= initialPageThreshold
                val satisfiedOffset = if (targetOffset <= 0) true else page.endCharOffset >= targetOffset
                if (satisfiedCount && satisfiedOffset && stream.hasNext) {
                    initialDispatched = true
                    onInitialPages(ChapterLayout(pages.toList(), starts.toIntArray(), charCount))
                }
            }
        }

        return ChapterLayout(pages, starts.toIntArray(), charCount)
    }

    /**
     * 文本与块级图片混合分页。图片整块放置、不拆页，也不占用字符偏移。
     *
     * 纯文本输入直接走 [paginate]，因此既有 TXT 的 pageStarts、行对象与浮点
     * 累加顺序逐字段不变，旧的页索引缓存继续有效。
     */
    fun paginateBlocks(
        blocks: List<LayoutBlock>,
        cfg: LayoutConfig,
        ruler: TextRuler,
        oracle: BreakOracle,
    ): ChapterLayout = paginateProgressive(
        blocks = blocks,
        cfg = cfg,
        ruler = ruler,
        oracle = oracle,
        targetOffset = 0,
        initialPageThreshold = Int.MAX_VALUE,
        onInitialPages = null,
    )

    private fun itemHeight(item: BlockItem, cfg: LayoutConfig): Float = when (item) {
        is BlockItem.Text -> lineHeight(item.line, cfg)
        is BlockItem.Image -> item.height
    }

    private fun spacingAfter(item: BlockItem, cfg: LayoutConfig): Float = when (item) {
        is BlockItem.Text -> if (item.isParaEnd) cfg.paragraphSpacingPx else 0f
        is BlockItem.Image -> cfg.paragraphSpacingPx
    }

    /**
     * 底部均摊：把页面剩余高度平均摊到**行**之间。
     *
     * 摊到行而不是摊到段：中文小说常见「一段占满整页」，那种页只有一个段，
     * 按段摊等于不摊。
     */
    private inline fun distributeBottomSlack(
        lines: List<LayoutLine>,
        isParaEnd: (Int) -> Boolean,
        used: Float,
        cfg: LayoutConfig,
    ): FloatArray {
        val tops = FloatArray(lines.size)
        if (lines.isEmpty()) return tops
        val slack = (cfg.contentHeightPx - used).coerceAtLeast(0f)
        val gaps = (lines.size - 1).coerceAtLeast(1)
        // 只在剩余不多时才摊；剩太多说明是章末短页，摊开反而怪
        val perGap = if (slack < cfg.lineHeightPx) slack / gaps else 0f

        var y = cfg.contentTopPaddingPx
        for (idx in lines.indices) {
            tops[idx] = y
            y += lineHeight(lines[idx], cfg)
            if (isParaEnd(idx)) y += cfg.paragraphSpacingPx
            if (idx < lines.size - 1) y += perGap
        }
        return tops
    }


    private fun lineHeight(line: LayoutLine, cfg: LayoutConfig): Float = when (line.role) {
        BlockRole.HEADING -> cfg.lineHeightPx * cfg.headingScale
        BlockRole.HEADING_1 -> cfg.lineHeightPx * cfg.headingScale * 1.15f
        BlockRole.HEADING_2 -> cfg.lineHeightPx * cfg.headingScale * 1.10f
        BlockRole.HEADING_3 -> cfg.lineHeightPx * cfg.headingScale * 1.05f
        BlockRole.CODE_BLOCK -> cfg.lineHeightPx * cfg.codeScale
        else -> cfg.lineHeightPx
    }

    private fun absoluteOffset(para: LayoutParagraph, inParagraph: Int): Int =
        para.charOffset + inParagraph
}
