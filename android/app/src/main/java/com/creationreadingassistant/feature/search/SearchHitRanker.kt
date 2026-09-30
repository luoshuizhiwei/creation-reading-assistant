package com.creationreadingassistant.feature.search

/**
 * 搜索命中结果排名与多维度聚合纯算法。
 *
 * 职责纯粹：
 * 1. 活跃书隔离：过滤非活跃书籍的孤儿命中行；
 * 2. 多文本基准（原文 ORIGINAL / 替换显示文 DISPLAY）分组与最佳坐标（最佳章节与段落偏移）选取；
 * 3. 覆盖率状态与原因绑定；
 * 4. 最终打分降序、书 ID 升序、文本基准升序排列，并按 limit 做 Top-K 截断。
 *
 * 无任何 Android 上下文或数据库 DAO 依赖，百分之百纯内存计算。
 */
object SearchHitRanker {

    /**
     * 将从索引查出的原始词项匹配集合，聚合为最终用于阅读器与书架展示的 [SearchHit] 列表。
     *
     * @param rawMatches 原始多基准词项匹配列表。
     * @param activeBookIds 当前在册的活跃书籍 ID 集合（防历史已删孤儿行）。
     * @param coverageProvider 覆盖率状态提供器，输入 (bookId, textBasisWire)，输出覆盖率状态和原因。
     * @param limit 最大返回条数。
     */
    fun rank(
        rawMatches: List<RawTermMatch>,
        activeBookIds: Set<String>,
        coverageProvider: (bookId: String, textBasisWire: String) -> Pair<SearchCoverageState?, String?>? = { _, _ -> null },
        limit: Int = 50,
    ): List<SearchHit> {
        if (rawMatches.isEmpty() || limit <= 0) return emptyList()
        val live = rawMatches.filter { it.bookId in activeBookIds }
        if (live.isEmpty()) return emptyList()

        return live.groupBy { it.bookId to it.textBasis }
            .map { (key, matches) ->
                val (bookId, basis) = key
                val (chapterIndex, span) = SearchHitSelection.pickCoordinate(matches)
                val coverageInfo = coverageProvider(bookId, basis.wire)
                SearchHit(
                    bookId = bookId,
                    score = SearchHitSelection.scoreOf(matches),
                    textBasis = basis,
                    indexChapterIndex = chapterIndex,
                    charOffset = span?.start,
                    matchLength = span?.length,
                    coverage = coverageInfo?.first,
                    coverageReason = coverageInfo?.second,
                )
            }
            .sortedWith(
                compareByDescending<SearchHit> { it.score }
                    .thenBy { it.bookId }
                    .thenBy { it.textBasis.wire },
            )
            .take(limit)
    }
}
