package com.creationreadingassistant.feature.reader.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-A：TxtTocProfile.key 必须兼容旧单规则身份，并在规则集合 / 顺序 / 内容
 * 变化时失效（key 变化）。标准内置恒参与并集。
 */
class RuleEngineTocProfileKeyTest {

    private fun tocRule(
        id: String,
        pattern: String?,
        builtin: Boolean,
        enabled: Boolean = true,
        position: Int = 0,
    ) = TocRule(
        id = id,
        name = id,
        pattern = pattern,
        builtin = builtin,
        enabled = enabled,
        scope = RuleScope.PER_BOOK,
        position = position,
    )

    private val standard = tocRule(BuiltinTocRules.STANDARD_ID, null, builtin = true, position = 0)
    private val numDot = tocRule("num-dot", null, builtin = true, position = 1)
    private val bracketed = tocRule("bracketed", null, builtin = true, position = 2)

    @Test
    fun `standard-only effective rules keep the legacy builtin key`() {
        val profile = RuleEngine.tocProfile(listOf(standard))
        assertEquals(BuiltinTocRules.STANDARD_ID, profile.key)
        assertTrue(profile.densityGuard)
    }

    @Test
    fun `single loose builtin keeps its semantic id as key`() {
        val profile = RuleEngine.tocProfile(listOf(standard, numDot))
        assertEquals("num-dot", profile.key)
        assertTrue("用户背书规则应关闭 density 兜底", !profile.densityGuard)
    }

    @Test
    fun `multi-rule profile uses a stable fingerprint distinct from single ids`() {
        val profile = RuleEngine.tocProfile(listOf(standard, numDot, bracketed))
        val again = RuleEngine.tocProfile(listOf(standard, numDot, bracketed))

        assertTrue(profile.key.startsWith("toc-"))
        assertEquals("同一配置的 key 必须稳定", again.key, profile.key)
        assertNotEquals("多规则 key 不得退化成单规则 id", "num-dot", profile.key)
        assertNotEquals(BuiltinTocRules.STANDARD_ID, profile.key)
    }

    @Test
    fun `key changes when effective rule set or enabled state changes`() {
        val base = RuleEngine.tocProfile(listOf(standard, numDot)).key
        val added = RuleEngine.tocProfile(listOf(standard, numDot, bracketed)).key
        val disabled = RuleEngine.tocProfile(
            listOf(standard, numDot.copy(enabled = false), bracketed),
        ).key

        assertNotEquals("规则集合变化后旧 key 必须失效", base, added)
        assertNotEquals("启停变化后旧 key 必须失效", added, disabled)
    }

    @Test
    fun `key changes when custom rule order changes`() {
        val first = tocRule("c1", "^甲\\d+$", builtin = false, position = 10)
        val second = tocRule("c2", "^乙\\d+$", builtin = false, position = 11)

        val a = RuleEngine.tocProfile(listOf(standard, first, second)).key
        val b = RuleEngine.tocProfile(listOf(standard, second.copy(position = 10), first.copy(position = 11))).key

        assertNotEquals("顺序变化后旧 key 必须失效", a, b)
    }

    @Test
    fun `key changes when custom rule pattern changes`() {
        val before = RuleEngine.tocProfile(
            listOf(standard, tocRule("c1", "^甲\\d+$", builtin = false)),
        ).key
        val after = RuleEngine.tocProfile(
            listOf(standard, tocRule("c1", "^丙\\d+$", builtin = false)),
        ).key

        assertNotEquals("规则内容变化后旧 key 必须失效", before, after)
    }

    @Test
    fun `ruleSnapshot effectiveTocProfile derives from enabled toc rules`() {
        val snapshot = RuleSnapshot(
            bookId = "b1",
            tocRules = listOf(standard, numDot),
            replaceRules = emptyList(),
            effectiveToc = listOf(standard, numDot),
            effectiveReplace = emptyList(),
        )

        assertEquals("num-dot", snapshot.effectiveTocProfile.key)
    }
}
