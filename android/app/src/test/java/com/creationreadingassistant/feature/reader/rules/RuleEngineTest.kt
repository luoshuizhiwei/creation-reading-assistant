package com.creationreadingassistant.feature.reader.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RuleEngine 行为测试：目录多规则并集（builtin 恒参与）+ 预览；
 * 替换按 position 顺序应用、删除/变长、预览与偏移映射。
 */
class RuleEngineTest {

    private fun tocRule(id: String, pattern: String?, builtin: Boolean, enabled: Boolean = true, scope: RuleScope = RuleScope.PER_BOOK, position: Int = 0) =
        TocRule(id = id, name = id, pattern = pattern, builtin = builtin, enabled = enabled, scope = scope, position = position)

    private fun replaceRule(id: String, pattern: String, replacement: String, enabled: Boolean = true, position: Int = 0) =
        ReplaceRule(id = id, name = id, pattern = pattern, replacement = replacement, enabled = enabled, position = position, scope = RuleScope.GLOBAL)

    // ── 目录并集 ────────────────────────────────────────────────────────

    @Test
    fun `standard builtin patterns always participate in the union`() {
        val patterns = RuleEngine.tocPatterns(emptyList())
        assertTrue("标准模式应恒参与", patterns.isNotEmpty())
        assertTrue(patterns.any { it.matches("第12章 夜行") })
        assertTrue(patterns.any { it.matches("卷三") })
        assertTrue(patterns.any { it.matches("Chapter 7") })
    }

    @Test
    fun `enabled seeded rule adds its patterns to the union`() {
        val rules = listOf(tocRule("num-dot", null, builtin = true))
        val patterns = RuleEngine.tocPatterns(rules)
        assertTrue("标准模式仍在", patterns.any { it.matches("第12章 夜行") })
        assertTrue("具名规则模式加入", patterns.any { it.matches("1. 序幕") })
    }

    @Test
    fun `disabled seed rule is excluded from the union`() {
        val rules = listOf(tocRule("num-dot", null, builtin = true, enabled = false))
        assertTrue(RuleEngine.tocPatterns(rules).none { it.matches("1. 序幕") })
    }

    @Test
    fun `custom rule contributes its own regex`() {
        val rules = listOf(tocRule("custom-1", "^foo-\\d+$", builtin = false))
        val patterns = RuleEngine.tocPatterns(rules)
        assertTrue(patterns.any { it.matches("foo-1") })
    }

    @Test
    fun `detectToc applies the union over the whole text`() {
        val body = "正文。".repeat(200)
        val text = "第1章 启程\n正文一$body\n1. 序幕\n正文二$body"
        val chapters = RuleEngine.detectToc(text, listOf(tocRule("num-dot", null, builtin = true)))
        val titles = chapters.map { it.title }
        assertEquals(listOf("第1章 启程", "1. 序幕"), titles)
    }

    @Test
    fun `detectToc with only standard rules ignores loose formats`() {
        val body = "正文。".repeat(200)
        val text = "第1章 启程\n正文$body\n1. 序幕"
        val chapters = RuleEngine.detectToc(text, emptyList())
        assertEquals(listOf("第1章 启程"), chapters.map { it.title })
    }

    @Test
    fun `user-selected rules disable the density fallback`() {
        val text = (1..20).joinToString("\n") { "$it. 密集小标题$it" }
        // 标准模式不识别「1. 」形态 -> 全文
        assertEquals(1, RuleEngine.detectToc(text, emptyList()).size)
        // 用户手选 num-dot：density 保护降级，全部命中
        assertEquals(20, RuleEngine.detectToc(text, listOf(tocRule("num-dot", null, builtin = true))).size)
    }

    @Test
    fun `previewToc returns hit count and sample titles`() {
        val body = "正文。".repeat(200)
        val text = (1..10).joinToString("\n") { "第${it}章 章节$it\n正文$body" }
        val preview = RuleEngine.previewToc(text, emptyList(), sampleLimit = 3)
        assertEquals(10, preview.chapterCount)
        assertEquals(listOf("第1章 章节1", "第2章 章节2", "第3章 章节3"), preview.sampleTitles)
        assertTrue(preview.ruleIds.isEmpty())
    }

    @Test
    fun `previewToc on empty text yields the full-text fallback chapter`() {
        val preview = RuleEngine.previewToc("", emptyList())
        assertEquals(1, preview.chapterCount)
        assertEquals(listOf("全文"), preview.sampleTitles)
    }

