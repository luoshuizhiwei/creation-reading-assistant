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
 *
 * 样式覆盖按上游 legado 的 txtTocRule.json 改写（负向断言、大写数字、卷册分组、
 * 「正文」前缀、括号前缀都来自它），但比它保守：
 *
 * - 单位字分强弱两档。强单位（章/节/回/卷/部/篇/集/册）可以直接跟副标题
 *   （「第一章初见」确有其写法），但带 legado 式负向断言——单位字与下一字成词
 *   （部分/回合/集合/节课/册封）的是正文；弱单位（话/场/幕/折）和具名专名
 *   （序章/楔子/番外……）太容易连进正文（话音/话说/序幕拉开/楔子钉进墙），
 *   后面必须是行尾、编号或分隔符。
 */
object TxtChapterDetector {
    data class Rule(val id: String, val label: String, val patterns: List<Regex>)

    /** 章节标题行的长度上限。超过这个长度的行是正文，不是标题。 */
    private const val MAX_TITLE_LENGTH = 40

    /** 识别结果的平均章节长度低于此值，判定为误报风暴，整体作废。 */
    private const val MIN_AVERAGE_CHAPTER_CHARS = 300

    /**
     * 数字：阿拉伯、全角、汉字小写（「两」也算，「第两百章」确有其写法；〇/○ 两种圈零都有人用）、
     * 汉字大写（壹贰叁……，仿古文常见）。
     */
    private const val NUM = "[0-9０-９零〇○一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟]"

    /** 行内空白。正则的 \s 不含全角空格（U+3000），必须显式列出。 */
    private const val WS = """[ \t　]"""

    /** 标题常见的左括号前缀（【第一章】风起）。照 legado 不收圆括号，避免吃进正文括注。 */
    private const val OPEN = """[【〔〖「『〈［\[]"""

    /**
     * 专名/弱单位与副标题之间允许的分隔符：只能是空白或标点，绝不能是汉字或数字——
     * 这是挡住「序章节奏太慢了」「楔子钉进了木头缝里」这类误报的关键。
     */
    private const val SEP =
        """[ \t　,，.．。、:：;；·・\-—―－_~～!！?？|｜(（)）\[\]【】〔〕〖〗「」『』〈〉《》"“”'‘’]"""

    /**
     * 强单位：后面可直接跟副标题。负向断言抄自 legado：
     * 节课/回合/回来/回事/回去/部分/部赛/部游/部队/篇张/集合/集和/册封 都是正文里的词。
     */
    private const val STRONG_UNIT =
        """(?:[章節]|节(?!课)|回(?![合来事去])|卷|部(?![分赛游队])|篇(?!张)|集(?![合和])|册(?!封))"""

    /** 弱单位：话音/话说/场面/幕后……与下一字成词太常见，副标题必须隔一个分隔符。 */
    private const val WEAK_UNIT = "[话話场場幕折]"

    private val PATTERNS: List<Regex> = listOf(
        // 第X章 / 第 1 节 / 第一百二十三回 / 第壹卷 / 第３册，可带「正文」前缀或左括号前缀
        Regex("""^$OPEN?$WS*(?:正文$WS{0,4})?第$WS*$NUM{1,12}$WS*$STRONG_UNIT[^\n]{0,30}$"""),
        // 第X话 / 第X场 / 第X幕 / 第X折：弱单位，副标题前必须有分隔符
        Regex("""^$OPEN?$WS*(?:正文$WS{0,4})?第$WS*$NUM{1,12}$WS*$WEAK_UNIT(?:$SEP[^\n]{0,30})?$"""),
        // 卷一 / 卷之十二 / 部X / 篇X / 册X 的省略写法
        Regex("""^$OPEN?$WS*[卷部篇册]$WS*之?$WS*$NUM{1,12}(?:$SEP[^\n]{0,30})?$"""),
        // 上卷 / 中部 / 下册 分组
        Regex("""^$OPEN?$WS*[上中下]$WS*[卷部篇册](?:$SEP[^\n]{0,30})?$"""),
        // 具名特殊章节。可带编号（番外一/番外篇二），副标题前必须有分隔符：
        // 「楔子」「序幕」是标题，「楔子钉进了木头缝里」「序幕拉开了」是正文。
        Regex(
            """^$OPEN?$WS*(?:序章|序言|序幕|自序|楔子|前言|引子|引言|后记|後記|後记|尾声|尾聲|终章|終章|终幕|終幕|番外篇|番外|外传|外傳|大结局|大結局|结局|結局|正文|序)(?:$WS*$NUM{1,8})?(?:$SEP[^\n]{0,30})?$"""
        ),
        // Chapter 1 / CHAPTER IV
        Regex("""^chapter\s+[0-9ivxlcdm]{1,12}\b[^\n]{0,30}$""", RegexOption.IGNORE_CASE),
    )

