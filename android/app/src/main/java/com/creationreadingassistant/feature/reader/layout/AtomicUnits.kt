package com.creationreadingassistant.feature.reader.layout

/**
 * 原子单元：内部不允许断行的簇区间。
 *
 * 四类：
 * - **成对标点**：`——`、`……` 必须整体移动，拆开是明显的排版错误
 * - **西文单词**：连续的拉丁字母
 * - **数字串**：连续数字（含其中的小数点、千分位逗号）
 * - **人名间隔号**：`A·B` 式译名，间隔号两侧都不可断
 *
 * **逃生条款**：若某个原子单元本身比一行还长（比如 120 字符无空格的 URL），
 * 必须降级为「任意簇边界可断」。否则装行时该单元内恒无合法断点，
 * 回退耗尽后会死循环。这一条是三个候选方案里唯一被复核揪出来的语义漏洞。
 */
object AtomicUnits {

    /** 不属于任何原子单元的簇标记。 */
    const val NONE = -1

    /**
     * 为每簇标注所属原子单元 id。同 id 的相邻簇之间不可断行。
     *
     * @return 长度等于 [clusters].count 的数组，元素为单元 id 或 [NONE]
     */
    fun compute(clusters: Clusters): IntArray {
        val n = clusters.count
        val out = IntArray(n) { NONE }
        if (n == 0) return out

        var nextId = 0
        var i = 0
        while (i < n) {
            val k = clusters.klass[i]
            when {
                // 成对标点：连续的同类破折号/省略号归为一个单元
                k == CharClass.DASH || k == CharClass.ELLIPSIS -> {
                    var j = i
                    while (j < n && clusters.klass[j] == k) j++
                    if (j - i >= 2) markRange(out, i, j, nextId++)
                    i = j
                }
                // 西文词与数字串：连续的拉丁/数字视为一体
                CharClass.isLatinLike(k) -> {
                    var j = i
                    while (j < n && (CharClass.isLatinLike(clusters.klass[j]) || isInWordPunct(clusters, j))) j++
                    if (j - i >= 2) markRange(out, i, j, nextId++)
                    i = j
                }
                else -> i++
            }
        }

        // 间隔号：A·B 三簇成一体（人名不可断）
        for (idx in 1 until n - 1) {
            if (clusters.klass[idx] == CharClass.MIDDLE) {
                val id = if (out[idx - 1] != NONE) out[idx - 1] else nextId++
                markRange(out, idx - 1, idx + 2, id)
            }
        }
        return out
    }

    /**
     * 原子单元是否长于一行，需降级为可断。
     *
     * @return 该单元的簇宽总和是否超过 [avail]
     */
    fun exceedsLine(clusters: Clusters, atom: IntArray, at: Int, avail: Float): Boolean {
        val id = atom.getOrNull(at) ?: return false
        if (id == NONE) return false
        var lo = at
        while (lo > 0 && atom[lo - 1] == id) lo--
        var hi = at
        while (hi + 1 < atom.size && atom[hi + 1] == id) hi++
        var w = 0f
        for (i in lo..hi) w += clusters.advance[i]
        return w > avail
    }

    /** 单元内部的连接标点：小数点、千分位逗号、连字符、下划线、斜杠 */
    private fun isInWordPunct(clusters: Clusters, i: Int): Boolean {
        if (i <= 0 || i + 1 >= clusters.count) return false
        val c = clusters.text[clusters.startInText[i]]
        if (c != '.' && c != ',' && c != '-' && c != '_' && c != '/' && c != ':' && c != '\'') return false
        // 两侧都必须是西文，否则「句号 + 中文」会被误并
        return CharClass.isLatinLike(clusters.klass[i - 1]) &&
            CharClass.isLatinLike(clusters.klass[i + 1])
    }

    private fun markRange(out: IntArray, from: Int, until: Int, id: Int) {
        for (i in from until until.coerceAtMost(out.size)) out[i] = id
    }
}
