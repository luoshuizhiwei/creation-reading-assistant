package com.creationreadingassistant.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 词项聚合（纯函数）契约测试。
 *
 * 索引写入侧与显示文通道写入侧共用 [SearchTokenAggregator.aggregateInto]，
 * 权重 / 偏移口径一旦漂移，两套通道就会对不上。把这些边界钉死。
 */
class SearchTokenAggregatorTest {

    private val tokenizer = SearchTokenizer()

    private fun aggregate(text: String, weight: Int, withOffsets: Boolean = true): Map<String, SearchTokenAggregator.AggregatedTerm> {
        val agg = HashMap<String, SearchTokenAggregator.AggregatedTerm>()
        SearchTokenAggregator.aggregateInto(agg, tokenizer.tokenizeDocument(text), weight, withOffsets, if (withOffsets) text else null)
        return agg
    }

    @Test
    fun `weight multiplies the hit count`() {
        // 「中文」出现 2 次，权重 ×2 → hits = 4
        val agg = aggregate("中文中文", weight = 2)
        assertEquals(4, agg["中文"]!!.hits)
    }

    @Test
    fun `offsets are recorded relative to the supplied full text`() {
        val agg = aggregate("中文中文", weight = 1)
        // 两个「中文」分别在偏移 0、2
        assertEquals("0:2,2:2", agg["中文"]!!.offsetsCsv.toString())
    }

    @Test
    fun `without offsets flag no csv is produced even when fullText present`() {
        val agg = HashMap<String, SearchTokenAggregator.AggregatedTerm>()
        // 关键：withOffsets=false 时即使传了 fullText，也不记录偏移
        SearchTokenAggregator.aggregateInto(agg, tokenizer.tokenizeDocument("中文中文"), 1, withOffsets = false, fullText = "中文中文")
        assertTrue(agg["中文"]!!.offsetsCsv.isEmpty())
    }

    @Test
    fun `no fullText means no offsets even when requested`() {
        val agg = HashMap<String, SearchTokenAggregator.AggregatedTerm>()
        SearchTokenAggregator.aggregateInto(agg, tokenizer.tokenizeDocument("中文"), 1, withOffsets = true, fullText = null)
        assertTrue(agg["中文"]!!.offsetsCsv.isEmpty())
    }

    @Test
    fun `multiple terms are kept distinct`() {
        // "中文小说" 经 tokenizeDocument → 中文 / 文小 / 小说 / 说（尾字「说」补单字），共 4 个 term。
        val agg = aggregate("中文小说", weight = 1)
        assertEquals(4, agg.size)
        assertTrue(agg.containsKey("中文"))
        assertTrue(agg.containsKey("文小"))
        assertTrue(agg.containsKey("小说"))
        assertTrue(agg.containsKey("说"))
    }

    @Test
    fun `aggregation is additive across calls`() {
        val agg = HashMap<String, SearchTokenAggregator.AggregatedTerm>()
        SearchTokenAggregator.aggregateInto(agg, tokenizer.tokenizeDocument("中文"), 1, withOffsets = false, fullText = null)
        SearchTokenAggregator.aggregateInto(agg, tokenizer.tokenizeDocument("中文"), 1, withOffsets = false, fullText = null)
        // 两次聚合「中文」各命中 1 次
        assertEquals(2, agg["中文"]!!.hits)
    }

    @Test
    fun `offset csv is truncated to guard string blowup`() {
        // 构造一个极长的单 token 重复文本，偏移列表会超过上限，必须被截断而非无限膨胀。
        val term = "中文"
        val repeated = term.repeat(20_000)
        val agg = aggregate(repeated, weight = 1)
        val csv = agg[term]!!.offsetsCsv.toString()
        // 上限由 SearchTokenAggregator 内部 16_000 把守；这里只断言它确实被限住了。
        assertTrue("偏移 CSV 必须被长度上限截断，不能无限增长", csv.length <= 16_000)
        assertFalse("截断后的 CSV 不应以逗号结尾", csv.endsWith(","))
    }
}
