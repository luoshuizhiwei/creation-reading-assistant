package com.creationreadingassistant.feature.reader.layout

/**
 * 装行：把一个段落切成若干行。
 *
 * 流程（对应 SIDECAR-ZH 5.3 的主循环）：
 *
 * 1. 一次测量、一次断点查询（唯二的全段遍历）
 * 2. 相位 A 宽度调整 + 前缀和
 * 3. 贪心装行（二分找到放得下的最大簇数）
 * 4. 相位 B「挤进」：行首行末调整释放出宽度后，尝试再吸一簇，最多 4 轮
 * 5. 禁则「推出」：回退到合法断点；回退失败则 OVERFLOW 前进
 *
 * 第 5 步的 OVERFLOW 是关键取舍：**宁可让一行略微溢出，也不接受禁则违例。**
 * 候选方案里有一个在回退失败时直接接受违例（把「。」放到行首），
 * 那是用户最容易一眼看到的失败模式。
 */
object LineComposer {

    fun layoutParagraph(
        p: LayoutParagraph,
        cfg: LayoutConfig,
        ruler: TextRuler,
        oracle: BreakOracle,
    ): List<LayoutLine> {
        if (p.text.isEmpty()) return emptyList()

        val c = Clusterizer.of(p.text, ruler)
        if (c.count == 0) return emptyList()

        val atom = AtomicUnits.compute(c)
        val brk = oracle.breaks(p.text)
        val a = WidthAdjuster.phaseA(c, cfg)

        // 前缀和：pre[i] 为前 i 簇的总宽（含内部 gap），用于二分
        val pre = FloatArray(c.count + 1)
        for (i in 0 until c.count) {
            pre[i + 1] = pre[i] + a.adv[i] + (if (i + 1 < c.count) a.gapAfter[i] else 0f)
        }

        val lines = ArrayList<LayoutLine>()
        var lineStart = 0
        var first = true

        while (lineStart < c.count) {
            val indent = if (first && p.role == BlockRole.BODY) cfg.firstLineIndentPx else 0f
            val avail = cfg.contentWidthPx - indent

            // ③ 贪心：找到最大的 end 使 [lineStart, end) 宽度 ≤ avail
            var end = upperBound(pre, lineStart, avail).coerceAtLeast(lineStart + 1)

            // ④ 相位 B「挤进」。B 只释放宽度，故必然收敛，上限 4 轮。
            var b = WidthAdjuster.phaseB(c, a, lineStart, end, cfg)
            var guard = 0
            while (guard < MAX_SQUEEZE_IN_ROUNDS && end < c.count) {
                val freed = avail - b.width
                if (freed < a.adv[end]) break
                end++
                b = WidthAdjuster.phaseB(c, a, lineStart, end, cfg)
                guard++
            }

            // ⑤ 禁则「推出」
            if (end < c.count) {
                val floor = lineStart + cfg.minLineClusters
                var k = end
                while (k > floor && !breakOk(c, atom, brk, k, lineStart, cfg)) k--
                end = if (k > floor && breakOk(c, atom, brk, k, lineStart, cfg)) {
                    k
                } else {
                    advanceToOverflow(c, atom, brk, end, avail, cfg)
                }
                b = WidthAdjuster.phaseB(c, a, lineStart, end, cfg)
            }

            val overflowed = b.width > avail + EPS
            lines.add(
                Justifier.build(
                    c = c,
                    a = a,
                    b = b,
                    from = lineStart,
                    until = end,
                    indent = indent,
                    avail = avail,
                    isParagraphStart = first,
                    isParagraphEnd = end >= c.count,
                    role = p.role,
                    overflowed = overflowed,
                    cfg = cfg,
                ),
            )

            val next = skipLeadingSpaces(c, end)
            // 绝对不允许原地踏步，否则死循环
            lineStart = if (next > lineStart) next else lineStart + 1
            first = false
        }
        return lines
    }

    /** 找到最大的 end 使 [from, end) 的宽度不超过 avail。 */
    private fun upperBound(pre: FloatArray, from: Int, avail: Float): Int {
        val target = pre[from] + avail
        var lo = from
        var hi = pre.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (pre[mid] <= target + EPS) lo = mid else hi = mid - 1
        }
        return lo
    }

    /**
     * 位置 [k] 是否可作为断点（即第 k 簇成为下一行的首簇）。
     *
     * 四个条件缺一不可：不能让禁首标点当行首、不能让禁尾标点当行尾、
     * 不能拆开原子单元、ICU 也认为此处可断。
     */
    fun breakOk(
        c: Clusters,
        atom: IntArray,
        brk: BooleanArray,
        k: Int,
        lineStart: Int,
        cfg: LayoutConfig,
    ): Boolean {
        if (k <= lineStart || k >= c.count) return false

        val chHere = c.text[c.startInText[k]]
        if (CharClass.isNoStart(c.klass[k], chHere, cfg.strictKinsoku)) return false

        val chPrev = c.text[c.startInText[k - 1]]
        if (CharClass.isNoEnd(c.klass[k - 1], chPrev)) return false

        if (atom[k] != AtomicUnits.NONE && atom[k] == atom[k - 1]) return false

        return brk.getOrElse(c.startInText[k]) { true }
    }

    /**
     * 回退失败时的兜底：向前跳到当前原子单元之后的第一个位置。
     *
     * 若该原子单元本身长于一行（120 字符的 URL），降级为「任意簇边界可断」并强放 ——
     * 否则单元内恒无合法断点，回退耗尽后会死循环。
     */
    private fun advanceToOverflow(
        c: Clusters,
        atom: IntArray,
        brk: BooleanArray,
        from: Int,
        avail: Float,
        cfg: LayoutConfig,
    ): Int {
        if (from >= c.count) return c.count

        if (AtomicUnits.exceedsLine(c, atom, from, avail)) {
            // 逃生：该单元比一行还长，任意位置断
            return (from + 1).coerceAtMost(c.count)
        }

        val id = atom[from]
        var k = from
        if (id != AtomicUnits.NONE) {
            while (k < c.count && atom[k] == id) k++
        } else {
            k = from + 1
        }
        // 跳出单元后再往前找一个不违反禁首的位置，最多再走几簇
        var guard = 0
        while (k < c.count && guard < MAX_OVERFLOW_SCAN) {
            val ch = c.text[c.startInText[k]]
            if (!CharClass.isNoStart(c.klass[k], ch, cfg.strictKinsoku)) break
            k++
            guard++
        }
        return k.coerceAtMost(c.count).coerceAtLeast(from + 1)
    }

    private fun skipLeadingSpaces(c: Clusters, from: Int): Int {
        var i = from
        while (i < c.count && c.klass[i] == CharClass.SPACE) i++
        return i
    }

    private const val MAX_SQUEEZE_IN_ROUNDS = 4
    private const val MAX_OVERFLOW_SCAN = 8
    const val EPS = 0.01f
}
