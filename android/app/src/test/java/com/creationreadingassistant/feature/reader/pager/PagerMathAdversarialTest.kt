package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.AnywhereBreakOracle
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.FakeTextRuler
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pager 纯逻辑层的对抗性复核回归护甲（2026-07-27 第二轮独立复核）。
 *
 * 分工：
 * - 绿色测试锁定**已核实为正确**的边界行为，防止后续修复把它们改坏；
 * - `@Ignore` 测试锁定**已确认缺陷**的期望行为，修复实现后移除注解即可转正。
 *   缺陷编号与推演过程见 docs/code-review/pager-math-review.md。
 *
 * 攻击清单来自项目教训（上一轮自测 24 全绿、独立复核仍确认 12 处缺陷）：
 * 空章、单字符章、章首章尾偏移、代理对、CRLF、超长段落、偏移换算 off-by-one。
 *
 * 编码纪律：不可见字符（BOM/NBSP/行分隔符）与增补平面字符（emoji、扩展汉字）
 * 一律用码点在运行时构造，不写进源码字面量 —— 本项目在中文 Windows 上吃过
 * 编码静默劣化的亏，语义敏感字符不能依赖肉眼不可见的字面量。
 */

private val BOM = 0xFEFF.toChar()
private val NBSP = 0x00A0.toChar()
private val LINE_SEP = 0x2028.toChar()
private val YOSHI = String(Character.toChars(0x20BB7))      // 𠮷（扩展 B 区汉字，1 簇 2 code unit）
private val THUMBS = String(Character.toChars(0x1F44D))     // 👍
private val MAHJONG = String(Character.toChars(0x1F004))    // 🀄
private val ZWJ = 0x200D.toChar()
private val ZWJ_FAMILY = String(Character.toChars(0x1F468)) + ZWJ +
    String(Character.toChars(0x1F469)) + ZWJ + String(Character.toChars(0x1F467)) // 一家三口，8 code unit

// ── PageStartsCodec ────────────────────────────────────────────────────────

class PageStartsCodecAdversarialTest {

    @Test
    fun `negative and extreme values round trip`() {
        val starts = intArrayOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, 0, 0x7F, 0x80, 0xFF, 0x100)
        assertArrayEquals(starts, PageStartsCodec.decode(PageStartsCodec.encode(starts)))
    }

    @Test
    fun `locked bytes for negative values`() {
        // 磁盘契约的补码侧：-1 与 Int.MIN_VALUE 的字节形状也要锁死
        assertArrayEquals(byteArrayOf(-1, -1, -1, -1), PageStartsCodec.encode(intArrayOf(-1)))
        assertArrayEquals(byteArrayOf(0, 0, 0, -128), PageStartsCodec.encode(intArrayOf(Int.MIN_VALUE)))
        assertArrayEquals(byteArrayOf(1, 0, 0, 0, 0, 1, 0, 0), PageStartsCodec.encode(intArrayOf(1, 256)))
    }

    @Test
    fun `empty blob is a valid empty array not corruption`() {
        assertArrayEquals(IntArray(0), PageStartsCodec.decode(ByteArray(0)))
    }

    @Test
    fun `all non multiple of four lengths decode to null`() {
        for (n in intArrayOf(1, 2, 3, 5, 6, 7, 9, 1023)) {
            assertNull("长度 $n 应判为损坏", PageStartsCodec.decode(ByteArray(n)))
        }
    }
}

// ── TxtPageSource：切片与段落化 ────────────────────────────────────────────

class TxtParagraphSlicingAdversarialTest {

    /** 偏移可逆不变式：chapterText[p.charOffset + k] == p.text[k]，对一切输入成立。 */
    private fun assertReversible(text: String) {
        val paras = TxtPageSource.paragraphsOf(text, null)
        paras.forEach { p ->
            assertTrue("charOffset 越界: ${p.charOffset}", p.charOffset in 0..text.length)
            for (k in p.text.indices) {
                assertEquals(
                    "输入 ${text.take(20)}… 段内偏移 $k 不可逆",
                    p.text[k],
                    text[p.charOffset + k],
                )
            }
            assertFalse("段落文本不应含换行", p.text.contains('\n') || p.text.contains('\r'))
            assertTrue("段落文本不应为空", p.text.isNotEmpty())
        }
    }

