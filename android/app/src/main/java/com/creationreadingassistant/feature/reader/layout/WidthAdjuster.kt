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
        /** [from, until) 的总宽度，含区间内部的 gap，但不含末尾 gap。 */
        fun width(from: Int, until: Int): Float {
            var w = 0f
            for (i in from until until) {
                w += adv[i]
                if (i + 1 < until) w += gapAfter[i]
            }
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
                val right = CharClass.rightBlankEm(k)
                val left = CharClass.leftBlankEm(kn)
                if (right > 0f) adv[i] -= right * em
                if (left > 0f) {
                    adv[i + 1] -= left * em
                    // 左侧空白被削掉后，墨水要跟着左移，否则会和前一个标点拉开缝
                    shift[i + 1] -= left * em
                }
            } else if (isCjkSide(k) && CharClass.isLatinLike(kn) ||
                CharClass.isLatinLike(k) && isCjkSide(kn)
            ) {
                // 中西文混排间距
                gap[i] = CJK_LATIN_GAP_EM * em
            }
        }
        return PhaseA(adv, gap, shift)
    }

    /**
     * 相位 B 的产物：在相位 A 基础上，针对某一行 [from, until) 的调整。
     *
     * **只释放宽度，不占用宽度** —— 这是收敛性的前提。
     */
    class PhaseB(
        val adv: FloatArray,
        val drawShift: FloatArray,
        val width: Float,
    )

    /**
     * 施加行首/行末调整。
     *
     * @param indentPx 本行的首行缩进；行首括号缩左半会从缩进里扣，
     *        所以「段首『你好』」的实际缩进是 2em − 0.5em = 1.5em
     */
    fun phaseB(
        c: Clusters,
        a: PhaseA,
        from: Int,
        until: Int,
        cfg: LayoutConfig,
    ): PhaseB {
        val adv = a.adv.copyOf()
        val shift = a.drawShift.copyOf()
        val em = cfg.em

        if (until > from) {
            // 行首起始类标点：左半空白无意义，削掉
            val kFirst = c.klass[from]
            val leftBlank = CharClass.leftBlankEm(kFirst)
            if (leftBlank > 0f && a.drawShift[from] == 0f) {
                adv[from] -= leftBlank * em
                shift[from] -= leftBlank * em
            }
            // 行末句读/收尾类标点：右半空白无意义，削掉
            val last = until - 1
            val kLast = c.klass[last]
            val rightBlank = CharClass.rightBlankEm(kLast)
            if (rightBlank > 0f && a.adv[last] == c.advance[last]) {
                adv[last] -= rightBlank * em
            }
        }

        var w = 0f
        for (i in from until until) {
            w += adv[i]
            if (i + 1 < until) w += a.gapAfter[i]
        }
        return PhaseB(adv, shift, w)
    }

    private fun isCjkSide(k: Int): Boolean =
        k == CharClass.CJK || CharClass.isFullWidthPunct(k)
}
