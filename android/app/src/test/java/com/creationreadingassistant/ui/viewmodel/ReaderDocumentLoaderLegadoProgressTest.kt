package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.locator.LegadoProgressMarker
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderDocumentLoaderLegadoProgressTest {

    @Test
    fun `legado chapter coordinate converts only after local toc is available`() {
        val chapters = listOf(
            DocChapter(index = 0, title = "第一章", startOffset = 0, charCount = 100, charCountIsEstimated = false),
            DocChapter(index = 1, title = "第二章", startOffset = 100, charCount = 200, charCountIsEstimated = false),
        )
        val marker = LegadoProgressMarker.encode(chapterIndex = 1, charOffset = 35)

        assertEquals(135, resolveImportedTextOffset(marker, chapters, textLength = 300))
        assertEquals(0, resolveImportedTextOffset(marker, emptyList(), textLength = 300))
    }

    @Test
    fun `round trip keeps the plain text position written by the importer`() {
        // 纯文本闭环：导入器写出的标记必须能在打开时换回同一处，而不是回到书首。
        val chapters = listOf(
            DocChapter(index = 0, title = "第一章", startOffset = 0, charCount = 100, charCountIsEstimated = false),
            DocChapter(index = 1, title = "第二章", startOffset = 100, charCount = 200, charCountIsEstimated = false),
        )
        val written = LegadoProgressMarker.encode(chapterIndex = 1, charOffset = 35)
        assertEquals(135, resolveImportedTextOffset(written, chapters, textLength = 300))
    }

    @Test
    fun `out of range chapter index falls back to the start instead of a wrong position`() {
        val chapters = listOf(
            DocChapter(index = 0, title = "第一章", startOffset = 0, charCount = 100, charCountIsEstimated = false),
        )
        val marker = LegadoProgressMarker.encode(chapterIndex = 9, charOffset = 500)
        assertEquals(0, resolveImportedTextOffset(marker, chapters, textLength = 100))
    }

    @Test
    fun `converted offset never exceeds the current text length`() {
        val chapters = listOf(
            DocChapter(index = 0, title = "第一章", startOffset = 0, charCount = 100, charCountIsEstimated = false),
        )
        val marker = LegadoProgressMarker.encode(chapterIndex = 0, charOffset = 9999)
        assertEquals(100, resolveImportedTextOffset(marker, chapters, textLength = 100))
    }

    @Test
    fun `native locator still resolves through the stored absolute offset`() {
        // 非标记进度必须继续走原有路径，避免修复影响既有纯文本进度。
        val native = """{"offset":420,"locator_v2":{"v":2,"offset":420,"ci":1,"co":20}}"""
        assertEquals(420, resolveImportedTextOffset(native, emptyList(), textLength = 1000))
    }
}
