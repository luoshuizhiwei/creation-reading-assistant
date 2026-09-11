package com.creationreadingassistant.feature.search

/**
 * `search_terms.offsets` 的解析。
 *
 * 存储格式是 CSV 片段串 `"start:len,start:len,..."`，来自
 * `SearchIndexRepository` 的写入侧（`addWeightedTokens`）。
 * 该列在 v11 就已经在写，但**查询侧从未读过** —— R2-S1.2 首次消费它，
 * 搜索结果这才有了「跳到第几章第几个字」的坐标。
 */
object SearchOffsets {

    /**
     * 解析 offsets CSV，返回最多 [limit] 个合法区间。
     *
     * 容错原则：单个坏片段（空段、非数字、负数、零长度）**只跳过自己**，
     * 不让整条命中失效 —— 宁可少一个精确偏移，也不要把这本书从结果里弄丢。
     */
    fun parse(csv: String?, limit: Int = 1): List<SearchMatchSpan> {
        if (csv.isNullOrBlank() || limit <= 0) return emptyList()
        val max = limit.coerceAtMost(16)
        val out = ArrayList<SearchMatchSpan>(max)
        var i = 0
        while (i < csv.length && out.size < max) {
            var comma = csv.indexOf(',', i)
            if (comma < 0) comma = csv.length
            val segment = csv.substring(i, comma)
            val separator = segment.indexOf(':')
            if (separator > 0) {
                val start = segment.substring(0, separator).toIntOrNull()
                val length = segment.substring(separator + 1).toIntOrNull()
                if (start != null && length != null && start >= 0 && length > 0) {
                    out.add(SearchMatchSpan(start, length))
                }
            }
            i = comma + 1
        }
        return out
    }
}

/**
 * 一条原始匹配：某个查询词在某本书、某章、某基准上的一次命中。
 *
 * 结构刻意扁平（不含 DAO / Android 类型），好让「怎么从一堆匹配里挑出展示坐标」
 * 变成一个可以纯函数断言的问题。
 */
data class RawTermMatch(
    val bookId: String,
    val textBasis: SearchTextBasis,
    /** 索引口径章节号（0 = 元数据/预览，≥1 = 逐章）。 */
    val chapterIndex: Int,
    val term: String,
    /** 该 term 在这行的 hits（已含元数据/章标题加权）。 */
    val hits: Int,
    /** 该 term 在查询串中出现了几次。 */
    val queryTermCount: Int,
    /** 首个命中区间；元数据行（offsets 为空）为 null。 */
    val span: SearchMatchSpan?,
)

/**
 * 命中打分与坐标挑选 —— 纯函数，无任何副作用。
 */
object SearchHitSelection {

    /** 加权分：term 在查询中出现越多次、该行 hits 越高，分越高。 */
    fun scoreOf(matches: List<RawTermMatch>): Int =
        matches.sumOf { it.hits * it.queryTermCount }

    /**
     * 挑出「这本书这个基准」用于展示的唯一坐标。
     *
     * 规则（有意写成显式顺序，便于逐条断言）：
     *  1. **先按该章命中了几个不同的查询词降序** —— 多词共现的那一章才是用户想看的那段，
     *     比「词频最高的章」更符合直觉；
     *  2. 同分取**索引章节号最小**的（书里最靠前的一处）；
     *  3. 章内取**偏移最小**的命中区间。
     *
     * 没有任何区间时（例如只命中了书名/作者）返回 `0 to null`，
     * 由调用方降级为「只能打开书，不能跳位置」。
     */
    fun pickCoordinate(matches: List<RawTermMatch>): Pair<Int, SearchMatchSpan?> {
        if (matches.isEmpty()) return 0 to null
        val ranked = matches.groupBy { it.chapterIndex }
            .map { (chapter, rows) ->
                ChapterGroup(
                    chapterIndex = chapter,
                    distinctTermCount = rows.mapTo(HashSet()) { it.term }.size,
                    rows = rows,
                )
            }
            .sortedWith(
                compareByDescending<ChapterGroup> { it.distinctTermCount }
                    .thenBy { it.chapterIndex },
            )
        val best = ranked.first()
        val span = best.rows
            .mapNotNull { it.span }
            .minWithOrNull(compareBy<SearchMatchSpan> { it.start }.thenBy { it.length })
        return best.chapterIndex to span
    }

    private data class ChapterGroup(
        val chapterIndex: Int,
        val distinctTermCount: Int,
        val rows: List<RawTermMatch>,
    )
}
