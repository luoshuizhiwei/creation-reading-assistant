package com.creationreadingassistant.feature.reader.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-替换（slice 1）：ReplaceProfile 稳定规则身份。
 *
 * key 必须覆盖有序生效规则的 id/pattern/replacement/enabled/position/scope/bookId；
 * 同输入重复稳定；任何影响执行语义（输出 / 适用范围 / 顺序）的字段变化都改变 key；
 * 显示名 name 不影响执行语义，刻意忽略；空规则有稳定 identity key。
 */
class ReplaceProfileKeyTest {

    private fun replaceRule(
        id: String = "r1",
        pattern: String = "bad",
        replacement: String = "good",
        enabled: Boolean = true,
        position: Int = 10,
        scope: RuleScope = RuleScope.GLOBAL,
    ) = ReplaceRule(
        id = id,
        name = "显示名-$id",
        pattern = pattern,
        replacement = replacement,
        enabled = enabled,
        position = position,
        scope = scope,
    )

    @Test
    fun `identical input produces the same key repeatedly`() {
        val rules = listOf(
            replaceRule("r1", "^广告\\d+$", "", position = 1),
            replaceRule("r2", "推广", "[推广]", position = 2),
        )
        val first = ReplaceProfile.key(bookId = "book-1", rules = rules)
        val second = ReplaceProfile.key(bookId = "book-1", rules = rules)
        assertEquals("同一配置的 key 必须稳定", first, second)
        assertEquals("值对象暴露同一 key", first, ReplaceProfile("book-1", rules).key)
    }

    @Test
    fun `input order is irrelevant when positions are distinct`() {
        val a = replaceRule("a", "^A$", "a", position = 1)
        val b = replaceRule("b", "^B$", "b", position = 2)
        assertEquals(
            "引擎按 position 排序，纯列表乱序不改变执行语义",
            ReplaceProfile.key("book-1", listOf(a, b)),
            ReplaceProfile.key("book-1", listOf(b, a)),
        )
    }

    @Test
    fun `same-position tie order changes the key like the engine order`() {
        val a = replaceRule("a", "^A$", "a", position = 5)
        val b = replaceRule("b", "^B$", "b", position = 5)
        assertNotEquals(
            "同 position 时引擎稳定排序保留传入顺序，顺序变化必须使 key 失效",
            ReplaceProfile.key("book-1", listOf(a, b)),
            ReplaceProfile.key("book-1", listOf(b, a)),
        )
    }

    @Test
    fun `swapping positions changes the key`() {
        val a = replaceRule("a", "^A$", "a", position = 1)
        val b = replaceRule("b", "^B$", "b", position = 2)
        val forward = ReplaceProfile.key("book-1", listOf(a, b))
        val swapped = ReplaceProfile.key("book-1", listOf(a.copy(position = 2), b.copy(position = 1)))
        assertNotEquals("position 影响应用顺序，变化后旧 key 必须失效", forward, swapped)
    }

    @Test
    fun `every execution-semantics field change alters the key`() {
        val base = replaceRule(
            id = "r1",
            pattern = "^广告$",
            replacement = "",
            enabled = true,
            position = 3,
            scope = RuleScope.GLOBAL,
        )
        val baseKey = ReplaceProfile.key("book-1", listOf(base))

        assertNotEquals("pattern", baseKey, ReplaceProfile.key("book-1", listOf(base.copy(pattern = "^推广$"))))
        assertNotEquals("replacement", baseKey, ReplaceProfile.key("book-1", listOf(base.copy(replacement = "x"))))
        assertNotEquals("position", baseKey, ReplaceProfile.key("book-1", listOf(base.copy(position = 4))))
        assertNotEquals("scope", baseKey, ReplaceProfile.key("book-1", listOf(base.copy(scope = RuleScope.PER_BOOK))))
        assertNotEquals("id", baseKey, ReplaceProfile.key("book-1", listOf(base.copy(id = "r2"))))
        assertNotEquals("bookId", baseKey, ReplaceProfile.key("book-2", listOf(base)))
        assertNotEquals("enabled 关闭后退出生效集合", baseKey, ReplaceProfile.key("book-1", listOf(base.copy(enabled = false))))
    }

    @Test
    fun `enabled filtering matches effective rules regardless of input list`() {
        val on = replaceRule("r1", "^A$", "a", position = 1)
        val off = replaceRule("r2", "^B$", "b", position = 2, enabled = false)
        assertEquals(
            "传完整列表与传生效列表必须同 key",
            ReplaceProfile.key("book-1", listOf(on)),
            ReplaceProfile.key("book-1", listOf(on, off)),
        )
    }

    @Test
    fun `display name is ignored by design`() {
        val a = replaceRule("r1", "^广告$", "", position = 1)
        val renamed = a.copy(name = "完全不同的显示名")
        assertEquals(
            "name 不影响执行语义，key 必须忽略",
            ReplaceProfile.key("book-1", listOf(a)),
            ReplaceProfile.key("book-1", listOf(renamed)),
        )
    }

    @Test
    fun `rule id stays part of the identity even when semantics match`() {
        val a = replaceRule("r1", "^广告$", "")
        val b = replaceRule("r2", "^广告$", "")
        assertNotEquals(
            "id 是规则管理身份，按需求纳入 key",
            ReplaceProfile.key("book-1", listOf(a)),
            ReplaceProfile.key("book-1", listOf(b)),
        )
    }

    @Test
    fun `empty effective rules have a stable identity key for any book`() {
        assertEquals(ReplaceProfile.EMPTY_KEY, ReplaceProfile.key("book-1", emptyList()))
        assertEquals(ReplaceProfile.EMPTY_KEY, ReplaceProfile.key("book-2", emptyList()))
        assertEquals("全禁用等价于空生效集合", ReplaceProfile.EMPTY_KEY, ReplaceProfile.key("book-1", listOf(replaceRule(enabled = false))))
        assertTrue("空 key 带版本前缀", ReplaceProfile.EMPTY_KEY.startsWith(ReplaceProfile.VERSION_PREFIX))
    }

    @Test
    fun `key has explicit version prefix and fixed sha256 length`() {
        val key = ReplaceProfile.key("book-1", listOf(replaceRule()))
        assertTrue("key 必须带明确版本前缀", key.startsWith("${ReplaceProfile.VERSION_PREFIX}-"))
        assertEquals("sha256 hex 长度", ReplaceProfile.VERSION_PREFIX.length + 1 + 64, key.length)
    }

    @Test
    fun `key never embeds raw rule content`() {
        val secretPattern = "广告_内容_隐私"
        val secretReplacement = "替换_内容_隐私"
        val key = ReplaceProfile.key(
            bookId = "secret-book",
            rules = listOf(replaceRule(pattern = secretPattern, replacement = secretReplacement)),
        )
        assertFalse("key 不得包含规则原文", key.contains(secretPattern))
        assertFalse("key 不得包含替换文本原文", key.contains(secretReplacement))
        assertFalse("key 不得包含 bookId 原文", key.contains("secret-book"))
        assertFalse("key 不得包含规则 id 原文", key.contains("r1"))
    }
}
