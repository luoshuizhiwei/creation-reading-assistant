package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.ui.screen.shelf.extractChapterLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 章节元数据源选择的行为测试（缺陷：小 TXT 保存进度时章号恒回落为「第 1 章」）。
 *
 * 缺陷链（真机可复现）：小 TXT 滚动路径下 bookIndex / markdownDocument /
 * txtStreamingDocument 全为 null，[selectChapterMetadata] 的旧回退链止于
 * `emptyList()`；进度落库用这张空表调 [EpubLocatorMapping.toChapterOffset]，
 * 空表恒返回 `ci = 0`，`LocatorBuilder.progressJson` 写入 `"ci":0`，
 * 书架进度卡 [extractChapterLabel] 于是永远显示「第1章」。
 *
 * 这些用例驱动的是生产函数本身（选择器 + 二进制反推 + locator 编码 + 书架标签），
 * 不是把期望抄进断言：把第四档源摘掉（回退成 `?: emptyList()`），小 TXT 的两条用例
 * 与端到端那条立刻变红。
 */
class ReaderDerivedChapterMetadataTest {

    private fun chapter(index: Int, title: String, startOffset: Int) = DocChapter(
        index = index,
        title = title,
        startOffset = startOffset,
        charCount = 100,
    )

    private val smallTxtChapters = listOf(
        chapter(index = 0, title = "第1章 起风了", startOffset = 0),
        chapter(index = 1, title = "第2章 雪原", startOffset = 500),
        chapter(index = 2, title = "第3章 篝火", startOffset = 1200),
    )

    private val epubIndex = BookIndex(
        chapterStartOffsets = listOf(0, 9000, 18000),
        chapterTitles = listOf("epub-1", "epub-2", "epub-3"),
        totalChars = 27000,
    )

    private val markdownChapters = listOf(
        chapter(index = 0, title = "md-1", startOffset = 0),
        chapter(index = 1, title = "md-2", startOffset = 640),
    )

    private val streamingChapters = listOf(
        chapter(index = 0, title = "stream-1", startOffset = 0),
        chapter(index = 1, title = "stream-2", startOffset = 40000),
    )

    @Test
    fun `small txt chapters supply both offsets and titles`() {
        // 缺陷现场：此前只有前三档，小 TXT 拿到的是两张空表
        val metadata = selectChapterMetadata(
            bookIndex = null,
            markdownChapters = null,
            txtStreamingChapters = null,
            smallTxtChapters = smallTxtChapters,
        )

        assertEquals(listOf(0, 500, 1200), metadata.startOffsets)
        assertEquals(
            listOf("第1章 起风了", "第2章 雪原", "第3章 篝火"),
            metadata.titles,
        )
    }

    @Test
    fun `offsets and titles never come from different sources`() {
        // 每章标题写死自己的起始偏移：偏移与标题一旦分头选源（各走一遍回退链），
        // 这个断言当场失败 —— 单独比 size 抓不到等长的跨源混搭。
        fun paired(prefix: String, offsets: List<Int>) = offsets.mapIndexed { i, off ->
            DocChapter(index = i, title = "$prefix@$off", startOffset = off, charCount = 10)
        }
        val pairedEpub = BookIndex(
            chapterStartOffsets = listOf(0, 700),
            chapterTitles = listOf("epub@0", "epub@700"),
            totalChars = 1500,
        )
        val md = paired("md", listOf(0, 30))
        val streaming = paired("st", listOf(0, 9000))
        val small = paired("small", listOf(0, 500))
        val cases = listOf(
            selectChapterMetadata(pairedEpub, md, streaming, small),
            selectChapterMetadata(null, md, streaming, small),
            selectChapterMetadata(null, null, streaming, small),
            selectChapterMetadata(null, null, null, small),
        )
        val prefixes = listOf("epub", "md", "st", "small")

        cases.zip(prefixes).forEach { (metadata, prefix) ->
            assertEquals(
                metadata.startOffsets.map { "$prefix@$it" },
                metadata.titles,
            )
            assertTrue(metadata.startOffsets.isNotEmpty())
        }
    }

