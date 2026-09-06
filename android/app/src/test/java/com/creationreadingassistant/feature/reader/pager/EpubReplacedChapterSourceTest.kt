package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceResult
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.ui.screen.reader.tts.splitSentencesWithOffsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EpubReplacedChapterSource 契约锁：EPUB 分页净化投影接入分页宿主后的坐标安全。
 *
 * 混合空间口径：全书层 = 估算章基址（chapterStartAbs，测试取 100）+ 章内真实字符偏移；
 * display 层 = 投影后块序的同构拼接。全部换算经 BoundedReplaceResult.Exact
 * （scopeSourceBase=基址，offsetMap=ChapterBlockOffsetMap），与分页宿主
 * （PagedReaderController / PagedReaderPageSurface）的生产消费路径同源。
 */
class EpubReplacedChapterSourceTest {

    private val chapterBase = 100

    /** 三文本块 + 两图片：source 文本 =「第一章标题\n这一段有广告内容，需要净化处理。\n第三段正常文字。」 */
    private fun blocks() = listOf(
        DocBlock.Text("第一章标题", isHeading = true),
        DocBlock.Image("img-a", 100, 200),
        DocBlock.Text("这一段有广告内容，需要净化处理。"),
        DocBlock.Image("img-b", 100, 200),
        DocBlock.Text("第三段正常文字。"),
    )

    private fun rules() = listOf(
        replaceRule("章标题", ""),
        replaceRule("广告", "推荐"),
        replaceRule("净化处理", "清理"),
    )

    private fun source(
        maxChars: Int = 256 * 1024,
        onUnsupportedTooLarge: (BoundedReplaceResult.UnsupportedTooLarge) -> Unit = {},
    ) = EpubReplacedChapterSource(
        delegate = EpubChapterSource(
            titles = listOf("第一章"),
            chapterStartOffsets = listOf(chapterBase),
            totalChars = chapterBase + 31,
            loadBlocks = { blocks() },
        ),
        bookId = "book-1",
        rules = rules(),
        maxSourceLength = maxChars,
        onUnsupportedTooLarge = onUnsupportedTooLarge,
    )

    @Test
    fun `loadChapter projects text faithfully and keeps structure`() {
        val src = source()
        val content = src.loadChapter(0)

        assertEquals("第一\n这一段有推荐内容，需要清理。\n第三段正常文字。", content.text)
        // 结构保真：标题角色保留、两张图片不丢
        val texts = content.blocks.filterIsInstance<LayoutBlock.Text>()
        assertEquals(3, texts.size)
        assertEquals(BlockRole.HEADING, texts[0].paragraph.role)
        assertEquals(BlockRole.BODY, texts[1].paragraph.role)
        assertEquals(2, content.blocks.count { it is LayoutBlock.Image })
    }

    @Test
    fun `selection write-back maps display offsets to global source offsets`() {
        val src = source()
        val projection = src.projectionForChapter(0)!!
        // 用户在 display 中选中「推荐」[7,9)：回写必须落在 source 的「广告」[10,12)+基址
        assertEquals(chapterBase + 10, projection.localDisplayToGlobalSource(7))
        assertEquals(chapterBase + 12, projection.localDisplayToGlobalSource(9))
        // 章首/章末边界
        assertEquals(chapterBase, projection.localDisplayToGlobalSource(0))
        assertEquals(chapterBase + 31, projection.localDisplayToGlobalSource(26))
        // 越界 clamp 有界
        assertEquals(chapterBase, projection.localDisplayToGlobalSource(-5))
        assertEquals(chapterBase + 31, projection.localDisplayToGlobalSource(999))
    }

    @Test
    fun `persistent highlight maps source range into display range`() {
        val src = source()
        val projection = src.projectionForChapter(0)!!
        // 库中高亮是 source 口径（「广告」= 全书 [base+10, base+12)），渲染需转 display
        val displayStart = projection.globalSourceToLocalDisplay(chapterBase + 10)
        val displayEnd = projection.globalSourceToLocalDisplay(chapterBase + 12)
        assertEquals(7, displayStart)
        assertEquals(9, displayEnd)
        assertEquals("推荐", src.loadChapterText(0).substring(displayStart, displayEnd))
    }

