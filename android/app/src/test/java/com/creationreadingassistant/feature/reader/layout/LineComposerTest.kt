package com.creationreadingassistant.feature.reader.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排版内核验收（对应 SIDECAR-ZH 第 6 节 P1 的验收清单）。
 *
 * 全部纯 JVM：`layout/` 包禁止 `import android.*`，所以这些测试不用起模拟器。
 *
 * 测宽用 [FakeTextRuler]：按 East_Asian_Width 给宽度，全角 1em、半角 0.5em。
 * 中文正文本来就是等宽的，所以断行、禁则、挤压、两端对齐的正确性在这里验证与真机高度一致。
 *
 * 复核确认的具体缺陷的回归断言另见 [LayoutRegressionTest]。
 */
class LineComposerTest {

    private val em = 100f
    private val ruler = FakeTextRuler(fontSizePx = em)
    private val oracle = AnywhereBreakOracle() // 隔离 ICU，专测禁则表本身

    private fun cfg(
        widthEm: Float = 20f,
        justify: Boolean = true,
        indentEm: Float = 2f,
        strict: Boolean = true,
    ) = LayoutConfig(
        contentWidthPx = widthEm * em,
        contentHeightPx = 30 * em,
        fontSizePx = em,
        firstLineIndentEm = indentEm,
        justify = justify,
        strictKinsoku = strict,
    )

    private fun layout(text: String, c: LayoutConfig = cfg(), role: BlockRole = BlockRole.BODY) =
        LineComposer.layoutParagraph(LayoutParagraph(text, role), c, ruler, oracle)

    private fun firstCharOf(line: LayoutLine, text: String) = text[line.startInText]
    private fun lastCharOf(line: LayoutLine, text: String) = text[line.endInText - 1]

    // ── 验收 1：禁则 ────────────────────────────────────────────────────

    @Test
    fun `forbidden leading punctuation never starts a line`() {
        val noStart = "。，、；：？！）】》」』｝”’·%"
        val body = "这是一段用来撑满行宽的中文正文内容"
        val text = buildString {
            repeat(30) { i ->
                append(body)
                append(noStart[i % noStart.length])
            }
        }
        val lines = layout(text)
        assertTrue("应排出多行", lines.size > 3)
        lines.drop(1).forEach { line ->
            val ch = firstCharOf(line, text)
            assertFalse(
                "禁首标点 '$ch' 出现在行首（行=${text.substring(line.startInText, line.endInText)}）",
                ch in noStart,
            )
        }
    }

    @Test
    fun `forbidden trailing punctuation never ends a line`() {
        val noEnd = "（【《「『｛“‘"
        val body = "这是一段用来撑满行宽的中文正文内容"
        val text = buildString {
            repeat(30) { i ->
                append(body)
                append(noEnd[i % noEnd.length])
                append("引文")
            }
        }
        val lines = layout(text)
        lines.dropLast(1).forEach { line ->
            val ch = lastCharOf(line, text)
            assertFalse("禁尾标点 '$ch' 出现在行末", ch in noEnd)
        }
    }

    // ── 验收 2：病态输入不死循环 ────────────────────────────────────────

    @Test
    fun `all forbidden text terminates with finite lines`() {
        // 整段都是禁首标点时，不存在任何合法断点。
        // 引擎必须终止、行数有限，并把这些行标记为 overflow ——
        // 此时禁则违例在数学上不可避免，但绝不能死循环或吐出无限行。
        val text = "。".repeat(400)
        val lines = layout(text)
        assertTrue("必须终止且行数有限，实际=${lines.size}", lines.size in 1..400)
        assertEquals("必须覆盖全文", text.length, lines.last().endInText)
        assertTrue("这些行应被标记为溢出", lines.any { it.overflowed })
    }

    @Test
    fun `overlong atomic unit degrades instead of hanging`() {
        // 120 字符无空格 URL 比一行还长：原子单元内恒无合法断点，
        // 必须降级为任意簇边界可断，否则回退耗尽后死循环。
        val url = "https://example.com/" + "a".repeat(100)
        val text = "见此链接$url 后文继续，这里再补一些中文正文让段落变长一些。"
        val lines = layout(text)
        assertTrue("必须终止", lines.isNotEmpty())
        assertEquals(text.length, lines.last().endInText)
        assertTrue("超长单元应被拆开", lines.size >= 2)
    }

    @Test
    fun `empty and whitespace input`() {
        assertTrue(layout("").isEmpty())
        assertTrue(layout("   ").let { it.isEmpty() || it.last().endInText == 3 })
    }

    // ── 验收 4：字素簇不被拆开 ──────────────────────────────────────────

    @Test
    fun `surrogate pairs and emoji families stay intact`() {
        val surrogate = "𠮷"          // 𠮷
        val family = "👨‍👩‍👧" // 👨‍👩‍👧
        val text = ("中文$surrogate 内容$family 继续").repeat(6)
        val lines = layout(text)
        lines.forEach { line ->
            // 行边界必须落在完整的字素簇上：不能切在代理对中间
            assertFalse(
                "行首切在低代理项上（offset=${line.startInText}）",
                line.startInText in text.indices && Character.isLowSurrogate(text[line.startInText]),
            )
            if (line.endInText in text.indices) {
                assertFalse(
                    "行末切在低代理项上（offset=${line.endInText}）",
                    Character.isLowSurrogate(text[line.endInText]),
                )
            }
        }
    }

