package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TXT 章节识别的边界。
 *
 * 这里最重要的不是「能认出多少章」，而是「不会乱认」。参考实现踩过的坑是
 * 把正文里的「1. 他说」当成章节标题，长篇小说凭空多出几百个伪章节。
 * 所以误报用例比正报用例更值得写。
 */
class TxtChapterDetectorTest {

    private fun body(n: Int = 600) = "正文内容。".repeat(n / 5)

    // ── 应当识别 ────────────────────────────────────────────────────────

    @Test
    fun `recognizes common chinese chapter headings`() {
        listOf(
            "第一章",
            "第一章 初见",
            "第 1 章 初见",
            "第123章",
            "第一百二十三章 风起",
            "第一节 开端",
            "第十回 群英会",
            "第一卷 少年篇",
            "卷一",
            "卷二 江湖",
            "序章",
            "楔子",
            "前言",
            "尾声",
            "番外 后日谈",
            "大结局",
            "Chapter 1",
            "CHAPTER IV The Return",
        ).forEach {
            assertTrue("应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    // ── 不应识别（这些才是关键）─────────────────────────────────────────

    @Test
    fun `does not misread numbered body lines as headings`() {
        // 参考实现正是栽在这一类上
        listOf(
            "1. 他说这句话的时候没有看我",
            "一、那天下着雨",
            "2）第二个理由",
            "３．第三点",
            "（1）首先",
        ).forEach {
            assertFalse("不应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `does not misread narrative sentences mentioning chapters`() {
        listOf(
            "他翻到第三章，发现里面夹着一张纸条，纸条上写着一行小字。",
            "这本书的第一章讲的是主角的童年。",
            "第二天，卷起的帘子后面站着一个人。",
        ).forEach {
            assertFalse("不应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `rejects overlong lines even if they start like a heading`() {
        val long = "第一章 " + "很长的副标题".repeat(20)
        assertFalse(TxtChapterDetector.isChapterTitle(long))
    }

    // ── 整篇识别 ────────────────────────────────────────────────────────

    @Test
    fun `splits text into chapters with continuous offsets`() {
        val text = buildString {
            append("第一章 起\n").append(body()).append("\n")
            append("第二章 承\n").append(body()).append("\n")
            append("第三章 转\n").append(body())
        }
        val chapters = TxtChapterDetector.detect(text)
        assertEquals(3, chapters.size)
        assertEquals("第一章 起", chapters[0].title)
        assertEquals("第三章 转", chapters[2].title)

        // 偏移必须连续无空洞，且覆盖全文 —— 后续按偏移定位全靠这一点
        assertEquals(0, chapters[0].startOffset)
        for (i in 0 until chapters.size - 1) {
            assertEquals(
                "第 $i 章的结尾必须紧接下一章的开头",
                chapters[i].endOffset,
                chapters[i + 1].startOffset,
            )
        }
        assertEquals(text.length, chapters.last().endOffset)
    }

    @Test
    fun `keeps content before first heading as its own chapter`() {
        val text = "某某 著\n简介：这是一本书。\n" + body() + "\n第一章 起\n" + body()
        val chapters = TxtChapterDetector.detect(text)
        assertEquals("开篇", chapters[0].title)
        assertEquals(0, chapters[0].startOffset)
        assertEquals("第一章 起", chapters[1].title)
    }

    @Test
    fun `falls back to single chapter when nothing matches`() {
        val text = body(5000)
        val chapters = TxtChapterDetector.detect(text)
        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
        assertEquals(text.length, chapters[0].endOffset)
    }

    @Test
    fun `discards detection when chapters are absurdly dense`() {
        // 一份目录页：连续都是标题、几乎没有正文。
        // 这种情况下给出几十个空章节比没有目录更糟，应整体作废。
        val toc = (1..50).joinToString("\n") { "第${it}章 标题$it" }
        val chapters = TxtChapterDetector.detect(toc)
        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
    }

    @Test
    fun `handles empty and blank input`() {
        assertEquals(1, TxtChapterDetector.detect("").size)
        assertEquals("全文", TxtChapterDetector.detect("").title())
        assertEquals("全文", TxtChapterDetector.detect("   \n  \n").title())
    }

    private fun List<TxtChapterDetector.Chapter>.title() = first().title
}
