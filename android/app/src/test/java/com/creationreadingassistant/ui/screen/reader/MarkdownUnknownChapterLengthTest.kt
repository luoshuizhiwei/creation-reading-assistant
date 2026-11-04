package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F4 第（四）项：**Markdown 章节长度未知（`charCount = 0`）时的显式降级**。
 *
 * 背景（Qoder 在 F3.1 复核里留下的 B 项）：`ReaderContentHostMarkdownBranch` 取
 * `chapterLength = chapters[i].charCount ?: 0`。上游尚未算出章长度时它是 0，
 * 于是 `chapterSpan = chapterBase until chapterBase` 是**空区间**。此时两条消费
 * 路径面临同一个问题，但**答案刻意不同**：
 *
 * | 路径 | 章长度未知时 | 理由 |
 * |------|--------------|------|
 * | 持久高亮 `projectPersistentHighlights` | 跳过 span 过滤，退化为单元求交 | 高亮是**用户数据**，静默消失等于丢东西 |
 * | 搜索命中 `markdownScrollSearchHits` | 返回空列表（不高亮） | 命中是**瞬时反馈**，错高亮会误导用户 |
 *
 * 这不是遗漏，是显式裁决。本文件把两侧都钉死，并锁住它们之间的**差异**：
 * 如果有人为了让两边「看起来一致」而随意统一，测试会红。
 *
 * 另外记录降级代价：持久高亮在章长度未知时只剩单元求交一道防串章，
 * 落在别的章但与本章单元坐标重叠的高亮**可能**被投影到本章（见生产注释）。
 */
class MarkdownUnknownChapterLengthTest {

    private fun chapter(src: String) = MarkdownParser.parse(src)

    private fun saved(start: Int, length: Int, colorKey: String = "yellow") =
        MarkdownPersistentHighlights.SavedHighlight(
            bookKey = "book-1",
            start = start,
            length = length,
            colorKey = colorKey,
        )

    private fun project(
        src: String,
        highlight: MarkdownPersistentHighlights.SavedHighlight,
        chapterSpan: IntRange,
        chapterBase: Int = 0,
    ): Map<String, List<MarkdownPersistentHighlights.HighlightSpan>> {
        val ch = chapter(src)
        return MarkdownPersistentHighlights.projectPersistentHighlights(
            units = MarkdownRenderModel.flatten(ch),
            canonicalText = ch.canonicalText,
            highlights = listOf(highlight),
            currentBookKey = "book-1",
            chapterSpan = chapterSpan,
            blockGlobalBase = chapterBase,
        )
    }

    private fun searchHits(
        src: String,
        abs: IntRange,
        chapterLength: Int,
        chapterBase: Int = 0,
    ): List<MarkdownScrollHit> {
        val ch = chapter(src)
        return markdownScrollSearchHits(
            chapter = ch,
            chapterBase = chapterBase,
            chapterLength = chapterLength,
            blockGlobalBase = chapterBase,
            target = SearchHitTarget(
                bookKey = "book-1",
                chapterIndex = 0,
                absoluteRange = abs,
                resultIndex = 0,
            ),
            currentBookKey = "book-1",
            currentChapterIndex = 0,
        )
    }

    // ── 1. 持久高亮：未知章长度必须保留高亮（不静默丢数据） ────────────

    @Test
    fun `an unknown chapter length keeps persistent highlights instead of dropping them`() {
        // charCount = 0 ⇒ chapterSpan 为空 ⇒ 不得按 span 过滤（否则整章高亮全消失）。
        val spans = project(
            src = "甲乙丙丁",
            highlight = saved(start = 1, length = 2),
            chapterSpan = 0 until 0,
        )
        assertTrue(
            "章长度未知时不得吞掉用户的高亮（显式降级为单元求交）",
            spans.values.any { it.isNotEmpty() },
        )
    }

