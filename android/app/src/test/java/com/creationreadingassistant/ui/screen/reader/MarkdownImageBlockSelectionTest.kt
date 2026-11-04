package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderContent
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderKind
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderSpan
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F5 D-1：**图片块不得成为「整块选择」的载体**（真机验收 B.2 契约违反）。
 *
 * 真机现象（`docs/qa/2026-09-21-s07-qoder-device-acceptance.md` §8 B.2）：
 * 在滚动 Markdown 里轻点图片块 → 摘录显示 **alt 文本 `示意图`**，工具栏弹出，
 * 还能保存成红色高亮并在重开后回显（`51/52/53-*` 三张截图）。
 *
 * 根因（本文件先用真实 parser 钉住事实，再钉修复后的契约）：
 * 图片在 render model 里**不是独立 kind**，而是 `MarkdownRenderSpan.Image`，
 * 嵌在 `MarkdownRenderContent.Paragraph` 里；parser 把 alt 文本写进了
 * `chapter.canonicalText`（见 `MarkdownDocument.kt:425` 的 `is MdInline.Image -> alt`），
 * 于是 `unitText()` 原样切片就得到 `示意图`。而 `markdownUnitIsBlockSelectable`
 * 只看 `unit.kind`，`PARAGRAPH` 一律放行 —— 纯图片段落因此被当成普通文本段落。
 *
 * 本文件钉三类事实：
 * 1. **模型事实**：纯图片段落 / 混合段落的 kind、spans、canonicalRange 究竟长什么样；
 * 2. **契约**：纯图片段落不可整块选择、不产出可保存选区；
 * 3. **回归护栏**：普通文本段落、代码块、标题等原有可选择性不得回退，
 *    分隔线 / 整表仍不可选。
 *
 * 全部为纯 JVM 行为测试（真实 `MarkdownParser` + 真实 `MarkdownRenderModel`），
 * 不依赖 Compose，也不使用文本搜索 / alt 猜测。
 */
class MarkdownImageBlockSelectionTest {

    private fun unitsOf(source: String): List<MarkdownRenderUnit> =
        MarkdownRenderModel.flatten(MarkdownParser.parse(source))

    private fun textOf(source: String, unit: MarkdownRenderUnit): String {
        val canonical = MarkdownParser.parse(source).canonicalText
        return canonical.substring(unit.canonicalRange.first, unit.canonicalRange.last + 1)
    }

    private fun paragraphSpans(unit: MarkdownRenderUnit): List<MarkdownRenderSpan> =
        when (val content = unit.content) {
            is MarkdownRenderContent.Paragraph -> content.spans
            is MarkdownRenderContent.Heading -> content.spans
            is MarkdownRenderContent.ListItem -> content.spans
            is MarkdownRenderContent.TaskItem -> content.spans
            else -> emptyList()
        }

    // ── 1. 模型事实：纯图片段落的真实形状 ──────────────────────────────

    @Test
    fun `a pure image paragraph is a paragraph whose only leaf span is an image`() {
        // 真机夹具原文（t-md-s07.md）里的独立一行图片。
        val src = "## 第二节 重复文字\n\n重复段落：山那边的钟声每天黄昏响三次。\n\n![示意图](t-img-s07.png)\n\n---\n"

        val imageUnit = unitsOf(src).single { unit ->
            paragraphSpans(unit).any { it is MarkdownRenderSpan.Image }
        }

        assertEquals("图片块在模型里仍是 PARAGRAPH（没有独立 kind）", MarkdownRenderKind.PARAGRAPH, imageUnit.kind)
        val leafSpans = paragraphSpans(imageUnit).filter { it is MarkdownRenderSpan.Image }
        assertEquals("纯图片段落只有一个叶 span", 1, leafSpans.size)
        assertEquals(
            "该段的可见文本就是 alt 文本 —— 这正是伪选区的来源",
            "示意图",
            textOf(src, imageUnit),
        )
    }

    @Test
    fun `the alt text really does enter the canonical text`() {
        // 这是 D-1 的机制：parser 把 alt 写进 canonicalText，unitText() 再原样切片。
        // 记录该事实，避免后来者以为「只要 UI 不画就没事」。
        val src = "![示意图](t-img-s07.png)\n"
        val chapter = MarkdownParser.parse(src)

        assertEquals("示意图", chapter.canonicalText)
    }

    @Test
    fun `a mixed paragraph exposes both plain and image leaf spans`() {
        val src = "前面文字 ![图](a.png) 后面文字。\n"

        val unit = unitsOf(src).single()
        val spans = paragraphSpans(unit)
        val imageSpan = spans.filterIsInstance<MarkdownRenderSpan.Image>().single()
        val plainSpans = spans.filterIsInstance<MarkdownRenderSpan.Plain>()

        assertEquals("混合段落里图片只是一个 span", MarkdownRenderKind.PARAGRAPH, unit.kind)
        assertEquals(2, plainSpans.size)
        assertTrue("图片 span 夹在两段文字之间", imageSpan.start > plainSpans.first().start)
        assertTrue(imageSpan.end < plainSpans.last().end)
        // 关键：整块 canonicalRange 覆盖了图片 alt，因此整块区间**不**是纯正文区间。
        assertTrue(
            "整块范围包含图片 alt 区间 ⇒ 整块选区会连带选中 alt",
            unit.canonicalRange.first <= imageSpan.start && imageSpan.end <= unit.canonicalRange.last + 1,
        )
    }

