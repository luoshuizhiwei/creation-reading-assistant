package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RuleValidationError
import com.creationreadingassistant.feature.reader.rules.TocRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则编辑器纯逻辑：草稿转命令、校验/预览（含错误防护）、上下移动排序、节选与反馈文案。
 * 与 Compose 无关，全部可在 JVM 上跑。
 */
class RuleEditorDraftTest {

    private val previewText = "第一章 起点\n第二章 转折\n第三章 高潮\n正文内容 foo foo bar"

    // ── toCommand ────────────────────────────────────────────────────────

    @Test
    fun `new toc draft converts to SaveCustomToc without id`() {
        val command = RuleEditorDraft(
            kind = RuleKind.TOC,
            name = " 自定义目录 ",
            pattern = "第[0-9]+章",
        ).toCommand()
        assertEquals(
            RuleCommand.SaveCustomToc(
                id = null,
                name = "自定义目录",
                pattern = "第[0-9]+章",
                scope = RuleScope.PER_BOOK,
                enabled = true,
            ),
            command,
        )
    }

    @Test
    fun `replace draft converts to SaveCustomReplace with replacement and scope`() {
        val command = RuleEditorDraft(
            kind = RuleKind.REPLACE,
            name = "净化广告",
            pattern = "广告",
            replacement = "",
            scope = RuleScope.GLOBAL,
        ).toCommand()
        assertEquals(
            RuleCommand.SaveCustomReplace(
                id = null,
                name = "净化广告",
                pattern = "广告",
                replacement = "",
                scope = RuleScope.GLOBAL,
                enabled = true,
            ),
            command,
        )
    }

    @Test
    fun `editing draft keeps original id`() {
        val command = RuleEditorDraft(
            kind = RuleKind.TOC,
            id = "custom-toc-1",
            name = "规则",
            pattern = "第[0-9]+章",
        ).toCommand()
        assertEquals("custom-toc-1", (command as RuleCommand.SaveCustomToc).id)
    }

    @Test
    fun `blank name or pattern cannot convert`() {
        assertNull(RuleEditorDraft(kind = RuleKind.TOC, name = "   ", pattern = "第[0-9]+章").toCommand())
        assertNull(RuleEditorDraft(kind = RuleKind.TOC, name = "规则", pattern = "  ").toCommand())
    }

    // ── evaluateDraft：TOC 预览 ─────────────────────────────────────────

    @Test
    fun `valid toc draft previews with effective rules`() {
        val existing = TocRule(
            id = "custom-toc-0",
            name = "已有",
            pattern = "第[0-9]+章",
            builtin = false,
            enabled = true,
            scope = RuleScope.PER_BOOK,
            position = 10,
        )
        val check = evaluateDraft(
            draft = RuleEditorDraft(kind = RuleKind.TOC, name = "新增", pattern = "第[0-9]+章"),
            effectiveToc = listOf(existing),
            effectiveReplace = emptyList(),
            previewText = previewText,
        )
        assertTrue(check is RuleDraftCheck.Valid)
        val preview = (check as RuleDraftCheck.Valid).preview as RuleDraftPreview.Toc
        assertEquals(3, preview.result.chapterCount)
        assertEquals("第一章 起点", preview.result.sampleTitles.first())
    }

    // ── evaluateDraft：错误防护（绝不能崩溃）────────────────────────────

    @Test
    fun `blank pattern invalid regex empty match and catastrophic risk are rejected`() {
        assertErrors("  ", setOf(RuleValidationError.INVALID_REGEX))
        assertErrors("(abc", setOf(RuleValidationError.INVALID_REGEX))
        assertErrors("a*", setOf(RuleValidationError.EMPTY_MATCH))
        assertErrors("(a+)+", setOf(RuleValidationError.CATASTROPHIC_RISK))
    }

    private fun assertErrors(pattern: String, expected: Set<RuleValidationError>) {
        val check = evaluateDraft(
            draft = RuleEditorDraft(kind = RuleKind.REPLACE, name = "r", pattern = pattern),
            effectiveToc = emptyList(),
            effectiveReplace = emptyList(),
            previewText = previewText,
        )
        assertTrue(check is RuleDraftCheck.Invalid)
        assertEquals(expected, (check as RuleDraftCheck.Invalid).errors.toSet())
    }