    // ── 替换应用 ────────────────────────────────────────────────────────

    @Test
    fun `single replace applies every hit`() {
        val result = RuleEngine.applyReplace(
            "hello world hello",
            listOf(replaceRule("r1", "hello", "hi", position = 0)),
        )
        assertEquals("hi world hi", result.displayText)
        assertEquals(2, result.hitCount)
    }

    @Test
    fun `empty replacement deletes the match`() {
        val result = RuleEngine.applyReplace(
            "abcXYZdef",
            listOf(replaceRule("r1", "XYZ", "", position = 0)),
        )
        assertEquals("abcdef", result.displayText)
        assertEquals(1, result.hitCount)
    }

    @Test
    fun `variable length replacements work both ways`() {
        val longer = RuleEngine.applyReplace("abcXYZdef", listOf(replaceRule("r1", "XYZ", "XYZW", position = 0)))
        assertEquals("abcXYZWdef", longer.displayText)
        val shorter = RuleEngine.applyReplace("abcXYZdef", listOf(replaceRule("r1", "XYZ", "XY", position = 0)))
        assertEquals("abcXYdef", shorter.displayText)
    }

    @Test
    fun `rules apply in position order regardless of input order`() {
        // A: a -> X；B: X -> Y。position 决定顺序，与传入顺序无关。
        val a = replaceRule("a", "a", "X", position = 1)
        val b = replaceRule("b", "X", "Y", position = 2)
        assertEquals("Ybc", RuleEngine.applyReplace("abc", listOf(b, a)).displayText)
        assertEquals("Ybc", RuleEngine.applyReplace("abc", listOf(a, b)).displayText)
        // 交换 position：先替换 X -> Y（无命中），再 a -> X
        val swapped = RuleEngine.applyReplace("abc", listOf(b.copy(position = 1), a.copy(position = 2)))
        assertEquals("Xbc", swapped.displayText)
    }

    @Test
    fun `a rule applies once per pass without cascading into itself`() {
        val result = RuleEngine.applyReplace("aaa", listOf(replaceRule("r1", "a", "aa", position = 0)))
        assertEquals("aaaaaa", result.displayText)
        assertEquals(3, result.hitCount)
    }

    @Test
    fun `disabled rules are skipped`() {
        val result = RuleEngine.applyReplace(
            "abc",
            listOf(
                replaceRule("on", "b", "B", position = 0),
                replaceRule("off", "a", "A", position = 1, enabled = false),
            ),
        )
        assertEquals("aBc", result.displayText)
        assertEquals(1, result.hitCount)
    }

    @Test
    fun `no rules yields identity text and zero hits`() {
        val result = RuleEngine.applyReplace("abc", emptyList())
        assertEquals("abc", result.displayText)
        assertEquals(0, result.hitCount)
        assertEquals(3, result.offsetMap.displayLength)
        assertEquals(3, result.offsetMap.sourceLength)
        for (i in 0..3) {
            assertEquals(i, result.offsetMap.toSource(i))
            assertEquals(i, result.offsetMap.toDisplay(i))
        }
    }

    @Test
    fun `engine result offset map stays monotonic with floor round trips`() {
        val result = RuleEngine.applyReplace(
            "abcXYZdef",
            listOf(replaceRule("r1", "XYZ", "", position = 0)),
        )
        val map = result.offsetMap
        assertEquals(3, map.toDisplay(4)) // 删除区间折叠
        assertEquals(7, map.toSource(4)) // 保留文本精确
        var prev = -1
        for (d in 0..map.displayLength) {
            val s = map.toSource(d)
            assertTrue(s >= prev)
            prev = s
            assertTrue(map.toDisplay(s) <= d)
        }
        prev = -1
        for (s in 0..map.sourceLength) {
            val d = map.toDisplay(s)
            assertTrue(d >= prev)
            prev = d
            assertTrue(map.toSource(d) <= s)
        }
    }

    @Test
    fun `previewReplace returns before after and hit count`() {
        val preview = RuleEngine.previewReplace(
            "aXbXc",
            listOf(replaceRule("r1", "X", "Y", position = 0)),
        )
        assertEquals("aXbXc", preview.before)
        assertEquals("aYbYc", preview.after)
        assertEquals(2, preview.hitCount)
    }

