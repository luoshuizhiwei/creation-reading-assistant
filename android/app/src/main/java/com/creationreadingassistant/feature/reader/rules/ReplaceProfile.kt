package com.creationreadingassistant.feature.reader.rules

import java.security.MessageDigest

/**
 * 替换净化规则集的稳定执行身份（slice 1）。
 *
 * [key] 是「有序生效规则」的确定性指纹：
 * - 生效 = [ReplaceRule.enabled] == true；规则集合 / 启停变化必然改变 key；
 * - 顺序与 [RuleEngine.applyReplace] 完全一致：按 [ReplaceRule.position] 稳定升序
 *   （同 position 保持传入相对顺序），因此「影响应用顺序的字段变化」必然改变 key，
 *   而「不影响应用顺序的纯列表乱序」（position 互不相同）保持 key 不变；
 * - key 覆盖 bookId 与每条生效规则的 id / pattern / replacement / enabled /
 *   position / scope；显示名 [ReplaceRule.name] 不影响执行语义，刻意忽略；
 * - 空生效规则返回稳定 identity key（[EMPTY_KEY]，与 bookId 无关），可用于
 *   无净化时的共享缓存身份。
 *
 * 隐私：key 是 SHA-256 摘要，不包含任何规则原文、bookId 或 id 原文；禁止把
 * 规则内容或 key 写入日志。
 *
 * 说明：当前 [ReplaceRule] 模型不携带 bookId（任务前提与此不符），bookId 作为
 * 显式参数传入，调用方必须传持久化的书身份，禁止用空串代替。
 */
data class ReplaceProfile(
    /** 持久化书身份；参与 key，保证跨书缓存身份不串。 */
    val bookId: String,
    /** 规则列表：可传完整列表或生效列表，内部按 enabled 过滤后镜像引擎排序。 */
    val rules: List<ReplaceRule>,
) {
    /** 稳定执行身份；同输入任意次调用结果一致。 */
    val key: String get() = key(bookId, rules)

    companion object {
        /** key 的明确版本前缀；语义变更时递增版本号使旧 key 全部失效。 */
        const val VERSION_PREFIX = "replace-v1"

        /** 空生效规则的稳定 identity key（无净化时全书可共享）。 */
        const val EMPTY_KEY = "$VERSION_PREFIX-empty"

        /**
         * 从生效规则构造稳定 key。规则顺序语义与 [RuleEngine.applyReplace]
         * 完全一致：过滤 enabled 后按 position 稳定升序（同 position 保留
         * 传入顺序），逐条以长度前缀编码全部执行语义字段再取 SHA-256。
         */
        fun key(bookId: String, rules: List<ReplaceRule>): String {
            val effective = rules.filter { it.enabled }.sortedBy { it.position }
            if (effective.isEmpty()) return EMPTY_KEY

            val canonical = buildString {
                append(VERSION_PREFIX).append('|')
                appendField("bookId", bookId)
                for (rule in effective) {
                    append('[')
                    appendField("id", rule.id)
                    appendField("pattern", rule.pattern)
                    appendField("replacement", rule.replacement)
                    appendField("enabled", "1")
                    appendField("position", rule.position.toString())
                    appendField("scope", rule.scope.name)
                    append(']')
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
            return "$VERSION_PREFIX-" + digest.joinToString("") { "%02x".format(it) }
        }

        /** 长度前缀编码，避免字段内容中的分隔符造成歧义。 */
        private fun StringBuilder.appendField(name: String, value: String) {
            append(name).append(':').append(value.length).append(':').append(value).append('|')
        }
    }
}
