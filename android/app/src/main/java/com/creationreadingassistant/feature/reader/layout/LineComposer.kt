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
        // 前缀和由相位 A 一并算好（二分与相位 B 的宽度都靠它）
        val pre = a.pre

        val lines = ArrayList<LayoutLine>()
        var lineStart = 0
        var first = true

        while (lineStart < c.count) {
            val baseIndent = when (p.role) {
                BlockRole.QUOTE -> cfg.em * (cfg.listIndentEm + cfg.quoteExtraIndentEm)
                BlockRole.LIST_ITEM_BULLET,
                BlockRole.LIST_ITEM_NUMBER,
                BlockRole.TASK_ITEM_UNCHECKED,
                BlockRole.TASK_ITEM_CHECKED -> cfg.em * cfg.listIndentEm * p.indentLevel.coerceAtLeast(1)
                else -> 0f
            }
            val firstLineIndent = if (first && p.role == BlockRole.BODY) cfg.firstLineIndentPx else 0f
            val indent = baseIndent + firstLineIndent
            val avail = (cfg.contentWidthPx - indent).coerceAtLeast(cfg.em * 2f)

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
                    advanceToOverflow(c, a, atom, lineStart, end, avail, cfg)
                }
                b = WidthAdjuster.phaseB(c, a, lineStart, end, cfg)
            }

            val overflowed = b.width > avail + EPS
            val line = Justifier.build(
                c = c,
                a = a,
                b = b,
                atom = atom,
                from = lineStart,
                until = end,
                indent = indent,
                avail = avail,
                isParagraphStart = first,
                isParagraphEnd = end >= c.count,
                role = p.role,
                overflowed = overflowed,
                cfg = cfg,
            )
            line.clusterStyles = MarkdownStyleMap.computeClusterStyles(line, p.inlineSpans)
            lines.add(line)

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
        a: WidthAdjuster.PhaseA,
        atom: IntArray,
        lineStart: Int,
        from: Int,
        avail: Float,
        cfg: LayoutConfig,
    ): Int {
        if (from >= c.count) return c.count

        if (AtomicUnits.exceedsLine(c, atom, from, avail)) {
            // 逃生：该单元比一行还长，降级为「任意簇边界可断」。
            //
            // 断在 from 本身 —— 它就是③④算出的「恰好放得下」的边界，已经是最优断点。
            // 曾经写成 from + 1，理由是「防死循环」，但那个理由不成立：
            // 调用点传进来的 end 已经 coerceAtLeast(lineStart + 1)，返回 from 必然前进。
            // 代价却是实打实的：逃生分支覆盖到的**每一行**都凭空多出一簇，
            // 稳定溢出半个到一个字宽，而且整行被标成 overflowed 从而放弃两端对齐。
            // 长省略号串、长破折号串、网址、长数字串在中文网文里都能触发。
            return from.coerceAtMost(c.count)
        }

        val id = atom[from]
        var k = from
        if (id != AtomicUnits.NONE) {
            while (k < c.count && atom[k] == id) k++
        } else {
            k = from + 1
        }
        // 跳出单元后再往前找一个不违反禁首的位置，最多再走几簇。
        // 除簇数上限外还有宽度闸门：连续禁首标点（「』」」这类）能让前扫一路推进，
        // 没有闸门时溢出量没有上界。宁可在这里停下接受一个禁首违例，
        // 也好过让一行冲出页面两三个字。
        var guard = 0
        val overflowBudget = avail + MAX_OVERFLOW_RATIO * avail
        while (k < c.count && guard < MAX_OVERFLOW_SCAN) {
            val ch = c.text[c.startInText[k]]
            if (!CharClass.isNoStart(c.klass[k], ch, cfg.strictKinsoku)) break
            if (a.width(lineStart, k + 1) > overflowBudget) break
            k++
            guard++
        }
        return k.coerceIn(from, c.count)
    }

    private fun skipLeadingSpaces(c: Clusters, from: Int): Int {
        var i = from
        while (i < c.count && c.klass[i] == CharClass.SPACE) i++
        return i
    }

    private const val MAX_SQUEEZE_IN_ROUNDS = 4
    private const val MAX_OVERFLOW_SCAN = 8

    /** 逃生前扫允许的最大额外溢出比例（相对可用宽度）。超过就停下，接受禁首违例。 */
    private const val MAX_OVERFLOW_RATIO = 0.15f
    const val EPS = 0.01f
}
