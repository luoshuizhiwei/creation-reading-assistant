package com.creationreadingassistant.feature.search

import com.creationreadingassistant.data.local.dao.SearchTermRow
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleEngine
import com.creationreadingassistant.feature.reader.rules.RuleScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 替换显示文通道索引器（纯函数）契约测试。
 *
 * 关键不变量（R2-S1.6）：
 *  - 无生效替换规则 → 返回空列表（与原文逐字一致，不重复建索引）；
 *  - 有规则 → 对原文套用规则后的**显示文**重新分词，offset 落在**显示文坐标空间**，
 *    与原文通道互不越界；
 *  - 规则正则失效 → 抛 [IllegalArgumentException]，由仓储捕获并记 FAILED，不拖垮整本书。
 *
 * 另含一项「反查」断言：用 [RuleEngine.applyReplace] 重放同样的规则得到的偏移映射，
 * 能把显示文偏移反查回原文章内偏移 —— 这正是 [com.creationreadingassistant.data.repository.SearchIndexRepository]
 * 在显示文命中上做精确到达（resolveLegacyOffset）所用的数学，必须可逆。
 */
class DisplayChannelIndexerTest {

    private val tokenizer = SearchTokenizer()

    private fun rule(pattern: String, replacement: String, enabled: Boolean = true) = ReplaceRule(
        id = "r-$pattern",
        name = "rule for $pattern",
        pattern = pattern,
        replacement = replacement,
        enabled = enabled,
        position = 0,
        scope = RuleScope.PER_BOOK,
    )

    private fun project(body: String, rules: List<ReplaceRule>, withOffsets: Boolean = true): List<SearchTermRow> =
        DisplayChannelIndexer.project(
            IndexUnit(chapterIndex = 1, title = "", body = body, withOffsets = withOffsets),
            bookId = "b1",
            rules = rules,
            tokenizer = tokenizer,
        )

    @Test
    fun `no rules yields no display rows`() {
        assertTrue(project("任何正文", rules = emptyList()).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bad regex throws so the repository can mark the channel failed`() {
        project("任何正文", rules = listOf(rule("[[", "")))
    }

    @Test
    fun `disabled replace rules leave the display text unchanged`() {
        // RuleEngine.applyReplace 会按 enabled 过滤：禁用规则不参与投影，
        // 因此显示文 == 原文，命中词来自原文「正文内容」（正文/文内/内容/容），而非替换后的「替换」。
        // 注意：body 非空时 project 总会产出 token（来自原文），不会因规则被禁用而归零。
        val r = rule("正文", "替换", enabled = false)
        val rows = project("正文内容", rules = listOf(r))
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.term in setOf("正文", "文内", "内容", "容") })
        assertFalse("禁用规则不应改变显示文，不应出现替换词", rows.any { it.term == "替换" })
    }

    @Test
    fun `offsets live in display coordinate space not source`() {
        // 原文「世界好美」，删除「好」→ 显示文「世界美」。
        // 显示文里「界美」这个 Bigram 落在显示偏移 1，但原文里「美」在偏移 3 —— 两者不同，
        // 说明索引写进 search_terms 的偏移是显示文坐标，绝不混用原文坐标。
        val rows = project("世界好美", rules = listOf(rule("好", "")))
        val jieMei = rows.first { it.term == "界美" }
        assertEquals("1:2", jieMei.offsets)
    }

    @Test
    fun `identity length replacement keeps display offset equal to source offset`() {
        // 「山峰」→「山巅」等长替换，显示文与原文逐字对齐；此时显示文偏移 == 原文章内偏移。
        val rows = project("云雾缭绕的山峰", rules = listOf(rule("山峰", "山巅")))
        val shanDian = rows.first { it.term == "山巅" }
        assertEquals("5:2", shanDian.offsets)
    }

    @Test
    fun `text basis is display`() {
        val rows = project("云雾缭绕的山峰", rules = listOf(rule("山峰", "山巅")))
        assertTrue(rows.all { it.text_basis == SearchTextBasis.DISPLAY.wire })
    }

    @Test
    fun `title and body are projected separately and weighted`() {
        // 标题按 ×2 加权、不记偏移；正文按 ×1 记偏移。
        val unit = IndexUnit(chapterIndex = 1, title = "第一章", body = "云雾缭绕的山峰", withOffsets = true)
        val rows = DisplayChannelIndexer.project(unit, bookId = "b1", rules = listOf(rule("山峰", "山巅")), tokenizer = tokenizer)
        val titleZh = rows.first { it.term == "一章" && it.offsets == null }
        assertEquals(2, titleZh.hits)
        val bodyShan = rows.first { it.term == "山巅" }
        assertEquals(1, bodyShan.hits)
        assertEquals("5:2", bodyShan.offsets)
    }

    @Test
    fun `display offset can be mapped back to source offset via rule replay`() {
        // 与 resolveLegacyOffset 的显示文精确到达同源：对原文重放同一组生效规则，
        // 用返回的偏移映射把显示文偏移反查回原文章内偏移。等长替换下应精确还原。
        val source = "云雾缭绕的山峰"
        val rules = listOf(rule("山峰", "山巅"))
        val displayText = RuleEngine.applyReplace(source, rules).displayText
        assertEquals("云雾缭绕的山巅", displayText)
        val displayOffset = displayText.indexOf("山")
        // 显示文「山」在偏移 5；原文章内「山」也在偏移 5（等长替换逐字对齐）。
        val sourceOffset = RuleEngine.applyReplace(source, rules).offsetMap.toSource(displayOffset)
        assertEquals(5, sourceOffset)
    }
}
