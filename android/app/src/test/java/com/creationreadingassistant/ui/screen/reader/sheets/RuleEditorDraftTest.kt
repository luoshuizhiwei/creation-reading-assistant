package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RuleValidationError
import com.creationreadingassistant.feature.reader.rules.TocRule
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability
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
    fun `blank pattern cannot convert`() {
        assertNull(RuleEditorDraft(kind = RuleKind.TOC, name = "规则", pattern = "  ").toCommand())
    }

    @Test
    fun `blank name falls back to pattern as rule name`() {
        val command = RuleEditorDraft(
            kind = RuleKind.REPLACE,
            name = "",
            pattern = "广告",
        ).toCommand()
        assertEquals(
            RuleCommand.SaveCustomReplace(
                id = null,
                name = "广告",
                pattern = "广告",
                replacement = "",
                scope = RuleScope.PER_BOOK,
                enabled = true,
            ),
            command,
        )
    }

    @Test
    fun `blank name trims whitespace-only input before fallback`() {
        val command = RuleEditorDraft(
            kind = RuleKind.TOC,
            name = "   ",
            pattern = "第[0-9]+章",
        ).toCommand()
        assertEquals("第[0-9]+章", (command as RuleCommand.SaveCustomToc).name)
    }

    @Test
    fun `selection replacement draft treats selected text as a literal per-book pattern`() {
        val draft = selectionReplaceDraft("  价格 (1+1) = 2?  ")!!

        assertEquals(RuleKind.REPLACE, draft.kind)
        assertEquals("替换选中文字", draft.name)
        assertEquals(Regex.escape("价格 (1+1) = 2?"), draft.pattern)
        assertEquals(RuleScope.PER_BOOK, draft.scope)
        assertNull(selectionReplaceDraft("  \n  "))
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

    // ── E1 普通/高级双模式 ───────────────────────────────────────────────

    @Test
    fun `simple mode derives escaped pattern from plain find text`() {
        val draft = RuleEditorDraft(kind = RuleKind.REPLACE, name = "r", replacement = "价格")
            .withSimpleFind("价格 (1+1)")
        assertEquals(Regex.escape("价格 (1+1)"), draft.resolvedPattern())
        // 保存为规则时同样使用转义派生
        val command = draft.toCommand()
        assertTrue(command is RuleCommand.SaveCustomReplace)
        assertEquals(Regex.escape("价格 (1+1)"), (command as RuleCommand.SaveCustomReplace).pattern)
    }

    @Test
    fun `advanced mode keeps raw pattern untouched`() {
        val draft = RuleEditorDraft(
            kind = RuleKind.REPLACE,
            name = "r",
            pattern = "第[0-9]+章",
            simpleFind = "",
            advanced = true,
        )
        assertEquals("第[0-9]+章", draft.resolvedPattern())
    }

    @Test
    fun `simple mode with blank find text cannot save`() {
        assertNull(
            RuleEditorDraft(kind = RuleKind.REPLACE, name = "r", simpleFind = "  ").toCommand(),
        )
    }

    @Test
    fun `unescape only accepts pure literal escape output`() {
        // 字面量规则（Regex.escape 产物）能回到普通模式
        val literal = "价格 (1+1) = 2?"
        assertEquals(literal, unescapeRegexEscapeOrNull(Regex.escape(literal)))
        // 手写正则（含 \d 等元语法）拒绝回到普通模式
        assertNull(unescapeRegexEscapeOrNull("第\\d+章"))
        assertNull(unescapeRegexEscapeOrNull(""))
    }

    @Test
    fun `editing existing literal rule reopens in simple mode`() {
        val literal = "广告插入"
        val draft = replaceDraft(
            ReplaceRule(
                id = "custom-replace-1",
                name = "广告",
                pattern = Regex.escape(literal),
                replacement = "",
                enabled = true,
                position = 1,
                scope = RuleScope.PER_BOOK,
            ),
        )
        assertTrue(!draft.advanced)
        assertEquals(literal, draft.simpleFind)

        val regexDraft = replaceDraft(
            ReplaceRule(
                id = "custom-replace-2",
                name = "数字",
                pattern = "第\\d+章",
                replacement = "",
                enabled = true,
                position = 2,
                scope = RuleScope.PER_BOOK,
            ),
        )
        assertTrue(regexDraft.advanced)
        assertEquals("", regexDraft.simpleFind)
    }

    @Test
    fun `simple mode evaluateDraft validates through escaped pattern`() {
        // 普通模式用户输入「a*」这样的文本时按字面量处理：转义后合法且只匹配字面 a*
        val check = evaluateDraft(
            draft = RuleEditorDraft(kind = RuleKind.REPLACE, name = "r", replacement = "X")
                .withSimpleFind("a*"),
            effectiveToc = emptyList(),
            effectiveReplace = emptyList(),
            previewText = "value a* end",
        )
        assertTrue(check is RuleDraftCheck.Valid)
        val result = (check as RuleDraftCheck.Valid).preview as RuleDraftPreview.Replace
        assertEquals(1, result.result.hitCount)
        assertEquals("value X end", result.result.after)
    }

    // ── E2 单处纠错 ─────────────────────────────────────────────────────

    @Test
    fun `selection draft defaults to single correction target`() {
        val draft = selectionReplaceDraft("选中错字")!!
        assertTrue(draft.saveAsCorrection)
        assertEquals("选中错字", draft.simpleFind)
    }

    @Test
    fun `correction draft converts to SaveSingleCorrection without coordinates`() {
        val command = RuleEditorDraft(
            kind = RuleKind.REPLACE,
            simpleFind = "选中错字",
            replacement = "改正字",
            saveAsCorrection = true,
        ).toCommand()
        assertEquals(
            RuleCommand.SaveSingleCorrection(
                sourceStart = -1,
                sourceEnd = -1,
                findText = "选中错字",
                replaceText = "改正字",
            ),
            command,
        )
    }

    @Test
    fun `correction draft with blank find cannot convert`() {
        assertNull(
            RuleEditorDraft(
                kind = RuleKind.REPLACE,
                simpleFind = " ",
                replacement = "x",
                saveAsCorrection = true,
            ).toCommand(),
        )
    }

    @Test
    fun `correction draft previews find to replace pair with single hit`() {
        val check = evaluateDraft(
            draft = RuleEditorDraft(
                kind = RuleKind.REPLACE,
                simpleFind = "选中错字",
                replacement = "改正字",
                saveAsCorrection = true,
            ),
            effectiveToc = emptyList(),
            effectiveReplace = emptyList(),
            previewText = previewText,
        )
        assertTrue(check is RuleDraftCheck.Valid)
        val result = (check as RuleDraftCheck.Valid).preview as RuleDraftPreview.Replace
        assertEquals("选中错字", result.result.before)
        assertEquals("改正字", result.result.after)
        assertEquals(1, result.result.hitCount)
    }

    // ── R4 应用反馈分离 ─────────────────────────────────────────────────

    @Test
    fun `saved feedback separates save success from body application status`() {
        val capability = ReaderReplacementCapability.Available()
        assertEquals(
            "已保存；正文将重新分页并应用",
            RuleMutationResult.Saved("custom-replace-1").feedbackText(capability),
        )
        // 单处纠错的保存反馈
        assertEquals(
            "已保存单处纠错；正文将重新分页并应用",
            RuleMutationResult.Saved("corr-1").feedbackText(capability),
        )
        // 降级状态：保存成功 ≠ 正文已应用
        val degraded = ReaderReplacementCapability.Available(bodyNotice = "部分章节保留原文")
        assertEquals(
            "已保存；部分章节保留原文",
            RuleMutationResult.Saved("custom-replace-1").feedbackText(degraded),
        )
        val unavailable = ReaderReplacementCapability.Unavailable("正文将保留原文")
        assertEquals(
            "已保存；正文将保留原文",
            RuleMutationResult.Saved("custom-replace-1").feedbackText(unavailable),
        )
        // 无 capability 时保持旧行为
        assertEquals("已保存", RuleMutationResult.Saved("custom-replace-1").feedbackText())
    }

    @Test
    fun `not anchorable feedback surfaces reason as error`() {
        val result = RuleMutationResult.NotAnchorable("当前没有可用的选区位置")
        assertEquals("当前没有可用的选区位置", result.feedbackText())
    }
}
