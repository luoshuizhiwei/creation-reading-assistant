package com.creationreadingassistant.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分词器边界测试：Bigram / 单字 / Latin 连续段 / 大小写归一 / 空白与标点跳过。
 *
 * 分词口径是全文搜索召回率的底座，任一偏移/词项算错都会让「能搜到却跳不到」或反过来说
 * 「搜不到明明存在的内容」。这里把行为钉死，避免以后有人把 Bigram 切成单字导致召回崩塌。
 */
class SearchTokenizerTest {

    private val tokenizer = SearchTokenizer()

    @Test
    fun `empty input yields no tokens`() {
        assertTrue(tokenizer.tokenize("").isEmpty())
        assertTrue(tokenizer.tokenizeDocument("").isEmpty())
    }

    @Test
    fun `cjk text is split into bigrams`() {
        // "中文小说"：相邻两字成 Bigram → 中文 / 文小 / 小说；
        // 末尾「说」后无后继 CJK 字，按孤立字补一个单字 token「说」（索引/查询同口径，召回一致）。
        val tokens = tokenizer.tokenize("中文小说")
        assertEquals(listOf("中文", "文小", "小说", "说"), tokens)
    }

    @Test
    fun `latin runs are kept as a single lowercase token`() {
        val tokens = tokenizer.tokenize("Hello World")
        assertEquals(listOf("hello", "world"), tokens)
    }

    @Test
    fun `mixed cjk and latin preserves both`() {
        // CJK 与数字/字母分属不同通道：汉字按 Bigram（孤立尾字补单字），数字走 latin 连续段。
        // "第3章" → 第 / 3 / 章；"Hello" → hello（小写）。
        val tokens = tokenizer.tokenize("第3章 Hello")
        assertEquals(listOf("第", "3", "章", "hello"), tokens)
    }

    @Test
    fun `punctuation and whitespace are skipped`() {
        // 标点/空白跳过；汉字 Bigram（孤立尾字补单字），数字走 latin 连续段。
        // "世界，你好！ 2024。" → 世界 / 界 / 你好 / 好 / 2024
        val tokens = tokenizer.tokenize("世界，你好！ 2024。")
        assertEquals(listOf("世界", "界", "你好", "好", "2024"), tokens)
    }

    @Test
    fun `tokenizeDocument reports counts and offsets`() {
        val hits = tokenizer.tokenizeDocument("中文中文")
        // 中→中文(0) 文→文中(1) 中→中文(2) 文→文(3)
        // → 中文 ×2 @[0,2]、文中 ×1 @[1]、文 ×1 @[3]
        assertEquals(3, hits.size)
        val zhongwen = hits.first { it.term == "中文" }
        assertEquals("中文", zhongwen.term)
        assertEquals(2, zhongwen.count)
        assertEquals(listOf(0, 2), zhongwen.offsets)
    }

    @Test
    fun `tokenizeForQuery adds unigrams for short phrases`() {
        // 短查询额外补单字（Bigram 有 1 字偏移漏匹配），提升召回
        val base = tokenizer.tokenize("小说")
        val query = tokenizer.tokenizeForQuery("小说")
        assertTrue("查询分词应额外包含单字小/说", query.containsAll(listOf("小", "说")))
        assertTrue("查询分词应保留基础 Bigram", query.containsAll(base))
    }

    @Test
    fun `tokenizeForQuery does not pad very long phrases`() {
        val longPhrase = "世界如此之大而人类如此之小不过是沧海一粟罢了"
        val query = tokenizer.tokenizeForQuery(longPhrase)
        val unigrams = longPhrase.map { it.toString() }
        // 超长时不补单字，避免查询词数爆炸
        assertFalse(query.containsAll(unigrams))
    }

    @Test
    fun `latin is lowercased for case insensitive match`() {
        val upper = tokenizer.tokenize("HELLO")
        val lower = tokenizer.tokenize("hello")
        assertEquals(upper, lower)
    }
}
