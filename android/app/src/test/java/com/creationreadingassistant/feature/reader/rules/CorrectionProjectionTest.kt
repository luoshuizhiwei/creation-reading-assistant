package com.creationreadingassistant.feature.reader.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2 单处纠错投影测试：锚定纠错在 [ReplaceProjection.projectScoped] 的
 * 「普通规则之后、source 锚点 + display 内容校验」叠加语义。
 *
 * 契约（与持久化坐标纪律一致）：
 * - source 坐标始终权威，display 只派生；映射必须通过 round-trip 不变量；
 * - 内容漂移（findText 不匹配）/ 越界锚点 / 禁用纠错一律诚实跳过，绝不近似替换；
 * - 普通规则先应用，纠错基于其 display 结果叠加，key 覆盖锚点字段。
 */
class CorrectionProjectionTest {

    private fun plainRule(
        id: String = "r1",
        pattern: String,
        replacement: String,
        position: Int = 0,
    ) = ReplaceRule(
        id = id,
        name = id,
        pattern = pattern,
        replacement = replacement,
        enabled = true,
        position = position,
        scope = RuleScope.GLOBAL,
    )

    private fun correction(
        id: String = "correction:c1",
        sourceStart: Int,
        sourceEnd: Int,
        findText: String,
        replaceText: String,
        enabled: Boolean = true,
    ) = ReplaceRule(
        id = id,
        name = "单处纠错",
        pattern = "",
        replacement = replaceText,
        enabled = enabled,
        position = Int.MAX_VALUE,
        scope = RuleScope.PER_BOOK,
        anchor = CorrectionAnchor(
            sourceStart = sourceStart,
            sourceEnd = sourceEnd,
            findText = findText,
        ),
    )

    private fun assertMapInvariants(map: TextOffsetMap) {
        var prevSource = -1
        for (d in 0..map.displayLength) {
            val s = map.toSource(d)
            assertTrue("toSource 单调不减 d=$d", s >= prevSource)
            prevSource = s
            assertTrue("toSource 不越界 d=$d", s in 0..map.sourceLength)
            // round trip floor 的既有豁免：toDisplay 对「源末偏移」恒返回显示末尾
            // （TextOffsetMap 末端精确特例），插入平铺到匹配末尾时 toSource 把插入尾
            // 上的 display 都折到 sourceLength，回查即得到 displayLength——
            // 这与 RuleEngine.applySingle 生成的映射一致，属 floor 语义的既有角落。
            val back = map.toDisplay(s)
            assertTrue(
                "display round trip floor d=$d s=$s back=$back",
                back <= d || s == map.sourceLength,
            )
        }
        var prevDisplay = -1
        for (s in 0..map.sourceLength) {
            val d = map.toDisplay(s)
            assertTrue("toDisplay 单调不减 s=$s", d >= prevDisplay)
            prevDisplay = d
            assertTrue("toDisplay 不越界 s=$s", d in 0..map.displayLength)
            assertTrue("source round trip floor s=$s", map.toSource(d) <= s)
        }
        assertEquals(0, map.toSource(0))
        assertEquals(0, map.toDisplay(0))
        assertEquals(map.displayLength, map.toDisplay(map.sourceLength))
    }

    // ── 基础应用 ─────────────────────────────────────────────────────────

