package com.creationreadingassistant.feature.reader.locator

/**
 * EPUB 全书字符偏移 ↔ (章节索引, 章内字符偏移) 的双向映射。
 *
 * 这是进度统一的核心坐标变换：所有格式的位置真源都是**全书字符偏移**，
 * 但 EPUB 的 locator / 笔记 / 搜索结果需要以「章 + 章内偏移」表达，才能在
 * 重排、换字号、原文微变后仍能回到同一句。
 *
 * [chapterStartOffsets] 来自 [com.creationreadingassistant.feature.reader.locator.LegacyOffsetCodec]
 * 的估算基准，**勿自行重算**（与历史 locator 同源，重算会漂移）。
 */
object EpubLocatorMapping {

    /** 全书偏移 → (章节索引, 章内字符偏移)。 */
    fun toChapterOffset(absOffset: Int, chapterStartOffsets: List<Int>): Pair<Int, Int> {
        val ci = run {
            var lo = 0
            var hi = chapterStartOffsets.lastIndex
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (chapterStartOffsets[mid] <= absOffset) lo = mid else hi = mid - 1
            }
            lo.coerceAtLeast(0)
        }
        val co = absOffset - chapterStartOffsets.getOrElse(ci) { 0 }
        return ci to co
    }

    /** (章节索引, 章内字符偏移) → 全书偏移。 */
    fun toAbsOffset(chapterIndex: Int, charOffset: Int, chapterStartOffsets: List<Int>): Int =
        chapterStartOffsets.getOrElse(chapterIndex) { 0 } + charOffset.coerceAtLeast(0)
}
