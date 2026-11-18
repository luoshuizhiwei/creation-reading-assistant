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
    fun compute(clusters: Clusters, checkActive: () -> Unit = {}): IntArray {
        val n = clusters.count
        val out = IntArray(n) { i ->
            if ((i and 255) == 0) checkActive()
            NONE
        }
        if (n == 0) return out

        var nextId = 0
        var i = 0
        var nextCheck = 0
        while (i < n) {
            if (i >= nextCheck) {
                checkActive()
                nextCheck = i + 256
            }
            val k = clusters.klass[i]
            when {
                // 成对标点：连续的同类破折号/省略号归为一个单元
                k == CharClass.DASH || k == CharClass.ELLIPSIS -> {
                    var j = i
                    while (j < n && clusters.klass[j] == k) {
                        if ((j and 255) == 0) checkActive()
                        j++
                    }
                    if (j - i >= 2) markRange(out, i, j, nextId++, checkActive)
                    i = j
                }
                // 叠用叹号/问号（如 ！？、？！、！！、？？）：连续出现成对的情感标点不可拆行
                isExclamationOrQuestion(clusters.text[clusters.startInText[i]]) -> {
                    var j = i
                    while (j < n && isExclamationOrQuestion(clusters.text[clusters.startInText[j]])) {
                        if ((j and 255) == 0) checkActive()
                        j++
                    }
                    if (j - i >= 2) markRange(out, i, j, nextId++, checkActive)
                    i = j
                }
                // 西文词与数字串：连续的拉丁/数字视为一体
                CharClass.isLatinLike(k) -> {
                    var j = i
                    while (j < n && (CharClass.isLatinLike(clusters.klass[j]) || isInWordPunct(clusters, j))) {
                        if ((j and 255) == 0) checkActive()
                        j++
                    }
                    if (j - i >= 2) markRange(out, i, j, nextId++, checkActive)
                    i = j
                }
                else -> i++
            }
        }

        // 间隔号：A·B 三簇成一体（人名不可断）。
        // 只认人名间隔号 ·・‧，不认分隔号 ／ / —— 否则 URL 末尾的 `/` 会把紧随其后的
        // 中文也粘进这个原子单元里。
        for (idx in 1 until n - 1) {
            if ((idx and 255) == 0) checkActive()
            if (CharClass.isNameSeparator(clusters.text[clusters.startInText[idx]])) {
                val id = if (out[idx - 1] != NONE) out[idx - 1] else nextId++
                out[idx - 1] = id
                out[idx] = id
                out[idx + 1] = id
            }
        }
        return out
    }

    internal data class Run(val start: Int, val endExclusive: Int, val width: Float) {
        operator fun contains(index: Int): Boolean = index >= start && index < endExclusive
    }

    internal fun runAt(clusters: Clusters, atom: IntArray, at: Int, checkActive: () -> Unit = {}): Run? {
        val id = atom[at]
        if (id == NONE) return null
        var start = at
        while (start > 0 && atom[start - 1] == id) {
            if ((start and 255) == 0) checkActive()
            start--
        }
        var endExclusive = at + 1
        while (endExclusive < atom.size && atom[endExclusive] == id) {
            if ((endExclusive and 255) == 0) checkActive()
            endExclusive++
        }
        var width = 0f
        for (i in start until endExclusive) {
            if ((i and 255) == 0) checkActive()
            width += clusters.advance[i]
        }
        return Run(start, endExclusive, width)
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

    private fun markRange(out: IntArray, from: Int, until: Int, id: Int, checkActive: () -> Unit) {
        for (i in from until until.coerceAtMost(out.size)) {
            if ((i and 255) == 0) checkActive()
            out[i] = id
        }
    }

    private fun isExclamationOrQuestion(c: Char): Boolean =
        c == '！' || c == '？' || c == '!' || c == '?'
}