    // ── 2. 契约：图片块不可整块选择（D-1 修复目标） ─────────────────────

    @Test
    fun `a pure image paragraph is not block selectable`() {
        val src = "段落。\n\n![示意图](t-img-s07.png)\n"
        val imageUnit = unitsOf(src).single { unit ->
            paragraphSpans(unit).any { it is MarkdownRenderSpan.Image }
        }

        assertFalse(
            "纯图片段落不得作为整块选择的载体（D-1）",
            markdownUnitIsBlockSelectable(imageUnit),
        )
    }

    @Test
    fun `a pure image paragraph never produces a saveable selection range`() {
        val src = "段落。\n\n![示意图](t-img-s07.png)\n"
        val imageUnit = unitsOf(src).single { unit ->
            paragraphSpans(unit).any { it is MarkdownRenderSpan.Image }
        }

        assertNull(
            "纯图片段落不得产出 canonical 选区范围（否则会落库伪高亮）",
            markdownUnitCanonicalRange(imageUnit, blockGlobalBase = 0),
        )
    }

    @Test
    fun `a pure image paragraph never produces block selection args`() {
        // 生产唯一入口：返回 null ⇒ 不产生 selectedText、不产生 rangeStart/sourceEnd，
        // 因而不可能保存高亮 / 批注 / 灵感 locator。
        val src = "段落。\n\n![示意图](t-img-s07.png)\n"
        val imageUnit = unitsOf(src).single { unit ->
            paragraphSpans(unit).any { it is MarkdownRenderSpan.Image }
        }

        val args = markdownBlockSelectionArgs(
            text = textOf(src, imageUnit),
            unit = imageUnit,
            blockGlobalBase = 0,
        )

        assertNull("纯图片段落不得产出任何选区参数（含 alt 文本）", args)
    }

    @Test
    fun `an image with a link style alt and title is still not selectable`() {
        // alt 含空白与标点、带 title 的写法也必须被挡住（不能靠 alt 文本形状判断）。
        val src = "![图 1：示意，含标点](t.png \"标题\")\n"
        val unit = unitsOf(src).single()

        assertFalse(markdownUnitIsBlockSelectable(unit))
        assertNull(markdownUnitCanonicalRange(unit, blockGlobalBase = 0))
        assertNull(markdownBlockSelectionArgs(textOf(src, unit), unit, blockGlobalBase = 0))
    }

    @Test
    fun `an empty alt image paragraph is not selectable either`() {
        val src = "![](t.png)\n"
        val unit = unitsOf(src).single()

        assertFalse("空 alt 同样不得成为选区载体", markdownUnitIsBlockSelectable(unit))
    }

    // ── 3. 混合段落：安全契约（宁可整块不可选） ───────────────────────

    @Test
    fun `a paragraph mixing text and an image is not block selectable`() {
        // 安全契约：当前整块坐标无法排除图片 alt 区间（alt 是 canonicalRange 的一部分，
        // 而 canonical 区间必须连续），因此**整个混合块不可选**。
        // 明确拒绝「用文本搜索 / alt 猜测 / 不连续区间伪装成连续 canonical range」。
        val src = "前面文字 ![图](a.png) 后面文字。\n"
        val unit = unitsOf(src).single()

        assertFalse(
            "文字+图片混合段落不得整块选择（整块区间包含 alt，无法安全排除）",
            markdownUnitIsBlockSelectable(unit),
        )
        assertNull(markdownUnitCanonicalRange(unit, blockGlobalBase = 0))
        assertNull(markdownBlockSelectionArgs(textOf(src, unit), unit, blockGlobalBase = 0))
    }

    @Test
    fun `the mixed paragraph contract is not implemented by guessing at alt text`() {
        // 反证：即使 alt 恰好等于段落里的某段正文，也不得因此放行。
        val src = "重复段落：山那边的钟声 ![重复段落：山那边的钟声](a.png)\n"
        val unit = unitsOf(src).single()

        assertFalse(
            "不得因 alt 文本与正文相同就判定可整块选择",
            markdownUnitIsBlockSelectable(unit),
        )
    }

    // ── 4. 不得回退：真实文本载体仍可选 ────────────────────────────────

    @Test
    fun `a plain text paragraph is still selectable`() {
        val src = "这是普通正文段落，没有图片。\n"
        val unit = unitsOf(src).single()

        assertTrue("普通文本段落必须仍可整块选择", markdownUnitIsBlockSelectable(unit))
        val range = requireNotNull(markdownUnitCanonicalRange(unit, blockGlobalBase = 0))
        assertEquals(0, range.first)
        assertEquals(src.trimEnd().length, range.last + 1)
    }

