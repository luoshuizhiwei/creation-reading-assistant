package com.creationreadingassistant.feature.reader.rules

/**
 * 规则作用域：GLOBAL 对全部书生效；PER_BOOK 需经绑定表逐书挂接。
 */
enum class RuleScope { GLOBAL, PER_BOOK }

/**
 * 规则种类。目录规则（TOC）与替换规则（REPLACE）共用一张持久化表，用该字段区分。
 */
enum class RuleKind { TOC, REPLACE }

/**
 * 目录识别规则。
 *
 * - 内置规则（[builtin] == true）由代码 seed（见 [BuiltinTocRules]），不允许删除；
 *   [pattern] 为 null，模式定义在 [TxtChapterDetector] 内。
 * - 自定义规则 [pattern] 为单条正则，保存前经 [RuleEngine.validateTocRule] 校验。
 * - 标准内置模式（第X章/卷X/序章/Chapter N）恒参与并集，不在此列表里重复出现。
 */
data class TocRule(
    val id: String,
    val name: String,
    val pattern: String?,
    val builtin: Boolean,
    val enabled: Boolean,
    val scope: RuleScope,
    val position: Int,
)

/**
 * 替换规则：按 [position] 升序应用；[replacement] 为空表示删除命中文本。
 * 保存前经 [RuleEngine.validateReplaceRule] 校验（可编译、非空匹配、无灾难性回溯）。
 */
data class ReplaceRule(
    val id: String,
    val name: String,
    val pattern: String,
    val replacement: String,
    val enabled: Boolean,
    val position: Int,
    val scope: RuleScope,
)

/** 规则保存前校验失败的原因。 */
enum class RuleValidationError {
    /** 正则无法编译。 */
    INVALID_REGEX,

    /** 正则可匹配空串（替换/目录规则都会造成零宽命中失控）。 */
    EMPTY_MATCH,

    /** 嵌套量词等灾难性回溯风险。 */
    CATASTROPHIC_RISK,

    /** 内置规则不允许增删改（保存时命中内置 id）。 */
    IMMUTABLE_BUILTIN,

    /** id 与规则集合冲突：命中其他书/其他种类的自定义规则，或保留的绑定前缀。 */
    ID_CONFLICT,
}

/** 规则保存前校验结果；[valid] 为 false 时规则不得落库。 */
data class RuleValidationResult(
    val valid: Boolean,
    val errors: List<RuleValidationError>,
) {
    companion object {
        val OK = RuleValidationResult(true, emptyList())
    }
}

/** 目录预览：参与规则、命中章节数与抽样标题。 */
data class TocPreviewResult(
    val ruleIds: List<String>,
    val chapterCount: Int,
    val sampleTitles: List<String>,
)

/** 替换预览：应用前/后文本与总命中数。 */
data class ReplacePreviewResult(
    val before: String,
    val after: String,
    val hitCount: Int,
)

/** 替换应用结果：显示文本、display↔source 双向偏移映射与总命中数。 */
data class ReplaceResult(
    val displayText: String,
    val offsetMap: TextOffsetMap,
    val hitCount: Int,
)