    // ── evaluateDraft：REPLACE 预览 ─────────────────────────────────────

    @Test
    fun `valid replace draft previews hit count and transformed text`() {
        val check = evaluateDraft(
            draft = RuleEditorDraft(kind = RuleKind.REPLACE, name = "r", pattern = "foo", replacement = "bar"),
            effectiveToc = emptyList(),
            effectiveReplace = emptyList(),
            previewText = "foo foo bar",
        )
        assertTrue(check is RuleDraftCheck.Valid)
        val result = (check as RuleDraftCheck.Valid).preview as RuleDraftPreview.Replace
        assertEquals(2, result.result.hitCount)
        assertEquals("bar bar bar", result.result.after)
    }

    @Test
    fun `replace preview applies draft after existing effective rules`() {
        val existing = ReplaceRule(
            id = "custom-replace-0",
            name = "旧",
            pattern = "foo",
            replacement = "X",
            enabled = true,
            position = 1,
            scope = RuleScope.GLOBAL,
        )
        val check = evaluateDraft(
            draft = RuleEditorDraft(kind = RuleKind.REPLACE, name = "新", pattern = "bar", replacement = "Y"),
            effectiveToc = emptyList(),
            effectiveReplace = listOf(existing),
            previewText = "foo bar",
        )
        assertTrue(check is RuleDraftCheck.Valid)
        val result = (check as RuleDraftCheck.Valid).preview as RuleDraftPreview.Replace
        assertEquals("X Y", result.result.after)
        assertEquals(2, result.result.hitCount)
    }

    // ── 排序 ────────────────────────────────────────────────────────────

    @Test
    fun `move custom rule swaps with neighbor only within bounds`() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveCustomRule(ids, 0, 1))
        assertEquals(listOf("a", "c", "b"), moveCustomRule(ids, 1, 1))
        assertEquals(listOf("b", "a", "c"), moveCustomRule(ids, 1, -1))
        assertNull(moveCustomRule(ids, 0, -1))
        assertNull(moveCustomRule(ids, 2, 1))
        assertNull(moveCustomRule(ids, 3, 1))
        assertNull(moveCustomRule(ids, -1, 1))
    }

    @Test
    fun `reorder command carries kind and full custom id order`() {
        assertEquals(
            RuleCommand.ReorderRules(RuleKind.REPLACE, listOf("b", "a", "c")),
            reorderCommand(RuleKind.REPLACE, listOf("a", "b", "c"), 0, 1),
        )
        assertNull(reorderCommand(RuleKind.TOC, listOf("a"), 0, -1))
    }

    // ── 节选与反馈文案 ───────────────────────────────────────────────────

    @Test
    fun `replace excerpt centers on first hit and ellipsizes`() {
        val excerpt = replaceExcerpts("0123456789foo0123456789", "0123456789bar0123456789", "foo", radius = 4)
        assertTrue(excerpt.before.startsWith("…"))
        assertTrue(excerpt.before.endsWith("…"))
        assertTrue(excerpt.before.contains("foo"))
        assertTrue(excerpt.after.contains("bar"))
    }

    @Test
    fun `replace excerpt without hit shows head`() {
        val excerpt = replaceExcerpts("hello world", "hello world", "xyz", radius = 40)
        assertEquals("hello world", excerpt.before)
        assertEquals("hello world", excerpt.after)
    }

    @Test
    fun `mutation feedback covers success saved not found and validation errors`() {
        assertEquals("操作成功", RuleMutationResult.Success.feedbackText())
        assertEquals("已保存", RuleMutationResult.Saved("custom-toc-1").feedbackText())
        assertEquals("规则不存在或不属于当前书", RuleMutationResult.NotFound.feedbackText())
        assertEquals(
            "保存失败：正则表达式无效，无法编译",
            RuleMutationResult.Rejected(listOf(RuleValidationError.INVALID_REGEX)).feedbackText(),
        )
        assertEquals("保存失败：规则冲突", RuleMutationResult.Rejected(emptyList()).feedbackText())
        assertEquals("已迁移目录规则", RuleMutationResult.Migrated("num-dot", "num-dot").feedbackText())
    }

    @Test
    fun `validation error messages are non blank chinese`() {
        for (error in RuleValidationError.entries) {
            assertTrue(error.userMessage().isNotBlank())
        }
    }
}
