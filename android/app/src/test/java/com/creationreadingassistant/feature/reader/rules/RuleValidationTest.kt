package com.creationreadingassistant.feature.reader.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保存前校验：正则必须能编译、不得匹配空串、不得有灾难性回溯风险。
 */
class RuleValidationTest {

    private fun errors(result: RuleValidationResult): Set<RuleValidationError> = result.errors.toSet()

    @Test
    fun `valid replace pattern and empty replacement are accepted`() {
        assertTrue(RuleEngine.validateReplaceRule("第\\d+章", "").valid)
        assertTrue(RuleEngine.validateReplaceRule("第\\d+章", "第X章").valid)
    }

    @Test
    fun `unbalanced parenthesis is an invalid regex`() {
        val result = RuleEngine.validateReplaceRule("(abc", "x")
        assertFalse(result.valid)
        assertEquals(setOf(RuleValidationError.INVALID_REGEX), errors(result))
    }

    @Test
    fun `empty pattern matches empty string and is rejected`() {
        val result = RuleEngine.validateReplaceRule("", "x")
        assertFalse(result.valid)
        assertEquals(setOf(RuleValidationError.EMPTY_MATCH), errors(result))
    }

    @Test
    fun `patterns that can match empty string are rejected`() {
        for (pattern in listOf("a*", "a?", "(a|)", "^$", "\\b*", "x{0,2}")) {
            val result = RuleEngine.validateReplaceRule(pattern, "y")
            assertFalse("应拒绝可空匹配: $pattern", result.valid)
            assertEquals("$pattern 应判 EMPTY_MATCH", setOf(RuleValidationError.EMPTY_MATCH), errors(result))
        }
    }

    @Test
    fun `anchored non-empty patterns stay valid`() {
        assertTrue(RuleEngine.validateReplaceRule("^第\\d+章$", "x").valid)
        assertTrue(RuleEngine.validateReplaceRule("(?m)^[0-9]{1,4}[.、]", "x").valid)
    }

    @Test
    fun `nested quantifiers are catastrophic risk`() {
        for (pattern in listOf("(a+)+", "(a*)*", "(?:a+)+", "(a|b*)+", "(a{2,})+", "(a(b+))+", "((ab)+)+")) {
            val result = RuleEngine.validateReplaceRule(pattern, "y")
            assertFalse("应拒绝灾难性回溯: $pattern", result.valid)
            assertEquals(
                "$pattern 应判 CATASTROPHIC_RISK",
                setOf(RuleValidationError.CATASTROPHIC_RISK),
                errors(result),
            )
        }
    }

    @Test
    fun `quantified groups without nested quantifiers are safe`() {
        for (pattern in listOf("(ab)+", "(a|b)+", "(?:第[0-9]{1,12}章)", "(a\\+)+", "[()+]+", "(?<=x)a+", "a++")) {
            val result = RuleEngine.validateReplaceRule(pattern, "y")
            assertTrue("应接受无嵌套量词: $pattern", result.valid)
        }
    }

    @Test
    fun `toc custom rule uses the same regex checks`() {
        assertTrue(RuleEngine.validateTocRule("^第\\d+章").valid)
        assertFalse(RuleEngine.validateTocRule("(a+)+").valid)
        assertFalse(RuleEngine.validateTocRule("a*").valid)
        assertFalse(RuleEngine.validateTocRule("(").valid)
        // 零个或多个空格等价于可空匹配，同样拒绝
        assertFalse(RuleEngine.validateTocRule(" *").valid)
    }

    @Test
    fun `null pattern is valid (builtin code-seeded rule)`() {
        assertTrue(RuleEngine.validateTocRule(null).valid)
    }
}
