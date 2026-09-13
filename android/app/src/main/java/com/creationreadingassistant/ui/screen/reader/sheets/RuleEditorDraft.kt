package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.feature.reader.rules.CorrectionRecord
import com.creationreadingassistant.feature.reader.rules.ReplacePreviewResult
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleEngine
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RuleValidationError
import com.creationreadingassistant.feature.reader.rules.TocPreviewResult
import com.creationreadingassistant.feature.reader.rules.TocRule
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability

// ─────────────────────────────────────────────────────────────────────────────
// 纯 JVM 可测的编辑/校验/预览逻辑（TDD seam）
// ─────────────────────────────────────────────────────────────────────────────

/** 编辑器草稿：新增/编辑自定义规则时的表单状态。编辑时 [id] 携带原规则 id。 */
internal data class RuleEditorDraft(
    val kind: RuleKind,
    val id: String? = null,
    val name: String = "",
    /** 高级（正则）模式下的模式串；普通模式下由 [simpleFind] 转义派生。 */
    val pattern: String = "",
    val replacement: String = "",
    val scope: RuleScope = RuleScope.PER_BOOK,
    val enabled: Boolean = true,
    /** E1 普通纠错模式：按原文精确匹配（自动转义），用户无需接触正则语法。 */
    val simpleFind: String = "",
    val advanced: Boolean = false,
    /** E2 单处纠错目标：保存为只作用于选区位置的纠错，而非本书/全局规则。 */
    val saveAsCorrection: Boolean = false,
)

/**
 * REPLACE 草稿实际参与校验/保存的正则。
 *
 * [RuleEditorDraft.pattern] 是**单一事实源**；[RuleEditorDraft.simpleFind] 只是
 * 普通模式的 UI 镜像（编辑时经 [withSimpleFind] 同步转义进 pattern），
 * 因此本函数恒等于 pattern，仅供调用点语义自文档化。
 */
internal fun RuleEditorDraft.resolvedPattern(): String = pattern

/** 普通模式编辑「查找文本」：同步把字面量转义为 [RuleEditorDraft.pattern]。 */
internal fun RuleEditorDraft.withSimpleFind(text: String): RuleEditorDraft = copy(
    simpleFind = text,
    pattern = if (text.isBlank()) "" else Regex.escape(text),
)

/**
 * 尝试把 [Regex.escape] 的输出还原为普通文本。
 *
 * Kotlin [Regex.escape] 等价于 `Pattern.quote`：输出形如 `\Q…\E`，内容里的字面
 * `\E` 会写成 `\E\\E\Q`。只有当剥壳还原后逐字重转义与原串一致时才接受
 * （保证只有「纯字面量」规则能回到普通模式），否则返回 null。
 */
internal fun unescapeRegexEscapeOrNull(pattern: String): String? {
    if (!pattern.startsWith("\\Q") || !pattern.endsWith("\\E") || pattern.length < 4) return null
    val inner = pattern.substring(2, pattern.length - 2)
    val text = inner.replace("\\E\\\\E\\Q", "\\E")
    return if (Regex.escape(text) == pattern) text else null
}

internal fun tocDraft(rule: TocRule): RuleEditorDraft = RuleEditorDraft(
    kind = RuleKind.TOC,
    id = rule.id,
    name = rule.name,
    pattern = rule.pattern.orEmpty(),
    scope = rule.scope,
    enabled = rule.enabled,
    advanced = true,
)

internal fun replaceDraft(rule: ReplaceRule): RuleEditorDraft {
    val literal = unescapeRegexEscapeOrNull(rule.pattern)
    return RuleEditorDraft(
        kind = RuleKind.REPLACE,
        id = rule.id,
        name = rule.name,
        pattern = rule.pattern,
        replacement = rule.replacement,
        scope = rule.scope,
        enabled = rule.enabled,
        simpleFind = literal ?: "",
        advanced = literal == null,
    )
}

/**
 * Build a safe per-book replacement draft from a text selection. The selection is a literal, not a
 * regular expression supplied by the user, so quote every regex metacharacter before preview/save.
 * E2：选区入口默认保存为**单处纠错**（只改这一处），用户可在目标选择器切换为本书/全局规则。
 */
internal fun selectionReplaceDraft(selectedText: String): RuleEditorDraft? {
    val literal = selectedText.trim().takeIf { it.isNotEmpty() } ?: return null
    return RuleEditorDraft(
        kind = RuleKind.REPLACE,
        name = "替换选中文字",
        pattern = Regex.escape(literal),
        simpleFind = literal,
        scope = RuleScope.PER_BOOK,
        saveAsCorrection = true,
    )
}