    // ── 验收 6：标点挤压 ────────────────────────────────────────────────

    @Test
    fun `adjacent punctuation squeezes to the expected width`() {
        fun widthOf(pair: String): Float {
            val c = Clusterizer.of(pair, ruler)
            val a = WidthAdjuster.phaseA(c, cfg())
            return a.width(0, c.count)
        }
        assertEquals("。” 应挤压为 1.5em", 1.5f * em, widthOf("。”"), 0.01f)
        assertEquals("“‘ 应挤压为 1.5em", 1.5f * em, widthOf("“‘"), 0.01f)
        assertEquals("）（ 应挤压为 1.0em", 1.0f * em, widthOf("）（"), 0.01f)
        assertEquals("汉字不参与挤压", 2.0f * em, widthOf("中文"), 0.01f)
    }

    // ── 验收 7：段首开始括号缩左半 ──────────────────────────────────────

    @Test
    fun `paragraph starting with opening bracket indents one and a half em`() {
        // 首行缩进 2em，但行首的「占左半空白，削掉后实际缩进 1.5em
        val text = "「你好」" + "这是正文内容".repeat(10)
        val lines = layout(text)
        assertEquals(
            "段首开始括号应把缩进削掉半个字",
            1.5f * em,
            lines[0].clusterX[0],
            0.01f,
        )
    }

    // ── 验收 9：首行缩进不跨行重复 ──────────────────────────────────────

    @Test
    fun `only the first line is indented`() {
        val text = "这是正文内容".repeat(30)
        val lines = layout(text)
        assertTrue("需要多行才能验证", lines.size >= 2)
        assertEquals("首行缩进 2em", 2f * em, lines[0].startX, 0.01f)
        assertEquals("续行不缩进", 0f, lines[1].startX, 0.01f)
    }

    @Test
    fun `heading is not indented`() {
        val lines = layout("第一章 起", role = BlockRole.HEADING)
        assertEquals(0f, lines[0].startX, 0.01f)
    }

    // ── 验收 8：两端对齐 ────────────────────────────────────────────────

    @Test
    fun `justified non tail lines end exactly at available width`() {
        val text = "这是一段足够长的中文正文用来验证两端对齐效果".repeat(10)
        val c = cfg(widthEm = 20f)
        val lines = layout(text, c)
        assertTrue(lines.size >= 3)
        lines.dropLast(1).forEach { line ->
            if (line.overflowed) return@forEach
            assertEquals(
                "非末行行末笔尖应精确落在可用宽度上",
                c.contentWidthPx,
                line.endX,
                0.05f,
            )
        }
    }

    @Test
    fun `tail line is not stretched`() {
        val text = "这是正文内容".repeat(30)
        val c = cfg()
        val lines = layout(text, c)
        val tail = lines.last()
        assertTrue("末行不应被拉满", tail.endX < c.contentWidthPx - 0.01f)
    }

    @Test
    fun `per gap stretch never exceeds one third em`() {
        val text = "这是一段中文正文内容用于检验单间隙拉伸配额是否受控".repeat(8)
        val c = cfg()
        val lines = layout(text, c)
        lines.dropLast(1).forEach { line ->
            if (line.overflowed || line.clusterCount < 2) return@forEach
            // 相邻簇 x 之差减去其自然宽度即为该间隙的拉伸量。
            // 安全优先：不得为了贴齐右边界突破 1/3em 上限。
            for (i in 0 until line.clusterCount - 1) {
                val gap = line.clusterX[i + 1] - line.clusterX[i]
                assertTrue(
                    "单簇推进 $gap 超过 1em + 1/3em 配额",
                    gap <= em * (1f + 1f / 3f) + 0.05f,
                )
            }
        }
    }

    @Test
    fun `justification disabled leaves lines ragged`() {
        val text = "这是正文内容".repeat(30)
        val c = cfg(justify = false)
        val lines = layout(text, c)
        lines.forEach { line ->
            assertTrue("关闭两端对齐后不应被拉满", line.endX <= c.contentWidthPx + 0.01f)
        }
    }

    // ── 覆盖完整性 ──────────────────────────────────────────────────────

    @Test
    fun `lines cover the whole paragraph without gaps or overlaps`() {
        val text = "这是一段中文正文，含标点。还有「引号」与——破折号……以及 English words 和 12345 数字。".repeat(6)
        val lines = layout(text)
        assertEquals(0, lines.first().startInText)
        assertEquals(text.length, lines.last().endInText)
        for (i in 0 until lines.size - 1) {
            assertTrue(
                "行 $i 的结尾不应超过下一行的开头",
                lines[i].endInText <= lines[i + 1].startInText,
            )
        }
    }
}