    @Test
    fun `tts sentence offsets round trip between display and source`() {
        val src = source()
        val content = src.loadChapter(0)
        val projection = src.projectionForChapter(0)!!
        // TTS 朗读 display 文本，句偏移是 display 章内局部；跟读高亮回写 source 口径
        val sentences = splitSentencesWithOffsets(content.text)
        assertTrue(sentences.isNotEmpty())
        for ((_, start) in sentences) {
            val globalSource = projection.localDisplayToGlobalSource(start)
            // 往返：display 句首 → 全书 source → display，必须回到原句首（floor 语义）
            assertEquals(start, projection.globalSourceToLocalDisplay(globalSource))
        }
        // 具体句锚定：第二段句首 display=3 ↔ source=6（章标题缩短后仍精确对齐）
        assertEquals(chapterBase + 6, projection.localDisplayToGlobalSource(3))
        assertEquals(3, projection.globalSourceToLocalDisplay(chapterBase + 6))
    }

    @Test
    fun `block separator positions map identically and floor roundtrip holds`() {
        val src = source()
        val projection = src.projectionForChapter(0)!!
        // 块间 "\n" 分隔位：source 5 → display 2（正向精确）；
        // display 2 同时是「章标题」删除的坍缩点，toSource 按floor 契约回落到删除起点 source 2
        assertEquals(2, projection.globalSourceToLocalDisplay(chapterBase + 5))
        assertEquals(chapterBase + 2, projection.localDisplayToGlobalSource(2))
        // floor 往返契约与 LinearTextOffsetMap 一致
        for (s in 0..31) {
            val d = projection.globalSourceToLocalDisplay(chapterBase + s)
            assertTrue("toDisplay($s) 越界: $d", d in 0..26)
            assertTrue("roundtrip source $s", projection.localDisplayToGlobalSource(d) <= chapterBase + s)
        }
        for (d in 0..26) {
            val s = projection.localDisplayToGlobalSource(d) - chapterBase
            assertTrue("toSource($d) 越界: $s", s in 0..31)
            assertTrue("roundtrip display $d", projection.globalSourceToLocalDisplay(chapterBase + s) <= d)
        }
    }

    @Test
    fun `oversized chapter keeps original text with identity mapping and reports once`() {
        val reported = mutableListOf<BoundedReplaceResult.UnsupportedTooLarge>()
        val src = source(maxChars = 8, onUnsupportedTooLarge = reported::add)
        val content = src.loadChapter(0)

        // 超大章整章保留原文；projectionForChapter 返回 null（消费方走 base+local 恒等）
        assertEquals("第一章标题\n这一段有广告内容，需要净化处理。\n第三段正常文字。", content.text)
        assertNull(src.projectionForChapter(0))
        assertEquals(listOf(BoundedReplaceResult.UnsupportedTooLarge(31, 8)), reported)
        // 再次加载不重复上报
        src.loadChapter(0)
        assertEquals(1, reported.size)
    }

    @Test
    fun `chapter cache is bounded lru with three slots`() {
        val src = EpubReplacedChapterSource(
            delegate = EpubChapterSource(
                titles = List(5) { "第${it}章" },
                chapterStartOffsets = List(5) { it * 40 },
                totalChars = 200,
                loadBlocks = { index -> listOf(DocBlock.Text("第${index}章正文")) },
            ),
            bookId = "book-1",
            rules = listOf(replaceRule("正文", "文本")),
        )
        for (i in 0 until 4) src.loadChapter(i)
        assertTrue(src.inspectionCacheSize() <= 3)
        assertEquals("第3章文本", src.loadChapterText(3))
    }

    private fun replaceRule(pattern: String, replacement: String) = ReplaceRule(
        id = "replace-$pattern",
        name = "测试规则-$pattern",
        pattern = pattern,
        replacement = replacement,
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )
}
