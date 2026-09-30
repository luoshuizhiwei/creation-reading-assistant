package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.DocBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F3：非分页 / 旧 EPUB 滚动模式「整块选择」的 source 半开区间证明。
 *
 * 坐标链（任务书要求从构建来源追到 locator 保存，不能只看变量名）：
 * `blockGlobalOffsets[i]` ← [computeBlockGlobalOffsets] ← [LegacyOffsetCodec.blockOffsets]
 *   = chapterBase + Σ(前面每个文本块 length + 1)，图片块记 -1。
 * `ReaderDocument` 不变式（`ReaderDocument.kt:47`）：
 *   text(i) == blocks(i).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }
 *
 * 因此第 i 个文本块的半开源区间 = [gOff, gOff + block.text.length)。
 * 终点**不**含块间 "\n"，恰好落在文本末字符之后，与 `epubChapterEndOffset`
 * 既有口径（start + text.length）一致。
 */
class EpubBlockSourceRangeTest {

    private fun blocks(texts: List<String?>) = texts.map { t ->
        if (t == null) DocBlock.Image(path = "/img.png", width = 0, height = 0)
        else DocBlock.Text(text = t)
    }

    @Test
    fun `a text block resolves to its exact half open source range`() {
        val bs = blocks(listOf("第一段落", "第二段落"))
        val offsets = computeBlockGlobalOffsets(bs, chapterBase = 0)
        assertEquals(0, offsets[0])
        assertEquals("第一段落".length + 1, offsets[1])

        val range = requireNotNull(epubTextBlockSourceRange(bs[0] as DocBlock.Text, offsets[0]))
        assertEquals(0, range.first)
        assertEquals("第一段落".length, range.last + 1)
    }

    @Test
    fun `second block start follows the newline separator and end is exact`() {
        val bs = blocks(listOf("第一段落", "第二段落"))
        val offsets = computeBlockGlobalOffsets(bs, chapterBase = 10)
        val second = bs[1] as DocBlock.Text

        val range = requireNotNull(epubTextBlockSourceRange(second, offsets[1]))
        assertEquals(offsets[1], range.first)
        assertEquals(offsets[1] + second.text.length, range.last + 1)
        // 半开终点不含块间换行：恰好等于下一块起点 - 1 之外的文本末
        assertEquals("第一段落".length + 1 + 10, range.first)
    }

    @Test
    fun `chapter base shifts every block range`() {
        val bs = blocks(listOf("段落"))
        val offsets = computeBlockGlobalOffsets(bs, chapterBase = 500)
        val range = requireNotNull(epubTextBlockSourceRange(bs[0] as DocBlock.Text, offsets[0]))
        assertEquals(500, range.first)
        assertEquals(500 + "段落".length, range.last + 1)
    }

    @Test
    fun `duplicate paragraphs never collapse into one range`() {
        val bs = blocks(listOf("相同文本", "相同文本", "相同文本"))
        val offsets = computeBlockGlobalOffsets(bs, chapterBase = 0)
        val ranges = bs.mapIndexed { i, b ->
            requireNotNull(epubTextBlockSourceRange(b as DocBlock.Text, offsets[i]))
        }
        assertEquals(3, ranges.size)
        ranges.forEachIndexed { i, r ->
            assertEquals("第 $i 块起点必须唯一", offsets[i], r.first)
            assertEquals("第 $i 块长度必须等于文本长度", "相同文本".length, r.last + 1 - r.first)
        }
        // 相邻两块区间不得重叠
        for (i in 0 until ranges.lastIndex) {
            assertTrue("相邻块区间必须不相交", ranges[i].last < ranges[i + 1].first)
        }
    }

    @Test
    fun `image block never produces a selectable range`() {
        val img = DocBlock.Image(path = "/a.png", width = 0, height = 0)
        assertNull("图片块不得生成伪选区", epubBlockSourceRange(img, gOff = -1))
        assertNull("图片块即使误传非负 gOff 也不得生成选区", epubBlockSourceRange(img, gOff = 42))
    }

    @Test
    fun `unknown offset never produces a selectable range`() {
        val text = DocBlock.Text("段落")
        assertNull("gOff = -1 表示坐标未知，不得猜测", epubTextBlockSourceRange(text, gOff = -1))
    }

    @Test
    fun `empty text block produces no range`() {
        assertNull(epubTextBlockSourceRange(DocBlock.Text(""), gOff = 0))
        assertNull(epubTextBlockSourceRange(DocBlock.Text("   "), gOff = 0))
    }

    @Test
    fun `block offsets reserve a newline between adjacent text blocks`() {
        // 与 ReaderScrollPosition.epubChapterEndOffset 的既有口径一致：
        // 不能以各块纯文本长度简单求和。
        val bs = blocks(listOf("aaa", "bbb", "ccc"))
        val offsets = computeBlockGlobalOffsets(bs, chapterBase = 0)
        assertEquals(listOf(0, 4, 8), offsets)
    }

    @Test
    fun `image blocks do not consume text offset space`() {
        val bs = blocks(listOf("aaa", null, "bbb"))
        val offsets = computeBlockGlobalOffsets(bs, chapterBase = 0)
        assertEquals(-1, offsets[1])
        assertEquals(4, offsets[2])
    }
}
