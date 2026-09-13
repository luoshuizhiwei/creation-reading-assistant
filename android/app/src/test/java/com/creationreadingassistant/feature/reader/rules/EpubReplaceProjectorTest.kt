package com.creationreadingassistant.feature.reader.rules

import com.creationreadingassistant.feature.reader.doc.DocBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EPUB 结构保真替换投影器锁定：
 * - 块结构 / 图片 / 标题原样保留；
 * - 规则只在块内生效（跨块不命中，无重无漏）；
 * - 章级偏移映射双向 floor 往返一致（与分页宿主对接的坐标契约）。
 */
class EpubReplaceProjectorTest {

    private fun rule(pattern: String, replacement: String) = ReplaceRule(
        id = "r1", name = "r", pattern = pattern, replacement = replacement,
        enabled = true, position = 0, scope = RuleScope.GLOBAL,
    )

    @Test
    fun `block structure images and heading flags survive projection`() {
        val blocks = listOf(
            DocBlock.Text("第一章 标题", isHeading = true),
            DocBlock.Image("img/p1.jpg", 300, 200),
            DocBlock.Text("正文第一段", isHeading = false),
        )
        val result = EpubReplaceProjector.project(blocks, listOf(rule("正文", "净化后")), "book1")

        assertEquals(3, result.displayBlocks.size)
        val heading = result.displayBlocks[0] as DocBlock.Text
        assertTrue(heading.isHeading)
        val image = result.displayBlocks[1] as DocBlock.Image
        assertEquals("img/p1.jpg", image.path)
        assertEquals("净化后第一段", (result.displayBlocks[2] as DocBlock.Text).text)
        assertEquals(1, result.hitCount)
    }

    @Test
    fun `cross block matches never fire - each block projected independently`() {
        // 「第一段第二段」只可能跨块命中，块内投影不得产生任何替换
        val blocks = listOf(
            DocBlock.Text("甲 第一段", isHeading = false),
            DocBlock.Text("第二段 乙", isHeading = false),
        )
        val result = EpubReplaceProjector.project(
            blocks,
            listOf(rule("第一段第二段", "X"), rule("甲", "A"), rule("乙", "B")),
            "book1",
        )
        assertEquals("A 第一段", (result.displayBlocks[0] as DocBlock.Text).text)
        assertEquals("第二段 B", (result.displayBlocks[1] as DocBlock.Text).text)
        assertEquals(2, result.hitCount)
    }

    @Test
    fun `deletion keeps block identity and shrinks display text`() {
        val blocks = listOf(
            DocBlock.Text("广告开头的正文", isHeading = false),
            DocBlock.Text("正常段落", isHeading = false),
        )
        val result = EpubReplaceProjector.project(blocks, listOf(rule("广告", "")), "book1")
        assertEquals("开头的正文", (result.displayBlocks[0] as DocBlock.Text).text)
        assertEquals("正常段落", (result.displayBlocks[1] as DocBlock.Text).text)
    }

    @Test
    fun `chapter map round trips through separators`() {
        // 块内替换使 display 短于 source；章级映射需跨分隔位对齐
        val blocks = listOf(
            DocBlock.Text("AAAA广告BB", isHeading = false),
            DocBlock.Text("CCCC", isHeading = false),
            DocBlock.Text("DDDD", isHeading = false),
        )
        val result = EpubReplaceProjector.project(blocks, listOf(rule("广告", "")), "book1")
        val map = result.chapterOffsetMap()

        // 章内 source 口径：块按 "\n" 连接 → source 长度 = 8 + 1 + 4 + 1 + 4 = 18
        assertEquals(18, map.sourceLength)
        // display = 6 + 1 + 4 + 1 + 4 = 16
        assertEquals(16, map.displayLength)

        // source→display：第 0 块内 0..5 恒等，6..7（BB）平移 -2，分隔位与后续块平移 -2
        assertEquals(0, map.toDisplay(0))
        assertEquals(4, map.toDisplay(4))
        assertEquals(6, map.toDisplay(8)) // 块 0 末尾
        assertEquals(7, map.toDisplay(9)) // 分隔位（floor 到段尾 6 + 分隔位 1）
        assertEquals(8, map.toDisplay(10)) // 块 1 首字符
        assertEquals(15, map.toDisplay(17)) // 章尾

        // display→source：坍缩点（display 4）floor 回删除起点；display 末点回到 source 末点
        assertEquals(4, map.toSource(4))
        assertEquals(8, map.toSource(6)) // 删除区间 [4,8) 坍缩为 display 点 4，末点 6 ↔ source 8
        assertEquals(10, map.toSource(8))
        assertEquals(18, map.toSource(16))

        // round trip floor 不变量
        for (s in 0..18) {
            val d = map.toDisplay(s)
            assertTrue("roundtrip s=$s", map.toSource(d) <= s)
        }
    }

