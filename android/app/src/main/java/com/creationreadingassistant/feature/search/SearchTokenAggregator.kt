package com.creationreadingassistant.feature.search

import com.creationreadingassistant.data.local.dao.SearchTermRow
import com.creationreadingassistant.feature.reader.rules.ReplaceProjection
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleEngine

/**
 * 搜索词项聚合（纯函数，零 Android / Room 依赖，可 JVM 单测）。
 *
 * 把一篇文档的 token 列表聚合为 `term → (加权命中数, 偏移 CSV)`。
 * 原索引写入侧（`SearchIndexRepository.addWeightedTokens`）与显示文通道写入侧
 * （[DisplayChannelIndexer]）共用同一套聚合逻辑，避免两套实现在权重 / 偏移口径上漂移。
 *
 * 偏移 CSV 格式与 `search_terms.offsets` 列一致：`"start:len,start:len,..."`，
 * 偏移相对**传入的 [fullText]**（原文通道传原文、显示文通道传显示文），
 * 因此两种基准各自落盘的坐标天然处在各自的文本空间内。
 */
object SearchTokenAggregator {

    /** 单 term 的聚合结果。 */
    data class AggregatedTerm(
        var hits: Int = 0,
        val offsetsCsv: StringBuilder = StringBuilder(),
    )

    /**
     * 单章/预览/元数据一次聚合允许的最大偏移 CSV 长度。
     *
     * 与 `SearchIndexRepository` 旧实现同源：每章 token 的偏移列表可能极长
     * （一本 20 万字书里「的是」这种 Bigram 可出现万次级），截断到 16KB 字符串，
     * 双保险（仓库侧写入时也会再做一次长度守卫）。
     */
    private const val MAX_OFFSETS_CSV = 16_000

    /**
     * 把 [tokens] 累加进 [agg]。
     *
     * @param weight 权重倍率（书名/章标题 ×2，正文 ×1）。
     * @param withOffsets 是否记录偏移（元数据命中不记偏移）。
     * @param fullText 偏移所相对的文本；为 null 时不记偏移（即使 [withOffsets] 为真）。
     */
    fun aggregateInto(
        agg: MutableMap<String, AggregatedTerm>,
        tokens: List<SearchTokenizer.TokenHit>,
        weight: Int,
        withOffsets: Boolean,
        fullText: String?,
    ) {
        for (tok in tokens) {
            val a = agg.getOrPut(tok.term) { AggregatedTerm() }
            a.hits += tok.count * weight
            if (withOffsets && fullText != null) {
                val iter = tok.offsets.iterator()
                while (iter.hasNext()) {
                    val offset = iter.next()
                    if (a.offsetsCsv.length >= MAX_OFFSETS_CSV) break // 避免 TEXT 列无限膨胀
                    if (a.offsetsCsv.isNotEmpty()) a.offsetsCsv.append(',')
                    a.offsetsCsv.append(offset).append(':').append(tok.term.length)
                }
            }
        }
    }
}

/**
 * 显示文索引单元：被索引的一段文本及其落点。
 *
 * 与原文通道一一对应：同一本书的原文单元与显示文单元共享 `chapterIndex`，
 * 区别只在于文本是否经过替换规则投影。
 *
 * [chapterSourceStart] 是 [body] 在全书 source 中的起始偏移（R4）：预览与元数据
 * 单元传 0；逐章单元传该章的全书起点（TXT 为 detector 真实偏移，EPUB 为
 * LegacyOffsetCodec 估算基址）。单处纠错锚点按它局部化后才能在索引侧应用。
 */
data class IndexUnit(
    /** 索引口径章节号（0 = 元数据/预览，≥1 = 逐章正文），与原文通道一致。 */
    val chapterIndex: Int,
    /** 章节标题（可被替换规则改写）。 */
    val title: String,
    /** 正文（预览或某一章正文，可被替换规则改写）。 */
    val body: String,
    /** 是否记录正文偏移（元数据命中为 false）。 */
    val withOffsets: Boolean,
    /** [body] 在全书 source 中的起点（0 = 全书文本/预览）。 */
    val chapterSourceStart: Int = 0,
)

/**
 * 替换显示文通道索引器（纯函数）。
 *
 * 设计边界：
 *  - 输入是原文通道同款的 [IndexUnit]（同一段文本），输出是 `text_basis = DISPLAY` 的
 *    [SearchTermRow] 列表；
 *  - offsets 在**显示文坐标空间**内（对投影后的文本重新分词得到），与原文通道互不越界；
 *  - [rules] 为空时直接返回空列表 —— 显示文与原文逐字一致，再建一套只是重复原文；
 *  - 规则正则失效（不可编译 / 可空匹配）时抛 [IllegalArgumentException]，由调用方捕获并
 *    把该书显示文覆盖记为 `FAILED`，**不让一条坏规则拖垮整本书的索引构建**。
 */
object DisplayChannelIndexer {

    /** 章标题相对正文的额外权重（与原文通道 `CHAPTER_TITLE_MULTIPLIER = 2` 对齐）。 */
    private const val CHAPTER_TITLE_MULTIPLIER = 2

    /**
     * 把单个单元投影为显示文索引行。
     *
     * 标题与正文分别投影，再合并进同一份聚合；标题按
     * [CHAPTER_TITLE_MULTIPLIER] 加权、不记偏移，正文记偏移。
     * 正文走 [ReplaceProjection.projectScoped]（按 [IndexUnit.chapterSourceStart]
     * 局部化单处纠错锚点）：普通正则规则与生效纠错都参与 display 文本，保证
     * 搜索 display 基准与阅读器渲染一致（R4 一致性契约）。标题没有 source
     * 锚点概念，只应用普通规则。
     */
    fun project(
        unit: IndexUnit,
        bookId: String,
        rules: List<ReplaceRule>,
        tokenizer: SearchTokenizer,
    ): List<SearchTermRow> {
        if (rules.isEmpty()) return emptyList()
        val plainRules = rules.filter { it.anchor == null }
        val agg = HashMap<String, SearchTokenAggregator.AggregatedTerm>()
        if (unit.title.isNotBlank()) {
            val displayTitle = RuleEngine.applyReplace(unit.title, plainRules).displayText
            SearchTokenAggregator.aggregateInto(
                agg,
                tokenizer.tokenizeDocument(displayTitle),
                CHAPTER_TITLE_MULTIPLIER,
                withOffsets = false,
                fullText = null,
            )
        }
        if (unit.body.isNotBlank()) {
            val displayBody = ReplaceProjection.projectScoped(
                sourceText = unit.body,
                rules = rules,
                bookId = bookId,
                scopeSourceBase = unit.chapterSourceStart,
            ).displayText
            SearchTokenAggregator.aggregateInto(
                agg,
                tokenizer.tokenizeDocument(displayBody),
                1,
                withOffsets = unit.withOffsets,
                fullText = displayBody,
            )
        }
        return agg.entries.map { (term, a) ->
            SearchTermRow(
                term = term,
                book_id = bookId,
                chapter_index = unit.chapterIndex,
                text_basis = SearchTextBasis.DISPLAY.wire,
                hits = a.hits,
                offsets = if (a.offsetsCsv.isNotEmpty()) a.offsetsCsv.toString() else null,
            )
        }
    }
}
