package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TxtTocProfile：目录规则并集的执行快照（稳定 key + 模式并集 + density 开关）。
 * 覆盖旧 ruleId 构造（[TxtTocProfile.fromRuleId]）与显式构造的匹配 / 密度语义。
 */
class TxtTocProfileTest {

    @Test
    fun `legacy standard ruleId yields standard patterns and density guard`() {
        val profile = TxtTocProfile.fromRuleId("builtin")
        assertTrue(profile.matches("第12章 夜行"))
        assertTrue(profile.matches("卷三"))
        assertTrue(profile.matches("Chapter 7"))
        assertFalse("宽松形态不得被标准规则命中", profile.matches("1. 序幕"))
        assertTrue("纯标准应启用 density 保护", profile.densityGuard)
        // builtin key 带版本戳：识别语义升级（新平台规则/自动嗅探）后旧缓存自动失效
        assertEquals("builtin:s2", profile.key)
    }

    @Test
    fun `legacy loose ruleId adds its patterns and disables density guard`() {
        val profile = TxtTocProfile.fromRuleId("num-dot")
        assertTrue(profile.matches("1. 序幕"))
        assertTrue("标准模式恒参与", profile.matches("第12章 夜行"))
        assertFalse("用户手选宽松规则 = 用户背书，关闭自动兜底", profile.densityGuard)
        assertEquals("num-dot", profile.key)
    }

    @Test
    fun `legacy unknown ruleId keeps standard patterns but disables density guard`() {
        val profile = TxtTocProfile.fromRuleId("unknown-id")
        assertTrue(profile.matches("第12章 夜行"))
        assertFalse(profile.matches("1. 序幕"))
        assertFalse(profile.densityGuard)
        assertEquals("unknown-id", profile.key)
    }

    @Test
    fun `explicit profile matches its own pattern union`() {
        val profile = TxtTocProfile(
            key = "custom-key",
            patterns = listOf(Regex("^第\\d+章$"), Regex("^foo-\\d+$")),
            densityGuard = false,
        )
        assertTrue(profile.matches("第3章"))
        assertTrue(profile.matches("foo-42"))
        assertFalse(profile.matches("bar"))
        assertFalse(profile.densityGuard)
        assertEquals("custom-key", profile.key)
    }
}