    @Test
    fun `offset reversibility survives hostile inputs`() {
        val battery = listOf(
            "CRLF 行\r\n　　第二段\r\n",
            BOM + "带 BOM 开头\n正文",
            "混入" + LINE_SEP + "行分隔符\n下一段",
            "NBSP" + NBSP + "段\n" + NBSP + "\n尾段",
            YOSHI + "野家" + ZWJ_FAMILY + "一家\n第二段" + MAHJONG,
            "",
            "\n",
            "\r\n",
            "　",
            " ",
            "字",
            "。",
            "\n\n\n",
            "甲\n\n\n乙\n",
            "尾部空白段   \n\t\n",
            "章尾无换行的末段",
        )
        battery.forEach(::assertReversible)
    }

    @Test
    fun `crlf line endings trim the carriage return but keep offsets`() {
        val text = "第一行\r\n　　第二行\r\n"
        val paras = TxtPageSource.paragraphsOf(text, null)
        assertEquals(2, paras.size)
        assertEquals("第一行", paras[0].text)
        assertEquals(0, paras[0].charOffset)
        assertEquals("第二行", paras[1].text)
        assertEquals(7, paras[1].charOffset) // \r\n(2) + 全角缩进(2) 之后
    }

    @Test
    fun `empty and whitespace only chapters produce no paragraphs`() {
        assertTrue(TxtPageSource.paragraphsOf("", null).isEmpty())
        assertTrue(TxtPageSource.paragraphsOf("\n\n\n", null).isEmpty())
        assertTrue(TxtPageSource.paragraphsOf("　　 \r\n\t\n", null).isEmpty())
    }

    @Test
    fun `title matching survives crlf and surrounding blanks`() {
        // CRLF 行尾：\r 被尾部收缩去掉后仍应命中标题
        val p1 = TxtPageSource.paragraphsOf("第一章 测试\r\n正文", "第一章 测试")
        assertEquals(BlockRole.HEADING, p1[0].role)
        // 标题参数自带空白：与 trim 后比较
        val p2 = TxtPageSource.paragraphsOf("第一章 测试\n正文", "  第一章 测试  ")
        assertEquals(BlockRole.HEADING, p2[0].role)
        // 标题前有空行：首个非空段仍算 first
        val p3 = TxtPageSource.paragraphsOf("\n\n第一章 测试\n正文", "第一章 测试")
        assertEquals(BlockRole.HEADING, p3[0].role)
        // 首段不是标题时，后续与标题相同的段不得误判
        val p4 = TxtPageSource.paragraphsOf("开头\n第一章 测试", "第一章 测试")
        assertEquals(BlockRole.BODY, p4[0].role)
        assertEquals(BlockRole.BODY, p4[1].role)
        // 整章只有标题一行
        val p5 = TxtPageSource.paragraphsOf("第一章 测试", "第一章 测试")
        assertEquals(1, p5.size)
        assertEquals(BlockRole.HEADING, p5[0].role)
    }

    @Test
    fun `chapterTextOf clamps hostile chapter bounds without crashing`() {
        val full = "0123456789"
        fun ch(start: Int, count: Int) = DocChapter(0, "t", start, count)
        assertEquals("", TxtPageSource.chapterTextOf(full, ch(-5, 3)))
        assertEquals("", TxtPageSource.chapterTextOf(full, ch(3, 0)))
        assertEquals("", TxtPageSource.chapterTextOf(full, ch(3, -2)))
        assertEquals("", TxtPageSource.chapterTextOf(full, ch(10, 5)))
        assertEquals("", TxtPageSource.chapterTextOf(full, ch(Int.MAX_VALUE, Int.MAX_VALUE)))
        // startOffset + charCount 溢出 Int 为负时钳到 start 而不是崩
        assertEquals("", TxtPageSource.chapterTextOf(full, ch(5, Int.MAX_VALUE)))
    }
}

