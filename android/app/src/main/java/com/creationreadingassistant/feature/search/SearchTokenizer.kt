package com.creationreadingassistant.feature.search

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 纯函数中文文本 Bigram/单字混合分词器。
 *
 * 设计原则（P2-FTS5 MVP）：
 *  - 零依赖，不引 HanLP / ICU4J / jieba；运行时仅一个对象 + CharArray 滑动窗。
 *  - 中文/日文汉字走 Bigram（相邻两字合一个 token，召回率比单字高）；
 *  - 英文/数字走 Unicode 字母数字连续段作为单个 token（保留 "第123章" 里的 123、"HTTP/2"
 *    里的 HTTP 和 2 这种完整 token，避免切成 Bigram 后搜不到整词）；
 *  - 标点、空白、控制字符跳过，不作为 token 一部分；
 *  - 生成的 token 全部小写（英文），查询侧同规则保证匹配。
 *
 * 典型结果：
 *   "第3章 这是一本中文小说。Hello v2.0!"
 *   → ["第3", "3章", "这是", "是一", "一本", "本中", "中文", "文小", "小说",
 *      "hello", "v", "2", "0"]
 *
 * 后续可升级：
 *   - 加停用 Bigram 过滤（如"的是"、"了一"之类出现频率过高无意义对）；
 *   - 加 Trigram（三字段）在短语匹配时做二次加权排序。
 */
@Singleton
class SearchTokenizer @Inject constructor() {

    /** 聚合后的词项统计：同一 term 在单篇文档里出现多少次 + 在原文的起始偏移列表。 */
    data class TokenHit(
        val term: String,
        val count: Int,
        val offsets: List<Int>,
    )

    /**
     * 文档级分词：返回 term → (出现次数, 偏移列表) 的聚合。
     *
     * MVP 策略：offsets 最多记录 1000 处，超过截断只累计 count，避免 offsets 列表
     * 爆长（一本 20 万字书里的"的是"这种 Bigram 可出现万次级）；调用方在 CSV 序列化时
     * 也会再做一次 16KB 字符串截断，双保险。
     *
     * 长文档优化：一次性分词时用 chars[] 滑窗，同步填 termCount + termOffsets
     * 两个 HashMap，最后转 List<TokenHit>。单遍 O(N)。
     */
    fun tokenizeDocument(input: String): List<TokenHit> {
        if (input.isEmpty()) return emptyList()
        val chars = input.toCharArray()
        val n = chars.size
        val termCount = LinkedHashMap<String, Int>(n.coerceAtMost(2048))
        val termOffsets = LinkedHashMap<String, ArrayList<Int>>(n.coerceAtMost(2048))
        var i = 0
        val latinRun = StringBuilder(16)
        var latinStart = -1
        fun flushLatin() {
            if (latinRun.isNotEmpty()) {
                val t = latinRun.toString()
                termCount[t] = (termCount[t] ?: 0) + 1
                val offs = termOffsets.getOrPut(t) { ArrayList(4) }
                if (offs.size < 1000) offs.add(latinStart)
                latinRun.clear()
                latinStart = -1
            }
        }
        while (i < n) {
            val c = chars[i]
            val code = c.code
            when {
                code in 0x30..0x39 ||
                    (code or 0x20) in 0x61..0x7A ||
                    code == 0x5F || code == 0x27
                -> {
                    if (latinStart < 0) latinStart = i
                    latinRun.append(if (code in 0x41..0x5A) c.lowercaseChar() else c)
                }
                isCjkTokenChar(code) -> {
                    flushLatin()
                    if (i + 1 < n && isCjkTokenChar(chars[i + 1].code)) {
                        val bi = String(chars, i, 2)
                        termCount[bi] = (termCount[bi] ?: 0) + 1
                        val offs = termOffsets.getOrPut(bi) { ArrayList(4) }
                        if (offs.size < 1000) offs.add(i)
                    } else {
                        val s = c.toString()
                        termCount[s] = (termCount[s] ?: 0) + 1
                        val offs = termOffsets.getOrPut(s) { ArrayList(4) }
                        if (offs.size < 1000) offs.add(i)
                    }
                }
                else -> flushLatin()
            }
            i++
        }
        flushLatin()
        if (termCount.isEmpty()) return emptyList()
        val out = ArrayList<TokenHit>(termCount.size)
        for ((term, count) in termCount) {
            val offs = termOffsets[term].orEmpty()
            out.add(TokenHit(term = term, count = count, offsets = offs))
        }
        return out
    }

    /**
     * 分词。返回 token 列表（不含空格/标点），保证无空字符串项，顺序与原文一致。
     * 英文字母小写化。
     */
    fun tokenize(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        val out = ArrayList<String>(input.length.coerceAtMost(256))
        val chars = input.toCharArray()
        val n = chars.size
        var i = 0
        val latinRun = StringBuilder(16)
        fun flushLatin() {
            if (latinRun.isNotEmpty()) {
                out.add(latinRun.toString())
                latinRun.clear()
            }
        }
        while (i < n) {
            val c = chars[i]
            val code = c.code
            when {
                // ASCII 字母/数字：合并连续段（字母小写，数字保留）
                code in 0x30..0x39 ||                       // 0-9
                    (code or 0x20) in 0x61..0x7A ||          // a-zA-Z
                    code == 0x5F || code == 0x27              // _  '  (如 "it's", "foo_bar")
                -> {
                    latinRun.append(if (code in 0x41..0x5A) c.lowercaseChar() else c)
                }
                // 汉字/日文汉字/日文假名：按 Bigram 切，先清 latinRun
                isCjkTokenChar(code) -> {
                    flushLatin()
                    if (i + 1 < n && isCjkTokenChar(chars[i + 1].code)) {
                        val bi = String(chars, i, 2)
                        out.add(bi)
                    } else {
                        // 孤立 CJK 字符（末尾或下一个不是 CJK）：切成单字，避免完全漏召回
                        out.add(c.toString())
                    }
                }
                else -> {
                    // 标点/空白/其他符号：切开 latin run 并跳过
                    flushLatin()
                }
            }
            i++
        }
        flushLatin()
        return out
    }

    /** 查询专用：对短语额外补单字 token，提升短查询召回。 */
    fun tokenizeForQuery(phrase: String): List<String> {
        val base = tokenize(phrase)
        // 单字补全（Bigram 会有 1 字偏移漏匹配，查询侧再补一份单字即可）
        if (phrase.length <= 6) {
            val unigrams = phrase.mapNotNull { ch ->
                when {
                    isCjkTokenChar(ch.code) -> ch.toString()
                    ch.isLetterOrDigit() -> ch.lowercase().toString()
                    else -> null
                }
            }
            if (unigrams.isEmpty()) return base
            val merged = ArrayList<String>(base.size + unigrams.size)
            merged.addAll(base)
            merged.addAll(unigrams)
            return merged
        }
        return base
    }

    /**
     * CJK 字符范围（粗粒度，覆盖中日韩越统一表意文字 + 扩展 A 区 + 假名）。
     * 此处不做更细拆分（如韩文 Hangul 单音节 Bigram 的语言差异），MVP 够用即可。
     */
    private fun isCjkTokenChar(code: Int): Boolean =
        (code in 0x4E00..0x9FFF) ||          // CJK Unified Ideographs (基本区)
            (code in 0x3400..0x4DBF) ||       // Extension A
            (code in 0x3040..0x30FF) ||       // Hiragana + Katakana
            (code in 0xF900..0xFAFF) ||       // CJK Compatibility Ideographs
            (code in 0x20000..0x2A6DF)        // Extension B（少部分冷门字，宽范围判断）
}