    @Test
    fun `an unknown chapter length still projects a highlight that fits a unit`() {
        val src = "甲乙丙丁"
        val spans = project(
            src = src,
            highlight = saved(start = 0, length = 2),
            chapterSpan = 0 until 0,
        )
        val hit = spans.values.flatten().singleOrNull()
        assertTrue("未知章长度下落在单元内的高亮仍须投影", hit != null)
        assertEquals(0, hit!!.localRange.first)
        assertEquals(2, hit.localRange.last + 1)
    }

    @Test
    fun `the degradation is bounded by unit intersection not by wishful matching`() {
        // 降级不等于「全部照画」：仍然必须落在某个渲染单元里。
        val spans = project(
            src = "甲乙丙丁",
            highlight = saved(start = 500, length = 3), // 远在文本之外
            chapterSpan = 0 until 0,
        )
        assertTrue(
            "降级只是跳过章级过滤，单元求交必须仍在",
            spans.values.all { it.isEmpty() },
        )
    }

    @Test
    fun `a known chapter length restores the chapter span filter`() {
        // 对照组：章长度已知时，落在章外的高亮必须被过滤（证明降级是条件性的）。
        val spans = project(
            src = "甲乙丙丁戊己",
            highlight = saved(start = 4, length = 2), // 在单元内
            chapterSpan = 0 until 3, // 但本章 span 只到 3
        )
        assertTrue(
            "章长度已知时章级过滤必须生效",
            spans.values.all { it.isEmpty() },
        )
    }

    // ── 2. 搜索命中：未知章长度必须不高亮（宁可不高亮也不错高亮） ──────

    @Test
    fun `an unknown chapter length suppresses search hit highlighting`() {
        // 与持久高亮**相反**：搜索命中是瞬时反馈，章长度未知时 chapterBase / 单元
        // 全局坐标都不可信，画出来的高亮会把用户指向错误位置。
        val hits = searchHits(
            src = "甲乙丙丁",
            abs = 1 until 3,
            chapterLength = 0,
        )
        assertTrue(
            "章长度未知时搜索命中必须不高亮（与持久高亮的降级刻意不同）",
            hits.isEmpty(),
        )
    }

    @Test
    fun `a negative chapter length suppresses search hit highlighting too`() {
        // 防御：chapterLength 由 `?: 0` 兜底，但调用方若直接传负值也必须是空，
        // 不能因为 `<= 0` 被改成 `== 0` 而漏掉。
        assertTrue(
            searchHits(src = "甲乙丙丁", abs = 1 until 3, chapterLength = -1).isEmpty(),
        )
    }

    @Test
    fun `a known chapter length highlights the search hit normally`() {
        // 对照组：章长度已知时命中照常高亮（证明上一条是条件性降级）。
        val hits = searchHits(
            src = "甲乙丙丁",
            abs = 1 until 3,
            chapterLength = 4,
        )
        assertTrue("章长度已知时命中必须高亮", hits.isNotEmpty())
        assertEquals(1, hits.single().localRange.first)
        assertEquals(3, hits.single().localRange.last + 1)
    }

    // ── 3. 两条路径的差异本身是契约 ──────────────────────────────────

    @Test
    fun `the two paths degrade differently on purpose`() {
        // 反向钉子：如果有人把两边统一（例如让搜索命中也退化成单元求交，
        // 或让持久高亮也返回空），这条会红。差异是有记录的裁决，不是 bug。
        val src = "甲乙丙丁"
        val persistent = project(
            src = src,
            highlight = saved(start = 1, length = 2),
            chapterSpan = 0 until 0,
        ).values.any { it.isNotEmpty() }
        val search = searchHits(src = src, abs = 1 until 3, chapterLength = 0).isNotEmpty()

        assertTrue("持久高亮在未知章长度时必须保留", persistent)
        assertTrue("搜索命中在未知章长度时必须不高亮", !search)
        assertTrue("两条路径的降级必须不同（显式裁决）", persistent != search)
    }
}