// ── layoutChapter 全链路契约 ───────────────────────────────────────────────

class LayoutChapterContractAdversarialTest {

    private val em = 100f
    private val ruler = FakeTextRuler(fontSizePx = em)
    private val oracle = AnywhereBreakOracle()
    private val cfg = LayoutConfig(
        contentWidthPx = 20 * em,
        contentHeightPx = 10 * em * 1.7f,
        fontSizePx = em,
    )

    private fun layout(text: String, title: String = "x") =
        TxtPageSource.layoutChapter(text, DocChapter(0, title, 0, text.length), cfg, ruler, oracle)

    @Test
    fun `single character chapter is one page covering one char`() {
        val l = layout("字")
        assertEquals(1, l.pages.size)
        assertArrayEquals(intArrayOf(0), l.pageStarts)
        assertEquals(0, l.pages[0].startCharOffset)
        assertEquals(1, l.pages[0].endCharOffset)
        assertEquals(1, l.charCount)
        assertEquals(0, l.pageIndexFor(0))
        assertEquals(0, l.pageIndexFor(99))
        assertEquals(0, l.pageIndexFor(-1))
    }

    @Test
    fun `indented chapter first page start is 2 and pageIndexFor still resolves chapter head`() {
        // 章首全角缩进被切片跳过：pageStarts[0] == 2 而不是 0。
        // 这不是缺陷，但下游不得假设 pageStarts[0] == 0 —— 这里锁死两件事。
        val l = layout("　　正文若干字。")
        assertArrayEquals(intArrayOf(2), l.pageStarts)
        assertEquals(0, l.pageIndexFor(0))
        assertEquals(0, l.pageIndexFor(1))
        assertEquals(0, l.pageIndexFor(2))
    }

    @Test
    fun `charCount is the sum of paragraph texts not the chapter length`() {
        // 已知语义分裂（见 pager-math-review.md C2）：charCount 不含缩进/换行/空行，
        // 而 pageStarts 里的偏移是含这些字符的章内偏移，可以大于 charCount。
        // PageIndexStore.load 的 expectedCharCount 必须用同一口径（Σ 段文本长），
        // 传 chapterText.length 将导致缓存永不命中。本测试把当前口径锁死。
        val text = "　　正文若干字。"
        val l = layout(text)
        assertEquals(6, l.charCount)
        assertTrue(l.charCount != text.length)
    }

    @Test
    fun `pageIndexFor on empty pageStarts returns 0`() {
        val empty = ChapterPaginator.ChapterLayout(emptyList(), IntArray(0), 0)
        assertEquals(0, empty.pageIndexFor(0))
        assertEquals(0, empty.pageIndexFor(42))
    }

    @Test
    fun `whitespace only chapter keeps pages and pageStarts the same size`() {
        val l = layout("\n\n　　\n")
        assertEquals(l.pages.size, l.pageStarts.size)
    }

    @Test
    fun `line boundaries never split surrogate pairs on hostile text`() {
        val body = (YOSHI + "野家" + ZWJ_FAMILY + "测试内容再加一些汉字凑长度。").repeat(40)
        val text = "　　" + body + "\n第二段" + MAHJONG + "结尾"
        val l = layout(text)
        assertTrue(l.pages.isNotEmpty())
        l.pages.forEach { page ->
            assertEquals(page.lines.size, page.lineParaOffsets.size)
            page.lines.forEachIndexed { li, line ->
                val startCh = page.lineParaOffsets[li] + line.startInText
                val endCh = page.lineParaOffsets[li] + line.endInText
                assertTrue(startCh in 0..text.length && endCh in startCh..text.length)
                if (startCh < text.length) {
                    assertFalse("行首劈开代理对 @$startCh", Character.isLowSurrogate(text[startCh]))
                }
                if (endCh < text.length) {
                    assertFalse("行尾劈开代理对 @$endCh", Character.isLowSurrogate(text[endCh]))
                }
            }
        }
        // pageStarts 严格递增且与 pages 对齐
        assertEquals(l.pages.size, l.pageStarts.size)
        for (i in 1 until l.pageStarts.size) {
            assertTrue(l.pageStarts[i] > l.pageStarts[i - 1])
        }
    }

