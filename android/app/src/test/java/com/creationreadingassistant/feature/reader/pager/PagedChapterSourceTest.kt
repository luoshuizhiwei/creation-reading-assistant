package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class PagedChapterSourceTest {

    @Test
    fun `txt source preserves exact chapter slices and offsets`() {
        val text = "第一章\n甲乙\n第二章\n丙丁"
        val secondStart = text.indexOf("第二章")
        val source = TxtChapterSource(
            fullText = text,
            chapters = listOf(
                DocChapter(0, "第一章", 0, secondStart),
                DocChapter(1, "第二章", secondStart, text.length - secondStart),
            ),
        )

        assertEquals(2, source.chapterCount)
        assertEquals(text.substring(0, secondStart), source.loadChapterText(0))
        assertEquals(text.substring(secondStart), source.loadChapterText(1))
        assertEquals(secondStart, source.chapterStartAbs(1))
        assertEquals(1, source.chapterIndexFor(text.length - 1))
        assertEquals(text.length, source.totalChars)
    }

    @Test
    fun `epub source uses legacy chapter bases for binary lookup`() {
        val loads = mutableListOf<Int>()
        val source = EpubChapterSource(
            titles = listOf("一", "二", "三"),
            chapterStartOffsets = listOf(0, 101, 302),
            totalChars = 603,
            loadBlocks = { index ->
                loads += index
                listOf(DocBlock.Text("chapter-$index"))
            },
        )

        assertEquals(0, source.chapterIndexFor(-50))
        assertEquals(0, source.chapterIndexFor(100))
        assertEquals(1, source.chapterIndexFor(101))
        assertEquals(1, source.chapterIndexFor(301))
        assertEquals(2, source.chapterIndexFor(9999))
        assertEquals("chapter-2", source.loadChapterText(2))
        assertEquals(listOf(2), loads)
    }

    @Test
    fun `epub page source keeps text offsets reversible and images zero-width`() {
        val blocks = listOf(
            DocBlock.Image("cover", 800, 1200),
            DocBlock.Text("　标题　", isHeading = true),
            DocBlock.Image("middle", 640, 480),
            DocBlock.Text(" 第一行 \n\n第二行 "),
            DocBlock.Image("ending", 0, 0),
        )
        val text = EpubPageSource.chapterTextOf(blocks)
        val layout = EpubPageSource.layoutBlocksOf(blocks)

        assertEquals("　标题　\n 第一行 \n\n第二行 ", text)
        val paragraphs = layout.filterIsInstance<LayoutBlock.Text>().map { it.paragraph }
        paragraphs.forEach { paragraph ->
            assertEquals(
                paragraph.text,
                text.substring(paragraph.charOffset, paragraph.charOffset + paragraph.text.length),
            )
        }
        assertEquals(listOf(0, 5, text.length), layout.filterIsInstance<LayoutBlock.Image>().map { it.anchorOffset })
        assertEquals(com.creationreadingassistant.feature.reader.layout.BlockRole.HEADING, paragraphs.first().role)
    }
}