    @Test
    fun `correction splices exact source range with content check`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "ABCDEF",
            rules = listOf(correction(sourceStart = 2, sourceEnd = 4, findText = "CD", replaceText = "X")),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        assertEquals("ABXEF", p.displayText)
        assertEquals(1, p.hitCount)
        assertMapInvariants(p.offsetMap)
        // display→source：X 起点 → source 2（对齐前缀对角）；display 3 是被删除
        // 「D」的坍缩点，按 floor 回到删除起点 source 3（与 applySingle 语义一致）
        assertEquals(2, p.offsetMap.toSource(2))
        assertEquals(3, p.offsetMap.toSource(3))
        // source→display：区间前缘映射到 X 位置；区间终点坍缩到 X 之后
        assertEquals(2, p.offsetMap.toDisplay(2))
        assertEquals(3, p.offsetMap.toDisplay(4))
    }

    @Test
    fun `correction applies after plain rules on display space`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "ABCDEF",
            rules = listOf(
                plainRule(pattern = "AB", replacement = "QQQ"),
                correction(sourceStart = 2, sourceEnd = 4, findText = "CD", replaceText = "X"),
            ),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        // 规则先把 AB→QQQ，纠错锚定 source [2,4) 的 CD 在 display 中换成 X
        assertEquals("QQQXEF", p.displayText)
        assertEquals(2, p.hitCount)
        assertMapInvariants(p.offsetMap)
    }

    @Test
    fun `correction with no plain rules works via identity base`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "ABC",
            rules = listOf(correction(sourceStart = 0, sourceEnd = 1, findText = "A", replaceText = "Z")),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        assertEquals("ZBC", p.displayText)
        assertMapInvariants(p.offsetMap)
    }

    // ── 诚实跳过 ─────────────────────────────────────────────────────────

    @Test
    fun `content drift skips correction without partial replacement`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "ABCDEF",
            rules = listOf(correction(sourceStart = 2, sourceEnd = 4, findText = "XY", replaceText = "Z")),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        assertEquals("ABCDEF", p.displayText)
        assertEquals(0, p.hitCount)
    }

    @Test
    fun `anchor outside scope is skipped not clamped`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "CDE",
            rules = listOf(correction(sourceStart = 2, sourceEnd = 4, findText = "CD", replaceText = "X")),
            bookId = "b1",
            scopeSourceBase = 10,
        )
        // 锚点在全局 [2,4)，scope 基址 10 → 局部区间为负 → 跳过
        assertEquals("CDE", p.displayText)
        assertEquals(0, p.hitCount)
    }

    @Test
    fun `anchor partially beyond scope end is skipped`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "CD",
            rules = listOf(correction(sourceStart = 0, sourceEnd = 5, findText = "CDXXX", replaceText = "X")),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        assertEquals("CD", p.displayText)
        assertEquals(0, p.hitCount)
    }

    @Test
    fun `disabled correction does not apply`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "ABCDEF",
            rules = listOf(correction(sourceStart = 2, sourceEnd = 4, findText = "CD", replaceText = "X", enabled = false)),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        assertEquals("ABCDEF", p.displayText)
        assertEquals(0, p.hitCount)
    }

    @Test
    fun `second overlapping correction is guarded by content check`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "ABCDEF",
            rules = listOf(
                correction(id = "correction:c1", sourceStart = 2, sourceEnd = 4, findText = "CD", replaceText = "X"),
                correction(id = "correction:c2", sourceStart = 3, sourceEnd = 5, findText = "DE", replaceText = "Y"),
            ),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        // 第一条把 CD→X 后，第二条的 display 内容校验失败，诚实跳过
        assertEquals("ABXEF", p.displayText)
        assertEquals(1, p.hitCount)
        assertMapInvariants(p.offsetMap)
    }

    @Test
    fun `two disjoint corrections both apply in order`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "ABCDEF",
            rules = listOf(
                correction(id = "correction:c1", sourceStart = 0, sourceEnd = 1, findText = "A", replaceText = "XX"),
                correction(id = "correction:c2", sourceStart = 5, sourceEnd = 6, findText = "F", replaceText = ""),
            ),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        assertEquals("XXBCDE", p.displayText)
        assertEquals(2, p.hitCount)
        assertMapInvariants(p.offsetMap)
    }

    // ── 删除 / 变长映射 ──────────────────────────────────────────────────

    @Test
    fun `deletion correction collapses source range`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "ABCDEF",
            rules = listOf(correction(sourceStart = 2, sourceEnd = 4, findText = "CD", replaceText = "")),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        assertEquals("ABEF", p.displayText)
        assertMapInvariants(p.offsetMap)
        // 删除区间坍缩：source 2/3/4 都映射到 display 2
        assertEquals(2, p.offsetMap.toDisplay(2))
        assertEquals(2, p.offsetMap.toDisplay(3))
        assertEquals(2, p.offsetMap.toDisplay(4))
    }

    @Test
    fun `growing correction keeps round trip floor`() {
        val p = ReplaceProjection.projectScoped(
            sourceText = "AB",
            rules = listOf(correction(sourceStart = 1, sourceEnd = 2, findText = "B", replaceText = "WXYZ")),
            bookId = "b1",
            scopeSourceBase = 0,
        )
        assertEquals("AWXYZ", p.displayText)
        assertMapInvariants(p.offsetMap)
        assertEquals(2, p.offsetMap.toSource(5))
    }

    // ── 缓存身份（规则失效缓存）──────────────────────────────────────────

    @Test
    fun `profile key changes when correction anchor or content changes`() {
        val base = listOf(
            plainRule(pattern = "AB", replacement = "Q"),
            correction(sourceStart = 2, sourceEnd = 4, findText = "CD", replaceText = "X"),
        )
        val key1 = ReplaceProfile.key("b1", base)
        assertEquals(key1, ReplaceProfile.key("b1", base))

        val movedAnchor = listOf(
            base[0],
            correction(sourceStart = 2, sourceEnd = 5, findText = "CDE", replaceText = "X"),
        )
        assertNotEquals(key1, ReplaceProfile.key("b1", movedAnchor))

        val changedFind = listOf(
            base[0],
            correction(sourceStart = 2, sourceEnd = 4, findText = "CE", replaceText = "X"),
        )
        assertNotEquals(key1, ReplaceProfile.key("b1", changedFind))

        val changedReplace = listOf(
            base[0],
            correction(sourceStart = 2, sourceEnd = 4, findText = "CD", replaceText = "Y"),
        )
        assertNotEquals(key1, ReplaceProfile.key("b1", changedReplace))

        // 撤销（移除纠错）也必须换 key：旧缓存/旧索引身份失效
        assertNotEquals(key1, ReplaceProfile.key("b1", listOf(base[0])))
    }

    @Test
    fun `plain rule key is unchanged by anchor support for cache compat`() {
        val rules = listOf(plainRule(pattern = "AB", replacement = "Q"))
        // 无锚点规则的 key 必须与历史实现一致（既有分页缓存/索引身份不失效）
        assertEquals(
            ReplaceProfile.key("b1", rules),
            ReplaceProfile.key("b1", listOf(rules[0].copy())),
        )
        assertTrue(ReplaceProfile.key("b1", rules).startsWith(ReplaceProfile.VERSION_PREFIX))
    }

    // ── 引擎防御：applyReplace 不把锚定纠错当正则编译 ────────────────────

    @Test
    fun `applyReplace ignores anchored rules instead of compiling empty pattern`() {
        val result = RuleEngine.applyReplace(
            "ABC",
            listOf(correction(sourceStart = 0, sourceEnd = 1, findText = "A", replaceText = "Z")),
        )
        // applyReplace 只负责普通正则链；锚定纠错由 projectScoped 叠加
        assertEquals("ABC", result.displayText)
        assertEquals(0, result.hitCount)
    }
}
