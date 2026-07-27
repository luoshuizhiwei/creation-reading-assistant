package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
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
            loadText = { index ->
                loads += index
                "chapter-$index"
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
}
