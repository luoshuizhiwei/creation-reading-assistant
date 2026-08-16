package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 搜索命中绝对区间（absoluteRange）的统一基准测试：
 * 小文件 TXT / 流式 TXT（跨单元边界）/ EPUB 逐章 三条计算路径必须产出
 * 与「渲染/分页/locator 同一字符空间」的全书偏移区间。
 */
class SearchResultOffsetTest {

    @Test
    fun `book search absolute ranges are global text offsets`() {
        val text = "今天天气不错，明天天气更好。天气真好。"
        val results = computeBookSearch(
            fullText = text,
            query = "天气",
            chapterStartOffsets = emptyList(),
            chapterTitles = emptyList(),
            isTxt = true,
        )

        val offsets = results.map { it.absoluteRange.first to it.absoluteRange.last }
        // 「天气」出现在全书偏移 2 / 9 / 14，区间含首不含尾 → last = first + 1
        assertEquals(listOf(2 to 3, 9 to 10, 14 to 15), offsets)
        results.forEach {
            assertEquals("天气", text.substring(it.absoluteRange))
        }
    }

    @Test
    fun `streaming txt search maps cross unit boundary hit to global range`() = runTest {
        // 单元 1：前 6 字符；单元 2：后 6 字符；匹配「123456」跨越单元边界
        val unit1 = ReadingUnit(unitIndex = 0, chapterIndex = 0, title = "全文", charStart = 0, charCount = 6)
        val unit2 = ReadingUnit(unitIndex = 1, chapterIndex = 0, title = "全文", charStart = 6, charCount = 6)
        // 小文件构造 + 显式 readingUnits：readUnit 按 charStart/charCount 切片，
        // 与流式路径共用同一计算分支，可纯 JVM 验证跨单元偏移映射。
        val document = PlainTextDocument("abc123456def").apply {
            readingUnits = listOf(unit1, unit2)
        }

        val results = computeStreamingTxtSearch(
            document = document,
            readingUnits = listOf(unit1, unit2),
            query = "123456",
            totalChars = 12,
        )

        // 跨边界命中起点在单元 1 尾部（全局 3..9），不是单元 2 起点
        assertEquals(listOf(3 until 9), results.map { it.absoluteRange })
    }

    @Test
    fun `epub search absolute ranges map chapter local hits to global offsets`() = runTest {
        val fake = object : ReaderDocument {
            override val chapters: List<DocChapter> = listOf(
                DocChapter(0, "第一章", 0, 6),
                DocChapter(1, "第二章", 7, 6),
            )
            override val totalChars: Int = 13
            override fun blocks(chapterIndex: Int): List<DocBlock> = emptyList()
            override fun text(chapterIndex: Int): String = when (chapterIndex) {
                0 -> "abcabc"
                else -> "defabc"
            }
        }

        val results = computeEpubSearch(
            document = fake,
            query = "abc",
            chapterStartOffsets = listOf(0, 7),
            chapterTitles = listOf("第一章", "第二章"),
            totalChars = 13,
        )

        // 章 0 命中：0..3 / 3..6；章 1 命中：7+3=10..13
        assertEquals(
            listOf(0 until 3, 3 until 6, 10 until 13),
            results.map { it.absoluteRange },
        )
        assertEquals(listOf(0, 0, 1), results.map { it.chapterIndex })
    }

    @Test
    fun `select builds target from computed result without offset stitching`() {
        val text = "第一章内容abc，第二章内容abc。"
        val results = computeBookSearch(text, "abc", emptyList(), emptyList(), isTxt = true)
        val session = BookSearchSession(bookKey = "b1")
        session.onQueryChanged("abc")
        val run = session.beginRun()
        session.onSearchCompleted(run, results)

        val target = session.select(1)

        assertEquals("b1", target?.bookKey)
        assertEquals(-1, target?.chapterIndex)
        // 第二处「abc」在全书偏移 14（第0-7章前缀+正文，第二处起点 14）
        assertEquals(14 until 17, target?.absoluteRange)
        assertEquals(1, target?.resultIndex)
        assertEquals("abc", text.substring(target!!.absoluteRange))
    }
}
