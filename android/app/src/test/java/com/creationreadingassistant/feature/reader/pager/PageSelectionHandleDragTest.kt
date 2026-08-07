package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.AnywhereBreakOracle
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.FakeTextRuler
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.pager.TxtPageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选区把手拖拽微调的纯逻辑护甲（2026-08-07 新增功能）。
 *
 * 把手拖拽的「落库前区间正确性」全部收敛到 [PageSelection.adjustHandle] 这个纯函数，
 * 这里直接验证钳制规则：把手不能超出当前页边界、不能越过对侧把手、拖拽后的
 * 区间能映射出正确的保存文本（text.substring(first, last+1)）。
 * 高亮落库走既有 onSelect → SetSelectedText → SaveHighlight 路径，本测试锁定
 * 被保存区间本身的数学正确性。
 *
 * 期望值一律从 [PageSelection.rectsForRange] / [PageSelection.offsetAt] 现算，
 * 不写死排版像素，避免与具体布局指标耦合。
 */
class PageSelectionHandleDragTest {

    private val em = 100f
    private val ruler = FakeTextRuler(fontSizePx = em)
    private val oracle = AnywhereBreakOracle()
    private val cfg = LayoutConfig(
        contentWidthPx = 20 * em,
        contentHeightPx = 17 * em,
        fontSizePx = em,
    )
    private val eps = 0.01f

    /** 两段各一行的页：甲甲甲(0..2) / 乙乙乙(6..8)，段偏移 0 与 6。 */
    private fun twoParaPage(): Pair<ChapterPaginator.Page, String> {
        val text = "甲甲甲\n　　乙乙乙"
        val l = TxtPageSource.layoutChapter(text, DocChapter(0, "x", 0, text.length), cfg, ruler, oracle)
        assertEquals(1, l.pages.size)
        return l.pages[0] to text
    }

    @Test
    fun `calculateSelectionHandles returns left and right dots`() {
        val (page, _) = twoParaPage()
        val sel = 1..8
        val handles = PageSelection.calculateSelectionHandles(page, cfg, sel)
        assertTrue(handles.isActive)
        assertNotNull(handles.left)
        assertNotNull(handles.right)
        // 左点取首行选中首字符矩形左侧、右点取末行选中末字符矩形右侧，竖直居中于各行框。
        // 期望值直接从 rectsForRange 现算，避免依赖具体排版像素。
        val rects = PageSelection.rectsForRange(page, cfg, sel.first, sel.last + 1)
        assertTrue(rects.isNotEmpty())
        val firstRect = rects.first()
        val lastRect = rects.last()
        assertEquals(firstRect.left, handles.left!!.x, eps)
        assertEquals((firstRect.top + firstRect.bottom) / 2f, handles.left!!.y, eps)
        assertEquals(lastRect.right, handles.right!!.x, eps)
        assertEquals((lastRect.top + lastRect.bottom) / 2f, handles.right!!.y, eps)
        // 左把手必须在右把手左边，且纵向落在页内
        assertTrue(handles.left!!.x < handles.right!!.x)
        assertTrue(handles.left!!.y >= page.lineTops.first() - eps)
        assertTrue(handles.right!!.y <= page.lineTops.last() + 1000f)
    }

    @Test
    fun `empty selection yields no handles`() {
        val (page, _) = twoParaPage()
        assertFalse(PageSelection.calculateSelectionHandles(page, cfg, IntRange.EMPTY).isActive)
    }

    @Test
    fun `drag left handle inward past right clamps to right no inversion`() {
        val (page, _) = twoParaPage()
        val sel = 0..8
        // 把左把手拖到末字符矩形右侧（章内 ≥ 8），应被钳到原右边界 8（不越过、不反转）
        val lastRect = PageSelection.rectsForRange(page, cfg, 8, 9).first()
        val after = PageSelection.adjustHandle(
            page, cfg, sel, PageSelection.HandleSide.LEFT,
            lastRect.right + 50f, (lastRect.top + lastRect.bottom) / 2f,
        )
        assertEquals(8, after.first)
        assertEquals(8, after.last)
        assertTrue(after.first <= after.last)
        assertTrue(after.first in page.startCharOffset..page.endCharOffset)
    }

    @Test
    fun `drag left handle to page start clamps to page start`() {
        val (page, _) = twoParaPage()
        val sel = 2..5
        val after = PageSelection.adjustHandle(page, cfg, sel, PageSelection.HandleSide.LEFT, -100f, -100f)
        assertEquals(page.startCharOffset, after.first)
        assertEquals(5, after.last)
    }

    @Test
    fun `drag right handle inward past left clamps to left no inversion`() {
        val (page, _) = twoParaPage()
        val sel = 0..8
        // 把右把手拖到首字符矩形左侧（章内 ≤ 0），应被钳到原左边界 0
        val firstRect = PageSelection.rectsForRange(page, cfg, 0, 1).first()
        val after = PageSelection.adjustHandle(
            page, cfg, sel, PageSelection.HandleSide.RIGHT,
            firstRect.left - 50f, (firstRect.top + firstRect.bottom) / 2f,
        )
        assertEquals(0, after.first)
        assertEquals(0, after.last)
        assertTrue(after.first <= after.last)
    }

    @Test
    fun `drag right handle to page end clamps to page end`() {
        val (page, _) = twoParaPage()
        val sel = 1..3
        val after = PageSelection.adjustHandle(page, cfg, sel, PageSelection.HandleSide.RIGHT, 99999f, 99999f)
        assertEquals(1, after.first)
        assertEquals(page.endCharOffset, after.last)
    }

    @Test
    fun `precise drag maps pointer to correct clamped boundary and saved substring`() {
        // 验证「拖到某字符」后落库区间 = offsetAt 经钳制后的结果，且保存文本自洽。
        val text = "他说：“好。”然后走了。"
        val l = TxtPageSource.layoutChapter(text, DocChapter(0, "x", 0, text.length), cfg, ruler, oracle)
        val page = l.pages[0]
        val sentence = PageSelection.sentenceAround(text, text.indexOf('好')) // 「他说：“好。”」
        assertTrue(!sentence.isEmpty())

        val targetChar = text.indexOf('好') + 1 // ‘好’ 之后
        val rect = PageSelection.rectsForRange(page, cfg, targetChar, targetChar + 1).first()
        val pointerX = (rect.left + rect.right) / 2f
        val pointerY = (rect.top + rect.bottom) / 2f

        val after = PageSelection.adjustHandle(
            page, cfg, sentence, PageSelection.HandleSide.LEFT, pointerX, pointerY,
        )
        // 左边界 = 指针落点经 offsetAt 换算后钳到 [页首, 原句尾]，不越过原句尾、不反转
        val raw = PageSelection.offsetAt(page, cfg, pointerX, pointerY)
        val expectedLeft = raw.coerceIn(page.startCharOffset, sentence.last)
        assertEquals(expectedLeft, after.first)
        assertEquals(sentence.last, after.last)
        assertTrue(after.first <= after.last)
        // 保存文本 = chapterText.substring(first, last+1)，长度自洽且落在正文内
        val saved = text.substring(after.first, after.last + 1)
        assertEquals(after.last - after.first + 1, saved.length)
        assertTrue(after.first in 0 until text.length)
        assertTrue(after.last in 0 until text.length)
    }
}
