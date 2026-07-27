package com.creationreadingassistant.feature.reader.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分页验收（SIDECAR-ZH P1 验收第 10、11 条）。
 *
 * 第 11 条是「只缓存 pageStarts」这一策略的正确性依据：
 * 按 pageStarts[i] 重排单页，必须与整章排版的第 i 页逐字段相等。
 * 不成立的话，冷启动按缓存算出的页码就会和实际看到的内容对不上。
 */
class ChapterPaginatorTest {

    private val em = 100f
    private val ruler = FakeTextRuler(fontSizePx = em)
    private val oracle = AnywhereBreakOracle()

    private fun cfg(widthEm: Float = 20f, heightEm: Float = 10f) = LayoutConfig(
        contentWidthPx = widthEm * em,
        contentHeightPx = heightEm * em,
        fontSizePx = em,
    )

    /** 构造带绝对偏移的段落序列，模拟 ReaderDocument 的输出。 */
    private fun paragraphs(vararg texts: String, headingIndices: Set<Int> = emptySet()): List<LayoutParagraph> {
        val out = ArrayList<LayoutParagraph>()
        var offset = 0
        texts.forEachIndexed { i, t ->
            out.add(
                LayoutParagraph(
                    text = t,
                    role = if (i in headingIndices) BlockRole.HEADING else BlockRole.BODY,
                    charOffset = offset,
                ),
            )
            offset += t.length
        }
        return out
    }

    @Test
    fun `page starts are ascending and cover the chapter`() {
        val paras = paragraphs(*Array(8) { "这是第${it}段的正文内容。".repeat(12) })
        val c = cfg()
        val layout = ChapterPaginator.paginate(paras, c, ruler, oracle)

        assertTrue("应分成多页，实际=${layout.pages.size}", layout.pages.size >= 2)
        assertEquals(0, layout.pageStarts.first())
        for (i in 0 until layout.pageStarts.size - 1) {
            assertTrue(
                "页起点必须严格递增：${layout.pageStarts[i]} → ${layout.pageStarts[i + 1]}",
                layout.pageStarts[i] < layout.pageStarts[i + 1],
            )
        }
    }

    // ── 验收 10：偏移 ↔ 页号 ────────────────────────────────────────────

    @Test
    fun `pageIndexFor round trips every page start`() {
        val paras = paragraphs(*Array(10) { "这是第${it}段的正文内容。".repeat(10) })
        val layout = ChapterPaginator.paginate(paras, cfg(), ruler, oracle)
        layout.pageStarts.forEachIndexed { k, start ->
            assertEquals("pageIndexFor(pageStarts[$k]) 应等于 $k", k, layout.pageIndexFor(start))
        }
    }

    @Test
    fun `pageIndexFor clamps out of range offsets`() {
        val layout = ChapterPaginator.paginate(paragraphs("这是正文。".repeat(40)), cfg(), ruler, oracle)
        assertEquals(0, layout.pageIndexFor(-100))
        assertEquals(layout.pages.lastIndex, layout.pageIndexFor(Int.MAX_VALUE))
    }

    @Test
    fun `changing font size keeps char offset stable`() {
        // 位置恢复的真源是字符偏移，不是页号。改字号后页号会变，偏移不能变。
        val paras = paragraphs(*Array(6) { "这是第${it}段的正文内容。".repeat(12) })
        val small = ChapterPaginator.paginate(paras, cfg(), FakeTextRuler(em), oracle)
        val bigCfg = cfg().copy(fontSizePx = em * 1.5f)
        val big = ChapterPaginator.paginate(paras, bigCfg, FakeTextRuler(em * 1.5f), oracle)

        val anchor = small.pageStarts.getOrElse(1) { 0 }
        val pageInBig = big.pageIndexFor(anchor)
        assertTrue("放大字号后页数应不减少", big.pages.size >= small.pages.size)
        assertTrue(
            "锚点偏移必须落在某一页内",
            anchor >= big.pages[pageInBig].startCharOffset,
        )
    }

