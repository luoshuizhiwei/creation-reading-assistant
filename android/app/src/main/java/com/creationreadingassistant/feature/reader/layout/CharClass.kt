package com.creationreadingassistant.feature.reader.layout

/**
 * 中文排版的字符分类与禁则表。
 *
 * 依据：W3C《中文排版需求》(clreq) 与 GB/T 15834—2011《标点符号用法》。
 *
 * 三件事由这张表决定：
 *
 * 1. **禁则**（避头尾）：哪些标点不能出现在行首（如句号跑到下一行开头），
 *    哪些不能出现在行尾（如开引号被落单在行末）。
 * 2. **标点宽度调整**：全角标点占一个字宽，但墨水只占半个，另外半个是空白。
 *    空白在左还是在右决定了相邻标点能否互相挤压。
 * 3. **两端对齐时哪些间隙可拉伸**：西文词内、原子单元内不能拉。
 *
 * 关于「空白在哪一侧」，这是挤压规则的全部依据：
 * - 句读类（。，、；：？！）与收尾类（）」』等）：墨水在左半，**右半是空**
 * - 起始类（（「『“ 等）：墨水在右半，**左半是空**
 *
 * 于是相邻两个标点的挤压量 = 左字的右侧空白 + 右字的左侧空白：
 * - `。”` → 0.5 + 0   = 0.5em，两字合计 1.5em
 * - `“‘` → 0   + 0.5 = 0.5em，合计 1.5em
 * - `）（` → 0.5 + 0.5 = 1.0em，合计 1.0em
 */
object CharClass {

    // ── 类别常量 ────────────────────────────────────────────────────────
    const val CJK = 0            // 汉字与全角假名
    const val END_PUNCT = 1      // 句读：。，、；：？！ —— 禁首
    const val CLOSE = 2          // 收尾：）】》」』〕｝ —— 禁首
    const val QUOTE_R = 3        // 右引号 ”’ —— 禁首
    const val OPEN = 4           // 起始：（【《「『〔｛ —— 禁尾
    const val QUOTE_L = 5        // 左引号 “‘ —— 禁尾
    const val MIDDLE = 6         // 间隔号 ·／ —— 禁首且禁尾
    const val DASH = 7           // 破折号 —（成对不可拆）
    const val ELLIPSIS = 8       // 省略号 …（成对不可拆）
    const val LATIN = 9          // 拉丁字母
    const val DIGIT = 10         // 数字
    const val SPACE = 11         // 空白
    const val OTHER = 12

    /**
     * 半角句读：｡ ､ ｢ ｣（U+FF61..U+FF64）。
     *
     * 它们长得像全角句读，但 East_Asian_Width 是 Halfwidth —— 本身只占半个字宽，
     * **没有那半格空白可削**。早先把它们混进 [END_PUNCT]，于是行末削半会削掉它们
     * 自身的全部宽度，真机上表现为标点被压成零宽、后字直接压上来。
     * 禁则照常适用（靠字符表，不靠类别）。
     */
    const val HALF_PUNCT = 13

    // ── 空白所在侧 ──────────────────────────────────────────────────────
    const val BLANK_NONE = 0
    const val BLANK_LEFT = 1
    const val BLANK_RIGHT = 2

    // ── 字符表 ──────────────────────────────────────────────────────────
    // 逐个列出而不是用区间：区间会把不该包含的字符卷进来，而禁则表错一个字符
    // 就会在真实小说里稳定复现难看的排版。

    private const val S_END_PUNCT = "。．，、；：？！"
    private const val S_CLOSE = "）〕】｝》〉」』〗〙〛］"
    private const val S_QUOTE_R = "”’"
    private const val S_OPEN = "（〔【｛《〈「『〖〘〚［"
    private const val S_QUOTE_L = "“‘"

    /** 间隔号 / 分隔号。`‧`(U+2027) 是台港译名用的间隔号，漏了它「周‧杰倫」两侧都可断。 */
    private const val S_MIDDLE = "·・‧／/"

    /** 其中真正的**人名**间隔号。只有这些才让「A·B」成为不可拆的原子单元。 */
    private const val S_NAME_SEP = "·・‧"

    private const val S_DASH = "—―"
    private const val S_ELLIPSIS = "…‥"

    /** 半角句读，见 [HALF_PUNCT]。 */
    private const val S_HALF_PUNCT = "｡､｢｣"

    /**
     * 连接号。clreq 6.1.1：**只禁首，不禁尾** —— 「3～5 天」换行时 `～` 不能落在行首，
     * 但它留在行尾是合法的。
     *
     * 刻意不含 ASCII `-`：它在 TXT 里大量用作列表符号（「- 某项」），
     * 按连接号处理会把正常的列表断行顶掉。
     */
    private const val S_CONNECTOR = "～〜－–‐"

    /**
     * 禁首（不能出现在行首）。三档中的基础档，任何配置下都生效。
     *
     * `｣` 是半角右括号，禁首；`｢` 是半角左括号，归禁尾。
     */
    private val NO_START: Set<Char> =
        (S_END_PUNCT + S_CLOSE + S_QUOTE_R + S_MIDDLE + S_CONNECTOR + "｡､｣" + "%％‰℃℉°′″々〆ー").toSet()

    /**
     * 禁尾（不能出现在行尾）。
     *
     * 除了各类起始括号，还含正负号与货币符号 —— clreq 6.1.2.2 要求它们
     * 与后随数字不可拆行（「￥100」不能拆成行末 `￥` + 行首 `100`）。
     */
    private val NO_END: Set<Char> =
        (S_OPEN + S_QUOTE_L + S_MIDDLE + "｢" + "$￥£€#¥±+−₫₩₦").toSet()