    val rules: List<Rule> = listOf(
        Rule("builtin", "标准", emptyList()),
        Rule(
            "num-dot",
            "数字+标点",
            listOf(Regex("""^[ \t　]{0,4}[0-9０-９]{1,4}\s*[.、．:：,，]\s*(?![0-9０-９])\S.{0,29}$""")),
        ),
        Rule("num-bare", "纯数字", listOf(Regex("""^[ \t　]{0,4}[0-9０-９]{1,4}[ \t　]*$"""))),
        Rule(
            "cn-num-dot",
            "中文数字+顿号",
            listOf(Regex("""^[ \t　]{0,4}[零〇○一二三四五六七八九十百千两]{1,8}\s*[、.．]\s*\S.{0,29}$""")),
        ),
        Rule(
            "bracketed",
            "数字括号",
            listOf(
                Regex(
                    """^[ \t　]{0,4}[【〔\[（(]\s*(?:第?\s*$NUM{1,12}\s*[章节回卷部篇]?|[0-9０-９]{1,4})\s*[】〕\]）)]\s*.{0,30}$""",
                ),
            ),
        ),
        Rule(
            "en-extended",
            "英文扩展",
            listOf(
                Regex(
                    """^(?:part|section|book|act|prologue|epilogue|interlude)\s*[0-9ivxlcdm]{0,8}\b.{0,30}$""",
                    RegexOption.IGNORE_CASE,
                ),
            ),
        ),
        Rule("md-heading", "Markdown 标题", listOf(Regex("""^#{1,3}\s+\S.{0,38}$"""))),
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
    fun detect(text: String, ruleId: String = "builtin"): List<Chapter> = detect(text, listOf(ruleId))

    /**
     * 多规则并集识别：标准四类恒参与，另取 [ruleIds] 对应规则的正则。
     * density 保护（平均章节过短整体作废）仅在规则全部为内置标准
     * （空集合或仅 ["builtin"]）时启用 —— 用户手选规则 = 用户背书，不再自动兜底。
     */
    fun detect(text: String, ruleIds: Collection<String>): List<Chapter> {
        val ids = ruleIds.toSet()
        return detect(text, { isChapterTitle(it, ids) }, densityGuard = ids.all { it == "builtin" })
    }

    /**
     * 用显式正则列表识别章节（RuleEngine 自定义规则并集入口）。
     * [densityGuard] 控制是否启用「平均章节过短整体作废」。
     */
    fun detect(text: String, patterns: List<Regex>, densityGuard: Boolean): List<Chapter> =
        detect(text, { title -> patterns.any { it.matches(title) } }, densityGuard)

    private fun detect(text: String, matcher: (String) -> Boolean, densityGuard: Boolean): List<Chapter> {
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
                if (title.length in 1..MAX_TITLE_LENGTH && matcher(title)) {
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
        if (densityGuard && average < MIN_AVERAGE_CHAPTER_CHARS) {
            return listOf(Chapter("全文", 0, n))
        }
        return chapters
    }

    /** 单行是否构成章节标题。抽出来便于单测与复用。 */
    fun isChapterTitle(line: String, ruleId: String = "builtin"): Boolean = isChapterTitle(line, setOf(ruleId))

    /** 多规则版本：标准四类恒参与，另取 [ruleIds] 对应规则的模式。 */
    fun isChapterTitle(line: String, ruleIds: Collection<String>): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > MAX_TITLE_LENGTH) return false
        if (PATTERNS.any { it.matches(t) }) return true
        if (ruleIds.isEmpty()) return false
        return rules.any { rule -> rule.id in ruleIds && rule.patterns.any { it.matches(t) } }
    }

    /**
     * 标准四类 + [ruleIds] 对应具名规则正则的并集（供 RuleEngine 多规则并集复用；
     * 自定义规则的正则由引擎自行编译拼接）。
     */
    fun unionPatterns(ruleIds: Collection<String>): List<Regex> =
        PATTERNS + rules.filter { it.id in ruleIds }.flatMap { it.patterns }
}