    @Test
    fun `empty-matchable or unparsable patterns fail loudly at apply time`() {
        try {
            RuleEngine.applyReplace("abc", listOf(replaceRule("bad", "a*", "x", position = 0)))
            assertTrue("可空匹配规则应在应用时抛错", false)
        } catch (e: IllegalArgumentException) {
            // 预期：保存前校验已拦截，引擎兜底保护
        }
        try {
            RuleEngine.applyReplace("abc", listOf(replaceRule("bad", "(abc", "x", position = 0)))
            assertTrue("无法编译的规则应在应用时抛错", false)
        } catch (e: IllegalArgumentException) {
            // 预期
        }
    }

    // ── TxtTocProfile 构造 ─────────────────────────────────────────────

    @Test
    fun `tocProfile with no rules is pure standard with density guard`() {
        val profile = RuleEngine.tocProfile(emptyList())
        assertTrue("纯标准应启用 density 保护", profile.densityGuard)
        assertTrue(profile.matches("第12章 夜行"))
        assertTrue(profile.matches("卷三"))
        assertFalse(profile.matches("1. 序幕"))
    }

    @Test
    fun `tocProfile with only standard rule keeps density guard`() {
        val profile = RuleEngine.tocProfile(
            listOf(tocRule("builtin", null, builtin = true, scope = RuleScope.GLOBAL, position = 0))
        )
        assertTrue(profile.densityGuard)
        assertTrue(profile.matches("第12章 夜行"))
    }

    @Test
    fun `tocProfile with loose seed rule disables density guard and unions patterns`() {
        val profile = RuleEngine.tocProfile(listOf(tocRule("num-dot", null, builtin = true)))
        assertFalse("用户手选宽松规则应关闭 density 保护", profile.densityGuard)
        assertTrue("宽松规则模式加入并集", profile.matches("1. 序幕"))
        assertTrue("标准模式恒参与", profile.matches("第12章 夜行"))
    }

    @Test
    fun `tocProfile with custom rule contributes regex and disables density guard`() {
        val profile = RuleEngine.tocProfile(listOf(tocRule("custom-1", "^foo-\\d+$", builtin = false)))
        assertFalse(profile.densityGuard)
        assertTrue(profile.matches("foo-7"))
        assertTrue(profile.matches("第12章 夜行"))
    }

    @Test
    fun `tocProfile key is a stable fingerprint for identical rules`() {
        val rules = listOf(
            tocRule("num-dot", null, builtin = true, position = 1),
            tocRule("custom-1", "^foo-\\d+$", builtin = false, position = 2),
        )
        val first = RuleEngine.tocProfile(rules).key
        repeat(3) { assertEquals(first, RuleEngine.tocProfile(rules).key) }
    }

    @Test
    fun `tocProfile key changes when rule id pattern or position changes`() {
        val base = listOf(tocRule("num-dot", null, builtin = true, position = 1))
        val baseKey = RuleEngine.tocProfile(base).key
        assertNotEquals(
            "id 变化必须换 key",
            baseKey,
            RuleEngine.tocProfile(listOf(tocRule("num-bare", null, builtin = true, position = 1))).key,
        )
        assertNotEquals(
            "pattern 变化必须换 key",
            baseKey,
            RuleEngine.tocProfile(listOf(tocRule("num-dot", "^x$", builtin = false, position = 1))).key,
        )
        assertNotEquals(
            "多规则顺序 / position 变化必须换 key",
            baseKey,
            RuleEngine.tocProfile(
                listOf(
                    tocRule("num-dot", null, builtin = true, position = 2),
                    tocRule("custom-1", "^x$", builtin = false, position = 1),
                ),
            ).key,
        )
    }

    @Test
    fun `tocProfile key changes when enabled state changes`() {
        val enabled = listOf(tocRule("num-dot", null, builtin = true, position = 1))
        val disabled = listOf(tocRule("num-dot", null, builtin = true, enabled = false, position = 1))
        assertNotEquals(
            "enabled 状态变化必须换 key",
            RuleEngine.tocProfile(enabled).key,
            RuleEngine.tocProfile(disabled).key,
        )
    }

    @Test
    fun `tocProfile key is deterministic across instances of the same configuration`() {
        val a = RuleEngine.tocProfile(listOf(tocRule("c1", "^a$", builtin = false, position = 3))).key
        val b = RuleEngine.tocProfile(listOf(tocRule("c1", "^a$", builtin = false, position = 3))).key
        assertEquals(a, b)
    }
}
