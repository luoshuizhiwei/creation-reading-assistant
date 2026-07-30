/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.reader.legado.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBookParserTest {
    @Test
    fun convertsCommonBlocksAndBuildsNestedToc() {
        val source = """
            前言正文。

            # 第一卷
            ## 第一章 开始

            > 这是引用。
            - [x] 已完成
            - 普通列表

            | 人物 | 身份 |
            | --- | --- |
            | 阿明 | 主角 |

            正文包含 **粗体**、*斜体*、[资料](https://example.com) 和 ![封面](cover.png)。

            ```kotlin
            val answer = 42
            ```
        """.trimIndent()

        val document = MarkdownBookParser.parse(source)

        assertEquals(listOf("开始", "第一卷", "第一章 开始"), document.chapters.map { it.title })
        assertEquals(listOf(1, 1, 2), document.chapters.map { it.level })
        assertTrue(document.text.contains("│ 这是引用。"))
        assertTrue(document.text.contains("☑ 已完成"))
        assertTrue(document.text.contains("• 普通列表"))
        assertTrue(document.text.contains("人物 ｜ 身份"))
        assertTrue(document.text.contains("资料（https://example.com）"))
        assertTrue(document.text.contains("[图片：封面]"))
        assertTrue(document.text.contains("    val answer = 42"))
        assertFalse(document.text.contains("**粗体**"))
        assertTrue(document.chapters.zipWithNext().all { (left, right) -> left.startOffset < right.startOffset })
    }

    @Test
    fun supportsSetextHeadingsAndBodyFallback() {
        val document = MarkdownBookParser.parse("书名\n====\n\n只有正文。")
        assertEquals(listOf("书名"), document.chapters.map { it.title })
        assertEquals(1, document.chapters.first().level)

        val bodyOnly = MarkdownBookParser.parse("普通正文，没有标题。")
        assertEquals(listOf("正文"), bodyOnly.chapters.map { it.title })
    }
}