    @Test
    fun `no rules pass through is rejected by contract but identity map is exact`() {
        // 投影器要求非空规则；恒等投影（超限块）映射必须逐点精确
        val longText = "字".repeat(EpubReplaceProjector.DEFAULT_MAX_BLOCK_CHARS + 1)
        val blocks = listOf(DocBlock.Text(longText, isHeading = false))
        val result = EpubReplaceProjector.project(blocks, listOf(rule("字", "Z")), "book1")
        val map = result.chapterOffsetMap()
        assertEquals(longText.length, map.sourceLength)
        assertEquals(longText.length, map.displayLength)
        assertEquals((result.displayBlocks[0] as DocBlock.Text).text, longText)
    }

    // ── E2 单处纠错（R4）：块内锚定 + 跨块诚实跳过 ──────────────────────

    private fun correctionRule(
        id: String,
        sourceStart: Int,
        sourceEnd: Int,
        findText: String,
        replaceText: String,
    ) = ReplaceRule(
        id = id, name = "单处纠错", pattern = "", replacement = replaceText,
        enabled = true, position = Int.MAX_VALUE, scope = RuleScope.PER_BOOK,
        anchor = CorrectionAnchor(sourceStart, sourceEnd, findText),
    )

    @Test
    fun `anchor localized by chapter base applies inside a single block`() {
        // 章内块序：block0 = 4 chars，分隔 1，block1 = 4 chars。
        // 锚点为全书坐标：anchorScopeBase=100 → block1 全局基址 = 105，
        // block1 章内 [1,3)「字段」对应全书 [106,108)。
        val blocks = listOf(
            DocBlock.Text("第一段落", isHeading = false),
            DocBlock.Text("错字段落", isHeading = false),
        )
        val result = EpubReplaceProjector.project(
            blocks,
            listOf(correctionRule("correction:c1", sourceStart = 106, sourceEnd = 108, findText = "字段", replaceText = "字段正")),
            "book1",
            anchorScopeBase = 100,
        )
        // block1 章内 [1,3)「字段」命中并替换
        assertEquals("第一段落", (result.displayBlocks[0] as DocBlock.Text).text)
        assertEquals("错字段正落", (result.displayBlocks[1] as DocBlock.Text).text)
        assertEquals(1, result.hitCount)
        // 章级映射闭合
        val map = result.chapterOffsetMap()
        assertEquals(map.displayLength, map.toDisplay(map.sourceLength))
        for (s in 0..map.sourceLength) {
            assertTrue("roundtrip s=$s", map.toSource(map.toDisplay(s)) <= s)
        }
    }

    @Test
    fun `anchor spanning block boundary is skipped honestly`() {
        // 「段第二段」跨块，锚定区间覆盖块间分隔位 → 无法完整落入单块 → 跳过
        val blocks = listOf(
            DocBlock.Text("甲 第一段", isHeading = false),
            DocBlock.Text("第二段 乙", isHeading = false),
        )
        val result = EpubReplaceProjector.project(
            blocks,
            listOf(correctionRule("correction:c1", sourceStart = 0, sourceEnd = 9, findText = "甲 第一段第二段", replaceText = "X")),
            "book1",
            anchorScopeBase = 0,
        )
        assertEquals("甲 第一段", (result.displayBlocks[0] as DocBlock.Text).text)
        assertEquals("第二段 乙", (result.displayBlocks[1] as DocBlock.Text).text)
        assertEquals(0, result.hitCount)
    }

    @Test
    fun `anchor outside chapter range never applies`() {
        val blocks = listOf(DocBlock.Text("正文内容", isHeading = false))
        val result = EpubReplaceProjector.project(
            blocks,
            listOf(correctionRule("correction:c1", sourceStart = 500, sourceEnd = 502, findText = "内容", replaceText = "X")),
            "book1",
            anchorScopeBase = 0,
        )
        assertEquals("正文内容", (result.displayBlocks[0] as DocBlock.Text).text)
        assertEquals(0, result.hitCount)
    }
}