/**
 * 草稿 → 保存命令：TOC 转 [RuleCommand.SaveCustomToc]，REPLACE 转
 * [RuleCommand.SaveCustomReplace]（E2：勾选单处纠错时转 [RuleCommand.SaveSingleCorrection]，
 * source 锚点由 ReaderViewModel 从当前选区回查填充）；
 * 编辑时携带原 [id]。正则/查找文本为空时返回 null（UI 应禁用保存）；
 * 名称留空时以正则表达式兜底，避免「保存按钮置灰却无解释」的静默失败。
 */
internal fun RuleEditorDraft.toCommand(): RuleCommand? {
    if (kind == RuleKind.REPLACE && saveAsCorrection) {
        if (simpleFind.isBlank()) return null
        return RuleCommand.SaveSingleCorrection(
            sourceStart = -1,
            sourceEnd = -1,
            findText = simpleFind,
            replaceText = replacement,
        )
    }
    if (pattern.isBlank()) return null
    val resolvedName = name.trim().ifBlank { pattern.trim() }
    return when (kind) {
        RuleKind.TOC -> RuleCommand.SaveCustomToc(
            id = id,
            name = resolvedName,
            pattern = pattern,
            scope = scope,
            enabled = enabled,
        )
        RuleKind.REPLACE -> RuleCommand.SaveCustomReplace(
            id = id,
            name = resolvedName,
            pattern = pattern,
            replacement = replacement,
            scope = scope,
            enabled = enabled,
        )
    }
}

/** 草稿校验/预览结果：校验不通过时只给错误枚举，绝不把未校验模式交给引擎。 */
internal sealed interface RuleDraftCheck {
    data class Valid(val preview: RuleDraftPreview) : RuleDraftCheck
    data class Invalid(val errors: List<RuleValidationError>) : RuleDraftCheck
}

/** 预览结果：TOC 命中数 + 抽样标题；REPLACE 命中数 + 前后全文。 */
internal sealed interface RuleDraftPreview {
    data class Toc(val result: TocPreviewResult) : RuleDraftPreview
    data class Replace(val result: ReplacePreviewResult) : RuleDraftPreview
}

/**
 * 对草稿做保存前校验并生成预览：TOC 用「当前有效 TOC + 草稿」，REPLACE 用「当前有效替换 + 草稿」。
 * 正则为空/无效/可空匹配/灾难性回溯时返回 [RuleDraftCheck.Invalid]，不进入引擎，避免崩溃。
 *
 * E2 单处纠错草稿不走全书预览：纠错只作用于选区锚点，预览直接给出
 * 「查找文本 → 替换文本」前后对照（hitCount 固定为 1 = 这一处）。
 */
internal fun evaluateDraft(
    draft: RuleEditorDraft,
    effectiveToc: List<TocRule>,
    effectiveReplace: List<ReplaceRule>,
    previewText: String,
): RuleDraftCheck {
    if (draft.kind == RuleKind.REPLACE && draft.saveAsCorrection) {
        val find = draft.simpleFind
        if (find.isBlank()) {
            return RuleDraftCheck.Invalid(listOf(RuleValidationError.INVALID_REGEX))
        }
        return RuleDraftCheck.Valid(
            RuleDraftPreview.Replace(
                ReplacePreviewResult(before = find, after = draft.replacement, hitCount = 1),
            ),
        )
    }
    if (draft.pattern.isBlank()) {
        return RuleDraftCheck.Invalid(listOf(RuleValidationError.INVALID_REGEX))
    }
    val validation = when (draft.kind) {
        RuleKind.TOC -> RuleEngine.validateTocRule(draft.pattern)
        RuleKind.REPLACE -> RuleEngine.validateReplaceRule(draft.pattern, draft.replacement)
    }
    if (!validation.valid) return RuleDraftCheck.Invalid(validation.errors)
    return when (draft.kind) {
        RuleKind.TOC -> RuleDraftCheck.Valid(
            RuleDraftPreview.Toc(RuleEngine.previewToc(previewText, effectiveToc + draft.toTocRule())),
        )
        RuleKind.REPLACE -> RuleDraftCheck.Valid(
            RuleDraftPreview.Replace(RuleEngine.previewReplace(previewText, effectiveReplace + draft.toReplaceRule())),
        )
    }
}

