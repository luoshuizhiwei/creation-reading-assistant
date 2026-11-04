package com.creationreadingassistant.feature.reader.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式即时排版与内存削减（选项 D）核心单测。
 * 验证渐进式交付的正确性、阶段性回调触发以及与标准全量排版的逐字段纯函数等价性。
 */
class ChapterPaginatorStreamingTest {

    private val em = 100f
    private val ruler = FakeTextRuler(fontSizePx = em)
    private val oracle = AnywhereBreakOracle()

    private fun cfg(widthEm: Float = 20f, heightEm: Float = 10f) = LayoutConfig(
        contentWidthPx = widthEm * em,
        contentHeightPx = heightEm * em,
        fontSizePx = em,
    )

    private fun paragraphs(vararg texts: String): List<LayoutParagraph> {
        val out = ArrayList<LayoutParagraph>()
        var offset = 0
        texts.forEach { t ->
            out.add(
                LayoutParagraph(
                    text = t,
                    role = BlockRole.BODY,
                    charOffset = offset,
                ),
            )
            offset += t.length
        }
        return out
    }

    @Test
    fun testProgressivePaginationProducesIdenticalLayout() {
        val paras = paragraphs(*Array(12) { "这是第" + it + "段用于测试流式排版等价性的长正文。".repeat(10) })
        val config = cfg()

        var interimLayout: ChapterPaginator.ChapterLayout? = null
        val progressive = ChapterPaginator.paginateProgressiveText(
            paragraphs = paras,
            cfg = config,
            ruler = ruler,
            oracle = oracle,
            targetOffset = 0,
            initialPageThreshold = 2,
            onInitialPages = { interimLayout = it },
        )

        val standard = ChapterPaginator.paginate(paras, config, ruler, oracle)

        assertEquals("总页数必须与全量排版完全一致", standard.pages.size, progressive.pages.size)
        assertTrue("pageStarts 必须逐值一致", standard.pageStarts.contentEquals(progressive.pageStarts))
        assertEquals("总字数必须一致", standard.charCount, progressive.charCount)

        progressive.pages.forEachIndexed { i, page ->
            val stdPage = standard.pages[i]
            assertEquals("第 $i 页 startCharOffset 必须一致", stdPage.startCharOffset, page.startCharOffset)
            assertEquals("第 $i 页 endCharOffset 必须一致", stdPage.endCharOffset, page.endCharOffset)
            assertEquals("第 $i 页行数必须一致", stdPage.lines.size, page.lines.size)
            assertTrue("第 $i 页 lineTops 必须完全相等", stdPage.lineTops.contentEquals(page.lineTops))
            assertTrue("第 $i 页 lineParaOffsets 必须完全相等", stdPage.lineParaOffsets.contentEquals(page.lineParaOffsets))
        }

        // 验证阶段性回调产出
        assertNotNull("长章节应成功触发首屏阶段性交付回调", interimLayout)
        assertEquals(2, interimLayout!!.pages.size)
        assertEquals(progressive.pages[0].startCharOffset, interimLayout!!.pages[0].startCharOffset)
        assertEquals(progressive.pages[1].endCharOffset, interimLayout!!.pages[1].endCharOffset)
    }

    @Test
    fun testProgressivePaginationSatisfiesTargetOffset() {
        val paras = paragraphs(*Array(20) { "段落" + it + "：" + "正文内容。".repeat(25) })
        val config = cfg()
        val standard = ChapterPaginator.paginate(paras, config, ruler, oracle)
        assertTrue("章节至少需要 5 页以上", standard.pages.size >= 5)

        val targetPage = 3
        val targetOffset = standard.pageStarts[targetPage] + 10

        var interimLayout: ChapterPaginator.ChapterLayout? = null
        val progressive = ChapterPaginator.paginateProgressiveText(
            paragraphs = paras,
            cfg = config,
            ruler = ruler,
            oracle = oracle,
            targetOffset = targetOffset,
            initialPageThreshold = 2,
            onInitialPages = { interimLayout = it },
        )

        assertNotNull("应触发包含目标偏移的阶段性回调", interimLayout)
        val interim = interimLayout!!
        assertTrue("阶段性排版产出的页面必须包含目标偏移所在的页", interim.pages.size > targetPage)
        assertEquals("阶段性排版中 pageIndexFor 应精准命中目标页", targetPage, interim.pageIndexFor(targetOffset))
        assertEquals("最终结果与标准排版一致", standard.pages.size, progressive.pages.size)
    }

    @Test
    fun testShortSinglePageChapterCompletesWithoutPrematureCallback() {
        val paras = paragraphs("这是一个非常短的单页章节。")
        val config = cfg()

        var callbackTriggered = false
        val layout = ChapterPaginator.paginateProgressiveText(
            paragraphs = paras,
            cfg = config,
            ruler = ruler,
            oracle = oracle,
            targetOffset = 0,
            initialPageThreshold = 2,
            onInitialPages = { callbackTriggered = true },
        )

        assertEquals(1, layout.pages.size)
        assertTrue("单页章节不应多余触发未完成回调", !callbackTriggered)
    }

    @Test
    fun testEmptyParagraphsListYieldsSingleEmptyPageSafely() {
        var callbackTriggered = false
        val layout = ChapterPaginator.paginateProgressiveText(
            paragraphs = emptyList(),
            cfg = cfg(),
            ruler = ruler,
            oracle = oracle,
            targetOffset = 0,
            initialPageThreshold = 2,
            onInitialPages = { callbackTriggered = true },
        )

        assertEquals(1, layout.pages.size)
        assertTrue(layout.pages.first().lines.isEmpty())
        assertTrue(!callbackTriggered)
    }
}
