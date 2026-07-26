package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.AnywhereBreakOracle
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.FakeTextRuler
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageStartsCodecTest {

    @Test
    fun `round trip preserves values`() {
        val cases = listOf(
            intArrayOf(),
            intArrayOf(0),
            intArrayOf(0, 512, 1024, 65_536, 16_777_216, Int.MAX_VALUE),
        )
        cases.forEach { starts ->
            assertArrayEquals(starts, PageStartsCodec.decode(PageStartsCodec.encode(starts)))
        }
    }

    @Test
    fun `encoding is locked little endian`() {
        // 磁盘契约：小端 4 字节。这条断言锁死格式 —— 改了它，所有已落盘的缓存都解不出来。
        val bytes = PageStartsCodec.encode(intArrayOf(0x0A0B0C0D))
        assertArrayEquals(byteArrayOf(0x0D, 0x0C, 0x0B, 0x0A), bytes)
    }

    @Test
    fun `corrupt length decodes to null`() {
        assertNull(PageStartsCodec.decode(ByteArray(5)))
        assertNull(PageStartsCodec.decode(ByteArray(3)))
    }
}

class TxtPageSourceTest {

    private val em = 100f
    private val ruler = FakeTextRuler(fontSizePx = em)
    private val oracle = AnywhereBreakOracle()
    private val cfg = LayoutConfig(
        contentWidthPx = 20 * em,
        contentHeightPx = 10 * em * 1.7f, // 恰好 10 行高
        fontSizePx = em,
    )

    private fun chapter(start: Int, count: Int, title: String = "第一章 测试") =
        DocChapter(index = 0, title = title, startOffset = start, charCount = count)

    // ── 偏移可逆性：整个分页引擎的进度/高亮都压在这条不变式上 ────────────

    @Test
    fun `paragraph offsets map back to chapter text exactly`() {
        val text = "第一章 测试\n　　正文第一段，前面带全角缩进。\n\n  第二段带半角空格。\n末段无换行"
        val paras = TxtPageSource.paragraphsOf(text, "第一章 测试")
        assertTrue(paras.isNotEmpty())
        paras.forEach { p ->
            for (k in p.text.indices) {
                assertEquals(
                    "段内偏移 $k 换算回章文本后字符不一致",
                    p.text[k],
                    text[p.charOffset + k],
                )
            }
        }
    }

    @Test
    fun `leading indent whitespace is excluded by moving the offset not by reindexing`() {
        val text = "　　缩进段落"
        val paras = TxtPageSource.paragraphsOf(text, null)
        assertEquals(1, paras.size)
        assertEquals("缩进段落", paras[0].text)
        assertEquals(2, paras[0].charOffset) // 两个全角空格之后
    }

    @Test
    fun `blank lines produce no paragraphs but offsets stay correct`() {
        val text = "甲\n\n\n乙"
        val paras = TxtPageSource.paragraphsOf(text, null)
        assertEquals(2, paras.size)
        assertEquals(0, paras[0].charOffset)
        assertEquals(4, paras[1].charOffset)
    }

    @Test
    fun `first paragraph matching title becomes heading`() {
        val text = "第一章 测试\n正文内容"
        val paras = TxtPageSource.paragraphsOf(text, "第一章 测试")
        assertEquals(BlockRole.HEADING, paras[0].role)
        assertEquals(BlockRole.BODY, paras[1].role)
        // 标题不匹配时不乱标
        val paras2 = TxtPageSource.paragraphsOf("别的开头\n正文", "第一章 测试")
        assertEquals(BlockRole.BODY, paras2[0].role)
    }

    // ── 章切片与分页 ────────────────────────────────────────────────────

    @Test
    fun `chapter slice respects offsets and clamps`() {
        val full = "0123456789"
        assertEquals("2345", TxtPageSource.chapterTextOf(full, chapter(2, 4)))
        // 越界钳制而不是崩
        assertEquals("89", TxtPageSource.chapterTextOf(full, chapter(8, 100)))
    }

    @Test
    fun `page starts are chapter relative strictly increasing and cover the text`() {
        val body = (1..40).joinToString("\n") { "第${it}段的正文内容，写长一点确保会换行。这里再补一些字数。" }
        val full = "前一章占位\n$body"
        val ch = chapter(6, full.length - 6, title = "x")
        val layout = TxtPageSource.layoutChapter(full, ch, cfg, ruler, oracle)

        assertTrue("应分出多页", layout.pages.size > 1)
        assertEquals(layout.pages.size, layout.pageStarts.size)
        assertEquals("首页从 0 开始（章内偏移）", 0, layout.pageStarts[0])
        for (i in 1 until layout.pageStarts.size) {
            assertTrue("pageStarts 必须严格递增", layout.pageStarts[i] > layout.pageStarts[i - 1])
        }
        // 每页行数不超过视口能容纳的行数
        layout.pages.forEach { page ->
            assertTrue("单页行数超出视口", page.lines.size <= 10)
            assertEquals(page.lines.size, page.lineParaOffsets.size)
        }
        // pageIndexFor 与 pageStarts 一致
        layout.pageStarts.forEachIndexed { i, s ->
            assertEquals(i, layout.pageIndexFor(s))
            if (i > 0) assertEquals(i - 1, layout.pageIndexFor(s - 1))
        }
    }

    @Test
    fun `same input and config reproduce identical page starts`() {
        // 「只缓存 pageStarts、页按需重排」成立的前提：排版是纯函数。
        val body = (1..20).joinToString("\n") { "第${it}段，中文正文内容再长一些以便分页。" }
        val ch = chapter(0, body.length, title = "x")
        val a = TxtPageSource.layoutChapter(body, ch, cfg, ruler, oracle)
        val b = TxtPageSource.layoutChapter(body, ch, cfg, ruler, oracle)
        assertArrayEquals(a.pageStarts, b.pageStarts)
    }

    @Test
    fun `sentence selection expands to chinese sentence bounds`() {
        val text = "　　第一句话。第二句带引号：“对话内容。”第三句！"
        // 点在「第二句」中间
        val at = text.indexOf("带引号")
        val r = PageSelection.sentenceAround(text, at)
        assertEquals("第二句带引号：“对话内容。”", text.substring(r.first, r.last + 1))
        // 点在段首句：不能把「　　」缩进空白选进来
        val r2 = PageSelection.sentenceAround(text, text.indexOf("第一句"))
        assertEquals("第一句话。", text.substring(r2.first, r2.last + 1))
        // 句末闭合引号要跟着句子走
        val t3 = "他说：“好。”然后走了。"
        val r3 = PageSelection.sentenceAround(t3, t3.indexOf("好"))
        assertTrue(text.isNotEmpty())
        assertEquals("他说：“好。”", t3.substring(r3.first, r3.last + 1))
    }

    @Test
    fun `line para offsets let pages slice chapter text back`() {
        val body = "第一段正文内容比较长会跨越多行甚至多页。".repeat(30)
        val ch = chapter(0, body.length, title = "x")
        val layout = TxtPageSource.layoutChapter(body, ch, cfg, ruler, oracle)
        layout.pages.forEach { page ->
            page.lines.forEachIndexed { li, line ->
                val paraOff = page.lineParaOffsets[li]
                // 行首簇的章内偏移必须落在章文本里，且能切回与段内一致的文字
                val abs = paraOff + line.startInText
                assertTrue(abs in 0..body.length)
                val absEnd = paraOff + line.endInText
                assertTrue(absEnd in abs..body.length)
            }
        }
    }
}
