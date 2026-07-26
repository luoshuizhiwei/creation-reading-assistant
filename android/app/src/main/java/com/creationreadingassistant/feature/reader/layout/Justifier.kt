package com.creationreadingassistant.feature.reader.layout

/**
 * 两端对齐与逐簇 x 坐标计算。
 *
 * `clusterX` 是整个内核的关键产物：均排、选区命中、高亮矩形、绘制四件事全靠它。
 * 这也是自研断行的真正理由 —— 一旦有了它，断行只是顺带。
 *
 * 拉伸配额有**两条上限并存**，缺一不可：
 *
 * - **单间隙 ≤ 1/3 em**（clreq 6.3.3）。少了这条，5 簇的短行会把 2.5em 的余量
 *   摊成每个间隙 0.6em，字与字之间稀疏得像被撕开。
 * - **整行余量 ≤ 2em**。少了这条，余量特别大的行即使每个间隙都合规，
 *   整体看仍然发散。这时应当整行放弃拉伸。
 *
 * 另外，可拉伸的间隙是**逐个判定**的，不是整行开关。候选方案里有一个用
 * 「本行含拉丁字母就整行不拉伸」，而中文小说里含阿拉伯数字的行（「第3章」「2019年」
 * 「100块」）真实占比 10–25%，等于五分之一的行右边缘参差 —— 比全部不对齐更难看。
 */
object Justifier {

    fun build(
        c: Clusters,
        a: WidthAdjuster.PhaseA,
        b: WidthAdjuster.PhaseB,
        from: Int,
        until: Int,
        indent: Float,
        avail: Float,
        isParagraphStart: Boolean,
        isParagraphEnd: Boolean,
        role: BlockRole,
        overflowed: Boolean,
        cfg: LayoutConfig,
    ): LayoutLine {
        val n = until - from
        val em = cfg.em
        val slack = avail - b.width

        // 末行、标题、太短的行都不拉伸
        val noStretch = isParagraphEnd ||
            role != BlockRole.BODY ||
            !cfg.justify ||
            n < cfg.minJustifyClusters ||
            overflowed

        val stretchable = ArrayList<Int>(n)
        if (!noStretch) {
            for (i in from until until - 1) {
                if (isStretchable(c, i, until)) stretchable.add(i)
            }
        }

        val d: Float = when {
            noStretch || stretchable.isEmpty() -> 0f
            slack > 0f -> {
                if (slack > cfg.maxSlackEm * em) {
                    0f // 整行余量过大，放弃拉伸好过稀疏
                } else {
                    (slack / stretchable.size).coerceAtMost(cfg.maxStretchPerGapEm * em)
                }
            }
            else -> (slack / stretchable.size).coerceAtLeast(cfg.minCompressPerGapEm * em)
        }

        val stretchSet = stretchable.toHashSet()
        val xs = FloatArray(n + 1)
        var x = indent
        for (i in from until until) {
            xs[i - from] = x + b.drawShift[i]
            x += b.adv[i]
            if (i + 1 < until) x += a.gapAfter[i]
            if (i in stretchSet) x += d
        }
        xs[n] = x

        return LayoutLine(
            startCluster = from,
            endCluster = until,
            startInText = c.startInText[from],
            endInText = c.startInText[until],
            startX = indent,
            clusterX = xs,
            isParagraphStart = isParagraphStart,
            isParagraphEnd = isParagraphEnd,
            role = role,
            overflowed = overflowed,
        )
    }

    /**
     * 间隙 [i, i+1) 是否可拉伸。
     *
     * 四条排除：西文词内部、原子单元内部、连接类标点两侧、行末字之后。
     */
    private fun isStretchable(c: Clusters, i: Int, until: Int): Boolean {
        if (i + 1 >= until) return false
        val k = c.klass[i]
        val kn = c.klass[i + 1]
        // 西文词内部不拉，否则单词会被拆得七零八落
        if (CharClass.isLatinLike(k) && CharClass.isLatinLike(kn)) return false
        // 连接号/间隔号两侧不拉
        if (k == CharClass.MIDDLE || kn == CharClass.MIDDLE) return false
        if (k == CharClass.DASH && kn == CharClass.DASH) return false
        if (k == CharClass.ELLIPSIS && kn == CharClass.ELLIPSIS) return false
        return true
    }
}
