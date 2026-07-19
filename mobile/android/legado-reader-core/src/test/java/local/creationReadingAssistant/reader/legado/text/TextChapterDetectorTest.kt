/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextChapterDetectorTest {
    @Test
    fun detectsChineseAndEnglishHeadingsWithStableOffsets() {
        val content = "书名\n作者：测试\n\n第一章 开始\n正文一\nChapter 2: Continue\n正文二\n番外 小故事\n结束"
        val chapters = TextChapterDetector.detect(content)

        assertEquals(listOf("开始", "第一章 开始", "Chapter 2: Continue", "番外 小故事"), chapters.map { it.title })
        assertEquals(0, chapters[0].startOffset)
        assertEquals(content.indexOf("第一章"), chapters[1].startOffset)
        assertEquals(content.indexOf("Chapter 2"), chapters[2].startOffset)
    }

    @Test
    fun fallsBackToBodyForBooksWithoutHeadings() {
        assertEquals(listOf(TextChapter(0, "正文", 0)), TextChapterDetector.detect("只有普通正文。"))
    }
}