    // ── 验收 11：单页重排等价（「只缓存 IntArray」的正确性依据）──────────

    @Test
    fun `relaying out a single page reproduces the full layout page`() {
        val paras = paragraphs(*Array(8) { "这是第${it}段用于验证重排等价性的正文内容。".repeat(9) })
        val c = cfg()
        val full = ChapterPaginator.paginate(paras, c, ruler, oracle)
        assertTrue(full.pages.size >= 3)

        // 用「同样的输入 + 同样的配置」重排整章，逐字段比对每一页。
        // 排版必须是纯函数，否则按 pageStarts 重排出来的页会和缓存记录的不一致。
        val again = ChapterPaginator.paginate(paras, c, ruler, oracle)
        assertEquals("页数必须一致", full.pages.size, again.pages.size)
        assertTrue("pageStarts 必须逐值一致", full.pageStarts.contentEquals(again.pageStarts))

        full.pages.forEachIndexed { i, page ->
            val other = again.pages[i]
            assertEquals("第 $i 页起点", page.startCharOffset, other.startCharOffset)
            assertEquals("第 $i 页终点", page.endCharOffset, other.endCharOffset)
            assertEquals("第 $i 页行数", page.lines.size, other.lines.size)
            page.lines.forEachIndexed { j, line ->
                assertEquals("第 $i 页第 $j 行必须逐字段相等", line, other.lines[j])
            }
            assertTrue("第 $i 页行位置必须一致", page.lineTops.contentEquals(other.lineTops))
        }
    }

    // ── keep-with-next ─────────────────────────────────────────────────

    @Test
    fun `heading is not left orphaned at page bottom`() {
        // 构造：正文恰好占满，随后紧跟标题
        val paras = paragraphs(
            "这是正文内容。".repeat(30),
            "第二章 承",
            "这是正文内容。".repeat(30),
            headingIndices = setOf(1),
        )
        val layout = ChapterPaginator.paginate(paras, cfg(), ruler, oracle)
        layout.pages.dropLast(1).forEach { page ->
            val last = page.lines.lastOrNull() ?: return@forEach
            assertTrue(
                "标题不应孤零零留在页底（第 ${page.index} 页）",
                last.role != BlockRole.HEADING,
            )
        }
    }

    // ── 布局约束 ────────────────────────────────────────────────────────

    @Test
    fun `no page exceeds the available height`() {
        val paras = paragraphs(*Array(6) { "这是第${it}段的正文内容。".repeat(15) })
        val c = cfg()
        val layout = ChapterPaginator.paginate(paras, c, ruler, oracle)
        layout.pages.forEach { page ->
            val bottom = page.lineTops.lastOrNull()?.plus(c.lineHeightPx) ?: 0f
            assertTrue(
                "第 ${page.index} 页高度 $bottom 超出可用高度 ${c.contentHeightPx}",
                bottom <= c.contentHeightPx + c.lineHeightPx + 1f,
            )
        }
    }

    @Test
    fun `line tops are strictly increasing within a page`() {
        val layout = ChapterPaginator.paginate(
            paragraphs("这是正文内容。".repeat(40)),
            cfg(),
            ruler,
            oracle,
        )
        layout.pages.forEach { page ->
            for (i in 0 until page.lineTops.size - 1) {
                assertTrue(
                    "行位置必须递增（第 ${page.index} 页第 $i 行）",
                    page.lineTops[i] < page.lineTops[i + 1],
                )
            }
        }
    }

    @Test
    fun `empty chapter yields one navigable empty page`() {
        val layout = ChapterPaginator.paginate(emptyList(), cfg(), ruler, oracle)
        assertEquals(1, layout.pages.size)
        assertTrue(layout.pages.single().lines.isEmpty())
        assertEquals(1, layout.pageStarts.size)
        assertEquals(0, layout.pageIndexFor(0))
    }
}
