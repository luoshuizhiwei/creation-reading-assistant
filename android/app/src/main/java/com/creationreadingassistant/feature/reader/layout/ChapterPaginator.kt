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
    )

    class ChapterLayout(
        val pages: List<Page>,
        /** 每页首字符在章内的偏移。这是唯一需要持久化的东西。 */
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

    /** 段落及其在章内的绝对偏移。 */
    private class Placed(val para: LayoutParagraph, val lines: List<LayoutLine>)

    fun paginate(
        paragraphs: List<LayoutParagraph>,
        cfg: LayoutConfig,
        ruler: TextRuler,
        oracle: BreakOracle,
    ): ChapterLayout {
        val placed = paragraphs.map { p ->
            Placed(p, LineComposer.layoutParagraph(p, cfg, ruler, oracle))
        }

        // 展平成 (行, 所属段, 是否段末) 的序列，同时算出每行的绝对字符偏移
        data class Item(val line: LayoutLine, val para: LayoutParagraph, val isParaEnd: Boolean)
        val items = ArrayList<Item>()
        for (pl in placed) {
            pl.lines.forEachIndexed { i, ln ->
                items.add(Item(ln, pl.para, i == pl.lines.lastIndex))
            }
        }
        if (items.isEmpty()) {
            return ChapterLayout(emptyList(), intArrayOf(0), 0)
        }

        val pages = ArrayList<Page>()
        val starts = ArrayList<Int>()
        var i = 0
        while (i < items.size) {
            val pageLines = ArrayList<LayoutLine>()
            val pageItems = ArrayList<Item>()
            var used = 0f

            while (i < items.size) {
                val it = items[i]
                val h = lineHeight(it.line, cfg)
                val extra = if (it.isParaEnd) cfg.paragraphSpacingPx else 0f
                if (pageLines.isNotEmpty() && used + h > cfg.contentHeightPx + LineComposer.EPS) break
                pageLines.add(it.line)
                pageItems.add(it)
                used += h + extra
                i++
            }

            // keep-with-next：末行是标题且后面还有内容 → 把标题推到次页
            if (pageLines.size > 1 && i < items.size) {
                val lastIdx = pageItems.lastIndex
                if (pageItems[lastIdx].line.role == BlockRole.HEADING) {
                    pageLines.removeAt(lastIdx)
                    pageItems.removeAt(lastIdx)
                    i--
                    used -= lineHeight(items[i].line, cfg)
                }
            }

            val tops = distributeBottomSlack(pageItems.map { it.line }, pageItems.map { it.isParaEnd }, used, cfg)
            val startOffset = absoluteOffset(pageItems.first().para, pageItems.first().line.startInText)
            val endOffset = absoluteOffset(pageItems.last().para, pageItems.last().line.endInText)

            starts.add(startOffset)
            pages.add(
                Page(
                    index = pages.size,
                    startCharOffset = startOffset,
                    endCharOffset = endOffset,
                    lines = pageLines.toList(),
                    lineTops = tops,
                    lineParaOffsets = IntArray(pageItems.size) { pageItems[it].para.charOffset },
                ),
            )
        }

        val charCount = paragraphs.sumOf { it.text.length }
        return ChapterLayout(pages, starts.toIntArray(), charCount)
    }

    /**
     * 底部均摊：把页面剩余高度平均摊到**行**之间。
     *
     * 摊到行而不是摊到段：中文小说常见「一段占满整页」，那种页只有一个段，
     * 按段摊等于不摊。
     */
    private fun distributeBottomSlack(
        lines: List<LayoutLine>,
        isParaEnd: List<Boolean>,
        used: Float,
        cfg: LayoutConfig,
    ): FloatArray {
        val tops = FloatArray(lines.size)
        if (lines.isEmpty()) return tops
        val slack = (cfg.contentHeightPx - used).coerceAtLeast(0f)
        val gaps = (lines.size - 1).coerceAtLeast(1)
        // 只在剩余不多时才摊；剩太多说明是章末短页，摊开反而怪
        val perGap = if (slack < cfg.lineHeightPx) slack / gaps else 0f

        var y = 0f
        for (idx in lines.indices) {
            tops[idx] = y
            y += lineHeight(lines[idx], cfg)
            if (isParaEnd.getOrElse(idx) { false }) y += cfg.paragraphSpacingPx
            if (idx < lines.size - 1) y += perGap
        }
        return tops
    }

    private fun lineHeight(line: LayoutLine, cfg: LayoutConfig): Float =
        if (line.role == BlockRole.HEADING) cfg.lineHeightPx * cfg.headingScale else cfg.lineHeightPx

    private fun absoluteOffset(para: LayoutParagraph, inParagraph: Int): Int =
        para.charOffset + inParagraph
}
