package com.creationreadingassistant.feature.reader.rules

import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.doc.TxtTocProfile
import java.security.MessageDigest

/**
 * 文本规则引擎：目录并集识别与替换应用。
 *
 * 保存前校验（[validateTocRule] / [validateReplaceRule]）保证：
 * 1. 正则可编译（INVALID_REGEX）；
 * 2. 不可匹配空串（EMPTY_MATCH，防止零宽命中失控）；
 * 3. 无嵌套量词等灾难性回溯风险（CATASTROPHIC_RISK）。
 */
object RuleEngine {

    // ── 保存前校验 ─────────────────────────────────────────────────────

    fun validateTocRule(pattern: String?): RuleValidationResult =
        if (pattern == null) RuleValidationResult.OK else validatePattern(pattern)

    fun validateReplaceRule(pattern: String, replacement: String): RuleValidationResult =
        validatePattern(pattern)

    internal fun validatePattern(pattern: String): RuleValidationResult {
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            return RuleValidationResult(false, listOf(RuleValidationError.INVALID_REGEX))
        }
        // 先判灾难性回溯：同时可空匹配的 `(a*)*` 类模式，回溯风险是更严重的拒绝理由。
        if (hasCatastrophicRisk(pattern)) {
            return RuleValidationResult(false, listOf(RuleValidationError.CATASTROPHIC_RISK))
        }
        if (regex.matches("")) {
            return RuleValidationResult(false, listOf(RuleValidationError.EMPTY_MATCH))
        }
        return RuleValidationResult.OK
    }

    // ── 目录并集识别与预览 ─────────────────────────────────────────────

    /**
     * 目录规则并集：标准内置模式恒参与，另取每个启用规则的模式
     * （内置具名规则来自 [TxtChapterDetector]，自定义规则取自身正则）。
     */
    fun tocPatterns(rules: List<TocRule>): List<Regex> {
        val enabled = rules.filter { it.enabled }
        val seedIds = enabled
            .filter { it.builtin && it.id != BuiltinTocRules.STANDARD_ID }
            .map { it.id }
        val base = TxtChapterDetector.unionPatterns(seedIds)
        val custom = enabled.filter { !it.builtin }.map { Regex(it.pattern!!) }
        return base + custom
    }

    /**
     * 多规则并集识别。只含内置规则时走 [TxtChapterDetector.detect] 的规则 id 重载
     * （density 保护语义与现状一致）；含自定义正则时按用户背书跳过整体作废。
     */
    fun detectToc(text: String, rules: List<TocRule>): List<TxtChapterDetector.Chapter> {
        val enabled = rules.filter { it.enabled }
        val hasCustom = enabled.any { !it.builtin }
        return if (hasCustom) {
            TxtChapterDetector.detect(text, tocPatterns(enabled), densityGuard = false)
        } else {
            TxtChapterDetector.detect(text, enabled.map { it.id })
        }
    }

    fun previewToc(text: String, rules: List<TocRule>, sampleLimit: Int = 5): TocPreviewResult {
        val enabled = rules.filter { it.enabled }
        val chapters = detectToc(text, enabled)
        return TocPreviewResult(
            ruleIds = enabled.map { it.id },
            chapterCount = chapters.size,
            sampleTitles = chapters.take(sampleLimit).map { it.title },
        )
    }

    // ── TxtTocProfile 构造 ─────────────────────────────────────────────

    /**
     * 从生效目录规则（effective TocRule）构造 [TxtTocProfile]：
     *
     * - patterns = 标准恒参与 + 启用规则的模式（复用 [tocPatterns] 的并集语义）；
     * - key = 参与规则（id/pattern/enabled/position）的稳定 fingerprint，
     *   规则集合 / 内容 / 顺序变化时 key 变化，旧扫描缓存自动失效；
     * - densityGuard = 纯标准（无用户宽松 / 自定义启用规则）时 true，否则 false。
     */
    fun tocProfile(rules: List<TocRule>): TxtTocProfile {
        val enabled = rules.filter { it.enabled }
        return TxtTocProfile(
            key = tocProfileKey(enabled),
            patterns = tocPatterns(enabled),
            densityGuard = enabled.all { it.id == BuiltinTocRules.STANDARD_ID },
        )
    }

    /**
     * 目录身份的稳定 fingerprint（P1-A）：
     *
     * - 仅标准内置 → 返回旧语义 id `builtin`（与旧 DataStore 单选身份一致）；
     * - 标准 + 恰好一个宽松内置 → 返回该规则语义 id（`num-dot` 等），
     *   保证旧快速单选入口 / 历史 txtTocRuleId 迁移后的扫描身份不漂移；
     * - 其余多规则组合 → 按 position / id 排序后逐条拼接 id、pattern
     *   （内置为 null 标记）、enabled、position 再取 SHA-256。同一配置在
     *   任何进程 / 构建下结果一致；规则集合 / 顺序 / 内容变化时 key 变化，
     *   旧扫描索引自动失效。
     */
    private fun tocProfileKey(rules: List<TocRule>): String {
        val nonStandardRules = rules.filter { it.id != BuiltinTocRules.STANDARD_ID }
        if (nonStandardRules.isEmpty()) return BuiltinTocRules.STANDARD_ID
        if (nonStandardRules.size == 1 && nonStandardRules[0].builtin) return nonStandardRules[0].id
        val canonical = buildString {
            append("toc:v1")
            for (rule in rules.sortedWith(compareBy({ it.position }, { it.id }))) {
                append('|').append(rule.id)
                append(':').append(rule.pattern ?: "<builtin>")
                append(':').append(if (rule.enabled) '1' else '0')
                append(':').append(rule.position)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return "toc-" + digest.joinToString("") { "%02x".format(it) }
    }

    // ── 替换应用与预览 ─────────────────────────────────────────────────

    /**
     * 按 [ReplaceRule.position] 升序逐条应用替换（跳过 disabled）。
     * 每条规则只对当前文本做一遍 find（不与自身级联），命中区间互不重叠。
     * 返回显示文本、串联的 display↔source 偏移映射与总命中数。
     *
     * 规则在保存前已经过校验；此处对可空匹配/无法编译的规则做防御性拦截。
     */
    fun applyReplace(text: String, rules: List<ReplaceRule>): ReplaceResult {
        val enabled = rules.filter { it.enabled }.sortedBy { it.position }
        if (enabled.isEmpty()) {
            return ReplaceResult(text, TextOffsetMap.identity(text.length), 0)
        }
        val maps = ArrayList<TextOffsetMap>(enabled.size)
        var current = text
        var totalHits = 0
        for (rule in enabled) {
            val regex = try {
                Regex(rule.pattern)
            } catch (e: Exception) {
                throw IllegalArgumentException("替换规则正则无法编译: ${rule.id}", e)
            }
            if (regex.matches("")) {
                throw IllegalArgumentException("替换规则可匹配空串: ${rule.id}")
            }
            val applied = applySingle(current, regex, rule.replacement)
            maps += applied.offsetMap
            current = applied.displayText
            totalHits += applied.hitCount
        }
        return ReplaceResult(current, ChainedTextOffsetMap(maps), totalHits)
    }

    fun previewReplace(text: String, rules: List<ReplaceRule>): ReplacePreviewResult {
        val result = applyReplace(text, rules)
        return ReplacePreviewResult(before = text, after = result.displayText, hitCount = result.hitCount)
    }

    /**
     * 单条规则应用：逐段输出未命中文本与替换文本，同时记录 (display, source)
     * 控制点，构造该规则的偏移映射。
     */
    private fun applySingle(text: String, regex: Regex, replacement: String): ReplaceResult {
        val matcher = regex.toPattern().matcher(text)
        val out = StringBuilder(text.length + replacement.length * 8)
        val points = ArrayList<Pair<Int, Int>>()
        var displayPos = 0
        var lastSource = 0
        var hitCount = 0
        while (matcher.find()) {
            val s0 = matcher.start()
            val s1 = matcher.end()
            if (s1 < s0) throw IllegalStateException("正则命中区间非法: $s0..$s1")
            val matchLen = s1 - s0

            out.append(text, lastSource, s0)
            displayPos += s0 - lastSource
            points += displayPos to s0 // 命中起点（前段保留文本终点）

            if (replacement.isNotEmpty()) {
                out.append(replacement)
            }
            val replacedLen = replacement.length
            if (replacedLen > 0) {
                val aligned = minOf(replacedLen, matchLen)
                points += (displayPos + aligned) to (s0 + aligned)
            }
            if (replacedLen != matchLen) {
                // 变长：插入尾部（水平段）或删除余量（竖直跳变）
                points += (displayPos + replacedLen) to s1
            }
            displayPos += replacedLen
            lastSource = s1
            hitCount++
        }
        out.append(text, lastSource, text.length)
        displayPos += text.length - lastSource
        points += displayPos to text.length
        return ReplaceResult(out.toString(), TextOffsetMap.of(points, displayPos, text.length), hitCount)
    }

    /**
     * 灾难性回溯启发式：量化符（* + ? {m,n}）紧跟在一个内部含量化符的分组之后。
     * 保守但只针对经典 `(a+)+` 形态，避免误伤 `(ab)+`、`(a|b)+` 这类安全写法。
     * 转义字符与字符类内的量词一律跳过。
     */
    internal fun hasCatastrophicRisk(pattern: String): Boolean {
        val n = pattern.length
        val groupOpens = ArrayDeque<Int>()
        var i = 0
        while (i < n) {
            when (val c = pattern[i]) {
                '\\' -> i++
                '[' -> {
                    i++
                    while (i < n && pattern[i] != ']') {
                        if (pattern[i] == '\\') i++
                        i++
                    }
                }
                '(' -> groupOpens.addLast(i)
                ')' -> {
                    val open = groupOpens.removeLastOrNull()
                    if (open != null && i + 1 < n && pattern[i + 1] in "*+?{") {
                        if (groupContainsQuantifier(pattern, open, i)) return true
                    }
                }
            }
            i++
        }
        return false
    }

    private fun groupContainsQuantifier(pattern: String, open: Int, close: Int): Boolean {
        var i = open + 1
        while (i < close) {
            when (pattern[i]) {
                '\\' -> i++
                '[' -> {
                    i++
                    while (i < close && pattern[i] != ']') {
                        if (pattern[i] == '\\') i++
                        i++
                    }
                }
                '*', '+', '?', '{' -> return true
            }
            i++
        }
        return false
    }
}
