package com.creationreadingassistant.feature.reader.locator

/**
 * EPUB 可见字符总量的渐进估计：已读章节用真实字符数，未读章节用
 * “已知真实数 / 对应 ZIP 估算数”的学习比率折算，避免首开全书扫描。
 */
object LearningProgressEstimator {
    fun percent(
        chapterIndex: Int,
        charOffset: Int,
        estimatedCounts: List<Int>,
        actualCounts: Map<Int, Int>,
        atBookEnd: Boolean = false,
    ): Float {
        if (estimatedCounts.isEmpty()) return 0f
        if (atBookEnd) return 100f

        var knownEstimated = 0L
        var knownActual = 0L
        actualCounts.forEach { (index, actual) ->
            val estimated = estimatedCounts.getOrNull(index) ?: return@forEach
            knownEstimated += estimated.coerceAtLeast(1)
            knownActual += actual.coerceAtLeast(0)
        }
        val ratio = if (knownEstimated > 0L) {
            (knownActual.toDouble() / knownEstimated).coerceIn(0.05, 1.0)
        } else {
            1.0
        }
        fun effective(index: Int): Double =
            actualCounts[index]?.coerceAtLeast(0)?.toDouble()
                ?: estimatedCounts[index].coerceAtLeast(1) * ratio

        val chapter = chapterIndex.coerceIn(0, estimatedCounts.lastIndex)
        var before = 0.0
        for (index in 0 until chapter) before += effective(index)
        val currentCount = effective(chapter)
        val position = before + charOffset.coerceAtLeast(0).coerceAtMost(currentCount.toInt())
        var total = 0.0
        for (index in estimatedCounts.indices) total += effective(index)
        if (total <= 0.0) return 0f
        return (position * 100.0 / total).toFloat().coerceIn(0f, 100f)
    }
}
