package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动嗅探（builtin 兜底 → 编号样式候选）的验证。
 *
 * 场景：晋江/豆瓣阅读/知乎盐选系小说目录是「1、标题」「一、标题」「【1】标题」或纯数字行，
 * builtin 兜底成「全文」单章后应自动嗅探恢复目录；同时密度验证必须挡住误报风暴。
 */
class TxtChapterDetectorAutoSniffTest {

    /** 造一章正文：标题行 + 填充正文（默认 ~600 字，超过密度阈值 300）。 */
    private fun chapter(title: String, fillerChars: Int = 600): String =
        buildString {
            appendLine(title)
            append("正文内容。".repeat((fillerChars / 5).coerceAtLeast(1)))
            appendLine()
        }

    @Test
    fun `numbered dot style book gets directory via auto sniff`() {
        val text = (1..8).joinToString("") { chapter("${it}、第${it}章的标题") }
        val chapters = TxtChapterDetector.detect(text, listOf("builtin"))
        assertTrue("应嗅探出编号目录，实际 ${chapters.size} 章", chapters.size >= 8)
        assertTrue("不应退回全文", chapters.none { it.title == "全文" })
    }

    @Test
    fun `chinese numbered dot style book gets directory via auto sniff`() {
        val cn = listOf("一", "二", "三", "四", "五", "六")
        val text = cn.joinToString("") { chapter("$it、那一年的雨") }
        val chapters = TxtChapterDetector.detect(text, listOf("builtin"))
        assertTrue("应嗅探出中文编号目录，实际 ${chapters.size} 章", chapters.size >= 6)
    }

    @Test
    fun `bracketed number style book gets directory via auto sniff`() {
        val text = (1..6).joinToString("") { chapter("【$it】标题文字") }
        val chapters = TxtChapterDetector.detect(text, listOf("builtin"))
        assertTrue("应嗅探出括号编号目录，实际 ${chapters.size} 章", chapters.size >= 6)
    }

    @Test
    fun `false positive storm still falls back to full text`() {
        // 每 60 字一个「1、」行：平均章节远低于密度阈值，必须整体作废
        val noise = (1..60).joinToString("") { "${if (it % 3 == 0) 1 else it}、碎片" + "零散记录".repeat(4) + "\n" }
        val chapters = TxtChapterDetector.detect(noise, listOf("builtin"))
        assertEquals("误报风暴应退回全文", listOf("全文"), chapters.map { it.title })
    }

    @Test
    fun `proper chapter keyword books are not affected by sniff`() {
        val text = (1..6).joinToString("") { chapter("第${it}章 标准标题") }
        val chapters = TxtChapterDetector.detect(text, listOf("builtin"))
        assertTrue(chapters.size >= 6)
        assertEquals("第1章 标准标题", chapters.first { it.title.startsWith("第1章") }.title.trim())
    }

    @Test
    fun `short texts do not trigger sniff`() {
        val text = (1..4).joinToString("") { chapter("${it}、短文", fillerChars = 30) }
        // 总长 < 3000：即使编号样式可匹配也不嗅探（样本/片段无目录意义）
        val chapters = TxtChapterDetector.detect(text, listOf("builtin"))
        assertEquals("全文", chapters.single().title)
    }
}