    @Test
    fun `escape branch text keeps line offsets consistent`() {
        // 超长无断点串（200 个省略号）触发 LineComposer 逃生分支后，
        // startInText/endInText 仍须首尾相接、单调推进 —— 选区矩形靠这个自洽。
        val text = "…".repeat(200)
        val l = layout(text)
        assertTrue(l.pages.isNotEmpty())
        var prevEnd = -1
        l.pages.forEach { page ->
            page.lines.forEach { line ->
                assertTrue(line.startInText < line.endInText)
                if (prevEnd >= 0) assertTrue(line.startInText >= prevEnd)
                prevEnd = line.endInText
            }
        }
        assertEquals(200, l.pages.last().lines.last().endInText)
    }
}

// ── PageSelection：段内 → 章内换算与命中矩形 ───────────────────────────────

class PageSelectionAdversarialTest {

    private val em = 100f
    private val ruler = FakeTextRuler(fontSizePx = em)
    private val oracle = AnywhereBreakOracle()
    private val cfg = LayoutConfig(
        contentWidthPx = 20 * em,   // 2000px
        contentHeightPx = 17 * em,  // 1700px，恰好 10 行正文
        fontSizePx = em,
    )
    private val lineH = 1.80f * em         // 180，与 LayoutConfig 默认 lineHeightMultiplier=1.80 对齐
    private val paraGap = 0.4f * em        // 40
    private val indent = 2f * em           // 200
    private val eps = 0.01f

    /** 两段各一行的页：甲甲甲(0..2) / 乙乙乙(6..8)，段偏移 0 与 6。 */
    private fun twoParaPage(): Pair<ChapterPaginator.Page, String> {
        val text = "甲甲甲\n　　乙乙乙"
        val l = TxtPageSource.layoutChapter(text, DocChapter(0, "x", 0, text.length), cfg, ruler, oracle)
        assertEquals(1, l.pages.size)
        val page = l.pages[0]
        assertEquals(2, page.lines.size)
        assertArrayEquals(intArrayOf(0, 6), page.lineParaOffsets)
        assertArrayEquals(floatArrayOf(0f, lineH + paraGap), page.lineTops, eps)
        return page to text
    }

    @Test
    fun `offsetAt converts second paragraph hits to chapter offsets`() {
        val (page, text) = twoParaPage()
        // 第二行首字：段内 0 + 段偏移 6 = 章内 6，正是「乙」
        val hit = PageSelection.offsetAt(page, cfg, indent + 5f, lineH + paraGap + 5f)
        assertEquals(6, hit)
        assertEquals('乙', text[hit])
    }

    @Test
    fun `offsetAt clamps x and y overshoot to line bounds`() {
        val (page, text) = twoParaPage()
        val line2Y = lineH + paraGap + 5f
        // x 在行首之前 → 行首；x 远超行末 → 行末（= 段末 3 + 段偏移 6 = 9 = 章文本长）
        assertEquals(6, PageSelection.offsetAt(page, cfg, -50f, line2Y))
        assertEquals(9, PageSelection.offsetAt(page, cfg, 99999f, line2Y))
        // y 为负 → 首行；y 深超页底 → 末行（x 继续生效）
        assertEquals(0, PageSelection.offsetAt(page, cfg, indent + 5f, -100f))
        assertEquals(6, PageSelection.offsetAt(page, cfg, indent + 5f, 99999f))
        // 行末命中值可以等于 chapterText.length，喂给 sentenceAround 不得崩
        val r = PageSelection.sentenceAround(text, 9)
        assertFalse(r.isEmpty())
    }

    @Test
    fun `gap between lines belongs to the upper line`() {
        val (page, _) = twoParaPage()
        // 行 1 底(170) 与行 2 顶(210) 之间的空隙归上一行
        val hit = PageSelection.offsetAt(page, cfg, indent + 5f, 190f)
        assertEquals(0, hit)
        // 恰好等于行 2 顶 → 行 2
        assertEquals(6, PageSelection.offsetAt(page, cfg, indent + 5f, lineH + paraGap))
    }