    /**
     * STRICT 档追加的禁首字符：破折号与省略号。
     *
     * 单列成一档是因为它们是**成对**字符，回退时容易连着回退两簇，
     * 在窄屏或大字号下更容易触发溢出。默认开启，出问题可单独关掉。
     */
    private val NO_START_STRICT: Set<Char> = (S_DASH + S_ELLIPSIS).toSet()

    fun classify(c: Char): Int = classifyCodePoint(c.code)

    /**
     * 按**码位**分类。必须走这条，不能走 `classify(Char)`：
     * 扩展 B–I 的汉字（𠮷 U+20BB7）在 UTF-16 里是代理对，
     * 单看任一半都不是汉字，会被判成 [OTHER]，于是同一段里
     * 「𠮷abc」和「吉abc」的中西文间距行为不一致。
     */
    fun classifyCodePoint(cp: Int): Int {
        if (cp <= 0xFFFF) {
            val c = cp.toChar()
            when {
                c in S_HALF_PUNCT -> return HALF_PUNCT
                c in S_END_PUNCT -> return END_PUNCT
                c in S_CLOSE -> return CLOSE
                c in S_QUOTE_R -> return QUOTE_R
                c in S_OPEN -> return OPEN
                c in S_QUOTE_L -> return QUOTE_L
                c in S_MIDDLE -> return MIDDLE
                c in S_DASH -> return DASH
                c in S_ELLIPSIS -> return ELLIPSIS
                c == ' ' || c == '\t' || c == '　' -> return SPACE
            }
        }
        // isCjk 必须排在 isDigit 之前：全角数字 ０-９ 两边都成立，
        // 先判 isDigit 会让 ＡＢ 归 CJK 而 １２ 归 DIGIT，于是
        // 「ＵＳＢ３」内部被当成中西文边界插进间距。全角形式必须同类。
        if (isCjkCodePoint(cp)) return CJK
        if (cp > 0xFFFF) return if (Character.isLetter(cp)) LATIN else OTHER
        val c = cp.toChar()
        return when {
            c.isDigit() -> DIGIT
            c.isLetter() -> LATIN
            else -> OTHER
        }
    }

    /** 是否为需要按全角处理的东亚字符。BMP 版，内部委托 [isCjkCodePoint]。 */
    fun isCjk(c: Char): Boolean = isCjkCodePoint(c.code)

    /** 是否为需要按全角处理的东亚字符（按码位，含基本多文种平面之外的扩展汉字）。 */
    fun isCjkCodePoint(cp: Int): Boolean =
        cp in 0x4E00..0x9FFF ||        // CJK 统一表意文字
            cp in 0x3400..0x4DBF ||    // 扩展 A
            cp in 0x3040..0x30FF ||    // 平假名 / 片假名
            cp in 0xF900..0xFAFF ||    // 兼容表意文字
            cp in 0xFF00..0xFF60 ||    // 全角形式（含全角字母与全角数字）
            cp in 0x3000..0x303F ||    // CJK 标点
            cp in 0x20000..0x323AF ||  // 扩展 B–I
            cp in 0x2F800..0x2FA1F     // 兼容表意文字补充

    fun isNoStart(klass: Int, ch: Char, strict: Boolean): Boolean {
        if (ch in NO_START) return true
        if (strict && ch in NO_START_STRICT) return true
        return when (klass) {
            END_PUNCT, CLOSE, QUOTE_R, MIDDLE -> true
            else -> false
        }
    }

    fun isNoEnd(klass: Int, ch: Char): Boolean {
        if (ch in NO_END) return true
        return when (klass) {
            OPEN, QUOTE_L, MIDDLE -> true
            else -> false
        }
    }

    /** 该类别的空白占多少 em（全角标点恒为 0.5，其余为 0）。 */
    fun blankEm(klass: Int): Float = when (klass) {
        END_PUNCT, CLOSE, QUOTE_R, OPEN, QUOTE_L -> 0.5f
        else -> 0f
    }

    /** 空白在哪一侧。决定相邻标点能挤掉多少。 */
    fun blankSide(klass: Int): Int = when (klass) {
        END_PUNCT, CLOSE, QUOTE_R -> BLANK_RIGHT
        OPEN, QUOTE_L -> BLANK_LEFT
        else -> BLANK_NONE
    }

    /** 该簇右侧的空白量（em）。 */
    fun rightBlankEm(klass: Int): Float =
        if (blankSide(klass) == BLANK_RIGHT) blankEm(klass) else 0f

    /** 该簇左侧的空白量（em）。 */
    fun leftBlankEm(klass: Int): Float =
        if (blankSide(klass) == BLANK_LEFT) blankEm(klass) else 0f

    /** 是否为西文类（字母或数字）。西文词内不拉伸、不断行。 */
    fun isLatinLike(klass: Int): Boolean = klass == LATIN || klass == DIGIT

    /**
     * 是否为全角标点。
     *
     * 判据是「占一个字宽、其中半格是可挤压的空白」，[HALF_PUNCT] 不满足，故不在此列。
     */
    fun isFullWidthPunct(klass: Int): Boolean = when (klass) {
        END_PUNCT, CLOSE, QUOTE_R, OPEN, QUOTE_L -> true
        else -> false
    }

    /**
     * 是否为人名间隔号（`·` `・` `‧`）。
     *
     * 只有它们才让「A·B」成为不可拆的原子单元。[MIDDLE] 还含分隔号 `／` `/`，
     * 那些不该把两侧的字黏成一体 —— 否则 URL 末尾的 `/` 会把后面的中文也粘进来。
     */
    fun isNameSeparator(ch: Char): Boolean = ch in S_NAME_SEP
}