    @Test
    fun `a paragraph with only inline emphasis or links stays selectable`() {
        // 强调 / 链接是**文本**载体，不是图片；不得被 D-1 的修复连带挡住。
        listOf(
            "**加粗**文本\n",
            "*斜体*文本\n",
            "[链接文字](https://example.com)\n",
            "~~删除线~~文本\n",
        ).forEach { src ->
            val unit = unitsOf(src).single()
            assertTrue("$src 必须仍可整块选择", markdownUnitIsBlockSelectable(unit))
            assertNotNull(markdownUnitCanonicalRange(unit, blockGlobalBase = 0))
        }
    }

    @Test
    fun `headings list items and task items stay selectable`() {
        // 分开取，避免同一列表里 task/plain 混排时 parser 把两者都归为 TASK_ITEM。
        listOf(
            MarkdownRenderKind.HEADING to "# 标题\n",
            MarkdownRenderKind.LIST_ITEM to "- 列表项\n",
            MarkdownRenderKind.TASK_ITEM to "- [x] 任务项\n",
        ).forEach { (kind, src) ->
            val unit = unitsOf(src).single { it.kind == kind }
            assertTrue("$kind 必须仍可整块选择", markdownUnitIsBlockSelectable(unit))
            assertNotNull(markdownUnitCanonicalRange(unit, blockGlobalBase = 0))
        }
    }

    @Test
    fun `code blocks keep their existing selectable contract`() {
        val src = "```kotlin\nval x = 1\n```\n"
        val code = unitsOf(src).single { it.kind == MarkdownRenderKind.CODE_BLOCK }

        assertTrue("代码块现有契约不得回退（整块可选）", markdownUnitIsBlockSelectable(code))
        val range = requireNotNull(markdownUnitCanonicalRange(code, blockGlobalBase = 0))
        assertTrue(range.last >= range.first)
    }

    @Test
    fun `horizontal rules and whole tables stay unselectable`() {
        val src = "段落\n\n---\n\n| 甲 | 乙 |\n| --- | --- |\n| 丙 | 丁 |\n"
        val units = unitsOf(src)

        val rule = units.single { it.kind == MarkdownRenderKind.HORIZONTAL_RULE }
        val table = units.single { it.kind == MarkdownRenderKind.TABLE }

        assertFalse("分隔线不得可选", markdownUnitIsBlockSelectable(rule))
        assertNull(markdownUnitCanonicalRange(rule, blockGlobalBase = 0))
        assertFalse("整表不得可选", markdownUnitIsBlockSelectable(table))
        assertNull(markdownUnitCanonicalRange(table, blockGlobalBase = 0))
    }

    // ── 5. 持久高亮投影：图片块不得成为高亮落点 ────────────────────────

    @Test
    fun `the persistent highlight projection skips image only paragraphs`() {
        // D-1 的第二半：伪高亮会持久化。投影层同样以 markdownUnitIsBlockSelectable
        // 为准（MarkdownScrollHighlight.kt 的 else 分支），因此修复后
        // 即使库里**已经**存在那条伪记录，也不会再被投影回图片块。
        val src = "正文段落。\n\n![示意图](t-img-s07.png)\n"
        val chapter = MarkdownParser.parse(src)
        val units = MarkdownRenderModel.flatten(chapter)
        val imageUnit = units.single { unit ->
            paragraphSpans(unit).any { it is MarkdownRenderSpan.Image }
        }

        // 伪记录：起点落在图片块的 canonicalRange 上（真机上就是这样落库的）。
        val fakeHighlight = MarkdownPersistentHighlights.SavedHighlight(
            bookKey = "book-md",
            start = imageUnit.canonicalRange.first,
            length = imageUnit.canonicalRange.last + 1 - imageUnit.canonicalRange.first,
            colorKey = "red",
        )

        val projected = MarkdownPersistentHighlights.projectPersistentHighlights(
            units = units,
            canonicalText = chapter.canonicalText,
            highlights = listOf(fakeHighlight),
            currentBookKey = "book-md",
            chapterSpan = 0 until chapter.canonicalText.length,
            blockGlobalBase = 0,
        )

        assertFalse(
            "图片块不得作为持久高亮的落点（否则伪高亮会一直回显）",
            projected.containsKey(imageUnit.id),
        )
    }

    @Test
    fun `a highlight on a real text paragraph still projects after the fix`() {
        // 反向护栏：修复不得把正常的持久高亮投影一起关掉。
        val src = "正文段落一。\n\n正文段落二。\n"
        val chapter = MarkdownParser.parse(src)
        val units = MarkdownRenderModel.flatten(chapter)
        val target = units.first { unit -> textOf(src, unit) == "正文段落二。" }

        val highlight = MarkdownPersistentHighlights.SavedHighlight(
            bookKey = "book-md",
            start = target.canonicalRange.first,
            length = 2,
            colorKey = "yellow",
        )

        val projected = MarkdownPersistentHighlights.projectPersistentHighlights(
            units = units,
            canonicalText = chapter.canonicalText,
            highlights = listOf(highlight),
            currentBookKey = "book-md",
            chapterSpan = 0 until chapter.canonicalText.length,
            blockGlobalBase = 0,
        )

        assertTrue("正常文本段落的持久高亮必须仍能投影", projected.containsKey(target.id))
    }
}