internal fun RuleEditorDraft.toTocRule(): TocRule = TocRule(
    id = id ?: DRAFT_TOC_ID,
    name = name.trim(),
    pattern = resolvedPattern(),
    builtin = false,
    enabled = true,
    scope = scope,
    position = Int.MAX_VALUE,
)

internal fun RuleEditorDraft.toReplaceRule(): ReplaceRule = ReplaceRule(
    id = id ?: DRAFT_REPLACE_ID,
    name = name.trim(),
    pattern = resolvedPattern(),
    replacement = replacement,
    enabled = true,
    position = Int.MAX_VALUE,
    scope = scope,
)

private const val DRAFT_TOC_ID = "__draft_toc__"
private const val DRAFT_REPLACE_ID = "__draft_replace__"

/** 把自定义规则 id 列表中的 [index] 项移动 [delta] 步；越界返回 null（UI 应禁用按钮）。 */
internal fun moveCustomRule(ids: List<String>, index: Int, delta: Int): List<String>? {
    val target = index + delta
    if (index !in ids.indices || target !in ids.indices) return null
    val result = ids.toMutableList()
    val moved = result.removeAt(index)
    result.add(target, moved)
    return result
}

/** 上移/下移后的 [RuleCommand.ReorderRules]：携带当前书可管理自定义规则全集的新顺序。 */
internal fun reorderCommand(
    kind: RuleKind,
    customIds: List<String>,
    index: Int,
    delta: Int,
): RuleCommand.ReorderRules? = moveCustomRule(customIds, index, delta)?.let {
    RuleCommand.ReorderRules(kind, it)
}

/** 校验错误的中文文案（编辑器预览区与保存反馈共用）。 */
internal fun RuleValidationError.userMessage(): String = when (this) {
    RuleValidationError.INVALID_REGEX -> "正则表达式无效，无法编译"
    RuleValidationError.EMPTY_MATCH -> "正则可能匹配空串，请调整模式"
    RuleValidationError.CATASTROPHIC_RISK -> "正则存在灾难性回溯风险，请简化嵌套量词"
    RuleValidationError.IMMUTABLE_BUILTIN -> "内置规则不可修改"
    RuleValidationError.ID_CONFLICT -> "规则 ID 冲突，无法保存"
}

/**
 * 最近一次 mutation 的简短反馈文案。
 *
 * R4 应用反馈分离：[capability] 非空且保存成功时，反馈行在「已保存」之外追加
 * **正文应用状态**子句（来自真实能力裁决），避免把「保存成功」误读成「正文已生效」。
 */
internal fun RuleMutationResult.feedbackText(
    capability: ReaderReplacementCapability? = null,
): String {
    val base = when (this) {
        RuleMutationResult.Success -> "操作成功"
        is RuleMutationResult.Saved ->
            if (id.startsWith("corr-")) "已保存单处纠错" else "已保存"
        RuleMutationResult.NotFound -> "规则不存在或不属于当前书"
        is RuleMutationResult.NotAnchorable -> reason
        is RuleMutationResult.Rejected ->
            if (errors.isEmpty()) "保存失败：规则冲突"
            else "保存失败：" + errors.joinToString("；") { it.userMessage() }
        is RuleMutationResult.Migrated -> "已迁移目录规则"
    }
    if (this !is RuleMutationResult.Saved || capability == null) return base
    return base + when (capability) {
        is ReaderReplacementCapability.Available ->
            capability.bodyNotice?.let { "；$it" } ?: "；正文将重新分页并应用"
        is ReaderReplacementCapability.Unavailable -> "；${capability.message}"
    }
}

/** 替换预览的前后文本节选：以首个命中位置为中心截取；无命中时取文本开头。 */
internal data class ReplaceExcerpt(val before: String, val after: String)

internal fun replaceExcerpts(
    before: String,
    after: String,
    pattern: String,
    radius: Int = 40,
): ReplaceExcerpt {
    val center = try {
        Regex(pattern).find(before)?.range?.first ?: 0
    } catch (e: Exception) {
        0
    }
    fun excerpt(text: String): String {
        if (text.isEmpty()) return ""
        val start = (center - radius).coerceIn(0, text.length)
        val end = (center + radius).coerceIn(start, text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end) + suffix
    }
    return ReplaceExcerpt(excerpt(before), excerpt(after))
}

internal fun RuleScope.ruleScopeLabel(): String = when (this) {
    RuleScope.PER_BOOK -> "本书"
    RuleScope.GLOBAL -> "全局"
}