    @Test
    fun `offsetInLine rounds to nearest cluster boundary`() {
        val (page, _) = twoParaPage()
        val line2Y = lineH + paraGap + 5f
        // 第二行 clusterX = [200, 300, 400, 500]；x=340 在簇 1 的左半 → 偏移 1 → 章内 7
        assertEquals(7, PageSelection.offsetAt(page, cfg, 340f, line2Y))
        // x=360 在簇 1 的右半 → 偏移 2 → 章内 8
        assertEquals(8, PageSelection.offsetAt(page, cfg, 360f, line2Y))
    }

    @Test
    fun `rectsForRange spans paragraphs with chapter offsets`() {
        val (page, _) = twoParaPage()
        val rects = PageSelection.rectsForRange(page, cfg, 1, 8)
        assertEquals(2, rects.size)
        // 行 1：甲[1,3) → x [300, 500)，行框 [0, 170)
        assertEquals(300f, rects[0].left, eps)
        assertEquals(500f, rects[0].right, eps)
        assertEquals(0f, rects[0].top, eps)
        assertEquals(lineH, rects[0].bottom, eps)
        // 行 2：乙[6,8) → 段内 [0,2) → x [200, 400)，行框 [210, 380)
        assertEquals(200f, rects[1].left, eps)
        assertEquals(400f, rects[1].right, eps)
        assertEquals(lineH + paraGap, rects[1].top, eps)
        assertEquals(lineH + paraGap + lineH, rects[1].bottom, eps)
    }

    @Test
    fun `range covering only stripped whitespace yields no rects`() {
        val (page, _) = twoParaPage()
        // 章内 [3,6) 是 \n 与缩进「　　」，不属于任何行 → 无矩形，而不是崩或负宽矩形
        assertTrue(PageSelection.rectsForRange(page, cfg, 3, 6).isEmpty())
    }

    @Test
    fun `single char and degenerate ranges`() {
        val (page, _) = twoParaPage()
        val one = PageSelection.rectsForRange(page, cfg, 2, 3)
        assertEquals(1, one.size)
        assertEquals(400f, one[0].left, eps)
        assertEquals(500f, one[0].right, eps)
        assertTrue(PageSelection.rectsForRange(page, cfg, 5, 5).isEmpty())
        assertTrue(PageSelection.rectsForRange(page, cfg, 8, 1).isEmpty())
    }

    @Test
    fun `full page range produces one rect per line`() {
        val (page, text) = twoParaPage()
        val rects = PageSelection.rectsForRange(page, cfg, 0, text.length)
        assertEquals(page.lines.size, rects.size)
        rects.forEachIndexed { i, r -> assertEquals(page.lineTops[i], r.top, eps) }
    }

    @Test
    fun `heading line rect uses heading height`() {
        val text = "第一章 测试\n　　正文就一行。"
        val l = TxtPageSource.layoutChapter(text, DocChapter(0, "第一章 测试", 0, text.length), cfg, ruler, oracle)
        val page = l.pages[0]
        assertEquals(BlockRole.HEADING, page.lines[0].role)
        val headingH = lineH * cfg.headingScale // 212.5
        assertArrayEquals(floatArrayOf(0f, headingH + paraGap), page.lineTops, eps)

        val titleRects = PageSelection.rectsForRange(page, cfg, 0, 6)
        assertEquals(1, titleRects.size)
        assertEquals(headingH, titleRects[0].bottom - titleRects[0].top, eps)

        val bodyRects = PageSelection.rectsForRange(page, cfg, 9, 15)
        assertEquals(1, bodyRects.size)
        assertEquals(indent, bodyRects[0].left, eps)
        assertEquals(lineH, bodyRects[0].bottom - bodyRects[0].top, eps)

        // 标题底(212.5)与正文顶(252.5)之间的空隙归标题行
        val hit = PageSelection.offsetAt(page, cfg, 10f, 230f)
        assertTrue(hit in 0..6)
    }

