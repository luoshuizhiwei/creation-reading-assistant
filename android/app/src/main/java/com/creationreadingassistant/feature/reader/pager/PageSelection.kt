package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.PageHitTest

/**
 * 页级命中与选区矩形：把 [PageHitTest]（段内偏移语义）适配到「一页多段」。
 *
 * [PageHitTest] 的一切偏移都是**段内**的；一页里的行来自不同段落，
 * 各段偏移都从 0 起，直接混用会互相撞。这里统一换算成**章内**偏移：
 * 章内 = Page.lineParaOffsets[行] + 段内。纯 JVM，可单测。
 */
object PageSelection {

    /** 行框高度（标题行更高）。 */
    private fun lineBoxH(page: ChapterPaginator.Page, li: Int, cfg: LayoutConfig): Float =
        if (page.lines[li].role == BlockRole.HEADING) cfg.lineHeightPx * cfg.headingScale else cfg.lineHeightPx

    /** 点 (x, y) → 章内字符偏移。y 落在行间空隙归上一行，越界钳到首末。 */
    fun offsetAt(page: ChapterPaginator.Page, cfg: LayoutConfig, x: Float, y: Float): Int {
        if (page.lines.isEmpty()) return page.startCharOffset
        var li = 0
        for (i in page.lines.indices) {
            if (y >= page.lineTops[i]) li = i else break
        }
        val line = page.lines[li]
        return page.lineParaOffsets[li] + PageHitTest.offsetInLine(line, x) -
            // offsetInLine 返回的是段内偏移；行首之前/行末之后已被它钳住
            0
    }

    /** 章内区间 [startInChapter, endInChapter) 在本页的矩形集合（页内坐标）。 */
    fun rectsForRange(
        page: ChapterPaginator.Page,
        cfg: LayoutConfig,
        startInChapter: Int,
        endInChapter: Int,
    ): List<PageHitTest.Rect> {
        if (endInChapter <= startInChapter) return emptyList()
        val out = ArrayList<PageHitTest.Rect>()
        page.lines.forEachIndexed { li, line ->
            val paraOff = page.lineParaOffsets[li]
            val lineStartCh = paraOff + line.startInText
            val lineEndCh = paraOff + line.endInText
            val s = maxOf(startInChapter, lineStartCh)
            val e = minOf(endInChapter, lineEndCh)
            if (s >= e) return@forEachIndexed
            val left = PageHitTest.xForOffset(line, s - paraOff)
            val right = PageHitTest.xForOffset(line, e - paraOff)
            if (right > left) {
                val top = page.lineTops[li]
                out.add(PageHitTest.Rect(left, top, right, top + lineBoxH(page, li, cfg)))
            }
        }
        return out
    }

    /** 中文句子边界（含闭合引号跟随）。长按选中「一句」比选中「一个词」更贴中文阅读习惯。 */
    private const val SENTENCE_ENDS = "。！？…；\n"
    private const val TRAILING = "”’」』）"

    /** 以 [offset]（章内）为中心扩到整句，返回章内区间 [start, end)。 */
    fun sentenceAround(chapterText: String, offset: Int): IntRange {
        if (chapterText.isEmpty()) return 0 until 0
        val at = offset.coerceIn(0, chapterText.length - 1)
        var s = at
        while (s > 0 && chapterText[s - 1] !in SENTENCE_ENDS) s--
        // 句首的缩进空白（TXT 常见「　　」）不属于句子，选进去既难看也污染摘录
        while (s < at && chapterText[s].isWhitespace()) s++
        var e = at
        while (e < chapterText.length && chapterText[e] !in SENTENCE_ENDS) e++
        if (e < chapterText.length) e++ // 含句末标点
        while (e < chapterText.length && chapterText[e] in TRAILING) e++
        return s until e
    }
}
