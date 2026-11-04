package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRA-MD-PAGED-CHAPTER-FIX-v1 定向回归。
 *
 * 真实设备根因（final-v3 device-md-r3 + 本任务诊断批 mdpaged-seam-diag3）：
 * 整本解析（isWholeDocumentParse=true）的 Markdown，`MarkdownDocument.blocks(i)` 产出的
 * 章视图 canonicalText 是**章局部切片**，而块/单元的 canonicalRange 保持**全书全局坐标**。
 * `MarkdownChapterSource.loadChapter` 把两者原样交给分页器 —— 分页契约要求块偏移相对
 * `text`，因此 startOffset>0 的章在排版切片时抛
 * `StringIndexOutOfBoundsException: length=353; index=382`（377=第2章全局起点 + 5=章内偏移），
 * `applyChapter` 永不执行 → 目录选章后正文/底栏停留上一章。
 *
 * 本测试锁定**失配机制本身**：块偏移必须落在 `text` 长度内，且段落文本必须是 `text`
 * 在自身偏移处的原样切片（可逆性纪律）；章首块必须从 0 开始（全局基址不得泄漏）；
 * 各章文本长度必须与生产章节表 charCount 一致（持久进度区间不受影响）。
 */
class MarkdownChapterSourceTest {

    /** 3 个 H1 章，每章 6 段 —— 与设备夹具同构（中性内容，无前言）。 */
    private fun fixture(): String = buildString {
        (1..3).forEach { ch ->
            appendLine("# 第 $ch 章")
            appendLine()
            (1..6).forEach { i ->
                appendLine("中性 Markdown 第 $ch 章段落 $i：用于分页章节坐标回归的正文。")
                appendLine()
            }
        }
    }

    private fun newSource(): MarkdownChapterSource {
        val doc = MarkdownDocument(fixture())
        assertTrue("前置：夹具必须是整本解析路径", doc.isWholeDocumentParse)
        assertTrue("前置：夹具必须切出 >=3 章", doc.chapters.size >= 3)
        return MarkdownChapterSource(doc)
    }

    @Test
    fun `整本解析的章视图必须以章内坐标交给分页器`() {
        val source = newSource()
        val doc = MarkdownDocument(fixture())
        doc.chapters.forEachIndexed { index, chapter ->
            // 修复前：index>=1 在 layoutBlocksOf 内抛 StringIndexOutOfBoundsException
            val content = source.loadChapter(index)
            assertEquals("章 $index 文本长度必须与生产章节表 charCount 一致",
                chapter.charCount, content.text.length)
            assertTrue("章 $index 文本不得为空", content.text.isNotEmpty())
            content.blocks.forEach { block ->
                when (block) {
                    is LayoutBlock.Text -> {
                        val start = block.paragraph.charOffset
                        assertTrue(
                            "章 $index 块偏移 $start 超出章内文本长度 ${content.text.length}" +
                                "（全局基址泄漏）",
                            start in 0..content.text.length,
                        )
                        val end = start + block.paragraph.text.length
                        assertTrue(
                            "章 $index 块切片 [${start},$end) 超出章内文本长度 ${content.text.length}",
                            end <= content.text.length,
                        )
                        assertEquals(
                            "章 $index 块文本必须是章内文本在自身偏移处的原样切片",
                            content.text.substring(start, end),
                            block.paragraph.text,
                        )
                    }
                    is LayoutBlock.Image -> {
                        assertTrue(
                            "章 $index 图片锚点 ${block.anchorOffset} 超出章内文本长度 ${content.text.length}",
                            block.anchorOffset in 0..content.text.length,
                        )
                    }
                }
            }
            val firstText = content.blocks.filterIsInstance<LayoutBlock.Text>().first()
            assertEquals(
                "章 $index 首块必须从 0 开始（章全局起点 ${chapter.startOffset} 泄漏进章内坐标）",
                0, firstText.paragraph.charOffset,
            )
        }
    }

    @Test
    fun `章节查找与持久化全局坐标保持一致`() {
        val source = newSource()
        val doc = MarkdownDocument(fixture())
        doc.chapters.forEachIndexed { index, chapter ->
            assertEquals("chapterStartAbs($index) 必须等于章节表起点",
                chapter.startOffset, source.chapterStartAbs(index))
            assertEquals("章首偏移必须命中该章", index, source.chapterIndexFor(chapter.startOffset))
        }
        assertEquals("全书末尾前一字节必须命中末章", doc.chapters.size - 1,
            source.chapterIndexFor(source.totalChars - 1))
    }
}
