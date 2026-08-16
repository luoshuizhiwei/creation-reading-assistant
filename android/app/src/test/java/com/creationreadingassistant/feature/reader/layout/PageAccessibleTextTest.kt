package com.creationreadingassistant.feature.reader.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分页正文的可访问性文本（P1：TalkBack 按当前页粒度读取正文）。
 *
 * [pageAccessibleText] 必须与 [com.creationreadingassistant.feature.reader.pager.PagedTxtReaderHost]
 * 里 PageCanvas 的绘制切片共用同一套 `lineParaOffsets + clusterStarts` 换算 ——
 * 「语义文本 == 视觉文本」靠 `页文本逐字符等于章文本页区间切片` 这条断言锁死。
 */
class PageAccessibleTextTest {

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
            out.add(LayoutParagraph(text = t, role = BlockRole.BODY, charOffset = offset))
            offset += t.length
        }
        return out
    }

    /** 构造「段落文本 == 章文本」的最简章节（TXT 源的真实形状）。 */
    private fun chapter(vararg texts: String): Pair<String, ChapterPaginator.ChapterLayout> {
        val paras = paragraphs(*texts)
        val text = paras.joinToString("") { it.text }
        return text to ChapterPaginator.paginate(paras, cfg(), ruler, oracle)
    }

    @Test
    fun `short paragraphs read in draw order separated by newlines`() {
        val (text, layout) = chapter("第一段。", "第二段。", "第三段。")
        assertEquals("第一段。\n第二段。\n第三段。", pageAccessibleText(layout.pages[0], text))
    }

    @Test
    fun `wrapped paragraph loses nothing between lines`() {
        // 高 10em / 行高 1.7em ≈ 5 行/页：3 遍折 4 行，恰好一页内完成换行拼接
        val p = "这是正文内容，它会折成多行，行与行之间只换行不丢字。".repeat(3)
        val (text, layout) = chapter(p)
        val out = pageAccessibleText(layout.pages[0], text)
        assertEquals(p, out.replace("\n", ""))
    }

    @Test
    fun `each page text equals the exact chapter slice it draws`() {
        val paras = paragraphs(*Array(30) { "第${it}页测试正文内容。".repeat(12) })
        val text = paras.joinToString("") { it.text }
        val layout = ChapterPaginator.paginate(paras, cfg(), ruler, oracle)
        assertTrue("应分成多页，实际=${layout.pages.size}", layout.pages.size >= 3)
        layout.pages.forEach { page ->
            val out = pageAccessibleText(page, text)
            assertEquals("有正文行的页必须产出文本", page.lines.isEmpty(), out.isEmpty())
            if (page.lines.isNotEmpty()) {
                assertEquals(
                    "页语义文本必须逐字符等于绘制区间切片",
                    text.substring(page.startCharOffset, page.endCharOffset),
                    out.replace("\n", ""),
                )
            }
        }
    }

    @Test
    fun `adjacent pages expose different text`() {
        val paras = paragraphs(*Array(40) { "切换页测试正文。".repeat(8) })
        val text = paras.joinToString("") { it.text }
        val layout = ChapterPaginator.paginate(paras, cfg(), ruler, oracle)
        assertTrue("应分成多页，实际=${layout.pages.size}", layout.pages.size >= 2)
        val first = pageAccessibleText(layout.pages[0], text)
        val second = pageAccessibleText(layout.pages[1], text)
        assertNotEquals("相邻页语义文本不能相同", first, second)
        assertTrue("页 0 文本不能为空", first.isNotEmpty())
        assertTrue("页 1 文本不能为空", second.isNotEmpty())
    }

    @Test
    fun `text is bounded by the page not the chapter`() {
        val long = "长章节边界测试，每页只应读到本页内容。".repeat(120)
        val (text, layout) = chapter(long)
        assertTrue("应分成多页，实际=${layout.pages.size}", layout.pages.size >= 2)
        val first = pageAccessibleText(layout.pages[0], text)
        assertTrue("页文本应短于整章", first.length < text.length)
        assertFalse("页文本不能包含章尾内容", first.contains(long.takeLast(8)))
    }

    @Test
    fun `empty page yields empty text`() {
        val layout = ChapterPaginator.paginate(emptyList(), cfg(), ruler, oracle)
        assertEquals(1, layout.pages.size)
        assertEquals("", pageAccessibleText(layout.pages[0], ""))
    }

    @Test
    fun `horizontal rule contributes no text`() {
        val hr = LayoutParagraph(
            text = "---",
            role = BlockRole.HORIZONTAL_RULE,
            charOffset = 0,
        )
        val body = LayoutParagraph(
            text = "规则前后正文。",
            role = BlockRole.BODY,
            charOffset = 3,
        )
        val text = "---规则前后正文。"
        val layout = ChapterPaginator.paginate(listOf(hr, body), cfg(), ruler, oracle)
        val out = pageAccessibleText(layout.pages[0], text)
        assertFalse("分隔线不能进入语义文本", out.contains("-"))
        assertEquals("规则前后正文。", out)
    }

    @Test
    fun `list marker appears once at paragraph start not per wrapped line`() {
        val item = LayoutParagraph(
            text = "列表项内容需要换行才能继续展示。".repeat(4),
            role = BlockRole.LIST_ITEM_BULLET,
            charOffset = 0,
            listMarker = "• ",
            indentLevel = 1,
        )
        val layout = ChapterPaginator.paginate(listOf(item), cfg(), ruler, oracle)
        val out = pageAccessibleText(layout.pages[0], item.text)
        assertTrue("应以列表标记开头", out.startsWith("• "))
        assertEquals("列表标记只出现一次", 1, out.windowed(2).count { it == "• " })
        assertEquals("• " + item.text, out.replace("\n", ""))
    }

    @Test
    fun `cluster slicing keeps surrogate pairs intact`() {
        val p = "开篇𠮷与👨\u200D👩\u200D👧家族。"
        val (text, layout) = chapter(p)
        assertEquals(p, pageAccessibleText(layout.pages[0], text))
    }
}
