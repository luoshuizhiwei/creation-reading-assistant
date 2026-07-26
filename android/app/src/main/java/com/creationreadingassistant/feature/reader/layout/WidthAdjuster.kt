package com.creationreadingassistant.feature.reader.layout

/**
 * 标点宽度调整，分两个相位。
 *
 * clreq 6.1.1 规定「先按排版风格做标点宽度调整，再做禁则」，因为挤压会改变换行位置。
 * 但有两条调整规则本身**依赖行边界**：
 *
 * - 行末全角标点半宽（GB/T 15834 §5.1.10）—— 「行末」是断行的输出
 * - 行首开始括号缩左半（clreq 6.3.2.2）—— 「行首」是断行的输出
 *
 * 直接把它们放进整段调整会形成循环依赖：要调整先得知道行边界，要断行先得调整完。
 * 三个候选方案都栽在这里（其中一个把「行首括号缩半」写进了规格却实现不出来）。
 *
 * 解法是拆相位：
 *
 * - **相位 A（位置无关，整段做一次）**：连续标点挤压、中西文间距。只依赖字符序列。
 * - **相位 B（位置相关，装行时做）**：行首行末调整，且**只会释放宽度、绝不占用宽度**。
 *   因为只释放，就可以「先按 A 装行 → 施加 B → 若释放出的宽度够再吸一簇进来 → 重算 B」，
 *   循环上限可证：B 至多释放 1em（行首 0.5 + 行末 0.5），而最窄的簇也有约 0.25em，
 *   故最多 4 次必然收敛。
 */
object WidthAdjuster {

    /** 中西文之间的间距（em）。clreq 建议 1/4，实务上 1/8 更自然。 */
    const val CJK_LATIN_GAP_EM = 0.125f

    /**
     * 一个全角标点实际可削掉的空白（px）。
     *
     * **不能直接用 `blankEm * em`。** 那是「标点确实占满一个字宽」的理想假设，
     * 而真机上大量字体的标点并不满宽：思源黑体的 `，` 约 0.5em，
     * 半角/窄形标点更只有 0.3em 左右。硬减 0.5em 会把这些标点削成零宽甚至负宽 ——
     * 负 advance 会让 `clusterX` 不再单调递增，绘制时后字压上来，
     * 选区与高亮矩形跟着整行错位。
     *
     * 所以以**实测宽度的一半**封顶：无论字体怎么设计，标点最多让出自己的一半。
     */
    fun blankPx(klass: Int, measured: Float, em: Float): Float {
        val ideal = CharClass.blankEm(klass) * em
        if (ideal <= 0f) return 0f
        return ideal.coerceAtMost(measured * 0.5f).coerceAtLeast(0f)
    }

    private fun rightBlankPx(klass: Int, measured: Float, em: Float): Float =
        if (CharClass.blankSide(klass) == CharClass.BLANK_RIGHT) blankPx(klass, measured, em) else 0f

    private fun leftBlankPx(klass: Int, measured: Float, em: Float): Float =
        if (CharClass.blankSide(klass) == CharClass.BLANK_LEFT) blankPx(klass, measured, em) else 0f

    /**
     * 相位 A 的产物。
     *
     * @param adv 调整后的每簇推进宽度
     * @param gapAfter 每簇之后额外插入的间距（中西文间距），不属于任何簇
     * @param drawShift 绘制时相对推进起点的偏移，负值表示向左削
     */
    class PhaseA(
        val adv: FloatArray,
        val gapAfter: FloatArray,
        val drawShift: FloatArray,
    ) {
        /**
         * 前缀和：`pre[i]` 为前 i 簇的总宽（含其间的 gap）。
         *
         * 存在的理由有两个：装行时的二分要它；[width] 要它。
         * 后者尤其重要 —— 装行主循环每行会调 4~5 次相位 B，
         * 若每次都 O(行长) 地累加，长段落的装行就退化成平方级。
         */
        val pre: FloatArray = FloatArray(adv.size + 1).also { p ->
            for (i in adv.indices) {
                p[i + 1] = p[i] + adv[i] + (if (i + 1 < adv.size) gapAfter[i] else 0f)
            }
        }

        /** [from, until) 的总宽度，含区间内部的 gap，但不含末尾 gap。O(1)。 */
        fun width(from: Int, until: Int): Float {
            if (until <= from) return 0f
            var w = pre[until] - pre[from]
            // pre 把 gapAfter[until-1] 也算进来了（当它不是全段最后一簇时），
            // 但行末那个 gap 不属于本行。
            if (until < adv.size) w -= gapAfter[until - 1]
            return w
        }
    }