    @Test
    fun `epub book index outranks every txt chapter source`() {
        val metadata = selectChapterMetadata(
            bookIndex = epubIndex,
            markdownChapters = markdownChapters,
            txtStreamingChapters = streamingChapters,
            smallTxtChapters = smallTxtChapters,
        )

        // EPUB 章起是估算量纲（与历史 locator 同源），掺进真实字符偏移会让续读漂位
        assertEquals(listOf(0, 9000, 18000), metadata.startOffsets)
        assertEquals(listOf("epub-1", "epub-2", "epub-3"), metadata.titles)
    }

    @Test
    fun `markdown source keeps precedence over streaming and small txt`() {
        val metadata = selectChapterMetadata(
            bookIndex = null,
            markdownChapters = markdownChapters,
            txtStreamingChapters = streamingChapters,
            smallTxtChapters = smallTxtChapters,
        )

        assertEquals(listOf(0, 640), metadata.startOffsets)
        assertEquals(listOf("md-1", "md-2"), metadata.titles)
    }

    @Test
    fun `streaming txt source keeps precedence over small txt chapters`() {
        val metadata = selectChapterMetadata(
            bookIndex = null,
            markdownChapters = null,
            txtStreamingChapters = streamingChapters,
            smallTxtChapters = smallTxtChapters,
        )

        assertEquals(listOf(0, 40000), metadata.startOffsets)
        assertEquals(listOf("stream-1", "stream-2"), metadata.titles)
    }

    @Test
    fun `present but empty markdown chapter table does not fall through to small txt`() {
        // 前三档按「源是否在场」裁决，不按列表是否为空：文档在场但章表为空是解析器的
        // 诚实结论，继续下探会掺进另一个量纲的偏移。
        val metadata = selectChapterMetadata(
            bookIndex = null,
            markdownChapters = emptyList(),
            txtStreamingChapters = streamingChapters,
            smallTxtChapters = smallTxtChapters,
        )

        assertEquals(emptyList<Int>(), metadata.startOffsets)
        assertEquals(emptyList<String>(), metadata.titles)
    }

    @Test
    fun `chapter detection failure on small txt degrades honestly to empty metadata`() {
        val metadata = selectChapterMetadata(
            bookIndex = null,
            markdownChapters = null,
            txtStreamingChapters = null,
            smallTxtChapters = emptyList(),
        )

        assertEquals(emptyList<Int>(), metadata.startOffsets)
        assertEquals(emptyList<String>(), metadata.titles)
    }

    @Test
    fun `offset inside the second small txt chapter resolves to chapter index one`() {
        val metadata = selectChapterMetadata(
            bookIndex = null,
            markdownChapters = null,
            txtStreamingChapters = null,
            smallTxtChapters = smallTxtChapters,
        )

        assertEquals(0, EpubLocatorMapping.toChapterOffset(0, metadata.startOffsets).first)
        assertEquals(0, EpubLocatorMapping.toChapterOffset(499, metadata.startOffsets).first)
        assertEquals(1, EpubLocatorMapping.toChapterOffset(500, metadata.startOffsets).first)
        assertEquals(1, EpubLocatorMapping.toChapterOffset(700, metadata.startOffsets).first)
        assertEquals(2, EpubLocatorMapping.toChapterOffset(1300, metadata.startOffsets).first)
        // 章内偏移同样自洽：700 - 500 = 200
        assertEquals(200, EpubLocatorMapping.toChapterOffset(700, metadata.startOffsets).second)
    }

    @Test
    fun `shelf chapter label follows the saved locator instead of pinning chapter one`() {
        // 端到端串一遍真实链路：派生元数据 → 章号反推 → progressJson → 书架「第 N 章」。
        val metadata = selectChapterMetadata(
            bookIndex = null,
            markdownChapters = null,
            txtStreamingChapters = null,
            smallTxtChapters = smallTxtChapters,
        )
        fun labelFor(offsets: List<Int>, offset: Int): String? {
            val ci = EpubLocatorMapping.toChapterOffset(offset, offsets).first
            val json = LocatorBuilder.progressJson(
                legacyOffset = offset,
                chapterIndex = ci,
                charOffset = offset,
            )
            return extractChapterLabel(
                ReadingProgressEntity(
                    book_id = "book-1",
                    updated_at = "2026-09-22T00:00:00Z",
                    current_location_json = json,
                ),
            )
        }

        assertEquals("第2章", labelFor(metadata.startOffsets, 700))
        // 修复前的空表：同一条链路只能给出「第1章」——这就是用户看到的症状
        assertEquals("第1章", labelFor(emptyList(), 700))
    }
}
