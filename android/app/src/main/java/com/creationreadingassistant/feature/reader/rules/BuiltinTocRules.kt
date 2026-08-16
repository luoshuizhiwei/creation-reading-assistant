package com.creationreadingassistant.feature.reader.rules

/**
 * 内置目录规则 seed 目录（代码内定义，不落库，不允许删除）。
 *
 * - 「标准」（[STANDARD_ID]）：GLOBAL 恒参与并集，不可删除/停用；
 *   其模式定义在 TxtChapterDetector 的标准四类里。
 * - 具名宽松规则：PER_BOOK，默认启用但未绑定任何书，逐书绑定后参与该书并集；
 *   不可删除，模式不可改。
 *
 * 命名与显示名对齐 `TxtChapterDetector.rules`，保证旧 DataStore 逐书 ruleId 可直接映射。
 */
object BuiltinTocRules {
    const val STANDARD_ID = "builtin"

    val seeds: List<TocRule> = listOf(
        TocRule(STANDARD_ID, "标准", null, builtin = true, enabled = true, scope = RuleScope.GLOBAL, position = 0),
        TocRule("num-dot", "数字+标点", null, builtin = true, enabled = true, scope = RuleScope.PER_BOOK, position = 1),
        TocRule("num-bare", "纯数字", null, builtin = true, enabled = true, scope = RuleScope.PER_BOOK, position = 2),
        TocRule("cn-num-dot", "中文数字+顿号", null, builtin = true, enabled = true, scope = RuleScope.PER_BOOK, position = 3),
        TocRule("bracketed", "数字括号", null, builtin = true, enabled = true, scope = RuleScope.PER_BOOK, position = 4),
        TocRule("en-extended", "英文扩展", null, builtin = true, enabled = true, scope = RuleScope.PER_BOOK, position = 5),
        TocRule("md-heading", "Markdown 标题", null, builtin = true, enabled = true, scope = RuleScope.PER_BOOK, position = 6),
    )

    val byId: Map<String, TocRule> = seeds.associateBy { it.id }

    fun isSeed(id: String): Boolean = byId.containsKey(id)
}