    fun phaseA(c: Clusters, cfg: LayoutConfig): PhaseA {
        val n = c.count
        val adv = c.advance.copyOf()
        val gap = FloatArray(n)
        val shift = FloatArray(n)
        val em = cfg.em

        for (i in 0 until n - 1) {
            val k = c.klass[i]
            val kn = c.klass[i + 1]

            // 连续标点挤压：只在两个全角标点相邻时发生
            if (CharClass.isFullWidthPunct(k) && CharClass.isFullWidthPunct(kn)) {
                val right = rightBlankPx(k, c.advance[i], em)
                val left = leftBlankPx(kn, c.advance[i + 1], em)
                if (right > 0f) adv[i] -= right
                if (left > 0f) {
                    adv[i + 1] -= left
                    // 左侧空白被削掉后，墨水要跟着左移，否则会和前一个标点拉开缝
                    shift[i + 1] -= left
                }
            } else if (isCjkSide(k) && CharClass.isLatinLike(kn) ||
                CharClass.isLatinLike(k) && isCjkSide(kn)
            ) {
                // 中西文混排间距
                gap[i] = CJK_LATIN_GAP_EM * em
            }
        }
        // 兜底：任何路径都不允许出现负推进，否则 clusterX 会倒退。
        for (i in 0 until n) if (adv[i] < 0f) adv[i] = 0f
        return PhaseA(adv, gap, shift)
    }

    /**
     * 相位 B 的产物：在相位 A 基础上，针对某一行 [from, until) 的调整。
     *
     * **只释放宽度，不占用宽度** —— 这是收敛性的前提。
     *
     * 只记录三个增量而不是复制整段数组。相位 B 每行要算 4~5 次，
     * 复制两条长度为「整段簇数」的数组会让装行退化为 O(n²)：
     * 20 万字的单段（网文里「作者有话说」常见）能跑到几十秒，直接 ANR。
     * 真正被改动的永远只有行首、行末两个下标。
     */
    class PhaseB(
        val from: Int,
        val until: Int,
        val firstAdvDelta: Float,
        val firstShiftDelta: Float,
        val lastAdvDelta: Float,
        val width: Float,
    ) {
        /** 第 [i] 簇在本行的推进宽度。 */
        fun advAt(a: PhaseA, i: Int): Float {
            var v = a.adv[i]
            if (i == from) v += firstAdvDelta
            if (i == until - 1) v += lastAdvDelta
            return if (v < 0f) 0f else v
        }

        /** 第 [i] 簇在本行的绘制偏移。 */
        fun shiftAt(a: PhaseA, i: Int): Float =
            a.drawShift[i] + (if (i == from) firstShiftDelta else 0f)
    }

    /**
     * 施加行首/行末调整。
     *
     * 注：行首起始括号缩掉的左半会自然体现在 x 起点上，
     * 所以「段首『你好』」的实际缩进是 2em − 0.5em = 1.5em。
     */
    fun phaseB(
        c: Clusters,
        a: PhaseA,
        from: Int,
        until: Int,
        cfg: LayoutConfig,
    ): PhaseB {
        val em = cfg.em
        var firstAdv = 0f
        var firstShift = 0f
        var lastAdv = 0f

        if (until > from) {
            // 行首起始类标点：左半空白无意义，削掉
            val kFirst = c.klass[from]
            val leftBlank = leftBlankPx(kFirst, c.advance[from], em)
            if (leftBlank > 0f && a.drawShift[from] == 0f) {
                firstAdv -= leftBlank
                firstShift -= leftBlank
            }
            // 行末句读/收尾类标点：右半空白无意义，削掉
            val last = until - 1
            val kLast = c.klass[last]
            val rightBlank = rightBlankPx(kLast, c.advance[last], em)
            if (rightBlank > 0f && a.adv[last] == c.advance[last]) {
                lastAdv -= rightBlank
            }
        }

        val w = (a.width(from, until) + firstAdv + lastAdv).coerceAtLeast(0f)
        return PhaseB(from, until, firstAdv, firstShift, lastAdv, w)
    }

    /**
     * 该类别是否属于中西文间距里的「中」侧。
     *
     * **只认汉字/假名，不认全角标点。** clreq 6.3.3 明确规定标点与西文之间不加这个间距 ——
     * 全角标点自带的半格空白已经承担了同样的视觉职责，再加就是双份，
     * 表现为「他说：Hello」的冒号后凭空多出一道缝。
     */
    private fun isCjkSide(k: Int): Boolean = k == CharClass.CJK
}
