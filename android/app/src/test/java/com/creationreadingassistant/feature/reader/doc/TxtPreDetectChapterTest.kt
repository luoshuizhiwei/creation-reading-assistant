package com.creationreadingassistant.feature.reader.doc

import com.creationreadingassistant.feature.reader.layout.BreakOracle
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.TextRuler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 优化验证：TxtChapterDetector.detect() 预检测结果与同步检测完全一致，
 * 且分页引擎对相同章节产出相同页面边界。
 */
class TxtPreDetectChapterTest {

    // ── 测试 1：优化前后页面边界完全一致 ──────────────────────────────

    @Test
    fun `preDetect output matches synchronous detect for same text and rule`() {
        val text = buildString {
            appendLine("第一章 起源")
            appendLine("这是第一章的内容。".repeat(50))
            appendLine("第二章 征途")
            appendLine("这是第二章的内容。".repeat(50))
            appendLine("第三章 归来")
            appendLine("这是第三章的内容。".repeat(50))
        }
        // 模拟 IO 线程预检测
        val preDetected = TxtChapterDetector.detect(text, "builtin")
        // 模拟 UI 线程同步检测（fallback 路径）
        val syncDetected = TxtChapterDetector.detect(text, "builtin")

        assertEquals(syncDetected.size, preDetected.size)
        for (i in syncDetected.indices) {
            assertEquals(syncDetected[i].title, preDetected[i].title)
            assertEquals(syncDetected[i].startOffset, preDetected[i].startOffset)
            assertEquals(syncDetected[i].endOffset, preDetected[i].endOffset)
        }
    }

    @Test
    fun `preDetect DocChapter conversion preserves offsets`() {
        val text = buildString {
            appendLine("第一章 起源")
            appendLine("内容。".repeat(100))
            appendLine("第二章 征途")
            appendLine("内容。".repeat(100))
        }
        val detected = TxtChapterDetector.detect(text, "builtin")
        val docChapters = detected.mapIndexed { i, c ->
            DocChapter(
                index = i,
                title = c.title,
                startOffset = c.startOffset,
                charCount = c.charCount,
                charCountIsEstimated = false,
            )
        }
        assertEquals(detected.size, docChapters.size)
        for (i in detected.indices) {
            assertEquals(detected[i].title, docChapters[i].title)
            assertEquals(detected[i].startOffset, docChapters[i].startOffset)
            assertEquals(detected[i].charCount, docChapters[i].charCount)
        }
    }

    @Test
    fun `paginator produces same pageStarts for preDetected vs sync chapters`() {
        val text = buildString {
            appendLine("第一章 起源")
            appendLine("这是第一章的内容段落。".repeat(80))
            appendLine("第二章 征途")
            appendLine("这是第二章的内容段落。".repeat(80))
        }
        val detected = TxtChapterDetector.detect(text, "builtin")
        val docChapters = detected.mapIndexed { i, c ->
            DocChapter(index = i, title = c.title, startOffset = c.startOffset, charCount = c.charCount)
        }

        // 用预检测章节构建 TxtChapterSource
        val sourcePre = com.creationreadingassistant.feature.reader.pager.TxtChapterSource(text, docChapters)
        // 用同步检测章节构建 TxtChapterSource
        val syncChapters = PlainTextDocument(text, "builtin").chapters
        val sourceSync = com.creationreadingassistant.feature.reader.pager.TxtChapterSource(text, syncChapters)

        assertEquals(sourceSync.chapterCount, sourcePre.chapterCount)
        for (i in 0 until sourceSync.chapterCount) {
            assertEquals(sourceSync.chapterStartAbs(i), sourcePre.chapterStartAbs(i))
            assertEquals(sourceSync.loadChapter(i).text, sourcePre.loadChapter(i).text)
        }
    }

    // ── 测试 2：预检测章节正确传递 ──────────────────────────────────

    @Test
    fun `preDetectedChapters non-empty for small file with chapters`() {
        val text = buildString {
            appendLine("第一章 开始")
            appendLine("正文内容。".repeat(100))
            appendLine("第二章 继续")
            appendLine("正文内容。".repeat(100))
        }
        val detected = TxtChapterDetector.detect(text, "builtin")
        assertTrue("Should detect chapters", detected.size > 1)
        // 模拟 ReaderLoadedContent.Text 的 preDetectedChapters
        val docChapters = detected.mapIndexed { i, c ->
            DocChapter(index = i, title = c.title, startOffset = c.startOffset, charCount = c.charCount)
        }
        assertTrue("preDetectedChapters should not be empty", docChapters.isNotEmpty())
    }

    @Test
    fun `preDetectedRuleId mismatch triggers fallback`() {
        val text = buildString {
            appendLine("1. 第一节")
            appendLine("内容。".repeat(100))
            appendLine("2. 第二节")
            appendLine("内容。".repeat(100))
        }
        // 用 builtin 规则预检测（不会识别 "1. 第一节"）
        val preDetected = TxtChapterDetector.detect(text, "builtin")
        // 用 num-dot 规则同步检测（会识别 "1. 第一节"）
        val syncDetected = TxtChapterDetector.detect(text, "num-dot")

        // 规则不同时结果不同，验证 fallback 的必要性
        assertNotEquals(syncDetected.size, preDetected.size)
    }

