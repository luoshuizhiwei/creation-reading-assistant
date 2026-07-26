package com.creationreadingassistant.feature.reader.doc

/**
 * 中文 TXT 小说的章节识别。
 *
 * 现状问题：TXT 此前完全没有章节概念，只按 3000 字机械切块，于是没有目录、不能跳章、
 * 搜索结果的章节名恒为「全文」。
 *
 * 设计上最要紧的是**宁可少认，不可乱认**。参考实现（legado 系）里有一条「数字开头即标题」
 * 的规则，会把正文里的「1. 他说」「一、那天」全判成章节，长篇小说能凭空多出几百个伪章节，
 * 目录直接不可用。所以这里：
 *
 * - 只认有明确章节词的形态（第X章/卷X/序章/Chapter N），不认裸数字开头；
 * - 标题必须独占一行且足够短；
 * - 最后再做一次整体合理性检查，识别结果太密集就整个作废，退回「全文」单章。
 *   宁可没有目录，也不要一个错误的目录。
 */
object TxtChapterDetector {

    /** 章节标题行的长度上限。超过这个长度的行是正文，不是标题。 */
    private const val MAX_TITLE_LENGTH = 40

    /** 识别结果的平均章节长度低于此值，判定为误报风暴，整体作废。 */
    private const val MIN_AVERAGE_CHAPTER_CHARS = 300

    /** 中文数字与阿拉伯数字。「两」也算，「第两百章」确有其写法。 */
    private const val NUM = "[0-9０-９零〇一二三四五六七八九十百千万两]"

    private val PATTERNS: List<Regex> = listOf(
        // 第X章 / 第 1 节 / 第一百二十三回 / 第1卷
        Regex("""^第\s*$NUM{1,12}\s*[章節章节回卷部篇集幕折]\s*[^\n]{0,30}$"""),
        // 卷一 / 第一卷之类的省略写法：卷X / 部X / 篇X
        Regex("""^[卷部篇]\s*$NUM{1,12}\s*[^\n]{0,30}$"""),
        // 具名特殊章节
        Regex("""^(序章|序言|自序|楔子|前言|引子|引言|后记|後記|後记|尾声|尾聲|终章|終章|番外|结局|結局|大结局|大結局)\s*[^\n]{0,30}$"""),
        // Chapter 1 / CHAPTER IV
        Regex("""^chapter\s+[0-9ivxlcdm]{1,12}\b[^\n]{0,30}$""", RegexOption.IGNORE_CASE),
    )

    /** 一个识别出的章节。[startOffset] 是章节标题首字符在全文中的字符偏移。 */
    data class Chapter(
        val title: String,
        val startOffset: Int,
        val endOffset: Int,
    ) {
        val charCount: Int get() = endOffset - startOffset
    }

    /**
     * 从整篇 TXT 正文里识别章节。
     *
     * 识别不出来时返回单章「全文」，而不是空列表 —— 调用方永远能拿到至少一章，
     * 不必到处判空。
     */
    fun detect(text: String): List<Chapter> {
        if (text.isEmpty()) return listOf(Chapter("全文", 0, 0))

        val marks = ArrayList<Pair<Int, String>>() // (标题行起始偏移, 标题)
        var lineStart = 0
        val n = text.length
        var i = 0
        while (i <= n) {
            val atEnd = i == n
            if (atEnd || text[i] == '\n') {
                val rawLine = text.substring(lineStart, i)
                val title = rawLine.trim()
                if (title.length in 1..MAX_TITLE_LENGTH && isChapterTitle(title)) {
                    // 用 trim 之后的标题，但偏移仍指向原始行首，保证偏移连续、无空洞
                    marks.add(lineStart to title)
                }
                lineStart = i + 1
            }
            i++
        }

        if (marks.isEmpty()) return listOf(Chapter("全文", 0, n))

        val chapters = ArrayList<Chapter>(marks.size + 1)
        // 第一个标题之前若有内容（通常是书名页、简介），单独成章，否则会丢失
        if (marks[0].first > 0) {
            chapters.add(Chapter("开篇", 0, marks[0].first))
        }
        for ((idx, mark) in marks.withIndex()) {
            val end = if (idx + 1 < marks.size) marks[idx + 1].first else n
            chapters.add(Chapter(mark.second, mark.first, end))
        }

        // 合理性检查：真实小说的章节平均长度远大于几百字。
        // 平均值过小说明匹配到的多半是正文里的目录列表或诗歌，宁可没有目录。
        val average = n.toDouble() / chapters.size
        if (average < MIN_AVERAGE_CHAPTER_CHARS) {
            return listOf(Chapter("全文", 0, n))
        }
        return chapters
    }

    /** 单行是否构成章节标题。抽出来便于单测与复用。 */
    fun isChapterTitle(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > MAX_TITLE_LENGTH) return false
        return PATTERNS.any { it.matches(t) }
    }
}