    @Test
    fun `empty page returns page start offset`() {
        val page = ChapterPaginator.Page(
            index = 0, startCharOffset = 5, endCharOffset = 5,
            lines = emptyList(), lineTops = FloatArray(0), lineParaOffsets = IntArray(0),
        )
        assertEquals(5, PageSelection.offsetAt(page, cfg, 100f, 100f))
        assertTrue(PageSelection.rectsForRange(page, cfg, 0, 10).isEmpty())
    }

    @Test
    fun `offsetAt never lands inside a surrogate pair`() {
        val text = YOSHI + "了。" + THUMBS + "好。"
        val l = TxtPageSource.layoutChapter(text, DocChapter(0, "x", 0, text.length), cfg, ruler, oracle)
        val page = l.pages[0]
        var x = -50f
        while (x < 1200f) {
            val off = PageSelection.offsetAt(page, cfg, x, 5f)
            assertTrue(off in 0..text.length)
            if (off < text.length) {
                assertFalse("命中落在代理对中间 @$off (x=$x)", Character.isLowSurrogate(text[off]))
            }
            x += 12.5f
        }
    }
}

// ── PageSelection.sentenceAround：中文扩句 ─────────────────────────────────

class SentenceAroundAdversarialTest {

    private fun sel(text: String, at: Int): String {
        val r = PageSelection.sentenceAround(text, at)
        return if (r.isEmpty()) "" else text.substring(r.first, r.last + 1)
    }

    @Test
    fun `plain terminators split sentences at every press position`() {
        val text = "甲。乙！丙？丁；戊"
        assertEquals("甲。", sel(text, 0))
        assertEquals("甲。", sel(text, 1)) // 按在终止符上选它结束的句子
        assertEquals("乙！", sel(text, 2))
        assertEquals("丙？", sel(text, 4))
        assertEquals("丁；", sel(text, 6))
        assertEquals("戊", sel(text, 8))
    }

    @Test
    fun `offset clamping and degenerate inputs`() {
        assertEquals("甲。", sel("甲。", 99))
        assertEquals("甲。", sel("甲。", -5))
        assertEquals("。", sel("。", 0))
        assertEquals("字", sel("字", 0))
        assertTrue(PageSelection.sentenceAround("", 3).isEmpty())
    }

    @Test
    fun `second paragraph selection starts after indent`() {
        val text = "第一段无标点\n　　第二段。"
        assertEquals("第二段。", sel(text, 10))
    }

    @Test
    fun `closing quotes follow the sentence`() {
        val t = "他说：“好。”然后走了。"
        assertEquals("他说：“好。”", sel(t, t.indexOf('好')))
        val t2 = "「他喊道：『走！』」下一句。"
        assertEquals("「他喊道：『走！』」", sel(t2, 3))
    }

    @Test
    fun `sentence bounds never split surrogate pairs`() {
        val text = YOSHI + "了。" + THUMBS + "好。"
        for (i in text.indices) {
            val r = PageSelection.sentenceAround(text, i)
            assertFalse(r.isEmpty())
            assertFalse("起点劈开代理对 @${r.first}", Character.isLowSurrogate(text[r.first]))
            val end = r.last + 1
            if (end < text.length) {
                assertFalse("终点劈开代理对 @$end", Character.isLowSurrogate(text[end]))
            }
        }
        assertEquals(YOSHI + "了。", sel(text, 0))
        assertEquals(THUMBS + "好。", sel(text, 4))
    }

    @Test
    fun `double ellipsis stays whole`() {
        val text = "他愣住了……原来如此。"
        assertEquals("他愣住了……", sel(text, 1))
        // 后一句不应以孤儿 … 开头
        assertEquals("原来如此。", sel(text, 7))
    }

    @Test
    fun `unpunctuated paragraph end excludes the line break`() {
        assertEquals("第一段无标点", sel("第一段无标点\n　　第二段。", 2))
        assertEquals("第一段", sel("第一段\r\n第二段。", 1))
    }
}