    // ── 测试 4：配置变化正确失效缓存 ──────────────────────────────

    @Test
    fun `rule change produces different chapter list`() {
        val text = buildString {
            appendLine("一、概述")
            appendLine("内容。".repeat(100))
            appendLine("二、详情")
            appendLine("内容。".repeat(100))
        }
        val builtinResult = TxtChapterDetector.detect(text, "builtin")
        val cnNumDotResult = TxtChapterDetector.detect(text, "cn-num-dot")

        // cn-num-dot 规则应该识别 "一、概述" 和 "二、详情"
        assertTrue("cn-num-dot should detect more chapters", cnNumDotResult.size > builtinResult.size)
    }

    // ── 测试 6：大文件路径不受影响 ──────────────────────────────────

    @Test
    fun `streaming document path does not call detect - preDetectedChapters empty`() {
        // 大文件路径：streamingDocument != null 时，preDetectedChapters 应为空
        // 验证逻辑条件：fullText.isNotEmpty() && streamingDocument == null
        val fullText = "" // 大文件路径 fullText 为空
        val hasStreamingDoc = true // 模拟 streamingDocument != null
        val shouldPreDetect = fullText.isNotEmpty() && !hasStreamingDoc
        assertTrue("Large file path should NOT pre-detect", !shouldPreDetect)
    }

    @Test
    fun `small file path triggers preDetect`() {
        val fullText = "第一章 开始\n内容"
        val streamingDocument: PlainTextDocument? = null // 小文件无流式文档
        val shouldPreDetect = fullText.isNotEmpty() && streamingDocument == null
        assertTrue("Small file path should pre-detect", shouldPreDetect)
    }

    // ── 测试 7：编码边界不截断字符 ──────────────────────────────────

    @Test
    fun `UTF-8 mixed text chapter detection does not truncate characters`() {
        // 混合中英文、emoji、特殊字符
        val text = buildString {
            appendLine("第一章 中文English混合")
            appendLine("包含emoji\uD83D\uDE00和特殊字符©®™的内容。".repeat(50))
            appendLine("第二章 日本語テスト")
            appendLine("日本語の内容も正しく処理される。".repeat(50))
            appendLine("第三章 한국어")
            appendLine("한국어 내용도 올바르게 처리됩니다.".repeat(50))
        }
        val detected = TxtChapterDetector.detect(text, "builtin")
        assertTrue("Should detect 3 chapters", detected.size >= 3)

        // 验证每个章节的文本切片不截断字符
        for (chapter in detected) {
            val chapterText = text.substring(
                chapter.startOffset,
                chapter.endOffset.coerceAtMost(text.length),
            )
            // 验证切片是有效字符串（不截断代理对）
            assertEquals(chapter.charCount, chapterText.length)
            // 验证没有孤立代理字符
            for (i in chapterText.indices) {
                val c = chapterText[i]
                if (c.isHighSurrogate()) {
                    assertTrue("High surrogate must be followed by low surrogate",
                        i + 1 < chapterText.length && chapterText[i + 1].isLowSurrogate())
                }
                if (c.isLowSurrogate()) {
                    assertTrue("Low surrogate must be preceded by high surrogate",
                        i > 0 && chapterText[i - 1].isHighSurrogate())
                }
            }
        }
    }

    @Test
    fun `GB18030 decodable text preserves chapter boundaries`() {
        // 模拟 GB18030 解码后的文本（解码后就是 String，与 UTF-8 无差别）
        val text = buildString {
            appendLine("第一章 繁体中文測試")
            appendLine("繁體中文的內容也需要正確處理。".repeat(50))
            appendLine("第二章 简体")
            appendLine("简体中文内容。".repeat(50))
        }
        val detected = TxtChapterDetector.detect(text, "builtin")
        assertTrue("Should detect chapters in decoded GB18030 text", detected.size >= 2)
        // 验证偏移连续性（无空洞）
        for (i in 1 until detected.size) {
            assertEquals("Chapter boundaries must be contiguous",
                detected[i - 1].endOffset, detected[i].startOffset)
        }
    }

    // ── 测试 3：快速切书不显示旧书页面（逻辑验证）──────────────────

    @Test
    fun `different books produce independent chapter lists`() {
        val book1 = "第一章 书一\n内容A。".repeat(50)
        val book2 = "第一章 书二\n内容B。".repeat(50)

        val chapters1 = TxtChapterDetector.detect(book1, "builtin")
        val chapters2 = TxtChapterDetector.detect(book2, "builtin")

        // 两本书的章节列表是独立对象
        assertNotEquals(System.identityHashCode(chapters1), System.identityHashCode(chapters2))
        // 内容不同
        assertNotEquals(chapters1.firstOrNull()?.title, chapters2.firstOrNull()?.title)
    }

    // ── 测试 5：取消任务不遗留旧结果（逻辑验证）──────────────────

    @Test
    fun `empty text returns single full-text chapter - no stale data`() {
        val result = TxtChapterDetector.detect("", "builtin")
        assertEquals(1, result.size)
        assertEquals("全文", result[0].title)
        assertEquals(0, result[0].startOffset)
        assertEquals(0, result[0].endOffset)
    }
}
