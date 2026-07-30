/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test(timeout = 4_000)
    fun scansVeryLongTxtWithStableMonotonicOffsets() {
        val chapterCount = 6_000
        val content = buildString(chapterCount * 180) {
            append("书名\n作者：压力测试\n")
            repeat(chapterCount) { index ->
                append("第${index + 1}章 章节${index + 1}\n")
                append("这是一段用于验证超长 TXT 目录扫描、偏移恢复与 UI 不阻塞的正文。".repeat(3))
                append('\n')
            }
        }

        val chapters = TextChapterDetector.detect(content)

        assertEquals(chapterCount + 1, chapters.size)
        assertEquals("开始", chapters.first().title)
        assertEquals("第${chapterCount}章 章节$chapterCount", chapters.last().title)
        assertTrue(chapters.zipWithNext().all { (left, right) -> left.startOffset < right.startOffset })
        assertTrue(chapters.last().startOffset < content.length)
    }
}
