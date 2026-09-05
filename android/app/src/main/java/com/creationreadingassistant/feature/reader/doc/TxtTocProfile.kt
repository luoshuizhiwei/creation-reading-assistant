package com.creationreadingassistant.feature.reader.doc

/**
 * TXT 目录识别的可执行规则快照：模式并集 + density 开关 + 稳定 key。
 *
 * 由规则包 [com.creationreadingassistant.feature.reader.rules.RuleEngine] 从生效目录规则
 * 构造，或由旧 ruleId API（[fromRuleId]）直接构造。[key] 是参与规则
 * （id/pattern/enabled/position）的稳定 fingerprint，作为扫描结果的
 * detectedRuleId 落库 / 进缓存指纹——规则变化时 key 变化，旧索引自动失效。
 */
class TxtTocProfile(
    val key: String,
    val patterns: List<Regex>,
    val densityGuard: Boolean,
) {
    /** 标题是否命中模式并集。调用方负责 trim 与长度上限（约定同 [TxtChapterDetector]）。 */
    fun matches(title: String): Boolean = patterns.any { it.matches(title) }

    companion object {
        /**
         * 旧 ruleId 构造：标准四类恒参与，另取 [ruleId] 对应具名规则的模式。
         * density 语义与旧 API 完全一致——只有标准（builtin）启用
         * 「平均章节过短整体作废」，用户手选 / 未知 ruleId 一律视为背书、关闭兜底。
         * builtin 的 key 带版本戳（"builtin:s2"）：识别语义升级（新平台规则 /
         * 自动嗅探）后必须递增版本，旧磁盘索引与分页缓存才能失效重建。
         */
        fun fromRuleId(ruleId: String): TxtTocProfile = TxtTocProfile(
            key = if (ruleId == "builtin") "builtin:s2" else ruleId,
            patterns = TxtChapterDetector.unionPatterns(listOf(ruleId)),
            densityGuard = ruleId == "builtin",
        )
    }
}
